package com.tomessa.miff.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The FORMAT of the photo of an update (design/screens/update.md 6.1, ticket Update D24, criteria V3 and V7): the cases here are the contract with iOS. Nothing
 * is read from a phone: the crop, the sizes and the ladder of compression are pure.
 */
class UpdatePhotoFormatTest {
    private fun aspect(rect: PixelRect) = rect.width.toFloat() / rect.height

    // --- The ratio of the viewfinder, bounded between 9:21 and 9:16 -------------------------------------------------------------

    @Test
    fun theRatioOfTheTwoPhonesOfThePm_isKeptAsItIs() {
        // iPhone 11 Pro: 1125 x 2436 (19.5:9). OPPO Find X5 Lite: 1080 x 2400 (20:9).
        assertEquals(1125f / 2436f, UpdatePhotoFormat.boundedAspect(1125, 2436), 0.0001f)
        assertEquals(1080f / 2400f, UpdatePhotoFormat.boundedAspect(1080, 2400), 0.0001f)
    }

    @Test
    fun aTallerScreenIsBoundedAt9To21_aWiderOneAt9To16() {
        assertEquals(9f / 21f, UpdatePhotoFormat.boundedAspect(900, 2400), 0.0001f)
        assertEquals(9f / 16f, UpdatePhotoFormat.boundedAspect(1200, 1600), 0.0001f)
        // A tablet in landscape, a folded phone: still bounded.
        assertEquals(9f / 16f, UpdatePhotoFormat.boundedAspect(2560, 1600), 0.0001f)
    }

    @Test
    fun aZoneWithNoSizeGivesTheWidestRatioNeverADivisionByZero() {
        assertEquals(9f / 16f, UpdatePhotoFormat.boundedAspect(0, 0), 0.0001f)
        assertEquals(9f / 16f, UpdatePhotoFormat.boundedAspect(100, 0), 0.0001f)
    }

    // --- The "cover" crop ---------------------------------------------------------------------------------------------------------

    @Test
    fun aSensorImageOf3To4_isCutInWidth_toTheRatioOfTheViewfinder() {
        // A 4:3 sensor held upright: 3000 x 4000. The viewfinder is 20:9 tall (0.45): the width is cut, centred.
        val crop = UpdatePhotoFormat.coverCrop(3000, 4000, 0.45f)
        assertEquals(4000, crop.height)
        assertEquals(1800, crop.width)
        assertEquals(600, crop.left)
        assertEquals(0, crop.top)
        assertEquals(0.45f, aspect(crop), 0.001f)
    }

    @Test
    fun aSourceTallerThanTheRatio_isCutInHeight() {
        val crop = UpdatePhotoFormat.coverCrop(1000, 3000, 9f / 16f)
        assertEquals(1000, crop.width)
        assertEquals(1778, crop.height)
        assertEquals(0, crop.left)
        assertEquals(611, crop.top)
    }

    @Test
    fun theCropIsAlwaysInsideTheSource_whatEverTheShape() {
        val sizes = listOf(1 to 1, 1 to 5000, 5000 to 1, 640 to 480, 480 to 640, 4000 to 3000, 3000 to 4000, 7 to 13)
        val ratios = listOf(UpdatePhotoFormat.MIN_ASPECT, 0.45f, UpdatePhotoFormat.MAX_ASPECT, Float.NaN, 0f, -1f)
        for ((w, h) in sizes) {
            for (ratio in ratios) {
                val crop = UpdatePhotoFormat.coverCrop(w, h, ratio)
                assertTrue("$w x $h at $ratio: $crop", crop.width in 1..w && crop.height in 1..h)
                assertTrue(
                    "$w x $h at $ratio: $crop",
                    crop.left >= 0 && crop.top >= 0 && crop.left + crop.width <= w && crop.top + crop.height <= h,
                )
            }
        }
    }

    // --- The size of the saved image ----------------------------------------------------------------------------------------------

    @Test
    fun theLongSideIsAtMost1024_theRatioIsKept_aSmallPhotoIsNeverEnlarged() {
        assertEquals(461 to 1024, UpdatePhotoFormat.outputSize(1800, 4000, 1024))
        assertEquals(576 to 1024, UpdatePhotoFormat.outputSize(1000, 1778, 1024))
        assertEquals(439 to 1024, UpdatePhotoFormat.outputSize(900, 2100, 1024))
        assertEquals(300 to 600, UpdatePhotoFormat.outputSize(300, 600, 1024))
        // A source that is wider than tall (a tablet) is also bounded on its long side.
        assertEquals(1024 to 576, UpdatePhotoFormat.outputSize(2000, 1125, 1024))
    }

    // --- The ladder of compression --------------------------------------------------------------------------------------------------

    @Test
    fun theLadderStopsAtTheFirstResultThatFits_bestQualityAndBiggestSideFirst() {
        val tried = mutableListOf<Pair<Int, Int>>()
        val bytes =
            UpdatePhotoFormat.fit { side, quality ->
                tried += side to quality
                ByteArray(if (quality > 60) 160_000 else 120_000)
            }
        assertEquals(120_000, bytes?.size)
        assertEquals(listOf(1024 to 80, 1024 to 70, 1024 to 60), tried)
    }

    @Test
    fun whenTheBiggestSideNeverFits_theNextSideIsTried_downTo768() {
        val tried = mutableListOf<Pair<Int, Int>>()
        val bytes =
            UpdatePhotoFormat.fit { side, quality ->
                tried += side to quality
                ByteArray(if (side == 768 && quality == 55) 149_999 else 200_000)
            }
        assertEquals(149_999, bytes?.size)
        assertEquals(1024 to 80, tried.first())
        assertEquals(768 to 55, tried.last())
        assertEquals(12, tried.size)
    }

    @Test
    fun theLimitIs150000Bytes_includedAndNotOneMore() {
        assertEquals(150_000, UpdatePhotoFormat.fit { _, _ -> ByteArray(150_000) }?.size)
        assertNull(UpdatePhotoFormat.fit { _, _ -> ByteArray(150_001) })
    }

    @Test
    fun whenNothingFits_orTheEncoderFails_thereIsNoPhoto() {
        assertNull(UpdatePhotoFormat.fit { _, _ -> ByteArray(500_000) })
        assertNull(UpdatePhotoFormat.fit { _, _ -> null })
        // An empty result is not a photo either.
        assertNull(UpdatePhotoFormat.fit { _, _ -> ByteArray(0) })
    }

    @Test
    fun theFrameOfThePreview_keepsThePhotoWhole() {
        // A 461 x 1024 photo in a frame of 360 x 600: the height decides.
        assertEquals(270 to 600, UpdatePhotoFormat.fitInside(461, 1024, 360, 600))
        assertEquals(0 to 0, UpdatePhotoFormat.fitInside(0, 10, 360, 600))
    }

    // --- The photo in memory --------------------------------------------------------------------------------------------------------

    @Test
    fun aPhotoWaitingMoreThan5Minutes_isOld_atExactly5Minutes_itIsNot() {
        val photo = UpdatePhoto(ByteArray(10), 461, 1024, takenAtMillis = 1_000_000L)
        assertEquals(false, photo.isStale(1_000_000L + UpdatePhoto.STALE_MILLIS))
        assertEquals(true, photo.isStale(1_000_000L + UpdatePhoto.STALE_MILLIS + 1))
    }

    @Test
    fun aPhotoShowsNothingOfItself() {
        val photo = UpdatePhoto(byteArrayOf(1, 2, 3), 461, 1024, 5L)
        assertEquals("UpdatePhoto(redacted)", photo.toString())
    }
}
