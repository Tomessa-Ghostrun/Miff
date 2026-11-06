package com.tomessa.miff.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tomessa.miff.domain.tracking.StatusLevel
import com.tomessa.miff.ui.theme.GlassBlurRadius
import com.tomessa.miff.ui.theme.GlassBorderWidth
import com.tomessa.miff.ui.theme.GlassCentralPlate
import com.tomessa.miff.ui.theme.GlassConditions
import com.tomessa.miff.ui.theme.GlassLevel
import com.tomessa.miff.ui.theme.GlassPaletteLight
import com.tomessa.miff.ui.theme.GlassPolicy
import com.tomessa.miff.ui.theme.GlassVariant
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShadowToken
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorStatusDanger
import com.tomessa.miff.ui.theme.colorStatusSafe
import com.tomessa.miff.ui.theme.colorStatusWarning
import com.tomessa.miff.ui.theme.glassPalette
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.rememberGlassConditions
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/*
 * The frosted-glass tab bar (ticket TABBAR-GLASS, design/tab-bar-liquid-glass.md). Kept in its own file so that MainTabBar.kt and
 * MainShell.kt change as little as possible: this file holds the glass surface, the host that lets the content pass UNDER the bar,
 * and the central button.
 */

/** What a bar needs to draw its glass: the variant, the level decided for this phone, and (only for BLUR) the Haze state to read. */
@Immutable
class TabBarGlass(
    val variant: GlassVariant,
    val level: GlassLevel,
    val hazeState: HazeState? = null,
) {
    companion object {
        /** A light bar without any blur: what a bar shown on its own (tests, previews) looks like. */
        val Default = TabBarGlass(GlassVariant.LIGHT, GlassLevel.NO_BLUR)
    }
}

/**
 * Bottom margin that a tab must keep so that its last element can scroll ABOVE the bar: the height of the bar and of what is
 * under it (22 dp of air and the navigation bar), measured by [GlassTabHost]. Zero when the bar is hidden (keyboard) or absent.
 * The content itself fills the screen and passes under the bar: that is what makes the glass visible.
 */
val LocalTabBarInset = compositionLocalOf { 0.dp }

/**
 * Lays out the tabs full screen with the bar over them, at the bottom. The content is recorded for the blur ONLY while the level
 * is [GlassLevel.BLUR] (the recording has a cost: nothing is done when the bar is opaque, unblurred or hidden).
 *
 * The bar is measured, and its height is given to the tabs through [LocalTabBarInset], so that the bar never hides content.
 */
@Composable
fun GlassTabHost(
    variant: GlassVariant,
    barVisible: Boolean,
    bar: @Composable (TabBarGlass) -> Unit,
    modifier: Modifier = Modifier,
    conditions: GlassConditions = rememberGlassConditions(),
    /** The switch of the fallback ([GlassPolicy.BLUR_ALLOWED]); a parameter so that the tests can force it either way. */
    blurAllowed: Boolean = GlassPolicy.BLUR_ALLOWED,
    content: @Composable () -> Unit,
) {
    val level = GlassPolicy.level(conditions, variant, blurAllowed)
    val hazeState = rememberHazeState()
    val blurring = barVisible && level == GlassLevel.BLUR
    var barHeightPx by remember { mutableIntStateOf(0) }
    val inset = if (barVisible) with(LocalDensity.current) { barHeightPx.toDp() } else 0.dp
    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().then(if (blurring) Modifier.hazeSource(hazeState) else Modifier)) {
            CompositionLocalProvider(LocalTabBarInset provides inset, content = content)
        }
        if (barVisible) {
            Box(modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { barHeightPx = it.height }) {
                bar(TabBarGlass(variant, level, if (blurring) hazeState else null))
            }
        }
    }
}

/**
 * The glass: shadow, rounded clip, blur of what is behind (BLUR only), veil, hairline. At the OPAQUE level nothing is transparent
 * and there is no hairline (the mockups: a plain white or `#222222` bar with the shadow).
 */
fun Modifier.glassSurface(
    shape: Shape,
    glass: TabBarGlass,
): Modifier {
    val palette = glassPalette(glass.variant)
    val veil =
        when (glass.level) {
            GlassLevel.BLUR -> palette.tint
            GlassLevel.NO_BLUR -> palette.tintNoBlur
            GlassLevel.OPAQUE -> palette.surfaceOpaque
        }
    val blurred =
        if (glass.level == GlassLevel.BLUR && glass.hazeState != null) {
            // No noise, no colour effect of the library: the veil above is ours, so that the contrasts of the spec are the real ones.
            Modifier.hazeBlur(input = HazeInput.Sources(glass.hazeState), style = BlurStyle)
        } else {
            Modifier
        }
    val hairline =
        if (glass.level == GlassLevel.OPAQUE) Modifier else Modifier.border(GlassBorderWidth, palette.border, shape)
    return this
        .miffShadow(ShadowDefault, shape)
        .clip(shape)
        .then(blurred)
        .background(veil)
        .then(hairline)
}

private val BlurStyle =
    HazeBlurStyle {
        blurRadius(GlassBlurRadius)
        noiseFactor(0f)
        colorEffects(emptyList())
    }

// --- The central button (mockups 07 and 08, design/screens/update.md section 3) -------------------------------------------------

/** The colours of the central button for a level of status. Pure, so that it is tested without drawing anything. */
@Immutable
class CentralButtonColors(
    val ring: Color,
    val dot: Color,
)

/**
 * Green, orange, red: the ring is the blue of the actions and the dot the colour of the status. NEUTRAL (status unknown or sharing
 * stopped, decided by the PM): ring AND dot grey `#595959`, like the other buttons of the bar, on any variant of the bar.
 */
fun centralButtonColors(level: StatusLevel): CentralButtonColors =
    when (level) {
        StatusLevel.NEUTRAL -> CentralButtonColors(GlassPaletteLight.iconInactive, GlassPaletteLight.iconInactive)
        StatusLevel.GREEN -> CentralButtonColors(colorActionPrimary, colorStatusSafe)
        StatusLevel.ORANGE -> CentralButtonColors(colorActionPrimary, colorStatusWarning)
        StatusLevel.RED -> CentralButtonColors(colorActionPrimary, colorStatusDanger)
    }

private val PlateSize = 44.dp
private val RingSize = 36.dp
private val RingWidth = 3.dp
private val DotSize = 24.dp

/** shadow 0 / 1 / 4 black 30 % of the mockup plate. */
private val PlateShadow = ShadowToken(offsetY = 1.dp, blur = 4.dp, color = Color.Black.copy(alpha = 0.30f))

/**
 * An OPAQUE white plate (never glass: it isolates the colour from any background), a ring, and a dot of the colour of the status:
 * full colour, NO pictogram (decision of the PM: the colour is the only signal; the spoken name of the tab says the rest).
 * The dot has 3 dp of white air around it, inside the ring.
 */
@Composable
fun CentralStatusButton(
    level: StatusLevel,
    modifier: Modifier = Modifier,
) {
    val colors = centralButtonColors(level)
    Box(
        modifier =
            modifier
                .size(PlateSize)
                .miffShadow(PlateShadow, CircleShape)
                .background(GlassCentralPlate, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(RingSize).border(RingWidth, colors.ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(DotSize).background(colors.dot, CircleShape))
        }
    }
}

/** Height of the bar of the mockups, and of each entry (64 x 48, at least 48 dp for the touch). */
internal val GlassBarHeight: Dp = 64.dp
