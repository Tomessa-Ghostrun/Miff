package com.tomessa.miff.data.push

import com.tomessa.miff.data.notification.NotificationPreferencesStore
import com.tomessa.miff.data.notification.NotificationSettings
import com.tomessa.miff.data.notification.NotificationTeardownSteps
import com.tomessa.miff.domain.notification.NotificationPreferences
import com.tomessa.miff.domain.notification.NotificationType
import com.tomessa.miff.domain.push.RegistrationRecord
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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

        /** The server does not answer (offline with a connection that hangs): the write waits for ever. */
        var hang = false

        override suspend fun upsert(
            uid: String,
            deviceId: String,
            entry: DeviceEntry,
        ) {
            if (hang) awaitCancellation()
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
            assertTrue(written.entry.notifyUpdates)
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
    fun theSecondSyncCostsNothing_untilThirtyDaysHavePassed() =
        runTest {
            registrar.sync()
            registry.calls.clear()
            now += 60_000L
            registrar.sync()
            now += 29L * 24 * 3_600_000L
            registrar.sync()
            assertTrue(registry.calls.isEmpty())
            now += 2L * 24 * 3_600_000L
            registrar.sync()
            assertEquals(
                "a refresh after thirty days, without touching the other documents",
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
    fun theSwitchIsMirroredAtOnce_theTimeZoneOnceADay_theVersionTravelsWithTheNextWrite() =
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
            now += 60_000L
            registrar.sync()
            assertTrue("a time zone is written once a day at most", registry.calls.isEmpty())
            version = "0.37.0 (37000)"
            registrar.sync()
            assertTrue("the version alone writes nothing", registry.calls.isEmpty())
            now += 24 * 3_600_000L
            registrar.sync()
            val written = registry.calls.single().entry!!
            assertEquals("America/New_York", written.timeZoneId)
            assertEquals("0.37.0 (37000)", written.appVersion)
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
    fun offline_theTokenIsInvalidatedAtOnce_evenWhileARegistrationIsStuckOnTheServer() =
        runTest {
            registrar.sync()
            registry.calls.clear()
            now += 31L * 24 * 3_600_000L
            registry.hang = true
            val stuck = launch { registrar.sync() }
            runCurrent()
            val signingOut = launch { registrar.forget() }
            runCurrent()
            assertEquals("invalidated BEFORE the lock is free, not after the wait", 1, tokens.disabled)
            advanceTimeBy(15_001L)
            runCurrent()
            stuck.join()
            signingOut.join()
            assertNull(local.record)
            assertFalse(local.on)
            assertEquals("not invalidated a second time", 1, tokens.disabled)
        }

    @Test
    fun aWriteThatTheServerNeverAnswersIsGivenUpAfterFifteenSeconds_andTriedAgainLater() =
        runTest {
            registry.hang = true
            val first = launch { registrar.sync() }
            runCurrent()
            advanceTimeBy(15_001L)
            runCurrent()
            first.join()
            assertNull("not recorded: nothing confirmed", local.record)
            registry.hang = false
            now += 60_000L
            registrar.sync()
            assertTrue("within ten minutes: not tried again", registry.calls.none { it.kind == "upsert" })
            now += 10 * 60_000L
            registrar.sync()
            assertTrue(registry.calls.any { it.kind == "upsert" })
        }

    // --- The sign-out shuts the registration (contract 4.6) ------------------------------------------------------------------------

    private class MemoryPrefs : NotificationPreferencesStore {
        var saved = NotificationPreferences()

        override fun load() = saved

        override fun save(preferences: NotificationPreferences) {
            saved = preferences
        }

        override fun clear() {
            saved = NotificationPreferences()
        }
    }

    @Test
    fun afterTheSignOutShutIt_theSameAccountAndSessionIsNeverRegisteredAgain_anotherSessionOpensItAgain() =
        runTest {
            registrar.sync()
            registry.calls.clear()
            val fetches = tokens.fetches
            registrar.forget()
            registry.calls.clear()
            // The consent is still true, the account still signed in (the sign-out comes after the teardown): nothing wakes the service.
            registrar.sync()
            registrar.sync(freshToken = "token-2")
            assertTrue(registry.calls.isEmpty())
            assertEquals(fetches, tokens.fetches)
            assertFalse(local.on)
            assertNull(local.record)
            // A new sign-in makes a new session: the registration is allowed again.
            deviceId = "dev2"
            registrar.sync()
            assertTrue(registry.calls.any { it.kind == "upsert" && it.deviceId == "dev2" })
        }

    @Test
    fun theTeardownOfASignOut_theResetOfTheChoicesDoesNotWakeTheRegistration() =
        runTest {
            val settings = NotificationSettings(MemoryPrefs())
            // A choice that is not the default, so that the reset really emits (as it does for a person who changed something).
            settings.setType(NotificationType.N9, false)
            registrar.sync()
            registry.calls.clear()
            val fetches = tokens.fetches
            // What the container does: every change of a choice asks the registry to look again.
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                settings.preferences.drop(1).collect { registrar.sync() }
            }
            val teardown =
                NotificationTeardownSteps(
                    closePush = registrar::close,
                    local = listOf(settings::reset),
                    forgetPush = registrar::forget,
                )
            teardown.stop()
            advanceUntilIdle()
            assertEquals("only the deletion of the document, nothing written again", listOf("delete"), registry.kinds())
            assertEquals("the service was not switched on again", fetches, tokens.fetches)
            assertFalse(local.on)
            assertNull(local.record)
        }

    @Test
    fun aSlowForget_neverErasesTheRegistrationOfAnAccountThatSignedInAgainMeanwhile() =
        runTest {
            registrar.sync()
            registry.calls.clear()
            now += 31L * 24 * 3_600_000L
            registry.hang = true
            // A registration is stuck on the server and holds the lock.
            val stuck = launch { registrar.sync() }
            runCurrent()
            // The sign-out: shut at once, the forget waits behind the stuck one.
            val steps =
                NotificationTeardownSteps(
                    closePush = registrar::close,
                    local = emptyList(),
                    forgetPush = registrar::forget,
                )
            val signingOut = launch { steps.stop() }
            runCurrent()
            // The person signs in again (a new session) while the forget is still waiting; its first registration is queued behind it.
            deviceId = "dev2"
            registry.hang = false
            val again = launch { registrar.sync() }
            runCurrent()
            advanceTimeBy(15_001L)
            runCurrent()
            stuck.join()
            signingOut.join()
            again.join()
            assertEquals(
                "the new session is registered, the late forget did not erase it",
                "dev2",
                local.record?.deviceId,
            )
            assertTrue(local.on)
        }

    @Test
    fun aDeletionCancelled_theSessionKeepsItsIdentifier_reopenLetsTheRegistrationWorkAgain() =
        runTest {
            registrar.sync()
            registry.calls.clear()
            // The deletion of the account begins (teardown: shut, document deleted, token invalidated), then a Miff shows up and it is cancelled: same account, SAME session.
            val steps =
                NotificationTeardownSteps(
                    closePush = registrar::close,
                    local = emptyList(),
                    forgetPush = registrar::forget,
                )
            steps.stop()
            registry.calls.clear()
            registrar.sync()
            assertTrue("still shut: nothing is written for a session that was ended", registry.calls.isEmpty())
            registrar.reopen()
            registrar.sync()
            assertEquals(listOf("deleteOthers", "upsert"), registry.kinds())
            assertEquals("dev1", registry.calls.last().deviceId)
            assertTrue(local.on)
            assertEquals("dev1", local.record?.deviceId)
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
        val ok = DeviceEntry("t", "Europe/Paris", true, "1 (1)")
        assertEquals("DeviceEntry(redacted)", ok.toString())
        assertTrue(runCatching { DeviceEntry("t".repeat(4097), "Europe/Paris", true, "1 (1)") }.isFailure)
        assertTrue(runCatching { DeviceEntry("t".repeat(4096), "Europe/Paris", true, "1 (1)") }.isSuccess)
        assertTrue(runCatching { DeviceEntry("", "Europe/Paris", true, "1 (1)") }.isFailure)
        assertTrue(runCatching { DeviceEntry("t", "z".repeat(65), true, "1 (1)") }.isFailure)
        assertTrue(runCatching { DeviceEntry("t", "Europe/Paris", true, "v".repeat(33)) }.isFailure)
    }
}
