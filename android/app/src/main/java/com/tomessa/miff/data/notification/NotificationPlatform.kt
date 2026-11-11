package com.tomessa.miff.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.tomessa.miff.R
import com.tomessa.miff.data.permission.DevicePermissions
import com.tomessa.miff.data.permission.NotificationAskStore
import com.tomessa.miff.domain.notification.NotificationFamily
import com.tomessa.miff.domain.notification.NotificationPermissionState

/*
 * The part of the notifications that talks to the system: the channels, the tray, and the state of the permission. No position, no network, no log.
 * Alarms are NEVER exact here (SCHEDULE_EXACT_ALARM and USE_EXACT_ALARM are not declared and never will be for this feature, criterion N-M8).
 */

/** The notifications Miff has put in the tray of the system, by key (the key of deduplication of the line of the centre). */
interface NotificationTray {
    fun cancel(key: String)

    fun cancelAll()
}

class AndroidNotificationTray(
    context: Context,
) : NotificationTray {
    private val manager = NotificationManagerCompat.from(context.applicationContext)

    override fun cancel(key: String) {
        manager.cancel(key, TAG_ID)
    }

    override fun cancelAll() {
        manager.cancelAll()
    }

    companion object {
        /** A notification of Miff is identified by (key, 0): the key is the tag, so one line of the centre = one notification. */
        const val TAG_ID = 0
    }
}

/**
 * The four channels of the system: two families (reminders for me, news of my Miff) in two versions (normal, quiet for "Nuit calme").
 *
 * The importance of a channel can NEVER be raised by the app once the channel exists (the person owns it): it is fixed here, deliberately, from the
 * start (decision Q9bis of the ticket): normal = the default level (sound, no pop-up over what the person is doing), quiet = low (no sound, no
 * vibration, in the tray). No badge on the icon of the app (decision Q17). A name or description can change: [ensure] writes them again each time the
 * app comes to the front, so that a change of language shows in the settings of the phone.
 */
object NotificationChannels {
    const val REMINDERS = "miff_reminders"
    const val REMINDERS_QUIET = "miff_reminders_quiet"
    const val CIRCLE = "miff_circle"
    const val CIRCLE_QUIET = "miff_circle_quiet"

    val all = listOf(REMINDERS, REMINDERS_QUIET, CIRCLE, CIRCLE_QUIET)

    fun idOf(
        family: NotificationFamily,
        quiet: Boolean,
    ): String =
        when (family) {
            NotificationFamily.REMINDER -> if (quiet) REMINDERS_QUIET else REMINDERS
            NotificationFamily.CIRCLE -> if (quiet) CIRCLE_QUIET else CIRCLE
        }

    /** Creates the channels (or refreshes their texts). Cheap and idempotent; safe from any thread. */
    fun ensure(context: Context) {
        val manager = context.applicationContext.getSystemService(NotificationManager::class.java) ?: return

        fun channel(
            id: String,
            name: Int,
            description: Int,
            importance: Int,
        ) = NotificationChannel(id, context.getString(name), importance).apply {
            this.description = context.getString(description)
            setShowBadge(false)
        }
        manager.createNotificationChannels(
            listOf(
                channel(
                    REMINDERS,
                    R.string.android_notif_channel_reminders,
                    R.string.android_notif_channel_reminders_desc,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                channel(
                    REMINDERS_QUIET,
                    R.string.android_notif_channel_reminders_quiet,
                    R.string.android_notif_channel_quiet_desc,
                    NotificationManager.IMPORTANCE_LOW,
                ),
                channel(
                    CIRCLE,
                    R.string.android_notif_channel_circle,
                    R.string.android_notif_channel_circle_desc,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                channel(
                    CIRCLE_QUIET,
                    R.string.android_notif_channel_circle_quiet,
                    R.string.android_notif_channel_quiet_desc,
                    NotificationManager.IMPORTANCE_LOW,
                ),
            ),
        )
    }

    /** The person turned the normal channel of a family off in the settings of the phone ("Ce type est coupé dans le téléphone"). */
    fun isBlocked(
        context: Context,
        family: NotificationFamily,
    ): Boolean {
        val manager = context.applicationContext.getSystemService(NotificationManager::class.java) ?: return false
        val channel = manager.getNotificationChannel(idOf(family, quiet = false)) ?: return false
        return channel.importance == NotificationManager.IMPORTANCE_NONE
    }
}

/**
 * The state of the permission as the screens speak of it (one value, one wording: Settings > Notifications, Settings > Confidentialité, the centre, the
 * second-chance card) and the memory of the window of the system.
 */
class NotificationAuthorization(
    private val permissions: DevicePermissions,
    private val asks: NotificationAskStore,
) {
    fun state(): NotificationPermissionState =
        NotificationPermissionState.resolve(permissions.snapshot(), asks.systemAsked())

    /** Called when the system window comes back (granted or not): from now on a refusal means "only the settings of the phone". */
    fun onSystemWindowAnswered() = asks.markSystemAsked()

    /** Is a notification of Miff possible right now? The permission, nothing else (the switches of Settings are checked by the planner). */
    fun allowed(): Boolean = state() == NotificationPermissionState.ALLOWED
}
