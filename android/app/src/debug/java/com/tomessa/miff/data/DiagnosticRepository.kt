package com.tomessa.miff.data

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.tomessa.miff.domain.auth.SignInFailure
import com.tomessa.miff.domain.auth.interpretSignInError
import com.tomessa.miff.domain.diagnostic.OfferState
import com.tomessa.miff.domain.diagnostic.RulesProbeOutcome
import com.tomessa.miff.domain.diagnostic.interpretEntitlement
import com.tomessa.miff.domain.diagnostic.interpretRulesProbeFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

sealed interface Test2Result {
    data class Success(
        val profileExists: Boolean,
        val offer: OfferState,
    ) : Test2Result

    data class SignInFailed(
        val failure: SignInFailure,
    ) : Test2Result

    /** Signed in, but the rules refused the read of the user's own documents. */
    data object ReadDenied : Test2Result

    data object ReadFailed : Test2Result
}

sealed interface AppCheckOutcome {
    data object TokenObtained : AppCheckOutcome

    data object NoToken : AppCheckOutcome
}

/** Everything the diagnostic screen asks of Firebase. READ-ONLY: nothing here writes to Firestore. */
interface DiagnosticRepository {
    val firebaseProjectId: String?

    suspend fun probeRulesUnauthenticated(): RulesProbeOutcome

    suspend fun signInAndReadOwnDocuments(
        email: String,
        password: String,
    ): Test2Result

    suspend fun fetchAppCheckToken(): AppCheckOutcome
}

class FirebaseDiagnosticRepository : DiagnosticRepository {
    private val auth get() = FirebaseAuth.getInstance()
    private val firestore get() = FirebaseFirestore.getInstance()

    override val firebaseProjectId: String? get() = FirebaseApp.getInstance().options.projectId

    override suspend fun probeRulesUnauthenticated(): RulesProbeOutcome {
        // Test 1 must really be anonymous: leave any previous session first.
        auth.signOut()
        return try {
            withTimeout(NETWORK_TIMEOUT_MS) {
                firestore
                    .collection("miffs")
                    .document(PROBE_MIFF_ID)
                    .get(Source.SERVER)
                    .await()
            }
            RulesProbeOutcome.RULES_OPEN
        } catch (e: TimeoutCancellationException) {
            RulesProbeOutcome.OFFLINE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            interpretRulesProbeFailure(e)
        }
    }

    override suspend fun signInAndReadOwnDocuments(
        email: String,
        password: String,
    ): Test2Result {
        val uid: String? =
            try {
                withTimeout(NETWORK_TIMEOUT_MS) {
                    auth
                        .signInWithEmailAndPassword(email.trim(), password)
                        .await()
                        .user
                        ?.uid
                }
            } catch (e: TimeoutCancellationException) {
                return Test2Result.SignInFailed(SignInFailure.NETWORK)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return Test2Result.SignInFailed(interpretSignInError(e))
            }
        if (uid == null) return Test2Result.SignInFailed(SignInFailure.UNKNOWN)

        return try {
            withTimeout(NETWORK_TIMEOUT_MS) {
                // Read only: users/{uid} and entitlements/{uid}. An absent document is a normal result.
                val profile =
                    firestore
                        .collection("users")
                        .document(uid)
                        .get(Source.SERVER)
                        .await()
                val entitlement =
                    firestore
                        .collection("entitlements")
                        .document(uid)
                        .get(Source.SERVER)
                        .await()
                Test2Result.Success(
                    profileExists = profile.exists(),
                    offer =
                        interpretEntitlement(
                            documentExists = entitlement.exists(),
                            active = entitlement.getBoolean("active"),
                            expiresAtMillis = entitlement.getTimestamp("expiresAt")?.toDate()?.time,
                            nowMillis = System.currentTimeMillis(),
                        ),
                )
            }
        } catch (e: TimeoutCancellationException) {
            Test2Result.ReadFailed
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (e.code ==
                FirebaseFirestoreException.Code.PERMISSION_DENIED
            ) {
                Test2Result.ReadDenied
            } else {
                Test2Result.ReadFailed
            }
        } catch (e: Exception) {
            Test2Result.ReadFailed
        } finally {
            // The test account session is not kept on the device.
            auth.signOut()
        }
    }

    override suspend fun fetchAppCheckToken(): AppCheckOutcome =
        try {
            withTimeout(NETWORK_TIMEOUT_MS) {
                FirebaseAppCheck.getInstance().getAppCheckToken(false).await()
            }
            AppCheckOutcome.TokenObtained
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) AppCheckOutcome.NoToken else throw e
        } catch (e: Exception) {
            AppCheckOutcome.NoToken
        }

    private companion object {
        /** Identifier shaped like a Miff id (20 alphanumeric characters) that does not exist. */
        const val PROBE_MIFF_ID = "DIAGNOSTICPROBE00000"
        const val NETWORK_TIMEOUT_MS = 15_000L
    }
}
