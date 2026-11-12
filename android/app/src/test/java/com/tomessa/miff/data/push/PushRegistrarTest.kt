package com.tomessa.miff.data.push

import com.tomessa.miff.domain.push.RegistrationRecord
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The registration of the push token (SRV-2) with doubles for the registry, the phone's memory and the push service: nothing without an account, a valid session and
 * the consent; a write only when something changed; the other documents of the account go with a new session; the document goes with the consent, the sign-out and
 * the end of the session.
 */
class PushRegistrarTest {
    private class Call(
        val kind: String,
        val uid: String,
        val deviceId: String,
        val entry: DeviceEntry? = null,
    )

    private class FakeRegistry : DeviceTokenRegistry {
        val calls = mutableListOf<Call>()
        var failing = false

        override suspend fun upsert(
            uid: String,
            deviceId: String,
            entry: DeviceEntry,
        ) {
            if (failing) throw IOException("offline")
            calls += Call("upsert", uid, deviceId, entry)
        }

        override suspend fun deleteOthers(
            uid: String,
            keepDeviceId: String,
        ) {
            if (failing) throw IOException("offline")
            calls += Call("deleteOthers", uid, keepDeviceId)
        }

        override suspend fun delete(
            uid: String,
            deviceId: String,
        ) {
            if (failing) throw IOException("offline")
            calls += Call("delete", uid, deviceId)
        }

        fun kinds() = calls.map { it.kind }
    }

    private class FakeLocal : PushLocalState {
        var record: RegistrationRecord? = null
        var on = false
        var cleared = 0
        var clearedFor: String? = null

        override fun record() = record

        override fun saveRecord(record: RegistrationRecord) {
            this.record = record
        }

        override fun serviceOn() = on

        override fun setServiceOn(on: Boolean) {
            this.on = on
        }

        override fun othersClearedFor() = clearedFor

        override fun setOthersClearedFor(deviceId: String) {
            clearedFor = deviceId
        }

        override fun clear() {
            record = null
            on = false
            clearedFor = null
            cleared++
        }
    }

    private class FakeTokens : PushTokenSource {
        var token: String? = "token-1"
        var fetches = 0
        var disabled = 0

        override suspend fun enableAndFetch(): String? {
            fetches++
            return token
        }

        override fun disable() {
            disabled++
        }
    }

    private val registry = FakeRegistry()
    private val local = FakeLocal()
    private val tokens = FakeTokens()
    private var uid: String? = "uid1"
    private var deviceId: String? = "dev1"
    private var sessionOk = true
    private var consent = true
    private var notify = true
    private var zone = "Europe/Paris"
    private var version = "0.36.0 (36000)"
    private var language = "fr"
    private var now = 1_800_000_000_000L

    private val registrar =
        PushRegistrar(
            registry = registry,
            local = local,
            tokens = tokens,
            currentUid = { uid },
            sessionDeviceId = { deviceId },
            sessionValid = { sessionOk && it == "uid1" },
            consent = { consent && it == "uid1" },
            notifyUpdates = { notify },
            timeZoneId = { zone },
            appVersion = { version },
            locale = { language },
            nowMillis = { now },
        )

    @Test
    fun withoutConsentNoTokenIsRegistered_andThePushServiceIsNeverSwitchedOn_butTheOtherPhonesOfTheAccountAreCleared() =
        runTest {
            consent = false
            registrar.sync()
            // Contract 4.2: a new session clears the other documents of the account, consent or not (the old phone must not stay reachable).
            assertEquals(listOf("deleteOthers"), registry.kinds())
            assertEquals("no token is even asked for", 0, tokens.fetches)
            assertNull(local.record)
            assertFalse(local.on)
            assertEquals("nothing to switch off either", 0, tokens.disabled)
            // Once per session.
            registry.calls.clear()
            registrar.sync()
            assertTrue(registry.calls.isEmpty())
        }

    @Test
    fun withoutAnAccountOrAValidSession_nothingHappens() =
        runTest {
            uid = null
            registrar.sync()
            uid = "uid1"
            deviceId = null
            registrar.sync()
            deviceId = "dev/1"
            registrar.sync()
            deviceId = "dev1"
            sessionOk = false
            registrar.sync()
            assertTrue(registry.calls.isEmpty())
            assertEquals(0, tokens.fetches)
        }

    @Test
    fun withTheConsentTheFirstSyncClearsTheOthersThenWritesTheDocument() =
        runTest {
            registrar.sync()
            assertEquals(listOf("deleteOthers", "upsert"), registry.kinds())
            val cleared = registry.calls.first()
            assertEquals("dev1", cleared.deviceId)
            val written = registry.calls.last()
            assertEquals("uid1", written.uid)
            assertEquals("dev1", written.deviceId)
            assertEquals("token-1", written.entry!!.token)
            assertEquals("android", written.entry.platform)
            assertEquals("Europe/Paris", written.entry.timeZoneId)
            assertEquals("0.36.0 (36000)", written.entry.appVersion)
            assertEquals("fr", written.entry.locale)
            assertTrue(written.entry.notifyUpdates)
            assertTrue(
                "the permission and the general switch are on, or nothing is written",
                written.entry.notificationsEnabled,
            )
            assertTrue(local.on)
            assertEquals("dev1", local.record!!.deviceId)
        }

    @Test
    fun theTokenItselfIsNeverKeptOnThePhone_onlyItsFingerprint() =
        runTest {
            registrar.sync()
            val record = local.record!!
            assertFalse(record.tokenFingerprint.contains("token-1"))
            assertEquals(TokenFingerprint.of("token-1"), record.tokenFingerprint)
            assertEquals(64, record.tokenFingerprint.length)
            assertEquals("RegistrationRecord(redacted)", record.toString())
        }

    @Test
    fun theSecondSyncCostsNothing_untilADayHasPassed() =
        runTest {
            registrar.sync()
            registry.calls.clear()
            now += 60_000L
            registrar.sync()
            now += 22 * 3_600_000L
            registrar.sync()
            assertTrue(registry.calls.isEmpty())
            now += 3 * 3_600_000L
            registrar.sync()
            assertEquals(
                "a refresh after a day, without touching the other documents",
                listOf("upsert"),
                registry.kinds(),
            )
        }

    @Test
    fun aNewTokenFromTheSystemIsWrittenAtOnce() =
        runTest {
            registrar.sync()
            registry.calls.clear()
            registrar.sync(freshToken = "token-2")
            assertEquals(listOf("upsert"), registry.kinds())
            assertEquals(
                "token-2",
                registry.calls
                    .single()
                    .entry!!
                    .token,
            )
        }

    @Test
    fun aNewTokenWithoutTheConsentIsForgotten() =
        runTest {
            consent = false
            registrar.sync(freshToken = "token-2")
            assertTrue(registry.calls.none { it.kind == "upsert" })
            assertNull(local.record)
        }

    @Test
    fun theSwitchTheTimeZoneTheLanguageAndTheVersionAreMirroredAsSoonAsTheyChange() =
        runTest {
            registrar.sync()
            registry.calls.clear()
            notify = false
            registrar.sync()
            assertFalse(
                registry.calls
                    .single()
                    .entry!!
                    .notifyUpdates,
            )
            registry.calls.clear()
            zone = "America/New_York"
            registrar.sync()
            assertEquals(
                "America/New_York",
                registry.calls
                    .single()
                    .entry!!
                    .timeZoneId,
            )
            registry.calls.clear()
            language = "en"
            registrar.sync()
            assertEquals(
                "en",
                registry.calls
                    .single()
                    .entry!!
                    .locale,
            )
            registry.calls.clear()
            version = "0.37.0 (37000)"
            registrar.sync()
            assertEquals(
                "0.37.0 (37000)",
                registry.calls
                    .single()
                    .entry!!
                    .appVersion,
            )
            registry.calls.clear()
            registrar.sync()
            assertTrue("nothing changed since", registry.calls.isEmpty())
        }

    @Test
    fun aNewSessionOfTheSameAccountReplacesTheOtherDocuments() =
        runTest {
            registrar.sync()
            registry.calls.clear()
            deviceId = "dev2"
            registrar.sync()
            assertEquals(listOf("deleteOthers", "upsert"), registry.kinds())
            assertEquals("dev2", registry.calls.last().deviceId)
            assertEquals("the one to keep is the new one", "dev2", registry.calls.first().deviceId)
        }

    @Test
    fun theConsentGoing_deletesTheDocument_switchesTheServiceOffAndForgets() =
        runTest {
            registrar.sync()
            registry.calls.clear()
            consent = false
            registrar.sync()
            assertEquals(listOf("delete"), registry.kinds())
            assertEquals("dev1", registry.calls.single().deviceId)
            assertEquals(1, tokens.disabled)
            assertNull(local.record)
            assertFalse(local.on)
            // And it stays quiet afterwards: no second deletion, no second invalidation, no new clearing of the others for the same session.
            registry.calls.clear()
            registrar.sync()
            assertTrue(registry.calls.isEmpty())
            assertEquals(1, tokens.disabled)
        }

    @Test
    fun aFailedWriteIsRetriedLater_notAtEveryReturnToTheFront() =
        runTest {
            registry.failing = true
            registrar.sync()
            assertNull("not recorded: the server did not confirm", local.record)
            now += 60_000L
            registry.failing = false
            registrar.sync()
            assertTrue("within ten minutes: no new try", registry.calls.isEmpty())
            now += 10 * 60_000L
            registrar.sync()
            assertEquals(listOf("deleteOthers", "upsert"), registry.kinds())
            assertTrue(local.record != null)
        }

    @Test
    fun aTokenThatCannotBeHadYetWritesNothing() =
        runTest {
            tokens.token = null
            registrar.sync()
            assertTrue(registry.calls.none { it.kind == "upsert" })
            assertNull(local.record)
        }

    @Test
    fun forgetDeletesTheDocumentWhileTheAccountIsStillThere_invalidatesTheTokenAndErasesTheMemory() =
        runTest {
            registrar.sync()
            registry.calls.clear()
            registrar.forget()
            assertEquals(listOf("delete"), registry.kinds())
            assertEquals(1, tokens.disabled)
            assertNull(local.record)
            assertFalse(local.on)
            assertNull(local.clearedFor)
        }

    @Test
    fun forgetAfterTheAccountIsGone_stillInvalidatesTheTokenAndErases_withoutReachingTheServer() =
        runTest {
            registrar.sync()
            registry.calls.clear()
            uid = null
            registrar.forget()
            assertTrue("not authenticated any more: no call", registry.calls.isEmpty())
            assertEquals(1, tokens.disabled)
            assertNull(local.record)
        }

    @Test
    fun forgetThatTheServerCannotReachNeverThrows() =
        runTest {
            registrar.sync()
            registry.failing = true
            registrar.forget()
            assertEquals(1, tokens.disabled)
            assertNull(local.record)
        }

    @Test
    fun forgetWithNothingRegisteredDoesNothingToThePushService() =
        runTest {
            registrar.forget()
            assertEquals(0, tokens.disabled)
            assertTrue(registry.calls.isEmpty())
        }

    @Test
    fun theEntryRefusesValuesOutOfTheLimitsOfTheRules_andNeverPrintsItself() {
        val ok = DeviceEntry("t", "Europe/Paris", true, "1 (1)", "fr")
        assertEquals("DeviceEntry(redacted)", ok.toString())
        assertTrue(runCatching { DeviceEntry("t".repeat(4097), "Europe/Paris", true, "1 (1)", "fr") }.isFailure)
        assertTrue(runCatching { DeviceEntry("t".repeat(4096), "Europe/Paris", true, "1 (1)", "fr") }.isSuccess)
        assertTrue(runCatching { DeviceEntry("", "Europe/Paris", true, "1 (1)", "fr") }.isFailure)
        assertTrue(runCatching { DeviceEntry("t", "z".repeat(65), true, "1 (1)", "fr") }.isFailure)
        assertTrue(runCatching { DeviceEntry("t", "Europe/Paris", true, "v".repeat(33), "fr") }.isFailure)
        assertTrue(runCatching { DeviceEntry("t", "Europe/Paris", true, "1 (1)", "de") }.isFailure)
    }
}
