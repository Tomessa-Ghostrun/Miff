package com.tomessa.miff.data.session

import com.tomessa.miff.data.account.AccountDeletion
import com.tomessa.miff.data.account.AccountSession
import com.tomessa.miff.data.account.DeletionRun
import com.tomessa.miff.data.account.ROOM_1
import com.tomessa.miff.data.account.ROOM_2
import com.tomessa.miff.data.account.miffsOn
import com.tomessa.miff.domain.auth.AppDestination
import com.tomessa.miff.testsupport.AccountWorld
import com.tomessa.miff.testsupport.FakeAuthGateway
import com.tomessa.miff.testsupport.FakeCachePurger
import com.tomessa.miff.testsupport.SessionHarness
import com.tomessa.miff.testsupport.Timeline
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AUTH-5b (#97) seen from ANOTHER phone of the same account, with the note of #100: the deletion deletes the session FIRST (before any lot of Miff), so the other
 * phone signs out at once with the careful "your account changed on another device" note (SESSION_GONE), never "used on another phone", never a lost access to a
 * Miff, and it writes nothing back. The exact words of the note are checked on the sign-in screen (AuthScreensTest, FR and EN).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeletionSeenByAnotherPhoneTest {
    private val uid = "uid-1"

    private fun SessionManager.destination(): AppDestination? = (state.value as? SessionState.Ready)?.destination

    @Test
    fun theOtherPhone_signsOutBeforeTheFirstMiffGoes_withTheSessionGoneNote_andWritesNothingBack() =
        runTest(UnconfinedTestDispatcher()) {
            // The OTHER phone: signed in with the account, its session verified and listened to.
            val other = SessionHarness(backgroundScope)
            other.accounts.setLoaded(profileExists = true, hasMembership = true)
            other.manager.start()
            other.manager.signInWithEmail("a@b.fr", "password-1")
            assertEquals(AppDestination.FEED, other.manager.destination())
            val changesBefore = other.sessions.changes.toList()

            // The phone that deletes the account: a premium creator of one Miff with another person, and a member of a second one.
            val world = AccountWorld(uid)
            world.addRoom(ROOM_1, owner = uid, others = listOf("a"), indexKey = ROOM_1)
            world.addRoom(ROOM_2, owner = "someone", others = listOf(uid), indexKey = ROOM_2)
            var otherPhoneAtTheFirstLot: AppDestination? = null
            var firstLotSeen = false
            world.failLot = {
                if (!firstLotSeen) {
                    firstLotSeen = true
                    otherPhoneAtTheFirstLot = other.manager.destination()
                }
                null
            }
            val gateway = FakeAuthGateway().also { it.setSignedIn(uid) }
            val deletion =
                AccountDeletion(
                    store = world,
                    gateway = gateway,
                    cache = FakeCachePurger(Timeline()),
                    // The deletion of the session of the deleting phone IS the deletion of the document the other phone listens to.
                    session = AccountSession { id -> other.sessions.delete(id) },
                    miffs = miffsOn(world),
                    timeoutMillis = 1_000L,
                )
            assertEquals(DeletionRun.Completed, deletion.run(uid))

            assertTrue("a lot was made", firstLotSeen)
            assertEquals(
                "the other phone was already signed out when the FIRST lot of a Miff left",
                AppDestination.SIGN_IN,
                otherPhoneAtTheFirstLot,
            )
            assertEquals(SignedOutNotice.SESSION_GONE, other.manager.consumeNotice())
            assertNull("shown once", other.manager.consumeNotice())
            assertTrue("it never says the account is used on another phone", other.deviceIds.gone)
            // Nothing written back by the other phone: the only change on the server is the deletion of the session.
            assertEquals(
                changesBefore + "delete-any:$uid",
                other.sessions.changes.toList().let {
                    it.take(changesBefore.size + 1)
                },
            )
            assertTrue(other.sessions.changes.none { it.startsWith("write:") && it !in changesBefore })
        }
}
