package com.tomessa.miff.data.tracking

import com.tomessa.miff.data.miff.SharingConsentRead
import com.tomessa.miff.data.timeshare.StoredPresenceLog
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.timeshare.PlaceRef
import com.tomessa.miff.domain.timeshare.SliceKind
import com.tomessa.miff.domain.timeshare.TimeShare
import com.tomessa.miff.domain.timeshare.TimeShareResult
import com.tomessa.miff.domain.tracking.PlaceFact
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.TransitionKind
import com.tomessa.miff.domain.travel.TravelPeriod
import com.tomessa.miff.testsupport.FailingPresenceLog
import com.tomessa.miff.testsupport.FakeActiveDevice
import com.tomessa.miff.testsupport.FakeGeofences
import com.tomessa.miff.testsupport.FakeLocal
import com.tomessa.miff.testsupport.FakeMovement
import com.tomessa.miff.testsupport.FakeNetworkWake
import com.tomessa.miff.testsupport.FakePosition
import com.tomessa.miff.testsupport.FakeTrackingEnv
import com.tomessa.miff.testsupport.FakeTrackingStore
import com.tomessa.miff.testsupport.FakeTravelAlarm
import com.tomessa.miff.testsupport.MemoryPresenceLogStorage
import com.tomessa.miff.testsupport.RecordingJournal
import com.tomessa.miff.testsupport.RecordingPresenceLog
import com.tomessa.miff.testsupport.T0
import com.tomessa.miff.testsupport.whileInUseOnly
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TIME-1, criterion TS-8b of docs/tickets/profil-repartition-temps.md: the journal of the presence REPRODUCES the presence the engine works out. At every change of the presence
 * of a Miff, a fact with the same state, the same place and the SAME INSTANT as the status that is written is kept (so the Feed and the chart can never disagree), and nothing is
 * invented: an "unknown" where the engine cannot confirm, never an "out" that was not seen. The scenarios are those of the engine tests of the Feed. Doubles for the phone, the
 * system and Firestore.
 */
class TrackingEnginePresenceLogTest {
    private val miffA = "miffA"
    private val miffB = "miffB"
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
    private val alarm = FakeTravelAlarm()
    private val storage = MemoryPresenceLogStorage()
    private val log = StoredPresenceLog(storage, { env.uid }, { env.now })
    private val engine = engineWith(log)

    private fun engineWith(presence: com.tomessa.miff.domain.timeshare.PresenceLog) =
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
            presenceLog = presence,
        )

    private val lat = 48.8566
    private val lng = 2.3522
    private val home = PlaceFact("home", lat, lng, 100, PlaceType.TRUSTED)
    private val danger = PlaceFact("danger", lat + 0.01, lng, 100, PlaceType.RISK)

    private fun v3() = SharingConsentRead(enabled = true, version = "2026-11-v03")

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

    /** What the status said, for a Miff, in the order written: (state, place, instant). The place of an `out` is the one just left: the journal keeps none for it. */
    private fun statusesOf(miffId: String): List<Triple<String, String?, Long>> =
        statusWrites()
            .filter { it.path.startsWith("miffs/$miffId/") }
            .map {
                val state = it.data["presence"] as String
                val place = (it.data["placeId"] as String?).takeIf { _ -> state == "in_trusted" || state == "in_risk" }
                Triple(state, place, (it.data["presenceAt"] as com.google.firebase.Timestamp).toDate().time)
            }

    private fun factsOf(miffId: String): List<Triple<String, String?, Long>> =
        log.facts(miffId).map { Triple(it.presence.wire, it.placeId, it.atMillis) }

    /** TS-8b: the journal says exactly what the statuses said (same state, same place, same instant). */
    private fun assertTheJournalFollowsTheStatuses(vararg miffs: String) {
        miffs.forEach { assertEquals("the journal of $it follows its statuses", statusesOf(it), factsOf(it)) }
    }

    // --- An arrival and a departure (the Feed scenarios) ------------------------------------------------------------------------------------------------

    @Test
    fun anArrivalAndADeparture_areKeptWithTheInstantOfTheStatus_perMiff() =
        runTest {
            share(miffA, miffB)
            engine.refresh(TrackingEngine.Reason.OPENED)
            env.now += 10 * minute
            val arrived = env.now - 2 * minute
            engine.onTransition(TransitionKind.ENTER, home.key, arrived)
            env.now += 3 * hour
            val left = env.now - minute
            engine.onTransition(TransitionKind.EXIT, home.key, left)
            assertEquals(listOf(Triple("in_trusted", "home", arrived), Triple("out", null, left)), factsOf(miffA))
            assertTheJournalFollowsTheStatuses(miffA, miffB)
        }

    @Test
    fun theTimeOfAnEventInTheFuture_isCappedAtNow_likeTheStatus() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now + 3 * hour)
            assertEquals(env.now, log.facts(miffA).single().atMillis)
            assertTheJournalFollowsTheStatuses(miffA)
        }

    @Test
    fun anEventTooOldForTheRules_isKeptAtTheMomentItArrived_neverFromAnOldDate() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now - 30 * hour)
            // The status gives it up (more than 24 h old); the journal counts the presence from now, never from a date the Feed would not show.
            assertTrue(statusWrites().isEmpty())
            assertEquals(env.now, log.facts(miffA).single().atMillis)
        }

    @Test
    fun anArrivalSeenAgain_forAZoneThePersonIsAlreadyIn_isNotAnotherFact() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            env.now += 10 * minute
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertEquals(1, log.facts(miffA).size)
            assertTheJournalFollowsTheStatuses(miffA)
        }

    // --- The counter "Ton graphique arrive dans X jours" (PM feedback, recette on OPPO, 2026-11-25) ----------------------------------------------------------

    private fun notEnoughNow(miffId: String): TimeShareResult.NotEnoughData =
        TimeShare.compute(
            log.facts(miffId),
            listOf(PlaceRef("home", PlaceType.TRUSTED)),
            7,
            env.now,
            startedAtMillis = log.startedAt(miffId),
        ) as TimeShareResult.NotEnoughData

    private fun daysLeftNow(miffId: String): Int = notEnoughNow(miffId).daysLeft

    @Test
    fun theCounterOfTheChart_goesDownDayAfterDay_evenWhenThePersonHasNotMovedYet() =
        runTest {
            share(miffA)
            // The app is open and the zones armed, but no arrival or departure has been reported yet (the person stays where they are).
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertTrue(log.facts(miffA).isEmpty())
            val first = daysLeftNow(miffA)
            env.now += 25 * hour
            engine.refresh(TrackingEngine.Reason.OPENED)
            val second = daysLeftNow(miffA)
            env.now += 24 * hour
            engine.refresh(TrackingEngine.Reason.OPENED)
            val third = daysLeftNow(miffA)
            assertEquals("the counter must go down by one a day", listOf(first - 1, first - 2), listOf(second, third))
            assertEquals(3, first)
            // 49 h after the arming and still no arrival or departure: "1 jour", the wait is not over yet.
            assertEquals(1, third)
            assertFalse(notEnoughNow(miffA).historyReached)
            // Past 3 days with nothing measured (the person never entered a place): no countdown any more, the calm text takes over.
            env.now += 24 * hour
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertTrue(notEnoughNow(miffA).historyReached)
        }

    @Test
    fun theStartOfTheHistory_isTheFirstArming_notTheLaterOnes_andIsKeptPerMiff() =
        runTest {
            share(miffA, miffB)
            val armed = env.now
            engine.refresh(TrackingEngine.Reason.OPENED)
            env.now += 30 * hour
            engine.refresh(TrackingEngine.Reason.OPENED)
            // The first arming counts, for each Miff; the later passes change nothing.
            assertEquals(armed, log.startedAt(miffA))
            assertEquals(armed, log.startedAt(miffB))
            assertNull(log.startedAt("miffC"))
        }

    @Test
    fun theStartOfTheHistory_isRecordedOnlyForTheMiffsTheDetectionIsArmedFor() =
        runTest {
            // Nobody shares anywhere (no consent of version 3): nothing is watched, so no history starts.
            store.memberships = listOf(MembershipRef("k$miffA", miffA))
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertNull(log.startedAt(miffA))
        }

    @Test
    fun aJournalThatFailsAtTheStart_neverStopsTheDetection() =
        runTest {
            val failing = engineWith(FailingPresenceLog())
            share(miffA)
            failing.refresh(TrackingEngine.Reason.OPENED)
            failing.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertEquals(1, statusWrites().filter { it.path.startsWith("miffs/$miffA/") }.size)
        }

    // --- T17 and T18: the trusted zone wins ------------------------------------------------------------------------------------------------------------

    @Test
    fun t17_aDangerVisitInsideATrustedZone_changesNothing_noDangerTime() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            val t0 = env.now
            engine.onTransition(TransitionKind.ENTER, home.key, t0)
            env.now = t0 + hour
            engine.onTransition(TransitionKind.ENTER, danger.key, env.now)
            env.now = t0 + 3 * hour
            engine.onTransition(TransitionKind.EXIT, danger.key, env.now)
            env.now = t0 + 6 * hour
            engine.onTransition(TransitionKind.EXIT, home.key, env.now)
            // Same as the Feed: the trusted zone wins, so the presence went from "in A" to "out" and nothing else.
            assertEquals(listOf(PresenceValue.IN_TRUSTED, PresenceValue.OUT), log.facts(miffA).map { it.presence })
            assertTheJournalFollowsTheStatuses(miffA)
            // And the chart worked out from it: 6 h in A and no danger zone at all.
            val tally =
                TimeShare.tally(
                    log.facts(miffA),
                    listOf(PlaceRef("home", PlaceType.TRUSTED), PlaceRef("danger", PlaceType.RISK)),
                    7,
                    env.now,
                )
            assertEquals(6 * hour, tally.trusted["home"])
            assertTrue(tally.danger.isEmpty())
        }

    @Test
    fun t18_aDangerZoneAlone_isKeptAndCounted() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            val t0 = env.now
            env.now = t0 + hour
            engine.onTransition(TransitionKind.ENTER, danger.key, env.now)
            env.now = t0 + 3 * hour
            engine.onTransition(TransitionKind.EXIT, danger.key, env.now)
            assertEquals(listOf(PresenceValue.IN_RISK, PresenceValue.OUT), log.facts(miffA).map { it.presence })
            assertTheJournalFollowsTheStatuses(miffA)
            val tally = TimeShare.tally(log.facts(miffA), listOf(PlaceRef("danger", PlaceType.RISK)), 7, env.now)
            assertEquals(2 * hour, tally.danger["danger"])
        }

    @Test
    fun leavingTheTrustedZoneWhileStayingInTheDangerZone_isAFactOfDanger() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            engine.onTransition(TransitionKind.ENTER, danger.key, env.now + minute)
            engine.onTransition(TransitionKind.EXIT, home.key, env.now + 2 * minute)
            assertEquals(listOf(PresenceValue.IN_TRUSTED, PresenceValue.IN_RISK), log.facts(miffA).map { it.presence })
            assertEquals("danger", log.facts(miffA).last().placeId)
            assertTheJournalFollowsTheStatuses(miffA)
        }

    // --- Nothing is invented ----------------------------------------------------------------------------------------------------------------------------

    @Test
    fun openingWithNoEvent_leavesTheJournalEmpty_noOutIsGuessed() =
        runTest {
            share(miffA)
            position.lat = lat + 0.5
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertTrue(log.facts(miffA).isEmpty())
            // No fact, no "out" guessed: the file holds only the start of the follow-up (the Miff and an instant), nothing else.
            assertEquals("""{"uid":"uid1","since":{"miffA":$T0}}""", storage.text)
        }

    @Test
    fun aPlaceDeletedWhileThePersonWasInIt_becomesUnknown_andTheJournalForgetsThePlace() =
        runTest {
            val recording = RecordingPresenceLog()
            val tracked = engineWith(recording)
            share(miffA)
            tracked.refresh(TrackingEngine.Reason.OPENED)
            tracked.onTransition(TransitionKind.ENTER, home.key, env.now)
            // The places are read again after an hour (not before: nothing is read from the server for nothing).
            env.now += hour + minute
            // The creator deletes "home": the next look at the places (a change of sharing, a restart) finds it gone.
            store.places[miffA] = listOf(danger)
            tracked.refresh(TrackingEngine.Reason.SHARING_CHANGED)
            assertEquals(listOf(miffA to "home"), recording.forgottenPlaces)
            val last = recording.calls.last()
            assertEquals(PresenceValue.UNKNOWN, last.presence)
            assertEquals(env.now, last.atMillis)
        }

    @Test
    fun aPlaceMovedByItsCreator_whileThePersonWasInIt_becomesUnknown() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            env.now += hour + minute
            store.places[miffA] = listOf(PlaceFact("home", lat + 0.05, lng, 100, PlaceType.TRUSTED), danger)
            engine.refresh(TrackingEngine.Reason.SHARING_CHANGED)
            assertEquals(listOf(PresenceValue.IN_TRUSTED, PresenceValue.UNKNOWN), log.facts(miffA).map { it.presence })
            assertEquals(env.now, log.facts(miffA).last().atMillis)
        }

    @Test
    fun aRestartWithoutARecentPosition_isUnknown_neverOut() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            env.now += 2 * hour
            position.available = false
            engine.refresh(TrackingEngine.Reason.REARM)
            // With a position that says "outside", the engine writes unknown (it did not see the departure): so does the journal.
            val states = log.facts(miffA).map { it.presence }
            assertTrue(PresenceValue.OUT !in states)
        }

    // --- Sharing, permission, trip ----------------------------------------------------------------------------------------------------------------------

    @Test
    fun aSharingStopped_isUnknownFromThen_andTheJournalOfThatMiffIsKept() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            env.now += hour
            store.consents[miffA] = SharingConsentRead(enabled = false, version = "2026-11-v03")
            engine.refresh(TrackingEngine.Reason.SHARING_CHANGED)
            assertEquals(listOf(PresenceValue.IN_TRUSTED, PresenceValue.UNKNOWN), log.facts(miffA).map { it.presence })
            assertEquals(env.now, log.facts(miffA).last().atMillis)
        }

    @Test
    fun aSharingResumed_startsAgainFromWhereThePositionPutsThePerson() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            env.now += hour
            store.consents[miffA] = SharingConsentRead(enabled = false, version = "2026-11-v03")
            engine.refresh(TrackingEngine.Reason.SHARING_CHANGED)
            env.now += hour
            store.consents[miffA] = v3()
            engine.markResumed(miffA)
            engine.refresh(TrackingEngine.Reason.SHARING_CHANGED)
            val last = log.facts(miffA).last()
            assertEquals(PresenceValue.IN_TRUSTED, last.presence)
            assertEquals("home", last.placeId)
            // The phone forgot where the person was while the sharing was stopped: the presence starts again from the position of this pass, now.
            assertEquals(env.now, last.atMillis)
        }

    @Test
    fun theAccessWithdrawn_isUnknownFromThen_noTimeIsMeasuredWithoutAccess() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            env.now += hour
            env.snapshot = whileInUseOnly()
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(listOf(PresenceValue.IN_TRUSTED, PresenceValue.UNKNOWN), log.facts(miffA).map { it.presence })
            // Opened again with the access still missing: nothing more is written (the same state is not kept twice).
            env.now += hour
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(2, log.facts(miffA).size)
        }

    @Test
    fun t23_aTrip_isUnknownFromItsBeginning_untilItEnds() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            val entered = env.now
            engine.onTransition(TransitionKind.ENTER, home.key, entered)
            env.now += 2 * hour
            val begun = env.now
            assertEquals(
                com.tomessa.miff.data.tracking.TravelResult.Done,
                engine.setTravel(TravelPeriod(T0 + 4 * 24 * hour, "2027-01-19")),
            )
            assertEquals(listOf(PresenceValue.IN_TRUSTED, PresenceValue.UNKNOWN), log.facts(miffA).map { it.presence })
            assertEquals(begun, log.facts(miffA).last().atMillis)
            // Nothing is worked out during the trip: an event that arrives is dropped by the engine, and the journal stays as it was.
            env.now += 60 * hour
            engine.onTransition(TransitionKind.EXIT, home.key, env.now)
            assertEquals(2, log.facts(miffA).size)
            // The trip ends: still unknown (the engine never invents a departure), the person is not in a place.
            position.lat = lat + 0.5
            assertTrue(engine.endTravel())
            assertEquals(listOf(PresenceValue.IN_TRUSTED, PresenceValue.UNKNOWN), log.facts(miffA).map { it.presence })
            // The 60 h of the trip are not measured: only the 2 h before it are.
            val tally = TimeShare.tally(log.facts(miffA), listOf(PlaceRef("home", PlaceType.TRUSTED)), 7, env.now)
            assertEquals(2 * hour, tally.trusted["home"])
            assertEquals(0L, tally.elsewhere)
        }

    @Test
    fun aTripEndedWhileThePersonIsInAPlace_startsAgainFromThePlace() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(
                com.tomessa.miff.data.tracking.TravelResult.Done,
                engine.setTravel(TravelPeriod(T0 + 4 * 24 * hour, "2027-01-19")),
            )
            env.now += 30 * hour
            assertTrue(engine.endTravel())
            // The position read at the end of the trip is inside "home": an arrival is what "already there" means.
            val last = log.facts(miffA).lastOrNull()
            assertEquals(PresenceValue.IN_TRUSTED, last?.presence)
            assertEquals("home", last?.placeId)
        }

    // --- End of the session, and a journal that fails --------------------------------------------------------------------------------------------------

    @Test
    fun theEndOfTheSession_erasesTheJournal() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertTrue(storage.text != null)
            engine.stop()
            assertNull(storage.text)
            assertTrue(log.facts(miffA).isEmpty())
        }

    @Test
    fun aJournalThatFails_neverStopsTheDetection() =
        runTest {
            val broken = engineWith(FailingPresenceLog())
            share(miffA)
            broken.refresh(TrackingEngine.Reason.OPENED)
            broken.onTransition(TransitionKind.ENTER, home.key, env.now)
            broken.onTransition(TransitionKind.EXIT, home.key, env.now + minute)
            // The statuses were written all the same.
            assertEquals(listOf("in_trusted", "out"), statusWrites().map { it.data["presence"] })
            broken.stop()
        }

    @Test
    fun theJournalIsFilledEvenWhenTheStatusCannotBeWritten_offline() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            env.online = false
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            // The statuses wait for the network; the chart does not need it (it is local).
            assertTrue(statusWrites().isEmpty())
            assertEquals(PresenceValue.IN_TRUSTED, log.facts(miffA).single().presence)
        }

    @Test
    fun theChartFromARealisticDay_namesTheHomeAndTheElsewhere() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            val start = env.now
            // 10 h at home, 8 h out, then home again for 10 h: 28 h measured.
            engine.onTransition(TransitionKind.ENTER, home.key, start)
            env.now = start + 10 * hour
            engine.onTransition(TransitionKind.EXIT, home.key, env.now)
            env.now = start + 18 * hour
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            env.now = start + 28 * hour
            // 28 h of journal: under the 3 days the chart waits for (2 days left to wait: 3 days minus a day and a bit), but the measure is already right.
            val tally = TimeShare.tally(log.facts(miffA), listOf(PlaceRef("home", PlaceType.TRUSTED)), 7, env.now)
            assertEquals(20 * hour, tally.trusted["home"])
            assertEquals(8 * hour, tally.elsewhere)
            val result = TimeShare.compute(log.facts(miffA), listOf(PlaceRef("home", PlaceType.TRUSTED)), 7, env.now)
            assertTrue(result is TimeShareResult.NotEnoughData)
            assertEquals(2, (result as TimeShareResult.NotEnoughData).daysLeft)
        }
}
