package com.tomessa.miff.data.tracking

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.data.miff.SharingConsentRead
import com.tomessa.miff.data.session.Clearance
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.tracking.Fix
import com.tomessa.miff.domain.tracking.PlaceFact
import com.tomessa.miff.domain.tracking.TransitionKind
import com.tomessa.miff.domain.travel.TravelPeriod
import com.tomessa.miff.testsupport.DEVICE_A
import com.tomessa.miff.testsupport.FakeActiveDevice
import com.tomessa.miff.testsupport.FakeGeofences
import com.tomessa.miff.testsupport.FakeLocal
import com.tomessa.miff.testsupport.FakeMovement
import com.tomessa.miff.testsupport.FakeNetworkWake
import com.tomessa.miff.testsupport.FakePosition
import com.tomessa.miff.testsupport.FakeTrackingEnv
import com.tomessa.miff.testsupport.FakeTrackingStore
import com.tomessa.miff.testsupport.FakeTravelAlarm
import com.tomessa.miff.testsupport.RecordingJournal
import com.tomessa.miff.testsupport.T0
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MODE VOYAGE, the engine (VOY-1A, criteria V1 to V11 of docs/tickets/mode-voyage.md): the trip is written FIRST and the tracking paused AFTER (a refusal changes
 * nothing); during a trip nothing is read, armed or written for the tracking; the trip ends by the alarm, by hand, or at the opening; a NEW phone adopts a trip instead of
 * overwriting it; a Miff joined during a trip learns of it. Doubles for the phone, the system and Firestore: no real position, no network.
 */
class TrackingEngineTravelTest {
    private val miffA = "miffA"
    private val miffB = "miffB"
    private val minute = 60_000L
    private val day = 24 * 60 * minute

    private val env = FakeTrackingEnv()
    private val store = FakeTrackingStore()
    private val local = FakeLocal()
    private val position = FakePosition(env)
    private val geofences = FakeGeofences()
    private val movement = FakeMovement()
    private val wake = FakeNetworkWake()
    private val device = FakeActiveDevice()
    private val journal = RecordingJournal()
    private val alarm = FakeTravelAlarm()
    private val engine =
        TrackingEngine(
            env,
            store,
            local,
            position,
            geofences,
            movement,
            wake,
            device,
            journal,
            timeoutMillis = 5_000,
            travelAlarm = alarm,
        )

    private val lat = 48.8566
    private val lng = 2.3522
    private val home = PlaceFact("home", lat, lng, 100, PlaceType.TRUSTED)

    // A trip of two days: it ends at T0 + 2 days.
    private val period = TravelPeriod(T0 + 2 * day, "2027-01-17")

    private fun v3() = SharingConsentRead(enabled = true, version = "2026-11-v03")

    private fun share(vararg miffs: String) {
        store.memberships = miffs.map { MembershipRef("k$it", it) }
        miffs.forEach {
            store.consents[it] = v3()
            store.places[it] = listOf(home)
        }
    }

    private fun refused() = FirebaseFirestoreException("x", FirebaseFirestoreException.Code.PERMISSION_DENIED)

    private fun unavailable() = FirebaseFirestoreException("x", FirebaseFirestoreException.Code.UNAVAILABLE)

    private fun statusWrites() = store.writesTo("/status/")

    private fun positionWrites() = store.writesTo("/positions/")

    private fun untilOf(write: DocWrite): Long = (write.data["travelUntil"] as Timestamp).toDate().time

    /** The tracking is running (a person at home, two Miffs...), then the trip begins. */
    private suspend fun startedTrip(vararg miffs: String) {
        share(*miffs)
        engine.refresh(TrackingEngine.Reason.OPENED)
        assertTrue(geofences.armed.isNotEmpty())
        assertEquals(TravelResult.Done, engine.setTravel(period))
    }

    // --- V1: the trip is written first, the tracking is paused after ------------------------------------------------------------------

    @Test
    fun theTripIsWrittenToEveryMiff_beforeTheTrackingIsPaused() =
        runTest {
            share(miffA, miffB)
            engine.refresh(TrackingEngine.Reason.OPENED)
            val before = store.commits.size
            val removeBefore = geofences.removeAllCalls
            var removeAtFirstWrite = -1
            store.onCommit =
                { if (removeAtFirstWrite < 0 && "/status/" in it) removeAtFirstWrite = geofences.removeAllCalls }

            assertEquals(TravelResult.Done, engine.setTravel(period))

            // V1: when the first status of the trip left, the zones were still there.
            assertEquals(removeBefore, removeAtFirstWrite)
            assertTrue(geofences.removeAllCalls > removeBefore)
            assertTrue(geofences.armed.isEmpty())
            assertFalse(movement.started)
            // One lot per Miff: the status of the trip AND the removal of my last position, together.
            val lots = store.commits.drop(before)
            assertEquals(2, lots.size)
            lots.forEach { lot ->
                assertEquals(2, lot.size)
                val status = lot[0]
                assertEquals(WriteKind.UPDATE, status.kind)
                assertTrue(status.path.endsWith("/status/uid1"))
                assertEquals("travel", status.data["presence"])
                assertNull(status.data["placeId"])
                assertNull(status.data["batteryPct"])
                assertNull(status.data["escalationDueAt"])
                assertEquals(period.untilMillis, untilOf(status))
                assertEquals("2027-01-17", status.data["travelUntilDay"])
                assertEquals(DEVICE_A, status.data["deviceId"])
                assertTrue(status.data["presenceAt"] is Timestamp)
                assertEquals(WriteKind.DELETE, lot[1].kind)
                assertTrue(lot[1].path.endsWith("/positions/uid1"))
            }
            // The copy of the position kept on the phone is erased, the trip is kept, the alarm is set, the Profile is told.
            val kept = local.state!!
            assertNull(kept.fix())
            assertTrue(kept.armed.isEmpty())
            kept.miffs.values.forEach {
                assertNull(it.written())
                assertNull(it.derived())
            }
            assertEquals(period, kept.travel!!.period())
            assertEquals(setOf(miffA, miffB), kept.travel!!.upToDateIn)
            assertEquals(period.untilMillis, alarm.at)
            assertEquals(period, engine.travel.value)
        }

    @Test
    fun aRefusedTrip_changesNothing_theTrackingIsNotStopped() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            val armed = geofences.armed.toSet()
            val removeBefore = geofences.removeAllCalls
            // The rules are not deployed yet (or the session is taken): the server says no.
            store.alwaysFailing["miffs/miffA/status/uid1"] = refused()

            assertEquals(TravelResult.Refused, engine.setTravel(period))

            assertEquals(armed, geofences.armed)
            assertEquals(removeBefore, geofences.removeAllCalls)
            assertTrue(movement.started)
            assertNull(local.state!!.travel)
            assertNotNull(local.state!!.fix())
            assertNull(alarm.at)
            assertNull(engine.travel.value)
        }

    @Test
    fun aPhoneThatWasReplaced_cannotBeginATrip_nothingChanges() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            device.answer = Clearance.Replaced

            assertEquals(TravelResult.Refused, engine.setTravel(period))

            assertTrue(geofences.armed.isNotEmpty())
            assertNull(local.state!!.travel)
        }

    @Test
    fun withoutNetwork_theTripIsAcceptedHere_andToldAsSoonAsTheNetworkIsBack() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            val before = store.commits.size
            env.online = false

            assertEquals(TravelResult.DoneOffline, engine.setTravel(period))

            assertEquals(before, store.commits.size)
            assertTrue(geofences.armed.isEmpty())
            assertNotNull(local.state!!.travel)
            assertTrue("the network is watched while the Miffs wait to be told", wake.watching)

            env.online = true
            engine.onNetworkBack()

            val status = statusWrites().single()
            assertEquals("travel", status.data["presence"])
            assertEquals(period.untilMillis, untilOf(status))
            assertTrue(positionWrites().any { it.kind == WriteKind.DELETE })
            assertFalse(wake.watching)
            assertEquals(setOf(miffA), local.state!!.travel!!.upToDateIn)
        }

    @Test
    fun aNetworkThatFailsDuringTheWrite_acceptsTheTripHere_andKeepsTheMiffToBeTold() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.alwaysFailing["miffs/miffA/status/uid1"] = unavailable()

            assertEquals(TravelResult.DoneOffline, engine.setTravel(period))

            assertTrue(geofences.armed.isEmpty())
            assertTrue(
                local.state!!
                    .travel!!
                    .upToDateIn
                    .isEmpty(),
            )
            assertTrue(wake.watching)
        }

    @Test
    fun withNoMiffWhereIShare_theTripIsKeptHere_nothingIsWritten() =
        runTest {
            store.memberships = emptyList()
            assertEquals(TravelResult.Done, engine.setTravel(period))
            assertTrue(store.commits.isEmpty())
            assertEquals(period, local.state!!.travel!!.period())
            assertEquals(period.untilMillis, alarm.at)

            // V11: and it ends without a write.
            assertTrue(engine.endTravel())
            assertTrue(store.commits.isEmpty())
            assertNull(local.state!!.travel)
        }

    // --- V2: during the trip, nothing is read, armed or written ---------------------------------------------------------------------

    @Test
    fun duringATrip_noPositionIsRead_noZoneIsArmed_noStatusPositionOrBatteryIsWritten() =
        runTest {
            startedTrip(miffA, miffB)
            val reads = position.reads
            val commits = store.commits.size
            val updates = geofences.updates
            val starts = movement.startCalls
            env.battery = 20

            engine.refresh(TrackingEngine.Reason.OPENED)
            env.now += 30 * minute
            engine.refresh(TrackingEngine.Reason.BACKGROUNDED)
            engine.refresh(TrackingEngine.Reason.MOVED, Fix(lat + 0.01, lng, env.now))
            engine.refresh(TrackingEngine.Reason.ZONE_ENTERED)
            engine.refresh(TrackingEngine.Reason.REARM)
            engine.refresh(TrackingEngine.Reason.SHARING_CHANGED)
            engine.lookAgain(TrackingEngine.Reason.OPENED)
            engine.lookAgain(TrackingEngine.Reason.BACKGROUNDED)
            engine.refreshBattery()
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            engine.onTransition(TransitionKind.EXIT, home.key, env.now)
            engine.onNetworkBack()

            assertEquals(reads, position.reads)
            assertEquals(commits, store.commits.size)
            assertEquals(updates, geofences.updates)
            assertEquals(starts, movement.startCalls)
            assertTrue(geofences.armed.isEmpty())
            assertFalse(movement.started)
            assertNotNull(local.state!!.travel)
            assertNull(local.state!!.fix())
        }

    @Test
    fun aRestart_duringATrip_armsNothing_andSetsTheAlarmAgain() =
        runTest {
            startedTrip(miffA)
            val calls = alarm.scheduleCalls
            alarm.at = null

            engine.refresh(TrackingEngine.Reason.REARM)

            assertTrue(geofences.armed.isEmpty())
            assertFalse(movement.started)
            assertEquals(period.untilMillis, alarm.at)
            assertTrue(alarm.scheduleCalls > calls)
        }

    // --- V6: the end moves ---------------------------------------------------------------------------------------------------------

    @Test
    fun movingTheEnd_writesTheNewEndInEveryMiff_andSetsTheAlarmAgain_withoutTouchingTheStartOfTheTrip() =
        runTest {
            startedTrip(miffA, miffB)
            val before = store.commits.size
            val later = TravelPeriod(period.untilMillis + 3 * day, "2027-01-20")

            assertEquals(TravelResult.Done, engine.setTravel(later))

            val lots = store.commits.drop(before)
            assertEquals(2, lots.size)
            lots.forEach { lot ->
                assertEquals(1, lot.size)
                val write = lot.single()
                assertTrue(write.path.endsWith("/status/uid1"))
                assertEquals(later.untilMillis, untilOf(write))
                assertEquals("2027-01-20", write.data["travelUntilDay"])
                assertFalse("presence" in write.data)
                assertFalse("presenceAt" in write.data)
            }
            assertEquals(later.untilMillis, alarm.at)
            assertEquals(later, engine.travel.value)
            assertEquals(later, local.state!!.travel!!.period())
        }

    @Test
    fun aShorterEnd_isAllowed() =
        runTest {
            startedTrip(miffA)
            val shorter = TravelPeriod(T0 + day, "2027-01-16")
            assertEquals(TravelResult.Done, engine.setTravel(shorter))
            assertEquals(shorter.untilMillis, alarm.at)
        }

    @Test
    fun anEndThatIsAlreadyPast_isNeverWritten() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            val before = store.commits.size
            assertEquals(TravelResult.Refused, engine.setTravel(TravelPeriod(T0 - minute, "2027-01-14")))
            assertEquals(before, store.commits.size)
            assertNull(local.state!!.travel)
        }

    @Test
    fun anEndMoreThan90DaysAhead_isNeverWritten() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            val before = store.commits.size
            assertEquals(TravelResult.Refused, engine.setTravel(TravelPeriod(T0 + 92 * day, "2027-04-17")))
            assertEquals(before, store.commits.size)
            assertNull(local.state!!.travel)
        }

    @Test
    fun theNetworkIsWatchedBeforeTheEndIsWritten() =
        runTest {
            startedTrip(miffA)
            store.commits.clear()
            wake.watching = false
            var watchingAtTheEndWrite: Boolean? = null
            store.onCommit =
                { if (watchingAtTheEndWrite == null && "/status/" in it) watchingAtTheEndWrite = wake.watching }
            env.now = period.untilMillis + minute

            engine.refresh(TrackingEngine.Reason.TRAVEL_ENDED)

            assertEquals(true, watchingAtTheEndWrite)
        }

    // --- V4, V5: the end ----------------------------------------------------------------------------------------------------------

    @Test
    fun theEndOfTheTrip_byTheAlarm_tellsUnknownFirst_thenArmsTheTrackingAgain() =
        runTest {
            startedTrip(miffA)
            store.commits.clear()
            val reads = position.reads
            var armedAtTheEndWrite: Set<String>? = null
            store.onCommit =
                { if (armedAtTheEndWrite == null && "/status/" in it) armedAtTheEndWrite = geofences.armed.toSet() }
            // The alarm rings, a little after the end.
            env.now = period.untilMillis + 2 * minute

            engine.refresh(TrackingEngine.Reason.TRAVEL_ENDED)

            val first = statusWrites().first()
            assertEquals("unknown", first.data["presence"])
            assertNull(first.data["placeId"])
            // The two keys of the trip are cleared in the same write.
            assertTrue("travelUntil" in first.data && first.data["travelUntil"] == null)
            assertTrue("travelUntilDay" in first.data && first.data["travelUntilDay"] == null)
            // V4: the neutral state left BEFORE the first measure and before the zones came back.
            assertEquals(emptySet<String>(), armedAtTheEndWrite)
            assertTrue(geofences.armed.isNotEmpty())
            assertTrue(position.reads > reads)
            // The person is at home: the usual arrival follows, and it carries no key of the trip (they are already cleared).
            val last = statusWrites().last()
            assertEquals("in_trusted", last.data["presence"])
            assertFalse("travelUntil" in last.data)
            assertNull(local.state!!.travel)
            assertNull(engine.travel.value)
            assertNull(alarm.at)
        }

    @Test
    fun anAlarmThatRingsWithNoTrip_doesNothing() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            val commits = store.commits.size
            val reads = position.reads
            val updates = geofences.updates
            engine.refresh(TrackingEngine.Reason.TRAVEL_ENDED)
            assertEquals(commits, store.commits.size)
            assertEquals(reads, position.reads)
            assertEquals(updates, geofences.updates)
        }

    @Test
    fun theEndIsFoundAtTheOpening_whenTheAlarmWasKilled() =
        runTest {
            startedTrip(miffA)
            store.commits.clear()
            env.now = period.untilMillis + 5 * day

            engine.refresh(TrackingEngine.Reason.OPENED)

            assertEquals("unknown", statusWrites().first().data["presence"])
            assertNull(local.state!!.travel)
            assertTrue(geofences.armed.isNotEmpty())
        }

    @Test
    fun turningTheTripOff_resumesAtOnce_andItIsNotFollowedByAnythingElse() =
        runTest {
            startedTrip(miffA)
            store.commits.clear()
            env.now += 10 * minute

            assertTrue(engine.endTravel())

            assertEquals("unknown", statusWrites().first().data["presence"])
            assertNull(local.state!!.travel)
            assertTrue(geofences.armed.isNotEmpty())
            assertNull(alarm.at)
            // And a second tap finds nothing to end.
            assertFalse(engine.endTravel())
        }

    @Test
    fun theEndThatCannotLeave_waitsForTheNetwork_andTheTrackingResumesAnyway() =
        runTest {
            startedTrip(miffA)
            store.commits.clear()
            env.online = false
            env.now = period.untilMillis + minute

            engine.refresh(TrackingEngine.Reason.TRAVEL_ENDED)

            // The local state is free and the zones are back, but the neutral state is still to be told.
            assertTrue(statusWrites().isEmpty())
            assertNull(local.state!!.travel)
            assertTrue(geofences.armed.isNotEmpty())
            assertTrue(
                local.state!!
                    .miffs
                    .getValue(miffA)
                    .clearTravel,
            )
            assertTrue(wake.watching)

            env.online = true
            engine.onNetworkBack()

            // Whatever status leaves first clears the keys of the trip.
            val written = statusWrites()
            assertTrue(written.isNotEmpty())
            assertTrue("travelUntil" in written.first().data && written.first().data["travelUntil"] == null)
            assertFalse(
                local.state!!
                    .miffs
                    .getValue(miffA)
                    .clearTravel,
            )
        }

    // --- V7: a new phone adopts the trip ------------------------------------------------------------------------------------------

    @Test
    fun aNewPhone_adoptsAnOngoingTrip_beforeArmingAnything() =
        runTest {
            share(miffA, miffB)
            env.now = T0
            store.ownTravels[miffA] = TravelRead(period, T0 - day)
            store.ownTravels[miffB] = TravelRead(period, T0 - day)
            assertNull(local.state)

            engine.refresh(TrackingEngine.Reason.OPENED)

            assertEquals(0, position.reads)
            assertTrue(geofences.armed.isEmpty())
            assertFalse(movement.started)
            // The trip is adopted as it is: nothing is written over it.
            assertTrue(store.commits.isEmpty())
            val kept = local.state!!.travel!!
            assertEquals(period, kept.period())
            assertEquals(T0 - day, kept.sinceMillis)
            assertEquals(setOf(miffA, miffB), kept.upToDateIn)
            assertEquals(period, engine.travel.value)
            assertEquals(period.untilMillis, alarm.at)
        }

    @Test
    fun aNewPhone_adoptsATripThatIsOver_andEndsIt() =
        runTest {
            share(miffA)
            store.ownTravels[miffA] = TravelRead(TravelPeriod(T0 - day, "2027-01-14"), T0 - 5 * day)

            engine.refresh(TrackingEngine.Reason.OPENED)

            val first = statusWrites().first()
            assertEquals("unknown", first.data["presence"])
            assertTrue("travelUntil" in first.data && first.data["travelUntil"] == null)
            assertNull(local.state!!.travel)
            assertTrue(geofences.armed.isNotEmpty())
        }

    @Test
    fun aNewPhone_withNoTrip_startsNormally_andReadsMyStatusOnlyOnce() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertTrue(geofences.armed.isNotEmpty())
            assertEquals(1, store.ownTravelReads)
            env.now += 2 * minute
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.refresh(TrackingEngine.Reason.BACKGROUNDED)
            assertEquals(1, store.ownTravelReads)
        }

    @Test
    fun aNewPhone_thatCannotReadMyStatus_armsNothing_andAsksAgainAtTheNextWakeUp() =
        runTest {
            share(miffA)
            store.ownTravelFailure = unavailable()

            engine.refresh(TrackingEngine.Reason.OPENED)

            // The trip must never be overwritten: nothing is armed, read or saved on a guess.
            assertEquals(0, position.reads)
            assertTrue(geofences.armed.isEmpty())
            assertNull(local.state)
            assertTrue(store.commits.isEmpty())

            store.ownTravelFailure = null
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertTrue(geofences.armed.isNotEmpty())
        }

    // --- V8: a Miff joined during the trip ---------------------------------------------------------------------------------------

    @Test
    fun aMiffJoinedDuringTheTrip_learnsOfIt_theOthersAreNotWrittenAgain() =
        runTest {
            startedTrip(miffA)
            store.commits.clear()
            share(miffA, miffB)

            engine.refresh(TrackingEngine.Reason.SHARING_CHANGED)

            val writes = statusWrites()
            assertEquals(1, writes.size)
            assertEquals("miffs/$miffB/status/uid1", writes.single().path)
            assertEquals("travel", writes.single().data["presence"])
            assertEquals(period.untilMillis, untilOf(writes.single()))
            assertEquals(setOf(miffA, miffB), local.state!!.travel!!.upToDateIn)
            // Still nothing armed or read.
            assertTrue(geofences.armed.isEmpty())
        }

    @Test
    fun aMiffWhereIStopSharing_leavesTheListOfThoseToTell() =
        runTest {
            startedTrip(miffA, miffB)
            share(miffA)
            engine.refresh(TrackingEngine.Reason.SHARING_CHANGED)
            assertEquals(setOf(miffA), local.state!!.miffs.keys)
            assertEquals(setOf(miffA), local.state!!.travel!!.upToDateIn)
        }

    // --- V10: sign-out -------------------------------------------------------------------------------------------------------------

    @Test
    fun signingOut_erasesTheTripFromThePhone_andCancelsTheAlarm() =
        runTest {
            startedTrip(miffA)

            engine.stop()

            assertNull(local.state)
            assertNull(alarm.at)
            assertNull(engine.travel.value)
        }

    @Test
    fun theTripIsKept_forTheAccountItBelongsTo_only() =
        runTest {
            startedTrip(miffA)
            env.uid = "uid2"
            engine.reloadTravel()
            assertNull(engine.travel.value)
            env.uid = "uid1"
            engine.reloadTravel()
            assertEquals(period, engine.travel.value)
        }
}
