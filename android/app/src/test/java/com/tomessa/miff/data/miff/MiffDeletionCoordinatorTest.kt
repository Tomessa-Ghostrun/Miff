package com.tomessa.miff.data.miff

import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.MemoryPendingDeletions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deletion as a thing the app keeps doing (ticket S3, S4, S7): the marker is written before the first write, a cut network
 * pauses it and the return of the network carries it on, a launch takes up what was not finished, and the marker goes only when
 * the Miff is gone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MiffDeletionCoordinatorTest {
    private val answers = ArrayDeque<MiffDeletionResult>()
    private val runs = mutableListOf<Pair<String, String>>()
    private val pending = MemoryPendingDeletions()
    private val network = FakeNetwork()
    private val started = mutableListOf<String>()
    private val deleted = mutableListOf<String>()
    private var uid: String? = "uid-owner"

    private fun TestScope.coordinator() =
        MiffDeletionCoordinator(
            deletion =
                MiffDeleter { u, m ->
                    runs += u to m
                    answers.removeFirstOrNull() ?: MiffDeletionResult.Deleted
                },
            pending = pending,
            network = network,
            currentUid = { uid },
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            onStarted = { started += it },
            onDeleted = { deleted += it },
            retryPauseMillis = 1_000,
        )

    @Test
    fun start_writesTheMarkerFirst_tellsTheDirectory_runs_andClearsTheMarkerOnlyWhenTheMiffIsGone() =
        runTest {
            val c = coordinator()
            c.start(MIFF_ID)
            assertEquals("the marker is there before anything ran", setOf(MIFF_ID), pending.ids("uid-owner"))
            assertEquals(listOf(MIFF_ID), started)
            assertEquals(DeletionState.Running(resumed = false), c.states.value[MIFF_ID])
            advanceUntilIdle()
            assertEquals(DeletionState.Done, c.states.value[MIFF_ID])
            assertTrue(pending.ids("uid-owner").isEmpty())
            assertEquals(listOf(MIFF_ID), deleted)
            assertEquals(listOf("uid-owner" to MIFF_ID), runs)
        }

    @Test
    fun aSecondTapWhileItRuns_doesNotStartASecondRun() =
        runTest {
            val c = coordinator()
            c.start(MIFF_ID)
            c.start(MIFF_ID)
            advanceUntilIdle()
            assertEquals(1, runs.size)
        }

    @Test
    fun noNetwork_waits_thenCarriesOnWhenTheNetworkIsBack_andKeepsTheMarkerMeanwhile() =
        runTest {
            answers += MiffDeletionResult.Paused(MiffFailure.OFFLINE)
            network.online = false
            val c = coordinator()
            c.start(MIFF_ID)
            advanceUntilIdle()
            assertEquals(DeletionState.WaitingForNetwork, c.states.value[MIFF_ID])
            assertEquals("nothing else is tried while there is no network", 1, runs.size)
            assertEquals(setOf(MIFF_ID), pending.ids("uid-owner"))
            network.online = true
            advanceUntilIdle()
            assertEquals(2, runs.size)
            assertEquals(DeletionState.Done, c.states.value[MIFF_ID])
            assertTrue(pending.ids("uid-owner").isEmpty())
        }

    @Test
    fun aFailureThatIsNotTheNetwork_isTriedAgainALittle_thenSaysStuck_andKeepsTheMarker() =
        runTest {
            repeat(5) { answers += MiffDeletionResult.Paused(MiffFailure.REFUSED) }
            val c = coordinator()
            c.start(MIFF_ID)
            advanceUntilIdle()
            assertEquals(DeletionState.Stuck, c.states.value[MIFF_ID])
            assertEquals(MiffDeletionCoordinator.MAX_OTHER_FAILURES, runs.size)
            assertEquals("the next launch tries again", setOf(MIFF_ID), pending.ids("uid-owner"))
            assertTrue(deleted.isEmpty())
            // "Réessayer" runs it again.
            answers.clear()
            c.retry(MIFF_ID)
            advanceUntilIdle()
            assertEquals(DeletionState.Done, c.states.value[MIFF_ID])
        }

    @Test
    fun resumePending_takesUpWhatWasNotFinished_atLaunch_andReportsIt() =
        runTest {
            pending.add("uid-owner", MIFF_ID)
            pending.add("uid-owner", "ZyXwVuTsRqPoNmLkJiHg")
            val c = coordinator()
            val resumed = c.resumePending()
            assertEquals(setOf(MIFF_ID, "ZyXwVuTsRqPoNmLkJiHg"), resumed.toSet())
            assertEquals(DeletionState.Running(resumed = true), c.states.value[MIFF_ID])
            advanceUntilIdle()
            assertTrue(pending.ids("uid-owner").isEmpty())
            assertEquals(2, deleted.size)
        }

    @Test
    fun resumePending_withNoMarker_doesNothing_andWithNoAccount_doesNothing() =
        runTest {
            val c = coordinator()
            assertTrue(c.resumePending().isEmpty())
            pending.add("uid-owner", MIFF_ID)
            uid = null
            assertTrue(c.resumePending().isEmpty())
            advanceUntilIdle()
            assertTrue(runs.isEmpty())
            assertEquals("the marker stays for the account that comes back", setOf(MIFF_ID), pending.ids("uid-owner"))
        }

    @Test
    fun anotherAccountOnTheSamePhone_neverTakesUpTheMarkerOfSomeoneElse() =
        runTest {
            pending.add("uid-owner", MIFF_ID)
            uid = "uid-other"
            val c = coordinator()
            assertTrue(c.resumePending().isEmpty())
            advanceUntilIdle()
            assertTrue("nothing was tried, nothing was announced", runs.isEmpty() && deleted.isEmpty())
            assertEquals("the marker stays for its own account", setOf("uid-owner" to MIFF_ID), pending.all())
        }

    @Test
    fun aMiffThatIsNotTheirs_dropsTheMarker_andSaysNothing() =
        runTest {
            answers += MiffDeletionResult.NotOwner
            pending.add("uid-owner", MIFF_ID)
            val c = coordinator()
            c.resumePending()
            advanceUntilIdle()
            assertTrue(pending.ids("uid-owner").isEmpty())
            assertNull(c.states.value[MIFF_ID])
            assertTrue(deleted.isEmpty())
        }

    @Test
    fun theEndIsAnnounced_once_toWhoeverListens() =
        runTest {
            val c = coordinator()
            val heard = mutableListOf<String>()
            val job = launch { c.completed.toList(heard) }
            advanceUntilIdle()
            c.start(MIFF_ID)
            advanceUntilIdle()
            assertEquals(listOf(MIFF_ID), heard)
            job.cancel()
            assertFalse(heard.isEmpty())
        }

    @Test
    fun theWaitBetweenTries_growsButNeverBeyondAMinute() =
        runTest {
            repeat(2) { answers += MiffDeletionResult.Paused(MiffFailure.REFUSED) }
            val c = coordinator()
            c.start(MIFF_ID)
            advanceTimeBy(500)
            assertEquals("first try at once", 1, runs.size)
            advanceTimeBy(1_000)
            assertEquals("second try after the first pause", 2, runs.size)
            advanceUntilIdle()
            assertEquals(3, runs.size)
        }
}
