package com.tomessa.miff.domain.paywall

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * A price AS THE STORE GIVES IT (ticket paywall-v2, E3): never written by hand in the app or in the translations. [formatted] is the text of
 * the store (currency and country already applied); [micros] and [currencyCode] only serve to compute the monthly equivalent of a yearly price.
 */
data class PaywallPrice(
    val formatted: String,
    val micros: Long,
    val currencyCode: String,
) {
    /** "4,99 €": the yearly price over 12, in the currency of the store, rounded to the smallest unit of that currency. Null if the currency is unknown. */
    fun perMonth(locale: Locale = Locale.getDefault()): String? {
        val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull() ?: return null
        val digits = currency.defaultFractionDigits.coerceAtLeast(0)
        val monthly =
            BigDecimal(micros)
                .movePointLeft(MICROS_DECIMALS)
                .divide(BigDecimal(MONTHS_PER_YEAR), digits, RoundingMode.HALF_UP)
        val format = NumberFormat.getCurrencyInstance(locale)
        format.currency = currency
        return format.format(monthly)
    }

    private companion object {
        const val MICROS_DECIMALS = 6
        const val MONTHS_PER_YEAR = 12
    }
}

/**
 * What the offer screen shows in place of the offer. The store (SRV-8A) will fill the first three; until then production only ever has
 * [ComingSoon] (the premium is granted by hand, nothing is sold: no price, no trial button, no lie).
 */
sealed interface PaywallOffer {
    /** The ONLY variant visible in production until the purchase exists: "Miff+ arrive bientôt", no price, no button. */
    data object ComingSoon : PaywallOffer

    /** The offers are asked to the store: placeholders of the final height, buttons inactive. */
    data object Loading : PaywallOffer

    /** No answer, no connection, or more than 10 s: a calm sentence and "Réessayer", no price, no purchase button. */
    data object Unavailable : PaywallOffer

    /** The store offers the 7-day trial on the yearly plan to this account (mockup 1). The monthly plan is reachable by "Voir toutes les offres". */
    data class TrialEligible(
        val annual: PaywallPrice,
        val monthly: PaywallPrice,
    ) : PaywallOffer

    /** No trial for this account (already used, or any other reason: it is not shown): yearly and monthly (mockup 2). */
    data class Plans(
        val annual: PaywallPrice,
        val monthly: PaywallPrice,
    ) : PaywallOffer

    /** Miff+ is active (after a purchase, a restoration, or a race): no offer, only a confirmation. */
    data object Subscribed : PaywallOffer
}

/** What is going on with the store, on top of an offer. */
enum class PaywallProgress { NONE, PURCHASING, RESTORING }

/** A calm sentence shown above the buttons (never red, never an exclamation mark, never a code). */
enum class PaywallNotice {
    /** The store says "pending" (deferred payment, "ask a parent"): nothing is granted until it is confirmed. */
    PENDING,

    /** The purchase did not end, or its check failed. */
    ERROR,

    /** A restoration found no Miff+ subscription on this store account. */
    RESTORE_NONE,
}

/** The whole state of the offer screen (a pure value: the screens are tested with simulated states, ticket paywall-v2 section 5.1). */
data class PaywallUiState(
    val offer: PaywallOffer,
    val progress: PaywallProgress = PaywallProgress.NONE,
    val notice: PaywallNotice? = null,
) {
    /** Buttons are inactive while the store is working. */
    val busy: Boolean get() = progress != PaywallProgress.NONE

    companion object {
        /** What production shows until SRV-8A plugs the store in (see PaywallOffer.ComingSoon). */
        val PRODUCTION = PaywallUiState(PaywallOffer.ComingSoon)
    }
}
