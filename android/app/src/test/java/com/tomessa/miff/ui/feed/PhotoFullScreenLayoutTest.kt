package com.tomessa.miff.ui.feed

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
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
 * The SIZE and the PLACE of the photo in full screen never depend on its description (regression of the recipe of the description APK: the photo was shown
 * smaller, with black bands). A real picture (red, 300 x 400 = 3:4) is drawn in a 360 x 640 dp window and the pixels that are really painted are measured:
 * Fit means 360 x 480 centred, so the red starts at y = 80 and spans the whole width, with no description, a short one, a long one with the largest font.
 * The footer is drawn OVER the photo: it hides the bottom of the picture under its veil, it never moves or shrinks the picture.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w360dp-h640dp", application = android.app.Application::class)
class PhotoFullScreenLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = 1_700_000_000_000L

    private fun redJpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    private fun show(
        description: String?,
        fontScale: Float = 1f,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val photo =
            SharedPhoto(
                redJpeg(),
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

    /** The painted red: left, top, right (inclusive) of the part of the picture that is not under the footer veil, measured on the rows above the footer. */
    private class Painted(
        val left: Int,
        val right: Int,
        val top: Int,
    )

    private fun painted(): Painted {
        val image = rule.onNodeWithTag(FULL_SCREEN_PHOTO_TAG).captureToImage().toPixelMap()
        var left = Int.MAX_VALUE
        var right = -1
        var top = Int.MAX_VALUE
        // Only the rows above the footer (at most 45 % of 640 dp from the bottom): the picture there is never veiled.
        val lastRow = (image.height * 0.5f).toInt()
        for (y in 0 until lastRow) {
            for (x in 0 until image.width) {
                val c = image[x, y]
                if (c.red > 0.8f && c.green < 0.3f && c.blue < 0.3f) {
                    left = minOf(left, x)
                    right = maxOf(right, x)
                    top = minOf(top, y)
                }
            }
        }
        return Painted(left, right, top)
    }

    private fun assertPictureUntouched() {
        val bounds = rule.onNodeWithTag(FULL_SCREEN_PHOTO_TAG).fetchSemanticsNode().boundsInRoot
        // The picture itself fills the whole window (nothing is taken from it by the footer).
        assertEquals(0f, bounds.left, 0.5f)
        assertEquals(0f, bounds.top, 0.5f)
        assertEquals(360f * rule.density.density, bounds.width, 1f)
        assertEquals(640f * rule.density.density, bounds.height, 1f)
        val p = painted()
        val density = rule.density.density
        // Fit: full width, and 3:4 centred => 80 dp of black above.
        assertEquals("left edge", 0, p.left)
        assertEquals("right edge", (360 * density).toInt() - 1, p.right)
        assertEquals("top edge", (80 * density).toInt(), p.top)
    }

    @Test
    fun withoutDescription_thePhotoFillsTheWidth_centred() {
        show(null)
        assertPictureUntouched()
    }

    @Test
    fun withAShortDescription_thePhotoIsExactlyTheSame() {
        show("Bien arrivée")
        assertPictureUntouched()
    }

    @Test
    fun withALongDescriptionAndTheLargestFont_thePhotoIsExactlyTheSame() {
        // Line breaks make the footer really overflow (Robolectric has no real glyph widths): it is then capped at 45 % and scrolls, over the photo.
        show("a\n".repeat(70), fontScale = 2f)
        assertPictureUntouched()
    }

    @Test
    fun withoutOverflow_theFooterTakesNoTouch_andAGestureStartedOnItZoomsThePhoto() {
        show("Bien arrivée")
        // A short footer does not scroll: it offers no scroll action, so it keeps no touch for itself.
        assertEquals(0, rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size)
        val before = painted()
        rule.onNode(hasText("Bien arrivée", substring = true)).performTouchInput {
            pinch(
                Offset(center.x - 10f, center.y),
                Offset(center.x - 60f, center.y),
                Offset(center.x + 10f, center.y),
                Offset(center.x + 60f, center.y),
            )
        }
        rule.waitForIdle()
        val after = painted()
        // Zoomed in: the picture overflows the window on every side, so the red now reaches the top edge, 80 dp higher than at rest.
        assertTrue("zoomed: top ${after.top} < ${before.top}", after.top < before.top)
    }
}
