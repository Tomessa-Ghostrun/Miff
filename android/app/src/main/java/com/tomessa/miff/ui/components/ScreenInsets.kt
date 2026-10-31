package com.tomessa.miff.ui.components

import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.areNavigationBarsVisible
import androidx.compose.foundation.layout.areStatusBarsVisible
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp

/*
 * THE ONLY PLACE where the app deals with the "safe areas" of the phone: status bar (clock, battery), navigation bar (3 buttons
 * or gesture line), camera cut-out (notch) and keyboard. The app draws edge to edge (MainActivity.enableEdgeToEdge, and
 * Android 15+ imposes it), so a screen that does nothing is drawn UNDER the clock and the icons of the system.
 *
 * RULE: every screen (and every bar, sheet, overlay) takes its safe areas from here, never from the raw Compose functions
 * (safeDrawingPadding, statusBarsPadding, navigationBarsPadding, imePadding, windowInsetsPadding, Scaffold contentWindowInsets,
 * consumeWindowInsets). A test (ScreenInsetsRuleTest) fails when one of them appears elsewhere. These modifiers CONSUME what
 * they pad, so nesting them never doubles a margin; and nothing else may consume insets it did not pad.
 *
 * Which one to use:
 * - a whole screen                          -> [MiffScreen] (or [miffScreenInsets] on an existing root)
 * - a top bar that draws under the status bar -> [miffTopInsets] on the bar, then [miffBottomInsets] on the body under it
 * - the floating tab bar                      -> [miffBottomInsets] (the tab content uses [miffTopInsets])
 * - content of a bottom sheet                 -> [miffBottomInsets]
 * A background (colour, gradient, picture) goes BEFORE the modifier so that it still fills the area under the system bars.
 * The splash screen is the one voluntary exception: it is full screen on purpose and takes nothing from here.
 */

private val Top = WindowInsetsSides.Top
private val HorizontalAndTop = WindowInsetsSides.Horizontal + WindowInsetsSides.Top
private val HorizontalAndBottom = WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom

/**
 * Container of a whole screen: fills the window and keeps its [content] out of the status bar, the navigation bar, the cut-out
 * and the keyboard. Put the background in [modifier] (it fills the whole window, under the system bars, and is not padded).
 */
@Composable
fun MiffScreen(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().miffScreenInsets(), content = content)
}

/** All four sides: status bar, navigation bar, cut-out, keyboard. For the root of a screen that is not a [MiffScreen]. */
fun Modifier.miffScreenInsets(): Modifier = composed { windowInsetsPadding(miffSafeInsets()) }

/** Top and left/right sides: status bar and cut-out. For a top bar, or for the content of a tab above the tab bar. */
fun Modifier.miffTopInsets(): Modifier = composed { windowInsetsPadding(miffSafeInsets().only(HorizontalAndTop)) }

/** Bottom and left/right sides: navigation bar (3 buttons or gestures), cut-out and keyboard. For a body, a bar or a sheet. */
fun Modifier.miffBottomInsets(): Modifier = composed { windowInsetsPadding(miffSafeInsets().only(HorizontalAndBottom)) }

/** Height of the status bar and cut-out, for the rare element that must not be padded as a whole but only pushed down. */
@Composable
fun miffTopInset(): Dp =
    miffSafeInsets()
        .only(Top)
        .asPaddingValues()
        .calculateTopPadding()

/** True while the keyboard is on screen (the floating tab bar hides then). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun miffKeyboardVisible(): Boolean = WindowInsets.isImeVisible

/**
 * What the screens use: the safe areas REPORTED by the system, raised to the real size of the system bars when the phone
 * reports nothing for a bar that is on screen (see [SystemBarsFallback]). The recipe of 2026-10-31 on the OPPO Find X5 Lite
 * (ColorOS, Android 13) showed content drawn under the status bar although every screen asked for the status bar insets:
 * whatever the reason, a screen must not depend on the phone reporting them. The fallback is off unless
 * [ProvideSystemBarsFallback] switched it on (MainActivity does), so the screen tests see the insets they send, and nothing else.
 */
@Composable
internal fun miffSafeInsets(): WindowInsets {
    val reported = WindowInsets.safeDrawing
    if (!LocalSystemBarsFallback.current) return reported
    val fallback = rememberSystemBarsFallback(reportedTop = reported.getTop(LocalDensity.current))
    return if (fallback == SystemBarsFallback.None) {
        reported
    } else {
        reported.union(WindowInsets(top = fallback.top, bottom = fallback.bottom))
    }
}

/** What [miffSafeInsets] adds for the current window (also shown by the debug diagnostic). */
@Composable
internal fun miffSystemBarsFallbackInUse(): SystemBarsFallback {
    if (!LocalSystemBarsFallback.current) return SystemBarsFallback.None
    return rememberSystemBarsFallback(reportedTop = WindowInsets.safeDrawing.getTop(LocalDensity.current))
}

/**
 * The fallback of the current window, recomputed whenever one of its ingredients changes. Every ingredient is read from
 * Compose state (reported insets and visibility of the bars: reading them recomposes when the system sends new ones) or from
 * the configuration (rotation, window resize, display size, which recompose too). Nothing is read from the [View] itself
 * (height, position): on the first composition the view is not measured yet, so such a value would be 0 and, being part of a
 * remembered result, would stay wrong. The position of the window comes from the window manager instead (see
 * [SystemBarSizes.windowArea]), which knows it before any layout.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun rememberSystemBarsFallback(reportedTop: Int): SystemBarsFallback {
    val view = LocalView.current
    val density = LocalDensity.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val reportedNavigationBottom = WindowInsets.navigationBars.getBottom(density)
    val statusBarShown = WindowInsets.areStatusBarsVisible
    val navigationBarShown = WindowInsets.areNavigationBarsVisible
    // Read at each composition (two cheap calls, no layout needed): a rotation or a resized window changes the configuration,
    // which recomposes this function, and the keys below then change.
    val window = SystemBarSizes.windowArea(view)
    return remember(
        window,
        landscape,
        density.density,
        reportedTop,
        reportedNavigationBottom,
        statusBarShown,
        navigationBarShown,
    ) {
        SystemBarsFallback.compute(
            window = window,
            landscape = landscape,
            reportedTop = reportedTop,
            reportedNavigationBottom = reportedNavigationBottom,
            statusBarShown = statusBarShown,
            navigationBarShown = navigationBarShown,
        )
    }
}

/** Switches the fallback of [miffSafeInsets] on for everything inside [content] (one place: MainActivity). */
@Composable
fun ProvideSystemBarsFallback(
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSystemBarsFallback provides enabled, content = content)
}

private val LocalSystemBarsFallback = compositionLocalOf { false }

/**
 * Sizes (in pixels) added when the phone reports 0 for a system bar that the window really goes under.
 *
 * Top: the status bar height of the system resources, only when the reported top inset is 0, the window starts at the very top
 * of the screen and the status bar is not reported hidden. Bottom: the navigation bar height, only with the 3-button (or
 * 2-button) navigation, only when the reported bar inset is 0, the bar is not reported hidden, in portrait, and the window
 * reaches the bottom of the screen: with gestures there is no bar to keep out of, only a thin line.
 *
 * A real value is never replaced: [miffSafeInsets] makes a UNION (the maximum, side by side) of what is reported and of what is
 * added here, and nothing is added at all for a side where the system reports something. So a phone that works keeps its
 * exact insets (cut-out included), and a margin cannot be doubled.
 *
 * Limits, on purpose:
 * - INERT under Android 10 and below (API 29 and less): there, "is this bar visible" is deduced from its inset (a reported 0
 *   reads as "hidden", so the condition "reported 0 but visible" is never true) and the window manager does not give the area
 *   of the window ([SystemBarSizes.windowArea] answers null). The phone of the recipe (OPPO, Android 13) is not concerned.
 * - The bottom needs `config_navBarInteractionMode`, a PRIVATE framework resource: when a phone does not have it,
 *   [SystemBarSizes.navigationMode] answers null and the bottom of the fallback is simply off. That is the safe side: the
 *   screen then behaves exactly as without the fallback (nothing added, nothing doubled).
 */
internal data class SystemBarsFallback(
    val top: Int,
    val bottom: Int,
) {
    companion object {
        val None = SystemBarsFallback(0, 0)

        fun compute(
            window: WindowArea?,
            landscape: Boolean,
            reportedTop: Int,
            reportedNavigationBottom: Int,
            statusBarShown: Boolean,
            navigationBarShown: Boolean,
            sizes: BarSizeSource = SystemBarSizes,
        ): SystemBarsFallback {
            if (window == null) return None
            val top =
                if (reportedTop == 0 && window.top <= 0 && statusBarShown) sizes.statusBarHeight(landscape) else 0
            val reachesBottom = window.bottom >= window.screenBottom - 1
            val bottom =
                if (reportedNavigationBottom == 0 && navigationBarShown && reachesBottom && !landscape &&
                    sizes.hasNavigationButtons()
                ) {
                    sizes.navigationBarHeight()
                } else {
                    0
                }
            return if (top == 0 && bottom == 0) None else SystemBarsFallback(top, bottom)
        }
    }
}

/** Where the window is on the screen, in pixels (screen coordinates): its top, its bottom, and the bottom of the screen. */
internal data class WindowArea(
    val top: Int,
    val bottom: Int,
    val screenBottom: Int,
)

/** What the fallback needs to know about the bars (the real one reads the framework resources; the tests give their own). */
internal interface BarSizeSource {
    fun statusBarHeight(landscape: Boolean): Int

    fun navigationBarHeight(): Int

    fun hasNavigationButtons(): Boolean
}

/** The sizes the system itself declares for its bars (framework resources); the debug diagnostic displays them too. */
internal object SystemBarSizes : BarSizeSource {
    private fun dimen(name: String): Int {
        val resources = Resources.getSystem()
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    override fun statusBarHeight(landscape: Boolean): Int =
        listOf(if (landscape) "status_bar_height_landscape" else "status_bar_height_portrait", "status_bar_height")
            .firstNotNullOfOrNull { dimen(it).takeIf { size -> size > 0 } } ?: 0

    override fun navigationBarHeight(): Int = dimen("navigation_bar_height")

    /**
     * 0 = 3 buttons, 1 = 2 buttons, 2 = gestures (config_navBarInteractionMode); null when the phone does not say.
     * That resource is PRIVATE to the framework (not part of the public API): it may be missing on some phones or versions,
     * and then the bottom of the fallback is off (see [SystemBarsFallback]).
     */
    fun navigationMode(): Int? {
        val resources = Resources.getSystem()
        val id = resources.getIdentifier("config_navBarInteractionMode", "integer", "android")
        return if (id > 0) resources.getInteger(id) else null
    }

    override fun hasNavigationButtons(): Boolean = navigationMode()?.let { it != NAVIGATION_GESTURES } ?: false

    /**
     * Position of the window on the screen, from the window manager: known before the first layout, unlike the height and the
     * position of a [View] at the first composition. Null before Android 11 (API 30), where it is not available: the fallback
     * is off there.
     */
    fun windowArea(view: View): WindowArea? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val windowManager = view.context.getSystemService(WindowManager::class.java) ?: return null
        val current = windowManager.currentWindowMetrics.bounds
        val screen = windowManager.maximumWindowMetrics.bounds
        return WindowArea(top = current.top, bottom = current.bottom, screenBottom = screen.bottom)
    }

    private const val NAVIGATION_GESTURES = 2
}
