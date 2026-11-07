package com.tomessa.miff.domain.update

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.domain.tracking.Fix
import com.tomessa.miff.domain.tracking.TrackingLimits
import com.tomessa.miff.testsupport.DEVICE_A
import com.tomessa.miff.testsupport.T0
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The writes of the Update (contract with backend/firestore.rules validPhoto, validPhotoLocation, validStatus; ticket criteria U1, U8, U9, L1) and the position
 * of the photo (decision of the PM, 2026-11-06). Pure: Firestore classes are built, nothing is sent.
 */
class UpdateRulesTest {
    private val miffId = "AbCdEfGhIjKlMnOpQrSt"
    private val uid = "uid-1"
    private val jpeg = ByteArray(90_000) { it.toByte() }
    private val photo = UpdatePhoto(jpeg, 461, 1024, T0)
    private val place = PhotoPlace.from(Fix(48.856614, 2.352222, T0 - 60_000L), T0)!!

    // --- The place of a photo -----------------------------------------------------------------------------------------------------

    @Test
    fun aRecentMeasure_becomesAPlace_withFiveDecimals_andTheTimeOfTheMeasure() {
        assertEquals(48.85661, place.lat, 0.0)
        assertEquals(2.35222, place.lng, 0.0)
        assertEquals(T0 - 60_000L, place.measuredAtMillis)
    }

    @Test
    fun aMeasureOf10Minutes_isStillGood_aMeasureOfMoreIsNot() {
        assertNotNull(PhotoPlace.from(Fix(1.0, 1.0, T0 - TrackingLimits.FIX_MAX_AGE_MILLIS), T0))
        assertNull(PhotoPlace.from(Fix(1.0, 1.0, T0 - TrackingLimits.FIX_MAX_AGE_MILLIS - 1), T0))
        // Three hours: the rules would accept it (24 h), the client does not: an old position is a place the person left.
        assertNull(PhotoPlace.from(Fix(1.0, 1.0, T0 - 3 * 60 * 60 * 1000L), T0))
    }

    @Test
    fun noMeasure_noPlace_andNothingThatTheRulesWouldRefuse() {
        assertNull(PhotoPlace.from(null, T0))
        assertNull(PhotoPlace.from(Fix(Double.NaN, 2.0, T0), T0))
        assertNull(PhotoPlace.from(Fix(2.0, Double.POSITIVE_INFINITY, T0), T0))
        assertNull(PhotoPlace.from(Fix(90.1, 2.0, T0), T0))
        assertNull(PhotoPlace.from(Fix(2.0, -180.5, T0), T0))
        assertNotNull(PhotoPlace.from(Fix(90.0, 180.0, T0), T0))
        assertNotNull(PhotoPlace.from(Fix(-90.0, -180.0, T0), T0))
    }

    @Test
    fun aMeasureInTheFuture_aWrongClock_isCappedAtNow_neverAfterThePhoto() {
        val capped = PhotoPlace.from(Fix(1.0, 1.0, T0 + 3_600_000L), T0)
        assertEquals(T0, capped?.measuredAtMillis)
    }

    @Test
    fun aPlaceShowsNothingOfItself() {
        assertEquals("PhotoPlace(redacted)", place.toString())
    }

    // --- The document of the photo ------------------------------------------------------------------------------------------------

    @Test
    fun thePhotoHasExactlyTheKeysOfTheRules_theThreeOfThePlaceAlwaysTogether_andNoDeviceId() {
        val write = UpdateWrites.photo(miffId, uid, photo, place, T0)
        assertEquals("miffs/$miffId/photos/$uid", write.path)
        assertEquals(WriteKind.SET, write.kind)
        assertEquals(setOf("jpeg", "createdAt", "expiresAt", "lat", "lng", "measuredAt"), write.data.keys)
        assertFalse("deviceId" in write.data)
        assertTrue(write.data["jpeg"] is Blob)
        assertEquals(jpeg.size, (write.data["jpeg"] as Blob).toBytes().size)
        assertEquals(place.lat, write.data["lat"])
        assertEquals(place.lng, write.data["lng"])
        assertEquals(Timestamp(java.util.Date(place.measuredAtMillis)), write.data["measuredAt"])
    }

    @Test
    fun theSecondsOfTheServerAreUsed_createdAtIsNotTheClockOfThePhone() {
        val write = UpdateWrites.photo(miffId, uid, photo, place, T0)
        assertEquals(FieldValue.serverTimestamp(), write.data["createdAt"])
    }

    @Test
    fun theLifetimeIs23Hours_wellInsideTheTwentyFiveOfTheRules() {
        val write = UpdateWrites.photo(miffId, uid, photo, place, T0)
        val expires = (write.data["expiresAt"] as Timestamp).toDate().time
        assertEquals(T0 + 23L * 60 * 60 * 1000, expires)
        assertTrue(expires - T0 <= 25L * 60 * 60 * 1000)
        assertTrue(expires > T0)
    }

    @Test
    fun aPhotoOfMoreThan150000Bytes_isNeverBuilt_theRulesWouldRefuseTheWholeLot() {
        val tooBig = UpdatePhoto(ByteArray(150_001), 461, 1024, T0)
        try {
            UpdateWrites.photo(miffId, uid, tooBig, place, T0)
            fail("a photo of 150 001 bytes was built")
        } catch (e: IllegalArgumentException) {
            assertFalse(e.message.orEmpty().contains(miffId))
        }
        // The limit itself is allowed.
        assertNotNull(UpdateWrites.photo(miffId, uid, UpdatePhoto(ByteArray(150_000), 1, 1, T0), place, T0))
        try {
            UpdateWrites.photo(miffId, uid, UpdatePhoto(ByteArray(0), 1, 1, T0), place, T0)
            fail("an empty photo was built")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    // --- The lot of a publication -------------------------------------------------------------------------------------------------

    @Test
    fun publishing_writesThePhotoAndTheStatusTogether_theStatusSaysHasPhoto_andCarriesTheSession() {
        val writes = UpdateWrites.publish(miffId, uid, photo, place, T0, 80, DEVICE_A)
        assertEquals(listOf("miffs/$miffId/photos/$uid", "miffs/$miffId/status/$uid"), writes.map { it.path })
        val status = writes[1]
        assertEquals(WriteKind.UPDATE, status.kind)
        assertEquals(true, status.data["hasPhoto"])
        assertNull(status.data["updateText"])
        assertTrue("updateText" in status.data)
        assertEquals(FieldValue.serverTimestamp(), status.data["lastUpdateAt"])
        assertEquals(DEVICE_A, status.data["deviceId"])
        assertEquals(80, status.data["batteryPct"])
    }

    @Test
    fun theStatusOfAnUpdate_neverTouchesThePresence_norCarriesACoordinate() {
        val status = UpdateWrites.publish(miffId, uid, photo, place, T0, null, DEVICE_A)[1]
        // Criterion U8: `presence`, `placeId`, `presenceAt` (and the access to the location) are left as they are: a partial update.
        listOf("presence", "placeId", "presenceAt", "locationAccess", "escalationDueAt").forEach {
            assertFalse("$it must not be sent", it in status.data)
        }
        listOf("lat", "lng", "measuredAt", "jpeg").forEach {
            assertFalse(
                "$it must not be in a status",
                it in status.data,
            )
        }
    }

    @Test
    fun theLotNeverWritesThePositionDocument() {
        val writes = UpdateWrites.publish(miffId, uid, photo, place, T0, 50, DEVICE_A)
        assertTrue(writes.none { "/positions/" in it.path })
        assertTrue(writes.none { it.kind == WriteKind.DELETE })
    }

    @Test
    fun theCleanUpOfAnExpiredPhoto_deletesIt_andSaysHasPhotoFalse_withoutTouchingTheLastUpdate() {
        val writes = UpdateWrites.removeExpired(miffId, uid, 70, DEVICE_A)
        assertEquals(WriteKind.DELETE, writes[0].kind)
        assertEquals("miffs/$miffId/photos/$uid", writes[0].path)
        val status = writes[1]
        assertEquals(false, status.data["hasPhoto"])
        assertFalse("lastUpdateAt" in status.data)
        assertEquals(DEVICE_A, status.data["deviceId"])
    }

    @Test
    fun aStatusCannotBeBuiltWithoutAWellFormedSession() {
        listOf("", "short", "has space in it 0123456789").forEach { id ->
            try {
                UpdateWrites.publish(miffId, uid, photo, place, T0, 50, id)
                fail("a status was built with '$id'")
            } catch (e: IllegalArgumentException) {
                assertFalse(e.message.orEmpty().contains(id) && id.isNotEmpty())
            }
        }
    }

    // --- The guard-rail ---------------------------------------------------------------------------------------------------------

    private val zone = java.time.ZoneId.of("Europe/Paris")

    @Test
    fun theTwentyFirstPublicationOfTheDay_isRefused_theTwentiethIsNot() {
        val day = PublishGuard.dayKey(T0, zone)
        assertEquals(PublishGuard.Verdict.GO, PublishGuard.check(T0, zone, day, 19, null))
        assertEquals(PublishGuard.Verdict.TOO_MANY, PublishGuard.check(T0, zone, day, 20, null))
    }

    @Test
    fun theCountOfAnotherDayDoesNotCount_theNextMidnightStartsAgain() {
        val yesterday = PublishGuard.dayKey(T0 - 24L * 3_600_000L, zone)
        assertEquals(PublishGuard.Verdict.GO, PublishGuard.check(T0, zone, yesterday, 20, null))
        assertEquals(PublishGuard.dayKey(T0, zone) to 1, PublishGuard.next(T0, zone, yesterday, 20))
        assertEquals(PublishGuard.dayKey(T0, zone) to 6, PublishGuard.next(T0, zone, PublishGuard.dayKey(T0, zone), 5))
    }

    @Test
    fun lessThan30SecondsAfterTheLastOne_itIsTooSoon_atExactly30SecondsItIsNot() {
        assertEquals(PublishGuard.Verdict.TOO_SOON, PublishGuard.check(T0, zone, null, 0, T0 - 29_999L))
        assertEquals(PublishGuard.Verdict.GO, PublishGuard.check(T0, zone, null, 0, T0 - 30_000L))
        assertEquals(PublishGuard.Verdict.TOO_SOON, PublishGuard.check(T0, zone, null, 0, T0))
    }

    @Test
    fun aLastPublicationInTheFuture_aClockSetBack_doesNotBlockForHours() {
        assertEquals(PublishGuard.Verdict.GO, PublishGuard.check(T0, zone, null, 0, T0 + 3_600_000L))
    }

    // --- The permission of the camera ------------------------------------------------------------------------------------------------

    @Test
    fun theCameraIsAskedOnlyAfterTheExplanation_neverCold() {
        assertEquals(
            CameraAccessScreen.EXPLAIN,
            CameraAccess.forOpening(granted = false, explained = false, shouldShowRationale = false),
        )
        // Even if the system says "rationale", the explanation comes first when it was never accepted.
        assertEquals(
            CameraAccessScreen.EXPLAIN,
            CameraAccess.forOpening(granted = false, explained = false, shouldShowRationale = true),
        )
        assertEquals(
            CameraAccessScreen.VIEWFINDER,
            CameraAccess.forOpening(granted = true, explained = false, shouldShowRationale = false),
        )
    }

    @Test
    fun afterOneRefusalTheBoxCanComeBack_afterTwoOnlyTheSettingsCanAllowIt() {
        assertEquals(CameraAccessScreen.RETRY, CameraAccess.afterRequest(granted = false, shouldShowRationale = true))
        assertEquals(
            CameraAccessScreen.DISABLED,
            CameraAccess.afterRequest(granted = false, shouldShowRationale = false),
        )
        assertEquals(
            CameraAccessScreen.VIEWFINDER,
            CameraAccess.afterRequest(granted = true, shouldShowRationale = false),
        )
        // On a later opening, the same reading.
        assertEquals(
            CameraAccessScreen.RETRY,
            CameraAccess.forOpening(granted = false, explained = true, shouldShowRationale = true),
        )
        assertEquals(
            CameraAccessScreen.DISABLED,
            CameraAccess.forOpening(granted = false, explained = true, shouldShowRationale = false),
        )
    }
}
