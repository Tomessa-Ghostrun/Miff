package com.tomessa.miff.domain.notification

import com.tomessa.miff.domain.permission.PermissionSnapshot

/*
 * NOTIF-P0 (ticket docs/tickets/notifications.md, section 20): the notifications of Miff that need NO server (plan Spark, no Cloud Function).
 * Everything in this package is PURE (no Android class): the decisions are tested on the JVM and are the contract the iOS app copies. Nothing here
 * holds a position, an address or the name of a place: a notification is a type, a Miff identifier and a time (criterion N-P0-10).
 */

/** Where a tap on a notification leads (section 20.1, "Au toucher"): the Update tab for the reminders to me, the Feed for everything about my Miff. */
enum class NotificationDestination { UPDATE, FEED }

/**
 * The family of a notification: it decides the channel of the system (Android) and the section of Settings. A person who turned a channel off in the
 * phone settings turns off the whole family.
 */
enum class NotificationFamily {
    /** "Rappels pour toi": N1, N2 and N7, about my own state. */
    REMINDER,

    /** "Nouvelles de ta Miff": N3, N4 and N6, about the circle and the zones of the circle. */
    CIRCLE,
}

/**
 * The six notifications of the perimeter (N5 is abandoned, phase 2, and has NO value here: nothing to switch, nothing to promise). N7 (MODE VOYAGE, VOY-4A) is the end of
 * my own trip: it needs no Miff and no server, it is planned from the end of the trip alone.
 *
 * [quietAtNight]: decision Q30 of the PM, "nuit calme" 22 h to 7 h: N1, N3 and N6 arrive without sound or vibration, N2 and N4 (the red ones) keep their
 * normal importance.
 */
enum class NotificationType(
    val code: String,
    val family: NotificationFamily,
    val destination: NotificationDestination,
    val quietAtNight: Boolean,
) {
    N1("n1", NotificationFamily.REMINDER, NotificationDestination.UPDATE, quietAtNight = true),
    N2("n2", NotificationFamily.REMINDER, NotificationDestination.UPDATE, quietAtNight = false),
    N3("n3", NotificationFamily.CIRCLE, NotificationDestination.FEED, quietAtNight = true),
    N4("n4", NotificationFamily.CIRCLE, NotificationDestination.FEED, quietAtNight = false),
    N6("n6", NotificationFamily.CIRCLE, NotificationDestination.FEED, quietAtNight = true),

    /** MODE VOYAGE: "Ton mode voyage est terminé", 08:00 local the day after the last day. Never at night, so it has no quiet version. */
    N7("n7", NotificationFamily.REMINDER, NotificationDestination.FEED, quietAtNight = false),

    /**
     * LIEU-REQ: a member of my Miff asked to add a place. A line of the centre ONLY (the app is in front: there is no server to push to, ticket section 7):
     * never planned, never shown by the system, no switch in Settings. A tap leads to the list of the places of that Miff (the Profile), handled by the centre.
     */
    N8("n8", NotificationFamily.CIRCLE, NotificationDestination.FEED, quietAtNight = true),
    ;

    companion object {
        fun fromCode(code: String?): NotificationType? = entries.firstOrNull { it.code == code }

        /**
         * The notifications of the perimeter: the ones a phone can SHOW, with a switch in Settings and a channel of the system. [N8] is not one of them (a line of
         * the centre only), so every rule that is about showing, planning, switching or silencing a notification is written over THIS list, never over [entries].
         */
        val PERIMETER: List<NotificationType> = entries.filter { it != N8 }
    }
}

/**
 * What the person chose in Settings > Notifications (section 20.11). Local to the phone, erased at the sign-out. Every switch is ON by default; the
 * switches of the types keep their value when the general one is OFF (they are only greyed), so that turning it back on restores the choice.
 */
data class NotificationPreferences(
    val enabled: Boolean = true,
    val n1: Boolean = true,
    val n2: Boolean = true,
    val n3: Boolean = true,
    val n4: Boolean = true,
    val n6: Boolean = true,
    val n7: Boolean = true,
    val nightCalm: Boolean = true,
) {
    /** The switch of the type alone (what the screen draws, greyed when the general one is off). */
    fun switchOf(type: NotificationType): Boolean =
        when (type) {
            NotificationType.N1 -> n1

            NotificationType.N2 -> n2

            NotificationType.N3 -> n3

            NotificationType.N4 -> n4

            NotificationType.N6 -> n6

            NotificationType.N7 -> n7

            // In the centre only, no switch of its own: always on.
            NotificationType.N8 -> true
        }

    /** The type may be produced: the general switch AND its own are on. */
    fun allows(type: NotificationType): Boolean = enabled && switchOf(type)

    fun with(
        type: NotificationType,
        on: Boolean,
    ): NotificationPreferences =
        when (type) {
            NotificationType.N1 -> copy(n1 = on)
            NotificationType.N2 -> copy(n2 = on)
            NotificationType.N3 -> copy(n3 = on)
            NotificationType.N4 -> copy(n4 = on)
            NotificationType.N6 -> copy(n6 = on)
            NotificationType.N7 -> copy(n7 = on)
            NotificationType.N8 -> this
        }
}

/**
 * What the phone says about the notifications of Miff, as the screens speak of it (ticket section 8.2). One value, one wording, in Settings >
 * Notifications, Settings > Confidentialité and the centre.
 */
enum class NotificationPermissionState {
    /** The phone lets Miff notify (permission granted, or Android 12 and earlier with the notifications left on). */
    ALLOWED,

    /** Android 13+: the system window was never shown for Miff. The permission can still be asked once. */
    NOT_ASKED,

    /** Refused in the system window, or turned off in the settings of the phone. Only the settings of the phone can change it. */
    OFF,
    ;

    companion object {
        /**
         * [systemAsked]: the system window was launched at least once by Miff (a flag of the phone: the system itself does not tell "never asked" apart
         * from "refused for good"). Before Android 13 there is no window: a notification that is not allowed was turned off by the person.
         */
        fun resolve(
            snapshot: PermissionSnapshot,
            systemAsked: Boolean,
        ): NotificationPermissionState =
            when {
                snapshot.notifications -> ALLOWED
                snapshot.notificationsApplicable && !systemAsked -> NOT_ASKED
                else -> OFF
            }
    }
}

/** "Nuit calme" (Q30): 22 h to 7 h in the LOCAL time of the moment the notification is shown (a change of time zone or of summer time has no side effect). */
object NightCalm {
    const val START_HOUR = 22
    const val END_HOUR = 7

    fun isNight(localHour: Int): Boolean = localHour >= START_HOUR || localHour < END_HOUR

    /** Shown without sound or vibration? Only when the person kept "Nuit calme" on, at night, for a type that is quiet at night. */
    fun isQuiet(
        type: NotificationType,
        preferences: NotificationPreferences,
        localHour: Int,
    ): Boolean = preferences.nightCalm && type.quietAtNight && isNight(localHour)
}
