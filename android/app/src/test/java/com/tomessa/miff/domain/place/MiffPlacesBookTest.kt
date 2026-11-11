package com.tomessa.miff.domain.place

import com.tomessa.miff.testsupport.testPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a reading of the places of a Miff is needed: the cost rules, with a clock we move by hand. */
class MiffPlacesBookTest {
    private var now = 0L
    private val book = MiffPlacesBook { now }
    private val two = listOf(testPlace("p1", "A"), testPlace("p2", "B"))

    @Test
    fun anUnknownMiff_needsAReading_andShowsNothingYet() {
        assertTrue(book.needsReading("a"))
        assertEquals(MiffPlacesView.Loading, book.view("a"))
    }

    @Test
    fun whatTheFeedRead_isFree_andNeverAges() {
        book.seedFromFeed(ReadPlaces("a", two))
        now += 10 * MiffPlacesBook.TTL_MILLIS
        assertFalse(book.needsReading("a"))
        assertEquals(2, (book.view("a") as MiffPlacesView.Ready).places.size)
    }

    @Test
    fun aReading_isReusedForFiveMinutes_thenReadAgain() {
        book.readingStarted("a")
        assertFalse("under way", book.needsReading("a"))
        book.readingSucceeded("a", two)
        now += MiffPlacesBook.TTL_MILLIS - 1
        assertFalse(book.needsReading("a"))
        now += 1
        assertTrue(book.needsReading("a"))
    }

    @Test
    fun aFailure_waitsThirtySeconds_andKeepsAListThatWasThere() {
        book.readingSucceeded("a", two)
        book.readingFailed("a")
        assertEquals(2, (book.view("a") as MiffPlacesView.Ready).places.size)
        assertFalse(book.needsReading("a"))
        now += MiffPlacesBook.RETRY_MILLIS
        assertTrue(book.needsReading("a"))
    }

    @Test
    fun aFailureWithNothingBefore_isUnavailable() {
        book.readingFailed("a")
        assertEquals(MiffPlacesView.Unavailable, book.view("a"))
    }

    @Test
    fun invalidateAll_marksEveryList_andANewSeedOfTheFeedClearsTheMark() {
        book.seedFromFeed(ReadPlaces("a", two))
        book.readingSucceeded("b", two)
        book.invalidateAll()
        assertTrue(book.needsReading("a"))
        assertTrue(book.needsReading("b"))
        book.seedFromFeed(ReadPlaces("a", two))
        assertFalse("the Feed has just heard the server", book.needsReading("a"))
    }

    @Test
    fun retainOnly_forgetsTheMiffsThatAreGone() {
        book.readingSucceeded("a", two)
        book.readingSucceeded("b", two)
        book.retainOnly(setOf("a"))
        assertEquals(MiffPlacesView.Loading, book.view("b"))
        assertTrue(book.needsReading("b"))
        assertFalse(book.needsReading("a"))
    }
}
