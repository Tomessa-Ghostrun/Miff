package com.tomessa.miff.domain.notification

/*
 * NOTIF-3A: WHAT to do with a notification that is due (ticket section 20.3, decision Q30 of the PM, section 20.8): show it, write it to the centre
 * only, or drop it. Pure: the clock, the counters and the state of the app are given. Nothing here knows a position, a place or a name.
 */

/** What to do with a notification that is due. */
sealed interface Delivery {
    /** Show it in the tray of the system AND write it to the centre. [quiet]: without sound or vibration (the quiet channel, "Nuit calme"). */
    class Show(
        val quiet: Boolean,
    ) : Delivery {
        override fun toString(): String = "Show(quiet=$quiet)"
    }

    /** Only a line of the centre (marked [ItemState.SILENT]): the app is in front, the notification is late, or the daily cap is reached. */
    data object Silent : Delivery

    /** Nothing at all: already notified, or the switch is off. */
    data object Drop : Delivery
}

/** What was shown today (the local day), to hold the caps. Only the notifications the system SHOWED count; a silent line of the centre does not. */
data class DailyCounters(
    /** The local day, `yyyy-MM-dd`. Counters of another day are worth zero. */
    val day: String,
    val perType: Map<NotificationType, Int> = emptyMap(),
) {
    fun count(type: NotificationType): Int = perType[type] ?: 0

    /** N1 to N4 together; N6 has its own cap and is OUTSIDE the total (decision Q30), N7 (once per trip, key `n7|uid|end`) has no cap at all (ticket mode voyage 9.1). */
    val total: Int get() = perType.filterKeys { it != NotificationType.N6 && it != NotificationType.N7 }.values.sum()

    fun canShow(type: NotificationType): Boolean =
        if (type == NotificationType.N7) {
            true
        } else if (type == NotificationType.N6) {
            count(type) < NotificationPolicy.MAX_N6_PER_DAY
        } else {
            count(type) < NotificationPolicy.MAX_PER_TYPE_PER_DAY && total < NotificationPolicy.MAX_TOTAL_PER_DAY
        }

    fun afterShown(type: NotificationType): DailyCounters = copy(perType = perType + (type to count(type) + 1))

    /** The counters of [dayNow]: those of an older day are forgotten (a new local day starts from zero). */
    fun forDay(dayNow: String): DailyCounters = if (day == dayNow) this else DailyCounters(dayNow)
}

object NotificationDelivery {
    /**
     * The decision, in this order (each rule is a case of the ticket):
     * 1. already notified for this key (it is never notified twice, a restart or a change of time must not repeat it): drop;
     * 2. the type or the general switch is off: drop (a switch cancels at once);
     * 3. the app is in front: only the centre (the bubble of the Feed already speaks, section 20.3, "Foreground");
     * 4. late by more than 15 minutes (restart, deferred work, late alarm): only the centre (section 20.8);
     * 5. the daily cap is reached: only the centre (decision Q30);
     * 6. otherwise show it, quietly at night for N1, N3 and N6 (22 h to 7 h, local time NOW), normally for N2 and N4.
     */
    fun decide(
        type: NotificationType,
        preferences: NotificationPreferences,
        counters: DailyCounters,
        localHour: Int,
        appInForeground: Boolean,
        latenessMillis: Long,
        alreadyNotified: Boolean,
    ): Delivery =
        when {
            alreadyNotified -> Delivery.Drop
            !preferences.allows(type) -> Delivery.Drop
            appInForeground -> Delivery.Silent
            latenessMillis > NotificationPolicy.CATCH_UP_MAX_LATENESS_MILLIS -> Delivery.Silent
            !counters.canShow(type) -> Delivery.Silent
            else -> Delivery.Show(quiet = NightCalm.isQuiet(type, preferences, localHour))
        }
}

/** The keys already notified, kept 48 hours (section 20.2): a key is never notified twice. Pure functions over a map key -> time of the notification. */
object NotifiedKeys {
    fun contains(
        keys: Map<String, Long>,
        key: String,
        nowMillis: Long,
    ): Boolean = keys[key]?.let { nowMillis - it <= NotificationPolicy.KEEP_KEYS_MILLIS } == true

    fun add(
        keys: Map<String, Long>,
        key: String,
        nowMillis: Long,
    ): Map<String, Long> = prune(keys + (key to nowMillis), nowMillis)

    /** Forgets what is older than 48 hours. */
    fun prune(
        keys: Map<String, Long>,
        nowMillis: Long,
    ): Map<String, Long> = keys.filterValues { nowMillis - it <= NotificationPolicy.KEEP_KEYS_MILLIS }
}
