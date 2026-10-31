package com.tomessa.miff.startup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.AppContainer
import com.tomessa.miff.domain.startup.StartupMark
import com.tomessa.miff.ui.splash.SplashArtwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * Start-up work, on the JVM: what the container does when it is created (nothing), and how the launch is measured.
 * No Firebase is initialised in these tests: any eager use of Firebase in the container would make them fail.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StartupTraceAndContainerTest {
    @Test
    fun creatingTheContainerBuildsNothing_everythingIsBuiltOnFirstUse() {
        // Firebase is not initialised here: touching Authentication, Firestore, Analytics or Crashlytics would throw.
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        // Members that only need private files can be built without Firebase, and only when asked.
        assertNotNull(container.onboardingFlags)
        assertNotNull(container.pendingConsent)
        assertNotNull(container.languageStore)
        assertNotNull(container.passwordResetCooldown)
    }

    @Test
    fun theCameraCleanUpIsAnExplicitStepAndNotPartOfCreatingTheContainer() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        container.deleteCameraLeftovers() // no folder: nothing to delete, no failure
    }

    @Test
    fun theSplashOutlinesCanBePreparedOffTheMainThread() {
        var failure: Throwable? = null
        val thread =
            Thread {
                try {
                    SplashArtwork.prepare()
                } catch (e: Throwable) {
                    failure = e
                }
            }
        thread.start()
        thread.join(10_000L)
        assertNull(failure)
        assertFalse(SplashArtwork.paths.mark.isEmpty)
        assertFalse(SplashArtwork.paths.heart.isEmpty)
    }

    @Test
    fun theLaunchIsMeasuredOnce_firstImageThenHeart_andARelaunchStartsAgain() {
        StartupTrace.resetForTest()
        StartupTrace.onActivityCreate(recreation = false)
        StartupTrace.mark(StartupMark.APP_CREATE_START)
        ShadowSystemClock.advanceBy(Duration.ofMillis(500))
        StartupTrace.onSplashDraw(heartVisible = false)
        val first = StartupTrace.snapshot().marks.getValue(StartupMark.FIRST_FRAME)
        ShadowSystemClock.advanceBy(Duration.ofMillis(100))
        StartupTrace.onSplashDraw(heartVisible = false)
        assertEquals("the first image is recorded once", first, StartupTrace.snapshot().marks[StartupMark.FIRST_FRAME])
        assertNull(StartupTrace.snapshot().marks[StartupMark.HEART_VISIBLE])
        ShadowSystemClock.advanceBy(Duration.ofMillis(50))
        StartupTrace.onSplashDraw(heartVisible = true)
        val heart = StartupTrace.snapshot().marks.getValue(StartupMark.HEART_VISIBLE)
        assertEquals(first + 150L, heart)
        ShadowSystemClock.advanceBy(Duration.ofMillis(2_000))
        StartupTrace.onSplashDraw(heartVisible = true)
        assertEquals("the heart is recorded once", heart, StartupTrace.snapshot().marks[StartupMark.HEART_VISIBLE])
        assertEquals(2_000L, StartupTrace.snapshot().maxFrameGapMs)

        StartupTrace.onSplashDone()
        assertTrue(StartupTrace.snapshot().isComplete)
        // Debug builds publish the report for the readout.
        assertNotNull(StartupTrace.completedReport.value)

        // The screen is rebuilt (rotation): nothing is measured again.
        StartupTrace.onActivityCreate(recreation = true)
        assertTrue(StartupTrace.snapshot().isComplete)

        // A new launch in the living process: the measure starts again, from that moment.
        StartupTrace.onActivityCreate(recreation = false)
        val warm = StartupTrace.snapshot()
        assertFalse(warm.cold)
        assertFalse(warm.isComplete)
        assertNull(warm.marks[StartupMark.FIRST_FRAME])
        assertNull(StartupTrace.completedReport.value)
    }
}
