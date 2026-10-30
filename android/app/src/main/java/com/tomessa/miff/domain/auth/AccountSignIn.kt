package com.tomessa.miff.domain.auth

/** How the account signs in: decides whether a password exists to change (a Google account has none in Miff). */
enum class AccountSignIn { PASSWORD, GOOGLE, OTHER }
