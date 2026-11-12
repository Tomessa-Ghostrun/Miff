package com.tomessa.miff.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tomessa.miff.AppContainer
import com.tomessa.miff.MiffApplication
import com.tomessa.miff.domain.notification.NotificationType
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * NOTIF-4A and NOTIF-5A. The ONE receiver of the reminders: the alarm of a notification that is due, and the events that make the alarms be planned again
 * (restart of the phone, update of the app, change of the time or of the zone: the system erases or shifts them).
 *
 * SECURITY and PRIVACY: NOT exported. The alarms are explicit intents to this component, built by [AndroidNotificationAlarms]; the system broadcasts of the
 * filter below come from the system only. What an intent says (a type, a key) is only a hint that [NotificationScheduler] checks against the plan of the
 * moment: a forged intent cannot make a notification appear that the plan does not hold. Nothing is logged. No foreground service: the work fits in the
 * few seconds a receiver has ([BUDGET_MILLIS], like the receiver of the detection); what cannot finish waits for the next alarm.
 */
class NotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.applicationContext as? MiffApplication ?: return
        val container = app.container
        val work: suspend (AppContainer) -> Unit =
            when (intent.action) {
                ACTION_FIRE -> {
                    val type = NotificationType.fromCode(intent.getStringExtra(EXTRA_TYPE)) ?: return
                    val key = intent.getStringExtra(EXTRA_KEY)?.takeIf { it.length in 1..MAX_KEY_LENGTH } ?: return
                    when (type) {
                        // About the circle: the server is read a moment before it is shown (the network belongs to this receiver and its seconds).
                        NotificationType.N3, NotificationType.N4 -> {
                            { it.notificationScheduler.fireCircle(type, key) }
                        }

                        // N7 (end of my trip): decided locally, from the trip the phone knows; no server.
                        NotificationType.N1, NotificationType.N2, NotificationType.N7 -> {
                            { it.notificationScheduler.fire(type, key) }
                        }

                        // N6 and N8 are never an alarm (N8 is only a line of the centre, written by the app when it is in front).
                        NotificationType.N6, NotificationType.N8 -> {
                            return
                        }
                    }
                }

                // The system erased the alarms (restart, update of the app) or moved the clock (time, zone): they are asked again from the snapshot.
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                -> {
                    { it.notificationScheduler.reconcile(force = true) }
                }

                else -> {
                    return
                }
            }
        val pending = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(BUDGET_MILLIS) {
                    // A purge of the cache left by a failed sign-out comes before any other access to Firestore.
                    container.cachePurger.purgeIfPending()
                    work(container)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.tomessa.miff.notification.FIRE"
        const val EXTRA_TYPE = "miff.notification.type"
        const val EXTRA_KEY = "miff.notification.alarm.key"
        const val EXTRA_MIFF = "miff.notification.alarm.miff"
        const val EXTRA_DUE = "miff.notification.alarm.due"

        /** A receiver has about ten seconds: stopping a little before, what is not finished waits for the next alarm. */
        const val BUDGET_MILLIS = 8_500L
        private const val MAX_KEY_LENGTH = 200

        // A failure of a notification never takes the app down from a receiver: swallowed here (nothing is logged: it could hold a Miff).
        private val scope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.Default +
                    CoroutineExceptionHandler {
                        _,
                        _,
                        ->
                    },
            )
    }
}

/**
 * Is the app in front? Written by the lifecycle observer of the application, read when a notification is due: in front, the notification of the system is
 * not shown (the bubble of the Feed already speaks), only a line of the centre. False by default: a process woken by an alarm is not in front.
 */
object AppVisibility {
    @Volatile
    var inForeground: Boolean = false
}
