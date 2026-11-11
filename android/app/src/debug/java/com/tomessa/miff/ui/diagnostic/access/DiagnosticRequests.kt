package com.tomessa.miff.ui.diagnostic.access

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * "Open the diagnostic screen": sent by the hold detector and by the Settings line, received once by the navigation graph (MiffNavHost).
 * No data travels: no replay (an old request never opens the screen later), and a request made while nobody listens is simply dropped.
 */
object DiagnosticRequests {
    private val mutableEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = mutableEvents.asSharedFlow()

    fun request() {
        mutableEvents.tryEmit(Unit)
    }
}
