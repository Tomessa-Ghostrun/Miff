package com.tomessa.miff.data.profile

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.tomessa.miff.data.auth.ProfileWriteResult
import com.tomessa.miff.data.auth.interpretWriteError
import com.tomessa.miff.domain.auth.ProfileDocument
import com.tomessa.miff.domain.profile.MyProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/*
 * SECURITY, for this file: names, phone number and photo are personal data. They go to Firestore through this class and
 * nowhere else: never logged, never in an analytics event, never in an exception message (the errors are reduced to a
 * [ProfileWriteResult] before they leave this file).
 */

sealed interface MyProfileLoad {
    data class Loaded(
        val profile: MyProfile,
    ) : MyProfileLoad

    /** The profile could not be read (offline without a cache, refused, slow). The person can retry. */
    data object Unavailable : MyProfileLoad
}

/** How the saving of "Mes informations" ended. */
enum class ProfileSaveResult {
    /** The profile AND the copy kept in each Miff are written. */
    SAVED,

    /** The profile is written, but the copy of at least one Miff could not be reached yet: the person can try again. */
    SAVED_PARTIAL,

    /** No network: nothing was written (the writes are transactions, never queued for later). */
    OFFLINE,

    /** The server answered "no". */
    REFUSED,

    FAILED,
}

/**
 * Reading and changing the profile of the person signed in (Settings > Mes informations, ticket ONB-3a).
 *
 * The profile lives twice (backend/data-model.md sections 2.1 and 2.6): `users/{uid}` (private) and a COPY in
 * `miffs/{miffId}/members/{uid}` of each Miff, which is what the other members see. A change is written to both.
 */
interface MyProfileRepository {
    suspend fun read(uid: String): MyProfileLoad

    suspend fun save(
        uid: String,
        profile: MyProfile,
    ): ProfileSaveResult
}

/**
 * The Firestore operations behind [FirestoreMyProfileRepository]. Writes are TRANSACTIONS (like the Onboarding ones): a
 * plain write made without network is queued and leaves later, by itself, possibly after the person signed out; a
 * transaction only exists online and leaves nothing behind when it fails, so "no confirmation" means "nothing written".
 */
interface MyProfileStore {
    /** The fields of `users/{uid}` (the photo as bytes), or null if there is no profile. */
    suspend fun readProfile(uid: String): Map<String, Any?>?

    /** The Miffs of the person (`users/{uid}/memberships`), read from the SERVER, never from the cache. */
    suspend fun membershipMiffIds(uid: String): List<String>

    /** ONE transaction: `users/{uid}` (when [writeProfile]) and the copy of each Miff of [miffIds]. */
    suspend fun commit(
        uid: String,
        data: Map<String, Any?>,
        writeProfile: Boolean,
        miffIds: List<String>,
    )
}

class FirestoreMyProfileStore(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : MyProfileStore {
    // The instance is resolved at each use: it is replaced after a cache purge.
    override suspend fun readProfile(uid: String): Map<String, Any?>? {
        val snapshot =
            firestore()
                .collection("users")
                .document(uid)
                .get()
                .await()
        if (!snapshot.exists()) return null
        return snapshot.data?.mapValues { (_, value) -> (value as? Blob)?.toBytes() ?: value }
    }

    override suspend fun membershipMiffIds(uid: String): List<String> =
        firestore()
            .collection("users")
            .document(uid)
            .collection("memberships")
            .get(Source.SERVER)
            .await()
            .documents
            .mapNotNull { it.getString("miffId") }

    override suspend fun commit(
        uid: String,
        data: Map<String, Any?>,
        writeProfile: Boolean,
        miffIds: List<String>,
    ) {
        val db = firestore()
        val profileRef = db.collection("users").document(uid)
        val copies =
            miffIds.map {
                db
                    .collection("miffs")
                    .document(it)
                    .collection("members")
                    .document(uid)
            }
        db
            .runTransaction { transaction ->
                if (writeProfile) transaction.update(profileRef, data)
                copies.forEach { transaction.update(it, data) }
                Unit
            }.await()
    }
}

class FirestoreMyProfileRepository(
    private val store: MyProfileStore = FirestoreMyProfileStore(),
    private val timeoutMillis: Long = TIMEOUT_MS,
) : MyProfileRepository {
    override suspend fun read(uid: String): MyProfileLoad =
        try {
            val fields = withTimeout(timeoutMillis) { store.readProfile(uid) }
            fields?.let(::profileOf)?.let { MyProfileLoad.Loaded(it) } ?: MyProfileLoad.Unavailable
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) MyProfileLoad.Unavailable else throw e
        } catch (e: Exception) {
            MyProfileLoad.Unavailable
        }

    /**
     * Order of the writes, and why:
     * 1. ONE transaction with the profile and the copy of each Miff (15 at most: the rules read the profile again for each
     *    copy, and a request may make 20 such reads, backend/data-model.md section 2.6). This is the contract of the data
     *    model: the copies are never seen with a different number than the profile.
     * 2. If the server refuses that transaction while there are copies (a Miff that no longer exists, a membership that
     *    outlived it), the profile is written ALONE first, then each copy on its own: one stale Miff must never prevent
     *    the person from changing their own name. A copy refused on its own is skipped (the Miff is gone).
     * 3. More than 15 Miffs (Premium only): the following copies go by groups of 15, the profile is already written.
     * Only a missing network leaves a copy "pending" ([ProfileSaveResult.SAVED_PARTIAL]): the same save, repeated, finishes it.
     */
    override suspend fun save(
        uid: String,
        profile: MyProfile,
    ): ProfileSaveResult {
        val phone = profile.phone
        require(phone != null && ProfileDocument.E164.matches(phone)) { "phone is not E.164" }
        require(ProfileDocument.isNameValid(profile.firstName) && ProfileDocument.isNameValid(profile.lastName)) {
            "name length"
        }
        require(
            profile.avatarJpeg == null || profile.avatarJpeg.size <= ProfileDocument.MAX_AVATAR_BYTES,
        ) { "avatar size" }
        val data = documentOf(profile)

        val miffIds =
            when (val found = guardedResult { store.membershipMiffIds(uid) }) {
                is Guarded.Value -> found.value.filter(::isMiffId).distinct()
                is Guarded.Failed -> return found.result.toSaveResult()
            }

        val firstGroup = miffIds.take(MAX_COPIES_PER_REQUEST)
        val first = guarded { store.commit(uid, data, writeProfile = true, miffIds = firstGroup) }
        var pending = false
        when {
            first == ProfileWriteResult.DONE -> {
                miffIds.drop(MAX_COPIES_PER_REQUEST).chunked(MAX_COPIES_PER_REQUEST).forEach { group ->
                    val result = guarded { store.commit(uid, data, writeProfile = false, miffIds = group) }
                    if (result == ProfileWriteResult.DONE) return@forEach
                    group.forEach { pending = copyAlone(uid, data, it) || pending }
                }
            }

            first == ProfileWriteResult.OFFLINE || firstGroup.isEmpty() -> {
                return first.toSaveResult()
            }

            else -> {
                val profileAlone = guarded { store.commit(uid, data, writeProfile = true, miffIds = emptyList()) }
                if (profileAlone != ProfileWriteResult.DONE) return profileAlone.toSaveResult()
                miffIds.forEach { pending = copyAlone(uid, data, it) || pending }
            }
        }
        return if (pending) ProfileSaveResult.SAVED_PARTIAL else ProfileSaveResult.SAVED
    }

    /** One copy on its own. True = it is still to do (no network); a copy the server refuses is skipped. */
    private suspend fun copyAlone(
        uid: String,
        data: Map<String, Any?>,
        miffId: String,
    ): Boolean =
        guarded { store.commit(uid, data, writeProfile = false, miffIds = listOf(miffId)) } ==
            ProfileWriteResult.OFFLINE

    private suspend fun guarded(write: suspend () -> Unit): ProfileWriteResult =
        when (val outcome = guardedResult(write)) {
            is Guarded.Value -> ProfileWriteResult.DONE
            is Guarded.Failed -> outcome.result
        }

    private sealed interface Guarded<out T> {
        data class Value<T>(
            val value: T,
        ) : Guarded<T>

        data class Failed(
            val result: ProfileWriteResult,
        ) : Guarded<Nothing>
    }

    private suspend fun <T> guardedResult(call: suspend () -> T): Guarded<T> =
        try {
            Guarded.Value(withTimeout(timeoutMillis) { call() })
        } catch (e: TimeoutCancellationException) {
            Guarded.Failed(ProfileWriteResult.OFFLINE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Guarded.Failed(interpretWriteError(e))
        }

    private fun ProfileWriteResult.toSaveResult(): ProfileSaveResult =
        when (this) {
            ProfileWriteResult.DONE -> ProfileSaveResult.SAVED
            ProfileWriteResult.OFFLINE -> ProfileSaveResult.OFFLINE
            ProfileWriteResult.REFUSED -> ProfileSaveResult.REFUSED
            ProfileWriteResult.FAILED -> ProfileSaveResult.FAILED
        }

    companion object {
        const val TIMEOUT_MS = 20_000L

        /** `data-model.md` section 2.6: at most 15 copies per request (20 reads of rules, 5 of margin). */
        const val MAX_COPIES_PER_REQUEST = 15

        /** An identifier read from the server is checked before it becomes part of a path (same shape as the rules). */
        private val MIFF_ID = Regex("^[A-Za-z0-9]{20}$")

        fun isMiffId(value: String): Boolean = MIFF_ID.matches(value)

        /** The four keys that change, always all four: a copy left behind by an earlier failure is healed by any save. */
        fun documentOf(profile: MyProfile): Map<String, Any?> =
            mapOf(
                "firstName" to profile.firstName,
                "lastName" to profile.lastName,
                "phone" to profile.phone,
                "avatar" to (profile.avatarJpeg?.let { Blob.fromBytes(it) } ?: FieldValue.delete()),
            )

        /** Names and photo of a profile as the rules require them; null otherwise. The phone may be absent (older account). */
        fun profileOf(fields: Map<String, Any?>): MyProfile? {
            val first = fields["firstName"] as? String ?: return null
            val last = fields["lastName"] as? String ?: return null
            val phone = (fields["phone"] as? String)?.takeIf { ProfileDocument.E164.matches(it) }
            val avatar = (fields["avatar"] as? ByteArray)?.takeIf { it.size in 1..ProfileDocument.MAX_AVATAR_BYTES }
            return MyProfile(first, last, phone, avatar)
        }
    }
}
