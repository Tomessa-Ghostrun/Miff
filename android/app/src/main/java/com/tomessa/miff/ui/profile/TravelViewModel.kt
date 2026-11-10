package com.tomessa.miff.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.tracking.TravelControl
import com.tomessa.miff.data.tracking.TravelResult
import com.tomessa.miff.domain.travel.TravelChoice
import com.tomessa.miff.domain.travel.TravelPeriod
import com.tomessa.miff.domain.travel.TravelPlan
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/*
 * MODE VOYAGE, the card of the Profile (VOY-2A, docs/tickets/mode-voyage.md section 7). Open to EVERY account (decision of the PM, 2026-11-10): no plan is read, no offer
 * is shown. Nothing here is logged or sent as an event; the trip is written by the engine ([TravelControl]) and nowhere else.
 */

/** Why the date picker is open: a trip begins (then the person is asked to confirm), or the end of a trip in progress moves (no confirmation: reversible). */
enum class TravelPickerPurpose { START, CHANGE }

class TravelPickerState(
    val purpose: TravelPickerPurpose,
    val initialDay: LocalDate,
    val minDay: LocalDate,
    val maxDay: LocalDate,
)

/** The calm sentences under the card. */
enum class TravelMessage {
    /** The tracking resumes ("Le suivi reprend."). */
    RESUMED,

    /** Accepted here, the loved ones will see it once online. */
    OFFLINE,

    /** The server did not take it: nothing changed. */
    REFUSED,

    /** The new end of a trip in progress was not taken: the end that was there stays. */
    UPDATE_REFUSED,
}

data class TravelUiState(
    /** The trip in progress, or null. */
    val period: TravelPeriod? = null,
    /** An operation is under way: the switch cannot be touched twice. */
    val working: Boolean = false,
    val picker: TravelPickerState? = null,
    /** The trip waiting for the confirmation sheet. */
    val confirm: TravelPeriod? = null,
    val message: TravelMessage? = null,
)

class TravelViewModel(
    private val control: TravelControl,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {
    private val mutable = MutableStateFlow(TravelUiState())
    val uiState: StateFlow<TravelUiState> = mutable.asStateFlow()

    /** The calm sentences that only inform go away by themselves; one that says something did not work stays until the next gesture. */
    private var clearJob: Job? = null

    init {
        control.reload()
        mutable.update { it.copy(period = control.travel.value) }
        viewModelScope.launch { control.travel.collect { period -> mutable.update { it.copy(period = period) } } }
    }

    /** The person touched the switch. Off to on: the date picker; on to off: the trip ends at once (no confirmation, the tracking resumes). */
    fun onToggle() {
        val state = mutable.value
        if (state.working) return
        clearJob?.cancel()
        if (state.period == null) {
            openPicker(TravelPickerPurpose.START)
        } else {
            mutable.update { it.copy(working = true, message = null) }
            viewModelScope.launch {
                control.end()
                mutable.update { it.copy(working = false, message = TravelMessage.RESUMED) }
                clearLater(TravelMessage.RESUMED)
            }
        }
    }

    /** The line "Date de fin" of a trip in progress. */
    fun onChangeDate() {
        if (mutable.value.working || mutable.value.period == null) return
        openPicker(TravelPickerPurpose.CHANGE)
    }

    private fun openPicker(purpose: TravelPickerPurpose) {
        val now = today()
        val current = mutable.value.period?.let { TravelPlan.parseDay(it.untilDay) }
        val initial = current?.coerceIn(TravelPlan.minDay(now), TravelPlan.maxDay(now)) ?: TravelPlan.defaultDay(now)
        mutable.update {
            it.copy(
                picker = TravelPickerState(purpose, initial, TravelPlan.minDay(now), TravelPlan.maxDay(now)),
                message = null,
            )
        }
    }

    fun onPickerDismissed() = mutable.update { it.copy(picker = null) }

    /** A day was chosen in the picker. */
    fun onDayChosen(day: LocalDate) {
        val picker = mutable.value.picker ?: return
        val choice = TravelPlan.choose(day, today(), zone())
        // The picker already refuses these days: a day outside the bounds (a clock that moved meanwhile) changes nothing.
        val period = (choice as? TravelChoice.Valid)?.period ?: return
        mutable.update { it.copy(picker = null) }
        when (picker.purpose) {
            TravelPickerPurpose.START -> mutable.update { it.copy(confirm = period) }
            TravelPickerPurpose.CHANGE -> apply(period, change = true)
        }
    }

    fun onConfirm() {
        val period = mutable.value.confirm ?: return
        mutable.update { it.copy(confirm = null) }
        apply(period, change = false)
    }

    /** "Annuler" on the sheet: nothing was written, nothing changes. */
    fun onConfirmDismissed() = mutable.update { it.copy(confirm = null) }

    fun onMessageShown() = mutable.update { it.copy(message = null) }

    private fun apply(
        period: TravelPeriod,
        change: Boolean,
    ) {
        if (mutable.value.working) return
        mutable.update { it.copy(working = true, message = null) }
        clearJob?.cancel()
        viewModelScope.launch {
            val message =
                when (control.set(period)) {
                    TravelResult.Done -> {
                        null
                    }

                    TravelResult.DoneOffline -> {
                        TravelMessage.OFFLINE
                    }

                    TravelResult.Refused, TravelResult.Unavailable -> {
                        if (change) TravelMessage.UPDATE_REFUSED else TravelMessage.REFUSED
                    }
                }
            mutable.update { it.copy(working = false, message = message) }
            if (message == TravelMessage.OFFLINE) clearLater(message)
        }
    }

    /** Removes [message] after a few seconds, if it is still the one shown (a newer gesture has already replaced or cleared it otherwise). */
    private fun clearLater(message: TravelMessage) {
        clearJob?.cancel()
        clearJob =
            viewModelScope.launch {
                delay(MESSAGE_SHOWN_MILLIS)
                mutable.update { if (it.message == message) it.copy(message = null) else it }
            }
    }

    companion object {
        /** How long a sentence that only informs stays under the card. */
        const val MESSAGE_SHOWN_MILLIS = 6_000L
    }
}
