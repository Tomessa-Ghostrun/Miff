package com.tomessa.miff.domain

import com.tomessa.miff.domain.auth.AccountSnapshot
import com.tomessa.miff.domain.auth.AppDestination
import com.tomessa.miff.domain.auth.resolveDestination
import org.junit.Assert.assertEquals
import org.junit.Test

/** Criteria A5 and X3: the screen comes from the state of the account, in the order of ticket section 5. */
class AppRoutingTest {
    private fun route(
        signedIn: Boolean = true,
        profile: Boolean = false,
        miff: Boolean = false,
        pendingTerms: Boolean = false,
        returning: Boolean = false,
        account: AccountSnapshot? = AccountSnapshot(profile, miff, false),
    ) = resolveDestination(signedIn, account, pendingTerms, returning)

    @Test
    fun noSession_firstVisit_signUp() = assertEquals(AppDestination.SIGN_UP, route(signedIn = false, account = null))

    @Test
    fun noSession_appAlreadyUsed_signInDirectly() =
        assertEquals(AppDestination.SIGN_IN, route(signedIn = false, account = null, returning = true))

    @Test
    fun session_noProfile_noRecordedTerms_consentPanel() = assertEquals(AppDestination.CONSENT_PANEL, route())

    @Test
    fun session_noProfile_termsRecorded_profileStep() = assertEquals(AppDestination.PROFILE, route(pendingTerms = true))

    @Test
    fun session_accountUnknownYet_isTreatedAsNoProfile() =
        assertEquals(AppDestination.CONSENT_PANEL, route(account = null))

    @Test
    fun session_profile_noMiff_createOrJoin() = assertEquals(AppDestination.MIFF_CHOICE, route(profile = true))

    @Test
    fun session_profile_miff_feed() = assertEquals(AppDestination.FEED, route(profile = true, miff = true))

    @Test
    fun anExistingProfile_neverGoesBackToTheProfileStep_orThePanel() {
        assertEquals(AppDestination.MIFF_CHOICE, route(profile = true, pendingTerms = false))
        assertEquals(AppDestination.MIFF_CHOICE, route(profile = true, pendingTerms = true))
    }
}
