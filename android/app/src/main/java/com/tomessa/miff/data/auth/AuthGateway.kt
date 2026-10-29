package com.tomessa.miff.data.auth

import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.tomessa.miff.domain.analytics.AnalyticsEvent
import com.tomessa.miff.domain.auth.AuthAttempt
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.AuthOutcome
import com.tomessa.miff.domain.auth.interpretAuthError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/**
 * Everything the app asks of Firebase Authentication. The password goes from the form to Firebase and nowhere else:
 * it is never stored, never logged, never put in an exception message of ours.
 */
interface AuthGateway {
    val currentUid: String?

    /** Emits the user id at each change (null = signed out). Used to notice a session lost behind our back. */
    fun uidChanges(): Flow<String?>

    suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AuthOutcome

    suspend fun createAccountWithEmail(
        email: String,
        password: String,
    ): AuthOutcome

    suspend fun signInWithGoogle(idToken: String): AuthOutcome

    /** Null = done. */
    suspend fun reauthenticateWithGoogle(idToken: String): AuthFailure?

    /** Deletes the current account. Null = done (also when there is no account any more). */
    suspend fun deleteCurrentAccount(): AuthFailure?

    /** How the current account signs in; null if no one is signed in. */
    fun signInMethod(): AnalyticsEvent.Method?

    fun signOut()
}

class FirebaseAuthGateway(
    private val authProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() },
) : AuthGateway {
    // Resolved at each use: never keep an instance across a sign-out / cache purge.
    private val auth get() = authProvider()

    override val currentUid: String? get() = auth.currentUser?.uid

    override fun uidChanges(): Flow<String?> =
        callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        }.distinctUntilChanged()

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AuthOutcome = attempt(AuthAttempt.SIGN_IN_EMAIL) { auth.signInWithEmailAndPassword(email, password).await() }

    override suspend fun createAccountWithEmail(
        email: String,
        password: String,
    ): AuthOutcome = attempt(AuthAttempt.SIGN_UP_EMAIL) { auth.createUserWithEmailAndPassword(email, password).await() }

    override suspend fun signInWithGoogle(idToken: String): AuthOutcome =
        attempt(
            AuthAttempt.SOCIAL,
        ) { auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await() }

    override suspend fun reauthenticateWithGoogle(idToken: String): AuthFailure? =
        runCatchingAuth(AuthAttempt.SOCIAL) {
            val user = auth.currentUser ?: return@runCatchingAuth
            user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
        }

    override suspend fun deleteCurrentAccount(): AuthFailure? =
        runCatchingAuth(AuthAttempt.DELETE_ACCOUNT) {
            val user = auth.currentUser ?: return@runCatchingAuth
            user.delete().await()
        }

    override fun signInMethod(): AnalyticsEvent.Method? {
        val providers =
            auth.currentUser
                ?.providerData
                ?.map { it.providerId }
                .orEmpty()
        return when {
            auth.currentUser == null -> null
            GoogleAuthProvider.PROVIDER_ID in providers -> AnalyticsEvent.Method.GOOGLE
            else -> AnalyticsEvent.Method.EMAIL
        }
    }

    override fun signOut() = auth.signOut()

    // No timeout of our own on purpose: a request abandoned by a timeout may still succeed later and open a session
    // nobody is watching. Firebase fails fast when offline and has its own network limits; the callers wrap the whole
    // operation so that it always completes (see SessionManager).
    private suspend fun attempt(
        attempt: AuthAttempt,
        call: suspend () -> AuthResult,
    ): AuthOutcome =
        try {
            val result = call()
            val user = result.user
            if (user == null) {
                AuthOutcome.Failure(AuthFailure.UNKNOWN)
            } else {
                AuthOutcome.Success(uid = user.uid, isNewUser = result.additionalUserInfo?.isNewUser == true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthOutcome.Failure(interpretAuthError(e, attempt))
        }

    private suspend fun runCatchingAuth(
        attempt: AuthAttempt,
        call: suspend () -> Unit,
    ): AuthFailure? =
        try {
            call()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            interpretAuthError(e, attempt)
        }
}
