package com.tomessa.miff.data.tracking

import com.tomessa.miff.data.miff.SharingConsentRead
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.tracking.PlaceFact
import com.tomessa.miff.domain.tracking.PresenceValue
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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the engine tells the notifications about a trip (VOY-4A, criteria N7-1 and V5, V6, V9): the end of the trip is told when it begins, when its date moves, when a new
 * phone adopts it and when a wake-up finds it again; its end is told as "early" (I turned it off: N7 is cancelled) or not (its end came: N7 still waits for 08:00). A listener
 * that fails never stops the trip. The doubles are those of `TrackingEngineTravelTest`.
 */
class TrackingEngineTravelNotificationTest {
    private val miffA = "miffA"
    private val minute = 60_000L
    private val day = 24 * 60 * minute

    private val env = FakeTrackingEnv()
    private val store = FakeTrackingStore()
    private val local = FakeLocal()
    private val position = FakePosition(env)

    private val set = mutableListOf<Pair<String, Long>>()
    private val ended = mutableListOf<Pair<String, Boolean>>()
    private var explode = false

    private val listener =
        object : PresenceListener {
            override fun onMyPresence(
                miffId: String,
                presence: PresenceValue,
                atMillis: Long,
                setLastUpdate: Boolean,
            ) = Unit

            override fun onTravelSet(
                uid: String,
                untilMillis: Long,
            ) {
                set += uid to untilMillis
                if (explode) error("a notification that cannot be planned")
            }

            override fun onTravelEnded(
                uid: String,
                early: Boolean,
            ) {
                ended += uid to early
                if (explode) error("a notification that cannot be cancelled")
            }
        }

    private val engine =
        TrackingEngine(
            env,
            store,
            local,
            position,
            FakeGeofences(),
            FakeMovement(),
            FakeNetworkWake(),
            FakeActiveDevice(),
            RecordingJournal(),
            timeoutMillis = 5_000,
            presenceListener = listener,
            travelAlarm = FakeTravelAlarm(),
        )

    private val home = PlaceFact("home", 48.8566, 2.3522, 100, PlaceType.TRUSTED)
    private val period = TravelPeriod(T0 + 2 * day, "2027-01-17")

    private fun share() {
        store.memberships = listOf(MembershipRef("k$miffA", miffA))
        store.consents[miffA] = SharingConsentRead(enabled = true, version = "2026-11-v03")
        store.places[miffA] = listOf(home)
    }

    @Test
    fun aTripBegins_theNotificationsAreToldItsEnd() =
        runTest {
            share()
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.setTravel(period)
            assertEquals(listOf("uid1" to period.untilMillis), set.distinct())
            assertTrue(ended.isEmpty())
        }

    @Test
    fun theDateMoves_theNewEndIsTold() =
        runTest {
            share()
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.setTravel(period)
            val moved = TravelPeriod(T0 + 4 * day, "2027-01-19")
            engine.setTravel(moved)
            assertEquals(moved.untilMillis, set.last().second)
        }

    @Test
    fun aTripFoundAgainAtAWakeUp_isToldAgain_forATripBegunBeforeThisVersion() =
        runTest {
            share()
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.setTravel(period)
            set.clear()
            env.now += 10 * minute
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(listOf("uid1" to period.untilMillis), set)
        }

    @Test
    fun turningTheTripOff_isAnEarlyEnd() =
        runTest {
            share()
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.setTravel(period)
            env.now += 10 * minute
            engine.endTravel()
            assertEquals(listOf("uid1" to true), ended)
        }

    @Test
    fun theEndComingByItself_isNotAnEarlyEnd_n7StillWaitsForEight() =
        runTest {
            share()
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.setTravel(period)
            env.now = period.untilMillis + 2 * minute
            engine.refresh(TrackingEngine.Reason.TRAVEL_ENDED)
            assertEquals(listOf("uid1" to false), ended)
        }

    @Test
    fun anEndFoundAtTheOpeningDaysLater_isNotAnEarlyEnd() =
        runTest {
            share()
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.setTravel(period)
            env.now = period.untilMillis + 5 * day
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(listOf("uid1" to false), ended)
        }

    @Test
    fun aNewPhoneThatAdoptsATrip_toldItsEnd() =
        runTest {
            share()
            store.ownTravels[miffA] = TravelRead(period, T0 - day)
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(period.untilMillis, set.first().second)
        }

    @Test
    fun noTripNoTelling() =
        runTest {
            share()
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertTrue(set.isEmpty())
            assertTrue(ended.isEmpty())
        }

    @Test
    fun aListenerThatFails_neverStopsTheTrip() =
        runTest {
            share()
            engine.refresh(TrackingEngine.Reason.OPENED)
            explode = true
            assertEquals(TravelResult.Done, engine.setTravel(period))
            assertEquals(period, engine.travel.value)
            env.now += 10 * minute
            assertTrue(engine.endTravel())
            assertEquals(null, engine.travel.value)
        }
}
