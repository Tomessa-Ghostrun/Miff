package com.tomessa.miff.ui.diagnostic.access

import android.app.Application
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.center
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Example screen under the detector (Robolectric, no phone): a button and a long scrolling list. The hold opens after 10 s of a still finger and
 * only then; and the detector never gets in the way of the normal taps and scrolls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DiagnosticHoldDetectorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var holds = 0
    private var clicks = 0
    private val scroll = ScrollState(0)

    @Composable
    private fun Example() {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag("root")
                    .diagnosticHoldDetector { holds++ }
                    .verticalScroll(scroll),
        ) {
            Button(onClick = { clicks++ }) { Text("Continuer") }
            Column(modifier = Modifier.height(3000.dp)) { Text("Long content") }
        }
    }

    private fun start() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Example() }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun px(dp: Float): Float = with(composeRule.density) { dp.dp.toPx() }

    @Test
    fun aStillFinger_opensAfterTenSeconds_notBefore() {
        start()
        composeRule.onNodeWithTag("root").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(9_900)
        assertEquals("not yet", 0, holds)
        composeRule.mainClock.advanceTimeBy(200)
        assertEquals("opened once", 1, holds)
        composeRule.onNodeWithTag("root").performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(20_000)
        assertEquals("and only once for this hold", 1, holds)
    }

    @Test
    fun liftingTheFingerBeforeTenSeconds_cancelsCleanly_andANewHoldWorks() {
        start()
        composeRule.onNodeWithTag("root").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(9_000)
        composeRule.onNodeWithTag("root").performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(30_000)
        assertEquals(0, holds)

        composeRule.onNodeWithTag("root").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(10_100)
        assertEquals(1, holds)
    }

    @Test
    fun aJitterUnderTwentyDp_stillOpens() {
        start()
        val jitter = px(12f)
        composeRule.onNodeWithTag("root").performTouchInput {
            down(center)
            moveBy(Offset(jitter, 0f))
        }
        composeRule.mainClock.advanceTimeBy(10_100)
        assertEquals(1, holds)
    }

    @Test
    fun movingMoreThanTwentyDp_cancels() {
        start()
        val far = px(25f)
        composeRule.onNodeWithTag("root").performTouchInput {
            down(center)
            moveBy(Offset(far, 0f))
        }
        composeRule.mainClock.advanceTimeBy(15_000)
        assertEquals(0, holds)
    }

    @Test
    fun aSecondFinger_cancels() {
        start()
        composeRule.onNodeWithTag("root").performTouchInput {
            down(0, center)
            down(1, center + Offset(60f, 0f))
        }
        composeRule.mainClock.advanceTimeBy(15_000)
        assertEquals(0, holds)
    }

    @Test
    fun aNormalTap_stillReachesTheButton_andOpensNothing() {
        start()
        composeRule.onNodeWithText("Continuer").performClick()
        composeRule.mainClock.advanceTimeBy(15_000)
        assertEquals("the button received its tap", 1, clicks)
        assertEquals(0, holds)
    }

    @Test
    fun aScroll_stillScrolls_andOpensNothing() {
        start()
        composeRule
            .onNodeWithTag(
                "root",
            ).performTouchInput { swipeUp() }
        composeRule.mainClock.advanceTimeBy(15_000)
        assertTrue("the list scrolled under the detector", scroll.value > 0)
        assertEquals(0, holds)
    }

    @Test
    fun theHoldOnAButton_opens_andTheDetectorDoesNotBreakTheTapAfterwards() {
        start()
        composeRule.onNodeWithText("Continuer").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(10_100)
        composeRule.onNodeWithText("Continuer").performTouchInput { up() }
        assertEquals(1, holds)
        // Nothing was swallowed: like any long press on a button, the release counts as its tap; and a later tap still works.
        assertEquals(1, clicks)
        composeRule.onNodeWithText("Continuer").performClick()
        assertEquals(2, clicks)
    }
}
