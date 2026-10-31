package com.tomessa.miff.ui.components

import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

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
    val view = LocalView.current
    val density = LocalDensity.current
    val orientation = LocalConfiguration.current.orientation
    val reportedTop = reported.getTop(density)
    val reportedNavigationBottom = WindowInsets.navigationBars.getBottom(density)
    val fallback =
        remember(view, orientation, reportedTop, reportedNavigationBottom) {
            SystemBarsFallback.compute(view, orientation, reportedTop, reportedNavigationBottom)
        }
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
    val view = LocalView.current
    val density = LocalDensity.current
    val orientation = LocalConfiguration.current.orientation
    return SystemBarsFallback.compute(
        view,
        orientation,
        WindowInsets.safeDrawing.getTop(density),
        WindowInsets.navigationBars.getBottom(density),
    )
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
 * 2-button) navigation, only when the reported bar inset is 0 and the window reaches the bottom of the screen: with gestures
 * there is no bar to keep out of, only a thin line. Never used when the system gives a real value, so a phone that works keeps
 * its exact insets (cut-out included).
 */
internal data class SystemBarsFallback(
    val top: Int,
    val bottom: Int,
) {
    companion object {
        val None = SystemBarsFallback(0, 0)

        fun compute(
            view: View,
            orientation: Int,
            reportedTop: Int,
            reportedNavigationBottom: Int,
        ): SystemBarsFallback {
            val onScreen = IntArray(2).also { view.getLocationOnScreen(it) }
            val rootInsets = ViewCompat.getRootWindowInsets(view)
            val landscape = orientation == Configuration.ORIENTATION_LANDSCAPE
            val statusBarShown = rootInsets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
            val top =
                if (reportedTop == 0 && onScreen[1] <= 0 && statusBarShown) {
                    SystemBarSizes.statusBarHeight(landscape)
                } else {
                    0
                }
            val navigationBarShown = rootInsets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
            val reachesBottom = onScreen[1] + view.height >= SystemBarSizes.realScreenHeight(view) - 1
            val bottom =
                if (reportedNavigationBottom == 0 && navigationBarShown && reachesBottom && !landscape &&
                    SystemBarSizes.hasNavigationButtons()
                ) {
                    SystemBarSizes.navigationBarHeight()
                } else {
                    0
                }
            return if (top == 0 && bottom == 0) None else SystemBarsFallback(top, bottom)
        }
    }
}

/** The sizes the system itself declares for its bars (framework resources); the debug diagnostic displays them too. */
internal object SystemBarSizes {
    private fun dimen(name: String): Int {
        val resources = Resources.getSystem()
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    fun statusBarHeight(landscape: Boolean): Int =
        listOf(if (landscape) "status_bar_height_landscape" else "status_bar_height_portrait", "status_bar_height")
            .firstNotNullOfOrNull { dimen(it).takeIf { size -> size > 0 } } ?: 0

    fun navigationBarHeight(): Int = dimen("navigation_bar_height")

    /** 0 = 3 buttons, 1 = 2 buttons, 2 = gestures (config_navBarInteractionMode); null when the phone does not say. */
    fun navigationMode(): Int? {
        val resources = Resources.getSystem()
        val id = resources.getIdentifier("config_navBarInteractionMode", "integer", "android")
        return if (id > 0) resources.getInteger(id) else null
    }

    fun hasNavigationButtons(): Boolean = navigationMode()?.let { it != NAVIGATION_GESTURES } ?: false

    @Suppress("DEPRECATION")
    fun realScreenHeight(view: View): Int {
        val display = view.display ?: return Int.MAX_VALUE
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        return metrics.heightPixels
    }

    private const val NAVIGATION_GESTURES = 2
}
