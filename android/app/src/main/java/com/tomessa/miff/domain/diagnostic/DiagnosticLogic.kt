package com.tomessa.miff.domain.diagnostic

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException

/** Firebase project this app must talk to. Anything else must be flagged on the diagnostic screen. */
const val EXPECTED_FIREBASE_PROJECT_ID = "miff-3d283"

fun isExpectedProject(projectId: String?): Boolean = projectId == EXPECTED_FIREBASE_PROJECT_ID

/** Result of an unauthenticated read of a Miff document (Test 1). */
enum class RulesProbeOutcome {
    /** Read refused with permission-denied: the security rules protect the data. This is the expected result. */
    RULES_ACTIVE,

    /** The read succeeded without being signed in: the rules are open. Must be flagged without ambiguity. */
    RULES_OPEN,

    /** No network (or server unreachable): the test could not be done. */
    OFFLINE,

    /** Anything else (App Check, quota, unknown). */
    INCONCLUSIVE,
}

/** Interprets the failure of the unauthenticated read. A successful read is [RulesProbeOutcome.RULES_OPEN]. */
fun interpretRulesProbeFailure(error: Throwable): RulesProbeOutcome =
    when (error) {
        is FirebaseFirestoreException -> {
            when (error.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> RulesProbeOutcome.RULES_ACTIVE

                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                -> RulesProbeOutcome.OFFLINE

                else -> RulesProbeOutcome.INCONCLUSIVE
            }
        }

        is FirebaseNetworkException, is IOException -> {
            RulesProbeOutcome.OFFLINE
        }

        else -> {
            RulesProbeOutcome.INCONCLUSIVE
        }
    }

/** True when a Firestore failure only means "no connection for now" (shown with a calm message). */
fun isOfflineFailure(error: Throwable): Boolean = interpretRulesProbeFailure(error) == RulesProbeOutcome.OFFLINE

/** Offer shown for `entitlements/{uid}`. A missing document or an inactive/expired one means Classic (free). */
sealed interface OfferState {
    data object Classic : OfferState

    data class Premium(
        val expiresAtMillis: Long,
    ) : OfferState
}

/**
 * Reads the premium status the same way the security rules do: active == true and expiresAt in the future.
 * Missing document, missing fields or an expired date all give Classic.
 */
fun interpretEntitlement(
    documentExists: Boolean,
    active: Boolean?,
    expiresAtMillis: Long?,
    nowMillis: Long,
): OfferState =
    if (documentExists && active == true && expiresAtMillis != null && expiresAtMillis > nowMillis) {
        OfferState.Premium(expiresAtMillis)
    } else {
        OfferState.Classic
    }
