package com.tomessa.miff.data

import com.google.android.gms.tasks.Task

/**
 * AC-4, DEBUG BUILDS ONLY. Makes the App Check SDK forget the last token it kept (valid up to one hour), so that the switch "without
 * App Check" really sends no token from the start of the launch.
 *
 * Why a trick: the SDK component is created EAGERLY by Firebase's content provider, BEFORE [android.app.Application.onCreate], and keeps the
 * token in memory; deleting its preferences file from onCreate is too late. `resetAppCheckState()` is public on the implementation class
 * (firebase-appcheck 20.0.0, `DefaultFirebaseAppCheck`) but not on the public `FirebaseAppCheck` API, hence the reflection. It is a
 * best effort: if the method is missing (a newer SDK), the answer is `false` and the written instruction applies (clear the data of the app
 * or wait more than one hour before the proof, and check the "unverified requests" in the App Check console).
 */
internal object StoredAppCheckToken {
    private const val RESET_METHOD = "resetAppCheckState"
    private const val RETRIEVAL_FIELD = "retrieveStoredTokenTask"

    /** Calls `resetAppCheckState()` on [target]; `true` only if it was found and ran without error. */
    fun forget(target: Any): Boolean =
        try {
            target.javaClass
                .getMethod(RESET_METHOD)
                .apply { isAccessible = true }
                .invoke(target)
            true
        } catch (e: Exception) {
            false
        }

    /**
     * The SDK reads the stored token in the background right after its creation and may set it AFTER a first reset: [forget] is therefore also
     * run once that reading is over. `null` when the field is not there (then only the first reset applies).
     */
    @Suppress("UNCHECKED_CAST")
    fun retrieval(target: Any): Task<Void>? =
        try {
            target.javaClass
                .getDeclaredField(RETRIEVAL_FIELD)
                .apply { isAccessible = true }
                .get(target) as? Task<Void>
        } catch (e: Exception) {
            null
        }
}
