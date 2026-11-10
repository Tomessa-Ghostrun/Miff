package com.tomessa.miff.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.miff.FacesRead
import com.tomessa.miff.data.miff.MemberFacesReader
import com.tomessa.miff.domain.profile.CardFaces
import com.tomessa.miff.domain.profile.MiffFacesBook
import com.tomessa.miff.domain.profile.ReadMembers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/*
 * PRIVACY and COST, for this file: the faces of the members stay in memory, for this view model only (one per account and per session: the
 * next person never meets them); nothing is logged or sent as an event. The reading rules (what is reused, what is read, how often) are
 * [MiffFacesBook]'s; the single reading is [MemberFacesReader]'s. No listener is attached here, ever.
 */

/**
 * The faces drawn on the cards of the carousel of Miffs of the Profile tab (decision of the PM, 2026-11-10).
 *
 * - The Miff the Feed has read: its members are given by [feedMembers] and drawn without any reading.
 * - The others: read ONE time when their card appears in the carousel ([onCardShown]), then reused for 5 minutes.
 * - The screen of a Miff was opened ([onMiffOpened]): its faces are read again the next time its card appears (somebody may have been removed).
 */
class ProfileMiffsViewModel(
    private val reader: MemberFacesReader,
    feedMembers: Flow<ReadMembers?>,
    nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val book = MiffFacesBook(nowMillis)
    private val mutableFaces = MutableStateFlow<Map<String, CardFaces>>(emptyMap())

    /** What each card of the carousel shows, by id of Miff. A Miff that is not in the map has nothing yet: the card shows its placeholders. */
    val faces: StateFlow<Map<String, CardFaces>> = mutableFaces.asStateFlow()

    init {
        viewModelScope.launch {
            feedMembers.filterNotNull().collect { read ->
                book.seedFromFeed(read)
                publish()
            }
        }
    }

    /** The card of [miffId] has appeared (it is the page the person looks at): reads the members if, and only if, the book says so. */
    fun onCardShown(miffId: String) {
        if (!book.needsReading(miffId)) return
        book.readingStarted(miffId)
        publish()
        viewModelScope.launch {
            when (val answer = reader.read(miffId)) {
                is FacesRead.Loaded -> book.readingSucceeded(miffId, answer.members)
                FacesRead.Failed -> book.readingFailed(miffId)
            }
            publish()
        }
    }

    /** The person opens the screen of this Miff from its card. */
    fun onMiffOpened(miffId: String) = book.invalidate(miffId)

    /** The Miffs the person is in now: the faces of any other are forgotten. */
    fun retainOnly(miffIds: Set<String>) {
        book.retainOnly(miffIds)
        publish()
    }

    private fun publish() {
        mutableFaces.value = book.view()
    }
}
