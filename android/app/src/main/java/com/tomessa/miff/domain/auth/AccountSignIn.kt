package com.tomessa.miff.domain.auth

/** How the account signs in: decides whether a password exists to change (a Google account has none in Miff). */
enum class AccountSignIn { PASSWORD, GOOGLE, OTHER }

/**
 * The sign-in methods really LINKED to the account (the providers Firebase holds for the user). The re-authentication of the
 * deletion of the account offers only these (AUTH-5, ticket 5.4). APPLE exists on an account created on an iPhone: Android has
 * no way to re-authenticate with it nor to revoke it (limit accepted by the PM, 2026-11-05).
 */
enum class SignInProvider { PASSWORD, GOOGLE, APPLE }

/** What Firebase says when asked whether the session of the account still exists (used by the deletion of the account). */
enum class SessionCheck {
    /** The account exists and its session is valid. */
    VALID,

    /** Firebase says the account does not exist any more (`ERROR_USER_NOT_FOUND`): for a deletion in progress, that is a success. */
    GONE,

    /** The session can no longer be used (token expired or revoked) but the account may still exist: NEVER a success, sign in again. */
    EXPIRED,

    /** No network, or the answer is unclear: nothing can be concluded. */
    UNKNOWN,
}
