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
}
