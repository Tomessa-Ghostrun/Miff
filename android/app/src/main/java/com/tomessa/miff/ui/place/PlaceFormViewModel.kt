package com.tomessa.miff.ui.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.place.PlaceContext
import com.tomessa.miff.data.place.PlaceContextLoad
import com.tomessa.miff.data.place.PlaceNotices
import com.tomessa.miff.data.place.PlaceRepository
import com.tomessa.miff.data.place.PlaceWriteResult
import com.tomessa.miff.domain.miff.MiffName
import com.tomessa.miff.domain.place.AddressSearch
import com.tomessa.miff.domain.place.AddressSearchResult
import com.tomessa.miff.domain.place.AddressSuggestion
import com.tomessa.miff.domain.place.DefaultMiffChoice
import com.tomessa.miff.domain.place.OwnedMiff
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.domain.place.PlaceDraft
import com.tomessa.miff.domain.place.PlaceGroup
import com.tomessa.miff.domain.place.PlaceLimits
import com.tomessa.miff.domain.place.PlaceName
import com.tomessa.miff.domain.place.PlaceQuota
import com.tomessa.miff.domain.place.PlaceRadius
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.ui.miff.ActionMessage
import com.tomessa.miff.ui.miff.messageFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
 * SECURITY, for this file: the name, the address (typed and chosen), the coordinates and the Miffs of a place live in memory in
 * this state only: never in a log, an analytics event (no event carries a place: the only ones are paywall_*, with a fixed
 * trigger), a saved instance state or a crash report. The text typed in the address field goes to the search service and nowhere
 * else (see AddressSearchClients). Every toString() shows nothing.
 */

enum class PlaceFormMode { CREATE, EDIT }

/** The list under the address field. */
sealed interface SuggestionsState {
    data object Idle : SuggestionsState

    data object Loading : SuggestionsState

    class Found(
        val items: List<AddressSuggestion>,
    ) : SuggestionsState {
        override fun toString(): String = "Found(redacted)"
    }

    data object NoResults : SuggestionsState

    data object Unavailable : SuggestionsState
}

/** What the form says under the button (calm sentences, never a code). */
enum class PlaceMessage { OFFLINE, GENERIC, MIFF_GONE }

/** How the form ends: the host leaves the screen (and shows what must be shown). */
enum class PlaceFormEnd { SAVED, DELETED, PLACE_GONE }

data class PlaceFormState(
    val mode: PlaceFormMode = PlaceFormMode.CREATE,
    /** The context (Miff+, my Miffs, my places) is being read. */
    val loading: Boolean = true,
    val loadFailed: PlaceMessage? = null,
    val premium: Boolean = false,
    val name: String = "",
    val nameLimitReached: Boolean = false,
    /** "2 caractères minimum", shown after a tap on "Enregistrer" with one character (the button works from 1). */
    val nameTooShort: Boolean = false,
    val owned: List<OwnedMiff> = emptyList(),
    val lastJoinedId: String? = null,
    val selectedMiffIds: List<String> = emptyList(),
    val miffSheetOpen: Boolean = false,
    /** What the address field shows: what is typed, or the label of the address chosen. */
    val addressText: String = "",
    val chosen: AddressSuggestion? = null,
    val suggestions: SuggestionsState = SuggestionsState.Idle,
    val radiusM: Int = PlaceLimits.RADIUS_DEFAULT,
    val type: PlaceType = PlaceType.TRUSTED,
    val category: PlaceCategory? = PlaceCategory.DEFAULT,
    val saving: Boolean = false,
    val message: PlaceMessage? = null,
    val deleteDialogOpen: Boolean = false,
    val deleting: Boolean = false,
    val deleteMessage: ActionMessage? = null,
    /** The form is done: the host leaves the screen. */
    val end: PlaceFormEnd? = null,
    /** Miff+ is needed: the host opens the screen of the offer (and, for the limit, closes the form). */
    val paywall: PaywallReason? = null,
    /** Edit mode: what the form was opened with, to know whether anything changed. */
    val initial: Snapshot? = null,
) {
    /** The values compared to know whether an edit changed something. */
    data class Snapshot(
        val name: String,
        val miffIds: Set<String>,
        val label: String,
        val lat: Double,
        val lng: Double,
        val radiusM: Int,
        val type: PlaceType,
        val category: PlaceCategory?,
    ) {
        override fun toString(): String = "Snapshot(redacted)"
    }

    val snapshot: Snapshot
        get() =
            Snapshot(
                name = PlaceName.stored(name),
                miffIds = selectedMiffIds.toSet(),
                label = chosen?.label.orEmpty(),
                lat = chosen?.lat ?: 0.0,
                lng = chosen?.lng ?: 0.0,
                radiusM = radiusM,
                type = type,
                category = category,
            )

    val changed: Boolean get() = initial == null || initial != snapshot

    val busy: Boolean get() = saving || deleting

    /** "Enregistrer" is active from ONE character of name AND a suggestion chosen (decision Q10), and (edit) something changed. */
    val canSave: Boolean
        get() =
            !loading && loadFailed == null && !busy && PlaceName.hasSomething(name) && chosen != null &&
                selectedMiffIds.isNotEmpty() && category != null && changed

    /** One Miff only: no picker, the Miff is shown (design 05E). */
    val singleMiff: Boolean get() = owned.size <= 1

    val canEditAddress: Boolean get() = !loading && loadFailed == null && !busy

    override fun toString(): String = "PlaceFormState(redacted)"
}

/**
 * The form "Nouveau lieu" / "Modifier le lieu" (ticket LOC-1, 6.1 to 6.4; design fiches nouveau-lieu.md and modifier-lieu.md).
 *
 * Nothing is written before the person taps "Enregistrer", nothing is shown as saved before the server confirmed, nothing is
 * queued offline (criteria PL9, HOR2). The limits of the free plan are applied BEFORE writing; the rules are only the safety net.
 */
class PlaceFormViewModel(
    /** Null: a new place. Else the identifier of the place to edit. */
    private val placeId: String?,
    private val repository: PlaceRepository,
    private val search: AddressSearch,
    private val network: NetworkMonitor,
    private val currentUid: () -> String?,
    private val notices: PlaceNotices,
    /** "fr" or "en": the language of the labels asked of the search service. */
    private val language: () -> String,
    private val debounceMillis: Long = PlaceLimits.SUGGESTION_DEBOUNCE_MS,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(PlaceFormState(mode = if (placeId == null) PlaceFormMode.CREATE else PlaceFormMode.EDIT))
    val uiState: StateFlow<PlaceFormState> = mutableState.asStateFlow()

    private var context: PlaceContext? = null
    private var original: PlaceGroup? = null
    private var searchJob: Job? = null
    private var loadJob: Job? = null

    init {
        load()
    }

    // --- Loading -------------------------------------------------------------------------------------------------

    /** "Réessayer" after a reading that failed. */
    fun retry() {
        if (!mutableState.value.loading && mutableState.value.loadFailed == null) return
        mutableState.update { it.copy(loading = true, loadFailed = null) }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                val uid = currentUid()
                if (uid == null || !network.isOnline()) {
                    val message = if (uid == null) PlaceMessage.GENERIC else PlaceMessage.OFFLINE
                    mutableState.update { it.copy(loading = false, loadFailed = message) }
                    return@launch
                }
                when (val load = repository.prepare(uid)) {
                    is PlaceContextLoad.Failed -> {
                        mutableState.update {
                            it.copy(
                                loading = false,
                                loadFailed = messageFor(load.failure).toPlaceMessage(),
                            )
                        }
                    }

                    is PlaceContextLoad.Loaded -> {
                        context = load.context
                        open(load.context)
                    }
                }
            }
    }

    private fun open(loaded: PlaceContext) {
        if (loaded.ownedMiffs.isEmpty()) {
            // Only the creator of a Miff comes here: nothing sensible to show otherwise.
            mutableState.update { it.copy(loading = false, loadFailed = PlaceMessage.GENERIC) }
            return
        }
        if (placeId == null) {
            if (PlaceQuota.isAtFreeLimit(loaded.premium, loaded.distinctPlaces)) {
                // The server says 2 places already (the list was behind): the offer, and nothing is written.
                mutableState.update { it.copy(loading = false, premium = false, paywall = PaywallReason.LIMIT) }
                return
            }
            val default = DefaultMiffChoice.of(loaded.ownedMiffs, loaded.lastJoinedMiffId)
            mutableState.update {
                it.copy(
                    loading = false,
                    premium = loaded.premium,
                    owned = loaded.ownedMiffs,
                    lastJoinedId = loaded.lastJoinedMiffId,
                    selectedMiffIds = listOfNotNull(default),
                )
            }
            return
        }
        val group = loaded.groups.firstOrNull { it.id == placeId }
        if (group == null) {
            mutableState.update { it.copy(loading = false, end = PlaceFormEnd.PLACE_GONE) }
            return
        }
        original = group
        val place = group.main
        val address = place.address.orEmpty()
        val chosen =
            AddressSuggestion(title = address, subtitle = "", label = address, lat = place.lat, lng = place.lng)
        val miffs = loaded.ownedMiffs.map { it.id }.filter { it in group.miffIds }
        val filled =
            PlaceFormState(
                mode = PlaceFormMode.EDIT,
                loading = false,
                premium = loaded.premium,
                name = place.name,
                owned = loaded.ownedMiffs,
                lastJoinedId = loaded.lastJoinedMiffId,
                selectedMiffIds = miffs,
                addressText = address,
                chosen = chosen,
                radiusM = place.radiusM,
                type = place.type,
                // A place written before the categories existed has none: the person must choose one to save.
                category = place.category,
            )
        mutableState.value = filled.copy(initial = filled.snapshot)
    }

    // --- Name, Miffs -----------------------------------------------------------------------------------------------

    fun onNameChanged(typed: String) {
        if (mutableState.value.busy) return
        val limited = PlaceName.limit(typed)
        mutableState.update {
            it.copy(name = limited.text, nameLimitReached = limited.limitReached, nameTooShort = false, message = null)
        }
    }

    fun onMiffSheetOpened() {
        if (mutableState.value.canEditAddress && !mutableState.value.singleMiff) {
            mutableState.update { it.copy(miffSheetOpen = true) }
        }
    }

    fun onMiffSheetClosed() {
        mutableState.update { it.copy(miffSheetOpen = false) }
    }

    /** Ticks or unticks a Miff; at 5 ticked, the others cannot be ticked (the rules read 20 documents per lot at most). */
    fun onMiffToggled(miffId: String) {
        val state = mutableState.value
        if (state.busy || state.owned.none { it.id == miffId }) return
        val now = state.selectedMiffIds
        val next =
            when {
                miffId in now -> now - miffId
                now.size >= PlaceLimits.MAX_MIFFS -> now
                else -> now + miffId
            }
        // Always in the order of the list, so that the chips do not jump around.
        val ordered = state.owned.map { it.id }.filter { it in next }
        mutableState.update { it.copy(selectedMiffIds = ordered, message = null) }
    }

    // --- Address -----------------------------------------------------------------------------------------------------

    /** Typing changes the text; typing after an address was chosen erases the choice (the button and the map wait again). */
    fun onAddressChanged(typed: String) {
        val state = mutableState.value
        if (!state.canEditAddress) return
        val text = MiffName.limit(typed, ADDRESS_INPUT_MAX).text
        val keepChoice = state.chosen != null && text == state.chosen.label
        mutableState.update {
            it.copy(
                addressText = text,
                chosen = if (keepChoice) it.chosen else null,
                suggestions = if (keepChoice) SuggestionsState.Idle else it.suggestions,
                message = null,
            )
        }
        if (!keepChoice) startSearch(text)
    }

    fun onSuggestionChosen(suggestion: AddressSuggestion) {
        if (!mutableState.value.canEditAddress) return
        searchJob?.cancel()
        mutableState.update {
            it.copy(
                addressText = suggestion.label,
                chosen = suggestion,
                suggestions = SuggestionsState.Idle,
                message = null,
            )
        }
    }

    /** The cross of the field: start again. */
    fun onAddressCleared() {
        if (!mutableState.value.canEditAddress) return
        searchJob?.cancel()
        mutableState.update { it.copy(addressText = "", chosen = null, suggestions = SuggestionsState.Idle) }
    }

    /** "Réessayer" under "La recherche d'adresse n'est pas disponible". */
    fun onRetrySearch() {
        val state = mutableState.value
        if (state.chosen == null) startSearch(state.addressText)
    }

    /**
     * From 3 characters, 300 ms after the last key, the previous request is cancelled when a new letter comes (criterion ADR1); the
     * same text typed again is answered by the session cache (see CachedAddressSearch). The text goes nowhere but to the service.
     */
    private fun startSearch(text: String) {
        searchJob?.cancel()
        val query = text.trim()
        if (query.length < PlaceLimits.SUGGESTION_MIN_CHARS) {
            mutableState.update { it.copy(suggestions = SuggestionsState.Idle) }
            return
        }
        mutableState.update { it.copy(suggestions = SuggestionsState.Loading) }
        searchJob =
            viewModelScope.launch {
                delay(debounceMillis)
                val answer =
                    if (network.isOnline()) search.search(query, language()) else AddressSearchResult.Unavailable
                val next =
                    when (answer) {
                        is AddressSearchResult.Found -> SuggestionsState.Found(answer.suggestions)
                        AddressSearchResult.NoResults -> SuggestionsState.NoResults
                        AddressSearchResult.Unavailable -> SuggestionsState.Unavailable
                    }
                mutableState.update {
                    if (it.chosen == null &&
                        it.addressText.trim() == query
                    ) {
                        it.copy(suggestions = next)
                    } else {
                        it
                    }
                }
            }
    }

    // --- Radius, type, category -------------------------------------------------------------------------------------------

    fun onRadiusChanged(raw: Float) {
        if (mutableState.value.busy || mutableState.value.chosen == null) return
        mutableState.update { it.copy(radiusM = PlaceRadius.snap(raw)) }
    }

    /** A free account that touches "Zone de danger": the offer opens and the choice stays "Zone de confiance" (criterion LIM3). */
    fun onTypeSelected(type: PlaceType) {
        val state = mutableState.value
        if (state.busy || state.loading || state.loadFailed != null || type == state.type) return
        val reason = PlaceQuota.forTypeChange(state.premium, state.type, type)
        if (reason != null) {
            mutableState.update { it.copy(paywall = reason) }
            return
        }
        mutableState.update { it.copy(type = type) }
    }

    fun onCategorySelected(category: PlaceCategory) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(category = category) }
    }

    // --- Save ------------------------------------------------------------------------------------------------------

    fun onSave() {
        val state = mutableState.value
        if (!state.canSave) return
        // From 1 character the button works; below 2 the sentence of the ticket is shown and nothing is written (PL2).
        if (!PlaceName.isValid(state.name)) {
            mutableState.update { it.copy(nameTooShort = true) }
            return
        }
        // Nothing to try without a network: the answer is immediate and nothing is left waiting (PL9).
        if (!network.isOnline()) {
            mutableState.update { it.copy(message = PlaceMessage.OFFLINE) }
            return
        }
        val chosen = state.chosen ?: return
        val category = state.category ?: return
        val uid = currentUid()
        val loaded = context
        if (uid == null || loaded == null) {
            mutableState.update { it.copy(message = PlaceMessage.GENERIC) }
            return
        }
        val draft =
            PlaceDraft(
                name = state.name,
                miffIds = state.selectedMiffIds,
                address = chosen.label,
                lat = chosen.lat,
                lng = chosen.lng,
                radiusM = state.radiusM,
                type = state.type,
                category = category,
            )
        if (draft.problem != null) {
            mutableState.update { it.copy(message = PlaceMessage.GENERIC) }
            return
        }
        mutableState.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            val result =
                original?.let { repository.update(uid, it, draft, loaded) } ?: repository.create(uid, draft, loaded)
            when (result) {
                is PlaceWriteResult.Done -> {
                    notices.saved(PlaceName.stored(state.name))
                    mutableState.update { it.copy(saving = false, end = PlaceFormEnd.SAVED) }
                }

                is PlaceWriteResult.NeedsPremium -> {
                    mutableState.update { it.copy(saving = false, paywall = result.reason) }
                }

                PlaceWriteResult.MiffUnavailable -> {
                    mutableState.update { it.copy(saving = false, message = PlaceMessage.MIFF_GONE) }
                }

                PlaceWriteResult.PlaceGone -> {
                    mutableState.update { it.copy(saving = false, end = PlaceFormEnd.PLACE_GONE) }
                }

                is PlaceWriteResult.Failed -> {
                    mutableState.update {
                        it.copy(
                            saving = false,
                            message = messageFor(result.failure).toPlaceMessage(),
                        )
                    }
                }
            }
        }
    }

    // --- Delete ----------------------------------------------------------------------------------------------------

    fun onDeleteRequested() {
        val state = mutableState.value
        if (state.mode != PlaceFormMode.EDIT || state.busy || original == null) return
        mutableState.update { it.copy(deleteDialogOpen = true, deleteMessage = null) }
    }

    fun onDeleteDismissed() {
        if (mutableState.value.deleting) return
        mutableState.update { it.copy(deleteDialogOpen = false, deleteMessage = null) }
    }

    fun onDeleteConfirmed() {
        val state = mutableState.value
        val group = original ?: return
        if (state.deleting || !state.deleteDialogOpen) return
        if (!network.isOnline()) {
            mutableState.update { it.copy(deleteMessage = ActionMessage.OFFLINE) }
            return
        }
        mutableState.update { it.copy(deleting = true, deleteMessage = null) }
        viewModelScope.launch {
            when (val result = repository.delete(group.id, group.miffIds)) {
                is PlaceWriteResult.Done -> {
                    mutableState.update {
                        it.copy(
                            deleting = false,
                            deleteDialogOpen = false,
                            end = PlaceFormEnd.DELETED,
                        )
                    }
                }

                is PlaceWriteResult.Failed -> {
                    mutableState.update { it.copy(deleting = false, deleteMessage = messageFor(result.failure)) }
                }

                else -> {
                    mutableState.update { it.copy(deleting = false, deleteMessage = ActionMessage.GENERIC) }
                }
            }
        }
    }

    /** The host has left the screen or opened the offer: the one-shot flags go down. */
    fun onPaywallHandled() {
        mutableState.update { it.copy(paywall = null) }
    }

    private fun ActionMessage.toPlaceMessage(): PlaceMessage =
        if (this == ActionMessage.OFFLINE) PlaceMessage.OFFLINE else PlaceMessage.GENERIC

    private companion object {
        /** What can be typed in the address field: the longest address the rules accept. */
        const val ADDRESS_INPUT_MAX = PlaceLimits.ADDRESS_MAX
    }
}
