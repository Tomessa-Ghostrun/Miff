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
class TabRequests {
    private val state = MutableStateFlow<MainTab?>(null)

    val pending: StateFlow<MainTab?> = state.asStateFlow()

    fun request(tab: MainTab) {
        state.value = tab
    }

    /** The sign-out: a request made for the previous person must never be taken by the next one. */
    fun clear() {
        state.value = null
    }

    /** The tabs took the request for [tab]; a newer request for another tab is kept. */
    fun consume(tab: MainTab) {
        state.compareAndSet(tab, null)
    }
}
