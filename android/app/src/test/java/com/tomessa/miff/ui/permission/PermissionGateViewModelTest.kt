package com.tomessa.miff.ui.permission

import com.tomessa.miff.data.miff.AwaitingAgreement
import com.tomessa.miff.data.miff.ConsentRenewalCheck
import com.tomessa.miff.data.miff.ConsentRenewals
import com.tomessa.miff.data.miff.MiffWriteResult
import com.tomessa.miff.data.miff.RenewalMiff
import com.tomessa.miff.data.permission.DevicePermissions
import com.tomessa.miff.data.permission.NotificationAskStore
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.notification.SecondChance
import com.tomessa.miff.domain.permission.BackgroundRequestOutcome
import com.tomessa.miff.domain.permission.GateScreen
import com.tomessa.miff.domain.permission.LocationButton
import com.tomessa.miff.domain.permission.PermissionSnapshot
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.ui.miff.ActionMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The gate of the permission screens (LOC-2a): withdrawal at resume (AUT5), the settings round trip (AUT3), the renewal, the switch (AUT6). */
@OptIn(ExperimentalCoroutinesApi::class)
class PermissionGateViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class Phone(
        var snapshot: PermissionSnapshot,
    ) : DevicePermissions {
        override fun snapshot() = snapshot
    }

    private class Asks : NotificationAskStore {
        val shown = mutableSetOf<String>()
        var systemWindowLaunched = false
        val secondChances = mutableMapOf<String, SecondChance>()

        override fun wasShown(uid: String) = uid in shown

        override fun markShown(uid: String) {
            shown += uid
        }

        override fun systemAsked() = systemWindowLaunched

        override fun markSystemAsked() {
            systemWindowLaunched = true
        }

        override fun secondChance(uid: String) = secondChances[uid] ?: SecondChance.NONE

        override fun setSecondChance(
            uid: String,
            value: SecondChance,
        ) {
            secondChances[uid] = value
        }
    }

    private class Renewals(
        var check: ConsentRenewalCheck = ConsentRenewalCheck.NothingToRenew,
        var renewResult: MiffWriteResult = MiffWriteResult.Done,
    ) : ConsentRenewals {
        val renewed = mutableListOf<List<String>>()
        var gate: CompletableDeferred<Unit>? = null

        var checkGate: CompletableDeferred<Unit>? = null

        override suspend fun check(uid: String): ConsentRenewalCheck {
            checkGate?.await()
            return check
        }

        override suspend fun renew(
            uid: String,
            miffIds: List<String>,
        ): MiffWriteResult {
            renewed += miffIds
            gate?.await()
            return renewResult
        }
    }

    private fun snap(
        fine: Boolean = false,
        coarse: Boolean = false,
        background: Boolean = false,
        notifications: Boolean = false,
        sdk: Int = 34,
    ) = PermissionSnapshot(sdk, fine, coarse, background, notifications)

    private val asks = Asks()
    private val renewals = Renewals()
    private val left = mutableListOf<String>()
    private var leaveResult: MiffWriteResult = MiffWriteResult.Done
    private val leaveCalls = mutableListOf<Pair<String, String>>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        phone: Phone,
        blocking: Boolean = true,
        uid: String? = "uid-1",
        awaiting: AwaitingAgreement = AwaitingAgreement(),
    ) = PermissionGateViewModel(
        phone,
        asks,
        renewals,
        { uid },
        blocking,
        leaver = { u, id ->
            leaveCalls += u to id
            leaveResult
        },
        onLeft = { left += it },
        awaiting = awaiting,
    )

    @Test
    fun everythingGranted_theFeedIsShown_andNothingIsAskedOfTheSystem() =
        runTest {
            val model = vm(Phone(snap(true, true, true, true)))
            advanceUntilIdle()
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
        }

    @Test
    fun notificationsThenLocation_theNotificationScreenIsShownOnce() =
        runTest {
            val phone = Phone(snap())
            val model = vm(phone)
            advanceUntilIdle()
            assertEquals(GateScreen.NOTIFICATIONS, model.uiState.value.screen)
            model.onNotificationsAnswered()
            assertEquals(GateScreen.LOCATION, model.uiState.value.screen)
            assertTrue("uid-1" in asks.shown)
            // A new view model of the same account (next launch): the screen is not shown again, even though they were refused.
            val next = vm(Phone(snap()))
            advanceUntilIdle()
            assertEquals(GateScreen.LOCATION, next.uiState.value.screen)
        }

    @Test
    fun notificationsPutOff_doesNotUseUpTheSystemWindow_neverComesBack_andPreparesTheSecondChance() =
        runTest {
            val model = vm(Phone(snap()))
            advanceUntilIdle()
            assertEquals(GateScreen.NOTIFICATIONS, model.uiState.value.screen)
            model.onNotificationsPutOff()
            // The pop-in is gone, the location screen follows, and the window of the system was NOT launched (it still can be, once).
            assertEquals(GateScreen.LOCATION, model.uiState.value.screen)
            assertFalse(asks.systemWindowLaunched)
            assertEquals(SecondChance.DEFERRED, asks.secondChance("uid-1"))
            // Next launch: not shown again.
            val next = vm(Phone(snap()))
            advanceUntilIdle()
            assertEquals(GateScreen.LOCATION, next.uiState.value.screen)
        }

    @Test
    fun notificationsAnswered_remembersThatTheSystemWindowWasShown_andNeverOffersTheSecondChance() =
        runTest {
            val model = vm(Phone(snap()))
            advanceUntilIdle()
            model.onNotificationsAnswered()
            assertTrue(asks.systemWindowLaunched)
            assertEquals(SecondChance.NONE, asks.secondChance("uid-1"))
        }

    @Test
    fun locationBlocks_theWindowThenTheSettings_andTheScreenClosesOnlyWhenAlwaysIsGiven() =
        runTest {
            val phone = Phone(snap(notifications = true))
            val model = vm(phone)
            advanceUntilIdle()
            assertEquals(GateScreen.LOCATION, model.uiState.value.screen)
            assertFalse(model.uiState.value.location.error)
            // The window: the precise location is given while using the app.
            phone.snapshot = snap(fine = true, coarse = true, notifications = true)
            model.onLocationDialogAnswered(canAskAgain = true)
            assertEquals(GateScreen.LOCATION, model.uiState.value.screen)
            // Android 11+: the next button asks the system for ITS OWN screen of the permission (not the settings of the app).
            assertEquals(LocationButton.SYSTEM_BACKGROUND_SCREEN, model.uiState.value.location.button)
            assertFalse(model.uiState.value.location.error)
            // The system opened nothing (refused at once): the screen must open the settings of the app, on that same tap.
            assertTrue(model.onBackgroundRequestAnswered(BackgroundRequestOutcome.NO_UI))
            assertEquals(LocationButton.OPEN_SETTINGS, model.uiState.value.location.button)
            // Back from the settings with the wrong choice: the message of the ticket.
            model.onResumed()
            assertEquals(GateScreen.LOCATION, model.uiState.value.screen)
            assertTrue(model.uiState.value.location.error)
            // To the settings again, "all the time" this time: the screen closes.
            model.onSettingsOpened()
            phone.snapshot = snap(fine = true, coarse = true, background = true, notifications = true)
            model.onResumed()
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
        }

    @Test
    fun android11Plus_theSystemScreenOfTheLocation_isAskedFirst_andTheGateClosesOnItsOwnWhenAlwaysIsGiven() =
        runTest {
            val phone = Phone(snap(fine = true, coarse = true, notifications = true, sdk = 33))
            val model = vm(phone)
            advanceUntilIdle()
            assertEquals(GateScreen.LOCATION, model.uiState.value.screen)
            assertEquals(LocationButton.SYSTEM_BACKGROUND_SCREEN, model.uiState.value.location.button)
            assertTrue(model.uiState.value.location.systemScreen)
            // The system showed its screen, the person chose "only while using": the calm message, same button, the settings of the app NOT opened.
            assertFalse(model.onBackgroundRequestAnswered(BackgroundRequestOutcome.SYSTEM_UI_SEEN))
            model.onResumed()
            assertTrue(model.uiState.value.location.error)
            assertEquals(LocationButton.SYSTEM_BACKGROUND_SCREEN, model.uiState.value.location.button)
            // Second time: "Toujours autoriser". Coming back, the state is read and the next step (the Feed) follows by itself.
            phone.snapshot = snap(fine = true, coarse = true, background = true, notifications = true, sdk = 33)
            assertFalse(model.onBackgroundRequestAnswered(BackgroundRequestOutcome.GRANTED))
            model.onResumed()
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
        }

    @Test
    fun android11Plus_aSystemThatAnswersAtOnceNeverMakesALoop_theSettingsOfTheAppTakeOver() =
        runTest {
            val phone = Phone(snap(fine = true, coarse = true, notifications = true, sdk = 31))
            val model = vm(phone)
            advanceUntilIdle()
            assertTrue(model.onBackgroundRequestAnswered(BackgroundRequestOutcome.NO_UI))
            // The next taps go straight to the settings of the app: no second request that would be refused again.
            assertEquals(LocationButton.OPEN_SETTINGS, model.uiState.value.location.button)
            assertFalse(model.uiState.value.location.systemScreen)
            model.onResumed()
            assertEquals(LocationButton.OPEN_SETTINGS, model.uiState.value.location.button)
        }

    @Test
    fun android10_theBackgroundIsStillAskedByTheWindowOfTheSystem_notBySystemScreen() =
        runTest {
            val model = vm(Phone(snap(fine = true, coarse = true, notifications = true, sdk = 29)))
            advanceUntilIdle()
            assertEquals(LocationButton.SYSTEM_DIALOG, model.uiState.value.location.button)
            assertFalse(model.uiState.value.location.systemScreen)
        }

    @Test
    fun aWithdrawalSeenWhenTheAppComesBackToTheFront_bringsTheBlockingScreenBack() =
        runTest {
            val phone = Phone(snap(true, true, true, true))
            val model = vm(phone)
            advanceUntilIdle()
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
            phone.snapshot = phone.snapshot.copy(backgroundLocation = false)
            model.onResumed()
            assertEquals(GateScreen.LOCATION, model.uiState.value.screen)
        }

    @Test
    fun theBuildSwitch_blocking_cannotBePutOff_relaxed_can() =
        runTest {
            val blocking = vm(Phone(snap(notifications = true)), blocking = true)
            advanceUntilIdle()
            blocking.onLocationPutOff()
            assertEquals(GateScreen.LOCATION, blocking.uiState.value.screen)
            assertFalse(blocking.uiState.value.canPutOffLocation)

            val relaxed = vm(Phone(snap(notifications = true)), blocking = false)
            advanceUntilIdle()
            assertTrue(relaxed.uiState.value.canPutOffLocation)
            relaxed.onLocationPutOff()
            assertEquals(GateScreen.NONE, relaxed.uiState.value.screen)
        }

    @Test
    fun theDefaultOfTheBuild_isBlocking() {
        assertTrue(
            "LOCATION_BLOCKING must be true unless -Pmiff.locationBlocking=false",
            com.tomessa.miff.BuildConfig.LOCATION_BLOCKING,
        )
    }

    // --- Renewal -------------------------------------------------------------------------------------------------------

    private val due = ConsentRenewalCheck.Due(listOf(RenewalMiff(MIFF_ID, "Chez nous")))

    @Test
    fun anOldConsent_showsTheRenewalScreen_andNotNowWritesNothing() =
        runTest {
            renewals.check = due
            val model = vm(Phone(snap(true, true, true, true)))
            advanceUntilIdle()
            assertEquals(GateScreen.RENEWAL, model.uiState.value.screen)
            assertNotNull(model.uiState.value.renewal)
            model.onRenewalPutOff()
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
            assertTrue("nothing is written without an agreement", renewals.renewed.isEmpty())
        }

    @Test
    fun notNow_comesBackAtTheNextLaunch() =
        runTest {
            renewals.check = due
            val phone = Phone(snap(true, true, true, true))
            val first = vm(phone)
            advanceUntilIdle()
            first.onRenewalPutOff()
            val next = vm(phone)
            advanceUntilIdle()
            assertEquals(GateScreen.RENEWAL, next.uiState.value.screen)
        }

    @Test
    fun agreeing_writesOnce_closesTheScreen_andAnExtraTapIsIgnored() =
        runTest {
            renewals.check = due
            renewals.gate = CompletableDeferred()
            val model = vm(Phone(snap(true, true, true, true)))
            advanceUntilIdle()
            model.onRenewalAccepted()
            model.onRenewalAccepted()
            advanceUntilIdle()
            assertTrue(
                model.uiState.value.renewal!!
                    .busy,
            )
            assertEquals(1, renewals.renewed.size)
            renewals.gate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
            assertNull(model.uiState.value.renewal)
            assertEquals(listOf(listOf(MIFF_ID)), renewals.renewed)
        }

    @Test
    fun aFailedRenewal_staysOnTheScreen_withACalmMessage_andNothingIsRetriedByItself() =
        runTest {
            renewals.check = due
            renewals.renewResult = MiffWriteResult.Failed(MiffFailure.OFFLINE)
            val model = vm(Phone(snap(true, true, true, true)))
            advanceUntilIdle()
            model.onRenewalAccepted()
            advanceUntilIdle()
            assertEquals(GateScreen.RENEWAL, model.uiState.value.screen)
            assertEquals(
                ActionMessage.OFFLINE,
                model.uiState.value.renewal!!
                    .message,
            )
            assertFalse(
                model.uiState.value.renewal!!
                    .busy,
            )
            assertEquals(1, renewals.renewed.size)
        }

    @Test
    fun whenTheServerCannotBeAsked_nothingIsClaimedAndNothingBlocks() =
        runTest {
            renewals.check = ConsentRenewalCheck.Unknown
            val model = vm(Phone(snap(true, true, true, true)))
            advanceUntilIdle()
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
        }

    @Test
    fun consentFirst_whileItIsDue_theLocationScreenWaitsForTheAgreement() =
        runTest {
            renewals.check = due
            val model = vm(Phone(snap(notifications = true)))
            // While the check runs the "Toujours" screen is NOT shown (a neutral wait): the agreement comes first, and no permission is
            // asked before it (review of the PR).
            assertEquals(GateScreen.CHECKING, model.uiState.value.screen)
            advanceUntilIdle()
            assertEquals(GateScreen.RENEWAL, model.uiState.value.screen)
            model.onRenewalPutOff()
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
        }

    @Test
    fun theLocationScreenWaitsForTheEndOfTheCheck_evenWhenItFails_andTheWaitIsBounded() =
        runTest {
            renewals.checkGate = CompletableDeferred() // never answers: a slow server
            val model = vm(Phone(snap(notifications = true)))
            advanceUntilIdle()
            // The bound (8 s) has passed: the check ended as "unknown" and the screen is shown, not an endless wait.
            assertEquals(GateScreen.LOCATION, model.uiState.value.screen)
        }

    @Test
    fun whileTheCheckRuns_theScreenIsTheNeutralWait_thenTheAnswerDecides() =
        runTest {
            renewals.checkGate = CompletableDeferred()
            val model = vm(Phone(snap(notifications = true)))
            dispatcher.scheduler.runCurrent()
            assertEquals(GateScreen.CHECKING, model.uiState.value.screen)
            renewals.check = due
            renewals.checkGate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(GateScreen.RENEWAL, model.uiState.value.screen)
        }

    @Test
    fun aFailedCheck_endsAsUnknown_andLetsTheLocationScreenThrough() =
        runTest {
            renewals.check = ConsentRenewalCheck.Unknown
            val model = vm(Phone(snap(notifications = true)))
            assertEquals(GateScreen.CHECKING, model.uiState.value.screen)
            advanceUntilIdle()
            assertEquals(GateScreen.LOCATION, model.uiState.value.screen)
        }

    // --- Leaving from the renewal screen (design 21B) ------------------------------------------------------------------

    @Test
    fun aMemberWithOneMiff_canLeaveFromTheRenewalScreen_afterConfirming() =
        runTest {
            renewals.check = due
            val model = vm(Phone(snap(true, true, true, true)))
            advanceUntilIdle()
            assertNotNull(
                model.uiState.value.renewal!!
                    .leavable,
            )
            model.onLeaveRequested()
            assertTrue(
                model.uiState.value.renewal!!
                    .leaveAsked,
            )
            assertTrue("nothing is written before the confirmation", leaveCalls.isEmpty())
            model.onLeaveConfirmed()
            advanceUntilIdle()
            assertEquals(listOf("uid-1" to MIFF_ID), leaveCalls)
            assertEquals(listOf(MIFF_ID), left)
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
            assertTrue("leaving writes no agreement", renewals.renewed.isEmpty())
        }

    @Test
    fun cancellingTheLeaveQuestion_writesNothing_andAFailedLeaveKeepsTheScreenWithACalmMessage() =
        runTest {
            renewals.check = due
            val model = vm(Phone(snap(true, true, true, true)))
            advanceUntilIdle()
            model.onLeaveRequested()
            model.onLeaveCancelled()
            assertFalse(
                model.uiState.value.renewal!!
                    .leaveAsked,
            )
            assertTrue(leaveCalls.isEmpty())
            leaveResult = MiffWriteResult.Failed(MiffFailure.OFFLINE)
            model.onLeaveRequested()
            model.onLeaveConfirmed()
            advanceUntilIdle()
            assertEquals(GateScreen.RENEWAL, model.uiState.value.screen)
            assertEquals(
                ActionMessage.OFFLINE,
                model.uiState.value.renewal!!
                    .message,
            )
            assertTrue(left.isEmpty())
        }

    @Test
    fun theCreator_andSeveralMiffs_haveNoLeaveLink() =
        runTest {
            renewals.check = ConsentRenewalCheck.Due(listOf(RenewalMiff(MIFF_ID, "Chez nous", isOwner = true)))
            val creator = vm(Phone(snap(true, true, true, true)))
            advanceUntilIdle()
            assertNull(
                creator.uiState.value.renewal!!
                    .leavable,
            )
            creator.onLeaveRequested()
            assertFalse(
                creator.uiState.value.renewal!!
                    .leaveAsked,
            )
            renewals.check =
                ConsentRenewalCheck.Due(listOf(RenewalMiff(MIFF_ID, "A"), RenewalMiff("ZyXwVuTsRqPoNmLkJiHg", "B")))
            val several = vm(Phone(snap(true, true, true, true)))
            advanceUntilIdle()
            assertNull(
                several.uiState.value.renewal!!
                    .leavable,
            )
        }

    // --- LOC-2b, design 23: the Miffs that wait for the agreement -----------------------------------------------------------

    @Test
    fun theMiffsThatWait_areTold_whileNotNowIsChosen_andForgottenOnceTheAgreementIsGiven() =
        runTest {
            renewals.check = due
            val awaiting = AwaitingAgreement()
            val model = vm(Phone(snap(true, true, true, true)), awaiting = awaiting)
            advanceUntilIdle()
            assertEquals(setOf(MIFF_ID), awaiting.waiting.value)
            model.onRenewalPutOff()
            // The Feed is shown, and the Miff is still said to wait: nothing was written.
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
            assertEquals(setOf(MIFF_ID), awaiting.waiting.value)
            assertTrue(renewals.renewed.isEmpty())
        }

    @Test
    fun reviewAndAgree_bringsTheRenewalScreenBack_afterNotNow() =
        runTest {
            renewals.check = due
            val awaiting = AwaitingAgreement()
            val model = vm(Phone(snap(true, true, true, true)), awaiting = awaiting)
            advanceUntilIdle()
            model.onRenewalPutOff()
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
            awaiting.requestReview()
            advanceUntilIdle()
            assertEquals(GateScreen.RENEWAL, model.uiState.value.screen)
            model.onRenewalAccepted()
            advanceUntilIdle()
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
            assertEquals(emptySet<String>(), awaiting.waiting.value)
            assertEquals(listOf(listOf(MIFF_ID)), renewals.renewed)
        }

    @Test
    fun nothingToRenew_nobodyWaits() =
        runTest {
            val awaiting = AwaitingAgreement()
            awaiting.replace(setOf("stale"))
            vm(Phone(snap(true, true, true, true)), awaiting = awaiting)
            advanceUntilIdle()
            assertEquals(emptySet<String>(), awaiting.waiting.value)
        }

    @Test
    fun aMiffLeftFromTheRenewalScreen_doesNotWaitAnyMore() =
        runTest {
            renewals.check = due
            val awaiting = AwaitingAgreement()
            val model = vm(Phone(snap(true, true, true, true)), awaiting = awaiting)
            advanceUntilIdle()
            model.onLeaveRequested()
            model.onLeaveConfirmed()
            advanceUntilIdle()
            assertEquals(emptySet<String>(), awaiting.waiting.value)
        }

    @Test
    fun aReviewRequestedWithNothingDue_changesNothing() =
        runTest {
            val awaiting = AwaitingAgreement()
            val model = vm(Phone(snap(true, true, true, true)), awaiting = awaiting)
            advanceUntilIdle()
            awaiting.requestReview()
            advanceUntilIdle()
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
        }

    @Test
    fun noSession_nothingIsLookedAt() =
        runTest {
            val model = vm(Phone(snap(true, true, true, true)), uid = null)
            advanceUntilIdle()
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
        }
}
