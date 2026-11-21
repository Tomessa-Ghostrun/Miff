package com.tomessa.miff.domain.paywall

/**
 * The yellow label "presque 30 % d'économie" is a sentence written once (decision of the PM, 2026-11-07), but the prices come from the store and can change (a promotion, another
 * country, another currency step): the label is only shown while it stays true. The saving is computed from the store's own amounts: `1 - annual / (12 x monthly)`, in the same currency.
 * Below [MIN_SHOWN] (25 %) the label is hidden (never an inexact advertising claim, Apple 2.3.1(a)); the text itself is unchanged.
 */
object PaywallSavings {
    const val MIN_SHOWN = 0.25

    fun saving(
        annual: PaywallPrice,
        monthly: PaywallPrice,
    ): Double? {
        if (annual.currencyCode != monthly.currencyCode || annual.micros <= 0L || monthly.micros <= 0L) return null
        return 1.0 - annual.micros.toDouble() / (MONTHS_PER_YEAR * monthly.micros.toDouble())
    }

    fun isWorthShowing(
        annual: PaywallPrice,
        monthly: PaywallPrice,
    ): Boolean = (saving(annual, monthly) ?: return false) >= MIN_SHOWN

    private const val MONTHS_PER_YEAR = 12.0
}
