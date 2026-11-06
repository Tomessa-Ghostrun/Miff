package com.tomessa.miff.data.session

import android.content.Context
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.tomessa.miff.domain.session.DeviceIds
import com.tomessa.miff.domain.session.RemoteSession
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/*
 * SECURITY, for this file: the document holds an identifier and a server time, nothing else (the rules refuse any other key). Writes are TRANSACTIONS
 * (like every other write of the app): a plain write made without network would be queued and leave LATER, possibly over the session of the phone that
 * took over. Nothing is logged; the identifier never appears in an error message of ours.
 */
class FirestoreSessionDocuments(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : SessionDocuments {
    // The instance is resolved at each use: it is replaced after a purge of the cache.
    private fun reference(
        db: FirebaseFirestore,
        uid: String,
    ): DocumentReference =
        db
            .collection("users")
            .document(uid)
            .collection("session")
            .document("active")

    override suspend fun write(
        uid: String,
        deviceId: String,
    ) {
        require(DeviceIds.isValid(deviceId)) { "device id shape" }
        val db = firestore()
        val ref = reference(db, uid)
        db
            .runTransaction { transaction ->
                transaction.set(ref, mapOf("deviceId" to deviceId, "startedAt" to FieldValue.serverTimestamp()))
                Unit
            }.await()
    }

    override suspend fun delete(uid: String) {
        val db = firestore()
        val ref = reference(db, uid)
        db.runTransaction { transaction -> transaction.delete(ref) }.await()
    }

    override suspend fun read(uid: String): RemoteSession {
        val snapshot = reference(firestore(), uid).get(Source.SERVER).await()
        return if (snapshot.exists()) {
            RemoteSession.Present(
                snapshot.getString("deviceId").orEmpty(),
            )
        } else {
            RemoteSession.Missing
        }
    }

    override suspend fun deleteIfMine(
        uid: String,
        deviceId: String,
    ): Boolean {
        val db = firestore()
        val ref = reference(db, uid)
        return db
            .runTransaction { transaction ->
                val current = transaction.get(ref)
                if (current.exists() && current.getString("deviceId") == deviceId) {
                    transaction.delete(ref)
                    true
                } else {
                    false
                }
            }.await()
    }

    override fun observe(uid: String): Flow<RemoteSession> =
        callbackFlow {
            val registration =
                reference(firestore(), uid).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    // An error (token, server, the sign-out under way) ENDS the flow with it: the loop that listens notices, forgets what it had verified and
                    // starts over (verify, then listen), a few times at most. A dead listener must never look like a healthy one.
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener
                    // A copy from the local cache proves nothing (offline, nobody is signed out): only the server's word counts.
                    if (snapshot.metadata.isFromCache) return@addSnapshotListener
                    trySend(
                        if (snapshot.exists()) {
                            RemoteSession.Present(
                                snapshot.getString("deviceId").orEmpty(),
                            )
                        } else {
                            RemoteSession.Missing
                        },
                    )
                }
            awaitClose { registration.remove() }
        }
}

/**
 * The identifier of the session, in a private file of the app. Android does not back it up (`allowBackup=false` and the extraction rules exclude every
 * shared preference), so a restore on another phone never brings another phone's identifier. `commit()` on purpose: it is on the disk before the
 * session is considered started.
 */
class SharedPreferencesDeviceIdStore(
    context: Context,
) : DeviceIdStore {
    private val prefs = context.applicationContext.getSharedPreferences("miff_device", Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY, null)?.takeIf(DeviceIds::isValid)

    /** The result of `commit()` is the answer: false = the disk did not confirm. */
    override fun save(id: String): Boolean {
        require(DeviceIds.isValid(id)) { "device id shape" }
        return prefs.edit().putString(KEY, id).commit()
    }

    override fun clear() {
        prefs.edit().remove(KEY).commit()
    }

    override fun isReplaced(): Boolean = prefs.getBoolean(KEY_REPLACED, false)

    override fun markReplaced() {
        prefs.edit().putBoolean(KEY_REPLACED, true).commit()
    }

    override fun clearReplaced() {
        prefs.edit().remove(KEY_REPLACED).commit()
    }

    override fun isClosed(): Boolean = prefs.getBoolean(KEY_CLOSED, false)

    override fun markClosed() {
        prefs.edit().putBoolean(KEY_CLOSED, true).commit()
    }

    override fun clearClosed() {
        prefs.edit().remove(KEY_CLOSED).commit()
    }

    private companion object {
        const val KEY = "device_id"
        const val KEY_REPLACED = "replaced"
        const val KEY_CLOSED = "closed_for_deletion"
    }
}
