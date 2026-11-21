package com.tomessa.miff.domain.billing

import com.tomessa.miff.domain.paywall.PaywallPrice
import com.tomessa.miff.domain.subscription.SubscriptionSource

/**
 * The public identifiers of the Miff+ subscription in the Play Console (ticket docs/tickets/premium-achat-reel.md, Q7, decided on 2026-11-12: DEFINITIVE once created, so they
 * are plain constants): ONE subscription, two base plans. They are identifiers of a product, not of a person, and not secrets.
 */
object MiffPlusProducts {
    const val PRODUCT_ID = "miff_plus"
    const val BASE_PLAN_ANNUAL = "annuel"
    const val BASE_PLAN_MONTHLY = "mensuel"

    /** ISO 8601 durations as Google Play writes them. A plan whose period is not the one the screen promises is not offered (see [chooseOffers]). */
    const val PERIOD_YEAR = "P1Y"
    const val PERIOD_MONTH = "P1M"

    /** A free trial of 7 days: Google Play may write it as 7 days or as 1 week. */
    private val TRIAL_PERIODS = setOf("P7D", "P1W")

    fun isSevenDayTrial(period: String): Boolean = period in TRIAL_PERIODS

    /** The page of Google Play where the person cancels, resumes or changes the subscription (the "easy way to cancel" the store requires). */
    fun manageUrl(packageName: String): String =
        "https://play.google.com/store/account/subscriptions?sku=$PRODUCT_ID&package=$packageName"

    /** The page where a subscription bought in the App Store (on an iPhone) is managed: it opens in the browser of the phone (Apple redirects it to the account page). */
    const val APP_STORE_MANAGE_URL = "https://apps.apple.com/account/subscriptions"

    /**
     * Where the person manages the subscription according to the store it was bought in: Google Play (this phone) or the App Store (bought on an iPhone). Nothing for an access
     * given by hand or of an unknown origin: there is nothing to manage in a store.
     */
    fun manageUrlFor(
        source: SubscriptionSource,
        packageName: String,
    ): String? =
        when (source) {
            SubscriptionSource.GOOGLE_PLAY -> manageUrl(packageName)
            SubscriptionSource.APP_STORE -> APP_STORE_MANAGE_URL
            else -> null
        }
}

enum class PhaseRecurrence { INFINITE, FINITE, NON_RECURRING, UNKNOWN }

/** One pricing phase of an offer, as Google Play gives it (the text of the price, its amount, its currency and its period: nothing is ever written by hand). */
data class StorePhase(
    val priceMicros: Long,
    val currencyCode: String,
    val formattedPrice: String,
    val billingPeriod: String,
    val recurrence: PhaseRecurrence,
)

/**
 * One way of buying Miff+ that Google Play offers to THIS account (the list is already filtered by Google: a trial the account cannot have is not in it).
 * [offerToken] is what the purchase asks for; it is opaque, and never shown.
 */
data class StoreOffer(
    val basePlanId: String,
    val offerId: String?,
    val offerToken: String,
    val phases: List<StorePhase>,
) {
    /** A free first phase followed by a paid one. */
    val hasFreePhase: Boolean get() = phases.size >= 2 && phases.first().priceMicros == 0L

    /** The price the person pays again and again once the offer phases are over (the last endless phase). */
    val recurringPhase: StorePhase? get() = phases.lastOrNull { it.recurrence == PhaseRecurrence.INFINITE }

    override fun toString(): String = "StoreOffer($basePlanId, ${if (offerId == null) "base" else "offer"})"
}

/** What the person can buy, with the price of each choice. The buttons of the offer screen are built from this and from nothing else. */
data class PlanChoice(
    val offerToken: String,
    val price: PaywallPrice,
) {
    override fun toString(): String = "PlanChoice(${price.formatted})"
}

/** The choices of Google Play for this account: [annualTrial] only when the store offers the 7-day trial (the account is eligible), [annual] and [monthly] always. */
data class MiffPlusOffers(
    val annualTrial: PlanChoice?,
    val annual: PlanChoice,
    val monthly: PlanChoice,
)

/** Which purchase button was touched. */
enum class PlanKind { ANNUAL_TRIAL, ANNUAL, MONTHLY }

fun MiffPlusOffers.choice(kind: PlanKind): PlanChoice? =
    when (kind) {
        PlanKind.ANNUAL_TRIAL -> annualTrial
        PlanKind.ANNUAL -> annual
        PlanKind.MONTHLY -> monthly
    }

/**
 * Chooses, among the offers of Google Play, the three that the screen shows. NOTHING is invented: a missing price, an amount of zero, an unknown currency, or a plan whose
 * period is not a year (annual) or a month (monthly) means "not available" (null): the screen then says so calmly and shows no price and no purchase button. The monthly
 * equivalent of the annual price (a division by 12) is only ever computed on a plan that really is yearly.
 *
 * - The 7-day trial exists only on the annual plan (decision of the PM, 2026-10-31): the offer of the annual plan whose first phase is free and lasts 7 days.
 * - The "base" offer of a plan is the one without an offer id; if Google gives none, the first offer without a free phase.
 */
fun chooseOffers(offers: List<StoreOffer>): MiffPlusOffers? {
    fun choiceOf(
        offer: StoreOffer?,
        period: String,
    ): PlanChoice? {
        offer ?: return null
        val recurring = offer.recurringPhase ?: return null
        val invalid =
            recurring.billingPeriod != period ||
                recurring.priceMicros <= 0L ||
                recurring.formattedPrice.isBlank() ||
                recurring.currencyCode.isBlank() ||
                offer.offerToken.isBlank()
        if (invalid) return null
        return PlanChoice(
            offer.offerToken,
            PaywallPrice(recurring.formattedPrice, recurring.priceMicros, recurring.currencyCode),
        )
    }

    fun baseOf(plan: String): StoreOffer? {
        val ofPlan = offers.filter { it.basePlanId == plan && !it.hasFreePhase }
        return ofPlan.firstOrNull { it.offerId == null } ?: ofPlan.firstOrNull()
    }

    val annual = choiceOf(baseOf(MiffPlusProducts.BASE_PLAN_ANNUAL), MiffPlusProducts.PERIOD_YEAR) ?: return null
    val monthly = choiceOf(baseOf(MiffPlusProducts.BASE_PLAN_MONTHLY), MiffPlusProducts.PERIOD_MONTH) ?: return null
    val trialOffer =
        offers.firstOrNull {
            it.basePlanId == MiffPlusProducts.BASE_PLAN_ANNUAL &&
                it.hasFreePhase &&
                MiffPlusProducts.isSevenDayTrial(it.phases.first().billingPeriod)
        }
    val trial = choiceOf(trialOffer, MiffPlusProducts.PERIOD_YEAR)
    return MiffPlusOffers(annualTrial = trial, annual = annual, monthly = monthly)
}
