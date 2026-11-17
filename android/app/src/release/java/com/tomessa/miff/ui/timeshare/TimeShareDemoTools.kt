package com.tomessa.miff.ui.timeshare

import androidx.compose.runtime.Composable
import com.tomessa.miff.domain.place.Place
import com.tomessa.miff.domain.timeshare.PresenceLog

/**
 * Release build (and releaseLike): NO tool to fill the journal of the presence with test data (criterion TS-23). The debug build has a file of the same name in src/debug; nothing
 * of it is compiled here, and the CI looks for its marker in the release bundle and in releaseLike. Draws nothing.
 */
object TimeShareDemoTools {
    @Suppress("UNUSED_PARAMETER")
    @Composable
    fun Panel(
        log: PresenceLog,
        miffId: String,
        places: List<Place>,
    ) = Unit
}
