package com.tomessa.miff.ui.permission

import com.tomessa.miff.data.miff.ConsentRenewalCheck
import com.tomessa.miff.data.miff.ConsentRenewals
import com.tomessa.miff.data.miff.MiffWriteResult
import com.tomessa.miff.data.miff.RenewalMiff
import com.tomessa.miff.data.permission.DevicePermissions
import com.tomessa.miff.data.permission.NotificationAskStore
import com.tomessa.miff.domain.miff.MiffFailure
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

        override fun wasShown(uid: String) = uid in shown

        override fun markShown(uid: String) {
            shown += uid
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
            assertEquals(LocationButton.OPEN_SETTINGS, model.uiState.value.location.button)
            assertFalse(model.uiState.value.location.error)
            // To the settings and back with the wrong choice: the message of the ticket.
            model.onSettingsOpened()
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

    @Test
    fun noSession_nothingIsLookedAt() =
        runTest {
            val model = vm(Phone(snap(true, true, true, true)), uid = null)
            advanceUntilIdle()
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
        }
}
