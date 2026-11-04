package com.tomessa.miff.data.auth

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.GoogleTokenResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull

/** Opens the Google account dialog and returns an ID token for Firebase. Bound to a screen (needs an Activity). */
interface GoogleIdTokenSource {
    suspend fun requestIdToken(): GoogleTokenResult
}

/**
 * The part that talks to the system (Credential Manager), cut out so that every outcome can be played in a unit test.
 * Returns the Google ID token, or null if the system answered with another kind of credential. Failures are thrown as
 * the exceptions of `androidx.credentials` (or `GoogleIdTokenParsingException`); [CredentialManagerGoogleSource]
 * turns them into a [GoogleTokenResult].
 */
fun interface GoogleCredentialClient {
    suspend fun fetchIdToken(webClientId: String): String?
}

/**
 * Credential Manager + "Sign in with Google" option (the explicit button flow recommended by Google).
 * [activityContext] MUST be an Activity: Credential Manager shows its dialog on top of it.
 */
class CredentialManagerClient(
    private val activityContext: Context,
) : GoogleCredentialClient {
    override suspend fun fetchIdToken(webClientId: String): String? {
        val request =
            GetCredentialRequest
                .Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
                .build()
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        return if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } else {
            null
        }
    }
}

/**
 * Turns the outcome of the Google dialog into a [GoogleTokenResult]. The ID token only travels to Firebase
 * Authentication; it is not stored nor logged, and neither are the account, the address or any message of the system
 * (an error is reduced to its KIND here, nothing of it is kept).
 *
 * [webClientId] is the OAuth *web* client of the Firebase project (`default_web_client_id`, generated from
 * google-services.json); without it the sign-in cannot start ([GoogleTokenResult.Unavailable]).
 */
class CredentialManagerGoogleSource(
    private val client: GoogleCredentialClient,
    private val webClientId: String?,
    private val dialogTimeoutMs: Long = DIALOG_TIMEOUT_MS,
    /** Monotonic clock in milliseconds; injected so that tests can place the cancellation on either side of the threshold. */
    private val nowMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) : GoogleIdTokenSource {
    override suspend fun requestIdToken(): GoogleTokenResult {
        val clientId = webClientId?.takeIf { it.isNotBlank() } ?: return GoogleTokenResult.Unavailable
        val startedAt = nowMillis()
        return try {
            // A dialog left open for ever must not freeze the screen: after this long, back to a stable state.
            withTimeoutOrNull(dialogTimeoutMs) {
                // Another kind of credential, or an empty token: the system answered, but not with what we need.
                client
                    .fetchIdToken(clientId)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { GoogleTokenResult.Token(it) }
                    ?: GoogleTokenResult.Failed(AuthFailure.UNKNOWN)
            } ?: GoogleTokenResult.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            // Android also reports some Google failures as a "cancellation" (status 16, e.g. account re-authentication that
            // failed). A person cannot close a dialog they have not even had time to see: a cancellation this quick is a
            // temporary failure, told as such. A slower one is a real closing and stays silent. The message of the
            // exception is never read (it is Google-internal, fragile, and could hold personal data).
            if (nowMillis() - startedAt < FAST_CANCELLATION_MS) {
                GoogleTokenResult.Failed(AuthFailure.GOOGLE_INTERRUPTED)
            } else {
                GoogleTokenResult.Cancelled
            }
        } catch (e: Exception) {
            googleFailureOf(e)
        }
    }

    companion object {
        private const val DIALOG_TIMEOUT_MS = 180_000L
        private const val NANOS_PER_MILLI = 1_000_000L

        /**
         * A cancellation that comes back less than this long after the request was made (strictly less) is NOT the person
         * closing the window: nobody can see the dialog and act on it in 1.5 s. Product default to be confirmed by the PM
         * on the OPPO Find X5 Lite; to be moved if real closings are told as failures (threshold too high) or if quiet
         * failures remain (too low). Exactly this value or more counts as a real closing.
         */
        const val FAST_CANCELLATION_MS = 1_500L

        /**
         * The web client id generated by the google-services plugin, looked up by name: if the configuration file has
         * no OAuth web client, the resource does not exist (and the build must not fail for that, for example for the
         * fake configuration of Dependabot). Null when absent.
         */
        fun webClientIdFrom(context: Context): String? {
            val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            return if (id == 0) null else context.getString(id)
        }
    }
}

/**
 * What an error of the Google dialog means for the person (only the KIND of the exception is read, never its message):
 * - the person closed the dialog: [GoogleTokenResult.Cancelled], no message at all (criterion S7). The caller
 *   ([CredentialManagerGoogleSource]) reclassifies a cancellation that comes back too quickly to be a human gesture;
 * - no usable Google account on the phone ([NoCredentialException]);
 * - Google services missing, too old or unable to serve this request (provider configuration / unsupported);
 * - Google did not answer this time ([GetCredentialInterruptedException]: Play services report their network and
 *   internal errors this way): try again;
 * - everything else (unknown error of the system, token that cannot be read, any other exception) is the honest generic
 *   [AuthFailure.UNKNOWN]: we do not know more, so we do not pretend to.
 */
internal fun googleFailureOf(error: Throwable): GoogleTokenResult =
    when (error) {
        is GetCredentialCancellationException -> GoogleTokenResult.Cancelled

        is NoCredentialException -> GoogleTokenResult.Failed(AuthFailure.GOOGLE_NO_ACCOUNT)

        is GetCredentialInterruptedException -> GoogleTokenResult.Failed(AuthFailure.GOOGLE_INTERRUPTED)

        is GetCredentialProviderConfigurationException,
        is GetCredentialUnsupportedException,
        -> GoogleTokenResult.Failed(AuthFailure.GOOGLE_SERVICES_UNAVAILABLE)

        else -> GoogleTokenResult.Failed(AuthFailure.UNKNOWN)
    }

/** Is there a network at all? Used to answer "no connection" at once instead of waiting for a long failure. */
interface NetworkMonitor {
    fun isOnline(): Boolean

    /**
     * Emits the current state, then every change (true = a network with Internet is there). Lets a background task wait for the
     * network to come back instead of asking again and again. The default is a single answer (tests, fakes).
     */
    fun onlineChanges(): Flow<Boolean> = flowOf(isOnline())
}

class AndroidNetworkMonitor(
    context: Context,
) : NetworkMonitor {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val network = manager?.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onlineChanges(): Flow<Boolean> =
        callbackFlow {
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(true)
                    }

                    override fun onLost(network: Network) {
                        trySend(isOnline())
                    }
                }
            trySend(isOnline())
            manager?.registerDefaultNetworkCallback(callback)
            awaitClose { manager?.unregisterNetworkCallback(callback) }
        }.distinctUntilChanged()
}
