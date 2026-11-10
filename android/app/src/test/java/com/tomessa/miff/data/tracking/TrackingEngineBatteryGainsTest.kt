package com.tomessa.miff.data.tracking

import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.data.miff.SharingConsentRead
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.tracking.BreakerState
import com.tomessa.miff.domain.tracking.JournalKind
import com.tomessa.miff.domain.tracking.PlaceFact
import com.tomessa.miff.domain.tracking.TrackingLimits
import com.tomessa.miff.domain.tracking.TransitionKind
import com.tomessa.miff.domain.tracking.WriteBreaker
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two battery gains that change nothing in the detection (docs/dev/optimisation-batterie.md, BATT-2 and BATT-4).
 *
 * BATT-2: an entry seen again for a zone the person is already in does not start a full pass (no position read, no write), and no real transition is lost.
 * BATT-4: at the return of the network, the server is read in full at most once per 5 minutes, and the waiting status is never held back by it.
 */
class TrackingEngineBatteryGainsTest {
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
    private val danger = PlaceFact("danger", lat, lng, 100, PlaceType.RISK)

    private fun share(vararg places: PlaceFact) {
        store.memberships = listOf(MembershipRef("k$miffA", miffA))
        store.consents[miffA] = SharingConsentRead(enabled = true, version = "2026-11-v03")
        store.places[miffA] = places.toList()
    }

    private fun statusWrites() = store.writesTo("/status/")

    private fun positionWrites() = store.writesTo("/positions/")

    // --- BATT-2: an entry in a zone where the person already is ------------------------------------------------------------------------------

    @Test
    fun aRealEntry_stillRunsTheFullPass_theBaselineOfTheGain() =
        runTest {
            share(home)
            engine.refresh(TrackingEngine.Reason.OPENED)
            val reads = position.reads
            env.now += 10 * minute
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertEquals("a first entry reads the position again, as before", reads + 1, position.reads)
            assertEquals(1, statusWrites().size)
        }

    @Test
    fun anEntryInAZoneAlreadyInside_readsNoPosition_writesNothing_changesNothing() =
        runTest {
            share(home)
            engine.refresh(TrackingEngine.Reason.OPENED)
            env.now += 10 * minute
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            val reads = position.reads
            val commits = store.commits.size
            val stateBefore = local.state
            val serverReads = store.readCalls
            val zoneUpdates = geofences.updates
            val movementStarts = movement.startCalls

            // The system reports the entry again (the zones were registered again at an opening): 20 minutes later, so that a pass WOULD have written a position.
            env.now += 20 * minute
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)

            assertEquals("no position is read", reads, position.reads)
            assertEquals("nothing is written (no status, no position)", commits, store.commits.size)
            assertEquals("nothing is read from the server", serverReads, store.readCalls)
            assertEquals("no zone is registered again", zoneUpdates, geofences.updates)
            assertEquals("the movement request is not renewed", movementStarts, movement.startCalls)
            assertEquals("the local copy is untouched", stateBefore, local.state)
            assertTrue(home.key in local.state!!.inside)
        }

    @Test
    fun theEntryAlreadyInside_isStillOnTheJournal_soThePassesCanBeCounted() =
        runTest {
            share(home)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            val enters = journal.kinds.count { it == JournalKind.TRANSITION_ENTER }
            val positionReads = journal.kinds.count { it == JournalKind.POSITION_READ }
            env.now += 20 * minute
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertEquals(enters + 1, journal.kinds.count { it == JournalKind.TRANSITION_ENTER })
            assertEquals(positionReads, journal.kinds.count { it == JournalKind.POSITION_READ })
        }

    @Test
    fun anEntryAfterADeparture_isARealTransition_fullPassAndStatus() =
        runTest {
            share(home)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            env.now += 3 * hour
            engine.onTransition(TransitionKind.EXIT, home.key, env.now)
            store.commits.clear()
            val reads = position.reads
            env.now += 2 * hour
            val back = env.now
            engine.onTransition(TransitionKind.ENTER, home.key, back)
            assertEquals("in_trusted", statusWrites().single().data["presence"])
            assertEquals("the full pass ran", reads + 1, position.reads)
            assertTrue(home.key in local.state!!.inside)
        }

    @Test
    fun aMissedDeparture_thenAnEntry_writesNothingNew_andTheNextDepartureIsStillWritten() =
        runTest {
            share(home)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            store.commits.clear()
            // The exit was never reported (phone off): the system only reports the next entry. The status already says "at home": nothing to say, before or after.
            env.now += 4 * hour
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertTrue(statusWrites().isEmpty())
            // The detection goes on: the following real departure is written.
            env.now += hour
            engine.onTransition(TransitionKind.EXIT, home.key, env.now)
            assertEquals("out", statusWrites().single().data["presence"])
            assertFalse(home.key in local.state!!.inside)
        }

    @Test
    fun afterARestartWithNoStatusKnownYet_theEntryOfTheSystem_isNotLost() =
        runTest {
            share(home)
            // First ever pass is a restart, the person is at home: the local list says "inside" (rebuilt from a recent position) but no status exists yet.
            engine.refresh(TrackingEngine.Reason.REARM)
            assertTrue(home.key in local.state!!.inside)
            assertTrue(statusWrites().isEmpty())
            store.commits.clear()
            // The system then reports the entry of the zone registered again: a status would change, so the pass is NOT skipped.
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertEquals("in_trusted", statusWrites().single().data["presence"])
        }

    @Test
    fun anEntryAlreadyInside_whoseStatusWouldChange_goesOnAsBefore() =
        runTest {
            share(home, danger)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, danger.key, env.now)
            assertEquals("in_risk", statusWrites().single().data["presence"])
            store.commits.clear()
            env.now += 30 * minute
            val reads = position.reads
            // The same zone, already inside, but entering the trusted one changes the derived presence: a status, then the full pass.
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertEquals("in_trusted", statusWrites().single().data["presence"])
            assertEquals(reads + 1, position.reads)
        }

    @Test
    fun theRegistrationOfTheZones_atEachOpening_isUntouched_evenWhenTheSystemLostThem() =
        runTest {
            share(home)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            // The phone cleaned the registered zones behind our back.
            geofences.armed.clear()
            val starts = movement.startCalls
            env.now += hour
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertTrue("the whole set is registered again", home.key in geofences.armed)
            assertTrue("the movement request is renewed", movement.startCalls > starts)
        }

    @Test
    fun theZonesAreStillRegisteredAgain_afterARestart_andTheEntryThatFollowsDoesNothingMore() =
        runTest {
            share(home)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            geofences.armed.clear()
            env.now += hour
            engine.refresh(TrackingEngine.Reason.REARM)
            assertTrue(home.key in geofences.armed)
            store.commits.clear()
            val reads = position.reads
            env.now += minute
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertEquals(reads, position.reads)
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun aSkippedEntry_stillRestartsAMovementRequestThatNeverStarted() =
        runTest {
            share(home)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            // The request of moves could not be made earlier (refused by the system): what the full pass used to retry is kept.
            local.state = local.state!!.copyWith(updatesArmed = false)
            val starts = movement.startCalls
            env.now += 20 * minute
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertEquals(starts + 1, movement.startCalls)
            assertTrue(local.state!!.updatesArmed)
        }

    @Test
    fun anEntryAfterTheAccessIsLost_isStillIgnored_andTheZonesRemoved() =
        runTest {
            share(home)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            store.commits.clear()
            env.snapshot = whileInUseOnly()
            engine.onTransition(TransitionKind.ENTER, home.key, env.now + minute)
            assertTrue(store.commits.isEmpty())
            assertTrue(geofences.armed.isEmpty())
        }

    // --- BATT-4: the server read again at the return of the network ----------------------------------------------------------------------------

    /** A status that waits because the daily limit of statuses is reached: the network wake-up stays on and each return of the network passes here. */
    private suspend fun openedWithAStatusWaiting() {
        share(home)
        engine.refresh(TrackingEngine.Reason.OPENED)
        engine.onTransition(TransitionKind.ENTER, home.key, env.now)
        store.commits.clear()
        local.state =
            local.state!!.copyWith(
                breaker = BreakerState(WriteBreaker.dayKey(env.now), TrackingLimits.MAX_STATUS_WRITES_PER_DAY, 0),
            )
        env.now += 6 * minute
        engine.onTransition(TransitionKind.EXIT, home.key, env.now)
        assertNotNull(
            local.state!!
                .miffs
                .getValue(miffA)
                .pending,
        )
        assertTrue(wake.watching)
    }

    @Test
    fun theNamedConstantIsFiveMinutes() {
        assertEquals(5 * minute, TrackingLimits.NETWORK_REREAD_MIN_INTERVAL_MILLIS)
    }

    @Test
    fun severalReturnsOfTheNetworkWithinFiveMinutes_readTheServerOnce() =
        runTest {
            openedWithAStatusWaiting()
            // The last complete look is the opening, more than 5 minutes ago: the first return reads in full.
            env.now += 10 * minute
            val before = store.readCalls
            engine.onNetworkBack()
            val afterFirst = store.readCalls
            assertTrue("the first return reads in full", afterFirst > before)
            // The Wi-Fi drops and comes back four times in the next four minutes: nothing is read again.
            repeat(4) {
                env.now += minute
                engine.onNetworkBack()
            }
            assertEquals(afterFirst, store.readCalls)
        }

    @Test
    fun theBound_isStrictlyFiveMinutes_oneMillisecondShortReadsNothing_theFiveMinutesReadAgain() =
        runTest {
            openedWithAStatusWaiting()
            env.now += 10 * minute
            engine.onNetworkBack()
            val checkedAt = local.state!!.checkedAtMillis
            assertEquals(env.now, checkedAt)
            val reads = store.readCalls

            env.now = checkedAt + TrackingLimits.NETWORK_REREAD_MIN_INTERVAL_MILLIS - 1
            engine.onNetworkBack()
            assertEquals("4 min 59 s 999: nothing read", reads, store.readCalls)

            env.now = checkedAt + TrackingLimits.NETWORK_REREAD_MIN_INTERVAL_MILLIS
            engine.onNetworkBack()
            assertTrue("exactly 5 min: read again", store.readCalls > reads)
            assertEquals(env.now, local.state!!.checkedAtMillis)
        }

    @Test
    fun thatBound_alsoCountsACompleteLookMadeByAnotherWakeUp() =
        runTest {
            openedWithAStatusWaiting()
            // An opening 2 minutes ago made a complete look (the hour is not over, so we force it with a change of sharing).
            env.now += 10 * minute
            engine.refresh(TrackingEngine.Reason.SHARING_CHANGED)
            val reads = store.readCalls
            env.now += 2 * minute
            engine.onNetworkBack()
            assertEquals(reads, store.readCalls)
        }

    @Test
    fun theWaitingStatus_isWrittenAtOnce_withoutTheLookAtTheServer_andTheSessionIsStillVerified() =
        runTest {
            share(home)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            store.commits.clear()
            // Offline: the departure waits. The last complete look (the opening) is only 3 minutes old when the network comes back.
            env.online = false
            env.now += 2 * minute
            val left = env.now
            engine.onTransition(TransitionKind.EXIT, home.key, left)
            assertTrue(statusWrites().isEmpty())
            assertTrue(wake.watching)
            env.online = true
            env.now += minute
            val reads = store.readCalls
            val checks = device.rechecks.size
            engine.onNetworkBack()
            assertEquals("no look at the server", reads, store.readCalls)
            assertEquals("out", statusWrites().single().data["presence"])
            assertEquals("the real time of the departure is kept", left, statusTimeMillis())
            assertFalse(wake.watching)
            assertEquals("SESS-2: the session is verified again before the write", checks + 1, device.rechecks.size)
            assertTrue(device.rechecks.last())
        }

    private fun statusTimeMillis(): Long =
        (statusWrites().single().data["presenceAt"] as com.google.firebase.Timestamp).toDate().time

    @Test
    fun afterTheFiveMinutes_theLookIsMadeInFull_andAConsentWithdrawnMeanwhileStopsTheWrite() =
        runTest {
            openedWithAStatusWaiting()
            local.state =
                local.state!!.copyWith(
                    breaker = BreakerState("1999-01-01", TrackingLimits.MAX_STATUS_WRITES_PER_DAY, 0),
                )
            store.consents[miffA] = SharingConsentRead(enabled = false, version = "2026-11-v03")
            env.now += 6 * minute
            engine.onNetworkBack()
            assertTrue("the consent was read again: nothing is written", statusWrites().isEmpty())
            assertTrue(local.state!!.miffs.isEmpty())
            assertFalse(wake.watching)
        }

    @Test
    fun anIncompleteLook_isNotCounted_theNextReturnOfTheNetworkTriesAgain_andWrites() =
        runTest {
            share(home)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            store.commits.clear()
            env.online = false
            env.now += 10 * minute
            engine.onTransition(TransitionKind.EXIT, home.key, env.now)
            env.online = true
            // A return of the network where the server does not answer: nothing is concluded, nothing is written, the status keeps waiting.
            store.readFailure = FirebaseFirestoreException("x", FirebaseFirestoreException.Code.UNAVAILABLE)
            engine.onNetworkBack()
            assertTrue(statusWrites().isEmpty())
            assertNotNull(
                local.state!!
                    .miffs
                    .getValue(miffA)
                    .pending,
            )
            // One minute later the server answers: the look is made again at once (the failed one did not count), and the status goes out.
            store.readFailure = null
            env.now += minute
            val reads = store.readCalls
            engine.onNetworkBack()
            assertTrue(store.readCalls > reads)
            assertEquals("out", statusWrites().single().data["presence"])
        }

    @Test
    fun afterARefusal_theLookWanted_isNotMistakenForARecentOne() =
        runTest {
            share(home)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            store.alwaysFailing["miffs/miffA/status/uid1"] =
                FirebaseFirestoreException("x", FirebaseFirestoreException.Code.PERMISSION_DENIED)
            env.now += 2 * minute
            engine.onTransition(TransitionKind.EXIT, home.key, env.now)
            assertTrue(JournalKind.STATUS_REFUSED in journal.kinds)
            val reads = store.readCalls
            // Within 5 minutes of the opening, but the refusal asked for a new look: the return of the network makes it.
            env.now += minute
            engine.onNetworkBack()
            assertTrue(store.readCalls > reads)
        }

    @Test
    fun aClockMovedBack_neverFreezesTheLook() =
        runTest {
            openedWithAStatusWaiting()
            local.state = local.state!!.copyWith(checkedAtMillis = env.now + hour)
            val reads = store.readCalls
            engine.onNetworkBack()
            assertTrue(store.readCalls > reads)
        }

    @Test
    fun theAccessWithdrawn_isStillCheckedBeforeAnythingElse_evenWithinTheFiveMinutes() =
        runTest {
            share(home)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            store.commits.clear()
            env.online = false
            env.now += minute
            engine.onTransition(TransitionKind.EXIT, home.key, env.now)
            env.online = true
            env.snapshot = whileInUseOnly()
            env.now += minute
            engine.onNetworkBack()
            assertTrue(store.commits.isEmpty())
            assertTrue(geofences.armed.isEmpty())
            assertFalse(wake.watching)
            assertTrue(positionWrites().isEmpty())
        }
}
