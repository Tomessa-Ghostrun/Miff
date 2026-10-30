package com.tomessa.miff.domain.profile

import com.tomessa.miff.domain.auth.ProfileDocument
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A square zone of the source photo, in pixels of the (already straightened) source: the part that becomes the avatar. */
data class SquareCrop(
    val left: Int,
    val top: Int,
    val size: Int,
)

/**
 * Geometry of the square crop (Onboarding ticket 6.3: "recadrage carre obligatoire avant validation"). The person moves
 * and zooms the photo under a fixed square; we keep the state as a zoom and the CENTRE of the square in source pixels, so
 * that a rotation of the screen or a different screen size changes nothing.
 *
 * Zoom 1 = the largest square that fits in the photo (the short side); zoom 4 = a square 4 times smaller.
 */
object CropMath {
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 4f

    fun clampZoom(zoom: Float): Float = if (zoom.isNaN()) MIN_ZOOM else zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /** Side of the square, in source pixels, at this zoom. */
    fun sideAtZoom(
        width: Int,
        height: Int,
        zoom: Float,
    ): Float = min(width, height) / clampZoom(zoom)

    /** The centre kept inside the photo so that the square never leaves it. Returns (x, y). */
    fun clampCenter(
        width: Int,
        height: Int,
        zoom: Float,
        centerX: Float,
        centerY: Float,
    ): Pair<Float, Float> {
        val half = sideAtZoom(width, height, zoom) / 2f
        val x = if (centerX.isNaN()) width / 2f else centerX.coerceIn(half, max(half, width - half))
        val y = if (centerY.isNaN()) height / 2f else centerY.coerceIn(half, max(half, height - half))
        return x to y
    }

    /** The zone to cut in the source photo. Always inside the photo, always a square of at least 1 pixel. */
    fun toCrop(
        width: Int,
        height: Int,
        zoom: Float,
        centerX: Float,
        centerY: Float,
    ): SquareCrop {
        require(width > 0 && height > 0) { "empty photo" }
        val (cx, cy) = clampCenter(width, height, zoom, centerX, centerY)
        val side = sideAtZoom(width, height, zoom).roundToInt().coerceIn(1, min(width, height))
        val left = (cx - side / 2f).roundToInt().coerceIn(0, width - side)
        val top = (cy - side / 2f).roundToInt().coerceIn(0, height - side)
        return SquareCrop(left, top, side)
    }
}

/**
 * Finding an avatar that fits in [MAX_BYTES] (data-model.md section 2.1: `avatar` is at most 20 000 bytes; Onboarding
 * ticket 6.3): a 160 x 160 JPEG at a quality that goes down until it fits; if it never does, a smaller picture. The
 * encoder is given from outside (Android's JPEG encoder in the app, a fake in the unit tests).
 */
object AvatarCompression {
    const val MAX_BYTES = ProfileDocument.MAX_AVATAR_BYTES

    /** Side of the stored picture, in pixels. The first one is the target of the ticket. */
    val SIDES = intArrayOf(160, 128, 96)

    val QUALITIES = intArrayOf(80, 70, 60, 50, 40, 30)

    /** First result of [encode] (side, quality) that fits; null if even the smallest does not. */
    fun fit(encode: (side: Int, quality: Int) -> ByteArray?): ByteArray? {
        for (side in SIDES) {
            for (quality in QUALITIES) {
                val bytes = encode(side, quality) ?: return null
                if (bytes.size <= MAX_BYTES) return bytes
            }
        }
        return null
    }
}

/** A photo decoded and straightened, ready to be cropped. Kept by the view model, so a rotation of the screen keeps it. */
interface DecodedPhoto {
    val width: Int
    val height: Int
}

/**
 * Turns a photo chosen by the person into the stored avatar. [decode] opens it (null = not an image, unreadable, or far
 * too big); [encode] cuts the square and produces the JPEG of at most 20 000 bytes WITHOUT ANY METADATA (no position, no
 * date, no device: the picture is re-encoded from the pixels), or null if it cannot fit. Nothing is written to the
 * gallery or anywhere else.
 */
interface AvatarCodec {
    suspend fun decode(source: String): DecodedPhoto?

    suspend fun encode(
        photo: DecodedPhoto,
        crop: SquareCrop,
    ): ByteArray?
}
