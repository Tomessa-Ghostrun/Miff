package com.tomessa.miff.data

import com.tomessa.miff.data.account.AccountDeletionMark
import com.tomessa.miff.data.miff.LostMiff
import com.tomessa.miff.data.miff.MiffDirectory
import com.tomessa.miff.data.miff.MiffRepository
import com.tomessa.miff.data.miff.MyMiffsLoad
import com.tomessa.miff.data.privacy.PendingWithdrawals
import com.tomessa.miff.data.privacy.WithdrawalStorage
import com.tomessa.miff.data.session.LocalDataEraser
import com.tomessa.miff.data.session.SessionManager
import com.tomessa.miff.data.session.SessionState
import com.tomessa.miff.domain.auth.AppDestination
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.testsupport.FakeCachePurger
import com.tomessa.miff.testsupport.FakeMiffRepository
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.MemoryActiveMiffStore
import com.tomessa.miff.testsupport.MemoryDeletionMarker
import com.tomessa.miff.testsupport.SessionHarness
import com.tomessa.miff.testsupport.Timeline
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The session and the deletion of the account (ticket AUTH-5, sections 5.5, 5.6 and 5.10): the marker has PRIORITY over the routing, a
 * session that is gone is a success, the local data is erased in the right order, nothing is sent, the loss watcher does not take the end
 * of our own session for a loss (criteria SC15, SC27, SC28, SC29, SC35 to SC39, SA5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerAccountDeletionTest {
    private val timeline = Timeline()
    private val marker = MemoryDeletionMarker(timeline = timeline)
    private val erasedFor = mutableListOf<String>()

    private fun SessionManager.destination(): AppDestination? = (state.value as? SessionState.Ready)?.destination

    private fun run(block: suspend TestScope.(SessionHarness) -> Unit) =
        runTest(UnconfinedTestDispatcher()) {
            block(
                SessionHarness(
                    backgroundScope,
                    deletionMarker = marker,
                    localData =
                        listOf(
                            LocalDataEraser { uid ->
                                erasedFor += uid
                                timeline += "localData"
                            },
                        ),
                    timeline = timeline,
                ),
            )
        }

    // --- Priority over the routing ---------------------------------------------------------------------------

    @Test
    fun aMarkerOfThisAccount_prevailsOverTheRouting_noAccountIsRead() =
        run { h ->
            marker.stored = AccountDeletionMark("uid-1", appleLinked = false)
            h.gateway.setSignedIn("uid-1")
            h.manager.start()
            assertEquals(AppDestination.ACCOUNT_DELETION, h.manager.destination())
            assertEquals(0, h.accounts.loadCalls)
        }

    @Test
    fun theProfileIsAlreadyDeleted_theAppNeverGoesBackToCreateTheProfile() =
        run { h ->
            // Killed between the deletion of the profile and the one of the account: the profile is gone, the session is still there.
            marker.stored = AccountDeletionMark("uid-1", appleLinked = false)
            h.gateway.setSignedIn("uid-1")
            h.accounts.setLoaded(profileExists = false)
            h.manager.start()
            assertEquals(AppDestination.ACCOUNT_DELETION, h.manager.destination())
            assertFalse(h.manager.destination() == AppDestination.PROFILE)
            assertFalse(h.manager.destination() == AppDestination.CONSENT_PANEL)
        }

    @Test
    fun withTheMarker_whatRunsIsStoppedAtOnce_whenTheDeletionIsTakenUp() =
        run { h ->
            marker.stored = AccountDeletionMark("uid-1", appleLinked = false)
            h.gateway.setSignedIn("uid-1")
            h.manager.start()
            assertEquals(1, h.teardownCalls)
        }

    @Test
    fun aMarkerOfAnotherAccount_isIgnoredAndErased_theRoutingIsTheUsualOne() =
        run { h ->
            marker.stored = AccountDeletionMark("uid-other", appleLinked = false)
            h.gateway.setSignedIn("uid-1")
            h.accounts.setLoaded(profileExists = true, hasMembership = true)
            h.manager.start()
            assertNull(marker.stored)
            assertEquals(AppDestination.FEED, h.manager.destination())
            assertEquals(0, h.teardownCalls)
        }

    // --- A session that is gone = success --------------------------------------------------------------------

    @Test
    fun aMarkerButNoSessionAnyMore_isASuccess_theDataIsErased_theCarouselSaysItOnce() =
        run { h ->
            marker.stored = AccountDeletionMark("uid-1", appleLinked = false)
            h.gateway.setSignedIn(null)
            h.manager.start()
            assertEquals(AppDestination.SIGN_UP, h.manager.destination())
            assertNull(marker.stored)
            assertEquals(listOf("uid-1"), erasedFor)
            assertEquals(1, h.cache.purgeCalls)
            // Once: the banner and the carousel.
            assertTrue(h.manager.consumeAccountDeletedNotice())
            assertFalse(h.manager.consumeAccountDeletedNotice())
            assertTrue(h.manager.consumeCarouselAfterDeletion())
            assertFalse(h.manager.consumeCarouselAfterDeletion())
            // And it is not the "session lost" message of the sign-in screen.
            assertNull(h.manager.consumeNotice())
        }

    // --- Our own end of session is not a loss ----------------------------------------------------------------

    @Test
    fun theEndOfOurOwnSession_isNeverTakenForALoss() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.accounts.setLoaded(profileExists = true, hasMembership = true)
            h.manager.start()
            h.manager.beginAccountDeletion()
            // The authentication account is deleted: the session ends, on purpose.
            h.gateway.setSignedIn(null)
            assertEquals(AppDestination.ACCOUNT_DELETION, h.manager.destination())
            assertNull(h.manager.consumeNotice())
            assertEquals(0, h.cache.purgeCalls)
        }

    // --- Step 0: nothing may react to the documents disappearing (criterion SC15) -----------------------------

    @Test
    fun aListenerRefusedWhileTheDeletionRuns_neverStartsTheAccessLostClean_up() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = CompletableDeferred<Unit>()
            val inner = FakeMiffRepository()
            val cleanUps = mutableListOf<String>()
            val directoryCache = FakeCachePurger(Timeline())
            val repository =
                object : MiffRepository by inner {
                    override suspend fun myMiffs(uid: String): MyMiffsLoad {
                        gate.await()
                        // The server now refuses: the index is "lost" (what the documents disappearing would look like).
                        return MyMiffsLoad.Loaded(
                            emptyList(),
                            listOf(LostMiff(MembershipRef("free", MIFF_ID), "Chez nous")),
                            premium = false,
                            fromCache = false,
                        )
                    }

                    override suspend fun cleanUpLost(
                        uid: String,
                        lost: LostMiff,
                    ): Boolean {
                        cleanUps += lost.ref.miffId
                        return true
                    }
                }
            val directory = MiffDirectory(repository, { "uid-1" }, MemoryActiveMiffStore(), directoryCache)
            val h = SessionHarness(backgroundScope, extraTeardown = listOf(directory), deletionMarker = marker)
            h.gateway.setSignedIn("uid-1")
            h.accounts.setLoaded(profileExists = true, hasMembership = true)
            h.manager.start()
            // A read of the Miffs is under way when the deletion begins...
            val reading = backgroundScope.launch { directory.refresh() }
            h.manager.beginAccountDeletion()
            // ...and the server answers "refused" only now.
            gate.complete(Unit)
            reading.join()
            assertTrue("no message of lost access", directory.notice.value.isEmpty())
            assertTrue("no clean-up of an index", cleanUps.isEmpty())
            assertEquals("no purge started by the directory", 0, directoryCache.purgeCalls)
        }

    // --- Step 9: the local data (tickets 5.5 and 5.10; criteria SC35 to SC39) -----------------------------------

    @Test
    fun theLocalCleaningHasAnOrder_purgeRecordedFirst_theMarkerErasedLast() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.manager.start()
            timeline.entries.clear()
            h.manager.eraseAfterAccountDeletion("uid-1")
            val order = timeline.entries
            // The purge is recorded, what runs is stopped, the cache purged, the data of the account erased, the session closed, the marker last.
            assertEquals(listOf("markPending", "teardown", "purge", "localData", "signOut", "marker.clear"), order)
        }

    @Test
    fun theCollectionIsCutAndTheInstallationForgotten_andNothingIsSent() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.accounts.setLoaded(profileExists = true, hasMembership = true, analyticsConsent = true)
            h.manager.start()
            h.manager.eraseAfterAccountDeletion("uid-1")
            assertEquals(1, h.analyticsSwitch.forgetCalls)
            assertEquals(1, h.crashSwitch.forgetCalls)
            assertFalse(h.privacy.analyticsEnabled.value)
            assertFalse(h.privacy.crashReportsEnabled.value)
            // No event for the deletion itself (criterion D6).
            assertTrue(h.sink.events.none { it.first.contains("delet") || it.first.contains("account") })
        }

    @Test
    fun theLanguageAndTheCarouselFlag_areKept_theConsentAndWithdrawalsAreErased() =
        run { h ->
            val storage =
                object : WithdrawalStorage {
                    var entries = emptyMap<String, Long>()

                    override fun read() = entries

                    override fun write(entries: Map<String, Long>) {
                        this.entries = entries
                    }
                }
            val withdrawals = PendingWithdrawals(storage, nowMillis = { 0L })
            val harness =
                SessionHarness(
                    backgroundScope,
                    withdrawals = withdrawals,
                    deletionMarker = marker,
                )
            withdrawals.mark("uid-1")
            harness.flags.put("welcome_carousel_seen", true)
            harness.pending.save(
                com.tomessa.miff.domain.auth
                    .PendingConsent("uid-1", "v1", analyticsAccepted = true),
            )
            harness.gateway.setSignedIn("uid-1")
            harness.manager.start()
            harness.manager.eraseAfterAccountDeletion("uid-1")
            assertFalse(withdrawals.isPending("uid-1"))
            assertNull(harness.pending.stored)
            assertTrue("the carousel flag is kept", harness.flags.get("welcome_carousel_seen", false))
            assertEquals(1, harness.gateway.signOutCalls)
        }

    @Test
    fun aLocalErasureThatFails_doesNotKeepTheOthers_norTheMarkerFromBeingErased() =
        runTest(UnconfinedTestDispatcher()) {
            val h =
                SessionHarness(
                    backgroundScope,
                    deletionMarker = marker,
                    localData =
                        listOf(
                            LocalDataEraser { error("one preference does not go") },
                            LocalDataEraser { uid -> erasedFor += uid },
                        ),
                    timeline = timeline,
                )
            h.gateway.setSignedIn("uid-1")
            h.manager.start()
            marker.stored = AccountDeletionMark("uid-1", false)
            h.manager.eraseAfterAccountDeletion("uid-1")
            assertEquals(listOf("uid-1"), erasedFor)
            assertNull(marker.stored)
        }

    @Test
    fun leaving_opensTheSignUpWithTheCarousel_onceOnly() =
        run { h ->
            h.gateway.setSignedIn("uid-1")
            h.manager.start()
            h.manager.beginAccountDeletion()
            h.manager.eraseAfterAccountDeletion("uid-1")
            h.manager.leaveAfterAccountDeletion()
            assertEquals(AppDestination.SIGN_UP, h.manager.destination())
            assertTrue(h.manager.consumeCarouselAfterDeletion())
            assertFalse(h.manager.consumeCarouselAfterDeletion())
            // The end screen was seen: no banner.
            assertFalse(h.manager.consumeAccountDeletedNotice())
        }
}
