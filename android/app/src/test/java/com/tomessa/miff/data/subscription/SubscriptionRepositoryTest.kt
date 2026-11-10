package com.tomessa.miff.data.subscription

import com.tomessa.miff.domain.subscription.SubscriptionInfo
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

/** Ticket ABO-1: ONE read of `entitlements/{uid}`; hidden (null) whenever it is not an active Miff+ or cannot be read. */
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
    fun anActiveManualGrant_isShown_withOneReadOfTheOwnDocument() =
        runTest {
            store.document = EntitlementDocument(active = true, expiresAtMillis = now + 1, source = "manual")
            assertEquals(SubscriptionInfo(now + 1, grantedByTeam = true), repository().load())
            assertEquals(listOf("uid-1"), store.reads)
        }

    @Test
    fun anotherSource_isShown_withoutTheTeamMention() =
        runTest {
            store.document = EntitlementDocument(active = true, expiresAtMillis = now + 1, source = "google_play")
            assertEquals(SubscriptionInfo(now + 1, grantedByTeam = false), repository().load())
        }

    @Test
    fun noDocument_inactive_expired_orIncomplete_showNothing() =
        runTest {
            store.document = null
            assertNull(repository().load())
            store.document = EntitlementDocument(active = false, expiresAtMillis = now + 1, source = "manual")
            assertNull(repository().load())
            store.document = EntitlementDocument(active = true, expiresAtMillis = now - 1, source = "manual")
            assertNull(repository().load())
            store.document = EntitlementDocument(active = true, expiresAtMillis = null, source = "manual")
            assertNull(repository().load())
            store.document = EntitlementDocument(active = null, expiresAtMillis = now + 1, source = "manual")
            assertNull(repository().load())
        }

    @Test
    fun offlineOrRefused_showNothing_withoutAnException() =
        runTest {
            store.failure = IOException("offline")
            assertNull(repository().load())
            store.failure = IllegalStateException("PERMISSION_DENIED")
            assertNull(repository().load())
        }

    @Test
    fun aReadThatNeverAnswers_isGivenUpAfterTheTimeout_andShowsNothing() =
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
    fun anotherAccountByTheTimeTheAnswerComes_showsNothing() =
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
