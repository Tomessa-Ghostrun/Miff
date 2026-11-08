package com.tomessa.miff.domain.tracking

/*
 * Pure function of the colour of a member (ticket 6.9, criteria STA6, STA9). Nothing is read, nothing is written: the colour is computed
 * by the phone of the person who LOOKS, with the time of the moment; it is never stored in Firestore. The Feed (a later ticket) calls it; the
 * same function and the same constants are meant to be written identically on iOS (the cases of the tests are the contract).
 */

enum class StatusLevel { NEUTRAL, GREEN, ORANGE, RED }

/**
 * The thresholds of the ticket Statut, in ONE place (criterion STA9): nobody else writes an hour count for a colour. Decision of the PM, 2026-11-08
 * (it replaces 1 h / 2 h): a member who LEFT a trusted zone turns orange from 2 h and red past 4 h, counted from the most recent of the departure
 * and the last update.
 */
object StatusThresholds {
    const val ORANGE_AFTER_MILLIS = 2L * 60L * 60L * 1000L
    const val RED_AFTER_MILLIS = 4L * 60L * 60L * 1000L

    /**
     * The rule of the danger zone (decision of the PM, 2026-11-08): in a danger zone, a last update OLDER than this (strictly more than one hour, or no
     * update at all) makes the person red. An update at most this old does not force red: the colour then follows the normal rule of the age.
     * A trusted zone always wins over a danger zone (green).
     */
    const val DANGER_ZONE_UPDATE_AGE_MILLIS = 60L * 60L * 1000L
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
     * The order of the ticket: sharing stopped or no status = neutral; a trusted zone = green; else a danger zone = red when the last update is
     * older than [StatusThresholds.DANGER_ZONE_UPDATE_AGE_MILLIS] (or missing), otherwise the normal rule below; `unknown` = neutral (a person who
     * never went through a trusted zone, decision N4); else the age of the reference = the most recent of the departure and the last update
     * (decision N2): under 2 h green, from 2 h to 4 h INCLUDED orange, over 4 h red. A reference in the future (a phone with a wrong clock) counts
     * as "now": green.
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
                val update = facts.lastUpdateAtMillis
                if (update == null || nowMillis - update > StatusThresholds.DANGER_ZONE_UPDATE_AGE_MILLIS) {
                    StatusLevel.RED
                } else {
                    byAge(facts, nowMillis)
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
            elapsed < StatusThresholds.ORANGE_AFTER_MILLIS -> StatusLevel.GREEN
            elapsed <= StatusThresholds.RED_AFTER_MILLIS -> StatusLevel.ORANGE
            else -> StatusLevel.RED
        }
    }
}
