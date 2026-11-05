package com.tomessa.miff.data.auth

import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.tomessa.miff.domain.analytics.AnalyticsEvent
import com.tomessa.miff.domain.auth.AccountSignIn
import com.tomessa.miff.domain.auth.AuthAttempt
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.AuthOutcome
import com.tomessa.miff.domain.auth.SessionCheck
import com.tomessa.miff.domain.auth.SignInProvider
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

    /**
     * The same proof for the deletion of the account (AUTH-5): the errors are read as errors on the OPEN session (wrong Google account,
     * session expired...). The refusal of the consent panel keeps [reauthenticateWithGoogle].
     */
    suspend fun reauthenticateWithGoogleForDeletion(idToken: String): AuthFailure?

    /**
     * Proves who the person is again with the password of the account (AUTH-5). Null = done. The password goes to Firebase and
     * nowhere else: it is not kept, not logged, not part of any message of ours.
     */
    suspend fun reauthenticateWithPassword(password: String): AuthFailure?

    /** Deletes the current account. Null = done (also when there is no account any more). */
    suspend fun deleteCurrentAccount(): AuthFailure?

    /** The sign-in methods linked to the current account (empty if nobody is signed in). */
    fun linkedProviders(): Set<SignInProvider>

    /**
     * Asks Firebase whether the session of the current account still exists (one `reload`). Used by the deletion of the account:
     * when a step is refused, "the account is gone" means the deletion is already done (AUTH-5, criterion SC28).
     */
    suspend fun checkSession(): SessionCheck

    /** How the current account signs in; null if no one is signed in. */
    fun signInMethod(): AnalyticsEvent.Method?

    /**
     * The name given by the sign-in provider (Google), if any. It only pre-fills the profile step, where the person can
     * change it: never stored by itself, never logged. Null for an e-mail account.
     */
    fun displayName(): String?

    /**
     * The e-mail address of the account (an Apple relay address is returned as it is), only to be SHOWN in Settings > Mon
     * compte. Never stored (not in Firestore, not in an event, not in a log). Null if nobody is signed in.
     */
    fun currentEmail(): String?

    /** Does this account have a password in Miff (e-mail), or does it sign in with Google? Decides the "Mon compte" screen. */
    fun accountSignIn(): AccountSignIn

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

    override suspend fun reauthenticateWithGoogleForDeletion(idToken: String): AuthFailure? =
        runCatchingAuth(AuthAttempt.REAUTH_SOCIAL) {
            val user = auth.currentUser ?: throw IllegalStateException("no session")
            user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
        }

    override suspend fun reauthenticateWithPassword(password: String): AuthFailure? {
        val user = auth.currentUser
        val email = user?.email
        if (user == null || email.isNullOrBlank()) return AuthFailure.UNKNOWN
        return runCatchingAuth(AuthAttempt.REAUTH_EMAIL) {
            user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        }
    }

    override suspend fun deleteCurrentAccount(): AuthFailure? =
        runCatchingAuth(AuthAttempt.DELETE_ACCOUNT) {
            val user = auth.currentUser ?: return@runCatchingAuth
            user.delete().await()
        }

    override fun linkedProviders(): Set<SignInProvider> =
        providersFrom(
            auth.currentUser
                ?.providerData
                .orEmpty()
                .map { it.providerId },
        )

    override suspend fun checkSession(): SessionCheck {
        // Nobody signed in: the account may exist or not, we cannot tell: never "gone".
        val user = auth.currentUser ?: return SessionCheck.EXPIRED
        return sessionCheckOf(runCatchingAuth(AuthAttempt.CHECK_SESSION) { user.reload().await() })
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

    override fun displayName(): String? = auth.currentUser?.displayName

    override fun currentEmail(): String? = auth.currentUser?.email?.takeIf { it.isNotBlank() }

    override fun accountSignIn(): AccountSignIn =
        when (signInMethod()) {
            AnalyticsEvent.Method.EMAIL -> AccountSignIn.PASSWORD
            AnalyticsEvent.Method.GOOGLE -> AccountSignIn.GOOGLE
            else -> AccountSignIn.OTHER
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

    internal companion object {
        const val APPLE_PROVIDER_ID = "apple.com"

        /** The sign-in methods Miff knows, from the provider ids Firebase holds for the user. */
        fun providersFrom(providerIds: List<String>): Set<SignInProvider> =
            providerIds
                .mapNotNull {
                    when (it) {
                        EmailAuthProvider.PROVIDER_ID -> SignInProvider.PASSWORD
                        GoogleAuthProvider.PROVIDER_ID -> SignInProvider.GOOGLE
                        APPLE_PROVIDER_ID -> SignInProvider.APPLE
                        else -> null
                    }
                }.toSet()

        /** What a `reload` answer means: only `ERROR_USER_NOT_FOUND` says "gone"; an expired or revoked token says "sign in again". */
        fun sessionCheckOf(failure: AuthFailure?): SessionCheck =
            when (failure) {
                null -> SessionCheck.VALID
                AuthFailure.ACCOUNT_GONE -> SessionCheck.GONE
                AuthFailure.SESSION_EXPIRED -> SessionCheck.EXPIRED
                else -> SessionCheck.UNKNOWN
            }
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
