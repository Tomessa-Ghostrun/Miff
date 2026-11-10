package com.tomessa.miff.domain.subscription

import com.tomessa.miff.domain.entitlement.OfferState
import com.tomessa.miff.domain.entitlement.interpretEntitlement
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** What the read-only "Abonnement" screen shows for an ACTIVE Miff+ account (ticket ABO-1). */
data class SubscriptionInfo(
    /** The end of the current period, in milliseconds since the epoch (`entitlements/{uid}.expiresAt`). */
    val expiresAtMillis: Long,
    /** True when the access was granted by hand by the team (`source == "manual"`): the screen then says so. */
    val grantedByTeam: Boolean,
)

/** Value of `entitlements/{uid}.source` for an access granted by hand (procedure `docs/dev/premium-manuel.md`). */
const val ENTITLEMENT_SOURCE_MANUAL = "manual"

/**
 * The "Abonnement" line exists ONLY for an active account: same reading as the security rules and the plan of the places
 * ([interpretEntitlement]: `active == true` and `expiresAt` in the future). Anything else (no document, inactive, expired, fields
 * missing) gives null: no line, no screen, no trace of a subscription. [source] is only compared, never displayed as is.
 */
fun interpretSubscription(
    documentExists: Boolean,
    active: Boolean?,
    expiresAtMillis: Long?,
    source: String?,
    nowMillis: Long,
): SubscriptionInfo? =
    when (val offer = interpretEntitlement(documentExists, active, expiresAtMillis, nowMillis)) {
        OfferState.Classic -> {
            null
        }

        is OfferState.Premium -> {
            SubscriptionInfo(
                offer.expiresAtMillis,
                grantedByTeam =
                    source == ENTITLEMENT_SOURCE_MANUAL,
            )
        }
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
