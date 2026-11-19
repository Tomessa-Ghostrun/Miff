package com.tomessa.miff.domain.alert

/**
 * What the transaction that STARTS an alert decides from what it read (my card, my alert document, my daily counter), as a pure function: the order of the checks matters and is
 * tested. It never writes anything itself.
 *
 * THE ORDER (review of #275, M1): an alert of mine that is ALREADY in progress comes before the daily limit. Scenario: the 3rd start of the day was accepted but the answer was lost
 * (timeout, app killed); at the retry the counter is full. Asking the limit first would tell the author "limit reached" while her circle already sees the red card, and she would
 * have no banner and no way to end it for 7 days. So a live alert is ADOPTED first; the limit is only judged before a new credit would be written.
 */
sealed interface StartDecision {
    data object NotMember : StartDecision

    data object SharingStopped : StartDecision

    /** My alert is already in progress in that Miff: nothing is written, it is adopted (banner, end). */
    class Adopt(
        val doc: AlertDoc,
    ) : StartDecision

    /** The previous start is less than 60 s old: the rules would refuse a re-arming; wait [waitMillis] and try again. Consumes nothing. */
    class Wait(
        val waitMillis: Long,
    ) : StartDecision

    /** The three starts of the last 24 hours are used: nothing is tried. */
    data object QuotaFull : StartDecision

    /** Write the credit (create the counter, or replace [slot]) and the alert with [seq]. */
    class Write(
        val slot: Int?,
        val seq: Long,
    ) : StartDecision
}

object AlertStartPlan {
    fun decide(
        cardExists: Boolean,
        sharing: Boolean,
        alertExists: Boolean,
        alert: AlertDoc?,
        counterExists: Boolean,
        quota: AlertQuota,
        nowMillis: Long,
    ): StartDecision {
        if (!cardExists) return StartDecision.NotMember
        if (!sharing) return StartDecision.SharingStopped
        if (alert != null && alert.isLive(nowMillis)) return StartDecision.Adopt(alert)
        val startedAt = alert?.startedAtMillis
        if (startedAt != null && nowMillis - startedAt in 0 until AlertRules.REARM_MIN_GAP_MILLIS) {
            return StartDecision.Wait(
                AlertRules.REARM_MIN_GAP_MILLIS - (nowMillis - startedAt) + AlertRules.REARM_SAFETY_MILLIS,
            )
        }
        val slot = quota.slotToReplace(nowMillis)
        // A missing counter is never "full": its creation is the credit of the first start.
        if (slot == null && counterExists) return StartDecision.QuotaFull
        val seq = if (alertExists) (alert?.seq ?: 0L) + 1L else 1L
        return StartDecision.Write(if (counterExists) slot else null, seq)
    }
}
