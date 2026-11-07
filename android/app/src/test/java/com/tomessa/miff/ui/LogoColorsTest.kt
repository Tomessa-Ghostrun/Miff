package com.tomessa.miff.ui

import androidx.compose.ui.graphics.toArgb
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorBrandPrimary
import com.tomessa.miff.ui.theme.colorTextOnAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The logo drawables cannot reference Compose tokens, so their hexadecimal colors are duplicated from
 * ui/theme/Color.kt. This test fails if a logo color drifts away from the design tokens.
 */
class LogoColorsTest {
    // Gradle runs unit tests with the module directory (android/app) as working directory.
    private val res = File("src/main/res")

    private fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    private fun fillColors(name: String): Set<String> {
        val file = File(res, "drawable/$name.xml")
        assertTrue("Missing drawable ${file.path}", file.exists())
        return Regex("""fillColor="(#[0-9A-Fa-f]{6})"""")
            .findAll(file.readText())
            .map { it.groupValues[1].uppercase() }
            .toSet()
    }

    private val brandColors =
        setOf(
            hex(colorActionPrimary.toArgb()),
            hex(colorBrandPrimary.toArgb()),
            hex(colorTextOnAction.toArgb()),
        )

    @Test
    fun launcherBackgroundIsTheActionPrimaryToken() {
        val colors = File(res, "values/colors.xml").readText()
        val value = Regex("""name="ic_launcher_background">(#[0-9A-Fa-f]{6})<""").find(colors)?.groupValues?.get(1)
        assertEquals(hex(colorActionPrimary.toArgb()), value?.uppercase())
    }

    @Test
    fun launcherForegroundUsesOnlyTokenColors() {
        val colors = fillColors("ic_launcher_foreground")
        assertEquals(setOf(hex(colorTextOnAction.toArgb()), hex(colorBrandPrimary.toArgb())), colors)
    }

    @Test
    fun wordmarkUsesOnlyTokenColors() {
        val colors = fillColors("miff_wordmark_color")
        assertEquals(setOf(hex(colorActionPrimary.toArgb()), hex(colorBrandPrimary.toArgb())), colors)
    }

    @Test
    fun monochromeLayerIsASingleOpaqueSilhouette() {
        // The system tints it: one color, and not a brand color that could suggest a different rendering.
        val colors = fillColors("ic_launcher_monochrome")
        assertEquals(1, colors.size)
        assertFalse(colors.any { it in brandColors })
    }

    @Test
    fun adaptiveIconsReferenceTheOfficialLayersAndCarryNoProvisionalMention() {
        listOf("ic_launcher", "ic_launcher_round").forEach { name ->
            val text = File(res, "mipmap-anydpi-v26/$name.xml").readText()
            assertTrue(text.contains("@drawable/ic_launcher_foreground"))
            assertTrue(text.contains("@drawable/ic_launcher_monochrome"))
            assertTrue(text.contains("@color/ic_launcher_background"))
            assertFalse(text.contains("PROVISIONAL", ignoreCase = true))
        }
    }

    @Test
    fun playStoreIconIsNotShippedInTheApp() {
        // Kept under src/main/play (not a resource folder): it must never end up in the APK.
        assertFalse(File(res, "drawable/play-store-icon-512.png").exists())
        assertTrue(File("src/main/play/play-store-icon-512.png").exists())
    }
}
