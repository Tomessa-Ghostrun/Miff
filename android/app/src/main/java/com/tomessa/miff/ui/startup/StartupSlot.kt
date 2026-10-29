package com.tomessa.miff.ui.startup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * First step of the app's own flow, before the welcome carousel and any routing; [onFinished] is called exactly once.
 *
 * The animated splash does NOT play here: it plays ABOVE the whole app, from [com.tomessa.miff.ui.splash.SplashHost]
 * (set up in MainActivity, once per cold start). The app is composed underneath, so the session is resolved and the
 * first screen (welcome carousel on the first opening only, otherwise sign-in / routing) is ready by the time the
 * animation fades out: splash -> (carousel) -> sign-in / routing, without the splash ever delaying the routing.
 * Touches and the screen reader are kept away from the app while the splash covers it. This slot therefore finishes
 * at once; it stays as the single place to add a future start-up step that must come before the carousel.
 */
@Composable
fun StartupSlot(onFinished: () -> Unit) {
    LaunchedEffect(Unit) { onFinished() }
}
