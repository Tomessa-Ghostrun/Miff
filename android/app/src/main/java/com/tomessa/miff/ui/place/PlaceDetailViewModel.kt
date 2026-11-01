package com.tomessa.miff.ui.place

import androidx.lifecycle.ViewModel
import com.tomessa.miff.data.miff.MiffRepository
import com.tomessa.miff.domain.miff.MiffObservation
import com.tomessa.miff.domain.place.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/*
 * SECURITY, for this file: the place (name, address, coordinates) and the names of the members live in memory in this state only:
 * never in a log, an analytics event (none is sent from this screen) or a crash report.
 */

enum class PlaceDetailStatus {
    LOADING,
    READY,

    /** The place was deleted (from another phone) while it was open: "Ce lieu n'existe plus." and back to the list. */
    GONE,

    /** The Miff could not be read and nothing is known yet. */
    UNAVAILABLE,

    /** The Miff is no longer the person's: the single message of a lost access is drawn over everything by the host. */
    ACCESS_LOST,
}

data class PlaceDetailState(
    val status: PlaceDetailStatus = PlaceDetailStatus.LOADING,
    val place: Place? = null,
    val miffName: String = "",
    /** The first name and last name of the creator of the place, as the card of the Miff gives them; empty when unknown. */
    val creatorName: String = "",
    val creatorIsMe: Boolean = false,
    /** Only the creator of the Miff can change the place (criterion PL1). */
    val canEdit: Boolean = false,
    val fromCache: Boolean = false,
) {
    override fun toString(): String = "PlaceDetailState(redacted)"
}

/**
 * The detail of one place (design 16): read live from the Miff it was opened from, so that a change made on another phone shows
 * and a deletion is noticed. Nothing is written here; "Modifier" opens the form.
 */
class PlaceDetailViewModel(
    private val miffId: String,
    private val placeId: String,
    private val repository: MiffRepository,
    private val currentUid: () -> String?,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PlaceDetailState())
    val uiState: StateFlow<PlaceDetailState> = mutableState.asStateFlow()

    /** Reads the Miff while the screen is on view (called inside `repeatOnLifecycle(STARTED)`: cancelled in the background). */
    suspend fun observe() {
        repository.observe(miffId).collect { observation ->
            when (observation) {
                MiffObservation.Loading -> {
                    Unit
                }

                MiffObservation.AccessLost -> {
                    mutableState.update { it.copy(status = PlaceDetailStatus.ACCESS_LOST) }
                }

                MiffObservation.Failed -> {
                    if (mutableState.value.status != PlaceDetailStatus.READY) {
                        mutableState.update { it.copy(status = PlaceDetailStatus.UNAVAILABLE) }
                    }
                }

                is MiffObservation.Data -> {
                    apply(observation)
                }
            }
        }
    }

    private fun apply(observation: MiffObservation.Data) {
        val snapshot = observation.snapshot
        val place = snapshot.places.firstOrNull { it.id == placeId }
        if (place == null) {
            // "Not in the cache yet" is not "deleted": only an answer of the server can say the place is gone.
            mutableState.update {
                if (snapshot.fromCache) {
                    it.copy(
                        status = PlaceDetailStatus.LOADING,
                    )
                } else {
                    it.copy(status = PlaceDetailStatus.GONE)
                }
            }
            return
        }
        val uid = currentUid().orEmpty()
        val creator = snapshot.members.firstOrNull { it.uid == place.createdBy }
        mutableState.value =
            PlaceDetailState(
                status = PlaceDetailStatus.READY,
                place = place,
                miffName = snapshot.miff.name,
                creatorName = creator?.displayName.orEmpty(),
                creatorIsMe = place.createdBy == uid,
                canEdit = snapshot.miff.ownerUid == uid,
                fromCache = snapshot.fromCache,
            )
    }
}
