package com.tomessa.miff.domain.billing

/**
 * How ONE purchase attempt ended, as the offer screen tells it (ticket docs/tickets/premium-achat-reel.md section 6 and criteria AN2 to AN5, AN9, AN12). A pure value: no store
 * object, no token, nothing personal.
 */
enum class PurchaseOutcome {
    /** The server checked the purchase and Miff+ is on for this account. */
    GRANTED,

    /** Google Play says the payment is not final yet (deferred payment): NOTHING is granted until it is confirmed. */
    PENDING,

    /** The person closed the sheet of Google Play: no message at all. */
    CANCELED,

    /** The purchase already belongs to ANOTHER Miff account: a calm refusal, nothing said about the other account (decision Q6: one purchase = one account). */
    OTHER_ACCOUNT,

    /** The product (or the store) is not available here: the offer shows its calm "not available" state. */
    UNAVAILABLE,

    /** No connection or a service that did not answer: nothing is lost, a paid purchase is checked again at the next opening. */
    NETWORK,

    /** The store says "already owned" but the subscription is ON HOLD (payment failing; the store does not list it): the offer says so and points to the page of the store. */
    ON_HOLD,

    /** Anything else. */
    FAILED,
}

/** How a "restore my purchases" ended. */
enum class RestoreOutcome {
    /** A Miff+ subscription is there for this account (found, or already known). */
    FOUND,

    /** Nothing on this store account. */
    NONE_FOUND,

    /** The only subscription found is used by another Miff account. */
    OTHER_ACCOUNT,

    /** A payment of the store account is still pending: nothing is granted until it is confirmed. */
    PENDING,

    /** The store lists nothing but the server knows a subscription ON HOLD. */
    ON_HOLD,

    NETWORK,

    FAILED,
}

/** What the store says about the offers: the choices, "not available", or an error that is worth a second try. */
sealed interface OffersResult {
    data class Loaded(
        val offers: MiffPlusOffers,
    ) : OffersResult

    /** The product does not exist for this account or this device (no Play Store, product not published, country): no price, no button, calm sentence. */
    data object NotAvailable : OffersResult

    /** No connection, or the store did not answer in time. */
    data object Error : OffersResult
}
