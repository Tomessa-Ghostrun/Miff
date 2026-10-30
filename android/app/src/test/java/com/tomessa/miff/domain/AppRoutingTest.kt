package com.tomessa.miff.domain

import com.tomessa.miff.domain.auth.AccountSnapshot
import com.tomessa.miff.domain.auth.AppDestination
import com.tomessa.miff.domain.auth.legalPageUrl
import com.tomessa.miff.domain.auth.resolveDestination
import com.tomessa.miff.domain.auth.shouldShowWelcomeCarousel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Criteria A5 and X3: the screen comes from the state of the account, in the order of ticket section 5. */
class AppRoutingTest {
    private fun route(
        signedIn: Boolean = true,
        profile: Boolean = false,
        miff: Boolean = false,
        pendingTerms: Boolean = false,
        returning: Boolean = false,
        phone: Boolean = true,
        account: AccountSnapshot? = AccountSnapshot(profile, miff, false, hasPhone = phone),
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
    fun session_profileWithoutPhone_phoneStepAlone() =
        assertEquals(AppDestination.PHONE_ONLY, route(profile = true, phone = false))

    @Test
    fun session_profileWithoutPhone_evenWithAMiff_phoneStepComesFirst() =
        assertEquals(AppDestination.PHONE_ONLY, route(profile = true, miff = true, phone = false))

    @Test
    fun session_profileWithPhone_isNotAskedAgain() {
        assertEquals(AppDestination.MIFF_CHOICE, route(profile = true, phone = true))
        assertEquals(AppDestination.FEED, route(profile = true, miff = true, phone = true))
    }

    @Test
    fun session_noProfile_thePhoneFlagIsIgnored_theProfileStepComesFirst() {
        assertEquals(AppDestination.PROFILE, route(profile = false, pendingTerms = true, phone = false))
        assertEquals(AppDestination.CONSENT_PANEL, route(profile = false, phone = false))
    }

    @Test
    fun session_profile_miff_feed() = assertEquals(AppDestination.FEED, route(profile = true, miff = true))

    @Test
    fun anExistingProfile_neverGoesBackToTheProfileStep_orThePanel() {
        assertEquals(AppDestination.MIFF_CHOICE, route(profile = true, pendingTerms = false))
        assertEquals(AppDestination.MIFF_CHOICE, route(profile = true, pendingTerms = true))
    }

    // --- Welcome carousel: first opening without a session only (criterion CA5) ---------------------------

    @Test
    fun carousel_isShownOnTheVeryFirstOpening() {
        val start = route(signedIn = false, account = null, returning = false)
        assertTrue(shouldShowWelcomeCarousel(start, carouselSeen = false))
    }

    @Test
    fun carousel_isNotShownOnceItWasValidated() {
        val start = route(signedIn = false, account = null, returning = false)
        assertFalse(shouldShowWelcomeCarousel(start, carouselSeen = true))
    }

    @Test
    fun carousel_isNotShownAfterASignOut_evenIfItWasNeverValidated() {
        val start = route(signedIn = false, account = null, returning = true)
        assertEquals(AppDestination.SIGN_IN, start)
        assertFalse(shouldShowWelcomeCarousel(start, carouselSeen = false))
    }

    @Test
    fun carousel_isNeverShownToASignedInPerson() {
        AppDestination.entries
            .filter { it != AppDestination.SIGN_UP }
            .forEach { assertFalse(it.name, shouldShowWelcomeCarousel(it, carouselSeen = false)) }
        assertFalse(shouldShowWelcomeCarousel(route(), carouselSeen = false))
    }

    // --- Legal pages ------------------------------------------------------------------------------------------

    @Test
    fun legalPageUrl_joinsTheBaseAndThePathWithExactlyOneSlash() {
        val expected = "https://miff-3d283.web.app/fr/cgu"
        assertEquals(expected, legalPageUrl("https://miff-3d283.web.app", "/fr/cgu"))
        assertEquals(expected, legalPageUrl("https://miff-3d283.web.app/", "/fr/cgu"))
        assertEquals(expected, legalPageUrl("https://miff-3d283.web.app//", "fr/cgu"))
        assertEquals(expected, legalPageUrl("https://miff-3d283.web.app", "fr/cgu"))
    }
}
