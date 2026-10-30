package com.tomessa.miff.ui.splash

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.tomessa.miff.domain.splash.SplashTimeline
import com.tomessa.miff.ui.auth.ConsentPanelScreen
import com.tomessa.miff.ui.auth.ConsentPanelUiState
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behaviour of the launch splash towards the system and the person (reviews of AUTH-1 and the splash): the system
 * "back" and the touches during the animation do not reach the screen composed underneath, a rotation does not play
 * the animation again; and the consent panel keeps its own rule (back = refusal) once the splash is gone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h640dp-xhdpi")
class SplashBackAndTouchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun advance(ms: Long) {
        composeRule.mainClock.advanceTimeBy(ms)
        composeRule.waitForIdle()
    }

    private fun splashIsShown(): Boolean =
        composeRule.onAllNodesWithContentDescription("Miff").fetchSemanticsNodes().isNotEmpty()

    private fun pressBack() {
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun activityIsFinishing(): Boolean {
        var finishing = false
        composeRule.activityRule.scenario.onActivity { finishing = it.isFinishing }
        return finishing
    }

    @Test
    fun backDuringTheSplash_doesNotRefuseThePanelUnderneath_norLeaveTheApp() {
        var refused = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MiffTheme {
                SplashHost(reduceMotion = false) {
                    ConsentPanelScreen(state = ConsentPanelUiState(), onContinue = {}, onRefuse = { refused++ })
                }
            }
        }
        advance(300) // the panel is composed under the animation
        assertTrue(splashIsShown())

        pressBack()

        assertEquals("back during the animation must not refuse for the person", 0, refused)
        assertFalse("nor close the app", activityIsFinishing())

        advance(SplashTimeline.MAX_ALLOWED_MS)
        assertFalse(splashIsShown())
        pressBack()
        assertEquals("once the splash is gone, back on the panel is a refusal", 1, refused)
    }

    @Test
    fun consentPanel_systemBackIsARefusal() {
        var refused = 0
        composeRule.setContent {
            MiffTheme { ConsentPanelScreen(state = ConsentPanelUiState(), onContinue = {}, onRefuse = { refused++ }) }
        }
        pressBack()
        assertEquals(1, refused)
        assertFalse("the app stays open: the refusal is handled by the session", activityIsFinishing())
    }

    @Test
    fun consentPanel_whileBusy_backIsAbsorbed_withoutRefusingAndWithoutLeaving() {
        var refused = 0
        composeRule.setContent {
            MiffTheme {
                ConsentPanelScreen(
                    state = ConsentPanelUiState(isLoading = true),
                    onContinue = {},
                    onRefuse = { refused++ },
                )
            }
        }
        pressBack()
        assertEquals(0, refused)
        assertFalse(activityIsFinishing())
    }

    @Test
    fun touchesDuringTheSplash_neverReachTheAppUnderneath() {
        var taps = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SplashHost(reduceMotion = false) {
                Box(Modifier.fillMaxSize().clickable { taps++ }) { Text("under the splash") }
            }
        }
        advance(300)
        assertTrue(splashIsShown())
        composeRule.onRoot().performTouchInput { click(center) }
        assertEquals("absorbed by the splash", 0, taps)

        advance(SplashTimeline.MAX_ALLOWED_MS)
        assertFalse(splashIsShown())
        composeRule.onRoot().performTouchInput { click(center) }
        assertEquals("reachable once the splash is gone", 1, taps)
    }

    @Test
    fun aRotationAfterTheSplash_doesNotPlayItAgain() {
        val tester = StateRestorationTester(composeRule)
        composeRule.mainClock.autoAdvance = false
        tester.setContent { SplashHost(reduceMotion = false) { Text("app") } }
        advance(SplashTimeline.MAX_ALLOWED_MS)
        assertFalse(splashIsShown())

        tester.emulateSavedInstanceStateRestore()
        advance(100)

        assertFalse("the animation must not start again after a rotation", splashIsShown())
        composeRule.onNodeWithText("app").assertExists()
    }

    @Test
    fun aRotationDuringTheSplash_goesOnInsteadOfStartingAgain() {
        val tester = StateRestorationTester(composeRule)
        composeRule.mainClock.autoAdvance = false
        tester.setContent { SplashHost(reduceMotion = false) { Text("app") } }
        advance(1500)
        assertTrue(splashIsShown())

        tester.emulateSavedInstanceStateRestore()
        advance(100)
        assertTrue(splashIsShown())

        // 1.5 s were already played: it ends within its maximum from the START, not 3 s after the rotation.
        advance(SplashTimeline.MAX_ALLOWED_MS - 1500)
        assertFalse(splashIsShown())
    }
}
