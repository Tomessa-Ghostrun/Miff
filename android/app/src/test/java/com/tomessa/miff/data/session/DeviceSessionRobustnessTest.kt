package com.tomessa.miff.data.session

import com.tomessa.miff.testsupport.DEVICE_A
import com.tomessa.miff.testsupport.DEVICE_B
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.FakeSessionDocuments
import com.tomessa.miff.testsupport.MemoryDeviceIdStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The corners of [DeviceSession] (review of PR #80): the identifier is on the disk BEFORE the server hears of it, a process killed in the middle of a forced
 * sign-out stays replaced, a dead listener starts over. The session logic alone, on a fake server and a fake private file.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSessionRobustnessTest {
    private val uid = "uid-1"

    private class Rig(
        scope: CoroutineScope,
        val ids: MemoryDeviceIdStore = MemoryDeviceIdStore(),
        val docs: FakeSessionDocuments = FakeSessionDocuments(),
        val network: FakeNetwork = FakeNetwork(),
        var signedInAs: String? = "uid-1",
        retryPauses: List<Long> = emptyList(),
        nextId: String = "device-new-0123456789abcdef",
    ) {
        var replacedCalls = 0
        val goneAsked = mutableListOf<Boolean>()
        val device =
            DeviceSession(
                ids = ids,
                documents = docs,
                network = network,
                currentUid = { signedInAs },
                scope = scope,
                onReplaced = { gone ->
                    replacedCalls++
                    goneAsked += gone
                },
                newId = { nextId },
                nowMillis = { 1_000_000L },
                retryPausesMillis = retryPauses,
            )
    }

    private fun run(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher()) { block() }

    // --- The identifier is kept on the disk first (6) ------------------------------------------------------------------

    @Test
    fun begin_putsTheIdentifierOnTheDisk_beforeTheServerHearsOfIt() =
        run {
            val rig = Rig(backgroundScope)
            val heldWhenTheServerWrote = mutableListOf<String?>()
            rig.docs.beforeWrite = { heldWhenTheServerWrote += rig.ids.id }
            assertTrue(rig.device.begin(uid))
            assertEquals(listOf<String?>("device-new-0123456789abcdef"), heldWhenTheServerWrote)
            assertEquals("device-new-0123456789abcdef", rig.docs.sessions[uid])
        }

    @Test
    fun begin_isFalse_andWritesNothing_whenTheDiskRefusesTheIdentifier() =
        run {
            val rig = Rig(backgroundScope)
            rig.ids.failSave = true
            assertFalse(rig.device.begin(uid))
            assertTrue("no session written with an identifier nobody keeps", rig.docs.changes.isEmpty())
            assertNull(rig.ids.id)
        }

    @Test
    fun begin_forgetsTheIdentifier_whenTheServerDoesNotConfirm() =
        run {
            val rig = Rig(backgroundScope)
            rig.docs.offline = true
            assertFalse(rig.device.begin(uid))
            assertNull("an identifier whose session was never written is not kept", rig.ids.id)
        }

    @Test
    fun anOldInstallation_writesItsSessionOnlyOnceTheIdentifierIsOnTheDisk() =
        run {
            val rig = Rig(backgroundScope)
            rig.ids.failSave = true
            assertEquals(Ensured.UNREACHABLE, rig.device.ensure(uid))
            assertTrue(rig.docs.changes.isEmpty())
            rig.ids.failSave = false
            assertEquals(Ensured.VERIFIED, rig.device.ensure(uid))
            assertEquals(rig.ids.id, rig.docs.sessions[uid])
        }

    // --- A process killed during a forced sign-out stays replaced (4) -------------------------------------------------------

    @Test
    fun theReplacedMarker_isOnTheDiskBeforeTheForcedSignOutIsAsked() =
        run {
            val rig = Rig(backgroundScope, ids = MemoryDeviceIdStore(DEVICE_A))
            rig.docs.sessions[uid] = DEVICE_B
            var markerWhenAsked: Boolean? = null
            val device =
                DeviceSession(
                    ids = rig.ids,
                    documents = rig.docs,
                    network = rig.network,
                    currentUid = { uid },
                    scope = backgroundScope,
                    onReplaced = { markerWhenAsked = rig.ids.replaced },
                )
            assertEquals(Ensured.REPLACED, device.ensure(uid))
            assertEquals("the marker was on the disk when the sign-out was asked for", true, markerWhenAsked)
        }

    @Test
    fun aProcessKilledAfterASessionGone_wakesUpStillReplaced_andRemembersWhy() =
        run {
            val first = Rig(backgroundScope, ids = MemoryDeviceIdStore(DEVICE_A))
            // The session document is gone while this phone has an identifier: the account is being deleted elsewhere.
            assertEquals(Ensured.REPLACED, first.device.ensure(uid))
            assertEquals(listOf(true), first.goneAsked)
            assertTrue(first.ids.gone)
            // The process dies before the forced sign-out finished: the next one asks again, with the SAME reason (never "used on another phone").
            val second = Rig(backgroundScope, ids = first.ids, docs = first.docs)
            assertEquals(Ensured.REPLACED, second.device.ensure(uid))
            assertEquals(listOf(true), second.goneAsked)
        }

    @Test
    fun aReplacementByAnotherPhone_isNeverMarkedGone() =
        run {
            val rig = Rig(backgroundScope, ids = MemoryDeviceIdStore(DEVICE_A))
            rig.docs.sessions[uid] = DEVICE_B
            assertEquals(Ensured.REPLACED, rig.device.ensure(uid))
            assertEquals(listOf(false), rig.goneAsked)
            assertFalse(rig.ids.gone)
        }

    @Test
    fun aProcessKilledAfterTheReplacement_wakesUpStillReplaced_andNeverWritesANewSession() =
        run {
            val first = Rig(backgroundScope, ids = MemoryDeviceIdStore(DEVICE_A))
            first.docs.sessions[uid] = DEVICE_B
            assertEquals(Ensured.REPLACED, first.device.ensure(uid))
            assertEquals(1, first.replacedCalls)
            // The process dies here: the forced sign-out never finished (still signed in). The other phone's session then vanishes from the server
            // (without the marker, "no session + no identifier" would look like an installation from before the feature and WRITE a new one).
            first.docs.remove(uid)
            val changes = first.docs.changes.toList()

            val second = Rig(backgroundScope, ids = first.ids, docs = first.docs)
            assertEquals(Ensured.REPLACED, second.device.ensure(uid))
            assertEquals(Clearance.Replaced, second.device.clearance(recheck = true))
            assertTrue(second.device.afterRefusal())
            assertEquals("the forced sign-out is taken up again", true, second.replacedCalls >= 1)
            assertEquals("no new session was written", changes, first.docs.changes)
            assertNull(second.docs.sessions[uid])
        }

    @Test
    fun theMarker_endsAtTheNextSignIn_andAtAVoluntarySignOut() =
        run {
            val rig = Rig(backgroundScope)
            rig.ids.replaced = true
            val restarted = Rig(backgroundScope, ids = rig.ids, docs = rig.docs)
            assertEquals(Clearance.Replaced, restarted.device.clearance(recheck = false))
            assertTrue(restarted.device.begin(uid))
            assertFalse(rig.ids.replaced)
            assertTrue(restarted.device.clearance(recheck = false) is Clearance.Cleared)

            val other = Rig(backgroundScope, ids = MemoryDeviceIdStore(DEVICE_A).also { it.replaced = true })
            other.device.release(uid)
            assertFalse(other.ids.replaced)
        }

    // --- A pass already under way after a voluntary sign-out finds nothing to rewrite (review of 74a74ac) -----------------------------

    @Test
    fun afterTheSessionIsGivenBack_nothingIsVerifiedOrWrittenAnyMore_neverANewSession_untilTheNextSignIn() =
        run {
            val rig = Rig(backgroundScope)
            assertTrue(rig.device.begin(uid))
            rig.device.release(uid)
            val changes = rig.docs.changes.toList()
            assertNull(rig.docs.sessions[uid])
            assertNull(rig.ids.id)

            // The pass of the engine that was under way asks now: no identifier + no session would normally mean "write one".
            assertEquals(Ensured.UNREACHABLE, rig.device.ensure(uid))
            assertEquals(Clearance.Unverified, rig.device.clearance(recheck = true))
            assertEquals(Clearance.Unverified, rig.device.clearance(recheck = false))
            assertFalse(rig.device.afterRefusal())
            assertEquals("no session was written back", changes, rig.docs.changes)
            assertNull(rig.ids.id)

            // The next sign-in reopens it.
            assertTrue(rig.device.begin(uid))
            assertTrue(rig.device.clearance(recheck = false) is Clearance.Cleared)
        }

    // --- A dead listener starts over (5) ------------------------------------------------------------------------------------

    @Test
    fun whenTheListenerDies_whatWasVerifiedNoLongerCounts_andItStartsOverAndStillHearsTheReplacement() =
        run {
            val rig = Rig(backgroundScope, ids = MemoryDeviceIdStore(DEVICE_A), retryPauses = listOf(10L))
            rig.docs.sessions[uid] = DEVICE_A
            rig.docs.observeErrors = 1
            rig.device.watch(uid)
            runCurrent()
            // The listener died at once: the verification is forgotten (a write would be verified again, not trusted), and it waits to start over.
            val readsBefore = rig.docs.reads
            assertTrue(rig.device.clearance(recheck = false) is Clearance.Cleared)
            assertEquals("not fresh any more: the server was asked", readsBefore + 1, rig.docs.reads)

            advanceTimeBy(11L)
            runCurrent()
            // Started over: verified again, listening again.
            rig.docs.takeOver(uid, DEVICE_B)
            runCurrent()
            assertEquals("the replacement is heard by the new listener", 1, rig.replacedCalls)
            assertNull(rig.ids.id)
        }

    @Test
    fun aListenerThatKeepsDying_givesUpAfterTheBoundedTries_noLoop() =
        run {
            val rig = Rig(backgroundScope, ids = MemoryDeviceIdStore(DEVICE_A), retryPauses = listOf(10L, 20L))
            rig.docs.sessions[uid] = DEVICE_A
            rig.docs.observeErrors = 100
            rig.device.watch(uid)
            advanceTimeBy(1_000L)
            runCurrent()
            assertEquals("1 first look + 2 tries", 3, rig.docs.reads)
        }
}
