package com.tomessa.miff.data.auth

import com.google.firebase.firestore.FirebaseFirestore
import com.tomessa.miff.domain.auth.AccountSnapshot
import com.tomessa.miff.domain.auth.PendingConsent
import com.tomessa.miff.domain.auth.ProfileDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

sealed interface AccountLoad {
    data class Loaded(
        val snapshot: AccountSnapshot,
    ) : AccountLoad

    /** The account state could not be read (offline, refused, slow). The session is kept; the user can retry. */
    data object Unavailable : AccountLoad
}

/** The two cheap reads that decide where the app opens (ticket section 5). Read only. */
interface AccountRepository {
    suspend fun load(uid: String): AccountLoad
}

class FirestoreAccountRepository(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : AccountRepository {
    override suspend fun load(uid: String): AccountLoad =
        try {
            withTimeout(READ_TIMEOUT_MS) {
                // The Firestore instance is resolved here, not stored: it is replaced after a cache purge.
                val users = firestore().collection("users")
                val profile = users.document(uid).get().await()
                if (!profile.exists()) {
                    AccountLoad.Loaded(
                        AccountSnapshot(profileExists = false, hasMembership = false, analyticsConsentEnabled = false),
                    )
                } else {
                    val memberships =
                        users
                            .document(uid)
                            .collection("memberships")
                            .limit(1)
                            .get()
                            .await()
                    AccountLoad.Loaded(
                        AccountSnapshot(
                            profileExists = true,
                            hasMembership = !memberships.isEmpty,
                            analyticsConsentEnabled = profile.getBoolean("analyticsConsent.enabled") == true,
                        ),
                    )
                }
            }
        } catch (e: TimeoutCancellationException) {
            AccountLoad.Unavailable
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AccountLoad.Unavailable
        }

    private companion object {
        const val READ_TIMEOUT_MS = 15_000L
    }
}

/**
 * Creation of `users/{uid}`: ONE `set` with every key, server time for the dates (rules AUTH-B). Called by the profile
 * screen of the Onboarding ticket; the document shape and its tests live in [ProfileDocument].
 */
interface UserProfileRepository {
    suspend fun create(
        uid: String,
        firstName: String,
        lastName: String,
        avatarJpeg: ByteArray?,
        consent: PendingConsent,
    )
}

class FirestoreUserProfileRepository(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : UserProfileRepository {
    /** Completes when the server has accepted the write (offline, Firestore keeps it queued and this waits). */
    override suspend fun create(
        uid: String,
        firstName: String,
        lastName: String,
        avatarJpeg: ByteArray?,
        consent: PendingConsent,
    ) {
        require(consent.uid == uid) { "consent of another account" }
        val data = ProfileDocument.forCreation(firstName, lastName, avatarJpeg, consent)
        firestore()
            .collection("users")
            .document(uid)
            .set(data)
            .await()
    }
}
