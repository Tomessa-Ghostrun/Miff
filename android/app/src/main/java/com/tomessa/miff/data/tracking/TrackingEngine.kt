package com.tomessa.miff.data.tracking

import com.tomessa.miff.data.miff.Guarded
import com.tomessa.miff.data.miff.guardedCall
import com.tomessa.miff.data.session.ActiveDevice
import com.tomessa.miff.data.session.Clearance
import com.tomessa.miff.data.session.SessionTeardown
import com.tomessa.miff.domain.miff.LocationAccess
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.MiffSharingConsent
import com.tomessa.miff.domain.tracking.DerivedPresence
import com.tomessa.miff.domain.tracking.DetectionJournal
import com.tomessa.miff.domain.tracking.EventTime
import com.tomessa.miff.domain.tracking.Fix
import com.tomessa.miff.domain.tracking.JournalKind
import com.tomessa.miff.domain.tracking.LocationAccessRule
import com.tomessa.miff.domain.tracking.NoDetectionJournal
import com.tomessa.miff.domain.tracking.PositionThrottle
import com.tomessa.miff.domain.tracking.PresenceMachine
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.StatusFact
import com.tomessa.miff.domain.tracking.TrackedZone
import com.tomessa.miff.domain.tracking.TrackingLimits
import com.tomessa.miff.domain.tracking.TrackingWrites
import com.tomessa.miff.domain.tracking.TransitionKind
import com.tomessa.miff.domain.tracking.WriteBreaker
import com.tomessa.miff.domain.tracking.WrittenPosition
import com.tomessa.miff.domain.tracking.ZoneKey
import com.tomessa.miff.domain.tracking.ZoneRef
import com.tomessa.miff.domain.tracking.ZoneSelection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/*
 * SECURITY / PRIVACY, for this file (criteria AUT7, POS1 to POS8, DET1 to DET4, STA1 to STA4, VIE1 to VIE3, HOR1):
 * - NOTHING is watched, read or written unless the person is signed in, has "Toujours" + the precise location, AND shares with a Miff under the
 *   consent of version 3. Each condition is checked at EVERY entry (a withdrawal is noticed at the next wake-up and everything stops).
 * - A position is read only here, through [PositionSource]; it is kept in the private local copy (to choose the 20 nearest places), written to
 *   Firestore ONLY as the last position of the Miffs that qualify (one write per Miff, alone), and never logged, never sent as an event, never
 *   copied anywhere else. The geofence receiver never reads the location that comes with an event (see TrackingReceiver).
 * - The status carries facts only; the colour is never written. The time written is the real time of the event.
 * - Everything is erased by [stop] (sign-out, deletion of the account, session lost) and, Miff by Miff, when the sharing or the access is lost.
 *   [stop] is STICKY for the session that ended (review I4): nothing is rebuilt until the session changes.
 * - A zone is named by its [ZoneKey] (identifier + geometry + type), never by the identifier alone (review B1). The key holds coordinates: it is never
 *   logged or journaled; the journal and the status only get the place identifier.
 * - "Un compte, un seul appareil" (SESS-2): NOTHING is written (status, access, position) unless [ActiveDevice.clearance] says this phone holds the session, checked
 *   against the server before the first write of a pass (and again at the return of the network); every write carries the identifier of the session. A phone that
 *   was replaced keeps what waits (it is erased by [stop], which the forced sign-out calls) and writes nothing; a refused write makes the session be looked at again.
 * No log, no analytics event: only the local journal of the debug build, which cannot hold a coordinate.
 */
class TrackingEngine(
    private val env: TrackingEnvironment,
    private val store: TrackingStore,
    private val local: TrackingLocalStore,
    private val position: PositionSource,
    private val geofences: GeofenceArming,
    private val movement: MovementUpdates,
    private val networkWake: NetworkWake,
    /** Who holds the session of the account: the writes below only leave with its identifier. */
    private val device: ActiveDevice,
    private val journal: DetectionJournal = NoDetectionJournal,
    private val timeoutMillis: Long = 15_000L,
    /** Goes up at every end of a session (`SessionManager.generation`): what [stop] stopped stays stopped until it changes. */
    private val generation: () -> Int = { 0 },
) : SessionTeardown {
    /** Why the engine is asked to look again (ticket 6.5: opening, going to the background, a move of more than 250 m, an entry in a zone). */
    enum class Reason { OPENED, BACKGROUNDED, MOVED, ZONE_ENTERED, REARM, SHARING_CHANGED, NETWORK_BACK }

    private val mutex = Mutex()

    /** The generation of the session that was stopped (nothing is allowed to run in it any more), or [NEVER]. */
    @Volatile
    private var stoppedGeneration = NEVER

    private fun sessionEnded(): Boolean = stoppedGeneration == generation()

    /**
     * The permission to write during ONE pass (a refresh, an event, the return of the network): the session is verified at the FIRST write only (a pass that
     * writes nothing costs no read), the answer is kept for the rest of the pass, and a refusal that turns out to be a replacement closes it at once.
     * [recheck]: the server is asked even if a recent verification exists (opening, restart, return of the network).
     */
    private inner class WriteGate(
        private val recheck: Boolean,
    ) {
        private var asked = false
        private var deviceId: String? = null

        /** The identifier to write, or null: nothing may be written now (replaced, or the server could not be asked). */
        suspend fun deviceId(): String? {
            if (!asked) {
                asked = true
                deviceId = (device.clearance(recheck) as? Clearance.Cleared)?.deviceId
            }
            return deviceId
        }

        /** A write was refused: was it because the session is no longer ours? Then nothing else is written in this pass. */
        suspend fun refused() {
            if (device.afterRefusal()) {
                asked = true
                deviceId = null
            }
        }
    }

    /** The app came to the front, went to the back, the provider reported a move, the phone restarted, the sharing changed. */
    suspend fun refresh(
        reason: Reason,
        suppliedFix: Fix? = null,
    ) {
        mutex.withLock { if (!sessionEnded()) doRefresh(reason, suppliedFix) }
    }

    /**
     * The geofencing says: the person entered or left the zone named [zoneKey]. The location that comes with the event is NEVER read (the receiver
     * does not).
     */
    suspend fun onTransition(
        kind: TransitionKind,
        zoneKey: String,
        atMillis: Long,
    ) {
        mutex.withLock { if (!sessionEnded()) doTransition(kind, zoneKey, atMillis) }
    }

    /**
     * The network is back: what waits to be written is written, but only after the conditions are checked AGAIN (review I3): "Toujours" still there,
     * and the sharing under the consent of version 3 read from the server. Reads no position.
     */
    suspend fun onNetworkBack() {
        mutex.withLock {
            if (sessionEnded()) return@withLock
            val uid = env.uid() ?: return@withLock doStop()
            val state = stateFor(uid) ?: return@withLock networkWake.stop()
            journal.record(JournalKind.WAKE_NETWORK)
            if (!env.permissions().locationComplete) {
                // The access was withdrawn while the status waited: nothing is written any more, and nothing is kept.
                local.save(disarm(state))
                networkWake.stop()
                return@withLock
            }
            val synced = syncMiffs(uid, state, Reason.NETWORK_BACK, env.nowMillis())
            if (synced.state.miffs.isEmpty() && synced.verified) {
                runCatchingCancellable { geofences.removeAll() }
                runCatchingCancellable { movement.stop() }
                networkWake.stop()
                local.save(LocalState(uid, checkedAtMillis = synced.state.checkedAtMillis))
                return@withLock
            }
            // Not verified (no answer): it stays waiting and nothing is written on an assumption. SESS-2: the session is verified AGAIN before the first write
            // (a phone that was offline may have been replaced meanwhile: what waits is then never written).
            val next = if (synced.verified) flushPending(uid, synced.state, WriteGate(recheck = true)) else synced.state
            local.save(next)
            updateNetworkWake(next)
        }
    }

    /** Sign-out, deletion of the account, session lost (a [SessionTeardown]): everything stops and is erased, and stays stopped for this session. */
    override suspend fun stop() {
        mutex.withLock {
            stoppedGeneration = generation()
            doStop()
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------------

    private suspend fun doStop() {
        runCatchingCancellable { geofences.removeAll() }
        runCatchingCancellable { movement.stop() }
        networkWake.stop()
        local.clear()
        journal.clear()
    }

    /**
     * Nothing is watched any more (the access or the consent is gone): zones and reports removed, and everything that depends on the position or on the
     * consent is erased: where the person is, their last position, the statuses waiting. The places of the Miffs are kept (cheap, and no secret).
     */
    private suspend fun disarm(state: LocalState): LocalState {
        runCatchingCancellable { geofences.removeAll() }
        runCatchingCancellable { movement.stop() }
        journal.record(JournalKind.DISARMED)
        return state.copyWith(
            miffs =
                state.miffs.mapValues { (_, miff) ->
                    miff.copyWith(derived = null, written = null, pending = null)
                },
            inside = emptyMap(),
            armed = emptySet(),
            fix = null,
            updatesArmed = false,
        )
    }

    private suspend fun runCatchingCancellable(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // The system refused (a permission withdrawn between two calls...): nothing more can be done here; the next wake-up looks again.
        }
    }

    /** The local copy of THIS account, or null. A copy of another account is erased on the spot (never reused). */
    private fun stateFor(uid: String): LocalState? {
        val loaded = local.load() ?: return null
        if (loaded.uid == uid) return loaded
        local.clear()
        return null
    }

    private fun accessNow(): LocationAccess = LocationAccessRule.of(env.permissions(), env.locationServicesOn())

    /** The Miffs in the order of this pass: it starts AFTER the one served last (review I5), so that a breaker never always spares the same ones. */
    private fun rotated(
        miffIds: Set<String>,
        cursor: String?,
    ): List<String> {
        val sorted = miffIds.sorted()
        if (cursor == null) return sorted
        return sorted.filter { it > cursor } + sorted.filter { it <= cursor }
    }

    // --- The big recalculation ---------------------------------------------------------------------------------------------------------

    private suspend fun doRefresh(
        reason: Reason,
        suppliedFix: Fix?,
    ) {
        val uid =
            env.uid() ?: run {
                // Nobody signed in: whatever is still armed is an orphan (killed process, failed sign-out): removed.
                doStop()
                return
            }
        val now = env.nowMillis()
        var state = stateFor(uid) ?: LocalState(uid)
        if (reason == Reason.REARM) {
            // After a restart of the phone or an update of the app the system has forgotten the zones and the reports: they are all armed again.
            journal.record(JournalKind.WAKE_REARM)
            state = state.copyWith(armed = emptySet(), updatesArmed = false)
        }
        val permissions = env.permissions()
        // One permission for the whole pass; the session is verified against the server at the first write, again at an opening, a restart, a return of the network.
        val gate =
            WriteGate(
                recheck =
                    reason == Reason.OPENED || reason == Reason.REARM || reason == Reason.NETWORK_BACK,
            )

        state = syncMiffs(uid, state, reason, now).state

        if (state.miffs.isEmpty()) {
            // Nobody to tell: nothing is watched, nothing is kept (the person shares nowhere with the consent of version 3).
            journal.record(JournalKind.NOT_ELIGIBLE)
            runCatchingCancellable { geofences.removeAll() }
            runCatchingCancellable { movement.stop() }
            networkWake.stop()
            local.save(LocalState(uid, checkedAtMillis = state.checkedAtMillis))
            return
        }
        if (!permissions.locationComplete) {
            // "Toujours" + precise are not there: no detection, no position (AUT7). The Miffs are told, once, that the access changed.
            state = disarm(state)
            state = syncAccess(uid, state, now, gate)
            local.save(state)
            return
        }

        // 1. The zones are armed FIRST, without waiting for a position (review I1): a slow start must not cost the arming (a receiver has seconds).
        //    The whole wanted set is registered again at each opening, restart and change of sharing (review B2): what the system lost (forced stop,
        //    cleaning by the phone, location switched off and on, data of Play services erased) comes back; the call is idempotent.
        val fullRegistration = reason == Reason.OPENED || reason == Reason.REARM || reason == Reason.SHARING_CHANGED
        state = armZones(state, state.fix(), fullRegistration)
        local.save(state)

        // 2. The position (bounded wait), only if it is recent (review I2).
        val fix = chooseFix(state, suppliedFix, now)
        state = fix?.let { state.copyWith(fix = it) } ?: state
        if (fix != null) journal.record(JournalKind.POSITION_READ, batteryPct = env.batteryPct())

        state = reconcile(state, reason, fix, now)
        // With more than 20 zones the choice is made again with the fresh position.
        if (fix != null && allZones(state).size > ZoneSelection.MAX_WATCHED) state = armZones(state, fix, false)
        local.save(state)

        state = flushPending(uid, state, gate)
        state = publishPosition(uid, state, fix, now, gate)
        state = syncAccess(uid, state, now, gate)
        local.save(state)
        updateNetworkWake(state)
    }

    private fun usable(
        fix: Fix,
        now: Long,
    ): Boolean =
        now - fix.measuredAtMillis <= TrackingLimits.FIX_MAX_AGE_MILLIS &&
            fix.measuredAtMillis - now <= TrackingLimits.CLOCK_SKEW_MILLIS

    /**
     * A position for this occasion: the one the provider reported, else a fresh one (a few seconds at most), else the last one kept. NONE older than
     * [TrackingLimits.FIX_MAX_AGE_MILLIS] is used, for any reason (review I2): an old position would write a false arrival or a false position.
     */
    private suspend fun chooseFix(
        state: LocalState,
        supplied: Fix?,
        now: Long,
    ): Fix? {
        supplied?.takeIf { usable(it, now) }?.let { return it }
        val fresh =
            try {
                withTimeoutOrNull(TrackingLimits.POSITION_WAIT_MILLIS) { position.current() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        if (fresh != null && usable(fresh, now)) return fresh
        journal.record(JournalKind.POSITION_UNAVAILABLE)
        return state.fix()?.takeIf { usable(it, now) }
    }

    // --- Which Miffs, which places -------------------------------------------------------------------------------------------------------

    /**
     * The result of looking at the server again: the new local copy, and whether the look was COMPLETE (every answer was read; a network cut makes it
     * incomplete and nothing is concluded from it).
     */
    private class Synced(
        val state: LocalState,
        val verified: Boolean = false,
    )

    /**
     * The server is read again (the Miffs of the person, my consent in each, the places of each eligible Miff) when it is due: a change of sharing, a
     * restart, the return of the network, or the local copy older than an hour. That is the choice made for the "sharing stopped elsewhere" case (review
     * I3): between two looks, a write that the server refuses (sharing stopped, consent not of version 3) is the signal and the Miff is looked at again.
     */
    private suspend fun syncMiffs(
        uid: String,
        state: LocalState,
        reason: Reason,
        now: Long,
    ): Synced {
        val due =
            reason == Reason.SHARING_CHANGED ||
                reason == Reason.REARM ||
                reason == Reason.NETWORK_BACK ||
                state.checkedAtMillis == 0L ||
                now - state.checkedAtMillis > TrackingLimits.PLACES_REREAD_MILLIS ||
                (
                    reason == Reason.OPENED && state.miffs.isEmpty() &&
                        now - state.checkedAtMillis > RETRY_WHEN_EMPTY_MILLIS
                )
        if (!due || !env.isOnline()) return Synced(state)

        val refs =
            when (val read = guardedCall(timeoutMillis) { store.memberships(uid) }) {
                is Guarded.Value -> read.value.map { it.miffId }.distinct()

                // No answer: nothing is concluded, what the phone knew stays (a network cut is never "removed").
                is Guarded.Failed -> return Synced(state)
            }
        val miffs = mutableMapOf<String, LocalMiff>()
        var complete = true
        for (miffId in refs) {
            when (val consent = guardedCall(timeoutMillis) { store.consent(miffId, uid) }) {
                is Guarded.Value -> {
                    val card = consent.value
                    val eligible = card != null && card.enabled && MiffSharingConsent.coversPosition(card.version)
                    if (eligible) {
                        val read = readPlacesOf(miffId, state.miffs[miffId], now)
                        miffs[miffId] = read.first
                        if (!read.second) complete = false
                    }
                }

                is Guarded.Failed -> {
                    if (consent.lostAccess) {
                        // The server refused: no longer a member (removed, Miff deleted): not watched any more.
                    } else {
                        complete = false
                        state.miffs[miffId]?.let { miffs[miffId] = it }
                    }
                }
            }
        }
        val dropped = state.miffs.keys - miffs.keys
        if (dropped.isNotEmpty()) journal.record(JournalKind.NOT_ELIGIBLE)
        val next = state.copyWith(miffs = miffs, checkedAtMillis = if (complete) now else state.checkedAtMillis)
        return Synced(next, verified = complete)
    }

    /** The places of a Miff, read again when the copy is older than an hour. Second value: false when the places could not be read this time. */
    private suspend fun readPlacesOf(
        miffId: String,
        previous: LocalMiff?,
        now: Long,
    ): Pair<LocalMiff, Boolean> {
        val needed =
            previous == null || previous.readAtMillis == 0L ||
                now - previous.readAtMillis > TrackingLimits.PLACES_REREAD_MILLIS
        val base = previous ?: LocalMiff()
        if (!needed && previous != null) return previous to true
        return when (val read = guardedCall(timeoutMillis) { store.places(miffId) }) {
            is Guarded.Value -> {
                val fresh = read.value.map(LocalPlace::of)
                // The place the person was named in, moved or resized (or its type changed) by its creator: the presence cannot be confirmed. A moved
                // place is another zone (its key holds its geometry): the old one goes away, the new one is armed, and arrives as a new arrival if inside.
                val old = base.places.associateBy { it.id }
                val moved =
                    fresh
                        .filter { n ->
                            old[n.id]?.let { o ->
                                o.toFact()?.sameGeometry(n.toFact() ?: return@let false) ==
                                    false
                            } ==
                                true
                        }.map { it.id }
                val named = base.derived()?.placeId
                val updated = base.copyWith(places = fresh, readAtMillis = now)
                if (named != null && named in moved) {
                    journal.record(JournalKind.PRESENCE_UNKNOWN, named)
                    updated.copyWith(
                        derived = DerivedPresence(PresenceValue.UNKNOWN, null),
                        pending = PendingStatus.of(PresenceMachine.unknown(now)),
                    ) to true
                } else {
                    updated to true
                }
            }

            is Guarded.Failed -> {
                base to false
            }
        }
    }

    // --- Presence that can no longer be confirmed, restart --------------------------------------------------------------------------------

    private fun zonesOf(
        state: LocalState,
        miffId: String,
    ): List<ZoneRef> =
        state.miffs[miffId]
            ?.places
            ?.mapNotNull {
                it.toFact()?.let { f ->
                    ZoneRef(f.key, f.id, f.type)
                }
            }.orEmpty()

    private fun allZones(state: LocalState): List<TrackedZone> =
        ZoneSelection.merge(
            state.miffs.flatMap { (miffId, miff) ->
                miff.places.mapNotNull { p -> p.toFact()?.let { ZoneSelection.MiffPlace(miffId, it) } }
            },
        )

    /**
     * A place deleted or changed while the person was in it, a restart of the phone: the presence that was written may no longer be true, and
     * nothing says what is. It becomes `unknown` (never a guess, ticket 6.6); where the phone has just restarted and the person is still in a
     * zone, the arrival is written again (an arrival is what "already inside" means, DET3). Nothing here invents an `out`.
     */
    private suspend fun reconcile(
        state: LocalState,
        reason: Reason,
        fix: Fix?,
        now: Long,
    ): LocalState {
        val zones = allZones(state)
        val byKey = zones.associateBy { it.key }
        var inside = state.inside.filterKeys { it in byKey }
        if (reason == Reason.REARM) {
            // The geofences of the system are gone after a restart and no departure was reported meanwhile: the local state is rebuilt from a RECENT position.
            inside =
                if (fix != null) {
                    zones.filter { ZoneSelection.contains(it, fix.lat, fix.lng) }.associate {
                        it.key to
                            (state.inside[it.key] ?: now)
                    }
                } else {
                    emptyMap()
                }
        }
        val miffs = state.miffs.toMutableMap()
        for ((miffId, miff) in state.miffs) {
            val last = miff.derived() ?: continue
            val miffZones = zonesOf(state, miffId)
            val derived = PresenceMachine.derive(miffZones, inside)
            val named = last.placeId
            val zoneGone = named != null && miffZones.none { it.placeId == named }
            val fact: StatusFact? =
                when {
                    // The place was deleted or changed.
                    last.presence != PresenceValue.OUT && last.presence != PresenceValue.UNKNOWN && zoneGone -> {
                        PresenceMachine.unknown(now)
                    }

                    reason == Reason.REARM && fix != null && derived != last &&
                        derived.presence != PresenceValue.OUT -> {
                        StatusFact(derived.presence, derived.placeId, now, derived.presence == PresenceValue.IN_TRUSTED)
                    }

                    reason == Reason.REARM && fix != null && derived.presence == PresenceValue.OUT &&
                        (last.presence == PresenceValue.IN_TRUSTED || last.presence == PresenceValue.IN_RISK) -> {
                        PresenceMachine.unknown(now)
                    }

                    else -> {
                        null
                    }
                }
            if (fact != null) {
                journal.record(
                    if (fact.presence ==
                        PresenceValue.UNKNOWN
                    ) {
                        JournalKind.PRESENCE_UNKNOWN
                    } else {
                        JournalKind.TRANSITION_ENTER
                    },
                    fact.placeId,
                )
                miffs[miffId] =
                    miff.copyWith(
                        derived = DerivedPresence(fact.presence, fact.placeId),
                        pending = PendingStatus.of(fact),
                    )
            }
        }
        return state.copyWith(miffs = miffs, inside = inside)
    }

    // --- The 20 zones ---------------------------------------------------------------------------------------------------------------------

    /**
     * Registers the zones with the system. [full] = the WHOLE wanted set is sent again (idempotent: a zone with the same name is replaced), because the
     * list kept on the phone can be wrong about what the system holds; otherwise only the difference (an entry in a zone must not re-fire the arrival of
     * the others). The same for the request of the reports of a move of more than 250 m.
     */
    private suspend fun armZones(
        state: LocalState,
        fix: Fix?,
        full: Boolean,
    ): LocalState {
        val zones = allZones(state)
        val desired: List<TrackedZone> =
            when {
                zones.size <= ZoneSelection.MAX_WATCHED -> zones

                // The choice by the last position known, however old (it only orders the zones; a fresh one corrects it later): better than none.
                fix != null -> ZoneSelection.select(fix.lat, fix.lng, zones, state.inside.keys)

                else -> zones.take(ZoneSelection.MAX_WATCHED)
            }
        val desiredKeys = desired.map { it.key }.toSet()
        val toRemove = state.armed - desiredKeys
        val toAdd = if (full) desired else desired.filter { it.key !in state.armed }
        var next = state
        if (toRemove.isNotEmpty() || toAdd.isNotEmpty()) {
            if (geofences.update(toAdd, toRemove.toList())) {
                next = next.copyWith(armed = desiredKeys)
                journal.record(JournalKind.ARMED)
            }
        }
        if (full || !next.updatesArmed) {
            next = next.copyWith(updatesArmed = movement.start())
        }
        return next
    }

    // --- Events -----------------------------------------------------------------------------------------------------------------------------------

    private suspend fun doTransition(
        kind: TransitionKind,
        zoneKey: String,
        atMillis: Long,
    ) {
        val uid =
            env.uid() ?: run {
                doStop()
                return
            }
        val state = stateFor(uid)
        if (state == null || state.miffs.isEmpty() || !env.permissions().locationComplete) {
            // The consent or the access is gone: the zones are removed now and the copy (position, statuses waiting) erased; nothing is written.
            journal.record(JournalKind.NOT_ELIGIBLE)
            if (state == null) {
                runCatchingCancellable { geofences.removeAll() }
                runCatchingCancellable { movement.stop() }
            } else {
                local.save(disarm(state))
            }
            return
        }
        val placeId = ZoneKey.placeId(zoneKey)
        val now = env.nowMillis()
        journal.record(
            if (kind ==
                TransitionKind.ENTER
            ) {
                JournalKind.TRANSITION_ENTER
            } else {
                JournalKind.TRANSITION_EXIT
            },
            placeId,
            env.batteryPct(),
        )
        val inside =
            when (kind) {
                TransitionKind.ENTER -> {
                    if (zoneKey in
                        state.inside
                    ) {
                        state.inside
                    } else {
                        state.inside + (zoneKey to minOf(atMillis, now))
                    }
                }

                TransitionKind.EXIT -> {
                    state.inside - zoneKey
                }
            }
        val at = EventTime.forWrite(atMillis, now)
        val miffs = state.miffs.toMutableMap()
        for ((miffId, miff) in state.miffs) {
            val result =
                PresenceMachine.onTransition(kind, zoneKey, at ?: now, zonesOf(state, miffId), inside, miff.derived())
                    ?: continue
            val (derived, fact) = result
            if (at == null) {
                // Too old for the rules to accept: given up (no loop), but the local state moves on.
                journal.record(JournalKind.STATUS_GIVEN_UP, derived.placeId)
                miffs[miffId] = miff.copyWith(derived = derived)
            } else {
                // The status is kept BEFORE it is sent: a process killed meanwhile does not lose it (it is the last one that counts).
                miffs[miffId] = miff.copyWith(derived = derived, pending = PendingStatus.of(fact))
            }
        }
        var next = state.copyWith(miffs = miffs, inside = inside)
        local.save(next)
        next = flushPending(uid, next, WriteGate(recheck = false))
        local.save(next)
        updateNetworkWake(next)
        // "À chaque entrée détectée dans une zone, la liste est recalculée" (ticket 6.5), and the position is read again.
        if (kind == TransitionKind.ENTER) doRefresh(Reason.ZONE_ENTERED, null)
    }

    // --- Writes ----------------------------------------------------------------------------------------------------------------------------

    /** After a refusal of the server the Miff is looked at again soon (not at every wake-up: a phone clock that is ahead would loop on it). */
    private fun recheckSoon(
        state: LocalState,
        now: Long,
    ): LocalState =
        state.copyWith(
            checkedAtMillis =
                now - TrackingLimits.PLACES_REREAD_MILLIS + TrackingLimits.RECHECK_AFTER_REFUSAL_MILLIS,
        )

    /** Writes what waits, one independent write per Miff (a refusal in one Miff never stops the others), the Miffs served in turn (review I5). */
    private suspend fun flushPending(
        uid: String,
        start: LocalState,
        gate: WriteGate,
    ): LocalState {
        var state = start
        var served: String? = null
        for (miffId in rotated(start.miffs.keys, start.statusCursor)) {
            val miff = state.miffs[miffId] ?: continue
            val pending = miff.pending ?: continue
            val fact = pending.toFact()
            val now = env.nowMillis()
            val at = EventTime.forWrite(pending.atMillis, now)
            if (fact == null || at == null) {
                journal.record(JournalKind.STATUS_GIVEN_UP, pending.placeId)
                state = state.withMiff(miffId) { it.copyWith(pending = null) }
                continue
            }
            val day = WriteBreaker.dayKey(now)
            if (!WriteBreaker.canWriteStatus(state.breaker?.toState(), day)) {
                // The breaker keeps the LAST state waiting; it goes after midnight if it is still recent (the rules accept 24 h). Until then the
                // loved ones keep seeing the previous state, with no sign that it is old: a limit for the PM to decide on (README, PR).
                journal.record(JournalKind.BREAKER_STATUS, pending.placeId)
                continue
            }
            if (!env.isOnline()) {
                journal.record(JournalKind.STATUS_WAITING, pending.placeId)
                continue
            }
            // SESS-2: no identifier of session = nothing is written (replaced, or the server could not be asked): the status keeps waiting.
            val deviceId = gate.deviceId()
            if (deviceId == null) {
                journal.record(JournalKind.STATUS_WAITING, pending.placeId)
                continue
            }
            served = miffId
            val access = accessNow()
            // R-LIEU-1 (deployed): the update leaves `presenceAt` / `lastUpdateAt` alone unless the event changes them (the stored `lastUpdateAt` is accepted whatever its
            // age), so a refusal is never answered by writing the time of the event into `lastUpdateAt`.
            val outcome = writeStatus(uid, miffId, fact, at, access, deviceId)
            if (outcome is Guarded.Failed && outcome.failure == MiffFailure.REFUSED) gate.refused()
            state =
                when (outcome) {
                    is Guarded.Value -> {
                        journal.record(JournalKind.STATUS_WRITTEN, pending.placeId, env.batteryPct())
                        state
                            .copyWith(breaker = WriteBreaker.recordStatus(state.breaker?.toState(), day))
                            .withMiff(miffId) { it.copyWith(pending = null, access = access.value) }
                    }

                    is Guarded.Failed -> {
                        when (outcome.failure) {
                            MiffFailure.OFFLINE -> {
                                journal.record(JournalKind.STATUS_WAITING, pending.placeId)
                                state
                            }

                            MiffFailure.REFUSED -> {
                                // No more sharing, or no consent of the right version, or a clock too far ahead, according to the server: the Miff is looked at again soon.
                                journal.record(JournalKind.STATUS_REFUSED, pending.placeId)
                                recheckSoon(state, now).withMiff(miffId) { it.copyWith(pending = null) }
                            }

                            else -> {
                                journal.record(JournalKind.STATUS_GIVEN_UP, pending.placeId)
                                state.withMiff(miffId) { it.copyWith(pending = null) }
                            }
                        }
                    }
                }
        }
        return state.copyWith(statusCursor = served ?: state.statusCursor)
    }

    private suspend fun writeStatus(
        uid: String,
        miffId: String,
        fact: StatusFact,
        at: Long,
        access: LocationAccess,
        deviceId: String,
    ): Guarded<Unit> =
        guardedCall(timeoutMillis) {
            val battery = env.batteryPct()
            store.commitStatus(
                update = TrackingWrites.statusUpdate(miffId, uid, fact, at, battery, access, deviceId),
                // Only used when the status was deleted meanwhile: a complete first status, with recent times.
                create = TrackingWrites.statusCreate(miffId, uid, fact, at, battery, access, deviceId),
            )
        }

    /**
     * The access to the location changed (the permission was withdrawn, the location of the phone switched off): written once per change, with the
     * battery; never the presence (ticket 6.7). The update sends neither `presenceAt` nor `lastUpdateAt`, so (rule R-LIEU-1, deployed) it passes even for someone
     * who stayed more than 24 h in the same place. Whatever the answer, it is not tried again until the access changes again (no loop).
     */
    private suspend fun syncAccess(
        uid: String,
        start: LocalState,
        now: Long,
        gate: WriteGate,
    ): LocalState {
        val access = accessNow()
        var state = start
        for (miffId in rotated(start.miffs.keys, start.statusCursor)) {
            val miff = state.miffs[miffId] ?: continue
            if (miff.access == null || miff.access == access.value) {
                if (miff.access == null) state = state.withMiff(miffId) { it.copyWith(access = access.value) }
                continue
            }
            val day = WriteBreaker.dayKey(now)
            if (!WriteBreaker.canWriteStatus(state.breaker?.toState(), day) || !env.isOnline()) continue
            // SESS-2: without the identifier of the session nothing is written, and the change stays to be told (the access is not marked as done).
            val deviceId = gate.deviceId() ?: continue
            val outcome =
                guardedCall(timeoutMillis) {
                    store.commit(listOf(TrackingWrites.accessUpdate(miffId, uid, env.batteryPct(), access, deviceId)))
                }
            if (outcome is Guarded.Failed && outcome.failure == MiffFailure.REFUSED) gate.refused()
            state =
                when (outcome) {
                    is Guarded.Value -> {
                        state
                            .copyWith(breaker = WriteBreaker.recordStatus(state.breaker?.toState(), day))
                            .withMiff(miffId) { it.copyWith(access = access.value) }
                    }

                    is Guarded.Failed -> {
                        // Whatever the answer, it is not tried again until the access changes again: no loop.
                        state.withMiff(miffId) { it.copyWith(access = access.value) }
                    }
                }
        }
        return state
    }

    /**
     * The LAST known position, to every Miff that qualifies (sharing + consent of version 3: all the Miffs of the local list), one write per Miff,
     * ALONE (never with places or any other document), with the guard of frequency and the breaker (criterion POS8). No history: it replaces the previous
     * one. The Miffs are served in turn (review I5).
     */
    private suspend fun publishPosition(
        uid: String,
        start: LocalState,
        fix: Fix?,
        now: Long,
        gate: WriteGate,
    ): LocalState {
        if (fix == null) return start
        val measuredAt = EventTime.forWrite(fix.measuredAtMillis, now) ?: return start
        var state = start
        var served: String? = null
        for (miffId in rotated(start.miffs.keys, start.positionCursor)) {
            val miff = state.miffs[miffId] ?: continue
            val last = miff.written()
            if (last != null && measuredAt <= last.atMillis) continue
            if (!PositionThrottle.shouldWrite(last, fix.lat, fix.lng, measuredAt)) {
                journal.record(JournalKind.POSITION_SKIPPED_GUARD)
                continue
            }
            val day = WriteBreaker.dayKey(now)
            if (!WriteBreaker.canWritePosition(state.breaker?.toState(), day)) {
                journal.record(JournalKind.BREAKER_POSITION)
                continue
            }
            if (!env.isOnline()) continue
            // SESS-2: a position never leaves a phone that does not hold the session (it would tell the loved ones where the OLD phone is).
            val deviceId = gate.deviceId() ?: continue
            served = miffId
            val outcome =
                guardedCall(timeoutMillis) {
                    store.commit(listOf(TrackingWrites.position(miffId, uid, fix.lat, fix.lng, measuredAt, deviceId)))
                }
            if (outcome is Guarded.Failed && outcome.failure == MiffFailure.REFUSED) gate.refused()
            state =
                when (outcome) {
                    is Guarded.Value -> {
                        journal.record(JournalKind.POSITION_WRITTEN, batteryPct = env.batteryPct())
                        state
                            .copyWith(breaker = WriteBreaker.recordPosition(state.breaker?.toState(), day))
                            .withMiff(miffId) { it.copyWith(written = WrittenPosition(fix.lat, fix.lng, measuredAt)) }
                    }

                    is Guarded.Failed -> {
                        // Refused: the consent or the sharing is not what the phone thought (or the clock is far ahead): the Miff is looked at again soon.
                        if (outcome.failure == MiffFailure.REFUSED) recheckSoon(state, now) else state
                    }
                }
        }
        return state.copyWith(positionCursor = served ?: state.positionCursor)
    }

    private fun updateNetworkWake(state: LocalState) {
        if (state.miffs.values.any { it.pending != null }) networkWake.watch() else networkWake.stop()
    }

    private fun LocalState.withMiff(
        miffId: String,
        change: (LocalMiff) -> LocalMiff,
    ): LocalState {
        val current = miffs[miffId] ?: return this
        return copyWith(miffs = miffs + (miffId to change(current)))
    }

    private companion object {
        const val NEVER = Int.MIN_VALUE

        /** A person who shares nowhere is looked at again at most this often at the opening of the app (2 reads of the server per Miff). */
        const val RETRY_WHEN_EMPTY_MILLIS = 2L * 60L * 1000L
    }
}
