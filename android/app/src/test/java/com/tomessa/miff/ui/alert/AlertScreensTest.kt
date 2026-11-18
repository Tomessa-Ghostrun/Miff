package com.tomessa.miff.ui.alert

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.tomessa.miff.data.alert.EndStep
import com.tomessa.miff.data.alert.MyAlert
import com.tomessa.miff.data.alert.SendStep
import com.tomessa.miff.domain.alert.AlertCallList
import com.tomessa.miff.domain.alert.AlertDialTarget
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The screens of the alert (docs/tickets/alerte-danger.md 7 and 13, criteria AD1 to AD5, AD10, AD10b, AD13 to AD19, AD21b, AD22, AD23, AD44), drawn on the JVM: what each phase says, that
 * the title "viennent d'être avertis" only exists once the alert left, that the numbers of emergency are there in EVERY phase, the long name of a Miff, the gesture of the hold.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en", application = android.app.Application::class)
class AlertScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private fun member(
        uid: String,
        first: String,
        owner: Boolean = false,
        phone: String? = "+33612345678",
    ) = MiffMember(uid, first, "X", null, if (owner) MiffRole.OWNER else MiffRole.MEMBER, true, phone)

    private val list =
        AlertCallList.build(
            listOf(member("c", "Emmanuelle", owner = true), member("me", "Moi")),
            "me",
            "FR",
        )
    private val ready = CallState.Ready(list, list.preselected, contactsUnavailable = false)
    private val called = mutableListOf<AlertDialTarget>()
    private var cancelled = 0
    private var retried = 0

    private fun show(
        phase: SentPhase,
        name: String? = "Family",
        several: Boolean = false,
        call: CallState = ready,
    ) {
        rule.setContent {
            AlertSentScreen(
                phase = phase,
                miffName = name,
                several = several,
                call = call,
                actions =
                    AlertSentActions(
                        onCall = { called += it },
                        onCancelPending = { cancelled++ },
                        onRetry = { retried++ },
                    ),
            )
        }
    }

    private fun titleDescription() =
        rule
            .onNodeWithTag(
                ALERT_SENT_TITLE_TAG,
            ).fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ContentDescription)
            ?.single()

    // ---- the screen "avertis" ---------------------------------------------------------------------------------------------------------

    @Test
    fun onceSent_theTitleNamesTheMiff_andTheCallButtonIsThere() {
        show(SentPhase.SENT)
        assertEquals("Your people in the Miff “Family” have been alerted", titleDescription())
        rule.onNodeWithTag(ALERT_SENT_CALL_TAG).assertExists()
        rule
            .onNodeWithText(
                "Miff is not an emergency service. If you're in immediate danger, call 112.",
            ).assertExists()
    }

    @Test
    fun beforeTheServerAccepted_itNeverSaysAlerted() {
        show(SentPhase.SENDING)
        assertEquals("Sending the alert to the Miff “Family”…", titleDescription())
        assertFalse(titleDescription()!!.contains("alerted"))
        rule.onNodeWithTag(ALERT_SENT_CANCEL_TAG).assertDoesNotExist()
    }

    @Test
    fun withoutANetwork_theAlertWaits_theCallsStayAvailable_andItCanBeCancelled() {
        show(SentPhase.WAITING)
        assertEquals("No connection right now. Your alert will go as soon as possible.", titleDescription())
        rule.onNodeWithTag(ALERT_SENT_CALL_TAG).assertExists()
        rule.onNodeWithTag(ALERT_SENT_CANCEL_TAG).performScrollTo().performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun aRefusal_isCalm_offersARetry_andTheEmergencyServices() {
        show(SentPhase.REFUSED)
        assertTrue(titleDescription()!!.startsWith("Your alert didn't go."))
        assertTrue(titleDescription()!!.contains("call the emergency services directly"))
        rule.onNodeWithTag(ALERT_SENT_RETRY_TAG).performScrollTo().performClick()
        assertEquals(1, retried)
        rule.onNodeWithTag(ALERT_SENT_CALL_TAG).assertExists()
        rule.onNodeWithTag(ALERT_SENT_EMERGENCY_LINK_TAG).assertExists()
    }

    @Test
    fun theDailyLimit_isToldWithoutReproach_noRetry_andTheEmergencyNumberIsTheChoice() {
        val emergencyOnly =
            CallState.Ready(
                list,
                AlertDialTarget.Emergency(
                    list.emergency.first {
                        it.number == "112"
                    },
                ),
                contactsUnavailable = false,
            )
        show(SentPhase.QUOTA, call = emergencyOnly)
        val text = titleDescription()!!
        assertTrue(text.contains("limit of 3 alerts"))
        assertTrue(text.contains("call the emergency services directly"))
        assertFalse(text.contains("too many", ignoreCase = true))
        assertFalse(text.contains("abuse", ignoreCase = true))
        rule.onNodeWithTag(ALERT_SENT_RETRY_TAG).assertDoesNotExist()
        // one touch to call 112
        rule.onNodeWithTag(ALERT_SENT_CALL_TAG).performScrollTo().performClick()
        assertEquals("112", called.single().number)
        // and the person can close it
        rule.onNodeWithTag(ALERT_SENT_CANCEL_TAG).assertExists()
    }

    @Test
    fun sharingStopped_saysWhatToDo_andKeepsTheNumbers() {
        show(SentPhase.SHARING_STOPPED)
        assertEquals(
            "You've stopped sharing with the Miff “Family”: resume sharing to alert your people.",
            titleDescription(),
        )
        rule.onNodeWithText("Resume sharing").assertExists()
        rule.onNodeWithTag(ALERT_SENT_CALL_TAG).assertExists()
    }

    @Test
    fun aMiffWithSeveralMiffsSaysOnlyThisOneIsAlerted_aSingleMiffDoesNot() {
        show(SentPhase.SENT, several = true)
        rule.onNodeWithText("Only this Miff is alerted.").assertExists()
    }

    @Test
    fun aSingleMiff_noSuchSentence() {
        show(SentPhase.SENT, several = false)
        rule.onNodeWithText("Only this Miff is alerted.").assertDoesNotExist()
    }

    @Test
    fun aVeryLongNameWithEmojisAndNoSpace_isReadWholeByTheScreenReader_andNeverCrashes() {
        val name = "👪" + "A".repeat(36) + "🚀"
        assertEquals(40, name.length)
        show(SentPhase.SENT, name = name)
        assertEquals("Your people in the Miff “$name” have been alerted", titleDescription())
        rule.onNodeWithTag(ALERT_SENT_CALL_TAG).assertExists()
    }

    @Test
    fun aNameThatCannotBeFound_givesTheTitleWithoutAName_neverAGap() {
        show(SentPhase.SENT, name = null)
        assertEquals("Your people have been alerted", titleDescription())
    }

    @Test
    fun theListHasTwoSections_thePeopleThenTheServices_andAChoiceChangesTheCall() {
        var picked: AlertDialTarget? = null
        rule.setContent {
            AlertSentScreen(
                SentPhase.SENT,
                "Family",
                false,
                ready,
                AlertSentActions(onSelect = { picked = it }, onCall = {
                    called +=
                        it
                }),
            )
        }
        rule.onNodeWithTag(ALERT_SENT_PICKER_TAG).performScrollTo().performClick()
        rule.onNodeWithText("Your people").assertIsDisplayed()
        rule.onNodeWithText("Emergency services").assertIsDisplayed()
        rule.onNodeWithText("Medical emergencies (15)").assertIsDisplayed()
        rule.onNodeWithText("Police (17)").performClick()
        assertEquals("e:17:POLICE", picked?.id)
    }

    @Test
    fun contactsThatCouldNotBeRead_saysItCalmly_andOnlyTheServicesAreOffered() {
        val none = AlertCallList.build(emptyList(), "me", "FR")
        show(SentPhase.SENT, call = CallState.Ready(none, none.preselected, contactsUnavailable = true))
        rule
            .onNodeWithText(
                "Your people's numbers aren't available right now. You can call the emergency services.",
            ).assertIsDisplayed()
        rule.onNodeWithTag(ALERT_SENT_CALL_TAG).performScrollTo().performClick()
        assertEquals("112", called.single().number)
    }

    @Test
    fun theCallButton_isNamedWithWhoIsCalled() {
        show(SentPhase.SENT)
        rule.onNode(hasContentDescription("Call Emmanuelle")).assertIsDisplayed()
    }

    // ---- the banner -------------------------------------------------------------------------------------------------------------------

    private var seen = 0
    private var calledBanner = 0
    private var endAsked = 0
    private var endAccessible = 0

    private var closed = 0

    private fun banner(
        alert: MyAlert,
        showSee: Boolean = false,
        name: String? = "Family",
    ) {
        rule.setContent {
            AlertBanner(
                alert = alert,
                miffName = name,
                showSeeMiff = showSee,
                onCall = { calledBanner++ },
                onSeeMiff = { seen++ },
                onEndRequested = { endAsked++ },
                onEndAccessible = { endAccessible++ },
                onRetry = { retried++ },
                onClose = { closed++ },
            )
        }
    }

    private val active = MyAlert.Active("m", 1, 2)

    @Test
    fun theBannerNamesTheOriginMiff_andSaysHowToEnd() {
        banner(active)
        rule.onNodeWithText("You alerted the people of the Miff “Family”.").assertIsDisplayed()
        rule.onNodeWithText("Hold for 5 seconds to end the alert if you're no longer in danger.").assertIsDisplayed()
        rule.onNodeWithTag(ALERT_BANNER_SEE_TAG).assertDoesNotExist()
    }

    @Test
    fun whenTheFeedShowsAnotherMiff_theBannerOffersToSeeTheMiffOfOrigin() {
        banner(active, showSee = true)
        rule.onNodeWithTag(ALERT_BANNER_SEE_TAG).performClick()
        assertEquals(1, seen)
        assertEquals(0, endAsked)
    }

    @Test
    fun aTouchOnAButtonOfTheBanner_neverStartsTheHold() {
        banner(active)
        rule.onNodeWithTag(ALERT_BANNER_CALL_TAG).performClick()
        assertEquals(1, calledBanner)
        assertEquals(0, endAsked)
    }

    @Test
    fun theBannerHasTheAccessibleWayToEnd_andNoCross() {
        banner(active)
        val actions =
            rule
                .onNodeWithTag(
                    ALERT_BANNER_TAG,
                ).fetchSemanticsNode()
                .config
                .getOrNull(SemanticsActions.CustomActions)
                .orEmpty()
        val end = actions.single { it.label == "End the alert" }
        rule.runOnUiThread { end.action() }
        assertEquals(1, endAccessible)
        assertEquals(0, endAsked)
    }

    @Test
    fun holdingTheBanner5Seconds_endsTheAlert_releasingEarlierDoesNothing() {
        banner(active)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(ALERT_BANNER_TAG).performTouchInput { down(Offset(20f, 20f)) }
        rule.mainClock.advanceTimeBy(3_000)
        rule.onNodeWithTag(ALERT_BANNER_TAG).performTouchInput { up() }
        rule.mainClock.advanceTimeBy(6_000)
        assertEquals(0, endAsked)
        rule.onNodeWithTag(ALERT_BANNER_TAG).performTouchInput { down(Offset(20f, 20f)) }
        rule.mainClock.advanceTimeBy(5_600)
        assertEquals(1, endAsked)
    }

    @Test
    fun anAlertOnItsWay_says_sendingOrWaiting_andCannotBeEndedByAHold() {
        banner(MyAlert.Sending("m", 1, SendStep.WAITING_NETWORK))
        rule.onNodeWithText("Sending your alert to the Miff “Family”…").assertIsDisplayed()
        rule.onNodeWithText("Waiting to be sent").assertIsDisplayed()
        val actions =
            rule
                .onNodeWithTag(
                    ALERT_BANNER_TAG,
                ).fetchSemanticsNode()
                .config
                .getOrNull(SemanticsActions.CustomActions)
                .orEmpty()
        assertTrue(actions.isEmpty())
    }

    @Test
    fun anEndThatWaitsForTheNetwork_isSaid_inClearWords() {
        banner(MyAlert.Active("m", 1, 2, EndStep.WAITING_NETWORK))
        rule.onNodeWithText("Ending the alert: waiting for a connection").assertIsDisplayed()
    }

    @Test
    fun theDailyLimitOnTheBanner_pointsToTheEmergencyServices() {
        banner(MyAlert.Sending("m", 1, SendStep.QUOTA))
        rule
            .onNodeWithText(
                "The limit of 3 alerts a day is reached. If you're in danger, open the emergency numbers.",
            ).assertIsDisplayed()
    }

    // ---- the blue bubble and the red mode ----------------------------------------------------------------------------------------------

    @Test
    fun theBlueBubbleSaysTheTwoGestures_hidesItselfAfterItsTime_andOffersLearnMore() {
        var gone = 0
        var more = 0
        rule.mainClock.autoAdvance = false
        rule.setContent { AlertHintBubble(onMore = { more++ }, onGone = { gone++ }, visibleMillis = 6_000) }
        rule.onNodeWithText("• Tap to publish an update").assertIsDisplayed()
        rule.onNodeWithText("• Hold to declare an alert").assertIsDisplayed()
        rule.onNodeWithText("Learn more").performClick()
        assertEquals(1, more)
        rule.mainClock.advanceTimeBy(5_900)
        assertEquals(0, gone)
        rule.mainClock.advanceTimeBy(300)
        assertEquals(1, gone)
    }

    @Test
    fun theRedModeSaysIAmInDanger_andNamesTheMiffOnlyWhenAsked() {
        rule.setContent { AlertArmedOverlay(progress = 0.4f, miffName = "Family") }
        rule.onNodeWithText("I'm in danger").assertIsDisplayed()
        rule.onNodeWithText("Hold for 5 seconds to send the alert").assertIsDisplayed()
        rule.onNodeWithText("Alert for the Miff “Family”").assertIsDisplayed()
    }

    @Test
    fun theRedMode_withASingleMiff_hasNoMiffLine() {
        rule.setContent { AlertArmedOverlay(progress = 0f, miffName = null) }
        rule.onNodeWithText("I'm in danger").assertIsDisplayed()
        rule.onNodeWithText("Alert for the Miff “Family”").assertDoesNotExist()
    }

    // ---- the hold gesture ---------------------------------------------------------------------------------------------------------------

    private var taps = 0
    private var armed = 0
    private var done = 0
    private var cancelledHold = 0
    private lateinit var holdState: AlertHoldState

    private fun hold() {
        rule.setContent {
            holdState = androidx.compose.runtime.remember { AlertHoldState() }
            Box(
                Modifier
                    .size(80.dp)
                    .testTag("shutter")
                    .alertHoldGesture(holdState, onTap = {
                        taps++
                    }, onArmed = { armed++ }, onDone = { done++ }, onCancel = { cancelledHold++ }),
            )
        }
        rule.mainClock.autoAdvance = false
    }

    @Test
    fun aShortTouch_isATap_aPhoto_andNothingElse() {
        hold()
        rule.onNodeWithTag("shutter").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(150)
        rule.onNodeWithTag("shutter").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(6_000)
        assertEquals(1, taps)
        assertEquals(0, armed)
        assertEquals(0, done)
    }

    @Test
    fun theScreenArmsAfter400ms_andFiveSecondsLaterTheAlertIsDone() {
        hold()
        rule.onNodeWithTag("shutter").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(300)
        assertFalse(holdState.armed)
        rule.mainClock.advanceTimeBy(200)
        assertTrue(holdState.armed)
        assertEquals(1, armed)
        rule.mainClock.advanceTimeBy(4_000)
        assertEquals(0, done)
        rule.mainClock.advanceTimeBy(1_100)
        assertEquals(1, done)
        assertFalse(holdState.armed)
        assertEquals(0, taps)
    }

    @Test
    fun releasingBeforeFiveSeconds_cancelsSilently_noPhoto_noAlert() {
        hold()
        rule.onNodeWithTag("shutter").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(3_000)
        assertTrue(holdState.armed)
        rule.onNodeWithTag("shutter").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(100)
        assertEquals(0, done)
        assertEquals(0, taps)
        assertEquals(1, cancelledHold)
        assertFalse(holdState.armed)
    }

    @Test
    fun aFingerThatDriftsMoreThan48dp_cancels() {
        hold()
        rule.onNodeWithTag("shutter").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("shutter").performTouchInput { moveBy(Offset(400f, 400f)) }
        rule.mainClock.advanceTimeBy(6_000)
        assertEquals(0, done)
        assertEquals(1, cancelledHold)
    }

    // ---- the cross of the banner and the honest buttons (retour du PM) ------------------------------------------------------------------

    @Test
    fun aRefusedAlert_hasACross_aTryAgain_andTheEmergencyNumbers_andTheWordsDoNotSayCallToResend() {
        banner(MyAlert.Sending("m", 1, SendStep.REFUSED))
        rule.onNodeWithText("Your alert didn't go. Tap “Try again” to send it again.").assertIsDisplayed()
        rule.onNodeWithTag(ALERT_BANNER_RETRY_TAG).performClick()
        assertEquals(1, retried)
        rule.onNodeWithText("Emergency numbers").performClick()
        assertEquals(1, calledBanner)
        rule.onNodeWithTag(ALERT_BANNER_CLOSE_TAG).performClick()
        assertEquals(1, closed)
        // nothing was ended, nothing sent
        assertEquals(0, endAsked)
    }

    @Test
    fun theCross_isNamedForAScreenReader_andIs48dpWide() {
        banner(MyAlert.Sending("m", 1, SendStep.WAITING_NETWORK))
        val node = rule.onNodeWithTag(ALERT_BANNER_CLOSE_TAG)
        node.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val cross = rule.onNode(hasContentDescription("Close and give up sending the alert"))
        cross.assertExists()
    }

    private fun crossIsThere(step: SendStep) {
        rule.setContent { AlertBanner(MyAlert.Sending("m", 1, step), "Family", false, {}, {}, {}, {}) }
        rule.onNodeWithTag(ALERT_BANNER_CLOSE_TAG).assertExists()
    }

    @Test
    fun waiting_hasTheCross() = crossIsThere(SendStep.WAITING_NETWORK)

    @Test
    fun refused_hasTheCross() = crossIsThere(SendStep.REFUSED)

    @Test
    fun theDailyLimit_hasTheCross() = crossIsThere(SendStep.QUOTA)

    @Test
    fun waitingForADecision_hasTheCross() = crossIsThere(SendStep.NEEDS_CONFIRMATION)

    @Test
    fun noCrossWhileTheWriteIsInFlight_noCrossOnAnActiveAlert() {
        banner(MyAlert.Sending("m", 1, SendStep.SENDING))
        rule.onNodeWithTag(ALERT_BANNER_CLOSE_TAG).assertDoesNotExist()
    }

    @Test
    fun anActiveAlertHasNoCross() {
        banner(active)
        rule.onNodeWithTag(ALERT_BANNER_CLOSE_TAG).assertDoesNotExist()
        rule.onNodeWithTag(ALERT_BANNER_RETRY_TAG).assertDoesNotExist()
        rule.onNodeWithText("Call").assertExists()
    }

    @Test
    fun theBannerNeverShowsAnEmptyName_afterARelaunch() {
        banner(active, showSee = true, name = null)
        rule.onNodeWithText("You alerted the people of your Miff.").assertIsDisplayed()
        // "Voir la Miff" needs a name: not offered without one
        rule.onNodeWithTag(ALERT_BANNER_SEE_TAG).assertDoesNotExist()
    }

    @Test
    fun aBlankNameIsNoName_whileSending() {
        banner(MyAlert.Sending("m", 1, SendStep.WAITING_NETWORK), name = " ")
        rule.onNodeWithText("Sending your alert…").assertExists()
    }

    // ---- the screen "avertis" without anything to send, and the emergency services at once ---------------------------------------------

    @Test
    fun whenNothingCanBeSent_theScreenSaysItCalmly_andTheNumbersAreThere() {
        val services = AlertCallList.build(emptyList(), "me", "FR")
        show(
            SentPhase.UNAVAILABLE,
            name = null,
            call = CallState.Ready(services, services.preselected, contactsUnavailable = false),
        )
        assertTrue(titleDescription()!!.startsWith("The alert to your people isn't available right now."))
        assertTrue(titleDescription()!!.contains("call the emergency services directly"))
        rule.onNodeWithTag(ALERT_SENT_CALL_TAG).performScrollTo().performClick()
        assertEquals("112", called.single().number)
    }

    @Test
    fun theEmergencyServicesAreThereWhileThePeopleStillLoad() {
        val services = AlertCallList.build(emptyList(), "me", "FR")
        show(
            SentPhase.SENT,
            call = CallState.Ready(services, services.preselected, contactsUnavailable = false, loadingPeople = true),
        )
        rule.onNodeWithTag(ALERT_SENT_CALL_TAG).performScrollTo().performClick()
        assertEquals("112", called.single().number)
    }

    // ---- the shutter keeps its old behaviour (review of #275, m2) -------------------------------------------------------------------

    @Test
    fun twoQuickTapsOnTheShutter_areTwoPhotos_neverTheDoubleTapThatSwitchesTheCamera() {
        var doubleTaps = 0
        rule.setContent {
            holdState = androidx.compose.runtime.remember { AlertHoldState() }
            Box(
                Modifier
                    .size(300.dp)
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { doubleTaps++ }) },
            ) {
                Box(
                    Modifier
                        .size(80.dp)
                        .testTag("shutter")
                        .alertHoldGesture(holdState, onTap = { taps++ }, onDone = { done++ }),
                )
            }
        }
        rule.onNodeWithTag("shutter").performTouchInput {
            click()
            advanceEventTime(80)
            click()
        }
        assertEquals(2, taps)
        assertEquals(0, doubleTaps)
    }

    @Test
    fun theBarFollowsTheTimeHeld_evenWithAnimationsRemoved_neverJumpingTo100() {
        setAnimatorScale(0f)
        try {
            hold()
            rule.onNodeWithTag("shutter").performTouchInput { down(center) }
            rule.mainClock.advanceTimeBy(400 + 1_000)
            assertTrue(holdState.armed)
            val early = holdState.progress
            assertTrue("progress after 1 s of 5 is $early", early > 0.1f && early < 0.35f)
            rule.mainClock.advanceTimeBy(1_500)
            val middle = holdState.progress
            assertTrue("progress after 2.5 s of 5 is $middle", middle > 0.4f && middle < 0.6f)
        } finally {
            setAnimatorScale(1f)
        }
    }

    /** "Supprimer les animations": the animator scale of the process (a hidden setter, reached by reflection; the bar must not depend on it anyway). */
    private fun setAnimatorScale(scale: Float) {
        runCatching {
            android.animation.ValueAnimator::class.java
                .getMethod("setDurationScale", Float::class.javaPrimitiveType)
                .invoke(null, scale)
        }
    }
}
