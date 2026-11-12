package com.tomessa.miff.data.tracking

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.data.miff.SharingConsentRead
import com.tomessa.miff.data.session.Clearance
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.tracking.JournalKind
import com.tomessa.miff.domain.tracking.PlaceFact
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.TransitionKind
import com.tomessa.miff.domain.travel.TravelPeriod
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
import com.tomessa.miff.testsupport.fullPermissions
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
 * REPRISE DU PARTAGE (recette TestFlight / Android, 2026-11-11): after "stop sharing" then "resume", the card of the person stayed GREY at the loved ones instead of turning green,
 * orange or red. Stopping DELETES the status, the photo and the last position of the Miff on the server; resuming writes an `unknown` status. The engine only wrote a status on a CHANGE
 * of presence, so nothing wrote it again before the next entry or exit. The first tests show the defect WITHOUT the signal of the repository ([TrackingEngine.markResumed]), the others
 * what the signal does and what it never does. Doubles for the phone, the system and Firestore: no real position, no network.
 */
class TrackingEngineResumeTest {
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

    private class Told(
        val miffId: String,
        val presence: PresenceValue,
    )

    private val presences = mutableListOf<Told>()
    private val dangerTold = mutableListOf<String>()

    private val listener =
        object : PresenceListener {
            override fun onMyPresence(
                miffId: String,
                presence: PresenceValue,
                atMillis: Long,
                setLastUpdate: Boolean,
            ) {
                presences += Told(miffId, presence)
            }

            override fun onEnteredDanger(
                myUid: String,
                miffId: String,
                placeId: String,
                atMillis: Long,
            ) {
                dangerTold += miffId
            }
        }

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
            presenceListener = listener,
            travelAlarm = alarm,
        )

    // The person is at (48.8566, 2.3522); `home` is under their feet, `danger` is 1.1 km north.
    private val lat = 48.8566
    private val lng = 2.3522
    private val home = PlaceFact("home", lat, lng, 100, PlaceType.TRUSTED)
    private val danger = PlaceFact("danger", lat + 0.01, lng, 100, PlaceType.RISK)

    private fun v3() = SharingConsentRead(enabled = true, version = "2026-11-v03")

    private fun stopped() = SharingConsentRead(enabled = false, version = "2026-11-v03")

    private fun share(
        vararg miffs: String,
        placesOf: (String) -> List<PlaceFact> = { listOf(home, danger) },
    ) {
        store.memberships = miffs.map { MembershipRef("k$it", it) }
        miffs.forEach {
            store.consents[it] = v3()
            store.places[it] = placesOf(it)
        }
    }

    private fun statusWrites() = store.writesTo("/status/")

    private fun positionWrites() = store.writesTo("/positions/")

    /** The person stops sharing in [miffId]: the engine sees it (the Miff leaves its list, everything it kept for it goes), as the app does just after the stop. */
    private suspend fun stopSeenBy(miffId: String) {
        store.consents[miffId] = stopped()
        engine.refresh(TrackingEngine.Reason.SHARING_CHANGED)
    }

    /** The person resumes the sharing in [miffId]: what the repository does after the write is confirmed (the flag, then the look at the Miffs). */
    private suspend fun resumeSignal(miffId: String) {
        store.consents[miffId] = v3()
        engine.markResumed(miffId)
        engine.refresh(TrackingEngine.Reason.SHARING_CHANGED)
    }

    /** The person is at home, the tracking runs and the loved ones see them in the place; then the commits are forgotten. */
    private suspend fun atHome(vararg miffs: String) {
        share(*miffs)
        engine.refresh(TrackingEngine.Reason.OPENED)
        engine.onTransition(TransitionKind.ENTER, home.key, env.now)
        assertEquals(miffs.size, statusWrites().size)
    }

    // --- The defect (without the signal) --------------------------------------------------------------------------------------------------

    @Test
    fun withoutTheSignal_aSharingStoppedThenResumed_writesNoStatus_theCardStaysGrey() =
        runTest {
            atHome(miffA)
            stopSeenBy(miffA)
            assertTrue(local.state!!.miffs.isEmpty())
            store.commits.clear()
            // Resumed: the server wrote `unknown`; the person is at home, no entry to tell, the zone is already known to the system.
            store.consents[miffA] = v3()
            engine.refresh(TrackingEngine.Reason.SHARING_CHANGED)
            assertTrue("nothing wrote the status again", statusWrites().isEmpty())
        }

    @Test
    fun withoutTheSignal_aStopTheEngineNeverSaw_keepsThePresenceAndTheWrittenPosition() =
        runTest {
            atHome(miffA)
            env.now += minute
            store.commits.clear()
            engine.refresh(TrackingEngine.Reason.SHARING_CHANGED)
            assertTrue(statusWrites().isEmpty())
            assertTrue("the position the server deleted still counts as published", positionWrites().isEmpty())
        }

    // --- With the signal ----------------------------------------------------------------------------------------------------------------------

    @Test
    fun theStatusInThePlaceIsWrittenAgain_afterAStopTheEngineSaw_andTheFlagIsConsumedOnce() =
        runTest {
            atHome(miffA)
            stopSeenBy(miffA)
            store.commits.clear()
            journal.kinds.clear()

            resumeSignal(miffA)

            val write = statusWrites().single()
            assertEquals("miffs/miffA/status/uid1", write.path)
            assertEquals("in_trusted", write.data["presence"])
            assertEquals("home", write.data["placeId"])
            assertNotNull("an arrival in a trusted place is also an update", write.data["lastUpdateAt"])
            assertTrue(JournalKind.STATUS_AFTER_RESUME in journal.kinds)
            assertNull("the flag is consumed", local.state!!.resumedMiffs)
            assertEquals(
                "the position the server deleted is published again",
                listOf(
                    "miffs/miffA/positions/uid1",
                ),
                positionWrites().map {
                    it.path
                },
            )

            // Once: another look writes nothing more.
            store.commits.clear()
            engine.refresh(TrackingEngine.Reason.SHARING_CHANGED)
            assertTrue(statusWrites().isEmpty())
        }

    @Test
    fun theStatusKeepsTheKnownTimeOfTheEntry_whenTheStopWasNeverSeenByTheEngine() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            val enteredAt = env.now
            engine.onTransition(TransitionKind.ENTER, home.key, enteredAt)
            env.now += minute
            store.commits.clear()

            resumeSignal(miffA)

            val write = statusWrites().single()
            assertEquals("in_trusted", write.data["presence"])
            assertEquals(Timestamp(Date(enteredAt)), write.data["presenceAt"])
            // The last position is published again, even though the previous one is under the 2 minutes of the guard of frequency.
            assertEquals(1, positionWrites().size)
        }

    @Test
    fun inADangerZone_theStatusIsInRisk_withoutAnyNotificationOfEntry() =
        runTest {
            share(miffA)
            position.lat = lat + 0.01
            engine.refresh(TrackingEngine.Reason.OPENED)
            stopSeenBy(miffA)
            store.commits.clear()
            presences.clear()

            resumeSignal(miffA)

            val write = statusWrites().single()
            assertEquals("in_risk", write.data["presence"])
            assertEquals("danger", write.data["placeId"])
            assertFalse("only an arrival in a trusted place is an update", write.data.containsKey("lastUpdateAt"))
            assertEquals(listOf(PresenceValue.IN_RISK), presences.map { it.presence })
            assertTrue("nobody entered a danger zone just now: no N6", dangerTold.isEmpty())
        }

    @Test
    fun outsideEveryZone_withARecentPosition_theStatusIsOut_now() =
        runTest {
            share(miffA)
            position.lat = lat + 0.05
            engine.refresh(TrackingEngine.Reason.OPENED)
            stopSeenBy(miffA)
            store.commits.clear()

            resumeSignal(miffA)

            val write = statusWrites().single()
            assertEquals("out", write.data["presence"])
            assertNull(write.data["placeId"])
            assertEquals(Timestamp(Date(env.now)), write.data["presenceAt"])
            assertFalse(write.data.containsKey("lastUpdateAt"))
        }

    @Test
    fun withoutAnyPosition_andOutsideEveryKnownZone_nothingIsInvented_theCardStaysGreyUntilTheNextSignal() =
        runTest {
            share(miffA)
            position.available = false
            engine.refresh(TrackingEngine.Reason.OPENED)
            stopSeenBy(miffA)
            store.commits.clear()
            journal.kinds.clear()

            resumeSignal(miffA)

            assertTrue(statusWrites().isEmpty())
            assertFalse(JournalKind.STATUS_AFTER_RESUME in journal.kinds)
            assertNull(local.state!!.miffs[miffA]!!.derived())
            assertNull("the flag is consumed anyway", local.state!!.resumedMiffs)
            // The next signal (an entry in the place) writes the status as usual.
            position.available = true
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertEquals("in_trusted", statusWrites().single().data["presence"])
        }

    @Test
    fun withAnOldPosition_nothingIsInvented() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            stopSeenBy(miffA)
            store.commits.clear()
            // The only position the phone has is 11 minutes old: it is never used (review I2).
            position.ageMillis = 11 * minute

            resumeSignal(miffA)

            assertTrue(statusWrites().isEmpty())
        }

    @Test
    fun anotherMiffSharingTheSameZone_isLeftAlone() =
        runTest {
            atHome(miffA, miffB)
            stopSeenBy(miffA)
            store.commits.clear()

            resumeSignal(miffA)

            assertEquals(listOf("miffs/miffA/status/uid1"), statusWrites().map { it.path })
            assertEquals("in_trusted", statusWrites().single().data["presence"])
            // B keeps what it has: its status, its position and its detection are not written again.
            assertEquals(listOf("miffs/miffA/positions/uid1"), positionWrites().map { it.path })
            assertEquals(
                PresenceValue.IN_TRUSTED,
                local.state!!
                    .miffs[miffB]!!
                    .derived()!!
                    .presence,
            )
        }

    // --- Same guards as every other status ------------------------------------------------------------------------------------------------

    @Test
    fun withoutAlwaysAndPrecise_nothingIsWritten_theResumeWaits_andIsWorkedOutWhenTheAccessIsGrantedLater() =
        runTest {
            atHome(miffA)
            stopSeenBy(miffA)
            store.commits.clear()
            env.snapshot = whileInUseOnly()

            resumeSignal(miffA)

            assertTrue(statusWrites().none { it.data.containsKey("presence") })
            assertTrue(positionWrites().isEmpty())
            assertEquals("the resume is not lost: it waits for the access", setOf(miffA), local.state!!.resumedMiffs)

            // The access is granted later (the app is opened again): the presence is worked out and written.
            env.snapshot = fullPermissions()
            engine.refresh(TrackingEngine.Reason.OPENED)

            val presence = statusWrites().single { it.data.containsKey("presence") }
            assertEquals("in_trusted", presence.data["presence"])
            assertEquals("home", presence.data["placeId"])
            assertNull(local.state!!.resumedMiffs)
            assertEquals(listOf("miffs/miffA/positions/uid1"), positionWrites().map { it.path })
        }

    @Test
    fun aStopSeen_thenAResumeWithoutTheAccessFromTheStart_isWrittenOnceTheAccessIsGranted() =
        runTest {
            env.snapshot = whileInUseOnly()
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            stopSeenBy(miffA)
            resumeSignal(miffA)
            assertTrue(statusWrites().none { it.data.containsKey("presence") })
            assertEquals(setOf(miffA), local.state!!.resumedMiffs)

            env.snapshot = fullPermissions()
            engine.refresh(TrackingEngine.Reason.OPENED)

            assertEquals("in_trusted", statusWrites().single { it.data.containsKey("presence") }.data["presence"])
        }

    @Test
    fun theFlagIsRemovedOnlyBySaveThatFollowsTheNewPresence_soAKilledProcessDoesNotLoseTheResume() =
        runTest {
            val saves = mutableListOf<LocalState>()
            val recording =
                object : TrackingLocalStore {
                    var current: LocalState? = null

                    override fun load() = current

                    override fun save(state: LocalState) {
                        saves += state
                        current = state
                    }

                    override fun clear() {
                        current = null
                    }
                }
            val recorded =
                TrackingEngine(
                    env,
                    store,
                    recording,
                    position,
                    geofences,
                    movement,
                    wake,
                    device,
                    journal,
                    timeoutMillis = 5_000,
                    presenceListener = listener,
                    travelAlarm = alarm,
                )
            share(miffA)
            recorded.refresh(TrackingEngine.Reason.OPENED)
            store.consents[miffA] = stopped()
            recorded.refresh(TrackingEngine.Reason.SHARING_CHANGED)
            store.consents[miffA] = v3()
            recorded.markResumed(miffA)
            val from = saves.size

            recorded.refresh(TrackingEngine.Reason.SHARING_CHANGED)

            // Every copy saved before the new presence still carries the flag; the first one without it already holds the presence to write.
            val firstWithout = saves.drop(from).first { it.resumedMiffs == null }
            assertEquals(PresenceValue.IN_TRUSTED, firstWithout.miffs[miffA]!!.derived()!!.presence)
            assertNotNull(firstWithout.miffs[miffA]!!.pending ?: firstWithout.miffs[miffA]!!.derived())
            assertTrue(saves.drop(from).takeWhile { it !== firstWithout }.all { it.resumedMiffs == setOf(miffA) })
        }

    @Test
    fun afterResume_keepsTheStatusThatEndsATrip_butForgetsTheRest() {
        val waiting = PendingStatus("unknown", null, T0, false)
        val ended =
            LocalMiff(
                presence = "in_trusted",
                presencePlaceId = "home",
                pending = waiting,
                clearTravel = true,
                batteryPct = 50,
                batteryAtMillis = T0,
            )
        val kept = ended.afterResume()
        assertTrue(kept.clearTravel)
        assertNotNull("the status that clears the keys of the trip still waits", kept.pending)
        assertNull(kept.derived())
        assertNull(kept.batteryPct)

        val ordinary =
            LocalMiff(
                presence = "in_trusted",
                presencePlaceId = "home",
                pending = waiting,
                clearTravel = false,
            ).afterResume()
        assertFalse(ordinary.clearTravel)
        assertNull(ordinary.pending)
    }

    @Test
    fun withTheOldConsentText_nothingIsKept_nothingIsWritten() =
        runTest {
            atHome(miffA)
            stopSeenBy(miffA)
            store.commits.clear()
            store.consents[miffA] = SharingConsentRead(enabled = true, version = "2026-10-v2")
            engine.markResumed(miffA)

            engine.refresh(TrackingEngine.Reason.SHARING_CHANGED)

            assertTrue(store.commits.isEmpty())
            assertTrue(local.state!!.miffs.isEmpty())
            assertNull("a complete look said the Miff is not eligible: the flag goes", local.state!!.resumedMiffs)
        }

    @Test
    fun aSessionNotConfirmed_writesNothing_theStatusWaits() =
        runTest {
            atHome(miffA)
            stopSeenBy(miffA)
            store.commits.clear()
            device.answer = Clearance.Replaced

            resumeSignal(miffA)

            assertTrue(statusWrites().isEmpty())
            assertTrue(positionWrites().isEmpty())
            assertNotNull("kept to be written once the session is confirmed", local.state!!.miffs[miffA]!!.pending)
        }

    @Test
    fun aLookThatCouldNotFinish_keepsTheFlagForTheNextOne() =
        runTest {
            atHome(miffA)
            stopSeenBy(miffA)
            store.commits.clear()
            store.consents[miffA] = v3()
            engine.markResumed(miffA)
            store.readFailure = FirebaseFirestoreException("x", FirebaseFirestoreException.Code.UNAVAILABLE)

            engine.refresh(TrackingEngine.Reason.SHARING_CHANGED)

            assertTrue(statusWrites().isEmpty())
            assertEquals(setOf(miffA), local.state!!.resumedMiffs)
            store.readFailure = null
            engine.refresh(TrackingEngine.Reason.SHARING_CHANGED)
            assertEquals("in_trusted", statusWrites().single().data["presence"])
            assertNull(local.state!!.resumedMiffs)
        }

    @Test
    fun theFlagIsErasedWithTheLocalCopy_atSignOut() =
        runTest {
            atHome(miffA)
            engine.markResumed(miffA)
            assertEquals(setOf(miffA), local.state!!.resumedMiffs)
            engine.stop()
            assertNull(local.state)
        }

    // --- Trip (mode voyage) --------------------------------------------------------------------------------------------------------------

    @Test
    fun duringATrip_noOrdinaryPresenceIsWritten_theTripIsToldAgain_andThePositionIsPublishedAgain() =
        runTest {
            atHome(miffA)
            assertEquals(TravelResult.Done, engine.setTravel(TravelPeriod(T0 + 2 * day, "2027-01-17")))
            store.commits.clear()
            journal.kinds.clear()

            resumeSignal(miffA)

            // The status that the resume wrote says `unknown` without the trip: it is told again, and nothing else.
            assertEquals(listOf("travel"), statusWrites().map { it.data["presence"] })
            assertFalse(JournalKind.STATUS_AFTER_RESUME in journal.kinds)
            assertEquals(listOf("miffs/miffA/positions/uid1"), positionWrites().map { it.path })
            assertTrue(dangerTold.isEmpty())
            assertNull(local.state!!.resumedMiffs)
        }
}
