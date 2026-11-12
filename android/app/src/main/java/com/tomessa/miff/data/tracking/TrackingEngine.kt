package com.tomessa.miff.data.tracking

import com.tomessa.miff.data.miff.Guarded
import com.tomessa.miff.data.miff.guardedCall
import com.tomessa.miff.data.session.ActiveDevice
import com.tomessa.miff.data.session.Clearance
import com.tomessa.miff.data.session.SessionTeardown
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.LocationAccess
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.MiffSharingConsent
import com.tomessa.miff.domain.tracking.BatteryRefresh
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
import com.tomessa.miff.domain.travel.TravelLimits
import com.tomessa.miff.domain.travel.TravelPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * - The battery (opening / leaving the app, see [refreshBattery]) is an UPDATE of the battery level alone: it never creates a status, reads no position, reads
 *   nothing from the server, and passes through the same gates as every other write (sharing under the consent of version 3, session, deletion, sign-out).
 * - MODE VOYAGE (docs/tickets/mode-voyage.md): while a trip is on ([LocalState.travel]) EVERY entry returns before it reads a position, arms a zone or writes a presence, a
 *   position or a battery level; the only writes are the ones that TELL the loved ones about the trip (and its end). The trip is written to the server FIRST and the tracking
 *   is paused AFTER, so that a refusal of the server never leaves the tracking stopped without a trip. The last position kept on the server and the copy kept on the phone are
 *   erased when the trip begins.
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
    /**
     * The deletion of the account is under way (its marker is on the DISK, written before the first document goes): nothing is armed, read, kept or
     * written, even by a receiver of the system that wakes a COLD process (the memory of [stop] is gone there). Another account's marker does not count.
     */
    private val suspended: () -> Boolean = { false },
    /** NOTIF-4A: told, locally and at once, when MY presence changes in a Miff (a departure, an arrival): the reminders follow without waiting for the Feed. */
    private val presenceListener: PresenceListener = PresenceListener.None,
    /** MODE VOYAGE: the alarm that ends a trip while the app is closed (none by default: tests). */
    private val travelAlarm: TravelAlarm = NoTravelAlarm,
) : SessionTeardown {
    /**
     * Why the engine is asked to look again (ticket 6.5: opening, going to the background, a move of more than 250 m, an entry in a zone).
     * [TRAVEL_ENDED]: the alarm of the end of a trip rang, or the clock / the time zone changed; nothing happens when no trip is on.
     */
    enum class Reason { OPENED, BACKGROUNDED, MOVED, ZONE_ENTERED, REARM, SHARING_CHANGED, NETWORK_BACK, TRAVEL_ENDED }

    private val mutex = Mutex()

    private val travelFlow by lazy { MutableStateFlow(storedTravel()) }

    /**
     * MODE VOYAGE: the trip in progress on this phone (null: none). A copy of what the local state keeps, for the Profile; it changes when a trip begins, its end is moved, or it
     * ends. Read from the local copy on first use ([reloadTravel] reads it again, e.g. when the Profile opens).
     */
    val travel: StateFlow<TravelPeriod?> get() = travelFlow

    private fun storedTravel(): TravelPeriod? {
        val uid = env.uid() ?: return null
        return local
            .load()
            ?.takeIf { it.uid == uid }
            ?.travel
            ?.period()
    }

    fun reloadTravel() {
        travelFlow.value = storedTravel()
    }

    private fun publishTravel(period: TravelPeriod?) {
        travelFlow.value = period
    }

    /** The generation of the session that was stopped (nothing is allowed to run in it any more), or [NEVER]. */
    @Volatile
    private var stoppedGeneration = NEVER

    private fun sessionEnded(): Boolean = stoppedGeneration == generation() || suspended()

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

        /** The session this pass belongs to: a pass that started before a sign-out must not write after it, even once the next session has begun. */
        private val born = generation()

        /** True once the session of this pass has ended (stop asked for, or a new session started): nothing may be written. */
        fun ended(): Boolean = stoppedGeneration == born || generation() != born || suspended()

        /** The identifier to write, or null: nothing may be written now (ended, replaced, or the server could not be asked). */
        suspend fun deviceId(): String? {
            if (ended()) return null
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
     * REPRISE DU PARTAGE d'une Miff (see [takeResumed]): the Miff is flagged, in the order of the engine's own work (under its lock), BEFORE the look at the Miffs that follows
     * ([refresh] with [Reason.SHARING_CHANGED]). Nothing is read or written here: only the identifier of the Miff is kept, in the private local copy.
     */
    suspend fun markResumed(miffId: String) {
        mutex.withLock {
            if (sessionEnded()) return@withLock
            val uid = env.uid() ?: return@withLock
            val state = stateFor(uid) ?: LocalState(uid)
            local.save(state.copyWith(resumedMiffs = (state.resumedMiffs ?: emptySet()) + miffId))
        }
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
            if (state.travel != null) return@withLock doTravelPass(uid, state, Reason.NETWORK_BACK)
            if (!env.permissions().locationComplete) {
                // The access was withdrawn while the status waited: nothing is written any more, and nothing is kept.
                local.save(disarm(state))
                networkWake.stop()
                return@withLock
            }
            // BATT-4: a complete look made less than 5 minutes ago is trusted (nothing is read again, the waiting status is NOT held back); otherwise the
            // look is made in full, as before. An incomplete look (no answer) is never counted: it is tried again at the next return of the network.
            val now = env.nowMillis()
            val synced =
                if (verifiedRecently(state, now)) {
                    Synced(state, verified = true)
                } else {
                    syncMiffs(uid, state, Reason.NETWORK_BACK, now)
                }
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
        // Marked BEFORE the wait for the lock: a pass that has not started will not (the sign-out does not wait for the pass in progress for more than a few
        // seconds, see SessionManager.stopEverything), and the erasing below still happens when that pass ends.
        stoppedGeneration = generation()
        mutex.withLock { doStop() }
    }

    // ---------------------------------------------------------------------------------------------------------------------------------

    private suspend fun doStop() {
        runCatchingCancellable { geofences.removeAll() }
        runCatchingCancellable { movement.stop() }
        networkWake.stop()
        travelAlarm.cancel()
        local.clear()
        publishTravel(null)
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
        if (state.travel != null) {
            // MODE VOYAGE: the status stays a trip, the position keeps being published.
            doTravelPass(uid, state, reason, suppliedFix)
            return
        }
        // The alarm of a trip that is already over (ended by hand), or a change of the clock with no trip: nothing to do.
        if (reason == Reason.TRAVEL_ENDED) return
        // A NEW phone for this account (no verified look at the server yet) first reads whether a trip is on, before it arms anything (VOY-1A, "appareil remplacé").
        val adoptionDue = state.checkedAtMillis == 0L
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

        val synced = syncMiffs(uid, state, reason, now)
        state = synced.state
        if (adoptionDue && synced.verified && state.miffs.isNotEmpty()) {
            // Could not tell (no answer): nothing is saved and nothing is armed this time; the next wake-up asks again (the trip must never be overwritten).
            val adopted = adoptTravel(uid, state, now) ?: return
            if (adopted.travel != null) {
                val paused = pauseStatuses(adopted)
                local.save(paused)
                publishTravel(paused.travel?.period())
                paused.travel?.let { tellTravelSet(uid, it.untilMillis) }
                doTravelPass(uid, paused, reason, suppliedFix)
                return
            }
        }

        // REPRISE DU PARTAGE: the Miffs resumed and now known are started again from nothing (the server deleted what the phone thought it had written).
        val taken = takeResumed(state, synced.verified)
        state = taken.state

        if (state.miffs.isEmpty()) {
            // Nobody to tell: nothing is watched, nothing is kept (the person shares nowhere with the consent of version 3). Only the flags of a Miff not yet seen (no complete look) stay.
            journal.record(JournalKind.NOT_ELIGIBLE)
            runCatchingCancellable { geofences.removeAll() }
            runCatchingCancellable { movement.stop() }
            networkWake.stop()
            local.save(LocalState(uid, checkedAtMillis = state.checkedAtMillis, resumedMiffs = state.resumedMiffs))
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
        if (taken.miffIds.isNotEmpty()) state = presenceAfterResume(state, taken.miffIds, fix, now)
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
     * True when the last COMPLETE look at the server is less than [TrackingLimits.NETWORK_REREAD_MIN_INTERVAL_MILLIS] old (BATT-4). Never true for a copy that
     * was never read (0), for the artificial date left by [recheckSoon] (a refusal: the look WANTED is not recent), or for a date in the future (clock changed).
     */
    private fun verifiedRecently(
        state: LocalState,
        now: Long,
    ): Boolean {
        if (state.checkedAtMillis == 0L) return false
        val age = now - state.checkedAtMillis
        return age in 0 until TrackingLimits.NETWORK_REREAD_MIN_INTERVAL_MILLIS
    }

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
                tellPresence(miffId, fact.presence, fact.presenceAtMillis, fact.setLastUpdate)
                // "Already inside at the arming" (DET3): an arrival is what "already there" means; a presence that was already in_risk gives no fact, so no second N6.
                tellDanger(state.uid, miffId, last, DerivedPresence(fact.presence, fact.placeId), fact.presenceAtMillis)
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

    // --- Sharing resumed -----------------------------------------------------------------------------------------------------------------

    /** The Miffs of a pass whose detection was started again from nothing, with the local copy that results. */
    private class Taken(
        val state: LocalState,
        val miffIds: List<String>,
    )

    /**
     * REPRISE DU PARTAGE (recette, 2026-11-11: the card stayed GREY after "stop then resume"). Stopping the sharing DELETES the status, the photo and the last position of the Miff on
     * the server, and resuming writes an `unknown` (grey) status. The engine only writes a status when the presence CHANGES ([PresenceMachine.onTransition] returns nothing when the
     * derived presence is the one it keeps), so nothing wrote it again before the next entry or exit: either the phone still kept the presence from before the stop, or it found the
     * Miff "as new" while the person is already in the place (no arrival to tell, the geofencing only reports a change). The last position also stayed "written" on the phone while the
     * server had deleted it.
     *
     * The Miffs flagged by [markResumed] and now known to the engine are therefore started again from nothing ([LocalMiff.afterResume]) and returned, and their flag is removed. A flagged Miff
     * that is not eligible yet keeps its flag for the pass in which it is, unless a COMPLETE look at the server said it is not eligible ([verified]): then the flag is dropped.
     */
    private fun takeResumed(
        state: LocalState,
        verified: Boolean,
    ): Taken {
        val flagged = state.resumedMiffs
        if (flagged.isNullOrEmpty()) return Taken(state, emptyList())
        val taken = flagged.sorted().filter { it in state.miffs }
        val remaining = if (verified) emptySet() else flagged - taken.toSet()
        val miffs = state.miffs + taken.associateWith { state.miffs.getValue(it).afterResume() }
        return Taken(state.copyWith(miffs = miffs, resumedMiffs = remaining.ifEmpty { null }), taken)
    }

    /**
     * MODE VOYAGE: a trip is on, so NO ordinary presence is ever worked out. The resumed Miffs are started again from nothing (their last position is published again), and the trip is
     * told again to them: the status that the resume wrote says `unknown` without the trip, so they leave the list of the Miffs that already know it ([flushTravel] writes it).
     */
    private fun takeResumedDuringTravel(state: LocalState): LocalState {
        val taken = takeResumed(state, verified = false)
        val travel = taken.state.travel
        if (travel == null || taken.miffIds.isEmpty()) return taken.state
        val ids = taken.miffIds.toSet()
        return taken.state.copyWith(
            travel =
                travel.copyWith(
                    startedIn = travel.startedIn - ids,
                    upToDateIn =
                        travel.upToDateIn - ids,
                ),
        )
    }

    /**
     * The presence of the resumed Miffs, worked out again "as if they had never been grey" from what the phone knows of the person NOW: the zones where it believes them (`inside`) and the
     * position of this pass (never older than 10 minutes, see [chooseFix]). In a trusted or danger zone: that presence, at the known time of the entry (else now). Outside every zone of the
     * Miff, WITH a position to say so: `out`, now. Outside a zone and WITHOUT a position: nothing is invented, the status stays grey until the next signal. The status is written by the
     * usual pass ([flushPending]) with all its conditions ("Toujours" + precise, consent of version 3, session, breaker). The zones the position shows the person in are kept in `inside`
     * (as the restart does, see [reconcile]) so that the next departure or the next arrival in another zone starts from the right place. N6 is NOT told (nobody entered a danger zone just now).
     */
    private fun presenceAfterResume(
        start: LocalState,
        miffIds: List<String>,
        fix: Fix?,
        now: Long,
    ): LocalState {
        var state = start
        for (miffId in miffIds) {
            val zones = zonesOf(state, miffId)
            if (zones.isEmpty()) continue
            var inside = state.inside
            if (fix != null) {
                val own = zones.map { it.key }.toSet()
                val seen =
                    allZones(state).filter {
                        it.key in own && it.key !in inside && ZoneSelection.contains(it, fix.lat, fix.lng)
                    }
                if (seen.isNotEmpty()) inside = inside + seen.associate { it.key to now }
            }
            val derived = PresenceMachine.derive(zones, inside)
            val fact: StatusFact =
                when (derived.presence) {
                    PresenceValue.IN_TRUSTED, PresenceValue.IN_RISK -> {
                        val entered = zones.firstOrNull { it.placeId == derived.placeId }?.let { inside[it.key] } ?: now
                        StatusFact(
                            presence = derived.presence,
                            placeId = derived.placeId,
                            presenceAtMillis = EventTime.forWrite(entered, now) ?: now,
                            setLastUpdate = derived.presence == PresenceValue.IN_TRUSTED,
                        )
                    }

                    PresenceValue.OUT -> {
                        if (fix == null) continue
                        StatusFact(PresenceValue.OUT, null, now, setLastUpdate = false)
                    }

                    else -> {
                        continue
                    }
                }
            tellPresence(miffId, fact.presence, fact.presenceAtMillis, fact.setLastUpdate)
            journal.record(JournalKind.STATUS_AFTER_RESUME, derived.placeId)
            state =
                state.copyWith(inside = inside).withMiff(miffId) {
                    it.copyWith(
                        derived = DerivedPresence(fact.presence, fact.placeId),
                        pending = PendingStatus.of(fact),
                    )
                }
        }
        return state
    }

    /** The notifications are told that my presence changed; whatever they do, it never reaches the detection (a failure here is swallowed, nothing is logged). */
    private fun tellPresence(
        miffId: String,
        presence: PresenceValue,
        atMillis: Long,
        setLastUpdate: Boolean,
    ) {
        try {
            presenceListener.onMyPresence(miffId, presence, atMillis, setLastUpdate)
        } catch (e: Exception) {
            // A reminder that cannot be planned must never stop the detection of the arrivals and departures.
        }
    }

    /** N6: told only for a REAL entry in a danger zone (the previous presence of this Miff was not in_risk). Swallowed like [tellPresence]. */
    private fun tellDanger(
        uid: String,
        miffId: String,
        previous: DerivedPresence?,
        now: DerivedPresence,
        atMillis: Long,
    ) {
        if (now.presence != PresenceValue.IN_RISK || previous?.presence == PresenceValue.IN_RISK) return
        val placeId = now.placeId ?: return
        try {
            presenceListener.onEnteredDanger(uid, miffId, placeId, atMillis)
        } catch (e: Exception) {
            // A notification that cannot be shown must never stop the detection.
        }
    }

    /** MODE VOYAGE (VOY-4A): my trip is on and ends at [untilMillis] (N7 and the cut of the reminders follow). Swallowed like [tellPresence]. */
    private fun tellTravelSet(
        uid: String,
        untilMillis: Long,
    ) {
        try {
            presenceListener.onTravelSet(uid, untilMillis)
        } catch (e: Exception) {
            // A notification that cannot be planned must never stop the trip.
        }
    }

    /** My trip is over: [early] = I turned it off before its end. Swallowed like [tellPresence]. */
    private fun tellTravelEnded(
        uid: String,
        early: Boolean,
    ) {
        try {
            presenceListener.onTravelEnded(uid, early)
        } catch (e: Exception) {
            // Same.
        }
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
        if (state?.travel != null) {
            // MODE VOYAGE: a zone still registered with the system speaks during a trip (it should not): it is removed and no status is written.
            runCatchingCancellable { geofences.removeAll() }
            return
        }
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
        val alreadyInside = kind == TransitionKind.ENTER && zoneKey in state.inside
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
            tellPresence(miffId, derived.presence, at ?: now, fact.setLastUpdate)
            // I ENTERED a danger zone (the previous presence of this Miff was not already in_risk): N6, once, before the status is even written. Entering a danger zone
            // while in a trusted one gives no result here (the trusted zone wins); leaving the trusted one while staying in the danger one does.
            // An event too old for the rules (no time to write) is not told: it is no longer the present.
            if (at != null) tellDanger(uid, miffId, miff.derived(), derived, at)
            if (at == null) {
                // Too old for the rules to accept: given up (no loop), but the local state moves on.
                journal.record(JournalKind.STATUS_GIVEN_UP, derived.placeId)
                miffs[miffId] = miff.copyWith(derived = derived)
            } else {
                // The status is kept BEFORE it is sent: a process killed meanwhile does not lose it (it is the last one that counts).
                miffs[miffId] = miff.copyWith(derived = derived, pending = PendingStatus.of(fact))
            }
        }
        // BATT-2 (as iOS does): an entry seen again for a zone the person is ALREADY in (the system reports it again when the zones are registered again at
        // each opening, or answers a request of state) is nothing new: no position read, no position write, no full pass. A real return is never here: it
        // follows a departure that took the key out of `inside`. An event that would change a status (the derived presence differs from the one kept: a
        // restart that left no status yet, a zone of another kind) is NOT "nothing new" and goes on as before. What the skipped pass also did (reading the
        // server again when due, arming what is missing) is done by the next opening, move, restart or return of the network.
        val nothingNew = alreadyInside && miffs.all { (id, miff) -> miff === state.miffs[id] }
        if (nothingNew && state.miffs.values.none { it.pending != null }) {
            resumeMovementIfNeeded(state)
            return
        }
        var next = state.copyWith(miffs = miffs, inside = inside)
        local.save(next)
        next = flushPending(uid, next, WriteGate(recheck = false))
        local.save(next)
        updateNetworkWake(next)
        if (nothingNew) {
            // A status was still waiting: it was sent above as before; only the full pass is spared.
            resumeMovementIfNeeded(next)
            return
        }
        // "À chaque entrée détectée dans une zone, la liste est recalculée" (ticket 6.5), and the position is read again.
        if (kind == TransitionKind.ENTER) doRefresh(Reason.ZONE_ENTERED, null)
    }

    /** The request of moves of more than 250 m that never started (refused by the system earlier) is made again: what the full pass of an entry used to retry. */
    private suspend fun resumeMovementIfNeeded(state: LocalState) {
        if (state.updatesArmed) return
        local.save(state.copyWith(updatesArmed = movement.start()))
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
            if (gate.ended()) break
            served = miffId
            val access = accessNow()
            // R-LIEU-1 (deployed): the update leaves `presenceAt` / `lastUpdateAt` alone unless the event changes them (the stored `lastUpdateAt` is accepted whatever its
            // age), so a refusal is never answered by writing the time of the event into `lastUpdateAt`.
            val battery = env.batteryPct()
            val outcome = writeStatus(uid, miffId, fact, at, access, battery, deviceId, miff.clearTravel)
            if (outcome is Guarded.Failed && outcome.failure == MiffFailure.REFUSED) gate.refused()
            state =
                when (outcome) {
                    is Guarded.Value -> {
                        journal.record(JournalKind.STATUS_WRITTEN, pending.placeId, battery)
                        state
                            .copyWith(breaker = WriteBreaker.recordStatus(state.breaker?.toState(), day))
                            .withMiff(miffId) {
                                it
                                    .copyWith(
                                        pending = null,
                                        access = access.value,
                                        clearTravel = false,
                                    ).withBattery(battery, now)
                            }
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
        battery: Int?,
        deviceId: String,
        clearTravel: Boolean = false,
    ): Guarded<Unit> =
        // A status that does not exist any more (deleted meanwhile) is NOT recreated here: with the real rules an update of a missing status answers
        // PERMISSION_DENIED (probe on the emulator), i.e. the ordinary refusal below ("look at the Miff again soon"), as before SESS-2.
        guardedCall(timeoutMillis) {
            store.commit(
                listOf(TrackingWrites.statusUpdate(miffId, uid, fact, at, battery, access, deviceId, clearTravel)),
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
            // MODE VOYAGE: the status of a trip that just ended still carries its keys until the next status write clears them: a partial update would be refused.
            if (miff.clearTravel) continue
            if (miff.access == null || miff.access == access.value) {
                if (miff.access == null) state = state.withMiff(miffId) { it.copyWith(access = access.value) }
                continue
            }
            val day = WriteBreaker.dayKey(now)
            if (!WriteBreaker.canWriteStatus(state.breaker?.toState(), day) || !env.isOnline()) continue
            // SESS-2: without the identifier of the session nothing is written, and the change stays to be told (the access is not marked as done).
            val deviceId = gate.deviceId() ?: continue
            if (gate.ended()) break
            val battery = env.batteryPct()
            val outcome =
                guardedCall(timeoutMillis) {
                    store.commit(listOf(TrackingWrites.accessUpdate(miffId, uid, battery, access, deviceId)))
                }
            if (outcome is Guarded.Failed && outcome.failure == MiffFailure.REFUSED) gate.refused()
            state =
                when (outcome) {
                    is Guarded.Value -> {
                        state
                            .copyWith(breaker = WriteBreaker.recordStatus(state.breaker?.toState(), day))
                            .withMiff(miffId) { it.copyWith(access = access.value).withBattery(battery, now) }
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
            if (!env.isOnline()) {
                journal.record(JournalKind.POSITION_SKIPPED_OFFLINE)
                continue
            }
            // SESS-2: a position never leaves a phone that does not hold the session (it would tell the loved ones where the OLD phone is).
            val deviceId = gate.deviceId()
            if (deviceId == null) {
                journal.record(JournalKind.POSITION_SKIPPED_SESSION)
                continue
            }
            if (gate.ended()) break
            served = miffId
            val outcome =
                guardedCall(timeoutMillis) {
                    store.commit(listOf(TrackingWrites.position(miffId, uid, fix.lat, fix.lng, measuredAt, deviceId)))
                }
            if (outcome is Guarded.Failed && outcome.failure == MiffFailure.REFUSED) {
                journal.record(JournalKind.POSITION_REFUSED)
                gate.refused()
            }
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

    // --- The battery, when the app is opened and when it is left ---------------------------------------------------------------------------

    /**
     * What the app asks when it looks again for any [reason]: the recalculation ([refresh]) and, when the app was OPENED or LEFT, the battery. On leaving, the battery
     * comes FIRST (a short write, while the process is surely alive) and the recalculation (position, zones) after; on opening it comes AFTER the recalculation,
     * which has just read the Miffs where the person shares and verified the session.
     */
    suspend fun lookAgain(reason: Reason) {
        if (reason == Reason.BACKGROUNDED) refreshBattery()
        refresh(reason)
        if (reason == Reason.OPENED) refreshBattery()
    }

    /**
     * The app was opened or left (PM decision, 2026-11-07): the battery level of the status of every Miff where the person shares is written again, so that the
     * loved ones see a recent level. Best effort and short: a pass that cannot start at once (another pass holds the engine) is given up, a write waits
     * [TrackingLimits.BATTERY_WRITE_TIMEOUT_MILLIS] at most and the whole pass [TrackingLimits.BATTERY_PASS_MAX_MILLIS]; whatever goes wrong, nothing is thrown.
     *
     * What it never does: create a status (the write is an UPDATE of three keys, see [TrackingWrites.batteryUpdate]; a missing status is a refusal of the
     * rules), write for a Miff that is not in the local list (sharing under the consent of version 3, as read from the server by the last look), write without
     * the identifier of the session (replaced phone, session not verified), write while the account is being deleted or after a sign-out, write while a status
     * of the Miff waits (that one carries a fresh level itself), read a position, or read the data of the server (no read of a document: the local list is the one the last
     * look produced; at most ONE read of the session, when its last verification is older than 10 minutes; a Miff whose sharing stopped meanwhile answers with a refusal and is looked at again soon, as for any other automatic write).
     * Limits: one attempt every 15 minutes, a level that moved by 5 points or more (or a last write older than 3 hours), the dedicated daily breaker; the pass also stops when the breaker of the statuses is used up (same rule as iOS).
     */
    suspend fun refreshBattery() {
        if (!mutex.tryLock()) return
        try {
            if (!sessionEnded()) doRefreshBattery()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // A best-effort write must never take the app down; nothing is logged (no personal data here, and the app never logs).
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun doRefreshBattery() {
        val uid = env.uid() ?: return
        // No local copy = the Miffs where the person shares are not known yet (the first look of the opening has not happened): nothing to do, nothing to read.
        val start = stateFor(uid) ?: return
        // MODE VOYAGE: no battery level is written during a trip (nothing is measured, the status says null).
        if (start.travel != null) return
        if (start.miffs.isEmpty()) return
        val battery = env.batteryPct() ?: return
        val startedAt = env.nowMillis()
        if (!BatteryRefresh.passAllowed(start.batteryTriedAtMillis, startedAt)) return
        val due =
            rotated(start.miffs.keys, null).filter { miffId ->
                val miff = start.miffs.getValue(miffId)
                miff.pending == null && !miff.clearTravel &&
                    BatteryRefresh.shouldWrite(miff.batteryPct, miff.batteryAtMillis, battery, startedAt)
            }
        if (due.isEmpty() || !env.isOnline()) return

        // One attempt per 15 minutes whatever the outcome: a refusal (status missing, sharing stopped) is not repeated at every opening.
        var state = start.copyWith(batteryTriedAtMillis = startedAt)
        local.save(state)
        val gate = WriteGate(recheck = false)

        // The WHOLE pass (the verification of the session included) never goes past BATTERY_PASS_MAX_MILLIS: every wait below is bounded by what is left of it,
        // so that leaving the app is never held back by the battery for more than that before the usual recalculation (zones, position) starts.
        fun remainingMillis(): Long = TrackingLimits.BATTERY_PASS_MAX_MILLIS - maxOf(0L, env.nowMillis() - startedAt)
        for (miffId in due) {
            val now = env.nowMillis()
            if (gate.ended() || remainingMillis() <= 0L) break
            val day = WriteBreaker.dayKey(now)
            // Aligned with iOS: the refresh also stops when the breaker of the statuses is used up (a phone at that limit is already writing too much).
            if (!WriteBreaker.canWriteStatus(state.breaker?.toState(), day)) {
                journal.record(JournalKind.BREAKER_STATUS)
                break
            }
            if (!WriteBreaker.canWriteBattery(state.breaker?.toState(), day)) {
                journal.record(JournalKind.BREAKER_BATTERY)
                break
            }
            // SESS-2: no identifier of session = nothing is written (replaced, not verified, signed out).
            val deviceId = withTimeoutOrNull(remainingMillis()) { gate.deviceId() } ?: break
            val writeBudget = minOf(timeoutMillis, TrackingLimits.BATTERY_WRITE_TIMEOUT_MILLIS, remainingMillis())
            if (gate.ended() || writeBudget <= 0L) break
            val outcome =
                guardedCall(writeBudget) {
                    store.commit(listOf(TrackingWrites.batteryUpdate(miffId, uid, battery, deviceId)))
                }
            if (outcome is Guarded.Failed && outcome.failure == MiffFailure.REFUSED) gate.refused()
            state =
                when (outcome) {
                    is Guarded.Value -> {
                        journal.record(JournalKind.BATTERY_WRITTEN, batteryPct = battery)
                        state
                            .copyWith(breaker = WriteBreaker.recordBattery(state.breaker?.toState(), day))
                            .withMiff(miffId) { it.withBattery(battery, now) }
                    }

                    is Guarded.Failed -> {
                        if (outcome.failure == MiffFailure.REFUSED) {
                            // No status, no more sharing, or no consent of the right version, according to the server: the Miff is looked at again soon.
                            journal.record(JournalKind.BATTERY_REFUSED)
                            recheckSoon(state, now)
                        } else {
                            state
                        }
                    }
                }
            local.save(state)
        }
    }

    /** The level written to this Miff and when (a null level, when the phone did not say, leaves the record as it was). */
    private fun LocalMiff.withBattery(
        pct: Int?,
        atMillis: Long,
    ): LocalMiff = if (pct == null) this else copyWith(batteryPct = pct, batteryAtMillis = atMillis)

    private fun updateNetworkWake(state: LocalState) {
        if (state.miffs.values.any { it.pending != null } ||
            travelToTell(state)
        ) {
            networkWake.watch()
        } else {
            networkWake.stop()
        }
    }

    private fun LocalState.withMiff(
        miffId: String,
        change: (LocalMiff) -> LocalMiff,
    ): LocalState {
        val current = miffs[miffId] ?: return this
        return copyWith(miffs = miffs + (miffId to change(current)))
    }

    // --- MODE VOYAGE (VOY-1A) -------------------------------------------------------------------------------------------------------------

    /**
     * Begins a trip (or moves its end when one is on): the trip is WRITTEN to every Miff where I share FIRST, the statuses of the zones are stopped AFTER (my position stays shared). A refusal of the server leaves everything as it was ([TravelResult.Refused]); no network, or a network that fails, accepts the trip here and tells
     * the Miffs as soon as the network is back ([TravelResult.DoneOffline]). The trip is open to EVERY account (decision of the PM, 2026-11-10): no plan is read or checked.
     */
    suspend fun setTravel(period: TravelPeriod): TravelResult =
        mutex.withLock { if (sessionEnded()) TravelResult.Unavailable else doSetTravel(period) }

    /** Ends the trip NOW (the person turned it off): the loved ones are told (the neutral state), the status of the zones resumes at once. False when no trip was on. */
    suspend fun endTravel(): Boolean =
        mutex.withLock {
            if (sessionEnded()) return@withLock false
            val uid = env.uid() ?: return@withLock false
            val state = stateFor(uid)?.takeIf { it.travel != null } ?: return@withLock false
            finishTravel(uid, state, env.nowMillis())
            true
        }

    private suspend fun doSetTravel(period: TravelPeriod): TravelResult {
        val uid = env.uid() ?: return TravelResult.Unavailable
        val now = env.nowMillis()
        if (period.isOver(now)) return TravelResult.Refused
        // The server accepts an end at most 91 days ahead; the picker stops at today + 89 days, this is the guard for anything else (a clock that moved, a daylight-saving change).
        if (period.untilMillis > now + TravelLimits.MAX_END_AHEAD_MILLIS) return TravelResult.Refused
        var state = stateFor(uid) ?: LocalState(uid)
        val online = env.isOnline()
        // Who to tell: the Miffs where I share, read from the server when possible (the local list otherwise).
        val read = if (online) readEligible(uid) else null
        val targets = (read ?: state.miffs.keys).sorted()
        val previous = state.travel
        val since = previous?.sinceMillis ?: now
        var travel =
            (
                previous ?: LocalTravel(
                    period.untilMillis,
                    period.untilDay,
                    since,
                )
            ).copyWith(period = period, upToDateIn = emptySet())

        if (online && targets.isNotEmpty()) {
            val gate = WriteGate(recheck = true)
            // No identifier of session (replaced, or the server could not be asked): nothing is written and nothing changes.
            val deviceId = gate.deviceId() ?: return TravelResult.Refused
            val presenceAt = EventTime.forWrite(since, now) ?: now
            var refusals = 0
            val written = mutableSetOf<String>()
            for (miffId in targets) {
                if (gate.ended()) break
                val outcome =
                    guardedCall(timeoutMillis) { store.commit(travelWrites(miffId, uid, travel, presenceAt, deviceId)) }
                when {
                    outcome is Guarded.Value -> {
                        written += miffId
                    }

                    outcome is Guarded.Failed && outcome.failure == MiffFailure.REFUSED -> {
                        refusals++
                        gate.refused()
                    }

                    else -> {
                        break
                    }
                }
            }
            // Nothing could be written and the server said no (rules not deployed yet, session taken): nothing has changed.
            if (written.isEmpty() && refusals > 0) return TravelResult.Refused
            travel = travel.copyWith(startedIn = travel.startedIn + written, upToDateIn = written)
        }

        val listed = targets.associateWith { state.miffs[it] ?: LocalMiff() }
        state =
            state.copyWith(
                miffs =
                    if (read != null ||
                        state.miffs.isEmpty()
                    ) {
                        listed
                    } else {
                        state.miffs + listed
                    },
                travel = travel,
            )
        if (read != null) state = state.copyWith(checkedAtMillis = now)
        // The status of the zones stops only now: the trip is known to the server (or kept to be told). The position stays shared.
        state = pauseStatuses(state)
        local.save(state)
        travelAlarm.schedule(travel.untilMillis)
        publishTravel(travel.period())
        tellTravelSet(uid, travel.untilMillis)
        updateNetworkWake(state)
        return if (travelToTell(state)) TravelResult.DoneOffline else TravelResult.Done
    }

    /** The write of a trip in one Miff: the end only when the Miff already knows the trip; else the whole trip. My position is NOT touched. */
    private fun travelWrites(
        miffId: String,
        uid: String,
        travel: LocalTravel,
        presenceAtMillis: Long,
        deviceId: String,
    ): List<DocWrite> =
        if (miffId in travel.startedIn) {
            listOf(TrackingWrites.travelDate(miffId, uid, travel.period(), deviceId))
        } else {
            listOf(TrackingWrites.travelStart(miffId, uid, travel.period(), presenceAtMillis, deviceId))
        }

    /** A Miff of the list still has to be told the trip (or its new end). */
    private fun travelToTell(state: LocalState): Boolean =
        state.travel?.let { travel -> state.miffs.keys.any { it !in travel.upToDateIn } } == true

    /** The Miffs where I share (sharing on, consent of version 3), WITHOUT their places. Null when the server could not tell. */
    private suspend fun readEligible(uid: String): Set<String>? {
        val refs =
            when (val read = guardedCall(timeoutMillis) { store.memberships(uid) }) {
                is Guarded.Value -> read.value.map { it.miffId }.distinct()
                is Guarded.Failed -> return null
            }
        val eligible = linkedSetOf<String>()
        for (miffId in refs) {
            when (val consent = guardedCall(timeoutMillis) { store.consent(miffId, uid) }) {
                is Guarded.Value -> {
                    val card = consent.value
                    if (card != null && card.enabled &&
                        MiffSharingConsent.coversPosition(card.version)
                    ) {
                        eligible += miffId
                    }
                }

                // No longer a member: not a Miff to tell. Any other failure: nothing is concluded.
                is Guarded.Failed -> {
                    if (!consent.lostAccess) return null
                }
            }
        }
        return eligible
    }

    /**
     * One wake-up during a trip. The trip is over: it ends (the status resumes). Otherwise: the alarm is set again (idempotent), the Miffs that appeared since are looked for (a Miff joined
     * during a trip learns of it at once), what the Miffs have not been told yet is written, and the position keeps being published ([publishPositionDuringTravel]).
     */
    private suspend fun doTravelPass(
        uid: String,
        start: LocalState,
        reason: Reason,
        suppliedFix: Fix? = null,
    ) {
        val travel = start.travel ?: return
        val now = env.nowMillis()
        publishTravel(travel.period())
        // A trip found again at a wake-up (begun before this version, or after a restart): the notifications know it (idempotent, nothing changes when they already did; a trip
        // that is over is told too, so that N7 still waits for its 08:00 even if the notifications never heard of it).
        tellTravelSet(uid, travel.untilMillis)
        if (travel.period().isOver(now)) {
            finishTravel(uid, start, now)
            return
        }
        travelAlarm.schedule(travel.untilMillis)
        var state = takeResumedDuringTravel(syncTravelTargets(uid, start, reason, now))
        val recheck = reason == Reason.OPENED || reason == Reason.REARM || reason == Reason.NETWORK_BACK
        val gate = WriteGate(recheck)
        state = flushTravel(uid, state, gate, now)
        // The alarm, the clock and the return of the network measure nothing (as outside a trip).
        if (reason != Reason.TRAVEL_ENDED && reason != Reason.NETWORK_BACK) {
            state = publishPositionDuringTravel(uid, state, reason, suppliedFix, now, gate)
        }
        local.save(state)
        updateNetworkWake(state)
    }

    /**
     * The position keeps being measured and published during a trip, with the guards of a normal pass: nothing without "Toujours" + the precise location, without a Miff where I share
     * under the consent of version 3, or without the session of this phone; one write per Miff, alone, with the guard of frequency and the breaker. Only the 250 m move reports are kept
     * (they are what keeps the position fresh while the app is closed); the zones are removed: nothing follows an arrival or a departure during a trip, so watching them would only wake the
     * phone for nothing.
     */
    private suspend fun publishPositionDuringTravel(
        uid: String,
        start: LocalState,
        reason: Reason,
        suppliedFix: Fix?,
        now: Long,
        gate: WriteGate,
    ): LocalState {
        var state = start
        val full = reason == Reason.OPENED || reason == Reason.REARM || reason == Reason.SHARING_CHANGED
        if (state.miffs.isEmpty() || !env.permissions().locationComplete) {
            // Nothing to tell, or the access is gone (AUT7): no position is read, no report asked for, and the copy of the position is erased.
            runCatchingCancellable { geofences.removeAll() }
            runCatchingCancellable { movement.stop() }
            return state.copyWith(armed = emptySet(), updatesArmed = false, fix = null)
        }
        if (full) {
            runCatchingCancellable { geofences.removeAll() }
            state = state.copyWith(armed = emptySet())
        }
        if (full || !state.updatesArmed) state = state.copyWith(updatesArmed = movement.start())
        val fix = chooseFix(state, suppliedFix, now)
        if (fix != null) {
            state = state.copyWith(fix = fix)
            journal.record(JournalKind.POSITION_READ, batteryPct = null)
        }
        return publishPosition(uid, state, fix, now, gate)
    }

    /**
     * The statuses of the zones stop (a trip begins or is adopted): the zones are removed, what was derived from them and the statuses waiting are forgotten. The position, its guard of
     * frequency and the reports of a move stay: the position keeps being shared.
     */
    private suspend fun pauseStatuses(state: LocalState): LocalState {
        runCatchingCancellable { geofences.removeAll() }
        return state.copyWith(
            miffs = state.miffs.mapValues { (_, miff) -> miff.copyWith(derived = null, pending = null) },
            inside = emptyMap(),
            armed = emptySet(),
        )
    }

    /** The Miffs where I share are read again when it is due (a change of sharing, a restart, the network back, an hour at the opening): 1 + n reads, no place. */
    private suspend fun syncTravelTargets(
        uid: String,
        state: LocalState,
        reason: Reason,
        now: Long,
    ): LocalState {
        val travel = state.travel ?: return state
        val due =
            when (reason) {
                Reason.SHARING_CHANGED, Reason.REARM, Reason.NETWORK_BACK -> {
                    true
                }

                Reason.OPENED -> {
                    state.checkedAtMillis == 0L ||
                        now - state.checkedAtMillis > TrackingLimits.PLACES_REREAD_MILLIS
                }

                else -> {
                    false
                }
            }
        if (!due || !env.isOnline()) return state
        val eligible = readEligible(uid) ?: return state
        return state.copyWith(
            miffs = eligible.associateWith { state.miffs[it] ?: LocalMiff() },
            checkedAtMillis = now,
            travel =
                travel.copyWith(
                    startedIn = travel.startedIn intersect eligible,
                    upToDateIn =
                        travel.upToDateIn intersect eligible,
                ),
        )
    }

    /** Tells the Miffs that do not know the trip (or its new end) yet, one independent write per Miff; a refusal drops the Miff from the list (it is looked at again soon). */
    private suspend fun flushTravel(
        uid: String,
        start: LocalState,
        gate: WriteGate,
        now: Long,
    ): LocalState {
        var state = start
        var travel = state.travel ?: return state
        val pending =
            state.miffs.keys
                .sorted()
                .filter { it !in travel.upToDateIn }
        if (pending.isEmpty() || !env.isOnline()) return state
        val deviceId = gate.deviceId() ?: return state
        val presenceAt = EventTime.forWrite(travel.sinceMillis, now) ?: now
        for (miffId in pending) {
            if (gate.ended()) break
            val outcome =
                guardedCall(timeoutMillis) { store.commit(travelWrites(miffId, uid, travel, presenceAt, deviceId)) }
            when {
                outcome is Guarded.Value -> {
                    travel =
                        travel.copyWith(startedIn = travel.startedIn + miffId, upToDateIn = travel.upToDateIn + miffId)
                }

                outcome is Guarded.Failed && outcome.failure == MiffFailure.REFUSED -> {
                    gate.refused()
                    state = recheckSoon(state, now).copyWith(miffs = state.miffs - miffId)
                    travel =
                        travel.copyWith(startedIn = travel.startedIn - miffId, upToDateIn = travel.upToDateIn - miffId)
                }

                // No network or no answer: what is left waits for the next wake-up.
                else -> {
                    break
                }
            }
        }
        return state.copyWith(travel = travel)
    }

    /**
     * The trip ends (the person turned it off, or its end came): the loved ones are told the neutral state "unknown" (and the keys of the trip are cleared in the same write),
     * the alarm is cancelled, then the tracking resumes ([Reason.REARM]: zones, position, and a presence if the person is in a place). A write that cannot leave waits for the
     * network (the local state is already free: the tracking does not wait for it).
     */
    private suspend fun finishTravel(
        uid: String,
        start: LocalState,
        now: Long,
    ) {
        val ended = start.travel ?: return
        travelAlarm.cancel()
        // Told BEFORE the writes: N7 waits for 08:00 after a natural end (even if the network is down), and is cancelled after an early one.
        tellTravelEnded(uid, early = !ended.period().isOver(now))
        val fact = PresenceMachine.unknown(now)
        var state =
            start.copyWith(
                miffs =
                    start.miffs.mapValues { (_, miff) ->
                        // The two keys of the trip are ALWAYS sent as null with the end, for every Miff of the list, even one that is not recorded as told: the answer to a start
                        // may have been lost after the server took it, and a trip left on the server would show "Voyage terminé" for good. Null keys are accepted next to `unknown`.
                        miff.copyWith(
                            derived = DerivedPresence(fact.presence, fact.placeId),
                            pending = PendingStatus.of(fact),
                            clearTravel = true,
                        )
                    },
                travel = null,
            )
        local.save(state)
        // The network is watched from the start: a flush that cannot leave (or a process that ends meanwhile) is still written when the network is back.
        updateNetworkWake(state)
        publishTravel(null)
        state = flushPending(uid, state, WriteGate(recheck = true))
        local.save(state)
        updateNetworkWake(state)
        // Zones, position, presence: the usual recalculation of a restart.
        doRefresh(Reason.REARM, null)
    }

    /**
     * A NEW phone for this account: is a trip on? MY OWN status is read in each Miff where I share (one read each, from the server) BEFORE anything is armed, so that the new phone
     * ADOPTS a trip instead of overwriting it. Null: the server could not tell (the caller stops, nothing is saved, the next wake-up asks again).
     */
    private suspend fun adoptTravel(
        uid: String,
        state: LocalState,
        now: Long,
    ): LocalState? {
        var found: TravelRead? = null
        val reads = mutableMapOf<String, TravelPeriod>()
        for (miffId in state.miffs.keys.sorted()) {
            when (val read = guardedCall(timeoutMillis) { store.ownTravel(miffId, uid) }) {
                is Guarded.Value -> {
                    val trip = read.value ?: continue
                    if (found == null) found = trip
                    reads[miffId] = trip.period
                }

                is Guarded.Failed -> {
                    if (!read.lostAccess) return null
                }
            }
        }
        val trip = found ?: return state
        val travel =
            LocalTravel(
                untilMillis = trip.period.untilMillis,
                untilDay = trip.period.untilDay,
                sinceMillis = trip.sinceMillis ?: now,
                startedIn = reads.keys.toSet(),
                upToDateIn = reads.filterValues { it == trip.period }.keys.toSet(),
            )
        return state.copyWith(travel = travel)
    }

    private companion object {
        const val NEVER = Int.MIN_VALUE

        /** A person who shares nowhere is looked at again at most this often at the opening of the app (2 reads of the server per Miff). */
        const val RETRY_WHEN_EMPTY_MILLIS = 2L * 60L * 1000L
    }
}

/** What [TrackingEngine.setTravel] answers. */
enum class TravelResult {
    /** The trip is on, and every Miff where I share knows it (or I share nowhere). */
    Done,

    /** The trip is on HERE; at least one Miff will be told as soon as the network is back (no network, or it failed). */
    DoneOffline,

    /** The server said no (rules not deployed yet, session taken): nothing has changed. */
    Refused,

    /** Nobody is signed in, or the session ended. */
    Unavailable,
}
