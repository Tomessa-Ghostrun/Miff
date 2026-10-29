package com.tomessa.miff.data

import com.tomessa.miff.data.auth.AccountLoad
import com.tomessa.miff.data.session.RefuseResult
import com.tomessa.miff.data.session.SessionManager
import com.tomessa.miff.data.session.SessionState
import com.tomessa.miff.data.session.SignedOutNotice
import com.tomessa.miff.domain.analytics.AnalyticsEvent
import com.tomessa.miff.domain.auth.AppDestination
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.AuthOutcome
import com.tomessa.miff.domain.auth.ConsentChoice
import com.tomessa.miff.domain.auth.ConsentVersions
import com.tomessa.miff.domain.auth.GoogleTokenResult
import com.tomessa.miff.domain.auth.PendingConsent
import com.tomessa.miff.testsupport.SessionHarness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Routing at launch, consent rules, sign-out and refusal of the consent panel (ticket sections 5, 7.6, 7.8, 8). */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {
    private fun SessionManager.destination(): AppDestination? = (state.value as? SessionState.Ready)?.destination

    private fun run(block: suspend TestScope.(SessionHarness) -> Unit) =
        runTest(UnconfinedTestDispatcher()) { block(SessionHarness(backgroundScope)) }

    // --- Launch routing (section 5, criteria X3, X4, A5) --------------------------------------------------

    @Test
    fun noSession_firstVisit_opensSignUp() =
        run { h ->
            h.manager.start()
            assertEquals(AppDestination.SIGN_UP, h.manager.destination())
        }

    @Test
    fun noSession_afterAPreviousSession_opensSignInDirectly() =
        run { h ->
            h.flags.put(SessionManager.KEY_RETURNING_USER, true)
            h.manager.start()
            assertEquals(AppDestination.SIGN_IN, h.manager.destination())
        }

    @Test
    fun session_withoutProfileAndWithoutRecordedTerms_opensTheConsentPanel() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.accounts.setLoaded(profileExists = false)
            h.manager.start()
            assertEquals(AppDestination.CONSENT_PANEL, h.manager.destination())
        }

    @Test
    fun session_withoutProfile_butTermsRecordedForThisAccount_opensTheProfileStep() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.pending.save(PendingConsent("uid-1", ConsentVersions.TERMS, analyticsAccepted = false))
            h.accounts.setLoaded(profileExists = false)
            h.manager.start()
            assertEquals(AppDestination.PROFILE, h.manager.destination())
        }

    @Test
    fun termsRecordedForAnotherAccount_neverCountForThisOne() =
        run { h ->
            h.gateway.setSignedIn("uid-2")
            h.pending.save(PendingConsent("uid-1", ConsentVersions.TERMS, analyticsAccepted = true))
            h.accounts.setLoaded(profileExists = false)
            h.manager.start()
            assertEquals(AppDestination.CONSENT_PANEL, h.manager.destination())
            assertFalse("the other account's choice must not switch anything on", h.privacy.analyticsEnabled.value)
        }

    @Test
    fun session_profileButNoMiff_opensTheCreateOrJoinChoice_andNeverTheProfileAgain() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.accounts.setLoaded(profileExists = true, hasMembership = false)
            h.manager.start()
            assertEquals(AppDestination.MIFF_CHOICE, h.manager.destination())
        }

    @Test
    fun session_withProfileAndMiff_opensTheFeed() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.accounts.setLoaded(profileExists = true, hasMembership = true)
            h.manager.start()
            assertEquals(AppDestination.FEED, h.manager.destination())
        }

    @Test
    fun accountCannotBeRead_keepsTheSession_andRetryWorks() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.accounts.result = AccountLoad.Unavailable
            h.manager.start()
            assertEquals(SessionState.LookupFailed, h.manager.state.value)
            assertEquals("the session must be kept", "uid-1", h.gateway.currentUid)
            assertEquals(0, h.gateway.signOutCalls)

            h.accounts.setLoaded(profileExists = true, hasMembership = true)
            h.manager.retry()
            assertEquals(AppDestination.FEED, h.manager.destination())
        }

    // --- Cold start: nothing collected before the account is read (review I1/I2) ---------------------------

    @Test
    fun coldStartWithASession_cutsAStoredCollection_andStaysOffIfTheReadFails() =
        run { h ->
            h.privacy.applyAccountConsent(true) // the choice stored by the previous run
            h.gateway.setSignedIn("uid-1")
            h.accounts.result = AccountLoad.Unavailable
            h.manager.start()
            assertEquals(SessionState.LookupFailed, h.manager.state.value)
            assertFalse(h.privacy.analyticsEnabled.value)
            assertFalse(h.privacy.crashReportsEnabled.value)
            assertEquals("off while the account was being read", false to false, h.collectionWhenAccountRead)
        }

    @Test
    fun coldStartWithASession_switchesOnOnlyAfterASuccessfulRead_andOnlyIfTheAccountSaidYes() =
        run { h ->
            h.privacy.applyAccountConsent(true)
            h.gateway.setSignedIn("uid-1")
            h.accounts.setLoaded(profileExists = true, hasMembership = true, analyticsConsent = true)
            h.manager.start()
            assertEquals(false to false, h.collectionWhenAccountRead)
            assertTrue(h.privacy.analyticsEnabled.value)

            h.accounts.setLoaded(profileExists = true, hasMembership = true, analyticsConsent = false)
            h.privacy.applyAccountConsent(true)
            h.manager.retry()
            assertEquals(false to false, h.collectionWhenAccountRead)
            assertFalse(h.privacy.analyticsEnabled.value)
        }

    @Test
    fun signIn_cutsACollectionLeftOnBeforeTheRead_X9() =
        run { h ->
            h.manager.start()
            h.privacy.applyAccountConsent(true) // left on by something else
            h.accounts.setLoaded(profileExists = true, hasMembership = true, analyticsConsent = false)
            h.manager.signInWithEmail("a@b.fr", "password-1")
            assertEquals("off at the moment of the read", false to false, h.collectionWhenAccountRead)
            assertFalse(h.privacy.analyticsEnabled.value)
            assertTrue(h.sink.events.isEmpty())
        }

    // --- Session lost during a transition (review I4) --------------------------------------------------------

    @Test
    fun aSessionLostWhileTheAccountIsBeingRead_isStillCleanedUp() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.accounts.setLoaded(profileExists = true, hasMembership = true)
            h.duringAccountRead = { h.gateway.setSignedIn(null) }
            h.manager.start()
            assertEquals(AppDestination.SIGN_IN, h.manager.destination())
            assertEquals("the read result must not bring the app back in", 1, h.cache.purgeCalls)
            assertEquals(SignedOutNotice.ACCOUNT_UNAVAILABLE, h.manager.consumeNotice())
        }

    // --- Consent panel details (review I3 and minor) -------------------------------------------------------

    @Test
    fun panelRefuse_ofAnEmailAccount_neverOpensAGoogleDialog_andKeepsThePanel() =
        run { h ->
            h.gateway.setSignedIn("uid-1", AnalyticsEvent.Method.EMAIL)
            h.accounts.setLoaded(profileExists = false)
            h.manager.start()
            h.gateway.deleteResults.addLast(AuthFailure.RECENT_LOGIN_REQUIRED)
            var googleAsked = false

            val result =
                h.manager.refuseConsentPanel {
                    googleAsked = true
                    GoogleTokenResult.Cancelled
                }

            assertFalse("a password account cannot be sent to a Google dialog", googleAsked)
            assertEquals(RefuseResult.Failed(AuthFailure.UNKNOWN), result)
            assertEquals(AppDestination.CONSENT_PANEL, h.manager.destination())
        }

    @Test
    fun panelAccept_twice_recordsOnceAndSendsOneEvent() =
        run { h ->
            h.manager.start()
            h.accounts.setLoaded(profileExists = false)
            h.manager.signInWithGoogle("token", signUpConsent = null)
            assertTrue(h.manager.acceptConsentPanel(ConsentChoice.IMPLICIT))
            assertFalse("second tap", h.manager.acceptConsentPanel(ConsentChoice.IMPLICIT))
            assertEquals(listOf("sign_up_completed"), h.sink.names)
        }

    @Test
    fun startPurgesAFailedPurgeBeforeAnythingElse() =
        run { h ->
            h.manager.start()
            assertEquals(1, h.cache.purgeIfPendingCalls)
        }

    // --- Collection of Analytics and crash reports (section 8, criteria X9, C13) -----------------------

    @Test
    fun noSessionAtLaunch_cutsAnyCollectionLeftOn() =
        run { h ->
            h.privacy.applyAccountConsent(true)
            h.manager.start()
            assertFalse(h.privacy.analyticsEnabled.value)
            assertFalse(h.privacy.crashReportsEnabled.value)
        }

    // --- Review I2: the crash report of the previous run is kept until the account has answered --------------

    @Test
    fun coldStartWithAnExistingConsent_keepsTheCrashReport_thenLetsItBeSent() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.accounts.setLoaded(profileExists = true, hasMembership = true, analyticsConsent = true)
            h.manager.start()
            // Paused at the start (nothing deleted), switched on once the account said yes: never "off" in between,
            // which is what used to erase the report of the previous crash.
            assertEquals(listOf("pause", "on"), h.crashSwitch.states())
            assertEquals(listOf("pause", "on"), h.analyticsSwitch.states())
        }

    @Test
    fun coldStart_whenTheAccountCannotBeRead_keepsTheCrashReportForLater() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.accounts.result = AccountLoad.Unavailable
            h.manager.start()
            assertEquals(listOf("pause"), h.crashSwitch.states())

            h.accounts.setLoaded(profileExists = true, hasMembership = true, analyticsConsent = true)
            h.manager.retry()
            assertEquals("still nothing deleted, then switched on", listOf("pause", "on"), h.crashSwitch.states())
        }

    @Test
    fun coldStart_whenTheAccountWithdrewItsConsent_deletesTheCrashReport() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.accounts.setLoaded(profileExists = true, hasMembership = true, analyticsConsent = false)
            h.manager.start()
            assertEquals(listOf("pause", "off"), h.crashSwitch.states())
        }

    @Test
    fun coldStartWithoutASession_deletesTheCrashReport_thereIsNoConsent() =
        run { h ->
            h.manager.start()
            assertEquals(listOf("pause", "off"), h.crashSwitch.states())
        }

    @Test
    fun signOut_deletesTheCrashReport() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.accounts.setLoaded(profileExists = true, hasMembership = true, analyticsConsent = true)
            h.manager.start()
            h.manager.signOut()
            assertEquals(listOf("pause", "on", "off"), h.crashSwitch.states())
        }

    @Test
    fun refusingThePanel_deletesTheCrashReport() =
        run { h ->
            h.manager.start()
            h.accounts.setLoaded(profileExists = false)
            h.manager.signInWithGoogle("token", signUpConsent = null)
            h.manager.refuseConsentPanel { GoogleTokenResult.Unavailable }
            assertEquals("off", h.crashSwitch.log.last())
            assertFalse("never switched on", h.crashSwitch.log.contains("on"))
        }

    @Test
    fun signIn_pausesWhileTheAccountIsRead_thenSwitchesOn_andNeverDeletesInBetween() =
        run { h ->
            h.accounts.setLoaded(profileExists = true, hasMembership = true, analyticsConsent = true)
            h.manager.start()
            h.crashSwitch.log.clear()
            h.manager.signInWithEmail("a@b.fr", "password-1")
            assertEquals(listOf("pause", "on"), h.crashSwitch.states())
        }

    @Test
    fun emailSignUp_switchesBothOn_thenSendsSignUpCompleted() =
        run { h ->
            h.manager.start()
            h.manager.createAccountWithEmail("a@b.fr", "password-1", ConsentChoice.IMPLICIT)
            assertTrue(h.privacy.analyticsEnabled.value)
            assertTrue(h.privacy.crashReportsEnabled.value)
            assertEquals(listOf("sign_up_completed"), h.sink.names)
            assertEquals(
                mapOf<String, Any>("method" to "email"),
                h.sink.events
                    .single()
                    .second,
            )
            assertEquals(PendingConsent("uid-1", ConsentVersions.TERMS, true), h.pending.stored)
        }

    @Test
    fun anAccountIsNeverCreatedWithoutTheTerms() =
        run { h ->
            h.manager.start()
            try {
                h.manager.createAccountWithEmail("a@b.fr", "password-1", ConsentChoice(termsAccepted = false))
                fail("must refuse")
            } catch (expected: IllegalArgumentException) {
                // expected
            }
            assertEquals(0, h.gateway.createCalls)
        }

    @Test
    fun failedSignUp_changesNothing() =
        run { h ->
            h.gateway.createResult = AuthOutcome.Failure(AuthFailure.EMAIL_ALREADY_IN_USE)
            h.manager.start()
            val failure = h.manager.createAccountWithEmail("a@b.fr", "password-1", ConsentChoice.IMPLICIT)
            assertEquals(AuthFailure.EMAIL_ALREADY_IN_USE, failure)
            assertEquals(AppDestination.SIGN_UP, h.manager.destination())
            assertNull(h.pending.stored)
            assertTrue(h.sink.events.isEmpty())
            assertFalse(h.privacy.analyticsEnabled.value)
        }

    @Test
    fun signIn_collectsNothingUntilTheAccountHasBeenRead_thenFollowsTheAccountChoice() =
        run { h ->
            h.accounts.setLoaded(profileExists = true, hasMembership = true, analyticsConsent = true)
            h.manager.start()
            assertNull(h.manager.signInWithEmail("a@b.fr", "password-1"))
            assertEquals("off at the moment of the read", false to false, h.collectionWhenAccountRead)
            assertTrue(h.privacy.analyticsEnabled.value)
            assertTrue(h.privacy.crashReportsEnabled.value)
            assertEquals(listOf("sign_in_completed"), h.sink.names)
            assertEquals(AppDestination.FEED, h.manager.destination())
        }

    @Test
    fun signIn_accountWithoutConsent_collectsNothingAndSendsNothing() =
        run { h ->
            h.accounts.setLoaded(profileExists = true, hasMembership = true, analyticsConsent = false)
            h.manager.start()
            h.manager.signInWithEmail("a@b.fr", "password-1")
            assertFalse(h.privacy.analyticsEnabled.value)
            assertTrue(h.sink.events.isEmpty())
        }

    @Test
    fun signIn_whenTheAccountCannotBeRead_collectsNothing() =
        run { h ->
            h.accounts.result = AccountLoad.Unavailable
            h.manager.start()
            h.manager.signInWithEmail("a@b.fr", "password-1")
            assertEquals(SessionState.LookupFailed, h.manager.state.value)
            assertFalse(h.privacy.analyticsEnabled.value)
            assertTrue(h.sink.events.isEmpty())
        }

    @Test
    fun failedSignIn_returnsTheFailure_andChangesNothing() =
        run { h ->
            h.gateway.signInResult = AuthOutcome.Failure(AuthFailure.INVALID_CREDENTIALS)
            h.manager.start()
            assertEquals(AuthFailure.INVALID_CREDENTIALS, h.manager.signInWithEmail("a@b.fr", "wrong-password"))
            assertEquals(AppDestination.SIGN_UP, h.manager.destination())
            assertEquals(0, h.accounts.loadCalls)
        }

    // --- New person arriving through Google from the sign-in screen (section 7.8, criteria C8 to C14) -----

    @Test
    fun googleFromSignIn_newAccount_showsThePanel_andNothingIsCollectedOrWritten() =
        run { h ->
            h.manager.start()
            h.accounts.setLoaded(profileExists = false)
            assertNull(h.manager.signInWithGoogle("token", signUpConsent = null))
            assertEquals(AppDestination.CONSENT_PANEL, h.manager.destination())
            assertFalse(h.privacy.analyticsEnabled.value)
            assertFalse(h.privacy.crashReportsEnabled.value)
            assertTrue(h.sink.events.isEmpty())
            assertNull("nothing is recorded before the acceptance", h.pending.stored)
        }

    @Test
    fun googleFromSignIn_existingAccountWithProfile_goesStraightThrough_withoutThePanel() =
        run { h ->
            h.manager.start()
            h.accounts.setLoaded(profileExists = true, hasMembership = true)
            h.manager.signInWithGoogle("token", signUpConsent = null)
            assertEquals(AppDestination.FEED, h.manager.destination())
        }

    @Test
    fun panelAccept_recordsBoth_switchesOn_andSendsSignUpCompleted() =
        run { h ->
            h.manager.start()
            h.accounts.setLoaded(profileExists = false)
            h.manager.signInWithGoogle("token", signUpConsent = null)

            assertTrue(h.manager.acceptConsentPanel(ConsentChoice.IMPLICIT))
            assertEquals(AppDestination.PROFILE, h.manager.destination())
            assertEquals(PendingConsent("uid-1", ConsentVersions.TERMS, true), h.pending.stored)
            assertTrue(h.privacy.analyticsEnabled.value)
            assertEquals(listOf("sign_up_completed"), h.sink.names)
            assertEquals(
                mapOf<String, Any>("method" to "google"),
                h.sink.events
                    .single()
                    .second,
            )
        }

    @Test
    fun panelAccept_withoutTheTerms_doesNothing() =
        run { h ->
            h.manager.start()
            h.accounts.setLoaded(profileExists = false)
            h.manager.signInWithGoogle("token", signUpConsent = null)
            assertFalse(h.manager.acceptConsentPanel(ConsentChoice(termsAccepted = false)))
            assertEquals(AppDestination.CONSENT_PANEL, h.manager.destination())
            assertNull(h.pending.stored)
            assertTrue(h.sink.events.isEmpty())
        }

    @Test
    fun panelRefuse_deletesTheAccount_keepsNothing_sendsNothing_andComesBackToSignIn() =
        run { h ->
            h.manager.start()
            h.accounts.setLoaded(profileExists = false)
            h.manager.signInWithGoogle("token", signUpConsent = null)
            h.timeline.entries.clear()

            val result = h.manager.refuseConsentPanel { GoogleTokenResult.Unavailable }

            assertEquals(RefuseResult.Done, result)
            assertEquals(
                "delete first, then stop listeners, purge, close the session",
                listOf("markPending", "delete", "teardown", "purge", "signOut"),
                h.timeline.entries,
            )
            assertEquals(AppDestination.SIGN_IN, h.manager.destination())
            assertEquals(SignedOutNotice.ACCOUNT_NOT_CREATED, h.manager.consumeNotice())
            assertNull(h.manager.consumeNotice())
            assertNull("no recorded choice", h.pending.stored)
            assertNull(h.gateway.currentUid)
            assertTrue("no event at all", h.sink.events.isEmpty())
            assertFalse("collection never switched on", h.analyticsSwitch.calls.any { it })
            assertFalse(h.crashSwitch.calls.any { it })
            assertNull("no trace of the visit either", h.flags.values[SessionManager.KEY_RETURNING_USER])
            assertEquals("no extra read of the account", 1, h.accounts.loadCalls)
        }

    @Test
    fun panelRefuse_whenTheDeletionFails_staysOnThePanel_withoutAccessToTheApp() =
        run { h ->
            h.manager.start()
            h.accounts.setLoaded(profileExists = false)
            h.manager.signInWithGoogle("token", signUpConsent = null)
            h.gateway.deleteResults.addLast(AuthFailure.NETWORK)
            h.timeline.entries.clear()

            val result = h.manager.refuseConsentPanel { GoogleTokenResult.Unavailable }

            assertEquals(RefuseResult.Failed(AuthFailure.NETWORK), result)
            assertEquals(AppDestination.CONSENT_PANEL, h.manager.destination())
            assertEquals("the account is still there, the panel too", "uid-1", h.gateway.currentUid)
            assertFalse(h.timeline.entries.contains("purge"))
            assertFalse(h.timeline.entries.contains("signOut"))
            assertTrue(h.sink.events.isEmpty())
        }

    @Test
    fun panelRefuse_afterAnOldSession_asksForARecentSignIn_thenDeletes() =
        run { h ->
            h.manager.start()
            h.accounts.setLoaded(profileExists = false)
            h.manager.signInWithGoogle("token", signUpConsent = null)
            h.gateway.deleteResults.addLast(AuthFailure.RECENT_LOGIN_REQUIRED)
            h.gateway.deleteResults.addLast(null)

            val result = h.manager.refuseConsentPanel { GoogleTokenResult.Token("fresh") }

            assertEquals(RefuseResult.Done, result)
            assertEquals(
                listOf("delete", "reauth", "delete"),
                h.timeline.entries.filter {
                    it == "delete" ||
                        it == "reauth"
                },
            )
            assertEquals(AppDestination.SIGN_IN, h.manager.destination())
        }

    @Test
    fun panelRefuse_closingTheRecentSignInDialog_changesNothing() =
        run { h ->
            h.manager.start()
            h.accounts.setLoaded(profileExists = false)
            h.manager.signInWithGoogle("token", signUpConsent = null)
            h.gateway.deleteResults.addLast(AuthFailure.RECENT_LOGIN_REQUIRED)

            val result = h.manager.refuseConsentPanel { GoogleTokenResult.Cancelled }

            assertEquals(RefuseResult.Cancelled, result)
            assertEquals(AppDestination.CONSENT_PANEL, h.manager.destination())
            assertEquals("uid-1", h.gateway.currentUid)
        }

    @Test
    fun theSessionLossWatcher_doesNotReactToOurOwnRefusal() =
        run { h ->
            h.manager.start()
            h.accounts.setLoaded(profileExists = false)
            h.manager.signInWithGoogle("token", signUpConsent = null)
            h.manager.refuseConsentPanel { GoogleTokenResult.Unavailable }
            assertEquals("one purge, not two", 1, h.cache.purgeCalls)
            assertEquals(SignedOutNotice.ACCOUNT_NOT_CREATED, h.manager.consumeNotice())
        }

    // --- Google from the sign-up screen (the tap is the acceptance) ---------------------------------

    @Test
    fun googleFromSignUp_newAccount_recordsTheAcceptance_andSendsSignUp() =
        run { h ->
            h.manager.start()
            h.accounts.setLoaded(profileExists = false)
            assertNull(h.manager.signInWithGoogle("token", ConsentChoice.IMPLICIT))
            assertEquals(AppDestination.PROFILE, h.manager.destination())
            assertEquals("off at the moment of the read", false to false, h.collectionWhenAccountRead)
            assertEquals(PendingConsent("uid-1", ConsentVersions.TERMS, true), h.pending.stored)
            assertEquals(listOf("sign_up_completed"), h.sink.names)
        }

    @Test
    fun googleFromSignUp_butTheAccountAlreadyExists_theAccountChoiceRules() =
        run { h ->
            h.manager.start()
            h.accounts.setLoaded(profileExists = true, hasMembership = true, analyticsConsent = false)
            h.manager.signInWithGoogle("token", ConsentChoice.IMPLICIT)
            assertEquals(AppDestination.FEED, h.manager.destination())
            assertFalse(
                "the account said no: the tap on the sign-up screen does not override it",
                h.privacy.analyticsEnabled.value,
            )
            assertTrue(h.sink.events.isEmpty())
            assertNull("pending consent is dropped once the profile exists", h.pending.stored)
        }

    // --- Sign-out (section 7.6, criteria X10 to X12) ------------------------------------------------------

    @Test
    fun signOut_stopsListeners_flushesWrites_purgesTheCache_thenClosesTheSession() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.accounts.setLoaded(profileExists = true, hasMembership = true, analyticsConsent = true)
            h.manager.start()
            assertTrue(h.privacy.analyticsEnabled.value)
            h.timeline.entries.clear()

            h.manager.signOut()

            assertEquals(listOf("markPending", "teardown", "flush", "purge", "signOut"), h.timeline.entries)
            assertEquals(AppDestination.SIGN_IN, h.manager.destination())
            assertNull(h.gateway.currentUid)
            assertFalse(h.privacy.analyticsEnabled.value)
            assertFalse(h.privacy.crashReportsEnabled.value)
            assertNull(h.pending.stored)
            assertNull("a voluntary sign-out has no message", h.manager.consumeNotice())
        }

    @Test
    fun signOut_forgetsTheAcceptanceRecordedOnThisDevice() =
        run { h ->
            h.manager.start()
            h.accounts.setLoaded(profileExists = false)
            h.manager.signInWithGoogle("token", signUpConsent = null)
            assertTrue(h.manager.acceptConsentPanel(ConsentChoice.IMPLICIT))
            assertNotNull("recorded until the profile exists", h.pending.stored)

            h.manager.signOut()

            assertNull("never applied to the next person", h.pending.stored)
        }

    @Test
    fun afterASignOut_anotherPersonStartsFromZero() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.accounts.setLoaded(profileExists = true, hasMembership = true, analyticsConsent = true)
            h.manager.start()
            h.manager.signOut()

            h.accounts.setLoaded(profileExists = false)
            h.gateway.signInResult = AuthOutcome.Success("uid-2", isNewUser = false)
            h.manager.signInWithEmail("other@b.fr", "password-2")

            assertEquals(AppDestination.CONSENT_PANEL, h.manager.destination())
            assertEquals("the previous person's choice is gone", false to false, h.collectionWhenAccountRead)
            assertFalse(h.privacy.analyticsEnabled.value)
        }

    @Test
    fun aSessionLostBehindOurBack_purges_andExplainsKindly() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.accounts.setLoaded(profileExists = true, hasMembership = true)
            h.manager.start()
            assertEquals(AppDestination.FEED, h.manager.destination())

            h.gateway.setSignedIn(null) // account disabled or deleted from the console

            assertEquals(AppDestination.SIGN_IN, h.manager.destination())
            assertEquals(SignedOutNotice.ACCOUNT_UNAVAILABLE, h.manager.consumeNotice())
            assertEquals(1, h.cache.purgeCalls)
            assertEquals(1, h.teardownCalls)
        }

    @Test
    fun theWatcherIgnoresSignOutsWhenNoOneWasSignedIn() =
        run { h ->
            h.manager.start()
            h.gateway.setSignedIn("uid-1")
            h.gateway.setSignedIn(null) // e.g. the debug diagnostic signing a test account in and out
            assertEquals(0, h.cache.purgeCalls)
            assertEquals(AppDestination.SIGN_UP, h.manager.destination())
        }

    @Test
    fun theEventNamesAreTheOnesOfTheContract() {
        assertEquals("sign_up_completed", AnalyticsEvent.SignUpCompleted(AnalyticsEvent.Method.EMAIL).name)
        assertEquals("sign_in_completed", AnalyticsEvent.SignInCompleted(AnalyticsEvent.Method.GOOGLE).name)
    }
}
