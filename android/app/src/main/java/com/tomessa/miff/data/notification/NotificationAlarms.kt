package com.tomessa.miff.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import com.tomessa.miff.domain.notification.NotificationType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * NOTIF-4A. The alarms of the reminders. INEXACT ON PURPOSE (ticket sections 5.7, 14, 20.5; criterion N-M8): `setAndAllowWhileIdle` never rings BEFORE the hour
 * asked, rings a little late at worst (Android 12 and later: within the hour, unless the battery saver says otherwise; in Doze at most once per nine minutes), and
 * needs no special permission. SCHEDULE_EXACT_ALARM is refused to new apps by default and USE_EXACT_ALARM is reserved by Google Play to alarm clocks and calendars:
 * neither is declared, ever, for this feature (NotificationPrivacyTest). A late reminder says nothing that a delay could make false: no hour, no duration.
 *
 * At most ONE alarm per type (so at most four in all): a new one for the same type REPLACES the old one. The alarms are erased by the system when the phone
 * restarts: the receiver of the restart plans them again from the snapshot ([NotificationScheduler.reconcile] with `force`).
 */

/** An alarm Miff asked the system for (what [NotificationAlarms.scheduled] remembers: the system itself cannot be asked). */
data class ScheduledAlarm(
    val key: String,
    val miffId: String,
    val dueAtMillis: Long,
) {
    override fun toString(): String = "ScheduledAlarm(redacted)"
}

/** What the planner asks of the alarms (a double in tests). */
interface NotificationAlarms {
    /** One alarm per [type]: replaces the previous one. */
    fun schedule(
        type: NotificationType,
        alarm: ScheduledAlarm,
    )

    /** Cancels the alarm of [type] (nothing if there is none). */
    fun cancel(type: NotificationType)

    fun cancelAll()

    /** What Miff asked and did not cancel or consume yet, AND that the system still holds (an alarm it lost is not listed: the planner asks for it again). */
    fun scheduled(): Map<NotificationType, ScheduledAlarm>

    /** The alarm of [type] has rung: it is no longer waiting. */
    fun consumed(type: NotificationType)
}

@Serializable
private class StoredAlarm(
    val key: String,
    val miffId: String,
    val dueAt: Long,
)

class AndroidNotificationAlarms(
    context: Context,
) : NotificationAlarms {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(AlarmManager::class.java)
    private val prefs = appContext.getSharedPreferences("miff_notification_alarms", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    /** Always the SAME intent for a type (action, data, component): the system tells it apart by these, the extras only carry what the receiver checks. */
    private fun intentFor(type: NotificationType) =
        Intent(appContext, NotificationAlarmReceiver::class.java)
            .setAction(NotificationAlarmReceiver.ACTION_FIRE)
            .setData(Uri.parse("miff-notification://alarm/${type.code}"))

    override fun schedule(
        type: NotificationType,
        alarm: ScheduledAlarm,
    ) = synchronized(lock) {
        val intent =
            intentFor(type)
                .putExtra(NotificationAlarmReceiver.EXTRA_TYPE, type.code)
                .putExtra(NotificationAlarmReceiver.EXTRA_KEY, alarm.key)
                .putExtra(NotificationAlarmReceiver.EXTRA_MIFF, alarm.miffId)
                .putExtra(NotificationAlarmReceiver.EXTRA_DUE, alarm.dueAtMillis)
        val pending =
            PendingIntent.getBroadcast(
                appContext,
                type.ordinal,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        // Inexact and allowed in Doze: see the header. RTC_WAKEUP: the phone may be asleep.
        manager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.dueAtMillis, pending)
        write(read() + (type.code to StoredAlarm(alarm.key, alarm.miffId, alarm.dueAtMillis)))
    }

    override fun cancel(type: NotificationType) =
        synchronized(lock) {
            PendingIntent
                .getBroadcast(
                    appContext,
                    type.ordinal,
                    intentFor(type),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )?.let {
                    manager?.cancel(it)
                    it.cancel()
                }
            val stored = read()
            if (type.code in stored) write(stored - type.code)
        }

    override fun cancelAll() {
        NotificationType.PERIMETER.forEach(::cancel)
        synchronized(lock) { prefs.edit(commit = true) { clear() } }
    }

    override fun scheduled(): Map<NotificationType, ScheduledAlarm> =
        synchronized(lock) {
            read()
                .mapNotNull { (code, a) ->
                    NotificationType
                        .fromCode(code)
                        ?.takeIf { isHeldBySystem(it) }
                        ?.let { it to ScheduledAlarm(a.key, a.miffId, a.dueAt) }
                }.toMap()
        }

    /**
     * Does the system still hold the alarm of [type]? A phone maker's cleaner (ColorOS) or a forced stop erases alarms and their PendingIntent without telling the
     * app: the PendingIntent is looked for WITHOUT creating it (FLAG_NO_CREATE); absent, the alarm is gone and the next reconcile (every return to the front) asks again.
     */
    private fun isHeldBySystem(type: NotificationType): Boolean =
        PendingIntent.getBroadcast(
            appContext,
            type.ordinal,
            intentFor(type),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) != null

    override fun consumed(type: NotificationType) =
        synchronized(lock) {
            val stored = read()
            if (type.code in stored) write(stored - type.code)
        }

    private fun read(): Map<String, StoredAlarm> =
        prefs
            .getString(KEY, null)
            ?.let { raw ->
                runCatching { json.decodeFromString<Map<String, StoredAlarm>>(raw) }.getOrNull()
            }.orEmpty()

    private fun write(alarms: Map<String, StoredAlarm>) {
        prefs.edit(commit = true) { putString(KEY, json.encodeToString(alarms)) }
    }

    private companion object {
        const val KEY = "alarms"
    }
}
