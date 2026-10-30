package com.tomessa.miff.data.auth

import com.google.firebase.firestore.FirebaseFirestore
import com.tomessa.miff.domain.auth.AccountSnapshot
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
                        AccountSnapshot(
                            profileExists = false,
                            hasMembership = false,
                            analyticsConsentEnabled = false,
                            hasPhone = false,
                            hasTermsVersion = false,
                        ),
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
                            // Only the PRESENCE is read: the number itself is not kept in memory here.
                            hasPhone = profile.getString("phone") != null,
                            hasTermsVersion = profile.getString("termsVersion") != null,
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
