package com.tomessa.miff.data.tracking

import android.content.Context
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.tracking.BreakerState
import com.tomessa.miff.domain.tracking.DerivedPresence
import com.tomessa.miff.domain.tracking.Fix
import com.tomessa.miff.domain.tracking.PlaceFact
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.StatusFact
import com.tomessa.miff.domain.tracking.WrittenPosition
import com.tomessa.miff.domain.travel.TravelPeriod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/*
 * PRIVACY, for this file: this is the LOCAL COPY of what the detection needs: the places of the Miffs where the person shares (to choose the 20 nearest
 * without asking the server), where the person is (the zones they are in), their last position, and the status waiting to be written. It lives in
 * a PRIVATE preference file of the app, EXCLUDED from every backup (res/xml/backup_rules.xml and data_extraction_rules.xml exclude all the
 * shared preferences), and it is ERASED at sign-out, at the deletion of the account and when the access to a Miff or the sharing is lost
 * (criteria POS5, VIE3). It holds no name and no address of a place, never a log, never an event. The class that holds a position has a toString()
 * that shows nothing.
 */

@Serializable
class LocalPlace(
    val id: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
    val type: String,
) {
    fun toFact(): PlaceFact? = PlaceType.fromValue(type)?.let { PlaceFact(id, lat, lng, radiusM, it) }

    override fun toString(): String = "LocalPlace(redacted)"

    companion object {
        fun of(fact: PlaceFact) = LocalPlace(fact.id, fact.lat, fact.lng, fact.radiusM, fact.type.value)
    }
}

/** A status that could not be written yet (no network...): the LAST one wins, it carries the real time of the event. */
@Serializable
class PendingStatus(
    val presence: String,
    val placeId: String?,
    val atMillis: Long,
    val setLastUpdate: Boolean,
) {
    fun toFact(): StatusFact? =
        PresenceValue.fromWire(presence)?.let { StatusFact(it, placeId, atMillis, setLastUpdate) }

    override fun toString(): String = "PendingStatus(redacted)"

    companion object {
        fun of(fact: StatusFact) =
            PendingStatus(fact.presence.wire, fact.placeId, fact.presenceAtMillis, fact.setLastUpdate)
    }
}

/** What the phone remembers of ONE Miff where the person shares (with the consent of version 3). */
@Serializable
class LocalMiff(
    val places: List<LocalPlace> = emptyList(),
    /** When the places and the consent of this Miff were last read from the server (0 = never). */
    val readAtMillis: Long = 0L,
    /** The presence this phone last derived for this Miff (what was last written, or is waiting): to write only a CHANGE. */
    val presence: String? = null,
    val presencePlaceId: String? = null,
    /** The `locationAccess` last written (so that a change of the permission is written once). */
    val access: String? = null,
    /** The last position written to this Miff (guard of frequency). */
    val positionLat: Double? = null,
    val positionLng: Double? = null,
    val positionAtMillis: Long? = null,
    val pending: PendingStatus? = null,
    /** The last battery level written to this Miff by this phone (any status write counts) and when: the refresh on opening / leaving writes only a real change. Not a location. */
    val batteryPct: Int? = null,
    val batteryAtMillis: Long? = null,
    /** MODE VOYAGE: the status of this Miff still carries the keys of a trip that ended: the next write of the status clears them (then this goes back to false). */
    val clearTravel: Boolean = false,
) {
    fun derived(): DerivedPresence? = PresenceValue.fromWire(presence)?.let { DerivedPresence(it, presencePlaceId) }

    fun written(): WrittenPosition? =
        if (positionLat != null && positionLng != null && positionAtMillis != null) {
            WrittenPosition(positionLat, positionLng, positionAtMillis)
        } else {
            null
        }

    fun copyWith(
        places: List<LocalPlace> = this.places,
        readAtMillis: Long = this.readAtMillis,
        derived: DerivedPresence? = derived(),
        access: String? = this.access,
        written: WrittenPosition? = written(),
        pending: PendingStatus? = this.pending,
        batteryPct: Int? = this.batteryPct,
        batteryAtMillis: Long? = this.batteryAtMillis,
        clearTravel: Boolean = this.clearTravel,
    ) = LocalMiff(
        places = places,
        readAtMillis = readAtMillis,
        presence = derived?.presence?.wire,
        presencePlaceId = derived?.placeId,
        access = access,
        positionLat = written?.lat,
        positionLng = written?.lng,
        positionAtMillis = written?.atMillis,
        pending = pending,
        batteryPct = batteryPct,
        batteryAtMillis = batteryAtMillis,
        clearTravel = clearTravel,
    )

    override fun toString(): String = "LocalMiff(redacted)"
}

/**
 * MODE VOYAGE (VOY-1A): the trip in progress on this phone. While it exists NOTHING is watched, read or written by the tracking (no zone, no position, no presence, no
 * battery); it only tells the loved ones about the trip. [startedIn] = the Miffs whose status says `travel` on the server (any end), [upToDateIn] = those whose end is the
 * current one. A Miff of the local list that is not in [startedIn] still has to be told (offline, shared later); one that is in [startedIn] but not in [upToDateIn] has to
 * be told the new end. [sinceMillis] = when the trip began ("En voyage depuis…"). Holds no coordinate.
 */
@Serializable
class LocalTravel(
    val untilMillis: Long,
    val untilDay: String,
    val sinceMillis: Long,
    val startedIn: Set<String> = emptySet(),
    val upToDateIn: Set<String> = emptySet(),
) {
    fun period() = TravelPeriod(untilMillis, untilDay)

    fun copyWith(
        period: TravelPeriod = period(),
        startedIn: Set<String> = this.startedIn,
        upToDateIn: Set<String> = this.upToDateIn,
    ) = LocalTravel(period.untilMillis, period.untilDay, sinceMillis, startedIn, upToDateIn)

    override fun toString(): String = "LocalTravel(redacted)"
}

@Serializable
class LocalBreaker(
    val dayKey: String,
    val statusWrites: Int,
    val positionWrites: Int,
    val batteryWrites: Int = 0,
) {
    fun toState() = BreakerState(dayKey, statusWrites, positionWrites, batteryWrites)

    companion object {
        fun of(state: BreakerState) =
            LocalBreaker(state.dayKey, state.statusWrites, state.positionWrites, state.batteryWrites)
    }
}

/** The whole local copy. [uid] is the account it belongs to: the copy of another account is never used. */
@Serializable
class LocalState(
    val uid: String,
    val miffs: Map<String, LocalMiff> = emptyMap(),
    /** The zones the person is in now: zone -> time of the entry. */
    val inside: Map<String, Long> = emptyMap(),
    /** The zones currently registered with the geofencing of the system. */
    val armed: Set<String> = emptySet(),
    val fixLat: Double? = null,
    val fixLng: Double? = null,
    val fixAtMillis: Long? = null,
    val breaker: LocalBreaker? = null,
    /** The provider of positions has been asked to report moves of more than 250 m. */
    val updatesArmed: Boolean = false,
    /** When the list of Miffs where the person shares (with the consent of version 3) was last read from the server (0 = never). */
    val checkedAtMillis: Long = 0L,
    /** The last Miff served by a pass of statuses / of positions: the next pass starts AFTER it, so that a breaker never spares the same Miffs for good. */
    val statusCursor: String? = null,
    val positionCursor: String? = null,
    /** When a battery refresh (opening / leaving the app) was last TRIED, whatever its outcome (0 = never): at most one attempt every 15 minutes. */
    val batteryTriedAtMillis: Long = 0L,
    /** MODE VOYAGE: the trip in progress, or null. Kept by every copy of this state; erased with it (sign-out, deletion of the account, session lost). */
    val travel: LocalTravel? = null,
) {
    fun fix(): Fix? =
        if (fixLat != null && fixLng != null &&
            fixAtMillis != null
        ) {
            Fix(fixLat, fixLng, fixAtMillis)
        } else {
            null
        }

    fun copyWith(
        miffs: Map<String, LocalMiff> = this.miffs,
        inside: Map<String, Long> = this.inside,
        armed: Set<String> = this.armed,
        fix: Fix? = fix(),
        breaker: BreakerState? = this.breaker?.toState(),
        updatesArmed: Boolean = this.updatesArmed,
        checkedAtMillis: Long = this.checkedAtMillis,
        statusCursor: String? = this.statusCursor,
        positionCursor: String? = this.positionCursor,
        batteryTriedAtMillis: Long = this.batteryTriedAtMillis,
        travel: LocalTravel? = this.travel,
    ) = LocalState(
        uid = uid,
        miffs = miffs,
        inside = inside,
        armed = armed,
        fixLat = fix?.lat,
        fixLng = fix?.lng,
        fixAtMillis = fix?.measuredAtMillis,
        breaker = breaker?.let(LocalBreaker::of),
        updatesArmed = updatesArmed,
        checkedAtMillis = checkedAtMillis,
        statusCursor = statusCursor,
        positionCursor = positionCursor,
        batteryTriedAtMillis = batteryTriedAtMillis,
        travel = travel,
    )

    override fun toString(): String = "LocalState(redacted)"
}

/** The local copy (a double in tests). Synchronous on purpose: a receiver has seconds, and the file is small. */
interface TrackingLocalStore {
    fun load(): LocalState?

    fun save(state: LocalState)

    fun clear()
}

class SharedPreferencesTrackingLocalStore(
    context: Context,
) : TrackingLocalStore {
    // A private file of the app; excluded from the backups by the two backup rule files (all the shared preferences are).
    private val prefs = context.applicationContext.getSharedPreferences("miff_tracking_local", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): LocalState? {
        val raw = prefs.getString(KEY, null) ?: return null
        return try {
            json.decodeFromString<LocalState>(raw)
        } catch (e: Exception) {
            // An unreadable copy is no copy: it is rebuilt from the server (the next reading), never trusted.
            null
        }
    }

    override fun save(state: LocalState) {
        prefs.edit().putString(KEY, json.encodeToString(state)).commit()
    }

    override fun clear() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val KEY = "state"
    }
}
