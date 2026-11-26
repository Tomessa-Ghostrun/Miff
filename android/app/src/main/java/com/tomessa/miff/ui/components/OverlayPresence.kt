package com.tomessa.miff.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import com.tomessa.miff.domain.paywall.OverlayPresence

/** Where the overlays say they are open (provided by `MainShell`; nothing, and so no effect, in a screen test or a build of a screen that does not provide it). */
val LocalOverlayPresence = staticCompositionLocalOf<OverlayPresence?> { null }

/**
 * Call it as the FIRST line of the content of every dialog, bottom sheet, drop-down menu or full-screen window: while it is composed the periodic offer waits (see [OverlayPresence]). It draws
 * nothing. `PeriodicPaywallBuildTest` fails if an overlay of the app does not call it.
 */
@Composable
fun OverlayPresent() {
    val presence = LocalOverlayPresence.current
    DisposableEffect(presence) {
        presence?.onOverlayOpened()
        onDispose { presence?.onOverlayClosed() }
    }
}
