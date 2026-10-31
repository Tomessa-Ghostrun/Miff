package com.tomessa.miff.domain

import com.tomessa.miff.domain.startup.FrameGapTracker
import com.tomessa.miff.domain.startup.StartupMark
import com.tomessa.miff.domain.startup.StartupReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupReportTest {
    private fun report(vararg marks: Pair<StartupMark, Long>) =
        StartupReport(
            cold = true,
            baseElapsedMs = 1_000L,
            marks = mapOf(*marks),
            maxFrameGapMs = 0L,
            frames = 0,
        )

    @Test
    fun durationsAreCountedFromTheCreationOfTheProcess() {
        val r =
            report(
                StartupMark.APP_CREATE_START to 1_300L,
                StartupMark.APP_CREATE_END to 1_450L,
                StartupMark.FIRST_FRAME to 1_900L,
                StartupMark.HEART_VISIBLE to 2_020L,
                StartupMark.CONTENT_MOUNTED to 2_400L,
                StartupMark.SPLASH_DONE to 4_700L,
            )
        assertEquals(300L, r.processToApplicationMs)
        assertEquals(150L, r.applicationCreateMs)
        assertEquals(450L, r.appToFirstFrameMs)
        assertEquals(900L, r.toFirstFrameMs)
        assertEquals(120L, r.firstFrameToHeartMs)
        assertEquals(1_020L, r.toHeartMs)
        assertEquals(1_400L, r.toContentMountedMs)
        assertTrue(r.isComplete)
    }

    @Test
    fun aMomentNotReachedYetIsUnknownNotZero() {
        val r = report(StartupMark.FIRST_FRAME to 1_900L)
        assertEquals(900L, r.toFirstFrameMs)
        assertNull(r.toHeartMs)
        assertNull(r.firstFrameToHeartMs)
        assertNull(r.applicationCreateMs)
        assertFalse(r.isComplete)
    }

    @Test
    fun aClockGoingBackwardsNeverGivesANegativeDuration() {
        val r = report(StartupMark.FIRST_FRAME to 500L, StartupMark.HEART_VISIBLE to 400L)
        assertEquals(0L, r.toFirstFrameMs)
        assertEquals(0L, r.firstFrameToHeartMs)
    }

    @Test
    fun theLongestGapBetweenTwoImagesRevealsAFreeze() {
        val gaps = FrameGapTracker()
        listOf(0L, 16L, 33L, 49L, 560L, 577L, 593L).forEach(gaps::onFrame)
        assertEquals(7, gaps.frames)
        assertEquals(511L, gaps.maxGapMs)
        gaps.reset()
        assertEquals(0, gaps.frames)
        assertEquals(0L, gaps.maxGapMs)
        gaps.onFrame(10_000L)
        assertEquals(0L, gaps.maxGapMs) // the first image has no previous one
    }
}
