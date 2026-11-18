package com.tomessa.miff.ui.feed

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The arithmetic of the full-bleed photo: whatever the ratio of the photo and of the screen, the picture covers the window, and no drag can uncover a margin. */
class PhotoFramingTest {
    private class Ratio(
        val name: String,
        val width: Int,
        val height: Int,
    )

    private val photos =
        listOf(
            Ratio("narrow portrait 1:4", 100, 400),
            Ratio("portrait 3:4", 300, 400),
            Ratio("square", 300, 300),
            Ratio("landscape 4:3", 400, 300),
            Ratio("very wide 4:1", 400, 100),
            Ratio("camera of the app 150 kB", 1066, 800),
        )

    // 16:9, 20:9 (the OPPO Find X5 Lite is 2400 x 1080), 19.5:9 (iPhone-like), a tablet 4:3 and a landscape phone.
    private val screens =
        listOf(
            Ratio("16:9", 1080, 1920),
            Ratio("20:9", 1080, 2400),
            Ratio("19.5:9", 1170, 2532),
            Ratio("tablet 4:3", 1536, 2048),
            Ratio("landscape 20:9", 2400, 1080),
        )

    @Test
    fun theCoverScale_coversBothSides_withNoFreeSpace_forEveryPhotoAndEveryScreen() {
        for (photo in photos) {
            for (screen in screens) {
                val scale = PhotoFraming.coverScale(photo.width, photo.height, screen.width, screen.height)
                val drawnWidth = photo.width * scale
                val drawnHeight = photo.height * scale
                assertTrue("${photo.name} on ${screen.name}: width", drawnWidth >= screen.width - 0.01f)
                assertTrue("${photo.name} on ${screen.name}: height", drawnHeight >= screen.height - 0.01f)
                // Exactly the window on one side at least (the smallest scale that covers: nothing is cropped for nothing).
                assertTrue(
                    "${photo.name} on ${screen.name}: tight",
                    drawnWidth <= screen.width + 0.01f || drawnHeight <= screen.height + 0.01f,
                )
            }
        }
    }

    @Test
    fun theVisibleShare_isWholeOnOneAxis_andCutOnTheOther() {
        // A 3:4 portrait on a 20:9 screen: the whole HEIGHT is kept and 40 % of the WIDTH is cut (the screen is much narrower than the photo): the side effect to report.
        val (w, h) = PhotoFraming.visibleShare(300, 400, 1080, 2400)
        assertEquals(0.6f, w, 0.001f)
        assertEquals(1f, h, 0.001f)
        // A 4:3 landscape photo on the same screen: the whole height is kept, only a third of the width remains.
        val (lw, lh) = PhotoFraming.visibleShare(400, 300, 1080, 2400)
        assertEquals(1f, lh, 0.001f)
        assertEquals(0.3375f, lw, 0.001f)
        // A photo that has the ratio of the screen loses nothing.
        val (sw, sh) = PhotoFraming.visibleShare(1080, 2400, 1080, 2400)
        assertEquals(1f, sw, 0.001f)
        assertEquals(1f, sh, 0.001f)
    }

    @Test
    fun atScale1_thereIsNoRoomToMove() {
        val limit = PhotoFraming.maxPan(1f, 1080, 2400)
        assertEquals(0f, limit.x, 0f)
        assertEquals(0f, limit.y, 0f)
        val still = PhotoFraming.clampPan(Offset(500f, -500f), 1f, 1080, 2400)
        assertEquals(0f, still.x, 0f)
        assertEquals(0f, still.y, 0f)
        // Below 1 never happens, and would not give room either.
        assertEquals(0f, PhotoFraming.maxPan(0.5f, 1080, 2400).x, 0f)
        assertFalse(PhotoFraming.covers(0.9f, Offset.Zero, 1080, 2400))
    }

    @Test
    fun theDrag_isBounded_soThatNoEdgeLeavesAMargin() {
        for (scale in listOf(1.01f, 1.5f, 2f, 3.3f, 4f)) {
            for (pan in listOf(Offset(9999f, 0f), Offset(-9999f, 9999f), Offset(0f, -9999f), Offset(37f, -12f))) {
                val clamped = PhotoFraming.clampPan(pan, scale, 1080, 2400)
                assertTrue("scale $scale, pan $pan", PhotoFraming.covers(scale, clamped, 1080, 2400))
            }
        }
        // At 2x the picture overflows by half the window on each side: that is the whole room.
        val limit = PhotoFraming.maxPan(2f, 1080, 2400)
        assertEquals(540f, limit.x, 0.001f)
        assertEquals(1200f, limit.y, 0.001f)
        // A drag that is already inside is left untouched.
        assertEquals(Offset(100f, -200f), PhotoFraming.clampPan(Offset(100f, -200f), 2f, 1080, 2400))
    }

    @Test
    fun zoomingOutAgain_bringsThePictureBackInside() {
        val atFour = PhotoFraming.clampPan(Offset(10_000f, 10_000f), 4f, 1080, 2400)
        val atTwo = PhotoFraming.clampPan(atFour, 2f, 1080, 2400)
        assertTrue(PhotoFraming.covers(2f, atTwo, 1080, 2400))
        assertEquals(Offset(540f, 1200f), atTwo)
    }

    @Test
    fun afterARotation_theOffsetOfThePortraitIsBoundedAgainInTheLandscapeWindow() {
        // Zoomed at 2x, dragged to the limit of a 1080 x 2400 window, then the phone turns to 2400 x 1080: the old offset leaves a gap, bounding it again fixes that.
        val portrait = PhotoFraming.clampPan(Offset(10_000f, 10_000f), 2f, 1080, 2400)
        val landscape = PhotoFraming.clampPan(portrait, 2f, 2400, 1080)
        assertTrue(PhotoFraming.covers(2f, landscape, 2400, 1080))
        // The vertical offset of 1200 would leave a gap in the 1080-high window: it is brought back to 540 (the horizontal one, 540, is still inside).
        assertEquals(Offset(540f, 540f), landscape)
    }
}
