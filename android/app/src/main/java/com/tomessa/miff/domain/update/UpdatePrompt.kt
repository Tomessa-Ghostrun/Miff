package com.tomessa.miff.domain.update

import com.tomessa.miff.domain.feed.MemberStatus
import com.tomessa.miff.domain.tracking.MemberStatusFacts
import com.tomessa.miff.domain.tracking.StatusColor
import com.tomessa.miff.domain.tracking.StatusLevel

/** What the bubble of encouragement says: nothing, "Tu es passé en orange…", or "Tu es passé en rouge…" (the texts of the PM's mockups, D21). */
enum class PromptLevel { NONE, ORANGE, RED }

/*
 * When the bubble of encouragement above the tab bar shows (ticket Update, D18, D19, D21, criteria A1, A2): a pure function of MY status in the Miff shown
 * by the Feed and of the clock. It is the colour, nothing else: [StatusColor.level] (ONE calculation of the colour for the whole app), orange or red.
 * Decision of the PM, 2026-11-08: in a danger zone the colour is red only while the last update is more than an hour old, so a published update ends
 * the red (and the bubble) there too, and the bubble comes back an hour later if nothing new is published.
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
            StatusLevel.ORANGE -> PromptLevel.ORANGE

            StatusLevel.RED -> PromptLevel.RED

            // Green or neutral: nothing to encourage (arriving in a trusted zone without publishing also ends the bubble, D18).
            StatusLevel.GREEN, StatusLevel.NEUTRAL -> PromptLevel.NONE
        }
    }
}
