package com.tomessa.miff.data.update

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The real ledger of the Update (review C1): the note of the photo sent is tied to the account and SURVIVES a sign-out (the photo stays in the database), while
 * the counts, the explanation of the camera and the tip are erased.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PublishLedgerTest {
    private fun ledger() = SharedPreferencesPublishLedger(ApplicationProvider.getApplicationContext())

    @Test
    fun theNoteOfThePhoto_survivesASignOut_andIsTheSameAccountsOnly() {
        val ledger = ledger()
        ledger.notePhotoSent("uid-1", 1_234L)
        ledger.recordPublication(1_234L, "2026-11-06", 3)
        ledger.markCameraExplained()
        ledger.markDoubleTapUsed()

        ledger.clear()

        assertEquals(1_234L, ledger.photoSentMillis("uid-1"))
        assertNull("another account never sees it", ledger.photoSentMillis("uid-2"))
        assertNull(ledger.countedDay())
        assertEquals(0, ledger.countToday())
        assertNull(ledger.lastPublishMillis())
        assertFalse(ledger.cameraExplained())
        // What is about the INSTALLATION (the double tap already used) is not erased with the account.
        assertTrue(ledger.doubleTapUsed())
        // A new instance on the same file (a restart of the process) still has it.
        assertEquals(1_234L, ledger().photoSentMillis("uid-1"))
    }

    @Test
    fun theNoteCanBeForgotten_andAnotherAccountReplacesIt() {
        val ledger = ledger()
        ledger.notePhotoSent("uid-1", 10L)
        ledger.notePhotoSent("uid-2", 20L)
        assertNull(ledger.photoSentMillis("uid-1"))
        assertEquals(20L, ledger.photoSentMillis("uid-2"))
        ledger.clearPhotoSent()
        assertNull(ledger.photoSentMillis("uid-2"))
        assertTrue(ledger.countToday() == 0)
    }

    @Test
    fun theDoubleTapIsRemembered_neverUsedBeforeTheFirstOne_andSurvivesARestartOfTheApp() {
        assertFalse("never used on a fresh installation", ledger().doubleTapUsed())
        ledger().markDoubleTapUsed()
        // A new instance on the same private file = a restart of the app.
        assertTrue(ledger().doubleTapUsed())
    }
}
