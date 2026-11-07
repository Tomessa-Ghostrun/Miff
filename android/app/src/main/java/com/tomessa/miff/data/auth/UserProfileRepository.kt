package com.tomessa.miff.data.auth

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.domain.auth.PendingConsent
import com.tomessa.miff.domain.auth.ProfileDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** How a write of the profile ended. No code, no message, no value: nothing here can leak a name or a number. */
enum class ProfileWriteResult {
    /** The SERVER has accepted the write. */
    DONE,

    /** The server could not be reached (no network, too slow). Nothing was written; the person can retry. */
    OFFLINE,

    /** The server answered "no" (rules, App Check). Not a network problem: do not say "no connection". */
    REFUSED,

    FAILED,
}

/**
 * The writes of `users/{uid}` made by the Onboarding steps (profile creation, phone number of an older account), each one
 * CONFIRMED by the server before the next screen opens (ticket 6.4).
 *
 * They go through a Firestore TRANSACTION on purpose: a plain `set` made without network is kept in a queue and leaves
 * later, by itself, possibly after the person has typed something else or signed out; a transaction only exists online and
 * leaves nothing behind when it fails, so "no confirmation" always means "nothing was written".
 */
interface UserProfileRepository {
    /** Creation of the profile in ONE write with every key (see [ProfileDocument]), phone number included. */
    suspend fun create(
        uid: String,
        firstName: String,
        lastName: String,
        avatarJpeg: ByteArray?,
        phone: String,
        consent: PendingConsent,
    ): ProfileWriteResult

    /** Account created before ONB-B: the profile exists, only the phone number is added (ticket 6.2, row 2b). */
    suspend fun addPhone(
        uid: String,
        phone: String,
    ): ProfileWriteResult

    /**
     * Account created before AUTH-B (profile without `termsVersion`): the rules refuse any update, so the profile is deleted
     * and created again with the full set of keys (backend/data-model.md section 2.1).
     */
    suspend fun delete(uid: String): ProfileWriteResult

    /** The names and photo of the profile as it is now, read BEFORE it is deleted; null if it cannot be read. */
    suspend fun read(uid: String): ProfileSeed?
}

/** What an older profile holds, kept to pre-fill the steps when it has to be created again (nothing else is kept). */
class ProfileSeed(
    val firstName: String,
    val lastName: String,
    val avatarJpeg: ByteArray?,
)

/**
 * The Firestore operations used by the Onboarding, ALL of them transactions (never a plain `set`, see
 * [UserProfileRepository]). Behind an interface so that the logic of the repository (checks, time limit, error wording)
 * is tested with a fake, without Firestore.
 */
interface UsersTransactions {
    suspend fun create(
        uid: String,
        data: Map<String, Any?>,
    )

    suspend fun updatePhone(
        uid: String,
        phone: String,
    )

    suspend fun delete(uid: String)

    /** The fields of the profile (the photo as bytes), or null if there is no profile. */
    suspend fun read(uid: String): Map<String, Any?>?
}

class FirestoreUsersTransactions(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : UsersTransactions {
    // The instance is resolved at each use: it is replaced after a cache purge.
    private fun ref(uid: String) = firestore().collection("users").document(uid)

    override suspend fun create(
        uid: String,
        data: Map<String, Any?>,
    ) {
        val ref = ref(uid)
        firestore()
            .runTransaction { transaction ->
                transaction.set(ref, data)
                Unit
            }.await()
    }

    override suspend fun updatePhone(
        uid: String,
        phone: String,
    ) {
        val ref = ref(uid)
        firestore()
            .runTransaction { transaction ->
                transaction.update(ref, "phone", phone)
                Unit
            }.await()
    }

    override suspend fun delete(uid: String) {
        val ref = ref(uid)
        firestore()
            .runTransaction { transaction ->
                transaction.delete(ref)
                Unit
            }.await()
    }

    override suspend fun read(uid: String): Map<String, Any?>? {
        val snapshot = ref(uid).get().await()
        if (!snapshot.exists()) return null
        return snapshot.data?.mapValues { (_, value) -> (value as? Blob)?.toBytes() ?: value }
    }
}

class FirestoreUserProfileRepository(
    private val transactions: UsersTransactions = FirestoreUsersTransactions(),
    private val timeoutMillis: Long = WRITE_TIMEOUT_MS,
) : UserProfileRepository {
    override suspend fun create(
        uid: String,
        firstName: String,
        lastName: String,
        avatarJpeg: ByteArray?,
        phone: String,
        consent: PendingConsent,
    ): ProfileWriteResult {
        require(consent.uid == uid) { "consent of another account" }
        val data = ProfileDocument.forCreation(firstName, lastName, avatarJpeg, phone, consent)
        return guarded { transactions.create(uid, data) }
    }

    override suspend fun addPhone(
        uid: String,
        phone: String,
    ): ProfileWriteResult {
        require(ProfileDocument.E164.matches(phone)) { "phone is not E.164" }
        return guarded { transactions.updatePhone(uid, phone) }
    }

    override suspend fun delete(uid: String): ProfileWriteResult = guarded { transactions.delete(uid) }

    override suspend fun read(uid: String): ProfileSeed? =
        try {
            withTimeout(timeoutMillis) { transactions.read(uid) }?.let(::seedOf)
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) null else throw e
        } catch (e: Exception) {
            null
        }

    private suspend fun guarded(write: suspend () -> Unit): ProfileWriteResult =
        try {
            withTimeout(timeoutMillis) { write() }
            ProfileWriteResult.DONE
        } catch (e: TimeoutCancellationException) {
            ProfileWriteResult.OFFLINE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            interpretWriteError(e)
        }

    companion object {
        /** Long enough for a slow network, short enough not to leave the person watching a spinner. */
        const val WRITE_TIMEOUT_MS = 20_000L

        /** Names and photo of an existing profile, if they have the shape the rules require; otherwise null. */
        fun seedOf(fields: Map<String, Any?>): ProfileSeed? {
            val first = fields["firstName"] as? String ?: return null
            val last = fields["lastName"] as? String ?: return null
            val avatar = (fields["avatar"] as? ByteArray)?.takeIf { it.size in 1..ProfileDocument.MAX_AVATAR_BYTES }
            return ProfileSeed(first, last, avatar)
        }
    }
}

/** Never exposes the exception, its message or its code: they can quote the document. */
fun interpretWriteError(error: Throwable): ProfileWriteResult {
    val code = (error as? FirebaseFirestoreException)?.code ?: return ProfileWriteResult.FAILED
    return when (code) {
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.CANCELLED,
        -> ProfileWriteResult.OFFLINE

        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        FirebaseFirestoreException.Code.UNAUTHENTICATED,
        -> ProfileWriteResult.REFUSED

        else -> ProfileWriteResult.FAILED
    }
}
