package com.tomessa.miff.data.profile

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.domain.profile.CropMath
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Random
import java.util.zip.CRC32

/**
 * The profile photo (Onboarding ticket 6.3, criteria PR4, A4, QA2; risk R6): at most 20 000 bytes, square, and NO metadata
 * whatever the source holds (a photo taken on the spot carries a position). Real image codecs (Robolectric native graphics),
 * no phone needed. A source file that contains a GPS position, a date and a device name is built for the occasion.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class AndroidAvatarCodecTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val codec = AndroidAvatarCodec(context)

    private fun serve(
        id: String,
        bytes: ByteArray,
    ): String {
        val uri = Uri.parse("content://test.photos/$id")
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes) }
        return uri.toString()
    }

    private fun jpeg(
        bitmap: Bitmap,
        quality: Int = 90,
        exif: (ExifInterface) -> Unit = {},
    ): ByteArray {
        val file = File.createTempFile("source", ".jpg")
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            ExifInterface(file.absolutePath).also {
                exif(it)
                it.saveAttributes()
            }
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    private fun noise(
        width: Int,
        height: Int,
        seed: Long = 1,
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

    private fun smooth(
        width: Int,
        height: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(
            IntArray(width * height) { i -> Color.rgb((i % width) * 255 / width, (i / width) * 255 / height, 128) },
            0,
            width,
            0,
            0,
            width,
            height,
        )
        return bitmap
    }

    private fun decodeAndEncode(source: String): ByteArray? =
        runBlocking {
            val photo = codec.decode(source) ?: return@runBlocking null
            val crop = CropMath.toCrop(photo.width, photo.height, 1f, photo.width / 2f, photo.height / 2f)
            codec.encode(photo, crop)
        }

    // --- No metadata (PR4) ------------------------------------------------------------------------------------------------

    @Test
    fun aPhotoWithAPositionADateAndADeviceName_comesOutWithNoMetadataAtAll() {
        val source =
            jpeg(smooth(1200, 900)) {
                it.setLatLong(48.8566, 2.3522)
                it.setAttribute(ExifInterface.TAG_MAKE, "TestPhoneMaker")
                it.setAttribute(ExifInterface.TAG_MODEL, "TestPhoneModel")
                it.setAttribute(ExifInterface.TAG_DATETIME, "2026:10:28 17:45:00")
                it.setAttribute(ExifInterface.TAG_SOFTWARE, "SecretCamera")
            }
        // The test is meaningful: the source really holds the position.
        val sourceFile = File.createTempFile("check", ".jpg").apply { writeBytes(source) }
        assertNotNull("the source must hold a GPS position", ExifInterface(sourceFile.absolutePath).latLong)
        sourceFile.delete()

        val out = decodeAndEncode(serve("gps", source))!!

        assertTrue("a JPEG", out[0] == 0xFF.toByte() && out[1] == 0xD8.toByte())
        val text = String(out, Charsets.ISO_8859_1)
        listOf("Exif", "TestPhone", "SecretCamera", "2026:10", "GPS", "http://ns.adobe.com/xap").forEach {
            assertFalse("the output must not contain \"$it\"", text.contains(it))
        }
        val file = File.createTempFile("out", ".jpg").apply { writeBytes(out) }
        val exif = ExifInterface(file.absolutePath)
        assertNull(exif.latLong)
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(exif.getAttribute(ExifInterface.TAG_MODEL))
        assertNull(exif.getAttribute(ExifInterface.TAG_DATETIME))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertFalse(exif.hasThumbnail())
        file.delete()
    }

    @Test
    fun theOutputIsASquareOf160Pixels() {
        val out = decodeAndEncode(serve("sq", jpeg(smooth(900, 600))))!!
        val decoded = BitmapFactory.decodeByteArray(out, 0, out.size)
        assertEquals(160, decoded.width)
        assertEquals(160, decoded.height)
    }

    // --- 20 000 bytes (PR4, QA2: sizes and orientations) ----------------------------------------------------------------

    @Test
    fun imagesOfAllSizesAndShapes_fitIn20000Bytes() {
        val shapes =
            listOf(
                100 to 100,
                160 to 160,
                640 to 480,
                480 to 640,
                3000 to 2000,
                2000 to 3000,
                17 to 500,
                500 to 17,
                64 to 64,
                1280 to 1280,
            )
        var index = 0
        for ((w, h) in shapes) {
            for (bitmap in listOf(smooth(w, h), noise(w, h, seed = w * 31L + h))) {
                val out = decodeAndEncode(serve("img-${index++}", jpeg(bitmap)))
                assertNotNull("${w}x$h must give an avatar", out)
                assertTrue("${w}x$h gave ${out!!.size} bytes", out.size <= 20_000)
            }
        }
    }

    @Test
    fun pureNoise_theHardestCase_stillFitsByLoweringTheQualityAndTheSize() {
        val out = decodeAndEncode(serve("noise", jpeg(noise(800, 800), quality = 100)))!!
        assertTrue("${out.size} bytes", out.size <= 20_000)
    }

    @Test
    fun aTransparentPng_getsAWhiteBackground_notABlackOne() {
        val clear = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
        val png = ByteArrayOutputStream().also { clear.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val out = decodeAndEncode(serve("png", png))!!
        val pixel = BitmapFactory.decodeByteArray(out, 0, out.size).getPixel(80, 80)
        assertTrue(
            "red ${Color.red(pixel)}",
            Color.red(pixel) > 240 && Color.green(pixel) > 240 && Color.blue(pixel) > 240,
        )
    }

    // --- Orientation, crop -----------------------------------------------------------------------------------------------------

    @Test
    fun theOrientationOfThePhotoIsApplied_beforeTheCrop() {
        val source =
            jpeg(smooth(400, 200)) {
                it.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            }
        val photo = runBlocking { codec.decode(serve("rot", source)) }!!
        assertEquals("rotated: 200 wide, 400 high", 200, photo.width)
        assertEquals(400, photo.height)
    }

    @Test
    fun aMirroredPhotoIsStraightened() {
        val source =
            jpeg(smooth(400, 200)) {
                it.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_TRANSPOSE.toString())
            }
        val photo = runBlocking { codec.decode(serve("mir", source)) }!!
        assertEquals(200, photo.width)
        assertEquals(400, photo.height)
    }

    @Test
    fun aBigPhotoIsReducedInMemory_butKeepsEnoughPixelsForTheCrop() {
        val photo = runBlocking { codec.decode(serve("big", jpeg(smooth(4000, 3000), quality = 50))) }!!
        assertTrue("${photo.width}x${photo.height}", maxOf(photo.width, photo.height) in 1280..2560)
        assertEquals("same shape", 4.0 / 3.0, photo.width.toDouble() / photo.height, 0.01)
    }

    @Test
    fun theCropChosenByThePersonIsWhatIsKept() {
        // Left half red, right half blue: cropping the right half must give a blue avatar.
        val bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(400 * 200) { i -> if (i % 400 < 200) Color.RED else Color.BLUE }
        bitmap.setPixels(pixels, 0, 400, 0, 0, 400, 200)
        val photo = runBlocking { codec.decode(serve("halves", jpeg(bitmap, quality = 100))) }!!
        val crop = CropMath.toCrop(photo.width, photo.height, 1f, photo.width.toFloat(), photo.height / 2f)
        val out = runBlocking { codec.encode(photo, crop) }!!
        val pixel = BitmapFactory.decodeByteArray(out, 0, out.size).getPixel(80, 80)
        assertTrue("blue expected, got ${Integer.toHexString(pixel)}", Color.blue(pixel) > 200 && Color.red(pixel) < 60)
    }

    // --- What is refused -----------------------------------------------------------------------------------------------------

    @Test
    fun aFileThatIsNotAnImage_givesNothing_andDoesNotCrash() {
        assertNull(runBlocking { codec.decode(serve("text", "this is not an image".toByteArray())) })
        assertNull(runBlocking { codec.decode(serve("empty", ByteArray(0))) })
        assertNull(runBlocking { codec.decode(serve("junk", ByteArray(5000) { (it * 7).toByte() })) })
    }

    @Test
    fun onlyContentAddressesAreRead_neverAFilePath() {
        assertNull(runBlocking { codec.decode("file:///data/data/com.tomessa.miff/shared_prefs/miff_session.xml") })
        assertNull(runBlocking { codec.decode("/data/data/com.tomessa.miff/files/x.jpg") })
        assertNull(runBlocking { codec.decode("http://example.com/x.jpg") })
    }

    @Test
    fun aPhotoOfMoreThan100MillionPixels_isRefusedBeforeBeingDecoded() {
        // A PNG header that announces 20 000 x 10 000 pixels (200 million): only the header is needed to refuse it.
        val ihdr = ByteArrayOutputStream()
        ihdr.write("IHDR".toByteArray())
        listOf(20_000, 10_000).forEach { v -> repeat(4) { ihdr.write((v shr (24 - 8 * it)) and 0xFF) } }
        ihdr.write(byteArrayOf(8, 2, 0, 0, 0))
        val crc = CRC32().also { it.update(ihdr.toByteArray()) }.value.toInt()
        val png = ByteArrayOutputStream()
        png.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        png.write(byteArrayOf(0, 0, 0, 13))
        png.write(ihdr.toByteArray())
        repeat(4) { png.write((crc shr (24 - 8 * it)) and 0xFF) }
        assertNull(runBlocking { codec.decode(serve("huge", png.toByteArray())) })
    }

    // --- Camera file -----------------------------------------------------------------------------------------------------------

    /**
     * FileProvider keeps the folders it resolved in a static cache, and Robolectric gives every test a NEW data folder:
     * without this reset the second test of a run fails with "Failed to find configured root" (test environment only).
     */
    private fun resetFileProviderCache() {
        val field = FileProvider::class.java.getDeclaredField("sCache")
        field.isAccessible = true
        (field.get(null) as MutableMap<*, *>).clear()
    }

    @Test
    fun theCameraAddressIsOurOwn_andItsFileIsDeletedOnceRead() {
        val uri = Uri.parse("content://${AvatarCapture.authority(context)}/avatar_capture/capture.jpg")
        assertEquals("${context.packageName}.avatarcapture", uri.authority)
        val dir = File(context.cacheDir, "avatar_capture").apply { mkdirs() }
        File(dir, "capture.jpg").writeBytes(jpeg(smooth(300, 300)))
        assertTrue(File(dir, "capture.jpg").exists())
        AvatarCapture.deleteIfCapture(context, uri)
        assertFalse("deleted as soon as it was read", dir.exists())
    }

    @Test
    fun theCameraFileIsCreatedInThePrivateCacheOnly() {
        // The folders FileProvider resolves are cached statically and compared with canonical paths: Robolectric on
        // Windows cannot match them (test environment only), so this one runs on the Linux CI.
        Assume.assumeFalse(System.getProperty("os.name").orEmpty().startsWith("Windows"))
        resetFileProviderCache()
        val uri = AvatarCapture.newCaptureUri(context)
        assertEquals("${context.packageName}.avatarcapture", uri.authority)
        assertTrue(uri.toString().startsWith("content://"))
        assertFalse("never a file path nor the gallery", uri.toString().contains("media"))
        assertTrue(File(context.cacheDir, "avatar_capture").exists())
    }

    @Test
    fun anotherAddressIsNeverDeleted() {
        val dir = File(context.cacheDir, "avatar_capture").apply { mkdirs() }
        File(dir, "capture.jpg").writeBytes(ByteArray(10))
        AvatarCapture.deleteIfCapture(context, Uri.parse("content://media/external/images/1"))
        assertTrue(File(dir, "capture.jpg").exists())
        AvatarCapture.deleteAll(context)
        assertFalse(dir.exists())
    }
}
