package com.tomessa.miff.data.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The REAL shape of what the phone writes to `users/{uid}/devices/{deviceId}`, compared with the contract (docs/dev/jetons-appareil-clients.md, section 2): seven keys on creation,
 * `notify.updates` nested, `createdAt` and `platform` on creation only, the time of the server and never a date of the phone, nothing else.
 */
class DeviceDocumentsTest {
    private val entry =
        DeviceEntry(
            token = "token-xyz",
            timeZoneId = "Europe/Paris",
            notifyUpdates = false,
            appVersion = "0.36.0 (36000)",
        )

    @Test
    fun theCreationHasExactlyTheSevenKeysOfTheContract() {
        val document = DeviceDocuments.forCreate(entry)
        assertEquals(setOf("token", "platform", "appVersion", "tz", "notify", "updatedAt", "createdAt"), document.keys)
        assertEquals(DeviceDocuments.CREATE_KEYS, document.keys)
        assertEquals("token-xyz", document["token"])
        assertEquals("android", document["platform"])
        assertEquals("0.36.0 (36000)", document["appVersion"])
        assertEquals("Europe/Paris", document["tz"])
    }

    @Test
    fun theSwitchIsNested_notify_updates_andIsTheOnlyKeyOfItsMap() {
        val notify = DeviceDocuments.forCreate(entry)["notify"]
        assertEquals(mapOf("updates" to false), notify)
        assertEquals(mapOf("updates" to true), DeviceDocuments.forUpdate(entry.copyWith(notify = true))["notify"])
    }

    @Test
    fun bothTimesAreTheTimeOfTheServer_neverADateOfThePhone() {
        val created = DeviceDocuments.forCreate(entry)
        assertSame(ServerTime, created["createdAt"])
        assertSame(ServerTime, created["updatedAt"])
        assertSame(ServerTime, DeviceDocuments.forUpdate(entry)["updatedAt"])
    }

    @Test
    fun anUpdateNeverWritesCreatedAtNorPlatform_andHasNoOtherKey() {
        val update = DeviceDocuments.forUpdate(entry)
        assertEquals(setOf("token", "appVersion", "tz", "notify", "updatedAt"), update.keys)
        assertEquals(DeviceDocuments.UPDATE_KEYS, update.keys)
        assertFalse("createdAt" in update)
        assertFalse("platform" in update)
    }

    @Test
    fun noKeyThatTheRulesRefuse_noLanguage_noStateOfThePermission_noPosition_noName() {
        val keys = DeviceDocuments.forCreate(entry).keys
        listOf("locale", "notificationsEnabled", "lat", "lng", "name", "email", "miffId", "adid").forEach {
            assertFalse("$it must not be written", it in keys)
        }
        assertTrue(DeviceDocuments.forCreate(entry).values.none { it is Number })
    }

    private fun DeviceEntry.copyWith(notify: Boolean) = DeviceEntry(token, timeZoneId, notify, appVersion)
}
