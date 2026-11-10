package com.tomessa.miff.data.session

import com.tomessa.miff.data.permission.NotificationAskStore
import com.tomessa.miff.data.review.ReviewStateStore
import com.tomessa.miff.domain.review.ReviewOutcome
import com.tomessa.miff.domain.review.ReviewState
import com.tomessa.miff.testsupport.MemoryPendingDeletions
import com.tomessa.miff.testsupport.MemoryPublishLedger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What is kept for ONE account on the phone goes with it when the account is deleted, and only with it (AUTH-5, ticket 5.10). */
class AccountLocalDataTest {
    private class Asks : NotificationAskStore {
        val shown = mutableSetOf("uid-1", "uid-2")

        override fun wasShown(uid: String) = uid in shown

        override fun markShown(uid: String) {
            shown += uid
        }

        override fun forget(uid: String) {
            shown -= uid
        }
    }

    private class MemoryReviewState(
        var state: ReviewState =
            ReviewState(firstMiffSeenAtMillis = 5L, lastShownAtMillis = 9L, lastOutcome = ReviewOutcome.CAN_DO_BETTER),
    ) : ReviewStateStore {
        override fun read() = state

        override fun write(state: ReviewState) {
            this.state = state
        }

        override fun clear() {
            state = ReviewState()
        }

        override fun forceNext() = false

        override fun setForceNext(value: Boolean) = Unit
    }

    @Test
    fun theStateOfTheReviewWindow_goesWithTheAccount() =
        runTest {
            val review = MemoryReviewState()
            accountLocalDataErasers(Asks(), MemoryPendingDeletions(emptySet()), MemoryPublishLedger(), review).forEach {
                it.erase("uid-1")
            }
            assertEquals(ReviewState(), review.state)
        }

    @Test
    fun theErasersForgetTheAccount_theNotificationFlagAndTheMarkersOfDeletionOfItsMiffs() =
        runTest {
            val asks = Asks()
            val pending = MemoryPendingDeletions(setOf("uid-1" to "m1", "uid-1" to "m2", "uid-2" to "m3"))
            accountLocalDataErasers(
                asks,
                pending,
                MemoryPublishLedger(),
                MemoryReviewState(),
            ).forEach { it.erase("uid-1") }
            assertFalse(asks.wasShown("uid-1"))
            assertTrue(pending.ids("uid-1").isEmpty())
        }

    @Test
    fun anotherAccount_keepsEverything() =
        runTest {
            val asks = Asks()
            val pending = MemoryPendingDeletions(setOf("uid-1" to "m1", "uid-2" to "m3"))
            accountLocalDataErasers(
                asks,
                pending,
                MemoryPublishLedger(),
                MemoryReviewState(),
            ).forEach { it.erase("uid-1") }
            assertTrue(asks.wasShown("uid-2"))
            assertEquals(setOf("m3"), pending.ids("uid-2"))
        }

    @Test
    fun theNoteOfThePhotoSentByTheDeletedAccount_goesWithIt() =
        runTest {
            val ledger = MemoryPublishLedger().apply { notePhotoSent("uid-1", 1_000L) }
            accountLocalDataErasers(Asks(), MemoryPendingDeletions(emptySet()), ledger, MemoryReviewState()).forEach {
                it.erase("uid-1")
            }
            assertEquals(null, ledger.photoSentMillis("uid-1"))
            assertEquals(null, ledger.photoAt)
            assertEquals(null, ledger.photoUid)
        }

    @Test
    fun theNoteOfAnotherAccount_isKept() =
        runTest {
            val ledger = MemoryPublishLedger().apply { notePhotoSent("uid-2", 2_000L) }
            accountLocalDataErasers(Asks(), MemoryPendingDeletions(emptySet()), ledger, MemoryReviewState()).forEach {
                it.erase("uid-1")
            }
            assertEquals(2_000L, ledger.photoSentMillis("uid-2"))
        }

    @Test
    fun aSimpleSignOut_stillKeepsTheNote_soThatThePhotoIsCleanedUpLater() {
        val ledger = MemoryPublishLedger().apply { notePhotoSent("uid-1", 1_000L) }
        ledger.clear()
        assertEquals(1_000L, ledger.photoSentMillis("uid-1"))
    }
}
