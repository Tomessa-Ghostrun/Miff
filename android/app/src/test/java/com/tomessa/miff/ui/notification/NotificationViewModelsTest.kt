package com.tomessa.miff.ui.notification

import com.tomessa.miff.data.notification.NotificationAuthorization
import com.tomessa.miff.data.notification.NotificationCenter
import com.tomessa.miff.data.notification.NotificationCenterStore
import com.tomessa.miff.data.notification.NotificationPreferencesStore
import com.tomessa.miff.data.notification.NotificationSettings
import com.tomessa.miff.data.permission.DevicePermissions
import com.tomessa.miff.data.permission.NotificationAskStore
import com.tomessa.miff.domain.notification.NotificationFamily
import com.tomessa.miff.domain.notification.NotificationItem
import com.tomessa.miff.domain.notification.NotificationPermissionState
import com.tomessa.miff.domain.notification.NotificationPreferences
import com.tomessa.miff.domain.notification.NotificationType
import com.tomessa.miff.domain.notification.SecondChance
import com.tomessa.miff.domain.permission.PermissionSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Settings > Notifications, the centre and the bell of the Feed: what each gesture does, for each state of the phone's permission. All local. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelsTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Phone(
        var snapshot: PermissionSnapshot,
    ) : DevicePermissions {
        override fun snapshot() = snapshot
    }

    private class Asks : NotificationAskStore {
        var systemWindowLaunched = false
        val secondChances = mutableMapOf<String, SecondChance>()

        override fun wasShown(uid: String) = false

        override fun markShown(uid: String) {}

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

    private class PrefsStore : NotificationPreferencesStore {
        var saved = NotificationPreferences()

        override fun load() = saved

        override fun save(preferences: NotificationPreferences) {
            saved = preferences
        }

        override fun clear() {
            saved = NotificationPreferences()
        }
    }

    private class CentreStore : NotificationCenterStore {
        var items = emptyList<NotificationItem>()

        override fun load() = items

        override fun save(items: List<NotificationItem>) {
            this.items = items
        }

        override fun clear() {
            items = emptyList()
        }
    }

    private fun snap(
        sdk: Int = 34,
        notifications: Boolean,
    ) = PermissionSnapshot(
        sdk,
        fineLocation = true,
        coarseLocation = true,
        backgroundLocation = true,
        notifications = notifications,
    )

    private val now = 1_800_000_000_000L
    private val asks = Asks()
    private val prefsStore = PrefsStore()
    private val settings = NotificationSettings(prefsStore)
    private val tray = mutableListOf<String>()
    private val center = NotificationCenter(CentreStore(), nowMillis = { now }, onRead = { tray += it })

    private fun authorization(phone: Phone) = NotificationAuthorization(phone, asks)

    private fun settingsModel(
        phone: Phone,
        blocked: Set<NotificationFamily> = emptySet(),
    ) = NotificationSettingsViewModel(settings, authorization(phone), { it in blocked })

    private fun line(id: String) = NotificationItem(id, NotificationType.N1, "miff-1", now)

    // --- Settings > Notifications -----------------------------------------------------------------------------------------------------

    @Test
    fun allowed_theGeneralSwitchWorks_andTheTypesFollowIt() {
        val vm = settingsModel(Phone(snap(notifications = true)))
        assertTrue(vm.uiState.value.generalChecked && vm.uiState.value.typesEnabled)
        vm.onTypeToggled(NotificationType.N3, false)
        assertFalse(prefsStore.saved.n3)
        vm.onGeneralToggled(false)
        assertFalse(vm.uiState.value.generalChecked)
        assertFalse("the types are greyed", vm.uiState.value.typesEnabled)
        // A greyed switch does nothing (and keeps its value).
        vm.onTypeToggled(NotificationType.N1, false)
        vm.onNightCalmToggled(false)
        assertTrue(prefsStore.saved.n1)
        assertTrue(prefsStore.saved.nightCalm)
        vm.onGeneralToggled(true)
        assertTrue(vm.uiState.value.generalChecked)
        assertFalse("N3 was turned off before and stays off", prefsStore.saved.n3)
    }

    @Test
    fun refusedByThePhone_theGeneralSwitchIsOff_notActionable_andNothingCanBeChanged_criterionNR4() {
        val vm = settingsModel(Phone(snap(notifications = false)).also { asks.systemWindowLaunched = true })
        val state = vm.uiState.value
        assertEquals(NotificationPermissionState.OFF, state.permission)
        assertFalse("never drawn as on", state.generalChecked)
        assertFalse(state.generalEnabled)
        vm.onGeneralToggled(true)
        assertEquals("the app cannot go round a refusal of the phone", NotificationPreferences(), prefsStore.saved)
        assertFalse(vm.uiState.value.primerOpen)
        assertFalse(vm.uiState.value.typesEnabled)
    }

    @Test
    fun neverAsked_turningItOn_opensThePopIn_andNothingIsChangedUntilThePhoneAnswers() {
        val phone = Phone(snap(notifications = false))
        val vm = settingsModel(phone)
        assertEquals(NotificationPermissionState.NOT_ASKED, vm.uiState.value.permission)
        assertFalse(vm.uiState.value.generalChecked)
        assertTrue(vm.uiState.value.generalEnabled)
        vm.onGeneralToggled(false)
        assertFalse("turning off what is off opens nothing", vm.uiState.value.primerOpen)
        vm.onGeneralToggled(true)
        assertTrue(vm.uiState.value.primerOpen)
        // "Plus tard" on the pop-in: it closes, nothing changes, the window of the system was not used up.
        vm.onPrimerDismissed()
        assertFalse(vm.uiState.value.primerOpen)
        assertFalse(asks.systemWindowLaunched)
        // The window comes back and the phone allowed: the tap of the person is honoured, the window is remembered.
        vm.onGeneralToggled(true)
        phone.snapshot = snap(notifications = true)
        vm.onSystemWindowAnswered()
        assertTrue(asks.systemWindowLaunched)
        assertTrue(vm.uiState.value.generalChecked)
        assertFalse(vm.uiState.value.primerOpen)
    }

    @Test
    fun neverAsked_andTheWindowIsRefused_theStateBecomesOff_withTheCalmHelp() {
        val phone = Phone(snap(notifications = false))
        val vm = settingsModel(phone)
        vm.onGeneralToggled(true)
        vm.onSystemWindowAnswered()
        assertEquals(NotificationPermissionState.OFF, vm.uiState.value.permission)
        assertFalse(vm.uiState.value.generalChecked)
    }

    @Test
    fun comingBackFromThePhoneSettings_theStateIsReadAgain() {
        val phone = Phone(snap(notifications = false)).also { asks.systemWindowLaunched = true }
        val vm = settingsModel(phone)
        assertEquals(NotificationPermissionState.OFF, vm.uiState.value.permission)
        phone.snapshot = snap(notifications = true)
        vm.onResumed()
        assertEquals(NotificationPermissionState.ALLOWED, vm.uiState.value.permission)
        assertTrue(vm.uiState.value.generalChecked)
    }

    @Test
    fun aFamilyWhoseChannelWasTurnedOffInThePhoneIsSignalled() {
        val vm = settingsModel(Phone(snap(notifications = true)), blocked = setOf(NotificationFamily.CIRCLE))
        assertTrue(vm.uiState.value.blocked(NotificationFamily.CIRCLE))
        assertFalse(vm.uiState.value.blocked(NotificationFamily.REMINDER))
    }

    @Test
    fun beforeAndroid13_thereIsNoWindow_offMeansTheyWereTurnedOffInThePhone() {
        val vm = settingsModel(Phone(snap(sdk = 31, notifications = false)))
        assertEquals(NotificationPermissionState.OFF, vm.uiState.value.permission)
        val allowed = settingsModel(Phone(snap(sdk = 31, notifications = true)))
        assertEquals(NotificationPermissionState.ALLOWED, allowed.uiState.value.permission)
    }

    // --- The centre ---------------------------------------------------------------------------------------------------------------------

    private fun centreModel(
        phone: Phone,
        names: Map<String, String> = emptyMap(),
    ) = NotificationCenterViewModel(center, settings, authorization(phone), { names })

    @Test
    fun theBanner_followsThePermissionAndTheGeneralSwitch() =
        runTest(dispatcher) {
            val phone = Phone(snap(notifications = true))
            val vm = centreModel(phone)
            advanceUntilIdle()
            assertEquals(CenterBanner.NONE, vm.uiState.value.banner)
            settings.setEnabled(false)
            advanceUntilIdle()
            assertEquals(CenterBanner.DISABLED, vm.uiState.value.banner)
            vm.onEnableInApp()
            advanceUntilIdle()
            assertEquals(CenterBanner.NONE, vm.uiState.value.banner)
            phone.snapshot = snap(notifications = false)
            vm.onResumed()
            advanceUntilIdle()
            assertEquals(CenterBanner.NOT_ASKED, vm.uiState.value.banner)
            asks.systemWindowLaunched = true
            vm.onResumed()
            advanceUntilIdle()
            assertEquals(CenterBanner.OFF, vm.uiState.value.banner)
        }

    @Test
    fun theLines_areNewestFirst_unreadAtFirst_andTheMiffNameOnlyWhenKnown() =
        runTest(dispatcher) {
            center.add(NotificationItem("a", NotificationType.N1, "miff-1", now - 2000))
            center.add(NotificationItem("b", NotificationType.N6, "miff-2", now - 1000))
            val vm = centreModel(Phone(snap(notifications = true)), names = mapOf("miff-2" to "Famille"))
            advanceUntilIdle()
            val rows = vm.uiState.value.rows
            assertEquals(listOf("b", "a"), rows.map { it.id })
            assertTrue(rows.all { it.unread })
            assertEquals("Famille", rows[0].miffName)
            assertNull("a single Miff: no name", rows[1].miffName)
        }

    @Test
    fun aTap_marksTheLineRead_andTakesItsNotificationOutOfTheTray() =
        runTest(dispatcher) {
            center.add(line("a"))
            val vm = centreModel(Phone(snap(notifications = true)))
            advanceUntilIdle()
            vm.onOpen("a")
            advanceUntilIdle()
            assertFalse(
                vm.uiState.value.rows
                    .single()
                    .unread,
            )
            assertEquals(listOf("a"), tray)
        }

    @Test
    fun deleteAndClearAll() =
        runTest(dispatcher) {
            center.add(NotificationItem("a", NotificationType.N1, null, now - 2))
            center.add(NotificationItem("b", NotificationType.N2, null, now - 1))
            val vm = centreModel(Phone(snap(notifications = true)))
            advanceUntilIdle()
            vm.onDelete("a")
            advanceUntilIdle()
            assertEquals(
                listOf("b"),
                vm.uiState.value.rows
                    .map { it.id },
            )
            vm.onClearAll()
            advanceUntilIdle()
            assertTrue(
                vm.uiState.value.rows
                    .isEmpty(),
            )
        }

    @Test
    fun theWindowOfTheSystemFromTheBanner_isRemembered_andAYesTurnsThemOn() =
        runTest(dispatcher) {
            val phone = Phone(snap(notifications = false))
            settings.setEnabled(false)
            val vm = centreModel(phone)
            advanceUntilIdle()
            phone.snapshot = snap(notifications = true)
            vm.onSystemWindowAnswered()
            advanceUntilIdle()
            assertTrue(asks.systemWindowLaunched)
            assertTrue(prefsStore.saved.enabled)
            assertEquals(CenterBanner.NONE, vm.uiState.value.banner)
        }

    // --- The bell and the second chance ---------------------------------------------------------------------------------------------------

    private fun feedModel(phone: Phone) =
        FeedNotificationsViewModel(center, settings, authorization(phone), asks, {
            "uid-1"
        })

    @Test
    fun theDotFollowsTheUnreadLines_andGoesWhenTheyAreRead() =
        runTest(dispatcher) {
            val vm = feedModel(Phone(snap(notifications = true)))
            advanceUntilIdle()
            assertEquals(0, vm.uiState.value.unreadCount)
            center.add(line("a"))
            advanceUntilIdle()
            assertEquals(1, vm.uiState.value.unreadCount)
            center.markAllRead()
            advanceUntilIdle()
            assertEquals(0, vm.uiState.value.unreadCount)
        }

    @Test
    fun theSecondChanceCard_appearsAfterTheFirstUpdateOfSomeoneWhoPutItOff_andOnlyOnce() =
        runTest(dispatcher) {
            asks.secondChances["uid-1"] = SecondChance.DEFERRED
            val phone = Phone(snap(notifications = false))
            val vm = feedModel(phone)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.secondChanceVisible)
            vm.onUpdatePublished()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.secondChanceVisible)
            // "Pas maintenant": gone for good, even after another update.
            vm.onSecondChanceLater()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.secondChanceVisible)
            vm.onUpdatePublished()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.secondChanceVisible)
            assertEquals(SecondChance.DONE, asks.secondChances["uid-1"])
        }

    @Test
    fun theSecondChanceCard_neverAppearsForSomeoneWhoDidNotPutTheWindowOff_orWhoAllowedElsewhere() =
        runTest(dispatcher) {
            val phone = Phone(snap(notifications = false))
            val vm = feedModel(phone)
            vm.onUpdatePublished()
            advanceUntilIdle()
            assertFalse("no pop-in was put off: no card", vm.uiState.value.secondChanceVisible)

            asks.secondChances["uid-1"] = SecondChance.READY
            phone.snapshot = snap(notifications = true)
            vm.refresh()
            advanceUntilIdle()
            assertFalse("the permission was given in the phone settings: no card", vm.uiState.value.secondChanceVisible)
        }

    @Test
    fun enablingFromTheCard_answersTheCardForGood_andTurnsThemOn() =
        runTest(dispatcher) {
            asks.secondChances["uid-1"] = SecondChance.READY
            settings.setEnabled(false)
            val phone = Phone(snap(notifications = false))
            val vm = feedModel(phone)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.secondChanceVisible)
            phone.snapshot = snap(notifications = true)
            vm.onSystemWindowAnswered()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.secondChanceVisible)
            assertTrue(prefsStore.saved.enabled)
            assertTrue(asks.systemWindowLaunched)
        }
}
