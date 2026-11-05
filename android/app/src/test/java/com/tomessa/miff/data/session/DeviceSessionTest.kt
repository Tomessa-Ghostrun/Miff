package com.tomessa.miff.data.session

import com.tomessa.miff.domain.auth.AppDestination
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.ConsentChoice
import com.tomessa.miff.domain.auth.GoogleTokenResult
import com.tomessa.miff.domain.session.DeviceIds
import com.tomessa.miff.testsupport.DEVICE_A
import com.tomessa.miff.testsupport.DEVICE_B
import com.tomessa.miff.testsupport.FakeGeofences
import com.tomessa.miff.testsupport.FakeLocal
import com.tomessa.miff.testsupport.FakeMovement
import com.tomessa.miff.testsupport.FakeNetworkWake
import com.tomessa.miff.testsupport.FakePosition
import com.tomessa.miff.testsupport.FakeTrackingEnv
import com.tomessa.miff.testsupport.FakeTrackingStore
import com.tomessa.miff.testsupport.SessionHarness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SESS-2, "un compte, un seul appareil", on the real session logic with a fake server, a fake private file and a fake network (the "fausse
 * passerelle"): sign-in, replacement by another phone, voluntary sign-out, no loop, offline then network back, first installation, installation from
 * before the feature. Criteria SES1, SES2, SES3 (status and position, see the engine tests), SES4, SES5.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSessionTest {
    private val uid = "uid-1"

    private fun SessionManager.destination(): AppDestination? = (state.value as? SessionState.Ready)?.destination

    private fun run(block: suspend TestScope.(SessionHarness) -> Unit) =
        runTest(UnconfinedTestDispatcher()) { block(SessionHarness(backgroundScope)) }

    private fun SessionHarness.signedInWithAFeed() {
        accounts.setLoaded(profileExists = true, hasMembership = true)
    }

    // --- Sign-in writes a session (a) ----------------------------------------------------------------------------------

    @Test
    fun signInWithEmail_writesAFreshSession_andKeepsTheIdentifierInThePrivateFile() =
        run { h ->
            h.signedInWithAFeed()
            h.manager.start()
            assertNull(h.manager.signInWithEmail("a@b.fr", "password-1"))
            val id = h.deviceIds.id
            assertTrue(DeviceIds.isValid(id))
            assertEquals(id, h.sessions.sessions[uid])
            assertEquals(listOf("write:$uid:$id"), h.sessions.changes)
            assertEquals(AppDestination.FEED, h.manager.destination())
        }

    @Test
    fun signInWithGoogle_writesASession() =
        run { h ->
            h.signedInWithAFeed()
            h.manager.start()
            assertNull(h.manager.signInWithGoogle("token", signUpConsent = null))
            assertEquals(h.deviceIds.id, h.sessions.sessions[uid])
            assertEquals(1, h.sessions.changes.size)
        }

    @Test
    fun theAccountJustCreated_writesASession_beforeAnythingElse() =
        run { h ->
            h.manager.start()
            assertNull(h.manager.createAccountWithEmail("a@b.fr", "password-1", ConsentChoice.IMPLICIT))
            assertEquals(h.deviceIds.id, h.sessions.sessions[uid])
            assertEquals(AppDestination.PROFILE, h.manager.destination())
        }

    @Test
    fun everySignInDrawsANewIdentifier() =
        run { h ->
            h.signedInWithAFeed()
            h.manager.start()
            h.manager.signInWithEmail("a@b.fr", "password-1")
            val first = h.deviceIds.id
            h.manager.signOut()
            h.manager.signInWithEmail("a@b.fr", "password-1")
            assertNotNull(first)
            assertTrue("a new identifier at each sign-in", h.deviceIds.id != first)
        }

    @Test
    fun theSecondPhone_takesTheSessionOver_whateverWasThere_andIsNotSignedOut_firstInstallation() =
        run { h ->
            // Another phone holds the session; this installation has never signed in (no identifier of its own).
            h.sessions.takeOver(uid, DEVICE_B)
            h.signedInWithAFeed()
            h.manager.start()
            assertNull(h.manager.signInWithEmail("a@b.fr", "password-1"))
            assertEquals(AppDestination.FEED, h.manager.destination())
            assertEquals("the last connection wins", h.deviceIds.id, h.sessions.sessions[uid])
            assertEquals(0, h.gateway.signOutCalls)
        }

    @Test
    fun aSignInThatCannotWriteItsSession_isUndone_withTheNoConnectionAnswer() =
        run { h ->
            h.sessions.offline = true
            h.manager.start()
            assertEquals(AuthFailure.NETWORK, h.manager.signInWithEmail("a@b.fr", "password-1"))
            assertNull("nobody stays signed in without a session", h.gateway.currentUid)
            assertNull(h.deviceIds.id)
            assertTrue(h.sessions.changes.isEmpty())
        }

    @Test
    fun aGoogleSignInToAnExistingAccount_thatCannotWriteItsSession_isUndoneToo() =
        run { h ->
            h.sessions.offline = true
            h.manager.start()
            assertEquals(AuthFailure.NETWORK, h.manager.signInWithGoogle("token", signUpConsent = null))
            assertNull(h.gateway.currentUid)
            assertTrue(h.sessions.changes.isEmpty())
        }

    @Test
    fun aNewAccount_staysSignedIn_whenItsSessionCannotBeWritten_andTheSessionComesAtTheReturnOfTheNetwork() =
        run { h ->
            h.manager.start()
            // The network drops right after the account was created.
            h.sessions.offline = true
            val failure = h.manager.createAccountWithEmail("a@b.fr", "password-1", ConsentChoice.IMPLICIT)
            assertNull("no failure shown: the account exists", failure)
            assertEquals("never undone: a second sign-up would say the address is used", 0, h.gateway.signOutCalls)
            assertEquals(1, h.gateway.createCalls)
            assertEquals(uid, h.gateway.currentUid)
            assertEquals("the acceptance of the terms is kept", uid, h.pending.stored?.uid)
            assertEquals(AppDestination.PROFILE, h.manager.destination())
            assertNull("no identifier kept for a session that does not exist", h.deviceIds.id)
            val clearance = h.device.clearance(recheck = false)
            assertEquals("nothing is written without a verified session", Clearance.Unverified, clearance)
            assertTrue(h.sessions.changes.isEmpty())

            // The network is back: no identifier here + no session there = the watcher writes the session itself.
            h.sessions.offline = false
            h.sessionNetwork.online = false
            h.sessionNetwork.online = true
            assertEquals(h.deviceIds.id, h.sessions.sessions[uid])
            assertTrue(DeviceIds.isValid(h.deviceIds.id))
            assertTrue(h.device.clearance(recheck = false) is Clearance.Cleared)
            assertEquals(0, h.gateway.signOutCalls)
            assertEquals(AppDestination.PROFILE, h.manager.destination())
        }

    // --- Another phone signs in: this one is signed out (b) ------------------------------------------------------------

    private suspend fun SessionHarness.signedInOnThisPhone(): String {
        signedInWithAFeed()
        manager.start()
        manager.signInWithEmail("a@b.fr", "password-1")
        return deviceIds.id!!
    }

    @Test
    fun whenAnotherPhoneSignsIn_thisOneIsSignedOutLocally_withTheCalmNote_andStopsEverything() =
        run { h ->
            val mine = h.signedInOnThisPhone()
            val changesBefore = h.sessions.changes.toList()

            h.sessions.takeOver(uid, DEVICE_B)

            assertEquals(AppDestination.SIGN_IN, h.manager.destination())
            assertNull("signed out of Firebase on this phone", h.gateway.currentUid)
            assertEquals("the zones, the listeners, the position: everything is stopped", 1, h.teardownCalls)
            assertEquals("the cache is purged (contract of the purge)", 1, h.cache.purgeCalls)
            assertEquals(SignedOutNotice.SESSION_REPLACED, h.manager.consumeNotice())
            assertNull("the identifier is forgotten", h.deviceIds.id)
            assertFalse(mine == h.deviceIds.id)
            // SES3 / "ne jamais effacer ni écrire de données d'un autre appareil": the server was not touched, the other phone keeps its session.
            assertEquals(changesBefore, h.sessions.changes)
            assertEquals(DEVICE_B, h.sessions.sessions[uid])
            // Order of the contract: the cache is marked, the listeners stop, the purge, then the sign-out. No flush (nothing may be written).
            val order = h.timeline.entries
            assertTrue(order.indexOf("markPending") < order.indexOf("teardown"))
            assertTrue(order.indexOf("teardown") < order.indexOf("purge"))
            assertTrue(order.indexOf("purge") < order.lastIndexOf("signOut"))
            assertFalse("flush" in order)
        }

    @Test
    fun theNoteIsNeverAnAccessLostToAMiff() =
        run { h ->
            h.signedInOnThisPhone()
            h.sessions.takeOver(uid, DEVICE_B)
            val notice = h.manager.consumeNotice()
            // Its own notice: neither the "account unavailable" one nor anything the Miff directory understands as a removal (criteria D3, SES5).
            assertEquals(SignedOutNotice.SESSION_REPLACED, notice)
            assertTrue(notice != SignedOutNotice.ACCOUNT_UNAVAILABLE)
        }

    @Test
    fun theSessionDisappearing_whileSignedIn_alsoSignsOut_andWritesNothingBack() =
        run { h ->
            h.signedInOnThisPhone()
            val changesBefore = h.sessions.changes.toList()
            // The account was deleted from another phone: the session document is gone.
            h.sessions.remove(uid)
            assertEquals(AppDestination.SIGN_IN, h.manager.destination())
            assertEquals(SignedOutNotice.SESSION_REPLACED, h.manager.consumeNotice())
            assertEquals("no loop: nothing is written back", changesBefore, h.sessions.changes)
        }

    @Test
    fun aForcedSignOut_neverRewritesNorDeletesTheSession_loopImpossible() =
        run { h ->
            h.signedInOnThisPhone()
            h.sessions.takeOver(uid, DEVICE_B)
            val changes = h.sessions.changes.toList()
            val purges = h.cache.purgeCalls

            // Every way the same replacement could be noticed again, and the network coming and going: nothing happens twice, nothing is written.
            h.manager.onDeviceReplaced()
            h.manager.onDeviceReplaced()
            h.device.ensure(uid)
            h.sessionNetwork.online = false
            h.sessionNetwork.online = true
            h.sessions.takeOver(uid, DEVICE_B)

            assertEquals(changes, h.sessions.changes)
            assertEquals(purges, h.cache.purgeCalls)
            assertEquals(1, h.gateway.signOutCalls)
            assertEquals(DEVICE_B, h.sessions.sessions[uid])
        }

    @Test
    fun afterAForcedSignOut_signingInAgainTakesTheSessionBack_thatIsTheOnlyWayToWrite() =
        run { h ->
            h.signedInOnThisPhone()
            h.sessions.takeOver(uid, DEVICE_B)
            assertEquals(Clearance.Replaced, h.device.clearance(recheck = false))
            h.manager.signInWithEmail("a@b.fr", "password-1")
            val id = h.deviceIds.id
            assertEquals(id, h.sessions.sessions[uid])
            assertEquals(id, (h.device.clearance(recheck = true) as Clearance.Cleared).deviceId)
        }

    // --- Voluntary sign-out (a) -------------------------------------------------------------------------------------

    @Test
    fun aVoluntarySignOut_deletesTheSessionWhenItIsOurs_beforeThePurgeAndTheSignOut() =
        run { h ->
            h.signedInOnThisPhone()
            h.manager.signOut()
            assertNull("the session is released", h.sessions.sessions[uid])
            assertNull(h.deviceIds.id)
            assertEquals(1, h.sessions.changes.count { it.startsWith("delete:") })
            val order = h.timeline.entries
            assertTrue(order.indexOf("flush") < order.indexOf("session-delete"))
            assertTrue(
                "deleted BEFORE the client of Firestore is terminated",
                order.indexOf("session-delete") < order.indexOf("purge"),
            )
            assertTrue(order.indexOf("purge") < order.lastIndexOf("signOut"))
            assertNull("our own deletion is not a replacement", h.manager.consumeNotice())
            assertEquals(1, h.gateway.signOutCalls)
        }

    @Test
    fun aVoluntarySignOut_neverDeletesTheSessionOfAnotherPhone() =
        run { h ->
            h.signedInOnThisPhone()
            // Another phone took over, and this one has not noticed yet (it was offline for an instant): no event reaches it.
            h.sessions.sessions[uid] = DEVICE_B
            h.manager.signOut()
            assertEquals("the other phone keeps its session", DEVICE_B, h.sessions.sessions[uid])
            assertTrue(h.sessions.changes.none { it.startsWith("delete:") })
            assertNull(h.deviceIds.id)
        }

    @Test
    fun aVoluntarySignOut_offline_deletesNothing_andStillSignsOut() =
        run { h ->
            val mine = h.signedInOnThisPhone()
            h.sessionNetwork.online = false
            h.sessions.offline = true
            h.manager.signOut()
            assertEquals("nothing could be deleted: the next sign-in overwrites it", mine, h.sessions.sessions[uid])
            assertNull(h.gateway.currentUid)
            assertNull(h.deviceIds.id)
        }

    // --- A stop that is late does not keep the person on "Chargement" (review of PR #80) -----------------------------------------------

    private fun harnessWithAStopThatNeverEnds(
        scope: kotlinx.coroutines.CoroutineScope,
    ): Pair<SessionHarness, () -> Int> {
        var after = 0
        val h =
            SessionHarness(
                scope,
                extraTeardown = listOf(SessionTeardown { awaitCancellation() }, SessionTeardown { after++ }),
            )
        return h to { after }
    }

    @Test
    fun aVoluntarySignOut_waitsFiveSecondsAtMost_forAStopThatNeverEnds_andTheOthersStillRun() =
        runTest(UnconfinedTestDispatcher()) {
            val (h, after) = harnessWithAStopThatNeverEnds(backgroundScope)
            h.signedInWithAFeed()
            h.manager.start()
            h.manager.signInWithEmail("a@b.fr", "password-1")
            val before = currentTime
            h.manager.signOut()
            assertEquals(AppDestination.SIGN_IN, h.manager.destination())
            val waited = currentTime - before
            assertTrue("waited 5 s at most for the stop that hangs", waited <= SessionManager.STOP_WAIT_MILLIS)
            assertEquals("the stop after the hanging one still ran", 1, after())
        }

    @Test
    fun aForcedSignOut_waitsFiveSecondsAtMost_forAStopThatNeverEnds_andTheOthersStillRun() =
        runTest(UnconfinedTestDispatcher()) {
            val (h, after) = harnessWithAStopThatNeverEnds(backgroundScope)
            h.signedInWithAFeed()
            h.manager.start()
            h.manager.signInWithEmail("a@b.fr", "password-1")
            h.sessions.takeOver(uid, DEVICE_B)
            // The forced sign-out waits for the hanging stop, but not longer than the bound.
            advanceTimeBy(SessionManager.STOP_WAIT_MILLIS - 1)
            assertEquals(SessionState.Loading, h.manager.state.value)
            advanceTimeBy(2)
            runCurrent()
            assertEquals(AppDestination.SIGN_IN, h.manager.destination())
            assertEquals("the stop after the hanging one still ran", 1, after())
        }

    // --- Launch (c) --------------------------------------------------------------------------------------------------------

    @Test
    fun launch_withOurSession_staysSignedIn_andWritesNothing() =
        run { h ->
            h.deviceIds.id = DEVICE_A
            h.sessions.sessions[uid] = DEVICE_A
            h.gateway.setSignedIn(uid)
            h.signedInWithAFeed()
            h.manager.start()
            assertEquals(AppDestination.FEED, h.manager.destination())
            assertTrue(h.sessions.changes.isEmpty())
            assertEquals(1, h.sessions.reads)
        }

    @Test
    fun launch_aPhoneInstalledBeforeTheFeature_hasNoSession_soItWritesOne() =
        run { h ->
            h.gateway.setSignedIn(uid)
            h.signedInWithAFeed()
            h.manager.start()
            assertEquals(AppDestination.FEED, h.manager.destination())
            assertEquals(1, h.sessions.changes.size)
            assertEquals(h.deviceIds.id, h.sessions.sessions[uid])
            assertTrue(DeviceIds.isValid(h.deviceIds.id))
        }

    @Test
    fun launch_anotherPhonesSession_andNoIdentifierHere_signsOut() =
        run { h ->
            h.sessions.sessions[uid] = DEVICE_B
            h.gateway.setSignedIn(uid)
            h.signedInWithAFeed()
            h.manager.start()
            assertEquals(AppDestination.SIGN_IN, h.manager.destination())
            assertEquals(SignedOutNotice.SESSION_REPLACED, h.manager.consumeNotice())
            assertTrue("nothing written, nothing deleted", h.sessions.changes.isEmpty())
            assertEquals(DEVICE_B, h.sessions.sessions[uid])
        }

    @Test
    fun launch_aDifferentIdentifier_signsOut_beforeAnythingIsWritten() =
        run { h ->
            h.deviceIds.id = DEVICE_A
            h.sessions.sessions[uid] = DEVICE_B
            h.gateway.setSignedIn(uid)
            h.signedInWithAFeed()
            h.manager.start()
            assertEquals(AppDestination.SIGN_IN, h.manager.destination())
            assertTrue(h.sessions.changes.isEmpty())
            assertNull(h.deviceIds.id)
        }

    @Test
    fun launch_anIdentifierHereButNoSessionAnyMore_signsOut() =
        run { h ->
            h.deviceIds.id = DEVICE_A
            h.gateway.setSignedIn(uid)
            h.signedInWithAFeed()
            h.manager.start()
            assertEquals(AppDestination.SIGN_IN, h.manager.destination())
            assertTrue("the vanished session is not written back", h.sessions.changes.isEmpty())
        }

    @Test
    fun launch_replaced_neverShowsTheFeed_notEvenForAMoment() =
        run { h ->
            h.deviceIds.id = DEVICE_A
            h.sessions.sessions[uid] = DEVICE_B
            h.gateway.setSignedIn(uid)
            h.signedInWithAFeed()
            val seen = mutableListOf<AppDestination?>()
            val job =
                backgroundScope.launch {
                    h.manager.state.collect {
                        seen +=
                            (it as? SessionState.Ready)?.destination
                    }
                }
            h.manager.start()
            job.cancel()
            assertFalse(AppDestination.FEED in seen)
            assertTrue(AppDestination.SIGN_IN in seen)
        }

    // --- Offline, then the network is back (SES2) -------------------------------------------------------------------

    @Test
    fun offlineAtLaunch_theAppOpens_butIsNotVerified_thenTheNetworkBringsTheVerification() =
        run { h ->
            h.deviceIds.id = DEVICE_A
            h.sessions.sessions[uid] = DEVICE_A
            h.gateway.setSignedIn(uid)
            h.signedInWithAFeed()
            h.sessionNetwork.online = false
            h.sessions.offline = true
            h.manager.start()
            assertEquals(
                "an offline phone is not signed out (limit validated by the PM)",
                AppDestination.FEED,
                h.manager.destination(),
            )
            assertEquals(
                "and it may not write: the session could not be verified",
                Clearance.Unverified,
                h.device.clearance(recheck = false),
            )

            h.sessions.offline = false
            h.sessionNetwork.online = true
            assertEquals(AppDestination.FEED, h.manager.destination())
            assertEquals(DEVICE_A, (h.device.clearance(recheck = false) as Clearance.Cleared).deviceId)
        }

    @Test
    fun offlineAndReplaced_isSignedOutAtTheReturnOfTheNetwork_beforeAnyWrite() =
        run { h ->
            h.signedInOnThisPhone()
            h.sessionNetwork.online = false
            h.sessions.offline = true
            // The other phone signs in while this one has no network: nothing reaches it.
            h.sessions.sessions[uid] = DEVICE_B
            assertEquals(AppDestination.FEED, h.manager.destination())
            assertEquals(Clearance.Unverified, h.device.clearance(recheck = true))

            h.sessions.offline = false
            h.sessionNetwork.online = true
            assertEquals(AppDestination.SIGN_IN, h.manager.destination())
            assertEquals(SignedOutNotice.SESSION_REPLACED, h.manager.consumeNotice())
            assertEquals(DEVICE_B, h.sessions.sessions[uid])
            assertTrue(
                "the session of the other phone is never touched",
                h.sessions.changes.none { it.startsWith("delete:") },
            )
        }

    // --- The note survives a cold start (a receiver may have signed the phone out while the app was closed) -----------

    @Test
    fun theNote_survivesAColdStart_andIsShownOnce() =
        run { h ->
            h.signedInOnThisPhone()
            h.sessions.takeOver(uid, DEVICE_B)
            assertTrue(h.flags.get(SessionManager.KEY_REPLACED_NOTICE_PENDING, false))

            // A new process: the sign-in screen has not been shown, the in-memory notice is gone, the flag is on the disk.
            val next = SessionHarness(backgroundScope)
            next.flags.put(SessionManager.KEY_REPLACED_NOTICE_PENDING, true)
            next.manager.start()
            assertEquals(SignedOutNotice.SESSION_REPLACED, next.manager.consumeNotice())
            assertNull(next.manager.consumeNotice())
            assertFalse(next.flags.get(SessionManager.KEY_REPLACED_NOTICE_PENDING, false))
        }

    @Test
    fun theNote_isGoneOnceTheScreenShowedIt_andAtTheNextSignIn() =
        run { h ->
            h.signedInOnThisPhone()
            h.sessions.takeOver(uid, DEVICE_B)
            h.manager.signInWithEmail("a@b.fr", "password-1")
            assertFalse(h.flags.get(SessionManager.KEY_REPLACED_NOTICE_PENDING, false))
        }

    // --- Refusing the consent panel deletes the account: its session goes first ---------------------------------------------

    @Test
    fun refusingThePanel_releasesTheSessionBeforeTheAccountIsDeleted() =
        run { h ->
            h.accounts.setLoaded(profileExists = false)
            h.manager.start()
            h.manager.signInWithGoogle("token", signUpConsent = null)
            h.timeline.entries.clear()
            h.manager.refuseConsentPanel { GoogleTokenResult.Unavailable }
            assertNull(h.sessions.sessions[uid])
            val order = h.timeline.entries
            assertTrue(
                "the session is deleted while we are still signed in",
                order.indexOf("session-delete") < order.indexOf("delete"),
            )
        }

    @Test
    fun refusingThePanel_whenTheAccountIsKeptAfterAll_writesTheSessionAgain() =
        run { h ->
            h.accounts.setLoaded(profileExists = false)
            h.manager.start()
            h.manager.signInWithGoogle("token", signUpConsent = null)
            h.gateway.deleteResults.addLast(AuthFailure.UNKNOWN)
            h.manager.refuseConsentPanel { GoogleTokenResult.Unavailable }
            assertEquals("still signed in, with a session", h.deviceIds.id, h.sessions.sessions[uid])
            assertNotNull(h.deviceIds.id)
            assertEquals(AppDestination.CONSENT_PANEL, h.manager.destination())
        }

    // --- The detection engine, wired to the real session (SES2, SES3) ---------------------------------------------------------

    @Test
    fun aPhoneReplacedWhileOffline_neverWritesItsWaitingStatus_atTheReturnOfTheNetwork() =
        run { h ->
            h.signedInOnThisPhone()
            val env = FakeTrackingEnv().also { it.uid = uid }
            val store = FakeTrackingStore()
            val local = FakeLocal()
            val engine =
                com.tomessa.miff.data.tracking.TrackingEngine(
                    env,
                    store,
                    local,
                    FakePosition(env),
                    FakeGeofences(),
                    FakeMovement(),
                    FakeNetworkWake(),
                    h.device,
                    timeoutMillis = 5_000,
                )
            store.memberships =
                listOf(
                    com.tomessa.miff.domain.miff
                        .MembershipRef("k", "miffA"),
                )
            store.consents["miffA"] =
                com.tomessa.miff.data.miff
                    .SharingConsentRead(true, "2026-11-v03")
            val home =
                com.tomessa.miff.domain.tracking.PlaceFact(
                    "home",
                    48.8566,
                    2.3522,
                    100,
                    com.tomessa.miff.domain.place.PlaceType.TRUSTED,
                )
            store.places["miffA"] = listOf(home)
            engine.refresh(com.tomessa.miff.data.tracking.TrackingEngine.Reason.OPENED)
            engine.onTransition(com.tomessa.miff.domain.tracking.TransitionKind.ENTER, home.key, env.now)
            // This phone, still the active one, wrote with the identifier of its session.
            assertTrue(store.writesTo("/status/").all { it.data["deviceId"] == h.deviceIds.id })
            assertTrue(store.writesTo("/positions/").all { it.data["deviceId"] == h.deviceIds.id })
            store.commits.clear()

            // The phone goes offline, leaves home (the status waits), and the other phone signs in.
            env.online = false
            h.sessionNetwork.online = false
            h.sessions.offline = true
            env.now += 3_600_000
            engine.onTransition(com.tomessa.miff.domain.tracking.TransitionKind.EXIT, home.key, env.now)
            h.sessions.sessions[uid] = DEVICE_B
            assertTrue(store.writesTo("/status/").isEmpty())

            env.online = true
            h.sessions.offline = false
            engine.onNetworkBack()

            assertTrue("a replaced phone never writes what it kept (SES2)", store.writesTo("/status/").isEmpty())
            assertTrue(store.writesTo("/positions/").isEmpty())
            assertEquals(AppDestination.SIGN_IN, h.manager.destination())
            assertEquals(DEVICE_B, h.sessions.sessions[uid])
        }
}
