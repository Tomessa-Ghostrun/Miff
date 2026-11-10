package com.tomessa.miff.ui.settings

import com.tomessa.miff.data.subscription.EntitlementDocument
import com.tomessa.miff.data.subscription.SubscriptionRepository
import com.tomessa.miff.data.subscription.SubscriptionStore
import com.tomessa.miff.domain.subscription.SubscriptionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Ticket ABO-1: the line is absent until an ACTIVE Miff+ is read, and the document is read only once per screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class CountingStore(
        private val document: EntitlementDocument?,
        private val failure: Throwable? = null,
    ) : SubscriptionStore {
        var reads = 0

        override suspend fun read(uid: String): EntitlementDocument? {
            reads++
            failure?.let { throw it }
            return document
        }
    }

    private fun viewModel(store: SubscriptionStore) =
        SubscriptionViewModel(SubscriptionRepository(store, currentUid = { "uid-1" }, nowMillis = { 10L }))

    @Test
    fun nothingIsShownUntilTheAnswerComes_thenTheActiveSubscription_afterOneRead() =
        runTest(dispatcher) {
            val store = CountingStore(EntitlementDocument(true, 11L, "manual"))
            val model = viewModel(store)
            assertNull(model.subscription.value)
            advanceUntilIdle()
            assertEquals(SubscriptionInfo(11L, grantedByTeam = true), model.subscription.value)
            assertEquals(1, store.reads)
        }

    @Test
    fun aFreeAccount_staysWithoutTheLine() =
        runTest(dispatcher) {
            val model = viewModel(CountingStore(null))
            advanceUntilIdle()
            assertNull(model.subscription.value)
        }

    @Test
    fun offline_staysWithoutTheLine_withoutAnyError() =
        runTest(dispatcher) {
            val store = CountingStore(null, failure = IOException("offline"))
            val model = viewModel(store)
            advanceUntilIdle()
            assertNull(model.subscription.value)
            assertEquals(1, store.reads)
        }
}
