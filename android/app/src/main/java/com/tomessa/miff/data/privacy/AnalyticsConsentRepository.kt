package com.tomessa.miff.data.privacy

import com.google.firebase.firestore.FirebaseFirestore
import com.tomessa.miff.data.auth.ProfileWriteResult
import com.tomessa.miff.data.auth.interpretWriteError
import com.tomessa.miff.domain.privacy.AnalyticsConsentTransition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Writes the choice "statistiques d'usage et rapports de plantage anonymes" to the ACCOUNT (`users/{uid}.analyticsConsent`).
 * The choice of the account is the one that counts at the next start of the app (see `SessionManager`).
 */
interface AnalyticsConsentRepository {
    /** [enable] true = the person accepts, false = withdraws. Done also when the account already was in that state. */
    suspend fun set(
        uid: String,
        enable: Boolean,
    ): ProfileWriteResult
}

/** The Firestore transaction, behind an interface so that the rule "what is written" is tested without Firestore. */
interface ConsentTransactions {
    /** Reads the current block, calls [decide], writes what it answers (null = nothing), all in ONE transaction. */
    suspend fun run(
        uid: String,
        decide: (previous: Map<String, Any?>?) -> Map<String, Any?>?,
    )
}

class FirestoreConsentTransactions(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : ConsentTransactions {
    @Suppress("UNCHECKED_CAST")
    override suspend fun run(
        uid: String,
        decide: (previous: Map<String, Any?>?) -> Map<String, Any?>?,
    ) {
        val db = firestore()
        val ref = db.collection("users").document(uid)
        db
            .runTransaction { transaction ->
                val snapshot = transaction.get(ref)
                val previous = snapshot.get("analyticsConsent") as? Map<String, Any?>
                decide(previous)?.let { transaction.update(ref, "analyticsConsent", it) }
                Unit
            }.await()
    }
}

/**
 * A transaction on purpose (like every write of the profile): it exists only online and leaves nothing queued behind it, so
 * "no confirmation" always means "nothing written", and a withdrawal can never leave later, by itself, after the person has
 * signed out. Time limit of 20 s, then "no network". No value is ever put in an error.
 */
class FirestoreAnalyticsConsentRepository(
    private val transactions: ConsentTransactions = FirestoreConsentTransactions(),
    private val timeoutMillis: Long = TIMEOUT_MS,
) : AnalyticsConsentRepository {
    override suspend fun set(
        uid: String,
        enable: Boolean,
    ): ProfileWriteResult {
        var refused = false
        return try {
            withTimeout(timeoutMillis) {
                transactions.run(uid) { previous ->
                    when (val change = AnalyticsConsentTransition.change(previous, enable)) {
                        is AnalyticsConsentTransition.Change.Write -> {
                            change.block
                        }

                        AnalyticsConsentTransition.Change.None -> {
                            null
                        }

                        AnalyticsConsentTransition.Change.Refused -> {
                            refused = true
                            null
                        }
                    }
                }
            }
            if (refused) ProfileWriteResult.REFUSED else ProfileWriteResult.DONE
        } catch (e: TimeoutCancellationException) {
            ProfileWriteResult.OFFLINE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            interpretWriteError(e)
        }
    }

    companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
