package com.tomessa.miff.ui.paywall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The maths of the burst of confetti of the confirmation: deterministic, light, inside the screen at the start, gone at the end. */
class ConfettiTest {
    private val pieces = Confetti.pieces()

    @Test
    fun theBurstIsLight_andTheSameEveryTime() {
        assertEquals(Confetti.COUNT, pieces.size)
        assertTrue("a light burst", Confetti.COUNT <= 80)
        assertEquals(pieces, Confetti.pieces())
    }

    @Test
    fun nothingIsDrawnBeforeAPieceStarts() {
        pieces.forEach { assertNull(Confetti.yAt(it, 0f)) }
        pieces.forEach { assertEquals(0f, Confetti.alphaAt(it, 0f), 0f) }
    }

    @Test
    fun everyPieceStartsAboveTheScreen_andEndsBelowIt() {
        pieces.forEach { piece ->
            val start = Confetti.yAt(piece, piece.delay + 0.0001f)
            assertNotNull(start)
            assertTrue("starts above the top edge", start!! < 0.01f)
            val end = Confetti.yAt(piece, 1f)
            assertTrue("ends below the bottom edge: $end", end!! >= 1f)
        }
    }

    @Test
    fun everyPieceFades_beforeTheEnd() {
        pieces.forEach { piece ->
            assertEquals(1f, Confetti.alphaAt(piece, piece.delay + 0.01f), 0f)
            assertEquals(0f, Confetti.alphaAt(piece, 1f), 0.0001f)
        }
    }

    @Test
    fun thePiecesStayCloseToTheScreen_horizontally() {
        pieces.forEach { piece ->
            listOf(0.1f, 0.5f, 0.9f, 1f).forEach { progress ->
                val x = Confetti.xAt(piece, progress)
                assertTrue("x $x", x > -0.2f && x < 1.2f)
            }
        }
    }

    @Test
    fun theDurationIsAboutThreeSeconds() {
        assertTrue(Confetti.DURATION_MILLIS in 2_000..4_000)
    }
}
