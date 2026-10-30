package com.tomessa.miff.domain.auth

/** Where the app opens, decided from the state of the account (ticket section 5), never from "a sign-in just happened". */
enum class AppDestination {
    /** No session, first visit: sign-up screen. */
    SIGN_UP,

    /** No session, the app was used before (after a sign-out): sign-in screen directly. */
    SIGN_IN,

    /** Session, but no profile and no recorded acceptance of the terms (a): the consent panel comes first. */
    CONSENT_PANEL,

    /** Session, terms accepted on this device, no profile yet: profile step, then phone step (Onboarding ticket 6.3, 6.4). */
    PROFILE,

    /**
     * Profile present but without a phone number (account created before ONB-B, or a test account): the phone step alone
     * (Onboarding ticket 6.2, row 2b). Nothing else is reachable until it is done.
     */
    PHONE_ONLY,

    /** Profile and phone present, no Miff created or joined: create / join choice (Onboarding ticket). */
    MIFF_CHOICE,

    /** A Miff exists: the feed. */
    FEED,
}

/** What two cheap reads tell about the account. */
data class AccountSnapshot(
    val profileExists: Boolean,
    val hasMembership: Boolean,
    /** `users/{uid}.analyticsConsent.enabled`; absent means false. */
    val analyticsConsentEnabled: Boolean,
    /** `users/{uid}.phone` is present (absent on an account created before ONB-B). */
    val hasPhone: Boolean = true,
    /**
     * `users/{uid}.termsVersion` is present. A profile without it (created before AUTH-B) cannot receive its phone number
     * by an update (the rules read `termsVersion`): it has to be deleted and created again (data-model.md section 2.1).
     */
    val hasTermsVersion: Boolean = true,
)

/**
 * Pure routing rule. Order of ticket section 5 and Onboarding 6.2:
 * 1. no session -> sign-up, or sign-in if the app was already used;
 * 2. session, no profile -> consent panel if (a) is not recorded on this device, else profile;
 * 2b. profile without a phone number -> the phone step alone;
 * 3. profile and phone, no Miff -> create / join choice;
 * 4. otherwise the feed.
 */
fun resolveDestination(
    signedIn: Boolean,
    account: AccountSnapshot?,
    hasPendingTerms: Boolean,
    returningUser: Boolean,
): AppDestination =
    when {
        !signedIn -> if (returningUser) AppDestination.SIGN_IN else AppDestination.SIGN_UP
        account == null || !account.profileExists -> noProfileDestination(hasPendingTerms)
        !account.hasPhone -> AppDestination.PHONE_ONLY
        !account.hasMembership -> AppDestination.MIFF_CHOICE
        else -> AppDestination.FEED
    }

private fun noProfileDestination(hasPendingTerms: Boolean): AppDestination =
    if (hasPendingTerms) AppDestination.PROFILE else AppDestination.CONSENT_PANEL
