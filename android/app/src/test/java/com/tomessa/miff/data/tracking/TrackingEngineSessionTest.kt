package com.tomessa.miff.data.tracking

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.data.miff.SharingConsentRead
import com.tomessa.miff.data.session.Clearance
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.miff.MiffWrites
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.tracking.JournalKind
import com.tomessa.miff.domain.tracking.PlaceFact
import com.tomessa.miff.domain.tracking.TransitionKind
import com.tomessa.miff.testsupport.DEVICE_A
import com.tomessa.miff.testsupport.FakeActiveDevice
import com.tomessa.miff.testsupport.FakeGeofences
import com.tomessa.miff.testsupport.FakeLocal
import com.tomessa.miff.testsupport.FakeMovement
import com.tomessa.miff.testsupport.FakeNetworkWake
import com.tomessa.miff.testsupport.FakePosition
import com.tomessa.miff.testsupport.FakeTrackingEnv
import com.tomessa.miff.testsupport.FakeTrackingStore
import com.tomessa.miff.testsupport.RecordingJournal
import com.tomessa.miff.testsupport.whileInUseOnly
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * SESS-2 in the detection engine (criteria SES2, SES3, SES5): nothing is written without the identifier of the session, the identifier is in EVERY write
 * (partial updates included), a replaced phone writes nothing and keeps what waits, a refused write makes the session be looked at again, and (R-LIEU-1,
 * deployed) the old workaround on `lastUpdateAt` is gone.
 */
class TrackingEngineSessionTest {
    private val miffA = "miffA"
    private val minute = 60_000L
    private val hour = 60 * minute

    private val env = FakeTrackingEnv()
    private val store = FakeTrackingStore()
    private val local = FakeLocal()
    private val position = FakePosition(env)
    private val geofences = FakeGeofences()
    private val movement = FakeMovement()
    private val wake = FakeNetworkWake()
    private val device = FakeActiveDevice()
    private val journal = RecordingJournal()
    private val engine =
        TrackingEngine(env, store, local, position, geofences, movement, wake, device, journal, timeoutMillis = 5_000)

    private val lat = 48.8566
    private val lng = 2.3522
    private val home = PlaceFact("home", lat, lng, 100, PlaceType.TRUSTED)

    private fun share(vararg miffs: String) {
        store.memberships = miffs.map { MembershipRef("k$it", it) }
        miffs.forEach {
            store.consents[it] = SharingConsentRead(enabled = true, version = "2026-11-v03")
            store.places[it] = listOf(home)
        }
    }

    private fun refused() = FirebaseFirestoreException("x", FirebaseFirestoreException.Code.PERMISSION_DENIED)

    private fun statusWrites() = store.writesTo("/status/")

    private fun positionWrites() = store.writesTo("/positions/")

    private fun pendingOf(miffId: String) =
        local.state!!
            .miffs
            .getValue(miffId)
            .pending

    // --- The identifier is in every write -----------------------------------------------------------------------------------------------

    @Test
    fun everyWriteOfTheEngine_statusPositionAndAccess_carriesTheIdentifier_partialUpdatesIncluded() =
        runTest {
            share(miffA, "miffB")
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            env.now += 3 * hour
            engine.onTransition(TransitionKind.EXIT, home.key, env.now - minute)
            // The permission is withdrawn: the access to the location is told with a partial update too.
            env.snapshot = whileInUseOnly()
            env.now += 20 * minute
            engine.refresh(TrackingEngine.Reason.BACKGROUNDED)

            val writes = store.commits.flatten()
            assertTrue("positions were written", positionWrites().isNotEmpty())
            assertTrue("statuses were written", statusWrites().isNotEmpty())
            assertTrue(
                "an access update (partial) was written",
                statusWrites().any { it.kind == WriteKind.UPDATE && "presence" !in it.data },
            )
            writes.forEach { write ->
                assertEquals(
                    "${write.kind} ${write.path} left without the identifier of the session",
                    DEVICE_A,
                    write.data["deviceId"],
                )
            }
        }

    @Test
    fun theNetworkBack_writesWhatWaited_withTheIdentifier() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            store.commits.clear()
            env.online = false
            env.now += 2 * hour
            engine.onTransition(TransitionKind.EXIT, home.key, env.now)
            env.online = true
            engine.onNetworkBack()
            assertEquals(DEVICE_A, statusWrites().single().data["deviceId"])
        }

    // --- Nothing is written without the permission of the session -----------------------------------------------------------------------

    @Test
    fun aPhoneThatWasReplaced_writesNothing_andKeepsWhatWaits() =
        runTest {
            share(miffA)
            device.answer = Clearance.Replaced
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertTrue("no position", positionWrites().isEmpty())
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertTrue("no status", statusWrites().isEmpty())
            assertNotNull("the status waits (it is erased by the forced sign-out, never sent)", pendingOf(miffA))
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun aSessionThatCannotBeVerified_writesNothing_thenWritesAsSoonAsItIsCleared() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            device.answer = Clearance.Unverified
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertTrue(statusWrites().isEmpty())
            assertNotNull(pendingOf(miffA))

            device.answer = Clearance.Cleared(DEVICE_A)
            engine.onNetworkBack()
            assertEquals(DEVICE_A, statusWrites().single().data["deviceId"])
            assertNull(pendingOf(miffA))
        }

    @Test
    fun theAccessChange_isNotMarkedAsTold_whenTheSessionIsNotCleared() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            store.commits.clear()
            device.answer = Clearance.Unverified
            env.snapshot = whileInUseOnly()
            engine.refresh(TrackingEngine.Reason.BACKGROUNDED)
            assertTrue(store.commits.isEmpty())
            // The session is cleared later: the change is still to be told.
            device.answer = Clearance.Cleared(DEVICE_A)
            env.now += 20 * minute
            engine.refresh(TrackingEngine.Reason.BACKGROUNDED)
            val access = statusWrites().single()
            assertEquals("foreground", access.data["locationAccess"])
            assertEquals(DEVICE_A, access.data["deviceId"])
        }

    @Test
    fun theSessionIsVerifiedAtTheFirstWriteOnly_andAgainAtAnOpeningAndAtTheReturnOfTheNetwork() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals("an opening asks the server", listOf(true), device.rechecks)
            device.rechecks.clear()
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertEquals("an event reuses a recent verification", listOf(false), device.rechecks.distinct())
            device.rechecks.clear()
            env.online = false
            env.now += hour
            engine.onTransition(TransitionKind.EXIT, home.key, env.now)
            assertTrue("offline: nothing is asked, nothing is written", device.rechecks.isEmpty())
            env.online = true
            engine.onNetworkBack()
            assertEquals("the return of the network asks the server again", listOf(true), device.rechecks)
        }

    @Test
    fun aPassThatWritesNothing_costsNoVerification() =
        runTest {
            store.memberships = emptyList()
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertTrue(device.rechecks.isEmpty())
        }

    // --- A refused write makes the session be looked at again ---------------------------------------------------------------------------

    @Test
    fun aRefusedStatus_looksAtTheSessionAgain_andARefusalWithTheSessionOurs_staysAnOrdinaryRefusal() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            store.alwaysFailing["miffs/miffA/status/uid1"] = refused()
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertEquals(1, device.refusalChecks)
            assertTrue(JournalKind.STATUS_REFUSED in journal.kinds)
            assertEquals(Clearance.Cleared(DEVICE_A).deviceId, (device.clearance(false) as Clearance.Cleared).deviceId)
        }

    @Test
    fun aRefusalThatTurnsOutToBeAReplacement_stopsTheOtherMiffsOfTheSamePass() =
        runTest {
            share("miffA", "miffB")
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            store.alwaysFailing["miffs/miffA/status/uid1"] = refused()
            device.replacedAfterRefusal = true
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertEquals(1, device.refusalChecks)
            assertTrue(
                "the second Miff is not written once the phone is known to be replaced",
                statusWrites().isEmpty(),
            )
        }

    @Test
    fun aRefusedPosition_looksAtTheSessionAgain() =
        runTest {
            share(miffA)
            store.alwaysFailing["miffs/miffA/positions/uid1"] = refused()
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(1, device.refusalChecks)
        }

    // --- R-LIEU-1 (deployed): the old workaround is gone -----------------------------------------------------------------------------------

    @Test
    fun afterMoreThan24HoursInTheSamePlace_anAccessChangeIsAPartialUpdateWithNoOldTime() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            store.commits.clear()
            env.now += 30 * hour
            env.snapshot = whileInUseOnly()
            engine.refresh(TrackingEngine.Reason.BACKGROUNDED)
            val update = statusWrites().single()
            assertEquals(WriteKind.UPDATE, update.kind)
            assertFalse("presenceAt" in update.data || "lastUpdateAt" in update.data)
            assertEquals(DEVICE_A, update.data["deviceId"])
        }

    @Test
    fun noWriteOfTheEngine_everPutsTheTimeOfAnEventIntoLastUpdateAt_exceptAnArrivalInATrustedZone() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            val arrival = statusWrites().single()
            assertEquals(Timestamp(Date(env.now)), arrival.data["lastUpdateAt"])
            store.commits.clear()
            env.now += 40 * hour
            engine.onTransition(TransitionKind.EXIT, home.key, env.now - minute)
            assertTrue(statusWrites().none { "lastUpdateAt" in it.data })
        }

    // --- A status deleted meanwhile is NOT recreated (the real rules answer PERMISSION_DENIED to an update of a missing status) ----------------

    @Test
    fun aStatusThatWasDeleted_answersPermissionDenied_isNotRecreated_andTheMiffIsLookedAtAgainSoon() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            val readsBefore = store.readCalls
            // Probe on the emulator with the real rules: an update of a status that does not exist is refused (PERMISSION_DENIED), never NOT_FOUND.
            store.alwaysFailing["miffs/miffA/status/uid1"] = refused()
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertTrue(
                "nothing is recreated: no complete status is written",
                store.commits.none { lot ->
                    lot.any {
                        it.kind ==
                            WriteKind.SET &&
                            "/status/" in it.path
                    }
                },
            )
            assertTrue(statusWrites().isEmpty())
            assertTrue(JournalKind.STATUS_REFUSED in journal.kinds)
            assertNull("the refused status is not kept waiting (no loop)", pendingOf(miffA))
            assertEquals("a refusal is looked at on the next pass, not at once", readsBefore, store.readCalls)
            env.now += 6 * minute
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertTrue(
                "the Miff is read again soon (sharing stopped elsewhere, consent...)",
                store.readCalls > readsBefore,
            )
        }

    // --- Lots: this app puts ONE Miff in each lot (contract SESS-1: 3 Miffs for an activation, 6 for positions) ------------------------------

    @Test
    fun eightMiffs_neverShareALotOfPositionsOrStatuses_farBelowTheLimitsOfTheContract() =
        runTest {
            share("m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8")
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertEquals(8, positionWrites().size)
            assertEquals(8, statusWrites().size)
            val biggest = store.commits.maxOf { it.size }
            assertEquals("one document per lot", 1, biggest)
            assertTrue(biggest <= MiffWrites.MAX_MIFFS_PER_POSITION_LOT)
            assertTrue(biggest <= MiffWrites.MAX_MIFFS_PER_ACTIVATION_LOT)
        }

    @Test
    fun theResumeOfASharing_isOneMiffPerLot_statusIncluded() {
        val lot =
            MiffWrites.resumeSharing(
                "miffA",
                "uid1",
                com.tomessa.miff.domain.miff.LocationAccess.UNKNOWN,
                DEVICE_A,
            )
        assertEquals(1, lot.count { it.path.startsWith("miffs/miffA/members/") })
        assertEquals(1, lot.count { "/status/" in it.path })
        assertTrue(lot.none { "/positions/" in it.path })
        assertEquals(2, lot.size)
    }
}
