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
                TrackingWrites.statusCreate(miffId, uid, arrival, now, 80, LocationAccess.BACKGROUND, DEVICE_A),
                TrackingWrites.accessUpdate(miffId, uid, 50, LocationAccess.DENIED, DEVICE_A),
                TrackingWrites.position(miffId, uid, 48.8566, 2.3522, now, DEVICE_A),
            ) +
                MiffWrites.create(uid, miffId, code, "Chez nous", profile, "free", LocationAccess.UNKNOWN, DEVICE_A) +
                MiffWrites.join(uid, miffId, code, profile, "free", LocationAccess.UNKNOWN, DEVICE_A) +
                MiffWrites.resumeSharing(miffId, uid, LocationAccess.UNKNOWN, DEVICE_A)
        val statusAndPositionWrites = writes.filter { isStatusOrPosition(it) }
        // 5 builders of tracking + the status of create, join and resume.
        assertEquals(8, statusAndPositionWrites.size)
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
                    { TrackingWrites.statusCreate(miffId, uid, arrival, now, 80, LocationAccess.BACKGROUND, id) },
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

    @Test
    fun noSourceFileWritesAStatusOrAPositionOutsideTheTwoBuilders() {
        // A new writer of a status or of a position added anywhere else would escape the tests above: it must go through TrackingWrites / MiffWrites.
        val allowed = setOf("TrackingWrites.kt", "MiffWrites.kt")
        val root = File("src/main/java")
        assertTrue("source folder not found from ${File(".").absolutePath}", root.exists())
        val pathOfADocWrite = Regex("""DocWrite\(\s*"[^"]*/(status|positions)/""")
        val offenders =
            root
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name !in allowed }
                .filter { file -> pathOfADocWrite.containsMatchIn(file.readText()) }
                .map { it.name }
                .toList()
        assertTrue("these files write a status or a position by themselves: $offenders", offenders.isEmpty())
    }
}
