package com.tomessa.miff.domain.paywall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "presque 30 % d'économie" is only shown while the saving computed from the store's own prices stays true (>= 25 %). */
class PaywallSavingsTest {
    private fun price(
        micros: Long,
        currency: String = "EUR",
    ) = PaywallPrice("x", micros, currency)

    @Test
    fun theDecidedOffer_savesAboutTwentyNinePercent_andIsShown() {
        val saving = PaywallSavings.saving(price(59_880_000), price(6_990_000))
        assertNotNull(saving)
        assertEquals(0.286, saving!!, 0.001)
        assertTrue(PaywallSavings.isWorthShowing(price(59_880_000), price(6_990_000)))
    }

    @Test
    fun aSmallerSaving_hidesTheLabel() {
        // 70 per year against 6.99 per month: about 17 %: "almost 30 %" would be false.
        assertFalse(PaywallSavings.isWorthShowing(price(70_000_000), price(6_990_000)))
        // Exactly at the threshold: 75 % of 12 months.
        assertTrue(PaywallSavings.isWorthShowing(price(9_000_000), price(1_000_000)))
        assertFalse(PaywallSavings.isWorthShowing(price(9_100_000), price(1_000_000)))
    }

    @Test
    fun differentCurrenciesOrMissingAmounts_neverShowTheLabel() {
        assertNull(PaywallSavings.saving(price(59_880_000), price(6_990_000, "USD")))
        assertFalse(PaywallSavings.isWorthShowing(price(59_880_000), price(6_990_000, "USD")))
        assertFalse(PaywallSavings.isWorthShowing(price(0), price(6_990_000)))
        assertFalse(PaywallSavings.isWorthShowing(price(59_880_000), price(0)))
    }
}
