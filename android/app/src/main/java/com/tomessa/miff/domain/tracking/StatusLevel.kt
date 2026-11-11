package com.tomessa.miff.domain.tracking

/*
 * Pure function of the colour of a member (ticket 6.9, criteria STA6, STA9). Nothing is read, nothing is written: the colour is computed
 * by the phone of the person who LOOKS, with the time of the moment; it is never stored in Firestore. The Feed (a later ticket) calls it; the
 * same function and the same constants are meant to be written identically on iOS (the cases of the tests are the contract).
 */

enum class StatusLevel { NEUTRAL, GREEN, ORANGE, RED }

/**
 * The thresholds of the ticket Statut, in ONE place (criterion STA9): nobody else writes an hour count for a colour. Decision of the PM, 2026-11-08
 * (it replaces 1 h / 2 h): a member who LEFT a trusted zone turns orange past 2 h and red past 4 h (strictly "more than"), counted from the most recent of the departure
 * and the last update.
 */
object StatusThresholds {
    const val ORANGE_AFTER_MILLIS = 2L * 60L * 60L * 1000L
    const val RED_AFTER_MILLIS = 4L * 60L * 60L * 1000L

    /**
     * The rule of the danger zone (decision of the PM, 2026-11-08, which replaces D8 and the rule "red only after an hour without update"): entering a
     * danger zone makes the person red AT ONCE; an update published AFTER the entry makes her green for this long (up to one hour INCLUDED), then red
     * again (strictly more than one hour after THAT update) unless she publishes another one. No orange there. A trusted zone always wins (green).
     */
    const val DANGER_ZONE_RED_AFTER_MILLIS = 60L * 60L * 1000L
}

/** What the colour needs from a member's document, nothing else. Times are epoch milliseconds; null when the field is missing. */
class MemberStatusFacts(
    val sharingEnabled: Boolean,
    val presence: PresenceValue?,
    val presenceAtMillis: Long?,
    val lastUpdateAtMillis: Long?,
    /** MODE VOYAGE: the end of the trip when [presence] is `travel` (the colour does not need it: a trip is always neutral; the Feed does). */
    val travelUntilMillis: Long? = null,
) {
    override fun toString(): String = "MemberStatusFacts(redacted)"
}

object StatusColor {
    /**
     * The order of the ticket: sharing stopped or no status = neutral; a trusted zone = green; else a danger zone = red, except green while an update
     * POSTERIOR to the entry (`presenceAt`, strictly) is at most [StatusThresholds.DANGER_ZONE_RED_AFTER_MILLIS] old (an update at the very instant of the
     * entry, an older one, a missing one or a missing entry time do not count); `unknown` = neutral (a person who
     * never went through a trusted zone, decision N4); else the age of the reference = the most recent of the departure and the last update
     * (decision N2): up to 2 h INCLUDED green, over 2 h up to 4 h INCLUDED
     * orange, over 4 h red ("plus de": at 2:00 sharp still green). A reference in the future (a phone with a wrong clock) counts as "now": green.
     */
    fun level(
        facts: MemberStatusFacts?,
        nowMillis: Long,
    ): StatusLevel {
        if (facts == null || !facts.sharingEnabled) return StatusLevel.NEUTRAL
        return when (facts.presence) {
            null -> {
                StatusLevel.NEUTRAL
            }

            PresenceValue.IN_TRUSTED -> {
                StatusLevel.GREEN
            }

            PresenceValue.IN_RISK -> {
                val entry = facts.presenceAtMillis
                val update = facts.lastUpdateAtMillis
                // A last update still waiting for the server (null) counts as none; a wrong clock in the future counts as "now".
                if (entry != null && update != null && update > entry &&
                    nowMillis - update <= StatusThresholds.DANGER_ZONE_RED_AFTER_MILLIS
                ) {
                    StatusLevel.GREEN
                } else {
                    StatusLevel.RED
                }
            }

            PresenceValue.UNKNOWN -> {
                StatusLevel.NEUTRAL
            }

            // MODE VOYAGE: a traveller is ignored in the colour of the Miff, before AND after the end of the trip (after it, the card says "Voyage terminé"
            // until the first real measure: never a colour drawn from a state that is no longer true).
            PresenceValue.TRAVEL -> {
                StatusLevel.NEUTRAL
            }

            PresenceValue.OUT -> {
                byAge(facts, nowMillis)
            }
        }
    }

    /** The normal rule: the age of the most recent of the departure (or arrival) and the last update. */
    private fun byAge(
        facts: MemberStatusFacts,
        nowMillis: Long,
    ): StatusLevel {
        val reference =
            listOfNotNull(facts.presenceAtMillis, facts.lastUpdateAtMillis).maxOrNull()
                ?: return StatusLevel.NEUTRAL
        val elapsed = nowMillis - reference
        return when {
            elapsed <= StatusThresholds.ORANGE_AFTER_MILLIS -> StatusLevel.GREEN
            elapsed <= StatusThresholds.RED_AFTER_MILLIS -> StatusLevel.ORANGE
            else -> StatusLevel.RED
        }
    }

    /**
     * The instants at which the level of a member CAN change with the facts known now (NOTIF-3A, ticket notifications.md section 20.2): the planner of the
     * notifications asks "when will this person turn orange, when red?" without ever writing an hour count of its own (criterion STA9). Same file, same
     * constants as [level], to be written identically on iOS. Ascending, without doubles; empty when the level never changes (no sharing, no status, a trusted
     * zone, `unknown`, or red from the start, which is the danger zone without an update after the entry).
     *
     * CONTRACT (tested on every shape of facts): at each instant `t` returned, `level(t - 1 ms) != level(t)`, and the level is constant between two
     * consecutive instants (and before the first, and after the last). It holds by construction: the candidates (the thresholds of the age counted from the
     * reference, and the end of the hour that follows an update made after the entry in a danger zone) are kept only when the level really changes there.
     */
    fun changeInstants(facts: MemberStatusFacts?): List<Long> {
        if (facts == null || !facts.sharingEnabled) return emptyList()
        if (facts.presence != PresenceValue.OUT && facts.presence != PresenceValue.IN_RISK) return emptyList()
        val candidates = mutableListOf<Long>()
        if (facts.presence == PresenceValue.OUT) {
            listOfNotNull(facts.presenceAtMillis, facts.lastUpdateAtMillis).maxOrNull()?.let { reference ->
                safeAfter(reference, StatusThresholds.ORANGE_AFTER_MILLIS)?.let(candidates::add)
                safeAfter(reference, StatusThresholds.RED_AFTER_MILLIS)?.let(candidates::add)
            }
        } else {
            // Danger zone (rule D47): red at the entry; an update POSTERIOR to the entry makes it green for an hour, then red again. The only instant is the end of
            // that hour, and it exists only when such an update exists (otherwise the person is red from the start: no instant).
            val entry = facts.presenceAtMillis
            val update = facts.lastUpdateAtMillis
            if (entry != null && update != null && update > entry) {
                safeAfter(update, StatusThresholds.DANGER_ZONE_RED_AFTER_MILLIS)?.let(candidates::add)
            }
        }
        return candidates.distinct().sorted().filter { level(facts, it - 1) != level(facts, it) }
    }

    /** The first millisecond AFTER `reference + duration` (the rules say "more than"); null for a reference so absurd that the sum would overflow. */
    private fun safeAfter(
        reference: Long,
        durationMillis: Long,
    ): Long? = if (reference in 0..(Long.MAX_VALUE / 4)) reference + durationMillis + 1 else null
}
