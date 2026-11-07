package com.tomessa.miff.data.tracking

import com.tomessa.miff.data.miff.SharingConsentRead
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.tracking.PlaceFact
import com.tomessa.miff.domain.tracking.TransitionKind
import com.tomessa.miff.testsupport.FakeActiveDevice
import com.tomessa.miff.testsupport.FakeGeofences
import com.tomessa.miff.testsupport.FakeLocal
import com.tomessa.miff.testsupport.FakeMovement
import com.tomessa.miff.testsupport.FakeNetworkWake
import com.tomessa.miff.testsupport.FakePosition
import com.tomessa.miff.testsupport.FakeTrackingEnv
import com.tomessa.miff.testsupport.FakeTrackingStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The detection and the deletion of the account (AUTH-5b, criteria SC15 and SC39): while the deletion is under way (its marker is on the DISK), a receiver of the
 * system that wakes a COLD process (it has no memory of the [TrackingEngine.stop] of the deletion) arms nothing, reads no position, keeps no local copy and
 * writes nothing: no status, no position, no access, and no session either (that one is [com.tomessa.miff.data.session.DeviceSession]'s, persisted too).
 */
class TrackingEngineDeletionTest {
    private val miffA = "miffA"
    private val hour = 60L * 60L * 1000L
    private val env = FakeTrackingEnv()
    private val store = FakeTrackingStore()
    private val local = FakeLocal()
    private val position = FakePosition(env)
    private val geofences = FakeGeofences()
    private val movement = FakeMovement()
    private val wake = FakeNetworkWake()
    private val device = FakeActiveDevice()
    private var underDeletion = false
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
            timeoutMillis = 5_000,
            suspended = { underDeletion },
        )

    private val home = PlaceFact("home", 48.8566, 2.3522, 100, PlaceType.TRUSTED)

    private fun share() {
        store.memberships = listOf(MembershipRef("k$miffA", miffA))
        store.consents[miffA] = SharingConsentRead(enabled = true, version = "2026-11-v03")
        store.places[miffA] = listOf(home)
    }

    @Test
    fun aGeofenceEvent_whileTheDeletionIsUnderWay_writesNothing_readsNothing_keepsNothing() =
        runTest {
            share()
            underDeletion = true
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertTrue("no status, no position, no access", store.commits.isEmpty())
            assertEquals("no read of the server either", 0, store.readCalls)
            assertEquals("no position read", 0, position.reads)
            assertNull("no local copy", local.state)
            assertTrue("no zone armed", geofences.armed.isEmpty())
            assertTrue(device.rechecks.isEmpty())
        }

    @Test
    fun everyWakeUp_whileTheDeletionIsUnderWay_doesNothing_openedRearmMovedNetworkAndSharing() =
        runTest {
            share()
            underDeletion = true
            TrackingEngine.Reason.entries.forEach { engine.refresh(it) }
            engine.onNetworkBack()
            assertTrue(store.commits.isEmpty())
            assertEquals(0, store.readCalls)
            assertEquals(0, position.reads)
            assertNull(local.state)
            assertEquals("nothing is armed", 0, geofences.updates)
            assertEquals(0, movement.startCalls)
            assertTrue(!wake.watching)
            assertTrue("the session is not even asked", device.rechecks.isEmpty())
        }

    @Test
    fun aPassThatWasUnderWay_whenTheMarkerAppears_writesNothingMore() =
        runTest {
            share()
            // The pass starts normally; the marker is written (the person confirmed on this very phone) while it waits for the places.
            store.onPlaces = { underDeletion = true }
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now)
            assertTrue("nothing was written once the deletion was under way", store.commits.isEmpty())
        }

    @Test
    fun withNoDeletion_theEngineStillWorks_theMarkerIsTheOnlyThingThatStopsIt() =
        runTest {
            share()
            engine.refresh(TrackingEngine.Reason.OPENED)
            engine.onTransition(TransitionKind.ENTER, home.key, env.now + hour)
            assertTrue("a status was written", store.writesTo("/status/").isNotEmpty())
        }

    @Test
    fun whenTheDeletionIsCancelled_theEngineWorksAgain() =
        runTest {
            share()
            underDeletion = true
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertEquals(0, store.readCalls)
            underDeletion = false
            engine.refresh(TrackingEngine.Reason.OPENED)
            assertTrue(store.readCalls > 0)
        }
}
