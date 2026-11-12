package com.tomessa.miff.domain.place

import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.OTHER_MIFF_ID
import com.tomessa.miff.testsupport.testRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the requests of a Miff are read, and what the list shows (cost and expiry rules of the ticket LIEU-REQ). Plain Kotlin, a clock we move by hand. */
class PlaceRequestBookTest {
    private var now = 1_000_000L
    private val book = PlaceRequestBook { now }

    private fun ids(view: RequestsView) = (view as RequestsView.Ready).requests.map { it.id }

    @Test
    fun nothingKnown_isLoading_andNeedsAReading() {
        assertTrue(book.view(MIFF_ID) is RequestsView.Loading)
        assertTrue(book.needsReading(MIFF_ID))
    }

    @Test
    fun aReadingUnderWay_isNeverDoubled_andShowsNothingYet() {
        book.readingStarted(MIFF_ID)
        assertFalse(book.needsReading(MIFF_ID))
        assertTrue(book.view(MIFF_ID) is RequestsView.Loading)
    }

    @Test
    fun whatWasRead_isReusedFiveMinutes_thenReadAgain() {
        book.readingStarted(MIFF_ID)
        book.readingSucceeded(MIFF_ID, listOf(testRequest(slot = 1)))
        assertFalse(book.needsReading(MIFF_ID))
        now += PlaceRequestBook.TTL_MILLIS - 1
        assertFalse(book.needsReading(MIFF_ID))
        now += 1
        assertTrue(book.needsReading(MIFF_ID))
    }

    @Test
    fun aFailure_waitsThirtySeconds_neverALoop_andAnEmptyListBecomesUnavailable() {
        book.readingStarted(MIFF_ID)
        book.readingFailed(MIFF_ID)
        assertTrue(book.view(MIFF_ID) is RequestsView.Unavailable)
        assertFalse(book.needsReading(MIFF_ID))
        now += PlaceRequestBook.RETRY_MILLIS
        assertTrue(book.needsReading(MIFF_ID))
    }

    @Test
    fun aFailure_neverMakesAListThatWasDrawnVanish() {
        book.readingSucceeded(MIFF_ID, listOf(testRequest(slot = 1)))
        book.readingStarted(MIFF_ID)
        book.readingFailed(MIFF_ID)
        assertEquals(listOf("u2_1"), ids(book.view(MIFF_ID)))
    }

    @Test
    fun aRequestOfMoreThanThirtyDays_isNotInTheList_butIsKnownToBeDeleted() {
        val old = testRequest(slot = 1, createdAtMillis = 0L)
        val recent = testRequest(slot = 2, createdAtMillis = now)
        book.readingSucceeded(MIFF_ID, listOf(old, recent))
        now = PlaceRequestLimits.EXPIRY_MILLIS + 1
        assertEquals(listOf("u2_2"), ids(book.view(MIFF_ID)))
        assertEquals(2, book.known(MIFF_ID)!!.size)
    }

    @Test
    fun aMember_seesTheNewestFirst_theCreatorTheOldestFirst() {
        book.readingSucceeded(
            MIFF_ID,
            listOf(testRequest(slot = 1, createdAtMillis = 10), testRequest(slot = 2, createdAtMillis = 20)),
        )
        assertEquals(listOf("u2_2", "u2_1"), ids(book.view(MIFF_ID, creator = false)))
        assertEquals(listOf("u2_1", "u2_2"), ids(book.view(MIFF_ID, creator = true)))
    }

    @Test
    fun invalidateAll_makesEveryListReadAgain_andARequestWrittenIsSeenWithoutWaiting() {
        book.readingSucceeded(MIFF_ID, emptyList())
        book.readingSucceeded(OTHER_MIFF_ID, emptyList())
        book.invalidateAll()
        assertTrue(book.needsReading(MIFF_ID))
        assertTrue(book.needsReading(OTHER_MIFF_ID))
    }

    @Test
    fun invalidate_oneMiff_leavesTheOthersAlone() {
        book.readingSucceeded(MIFF_ID, emptyList())
        book.readingSucceeded(OTHER_MIFF_ID, emptyList())
        book.invalidate(MIFF_ID)
        assertTrue(book.needsReading(MIFF_ID))
        assertFalse(book.needsReading(OTHER_MIFF_ID))
    }

    @Test
    fun remove_takesARequestOutAtOnce_andAddPutsOneIn() {
        book.readingSucceeded(MIFF_ID, listOf(testRequest(slot = 1), testRequest(slot = 2)))
        book.remove(MIFF_ID, "u2_1")
        assertEquals(listOf("u2_2"), ids(book.view(MIFF_ID)))
        book.add(MIFF_ID, testRequest(slot = 3))
        assertEquals(setOf("u2_2", "u2_3"), ids(book.view(MIFF_ID)).toSet())
        // The same slot written twice is ONE request, never two cards.
        book.add(MIFF_ID, testRequest(slot = 3))
        assertEquals(2, ids(book.view(MIFF_ID)).size)
    }

    @Test
    fun live_listenerAnswers_needNoReading_andStayFreshWhileAttached() {
        book.live(MIFF_ID, listOf(testRequest(slot = 1)))
        now += PlaceRequestBook.TTL_MILLIS * 10
        assertFalse(book.needsReading(MIFF_ID))
        // invalidate does not touch what the listener feeds.
        book.invalidateAll()
        assertFalse(book.needsReading(MIFF_ID))
    }

    @Test
    fun liveStopped_keepsTheListButItAgesLikeAReading() {
        book.live(MIFF_ID, listOf(testRequest(slot = 1)))
        book.liveStopped(MIFF_ID)
        assertEquals(listOf("u2_1"), ids(book.view(MIFF_ID)))
        assertTrue(book.needsReading(MIFF_ID))
    }

    @Test
    fun retainOnly_forgetsTheMiffsThePersonLeft() {
        book.readingSucceeded(MIFF_ID, listOf(testRequest()))
        book.readingSucceeded(OTHER_MIFF_ID, listOf(testRequest(miffId = OTHER_MIFF_ID)))
        book.retainOnly(setOf(OTHER_MIFF_ID))
        assertNull(book.known(MIFF_ID))
        assertEquals(1, book.known(OTHER_MIFF_ID)!!.size)
    }
}
