package com.tomessa.miff.data.miff

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.tomessa.miff.domain.miff.LocationAccess
import com.tomessa.miff.domain.miff.MiffDetails
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffObservation
import com.tomessa.miff.domain.miff.MiffPlace
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.miff.MiffSnapshot
import com.tomessa.miff.domain.miff.isAccessLostError
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The permission of the phone to locate, as it is NOW, written in the first status (ticket Miff 5.4, step 7). The app does not
 * ask for it yet (the Location ticket does): without a grant the value is "unknown" (never asked or not granted: the app cannot
 * tell a refusal from "not asked yet", so it does not claim a refusal).
 */
interface LocationAccessProbe {
    fun current(): LocationAccess
}

class AndroidLocationAccessProbe(
    context: Context,
) : LocationAccessProbe {
    private val appContext = context.applicationContext

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    override fun current(): LocationAccess =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && granted(BACKGROUND) -> {
                LocationAccess.BACKGROUND
            }

            granted(android.Manifest.permission.ACCESS_FINE_LOCATION) ||
                granted(android.Manifest.permission.ACCESS_COARSE_LOCATION) -> {
                LocationAccess.FOREGROUND
            }

            else -> {
                LocationAccess.UNKNOWN
            }
        }

    private companion object {
        const val BACKGROUND = "android.permission.ACCESS_BACKGROUND_LOCATION"
    }
}

/** The live view of one Miff (ticket Miff G5, D1, D7). */
interface MiffObserver {
    fun observe(miffId: String): Flow<MiffObservation>
}

/**
 * Three listeners (the Miff, its members, its places) put together in ONE picture, so that the screen never shows a name with
 * somebody else's members. A refusal of the rules, or a Miff that does not exist, is [MiffObservation.AccessLost]; a network
 * problem never is (the listeners simply serve the cache and catch up).
 */
class FirestoreMiffObserver(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : MiffObserver {
    private class Piece<T>(
        val value: T? = null,
        val fromCache: Boolean = false,
        val lost: Boolean = false,
        val failed: Boolean = false,
    )

    override fun observe(miffId: String): Flow<MiffObservation> =
        callbackFlow {
            val lock = Any()
            var miff: Piece<MiffDetails>? = null
            var members: Piece<List<MiffMember>>? = null
            var places: Piece<List<MiffPlace>>? = null

            fun publish() {
                val current: MiffObservation =
                    synchronized(lock) {
                        val m = miff
                        val mem = members
                        val pl = places
                        when {
                            m?.lost == true || mem?.lost == true -> {
                                MiffObservation.AccessLost
                            }

                            m?.failed == true || mem?.failed == true -> {
                                MiffObservation.Failed
                            }

                            m?.value != null && mem?.value != null && pl != null -> {
                                MiffObservation.Data(
                                    MiffSnapshot(
                                        miff = m.value,
                                        members = mem.value,
                                        // The places are secondary: a list that cannot be read is an empty list.
                                        places = pl.value.orEmpty(),
                                        fromCache = m.fromCache || mem.fromCache,
                                    ),
                                )
                            }

                            else -> {
                                MiffObservation.Loading
                            }
                        }
                    }
                trySend(current)
            }

            val db = firestore()
            val miffRef = db.collection("miffs").document(miffId)
            val registrations =
                listOf(
                    miffRef.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                        synchronized(lock) {
                            miff =
                                when {
                                    error != null -> {
                                        Piece(
                                            lost = isAccessLostError(error),
                                            failed = !isAccessLostError(error),
                                        )
                                    }

                                    snapshot == null -> {
                                        Piece(failed = true)
                                    }

                                    else -> {
                                        miffPiece(snapshot)
                                    }
                                }
                        }
                        publish()
                    },
                    miffRef.collection("members").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                        synchronized(lock) {
                            members =
                                when {
                                    error != null -> {
                                        Piece(
                                            lost = isAccessLostError(error),
                                            failed = !isAccessLostError(error),
                                        )
                                    }

                                    snapshot == null -> {
                                        Piece(failed = true)
                                    }

                                    else -> {
                                        Piece(membersOf(snapshot), snapshot.metadata.isFromCache)
                                    }
                                }
                        }
                        publish()
                    },
                    miffRef.collection("places").addSnapshotListener { snapshot, error ->
                        synchronized(lock) {
                            places =
                                if (error != null || snapshot == null) Piece(emptyList()) else Piece(placesOf(snapshot))
                        }
                        publish()
                    },
                )
            awaitClose { registrations.forEach { it.remove() } }
        }.distinctUntilChanged()

    private fun miffPiece(snapshot: DocumentSnapshot): Piece<MiffDetails> {
        // "Not in the cache" is not "deleted": only the SERVER can say a Miff does not exist (airplane mode is not a loss).
        if (!snapshot.exists() && snapshot.metadata.isFromCache) return Piece()
        // A Miff that does not exist (deleted) is a lost access, exactly like a refusal (ticket 5.7, message QM8).
        val details = FirestoreMiffStore.detailsOf(snapshot) ?: return Piece(lost = true)
        return Piece(details, snapshot.metadata.isFromCache)
    }

    private fun membersOf(snapshot: QuerySnapshot): List<MiffMember> =
        snapshot.documents
            .mapNotNull { doc ->
                val first = doc.getString("firstName") ?: return@mapNotNull null
                val last = doc.getString("lastName") ?: return@mapNotNull null
                val role = if (doc.getString("role") == "owner") MiffRole.OWNER else MiffRole.MEMBER
                val joinedAt = doc.getTimestamp("joinedAt")?.toDate()?.time ?: Long.MAX_VALUE
                OrderedMember(
                    MiffMember(
                        uid = doc.id,
                        firstName = first,
                        lastName = last,
                        avatarJpeg = doc.getBlob("avatar")?.toBytes(),
                        role = role,
                        sharingEnabled = doc.getBoolean("sharing.enabled") == true,
                    ),
                    joinedAt,
                )
            }
            // The creator first, then in the order of arrival (stable: a list that reorders itself moves things under a finger).
            .sortedWith(
                compareBy<OrderedMember> {
                    it.member.role != MiffRole.OWNER
                }.thenBy { it.joinedAt }.thenBy { it.member.uid },
            ).map { it.member }

    private class OrderedMember(
        val member: MiffMember,
        val joinedAt: Long,
    )

    private fun placesOf(snapshot: QuerySnapshot): List<MiffPlace> =
        snapshot.documents
            .mapNotNull { doc -> doc.getString("name")?.let { MiffPlace(doc.id, it) } }
            .sortedBy { it.name.lowercase() }
}
