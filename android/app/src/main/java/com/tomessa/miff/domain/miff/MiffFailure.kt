package com.tomessa.miff.domain.miff

import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Why an action on a Miff did not work, reduced to what the screen can say. It carries no message, no code, no document:
 * Firestore messages can quote a path or a value (ticket Miff criterion V2).
 */
enum class MiffFailure {
    /**
     * The server could not be reached (no network, too slow), or answered "quota exhausted": the same calm sentence, never the
     * word "quota" (criterion H4).
     */
    OFFLINE,

    /**
     * The server answered "no" to a write or a read. DECISION (H4): a refusal of App Check has the same code as a refusal of the
     * rules (both are PERMISSION_DENIED), so by itself it gets the generic "Ça n'a pas fonctionné. Réessaie dans un instant." and
     * not the "no connection" sentence, which would be false. [DEVICE_NOT_RECOGNIZED] is the one case that can be told apart, see
     * [refineRefusal].
     */
    REFUSED,

    /**
     * AC-4: a refusal of the server on a phone that holds NO App Check token. A DEDUCTION, not a certainty (the code is the same
     * as for the rules, and the text of the error is not documented): it is only given by [refineRefusal], only to the screens
     * "Créer" and "Rejoindre", and the sentence is calm: "Cet appareil n'est pas reconnu pour le moment. Mets Miff à jour depuis
     * le store, puis réessaie." Everything that tests [REFUSED] in the logic (position tracking, places, notifications) never
     * sees this value.
     */
    DEVICE_NOT_RECOGNIZED,

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
        // "Quota" is never said to the person: it is the same sentence as "no connection" (criterion H4, see [MiffFailure.OFFLINE]).
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
        -> MiffFailure.OFFLINE

        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        FirebaseFirestoreException.Code.UNAUTHENTICATED,
        -> MiffFailure.REFUSED

        else -> MiffFailure.UNKNOWN
    }
}

/**
 * AC-4: a refusal ([MiffFailure.REFUSED]) while the phone holds no App Check token ([tokenIsMissing]) is, most likely, the refusal
 * of App Check: "this device is not recognized". Anything else is returned as it is: with a token, or when the token could not be
 * checked, the answer of before stays (never a new sentence on a doubt), and a network problem is never a refusal.
 *
 * KNOWN LIMIT: [interpretMiffError] puts UNAUTHENTICATED in [MiffFailure.REFUSED] with PERMISSION_DENIED, so an expired session without a
 * token can also be called "not recognized" (calm sentence, no harm; the loss of a session is handled elsewhere).
 */
fun refineRefusal(
    failure: MiffFailure,
    tokenIsMissing: Boolean,
): MiffFailure = if (failure == MiffFailure.REFUSED && tokenIsMissing) MiffFailure.DEVICE_NOT_RECOGNIZED else failure

/**
 * Did the listener or the read of a Miff just say that the access is gone (ticket 5.7)? ONLY a refusal of the rules counts, or
 * a Miff that does not exist. A network error is not a loss of access: nobody is told they were removed because the phone is
 * in airplane mode (criterion D3).
 */
fun isAccessLostError(error: Throwable): Boolean =
    (error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
