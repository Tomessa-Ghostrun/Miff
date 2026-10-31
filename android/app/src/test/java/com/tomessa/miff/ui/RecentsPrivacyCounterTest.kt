package com.tomessa.miff.ui

import com.tomessa.miff.ui.components.RecentsPrivacyCounter
import com.tomessa.miff.ui.components.RecentsScreenshotSwitch
import org.junit.Assert.assertEquals
import org.junit.Test

/** The picture of the app in the recent apps stays hidden as long as ONE screen that shows a Miff is on view (decision of 2026-10-31). */
class RecentsPrivacyCounterTest {
    private val calls = mutableListOf<Boolean>()
    private val counter = RecentsPrivacyCounter(RecentsScreenshotSwitch { calls += it })

    @Test
    fun theFirstScreenHidesIt_theLastOneGivesItBack() {
        counter.acquire()
        counter.release()
        assertEquals(listOf(false, true), calls)
    }

    @Test
    fun twoScreensThatOverlapDuringATransition_giveItBackOnlyWhenBothHaveGone() {
        counter.acquire()
        counter.acquire()
        counter.release()
        assertEquals("still one screen on view", listOf(false), calls)
        counter.release()
        assertEquals(listOf(false, true), calls)
    }

    @Test
    fun aReleaseWithoutAnAcquire_changesNothing() {
        counter.release()
        assertEquals(emptyList<Boolean>(), calls)
        counter.acquire()
        counter.release()
        counter.release()
        assertEquals(listOf(false, true), calls)
    }
}
