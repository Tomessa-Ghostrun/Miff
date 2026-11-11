package com.tomessa.miff.data.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tomessa.miff.MainActivity
import com.tomessa.miff.R
import com.tomessa.miff.data.settings.AndroidLanguageStore
import com.tomessa.miff.domain.notification.NotificationDestination
import com.tomessa.miff.domain.notification.NotificationType

/*
 * PRIVACY, for this file: the notification of the system holds the two sentences of the type (the words of the PM) and nothing else: no name, no hour, no place, no
 * address, no photo, no battery, no count. It carries, for the tap, the destination (a keyword) and the key of the line of the centre. No log.
 */

/** Puts a notification in the tray of the system (a double in tests). */
interface NotificationPresenter {
    /** [quiet]: the quiet channel (no sound, no vibration). */
    fun show(
        type: NotificationType,
        key: String,
        quiet: Boolean,
    )
}

/** The way a tap on a notification reaches the app: a keyword for the tab, and the key of the line of the centre to mark as read. */
object NotificationIntents {
    const val ACTION_OPEN = "com.tomessa.miff.notification.OPEN"
    const val EXTRA_DESTINATION = "miff.notification.destination"
    const val EXTRA_KEY = "miff.notification.key"

    /** What the app understands of such an intent: the tab to show and the key; null when it is not one of ours or is malformed (never an error). */
    fun parse(intent: Intent?): Pair<NotificationDestination, String?>? {
        if (intent?.action != ACTION_OPEN) return null
        val destination =
            NotificationDestination.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_DESTINATION) }
                ?: return null
        return destination to intent.getStringExtra(EXTRA_KEY)?.takeIf { it.length <= 200 }
    }
}

class AndroidNotificationPresenter(
    context: Context,
) : NotificationPresenter {
    private val appContext = context.applicationContext

    override fun show(
        type: NotificationType,
        key: String,
        quiet: Boolean,
    ) {
        // Resolved NOW, in the language of the app (the choice of Settings > Langue, also before Android 13): the text of the moment, not of the planning.
        val context = AndroidLanguageStore.wrap(appContext)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationChannels.ensure(context)
        val open =
            Intent(context, MainActivity::class.java)
                .setAction(NotificationIntents.ACTION_OPEN)
                .putExtra(NotificationIntents.EXTRA_DESTINATION, type.destination.name)
                .putExtra(NotificationIntents.EXTRA_KEY, key)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
        val tap =
            PendingIntent.getActivity(
                context,
                type.ordinal,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, NotificationChannels.idOf(type.family, quiet))
                .setSmallIcon(R.drawable.ic_stat_miff)
                .setColor(ACTION_BLUE)
                .setContentTitle(context.getString(NotificationTexts.title(type)))
                .setContentText(context.getString(NotificationTexts.body(type)))
                .setCategory(
                    if (type.destination ==
                        NotificationDestination.UPDATE
                    ) {
                        NotificationCompat.CATEGORY_REMINDER
                    } else {
                        NotificationCompat.CATEGORY_STATUS
                    },
                ).setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(tap)
                .build()
        // Tag = the key of the line of the centre: reading the line removes this notification, and the same key never makes two.
        manager.notify(key, AndroidNotificationTray.TAG_ID, notification)
    }

    private companion object {
        /** The blue of the actions of the design system (colorActionPrimary), as the system wants a colour: an Int, not a Compose colour. */
        val ACTION_BLUE = 0xFF043CD1.toInt()
    }
}
