package com.tomessa.miff.domain.update

import com.tomessa.miff.domain.feed.MemberStatus
import com.tomessa.miff.domain.tracking.MemberStatusFacts
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.StatusColor
import com.tomessa.miff.domain.tracking.StatusLevel

/** What the bubble of encouragement says: nothing, "Tu es passé en orange…", or "Tu es passé en rouge…" (the texts of the PM's mockups, D21). */
enum class PromptLevel { NONE, ORANGE, RED }

/*
 * When the bubble of encouragement above the tab bar shows (ticket Update, D18, D19, D21, criteria A1, A2): a pure function of MY status in the Miff shown
 * by the Feed and of the clock. It reuses [StatusColor.level] (ONE calculation of the colour for the whole app) and adds the only rule of its own: in a
 * danger zone the colour stays red after an update (an update never brings back to green there, D8), so the bubble goes away when an update was published
 * AFTER the arrival (`lastUpdateAt >= presenceAt`, C8), not when the colour changes.
 */
object UpdatePrompt {
    fun level(
        status: MemberStatus?,
        sharing: Boolean,
        nowMillis: Long,
    ): PromptLevel {
        if (status == null || !sharing) return PromptLevel.NONE
        val facts = MemberStatusFacts(sharing, status.presence, status.presenceAtMillis, status.lastUpdateAtMillis)
        return when (StatusColor.level(facts, nowMillis)) {
            StatusLevel.ORANGE -> {
                PromptLevel.ORANGE
            }

            StatusLevel.RED -> {
                if (status.presence == PresenceValue.IN_RISK &&
                    updatedSinceArrival(status)
                ) {
                    PromptLevel.NONE
                } else {
                    PromptLevel.RED
                }
            }

            // Green or neutral: nothing to encourage (arriving in a trusted zone without publishing also ends the bubble, D18).
            StatusLevel.GREEN, StatusLevel.NEUTRAL -> {
                PromptLevel.NONE
            }
        }
    }

    private fun updatedSinceArrival(status: MemberStatus): Boolean {
        val update = status.lastUpdateAtMillis ?: return false
        val arrival = status.presenceAtMillis ?: return true
        return update >= arrival
    }
}
