package com.tomessa.miff.domain.subscription

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Value of `entitlements/{uid}.source` for an access granted by hand (procedure `docs/dev/premium-manuel.md`). */
const val ENTITLEMENT_SOURCE_MANUAL = "manual"

/** Values of `entitlements/{uid}.source` written by the server for a real purchase (ticket docs/tickets/premium-achat-reel.md section 9.1). */
const val ENTITLEMENT_SOURCE_APP_STORE = "app_store"
const val ENTITLEMENT_SOURCE_GOOGLE_PLAY = "google_play"

/**
 * `entitlements/{uid}` as the phone reads it. Every field is optional: the document is written by the server (a real purchase) or by hand in the console (premium-manuel.md),
 * and an older document has only the first three fields. Only the EXACT expected types count (see `entitlementFrom`): anything else reads as a missing field, never as an error.
 */
data class EntitlementDocument(
    val active: Boolean?,
    val expiresAtMillis: Long?,
    val source: String?,
    /** Informative state written by the server: trial, active, canceled, grace, on_hold, expired, revoked (the ACCESS is decided by [active] and [expiresAtMillis] only). */
    val state: String? = null,
    /** yearly or monthly. */
    val plan: String? = null,
    val trial: Boolean? = null,
    val willRenew: Boolean? = null,
    /** A test purchase (licence tester, sandbox): accepted and marked by the server. */
    val test: Boolean? = null,
)

enum class SubscriptionSource { MANUAL, APP_STORE, GOOGLE_PLAY, OTHER }

enum class SubscriptionPlan { YEARLY, MONTHLY }

/** What the store says about a purchase of Miff+ (ticket section 8). */
enum class StoreState {
    /** Free trial running. */
    TRIAL,

    ACTIVE,

    /** Cancelled by the person, but paid until the end of the period. */
    CANCELED,

    /** Payment failed, the store tries again: the access is KEPT until the end of the grace period. */
    GRACE,

    /** Suspended by the store (payment still failing): NO access. */
    ON_HOLD,

    EXPIRED,

    /** Refunded or revoked: NO access, right away. */
    REVOKED,
}

val StoreState.grantsAccess: Boolean
    get() =
        this == StoreState.TRIAL || this == StoreState.ACTIVE || this == StoreState.CANCELED ||
            this == StoreState.GRACE

/**
 * What the app knows about the Miff+ of the account. The ACCESS is decided exactly like the security rules do: `active == true` and `expiresAt` in the future; the state written
 * by the server only chooses the sentence (a document that contradicts itself never grants more than its dates say).
 */
sealed interface SubscriptionStatus {
    /** Nothing to show but the offer: no document, an access given by hand that is over, a document that says nothing. */
    data object Free : SubscriptionStatus

    /** Miff+ given by hand by the team (premium-manuel.md), still valid. */
    data class Manual(
        val expiresAtMillis: Long,
    ) : SubscriptionStatus

    /** A purchase made in a store (any state: the screen of the subscription exists for all of them, even an expired one). */
    data class Store(
        val source: SubscriptionSource,
        val state: StoreState,
        val plan: SubscriptionPlan?,
        val expiresAtMillis: Long?,
        val test: Boolean,
    ) : SubscriptionStatus

    /** True when Miff+ is ON: the unlimited places and Miffs and the danger zones depend on this. */
    val hasAccess: Boolean
        get() =
            when (this) {
                Free -> false
                is Manual -> true
                is Store -> state.grantsAccess
            }
}

private fun parseState(value: String?): StoreState? =
    when (value) {
        "trial" -> StoreState.TRIAL
        "active" -> StoreState.ACTIVE
        "canceled" -> StoreState.CANCELED
        "grace" -> StoreState.GRACE
        "on_hold" -> StoreState.ON_HOLD
        "expired" -> StoreState.EXPIRED
        "revoked" -> StoreState.REVOKED
        else -> null
    }

private fun parsePlan(value: String?): SubscriptionPlan? =
    when (value) {
        "yearly" -> SubscriptionPlan.YEARLY
        "monthly" -> SubscriptionPlan.MONTHLY
        else -> null
    }

private fun parseSource(value: String?): SubscriptionSource =
    when (value) {
        ENTITLEMENT_SOURCE_MANUAL -> SubscriptionSource.MANUAL
        ENTITLEMENT_SOURCE_APP_STORE -> SubscriptionSource.APP_STORE
        ENTITLEMENT_SOURCE_GOOGLE_PLAY -> SubscriptionSource.GOOGLE_PLAY
        else -> SubscriptionSource.OTHER
    }

/**
 * Reads `entitlements/{uid}` (null = no document) into what the screens show (ticket section 8).
 *
 * - No document: [SubscriptionStatus.Free]. A manual access that is not valid any more: Free too (no trace of a subscription, as before).
 * - A store purchase is shown in ALL its states, an expired or refunded one included (the person may want to resume it).
 * - The ACCESS comes from the dates (like the rules): a document that says "active" with an end in the past is shown as expired; one that says "expired" with an end in
 *   the future is shown as active (the dates win, the access is never wrong). An unknown or missing state is deduced from the dates.
 * - An access whose source is not known (neither manual nor a store) but valid keeps the old neutral wording ([SubscriptionSource.OTHER]); one that is over is Free.
 */
fun interpretSubscription(
    document: EntitlementDocument?,
    nowMillis: Long,
): SubscriptionStatus {
    document ?: return SubscriptionStatus.Free
    val end = document.expiresAtMillis
    val access = document.active == true && end != null && end > nowMillis
    val source = parseSource(document.source)
    if (source == SubscriptionSource.MANUAL) {
        return if (access) SubscriptionStatus.Manual(requireNotNull(end)) else SubscriptionStatus.Free
    }
    val declared = parseState(document.state)
    val state =
        if (access) {
            when (declared) {
                StoreState.TRIAL, StoreState.ACTIVE, StoreState.CANCELED, StoreState.GRACE -> declared
                else -> if (document.trial == true) StoreState.TRIAL else StoreState.ACTIVE
            }
        } else {
            when (declared) {
                StoreState.ON_HOLD, StoreState.REVOKED, StoreState.EXPIRED -> declared
                else -> StoreState.EXPIRED
            }
        }
    if (source == SubscriptionSource.OTHER && !access) return SubscriptionStatus.Free
    return SubscriptionStatus.Store(
        source = source,
        state = state,
        plan = parsePlan(document.plan),
        expiresAtMillis = end,
        test = document.test == true,
    )
}

/**
 * The reminder of the deletion of the account (ticket section 9.5): deleting the account does NOT cancel a subscription that the store keeps billing. Only a subscription that
 * can still be billed counts: a free trial, an active one, one in its grace period. A cancelled one ends by itself and an expired one is over: no reminder.
 */
data class SubscriptionReminder(
    val source: SubscriptionSource,
    /** During the free trial the yearly subscription STARTS BY ITSELF at its end: a stronger sentence. */
    val trial: Boolean,
)

fun reminderFor(status: SubscriptionStatus?): SubscriptionReminder? {
    val store = status as? SubscriptionStatus.Store ?: return null
    val billable =
        store.state == StoreState.TRIAL || store.state == StoreState.ACTIVE || store.state == StoreState.GRACE
    if (!billable) return null
    return SubscriptionReminder(store.source, trial = store.state == StoreState.TRIAL)
}

/**
 * A date in the long format of the language ("12 janvier 2027", "January 12, 2027"), in the time zone of the phone: the
 * formatter of the system, never a hand-made text (contract `docs/contracts/i18n-keys.md`, ticket criterion F4).
 */
fun formatLongDate(
    millis: Long,
    locale: Locale,
    zone: ZoneId = ZoneId.systemDefault(),
): String =
    DateTimeFormatter
        .ofLocalizedDate(FormatStyle.LONG)
        .withLocale(locale)
        .format(Instant.ofEpochMilli(millis).atZone(zone))
