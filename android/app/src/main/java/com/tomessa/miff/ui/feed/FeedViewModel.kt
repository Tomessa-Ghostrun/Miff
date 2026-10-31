package com.tomessa.miff.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.data.miff.MiffDirectory
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/*
 * SECURITY, for this file: the names of the Miffs are personal data; they stay in memory (see MiffDirectory), never logged.
 */

/**
 * The Feed tab, PROVISIONAL like its screen (ticket ONB-3a): what it needs from the Miff ticket is the list of the person's
 * Miffs (for the pastille and the empty state) and the check, at each opening, that the access to each of them still exists
 * (ticket Miff 5.7: the clean-up and the single message are done by [MiffDirectory]).
 */
class FeedViewModel(
    private val directory: MiffDirectory,
) : ViewModel() {
    val directoryState: StateFlow<DirectoryState> = directory.state

    init {
        refresh()
    }

    /** At the opening of the tab, and "Réessayer". */
    fun refresh() {
        viewModelScope.launch { directory.refresh() }
    }

    /** Premium: another Miff was chosen in the selector. */
    fun onSelect(miffId: String) = directory.select(miffId)
}
