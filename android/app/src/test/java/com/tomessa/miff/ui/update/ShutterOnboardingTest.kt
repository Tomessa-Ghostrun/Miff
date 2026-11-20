package com.tomessa.miff.ui.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.tomessa.miff.ui.alert.ALERT_HINT_TAG
import com.tomessa.miff.ui.alert.AlertHintBubble
import com.tomessa.miff.ui.alert.AlertHoldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The blue bubble of the onboarding of the alert (decision of the PM, 2026-11-20): NO timer, and it goes at the FIRST interaction with the capture button (a press, a hold, a click, or what
 * a screen reader does), never at a touch elsewhere. The harness below is the very condition of the Update (the bubble is there while the phone has not remembered the interaction), with
 * the real shutter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en", application = android.app.Application::class)
class ShutterOnboardingTest {
    @get:Rule
    val rule = createComposeRule()

    private var photos = 0
    private var declared = 0
    private var triggered = 0
    private var interactions = 0

    /** [hintDone] is what the phone remembers (AlertViewModel.hintDone): the bubble is shown while it is false. */
    private fun update() {
        rule.setContent {
            val hold = remember { AlertHoldState() }
            var hintDone by remember { mutableStateOf(false) }
            Column {
                if (!hintDone && !hold.armed) AlertHintBubble(onMore = {})
                Box(Modifier.testTag("elsewhere"))
                Shutter(
                    hold =
                        ShutterHold(
                            state = hold,
                            onInteract = {
                                interactions++
                                hintDone = true
                            },
                            onArmed = {},
                            onDone = { triggered++ },
                            onDeclareAccessible = { declared++ },
                        ),
                    onPhoto = { photos++ },
                )
            }
        }
        rule.mainClock.autoAdvance = false
    }

    private fun shutter() = rule.onNode(hasContentDescription("Take the photo"))

    @Test
    fun withoutAnyInteraction_theBubbleStaysAsLongAsYouLike_noTimer() {
        update()
        rule.onNodeWithTag(ALERT_HINT_TAG).assertIsDisplayed()
        rule.mainClock.advanceTimeBy(60 * 60_000L)
        rule.onNodeWithTag(ALERT_HINT_TAG).assertIsDisplayed()
        assertEquals(0, interactions)
    }

    @Test
    fun aTouchElsewhere_doesNotPutTheBubbleAway() {
        update()
        rule.onNodeWithTag("elsewhere").performTouchInput { click() }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag(ALERT_HINT_TAG).assertIsDisplayed()
        assertEquals(0, interactions)
    }

    @Test
    fun aClickOnTheShutter_takesThePhoto_andPutsTheBubbleAway_forGood() {
        update()
        shutter().performTouchInput {
            down(center)
            advanceEventTime(120)
            up()
        }
        rule.mainClock.advanceTimeBy(100)
        assertEquals(1, photos)
        assertEquals(1, interactions)
        rule.onNodeWithTag(ALERT_HINT_TAG).assertDoesNotExist()
        rule.mainClock.advanceTimeBy(60_000)
        rule.onNodeWithTag(ALERT_HINT_TAG).assertDoesNotExist()
    }

    @Test
    fun aHoldOnTheShutter_putsTheBubbleAway_andStartsTheAlertAfter5Seconds() {
        update()
        shutter().performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(50)
        // the press alone is already the first interaction
        assertEquals(1, interactions)
        rule.onNodeWithTag(ALERT_HINT_TAG).assertDoesNotExist()
        rule.mainClock.advanceTimeBy(5_600)
        assertEquals(1, triggered)
        assertEquals(0, photos)
    }

    @Test
    fun theClickOfAScreenReader_isTheFirstInteractionToo() {
        update()
        shutter().performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(1, photos)
        assertEquals(1, interactions)
        rule.onNodeWithTag(ALERT_HINT_TAG).assertDoesNotExist()
    }

    @Test
    fun theCustomActionOfAScreenReader_isTheFirstInteractionToo_andAsksTheTwoStepConfirmation() {
        update()
        val actions =
            shutter()
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsActions.CustomActions)
                .orEmpty()
        val declare = actions.single { it.label == "Declare an alert" }
        rule.runOnUiThread {
            declare.action()
            androidx.compose.runtime.snapshots.Snapshot
                .sendApplyNotifications()
        }
        rule.mainClock.advanceTimeBy(100)
        assertEquals(1, declared)
        assertEquals(1, interactions)
        assertFalse(rule.onNodeWithTag(ALERT_HINT_TAG).isDisplayedSafe())
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.isDisplayedSafe(): Boolean =
        runCatching { assertIsDisplayed() }.isSuccess

    @Test
    fun theShutterWithoutTheAlert_isUntouched_aPlainClickIsAPhoto() {
        var shots = 0
        rule.setContent { Shutter(hold = null, onPhoto = { shots++ }) }
        rule
            .onNode(
                androidx.compose.ui.test
                    .hasClickAction(),
            ).performClick()
        assertEquals(1, shots)
        assertTrue(shots == 1)
    }
}
