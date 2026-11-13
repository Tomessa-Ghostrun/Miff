package com.tomessa.miff.domain.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the phone writes (or deletes) its document of the registry of devices: only when something changed, at most a refresh a day. */
class PushRegistrationTest {
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    private val wish =
        RegistrationWish(
            uid = "uid1",
            deviceId = "dev1",
            tokenFingerprint = "fp1",
            timeZoneId = "Europe/Paris",
            notifyUpdates = true,
        )

    private fun recordOf(
        wish: RegistrationWish,
        at: Long = now,
    ) = RegistrationRecord(
        wish.uid,
        wish.deviceId,
        wish.tokenFingerprint,
        wish.timeZoneId,
        wish.notifyUpdates,
        at,
    )

    @Test
    fun nothingWrittenBeforeBeingAskedFor_thenTheFirstWriteIsACreation() {
        assertEquals(RegistrationAction.None, RegistrationRules.decide(null, null, now))
        assertEquals(
            RegistrationAction.Write(RegistrationAction.Reason.FIRST),
            RegistrationRules.decide(wish, null, now),
        )
    }

    @Test
    fun anUnchangedDocumentIsLeftAlone() {
        assertEquals(RegistrationAction.None, RegistrationRules.decide(wish, recordOf(wish, now - 1), now))
        assertEquals(RegistrationAction.None, RegistrationRules.decide(wish, recordOf(wish, now - 23 * hour), now))
        // The version of the app alone never causes a write.
        assertEquals(RegistrationAction.None, RegistrationRules.decide(wish, recordOf(wish, now - 2 * hour), now))
    }

    @Test
    fun thirtyDaysLaterItIsRefreshedOnce() {
        assertEquals(RegistrationAction.None, RegistrationRules.decide(wish, recordOf(wish, now - 29 * 24 * hour), now))
        val action = RegistrationRules.decide(wish, recordOf(wish, now - 30 * 24 * hour), now)
        assertEquals(RegistrationAction.Write(RegistrationAction.Reason.REFRESH), action)
    }

    @Test
    fun aClockThatWentBackDoesNotCauseAWrite() {
        assertEquals(RegistrationAction.None, RegistrationRules.decide(wish, recordOf(wish, now + 5 * hour), now))
    }

    @Test
    fun aNewTokenIsWrittenAtOnce() {
        val action = RegistrationRules.decide(wish.copy(tokenFingerprint = "fp2"), recordOf(wish), now)
        assertEquals(RegistrationAction.Write(RegistrationAction.Reason.TOKEN_CHANGED), action)
    }

    @Test
    fun aNewSwitchIsWrittenAtOnce_aNewTimeZoneAtMostOnceADay() {
        assertEquals(
            RegistrationAction.Write(RegistrationAction.Reason.SETTINGS_CHANGED),
            RegistrationRules.decide(wish.copy(notifyUpdates = false), recordOf(wish), now),
        )
        val zone = wish.copy(timeZoneId = "America/New_York")
        assertEquals(RegistrationAction.None, RegistrationRules.decide(zone, recordOf(wish, now - 2 * hour), now))
        assertEquals(
            RegistrationAction.Write(RegistrationAction.Reason.SETTINGS_CHANGED),
            RegistrationRules.decide(zone, recordOf(wish, now - 24 * hour), now),
        )
    }

    @Test
    fun aNewSessionOrANewAccountIsAFreshDocument() {
        val newSession = RegistrationRules.decide(wish.copy(deviceId = "dev2"), recordOf(wish), now)
        assertEquals(RegistrationAction.Write(RegistrationAction.Reason.NEW_SESSION), newSession)
        val newAccount = RegistrationRules.decide(wish.copy(uid = "uid2"), recordOf(wish), now)
        assertEquals(RegistrationAction.Write(RegistrationAction.Reason.NEW_SESSION), newAccount)
    }

    @Test
    fun noWishButARecordMeansTheConsentIsGone_theDocumentIsRemoved() {
        assertEquals(RegistrationAction.Remove, RegistrationRules.decide(null, recordOf(wish), now))
    }

    @Test
    fun aFailedWriteWaitsTenMinutesBeforeTheNextTry() {
        assertTrue(RegistrationRules.mayRetry(null, now))
        assertFalse(RegistrationRules.mayRetry(now - 60_000L, now))
        assertTrue(RegistrationRules.mayRetry(now - RegistrationRules.RETRY_AFTER_FAILURE_MILLIS, now))
        // A clock that went back does not freeze the tries.
        assertTrue(RegistrationRules.mayRetry(now + hour, now))
    }

    @Test
    fun theDocumentIdentifierIsTheShapeOfASessionIdentifier() {
        assertTrue(DeviceDocumentId.isValid("aB3-_9"))
        assertFalse(DeviceDocumentId.isValid(null))
        assertFalse(DeviceDocumentId.isValid(""))
        assertFalse(DeviceDocumentId.isValid("a/b"))
        assertFalse(DeviceDocumentId.isValid("x".repeat(65)))
    }

    @Test
    fun theVersionOfTheDocumentIsNeverEmptyNorLongerThanTheRuleAccepts() {
        assertEquals("0.36.0 (36000)", DeviceAppVersion.of("0.36.0", 36000))
        assertEquals("0 (1)", DeviceAppVersion.of(null, 1))
        assertEquals("0 (1)", DeviceAppVersion.of("  ", 1))
        assertEquals(DeviceEntryLimits.MAX_APP_VERSION_CHARS, DeviceAppVersion.of("1.2.3-" + "x".repeat(60), 5).length)
    }

    @Test
    fun recordsAndWishesNeverPrintTheirContent() {
        assertEquals("RegistrationRecord(redacted)", recordOf(wish).toString())
        assertEquals("RegistrationWish(redacted)", wish.toString())
    }
}
