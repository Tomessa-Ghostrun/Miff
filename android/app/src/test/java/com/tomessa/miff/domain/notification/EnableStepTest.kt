package com.tomessa.miff.domain.notification

import org.junit.Assert.assertEquals
import org.junit.Test

/** What "Activer les notifications" of the centre does: the native window when Android can still show it, else the calm help to the phone settings. */
class EnableStepTest {
    private val off = NotificationPermissionState.OFF
    private val never = NotificationPermissionState.NOT_ASKED

    @Test
    fun neverAsked_isTheNativeWindow() {
        assertEquals(EnableStep.ASK_SYSTEM, EnableStep.of(never, sdk = 34, systemWindowStillPossible = false))
    }

    @Test
    fun refusedOnce_androidStillShowsItsWindow_android13Plus() {
        assertEquals(EnableStep.ASK_SYSTEM, EnableStep.of(off, sdk = 33, systemWindowStillPossible = true))
    }

    @Test
    fun refusedForGood_orBefore13_orTurnedOffInThePhone_isTheHelpThatOpensTheSettings() {
        assertEquals(EnableStep.OPEN_HELP, EnableStep.of(off, sdk = 34, systemWindowStillPossible = false))
        assertEquals(EnableStep.OPEN_HELP, EnableStep.of(off, sdk = 31, systemWindowStillPossible = true))
        assertEquals(EnableStep.OPEN_HELP, EnableStep.of(off, sdk = 26, systemWindowStillPossible = false))
    }
}
