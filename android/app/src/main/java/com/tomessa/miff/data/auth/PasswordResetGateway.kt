package com.tomessa.miff.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.tomessa.miff.domain.auth.PasswordResetOutcome
import com.tomessa.miff.domain.auth.interpretPasswordResetError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** Asks Firebase Authentication to send the "reset your password" e-mail. */
interface PasswordResetGateway {
    suspend fun sendResetEmail(email: String): PasswordResetOutcome
}

/**
 * `sendPasswordResetEmail` of Firebase Authentication, with NO custom action URL in the code: the address of the web page
 * where the new password is chosen (AUTH-W, `/reset-password`) is set by the PM in the Firebase console (Authentication >
 * Templates > Password reset > customise the action URL), once that page is deployed. Nothing about it lives in the app.
 *
 * The language of the e-mail follows the language of the phone (`useAppLanguage`): the link of the e-mail then carries
 * `lang=fr` or `lang=en`, which the web page uses. The address goes from the form to Firebase and nowhere else: never
 * stored, never logged.
 */
class FirebasePasswordResetGateway(
    private val authProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() },
) : PasswordResetGateway {
    override suspend fun sendResetEmail(email: String): PasswordResetOutcome =
        attemptPasswordReset {
            val auth = authProvider()
            auth.useAppLanguage()
            auth.sendPasswordResetEmail(email).await()
        }
}

/**
 * Runs [call] and sorts its result: it never throws (except to propagate a cancellation). Kept apart from the Firebase
 * instance so that the sorting of errors is tested with plain exceptions.
 */
suspend fun attemptPasswordReset(call: suspend () -> Unit): PasswordResetOutcome =
    try {
        call()
        PasswordResetOutcome.Accepted
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        interpretPasswordResetError(e)
    }
