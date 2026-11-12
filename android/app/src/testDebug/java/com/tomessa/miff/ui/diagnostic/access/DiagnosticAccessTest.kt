package com.tomessa.miff.ui.diagnostic.access

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.tomessa.miff.ui.navigation.holdForDiagnostic
import com.tomessa.miff.ui.settings.SettingsScreen
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two ways to the diagnostic screen in the debug build (PM, 2026-11-12), end to end on the JVM: the line of Settings, the 10 s hold from the
 * root modifier used by MainActivity and the dialogs, and the overlay that shows the screen ABOVE the app, which stays composed underneath
 * (what was open or typed in the app is still there once the diagnostic is closed).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h800dp-xhdpi")
class DiagnosticAccessTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun closed() = DiagnosticRequests.close()

    @After
    fun closeAgain() = DiagnosticRequests.close()

    /** Stand-in for the app: some state that is only kept while the screen stays composed (a typed draft, an open dialog). */
    @Composable
    private fun AppStandIn() {
        var taps by remember { mutableIntStateOf(0) }
        var draft by remember { mutableStateOf("") }
        Column {
            Button(onClick = { taps++ }) { Text("taps=$taps") }
            Button(onClick = { draft = "typed" }) { Text("draft=$draft") }
        }
    }

    @Test
    fun theSettingsLine_isThere_andAsksForTheDiagnostic() {
        composeRule.setContent {
            MiffTheme { SettingsScreen({}, {}, {}, {}, {}) }
        }
        assertFalse(DiagnosticRequests.isOpen.value)
        composeRule.onNodeWithText("Diagnostic").performScrollTo().performClick()
        assertTrue(DiagnosticRequests.isOpen.value)
    }

    @Test
    fun theSettingsLine_isThereInTheRestrictedListToo() {
        composeRule.setContent {
            MiffTheme { SettingsScreen({}, null, {}, null, {}) }
        }
        composeRule.onNodeWithText("Diagnostic").performScrollTo().performClick()
        assertTrue(DiagnosticRequests.isOpen.value)
    }

    @Test
    fun theDiagnosticOpensAboveTheApp_andClosingItLeavesTheAppExactlyAsItWas() {
        composeRule.setContent {
            MiffTheme {
                AppStandIn()
                DiagnosticOverlay { onClose -> Button(onClick = onClose) { Text("the diagnostic") } }
            }
        }
        composeRule.onNodeWithText("taps=0").performClick()
        composeRule.onNodeWithText("taps=1").performClick()
        composeRule.onNodeWithText("taps=2").assertPlaced()
        composeRule.onNodeWithText("draft=").performClick()
        composeRule.onNodeWithText("draft=typed").assertPlaced()

        DiagnosticRequests.request()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("the diagnostic").assertPlaced()
        // The app is still there, composed, under the diagnostic.
        composeRule.onNodeWithText("taps=2").assertPlaced()

        // A second request while it is shown changes nothing.
        DiagnosticRequests.request()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("the diagnostic").performClick()
        composeRule.waitForIdle()
        assertFalse(DiagnosticRequests.isOpen.value)
        composeRule.onNodeWithText("taps=2").assertPlaced()
        composeRule.onNodeWithText("draft=typed").assertPlaced()
    }

    @Test
    fun theTenSecondHold_atTheRootOfTheWindow_asksForTheDiagnostic() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MiffTheme { Box(Modifier.fillMaxSize().testTag("window").holdForDiagnostic()) { Text("any screen") } }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("window").performTouchInput { down(Offset(100f, 100f)) }
        composeRule.mainClock.advanceTimeBy(9_000)
        assertFalse(DiagnosticRequests.isOpen.value)
        composeRule.mainClock.advanceTimeBy(1_500)
        assertTrue(DiagnosticRequests.isOpen.value)
    }

    private fun SemanticsNodeInteraction.assertPlaced() {
        assertTrue(fetchSemanticsNode().layoutInfo.isPlaced)
    }
}
