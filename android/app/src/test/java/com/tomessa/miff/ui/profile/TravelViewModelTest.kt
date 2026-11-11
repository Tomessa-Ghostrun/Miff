package com.tomessa.miff.ui.profile

import com.tomessa.miff.data.tracking.TravelControl
import com.tomessa.miff.data.tracking.TravelResult
import com.tomessa.miff.domain.travel.TravelPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import java.time.LocalDate
import java.time.ZoneId

/**
 * MODE VOYAGE, the card of the Profile (VOY-2A, criteria P2, P3): the switch opens the date picker, the confirmation comes before anything is written, "Annuler" changes
 * nothing, a refusal puts the switch back to off with a calm message, no network is accepted with its own message, turning off is immediate. NO account is ever sent to an
 * offer: the trip is open to everybody (decision of the PM, 2026-11-10).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TravelViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val paris = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 11, 10)

    private class FakeControl : TravelControl {
        val state = MutableStateFlow<TravelPeriod?>(null)
        override val travel: StateFlow<TravelPeriod?> get() = state
        var result = TravelResult.Done
        val sets = mutableListOf<TravelPeriod>()
        var ends = 0
        var reloads = 0

        override fun reload() {
            reloads++
        }

        override suspend fun set(period: TravelPeriod): TravelResult {
            sets += period
            if (result == TravelResult.Done || result == TravelResult.DoneOffline) state.value = period
            return result
        }

        override suspend fun end(): Boolean {
            ends++
            val was = state.value != null
            state.value = null
            return was
        }
    }

    private val control = FakeControl()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun model() = TravelViewModel(control, today = { today }, zone = { paris })

    @Test
    fun theSwitch_opensThePicker_withTheDefaultDayAndTheBounds_andWritesNothing() =
        runTest(dispatcher) {
            val vm = model()
            vm.onToggle()
            val picker = vm.uiState.value.picker!!
            assertEquals(TravelPickerPurpose.START, picker.purpose)
            assertEquals(today.plusDays(7), picker.initialDay)
            assertEquals(today, picker.minDay)
            assertEquals(today.plusDays(89), picker.maxDay)
            assertTrue(control.sets.isEmpty())
            assertEquals(1, control.reloads)
        }

    @Test
    fun aDay_goesThroughTheConfirmation_beforeAnythingIsWritten() =
        runTest(dispatcher) {
            val vm = model()
            vm.onToggle()
            vm.onDayChosen(LocalDate.of(2026, 11, 15))
            val state = vm.uiState.value
            assertNull(state.picker)
            assertEquals("2026-11-15", state.confirm!!.untilDay)
            assertTrue(control.sets.isEmpty())

            vm.onConfirm()
            advanceUntilIdle()
            assertEquals(listOf("2026-11-15"), control.sets.map { it.untilDay })
            assertNull(vm.uiState.value.confirm)
            assertEquals(
                "2026-11-15",
                vm.uiState.value.period!!
                    .untilDay,
            )
            assertNull(vm.uiState.value.message)
            assertFalse(vm.uiState.value.working)
        }

    @Test
    fun cancellingTheConfirmation_changesNothing() =
        runTest(dispatcher) {
            val vm = model()
            vm.onToggle()
            vm.onDayChosen(today.plusDays(3))
            vm.onConfirmDismissed()
            advanceUntilIdle()
            assertNull(vm.uiState.value.confirm)
            assertNull(vm.uiState.value.period)
            assertTrue(control.sets.isEmpty())
        }

    @Test
    fun closingThePicker_changesNothing() =
        runTest(dispatcher) {
            val vm = model()
            vm.onToggle()
            vm.onPickerDismissed()
            assertNull(vm.uiState.value.picker)
            assertTrue(control.sets.isEmpty())
        }

    @Test
    fun aDayOutsideTheBounds_isIgnored() =
        runTest(dispatcher) {
            val vm = model()
            vm.onToggle()
            vm.onDayChosen(today.minusDays(1))
            assertNull(vm.uiState.value.confirm)
            assertNotNull("the picker stays open", vm.uiState.value.picker)
            vm.onDayChosen(today.plusDays(90))
            assertNull("today + 90 would end more than 90 days ahead", vm.uiState.value.confirm)
            vm.onDayChosen(today.plusDays(91))
            assertNull(vm.uiState.value.confirm)
        }

    @Test
    fun theServerSaysNo_theSwitchStaysOff_withACalmMessage() =
        runTest(dispatcher) {
            control.result = TravelResult.Refused
            val vm = model()
            vm.onToggle()
            vm.onDayChosen(today.plusDays(2))
            vm.onConfirm()
            advanceUntilIdle()
            assertNull(vm.uiState.value.period)
            assertEquals(TravelMessage.REFUSED, vm.uiState.value.message)
            assertFalse(vm.uiState.value.working)
        }

    @Test
    fun aRefusedChangeOfTheEnd_hasItsOwnMessage_andTheEndThatWasThereStays() =
        runTest(dispatcher) {
            control.state.value = TravelPeriod(1_900_000_000_000L, "2026-11-15")
            val vm = model()
            advanceUntilIdle()
            control.result = TravelResult.Refused
            vm.onChangeDate()
            vm.onDayChosen(LocalDate.of(2026, 11, 20))
            advanceUntilIdle()
            assertEquals(TravelMessage.UPDATE_REFUSED, vm.uiState.value.message)
            assertEquals(
                "2026-11-15",
                vm.uiState.value.period!!
                    .untilDay,
            )
            // It does not go away by itself (something did not work); the next gesture clears it.
            advanceTimeBy(TravelViewModel.MESSAGE_SHOWN_MILLIS * 3)
            assertEquals(TravelMessage.UPDATE_REFUSED, vm.uiState.value.message)
            vm.onChangeDate()
            assertNull(vm.uiState.value.message)
        }

    @Test
    fun theSentencesThatOnlyInform_goAwayByThemselves() =
        runTest(dispatcher) {
            control.result = TravelResult.DoneOffline
            val vm = model()
            vm.onToggle()
            vm.onDayChosen(today.plusDays(2))
            vm.onConfirm()
            runCurrent()
            assertEquals(TravelMessage.OFFLINE, vm.uiState.value.message)
            advanceTimeBy(TravelViewModel.MESSAGE_SHOWN_MILLIS + 1)
            runCurrent()
            assertNull(vm.uiState.value.message)

            vm.onToggle()
            runCurrent()
            assertEquals(TravelMessage.RESUMED, vm.uiState.value.message)
            advanceTimeBy(TravelViewModel.MESSAGE_SHOWN_MILLIS + 1)
            runCurrent()
            assertNull(vm.uiState.value.message)
        }

    @Test
    fun aNewGesture_replacesTheSentence_andTheOldTimerDoesNotClearTheNewOne() =
        runTest(dispatcher) {
            control.state.value = TravelPeriod(1_900_000_000_000L, "2026-11-15")
            val vm = model()
            runCurrent()
            vm.onToggle()
            runCurrent()
            assertEquals(TravelMessage.RESUMED, vm.uiState.value.message)
            advanceTimeBy(TravelViewModel.MESSAGE_SHOWN_MILLIS - 1_000)
            control.result = TravelResult.Refused
            vm.onToggle()
            vm.onDayChosen(today.plusDays(2))
            vm.onConfirm()
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(TravelMessage.REFUSED, vm.uiState.value.message)
        }

    @Test
    fun withoutNetwork_theTripIsOn_withItsOwnMessage() =
        runTest(dispatcher) {
            control.result = TravelResult.DoneOffline
            val vm = model()
            vm.onToggle()
            vm.onDayChosen(today.plusDays(2))
            vm.onConfirm()
            runCurrent()
            assertNotNull(vm.uiState.value.period)
            assertEquals(TravelMessage.OFFLINE, vm.uiState.value.message)
        }

    @Test
    fun turningOff_isImmediate_withoutAConfirmation_andSaysTheTrackingResumes() =
        runTest(dispatcher) {
            control.state.value = TravelPeriod(1_900_000_000_000L, "2026-11-15")
            val vm = model()
            runCurrent()
            assertNotNull(vm.uiState.value.period)
            vm.onToggle()
            runCurrent()
            assertEquals(1, control.ends)
            assertNull(vm.uiState.value.picker)
            assertNull(vm.uiState.value.confirm)
            assertNull(vm.uiState.value.period)
            assertEquals(TravelMessage.RESUMED, vm.uiState.value.message)
        }

    @Test
    fun movingTheEnd_opensThePickerOnTheCurrentDay_andNeedsNoConfirmation() =
        runTest(dispatcher) {
            control.state.value = TravelPeriod(1_900_000_000_000L, "2026-11-15")
            val vm = model()
            advanceUntilIdle()
            vm.onChangeDate()
            val picker = vm.uiState.value.picker!!
            assertEquals(TravelPickerPurpose.CHANGE, picker.purpose)
            assertEquals(LocalDate.of(2026, 11, 15), picker.initialDay)

            vm.onDayChosen(LocalDate.of(2026, 11, 20))
            advanceUntilIdle()
            assertNull(vm.uiState.value.confirm)
            assertEquals("2026-11-20", control.sets.single().untilDay)
        }

    @Test
    fun aSecondTouch_whileWorking_isIgnored() =
        runTest(dispatcher) {
            val vm = model()
            vm.onToggle()
            vm.onDayChosen(today.plusDays(2))
            vm.onConfirm()
            // Not run yet: the first operation is under way, a second touch does nothing.
            assertTrue(vm.uiState.value.working)
            vm.onToggle()
            assertNull(vm.uiState.value.picker)
            advanceUntilIdle()
            assertEquals(1, control.sets.size)
        }
}
