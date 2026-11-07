package com.tomessa.miff.ui.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.data.account.AccountDeletionController
import com.tomessa.miff.data.account.AccountDeletionState
import com.tomessa.miff.data.account.DeletionRun
import com.tomessa.miff.testsupport.FakeDeletionSession
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.FakeProcedure
import com.tomessa.miff.testsupport.MemoryDeletionMarker
import com.tomessa.miff.testsupport.Timeline
import com.tomessa.miff.ui.theme.MiffTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The deletion of the account after "Me reconnecter" (session expired): the SAME controller (one per process) must take the deletion up again
 * when the person signs in again with the same account and the host enters the composition once more (the host only resumes from Idle).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-port-xxhdpi")
class AccountDeletionHostResumeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val timeline = Timeline()
    private val procedure = FakeProcedure()
    private val marker = MemoryDeletionMarker(timeline = timeline)
    private val session = FakeDeletionSession(timeline)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() = scope.cancel()

    private fun text(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    @Test
    fun afterMeReconnecter_theNewSignIn_takesTheDeletionUpOnTheSameController() {
        procedure.results += listOf(DeletionRun.SessionExpired, DeletionRun.Completed)
        val controller =
            AccountDeletionController(
                procedure = procedure,
                marker = marker,
                session = session,
                network = FakeNetwork(),
                currentUid = { "uid-1" },
                isAppleLinked = { false },
                scope = scope,
            )
        assertEquals(true, controller.start())
        assertEquals(AccountDeletionState.NeedsSignIn, controller.state.value)

        var hostShown by mutableStateOf(true)
        composeRule.setContent {
            MiffTheme {
                Box(Modifier.fillMaxSize()) {
                    if (hostShown) {
                        AccountDeletionHostFor(controls = controller, reauthHost = {}, onLeave = {})
                    }
                }
            }
        }
        composeRule
            .onNodeWithText(
                text(R.string.settings_delete_signin_again_action),
            ).assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, session.keptMarkerSignOuts)
        assertEquals(
            "the controller is free again, the marker is kept",
            AccountDeletionState.Idle,
            controller.state.value,
        )

        // The session is closed (the host leaves), the person signs in again with the same account (the host enters again).
        composeRule.runOnIdle { hostShown = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { hostShown = true }
        composeRule.waitForIdle()

        assertEquals("the host called resume() on the same controller", 2, procedure.runs)
        assertEquals(AccountDeletionState.Done(false), controller.state.value)
        composeRule.onNodeWithText(text(R.string.settings_delete_done_title)).assertIsDisplayed()
    }
}
