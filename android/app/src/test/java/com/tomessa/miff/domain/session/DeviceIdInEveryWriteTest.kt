package com.tomessa.miff.domain.session

import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.LocationAccess
import com.tomessa.miff.domain.miff.MiffWrites
import com.tomessa.miff.domain.miff.ProfileCopy
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.StatusFact
import com.tomessa.miff.domain.tracking.TrackingWrites
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

    private fun isStatusOrPosition(write: DocWrite) = "/status/" in write.path || "/positions/" in write.path

    @Test
    fun everyWriteOfAStatusOrOfAPosition_thatTheAppCanBuild_carriesTheIdentifier() {
        val writes =
            listOf(
                TrackingWrites.statusUpdate(miffId, uid, arrival, now, 80, LocationAccess.BACKGROUND, DEVICE_A),
                TrackingWrites.statusUpdate(miffId, uid, departure, now, null, LocationAccess.BACKGROUND, DEVICE_A),
                TrackingWrites.accessUpdate(miffId, uid, 50, LocationAccess.DENIED, DEVICE_A),
                TrackingWrites.position(miffId, uid, 48.8566, 2.3522, now, DEVICE_A),
            ) +
                MiffWrites.create(uid, miffId, code, "Chez nous", profile, "free", LocationAccess.UNKNOWN, DEVICE_A) +
                MiffWrites.join(uid, miffId, code, profile, "free", LocationAccess.UNKNOWN, DEVICE_A) +
                MiffWrites.resumeSharing(miffId, uid, LocationAccess.UNKNOWN, DEVICE_A)
        val statusAndPositionWrites = writes.filter { isStatusOrPosition(it) }
        // 4 writes of tracking (the status update twice) + the status of create, join and resume.
        assertEquals(7, statusAndPositionWrites.size)
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
                    { TrackingWrites.position(miffId, uid, 48.8566, 2.3522, now, id) },
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
        // 3 in TrackingWrites (status update, access update, position) + 3 in MiffWrites (first status of create, join, resume).
        // A new write of a status or a position there forces this number, and the test of the builders above, to be updated on purpose.
        val counts =
            kotlinSources()
                .filter { it.name == "TrackingWrites.kt" || it.name == "MiffWrites.kt" }
                .associate { it.name to statusOrPositionWrite.findAll(it.readText()).count() }
        assertEquals(mapOf("TrackingWrites.kt" to 3, "MiffWrites.kt" to 3), counts)
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

    @Test
    fun noSourceFileGoesToTheStatusOrPositionCollectionByItself() {
        // Another way to write one: `collection("status").document(..).set(..)`. There is no reader of these collections in the app today; one that appears
        // must be reviewed (and this test adjusted) rather than slip a write past the contract.
        val byCollection = Regex("""collection\(\s*"(status|positions)"\s*\)""")
        val byPathString = Regex("""document\(\s*"[^"]*/(status|positions)/""")
        val offenders =
            kotlinSources()
                .filter { byCollection.containsMatchIn(it.readText()) || byPathString.containsMatchIn(it.readText()) }
                .map { it.name }
        assertTrue("these files reach status / positions without the builders: $offenders", offenders.isEmpty())
    }
}
