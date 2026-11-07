package com.tomessa.miff.data.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.tomessa.miff.domain.profile.AvatarCodec
import com.tomessa.miff.domain.profile.AvatarCompression
import com.tomessa.miff.domain.profile.DecodedPhoto
import com.tomessa.miff.domain.profile.SquareCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.max

/** A photo decoded in memory and already straightened (orientation applied). Lives in the view model, never on disk. */
class BitmapPhoto(
    val bitmap: Bitmap,
) : DecodedPhoto {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
}

/**
 * Photo of the profile (Onboarding ticket 6.3): chosen with the system Photo Picker or taken with the camera application
 * of the system (neither needs a permission), cropped to a square by the person, then RE-ENCODED from the pixels.
 *
 * Privacy, which is the point of this class (risk R6, criterion PR4):
 * - the stored JPEG is written by [Bitmap.compress] from the pixels: no EXIF block, so no position, no date, no device;
 * - the orientation is read from the source ONLY to straighten the pixels, then dropped with everything else;
 * - nothing is written to the gallery or anywhere else; a photo taken by the camera lives in the private cache of the app
 *   only until it is decoded, then it is deleted.
 *
 * Robustness: the file is read with a size limit and a downsampling factor, so a huge photo or a file that is not an image
 * cannot exhaust the memory or crash the app: it simply gives null ("this image cannot be used").
 */
class AndroidAvatarCodec(
    context: Context,
) : AvatarCodec {
    private val appContext = context.applicationContext

    override suspend fun decode(source: String): DecodedPhoto? =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(source)
            try {
                decodeUri(uri)?.let { BitmapPhoto(it) }
            } catch (e: IOException) {
                null
            } catch (e: SecurityException) {
                null
            } catch (e: OutOfMemoryError) {
                null
            } catch (e: RuntimeException) {
                null
            } finally {
                AvatarCapture.deleteIfCapture(appContext, uri)
            }
        }

    override suspend fun encode(
        photo: DecodedPhoto,
        crop: SquareCrop,
    ): ByteArray? =
        withContext(Dispatchers.Default) {
            val source = (photo as? BitmapPhoto)?.bitmap ?: return@withContext null
            try {
                encodeSquare(source, crop)
            } catch (e: OutOfMemoryError) {
                null
            } catch (e: RuntimeException) {
                null
            }
        }

    private fun decodeUri(uri: Uri): Bitmap? {
        // Only what our own launchers give: a content address (Photo Picker, or our camera file). Never a file path.
        if (uri.scheme != "content") return null
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // decodeStream returns NULL when only the bounds are asked: never use its result as "failed".
        val opened = resolver.openInputStream(uri) ?: return null
        opened.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sourceSide = max(bounds.outWidth, bounds.outHeight)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        if (bounds.outWidth.toLong() * bounds.outHeight > MAX_SOURCE_PIXELS) return null
        var sample = 1
        while (sourceSide / (sample * 2) >= WORKING_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded =
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val orientation =
            try {
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: IOException) {
                ExifInterface.ORIENTATION_NORMAL
            }
        return straighten(decoded, orientation)
    }

    private fun encodeSquare(
        source: Bitmap,
        crop: SquareCrop,
    ): ByteArray? {
        val left = crop.left.coerceIn(0, source.width - 1)
        val top = crop.top.coerceIn(0, source.height - 1)
        val size = crop.size.coerceIn(1, minOf(source.width - left, source.height - top))
        // White behind the picture: a JPEG has no transparency (a transparent PNG would turn black).
        val square =
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { target ->
                val canvas = Canvas(target)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(source, Rect(left, top, left + size, top + size), Rect(0, 0, size, size), null)
            }
        val steps = HashMap<Int, Bitmap>()
        try {
            return AvatarCompression.fit { side, quality ->
                val scaled = steps.getOrPut(side) { scaleDown(square, side) }
                val out = ByteArrayOutputStream(AvatarCompression.MAX_BYTES)
                // Bitmap.compress writes a bare JPEG: no EXIF, no GPS, no thumbnail.
                if (scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) out.toByteArray() else null
            }
        } finally {
            steps.values.forEach { if (it !== square) it.recycle() }
            square.recycle()
        }
    }

    /** Halving while the picture is much bigger than the target keeps the details (one big step would skip pixels). */
    private fun scaleDown(
        square: Bitmap,
        side: Int,
    ): Bitmap {
        if (square.width <= side) return square
        var current = square
        while (current.width / 2 >= side) {
            val half = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
            if (current !== square) current.recycle()
            current = half
        }
        val last = Bitmap.createScaledBitmap(current, side, side, true)
        if (current !== square && current !== last) current.recycle()
        return last
    }

    private fun straighten(
        bitmap: Bitmap,
        orientation: Int,
    ): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                matrix.postRotate(90f)
            }

            ExifInterface.ORIENTATION_ROTATE_180 -> {
                matrix.postRotate(180f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> {
                matrix.postRotate(270f)
            }

            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postScale(1f, -1f)
            }

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }

            else -> {
                return bitmap
            }
        }
        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (turned !== bitmap) bitmap.recycle()
        return turned
    }

    private companion object {
        /** A photo of more than 100 million pixels is refused (a panorama of 12 000 x 8 000): it would swamp the memory. */
        const val MAX_SOURCE_PIXELS = 100_000_000L

        /** The photo is kept in memory at about this size (long side between 1 280 and 2 560 px) for the crop screen. */
        const val WORKING_SIDE = 1280
    }
}

/**
 * The file in which the camera application of the system puts the photo. Private cache of the app (never the gallery,
 * never external storage), shared with the camera app through a [FileProvider] address that only that one request can use,
 * deleted as soon as it was read.
 */
object AvatarCapture {
    private const val DIR = "avatar_capture"
    private const val FILE = "capture.jpg"

    fun authority(context: Context): String = "${context.packageName}.avatarcapture"

    /** A fresh, empty file for the camera, and its address. Any older capture is deleted first. */
    fun newCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val file = File(dir, FILE)
        file.delete()
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    /** Called with every decoded source: if it is the camera file, it is deleted. */
    fun deleteIfCapture(
        context: Context,
        uri: Uri,
    ) {
        if (uri.authority == authority(context)) deleteAll(context)
    }

    /** Also run at startup: a capture left by a process killed in between must not stay in the cache. */
    fun deleteAll(context: Context) {
        runCatching { File(context.cacheDir, DIR).deleteRecursively() }
    }
}
