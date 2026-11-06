package com.tomessa.miff.data.session

import com.tomessa.miff.testsupport.DEVICE_A
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.FakeSessionDocuments
import com.tomessa.miff.testsupport.MemoryDeviceIdStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "closed" state of the session during the deletion of an account, kept on the DISK (AUTH-5b, review of AUTH-5a): a geofence receiver that wakes a COLD process
 * in the middle of the deletion has no memory of it, the identifier of the session may already be forgotten and the session document deleted, and "no identifier +
 * no session" would look like an installation from before the feature and WRITE a new session for an account that is going away. With the marker on the disk,
 * that process starts closed: nothing is verified, nothing is written, no session is ever opened. Same idea as the "replaced" marker.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSessionPersistedClosedTest {
    private val uid = "uid-1"

    private class Rig(
        scope: CoroutineScope,
        val ids: MemoryDeviceIdStore = MemoryDeviceIdStore(),
        val docs: FakeSessionDocuments = FakeSessionDocuments(),
    ) {
        var replacedCalls = 0
        val device =
            DeviceSession(
                ids = ids,
                documents = docs,
                network = FakeNetwork(),
                currentUid = { "uid-1" },
                scope = scope,
                onReplaced = { replacedCalls++ },
                newId = { "device-new-0123456789abcdef" },
                nowMillis = { 1_000_000L },
                retryPausesMillis = emptyList(),
            )
    }

    private fun run(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher()) { block() }

    @Test
    fun theControl_withoutTheMarker_aProcessWithNoIdentifierAndNoSession_writesANewOne() =
        run {
            // This is the very danger: an installation from before the feature (no identifier, no session) gets a session at its first look.
            val rig = Rig(backgroundScope)
            assertEquals(Ensured.VERIFIED, rig.device.ensure(uid))
            assertEquals("device-new-0123456789abcdef", rig.docs.sessions[uid])
        }

    @Test
    fun suspendForDeletion_putsTheMarkerOnTheDisk_beforeAnythingElse() =
        run {
            val rig = Rig(backgroundScope, ids = MemoryDeviceIdStore(DEVICE_A))
            rig.device.suspendForDeletion()
            assertTrue(rig.ids.closed)
            assertEquals(Clearance.Unverified, rig.device.clearance(recheck = true))
        }

    @Test
    fun aColdProcessInTheMiddleOfTheDeletion_startsClosed_andNeverWritesANewSession() =
        run {
            // The first process deleted the session and forgot the identifier, then it was killed (or a receiver woke another process).
            val first = Rig(backgroundScope, ids = MemoryDeviceIdStore(DEVICE_A))
            first.docs.sessions[uid] = DEVICE_A
            first.device.suspendForDeletion()
            first.device.releaseForAccountDeletion(uid)
            assertNull("the identifier is forgotten", first.ids.id)
            assertNull("the session is deleted", first.docs.sessions[uid])
            val changes = first.docs.changes.toList()

            val cold = Rig(backgroundScope, ids = first.ids, docs = first.docs)
            assertEquals(Ensured.UNREACHABLE, cold.device.ensure(uid))
            assertEquals(Clearance.Unverified, cold.device.clearance(recheck = true))
            assertEquals(Clearance.Unverified, cold.device.clearance(recheck = false))
            assertFalse(cold.device.afterRefusal())
            assertEquals("not a single write, not a single new session", changes, cold.docs.changes)
            assertNull("no identifier drawn either", cold.ids.id)
            assertEquals("and it is not a replacement either: no forced sign-out", 0, cold.replacedCalls)
        }

    @Test
    fun aColdProcessKilledBeforeTheSessionWasDeleted_alsoStartsClosed_andDoesNotVerifyIt() =
        run {
            val first = Rig(backgroundScope, ids = MemoryDeviceIdStore(DEVICE_A))
            first.docs.sessions[uid] = DEVICE_A
            first.device.suspendForDeletion()
            val reads = first.docs.reads
            val cold = Rig(backgroundScope, ids = first.ids, docs = first.docs)
            assertEquals(Clearance.Unverified, cold.device.clearance(recheck = true))
            assertEquals("the server is not even asked", reads, first.docs.reads)
        }

    @Test
    fun theDeletionCancelled_reopensTheSession_andClearsTheMarker() =
        run {
            val rig = Rig(backgroundScope, ids = MemoryDeviceIdStore(DEVICE_A))
            rig.docs.sessions[uid] = DEVICE_A
            rig.device.suspendForDeletion()
            rig.device.reopen()
            assertFalse(rig.ids.closed)
            assertTrue(rig.device.clearance(recheck = true) is Clearance.Cleared)
            // A process started later is open too.
            val cold = Rig(backgroundScope, ids = rig.ids, docs = rig.docs)
            assertTrue(cold.device.clearance(recheck = true) is Clearance.Cleared)
        }

    @Test
    fun theNextSignIn_clearsTheMarker_evenIfTheSessionCannotBeWritten() =
        run {
            val rig = Rig(backgroundScope, ids = MemoryDeviceIdStore(DEVICE_A))
            rig.device.suspendForDeletion()
            rig.docs.offline = true
            assertFalse(rig.device.begin(uid))
            assertFalse("the person chose to sign in here: this phone is no longer closed", rig.ids.closed)
        }

    @Test
    fun theMarkerIsOnlyAboutTheDeletion_aVoluntarySignOutDoesNotLeaveOne() =
        run {
            val rig = Rig(backgroundScope, ids = MemoryDeviceIdStore(DEVICE_A))
            rig.docs.sessions[uid] = DEVICE_A
            rig.device.release(uid)
            assertFalse(rig.ids.closed)
        }
}
