package com.tomessa.miff.ui.timeshare

import com.tomessa.miff.data.timeshare.StoredPresenceLog
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.timeshare.PresenceLog
import com.tomessa.miff.domain.timeshare.SliceKind
import com.tomessa.miff.domain.timeshare.SummaryKind
import com.tomessa.miff.domain.timeshare.TimeShareRules
import com.tomessa.miff.domain.timeshare.TimeShareTracking
import com.tomessa.miff.domain.timeshare.TrackingHealth
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.testsupport.FailingPresenceLog
import com.tomessa.miff.testsupport.FakeTimeShareTracking
import com.tomessa.miff.testsupport.MemoryPresenceLogStorage
import com.tomessa.miff.testsupport.MemoryTimeShareWindowStore
import com.tomessa.miff.testsupport.testPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The card "Répartition de ton temps" seen from its view model (docs/tickets/profil-repartition-temps.md sections 5 and 7): what is shown for which Miff, which states, the window
 * chosen and kept. Everything is local: the double of the journal and of the tracking are all it knows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimeShareViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val hour = TimeShareRules.HOUR_MILLIS
    private val now = 1_800_000_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val uid = "uid1"
    private val log: PresenceLog = StoredPresenceLog(MemoryPresenceLogStorage(), { uid }, { now })
    private val windows = MemoryTimeShareWindowStore()
    private val tracking = FakeTimeShareTracking()
    private val places =
        listOf(
            testPlace("home", "Maison", category = PlaceCategory.HOME),
            testPlace("sport", "Salle de sport", category = PlaceCategory.SPORT),
            testPlace("risk1", "Le parc", type = PlaceType.RISK, category = null),
        )

    private fun viewModel(presence: PresenceLog = log) =
        TimeShareViewModel(presence, windows, tracking, nowMillis = { now }, dispatcher = dispatcher)

    /** 30 h at home, 10 h out, 8 h at the sport place, 6 h in the park: 54 h. */
    private fun fillAWeek(miffId: String = "miffA") {
        val start = now - 100 * hour
        log.record(miffId, PresenceValue.IN_TRUSTED, "home", start)
        log.record(miffId, PresenceValue.OUT, null, start + 30 * hour)
        log.record(miffId, PresenceValue.IN_TRUSTED, "sport", start + 40 * hour)
        log.record(miffId, PresenceValue.OUT, null, start + 48 * hour)
        log.record(miffId, PresenceValue.IN_RISK, "risk1", start + 50 * hour)
        log.record(miffId, PresenceValue.UNKNOWN, null, start + 56 * hour)
    }

    private fun TimeShareViewModel.shown() = state.value as TimeShareUiState.Shown

    private fun TimeShareViewModel.chart() = shown().body as TimeShareBody.Chart

    // --- Hidden, loading ---------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun withoutAMiffOrWithoutPlaces_theCardIsHidden_neverAnEmptyOne() {
        val vm = viewModel()
        vm.onInputs(null, places)
        dispatcher.scheduler.runCurrent()
        assertEquals(TimeShareUiState.Hidden, vm.state.value)
        vm.onInputs("miffA", null)
        dispatcher.scheduler.runCurrent()
        assertEquals(TimeShareUiState.Hidden, vm.state.value)
        vm.onInputs("miffA", emptyList())
        dispatcher.scheduler.runCurrent()
        assertEquals(TimeShareUiState.Hidden, vm.state.value)
    }

    @Test
    fun theFirstCardWaitsForTheLocalReading_thenShows() {
        val vm = viewModel()
        vm.onInputs("miffA", places)
        assertEquals(TimeShareUiState.Loading, vm.state.value)
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.state.value is TimeShareUiState.Shown)
    }

    @Test
    fun swipingToAnotherMiff_keepsTheCardOnScreenUntilTheNewOneIsReady_noFlash() {
        fillAWeek("miffA")
        val vm = viewModel()
        vm.onInputs("miffA", places)
        dispatcher.scheduler.runCurrent()
        assertEquals("miffA", vm.shown().miffId)
        vm.onInputs("miffB", places)
        // Between the two, the card of the first Miff is still there: never "Loading", never an empty state.
        assertEquals("miffA", vm.shown().miffId)
        dispatcher.scheduler.runCurrent()
        assertEquals("miffB", vm.shown().miffId)
        assertEquals(TimeShareBody.NotEnough, vm.shown().body)
    }

    // --- The states ---------------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun withNothingInTheJournal_itIsNotEnoughData_whateverTheWindow() {
        val vm = viewModel()
        vm.onInputs("miffA", places)
        dispatcher.scheduler.runCurrent()
        assertEquals(TimeShareBody.NotEnough, vm.shown().body)
        assertEquals(7, vm.shown().windowDays)
        assertEquals(TrackingHealth.ACTIVE, vm.shown().tracking.health)
    }

    @Test
    fun theKindOfNotEnoughData_followsWhatIsTrueAboutTheTracking() {
        assertEquals(NotEnoughKind.COMING_SOON, notEnoughKind(TrackingHealth.ACTIVE))
        assertEquals(NotEnoughKind.TRAVEL, notEnoughKind(TrackingHealth.TRAVEL))
        assertEquals(NotEnoughKind.SHARING_STOPPED, notEnoughKind(TrackingHealth.SHARING_STOPPED))
        assertEquals(NotEnoughKind.LOCATION_NEEDED, notEnoughKind(TrackingHealth.LOCATION_INSUFFICIENT))
        tracking.tracking = TimeShareTracking(TrackingHealth.TRAVEL)
        val vm = viewModel()
        vm.onInputs("miffA", places)
        dispatcher.scheduler.runCurrent()
        assertEquals(TrackingHealth.TRAVEL, vm.shown().tracking.health)
    }

    @Test
    fun withEnoughData_theLegendHasTheNamesAndTheCategoriesOfTheCurrentPlaces() {
        fillAWeek()
        val vm = viewModel()
        vm.onInputs("miffA", places)
        dispatcher.scheduler.runCurrent()
        val chart = vm.chart()
        assertEquals(
            listOf(SliceKind.PLACE, SliceKind.PLACE, SliceKind.DANGER, SliceKind.ELSEWHERE),
            chart.items.map {
                it.kind
            },
        )
        val named = chart.items.filter { it.kind == SliceKind.PLACE }
        assertEquals(listOf("Maison", "Salle de sport"), named.map { it.name })
        assertEquals(listOf(PlaceCategory.HOME, PlaceCategory.SPORT), named.map { it.category })
        assertEquals(listOf(0, 1), named.map { it.rank })
        assertEquals(100, chart.items.sumOf { it.percent })
        // The danger zone is one line, its place is in the detail.
        val danger = chart.items.first { it.kind == SliceKind.DANGER }
        assertEquals(listOf("Le parc"), danger.details.map { it.name })
        assertEquals(danger.percent, danger.details.sumOf { it.percent })
        // The sentence names the biggest place (a name of the current list), and the shares close the ring.
        assertEquals(SummaryKind.PLACE, chart.summary)
        assertEquals("Maison", chart.summaryPlaceName)
        assertEquals(1f, chart.items.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun aRenamedPlace_isShownWithItsNewName_theJournalHoldsNoName() {
        fillAWeek()
        val vm = viewModel()
        vm.onInputs("miffA", places)
        dispatcher.scheduler.runCurrent()
        val renamed =
            places.map {
                if (it.id ==
                    "home"
                ) {
                    testPlace("home", "Chez nous", category = PlaceCategory.HOME)
                } else {
                    it
                }
            }
        vm.onInputs("miffA", renamed)
        dispatcher.scheduler.runCurrent()
        assertEquals(
            "Chez nous",
            vm
                .chart()
                .items
                .first { it.kind == SliceKind.PLACE }
                .name,
        )
    }

    @Test
    fun aPlaceThatIsGone_isNotInTheLegend_andItsTimeIsNotElsewhere() {
        fillAWeek()
        val vm = viewModel()
        vm.onInputs("miffA", places.filter { it.id != "sport" })
        dispatcher.scheduler.runCurrent()
        val chart = vm.chart()
        assertEquals(listOf("Maison"), chart.items.filter { it.kind == SliceKind.PLACE }.map { it.name })
        // 30 h home, 10 h + 2 h out, 6 h danger: the 8 h of the sport place are in nothing.
        val elsewhere = chart.items.first { it.kind == SliceKind.ELSEWHERE }
        assertEquals(12.0 / 48.0, elsewhere.share.toDouble(), 0.001)
    }

    // --- The window ----------------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun theWindowIsSevenDaysByDefault_andTheChoiceIsKept() {
        fillAWeek()
        val vm = viewModel()
        vm.onInputs("miffA", places)
        dispatcher.scheduler.runCurrent()
        assertEquals(7, vm.shown().windowDays)
        vm.onWindowChosen(14)
        dispatcher.scheduler.runCurrent()
        assertEquals(14, vm.shown().windowDays)
        assertEquals(14, windows.days)
        // A new view model (the app was closed and opened again) starts from the choice kept.
        val next = viewModel()
        next.onInputs("miffA", places)
        dispatcher.scheduler.runCurrent()
        assertEquals(14, next.shown().windowDays)
    }

    @Test
    fun aWindowThatIsNotOneOfTheThree_isIgnored() {
        val vm = viewModel()
        vm.onInputs("miffA", places)
        dispatcher.scheduler.runCurrent()
        vm.onWindowChosen(9)
        dispatcher.scheduler.runCurrent()
        assertEquals(7, vm.shown().windowDays)
        assertEquals(null, windows.days)
    }

    @Test
    fun theWindowIsKeptWhenTheMiffChanges() {
        val vm = viewModel()
        vm.onInputs("miffA", places)
        dispatcher.scheduler.runCurrent()
        vm.onWindowChosen(30)
        dispatcher.scheduler.runCurrent()
        vm.onInputs("miffB", places)
        dispatcher.scheduler.runCurrent()
        assertEquals(30, vm.shown().windowDays)
    }

    // --- Changes of the journal, errors ------------------------------------------------------------------------------------------------------------------

    @Test
    fun whenTheJournalChanges_theChartIsWorkedOutAgain() {
        val vm = viewModel()
        vm.onInputs("miffA", places)
        dispatcher.scheduler.runCurrent()
        assertEquals(TimeShareBody.NotEnough, vm.shown().body)
        fillAWeek()
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.shown().body is TimeShareBody.Chart)
    }

    @Test
    fun clearingTheJournal_backsToNotEnoughData() {
        fillAWeek()
        val vm = viewModel()
        vm.onInputs("miffA", places)
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.shown().body is TimeShareBody.Chart)
        log.clear()
        dispatcher.scheduler.runCurrent()
        assertEquals(TimeShareBody.NotEnough, vm.shown().body)
    }

    @Test
    fun aJournalThatCannotBeRead_isACalmError_andRetryWorksOnceItCan() {
        val vm = viewModel(FailingPresenceLog())
        vm.onInputs("miffA", places)
        dispatcher.scheduler.runCurrent()
        assertEquals(TimeShareUiState.Failed, vm.state.value)
        vm.onRetry()
        dispatcher.scheduler.runCurrent()
        assertEquals(TimeShareUiState.Failed, vm.state.value)
    }

    @Test
    fun comingBackToTheFront_worksTheChartOutAgainWithTheClockOfNow() {
        fillAWeek()
        val vm = viewModel()
        vm.onInputs("miffA", places)
        dispatcher.scheduler.runCurrent()
        val before = vm.shown()
        vm.refresh()
        dispatcher.scheduler.runCurrent()
        assertEquals(before.miffId, vm.shown().miffId)
        assertTrue(vm.shown().body is TimeShareBody.Chart)
    }
}
