package com.tomessa.miff.domain.tracking

import com.google.firebase.firestore.FieldValue
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.LocationAccess
import com.tomessa.miff.domain.miff.MiffWrites
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.domain.travel.TravelPeriod
import com.tomessa.miff.domain.update.PhotoPlace
import com.tomessa.miff.domain.update.UpdatePhoto
import com.tomessa.miff.domain.update.UpdateWrites
import com.tomessa.miff.testsupport.DEVICE_A
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * FEED-ORDRE-PHOTO, T20 and criterion B14 (docs/tickets/feed-ordre-photo.md, sections 5, 8 and 9): `photoAt`, the date the Feed sorts the cards by, is written ONLY by the
 * publication of a photo, with the SERVER time, and by no other write. The rules (backend/firestore.rules `validPhotoAt`) accept it only as the server time with `hasPhoto`
 * true, or kept as stored: these tests make sure the app never asks for anything else.
 */
class PhotoDateWritesTest {
    private val uid = "uid-1"
    private val miffId = "AbCdEfGhIjKlMnOpQrSt"
    private val now = 1_800_000_000_000L
    private val arrival = StatusFact(PresenceValue.IN_TRUSTED, "home", now, setLastUpdate = true)
    private val departure = StatusFact(PresenceValue.OUT, "home", now, setLastUpdate = false)
    private val trip = TravelPeriod(now + 86_400_000L, "2027-01-16")
    private val photo = UpdatePhoto(ByteArray(1_000) { it.toByte() }, 461, 1024, takenAtMillis = now - 20_000L)
    private val place = PhotoPlace.from(Fix(48.8566, 2.3522, now - 30_000L), now)!!

    @Test
    fun t20_thePublicationOfAPhoto_writesPhotoAtAsTheServerTime_withHasPhotoAndLastUpdateAt() {
        val data =
            TrackingWrites
                .photoStatus(
                    miffId,
                    uid,
                    photoPublished = true,
                    batteryPct = 80,
                    deviceId = DEVICE_A,
                ).data
        assertEquals(FieldValue.serverTimestamp(), data["photoAt"])
        assertEquals(true, data["hasPhoto"])
        assertEquals(FieldValue.serverTimestamp(), data["lastUpdateAt"])
        assertEquals(FieldValue.serverTimestamp(), data["updatedAt"])
        // Never the clock of the phone: the value is the marker of the server, not a date.
        assertFalse(
            data["photoAt"] is Number || data["photoAt"] is java.util.Date ||
                data["photoAt"] is com.google.firebase.Timestamp,
        )
    }

    @Test
    fun t20_thePublicationInOneMiff_carriesPhotoAtInTheStatusOnly_neverInThePhoto() {
        val writes = UpdateWrites.publish(miffId, uid, photo, place, now, 80, DEVICE_A)
        val status = writes.single { "/status/" in it.path }
        val photoDoc = writes.single { "/photos/" in it.path }
        assertEquals(FieldValue.serverTimestamp(), status.data["photoAt"])
        assertFalse(photoDoc.data.containsKey("photoAt"))
        assertEquals(WriteKind.UPDATE, status.kind)
    }

    @Test
    fun t20_theCleanUpOfAnExpiredPhoto_doesNotWritePhotoAt_norLastUpdateAt() {
        val data =
            TrackingWrites
                .photoStatus(
                    miffId,
                    uid,
                    photoPublished = false,
                    batteryPct = null,
                    deviceId = DEVICE_A,
                ).data
        assertEquals(false, data["hasPhoto"])
        assertFalse(data.containsKey("photoAt"))
        assertFalse(data.containsKey("lastUpdateAt"))
        val viaUpdate = UpdateWrites.removeExpired(miffId, uid, 50, DEVICE_A).single { "/status/" in it.path }
        assertFalse(viaUpdate.data.containsKey("photoAt"))
    }

    @Test
    fun t20_everyOtherWriteOfAStatus_doesNotWritePhotoAt() {
        val others: List<DocWrite> =
            listOf(
                // The arrival in a trusted place (it DOES write lastUpdateAt: the colour restarts, the order does not move), a departure, the battery, the access, a trip.
                TrackingWrites.statusUpdate(miffId, uid, arrival, now, 80, LocationAccess.BACKGROUND, DEVICE_A),
                TrackingWrites.statusUpdate(miffId, uid, departure, now, null, LocationAccess.BACKGROUND, DEVICE_A),
                TrackingWrites.statusUpdate(
                    miffId,
                    uid,
                    arrival,
                    now,
                    80,
                    LocationAccess.BACKGROUND,
                    DEVICE_A,
                    clearTravel = true,
                ),
                TrackingWrites.accessUpdate(miffId, uid, 50, LocationAccess.DENIED, DEVICE_A),
                TrackingWrites.batteryUpdate(miffId, uid, 50, DEVICE_A),
                TrackingWrites.travelStart(miffId, uid, trip, now, DEVICE_A),
                TrackingWrites.travelDate(miffId, uid, trip, DEVICE_A),
                // The initial status (a full `set`, at the activation of the sharing): no date, the rules take it as "no photo yet".
                DocWrite(
                    "miffs/$miffId/status/$uid",
                    WriteKind.SET,
                    MiffWrites.initialStatus(LocationAccess.BACKGROUND, DEVICE_A),
                ),
            )
        others.forEachIndexed { i, write ->
            assertFalse("write #$i names photoAt", write.data.containsKey("photoAt"))
        }
        // The arrival moves lastUpdateAt (and so the colour) but the order of the Feed does not follow it: that is the point of the ticket.
        assertTrue(others[0].data.containsKey("lastUpdateAt"))
    }

    @Test
    fun theSecondAttempt_withoutPhotoAt_isTheFirstOneMinusThatKeyAndNothingElse() {
        val stamped = TrackingWrites.photoStatus(miffId, uid, true, 80, DEVICE_A, stampPhotoDate = true).data
        val plain = TrackingWrites.photoStatus(miffId, uid, true, 80, DEVICE_A, stampPhotoDate = false).data
        assertFalse(plain.containsKey("photoAt"))
        assertEquals(stamped - "photoAt", plain)
        assertNotEquals(stamped.keys, plain.keys)
        // Through the Update: the photo document is identical, the status is the plain one.
        val withDate = UpdateWrites.publish(miffId, uid, photo, place, now, 80, DEVICE_A, null, true)
        val without = UpdateWrites.publish(miffId, uid, photo, place, now, 80, DEVICE_A, null, false)
        assertEquals(withDate.map { it.path }, without.map { it.path })
        assertEquals(withDate[0].data.keys, without[0].data.keys)
        assertEquals(plain, without[1].data)
    }

    @Test
    fun photoAtIsNamedInTheSourceOfTheApp_onlyWhereItIsWrittenAndWhereItIsRead() {
        // Source scan (like DeviceIdInEveryWriteTest): no other file of the app may name the key, so no other write can send it.
        val main = File("src/main/java")
        val namedIn =
            main
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { Regex("\"photoAt\"").containsMatchIn(it.readText()) }
                .map { it.name }
                .toSet()
        assertEquals(setOf("TrackingWrites.kt", "StatusObserver.kt"), namedIn)
    }

    @Test
    fun photoAtIsReadWithTheUntypedGetter_soAWrongTypeCanNeverBringTheStatusDown() {
        // No mock library is available to build a DocumentSnapshot: this pins the way the key is read. `get(field, behavior)` returns the raw value (never a cast
        // exception, unlike getTimestamp), and photoAtMillisOf turns anything that is not a time into "no date" (T14); the rest of the status is read apart.
        val text = File("src/main/java/com/tomessa/miff/data/feed/StatusObserver.kt").readText()
        assertTrue(text.contains("photoAtMillisOf(doc.get(\"photoAt\""))
        assertFalse(text.contains("getTimestamp(\"photoAt\""))
    }
}
