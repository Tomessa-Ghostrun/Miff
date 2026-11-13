package com.tomessa.miff.ui.permission

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tomessa.miff.domain.permission.BackgroundRequestOutcome
import com.tomessa.miff.domain.permission.LocationFlow
import com.tomessa.miff.domain.permission.PermissionNames

/*
 * PRIVACY, for this file: it ASKS the system for "all the time" after a tap on a button that says why (the disclosure is on the screen of that
 * button); it reads NO position and sends no event. Nothing is asked at start-up.
 */

/**
 * What the screen noted between the start of the request for "all the time" and its answer, to tell whether the system showed something.
 * Time is read from the clock handed in (the monotonic clock of the phone), so that the notes can be checked without a phone.
 */
internal class BackgroundRequestWatch(
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private var startedAt: Long? = null
    private var stopped = false

    fun start() {
        startedAt = clock()
        stopped = false
    }

    /** The app left the front (a screen of the system covers it entirely). */
    fun onStopped() {
        if (startedAt != null) stopped = true
    }

    /** Ends the watch and says what the system did. [granted] is the answer of the request. */
    fun finish(granted: Boolean): BackgroundRequestOutcome {
        val elapsed = startedAt?.let { clock() - it }
        val outcome = LocationFlow.backgroundOutcome(granted, elapsed, stopped)
        startedAt = null
        stopped = false
        return outcome
    }
}

/**
 * The standard request for "all the time" (ACCESS_BACKGROUND_LOCATION), to be called AFTER the precise location is given and the disclosure is on the
 * screen. On Android 11+ the system opens ITS OWN screen of the location permission of the app (no window from Miff); on Android 10 it shows its window.
 * The answer, [onFinished], says whether it was granted, whether the system showed something, or nothing at all (then the caller opens the settings of
 * the app, never a loop: see [LocationFlow.backgroundRoute]).
 *
 * Returns the function to call on the tap.
 */
@Composable
internal fun rememberBackgroundLocationRequest(onFinished: (BackgroundRequestOutcome) -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onFinished)
    val watch = remember { BackgroundRequestWatch() }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            latest(watch.finish(granted))
        }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, watch) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) watch.onStopped() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return {
        watch.start()
        launcher.launch(PermissionNames.BACKGROUND_LOCATION)
    }
}
