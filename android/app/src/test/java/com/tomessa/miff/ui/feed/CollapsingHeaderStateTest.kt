package com.tomessa.miff.ui.feed

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rules of the header that hides while scrolling down and comes back at once while scrolling up (decision of the PM, 2026-11-06): the header follows
 * the finger in both directions, never leaves its range, does not move when locked, and never leaves a blank band above the first card.
 */
class CollapsingHeaderStateTest {
    private fun measured(height: Int = 100) = CollapsingHeaderState().also { it.onMeasured(height) }

    @Test
    fun itHidesWhileTheContentGoesUp_scrollingDown() {
        val header = measured()
        header.follow(-30f)
        assertEquals(-30f, header.offsetPx, 0.001f)
        header.follow(-30f)
        assertEquals(-60f, header.offsetPx, 0.001f)
    }

    @Test
    fun itComesBackAtOnce_scrollingUp_evenFarFromTheTopOfTheList() {
        val header = measured()
        header.follow(-500f)
        assertEquals(-100f, header.offsetPx, 0.001f)
        // The very first upward movement shows it again: no need to be at the top of the list ("enter always").
        header.follow(12f)
        assertEquals(-88f, header.offsetPx, 0.001f)
        header.follow(200f)
        assertEquals(0f, header.offsetPx, 0.001f)
    }

    @Test
    fun itNeverLeavesItsRange() {
        val header = measured()
        header.follow(+80f)
        assertEquals(0f, header.offsetPx, 0.001f)
        header.follow(-1_000f)
        assertEquals(-100f, header.offsetPx, 0.001f)
    }

    @Test
    fun nothingMovesBeforeItIsMeasured() {
        val header = CollapsingHeaderState()
        header.follow(-50f)
        assertEquals(0f, header.offsetPx, 0.001f)
    }

    @Test
    fun whenLocked_itNeverMoves() {
        val header = measured()
        header.locked = true
        header.follow(-50f)
        assertEquals(0f, header.offsetPx, 0.001f)
        header.locked = false
        header.follow(-50f)
        assertEquals(-50f, header.offsetPx, 0.001f)
    }

    @Test
    fun aNewHeight_keepsTheOffsetInsideTheNewRange() {
        val header = measured(100)
        header.follow(-100f)
        header.onMeasured(60)
        assertEquals(-60f, header.offsetPx, 0.001f)
    }

    @Test
    fun nearTheTop_itIsNeverMoreHiddenThanTheListWasScrolled_soNoBlankBand() {
        val header = measured()
        header.follow(-100f)
        // The list is scrolled by only 20 px (the first card is still almost under the header): at most 20 px of the header are hidden.
        header.limitToScroll(20)
        assertEquals(-20f, header.offsetPx, 0.001f)
        // Back at the very top: fully shown.
        header.limitToScroll(0)
        assertEquals(0f, header.offsetPx, 0.001f)
    }

    @Test
    fun awayFromTheTop_nothingLimitsIt() {
        val header = measured()
        header.follow(-100f)
        header.limitToScroll(null)
        assertEquals(-100f, header.offsetPx, 0.001f)
    }

    @Test
    fun show_bringsItBackToTheTop() {
        val header = measured()
        header.follow(-70f)
        header.show()
        assertEquals(0f, header.offsetPx, 0.001f)
    }

    @Test
    fun theConnection_onlyFollows_itNeverConsumesTheScrollOfTheList() {
        val header = measured()
        val connection = CollapsingHeaderConnection(header, LazyListState())
        val consumedBefore = connection.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput)
        assertEquals(Offset.Zero, consumedBefore)
        assertEquals(-40f, header.offsetPx, 0.001f)
        // A fresh list is at its very top: after the scroll the header is not more hidden than the list moved (here, not at all).
        val consumedAfter = connection.onPostScroll(Offset.Zero, Offset.Zero, NestedScrollSource.UserInput)
        assertEquals(Offset.Zero, consumedAfter)
        assertEquals(0f, header.offsetPx, 0.001f)
    }
}
