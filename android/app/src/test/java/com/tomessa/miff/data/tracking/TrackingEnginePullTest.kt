package com.tomessa.miff.data.tracking

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.data.miff.SharingConsentRead
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.permission.PermissionSnapshot
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
import com.tomessa.miff.testsupport.whileInUseOnly
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * FEED-REFRESH-POSITION (docs/tickets/feed-refresh-position.md): the pull to refresh of the Feed checks MY presence again, through the SAME pass as an opening. What the tests pin down:
 * ONE position per pull (and no more than one per 30 s), the presence worked out again by the same machine and told like any other change (listener, journal), a status written ONLY when a
 * presence changes (never to confirm), nothing at all without the access, offline, during a trip, when I share nowhere, nor while another pass is under way, and a position that never comes
 * back ends the pass by itself. Doubles for the phone, the system and Firestore: no real position, no network.
 */
class TrackingEnginePullTest {
    private val miffA = "miffA"
    private val minute = 60_000L

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
        val atMillis: Long,
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
                presences += Told(miffId, presence, atMillis)
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

    // `home` (trusted, 100 m) is under the feet of the person; `danger` is 1.1 km north. One thousandth of a degree of latitude is about 111 m.
    private val lat = 48.8566
    private val lng = 2.3522
    private val home = PlaceFact("home", lat, lng, 100, PlaceType.TRUSTED)
    private val danger = PlaceFact("danger", lat + 0.01, lng, 100, PlaceType.RISK)
    private val farNorth = lat + 0.05

    private fun v3() = SharingConsentRead(enabled = true, version = "2026-11-v03")

    private fun share(vararg miffs: String) {
        store.memberships = miffs.map { MembershipRef("k$it", it) }
        miffs.forEach {
            store.consents[it] = v3()
            store.places[it] = listOf(home, danger)
        }
    }

    private fun statusWrites() = store.writesTo("/status/")

    private fun positionWrites() = store.writesTo("/positions/")

    /** The person is far from every place, the engine is running, nothing is known of any presence (no event yet). */
    private suspend fun openedFarAway() {
        share(miffA)
        position.lat = farNorth
        engine.refresh(TrackingEngine.Reason.OPENED)
        store.commits.clear()
        presences.clear()
        journal.kinds.clear()
    }

    /** The person is at home and the geofencing told it (the usual arrival); then the commits are forgotten. */
    private suspend fun atHome() {
        share(miffA)
        engine.refresh(TrackingEngine.Reason.OPENED)
        engine.onTransition(TransitionKind.ENTER, home.key, env.now)
        assertEquals(1, statusWrites().size)
        store.commits.clear()
        presences.clear()
        journal.kinds.clear()
    }

    // --- What the pull finds and tells ---------------------------------------------------------------------------------------------------

    @Test
    fun anArrivalTheGeofencingDidNotReport_isNoticedAtAPull_andWrittenOnceAtTheInstantItIsNoticed() =
        runTest {
            openedFarAway()
            env.now += 3 * minute
            position.lat = lat

            assertEquals(PullOutcome.DONE, engine.refreshFromFeed())

            val write = statusWrites().single()
            assertEquals("miffs/miffA/status/uid1", write.path)
            assertEquals("in_trusted", write.data["presence"])
            assertEquals("home", write.data["placeId"])
            // The instant the phone NOTICED it (the real one is not known); an arrival in a trusted place is also an update, as any arrival.
            assertEquals(Timestamp(Date(env.now)), write.data["presenceAt"])
            assertNotNull(write.data["lastUpdateAt"])
            // The same machinery as a real event: the reminders are told, the journal says so, the position is published under its usual guard.
            assertEquals(listOf(PresenceValue.IN_TRUSTED), presences.map { it.presence })
            assertEquals(env.now, presences.single().atMillis)
            assertTrue(JournalKind.TRANSITION_ENTER in journal.kinds)
            assertEquals(1, positionWrites().size)
            assertEquals(
                "home",
                local.state!!
                    .miffs
                    .getValue(miffA)
                    .derived()!!
                    .placeId,
            )
        }

    @Test
    fun aDepartureTheGeofencingDidNotReport_isNoticedAtAPull_asUnknown_neverAnInventedOut() =
        runTest {
            atHome()
            env.now += 3 * minute
            position.lat = farNorth

            assertEquals(PullOutcome.DONE, engine.refreshFromFeed())

            // The real time of the departure is not known (it may be hours ago): `unknown` at the instant it is noticed, as after a restart of the phone and as on iOS.
            val write = statusWrites().single()
            assertEquals("unknown", write.data["presence"])
            assertFalse(
                "a departure is not an update: the colour keeps ageing from what the loved ones know",
                write.data.containsKey("lastUpdateAt"),
            )
            assertEquals(Timestamp(Date(env.now)), write.data["presenceAt"])
            assertEquals(listOf(PresenceValue.UNKNOWN), presences.map { it.presence })
            assertTrue(JournalKind.PRESENCE_UNKNOWN in journal.kinds)
            // A second pull, later, at the same place: nothing more (no loop on an `unknown`).
            env.now += 31_000
            store.commits.clear()
            engine.refreshFromFeed()
            assertTrue(statusWrites().isEmpty())
            // The geofencing's own (late) departure, if it ever comes, tells the real thing.
            engine.onTransition(TransitionKind.EXIT, home.key, env.now)
            assertEquals("out", statusWrites().single().data["presence"])
        }

    @Test
    fun theEdgeOfAPlace_isNotADeparture_theMarginAbsorbsTheErrorOfTheBalancedPosition() =
        runTest {
            atHome()
            env.now += 3 * minute
            // About 150 m from the centre of a 100 m place: outside the radius, inside radius + 100 m.
            position.lat = lat + 0.00135

            engine.refreshFromFeed()

            assertTrue("a person at the edge is not told as having left", statusWrites().isEmpty())
            assertEquals(
                PresenceValue.IN_TRUSTED,
                local.state!!
                    .miffs
                    .getValue(miffA)
                    .derived()!!
                    .presence,
            )
            assertTrue(presences.isEmpty())
        }

    @Test
    fun aPresenceThatIsAlreadyRight_writesNoStatus_noConfirmationIsMadeUp() =
        runTest {
            atHome()
            env.now += 3 * minute
            val armedBefore = geofences.updates
            val movementBefore = movement.startCalls

            assertEquals(PullOutcome.DONE, engine.refreshFromFeed())

            assertTrue("still at home: nothing to tell", statusWrites().isEmpty())
            assertTrue(presences.isEmpty())
            // No re-arming, no new request of moves: a pull is not an opening.
            assertEquals(armedBefore, geofences.updates)
            assertEquals(movementBefore, movement.startCalls)
        }

    @Test
    fun outsideEveryPlace_withNoPresenceYet_noOutIsInvented() =
        runTest {
            openedFarAway()
            env.now += 3 * minute

            assertEquals(PullOutcome.DONE, engine.refreshFromFeed())

            assertTrue(statusWrites().isEmpty())
            assertNull(
                local.state!!
                    .miffs
                    .getValue(miffA)
                    .derived(),
            )
        }

    @Test
    fun anArrivalInADangerZone_isToldOnce_likeAnyOtherEntry() =
        runTest {
            openedFarAway()
            env.now += 3 * minute
            position.lat = lat + 0.01

            engine.refreshFromFeed()

            assertEquals("in_risk", statusWrites().single().data["presence"])
            assertEquals(listOf(miffA), dangerTold)
            // A second pull, later, at the same place: nothing more.
            env.now += 31_000
            store.commits.clear()
            engine.refreshFromFeed()
            assertTrue(statusWrites().isEmpty())
            assertEquals(listOf(miffA), dangerTold)
        }

    @Test
    fun aPositionTooOldToSayWhereThePersonIsNow_movesNothing() =
        runTest {
            openedFarAway()
            env.now += 3 * minute
            position.lat = lat
            // 45 s old: good enough to order the zones (10 minutes), not to tell an arrival ("now" means 30 s at most).
            position.ageMillis = 45_000L

            engine.refreshFromFeed()

            assertTrue(statusWrites().isEmpty())
            assertNull(
                local.state!!
                    .miffs
                    .getValue(miffA)
                    .derived(),
            )
        }

    @Test
    fun aPositionOf20Seconds_stillSaysWhereThePersonIsNow() =
        runTest {
            openedFarAway()
            env.now += 3 * minute
            position.lat = lat
            position.ageMillis = 20_000L

            engine.refreshFromFeed()

            assertEquals("in_trusted", statusWrites().single().data["presence"])
        }

    @Test
    fun aPullAsksTheProviderForANowPosition_notForTheTwoMinuteCopy() =
        runTest {
            openedFarAway()
            val fresh = position.freshReads
            env.now += 3 * minute
            engine.refreshFromFeed()
            assertEquals(fresh + 1, position.freshReads)
            // The other passes (an opening here) keep the usual reading.
            val before = position.freshReads
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(before, position.freshReads)
        }

    // --- The limits -----------------------------------------------------------------------------------------------------------------------

    @Test
    fun atMostOnePositionEvery30Seconds_howeverOftenThePersonPulls() =
        runTest {
            openedFarAway()
            val readsAfterOpening = position.reads
            env.now += 3 * minute

            assertEquals(PullOutcome.DONE, engine.refreshFromFeed())
            assertEquals(readsAfterOpening + 1, position.reads)

            env.now += 29_000
            assertEquals(PullOutcome.THROTTLED, engine.refreshFromFeed())
            env.now += 500
            assertEquals(PullOutcome.THROTTLED, engine.refreshFromFeed())
            assertEquals("no second reading inside 30 s", readsAfterOpening + 1, position.reads)

            env.now += 1_000
            assertEquals(PullOutcome.DONE, engine.refreshFromFeed())
            assertEquals(readsAfterOpening + 2, position.reads)
        }

    @Test
    fun aThrottledPull_readsNothingOfTheServerEither() =
        runTest {
            openedFarAway()
            env.now += 3 * minute
            engine.refreshFromFeed()
            val reads = store.readCalls
            env.now += 5_000
            assertEquals(PullOutcome.THROTTLED, engine.refreshFromFeed())
            assertEquals(reads, store.readCalls)
            assertTrue(statusWrites().isEmpty() && positionWrites().isEmpty())
        }

    @Test
    fun aPassAlreadyUnderWay_isNotQueued_nothingPilesUp() =
        runTest {
            share(miffA)
            val held = CompletableDeferred<Unit>()
            store.onPlaces = { held.await() }
            val opening = launch { engine.refresh(TrackingEngine.Reason.OPENED) }
            runCurrent()

            assertEquals(PullOutcome.BUSY, engine.refreshFromFeed())
            assertEquals("the pull read no position while the engine was busy", 0, position.reads)

            held.complete(Unit)
            opening.join()
        }

    @Test
    fun aPositionThatNeverComesBack_endsThePassBySelf_andWritesNothing() =
        runTest {
            openedFarAway()
            env.now += 3 * minute
            position.hangs = true

            // The engine waits 3 seconds for a position at most (virtual time here), then goes on with nothing.
            assertEquals(PullOutcome.DONE, engine.refreshFromFeed())

            assertTrue(statusWrites().isEmpty())
            assertTrue(JournalKind.POSITION_UNAVAILABLE in journal.kinds)
        }

    @Test
    fun noPositionAvailable_isCalm_nothingIsWritten() =
        runTest {
            atHome()
            env.now += 3 * minute
            position.available = false

            assertEquals(PullOutcome.DONE, engine.refreshFromFeed())

            assertTrue(statusWrites().isEmpty())
            assertEquals(
                "the presence is not touched without a position",
                PresenceValue.IN_TRUSTED,
                local.state!!
                    .miffs
                    .getValue(miffA)
                    .derived()!!
                    .presence,
            )
        }

    // --- Gates: nothing is read, nothing is written -----------------------------------------------------------------------------------------

    @Test
    fun withoutTheAlwaysAndPreciseAccess_nothingIsRead_andTheFeedCanSayItCalmly() =
        runTest {
            atHome()
            env.now += 3 * minute
            val reads = position.reads

            // "While in use" only, then the approximate position only, then nothing at all, then the location of the phone itself switched off.
            val approximateOnly =
                PermissionSnapshot(
                    sdk = 33,
                    fineLocation = false,
                    coarseLocation = true,
                    backgroundLocation = true,
                    notifications = true,
                )
            val nothing =
                PermissionSnapshot(
                    sdk = 33,
                    fineLocation = false,
                    coarseLocation = false,
                    backgroundLocation = false,
                    notifications = true,
                )
            for (snapshot in listOf(whileInUseOnly(), approximateOnly, nothing)) {
                env.snapshot = snapshot
                assertEquals(PullOutcome.NO_ACCESS, engine.refreshFromFeed())
                env.now += 31_000
            }
            env.snapshot =
                com.tomessa.miff.testsupport
                    .fullPermissions()
            env.servicesOn = false
            assertEquals(PullOutcome.NO_ACCESS, engine.refreshFromFeed())

            assertEquals(reads, position.reads)
            assertTrue(statusWrites().isEmpty() && positionWrites().isEmpty())
            // A pull never disarms anything by itself (the opening does, with its own message to the Miffs): the local copy is untouched.
            assertEquals(
                PresenceValue.IN_TRUSTED,
                local.state!!
                    .miffs
                    .getValue(miffA)
                    .derived()!!
                    .presence,
            )
        }

    @Test
    fun aRefusedAccess_doesNotUseUpTheLimit_theNextPullAfterTheAccessIsBackReadsAtOnce() =
        runTest {
            openedFarAway()
            env.now += 3 * minute
            env.snapshot = whileInUseOnly()
            assertEquals(PullOutcome.NO_ACCESS, engine.refreshFromFeed())
            env.snapshot =
                com.tomessa.miff.testsupport
                    .fullPermissions()
            position.lat = lat
            assertEquals(PullOutcome.DONE, engine.refreshFromFeed())
            assertEquals("in_trusted", statusWrites().single().data["presence"])
        }

    @Test
    fun offline_nothingIsRead_andTheLimitIsNotUsedUp() =
        runTest {
            openedFarAway()
            env.now += 3 * minute
            position.lat = lat
            val reads = position.reads
            env.online = false

            assertEquals(PullOutcome.OFFLINE, engine.refreshFromFeed())

            assertEquals(reads, position.reads)
            assertTrue(statusWrites().isEmpty())
            env.online = true
            assertEquals(PullOutcome.DONE, engine.refreshFromFeed())
        }

    @Test
    fun duringATrip_noPresenceIsWorkedOut_andNoStatusIsWritten_butThePositionIsRefreshedAsAnOpeningDoes() =
        runTest {
            atHome()
            val trip = TravelPeriod(T0 + 2 * 24 * 60 * minute, "2027-01-17")
            assertEquals(TravelResult.Done, engine.setTravel(trip))
            store.commits.clear()
            presences.clear()
            env.now += 3 * minute
            // The person is somewhere else, at 5 km: no presence is worked out and no status of presence is written, the trip stays what the loved ones see.
            position.lat = farNorth
            val reads = position.reads

            assertEquals(PullOutcome.DONE, engine.refreshFromFeed())

            assertEquals("one reading, as an opening does", reads + 1, position.reads)
            assertTrue("no status during a trip", statusWrites().isEmpty())
            assertTrue(presences.isEmpty())
            // The position stays shared during a trip (decision of the PM, 2026-11-10) and goes through the usual guard of frequency.
            assertEquals(1, positionWrites().size)
            assertNotNull("the trip is untouched", local.state!!.travel)
        }

    @Test
    fun duringATrip_withoutTheAccess_nothingIsRead() =
        runTest {
            atHome()
            assertEquals(TravelResult.Done, engine.setTravel(TravelPeriod(T0 + 2 * 24 * 60 * minute, "2027-01-17")))
            store.commits.clear()
            env.snapshot = whileInUseOnly()
            val reads = position.reads

            assertEquals(PullOutcome.NO_ACCESS, engine.refreshFromFeed())

            assertEquals(reads, position.reads)
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun sharingNowhere_orNothingKnownYet_nothingIsRead() =
        runTest {
            // Nothing is known yet (no local copy): no reading of the server, no position.
            assertEquals(PullOutcome.NOT_SHARING, engine.refreshFromFeed())
            // The person shares in no Miff.
            store.memberships = emptyList()
            engine.refresh(TrackingEngine.Reason.OPENED)
            val reads = position.reads
            assertEquals(PullOutcome.NOT_SHARING, engine.refreshFromFeed())
            assertEquals(reads, position.reads)
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun sharingStoppedInTheMiff_isTheSameAsSharingNowhere() =
        runTest {
            atHome()
            store.consents[miffA] = SharingConsentRead(enabled = false, version = "2026-11-v03")
            engine.refresh(TrackingEngine.Reason.SHARING_CHANGED)
            store.commits.clear()
            env.now += 3 * minute
            val reads = position.reads

            assertEquals(PullOutcome.NOT_SHARING, engine.refreshFromFeed())

            assertEquals(reads, position.reads)
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun afterTheSessionEnded_nothingHappens() =
        runTest {
            atHome()
            engine.stop()
            val reads = position.reads
            env.now += 3 * minute

            assertEquals(PullOutcome.UNAVAILABLE, engine.refreshFromFeed())

            assertEquals(reads, position.reads)
            assertNull("nothing is rebuilt", local.state)
        }

    @Test
    fun nobodySignedIn_nothingHappens() =
        runTest {
            atHome()
            env.uid = null
            assertEquals(PullOutcome.UNAVAILABLE, engine.refreshFromFeed())
        }

    @Test
    fun aStatusThatCannotLeaveNow_staysWaiting_theNetworkWakeTakesItOver() =
        runTest {
            openedFarAway()
            env.now += 3 * minute
            position.lat = lat
            // The write fails as a lost network does: the presence is kept (a process killed meanwhile does not lose it) and written at the next wake-up.
            store.alwaysFailing["miffs/miffA/status/uid1"] =
                FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)

            assertEquals(PullOutcome.DONE, engine.refreshFromFeed())

            assertEquals(
                "in_trusted",
                local.state!!
                    .miffs
                    .getValue(miffA)
                    .derived()!!
                    .presence.wire,
            )
            assertNotNull(
                "the status waits in the local copy",
                local.state!!
                    .miffs
                    .getValue(miffA)
                    .pending,
            )
            assertTrue("and the return of the network is watched", wake.watching)
        }
}
