package com.tomessa.miff.data.update

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import com.tomessa.miff.domain.update.UpdatePhotoFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Random

/**
 * The photo of an update, with the real image codecs (Robolectric native graphics, no phone needed): at most 150 000 bytes, at the ratio of the viewfinder, 1 024 px
 * on the long side at most, and NO METADATA whatever the source holds (a photo taken on the spot carries a position; criteria V3, V4, risk R6). The sizes of
 * ten photos are written to build/photo-sizes.txt for the pull request (criterion QU1).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class AndroidPhotoEncoderTest {
    private val encoder = AndroidPhotoEncoder { 1_800_000_000_000L }
    private val aspect = 1080f / 2400f

    private fun encode(
        bitmap: Bitmap,
        ratio: Float = aspect,
    ) = runBlocking { encoder.encode(bitmap, ratio) }

    private fun gradient(
        width: Int,
        height: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        for (y in 0 until height step 4) {
            paint.color = Color.rgb(y * 255 / height, 120, 255 - y * 255 / height)
            canvas.drawRect(0f, y.toFloat(), width.toFloat(), (y + 4).toFloat(), paint)
        }
        paint.color = Color.rgb(230, 190, 150)
        canvas.drawCircle(width / 2f, height / 3f, width / 6f, paint)
        return bitmap
    }

    private fun noise(
        width: Int,
        height: Int,
        seed: Long,
    ): Bitmap {
        val random = Random(seed)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(
            IntArray(width * height) {
                Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            },
            0,
            width,
            0,
            0,
            width,
            height,
        )
        return bitmap
    }

    private fun contains(
        bytes: ByteArray,
        text: String,
    ) = String(bytes, Charsets.ISO_8859_1).contains(text)

    // --- Size, ratio, weight -------------------------------------------------------------------------------------------------------------

    @Test
    fun aSensorImage_becomesAJpegAtTheRatioOfTheViewfinder_1024pxLongSide_andUnder150000Bytes() {
        val photo = encode(gradient(3000, 4000))
        assertNotNull(photo)
        photo!!
        assertTrue("weighs ${photo.jpeg.size}", photo.jpeg.size in 1..UpdatePhotoFormat.MAX_BYTES)
        assertEquals(1024, maxOf(photo.width, photo.height))
        assertEquals(aspect, photo.width.toFloat() / photo.height, 0.01f)
        // The bytes are a JPEG, of the size the photo says it has.
        assertEquals(0xFF, photo.jpeg[0].toInt() and 0xFF)
        assertEquals(0xD8, photo.jpeg[1].toInt() and 0xFF)
        val decoded = BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.size)
        assertEquals(photo.width, decoded.width)
        assertEquals(photo.height, decoded.height)
        assertEquals(1_800_000_000_000L, photo.takenAtMillis)
    }

    @Test
    fun theZoneSavedIsTheCentreOfTheImage_whatIsSeenIsWhatLeaves() {
        // A 3:4 sensor image cut to 9:16 keeps the 900 middle pixels of its 1200 (150 to 1050): red on the 140 pixels of each side that are cut,
        // green in the middle. What leaves is green all over; the red and the blue must not appear at all.
        val bitmap = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.color = Color.RED
        canvas.drawRect(0f, 0f, 140f, 1600f, paint)
        paint.color = Color.GREEN
        canvas.drawRect(140f, 0f, 1060f, 1600f, paint)
        paint.color = Color.BLUE
        canvas.drawRect(1060f, 0f, 1200f, 1600f, paint)
        val photo = encode(bitmap, UpdatePhotoFormat.MAX_ASPECT)!!
        val decoded = BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.size)
        listOf(0.0 to 0.5, 0.5 to 0.5, 1.0 to 0.5, 0.02 to 0.1, 0.98 to 0.9).forEach { (x, y) ->
            val pixel = decoded.getPixel(((decoded.width - 1) * x).toInt(), ((decoded.height - 1) * y).toInt())
            assertTrue(
                "green all over, at ($x, $y): $pixel",
                Color.green(pixel) > 150 && Color.red(pixel) < 100 && Color.blue(pixel) < 100,
            )
        }
    }

    @Test
    fun aSmallPhotoIsNeverEnlarged() {
        val photo = encode(gradient(300, 600), 0.5f)!!
        assertEquals(300, photo.width)
        assertEquals(600, photo.height)
    }

    @Test
    fun theWorstPhoto_pureNoise_isNeverOverTheLimit_whatEverTheLadderGives() {
        // Pure noise is the worst case for a JPEG: whatever the ladder gives (a smaller picture, or nothing), it is never over the limit of the rules.
        val photo = encode(noise(2000, 3000, seed = 7))
        assertTrue(photo == null || photo.jpeg.size <= UpdatePhotoFormat.MAX_BYTES)
    }

    // --- No metadata ----------------------------------------------------------------------------------------------------------------------

    @Test
    fun theSavedPhotoHasNoExif_noPosition_noDate_noModel_noThumbnail_noXmp() {
        val photo = encode(gradient(3000, 4000))!!
        listOf(
            "Exif",
            "GPS",
            "xap",
            "http://ns.adobe.com",
            "Make",
            "Model",
            "Samsung",
            "OPPO",
            "Apple",
            "DateTime",
        ).forEach {
            assertFalse("the JPEG holds '$it'", contains(photo.jpeg, it))
        }
        // Read back with the EXIF reader: nothing at all.
        val exif = ExifInterface(photo.jpeg.inputStream())
        assertNull(exif.latLong)
        assertNull(exif.getAttribute(ExifInterface.TAG_DATETIME))
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
        assertFalse(exif.hasThumbnail())
        // Only the APP0 (JFIF) marker may follow the start of image, never an APP1 (0xE1, where EXIF and XMP live).
        var i = 2
        while (i + 3 < photo.jpeg.size && (photo.jpeg[i].toInt() and 0xFF) == 0xFF) {
            val marker = photo.jpeg[i + 1].toInt() and 0xFF
            assertTrue("marker 0x${marker.toString(16)} must not be an EXIF/XMP block", marker != 0xE1)
            if (marker == 0xDA) break
            i += 2 + (((photo.jpeg[i + 2].toInt() and 0xFF) shl 8) or (photo.jpeg[i + 3].toInt() and 0xFF))
        }
    }

    @Test
    fun aSourceThatCarriesAPositionAndADate_giveAPhotoThatCarriesNothing() {
        val source = File.createTempFile("source", ".jpg")
        try {
            source.outputStream().use { gradient(1600, 2000).compress(Bitmap.CompressFormat.JPEG, 90, it) }
            ExifInterface(source.absolutePath).also {
                it.setLatLong(48.856614, 2.352222)
                it.setAttribute(ExifInterface.TAG_MAKE, "OPPO")
                it.setAttribute(ExifInterface.TAG_MODEL, "CPH2371")
                it.setAttribute(ExifInterface.TAG_DATETIME, "2026:11:06 12:22:00")
                it.saveAttributes()
            }
            // The source really holds them (the test is not empty).
            val raw = source.readBytes()
            assertTrue(contains(raw, "Exif") && contains(raw, "OPPO"))
            val photo = encode(BitmapFactory.decodeFile(source.absolutePath))!!
            listOf("Exif", "GPS", "OPPO", "CPH2371", "2026:11:06").forEach {
                assertFalse("the saved photo holds '$it'", contains(photo.jpeg, it))
            }
            assertNull(ExifInterface(photo.jpeg.inputStream()).latLong)
        } finally {
            source.delete()
        }
    }

    // --- Ten photos: real ones, and the extreme cases; their sizes go to the pull request (QU1) -------------------------------------------------

    @Test
    fun tenPhotos_realOnesAndExtremeOnes_alwaysFit_orGiveNoPhoto_andTheirSizesAreWritten() {
        val lines = mutableListOf<String>()
        val photos = mutableListOf<Pair<String, Bitmap>>()
        val real = File("../../design/exports/onboarding-photos")
        listOf("amandine", "maman", "salon", "thomas", "thomas-alt").forEach { name ->
            val file = File(real, "$name-original.jpg")
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)?.let {
                    photos +=
                        "réelle $name ${it.width}x${it.height}" to it
                }
            }
        }
        photos += "dégradé 3000x4000" to gradient(3000, 4000)
        photos +=
            "à plat (gris) 3000x4000" to
            Bitmap.createBitmap(3000, 4000, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.GRAY) }
        photos +=
            "nuit (très sombre) 1500x2000" to
            Bitmap.createBitmap(1500, 2000, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.rgb(8, 8, 14)) }
        photos +=
            "bruit léger 1200x1600" to
            noise(1200, 1600, 3).also { b -> Canvas(b).drawColor(Color.argb(200, 90, 90, 90)) }
        photos += "bruit pur 2000x3000" to noise(2000, 3000, 5)
        Assume.assumeTrue("at least five photos", photos.size >= 5)
        var fitted = 0
        photos.take(10).forEach { (name, bitmap) ->
            val photo = encode(bitmap)
            if (photo != null) {
                assertTrue("$name weighs ${photo.jpeg.size}", photo.jpeg.size <= UpdatePhotoFormat.MAX_BYTES)
                assertTrue(
                    photo.width.toFloat() / photo.height in
                        UpdatePhotoFormat.MIN_ASPECT - 0.02f..UpdatePhotoFormat.MAX_ASPECT + 0.02f,
                )
                fitted++
            }
            lines +=
                "$name : ${photo?.let {
                    "${it.jpeg.size} octets, ${it.width}x${it.height}"
                } ?: "aucune photo (ne tient pas en 150 000 octets)"}"
        }
        File("build").mkdirs()
        File("build/photo-sizes.txt").writeText(lines.joinToString("\n") + "\n")
        assertTrue("most photos fit", fitted >= photos.take(10).size - 1)
    }

    @Test
    fun theOrientationOfTheSensorIsAppliedToThePixels_thenForgotten() {
        // A landscape picture (wide) that the sensor says must be turned by 90 degrees becomes a tall one.
        val wide = gradient(2000, 1500)
        val upright = encoder.upright(wide, 90)
        assertEquals(1500, upright.width)
        assertEquals(2000, upright.height)
        assertEquals(upright, encoder.upright(upright, 0))
        val turnedBack = encoder.upright(upright, 360 + 270)
        assertEquals(2000, turnedBack.width)
    }

    // --- The front camera gives the REFLECTION, as the viewfinder shows it; the back camera is never mirrored (PM, 2026-11-06) ---------------------

    /** A captured image: red on the left half, blue on the right half, a green band on top (to tell top from bottom). */
    private fun leftRedRightBlue(
        width: Int,
        height: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.color = Color.RED
        canvas.drawRect(0f, 0f, width / 2f, height.toFloat(), paint)
        paint.color = Color.BLUE
        canvas.drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = Color.GREEN
        canvas.drawRect(0f, 0f, width.toFloat(), height / 10f, paint)
        return bitmap
    }

    private fun published(
        captured: Bitmap,
        rotation: Int,
        mirror: Boolean,
    ): Bitmap {
        val upright = encoder.upright(captured, rotation, mirror)
        val photo = encode(upright, UpdatePhotoFormat.MAX_ASPECT)!!
        assertTrue(photo.jpeg.size <= UpdatePhotoFormat.MAX_BYTES)
        return BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.size)
    }

    private fun isRed(pixel: Int) = Color.red(pixel) > 180 && Color.blue(pixel) < 90 && Color.green(pixel) < 90

    private fun isBlue(pixel: Int) = Color.blue(pixel) > 180 && Color.red(pixel) < 90 && Color.green(pixel) < 90

    @Test
    fun theFrontCamera_givesTheReflection_whatIsOnTheLeftOfTheCapturedImageIsOnTheRightOfThePhoto_likeTheViewfinder() {
        // The viewfinder of the front camera shows the captured image flipped: its left half (red) appears on the RIGHT. The published photo is the same.
        val photo = published(leftRedRightBlue(1200, 1600), rotation = 0, mirror = true)
        val y = photo.height / 2
        assertTrue("the red half is now on the right", isRed(photo.getPixel(photo.width - 12, y)))
        assertTrue("the blue half is now on the left", isBlue(photo.getPixel(12, y)))
    }

    @Test
    fun theBackCamera_isNeverMirrored_theLeftStaysOnTheLeft() {
        val photo = published(leftRedRightBlue(1200, 1600), rotation = 0, mirror = false)
        val y = photo.height / 2
        assertTrue(isRed(photo.getPixel(12, y)))
        assertTrue(isBlue(photo.getPixel(photo.width - 12, y)))
    }

    @Test
    fun theMirrorIsAppliedAfterTheTurn_neverUpsideDown_withARotationOfTheSensor() {
        // A landscape capture (red left, blue right, green band on top) that the sensor says to turn by 90 degrees: red goes to the TOP, the green band to the RIGHT.
        // The mirror (horizontal symmetry, after the turn) moves the green band to the LEFT and leaves the red on TOP: it never swaps top and bottom.
        val plain = encoder.upright(leftRedRightBlue(1600, 1200), 90, mirror = false)
        val mirrored = encoder.upright(leftRedRightBlue(1600, 1200), 90, mirror = true)

        fun isGreen(pixel: Int) = Color.green(pixel) > 180 && Color.red(pixel) < 90 && Color.blue(pixel) < 90
        assertEquals(1200, plain.width)
        assertEquals(1600, plain.height)
        assertTrue(isRed(plain.getPixel(plain.width / 2, 200)))
        assertTrue(isGreen(plain.getPixel(plain.width - 5, plain.height / 2)))
        assertTrue(isRed(mirrored.getPixel(mirrored.width / 2, 200)))
        assertTrue(isGreen(mirrored.getPixel(5, mirrored.height / 2)))
        assertTrue(isBlue(mirrored.getPixel(mirrored.width / 2, mirrored.height - 200)))
    }

    @Test
    fun theMirroredPhoto_stillHasNoMetadata_andFitsTheLimit() {
        val photo = encode(encoder.upright(gradient(3000, 4000), 0, mirror = true))!!
        assertTrue(photo.jpeg.size in 1..UpdatePhotoFormat.MAX_BYTES)
        listOf("Exif", "GPS", "xap", "http://ns.adobe.com").forEach {
            assertFalse("the JPEG holds '$it'", contains(photo.jpeg, it))
        }
        assertNull(ExifInterface(photo.jpeg.inputStream()).latLong)
    }
}
