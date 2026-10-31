package com.tomessa.miff.domain.miff

import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Why an action on a Miff did not work, reduced to what the screen can say. It carries no message, no code, no document:
 * Firestore messages can quote a path or a value (ticket Miff criterion V2).
 */
enum class MiffFailure {
    /** The server could not be reached (no network, too slow), or answered "quota" / "App Check": same calm sentence (H4). */
    OFFLINE,

    /** The server answered "no" to a write (rules). */
    REFUSED,

    /** The profile is missing (or has no phone number): the person goes back to the profile step, never a "code" message. */
    NO_PROFILE,

    /** Anything else. */
    UNKNOWN,
}

/** Reads the answer of Firestore as a [MiffFailure] (never exposes the exception). */
fun interpretMiffError(error: Throwable): MiffFailure {
    val code = (error as? FirebaseFirestoreException)?.code ?: return MiffFailure.UNKNOWN
    return when (code) {
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.CANCELLED,
        // "Quota" is never said to the person: it is the same sentence as "no connection" (criterion H4).
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
        -> MiffFailure.OFFLINE

        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        FirebaseFirestoreException.Code.UNAUTHENTICATED,
        -> MiffFailure.REFUSED

        else -> MiffFailure.UNKNOWN
    }
}

/**
 * Did the listener or the read of a Miff just say that the access is gone (ticket 5.7)? ONLY a refusal of the rules counts, or
 * a Miff that does not exist. A network error is not a loss of access: nobody is told they were removed because the phone is
 * in airplane mode (criterion D3).
 */
fun isAccessLostError(error: Throwable): Boolean =
    (error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
