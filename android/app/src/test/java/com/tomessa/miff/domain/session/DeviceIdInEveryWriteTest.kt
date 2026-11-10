package com.tomessa.miff.domain.session

import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.LocationAccess
import com.tomessa.miff.domain.miff.MiffWrites
import com.tomessa.miff.domain.miff.ProfileCopy
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.StatusFact
import com.tomessa.miff.domain.tracking.TrackingWrites
import com.tomessa.miff.domain.travel.TravelPeriod
import com.tomessa.miff.testsupport.DEVICE_A
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Contract SESS-1 (backend/data-model.md, "Un compte, un seul appareil"): `deviceId` is in EVERY write of a status and of a position, the partial
 * updates included. The rules cannot guarantee it (a partial update without the key passes when the base already holds the session's value), so
 * these tests do: one builds every write the app knows, another reads the source and fails if a status or a position is written anywhere else.
 */
class DeviceIdInEveryWriteTest {
    private val uid = "uid-1"
    private val miffId = "AbCdEfGhIjKlMnOpQrSt"
    private val code = "XTZRSP367988"
    private val profile = ProfileCopy("Léa", "Martin", "+33612345678", null)
    private val now = 1_800_000_000_000L
    private val arrival = StatusFact(PresenceValue.IN_TRUSTED, "home", now, setLastUpdate = true)
    private val departure = StatusFact(PresenceValue.OUT, "home", now, setLastUpdate = false)
    private val trip = TravelPeriod(now + 86_400_000L, "2027-01-16")

    private fun isStatusOrPosition(write: DocWrite) = "/status/" in write.path || "/positions/" in write.path

    @Test
    fun everyWriteOfAStatusOrOfAPosition_thatTheAppCanBuild_carriesTheIdentifier() {
        val writes =
            listOf(
                TrackingWrites.statusUpdate(miffId, uid, arrival, now, 80, LocationAccess.BACKGROUND, DEVICE_A),
                TrackingWrites.statusUpdate(miffId, uid, departure, now, null, LocationAccess.BACKGROUND, DEVICE_A),
                TrackingWrites.accessUpdate(miffId, uid, 50, LocationAccess.DENIED, DEVICE_A),
                TrackingWrites.batteryUpdate(miffId, uid, 50, DEVICE_A),
                TrackingWrites.position(miffId, uid, 48.8566, 2.3522, now, DEVICE_A),
                // UPDATE-1: the status of a manual update, and of the clean-up of an expired photo.
                TrackingWrites.photoStatus(miffId, uid, photoPublished = true, batteryPct = 80, deviceId = DEVICE_A),
                TrackingWrites.photoStatus(miffId, uid, photoPublished = false, batteryPct = null, deviceId = DEVICE_A),
                // MODE VOYAGE: the start of a trip, the move of its end, and the status that ends a trip.
                TrackingWrites.travelStart(miffId, uid, trip, now, DEVICE_A),
                TrackingWrites.travelDate(miffId, uid, trip, DEVICE_A),
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
            ) +
                MiffWrites.create(uid, miffId, code, "Chez nous", profile, "free", LocationAccess.UNKNOWN, DEVICE_A) +
                MiffWrites.join(uid, miffId, code, profile, "free", LocationAccess.UNKNOWN, DEVICE_A) +
                MiffWrites.resumeSharing(miffId, uid, LocationAccess.UNKNOWN, DEVICE_A)
        val statusAndPositionWrites = writes.filter { isStatusOrPosition(it) }
        // 10 writes of tracking (the status update twice, the access, the battery, the position, the two of the Update, the two of a trip, the end of a trip) + the status of create, join and resume.
        assertEquals(13, statusAndPositionWrites.size)
        statusAndPositionWrites.forEach { write ->
            val what = "${write.kind} of ${write.path.split("/")[2]}"
            assertTrue("$what is a set or a (partial) update", write.kind != WriteKind.DELETE)
            assertEquals("$what has no identifier of the session", DEVICE_A, write.data["deviceId"])
        }
    }

    @Test
    fun aPartialUpdate_alsoCarriesIt_asTheRulesCannotDemandIt() {
        val partial = TrackingWrites.statusUpdate(miffId, uid, departure, now, 80, LocationAccess.BACKGROUND, DEVICE_A)
        assertEquals(WriteKind.UPDATE, partial.kind)
        assertFalse("lastUpdateAt" in partial.data)
        assertEquals(DEVICE_A, partial.data["deviceId"])
        val access = TrackingWrites.accessUpdate(miffId, uid, 80, LocationAccess.BACKGROUND, DEVICE_A)
        assertEquals(WriteKind.UPDATE, access.kind)
        assertEquals(DEVICE_A, access.data["deviceId"])
        val battery = TrackingWrites.batteryUpdate(miffId, uid, 80, DEVICE_A)
        assertEquals(WriteKind.UPDATE, battery.kind)
        assertEquals(DEVICE_A, battery.data["deviceId"])
    }

    @Test
    fun theDeletionsOfAStatusAndOfAPosition_carryNoContent_andNoIdentifier() {
        val leave = MiffWrites.leave(miffId, uid, "free") + MiffWrites.stopSharing(miffId, uid)
        leave.filter { isStatusOrPosition(it) }.forEach {
            assertEquals(WriteKind.DELETE, it.kind)
            assertNull(it.data["deviceId"])
        }
    }

    @Test
    fun aWriteCannotBeBuiltWithoutAWellFormedIdentifier() {
        val bad = listOf("", "short", "has space in it 0123456789", "accentué-0123456789abcdef", "x".repeat(65))
        bad.forEach { id ->
            val builders =
                listOf<() -> Any>(
                    { TrackingWrites.statusUpdate(miffId, uid, arrival, now, 80, LocationAccess.BACKGROUND, id) },
                    { TrackingWrites.accessUpdate(miffId, uid, 50, LocationAccess.DENIED, id) },
                    { TrackingWrites.batteryUpdate(miffId, uid, 50, id) },
                    { TrackingWrites.position(miffId, uid, 48.8566, 2.3522, now, id) },
                    { TrackingWrites.photoStatus(miffId, uid, photoPublished = true, batteryPct = 50, deviceId = id) },
                    { TrackingWrites.travelStart(miffId, uid, trip, now, id) },
                    { TrackingWrites.travelDate(miffId, uid, trip, id) },
                    { MiffWrites.initialStatus(LocationAccess.UNKNOWN, id) },
                    { MiffWrites.resumeSharing(miffId, uid, LocationAccess.UNKNOWN, id) },
                )
            builders.forEach { build ->
                try {
                    build()
                    fail("a write was built with the identifier '$id'")
                } catch (e: IllegalArgumentException) {
                    // expected
                }
            }
        }
    }

    @Test
    fun theIdentifierIsNeverInTheMessageOfTheError() {
        try {
            TrackingWrites.position(miffId, uid, 1.0, 1.0, now, "bad id")
            fail()
        } catch (e: IllegalArgumentException) {
            assertFalse(e.message.orEmpty().contains("bad id"))
        }
    }

    private val sourceRoot = File("src/main/java")

    private fun kotlinSources(): List<File> {
        assertTrue("source folder not found from ${File(".").absolutePath}", sourceRoot.exists())
        return sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    // A construction of a write on a status or a position that is not a deletion: `DocWrite("miffs/..../status/...", WriteKind.SET|UPDATE`.
    private val statusOrPositionWrite =
        Regex("""DocWrite\(\s*"[^"]*/(status|positions)/[^"]*"\s*,\s*WriteKind\.(SET|UPDATE)""")

    @Test
    fun theTwoBuilderFiles_areNotExemptInBulk_theirWritesAreCounted() {
        // 7 in TrackingWrites (status update, access update, battery update, the status of the Update, position, the start of a trip, the move of its end) + 3 in MiffWrites (first status of create, join, resume).
        // A new write of a status or a position there forces this number, and the test of the builders above, to be updated on purpose.
        val counts =
            kotlinSources()
                .filter { it.name == "TrackingWrites.kt" || it.name == "MiffWrites.kt" }
                .associate { it.name to statusOrPositionWrite.findAll(it.readText()).count() }
        assertEquals(mapOf("TrackingWrites.kt" to 7, "MiffWrites.kt" to 3), counts)
    }

    @Test
    fun noOtherSourceFileWritesAStatusOrAPosition() {
        val offenders =
            kotlinSources()
                .filter { it.name != "TrackingWrites.kt" && it.name != "MiffWrites.kt" }
                .filter { statusOrPositionWrite.containsMatchIn(it.readText()) }
                .map { it.name }
        assertTrue("these files write a status or a position by themselves: $offenders", offenders.isEmpty())
    }

    // A DIRECT write on the status or position collections, whatever the file: `collection("status")` (or "positions") followed, in the same chain of
    // calls, by `.set(`, `.update(` or `.add(`. A READ of these collections (get, listener, query) is legitimate (the Feed reads them) and is not matched:
    // the chain is a sequence of `.name(...)` calls, nothing else, so a read followed by an unrelated statement does not reach a later `set(`.
    private val directWriteByCollection =
        Regex(
            """collection\(\s*"(status|positions)"\s*\)""" +
                """(?:\s*\.(?!set\(|update\(|add\()\w+\((?:[^()]|\([^()]*\))*\))*""" +
                """\s*\.(set|update|add)\s*\(""",
        )

    // The same by a path string: `document("miffs/x/status/y").set(`.
    private val directWriteByPath =
        Regex("""document\(\s*"[^"]*/(status|positions)/[^"]*"\s*\)\s*\.(set|update)\s*\(""")

    private fun isDirectWrite(source: String) =
        directWriteByCollection.containsMatchIn(source) || directWriteByPath.containsMatchIn(source)

    @Test
    fun noSourceFileWritesTheStatusOrPositionCollectionsDirectly() {
        val offenders = kotlinSources().filter { isDirectWrite(it.readText()) }.map { it.name }
        assertTrue("these files write status / positions without the builders: $offenders", offenders.isEmpty())
    }

    @Test
    fun theDirectWriteDetector_catchesTheWrites_inAnyShapeOfChain() {
        val writes =
            listOf(
                """firestore().collection("status").document(uid).set(map)""",
                """db.collection("miffs").document(id).collection("positions").document(uid).set(position)""",
                """ref
    .collection("status")
    .document(uid)
    .update(mapOf("a" to 1))""",
                """miff.collection("status").add(data)""",
                """db.document("miffs/a/status/b").set(data)""",
                """db.document("miffs/a/positions/b").update(data)""",
            )
        writes.forEach { assertTrue("not caught: $it", isDirectWrite(it)) }
    }

    @Test
    fun theDirectWriteDetector_letsTheReadsThrough_theFeedReadsStatusesAndPositions() {
        val reads =
            listOf(
                """firestore().collection("status").document(uid).get(Source.SERVER).await()""",
                """db.collection("miffs").document(id).collection("positions").document(uid).get().await()""",
                """ref.collection("status")
    .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error -> }""",
                """miff.collection("status").get().await().documents.map { it.id }""",
                // A read, then a write to ANOTHER document in a later statement, is not a write of the collection.
                """val ref = db.collection("status").document(uid)
transaction.set(other, data)""",
                """db.document("miffs/a/status/b").get().await()""",
            )
        reads.forEach { assertFalse("a read was taken for a write: $it", isDirectWrite(it)) }
    }
}
