package com.tomessa.miff.ui.main

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.domain.navigation.MainTab
import com.tomessa.miff.domain.navigation.TabBarConfig
import com.tomessa.miff.ui.theme.GlassBlurRadius
import com.tomessa.miff.ui.theme.GlassConditions
import com.tomessa.miff.ui.theme.GlassLevel
import com.tomessa.miff.ui.theme.GlassPaletteLight
import com.tomessa.miff.ui.theme.GlassPaletteOnPhoto
import com.tomessa.miff.ui.theme.GlassVariant
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * PM feedback on the OPPO: "a white rectangle inside the bar" and "the opacity is not always the same". The bar is ONE surface with ONE fixed veil: no second
 * layer behind the central button, nothing of the bubble blurred into it, the same colour whatever the tab in use. Pixels are read from the rendered bar
 * (the GPU blur itself is judged on the phone: docs/qa/checklists/tabbar-android-recette-pm.md). Writes screenshots to build/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-port-xxhdpi")
class GlassTabBarConstancyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val density = 3f
    private val config = TabBarConfig.forBuild(isInternalBuild = true)
    private val modern =
        GlassConditions(sdkInt = 33, powerSaveMode = false, animationsRemoved = false, contrastIncreased = false, lowRamDevice = false)

    private fun text(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun bounds(id: Int): DpRect = composeRule.onNodeWithContentDescription(text(id)).getUnclippedBoundsInRoot()

    private fun image(): Bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()

    private fun shoot(
        name: String,
        bitmap: Bitmap = image(),
    ) {
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun pixel(
        bitmap: Bitmap,
        x: Dp,
        y: Dp,
    ): Int = bitmap.getPixel((x.value * density).toInt(), (y.value * density).toInt())

    private fun close(
        a: Int,
        b: Int,
        tolerance: Int,
    ): Boolean = listOf(16, 8, 0).all { shift -> abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) <= tolerance }

    // --- The fixed parameters ---------------------------------------------------------------------------------------------------------

    @Test
    fun theVeilIsOneFixedColour_perLevelAndVariant() {
        // Light glass: white 32 % (validated by the PM on 2026-11-11) and a blur of 28 dp; then the denser veil and the opaque fill of the fallbacks.
        assertEquals(Color.White.copy(alpha = 0.32f), glassVeil(GlassLevel.BLUR, GlassPaletteLight))
        assertEquals(Color.White.copy(alpha = 0.92f), glassVeil(GlassLevel.NO_BLUR, GlassPaletteLight))
        assertEquals(Color.White, glassVeil(GlassLevel.OPAQUE, GlassPaletteLight))
        assertEquals(Color.Black.copy(alpha = 0.60f), glassVeil(GlassLevel.BLUR, GlassPaletteOnPhoto))
        assertEquals(28.dp, GlassBlurRadius)
    }

    // --- ONE surface, nothing behind the central button --------------------------------------------------------------------------------

    private val tab = mutableStateOf(MainTab.FEED)
    private val level = mutableStateOf(GlassLevel.NO_BLUR)

    private fun showBar() {
        composeRule.setContent {
            MiffTheme {
                CompositionLocalProvider(LocalDensity provides Density(density, 1f)) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        Box(Modifier.align(Alignment.BottomCenter)) {
                            MainTabBar(
                                config = config,
                                selected = tab.value,
                                onSelect = {},
                                glass = TabBarGlass(GlassVariant.LIGHT, level.value),
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun theBarIsOneSingleSurface() {
        showBar()
        composeRule.onAllNodesWithTag(TAB_BAR_TAG).assertCountEquals(1)
        composeRule.onNodeWithTag(TAB_BAR_TAG).getUnclippedBoundsInRoot().let { assertEquals(64.dp, it.bottom - it.top) }
    }

    @Test
    fun noLighterRectangleBehindTheCentralButton_whateverTheTabInUse() {
        showBar()
        listOf(MainTab.FEED, MainTab.PROFILE).forEach { selected ->
            listOf(GlassLevel.NO_BLUR, GlassLevel.OPAQUE).forEach { lvl ->
                tab.value = selected
                level.value = lvl
                composeRule.waitForIdle()
                val bitmap = image()
                if (selected == MainTab.FEED) shoot("tabbar-constant-feed-$lvl", bitmap)
                val feed = bounds(R.string.tabbar_feed_a11y)
                val central = bounds(R.string.tabbar_update_a11y)
                val profile = bounds(R.string.tabbar_profile_a11y)
                val y = (central.top + central.bottom) / 2
                val reference = pixel(bitmap, feed.left - 12.dp, y)
                val cx = (central.left + central.right) / 2
                // Inside the 64 x 48 dp entry of the central button, outside its 44 dp plate: the glass and nothing else.
                listOf(cx - 29.dp, cx + 29.dp, (feed.right + central.left) / 2, (central.right + profile.left) / 2).forEach { x ->
                    assertTrue(
                        "tab $selected, $lvl: a lighter patch at $x (${Integer.toHexString(pixel(bitmap, x, y))} instead of ${Integer.toHexString(reference)})",
                        close(reference, pixel(bitmap, x, y), 2),
                    )
                }
            }
        }
    }

    // --- The bubble is never blurred into the bar --------------------------------------------------------------------------------------

    private val bubble = mutableStateOf<Color?>(null)

    @Test
    fun aBubbleAboveTheBar_neverChangesTheGlassOfTheBar() {
        composeRule.setContent {
            MiffTheme {
                CompositionLocalProvider(LocalDensity provides Density(density, 1f)) {
                    GlassTabHost(
                        variant = GlassVariant.LIGHT,
                        barVisible = true,
                        conditions = modern,
                        blurAllowed = true,
                        bar = { glass -> MainTabBar(config = config, selected = MainTab.FEED, onSelect = {}, glass = glass) },
                        overlay = {
                            bubble.value?.let { color ->
                                // A wide bubble whose bottom edge touches the top of the bar, as the one of the Update does.
                                Box(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = LocalTabBarInset.current)
                                        .width(300.dp)
                                        .height(60.dp)
                                        .background(color),
                                )
                            }
                        },
                    ) {
                        Box(Modifier.fillMaxSize().background(Color.Black))
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val bar = composeRule.onNodeWithTag(TAB_BAR_TAG).getUnclippedBoundsInRoot()
        val middle = (bar.left + bar.right) / 2
        // Points of the bar where a bubble just above would have been blurred in: its top, at the middle of it and beside the middle.
        val points = listOf(middle to bar.top + 6.dp, middle - 40.dp to bar.top + 6.dp, middle + 90.dp to bar.top + 10.dp)
        val before = image()
        val withoutBubble = points.map { (x, y) -> pixel(before, x, y) }
        shoot("tabbar-constant-without-bubble", before)

        bubble.value = Color.White
        composeRule.waitForIdle()
        val after = image()
        shoot("tabbar-constant-with-bubble", after)
        points.forEachIndexed { i, (x, y) ->
            assertTrue(
                "the bubble above the bar changed the glass at ($x, $y): ${Integer.toHexString(withoutBubble[i])} -> ${Integer.toHexString(pixel(after, x, y))}",
                close(withoutBubble[i], pixel(after, x, y), 1),
            )
        }
    }
}
