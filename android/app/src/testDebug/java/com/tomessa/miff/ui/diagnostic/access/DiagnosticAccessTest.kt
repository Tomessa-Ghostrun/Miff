package com.tomessa.miff.ui.diagnostic.access

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tomessa.miff.navigation.DiagnosticRoute
import com.tomessa.miff.navigation.InternalDestinations
import com.tomessa.miff.ui.navigation.AppRoute
import com.tomessa.miff.ui.navigation.holdForDiagnostic
import com.tomessa.miff.ui.settings.SettingsScreen
import com.tomessa.miff.ui.theme.MiffTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
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
 * root modifier used by MainActivity and the dialogs, and the navigation that receives the request. The app itself starts on [AppRoute].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h800dp-xhdpi")
class DiagnosticAccessTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var requests = 0
    private lateinit var listener: Job
    private lateinit var nav: NavHostController

    @Before
    fun listen() {
        listener = CoroutineScope(Dispatchers.Unconfined).launch { DiagnosticRequests.events.collect { requests++ } }
    }

    @After
    fun stopListening() = listener.cancel()

    @Composable
    private fun Host() {
        nav = rememberNavController()
        NavHost(navController = nav, startDestination = AppRoute) {
            composable<AppRoute> { Text("the app") }
            composable<DiagnosticRoute> { Text("the diagnostic") }
        }
        InternalDestinations.OpenOnRequest(nav)
    }

    @Test
    fun theSettingsLine_isThere_andAsksForTheDiagnostic() {
        composeRule.setContent {
            MiffTheme { SettingsScreen({}, {}, {}, {}, {}) }
        }
        composeRule.onNodeWithText("Diagnostic").performScrollTo().performClick()
        assertEquals(1, requests)
    }

    @Test
    fun theSettingsLine_isThereInTheRestrictedListToo() {
        composeRule.setContent {
            MiffTheme { SettingsScreen({}, null, {}, null, {}) }
        }
        composeRule.onNodeWithText("Diagnostic").performScrollTo().performClick()
        assertEquals(1, requests)
    }

    @Test
    fun aRequest_opensTheDiagnosticOnTopOfTheApp_andBackReturnsToTheApp() {
        composeRule.setContent { Host() }
        composeRule.onNodeWithText("the app").assertExistsCompat()
        assertFalse("the app starts on the app, not on the diagnostic", currentIsDiagnostic())

        DiagnosticRequests.request()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("the diagnostic").assertExistsCompat()

        // A second request while the diagnostic is shown does not pile a second copy.
        DiagnosticRequests.request()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(2, nav.currentBackStack.value.count { it.destination.route != null }) }

        composeRule.runOnIdle { nav.popBackStack() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("the app").assertExistsCompat()
        assertFalse(currentIsDiagnostic())
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
        assertEquals(0, requests)
        composeRule.mainClock.advanceTimeBy(1_500)
        assertEquals(1, requests)
    }

    private fun currentIsDiagnostic(): Boolean {
        var result = false
        composeRule.runOnIdle { result = nav.currentDestination?.hasRoute<DiagnosticRoute>() == true }
        return result
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() {
        assertTrue(fetchSemanticsNode().layoutInfo.isPlaced)
    }
}
