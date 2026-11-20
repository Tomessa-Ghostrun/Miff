package com.tomessa.miff.ui.notification

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * CNC-L, CNC-12: the logo of Miff as a round picture. The glyph is the very one of the icon of the app (same two paths, to the digit), on a disc #043CD1; the heart stays a HEART (not a dot) at
 * 40 dp on the densities of the phones, and the picture is the same on the light and the dark theme (colours that do not depend on it). The drawing is checked for real (Robolectric native
 * graphics) and written as PNGs in `build/screenshots` for the human check.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class CenterLogoTest {
    private fun drawable(sizePx: Int): Bitmap {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val d = ContextCompat.getDrawable(context, R.drawable.ic_miff_logo_avatar)!!.mutate()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, sizePx, sizePx)
        d.draw(Canvas(bitmap))
        return bitmap
    }

    private fun save(
        name: String,
        bitmap: Bitmap,
    ) {
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun isHeartColour(pixel: Int): Boolean {
        // #D1D828 (209, 216, 40), tolerance for the edges of the anti-aliasing
        return Color.alpha(pixel) > 200 && Color.red(pixel) in 180..235 && Color.green(pixel) in 190..235 &&
            Color.blue(pixel) < 100
    }

    private fun isBlue(pixel: Int) =
        Color.alpha(pixel) > 200 && Color.red(pixel) < 40 && Color.green(pixel) in 40..80 &&
            Color.blue(pixel) in 190..225

    private fun isWhite(pixel: Int) =
        Color.alpha(pixel) > 200 && Color.red(pixel) > 235 && Color.green(pixel) > 235 && Color.blue(pixel) > 235

    private fun count(
        bitmap: Bitmap,
        test: (Int) -> Boolean,
    ): Int {
        var n = 0
        for (x in 0 until bitmap.width) for (y in 0 until bitmap.height) if (test(bitmap.getPixel(x, y))) n++
        return n
    }

    @Test
    fun theGlyphIsTheOneOfTheIconOfTheApp_toTheDigit() {
        fun paths(file: String): List<String> =
            Regex("""android:pathData="([^"]+)"""")
                .findAll(File("src/main/res/drawable/$file").readText())
                .map { it.groupValues[1] }
                .toList()
        val icon = paths("ic_launcher_foreground.xml")
        val avatar = paths("ic_miff_logo_avatar.xml")
        assertEquals("the icon has the letters and the heart", 2, icon.size)
        // the disc first, then the same two paths
        assertEquals(icon, avatar.drop(1))
        val xml = File("src/main/res/drawable/ic_miff_logo_avatar.xml").readText()
        assertTrue(xml.contains("#043CD1") && xml.contains("#FFFFFF") && xml.contains("#D1D828"))
    }

    @Test
    fun theRoundIsBlue_theCornersAreEmpty_noRoundedSquareNoMask() {
        val bitmap = drawable(480)
        assertEquals("corner: nothing (the disc, not a square)", 0, Color.alpha(bitmap.getPixel(2, 2)))
        assertEquals(0, Color.alpha(bitmap.getPixel(477, 2)))
        assertEquals(0, Color.alpha(bitmap.getPixel(2, 477)))
        assertEquals(0, Color.alpha(bitmap.getPixel(477, 477)))
        assertTrue("the edge of the disc, on its axis: blue", isBlue(bitmap.getPixel(240, 8)))
        assertTrue("the left edge of the disc: blue", isBlue(bitmap.getPixel(8, 240)))
    }

    @Test
    fun theHeartStaysAHeartAtFortyDp_onTheDensitiesOfThePhones() {
        // 40 dp: mdpi 40 px, hdpi 60, xhdpi 80, xxhdpi 120, xxxhdpi 160
        val sizes = mapOf(80 to 120, 120 to 260, 160 to 480)
        sizes.forEach { (px, minHeartPixels) ->
            val bitmap = drawable(px)
            save("cnc-l-logo-$px", bitmap)
            val heart = count(bitmap, ::isHeartColour)
            assertTrue(
                "at $px px the heart must still be drawn: $heart pixels (at least $minHeartPixels)",
                heart >= minHeartPixels,
            )
            assertTrue("the white M and i are there", count(bitmap, ::isWhite) > px)
        }
        // the heart is a blob of its own, above the right-hand end of the M, not smeared on the letters: its centre sits in the upper right of the disc
        val bitmap = drawable(480)
        save("cnc-l-logo-480", bitmap)
        var sx = 0L
        var sy = 0L
        var n = 0
        for (x in 0 until 480) {
            for (y in 0 until 480) {
                if (isHeartColour(bitmap.getPixel(x, y))) {
                    sx += x
                    sy += y
                    n++
                }
            }
        }
        val cx = sx / n.toDouble() / 480.0
        val cy = sy / n.toDouble() / 480.0
        assertTrue("the heart is on the right (x=$cx)", cx in 0.58..0.76)
        assertTrue("the heart is in the upper half (y=$cy)", cy in 0.22..0.42)
    }

    @Test
    fun theGlyphIsLargerThanInTheIcon_andStaysInsideTheRound() {
        val bitmap = drawable(480)
        // no glyph pixel (white or yellow-green) in the outer 12 % ring of the disc
        val centre = 240.0
        val limit = 240.0 * 0.88
        var outside = 0
        var inside = 0
        for (x in 0 until 480) {
            for (y in 0 until 480) {
                val p = bitmap.getPixel(x, y)
                if (isWhite(p) || isHeartColour(p)) {
                    val d = Math.hypot(x - centre, y - centre)
                    if (d > limit) outside++ else inside++
                }
            }
        }
        assertEquals("the glyph never touches the edge of the round", 0, outside)
        assertTrue(inside > 0)
        // bigger than the icon's glyph (the icon's M is 45 of 108 units wide): here about 58 of 108
        var left = 480
        var right = 0
        for (x in 0 until 480) {
            for (y in 0 until 480) {
                if (isWhite(bitmap.getPixel(x, y))) {
                    left = minOf(left, x)
                    right = maxOf(right, x)
                }
            }
        }
        val widthShare = (right - left) / 480.0
        assertTrue("the glyph is about 52-58 % of the round: $widthShare", widthShare in 0.50..0.62)
    }
}
