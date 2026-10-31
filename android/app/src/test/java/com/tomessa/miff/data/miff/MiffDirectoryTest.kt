package com.tomessa.miff.data.miff

import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.miff.MiffSummary
import com.tomessa.miff.testsupport.FakeCachePurger
import com.tomessa.miff.testsupport.FakeMiffRepository
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.MemoryActiveMiffStore
import com.tomessa.miff.testsupport.OTHER_MIFF_ID
import com.tomessa.miff.testsupport.Timeline
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Miffs of the person in memory, and the ONE place that deals with a loss of access (ticket Miff 5.7, criteria D1 to D6):
 * clean-up, purge of the cache, one message, no loop.
 */
class MiffDirectoryTest {
    private val repo = FakeMiffRepository()
    private val active = MemoryActiveMiffStore()
    private val timeline = Timeline()
    private val cache = FakeCachePurger(timeline)
    private var uid: String? = "uid-1"

    private fun directory() = MiffDirectory(repo, { uid }, active, cache)

    private fun loaded(
        miffs: List<MiffSummary> = emptyList(),
        lost: List<LostMiff> = emptyList(),
        premium: Boolean = false,
        fromCache: Boolean = false,
    ) = MyMiffsLoad.Loaded(miffs, lost, premium, fromCache)

    private fun lost(
        id: String = MIFF_ID,
        name: String? = "Chez nous",
    ) = LostMiff(MembershipRef("free", id), name)

    @Test
    fun noMiff_isReady_andEmpty_soTheFeedShowsTheTwoWaysToBegin() =
        runTest {
            val directory = directory()
            repo.myMiffsResult = loaded()
            directory.refresh()
            val state = directory.state.value as DirectoryState.Ready
            assertTrue(state.miffs.isEmpty())
            assertNull(state.active)
        }

    @Test
    fun theActiveMiff_isTheLastChosen_elseTheFirst_andAChoiceIsKept() =
        runTest {
            val directory = directory()
            repo.myMiffsResult =
                loaded(listOf(MiffSummary(MIFF_ID, "A"), MiffSummary(OTHER_MIFF_ID, "B")), premium = true)
            directory.refresh()
            assertEquals(MIFF_ID, (directory.state.value as DirectoryState.Ready).activeId)
            directory.select(OTHER_MIFF_ID)
            assertEquals(OTHER_MIFF_ID, active.value)
            directory.refresh()
            assertEquals("B", (directory.state.value as DirectoryState.Ready).active?.name)
            directory.select("not-one-of-mine")
            assertEquals(OTHER_MIFF_ID, active.value)
        }

    @Test
    fun aLostMiff_isCleanedUp_thePhoneCacheIsPurged_andTheMessageIsGivenOnce() =
        runTest {
            val directory = directory()
            repo.myMiffsResult = loaded(lost = listOf(lost()))
            directory.refresh()
            assertEquals(1, repo.cleaned.size)
            assertEquals("markPending", timeline.entries.first())
            assertTrue("purge" in timeline.entries)
            assertTrue(
                "the clean-up comes before the purge",
                timeline.entries.indexOf("markPending") < timeline.entries.indexOf("purge"),
            )
            val notices = directory.notice.value
            assertEquals(1, notices.size)
            assertEquals("Chez nous", notices.single().name)
            // The same loss seen again at the next refresh does not announce again: no loop (criterion D1).
            directory.refresh()
            assertEquals(1, directory.notice.value.size)
            directory.dismissNotice()
            directory.refresh()
            assertTrue(directory.notice.value.isEmpty())
        }

    @Test
    fun theMessage_isTheSame_whateverTheCause_andDoesNotNameOne() =
        runTest {
            val directory = directory()
            // Removed, or Miff deleted: the directory only knows "lost": there is no cause to show.
            repo.myMiffsResult = loaded(lost = listOf(lost(name = null)))
            directory.refresh()
            assertNull(
                directory.notice.value
                    .single()
                    .name,
            )
        }

    @Test
    fun aNetworkProblem_neverMakesAMessage_orAClean_up() =
        runTest {
            val directory = directory()
            repo.myMiffsResult = MyMiffsLoad.Unavailable
            directory.refresh()
            assertEquals(DirectoryState.Unavailable, directory.state.value)
            assertTrue(directory.notice.value.isEmpty())
            assertTrue(repo.cleaned.isEmpty())
            assertEquals(0, cache.purgeCalls)
        }

    @Test
    fun anAnswerThatFails_afterAGoodOne_keepsWhatWasKnown() =
        runTest {
            val directory = directory()
            repo.myMiffsResult = loaded(listOf(MiffSummary(MIFF_ID, "A")))
            directory.refresh()
            repo.myMiffsResult = MyMiffsLoad.Unavailable
            directory.refresh()
            assertTrue(directory.state.value is DirectoryState.Ready)
        }

    @Test
    fun theMiffScreenSeeingTheLoss_hasTheSameEffects_withTheNameItKnew() =
        runTest {
            val directory = directory()
            repo.myMiffsResult = loaded(listOf(MiffSummary(MIFF_ID, "Chez nous")))
            directory.refresh()
            repo.myMiffsResult = loaded(lost = listOf(lost()))
            assertTrue(directory.onAccessLost(MIFF_ID, "Chez nous"))
            assertEquals(
                "Chez nous",
                directory.notice.value
                    .single()
                    .name,
            )
            assertEquals(1, repo.cleaned.size)
            assertTrue((directory.state.value as DirectoryState.Ready).miffs.isEmpty())
            assertEquals(1, cache.purgeCalls)
            // Seen once by the screen, then by a refresh: still one message.
            directory.refresh()
            assertEquals(1, directory.notice.value.size)
        }

    @Test
    fun endOfSession_forgetsTheMiffs_theMessages_andTheChoice() =
        runTest {
            val directory = directory()
            repo.myMiffsResult = loaded(listOf(MiffSummary(MIFF_ID, "A")), lost = listOf(lost(OTHER_MIFF_ID)))
            directory.refresh()
            directory.select(MIFF_ID)
            directory.stop()
            assertEquals(DirectoryState.Loading, directory.state.value)
            assertTrue(directory.notice.value.isEmpty())
            assertNull(active.value)
        }

    @Test
    fun withoutASignedInPerson_nothingIsRead() =
        runTest {
            uid = null
            directory().refresh()
            assertFalse("myMiffs" in repo.calls)
        }

    @Test
    fun aRefusalHeardByTheScreen_whileTheServerCannotBeReached_givesZeroMessage_andCleansNothing() =
        runTest {
            val directory = directory()
            repo.myMiffsResult = MyMiffsLoad.Unavailable
            assertFalse(directory.onAccessLost(MIFF_ID, "Chez nous"))
            assertTrue("nobody is told they were removed on a doubt", directory.notice.value.isEmpty())
            assertTrue(repo.cleaned.isEmpty())
            assertEquals(0, cache.purgeCalls)
        }

    @Test
    fun aRefusalHeardByTheScreen_thatTheServerDoesNotConfirm_givesZeroMessage() =
        runTest {
            val directory = directory()
            // The server says the Miff is there and fine: the listener was wrong (or the answer too early).
            repo.myMiffsResult = loaded(listOf(MiffSummary(MIFF_ID, "Chez nous")))
            assertFalse(directory.onAccessLost(MIFF_ID, "Chez nous"))
            assertTrue(directory.notice.value.isEmpty())
            assertTrue(repo.cleaned.isEmpty())
            assertEquals("Chez nous", (directory.state.value as DirectoryState.Ready).active?.name)
        }

    @Test
    fun aConfirmedLoss_isAnnouncedOnlyAfterTheServerAnswered() =
        runTest {
            val directory = directory()
            repo.myMiffsResult = loaded(lost = listOf(lost()))
            assertTrue(directory.onAccessLost(MIFF_ID, null))
            assertEquals(
                "Chez nous",
                directory.notice.value
                    .single()
                    .name,
            )
        }
}
