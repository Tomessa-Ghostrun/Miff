package com.tomessa.miff.ui.timeshare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.timeshare.TimeShareTrackingSource
import com.tomessa.miff.data.timeshare.TimeShareWindowStore
import com.tomessa.miff.domain.place.Place
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.domain.timeshare.PlaceRef
import com.tomessa.miff.domain.timeshare.PresenceLog
import com.tomessa.miff.domain.timeshare.SliceKind
import com.tomessa.miff.domain.timeshare.TimeShare
import com.tomessa.miff.domain.timeshare.TimeShareResult
import com.tomessa.miff.domain.timeshare.TimeShareRules
import com.tomessa.miff.domain.timeshare.TimeShareTracking
import com.tomessa.miff.domain.timeshare.TrackingHealth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * PRIVACY and COST, for this file (docs/tickets/profil-repartition-temps.md): everything here is LOCAL. The chart is worked out from the journal of the phone and from the
 * places the Profile ALREADY read (no Firestore reading or writing of its own, no listener); the names of the places stay in memory for this view model; nothing is logged,
 * sent as an event, or kept after the account ends (the choice of the window is erased by the teardown of the session).
 */

/** One line of the legend (and one slice of the ring). The names come from the CURRENT list of places, never from the journal. */
class LegendItem(
    val kind: SliceKind,
    /** The name of the place (a trusted place only). */
    val name: String?,
    /** The category of the place, for its emoji (null: the neutral pin, as in the list of places). */
    val category: PlaceCategory?,
    /** The number of places grouped ("autres lieux") or listed under the danger zone. */
    val count: Int,
    val percent: Int,
    val tiny: Boolean,
    /** The index of the colour of the place in the palette (the rank of the place); 0 for the other kinds. */
    val rank: Int,
    val details: List<DetailItem>,
    /** The real share of the whole, 0 to 1, for the length of the slice (never rounded: the ring must close). */
    val share: Float,
) {
    override fun toString(): String = "LegendItem(redacted)"
}

/** One place under the danger zone. */
class DetailItem(
    val name: String,
    val category: PlaceCategory?,
    val percent: Int,
    val tiny: Boolean,
) {
    override fun toString(): String = "DetailItem(redacted)"
}

sealed interface TimeShareBody {
    /** The history goes back less than 3 days (or nothing was measured): no chart, no selector. */
    data class NotEnough(
        /** The days left before the chart can be shown, 1 to 3 ("Ton graphique arrive dans X jours"). */
        val daysLeft: Int,
    ) : TimeShareBody

    class Chart(
        val items: List<LegendItem>,
        /** The share in the trusted places (centre of the ring and sentence under it). */
        val centerPercent: Int,
    ) : TimeShareBody {
        override fun toString(): String = "Chart(redacted)"
    }
}

sealed interface TimeShareUiState {
    /** Nothing is drawn: no places yet (not read, or none), or no Miff. */
    data object Hidden : TimeShareUiState

    /** The local reading is under way (the card shows nothing for the first 300 ms, then a quiet placeholder). */
    data object Loading : TimeShareUiState

    /** The local reading failed: a calm sentence and "Réessayer"; the rest of the Profile is not affected. */
    data object Failed : TimeShareUiState

    class Shown(
        val miffId: String,
        val windowDays: Int,
        val body: TimeShareBody,
        val tracking: TimeShareTracking,
    ) : TimeShareUiState {
        override fun toString(): String = "Shown(redacted)"
    }
}

class TimeShareViewModel(
    private val log: PresenceLog,
    private val windows: TimeShareWindowStore,
    private val tracking: TimeShareTrackingSource,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutableState = MutableStateFlow<TimeShareUiState>(TimeShareUiState.Hidden)
    val state: StateFlow<TimeShareUiState> = mutableState.asStateFlow()

    private var miffId: String? = null
    private var places: List<Place>? = null
    private var windowDays = TimeShareRules.windowOrDefault(windows.read())
    private var job: Job? = null

    init {
        // The journal changed (the engine recorded a change of presence while the Profile is open): worked out again, no flash.
        viewModelScope.launch { log.revision.drop(1).collect { recompute(showLoading = false) } }
    }

    /**
     * The Miff of the carousel and its places, as the Profile read them ([places] null: not read yet or not readable; empty: none). Called at each composition that
     * changes one of them; a call that changes nothing does nothing.
     */
    fun onInputs(
        miffId: String?,
        places: List<Place>?,
    ) {
        val sameMiff = this.miffId == miffId
        val samePlaces = sameMiff && samePlaces(this.places, places)
        this.miffId = miffId
        this.places = places
        if (samePlaces) return
        recompute(showLoading = !sameMiff)
    }

    /** The Profile came back to the front: the chart is worked out again with the clock of now (the window slides). */
    fun refresh() = recompute(showLoading = false)

    fun onWindowChosen(days: Int) {
        if (days !in TimeShareRules.WINDOWS_DAYS || days == windowDays) return
        windowDays = days
        windows.write(days)
        recompute(showLoading = false)
    }

    /** "Réessayer" after a failed local reading. */
    fun onRetry() = recompute(showLoading = true)

    private fun samePlaces(
        a: List<Place>?,
        b: List<Place>?,
    ): Boolean {
        if (a == null || b == null) return a == null && b == null
        if (a.size != b.size) return false
        return a.indices.all { i ->
            a[i].id == b[i].id && a[i].type == b[i].type && a[i].name == b[i].name && a[i].category == b[i].category
        }
    }

    private fun recompute(showLoading: Boolean) {
        job?.cancel()
        val id = miffId
        val list = places
        if (id == null || list.isNullOrEmpty()) {
            mutableState.value = TimeShareUiState.Hidden
            return
        }
        // A card already on screen stays until the new one is ready (no flash, no empty state in between); only the first one waits for the reading.
        if (showLoading && mutableState.value !is TimeShareUiState.Shown) mutableState.value = TimeShareUiState.Loading
        val days = windowDays
        job =
            viewModelScope.launch {
                val next =
                    try {
                        withContext(dispatcher) { work(id, list, days) }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        TimeShareUiState.Failed
                    }
                mutableState.value = next
            }
    }

    private fun work(
        miffId: String,
        places: List<Place>,
        days: Int,
    ): TimeShareUiState {
        val result =
            TimeShare.compute(
                facts = log.facts(miffId),
                places = places.map { PlaceRef(it.id, it.type) },
                windowDays = days,
                nowMillis = nowMillis(),
                startedAtMillis = log.startedAt(miffId),
            )
        val status = tracking.of(miffId)
        val body =
            when (result) {
                is TimeShareResult.NotEnoughData -> TimeShareBody.NotEnough(result.daysLeft)
                is TimeShareResult.Ready -> chart(result, places)
            }
        return TimeShareUiState.Shown(miffId, days, body, status)
    }

    private fun chart(
        result: TimeShareResult.Ready,
        places: List<Place>,
    ): TimeShareBody.Chart {
        val byId = places.associateBy { it.id }
        var rank = 0
        val items =
            result.slices.mapNotNull { slice ->
                val share = slice.millis.toFloat() / result.measuredMillis.toFloat()
                when (slice.kind) {
                    SliceKind.PLACE -> {
                        val place = byId[slice.placeId] ?: return@mapNotNull null
                        LegendItem(
                            slice.kind,
                            place.name,
                            place.category,
                            0,
                            slice.percent,
                            slice.tiny,
                            rank++,
                            emptyList(),
                            share,
                        )
                    }

                    SliceKind.OTHER_PLACES -> {
                        LegendItem(
                            slice.kind,
                            null,
                            null,
                            slice.count,
                            slice.percent,
                            slice.tiny,
                            0,
                            emptyList(),
                            share,
                        )
                    }

                    SliceKind.DANGER -> {
                        val details =
                            slice.details.mapNotNull { detail ->
                                byId[detail.placeId]?.let {
                                    DetailItem(
                                        it.name,
                                        it.category,
                                        detail.percent,
                                        detail.tiny,
                                    )
                                }
                            }
                        LegendItem(slice.kind, null, null, slice.count, slice.percent, slice.tiny, 0, details, share)
                    }

                    SliceKind.ELSEWHERE -> {
                        LegendItem(slice.kind, null, null, 0, slice.percent, slice.tiny, 0, emptyList(), share)
                    }
                }
            }
        return TimeShareBody.Chart(items, result.centerPercent)
    }
}

/** The kinds of "not enough data" text, in the order of precedence of section 7 (the state of the tracking decides which words are true). */
enum class NotEnoughKind { COMING_SOON, TRAVEL, SHARING_STOPPED, LOCATION_NEEDED }

fun notEnoughKind(health: TrackingHealth): NotEnoughKind =
    when (health) {
        TrackingHealth.ACTIVE -> NotEnoughKind.COMING_SOON
        TrackingHealth.TRAVEL -> NotEnoughKind.TRAVEL
        TrackingHealth.SHARING_STOPPED -> NotEnoughKind.SHARING_STOPPED
        TrackingHealth.LOCATION_INSUFFICIENT -> NotEnoughKind.LOCATION_NEEDED
    }
