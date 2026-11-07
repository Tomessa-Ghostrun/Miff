package com.tomessa.miff.ui.splash

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.tomessa.miff.domain.splash.SplashTimeline
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The launch splash on the JVM (Robolectric, virtual clock): it plays, it covers the app, it hands over to the app on
 * time, and the reduced version does the same without movement. A plain Application is used so that Firebase is never
 * initialized.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class SplashHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val appText = "first screen of the app"

    private var firstFrames = 0

    private fun show(reduceMotion: Boolean) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SplashHost(reduceMotion = reduceMotion, onFirstFrame = { firstFrames++ }) { Text(appText) }
        }
    }

    private fun appIsComposed(): Boolean =
        composeRule.onAllNodesWithText(appText, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun advance(ms: Long) {
        composeRule.mainClock.advanceTimeBy(ms)
        composeRule.waitForIdle()
    }

    private fun splashIsShown(): Boolean =
        composeRule.onAllNodesWithContentDescription("Miff").fetchSemanticsNodes().isNotEmpty()

    @Test
    fun fullAnimation_coversTheAppThenHandsOverWithinTheMaximumDuration() {
        show(reduceMotion = false)
        advance(100)
        // Announced once as "Miff"; the app underneath is hidden from the screen reader while the animation plays.
        composeRule.onNodeWithContentDescription("Miff").assertExists()
        composeRule.onNodeWithText(appText).assertDoesNotExist()

        advance(SplashTimeline.HOLD_MS + SplashTimeline.APPEAR_MS + SplashTimeline.INFLATE_MS) // during the burst
        assertTrue("still playing before the end", splashIsShown())

        advance(SplashTimeline.MAX_ALLOWED_MS - 1000) // well beyond the planned 2.7 s, never beyond 3.0 s
        assertFalse("splash must be gone at 3 s", splashIsShown())
        composeRule.onNodeWithText(appText).assertExists()
    }

    @Test
    fun reducedMotion_showsAStillLogoBrieflyThenTheApp() {
        show(reduceMotion = true)
        advance(100)
        assertTrue(splashIsShown())
        advance(SplashTimeline.STILL_MS - 100)
        assertTrue("fading, not gone yet", splashIsShown())
        advance(SplashTimeline.EXIT_MS + 100)
        assertFalse("reduced splash must be gone after 0.8 s", splashIsShown())
        composeRule.onNodeWithText(appText).assertExists()
    }

    @Test
    fun appIsComposedUnderTheSplash_soTheSessionAndFirstScreenAreReadyWhenItFadesOut() {
        show(reduceMotion = false)
        advance(SplashTimeline.HOLD_MS + SplashTimeline.APPEAR_MS + 100)
        // Not in the screen reader's tree (hidden on purpose), but composed: found in the unmerged tree.
        composeRule.onNodeWithText(appText, useUnmergedTree = true).assertExists()
    }

    /**
     * PM recette 2026-10-31 ("the M stays alone for seconds"): the heavy first composition of the app must not happen before
     * the heart has appeared. It is composed once the heart is there, and in time for the end of the animation.
     */
    @Test
    fun theAppIsNotComposedBeforeTheHeartHasAppeared_thenItIsComposedUnderTheAnimation() {
        show(reduceMotion = false)
        advance(60)
        assertFalse("not at the first image", appIsComposed())
        advance(SplashTimeline.HOLD_MS + SplashTimeline.APPEAR_MS - 60 - 40)
        assertFalse("not while the heart appears", appIsComposed())
        advance(200)
        assertTrue("composed once the heart is there", appIsComposed())
        assertTrue("and the animation is still playing", splashIsShown())
    }

    @Test
    fun withReducedMotionTheAppIsComposedAtOnce() {
        show(reduceMotion = true)
        advance(60)
        assertTrue(appIsComposed())
    }

    /** What can wait for the first image (reading the session) is started by it. */
    @Test
    fun theFirstImageStartsWhatCanWait() {
        show(reduceMotion = false)
        advance(60)
        // Drawing happens when the picture is asked for (nothing is on screen in this test otherwise).
        composeRule.onRoot().captureToImage()
        composeRule.waitForIdle()
        assertTrue("started after the first image", firstFrames >= 1)
    }

    /** Writes several instants of the animation as PNG files (build/splash-frames) to look at them. */
    @Test
    fun captureSeveralInstantsOfTheAnimation() {
        val dir = File("build/splash-frames").apply { mkdirs() }
        show(reduceMotion = false)
        var last = 0L
        listOf(0L, 450L, 700L, 1100L, 1500L, 1900L, 2150L, 2250L, 2350L, 2500L, 2700L, 2800L).forEach { t ->
            advance(t - last)
            last = t
            val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
            File(
                dir,
                "splash-%04d.png".format(t),
            ).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertTrue(dir.listFiles().orEmpty().size >= 12)
    }
}
