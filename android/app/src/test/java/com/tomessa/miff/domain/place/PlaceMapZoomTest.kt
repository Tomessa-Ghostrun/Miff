package com.tomessa.miff.domain.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The zoom of the map of a place: limits, framing, memory of the zoom (ticket `lieu-carte-zoom`). */
class PlaceMapZoomTest {
    @Test
    fun range_isAroundTheFraming_threeLevelsEachWay() {
        val range = PlaceMapZoom.range(15.0)
        assertEquals(15.0, range.fit, 0.0)
        assertEquals(12.0, range.min, 0.0)
        assertEquals(18.0, range.max, 0.0)
    }

    @Test
    fun range_neverGoesBelowTheFloor_norAboveTheFinestDetail() {
        val far = PlaceMapZoom.range(4.0)
        assertEquals(PlaceMapZoom.FLOOR, far.min, 0.0)
        val near = PlaceMapZoom.range(18.5)
        assertEquals(PlaceMapZoom.CEILING, near.max, 0.0)
        assertTrue(near.min <= near.fit && near.fit <= near.max)
    }

    @Test
    fun range_aFramingOutsideTheLimitsOrNotANumber_staysUsable() {
        listOf(-5.0, 0.0, 25.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { fit ->
            val range = PlaceMapZoom.range(fit)
            assertTrue("fit $fit", range.min <= range.fit && range.fit <= range.max)
            assertTrue(range.min >= PlaceMapZoom.FLOOR && range.max <= PlaceMapZoom.CEILING)
        }
    }

    @Test
    fun range_everyAllowedRadius_hasAFramingWithinTheAbsoluteLimits() {
        // The smallest circle (50 m) and the largest (800 m) are framed at about level 16 and 12 in a 176 dp box: the zoom the person
        // can reach never makes a circle smaller than 1/8 of its framing (still a visible disc) nor leaves the tiles' detail.
        listOf(12.0, 16.0).forEach { fit ->
            val range = PlaceMapZoom.range(fit)
            assertEquals(PlaceMapZoom.LEVELS_OUT, range.fit - range.min, 0.0)
            assertEquals(PlaceMapZoom.LEVELS_IN, range.max - range.fit, 0.0)
        }
    }

    @Test
    fun offsetOf_isTheDistanceToTheFraming_andNeverLeavesTheLimits() {
        assertEquals(0.0, PlaceMapZoom.offsetOf(15.0, 15.0), 0.0)
        assertEquals(1.5, PlaceMapZoom.offsetOf(16.5, 15.0), 1e-9)
        assertEquals(-2.0, PlaceMapZoom.offsetOf(13.0, 15.0), 1e-9)
        assertEquals(3.0, PlaceMapZoom.offsetOf(30.0, 15.0), 1e-9)
        assertEquals(-3.0, PlaceMapZoom.offsetOf(0.0, 15.0), 1e-9)
        assertEquals(0.0, PlaceMapZoom.offsetOf(Double.NaN, 15.0), 0.0)
    }

    @Test
    fun restore_givesBackTheZoomChosen_evenWhenTheFramingChangedWithTheRotation() {
        // Before the rotation: framing 15, the person zoomed in by 2 levels. After: the box is not the same, the framing is 14.5.
        val offset = PlaceMapZoom.offsetOf(17.0, 15.0)
        assertEquals(16.5, PlaceMapZoom.restore(14.5, offset), 1e-9)
    }

    @Test
    fun restore_aBadSavedValue_givesTheFraming_andALargeOneIsHeldAtTheLimit() {
        assertEquals(15.0, PlaceMapZoom.restore(15.0, Double.NaN), 0.0)
        assertEquals(15.0, PlaceMapZoom.restore(15.0, 0.0), 0.0)
        assertEquals(18.0, PlaceMapZoom.restore(15.0, 40.0), 0.0)
        assertEquals(12.0, PlaceMapZoom.restore(15.0, -40.0), 0.0)
    }

    // --- The decision of the framing (review of PR #262) -----------------------------------------------------------------------

    @Test
    fun plan_aMeasureThatIsMissingOrNotBelievable_decidesNothing() {
        // A map of size 0 (or not laid out yet) answers with nothing, NaN, infinity, or the lowest zoom: recording that would leave the
        // map framed at the floor for good. The caller records nothing and tries again at the next layout.
        listOf(null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -3.0, 0.0, 2.9, 22.1, 25.5).forEach { measured ->
            assertNull("measure $measured", PlaceMapZoom.plan(measured, restoring = true, savedOffset = 1.0))
            assertNull("measure $measured", PlaceMapZoom.plan(measured, restoring = false, savedOffset = 0.0))
        }
    }

    @Test
    fun plan_aRealMeasure_isBelievable_forEveryRadiusOfTheSlider() {
        // About level 12 (800 m) to 17 (50 m) in a box of 176 dp: well inside what is accepted.
        listOf(PlaceMapZoom.FLOOR, 12.0, 14.5, 17.0, PlaceMapZoom.MAX_PLAUSIBLE_FIT).forEach { measured ->
            val plan = PlaceMapZoom.plan(measured, restoring = false, savedOffset = 0.0)!!
            assertEquals(PlaceMapZoom.range(measured).min, plan.range.min, 0.0)
            assertEquals(PlaceMapZoom.range(measured).max, plan.range.max, 0.0)
        }
    }

    @Test
    fun plan_firstFraming_givesBackTheSavedZoom_withinTheLimits() {
        val plan = PlaceMapZoom.plan(15.0, restoring = true, savedOffset = 2.0)!!
        assertEquals(17.0, plan.wanted, 1e-9)
        assertEquals(15.0, plan.range.fit, 0.0)
        assertEquals(18.0, PlaceMapZoom.plan(15.0, restoring = true, savedOffset = 40.0)!!.wanted, 0.0)
        assertEquals(12.0, PlaceMapZoom.plan(15.0, restoring = true, savedOffset = -40.0)!!.wanted, 0.0)
        assertEquals(15.0, PlaceMapZoom.plan(15.0, restoring = true, savedOffset = Double.NaN)!!.wanted, 0.0)
    }

    @Test
    fun plan_aNewFraming_goesBackToTheFraming_whateverWasSaved() {
        // Radius or address changed while the person had zoomed: the circle is framed again, the zoom is back to 0 (rule Z7).
        val plan = PlaceMapZoom.plan(14.0, restoring = false, savedOffset = 2.5)!!
        assertEquals(14.0, plan.wanted, 0.0)
        assertEquals(plan.range.fit, plan.wanted, 0.0)
    }
}
