package com.tomessa.miff.ui

import androidx.compose.ui.graphics.toArgb
import com.tomessa.miff.ui.splash.SplashArtwork
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorTextOnAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The launch drawable is "the official icon without the heart", the heart is drawn in Kotlin: both must stay identical
 * to ic_launcher_foreground.xml, and use only the tokens' colors.
 */
class SplashArtworkTest {
    private val res = File("src/main/res")

    private fun pathsOf(name: String): List<Pair<String, String>> {
        val text = File(res, "drawable/$name.xml").readText()
        return Regex("""fillColor="(#[0-9A-Fa-f]{6})"\s+android:pathData="([^"]+)"""")
            .findAll(text)
            .map { it.groupValues[1].uppercase() to it.groupValues[2] }
            .toList()
    }

    private fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    @Test
    fun launchMarkIsTheOfficialMAndIWithoutTheHeart() {
        val official = pathsOf("ic_launcher_foreground")
        val mark = pathsOf("splash_logo_mark")
        assertEquals(2, official.size)
        assertEquals(listOf(official.first()), mark)
        // The animation draws the very same outline as a path (no drawable resource to parse at the start).
        assertEquals(official.first().second, SplashArtwork.MARK_PATH)
        assertEquals(hex(colorTextOnAction.toArgb()), mark.single().first)
    }

    @Test
    fun heartDrawnByTheAnimationIsTheOfficialHeartInTheBrandColor() {
        val officialHeart = pathsOf("ic_launcher_foreground").last()
        assertEquals(officialHeart.second, SplashArtwork.HEART_PATH)
        assertEquals("#D1D828", officialHeart.first)
    }

    @Test
    fun launchMarkIsDrawnAt288DpOnTheSame108UnitCanvasAsTheIcon() {
        val text = File(res, "drawable/splash_logo_mark.xml").readText()
        assertTrue(text.contains("""android:width="${SplashArtwork.CANVAS_DP.toInt()}dp""""))
        assertTrue(text.contains("""android:viewportWidth="${SplashArtwork.CANVAS.toInt()}""""))
    }

    @Test
    fun systemLaunchScreenUsesTheTokenBlueAndTheMarkWithoutHeart() {
        val colors = File(res, "values/colors.xml").readText()
        val blue = Regex("""name="ic_launcher_background">(#[0-9A-Fa-f]{6})<""").find(colors)!!.groupValues[1]
        assertEquals(hex(colorActionPrimary.toArgb()), blue.uppercase())

        val themes = File(res, "values/themes.xml").readText()
        assertTrue(themes.contains("""name="windowSplashScreenBackground">@color/ic_launcher_background"""))
        assertTrue(themes.contains("""name="windowSplashScreenAnimatedIcon">@drawable/splash_logo_mark"""))
        assertTrue(themes.contains("""name="postSplashScreenTheme">@style/Theme.Miff"""))
        // The manifest starts the activity with this theme (otherwise Android 12+ shows its default launch screen).
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:theme="@style/Theme.Miff.Starting""""))
    }

    @Test
    fun launchAnimationAddsNoSoundVibrationOrExternalResource() {
        val files =
            File("src/main/java/com/tomessa/miff/ui/splash").listFiles().orEmpty().toList() +
                File("src/main/java/com/tomessa/miff/domain/splash").listFiles().orEmpty()
        val code = files.joinToString("\n") { it.readText() }
        listOf(
            "MediaPlayer",
            "SoundPool",
            "Vibrator",
            "HapticFeedback",
            "VIBRATE",
            "lottie",
            "Lottie",
            "AnalyticsTracker",
        ).forEach { assertFalse("splash code must not use $it", code.contains(it)) }
        assertFalse(File(res, "raw").listFiles().orEmpty().any { it.name.contains("splash", ignoreCase = true) })
    }
}
