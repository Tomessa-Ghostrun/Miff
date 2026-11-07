package com.tomessa.miff.domain.update

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The FORMAT of the photo of an update (design/screens/update.md, section 6.1, proposal validated with the PM's decisions of 2026-11-06 "viewfinder full
 * screen, preview a bit smaller"; ticket docs/tickets/update.md, D24). Pure: nothing is read from the phone, so that the same numbers are the contract of iOS
 * (the cases of UpdatePhotoFormatTest are the contract).
 *
 * - The ZONE SAVED is exactly the zone SEEN in the viewfinder ("what you see is what leaves"): a "cover" crop, centred, of the image of the sensor.
 * - The RATIO is the one of the viewfinder, BOUNDED between 9:21 and 9:16 (an iPhone 11 Pro and an OPPO Find X5 Lite are inside the bounds). Outside them
 *   (tablet, split screen, foldable) the viewfinder itself is limited to the bounded zone, centred, with dark bands: the crop below never sees an
 *   out-of-bounds ratio.
 * - The LONG SIDE is 1 024 px at most; the weight is 150 000 bytes at most (the hard limit of the rules `validPhoto`, measured on the real bytes).
 *   Estimate 60 to 110 KB for a typical photo (to be measured on ten photos in the PR).
 */

/** A rectangle of pixels in the (already straightened) source image. */
data class PixelRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

object UpdatePhotoFormat {
    /** The hard limit of `validPhoto` (backend/firestore.rules): a JPEG of more than this is refused with the whole lot. */
    const val MAX_BYTES = 150_000

    /** The aim for a typical photo (ticket V7); the ladder below stops at the first result that fits in [MAX_BYTES]. */
    const val TARGET_BYTES = 100_000

    /** The long sides tried, in order (the first is the maximum of the format). */
    val LONG_SIDES = intArrayOf(1024, 896, 768)

    /** JPEG qualities tried for each side, from the best to the lowest. */
    val QUALITIES = intArrayOf(80, 70, 60, 55)

    /** Width / height of the narrowest ratio kept (9:21) and of the widest (9:16). */
    const val MIN_ASPECT = 9f / 21f
    const val MAX_ASPECT = 9f / 16f

    /** The ratio (width / height) of a zone of [widthPx] x [heightPx], bounded between 9:21 and 9:16. A zone with no size gives the widest. */
    fun boundedAspect(
        widthPx: Int,
        heightPx: Int,
    ): Float {
        if (widthPx <= 0 || heightPx <= 0) return MAX_ASPECT
        return (widthPx.toFloat() / heightPx).coerceIn(MIN_ASPECT, MAX_ASPECT)
    }

    /**
     * The "cover" crop: the largest rectangle of the ratio [aspect] (width / height) that fits in the source, centred. A source wider than the ratio
     * loses width, a taller one loses height. Always inside the source, at least 1 pixel.
     */
    fun coverCrop(
        sourceWidth: Int,
        sourceHeight: Int,
        aspect: Float,
    ): PixelRect {
        require(sourceWidth > 0 && sourceHeight > 0) { "empty image" }
        val safeAspect = if (aspect.isNaN() || aspect <= 0f) MAX_ASPECT else aspect
        val sourceAspect = sourceWidth.toFloat() / sourceHeight
        val (w, h) =
            if (sourceAspect > safeAspect) {
                (sourceHeight * safeAspect).roundToInt().coerceIn(1, sourceWidth) to sourceHeight
            } else {
                sourceWidth to (sourceWidth / safeAspect).roundToInt().coerceIn(1, sourceHeight)
            }
        val left = ((sourceWidth - w) / 2).coerceIn(0, sourceWidth - w)
        val top = ((sourceHeight - h) / 2).coerceIn(0, sourceHeight - h)
        return PixelRect(left, top, w, h)
    }

    /** The size of the saved image: the crop reduced so that its long side is [longSide] at most (never enlarged), ratio kept. */
    fun outputSize(
        cropWidth: Int,
        cropHeight: Int,
        longSide: Int,
    ): Pair<Int, Int> {
        val long = max(cropWidth, cropHeight)
        if (long <= longSide) return cropWidth to cropHeight
        val scale = longSide.toFloat() / long
        return max(1, (cropWidth * scale).roundToInt()) to max(1, (cropHeight * scale).roundToInt())
    }

    /**
     * The first result of the ladder (side, quality) that weighs [MAX_BYTES] at most, measured on the real bytes; null when [encode] fails or when even
     * the smallest does not fit (the screen then says "On n'a pas pu préparer cette photo", and nothing is written).
     */
    fun fit(encode: (longSide: Int, quality: Int) -> ByteArray?): ByteArray? {
        for (side in LONG_SIDES) {
            for (quality in QUALITIES) {
                val bytes = encode(side, quality) ?: return null
                if (bytes.size in 1..MAX_BYTES) return bytes
            }
        }
        return null
    }

    /** The size of a [width] x [height] picture shown as large as possible inside a frame, ratio kept. */
    fun fitInside(
        width: Int,
        height: Int,
        frameWidth: Int,
        frameHeight: Int,
    ): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        val scale = min(frameWidth.toFloat() / width, frameHeight.toFloat() / height)
        return (width * scale).roundToInt() to (height * scale).roundToInt()
    }
}

/**
 * A photo ready to publish: the bytes of the JPEG (no metadata), its size and the moment it was taken. It lives in the memory of the screen only, NEVER
 * on the disk, and shows nothing in `toString()`. After [STALE_MILLIS] it is forgotten (ticket V6).
 */
class UpdatePhoto(
    val jpeg: ByteArray,
    val width: Int,
    val height: Int,
    val takenAtMillis: Long,
) {
    override fun toString(): String = "UpdatePhoto(redacted)"

    fun isStale(nowMillis: Long): Boolean = nowMillis - takenAtMillis > STALE_MILLIS

    companion object {
        /** A photo waiting for "Publier" for more than 5 minutes is old: "Cette photo date un peu : reprends-en une." */
        const val STALE_MILLIS = 5L * 60L * 1000L
    }
}
