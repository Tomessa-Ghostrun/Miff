package com.tomessa.miff.data.update

import android.graphics.Bitmap
import android.graphics.Matrix
import com.tomessa.miff.domain.update.UpdatePhoto
import com.tomessa.miff.domain.update.UpdatePhotoFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/*
 * PRIVACY, for this file (risk R6, criterion V3): the JPEG that leaves the phone is written by [Bitmap.compress] FROM THE PIXELS: it carries no EXIF block, so
 * no position, no date, no model of phone, no thumbnail. The orientation of the sensor is applied to the pixels and then forgotten. The pictures of the
 * camera are decoded in memory ([androidx.camera.core.ImageProxy.toBitmap]) or from the file of the camera application of the system, which the avatar
 * codec reads and deletes at once: NO file of the photo is ever left on the disk, nothing is written to the gallery. Nothing here logs anything.
 */

/** The zone of the camera image that becomes the photo, and what is done to the pixels (the cases are tested on the JVM, see UpdatePhotoFormat). */
class AndroidPhotoEncoder(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    /**
     * A photo ready to publish from [source] (already upright: see [upright]), cut to the ratio [aspect] ("cover", centred) and reduced until it weighs
     * 150 000 bytes at most; null when it cannot be prepared (memory, a failing encoder, or nothing ever fits). The source is NOT recycled here.
     */
    suspend fun encode(
        source: Bitmap,
        aspect: Float,
    ): UpdatePhoto? =
        withContext(Dispatchers.Default) {
            try {
                val crop = UpdatePhotoFormat.coverCrop(source.width, source.height, aspect)
                val cropped = Bitmap.createBitmap(source, crop.left, crop.top, crop.width, crop.height)
                try {
                    val steps = HashMap<Int, Bitmap>()
                    var size = 0 to 0
                    val jpeg =
                        try {
                            UpdatePhotoFormat.fit { side, quality ->
                                val (w, h) = UpdatePhotoFormat.outputSize(cropped.width, cropped.height, side)
                                size = w to h
                                val scaled = steps.getOrPut(side) { scaleTo(cropped, w, h) }
                                val out = ByteArrayOutputStream(UpdatePhotoFormat.MAX_BYTES)
                                // Bitmap.compress writes a bare JPEG: no EXIF, no GPS, no thumbnail.
                                if (scaled.compress(
                                        Bitmap.CompressFormat.JPEG,
                                        quality,
                                        out,
                                    )
                                ) {
                                    out.toByteArray()
                                } else {
                                    null
                                }
                            }
                        } finally {
                            steps.values.forEach { if (it !== cropped) it.recycle() }
                        }
                    jpeg?.let { UpdatePhoto(it, size.first, size.second, nowMillis()) }
                } finally {
                    if (cropped !== source) cropped.recycle()
                }
            } catch (e: OutOfMemoryError) {
                null
            } catch (e: RuntimeException) {
                null
            }
        }

    /**
     * The bitmap turned by [degrees] (the rotation the sensor reports), so that the pixels are upright. The source is recycled when a new one is made.
     * [mirror] (the FRONT camera only, decision of the PM of 2026-11-06): a horizontal symmetry applied AFTER the turn, so that the photo is the REFLECTION of the
     * scene, exactly what the viewfinder of the front camera shows (the preview is mirrored by the system, the captured image is not). The back camera is never mirrored.
     */
    fun upright(
        bitmap: Bitmap,
        degrees: Int,
        mirror: Boolean = false,
    ): Bitmap {
        val turn = ((degrees % FULL_TURN) + FULL_TURN) % FULL_TURN
        if (turn == 0 && !mirror) return bitmap
        val matrix =
            Matrix().apply {
                postRotate(turn.toFloat())
                if (mirror) postScale(-1f, 1f)
            }
        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (turned !== bitmap) bitmap.recycle()
        return turned
    }

    /** Halving while the picture is much bigger than the target keeps the details (one big step would skip pixels). */
    private fun scaleTo(
        source: Bitmap,
        width: Int,
        height: Int,
    ): Bitmap {
        if (source.width <= width && source.height <= height) return source
        var current = source
        while (current.width / 2 >= width && current.height / 2 >= height) {
            val half = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
            if (current !== source) current.recycle()
            current = half
        }
        val last = Bitmap.createScaledBitmap(current, width, height, true)
        if (current !== source && current !== last) current.recycle()
        return last
    }

    private companion object {
        const val FULL_TURN = 360
    }
}
