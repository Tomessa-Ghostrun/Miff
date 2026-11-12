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
import com.tomessa.miff.domain.notification.CircleNewsKeys
import com.tomessa.miff.domain.notification.CircleNewsPayload
import com.tomessa.miff.domain.notification.MiffIdShape
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

    /**
     * N9: "{Prénom} a donné des nouvelles", pushed by the server. The tray holds ONE notification per author (the tag is the tag of [CircleNewsKeys.trayTag]: a new one
     * replaces the old one); [centerId] is the key of its line of the centre, carried by the tap to mark the line as read. [quiet]: the quiet channel, at night.
     */
    fun showCircleNews(
        news: CircleNewsPayload,
        centerId: String,
        quiet: Boolean,
    ) = Unit
}

/** The way a tap on a notification reaches the app: a keyword for the tab, and the key of the line of the centre to mark as read. */
object NotificationIntents {
    const val ACTION_OPEN = "com.tomessa.miff.notification.OPEN"
    const val EXTRA_DESTINATION = "miff.notification.destination"
    const val EXTRA_KEY = "miff.notification.key"
    const val EXTRA_MIFF_ID = "miff.notification.miff"

    /** What the app understands of such an intent: the tab to show and the key; null when it is not one of ours or is malformed (never an error). */
    fun parse(intent: Intent?): Pair<NotificationDestination, String?>? {
        if (intent?.action != ACTION_OPEN) return null
        val destination =
            NotificationDestination.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_DESTINATION) }
                ?: return null
        return destination to intent.getStringExtra(EXTRA_KEY)?.takeIf { it.length <= 200 }
    }

    /**
     * The Miff a tap on a notification of the circle is about, or null. The intent can come from ANY application (the activity is the launcher one), so the identifier is
     * checked for its shape ([MiffIdShape]) and nothing else is read from it: it only ever selects a Miff the person already belongs to (the directory ignores any other).
     */
    fun miffId(intent: Intent?): String? =
        if (intent?.action !=
            ACTION_OPEN
        ) {
            null
        } else {
            intent.getStringExtra(EXTRA_MIFF_ID)?.takeIf { MiffIdShape.isValid(it) }
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
        // The check stays INSIDE this method (Android Lint only accepts a permission check it can see next to the call to notify).
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

    /**
     * N9. Text composed HERE, in the language of the app: the title holds the first name of the author (already cleaned and cut by the parser), the body is fixed. No photo,
     * no position, no Miff name, no hour. The tap opens the Feed of the Miff concerned (the identifier was validated by the parser and is validated again when the tap comes back).
     * The importance and the sound come from the channel (sound and vibration of the phone by day, the quiet channel at night).
     */
    override fun showCircleNews(
        news: CircleNewsPayload,
        centerId: String,
        quiet: Boolean,
    ) {
        val context = AndroidLanguageStore.wrap(appContext)
        val manager = NotificationManagerCompat.from(context)
        // The check stays INSIDE this method (Android Lint only accepts a permission check it can see next to the call to notify).
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationChannels.ensure(context)
        val tag = CircleNewsKeys.trayTag(news)
        val open =
            Intent(context, MainActivity::class.java)
                .setAction(NotificationIntents.ACTION_OPEN)
                .putExtra(NotificationIntents.EXTRA_DESTINATION, NotificationDestination.FEED.name)
                .putExtra(NotificationIntents.EXTRA_KEY, centerId)
                .putExtra(NotificationIntents.EXTRA_MIFF_ID, news.miffId)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
        // One request code per author: a shared one would make the system rewrite the extras of the other authors' taps (extras are not part of what tells two intents apart).
        val tap =
            PendingIntent.getActivity(
                context,
                CIRCLE_NEWS_REQUEST_BASE + Math.floorMod(tag.hashCode(), CIRCLE_NEWS_REQUEST_RANGE),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, NotificationChannels.idOf(NotificationType.N9.family, quiet))
                .setSmallIcon(R.drawable.ic_stat_miff)
                .setColor(ACTION_BLUE)
                .setContentTitle(context.getString(R.string.notif_circle_update_title, news.authorFirstName))
                .setContentText(context.getString(R.string.notif_circle_update_body))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setShowWhen(false)
                .setContentIntent(tap)
                .build()
        // Tag = one per author: a new notification of the same author replaces the previous one (ticket 7, "Remplacement").
        manager.notify(tag, AndroidNotificationTray.TAG_ID, notification)
    }

    private companion object {
        /** Request codes of the taps of N9, above the ones of the other types (their ordinal). */
        const val CIRCLE_NEWS_REQUEST_BASE = 1000
        const val CIRCLE_NEWS_REQUEST_RANGE = 100_000

        /** The blue of the actions of the design system (colorActionPrimary), as the system wants a colour: an Int, not a Compose colour. */
        val ACTION_BLUE = 0xFF043CD1.toInt()
    }
}
