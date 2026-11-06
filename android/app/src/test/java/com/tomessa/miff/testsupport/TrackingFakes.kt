package com.tomessa.miff.testsupport

import com.tomessa.miff.data.miff.SharingConsentRead
import com.tomessa.miff.data.tracking.GeofenceArming
import com.tomessa.miff.data.tracking.LocalState
import com.tomessa.miff.data.tracking.MovementUpdates
import com.tomessa.miff.data.tracking.NetworkWake
import com.tomessa.miff.data.tracking.PositionSource
import com.tomessa.miff.data.tracking.TrackingEnvironment
import com.tomessa.miff.data.tracking.TrackingLocalStore
import com.tomessa.miff.data.tracking.TrackingStore
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.permission.PermissionSnapshot
import com.tomessa.miff.domain.tracking.DetectionJournal
import com.tomessa.miff.domain.tracking.Fix
import com.tomessa.miff.domain.tracking.JournalKind
import com.tomessa.miff.domain.tracking.JournalLine
import com.tomessa.miff.domain.tracking.PlaceFact
import com.tomessa.miff.domain.tracking.TrackedZone

/** Doubles of the doors of the detection engine (LOC-2b): the phone, the system, Firestore. Nothing here touches a real position. */

const val T0 = 1_800_000_000_000L

fun fullPermissions(): PermissionSnapshot =
    PermissionSnapshot(
        sdk = 33,
        fineLocation = true,
        coarseLocation = true,
        backgroundLocation = true,
        notifications = true,
    )

fun whileInUseOnly(): PermissionSnapshot =
    PermissionSnapshot(
        sdk = 33,
        fineLocation = true,
        coarseLocation = true,
        backgroundLocation = false,
        notifications = true,
    )

class FakeTrackingEnv : TrackingEnvironment {
    var uid: String? = "uid1"
    var snapshot: PermissionSnapshot = fullPermissions()
    var servicesOn = true
    var battery: Int? = 80
    var online = true
    var now = T0

    override fun uid() = uid

    override fun permissions() = snapshot

    override fun locationServicesOn() = servicesOn

    override fun batteryPct() = battery

    override fun isOnline() = online

    override fun nowMillis() = now
}

class FakePosition(
    private val env: FakeTrackingEnv,
) : PositionSource {
    var lat = 48.8566
    var lng = 2.3522
    var available = true

    /** The position never comes back (a slow start of the provider): the engine must not wait for ever. */
    var hangs = false

    /** How old the position is when it comes back (a fallback on an old last location). */
    var ageMillis = 0L
    var reads = 0

    override suspend fun current(): Fix? {
        reads++
        if (hangs) kotlinx.coroutines.awaitCancellation()
        return if (available) Fix(lat, lng, env.now - ageMillis) else null
    }
}

class FakeGeofences : GeofenceArming {
    val armed = linkedSetOf<String>()
    var accept = true
    var removeAllCalls = 0
    var updates = 0

    override suspend fun update(
        add: List<TrackedZone>,
        removeIds: List<String>,
    ): Boolean {
        updates++
        if (!accept) return false
        armed.removeAll(removeIds.toSet())
        armed.addAll(add.map { it.key })
        return true
    }

    override suspend fun removeAll() {
        removeAllCalls++
        armed.clear()
    }
}

class FakeMovement : MovementUpdates {
    var started = false
    var startCalls = 0

    override suspend fun start(): Boolean {
        startCalls++
        started = true
        return true
    }

    override suspend fun stop() {
        started = false
    }
}

class FakeNetworkWake : NetworkWake {
    var watching = false

    override fun watch() {
        watching = true
    }

    override fun stop() {
        watching = false
    }
}

class FakeLocal : TrackingLocalStore {
    var state: LocalState? = null

    override fun load() = state

    override fun save(state: LocalState) {
        this.state = state
    }

    override fun clear() {
        state = null
    }
}

class RecordingJournal : DetectionJournal {
    val kinds = mutableListOf<JournalKind>()
    private val lines = mutableListOf<JournalLine>()

    override fun record(
        kind: JournalKind,
        placeId: String?,
        batteryPct: Int?,
    ) {
        kinds += kind
        lines += JournalLine(0, kind, placeId, batteryPct)
    }

    override fun lines() = lines.toList()

    override fun clear() {
        kinds.clear()
        lines.clear()
    }
}

class FakeTrackingStore : TrackingStore {
    var memberships: List<MembershipRef> = emptyList()
    val consents = mutableMapOf<String, SharingConsentRead?>()
    val places = mutableMapOf<String, List<PlaceFact>>()

    /** Every lot handed to the transaction, in order. */
    val commits = mutableListOf<List<DocWrite>>()

    /** For a path: what the next writes to it do (null = accepted, an exception = refused / offline). Consumed in order; empty = accepted. */
    val failuresByPath = mutableMapOf<String, ArrayDeque<Exception?>>()

    /** A path that always fails. */
    val alwaysFailing = mutableMapOf<String, Exception>()
    var readCalls = 0

    /** When set, the reads of the server fail with it (no network, a refusal). */
    var readFailure: Exception? = null

    override suspend fun memberships(uid: String): List<MembershipRef> {
        readCalls++
        readFailure?.let { throw it }
        return memberships
    }

    override suspend fun consent(
        miffId: String,
        uid: String,
    ): SharingConsentRead? {
        readCalls++
        return consents[miffId]
    }

    /** Runs when the places are read (to hold a pass of the engine in the middle: a slow network). */
    var onPlaces: suspend () -> Unit = {}

    override suspend fun places(miffId: String): List<PlaceFact> {
        onPlaces()
        readCalls++
        return places[miffId].orEmpty()
    }

    override suspend fun commit(writes: List<DocWrite>) {
        val path = writes.first().path
        alwaysFailing[path]?.let { throw it }
        val next = failuresByPath[path]?.removeFirstOrNull()
        if (next != null) throw next
        commits += writes
    }

    fun writesTo(fragment: String): List<DocWrite> = commits.flatten().filter { fragment in it.path }
}
