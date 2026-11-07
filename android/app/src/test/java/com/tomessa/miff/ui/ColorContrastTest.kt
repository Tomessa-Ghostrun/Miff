package com.tomessa.miff.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.tomessa.miff.ui.theme.colorBgCanvas
import com.tomessa.miff.ui.theme.colorBgGradientEnd
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorStatusDanger
import com.tomessa.miff.ui.theme.colorStatusDangerText
import com.tomessa.miff.ui.theme.colorStatusDangerTint
import com.tomessa.miff.ui.theme.colorStatusSafeText
import com.tomessa.miff.ui.theme.colorStatusSafeTint
import com.tomessa.miff.ui.theme.colorStatusWarningText
import com.tomessa.miff.ui.theme.colorStatusWarningTint
import com.tomessa.miff.ui.theme.colorTextError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ColorContrastTest {
    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double {
        val argb = color.toArgb()
        return 0.2126 * channel((argb shr 16) and 0xFF) +
            0.7152 * channel((argb shr 8) and 0xFF) +
            0.0722 * channel(argb and 0xFF)
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val (light, dark) = luminance(a).coerceAtLeast(luminance(b)) to luminance(a).coerceAtMost(luminance(b))
        return (light + 0.05) / (dark + 0.05)
    }

    @Test
    fun errorTextHasAtLeast4Point5ContrastOnEveryBackground() {
        listOf(
            "colorBgSurface" to colorBgSurface,
            "colorBgCanvas" to colorBgCanvas,
            "colorBgGradientEnd" to colorBgGradientEnd,
        ).forEach { (name, background) ->
            val ratio = contrast(colorTextError, background)
            assertTrue("colorTextError on $name has contrast $ratio, below 4.5", ratio >= 4.5)
        }
    }

    @Test
    fun errorTextColorIsTheValidatedOne() {
        assertEquals(0xFFD60700.toInt(), colorTextError.toArgb())
    }

    @Test
    fun statusDangerRedIsUnchanged() {
        // colorStatusDanger is a status/fill color (rings, badges): it is deliberately NOT darkened.
        assertEquals(0xFFFF5650.toInt(), colorStatusDanger.toArgb())
    }

    /** The tint is drawn over a white card: the colour the text sits on is the tint blended with white. */
    private fun overWhite(tint: Color): Color {
        val a = tint.alpha
        return Color(
            red = tint.red * a + (1 - a),
            green = tint.green * a + (1 - a),
            blue = tint.blue * a + (1 - a),
        )
    }

    @Test
    fun feedStateLabelsAreReadableOnTheirTint() {
        listOf(
            "safe" to (colorStatusSafeText to colorStatusSafeTint),
            "warning" to (colorStatusWarningText to colorStatusWarningTint),
            "danger" to (colorStatusDangerText to colorStatusDangerTint),
        ).forEach { (name, pair) ->
            val ratio = contrast(pair.first, overWhite(pair.second))
            assertTrue("status/$name-text on its tint has contrast $ratio, below 4.5", ratio >= 4.5)
        }
    }
}
