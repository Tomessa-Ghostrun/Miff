package com.tomessa.miff.data.tracking

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.tomessa.miff.data.miff.MiffStore
import com.tomessa.miff.data.miff.SharingConsentRead
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.permission.PermissionSnapshot
import com.tomessa.miff.domain.place.Place
import com.tomessa.miff.domain.tracking.Fix
import com.tomessa.miff.domain.tracking.PlaceFact
import com.tomessa.miff.domain.tracking.TrackedZone
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
     * The automatic status of one event: the [update] (partial, nothing else of the document is touched); if the status does not exist any more (deleted
     * meanwhile: Firestore answers NOT_FOUND), the [create] instead, a complete first status with recent times (R-LIEU-1: nothing old is written back).
     * Any other failure, a refusal included, is the caller's to handle.
     */
    suspend fun commitStatus(
        update: DocWrite,
        create: DocWrite,
    ) {
        try {
            commit(listOf(update))
        } catch (e: FirebaseFirestoreException) {
            if (e.code != FirebaseFirestoreException.Code.NOT_FOUND) throw e
            commit(listOf(create))
        }
    }
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
}
