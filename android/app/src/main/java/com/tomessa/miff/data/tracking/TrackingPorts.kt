package com.tomessa.miff.data.tracking

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.tomessa.miff.data.miff.MiffStore
import com.tomessa.miff.data.miff.SharingConsentRead
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.permission.PermissionSnapshot
import com.tomessa.miff.domain.place.Place
import com.tomessa.miff.domain.tracking.Fix
import com.tomessa.miff.domain.tracking.PlaceFact
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.TrackedZone
import com.tomessa.miff.domain.travel.TravelPeriod
import com.tomessa.miff.domain.travel.TravelPlan
import kotlinx.coroutines.tasks.await

/*
 * The doors of the engine to the outside (the system, the phone, Firestore), as small interfaces: the engine itself is tested on the JVM with doubles.
 * PRIVACY: a [Fix] or a [TrackedZone] passes through these; none is ever logged (their toString() shows nothing).
 */

/** What the engine asks of the phone, at the moment it asks. */
interface TrackingEnvironment {
    /** The account signed in, or null. */
    fun uid(): String?

    fun permissions(): PermissionSnapshot

    /** Is the location of the phone itself switched on (the quick setting)? Off = the access counts as "denied" (ticket 6.7). */
    fun locationServicesOn(): Boolean

    /** Battery level 0..100, or null when the phone does not say. */
    fun batteryPct(): Int?

    fun isOnline(): Boolean

    fun nowMillis(): Long
}

/** Where the person is, when the engine asks (a double in tests). */
interface PositionSource {
    /** A recent position (a few seconds at most), or null when the system cannot give one. Reads the position: only called once the agreement and "Toujours" are there. */
    suspend fun current(): Fix?
}

/** The geofencing of the system. */
interface GeofenceArming {
    /** Adds [add] and removes [removeIds] in the system; true when the system accepted. */
    suspend fun update(
        add: List<TrackedZone>,
        removeIds: List<String>,
    ): Boolean

    /** Removes every zone of Miff (sign-out, no more sharing...). */
    suspend fun removeAll()
}

/** The reports of "the person moved by more than 250 m", delivered to the receiver even when the app is closed. */
interface MovementUpdates {
    /** Asks the provider of positions to report such moves (replaces the previous request); false when the system refuses. */
    suspend fun start(): Boolean

    suspend fun stop()
}

/** Wakes the app when the network is back, only while a status waits to be written (criterion HOR1). */
interface NetworkWake {
    fun watch()

    fun stop()
}

/**
 * MODE VOYAGE: the alarm that ends a trip while the app is closed (an INEXACT alarm: no special permission, a few minutes of drift are fine). [schedule] replaces the previous
 * one; [cancel] removes it. Idempotent and cheap: the engine calls it at every wake-up of a trip.
 */
interface TravelAlarm {
    fun schedule(atMillis: Long)

    fun cancel()
}

/** No alarm (tests, and the places where none is wanted). */
object NoTravelAlarm : TravelAlarm {
    override fun schedule(atMillis: Long) = Unit

    override fun cancel() = Unit
}

/** The trip that my own status of one Miff says I am on (read once when a NEW phone starts, so that it adopts the trip instead of overwriting it). */
class TravelRead(
    val period: TravelPeriod,
    val sinceMillis: Long?,
) {
    override fun toString(): String = "TravelRead(redacted)"
}

/** What the engine reads and writes in Firestore. Writes are TRANSACTIONS (never queued offline, see MiffStore): "no answer" means "nothing written". */
interface TrackingStore {
    suspend fun memberships(uid: String): List<MembershipRef>

    /** My consent in one Miff, from the SERVER (null when there is no card). */
    suspend fun consent(
        miffId: String,
        uid: String,
    ): SharingConsentRead?

    /** The places of a Miff, from the SERVER: only what is needed to watch them. */
    suspend fun places(miffId: String): List<PlaceFact>

    suspend fun commit(writes: List<DocWrite>)

    /**
     * MODE VOYAGE: the trip written in MY OWN status of one Miff, from the SERVER (null: no status, or not a trip). One read of a document of mine. The default says
     * "no trip" (a double that does not know the trips).
     */
    suspend fun ownTravel(
        miffId: String,
        uid: String,
    ): TravelRead? = null
}

class FirestoreTrackingStore(
    private val base: MiffStore,
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : TrackingStore {
    override suspend fun memberships(uid: String): List<MembershipRef> = base.readMemberships(uid, fromServer = true)

    override suspend fun consent(
        miffId: String,
        uid: String,
    ): SharingConsentRead? = base.readSharingConsent(miffId, uid)

    override suspend fun places(miffId: String): List<PlaceFact> =
        firestore()
            .collection("miffs")
            .document(miffId)
            .collection("places")
            .get(Source.SERVER)
            .await()
            .documents
            .mapNotNull { doc ->
                // The same reading as the places screens (bad identifier, type or range = not a place of this app); name and address are dropped.
                Place
                    .fromServer(
                        doc.id,
                        miffId,
                        doc.data,
                    )?.let { PlaceFact(it.id, it.lat, it.lng, it.radiusM, it.type) }
            }

    override suspend fun commit(writes: List<DocWrite>) = base.commit(writes)

    override suspend fun ownTravel(
        miffId: String,
        uid: String,
    ): TravelRead? {
        val doc =
            firestore()
                .collection("miffs")
                .document(miffId)
                .collection("status")
                .document(uid)
                .get(Source.SERVER)
                .await()
        if (!doc.exists() || doc.getString("presence") != PresenceValue.TRAVEL.wire) return null
        val until = doc.getTimestamp("travelUntil")?.toDate()?.time ?: return null
        // The day is only for the display: a status whose day is not an `AAAA-MM-JJ` text is not a trip we can keep (a modified client).
        val day = doc.getString("travelUntilDay")?.takeIf { TravelPlan.parseDay(it) != null } ?: return null
        return TravelRead(TravelPeriod(until, day), doc.getTimestamp("presenceAt")?.toDate()?.time)
    }
}

/**
 * What the engine tells the notifications, locally and at once (nothing is read or written by the callee for N1, N2 and N6; a failure of the callee never reaches the
 * detection: [TrackingEngine] swallows it). It carries keywords, identifiers and times, never a position or the name of a place.
 * - [onMyPresence] (NOTIF-4A): MY presence just changed in a Miff (a departure, an arrival): the reminders N1 and N2 follow without waiting for the Feed.
 * - [onEnteredDanger] (NOTIF-7A): the engine validated that I ENTERED a danger zone of a Miff (a presence in_risk different from the previous one): N6 tells me, once.
 */
interface PresenceListener {
    fun onMyPresence(
        miffId: String,
        presence: PresenceValue,
        atMillis: Long,
        setLastUpdate: Boolean,
    )

    /** [placeId] is the identifier of the place in that Miff (never its name or its position). */
    fun onEnteredDanger(
        myUid: String,
        miffId: String,
        placeId: String,
        atMillis: Long,
    ) = Unit

    companion object {
        val None =
            object : PresenceListener {
                override fun onMyPresence(
                    miffId: String,
                    presence: PresenceValue,
                    atMillis: Long,
                    setLastUpdate: Boolean,
                ) = Unit
            }
    }
}
