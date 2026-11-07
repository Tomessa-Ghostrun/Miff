package com.tomessa.miff.domain.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The square crop (Onboarding ticket 6.3: square crop required): always a square, always inside the photo. */
class CropMathTest {
    @Test
    fun atZoomOne_theSquareIsTheShortSide_centred() {
        assertEquals(SquareCrop(500, 0, 1000), CropMath.toCrop(2000, 1000, 1f, 1000f, 500f))
        assertEquals(SquareCrop(0, 500, 1000), CropMath.toCrop(1000, 2000, 1f, 500f, 1000f))
        assertEquals(SquareCrop(0, 0, 800), CropMath.toCrop(800, 800, 1f, 400f, 400f))
    }

    @Test
    fun zoomingMakesTheSquareSmaller() {
        assertEquals(500, CropMath.toCrop(2000, 1000, 2f, 1000f, 500f).size)
        assertEquals(250, CropMath.toCrop(2000, 1000, 4f, 1000f, 500f).size)
    }

    @Test
    fun zoomIsKeptBetweenOneAndFour() {
        assertEquals(1f, CropMath.clampZoom(0.2f), 0f)
        assertEquals(4f, CropMath.clampZoom(40f), 0f)
        assertEquals(1f, CropMath.clampZoom(Float.NaN), 0f)
        assertEquals(1000, CropMath.toCrop(2000, 1000, 0.1f, 1000f, 500f).size)
    }

    @Test
    fun theSquareNeverLeavesThePhoto_whateverTheCentre() {
        val sizes = listOf(100 to 100, 4000 to 3000, 3000 to 4000, 17 to 5000, 5000 to 17, 1 to 1)
        val centres = listOf(-1e9f, -5f, 0f, 1f, 777f, 1e9f, Float.NaN)
        for ((w, h) in sizes) {
            for (zoom in listOf(1f, 1.5f, 2.7f, 4f)) {
                for (cx in centres) {
                    for (cy in centres) {
                        val crop = CropMath.toCrop(w, h, zoom, cx, cy)
                        assertTrue("size $crop in ${w}x$h", crop.size in 1..minOf(w, h))
                        assertTrue("left $crop in ${w}x$h", crop.left >= 0 && crop.left + crop.size <= w)
                        assertTrue("top $crop in ${w}x$h", crop.top >= 0 && crop.top + crop.size <= h)
                    }
                }
            }
        }
    }

    @Test
    fun movingFarToTheCorner_stopsAtTheCorner() {
        assertEquals(SquareCrop(1000, 0, 1000), CropMath.toCrop(2000, 1000, 1f, 99999f, -99999f))
        assertEquals(SquareCrop(1750, 750, 250), CropMath.toCrop(2000, 1000, 4f, 99999f, 99999f))
        assertEquals(SquareCrop(0, 0, 250), CropMath.toCrop(2000, 1000, 4f, -5f, -5f))
    }

    @Test
    fun theCentreIsClampedTheSameWayForTheScreen() {
        val (x, y) = CropMath.clampCenter(2000, 1000, 2f, -10f, 5000f)
        assertEquals(250f, x, 0f)
        assertEquals(750f, y, 0f)
    }
}
