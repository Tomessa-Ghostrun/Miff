package com.tomessa.miff.ui.diagnostic.access

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "The diagnostic screen is open": set by the hold detector and by the Settings line, shown by [DiagnosticOverlay] (MiffNavHost), cleared by its
 * back button or the back gesture. One boolean, nothing else: no data travels, and it starts closed at every launch of the app.
 */
object DiagnosticRequests {
    private val mutableIsOpen = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> = mutableIsOpen.asStateFlow()

    fun request() {
        mutableIsOpen.value = true
    }

    fun close() {
        mutableIsOpen.value = false
    }
}
