package com.tomessa.miff.data.subscription

import com.tomessa.miff.domain.subscription.EntitlementDocument
import com.tomessa.miff.domain.subscription.StoreState
import com.tomessa.miff.domain.subscription.SubscriptionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * ONE read of `entitlements/{uid}` each time it is asked: the status when the server answers (a missing document is `Free`: the server said so), null whenever it cannot be read
 * (a failure is never taken for "free": a screen must not tell a subscriber that nothing is there).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionRepositoryTest {
    private val now = 5_000L

    private class FakeStore(
        var document: EntitlementDocument? = null,
        var failure: Throwable? = null,
        var gate: CompletableDeferred<Unit>? = null,
        var hang: Boolean = false,
    ) : SubscriptionStore {
        val reads = mutableListOf<String>()

        override suspend fun read(uid: String): EntitlementDocument? {
            reads += uid
            if (hang) awaitCancellation()
            gate?.await()
            failure?.let { throw it }
            return document
        }
    }

    private var uid: String? = "uid-1"
    private val store = FakeStore()

    private fun repository(timeoutMillis: Long = 8_000L) =
        SubscriptionRepository(store, currentUid = { uid }, nowMillis = { now }, timeoutMillis = timeoutMillis)

    @Test
    fun anActiveManualGrant_isRead_withOneReadOfTheOwnDocument() =
        runTest {
            store.document = EntitlementDocument(active = true, expiresAtMillis = now + 1, source = "manual")
            assertEquals(SubscriptionStatus.Manual(now + 1), repository().load())
            assertEquals(listOf("uid-1"), store.reads)
        }

    @Test
    fun aStorePurchase_isRead_withItsState() =
        runTest {
            store.document =
                EntitlementDocument(true, now + 1, "google_play", state = "trial", plan = "yearly", trial = true)
            val status = repository().load() as SubscriptionStatus.Store
            assertEquals(StoreState.TRIAL, status.state)
        }

    @Test
    fun aStorePurchaseThatIsOver_isStillRead() =
        runTest {
            store.document = EntitlementDocument(false, now - 1, "google_play", state = "expired")
            val status = repository().load() as SubscriptionStatus.Store
            assertEquals(StoreState.EXPIRED, status.state)
        }

    @Test
    fun noDocument_orAnOverManualGrant_isFree_theServerSaidSo() =
        runTest {
            store.document = null
            assertEquals(SubscriptionStatus.Free, repository().load())
            store.document = EntitlementDocument(active = false, expiresAtMillis = now + 1, source = "manual")
            assertEquals(SubscriptionStatus.Free, repository().load())
            store.document = EntitlementDocument(active = true, expiresAtMillis = now - 1, source = "manual")
            assertEquals(SubscriptionStatus.Free, repository().load())
        }

    @Test
    fun offlineOrRefused_isNull_notFree_withoutAnException() =
        runTest {
            store.failure = IOException("offline")
            assertNull(repository().load())
            store.failure = IllegalStateException("PERMISSION_DENIED")
            assertNull(repository().load())
        }

    @Test
    fun aReadThatNeverAnswers_isGivenUpAfterTheTimeout_andIsNull() =
        runTest {
            store.hang = true
            val result = async { repository(timeoutMillis = 8_000L).load() }
            runCurrent()
            advanceTimeBy(7_999L)
            runCurrent()
            assertTrue(result.isActive)
            advanceTimeBy(2L)
            runCurrent()
            assertNull(result.await())
        }

    @Test
    fun signedOut_readsNothing() =
        runTest {
            uid = null
            store.document = EntitlementDocument(true, now + 1, "manual")
            assertNull(repository().load())
            assertTrue(store.reads.isEmpty())
        }

    @Test
    fun anotherAccountByTheTimeTheAnswerComes_isNull() =
        runTest {
            store.document = EntitlementDocument(true, now + 1, "manual")
            val gate = CompletableDeferred<Unit>()
            store.gate = gate
            val result = async { repository().load() }
            runCurrent()
            uid = "uid-2"
            gate.complete(Unit)
            assertNull(result.await())
        }

    @Test
    fun cancellation_isNotSwallowed() =
        runTest {
            store.failure = CancellationException("cancelled")
            var cancelled = false
            try {
                repository().load()
            } catch (e: CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
        }
}
