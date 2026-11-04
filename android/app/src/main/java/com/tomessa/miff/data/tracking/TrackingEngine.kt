package com.tomessa.miff.data.tracking

import com.tomessa.miff.data.miff.Guarded
import com.tomessa.miff.data.miff.guardedCall
import com.tomessa.miff.data.session.SessionTeardown
import com.tomessa.miff.domain.miff.LocationAccess
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.MiffSharingConsent
import com.tomessa.miff.domain.tracking.BreakerState
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
import com.tomessa.miff.domain.tracking.ZoneRef
import com.tomessa.miff.domain.tracking.ZoneSelection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
 * SECURITY / PRIVACY, for this file (criteria AUT7, POS1 to POS8, DET1 to DET4, STA1 to STA4, VIE1 to VIE3, HOR1):
 * - NOTHING is watched, read or written unless the person is signed in, has "Toujours" + the precise location, AND shares with a Miff under the
 *   consent of version 3. Each condition is checked at EVERY entry (a withdrawal is noticed at the next wake-up and everything stops).
 * - A position is read only here, through [PositionSource]; it is kept in the private local copy (to choose the 20 nearest places), written to
 *   Firestore ONLY as the last position of the Miffs that qualify (one write per Miff, alone), and never logged, never sent as an event, never
 *   copied anywhere else. The geofence receiver never reads the location that comes with an event (see TrackingReceiver).
 * - The status carries facts only; the colour is never written. The time written is the real time of the event.
 * - Everything is erased by [stop] (sign-out, deletion of the account, session lost) and, Miff by Miff, when the sharing or the access is lost.
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
    private val journal: DetectionJournal = NoDetectionJournal,
    private val timeoutMillis: Long = 15_000L,
) : SessionTeardown {
    /** Why the engine is asked to look again (ticket 6.5: opening, going to the background, a move of more than 250 m, an entry in a zone). */
    enum class Reason { OPENED, BACKGROUNDED, MOVED, ZONE_ENTERED, REARM, SHARING_CHANGED }

    private val mutex = Mutex()

    /** The app came to the front, went to the back, the provider reported a move, the phone restarted, the sharing changed. */
    suspend fun refresh(
        reason: Reason,
        suppliedFix: Fix? = null,
    ) {
        mutex.withLock { doRefresh(reason, suppliedFix) }
    }

    /** The geofencing says: the person entered or left a zone. The location that comes with the event is NEVER read (the receiver does not). */
    suspend fun onTransition(
        kind: TransitionKind,
        placeId: String,
        atMillis: Long,
    ) {
        mutex.withLock { doTransition(kind, placeId, atMillis) }
    }

    /** The network is back: what waits to be written is written. Reads no position. */
    suspend fun onNetworkBack() {
        mutex.withLock {
            val uid = env.uid() ?: return@withLock doStop()
            val state = stateFor(uid) ?: return@withLock networkWake.stop()
            journal.record(JournalKind.WAKE_NETWORK)
            val flushed = flushPending(uid, state)
            local.save(flushed)
            updateNetworkWake(flushed)
        }
    }

    /** Sign-out, deletion of the account, session lost (a [SessionTeardown]): everything stops and is erased. */
    override suspend fun stop() {
        mutex.withLock { doStop() }
    }

    // ---------------------------------------------------------------------------------------------------------------------------------

    private suspend fun doStop() {
        runCatchingCancellable { geofences.removeAll() }
        runCatchingCancellable { movement.stop() }
        networkWake.stop()
        local.clear()
        journal.clear()
    }

    private suspend fun disarm(state: LocalState): LocalState {
        runCatchingCancellable { geofences.removeAll() }
        runCatchingCancellable { movement.stop() }
        journal.record(JournalKind.DISARMED)
        // The position and where the person is are erased as soon as nothing uses them (privacy); the places of the Miffs are kept (cheap to keep).
        return state.copyWith(inside = emptyMap(), armed = emptySet(), fix = null, updatesArmed = false)
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
        // After a restart of the phone or an update of the app the system has forgotten the zones and the reports: they are all armed again.
        if (reason == Reason.REARM) state = state.copyWith(armed = emptySet(), updatesArmed = false)
        val permissions = env.permissions()

        val synced = syncMiffs(uid, state, reason, now)
        state = synced.state

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
            state = syncAccess(uid, state, now)
            local.save(state)
            return
        }

        val fix = chooseFix(state, reason, suppliedFix, now)
        state = fix?.let { state.copyWith(fix = it) } ?: state
        if (fix != null) journal.record(JournalKind.POSITION_READ, batteryPct = env.batteryPct())

        state = reconcile(state, reason, fix, now, synced.changedPlaceIds)
        state = armZones(state, fix, synced.changedPlaceIds)
        local.save(state)

        state = flushPending(uid, state)
        state = publishPosition(uid, state, fix, now)
        state = syncAccess(uid, state, now)
        local.save(state)
        updateNetworkWake(state)
    }

    /** A position for this occasion: the one the provider reported, else a fresh one, else (for an entry or a restart) the last one kept. */
    private suspend fun chooseFix(
        state: LocalState,
        reason: Reason,
        supplied: Fix?,
        now: Long,
    ): Fix? {
        if (supplied != null) return supplied
        val fresh =
            try {
                position.current()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        if (fresh != null) return fresh
        journal.record(JournalKind.POSITION_UNAVAILABLE)
        val kept = state.fix()
        return if (kept != null &&
            (reason == Reason.ZONE_ENTERED || now - kept.measuredAtMillis <= TrackingLimits.FIX_MAX_AGE_MILLIS)
        ) {
            kept
        } else {
            null
        }
    }

    // --- Which Miffs, which places -------------------------------------------------------------------------------------------------------

    /** The result of looking at the server again: the new local copy, and the places whose position, radius or type changed (they are armed again). */
    private class Synced(
        val state: LocalState,
        val changedPlaceIds: Set<String> = emptySet(),
    )

    private suspend fun syncMiffs(
        uid: String,
        state: LocalState,
        reason: Reason,
        now: Long,
    ): Synced {
        val due =
            reason == Reason.SHARING_CHANGED ||
                reason == Reason.REARM ||
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
        val changed = mutableSetOf<String>()
        var complete = true
        for (miffId in refs) {
            when (val consent = guardedCall(timeoutMillis) { store.consent(miffId, uid) }) {
                is Guarded.Value -> {
                    val card = consent.value
                    val eligible = card != null && card.enabled && MiffSharingConsent.coversPosition(card.version)
                    if (eligible) {
                        miffs[miffId] = readPlacesOf(miffId, state.miffs[miffId], now, changed)
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
        return Synced(
            state.copyWith(miffs = miffs, checkedAtMillis = if (complete) now else state.checkedAtMillis),
            changed,
        )
    }

    private suspend fun readPlacesOf(
        miffId: String,
        previous: LocalMiff?,
        now: Long,
        changed: MutableSet<String>,
    ): LocalMiff {
        val needed =
            previous == null || now - previous.readAtMillis > TrackingLimits.PLACES_REREAD_MILLIS ||
                previous.readAtMillis == 0L
        val base = previous ?: LocalMiff()
        if (!needed && previous != null) return previous
        return when (val read = guardedCall(timeoutMillis) { store.places(miffId) }) {
            is Guarded.Value -> {
                val fresh = read.value.map(LocalPlace::of)
                // A place the person was named in that its creator moved or resized (or changed the type of): the presence cannot be confirmed.
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
                changed += moved
                val named = base.derived()?.placeId
                val unsure = named != null && named in moved
                val updated = base.copyWith(places = fresh, readAtMillis = now)
                if (unsure) {
                    journal.record(JournalKind.PRESENCE_UNKNOWN, named)
                    updated.copyWith(
                        derived = DerivedPresence(PresenceValue.UNKNOWN, null),
                        pending = PendingStatus.of(PresenceMachine.unknown(now)),
                    )
                } else {
                    updated
                }
            }

            is Guarded.Failed -> {
                base
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
                    ZoneRef(f.id, f.type)
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
        changedPlaceIds: Set<String>,
    ): LocalState {
        val zones = allZones(state)
        val byId = zones.associateBy { it.placeId }
        var inside = state.inside.filterKeys { it in byId && it !in changedPlaceIds }
        if (reason == Reason.REARM) {
            // The geofences of the system are gone after a restart and no departure was reported meanwhile: the local state is rebuilt from the position.
            inside =
                if (fix != null) {
                    zones.filter { ZoneSelection.contains(it, fix.lat, fix.lng) }.associate {
                        it.placeId to
                            (state.inside[it.placeId] ?: now)
                    }
                } else {
                    emptyMap()
                }
        }
        val miffs = state.miffs.toMutableMap()
        for ((miffId, miff) in state.miffs) {
            val last = miff.derived() ?: continue
            val derived = PresenceMachine.derive(zonesOf(state, miffId), inside)
            val named = last.placeId
            val zoneGone = named != null && zonesOf(state, miffId).none { it.placeId == named }
            val fact: StatusFact? =
                when {
                    // The place was deleted or changed.
                    last.presence != PresenceValue.OUT && last.presence != PresenceValue.UNKNOWN && zoneGone -> {
                        PresenceMachine.unknown(now)
                    }

                    reason == Reason.REARM && fix != null && derived != last &&
                        derived.presence != PresenceValue.OUT -> {
                        StatusFact(
                            derived.presence,
                            derived.placeId,
                            now,
                            derived.presence == PresenceValue.IN_TRUSTED,
                        )
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

    private suspend fun armZones(
        state: LocalState,
        fix: Fix?,
        changedPlaceIds: Set<String>,
    ): LocalState {
        val zones = allZones(state)
        val desired: List<TrackedZone> =
            when {
                zones.size <= ZoneSelection.MAX_WATCHED -> zones

                fix != null -> ZoneSelection.select(fix.lat, fix.lng, zones, state.inside.keys)

                // More than 20 zones and no position: what is armed stays (nothing is chosen blind).
                else -> zones.filter { it.placeId in state.armed }
            }
        val desiredIds = desired.map { it.placeId }.toSet()
        val stale = state.armed.intersect(changedPlaceIds)
        val toRemove = (state.armed - desiredIds) + stale
        val toAdd = desired.filter { it.placeId !in state.armed || it.placeId in stale }
        var next = state
        if (toRemove.isNotEmpty() || toAdd.isNotEmpty()) {
            val done = geofences.update(toAdd, toRemove.toList())
            if (done) {
                next = next.copyWith(armed = desiredIds)
                journal.record(JournalKind.ARMED)
            }
        }
        if (!next.updatesArmed) {
            if (movement.start()) next = next.copyWith(updatesArmed = true)
        }
        return next
    }

    // --- Events -----------------------------------------------------------------------------------------------------------------------------------

    private suspend fun doTransition(
        kind: TransitionKind,
        placeId: String,
        atMillis: Long,
    ) {
        val uid =
            env.uid() ?: run {
                doStop()
                return
            }
        val state = stateFor(uid)
        if (state == null || state.miffs.isEmpty() || !env.permissions().locationComplete) {
            // The consent or the access is gone: the zones are removed now (nobody is told anything, nothing is written).
            journal.record(JournalKind.NOT_ELIGIBLE)
            runCatchingCancellable { geofences.removeAll() }
            runCatchingCancellable { movement.stop() }
            return
        }
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
                    if (placeId in
                        state.inside
                    ) {
                        state.inside
                    } else {
                        state.inside + (placeId to minOf(atMillis, now))
                    }
                }

                TransitionKind.EXIT -> {
                    state.inside - placeId
                }
            }
        val at = EventTime.forWrite(atMillis, now)
        val miffs = state.miffs.toMutableMap()
        for ((miffId, miff) in state.miffs) {
            val result =
                PresenceMachine.onTransition(kind, placeId, at ?: now, zonesOf(state, miffId), inside, miff.derived())
                    ?: continue
            val (derived, fact) = result
            if (at == null) {
                // Too old for the rules to accept: given up (no loop), but the local state moves on.
                journal.record(JournalKind.STATUS_GIVEN_UP, placeId)
                miffs[miffId] = miff.copyWith(derived = derived)
            } else {
                // The status is kept BEFORE it is sent: a process killed meanwhile does not lose it (it is the last one that counts).
                miffs[miffId] = miff.copyWith(derived = derived, pending = PendingStatus.of(fact))
            }
        }
        var next = state.copyWith(miffs = miffs, inside = inside)
        local.save(next)
        next = flushPending(uid, next)
        local.save(next)
        updateNetworkWake(next)
        // "À chaque entrée détectée dans une zone, la liste est recalculée" (ticket 6.5), and the position is read again.
        if (kind == TransitionKind.ENTER) doRefresh(Reason.ZONE_ENTERED, null)
    }

    // --- Writes ----------------------------------------------------------------------------------------------------------------------------

    /** Writes what waits, one independent write per Miff (a refusal in one Miff never stops the others). */
    private suspend fun flushPending(
        uid: String,
        start: LocalState,
    ): LocalState {
        var state = start
        for ((miffId, miff) in start.miffs) {
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
                // The breaker keeps the LAST state waiting; it goes after midnight if it is still recent (the rules accept 24 h).
                journal.record(JournalKind.BREAKER_STATUS, pending.placeId)
                continue
            }
            if (!env.isOnline()) {
                journal.record(JournalKind.STATUS_WAITING, pending.placeId)
                continue
            }
            val access = accessNow()
            var outcome = writeStatus(uid, miffId, fact, at, access, alsoLastUpdate = fact.setLastUpdate)
            if (outcome is Guarded.Failed && outcome.failure == MiffFailure.REFUSED && !fact.setLastUpdate) {
                // `lastUpdateAt` kept from long ago is refused by the rules (more than 24 h): it is moved to the time of this event (ticket 6.6).
                outcome = writeStatus(uid, miffId, fact, at, access, alsoLastUpdate = true)
            }
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
                                // No more sharing, or no consent of the right version, according to the server: the Miff is looked at again.
                                journal.record(JournalKind.STATUS_REFUSED, pending.placeId)
                                state.copyWith(checkedAtMillis = 0L).withMiff(miffId) { it.copyWith(pending = null) }
                            }

                            else -> {
                                journal.record(JournalKind.STATUS_GIVEN_UP, pending.placeId)
                                state.withMiff(miffId) { it.copyWith(pending = null) }
                            }
                        }
                    }
                }
        }
        return state
    }

    private suspend fun writeStatus(
        uid: String,
        miffId: String,
        fact: StatusFact,
        at: Long,
        access: LocationAccess,
        alsoLastUpdate: Boolean,
    ): Guarded<Unit> =
        guardedCall(timeoutMillis) {
            store.commit(
                listOf(TrackingWrites.statusUpdate(miffId, uid, fact, at, env.batteryPct(), access, alsoLastUpdate)),
            )
        }

    /**
     * The access to the location changed (the permission was withdrawn, the location of the phone switched off): written once per change, with the
     * battery; never the presence (ticket 6.7). Skipped quietly when the rules refuse it (a status older than 24 h): the next event rewrites it.
     */
    private suspend fun syncAccess(
        uid: String,
        start: LocalState,
        now: Long,
    ): LocalState {
        val access = accessNow()
        var state = start
        for ((miffId, miff) in start.miffs) {
            if (miff.access == null || miff.access == access.value) {
                if (miff.access == null) state = state.withMiff(miffId) { it.copyWith(access = access.value) }
                continue
            }
            val day = WriteBreaker.dayKey(now)
            if (!WriteBreaker.canWriteStatus(state.breaker?.toState(), day) || !env.isOnline()) continue
            val outcome =
                guardedCall(timeoutMillis) {
                    store.commit(listOf(TrackingWrites.accessUpdate(miffId, uid, env.batteryPct(), access)))
                }
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
     * ALONE (never with places or any other document), with the guard of frequency and the breaker (criterion POS8). No history: it replaces the previous one.
     */
    private suspend fun publishPosition(
        uid: String,
        start: LocalState,
        fix: Fix?,
        now: Long,
    ): LocalState {
        if (fix == null) return start
        val measuredAt = EventTime.forWrite(fix.measuredAtMillis, now) ?: return start
        var state = start
        for ((miffId, miff) in start.miffs) {
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
            val outcome =
                guardedCall(timeoutMillis) {
                    store.commit(listOf(TrackingWrites.position(miffId, uid, fix.lat, fix.lng, measuredAt)))
                }
            state =
                when (outcome) {
                    is Guarded.Value -> {
                        journal.record(JournalKind.POSITION_WRITTEN, batteryPct = env.batteryPct())
                        state
                            .copyWith(breaker = WriteBreaker.recordPosition(state.breaker?.toState(), day))
                            .withMiff(miffId) {
                                it.copyWith(
                                    written =
                                        com.tomessa.miff.domain.tracking.WrittenPosition(
                                            fix.lat,
                                            fix.lng,
                                            measuredAt,
                                        ),
                                )
                            }
                    }

                    is Guarded.Failed -> {
                        // Refused: the consent or the sharing is not what the phone thought: the Miff is looked at again at the next wake-up.
                        if (outcome.failure == MiffFailure.REFUSED) state.copyWith(checkedAtMillis = 0L) else state
                    }
                }
        }
        return state
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
        /** A person who shares nowhere is looked at again at most this often at the opening of the app (2 reads of the server per Miff). */
        const val RETRY_WHEN_EMPTY_MILLIS = 2L * 60L * 1000L
    }
}
