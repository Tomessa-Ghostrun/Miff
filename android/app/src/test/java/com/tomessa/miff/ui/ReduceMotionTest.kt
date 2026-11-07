package com.tomessa.miff.ui

import com.tomessa.miff.ui.splash.ReduceMotion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReduceMotionTest {
    @Test
    fun animationsOffMeansReducedMotion() {
        assertTrue(ReduceMotion.scaleIsOff(0f))
    }

    @Test
    fun anyOtherScaleKeepsTheAnimation() {
        // The splash measures real time, so even a slowed-down scale cannot make it longer than its maximum.
        listOf(0.5f, 1f, 1.5f, 5f, 10f).forEach { assertFalse(ReduceMotion.scaleIsOff(it)) }
    }
}
