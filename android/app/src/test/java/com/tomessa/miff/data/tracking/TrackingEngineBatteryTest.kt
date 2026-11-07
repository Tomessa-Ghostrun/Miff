package com.tomessa.miff.data.tracking

import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.data.miff.SharingConsentRead
import com.tomessa.miff.data.session.Clearance
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.tracking.BreakerState
import com.tomessa.miff.domain.tracking.JournalKind
import com.tomessa.miff.domain.tracking.PlaceFact
import com.tomessa.miff.domain.tracking.TrackingLimits
import com.tomessa.miff.domain.tracking.TransitionKind
import com.tomessa.miff.domain.tracking.WriteBreaker
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

/**
 * The battery refreshed when the app is opened and when it is left (PM decision, 2026-11-07), with doubles for the phone, the system and Firestore.
 * Triggers (opening, leaving, nothing else), limits (15 minutes, 5 points, 3 hours, dedicated breaker), and everything it must NEVER do: create a status, write
 * without the sharing under the consent of version 3, without the session, for an account being deleted or signed out, read a position, read the server.
 */
class TrackingEngineBatteryTest {
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
    private var underDeletion = false
    private var generation = 0
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
            generation = { generation },
            suspended = { underDeletion },
        )

    private val home = PlaceFact("home", 48.8566, 2.3522, 100, PlaceType.TRUSTED)

    private fun share(vararg miffs: String) {
        store.memberships = miffs.map { MembershipRef("k$it", it) }
        miffs.forEach {
            store.consents[it] = SharingConsentRead(enabled = true, version = "2026-11-v03")
            store.places[it] = listOf(home)
        }
    }

    private fun refused() = FirebaseFirestoreException("x", FirebaseFirestoreException.Code.PERMISSION_DENIED)

    /** The writes that carry the battery and nothing else. */
    private fun batteryWrites() =
        store.writesTo("/status/").filter { it.data.keys == setOf("batteryPct", "updatedAt", "deviceId") }

    private fun statusWrites() = store.writesTo("/status/")

    /** An opening followed by the look at the Miffs, as the app does; the commits of the opening itself are forgotten. */
    private suspend fun openedOnceWithABatteryWrite() {
        share(miffA)
        engine.lookAgain(TrackingEngine.Reason.OPENED)
        assertEquals(1, batteryWrites().size)
        store.commits.clear()
    }

    // --- Triggers ---------------------------------------------------------------------------------------------------------------------------

    @Test
    fun opening_writesTheBatteryOfEveryMiffWhereThePersonShares_oneLotPerMiff_onlyThreeKeys_neverACreation() =
        runTest {
            share(miffA, miffB)
            env.battery = 63
            engine.lookAgain(TrackingEngine.Reason.OPENED)
            val writes = batteryWrites()
            assertEquals(listOf("miffs/miffA/status/uid1", "miffs/miffB/status/uid1"), writes.map { it.path })
            writes.forEach {
                assertEquals("an UPDATE, which never creates a missing status", WriteKind.UPDATE, it.kind)
                assertEquals(63, it.data["batteryPct"])
                assertEquals(DEVICE_A, it.data["deviceId"])
                assertNotNull(it.data["updatedAt"])
            }
            // One lot per Miff, with that write alone.
            assertTrue(store.commits.filter { lot -> lot.any { "/status/" in it.path } }.all { it.size == 1 })
            // Every other field is left to the server: not one of them is in the write.
            val keys = writes.flatMap { it.data.keys }.toSet()
            listOf(
                "presence",
                "placeId",
                "presenceAt",
                "lastUpdateAt",
                "updateText",
                "hasPhoto",
                "locationAccess",
                "escalationDueAt",
            ).forEach { assertFalse("$it must be kept as the server holds it", it in keys) }
        }

    @Test
    fun leaving_writesTheBatteryBeforeAnythingElse() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            env.now += 20 * minute
            env.battery = 40
            engine.lookAgain(TrackingEngine.Reason.BACKGROUNDED)
            assertEquals(
                "miffs/miffA/status/uid1",
                store.commits
                    .first()
                    .single()
                    .path,
            )
            assertEquals(
                40,
                store.commits
                    .first()
                    .single()
                    .data["batteryPct"],
            )
        }

    @Test
    fun otherReasonsToLookAgain_doNotWriteTheBattery() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            env.now += hour
            env.battery = 10
            listOf(
                TrackingEngine.Reason.MOVED,
                TrackingEngine.Reason.ZONE_ENTERED,
                TrackingEngine.Reason.REARM,
                TrackingEngine.Reason.SHARING_CHANGED,
                TrackingEngine.Reason.NETWORK_BACK,
                TrackingEngine.Reason.REARM,
            ).forEach { engine.lookAgain(it) }
            assertTrue(batteryWrites().isEmpty())
        }

    @Test
    fun theRefreshItselfDoesNotRead_neitherThePositionNorTheServer() =
        runTest {
            openedOnceWithABatteryWrite()
            val reads = store.readCalls
            val positionReads = position.reads
            env.now += 30 * minute
            env.battery = 20
            engine.refreshBattery()
            assertEquals(1, batteryWrites().size)
            assertEquals("not one read of the server", reads, store.readCalls)
            assertEquals("not one position read", positionReads, position.reads)
            assertTrue("no position written", store.writesTo("/positions/").isEmpty())
        }

    @Test
    fun theJournalOnlyKnowsTheLevel() =
        runTest {
            share(miffA)
            env.battery = 71
            engine.lookAgain(TrackingEngine.Reason.OPENED)
            val line = journal.lines().single { it.kind == JournalKind.BATTERY_WRITTEN }
            assertEquals(71, line.batteryPct)
            assertNull(line.placeId)
        }

    // --- Limits: 15 minutes, 5 points, 3 hours -----------------------------------------------------------------------------------------------

    @Test
    fun neverTwiceWithinFifteenMinutes_whateverTheLevel_andOnceTheyPassed_aRealChangeIsWritten() =
        runTest {
            openedOnceWithABatteryWrite()
            env.now += 14 * minute
            env.battery = 10
            engine.lookAgain(TrackingEngine.Reason.BACKGROUNDED)
            assertTrue("14 minutes: nothing", batteryWrites().isEmpty())
            env.now += 1 * minute
            engine.lookAgain(TrackingEngine.Reason.OPENED)
            assertEquals("15 minutes and a change of 70 points: written", 1, batteryWrites().size)
        }

    @Test
    fun beforeThreeHours_aMoveOfLessThanFivePointsIsNotWritten_fivePointsAre_inBothDirections() =
        runTest {
            openedOnceWithABatteryWrite() // 80 %
            env.now += 20 * minute
            env.battery = 76
            engine.refreshBattery()
            assertTrue(batteryWrites().isEmpty())
            env.battery = 84
            engine.refreshBattery()
            assertTrue(batteryWrites().isEmpty())
            env.battery = 75
            engine.refreshBattery()
            assertEquals(1, batteryWrites().size)
            // The reference is now 75: 80 is a move of 5 upwards.
            env.now += 20 * minute
            env.battery = 80
            engine.refreshBattery()
            assertEquals(2, batteryWrites().size)
        }

    @Test
    fun aSteadyLevelIsWrittenAfterThreeHours_notBefore() =
        runTest {
            openedOnceWithABatteryWrite()
            env.now += 3 * hour - minute
            engine.refreshBattery()
            assertTrue(batteryWrites().isEmpty())
            env.now += minute
            engine.refreshBattery()
            assertEquals(1, batteryWrites().size)
            assertEquals(80, batteryWrites().single().data["batteryPct"])
        }

    @Test
    fun aStatusWrittenByTheDetection_carriesTheFreshBattery_andCountsAsTheLastBatteryWrite() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            env.battery = 55
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            val arrival = statusWrites().single()
            assertEquals("the arrival still writes the fresh level", 55, arrival.data["batteryPct"])
            assertEquals("home", arrival.data["placeId"])
            // Ten minutes later the level is the same as the one just written: nothing to refresh, even 20 minutes after.
            env.now += 20 * minute
            env.battery = 52
            engine.lookAgain(TrackingEngine.Reason.OPENED)
            assertTrue(batteryWrites().isEmpty())
            // And 3 hours after that arrival the level is written again.
            env.now += 3 * hour
            engine.lookAgain(TrackingEngine.Reason.BACKGROUNDED)
            assertEquals(1, batteryWrites().size)
        }

    @Test
    fun theAccessUpdate_carriesTheBattery_andCountsAsTheLastBatteryWrite() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            store.commits.clear()
            env.now += 20 * minute
            env.snapshot = whileInUseOnly()
            env.battery = 30
            engine.lookAgain(TrackingEngine.Reason.OPENED)
            val access = statusWrites().single()
            assertEquals(30, access.data["batteryPct"])
            assertEquals("foreground", access.data["locationAccess"])
            assertTrue("no second write for the same level", batteryWrites().isEmpty())
        }

    @Test
    fun withoutAlways_theBatteryIsStillRefreshed_noPositionIsRead_theAccessIsLeftAlone() =
        runTest {
            share(miffA)
            env.snapshot = whileInUseOnly()
            engine.refresh(TrackingEngine.Reason.OPENED)
            val positionReads = position.reads
            store.commits.clear()
            env.now += 4 * hour
            engine.refreshBattery()
            val write = batteryWrites().single()
            assertFalse("locationAccess" in write.data)
            assertEquals(positionReads, position.reads)
        }

    // --- The dedicated daily breaker ---------------------------------------------------------------------------------------------------------

    @Test
    fun eachBatteryWriteCountsInTheDedicatedCounter_notInThatOfTheArrivals() =
        runTest {
            share(miffA, miffB)
            engine.lookAgain(TrackingEngine.Reason.OPENED)
            val breaker = local.state!!.breaker!!
            assertEquals(2, breaker.batteryWrites)
            assertEquals(0, breaker.statusWrites)
        }

    @Test
    fun atTheDailyLimit_nothingIsWritten_theArrivalsKeepTheirWholeBudget_andTheNextDayStartsAgain() =
        runTest {
            openedOnceWithABatteryWrite()
            env.now += 4 * hour
            val day = WriteBreaker.dayKey(env.now)
            local.state =
                local.state!!.copyWith(breaker = BreakerState(day, 0, 0, TrackingLimits.MAX_BATTERY_WRITES_PER_DAY))
            engine.refreshBattery()
            assertTrue(batteryWrites().isEmpty())
            assertTrue(JournalKind.BREAKER_BATTERY in journal.kinds)
            // An arrival is written all the same.
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertEquals("home", statusWrites().single().data["placeId"])
            // The next day the counter is back to zero.
            env.now += 24 * hour
            engine.refreshBattery()
            assertEquals(1, batteryWrites().size)
        }

    @Test
    fun theStatusBreakerBeingFull_doesNotStopTheBattery_andTheBatteryNeverFillsIt() =
        runTest {
            openedOnceWithABatteryWrite()
            env.now += 4 * hour
            val day = WriteBreaker.dayKey(env.now)
            local.state =
                local.state!!.copyWith(breaker = BreakerState(day, TrackingLimits.MAX_STATUS_WRITES_PER_DAY, 0, 0))
            engine.refreshBattery()
            assertEquals(1, batteryWrites().size)
            assertEquals(TrackingLimits.MAX_STATUS_WRITES_PER_DAY, local.state!!.breaker!!.statusWrites)
        }

    // --- NEVER create, never write without the sharing --------------------------------------------------------------------------------------

    @Test
    fun aStatusThatDoesNotExist_isRefused_neverCreated_andTheRefusalIsNotRepeatedAtEveryOpening() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            store.alwaysFailing["miffs/miffA/status/uid1"] = refused()
            engine.lookAgain(TrackingEngine.Reason.OPENED)
            assertTrue("nothing reached the server", store.commits.isEmpty())
            assertTrue(JournalKind.BATTERY_REFUSED in journal.kinds)
            assertEquals(1, device.refusalChecks)
            // The Miff is looked at again soon.
            assertEquals(env.now - hour + TrackingLimits.RECHECK_AFTER_REFUSAL_MILLIS, local.state!!.checkedAtMillis)
            // Opening and leaving again within the quarter of an hour: not even tried.
            env.now += 10 * minute
            engine.lookAgain(TrackingEngine.Reason.BACKGROUNDED)
            engine.lookAgain(TrackingEngine.Reason.OPENED)
            assertEquals("no new attempt", 1, device.refusalChecks)
            // Nothing the engine sent was ever a creation of the status.
            assertTrue(store.commits.flatten().none { it.kind == WriteKind.SET && "/status/" in it.path })
        }

    @Test
    fun whenTheSharingWasStoppedElsewhere_theRefusalMakesTheNextLookDropTheMiff_andNothingIsWrittenAnyMore() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            store.alwaysFailing["miffs/miffA/status/uid1"] = refused()
            engine.lookAgain(TrackingEngine.Reason.OPENED)
            // The sharing really is stopped; 16 minutes later the look at the server finds it and the Miff is forgotten.
            store.consents[miffA] = SharingConsentRead(enabled = false, version = "2026-11-v03")
            store.alwaysFailing.clear()
            env.now += 16 * minute
            engine.lookAgain(TrackingEngine.Reason.OPENED)
            assertTrue(local.state!!.miffs.isEmpty())
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun withTheOldConsentText_orTheSharingStopped_nothingIsWritten() =
        runTest {
            store.memberships = listOf(MembershipRef("ka", miffA), MembershipRef("kb", miffB))
            store.consents[miffA] = SharingConsentRead(enabled = true, version = "2026-10-v2")
            store.consents[miffB] = SharingConsentRead(enabled = false, version = "2026-11-v03")
            store.places[miffA] = listOf(home)
            store.places[miffB] = listOf(home)
            engine.lookAgain(TrackingEngine.Reason.OPENED)
            env.now += 4 * hour
            engine.lookAgain(TrackingEngine.Reason.BACKGROUNDED)
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun onlyTheMiffsWhereTheConsentIsV3AndTheSharingIsOn_areRefreshed() =
        runTest {
            store.memberships = listOf(MembershipRef("ka", miffA), MembershipRef("kb", miffB))
            store.consents[miffA] = SharingConsentRead(enabled = true, version = "2026-11-v03")
            store.consents[miffB] = SharingConsentRead(enabled = true, version = "2026-10-v2")
            store.places[miffA] = listOf(home)
            store.places[miffB] = listOf(home)
            engine.lookAgain(TrackingEngine.Reason.OPENED)
            assertEquals(listOf("miffs/miffA/status/uid1"), batteryWrites().map { it.path })
        }

    @Test
    fun beforeAnyLookAtTheMiffs_orWithNoMiff_nothingIsWrittenAndNothingIsRead() =
        runTest {
            share(miffA)
            engine.refreshBattery()
            assertTrue(store.commits.isEmpty())
            assertEquals(0, store.readCalls)
            store.memberships = emptyList()
            engine.lookAgain(TrackingEngine.Reason.OPENED)
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun aStatusThatWaitsToBeWritten_isNotDoubledByABatteryWrite() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            env.online = false
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            env.online = true
            env.now += 4 * hour
            engine.refreshBattery()
            assertTrue("the waiting status will carry the level itself", store.commits.isEmpty())
        }

    // --- Session, account, sign-out ----------------------------------------------------------------------------------------------------------

    @Test
    fun aReplacedPhone_writesNothing() =
        runTest {
            openedOnceWithABatteryWrite()
            device.answer = Clearance.Replaced
            env.now += 4 * hour
            engine.lookAgain(TrackingEngine.Reason.BACKGROUNDED)
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun aSessionThatCannotBeVerified_writesNothing_thenWritesOnceItIsCleared() =
        runTest {
            openedOnceWithABatteryWrite()
            device.answer = Clearance.Unverified
            env.now += 4 * hour
            engine.refreshBattery()
            assertTrue(store.commits.isEmpty())
            device.answer = Clearance.Cleared(DEVICE_A)
            env.now += 16 * minute
            engine.refreshBattery()
            assertEquals(1, batteryWrites().size)
        }

    @Test
    fun aRefusalThatTurnsOutToBeAReplacement_stopsTheOtherMiffsOfTheSamePass() =
        runTest {
            share(miffA, miffB)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            store.alwaysFailing["miffs/miffA/status/uid1"] = refused()
            device.replacedAfterRefusal = true
            engine.refreshBattery()
            assertTrue("the second Miff was not written", store.commits.isEmpty())
            assertEquals(1, device.refusalChecks)
        }

    @Test
    fun whileTheAccountIsBeingDeleted_nothingIsWritten_andTheSessionIsNotEvenAsked() =
        runTest {
            openedOnceWithABatteryWrite()
            device.rechecks.clear()
            underDeletion = true
            env.now += 4 * hour
            engine.lookAgain(TrackingEngine.Reason.OPENED)
            engine.lookAgain(TrackingEngine.Reason.BACKGROUNDED)
            assertTrue(store.commits.isEmpty())
            assertTrue(device.rechecks.isEmpty())
        }

    @Test
    fun afterASignOut_orWhenNobodyIsSignedIn_nothingIsWritten() =
        runTest {
            openedOnceWithABatteryWrite()
            env.now += 4 * hour
            env.uid = null
            engine.refreshBattery()
            assertTrue(store.commits.isEmpty())
            env.uid = "uid1"
            engine.stop()
            engine.refreshBattery()
            assertTrue("a stopped engine stays stopped for the session", store.commits.isEmpty())
            assertNull(local.state)
        }

    @Test
    fun aSessionThatEndsInTheMiddleOfAPass_writesNothingMore() =
        runTest {
            openedOnceWithABatteryWrite()
            env.now += 4 * hour
            // The sign-out happens while the session is being verified at the first write: the pass must not write after it.
            val bumping =
                object : com.tomessa.miff.data.session.ActiveDevice {
                    override suspend fun clearance(recheck: Boolean): Clearance {
                        generation++
                        return Clearance.Cleared(DEVICE_A)
                    }

                    override suspend fun afterRefusal() = false
                }
            val other =
                TrackingEngine(
                    env,
                    store,
                    local,
                    position,
                    geofences,
                    movement,
                    wake,
                    bumping,
                    journal,
                    timeoutMillis = 5_000,
                    generation = { generation },
                )
            other.refreshBattery()
            assertTrue(store.commits.isEmpty())
        }

    // --- Best effort ---------------------------------------------------------------------------------------------------------------------------

    @Test
    fun offline_nothingIsTried_andOnceBackOnlineTheNextOpeningWrites() =
        runTest {
            openedOnceWithABatteryWrite()
            env.now += 4 * hour
            env.online = false
            engine.refreshBattery()
            assertTrue(store.commits.isEmpty())
            env.online = true
            env.now += minute
            engine.refreshBattery()
            assertEquals("an offline attempt does not use the quarter of an hour", 1, batteryWrites().size)
        }

    @Test
    fun aPhoneThatDoesNotSayItsLevel_writesNothing() =
        runTest {
            openedOnceWithABatteryWrite()
            env.battery = null
            env.now += 4 * hour
            engine.refreshBattery()
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun aWriteThatFails_neverThrows_isNotRecorded_andIsTriedAgainAfterAQuarterOfAnHour() =
        runTest {
            openedOnceWithABatteryWrite()
            env.now += 4 * hour
            store.alwaysFailing["miffs/miffA/status/uid1"] = RuntimeException("boom")
            engine.refreshBattery()
            assertTrue(store.commits.isEmpty())
            store.alwaysFailing.clear()
            env.now += 5 * minute
            engine.refreshBattery()
            assertTrue("not before a quarter of an hour", store.commits.isEmpty())
            env.now += 11 * minute
            engine.refreshBattery()
            assertEquals(1, batteryWrites().size)
        }

    @Test
    fun aPassHeldByAnotherPass_isGivenUp_withoutWaiting() =
        runTest {
            openedOnceWithABatteryWrite()
            env.now += 61 * minute // the places are due: the opening below reads them and is held there
            env.battery = 20
            val slow = CompletableDeferred<Unit>()
            store.onPlaces = { slow.await() }
            val opening = launch { engine.refresh(TrackingEngine.Reason.OPENED) }
            runCurrent()
            engine.refreshBattery() // returns at once: it does not queue behind a long pass
            assertTrue(store.commits.isEmpty())
            slow.complete(Unit)
            opening.join()
        }
}
