package com.tomessa.miff.domain.entitlement

/** Offer shown for `entitlements/{uid}`. A missing document or an inactive/expired one means Classic (free). */
sealed interface OfferState {
    data object Classic : OfferState

    data class Premium(
        val expiresAtMillis: Long,
    ) : OfferState
}

/**
 * Reads the premium status the same way the security rules do: active == true and expiresAt in the future.
 * Missing document, missing fields or an expired date all give Classic.
 */
fun interpretEntitlement(
    documentExists: Boolean,
    active: Boolean?,
    expiresAtMillis: Long?,
    nowMillis: Long,
): OfferState =
    if (documentExists && active == true && expiresAtMillis != null && expiresAtMillis > nowMillis) {
        OfferState.Premium(expiresAtMillis)
    } else {
        OfferState.Classic
    }
