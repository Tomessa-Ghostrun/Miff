package com.tomessa.miff.ui.startup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * EXTENSION POINT for the animated splash (ticket Onboarding, done on another branch). It is shown FIRST, before the
 * welcome carousel and before any routing; [onFinished] must be called exactly once when the splash is over (it must not
 * wait for the network: the session is checked behind it). Order of the app: splash -> (welcome carousel on the first
 * opening only) -> sign-in / routing. Until the splash exists, it finishes at once.
 */
@Composable
fun StartupSlot(onFinished: () -> Unit) {
    LaunchedEffect(Unit) { onFinished() }
}
