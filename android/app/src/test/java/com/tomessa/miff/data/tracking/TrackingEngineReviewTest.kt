package com.tomessa.miff.data.tracking

import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.data.miff.SharingConsentRead
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.tracking.BreakerState
import com.tomessa.miff.domain.tracking.Fix
import com.tomessa.miff.domain.tracking.JournalKind
import com.tomessa.miff.domain.tracking.PlaceFact
import com.tomessa.miff.domain.tracking.TrackingLimits
import com.tomessa.miff.domain.tracking.TransitionKind
import com.tomessa.miff.domain.tracking.WriteBreaker
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

/** The cases of the independent review of the PR #68 (B1, B2, I1 to I5 and the minor points): each one fails without its fix. */
class TrackingEngineReviewTest {
    private val miffA = "miffA"
    private val miffB = "miffB"
    private val miffC = "miffC"
    private val minute = 60_000L
    private val hour = 60 * minute

    private val env = FakeTrackingEnv()
    private val store = FakeTrackingStore()
    private val local = FakeLocal()
    private val position = FakePosition(env)
    private val geofences = FakeGeofences()
    private val movement = FakeMovement()
    private val wake = FakeNetworkWake()
    private val journal = RecordingJournal()
    private var session = 1
    private val engine =
        TrackingEngine(
            env,
            store,
            local,
            position,
            geofences,
            movement,
            wake,
            journal,
            timeoutMillis = 5_000,
            generation = { session },
        )

    private val lat = 48.8566
    private val lng = 2.3522
    private val home = PlaceFact("home", lat, lng, 100, PlaceType.TRUSTED)

    private fun share(
        vararg miffs: String,
        placesOf: (String) -> List<PlaceFact> = { listOf(home) },
    ) {
        store.memberships = miffs.map { MembershipRef("k$it", it) }
        miffs.forEach {
            store.consents[it] = SharingConsentRead(true, "2026-11-v03")
            store.places[it] = placesOf(it)
        }
    }

    private fun statusWrites() = store.writesTo("/status/")

    private fun refused() = FirebaseFirestoreException("x", FirebaseFirestoreException.Code.PERMISSION_DENIED)

    // --- B1 : the fixed identifier p1 of two creators ---------------------------------------------------------------------------------------

    @Test
    fun twoDifferentPlacesCalledP1InTwoMiffs_anArrivalAtOneNeverWritesInTheOther() =
        runTest {
            val atA = PlaceFact("p1", lat, lng, 100, PlaceType.TRUSTED)
            val atB = PlaceFact("p1", lat + 0.05, lng, 100, PlaceType.TRUSTED)
            share(miffA, miffB) { if (it == miffA) listOf(atA) else listOf(atB) }
            engine.refresh(TrackingEngine.Reason.OPENED)
            // Two zones for the system, one per place, not one.
            assertEquals(setOf(atA.key, atB.key), geofences.armed)
            store.commits.clear()
            // The person is at A's: the system says they entered A's zone.
            engine.onTransition(TransitionKind.ENTER, atA.key, env.now)
            assertEquals(listOf("miffs/miffA/status/uid1"), statusWrites().map { it.path })
            // The status carries the identifier of the place in THAT Miff.
            assertEquals("p1", statusWrites().single().data["placeId"])
            // And leaving it writes nothing in B either.
            store.commits.clear()
            engine.onTransition(TransitionKind.EXIT, atA.key, env.now + minute)
            assertEquals(listOf("miffs/miffA/status/uid1"), statusWrites().map { it.path })
            assertNull(
                local.state!!
                    .miffs
                    .getValue(miffB)
                    .derived(),
            )
        }

    @Test
    fun theSamePlaceInTwoMiffs_isOneZone_andWritesInBoth() =
        runTest {
            share(miffA, miffB)
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(setOf(home.key), geofences.armed)
            store.commits.clear()
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertEquals(2, statusWrites().size)
        }

    @Test
    fun theJournal_neverHoldsAZoneKey_itHoldsCoordinates() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            journal.lines().forEach {
                assertFalse("${it.placeId}", (it.placeId ?: "").contains("|"))
            }
            assertTrue(journal.lines().any { it.placeId == "home" })
        }

    // --- B2 : what the system lost is armed again ---------------------------------------------------------------------------------------------

    @Test
    fun afterAForcedStopOrACleaning_theZonesAndTheReportsAreRegisteredAgainAtTheNextOpening() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertTrue(home.key in geofences.armed)
            // The system forgot everything; the phone's own list still says "armed".
            geofences.armed.clear()
            movement.started = false
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertTrue(home.key in geofences.armed)
            assertTrue(movement.started)
        }

    @Test
    fun afterTheLocationWasSwitchedOffAndOn_orGeofenceNotAvailable_arearm_putsThemBack() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            geofences.armed.clear()
            movement.started = false
            // What the receiver does with an error of the geofencing (GEOFENCE_NOT_AVAILABLE...).
            engine.refresh(TrackingEngine.Reason.REARM)
            assertTrue(home.key in geofences.armed)
            assertTrue(movement.started)
        }

    @Test
    fun aMoveOrTheBackground_doNotSendTheWholeSetAgain_onlyTheDifference() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            val updates = geofences.updates
            env.now += 20 * minute
            engine.refresh(TrackingEngine.Reason.BACKGROUNDED)
            engine.refresh(TrackingEngine.Reason.MOVED, Fix(lat, lng, env.now))
            // Nothing changed: no call (an arrival must not be fired again for the zones already armed).
            assertEquals(updates, geofences.updates)
        }

    @Test
    fun theReceiverTreatsAnErrorOfTheGeofencingAsARearm_andOnlyValidKeys() {
        val source = java.io.File("src/main/java/com/tomessa/miff/data/tracking/TrackingReceiver.kt").readText()
        assertTrue(source.contains("event.hasError()"))
        assertTrue(source.contains("TrackingEngine.Reason.REARM"))
        assertTrue(source.contains("ZoneKey::isValid"))
        assertTrue(source.contains("CoroutineExceptionHandler"))
    }

    // --- I1 : arm before waiting for the position ---------------------------------------------------------------------------------------------

    @Test
    fun aSlowPosition_doesNotCostTheArming_theZonesAreArmedBeforeTheWait_andTheWaitEnds() =
        runTest {
            val many =
                (1..22).map {
                    PlaceFact(
                        "a$it",
                        lat + (500.0 + it * 1_000.0) / 111_195.0,
                        lng,
                        100,
                        PlaceType.TRUSTED,
                    )
                }
            share(miffA) { many }
            position.hangs = true
            engine.refresh(TrackingEngine.Reason.REARM)
            // 20 zones armed without a position, the movement reports requested, and no crash.
            assertEquals(20, geofences.armed.size)
            assertTrue(movement.started)
            assertTrue(JournalKind.POSITION_UNAVAILABLE in journal.kinds)
        }

    @Test
    fun withMoreThan20Zones_theChoiceIsMadeAgainWithTheFreshPosition() =
        runTest {
            val many =
                (1..22).map {
                    PlaceFact(
                        "a$it",
                        lat + (500.0 + it * 1_000.0) / 111_195.0,
                        lng,
                        100,
                        PlaceType.TRUSTED,
                    )
                }
            share(miffA) { many }
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(20, geofences.armed.size)
            assertFalse(many.last().key in geofences.armed)
            assertTrue(many.first().key in geofences.armed)
        }

    // --- I2 : an old position is never used ---------------------------------------------------------------------------------------------------

    @Test
    fun anOldPosition_isNeverUsed_noPositionWritten_noFalseArrivalAtARearm() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            store.commits.clear()
            env.now += 2 * hour
            // The only position the phone has is 30 minutes old and far away from home: not used.
            position.lat = lat + 0.05
            position.ageMillis = 30 * minute
            engine.refresh(TrackingEngine.Reason.REARM)
            assertTrue(store.writesTo("/positions/").isEmpty())
            assertTrue(statusWrites().isEmpty())
        }

    @Test
    fun anOldPositionKeptOnThePhone_isNotUsedForAZoneEntryEither() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            env.now += 3 * hour
            position.available = false
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertTrue(store.writesTo("/positions/").isEmpty())
        }

    @Test
    fun aSuppliedFixThatIsTooOld_isRefused() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            env.now += hour
            position.available = false
            engine.refresh(TrackingEngine.Reason.MOVED, Fix(lat + 0.05, lng, env.now - 20 * minute))
            assertTrue(store.writesTo("/positions/").isEmpty())
        }

    // --- I3 : the return of the network checks again ------------------------------------------------------------------------------------------

    private suspend fun waitingDeparture() {
        share(miffA)
        engine.refresh(TrackingEngine.Reason.OPENED)
        engine.onTransition(TransitionKind.ENTER, home.key, env.now)
        store.commits.clear()
        env.online = false
        env.now += hour
        engine.onTransition(TransitionKind.EXIT, home.key, env.now)
        assertTrue(wake.watching)
        env.online = true
        env.now += 10 * minute
    }

    @Test
    fun networkBack_afterAlwaysWasWithdrawn_writesNothing_andKeepsNothing() =
        runTest {
            waitingDeparture()
            env.snapshot = whileInUseOnly()
            engine.onNetworkBack()
            assertTrue(statusWrites().isEmpty())
            assertFalse(wake.watching)
            assertNull(
                local.state!!
                    .miffs
                    .getValue(miffA)
                    .pending,
            )
            assertNull(local.state!!.fix())
        }

    @Test
    fun networkBack_afterTheSharingWasStopped_writesNothing() =
        runTest {
            waitingDeparture()
            store.consents[miffA] = SharingConsentRead(enabled = false, version = "2026-11-v03")
            engine.onNetworkBack()
            assertTrue(statusWrites().isEmpty())
            assertFalse(wake.watching)
            assertTrue(local.state!!.miffs.isEmpty())
        }

    @Test
    fun networkBack_withTheConsentOfAnOlderText_writesNothing() =
        runTest {
            waitingDeparture()
            store.consents[miffA] = SharingConsentRead(enabled = true, version = "2026-10-v2")
            engine.onNetworkBack()
            assertTrue(statusWrites().isEmpty())
        }

    @Test
    fun networkBack_whenTheLookAtTheServerFails_keepsTheStatusWaiting_nothingIsWrittenOnAnAssumption() =
        runTest {
            waitingDeparture()
            store.readFailure = FirebaseFirestoreException("x", FirebaseFirestoreException.Code.UNAVAILABLE)
            engine.onNetworkBack()
            assertTrue(statusWrites().isEmpty())
            assertNotNull(
                local.state!!
                    .miffs
                    .getValue(miffA)
                    .pending,
            )
            assertTrue(wake.watching)
            // Later, the look works: written.
            store.readFailure = null
            engine.onNetworkBack()
            assertEquals(1, statusWrites().size)
        }

    @Test
    fun aTransitionAfterTheAccessIsLost_erasesThePositionAndTheWaitingStatus() =
        runTest {
            waitingDeparture()
            env.snapshot = whileInUseOnly()
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            val miff = local.state!!.miffs.getValue(miffA)
            assertNull(miff.pending)
            assertNull(miff.written())
            assertNull(local.state!!.fix())
            assertTrue(geofences.armed.isEmpty())
        }

    // --- I4 : stop is sticky --------------------------------------------------------------------------------------------------------------------

    @Test
    fun afterTheSessionStopped_noRefreshRebuildsTheZonesOrTheCopy_untilTheSessionChanges() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.stop()
            // The account is still signed in for a moment (the sign-out comes after the teardown): nothing is rebuilt.
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.refresh(TrackingEngine.Reason.MOVED, Fix(lat, lng, env.now))
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            engine.onNetworkBack()
            assertTrue(geofences.armed.isEmpty())
            assertFalse(movement.started)
            assertNull(local.state)
            assertTrue(store.commits.isEmpty() || store.commits.none { "/status/" in it.single().path })
            // A new session (the generation went up): the engine works again.
            session = 2
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertTrue(home.key in geofences.armed)
        }

    // --- I5 : the breakers serve the Miffs in turn ---------------------------------------------------------------------------------------------

    @Test
    fun theStatusBreaker_doesNotSpareTheSameMiffsForGood_theyAreServedInTurn() =
        runTest {
            share(miffA, miffB, miffC)
            engine.refresh(TrackingEngine.Reason.OPENED)
            val served = mutableSetOf<String>()
            repeat(8) { n ->
                // Only ONE more write is allowed today.
                local.state =
                    local.state!!.copyWith(
                        breaker =
                            BreakerState(
                                WriteBreaker.dayKey(env.now),
                                TrackingLimits.MAX_STATUS_WRITES_PER_DAY - 1,
                                0,
                            ),
                    )
                store.commits.clear()
                env.now += minute
                engine.onTransition(if (n % 2 == 0) TransitionKind.ENTER else TransitionKind.EXIT, home.key, env.now)
                statusWrites().forEach { served += it.path }
            }
            assertEquals(setOf("miffs/miffA/status/uid1", "miffs/miffB/status/uid1", "miffs/miffC/status/uid1"), served)
        }

    // --- Minor: a refusal does not make every wake-up read the server (clock ahead) --------------------------------------------------------------

    @Test
    fun aClockThatIsAhead_refusedEveryTime_doesNotReadTheServerAtEveryWakeUp() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.alwaysFailing["miffs/miffA/status/uid1"] = refused()
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            val reads = store.readCalls
            repeat(3) {
                env.now += minute
                engine.onTransition(TransitionKind.EXIT, home.key, env.now)
                engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            }
            assertEquals("no look at the server within 5 minutes of a refusal", reads, store.readCalls)
        }
}
