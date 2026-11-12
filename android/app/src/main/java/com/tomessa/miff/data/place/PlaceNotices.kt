package com.tomessa.miff.data.place

import com.tomessa.miff.data.session.SessionTeardown
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** "Le lieu « X » est enregistré. Tes proches le prendront en compte dès qu'ils ouvriront Miff…": the name only, in memory. */
class PlaceNotice(
    val name: String,
) {
    override fun toString(): String = "PlaceNotice(redacted)"
}

/**
 * "Demande envoyée. Elle est en attente dans la liste." (ticket LIEU-REQ, criterion M4), or, with no network, "Ta demande partira dès que tu seras connecté."
 * (criterion M11). It carries no name: only whether the phone kept the request for later.
 */
class RequestNotice(
    val queued: Boolean,
)

/**
 * The message shown on the list of places after a save (mockup 04L: no success screen, the form closes on the list). It lives in
 * memory only (the name of a place is sensitive: never in a saved state, a log or a file) and goes with the session. The screen
 * that shows it calls [consume] when the person leaves it.
 */
class PlaceNotices : SessionTeardown {
    private val mutable = MutableStateFlow<PlaceNotice?>(null)
    val notice: StateFlow<PlaceNotice?> = mutable.asStateFlow()

    private val mutableRequest = MutableStateFlow<RequestNotice?>(null)

    /** The confirmation of a request to add a place, for the list of a member. */
    val requestNotice: StateFlow<RequestNotice?> = mutableRequest.asStateFlow()

    fun saved(name: String) {
        mutable.value = PlaceNotice(name)
    }

    fun requestSent(queued: Boolean) {
        mutableRequest.value = RequestNotice(queued)
    }

    fun consume() {
        mutable.value = null
        mutableRequest.value = null
    }

    override suspend fun stop() {
        mutable.value = null
        mutableRequest.value = null
    }
}
