package com.tomessa.miff.domain.notification

import com.tomessa.miff.domain.feed.FeedBuilder
import com.tomessa.miff.domain.tracking.MemberStatusFacts
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.StatusColor
import com.tomessa.miff.domain.tracking.StatusLevel

/*
 * NOTIF-3A: the PLANNER of the notifications N1 to N4 (ticket docs/tickets/notifications.md, sections 20.2, 20.3, 20.8, 20.9). A pure function of the
 * facts the phone already knows: nothing is read, nothing is written, nothing is asked of a server. It says WHICH notifications must be waiting and at
 * which instant; the data layer (alarms) applies it, and the same function is called again at every event that can change it (reconcile). It never holds a
 * position, a place, an address or a name: only identifiers of a Miff and of members, and times (criterion N-P0-10).
 *
 * The colours come from [StatusColor] and nothing else: no hour count is written here (criterion STA9). Every duration below is a named constant.
 */

/** "Je suis responsable" (N3 / N4, decision Q28 of the PM, default LARGE: one line to change). */
enum class ResponsibilityMode {
    /** Responsible = my colour IS the colour of the Miff (I contribute to it: I already got N1 or N2 for this passage). */
    LARGE,

    /** Responsible = my colour is the colour of the Miff AND without me the Miff would have another colour (I am the only cause). */
    STRICT,
}

object NotificationPolicy {
    /** The decision Q28 of the PM (2026-11-08): LARGE. */
    val RESPONSIBILITY: ResponsibilityMode = ResponsibilityMode.LARGE

    /** N3 / N4 leave 30 minutes after the Miff turned orange / red (decision of the PM, section 20.1). */
    const val CIRCLE_DELAY_MILLIS = 30L * 60L * 1000L

    /** A notification that is due but late by more than this (restart, deferred work, late alarm) is only written to the centre (section 20.8). */
    const val CATCH_UP_MAX_LATENESS_MILLIS = 15L * 60L * 1000L

    /** A key already notified is never notified again; it is remembered this long (section 20.2). */
    const val KEEP_KEYS_MILLIS = 48L * 60L * 60L * 1000L

    /** N6: a danger zone left and entered again within this time (a geofence that oscillates) gives ONE notification (section 20.7). */
    const val N6_REARM_MILLIS = 60L * 60L * 1000L

    /** Daily caps of the local day (decision Q30): per type and in all, for N1 to N4; N6 has its own, outside the total. */
    const val MAX_PER_TYPE_PER_DAY = 2
    const val MAX_TOTAL_PER_DAY = 6
    const val MAX_N6_PER_DAY = 3
}

/** What the planner needs, nothing else. */
class PlanInput(
    val nowMillis: Long,
    /** The Miff shown by the Feed (M): N1 to N4 concern it alone. Null: no active Miff. */
    val miffId: String?,
    val myUid: String,
    /** The facts of every member of M the phone knows (me included), by uid. A member with no status is absent. */
    val members: Map<String, MemberStatusFacts>,
    val preferences: NotificationPreferences,
    /** The phone lets Miff notify (the state of the permission is ALLOWED). */
    val permissionAllowed: Boolean,
    /** Somebody is signed in on this phone with the session of this account (checked on the phone, no read). */
    val sessionValid: Boolean,
    val mode: ResponsibilityMode = NotificationPolicy.RESPONSIBILITY,
) {
    override fun toString(): String = "PlanInput(redacted)"
}

/** A notification to leave at [dueAtMillis] (a past instant = a catch-up). [key] is its key of deduplication. */
data class PlannedNotification(
    val type: NotificationType,
    val key: String,
    val dueAtMillis: Long,
    val miffId: String,
) {
    override fun toString(): String = "PlannedNotification(redacted)"
}

object NotificationPlanner {
    /**
     * The notifications that must be waiting (or caught up), sorted by instant, at most one per type (so at most 4 in all). IDEMPOTENT: the same input
     * gives the same list. Empty when nothing may be notified (no session, no active Miff, the general switch off, the permission refused, my sharing
     * stopped or my agreement waited for).
     */
    fun plan(input: PlanInput): List<PlannedNotification> {
        val miffId = input.miffId ?: return emptyList()
        if (!input.sessionValid || !input.permissionAllowed || !input.preferences.enabled) return emptyList()
        val mine = input.members[input.myUid] ?: return emptyList()
        // "Mon partage arrêté ou mon accord en attente": my status is not shared (the facts say so) = nothing is notified, to me or about the Miff.
        if (!mine.sharingEnabled) return emptyList()
        val planned = mutableListOf<PlannedNotification>()
        planMine(input, miffId, mine, planned)
        planMiff(input, miffId, planned)
        return planned.sortedWith(compareBy({ it.dueAtMillis }, { it.type.ordinal }))
    }

    // --- N1 and N2: me, hors zone de danger ---------------------------------------------------------------------------------------------

    private fun planMine(
        input: PlanInput,
        miffId: String,
        mine: MemberStatusFacts,
        out: MutableList<PlannedNotification>,
    ) {
        // Only a person who LEFT a trusted zone is reminded: never in a danger zone (decision Q27), never in a trusted zone, never with no departure.
        if (mine.presence != PresenceValue.OUT) return
        val reference = referenceOf(mine) ?: return
        val now = input.nowMillis
        val instants = StatusColor.changeInstants(mine)
        val levelNow = StatusColor.level(mine, now)
        val orangeAt = instants.firstOrNull { StatusColor.level(mine, it) == StatusLevel.ORANGE }
        val redAt = instants.firstOrNull { StatusColor.level(mine, it) == StatusLevel.RED }
        if (input.preferences.allows(NotificationType.N1) && orangeAt != null) {
            // Future: waits. Past: caught up only while I am STILL orange (already red: N2 takes over; green again: nothing to remind).
            if (orangeAt > now || levelNow == StatusLevel.ORANGE) {
                out +=
                    PlannedNotification(
                        NotificationType.N1,
                        keyMine("n1", miffId, input.myUid, reference),
                        orangeAt,
                        miffId,
                    )
            }
        }
        if (input.preferences.allows(NotificationType.N2) && redAt != null) {
            if (redAt > now || levelNow == StatusLevel.RED) {
                out +=
                    PlannedNotification(
                        NotificationType.N2,
                        keyMine("n2", miffId, input.myUid, reference),
                        redAt,
                        miffId,
                    )
            }
        }
    }

    private fun keyMine(
        code: String,
        miffId: String,
        uid: String,
        reference: Long,
    ) = "$code|$miffId|$uid|$reference"

    /** The reference of the colour of a member: the most recent of the departure and the last update (decision N2 of the Statut ticket). */
    fun referenceOf(facts: MemberStatusFacts): Long? =
        listOfNotNull(facts.presenceAtMillis, facts.lastUpdateAtMillis).maxOrNull()

    // --- N3 and N4: the Miff changed colour, 30 minutes ago, and it is not me --------------------------------------------------------------------

    private fun planMiff(
        input: PlanInput,
        miffId: String,
        out: MutableList<PlannedNotification>,
    ) {
        val now = input.nowMillis
        val everyInstant =
            input.members.values
                .flatMap { StatusColor.changeInstants(it) }
                .distinct()
                .sorted()
        for ((type, color) in listOf(
            NotificationType.N3 to StatusLevel.ORANGE,
            NotificationType.N4 to StatusLevel.RED,
        )) {
            if (!input.preferences.allows(type)) continue
            val start = episodeStart(input, color, everyInstant, now) ?: continue
            val due = start + NotificationPolicy.CIRCLE_DELAY_MILLIS
            // Projected at the moment it will leave (or, for a catch-up, now): the Miff has that colour then, and I am not the one who made it.
            val judged = if (due > now) due else now
            if (miffLevelAt(input, judged) != color) continue
            if (responsibleAt(input, judged)) continue
            out += PlannedNotification(type, "${type.code}|$miffId|$start", due, miffId)
        }
    }

    /**
     * The start of the episode of the Miff in [color] (section 20.2): when the Miff is in that colour NOW, the instant it entered it (the latest one
     * not after now); when it is not, the next instant it will enter it. Null when it never is (with these facts) or when there is no instant to
     * start from.
     */
    private fun episodeStart(
        input: PlanInput,
        color: StatusLevel,
        everyInstant: List<Long>,
        now: Long,
    ): Long? {
        val entries = everyInstant.filter { miffLevelAt(input, it) == color && miffLevelAt(input, it - 1) != color }
        return if (miffLevelAt(input, now) == color) {
            entries.lastOrNull { it <= now } ?: constantRedStart(input, color)
        } else {
            entries.firstOrNull { it > now }
        }
    }

    /**
     * The Miff has been in that colour "since always" for these facts: only a member in a danger zone (red with no instant of change, the rule of the code
     * delivered today) can do that. The episode then started when that member ENTERED the zone. (When the rule of the danger zone becomes "red after one
     * hour", `changeInstants` returns an instant for it and this fallback is never used.)
     */
    private fun constantRedStart(
        input: PlanInput,
        color: StatusLevel,
    ): Long? {
        if (color != StatusLevel.RED) return null
        return input.members.values
            .filter {
                StatusColor.level(
                    it,
                    input.nowMillis,
                ) == StatusLevel.RED && it.presence == PresenceValue.IN_RISK
            }.mapNotNull { it.presenceAtMillis }
            .minOrNull()
    }

    fun miffLevelAt(
        input: PlanInput,
        atMillis: Long,
    ): StatusLevel = FeedBuilder.miffLevel(input.members.values.map { StatusColor.level(it, atMillis) })

    /** Am I responsible for the colour the Miff has at [atMillis]? (Section 20.2, decision Q28.) Alone in the Miff: always. */
    fun responsibleAt(
        input: PlanInput,
        atMillis: Long,
    ): Boolean {
        val mine = input.members[input.myUid] ?: return false
        val miff = miffLevelAt(input, atMillis)
        val me = StatusColor.level(mine, atMillis)
        if (me != miff) return false
        return when (input.mode) {
            ResponsibilityMode.LARGE -> {
                true
            }

            ResponsibilityMode.STRICT -> {
                val others =
                    input.members.filterKeys { it != input.myUid }.values.map {
                        StatusColor.level(
                            it,
                            atMillis,
                        )
                    }
                FeedBuilder.miffLevel(others) != miff
            }
        }
    }

    // --- At the moment a notification is due ------------------------------------------------------------------------------------------------------

    /**
     * Is the notification STILL true now, with the facts known now? Asked when an alarm rings (Android recomputes before showing: a level already passed
     * shows nothing, an update made meanwhile shows nothing). For N3 and N4 the facts must be FRESH (verified on the server by the caller, section 20.5).
     */
    fun stillTrue(
        type: NotificationType,
        input: PlanInput,
    ): Boolean {
        if (input.miffId == null || !input.sessionValid || !input.permissionAllowed ||
            !input.preferences.allows(type)
        ) {
            return false
        }
        val mine = input.members[input.myUid] ?: return false
        if (!mine.sharingEnabled) return false
        val now = input.nowMillis
        return when (type) {
            NotificationType.N1 -> {
                mine.presence == PresenceValue.OUT &&
                    StatusColor.level(mine, now) == StatusLevel.ORANGE
            }

            NotificationType.N2 -> {
                mine.presence == PresenceValue.OUT && StatusColor.level(mine, now) == StatusLevel.RED
            }

            NotificationType.N3 -> {
                miffLevelAt(input, now) == StatusLevel.ORANGE && !responsibleAt(input, now)
            }

            NotificationType.N4 -> {
                miffLevelAt(input, now) == StatusLevel.RED && !responsibleAt(input, now)
            }

            NotificationType.N6 -> {
                false
            }
        }
    }
}
