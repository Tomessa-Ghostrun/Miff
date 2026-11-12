package com.tomessa.miff.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.miff.PlacesRead
import com.tomessa.miff.data.miff.PlacesReader
import com.tomessa.miff.domain.place.MiffPlacesBook
import com.tomessa.miff.domain.place.MiffPlacesView
import com.tomessa.miff.domain.place.ReadPlaces
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/*
 * PRIVACY and COST, for this file: the places (names, addresses) stay in memory, for this view model only (one per account and per session: the next
 * person never meets them); nothing is logged or sent as an event. The reading rules (what is reused, what is read, how often) are [MiffPlacesBook]'s;
 * the single reading is [PlacesReader]'s. No listener is attached here, ever.
 */

/** The Miff the carousel is on and what is known of its places. [selectedMiffId] is null until a card of a Miff has been shown. */
data class ProfilePlacesState(
    val selectedMiffId: String? = null,
    val view: MiffPlacesView = MiffPlacesView.Loading,
) {
    override fun toString(): String = "ProfilePlacesState(redacted)"
}

/**
 * The places under the carousel of Miffs of the Profile tab (decision of the PM, 2026-11-11): the list of the Miff whose card the person looks at,
 * for EVERY member of that Miff (not only its creator).
 *
 * - The Miff the Feed has read: its places are given by [feedPlaces] and drawn without any reading.
 * - The others: read ONE time when their card appears in the carousel ([onCardShown]), then reused for 5 minutes.
 * - The creator opened a place or the form of a new one ([onPlacesMayChange]): the lists are read again the next time they appear.
 */
class ProfilePlacesViewModel(
    private val reader: PlacesReader,
    feedPlaces: Flow<ReadPlaces?>,
    nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val book = MiffPlacesBook(nowMillis)
    private val mutableState = MutableStateFlow(ProfilePlacesState())

    val state: StateFlow<ProfilePlacesState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            feedPlaces.filterNotNull().collect { read ->
                book.seedFromFeed(read)
                publish()
            }
        }
    }

    /** The card of [miffId] has appeared (it is the page the person looks at): its places are shown, read if, and only if, the book says so. */
    fun onCardShown(miffId: String) {
        mutableState.value = mutableState.value.copy(selectedMiffId = miffId, view = book.view(miffId))
        if (!book.needsReading(miffId)) return
        book.readingStarted(miffId)
        publish()
        viewModelScope.launch {
            when (val answer = reader.read(miffId)) {
                is PlacesRead.Loaded -> book.readingSucceeded(miffId, answer.places)
                PlacesRead.Failed -> book.readingFailed(miffId)
            }
            publish()
        }
    }

    /** "Réessayer": reads again at once (a reading already under way is not doubled). */
    fun onRetry() {
        val miffId = mutableState.value.selectedMiffId ?: return
        book.invalidate(miffId)
        onCardShown(miffId)
    }

    /** The creator opens a place or the form of a new one: whatever happens there, the lists are read again when they come back. */
    fun onPlacesMayChange() = book.invalidateAll()

    /** The Miffs the person is in now: the places of any other are forgotten. */
    fun retainOnly(miffIds: Set<String>) {
        book.retainOnly(miffIds)
        if (mutableState.value.selectedMiffId !in miffIds) mutableState.value = ProfilePlacesState()
        publish()
    }

    private fun publish() {
        val selected = mutableState.value.selectedMiffId ?: return
        mutableState.value = mutableState.value.copy(view = book.view(selected))
    }
}
