package com.tomessa.miff.ui.feed

import androidx.compose.ui.geometry.Offset
import kotlin.math.max

/**
 * The framing of the photo in FULL SCREEN (decision of the PM, 2026-11-17: "il faut que l'image prenne tout l'écran et qu'aucune marge ne soit visible, quelle que
 * soit la photo et quel que soit le device"). Pure arithmetic, no Compose and no Android, so that the rule "never a free pixel" is tested for every ratio.
 *
 * The photo is drawn "cover" (ContentScale.Crop): scaled until BOTH sides of the window are covered, then centred, so one side overflows and is cut off. At rest the
 * picture is exactly the window on one axis and larger on the other. Pinching zooms from there (never below 1: the covering scale is the minimum), and the drag is
 * bounded so that no edge of the picture can ever leave a margin visible.
 */
internal object PhotoFraming {
    /** The scale that makes a photo of [imageWidth] x [imageHeight] cover a window of [boxWidth] x [boxHeight] (what ContentScale.Crop computes). */
    fun coverScale(
        imageWidth: Int,
        imageHeight: Int,
        boxWidth: Int,
        boxHeight: Int,
    ): Float {
        require(imageWidth > 0 && imageHeight > 0 && boxWidth > 0 && boxHeight > 0) { "sizes must be positive" }
        return max(boxWidth.toFloat() / imageWidth, boxHeight.toFloat() / imageHeight)
    }

    /**
     * The share (0..1) of the photo that stays visible at rest, per axis: (width, height). 1 on the axis that fits exactly; less than 1 on the other, where the
     * photo is cut off, half on each side (a 3:4 portrait photo on a 20:9 screen keeps its whole height and loses 40 % of its width, because the screen is much narrower than the photo).
     */
    fun visibleShare(
        imageWidth: Int,
        imageHeight: Int,
        boxWidth: Int,
        boxHeight: Int,
    ): Pair<Float, Float> {
        val scale = coverScale(imageWidth, imageHeight, boxWidth, boxHeight)
        return minOf(1f, boxWidth / (imageWidth * scale)) to minOf(1f, boxHeight / (imageHeight * scale))
    }

    /**
     * The farthest the picture may be moved from the centre at [scale], per axis, in pixels: the picture at rest covers the window, scaled by [scale] around its
     * centre it overflows by (scale - 1) / 2 of the window on each side, and that is all the room it can move before a margin shows.
     */
    fun maxPan(
        scale: Float,
        boxWidth: Int,
        boxHeight: Int,
    ): Offset {
        val room = (scale - 1f).coerceAtLeast(0f) / 2f
        return Offset(boxWidth * room, boxHeight * room)
    }

    /** [pan] brought back inside what [maxPan] allows (nothing at all at scale 1). */
    fun clampPan(
        pan: Offset,
        scale: Float,
        boxWidth: Int,
        boxHeight: Int,
    ): Offset {
        val limit = maxPan(scale, boxWidth, boxHeight)
        return Offset(pan.x.coerceIn(-limit.x, limit.x), pan.y.coerceIn(-limit.y, limit.y))
    }

    /** True when, at [scale] and moved by [pan], the picture of the same size as the window still covers every pixel of it (the invariant, used by the tests). */
    fun covers(
        scale: Float,
        pan: Offset,
        boxWidth: Int,
        boxHeight: Int,
    ): Boolean {
        if (scale < 1f) return false
        val limit = maxPan(scale, boxWidth, boxHeight)
        return kotlin.math.abs(pan.x) <= limit.x + EPSILON && kotlin.math.abs(pan.y) <= limit.y + EPSILON
    }

    private const val EPSILON = 0.001f
}
