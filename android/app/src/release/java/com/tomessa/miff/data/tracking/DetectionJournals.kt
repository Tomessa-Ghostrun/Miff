package com.tomessa.miff.data.tracking

import android.content.Context
import com.tomessa.miff.domain.tracking.DetectionJournal
import com.tomessa.miff.domain.tracking.NoDetectionJournal

/*
 * RELEASE BUILD (and releaseLike): NO journal of the detection (criterion VIE2). The debug build has a file of the same name in src/debug with the
 * writer; nothing of it is compiled here. A unit test and the CI check that the release holds no trace of it.
 */
object DetectionJournals {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context): DetectionJournal = NoDetectionJournal
}
