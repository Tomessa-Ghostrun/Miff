package com.tomessa.miff.domain.paywall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** The monthly equivalent is COMPUTED from the yearly price of the store (ticket paywall-v2, E3): never written by hand. */
class PaywallPriceTest {
    private fun digits(text: String?): String = text.orEmpty().filter { it.isDigit() || it == ',' || it == '.' }

    @Test
    fun yearlyPriceOverTwelve_inEuros_french() {
        val yearly = PaywallPrice("59,88 €", 59_880_000L, "EUR")
        val text = yearly.perMonth(Locale.FRANCE)
        assertNotNull(text)
        assertEquals("4,99", digits(text))
        assertTrue("the symbol of the currency of the store is there: $text", text!!.contains("€"))
    }

    @Test
    fun theLocaleDecidesTheWriting_notTheCode() {
        val yearly = PaywallPrice("$59.88", 59_880_000L, "USD")
        assertEquals("$4.99", yearly.perMonth(Locale.US))
    }

    @Test
    fun roundedToTheSmallestUnitOfTheCurrency() {
        // 49,99 over 12 = 4,1658...: 4,17.
        assertEquals("4,17", digits(PaywallPrice("49,99 €", 49_990_000L, "EUR").perMonth(Locale.FRANCE)))
        // A currency without cents: yen (119 880 over 12 = 9 990).
        val yen =
            java.text.NumberFormat.getCurrencyInstance(Locale.JAPAN).apply {
                currency =
                    java.util.Currency.getInstance("JPY")
            }
        assertEquals(yen.format(9_990), PaywallPrice("119880", 119_880_000_000L, "JPY").perMonth(Locale.JAPAN))
    }

    @Test
    fun anUnknownCurrencyGivesNothing_neverAWrongPrice() {
        assertNull(PaywallPrice("59,88", 59_880_000L, "???").perMonth(Locale.FRANCE))
        assertNull(PaywallPrice("59,88", 59_880_000L, "").perMonth(Locale.FRANCE))
    }

    @Test
    fun productionHasNoPurchase_theOnlyVisibleStateIsComingSoon() {
        assertEquals(PaywallOffer.ComingSoon, PaywallUiState.PRODUCTION.offer)
        assertEquals(PaywallProgress.NONE, PaywallUiState.PRODUCTION.progress)
        assertNull(PaywallUiState.PRODUCTION.notice)
    }

    @Test
    fun busyMeansTheStoreIsWorking() {
        val offer = PaywallOffer.Loading
        assertEquals(false, PaywallUiState(offer).busy)
        assertEquals(true, PaywallUiState(offer, PaywallProgress.PURCHASING).busy)
        assertEquals(true, PaywallUiState(offer, PaywallProgress.RESTORING).busy)
    }
}
