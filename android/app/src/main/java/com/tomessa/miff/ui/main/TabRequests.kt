package com.tomessa.miff.ui.main

import com.tomessa.miff.domain.navigation.MainTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A request, from outside the tabs, to show a tab: a tap on a line of the centre of notifications, and (phase B) a tap on a notification of the system.
 * The request waits until the tabs are on screen and are told to take it ([consume]), so it is never lost behind another screen. Nothing personal:
 * only which tab.
 */
/** NOTIF-P1A: bring the card of the member [uid] of the Miff [miffId] into view. Identifiers only, never a name. */
data class FeedFocus(
    val miffId: String,
    val uid: String,
) {
    override fun toString(): String = "FeedFocus(redacted)"
}

class TabRequests {
    private val state = MutableStateFlow<MainTab?>(null)
    private val focus = MutableStateFlow<FeedFocus?>(null)

    val pending: StateFlow<MainTab?> = state.asStateFlow()

    /**
     * NOTIF-P1A: the member whose card the Feed should bring into view (a tap on a line of "Depuis ta dernière visite"). An identifier only, never a name; the Feed forgets it
     * as soon as it took it ([consumeFocus]), and the sign-out clears it.
     */
    val pendingFocus: StateFlow<FeedFocus?> = focus.asStateFlow()

    fun requestFocus(target: FeedFocus) {
        focus.value = target
    }

    /** The Feed took the request for [target] (or found no card for it); a newer request for another member is kept. */
    fun consumeFocus(target: FeedFocus) {
        focus.compareAndSet(target, null)
    }

    fun request(tab: MainTab) {
        state.value = tab
    }

    /** The sign-out: a request made for the previous person must never be taken by the next one. */
    fun clear() {
        state.value = null
        focus.value = null
    }

    /** The tabs took the request for [tab]; a newer request for another tab is kept. */
    fun consume(tab: MainTab) {
        state.compareAndSet(tab, null)
    }
}
