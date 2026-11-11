package com.tomessa.miff.data.miff

import com.tomessa.miff.testsupport.FakeActiveDevice
import com.tomessa.miff.testsupport.FakeLocationProbe
import com.tomessa.miff.testsupport.FakeMiffObserver
import com.tomessa.miff.testsupport.FakeMiffStore
import com.tomessa.miff.testsupport.MIFF_ID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bug of 2026-11-10 ("le Feed se charge à l'infini sans connexion"): the reads that check the server waited 20 s EACH, one after the other. Offline, the
 * phone knows it has no network: they give up after a few seconds, and the Feed moves on to the cache or to its pop-in "Pas de connexion".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MiffRepositoryOfflineTest {
    private val store = FakeMiffStore()
    private var online = true

    private fun repo() =
        FirestoreMiffRepository(
            store = store,
            observer = FakeMiffObserver(),
            location = FakeLocationProbe(),
            device = FakeActiveDevice(),
            newMiffId = { MIFF_ID },
            timeoutMillis = 20_000,
            online = { online },
            offlineReadTimeoutMillis = 4_000,
        )

    @Test
    fun offline_aServerThatNeverAnswers_isGivenUpOnQuickly_notAfter20SecondsPerRead() =
        runTest {
            store.hangsOnServerReads = true
            online = false
            val answer = repo().myMiffs("uid-1")
            // The plan (4 s) then the list (4 s): the cache is empty, so nothing is known, and the Feed can say it.
            assertTrue(answer is MyMiffsLoad.Unavailable)
            assertTrue("took $currentTime ms", currentTime <= 8_000)
        }

    @Test
    fun withANetwork_theUsualTimeLimitIsKept() =
        runTest {
            store.hangsOnServerReads = true
            online = true
            repo().myMiffs("uid-1")
            assertEquals(40_000L, currentTime)
        }
}
