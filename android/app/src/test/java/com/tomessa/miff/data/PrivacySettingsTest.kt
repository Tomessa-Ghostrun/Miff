package com.tomessa.miff.data

import com.tomessa.miff.data.privacy.CrashReportsClient
import com.tomessa.miff.data.privacy.FirebaseCrashlyticsSwitch
import com.tomessa.miff.data.privacy.PrivacySettings
import com.tomessa.miff.testsupport.MemoryBooleanStore
import com.tomessa.miff.testsupport.RecordingSwitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacySettingsTest {
    private class RecordingCrashClient : CrashReportsClient {
        /** "enabled=true", "enabled=false" and "delete", in order. */
        val log = mutableListOf<String>()

        override fun setCollectionEnabled(enabled: Boolean) {
            log += "enabled=$enabled"
        }

        override fun deleteUnsentReports() {
            log += "delete"
        }
    }

    @Test
    fun everythingIsOffByDefault() {
        val settings = PrivacySettings(MemoryBooleanStore(), RecordingSwitch(), RecordingSwitch())
        assertFalse(settings.analyticsEnabled.value)
        assertFalse(settings.crashReportsEnabled.value)
    }

    @Test
    fun choicesAreAppliedToTheSdksAndPersisted() {
        val store = MemoryBooleanStore()
        val analytics = RecordingSwitch()
        val crash = RecordingSwitch()
        val settings = PrivacySettings(store, analytics, crash)

        settings.setAnalyticsEnabled(true)
        settings.setCrashReportsEnabled(true)
        assertTrue(settings.analyticsEnabled.value)
        assertTrue(settings.crashReportsEnabled.value)
        assertEquals(listOf(true), analytics.calls)
        assertEquals(listOf(true), crash.calls)

        // A new instance (next launch) reads the stored values; they are NOT pushed to the SDKs by themselves: the
        // session manager decides after having read the account.
        val analyticsAfterRestart = RecordingSwitch()
        val restarted = PrivacySettings(store, analyticsAfterRestart, RecordingSwitch())
        assertTrue(restarted.analyticsEnabled.value)
        assertTrue(analyticsAfterRestart.log.isEmpty())
    }

    @Test
    fun switchingOffIsAppliedAndPersisted() {
        val store = MemoryBooleanStore()
        val analytics = RecordingSwitch()
        val settings = PrivacySettings(store, analytics, RecordingSwitch())
        settings.setAnalyticsEnabled(true)
        settings.setAnalyticsEnabled(false)
        assertFalse(settings.analyticsEnabled.value)
        assertEquals(listOf(true, false), analytics.calls)
        assertFalse(PrivacySettings(store, RecordingSwitch(), RecordingSwitch()).analyticsEnabled.value)
    }

    @Test
    fun analyticsAndCrashReportsAreIndependent() {
        val settings = PrivacySettings(MemoryBooleanStore(), RecordingSwitch(), RecordingSwitch())
        settings.setCrashReportsEnabled(true)
        assertFalse(settings.analyticsEnabled.value)
        assertTrue(settings.crashReportsEnabled.value)
    }

    /** The consent is ONE choice for both SDKs; withdrawing it cuts both at once, before anything is written (X20). */
    @Test
    fun theAccountChoiceSwitchesBothCollectionsTogether_andWithdrawalCutsBothAtOnce() {
        val store = MemoryBooleanStore()
        val analytics = RecordingSwitch()
        val crash = RecordingSwitch()
        val settings = PrivacySettings(store, analytics, crash)

        settings.applyAccountConsent(true)
        assertTrue(settings.analyticsEnabled.value)
        assertTrue(settings.crashReportsEnabled.value)

        settings.applyAccountConsent(false)
        assertFalse(settings.analyticsEnabled.value)
        assertFalse(settings.crashReportsEnabled.value)
        assertEquals(listOf(true, false), analytics.calls)
        assertEquals(listOf(true, false), crash.calls)

        // The withdrawal is also what the next launch sees.
        val restarted = PrivacySettings(store, RecordingSwitch(), RecordingSwitch())
        assertFalse(restarted.analyticsEnabled.value)
        assertFalse(restarted.crashReportsEnabled.value)
    }

    // --- Review I2: the report of the previous crash must survive the cold start ---------------------------

    @Test
    fun pauseCutsBothCollections_butIsNotAWithdrawal() {
        val analytics = RecordingSwitch()
        val crash = RecordingSwitch()
        val settings = PrivacySettings(MemoryBooleanStore(), analytics, crash)
        settings.applyAccountConsent(true)

        settings.pauseCollection()

        assertFalse(settings.analyticsEnabled.value)
        assertFalse(settings.crashReportsEnabled.value)
        assertEquals(listOf("on", "pause"), analytics.log)
        assertEquals(listOf("on", "pause"), crash.log)
    }

    @Test
    fun crashlytics_pauseKeepsTheUnsentReports() {
        val client = RecordingCrashClient()
        FirebaseCrashlyticsSwitch(client).pause()
        assertEquals(listOf("enabled=false"), client.log)
    }

    @Test
    fun crashlytics_noConsentDeletesTheUnsentReports_afterCuttingTheCollection() {
        val client = RecordingCrashClient()
        FirebaseCrashlyticsSwitch(client).setEnabled(false)
        assertEquals(listOf("enabled=false", "delete"), client.log)
    }

    @Test
    fun crashlytics_consentTurnsTheCollectionOn_andDeletesNothing() {
        val client = RecordingCrashClient()
        FirebaseCrashlyticsSwitch(client).setEnabled(true)
        assertEquals(listOf("enabled=true"), client.log)
    }
}
