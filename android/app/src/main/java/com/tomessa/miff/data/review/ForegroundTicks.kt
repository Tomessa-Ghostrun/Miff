package com.tomessa.miff.data.review

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Counts the returns of the app to the foreground (the app was in the background, the first screen starts: [com.tomessa.miff.data.tracking.ForegroundObserver]
 * says so; a rotation is not one). Nothing else: no date, no screen, no account. The review window reads it to look at its rules once per return.
 */
class ForegroundTicks {
    private val count = MutableStateFlow(0)
    val ticks: StateFlow<Int> = count.asStateFlow()

    fun arrived() {
        count.update { it + 1 }
    }
}
