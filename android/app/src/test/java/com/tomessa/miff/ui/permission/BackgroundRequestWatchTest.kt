package com.tomessa.miff.ui.permission

import com.tomessa.miff.domain.permission.BackgroundRequestOutcome
import com.tomessa.miff.domain.permission.LocationFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the screen notes between the start of the request for "all the time" and its answer (no phone needed: the clock is handed in). */
class BackgroundRequestWatchTest {
    private var now = 10_000L
    private val watch = BackgroundRequestWatch { now }

    @Test
    fun answeredAtOnce_withoutLeavingTheFront_nothingWasShown() {
        watch.start()
        now += 150
        assertEquals(BackgroundRequestOutcome.NO_UI, watch.finish(granted = false))
    }

    @Test
    fun theAppWasStopped_aFullScreenOfTheSystemWasShown_evenIfTheAnswerIsQuick() {
        watch.start()
        watch.onStopped()
        now += 300
        assertEquals(BackgroundRequestOutcome.SYSTEM_UI_SEEN, watch.finish(granted = false))
    }

    @Test
    fun aPanelOverTheApp_theAppStaysStarted_butThePersonTookTime() {
        watch.start()
        now += LocationFlow.MIN_SYSTEM_UI_MS + 2_000
        assertEquals(BackgroundRequestOutcome.SYSTEM_UI_SEEN, watch.finish(granted = false))
    }

    @Test
    fun granted_whateverTheDelay() {
        watch.start()
        now += 5
        assertEquals(BackgroundRequestOutcome.GRANTED, watch.finish(granted = true))
    }

    @Test
    fun aStartWeDidNotSee_isNeverTakenForARefusalAtOnce() {
        // The screen was rebuilt while the system screen was open: no start is known.
        assertEquals(BackgroundRequestOutcome.SYSTEM_UI_SEEN, watch.finish(granted = false))
    }

    @Test
    fun aStopBeforeTheStart_doesNotCount_andEachRequestStartsClean() {
        watch.onStopped()
        watch.start()
        now += 100
        assertEquals(BackgroundRequestOutcome.NO_UI, watch.finish(granted = false))
        // The previous request left no trace: a quick answer is again "nothing shown".
        watch.start()
        watch.onStopped()
        now += 100
        assertEquals(BackgroundRequestOutcome.SYSTEM_UI_SEEN, watch.finish(granted = false))
        watch.start()
        now += 100
        assertEquals(BackgroundRequestOutcome.NO_UI, watch.finish(granted = false))
    }
}
