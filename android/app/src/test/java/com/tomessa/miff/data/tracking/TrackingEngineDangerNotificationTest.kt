package com.tomessa.miff.data.tracking

import com.tomessa.miff.data.miff.SharingConsentRead
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.tracking.PlaceFact
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.TransitionKind
import com.tomessa.miff.testsupport.FakeActiveDevice
import com.tomessa.miff.testsupport.FakeGeofences
import com.tomessa.miff.testsupport.FakeLocal
import com.tomessa.miff.testsupport.FakeMovement
import com.tomessa.miff.testsupport.FakeNetworkWake
import com.tomessa.miff.testsupport.FakePosition
import com.tomessa.miff.testsupport.FakeTrackingEnv
import com.tomessa.miff.testsupport.FakeTrackingStore
import com.tomessa.miff.testsupport.RecordingJournal
import com.tomessa.miff.testsupport.T0
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the detection tells the notifications (NOTIF-7A, vectors V1 to V4 of the ticket, section 20.9): N6 is told ONLY for a REAL entry in a danger zone. The engine is
 * driven with the same doubles as `TrackingEngineTest`; the listener records what it is told. Nothing is read or written because of it.
 */
class TrackingEngineDangerNotificationTest {
    private class Told(
        val uid: String,
        val miffId: String,
        val placeId: String,
        val atMillis: Long,
    )

    private val miffA = "miffA"
    private val miffB = "miffB"
    private val hour = 60L * 60L * 1000L

    private val env = FakeTrackingEnv()
    private val store = FakeTrackingStore()
    private val local = FakeLocal()
    private val position = FakePosition(env)
    private val geofences = FakeGeofences()
    private val movement = FakeMovement()
    private val wake = FakeNetworkWake()
    private val device = FakeActiveDevice()
    private val journal = RecordingJournal()

    private val danger = mutableListOf<Told>()
    private val presences = mutableListOf<Pair<String, PresenceValue>>()
    private var explode = false

    private val listener =
        object : PresenceListener {
            override fun onMyPresence(
                miffId: String,
                presence: PresenceValue,
                atMillis: Long,
                setLastUpdate: Boolean,
            ) {
                presences += miffId to presence
            }

            override fun onEnteredDanger(
                myUid: String,
                miffId: String,
                placeId: String,
                atMillis: Long,
            ) {
                danger += Told(myUid, miffId, placeId, atMillis)
                if (explode) error("a notification that cannot be shown")
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
        )

    // The person is at (48.8566, 2.3522); `home` is under their feet, `danger` is 1.1 km north.
    private val lat = 48.8566
    private val lng = 2.3522
    private val home = PlaceFact("home", lat, lng, 100, PlaceType.TRUSTED)
    private val dangerZone = PlaceFact("danger", lat + 0.01, lng, 100, PlaceType.RISK)

    private fun share(
        vararg miffs: String,
        placesOf: (String) -> List<PlaceFact> = { listOf(home, dangerZone) },
    ) {
        store.memberships = miffs.map { MembershipRef("k$it", it) }
        miffs.forEach {
            store.consents[it] = SharingConsentRead(enabled = true, version = "2026-11-v03")
            store.places[it] = placesOf(it)
        }
    }

    @Test
    fun v1_iEnterADangerZoneOutOfEveryTrustedZone_theNotificationsAreToldOnce_withThePlaceIdentifierOnly() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, dangerZone.key, env.now)
            val told = danger.single()
            assertEquals("uid1", told.uid)
            assertEquals(miffA, told.miffId)
            assertEquals("danger", told.placeId)
            assertEquals(env.now, told.atMillis)
            // My presence is told too (the reminders N1 and N2 are cancelled by it).
            assertTrue(miffA to PresenceValue.IN_RISK in presences)
        }

    @Test
    fun v2_theTrustedZoneWins_butLeavingItWhileStayingInTheDangerZoneIsAnEntry() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            engine.onTransition(TransitionKind.ENTER, dangerZone.key, env.now + 1_000)
            assertTrue("the trusted zone wins: no entry in a danger zone", danger.isEmpty())
            engine.onTransition(TransitionKind.EXIT, home.key, env.now + 2_000)
            assertEquals("I am now in the danger zone and in no trusted zone", 1, danger.size)
            assertEquals("danger", danger.single().placeId)
        }

    @Test
    fun v3_afterARestart_whenIAlreadyWasInRisk_nothingIsToldAgain() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, dangerZone.key, env.now)
            assertEquals(1, danger.size)
            geofences.armed.clear()
            env.now += hour
            position.lat = dangerZone.lat // still there when the phone woke up
            engine.refresh(TrackingEngine.Reason.REARM)
            assertEquals("a presence that was already in_risk is not an entry", 1, danger.size)
        }

    @Test
    fun v4_afterARestart_whenTheLastPresenceWasOut_andIAmNowInTheZone_itIsToldOnce() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            // I was at home, then left: my last presence is OUT. The phone restarts (the geofences are gone) while I walk into the danger zone: no event came.
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            engine.onTransition(TransitionKind.EXIT, home.key, env.now + 1_000)
            assertTrue("leaving a trusted zone is not an entry in a danger zone", danger.isEmpty())
            geofences.armed.clear()
            env.now += hour
            position.lat = dangerZone.lat
            engine.refresh(TrackingEngine.Reason.REARM)
            assertEquals(1, danger.size)
            assertEquals("danger", danger.single().placeId)
            // A second look at the same state: nothing more.
            engine.refresh(TrackingEngine.Reason.REARM)
            assertEquals(1, danger.size)
        }

    @Test
    fun aDangerZoneOfTwoMiffs_isToldOncePerMiff_theNameOfEachMiffIsTheCentresBusiness() =
        runTest {
            share(miffA, miffB)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, dangerZone.key, env.now)
            assertEquals(setOf(miffA, miffB), danger.map { it.miffId }.toSet())
            assertEquals(2, danger.size)
        }

    @Test
    fun aPlaceOfAnotherMiffOnly_tellsNothingForTheMiffThatDoesNotHaveIt() =
        runTest {
            share(miffA, miffB) { if (it == miffA) listOf(home, dangerZone) else listOf(home) }
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, dangerZone.key, env.now)
            assertEquals(listOf(miffA), danger.map { it.miffId })
        }

    @Test
    fun aTrustedZoneEntryOrAnExitTellsNoDanger() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            engine.onTransition(TransitionKind.EXIT, home.key, env.now + 1_000)
            assertTrue(danger.isEmpty())
            assertTrue(miffA to PresenceValue.OUT in presences)
        }

    @Test
    fun aNotificationThatFails_neverStopsTheDetection_theStatusIsStillWritten() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            store.commits.clear()
            explode = true
            engine.onTransition(TransitionKind.ENTER, dangerZone.key, env.now)
            assertEquals(1, danger.size)
            assertEquals("in_risk", store.writesTo("/status/").single().data["presence"])
        }

    @Test
    fun anEventTooOldForTheRules_isNotTold_itIsNoLongerThePresent() =
        runTest {
            share(miffA)
            engine.refresh(TrackingEngine.Reason.OPENED)
            // 25 hours before now: the rules refuse it (24 h), the engine gives it up, and no notification is made of a past that is over.
            engine.onTransition(TransitionKind.ENTER, dangerZone.key, T0 - 25 * hour)
            assertTrue(danger.isEmpty())
        }
}
