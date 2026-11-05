package com.tomessa.miff.domain.tracking

/*
 * Pure function of the colour of a member (ticket 6.9, criteria STA6, STA9). Nothing is read, nothing is written: the colour is computed
 * by the phone of the person who LOOKS, with the time of the moment; it is never stored in Firestore. The Feed (a later ticket) calls it; the
 * same function and the same two constants are meant to be written identically on iOS (the cases of the tests are the contract).
 */

enum class StatusLevel { NEUTRAL, GREEN, ORANGE, RED }

/** The two thresholds of the ticket Statut, in ONE place (criterion STA9): the Feed must never write 1 h or 2 h again. */
object StatusThresholds {
    const val ORANGE_AFTER_MILLIS = 60L * 60L * 1000L
    const val RED_AFTER_MILLIS = 2L * 60L * 60L * 1000L
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
     * The order of the ticket: sharing stopped or no status = neutral; a trusted zone = green; else a danger zone = red; `unknown` = neutral
     * (a person who never went through a trusted zone, decision N4); else the age of the reference = the most recent of the departure and the last
     * update (decision N2): under 1 h green, from 1 h to 2 h INCLUDED orange, over 2 h red. A reference in the future (a phone with a wrong
     * clock) counts as "now": green.
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
                StatusLevel.RED
            }

            PresenceValue.UNKNOWN -> {
                StatusLevel.NEUTRAL
            }

            PresenceValue.OUT -> {
                val reference =
                    listOfNotNull(facts.presenceAtMillis, facts.lastUpdateAtMillis).maxOrNull()
                        ?: return StatusLevel.NEUTRAL
                val elapsed = nowMillis - reference
                when {
                    elapsed < StatusThresholds.ORANGE_AFTER_MILLIS -> StatusLevel.GREEN
                    elapsed <= StatusThresholds.RED_AFTER_MILLIS -> StatusLevel.ORANGE
                    else -> StatusLevel.RED
                }
            }
        }
    }
}
