package com.tomessa.miff.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
fun Modifier.miffScreenInsets(): Modifier = composed { windowInsetsPadding(WindowInsets.safeDrawing) }

/** Top and left/right sides: status bar and cut-out. For a top bar, or for the content of a tab above the tab bar. */
fun Modifier.miffTopInsets(): Modifier =
    composed {
        windowInsetsPadding(WindowInsets.safeDrawing.only(HorizontalAndTop))
    }

/** Bottom and left/right sides: navigation bar (3 buttons or gestures), cut-out and keyboard. For a body, a bar or a sheet. */
fun Modifier.miffBottomInsets(): Modifier =
    composed {
        windowInsetsPadding(WindowInsets.safeDrawing.only(HorizontalAndBottom))
    }

/** Height of the status bar and cut-out, for the rare element that must not be padded as a whole but only pushed down. */
@Composable
fun miffTopInset(): Dp =
    WindowInsets.safeDrawing
        .only(Top)
        .asPaddingValues()
        .calculateTopPadding()

/** True while the keyboard is on screen (the floating tab bar hides then). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun miffKeyboardVisible(): Boolean = WindowInsets.isImeVisible
