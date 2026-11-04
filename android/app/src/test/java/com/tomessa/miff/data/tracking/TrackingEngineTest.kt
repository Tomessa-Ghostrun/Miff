package com.tomessa.miff.data.tracking

import com.google.firebase.Timestamp
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
import com.tomessa.miff.testsupport.FakeGeofences
import com.tomessa.miff.testsupport.FakeLocal
import com.tomessa.miff.testsupport.FakeMovement
import com.tomessa.miff.testsupport.FakeNetworkWake
import com.tomessa.miff.testsupport.FakePosition
import com.tomessa.miff.testsupport.FakeTrackingEnv
import com.tomessa.miff.testsupport.FakeTrackingStore
import com.tomessa.miff.testsupport.RecordingJournal
import com.tomessa.miff.testsupport.T0
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
 * The engine of the detection with doubles for the phone, the system and Firestore (criteria AUT7, POS1 to POS8, DET1 to DET4, STA1 to STA4, VIE1 to
 * VIE3, HOR1). No real position, no network.
 */
class TrackingEngineTest {
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
    private val journal = RecordingJournal()
    private val engine =
        TrackingEngine(env, store, local, position, geofences, movement, wake, journal, timeoutMillis = 5_000)

    // The person is at (48.8566, 2.3522); `home` is under their feet, 100 m wide.
    private val lat = 48.8566
    private val lng = 2.3522
    private val home = PlaceFact("home", lat, lng, 100, PlaceType.TRUSTED)
    private val danger = PlaceFact("danger", lat + 0.01, lng, 100, PlaceType.RISK)

    private fun v3() = SharingConsentRead(enabled = true, version = "2026-11-v03")

    private fun share(
        vararg miffs: String,
        placesOf: (String) -> List<PlaceFact> = { listOf(home) },
    ) {
        store.memberships = miffs.map { MembershipRef("k$it", it) }
        miffs.forEach {
            store.consents[it] = v3()
            store.places[it] = placesOf(it)
        }
    }

    private fun refused() = FirebaseFirestoreException("x", FirebaseFirestoreException.Code.PERMISSION_DENIED)

    private fun statusWrites() = store.writesTo("/status/")

    private fun positionWrites() = store.writesTo("/positions/")

    private fun places(
        prefix: String,
        count: Int,
        farMetres: Double,
    ) = (1..count).map {
        PlaceFact("$prefix$it", lat + (farMetres + it * 1_000.0) / 111_195.0, lng, 100, PlaceType.TRUSTED)
    }

    // --- Nothing before the agreement and "Toujours" (AUT7) ------------------------------------------------------------------------------------

    @Test
    fun withTheOldConsentText_nothingIsReadWatchedOrWritten() =
        runTest {
            store.memberships = listOf(MembershipRef("k", miffA))
            store.consents[miffA] = SharingConsentRead(enabled = true, version = "2026-10-v2")
            store.places[miffA] = listOf(home)
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(0, position.reads)
            assertTrue(geofences.armed.isEmpty())
            assertFalse(movement.started)
            assertTrue(store.commits.isEmpty())
            // And no place of that Miff is even kept.
            assertTrue(local.state!!.miffs.isEmpty())
        }

    @Test
    fun whenTheSharingIsStopped_nothingIsReadOrWatched() =
        runTest {
            store.memberships = listOf(MembershipRef("k", miffA))
            store.consents[miffA] = SharingConsentRead(enabled = false, version = "2026-11-v03")
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(0, position.reads)
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun withoutAlways_noPositionIsRead_nothingIsArmed_noPositionIsWritten() =
        runTest {
            share(miffA)
            env.snapshot = whileInUseOnly()
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(0, position.reads)
            assertTrue(geofences.armed.isEmpty())
            assertFalse(movement.started)
            assertTrue(positionWrites().isEmpty())
            assertNull(local.state!!.fix())
        }

    @Test
    fun withTheMemberOfNoMiff_nothingIsKept_andNoPositionIsRead() =
        runTest {
            store.memberships = emptyList()
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(0, position.reads)
            assertTrue(local.state!!.miffs.isEmpty())
        }

    // --- Position and selection (POS1 to POS4, POS8) ---------------------------------------------------------------------------------------

    @Test
    fun opening_with22PlacesOnTwoMiffs_arms20_andWritesTheLastPositionOncePerMiff_alone() =
        runTest {
            store.memberships = listOf(MembershipRef("ka", miffA), MembershipRef("kb", miffB))
            store.consents[miffA] = v3()
            store.consents[miffB] = v3()
            // 11 places each, from 1 km away; none under the person's feet.
            store.places[miffA] = places("a", 11, 500.0)
            store.places[miffB] = places("b", 11, 700.0)
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(20, geofences.armed.size)
            assertTrue(movement.started)
            // One position write per Miff, each alone in its lot, on the position document and nowhere else.
            assertEquals(2, store.commits.size)
            store.commits.forEach { lot ->
                assertEquals(1, lot.size)
                assertTrue(lot.single().path.matches(Regex("miffs/miff[AB]/positions/uid1")))
            }
            // DET3: outside every zone, no status is invented ("out" or anything else).
            assertTrue(statusWrites().isEmpty())
            // The nearest were chosen: the farthest two (by the edge) are left out.
            val farthest =
                (store.places[miffA].orEmpty() + store.places[miffB].orEmpty()).map { it.id }.toSet() - geofences.armed
            assertEquals(2, farthest.size)
        }

    @Test
    fun theSelectionUsesTheLocalList_noRequestToTheServerWhenItIsRecomputed() =
        runTest {
            share(miffA, miffB)
            engine.refresh(TrackingEngine.Reason.OPENED)
            val reads = store.readCalls
            assertTrue(reads > 0)
            // A move of more than 250 m, an entry, the background: the 20 nearest are recomputed on the phone, not asked of the server (POS4).
            env.now += 30 * minute
            engine.refresh(
                TrackingEngine.Reason.MOVED,
                com.tomessa.miff.domain.tracking
                    .Fix(lat + 0.004, lng, env.now),
            )
            env.now += 20 * minute
            engine.refresh(TrackingEngine.Reason.BACKGROUNDED)
            assertEquals(reads, store.readCalls)
        }

    @Test
    fun theServerIsReadAgain_whenTheLocalListIsOlderThanOneHour() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            val reads = store.readCalls
            env.now += 61 * minute
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertTrue(store.readCalls > reads)
        }

    @Test
    fun aMoveReport_usesTheFixOfTheProvider_noOtherPositionIsAsked() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            val reads = position.reads
            env.now += 20 * minute
            engine.refresh(
                TrackingEngine.Reason.MOVED,
                com.tomessa.miff.domain.tracking
                    .Fix(lat + 0.01, lng, env.now),
            )
            assertEquals(reads, position.reads)
        }

    @Test
    fun theGuardOfFrequency_skipsASecondPositionWithinFifteenMinutes_ifNotMoved_andWritesAfterAMoveOf250m() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(1, positionWrites().size)
            env.now += 5 * minute
            engine.refresh(TrackingEngine.Reason.BACKGROUNDED)
            assertEquals(1, positionWrites().size)
            // Moved by about 330 m, 6 minutes after the last write: written.
            env.now += minute
            engine.refresh(
                TrackingEngine.Reason.MOVED,
                com.tomessa.miff.domain.tracking
                    .Fix(lat + 0.003, lng, env.now),
            )
            assertEquals(2, positionWrites().size)
            // Not moved, 16 minutes later: written (the 15 minutes passed).
            env.now += 16 * minute
            engine.refresh(
                TrackingEngine.Reason.MOVED,
                com.tomessa.miff.domain.tracking
                    .Fix(lat + 0.003, lng, env.now),
            )
            assertEquals(3, positionWrites().size)
        }

    @Test
    fun theBreakerOfThePositions_stopsTheWrites() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            val day = WriteBreaker.dayKey(env.now)
            local.state =
                local.state!!.copyWith(breaker = BreakerState(day, 0, TrackingLimits.MAX_POSITION_WRITES_PER_DAY))
            val before = positionWrites().size
            env.now += 20 * minute
            engine.refresh(TrackingEngine.Reason.BACKGROUNDED)
            assertEquals(before, positionWrites().size)
            assertTrue(JournalKind.BREAKER_POSITION in journal.kinds)
        }

    @Test
    fun aPositionIsOnlyWrittenToTheMiffsWhereTheConsentIsV3() =
        runTest {
            store.memberships = listOf(MembershipRef("ka", miffA), MembershipRef("kb", miffB))
            store.consents[miffA] = v3()
            store.consents[miffB] = SharingConsentRead(true, "2026-10-v2")
            store.places[miffA] = listOf(home)
            store.places[miffB] = listOf(home)
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(listOf("miffs/miffA/positions/uid1"), positionWrites().map { it.path })
        }

    @Test
    fun anUnavailablePosition_isNoCrash_nothingIsWritten() =
        runTest {
            share(miffA)
            position.available = false
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertTrue(positionWrites().isEmpty())
            assertTrue(JournalKind.POSITION_UNAVAILABLE in journal.kinds)
        }

    // --- Events to status (DET2, DET3, STA1, STA3) ------------------------------------------------------------------------------------------

    @Test
    fun anArrival_writesInTrusted_withThePlace_theRealTime_andTheBattery_perMiff() =
        runTest {
            share(miffA, miffB)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            env.now += 10 * minute
            val eventAt = env.now - 2 * minute
            engine.onTransition(TransitionKind.ENTER, "home", eventAt)
            val statuses = statusWrites()
            assertEquals(setOf("miffs/miffA/status/uid1", "miffs/miffB/status/uid1"), statuses.map { it.path }.toSet())
            statuses.forEach {
                assertEquals("in_trusted", it.data["presence"])
                assertEquals("home", it.data["placeId"])
                assertEquals(Timestamp(Date(eventAt)), it.data["presenceAt"])
                assertEquals(Timestamp(Date(eventAt)), it.data["lastUpdateAt"])
                assertEquals(80, it.data["batteryPct"])
                assertEquals("background", it.data["locationAccess"])
                assertNull(it.data["escalationDueAt"])
            }
            // An independent write per Miff, each alone in its lot, never mixed with a position or a place.
            assertTrue(store.commits.filter { "/status/" in it.single().path }.all { it.size == 1 })
        }

    @Test
    fun aDeparture_writesOut_withThePlaceLeft_andTheRealTimeOfTheEvent() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            env.now += 5 * minute
            engine.onTransition(TransitionKind.ENTER, "home", env.now)
            store.commits.clear()
            env.now += 3 * hour
            val left = env.now - minute
            engine.onTransition(TransitionKind.EXIT, "home", left)
            val write = statusWrites().single()
            assertEquals("out", write.data["presence"])
            assertEquals("home", write.data["placeId"])
            assertEquals(Timestamp(Date(left)), write.data["presenceAt"])
            // lastUpdateAt, updateText and hasPhoto are not in an update that is not an arrival: they stay.
            assertFalse("lastUpdateAt" in write.data || "updateText" in write.data || "hasPhoto" in write.data)
        }

    @Test
    fun theTimeOfAnEventInTheFuture_isCappedAtNow() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            engine.onTransition(TransitionKind.ENTER, "home", env.now + 3 * hour)
            assertEquals(Timestamp(Date(env.now)), statusWrites().single().data["presenceAt"])
        }

    @Test
    fun theDangerZone_whenInNoTrustedZone_writesInRisk() =
        runTest {
            share(miffA) { listOf(home, danger) }
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            engine.onTransition(TransitionKind.ENTER, "danger", env.now)
            val write = statusWrites().single()
            assertEquals("in_risk", write.data["presence"])
            assertEquals("danger", write.data["placeId"])
            assertFalse("lastUpdateAt" in write.data)
        }

    @Test
    fun theTrustedZoneWins_enteringTheDangerZoneWhileInTheTrustedOne_writesNothing() =
        runTest {
            share(miffA) { listOf(home, danger) }
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, "home", env.now)
            store.commits.clear()
            engine.onTransition(TransitionKind.ENTER, "danger", env.now + 1000)
            assertTrue(statusWrites().isEmpty())
        }

    @Test
    fun anEventOfAPlaceOfOneMiffOnly_doesNotWriteInTheOtherMiff() =
        runTest {
            store.memberships = listOf(MembershipRef("ka", miffA), MembershipRef("kb", miffB))
            store.consents[miffA] = v3()
            store.consents[miffB] = v3()
            store.places[miffA] = listOf(home)
            store.places[miffB] = listOf(PlaceFact("other", lat + 0.05, lng, 100, PlaceType.TRUSTED))
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            engine.onTransition(TransitionKind.ENTER, "home", env.now)
            assertEquals(listOf("miffs/miffA/status/uid1"), statusWrites().map { it.path })
        }

    @Test
    fun oneMiffRefusingTheWrite_neverStopsTheOtherOne() =
        runTest {
            share(miffA, miffB)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            store.alwaysFailing["miffs/miffA/status/uid1"] = refused()
            val readsBefore = store.readCalls
            engine.onTransition(TransitionKind.ENTER, "home", env.now)
            assertEquals(listOf("miffs/miffB/status/uid1"), statusWrites().map { it.path })
            assertTrue(JournalKind.STATUS_REFUSED in journal.kinds)
            // The refused one is not kept waiting (no loop), and the Miff is looked at again (the consent is read from the server again).
            assertNull(
                local.state!!
                    .miffs
                    .getValue(miffA)
                    .pending,
            )
            assertTrue(store.readCalls > readsBefore)
        }

    @Test
    fun aRefusedLastUpdate_isRetriedOnceWithTheLastUpdateMovedToTheEventTime() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, "home", env.now)
            store.commits.clear()
            // The status says "last update 30 hours ago": the rules refuse it; the second try moves it to the time of the departure.
            store.failuresByPath["miffs/miffA/status/uid1"] = ArrayDeque(listOf(refused()))
            env.now += 30 * hour
            engine.onTransition(TransitionKind.EXIT, "home", env.now - minute)
            val write = statusWrites().single()
            assertEquals(Timestamp(Date(env.now - minute)), write.data["lastUpdateAt"])
        }

    // --- Offline (HOR1) and delay (STA3) ----------------------------------------------------------------------------------------------------

    @Test
    fun offline_theStatusWaits_andIsWrittenAtTheReturnOfTheNetwork_withItsRealTime() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, "home", env.now)
            store.commits.clear()
            env.online = false
            env.now += 2 * hour
            val left = env.now
            engine.onTransition(TransitionKind.EXIT, "home", left)
            assertTrue(statusWrites().isEmpty())
            assertTrue(wake.watching)
            assertNotNull(
                local.state!!
                    .miffs
                    .getValue(miffA)
                    .pending,
            )
            // 40 minutes later the network is back: the departure goes out with the time it really happened.
            env.online = true
            env.now += 40 * minute
            engine.onNetworkBack()
            val write = statusWrites().single()
            assertEquals("out", write.data["presence"])
            assertEquals(Timestamp(Date(left)), write.data["presenceAt"])
            assertFalse(wake.watching)
            assertNull(
                local.state!!
                    .miffs
                    .getValue(miffA)
                    .pending,
            )
        }

    @Test
    fun onlyTheLastStateIsKeptWaiting_aDepartureThenAnArrivalOfflineWritesTheArrival() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, "home", env.now)
            store.commits.clear()
            env.online = false
            env.now += hour
            engine.onTransition(TransitionKind.EXIT, "home", env.now)
            env.now += 10 * minute
            val back = env.now
            engine.onTransition(TransitionKind.ENTER, "home", back)
            env.online = true
            engine.onNetworkBack()
            val write = statusWrites().single()
            assertEquals("in_trusted", write.data["presence"])
            assertEquals(Timestamp(Date(back)), write.data["presenceAt"])
        }

    @Test
    fun anEventDetectedMoreThan24HoursAgo_isGivenUp_withoutLoop() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, "home", env.now)
            store.commits.clear()
            env.online = false
            engine.onTransition(TransitionKind.EXIT, "home", env.now)
            env.online = true
            env.now += 25 * hour
            engine.onNetworkBack()
            assertTrue(statusWrites().isEmpty())
            assertNull(
                local.state!!
                    .miffs
                    .getValue(miffA)
                    .pending,
            )
            assertFalse(wake.watching)
            assertTrue(JournalKind.STATUS_GIVEN_UP in journal.kinds)
        }

    @Test
    fun theBreakerOfTheStatuses_keepsTheLastStateWaiting_andItGoesTheNextDay() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, "home", env.now)
            store.commits.clear()
            local.state =
                local.state!!.copyWith(
                    breaker = BreakerState(WriteBreaker.dayKey(env.now), TrackingLimits.MAX_STATUS_WRITES_PER_DAY, 0),
                )
            engine.onTransition(TransitionKind.EXIT, "home", env.now)
            assertTrue(statusWrites().isEmpty())
            assertTrue(JournalKind.BREAKER_STATUS in journal.kinds)
            assertNotNull(
                local.state!!
                    .miffs
                    .getValue(miffA)
                    .pending,
            )
            // The next day, still recent enough (the event is 5 hours old): written.
            env.now += 5 * hour
            local.state =
                local.state!!.copyWith(
                    breaker = BreakerState("1999-01-01", TrackingLimits.MAX_STATUS_WRITES_PER_DAY, 0),
                )
            engine.onNetworkBack()
            assertEquals(1, statusWrites().size)
        }

    // --- Place deleted or changed, restart (6.6) ---------------------------------------------------------------------------------------------

    @Test
    fun aPlaceDeletedWhileInIt_makesTheStatusUnknown() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, "home", env.now)
            store.commits.clear()
            store.places[miffA] = emptyList()
            env.now += 2 * hour
            engine.refresh(TrackingEngine.Reason.OPENED)
            val write = statusWrites().single()
            assertEquals("unknown", write.data["presence"])
            assertNull(write.data["placeId"])
            assertFalse("lastUpdateAt" in write.data)
        }

    @Test
    fun aPlaceMovedWhileInIt_makesTheStatusUnknown_andTheZoneIsArmedAgain() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, "home", env.now)
            store.commits.clear()
            val updatesBefore = geofences.updates
            store.places[miffA] = listOf(PlaceFact("home", lat + 0.0001, lng, 150, PlaceType.TRUSTED))
            env.now += 2 * hour
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals("unknown", statusWrites().single().data["presence"])
            assertTrue(geofences.updates > updatesBefore)
            assertTrue("home" in geofences.armed)
        }

    @Test
    fun afterARestart_stillInTheZone_theArrivalIsNotWrittenTwice_andTheZonesAreArmedAgain() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, "home", env.now)
            store.commits.clear()
            geofences.armed.clear()
            env.now += hour
            engine.refresh(TrackingEngine.Reason.REARM)
            assertTrue("home" in geofences.armed)
            assertTrue(statusWrites().isEmpty())
        }

    @Test
    fun afterARestart_awayFromTheZone_theStatusBecomesUnknown_neverAnInventedOut() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, "home", env.now)
            store.commits.clear()
            geofences.armed.clear()
            env.now += hour
            position.lat = lat + 0.05
            engine.refresh(TrackingEngine.Reason.REARM)
            assertEquals("unknown", statusWrites().single().data["presence"])
        }

    // --- Clean-up (f, VIE3, DET4) ------------------------------------------------------------------------------------------------------------

    @Test
    fun stopErasesEverything_zones_updates_localCopy_journal_waitingStatus() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertTrue(geofences.armed.isNotEmpty())
            assertNotNull(local.state)
            engine.stop()
            assertTrue(geofences.armed.isEmpty())
            assertFalse(movement.started)
            assertNull(local.state)
            assertTrue(journal.kinds.isEmpty())
            assertFalse(wake.watching)
        }

    @Test
    fun nobodySignedIn_theOrphanZonesAreRemoved_andTheCopyErased() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            env.uid = null
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertTrue(geofences.armed.isEmpty())
            assertNull(local.state)
        }

    @Test
    fun theCopyOfAnotherAccount_isNeverReused() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            env.uid = "uid2"
            store.memberships = emptyList()
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals("uid2", local.state!!.uid)
            assertTrue(local.state!!.miffs.isEmpty())
        }

    @Test
    fun whenTheSharingStopsInOneMiff_itsOwnZonesAndWaitingStatusAreForgotten_theSharedZoneStays() =
        runTest {
            store.memberships = listOf(MembershipRef("ka", miffA), MembershipRef("kb", miffB))
            store.consents[miffA] = v3()
            store.consents[miffB] = v3()
            store.places[miffA] = listOf(home)
            store.places[miffB] = listOf(home, PlaceFact("onlyB", lat + 0.02, lng, 100, PlaceType.TRUSTED))
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(setOf("home", "onlyB"), geofences.armed)
            store.consents[miffB] = SharingConsentRead(enabled = false, version = "2026-11-v03")
            store.commits.clear()
            engine.refresh(TrackingEngine.Reason.SHARING_CHANGED)
            assertEquals(setOf("home"), geofences.armed)
            assertEquals(setOf(miffA), local.state!!.miffs.keys)
            engine.onTransition(TransitionKind.ENTER, "home", env.now)
            assertEquals(listOf("miffs/miffA/status/uid1"), statusWrites().map { it.path })
        }

    @Test
    fun whenTheLastMiffIsLeft_everythingStopsAndIsErased() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.memberships = emptyList()
            engine.refresh(TrackingEngine.Reason.SHARING_CHANGED)
            assertTrue(geofences.armed.isEmpty())
            assertFalse(movement.started)
            assertTrue(local.state!!.miffs.isEmpty())
            assertNull(local.state!!.fix())
        }

    @Test
    fun theAccessLost_stopsTheWatching_erasesThePosition_andIsWrittenOnce() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            env.snapshot = whileInUseOnly()
            val reads = position.reads
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertTrue(geofences.armed.isEmpty())
            assertFalse(movement.started)
            assertNull(local.state!!.fix())
            assertEquals(reads, position.reads)
            // The Miff is told, once, that the access changed (never the presence).
            val write = store.commits.single().single()
            assertEquals("miffs/miffA/status/uid1", write.path)
            assertEquals("foreground", write.data["locationAccess"])
            assertFalse("presence" in write.data)
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(1, store.commits.size)
        }

    @Test
    fun aTransitionAfterTheConsentIsLost_isIgnored_andTheZonesAreRemoved() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            env.snapshot = whileInUseOnly()
            engine.onTransition(TransitionKind.ENTER, "home", env.now)
            assertTrue(statusWrites().isEmpty())
            assertTrue(geofences.armed.isEmpty())
        }

    // --- What leaves the phone (VIE1, POS5, POS6, STA2) ------------------------------------------------------------------------------------

    @Test
    fun everyLotIsASingleDocument_aStatusNeverCarriesACoordinate_aPositionIsAlone() =
        runTest {
            share(miffA, miffB)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, "home", env.now)
            env.now += 2 * hour
            engine.onTransition(TransitionKind.EXIT, "home", env.now)
            env.now += 20 * minute
            engine.refresh(TrackingEngine.Reason.BACKGROUNDED)
            assertTrue(store.commits.isNotEmpty())
            store.commits.forEach { lot ->
                assertEquals("a lot of the detection is one document", 1, lot.size)
                val write = lot.single()
                when {
                    "/status/" in write.path -> {
                        assertFalse("lat" in write.data || "lng" in write.data)
                        assertFalse(write.data.values.any { it is Double })
                    }

                    "/positions/" in write.path -> {
                        assertEquals(setOf("lat", "lng", "measuredAt"), write.data.keys)
                    }

                    else -> {
                        throw AssertionError("the detection writes nothing but a status and a position: ${write.path}")
                    }
                }
            }
        }

    @Test
    fun theJournal_holdsNoCoordinate_onlyKindsAndIdentifiers() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, "home", env.now)
            val text = journal.lines().joinToString { "${it.kind} ${it.placeId} ${it.batteryPct}" }
            assertFalse(Regex("""\d+\.\d{3,}""").containsMatchIn(text))
            assertTrue(journal.lines().isNotEmpty())
        }
}
