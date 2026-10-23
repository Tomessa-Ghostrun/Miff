package com.tomessa.miff.data

import com.tomessa.miff.data.privacy.BooleanStore
import com.tomessa.miff.data.privacy.CollectionSwitch
import com.tomessa.miff.data.privacy.PrivacySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacySettingsTest {
    private class MemoryStore : BooleanStore {
        val values = mutableMapOf<String, Boolean>()

        override fun get(
            key: String,
            default: Boolean,
        ) = values[key] ?: default

        override fun put(
            key: String,
            value: Boolean,
        ) {
            values[key] = value
        }
    }

    private class RecordingSwitch : CollectionSwitch {
        val calls = mutableListOf<Boolean>()

        override fun setEnabled(enabled: Boolean) {
            calls += enabled
        }
    }

    @Test
    fun everythingIsOffByDefault() {
        val analytics = RecordingSwitch()
        val crash = RecordingSwitch()
        val settings = PrivacySettings(MemoryStore(), analytics, crash)
        assertFalse(settings.analyticsEnabled.value)
        assertFalse(settings.crashReportsEnabled.value)

        settings.applyStoredChoices()
        assertEquals(listOf(false), analytics.calls)
        assertEquals(listOf(false), crash.calls)
    }

    @Test
    fun choicesAreAppliedToTheSdksAndPersisted() {
        val store = MemoryStore()
        val analytics = RecordingSwitch()
        val crash = RecordingSwitch()
        val settings = PrivacySettings(store, analytics, crash)

        settings.setAnalyticsEnabled(true)
        settings.setCrashReportsEnabled(true)
        assertTrue(settings.analyticsEnabled.value)
        assertTrue(settings.crashReportsEnabled.value)
        assertEquals(listOf(true), analytics.calls)
        assertEquals(listOf(true), crash.calls)

        // "Restart": a new instance reads the stored choices and pushes them to the SDKs again.
        val analyticsAfterRestart = RecordingSwitch()
        val crashAfterRestart = RecordingSwitch()
        val restarted = PrivacySettings(store, analyticsAfterRestart, crashAfterRestart)
        assertTrue(restarted.analyticsEnabled.value)
        restarted.applyStoredChoices()
        assertEquals(listOf(true), analyticsAfterRestart.calls)
        assertEquals(listOf(true), crashAfterRestart.calls)
    }

    @Test
    fun switchingOffIsAppliedAndPersisted() {
        val store = MemoryStore()
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
        val settings = PrivacySettings(MemoryStore(), RecordingSwitch(), RecordingSwitch())
        settings.setCrashReportsEnabled(true)
        assertFalse(settings.analyticsEnabled.value)
        assertTrue(settings.crashReportsEnabled.value)
    }
}
