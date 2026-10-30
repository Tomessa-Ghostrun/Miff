package com.tomessa.miff.data.auth

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
}

class FirestoreUserProfileRepository(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
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
        return guarded {
            val db = firestore()
            val ref = db.collection("users").document(uid)
            db
                .runTransaction { transaction ->
                    transaction.set(ref, data)
                    Unit
                }.await()
        }
    }

    override suspend fun addPhone(
        uid: String,
        phone: String,
    ): ProfileWriteResult {
        require(ProfileDocument.E164.matches(phone)) { "phone is not E.164" }
        return guarded {
            val db = firestore()
            val ref = db.collection("users").document(uid)
            db
                .runTransaction { transaction ->
                    transaction.update(ref, "phone", phone)
                    Unit
                }.await()
        }
    }

    override suspend fun delete(uid: String): ProfileWriteResult =
        guarded {
            val db = firestore()
            val ref = db.collection("users").document(uid)
            db
                .runTransaction { transaction ->
                    transaction.delete(ref)
                    Unit
                }.await()
        }

    private suspend fun guarded(write: suspend () -> Unit): ProfileWriteResult =
        try {
            withTimeout(WRITE_TIMEOUT_MS) { write() }
            ProfileWriteResult.DONE
        } catch (e: TimeoutCancellationException) {
            ProfileWriteResult.OFFLINE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            interpretWriteError(e)
        }

    private companion object {
        /** Long enough for a slow network, short enough not to leave the person watching a spinner. */
        const val WRITE_TIMEOUT_MS = 20_000L
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
