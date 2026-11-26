package com.tomessa.miff.ui.paywall

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.tomessa.miff.domain.paywall.PeriodicPaywallCounterStore
import com.tomessa.miff.domain.paywall.PeriodicPaywallDecision
import com.tomessa.miff.domain.paywall.PeriodicPaywallRules
import com.tomessa.miff.domain.paywall.PeriodicPaywallSituation
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The first tap of the session (criteria PP8 and PP9), through the REAL modifier on a real Compose tree: a tap that a button took makes the offer possible; a tap on nothing, a scroll and a swipe
 * never do; and the tap itself still does its job (the modifier consumes nothing).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w390dp-h844dp-port-xxhdpi")
class PeriodicPaywallTouchesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class MemoryStore(
        var value: Int = 10,
    ) : PeriodicPaywallCounterStore {
        override fun read() = value

        override fun write(count: Int) {
            value = count
        }

        override fun clear() {
            value = 0
        }
    }

    private val clock = FakePaywallClock()
    private val rules = PeriodicPaywallRules(MemoryStore(), clock)
    private var clicks = 0
    private var rowClicks = 0

    private val calm =
        PeriodicPaywallSituation(
            miffPlus = false,
            tabsInFront = true,
            updateScreenOpen = false,
            keyboardOpen = false,
            messageOpen = false,
            ownAlertInProgress = false,
            lovedOneAlertUnseen = false,
        )

    private fun host() {
        rules.onForeground()
        rules.onShellShown()
        composeRule.setContent {
            MiffTheme {
                Box(Modifier.fillMaxSize().periodicPaywallTouches(rules).testTag("root")) {
                    Column {
                        Button(onClick = { clicks++ }, modifier = Modifier.testTag("button")) { Text("Go") }
                        LazyColumn(Modifier.height(120.dp).testTag("list")) {
                            items((0 until 40).toList()) { row -> Text("row $row", Modifier.clickable { rowClicks++ }) }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun possible() = rules.evaluate(calm) != PeriodicPaywallDecision.No

    @Test
    fun aTapOnAButton_makesTheOfferPossible_andTheButtonStillWorks() {
        host()
        assertTrue("nothing yet", !possible())
        composeRule.onNodeWithTag("button").performTouchInput { click() }
        composeRule.waitForIdle()
        assertEquals("the tap did its job", 1, clicks)
        assertTrue("the first tap of the session was seen", possible())
    }

    @Test
    fun aTapOnNothing_doesNotCount_butItIsATouch() {
        host()
        val before = rules.changes.value
        composeRule.onNodeWithTag("root").performTouchInput { click(Offset(width - 5f, height - 5f)) }
        composeRule.waitForIdle()
        assertTrue("a touch was seen", rules.changes.value > before)
        assertTrue("but it activated nothing", !possible())
    }

    @Test
    fun aScroll_orASwipe_neverCounts() {
        host()
        composeRule.onNodeWithTag("list").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assertEquals("the swipe was a scroll, not a click", 0, rowClicks)
        assertTrue("a swipe is not a tap", !possible())
    }

    @Test
    fun aTapOnARowOfAList_counts() {
        host()
        composeRule.onNodeWithTag("list").performTouchInput { click(Offset(20f, 20f)) }
        composeRule.waitForIdle()
        assertEquals(1, rowClicks)
        assertTrue(possible())
    }

    @Test
    fun theTapIsNotCounted_whenTheSignedInAppIsNotOnScreen() {
        host()
        rules.onShellGone()
        composeRule.onNodeWithTag("button").performTouchInput { click() }
        composeRule.waitForIdle()
        rules.onShellShown()
        assertNotEquals(PeriodicPaywallDecision.Show, rules.evaluate(calm))
        assertTrue(!possible())
    }
}
