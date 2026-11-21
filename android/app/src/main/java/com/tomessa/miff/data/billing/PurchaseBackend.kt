package com.tomessa.miff.data.billing

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/** The random purchase id of the server for this account, as a UUID (36 characters), or why it could not be had. */
sealed interface PrepareResult {
    class Ready(
        val purchaseId: String,
    ) : PrepareResult {
        override fun toString(): String = "Ready"
    }

    /** No connection, or the service did not answer: nothing was started, the person can try again. */
    data object Unavailable : PrepareResult

    /** The server refused (not signed in, App Check): nothing to retry in a loop. */
    data object Refused : PrepareResult
}

/** The answer of the server to "check this purchase and give me what it is worth". */
sealed interface SyncResult {
    /** Checked. [state] is the state the server computed: trial, active, canceled, grace, on_hold, expired, revoked, or pending (paid later: NOTHING granted). */
    class Synced(
        val state: String?,
    ) : SyncResult {
        override fun toString(): String = "Synced($state)"
    }

    /** The purchase belongs to ANOTHER Miff account (one purchase = one account). Nothing is said about that account. */
    data object AlreadyLinked : SyncResult

    /** One check per minute and per account: asked again too soon. */
    data object RateLimited : SyncResult

    /** The server does not accept this purchase (unknown product, other package, family sharing, unreadable token): a retry would change nothing. */
    data object Rejected : SyncResult

    /** No connection, the service is momentarily down, or the call did not pass App Check: the purchase is checked again later. */
    data object Unavailable : SyncResult
}

/** The functions of the Miff server that deal with purchases (backend/functions/src/preparerAchat.ts, synchroniserAchat.ts, supprimerDonneesAbonnement.ts). */
interface PurchaseBackend {
    suspend fun preparePurchaseId(): PrepareResult

    suspend fun sync(purchaseToken: String): SyncResult

    /**
     * At the deletion of the account, BEFORE the authentication account goes (the function needs the sign-in): the server deletes the purchase id and the links of the account.
     * It does NOT cancel the subscription in the store. A failure is TOLERATED by the caller (what is left is purged by the TTL rules): returns false, never throws.
     */
    suspend fun purgeSubscriptionData(): Boolean
}

/**
 * The three CALLABLE functions of billing, in the region of the project (europe-west9). The library attaches the Firebase Authentication token and the App Check token to
 * each call; the server REFUSES a call without a signed-in account or without App Check, and anonymous accounts. Nothing here is a secret: the identifiers are public, no key
 * is in the app, and the purchase token goes ONLY in the body of the call over HTTPS (never in a URL, a log or an event). The server never takes an identity from this body:
 * the account is the one that is signed in.
 */
class FirebasePurchaseBackend(
    private val functions: () -> FirebaseFunctions = { FirebaseFunctions.getInstance(REGION) },
) : PurchaseBackend {
    override suspend fun preparePurchaseId(): PrepareResult =
        try {
            val data = call(PREPARE, null)
            val id = (data as? Map<*, *>)?.get("purchaseId") as? String
            if (id != null && isPurchaseId(id)) PrepareResult.Ready(id) else PrepareResult.Unavailable
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFunctionsException) {
            if (isRefusal(e.code)) PrepareResult.Refused else PrepareResult.Unavailable
        } catch (e: Exception) {
            PrepareResult.Unavailable
        }

    override suspend fun sync(purchaseToken: String): SyncResult =
        try {
            val data = call(SYNC, mapOf("store" to "google_play", "purchaseToken" to purchaseToken))
            SyncResult.Synced((data as? Map<*, *>)?.get("state") as? String)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFunctionsException) {
            syncFailure(e.code)
        } catch (e: Exception) {
            SyncResult.Unavailable
        }

    override suspend fun purgeSubscriptionData(): Boolean =
        try {
            call(PURGE, null)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    private suspend fun call(
        name: String,
        payload: Any?,
    ): Any? =
        functions()
            .getHttpsCallable(name)
            .withTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .call(payload)
            .await()
            .data

    companion object {
        /** The region of the functions (backend/functions/src/options.ts). */
        const val REGION = "europe-west9"
        const val PREPARE = "preparerAchat"
        const val SYNC = "synchroniserAchat"
        const val PURGE = "supprimerDonneesAbonnement"
        private const val TIMEOUT_SECONDS = 30L
        private val UUID_SHAPE = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        /** What the server returns is data from the network: only a UUID is accepted (it is then given to Google Play as the obfuscated account id). */
        fun isPurchaseId(value: String): Boolean = UUID_SHAPE.matches(value)

        /** Not signed in, no App Check, no right: retrying changes nothing. */
        fun isRefusal(code: FirebaseFunctionsException.Code): Boolean =
            code == FirebaseFunctionsException.Code.UNAUTHENTICATED ||
                code == FirebaseFunctionsException.Code.PERMISSION_DENIED

        /** The codes of backend/functions/src/achatErreurs.ts, as the callable library reports them. */
        fun syncFailure(code: FirebaseFunctionsException.Code): SyncResult =
            when (code) {
                FirebaseFunctionsException.Code.ALREADY_EXISTS -> SyncResult.AlreadyLinked

                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> SyncResult.RateLimited

                FirebaseFunctionsException.Code.INVALID_ARGUMENT,
                FirebaseFunctionsException.Code.NOT_FOUND,
                FirebaseFunctionsException.Code.FAILED_PRECONDITION,
                -> SyncResult.Rejected

                else -> SyncResult.Unavailable
            }
    }
}
