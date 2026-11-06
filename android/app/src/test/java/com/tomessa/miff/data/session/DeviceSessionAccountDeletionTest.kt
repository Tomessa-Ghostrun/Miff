package com.tomessa.miff.data.session

import com.tomessa.miff.data.account.AccountDeletionMark
import com.tomessa.miff.domain.auth.AppDestination
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.GoogleTokenResult
import com.tomessa.miff.testsupport.DEVICE_B
import com.tomessa.miff.testsupport.MemoryDeletionMarker
import com.tomessa.miff.testsupport.SessionHarness
import com.tomessa.miff.testsupport.Timeline
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SESS-2 and the deletion of the account (AUTH-5a), integrated after the merge of #75: the session of the account goes before its profile with the
 * acknowledgement of the server, an account whose deletion is under way never gets a session verified or written (launch, sign-in again), and the
 * "used on another phone" note never appears during a deletion. Criteria SES4, SES5.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSessionAccountDeletionTest {
    private val uid = "uid-1"
    private val timeline = Timeline()
    private val marker = MemoryDeletionMarker(timeline = timeline)

    private fun SessionManager.destination(): AppDestination? = (state.value as? SessionState.Ready)?.destination

    private fun run(block: suspend TestScope.(SessionHarness) -> Unit) =
        runTest(UnconfinedTestDispatcher()) {
            block(SessionHarness(backgroundScope, deletionMarker = marker, timeline = timeline))
        }

    private suspend fun SessionHarness.signedIn() {
        accounts.setLoaded(profileExists = true, hasMembership = false)
        manager.start()
        manager.signInWithEmail("a@b.fr", "password-1")
    }

    // --- (b) The deletion in progress is resolved BEFORE the session: nothing verified, nothing written ---------------------------

    @Test
    fun aLaunchWithADeletionPending_neverVerifiesNorWritesASession() =
        run { h ->
            marker.stored = AccountDeletionMark(uid, appleLinked = false)
            h.gateway.setSignedIn(uid)
            h.manager.start()
            assertEquals(AppDestination.ACCOUNT_DELETION, h.manager.destination())
            assertEquals("the session was not even read", 0, h.sessions.reads)
            assertTrue("and none was written", h.sessions.changes.isEmpty())
            assertNull(h.deviceIds.id)
            assertEquals(Clearance.Unverified, h.device.clearance(recheck = true))
            assertNull(h.manager.consumeNotice())
        }

    @Test
    fun signingInAgainToTheAccountBeingDeleted_writesNoSession() =
        run { h ->
            marker.stored = AccountDeletionMark(uid, appleLinked = false)
            // Nobody signed in: the session was closed behind our back during the deletion.
            h.manager.start()
            assertNull(h.manager.signInWithEmail("a@b.fr", "password-1"))
            assertEquals(AppDestination.ACCOUNT_DELETION, h.manager.destination())
            assertTrue("no session for an account that is going away", h.sessions.changes.isEmpty())
            assertNull(h.deviceIds.id)
            assertEquals(Clearance.Unverified, h.device.clearance(recheck = true))
        }

    @Test
    fun aMarkerOfAnotherAccount_changesNothingForThisOne() =
        run { h ->
            marker.stored = AccountDeletionMark("uid-other", appleLinked = false)
            h.accounts.setLoaded(profileExists = true, hasMembership = true)
            h.manager.start()
            assertNull(h.manager.signInWithEmail("a@b.fr", "password-1"))
            assertEquals(h.deviceIds.id, h.sessions.sessions[uid])
        }

    // --- (a) The session goes before the profile, with the acknowledgement ---------------------------------------------------

    @Test
    fun theDeletionOfTheSession_isAcknowledged_thenTheIdentifierIsForgotten() =
        run { h ->
            h.signedIn()
            h.manager.beginAccountDeletion()
            h.device.releaseForAccountDeletion(uid)
            assertNull(h.sessions.sessions[uid])
            assertNull("forgotten only after the acknowledgement", h.deviceIds.id)
            assertEquals(Clearance.Unverified, h.device.clearance(recheck = true))
        }

    @Test
    fun withoutTheAcknowledgement_theIdentifierIsKept_theErrorIsThrown_andTheRetryGoesThrough() =
        run { h ->
            h.signedIn()
            val id = h.deviceIds.id
            h.manager.beginAccountDeletion()
            h.sessions.offline = true
            val failure = runCatching { h.device.releaseForAccountDeletion(uid) }.exceptionOrNull()
            assertNotNull("no acknowledgement = the deletion of the account must not go on", failure)
            assertEquals(id, h.deviceIds.id)
            assertEquals(id, h.sessions.sessions[uid])
            assertEquals(
                "still closed: nothing is written meanwhile",
                Clearance.Unverified,
                h.device.clearance(recheck = true),
            )

            h.sessions.offline = false
            h.device.releaseForAccountDeletion(uid)
            assertNull(h.sessions.sessions[uid])
            assertNull(h.deviceIds.id)
        }

    @Test
    fun theSessionOfAnotherPhone_isDeletedToo_theAccountIsGoingAway() =
        run { h ->
            h.signedIn()
            h.manager.beginAccountDeletion()
            h.sessions.sessions[uid] = DEVICE_B
            h.device.releaseForAccountDeletion(uid)
            assertNull("whoever holds it: the other phone sees it vanish and signs out", h.sessions.sessions[uid])
        }

    // --- (c) Never the "another phone" note during a deletion ------------------------------------------------------------------

    @Test
    fun ourOwnDeletionOfTheSession_isNotAReplacement_noSignOut_noNote() =
        run { h ->
            h.signedIn()
            h.manager.beginAccountDeletion()
            h.device.releaseForAccountDeletion(uid)
            assertEquals(AppDestination.ACCOUNT_DELETION, h.manager.destination())
            assertEquals(0, h.gateway.signOutCalls)
            assertNull(h.manager.consumeNotice())
            assertFalse(h.flags.get(SessionManager.KEY_REPLACED_NOTICE_PENDING, false))
        }

    @Test
    fun anotherPhoneSigningInDuringTheDeletion_changesNothing_noNote() =
        run { h ->
            h.signedIn()
            h.manager.beginAccountDeletion()
            h.sessions.takeOver(uid, DEVICE_B)
            assertEquals(AppDestination.ACCOUNT_DELETION, h.manager.destination())
            assertEquals(0, h.gateway.signOutCalls)
            assertNull(h.manager.consumeNotice())
        }

    @Test
    fun aForcedSignOutAskedDuringTheDeletion_isIgnored_noNote() =
        run { h ->
            h.signedIn()
            h.manager.beginAccountDeletion()
            h.manager.onDeviceReplaced()
            assertEquals(AppDestination.ACCOUNT_DELETION, h.manager.destination())
            assertEquals(0, h.gateway.signOutCalls)
            assertEquals(0, h.cache.purgeCalls)
            assertNull(h.manager.consumeNotice())
        }

    // --- A cancelled deletion keeps the account: the session comes back ----------------------------------------------------------

    @Test
    fun aDeletionCancelledAfterTheSessionWasDeleted_writesANewSession_thePhoneIsTheActiveOneAgain() =
        run { h ->
            h.signedIn()
            h.manager.beginAccountDeletion()
            h.device.releaseForAccountDeletion(uid)
            marker.stored = null // the controller erases it BEFORE calling cancel
            h.manager.cancelAccountDeletion()
            assertEquals(AppDestination.MIFF_CHOICE, h.manager.destination())
            assertNotNull(h.deviceIds.id)
            assertEquals(h.deviceIds.id, h.sessions.sessions[uid])
            assertTrue(h.device.clearance(recheck = false) is Clearance.Cleared)
            assertEquals(0, h.gateway.signOutCalls)
        }

    @Test
    fun aDeletionCancelledBeforeTheSessionWasTouched_keepsTheSameSession_andListensAgain() =
        run { h ->
            h.signedIn()
            val id = h.deviceIds.id
            val writes = h.sessions.changes.toList()
            h.manager.beginAccountDeletion()
            marker.stored = null
            h.manager.cancelAccountDeletion()
            assertEquals(id, h.deviceIds.id)
            assertEquals(writes, h.sessions.changes)
            // Listening again: another phone signing in is heard.
            h.sessions.takeOver(uid, DEVICE_B)
            assertEquals(AppDestination.SIGN_IN, h.manager.destination())
        }

    @Test
    fun aDeletionCancelledWhileAnotherPhoneHoldsTheSession_isReplaced_notAStolenSession() =
        run { h ->
            h.signedIn()
            h.manager.beginAccountDeletion()
            h.sessions.sessions[uid] = DEVICE_B
            marker.stored = null
            h.manager.cancelAccountDeletion()
            assertEquals(AppDestination.SIGN_IN, h.manager.destination())
            assertEquals(DEVICE_B, h.sessions.sessions[uid])
            assertEquals(SignedOutNotice.SESSION_REPLACED, h.manager.consumeNotice())
        }

    // --- The refusal of the panel that KEEPS the account (state closed after the release) --------------------------------------

    @Test
    fun theRefusalOfThePanelThatKeepsTheAccount_reopensTheSession_afterTheRelease() =
        run { h ->
            h.accounts.setLoaded(profileExists = false)
            h.manager.start()
            h.manager.signInWithGoogle("token", signUpConsent = null)
            h.gateway.deleteResults.addLast(AuthFailure.UNKNOWN)
            h.manager.refuseConsentPanel { GoogleTokenResult.Unavailable }
            // The release closed the session, the refusal reopened it (begin): the account is kept, the phone writes again.
            assertTrue(h.device.clearance(recheck = true) is Clearance.Cleared)
            assertEquals(h.deviceIds.id, h.sessions.sessions[uid])
            assertEquals(AppDestination.CONSENT_PANEL, h.manager.destination())
        }

    // --- The end of the deletion --------------------------------------------------------------------------------------------------

    @Test
    fun whenTheAccountIsDeleted_theIdentifierIsForgotten_andAnotherAccountCanSignInAfterwards() =
        run { h ->
            h.signedIn()
            h.manager.beginAccountDeletion()
            h.device.releaseForAccountDeletion(uid)
            h.manager.eraseAfterAccountDeletion(uid)
            assertNull(h.deviceIds.id)
            assertNull(h.sessions.sessions[uid])
            // Another sign-in (a new account, say) reopens the session as usual.
            h.accounts.setLoaded(profileExists = true, hasMembership = true)
            marker.stored = null
            assertNull(h.manager.signInWithEmail("b@c.fr", "password-2"))
            assertTrue(h.device.clearance(recheck = false) is Clearance.Cleared)
        }
}
