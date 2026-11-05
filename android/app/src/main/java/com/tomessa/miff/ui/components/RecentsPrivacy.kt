package com.tomessa.miff.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/*
 * Decision of the PM (2026-10-31): the picture of the app shown in the list of recent apps must not show the code, the names or the
 * photos of a Miff. Android 13 and later: Activity.setRecentsScreenshotEnabled(false) while such a screen is on view. FLAG_SECURE is
 * NOT used on purpose: it would also forbid the screenshots the PM takes for the acceptance tests. Earlier versions keep the
 * system's picture (no portable way that does not also block screenshots): a known limit, written in the README.
 */

/** The switch of the system picture of the app, behind an interface so that the counting is tested without an activity. */
internal fun interface RecentsScreenshotSwitch {
    fun setEnabled(enabled: Boolean)
}

/**
 * Counts the screens that want the picture hidden: two screens overlap during a transition, and the picture must come back only
 * when the LAST of them has gone.
 */
internal class RecentsPrivacyCounter(
    private val switch: RecentsScreenshotSwitch,
) {
    private var holders = 0

    fun acquire() {
        holders++
        if (holders == 1) switch.setEnabled(false)
    }

    fun release() {
        if (holders == 0) return
        holders--
        if (holders == 0) switch.setEnabled(true)
    }
}

private class ActivityRecentsSwitch(
    private val activity: Activity,
) : RecentsScreenshotSwitch {
    override fun setEnabled(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) activity.setRecentsScreenshotEnabled(enabled)
    }
}

private val counters = java.util.WeakHashMap<Activity, RecentsPrivacyCounter>()

private fun counterFor(activity: Activity): RecentsPrivacyCounter =
    synchronized(counters) { counters.getOrPut(activity) { RecentsPrivacyCounter(ActivityRecentsSwitch(activity)) } }

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/** Put this in a screen that shows the code, names or photos of a Miff: its picture is hidden from the recent apps while it is on view. */
@Composable
fun RecentsPrivacy() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val counter = counterFor(activity)
        counter.acquire()
        onDispose { counter.release() }
    }
}
