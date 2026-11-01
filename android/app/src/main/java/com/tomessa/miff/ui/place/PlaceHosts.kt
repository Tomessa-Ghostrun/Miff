package com.tomessa.miff.ui.place

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tomessa.miff.AppContainer
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.data.miff.MiffRepository
import com.tomessa.miff.domain.miff.MiffObservation
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.domain.place.Place
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textDisplayTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/*
 * SECURITY, for this file: the places (names, addresses) live in memory in this state only: never in a log or an event.
 */

/** The places of one Miff, read live, for the Profile tab (the Miff screen reads them with the rest of the Miff). */
data class MiffPlacesState(
    val loaded: Boolean = false,
    val places: List<Place> = emptyList(),
    val isCreator: Boolean = false,
) {
    override fun toString(): String = "MiffPlacesState(redacted)"
}

class MiffPlacesViewModel(
    private val miffId: String,
    private val repository: MiffRepository,
    private val currentUid: () -> String?,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MiffPlacesState())
    val uiState: StateFlow<MiffPlacesState> = mutableState.asStateFlow()

    /** Reads the Miff while the screen is on view (called inside `repeatOnLifecycle(STARTED)`). */
    suspend fun observe() {
        repository.observe(miffId).collect { observation ->
            when (observation) {
                is MiffObservation.Data -> {
                    val snapshot = observation.snapshot
                    mutableState.value =
                        MiffPlacesState(
                            loaded = true,
                            places = snapshot.places,
                            isCreator = snapshot.miff.ownerUid == currentUid(),
                        )
                }

                // A lost access is announced once, over everything, by the host of the tabs; here nothing of that Miff stays.
                MiffObservation.AccessLost -> {
                    mutableState.update { MiffPlacesState() }
                }

                // Loading, or a failure that is not a loss: what is shown stays as it is (a list that cannot be read is not shown).
                MiffObservation.Loading, MiffObservation.Failed -> {
                    Unit
                }
            }
        }
    }
}

/**
 * "Lieux de la Miff" on the Profile tab (mockup 17), for the Miff that the Feed shows. Nothing is drawn until the Miff has been
 * read (no empty flash, no invented place).
 */
@Composable
fun ProfilePlacesSection(
    container: AppContainer,
    directory: DirectoryState.Ready,
    scopeKey: String,
    onOpenPlace: (miffId: String, placeId: String) -> Unit,
    onAddPlace: () -> Unit,
    onOpenOffer: (PaywallReason) -> Unit,
) {
    val active = directory.active ?: return
    val session = container.sessionManager
    val viewModel: MiffPlacesViewModel =
        viewModel(
            key = "profile-places-${active.id}-$scopeKey",
            factory =
                viewModelFactory {
                    initializer { MiffPlacesViewModel(active.id, container.miffRepository, { session.currentUid }) }
                },
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.observe() }
    }
    val notice by container.placeNotices.notice.collectAsStateWithLifecycle()
    ConsumeNoticeOnLeave(container.placeNotices::consume)
    if (!state.loaded) return
    val ui = PlaceListUi(state.places, state.isCreator, directory.premium, notice?.name)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            text =
                if (directory.miffs.size > 1) {
                    stringResource(R.string.place_list_title_named, active.name)
                } else {
                    stringResource(R.string.place_list_title)
                },
            style = textDisplayTitle,
            color = colorTextForm,
            modifier = Modifier.semantics { heading() },
        )
        PlaceRows(
            ui = ui,
            onOpen = { place -> onOpenPlace(active.id, place.id) },
            onAdd = { if (ui.atFreeLimit) onOpenOffer(PaywallReason.LIMIT) else onAddPlace() },
        )
    }
}
