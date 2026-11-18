package com.tomessa.miff.ui.feed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.domain.update.PhotoLocation
import com.tomessa.miff.domain.update.SharedPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * The photo in FULL SCREEN is FULL BLEED (decision of the PM, 2026-11-17: "il faut que l'image prenne tout l'écran et qu'aucune marge ne soit visible, quelle que soit la
 * photo et quel que soit le device"), and its size and place never depend on its description. A real picture (red) is drawn in a window and
 * the pixels that are really painted are measured: not one of them may be black (a margin), for a narrow portrait, a portrait, a square, a landscape and a very wide
 * photo, on a 16:9 and on a 20:9 screen, with and without a description (the footer is drawn OVER the photo, on a veil that is dark but never black), and after
 * zooming and dragging as far as the fingers can go.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class PhotoFullScreenFramingBase(
    private val windowWidth: Int,
    private val windowHeight: Int,
) {
    @get:Rule
    val rule = createComposeRule()

    private val now = 1_700_000_000_000L

    private fun photoJpeg(
        width: Int,
        height: Int,
        split: Boolean,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = android.graphics.Color.RED })
        // Solid red everywhere, except with [split]: the right half is blue (to see where the crop is centred). A JPEG blends the limit between two colours, which would
        // look "black" under the veil, so the margin check never uses a split photo.
        if (split) {
            canvas.drawRect(
                width / 2f,
                0f,
                width.toFloat(),
                height.toFloat(),
                Paint().apply {
                    color =
                        android.graphics.Color.BLUE
                },
            )
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
    }

    protected fun show(
        photoWidth: Int,
        photoHeight: Int,
        description: String?,
        fontScale: Float = 1f,
        split: Boolean = false,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val photo =
            SharedPhoto(
                photoJpeg(photoWidth, photoHeight, split),
                now - 60_000L,
                now + 3_600_000L,
                PhotoLocation(48.85, 2.35, now - 120_000L),
                description,
            )
        rule.setContent {
            val base = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit
                        .Density(base.density, fontScale),
            ) {
                PhotoFullScreen(
                    opened = OpenedPhoto("Léa", photo),
                    texts = FeedTexts(context, java.time.ZoneId.of("UTC")),
                    nowMillis = now,
                    onOpenMap = { true },
                    onDismiss = {},
                )
            }
        }
    }

    /** Asserts that the picture fills the whole window and that no painted pixel is black (nothing but the photo, its veils, the buttons and the texts). */
    protected fun assertFullBleed() {
        val density = rule.density.density
        val bounds = rule.onNodeWithTag(FULL_SCREEN_PHOTO_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals(0f, bounds.left, 0.5f)
        assertEquals(0f, bounds.top, 0.5f)
        assertEquals(windowWidth * density, bounds.width, 1f)
        assertEquals(windowHeight * density, bounds.height, 1f)
        val image = rule.onNodeWithTag(FULL_SCREEN_PHOTO_TAG).captureToImage().toPixelMap()
        assertEquals((windowWidth * density).toInt(), image.width)
        assertEquals((windowHeight * density).toInt(), image.height)
        // The raw buffer (ARGB ints, row by row): a pixel map object per pixel would take minutes on a whole screen.
        val buffer = image.buffer
        var black = 0
        var firstBlack: Pair<Int, Int>? = null
        for (i in buffer.indices) {
            val argb = buffer[i]
            val brightest = maxOf((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
            if (brightest < 77) { // 0.3 of 255
                black++
                if (firstBlack == null) firstBlack = (i % image.width) to (i / image.width)
            }
        }
        assertEquals("black (margin) pixels, the first at $firstBlack", 0, black)
    }

    @Test
    fun portrait_isFullBleed() {
        show(300, 400, null)
        assertFullBleed()
    }

    @Test
    fun landscape_isFullBleed() {
        show(400, 300, null)
        assertFullBleed()
    }

    @Test
    fun withAShortDescription_isExactlyAsFullBleed() {
        show(300, 400, "Bien arrivée")
        assertFullBleed()
    }
}

/** A 16:9 phone (360 x 640 dp): the footer that overflows, the centred crop, the zoom and the drag as far as possible. */
@Config(sdk = [34], qualifiers = "en-w360dp-h640dp", application = android.app.Application::class)
class PhotoFullScreenFramingSixteenNineTest : PhotoFullScreenFramingBase(360, 640) {
    @Test
    fun withALongDescriptionAndTheLargestFont_isExactlyAsFullBleed() {
        // Line breaks make the footer really overflow (Robolectric has no real glyph widths): it is then capped at 45 % and scrolls, over the photo.
        show(300, 400, "a\n".repeat(70), fontScale = 2f)
        assertFullBleed()
    }

    @Test
    fun theCropIsCentred() {
        // The red/blue limit is in the middle of the photo, so it stays in the middle of the screen, whatever side is cut off.
        show(300, 400, null, split = true)
        val image = rule.onNodeWithTag(FULL_SCREEN_PHOTO_TAG).captureToImage().toPixelMap()
        val y = image.height / 3
        val left = image[image.width / 4, y]
        val right = image[image.width * 3 / 4, y]
        assertTrue("left half is red", left.red > 0.8f && left.blue < 0.3f)
        assertTrue("right half is blue", right.blue > 0.8f && right.red < 0.3f)
        assertTrue("left edge is red", image[0, y].red > 0.8f)
        assertTrue("right edge is blue", image[image.width - 1, y].blue > 0.8f)
    }

    @Test
    fun zoomingAndDraggingAsFarAsPossible_fromTheFooter_neverUncoversAMargin() {
        show(300, 400, "Bien arrivée")
        // A short footer does not scroll: it offers no scroll action, so it keeps no touch for itself.
        assertEquals(0, rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size)
        val footer = rule.onNode(hasText("Bien arrivée", substring = true))
        // Pinch started ON the footer: it zooms the photo (to the maximum).
        footer.performTouchInput {
            pinch(
                Offset(center.x - 10f, center.y),
                Offset(center.x - 60f, center.y),
                Offset(center.x + 10f, center.y),
                Offset(center.x + 60f, center.y),
            )
        }
        rule.waitForIdle()
        assertFullBleed()
        // Drag as far as the fingers can go, several times: the bounded drag never lets an edge of the picture show a margin.
        repeat(2) {
            footer.performTouchInput { swipe(Offset(10f, centerY), Offset(width - 10f, centerY), durationMillis = 200) }
            rule.waitForIdle()
            assertFullBleed()
            footer.performTouchInput { swipe(Offset(width - 10f, centerY), Offset(10f, centerY), durationMillis = 200) }
            footer.performTouchInput { swipe(Offset(width - 10f, centerY), Offset(10f, centerY), durationMillis = 200) }
            rule.waitForIdle()
            assertFullBleed()
        }
    }
}

/** A tall 20:9 phone (360 x 800 dp), like the OPPO Find X5 Lite: the extreme ratios of photo. */
@Config(sdk = [34], qualifiers = "en-w360dp-h800dp", application = android.app.Application::class)
class PhotoFullScreenFramingTwentyNineTest : PhotoFullScreenFramingBase(360, 800) {
    @Test
    fun narrowPortrait_isFullBleed() {
        show(100, 400, null)
        assertFullBleed()
    }

    @Test
    fun square_isFullBleed() {
        show(300, 300, null)
        assertFullBleed()
    }

    @Test
    fun veryWide_isFullBleed() {
        show(400, 100, null)
        assertFullBleed()
    }
}
