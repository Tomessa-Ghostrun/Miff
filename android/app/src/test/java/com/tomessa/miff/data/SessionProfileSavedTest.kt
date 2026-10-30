package com.tomessa.miff.data

import com.tomessa.miff.data.auth.AccountLoad
import com.tomessa.miff.data.session.SessionManager
import com.tomessa.miff.data.session.SessionState
import com.tomessa.miff.domain.auth.AccountSnapshot
import com.tomessa.miff.domain.auth.AppDestination
import com.tomessa.miff.domain.auth.ConsentVersions
import com.tomessa.miff.domain.auth.PendingConsent
import com.tomessa.miff.testsupport.SessionHarness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the session does once the Onboarding steps have written the profile (ticket 6.2, rows 2, 2b and 3 of the routing). */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionProfileSavedTest {
    private fun SessionManager.destination(): AppDestination? = (state.value as? SessionState.Ready)?.destination

    private fun run(block: suspend TestScope.(SessionHarness) -> Unit) =
        runTest(UnconfinedTestDispatcher()) { block(SessionHarness(backgroundScope)) }

    private fun SessionHarness.signedInWithTerms() {
        gateway.setSignedIn("uid-1")
        pending.save(PendingConsent("uid-1", ConsentVersions.TERMS, analyticsAccepted = true))
        accounts.setLoaded(profileExists = false)
        manager.start()
    }

    @Test
    fun afterTheProfileIsSaved_theAccountIsReadAgain_andTheChoiceOfMiffOpens() =
        run { h ->
            h.signedInWithTerms()
            assertEquals(AppDestination.PROFILE, h.manager.destination())
            val reads = h.accounts.loadCalls
            h.accounts.setLoaded(profileExists = true, hasPhone = true)
            h.manager.onProfileSaved(analyticsConsentEnabled = true)
            assertEquals(AppDestination.MIFF_CHOICE, h.manager.destination())
            assertEquals("one read to know where to go", reads + 1, h.accounts.loadCalls)
        }

    @Test
    fun theAcceptanceKeptOnTheDeviceIsForgottenOnceTheProfileExists() =
        run { h ->
            h.signedInWithTerms()
            h.accounts.setLoaded(profileExists = true)
            h.manager.onProfileSaved(analyticsConsentEnabled = true)
            assertNull(h.pending.stored)
        }

    @Test
    fun theAccountsOwnAnalyticsChoiceRules_afterTheProfile() =
        run { h ->
            h.signedInWithTerms()
            h.accounts.setLoaded(profileExists = true, analyticsConsent = true)
            h.manager.onProfileSaved(analyticsConsentEnabled = true)
            assertTrue(h.privacy.analyticsEnabled.value)
        }

    @Test
    fun noEventIsSent_whenTheProfileIsSaved() =
        run { h ->
            h.signedInWithTerms()
            val before = h.sink.events.size
            h.accounts.setLoaded(profileExists = true, analyticsConsent = true)
            h.manager.onProfileSaved(analyticsConsentEnabled = true)
            assertEquals(before, h.sink.events.size)
        }

    @Test
    fun ifTheReadFails_theAppMovesOnFromWhatWasWritten_noRetryScreenRightAfterASuccess() =
        run { h ->
            h.signedInWithTerms()
            h.accounts.result = AccountLoad.Unavailable
            h.manager.onProfileSaved(analyticsConsentEnabled = true)
            assertEquals(AppDestination.MIFF_CHOICE, h.manager.destination())
            assertTrue("the choice written with the profile is applied", h.privacy.analyticsEnabled.value)
        }

    @Test
    fun ifTheReadStillSaysNoProfile_theWrittenOneIsTrusted() =
        run { h ->
            h.signedInWithTerms()
            h.accounts.result =
                AccountLoad.Loaded(
                    AccountSnapshot(profileExists = false, hasMembership = false, analyticsConsentEnabled = false),
                )
            h.manager.onProfileSaved(analyticsConsentEnabled = false)
            assertEquals(AppDestination.MIFF_CHOICE, h.manager.destination())
        }

    @Test
    fun anOlderAccountThatAlreadyHasAMiff_goesToTheFeed() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.accounts.setLoaded(profileExists = true, hasMembership = true, hasPhone = false)
            h.manager.start()
            assertEquals(AppDestination.PHONE_ONLY, h.manager.destination())
            h.accounts.setLoaded(profileExists = true, hasMembership = true, hasPhone = true)
            h.manager.onProfileSaved(analyticsConsentEnabled = false)
            assertEquals(AppDestination.FEED, h.manager.destination())
        }

    @Test
    fun aSessionLostDuringTheRead_isNotBroughtBack() =
        run { h ->
            h.signedInWithTerms()
            h.accounts.setLoaded(profileExists = true)
            h.duringAccountRead = { h.gateway.setSignedIn(null) }
            h.manager.onProfileSaved(analyticsConsentEnabled = true)
            assertFalse(
                "no screen of a signed-in person after the loss",
                h.manager.destination() == AppDestination.MIFF_CHOICE,
            )
        }

    @Test
    fun withNobodySignedIn_nothingHappens() =
        run { h ->
            h.manager.start()
            val before = h.manager.destination()
            h.manager.onProfileSaved(analyticsConsentEnabled = true)
            assertEquals(before, h.manager.destination())
        }

    @Test
    fun profileAlreadySaved_isTrueOnlyForACompleteProfile() =
        run { h ->
            h.signedInWithTerms()
            assertFalse("no profile", h.manager.profileAlreadySaved())
            h.accounts.setLoaded(profileExists = true, hasPhone = false)
            assertFalse("a profile without a number is not complete", h.manager.profileAlreadySaved())
            h.accounts.setLoaded(profileExists = true, hasPhone = true)
            assertTrue(h.manager.profileAlreadySaved())
            h.accounts.result = AccountLoad.Unavailable
            assertFalse("unclear is false", h.manager.profileAlreadySaved())
        }

    @Test
    fun profileAlreadySaved_withNobodySignedIn_isFalse() =
        run { h ->
            h.manager.start()
            assertFalse(h.manager.profileAlreadySaved())
        }

    // --- What the screens ask the session ---------------------------------------------------------------------------

    @Test
    fun theSnapshotOfTheAccountIsKnown_toTellARefusalFromAnOldProfile() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.accounts.setLoaded(profileExists = true, hasPhone = false, hasTermsVersion = false)
            h.manager.start()
            assertEquals(false, h.manager.accountSnapshot?.hasTermsVersion)
            assertEquals(false, h.manager.accountSnapshot?.hasPhone)
        }

    @Test
    fun theSuggestedNameComesFromTheProvider_andIsNotStoredByTheSession() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.gateway.displayName = "Marie Dupont"
            assertEquals("Marie Dupont", h.manager.suggestedDisplayName())
            assertEquals("uid-1", h.manager.currentUid)
        }

    @Test
    fun everyEndOfSession_startsAnotherGeneration_soTheNextPersonNeverSeesWhatWasTyped() =
        run { h ->
            h.signedInWithTerms()
            assertEquals(0, h.manager.generation)
            h.manager.signOut()
            assertEquals(1, h.manager.generation)
            assertNull(h.manager.accountSnapshot)
        }
}
