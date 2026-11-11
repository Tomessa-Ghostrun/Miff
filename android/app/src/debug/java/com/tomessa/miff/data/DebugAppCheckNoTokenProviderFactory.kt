package com.tomessa.miff.data

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProvider
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.AppCheckToken

/**
 * AC-4, DEBUG BUILDS ONLY. A provider that never gives a token: every request leaves without an App Check token, exactly like a
 * copy of the app that cannot prove what it is. The SDK then sends the requests without the header (the app keeps working as long
 * as App Check is not enforced; once it is enforced, the server must refuse them, which is the whole point of the test).
 *
 * The error carries no secret and no personal data.
 */
class DebugAppCheckNoTokenProviderFactory : AppCheckProviderFactory {
    override fun create(firebaseApp: FirebaseApp): AppCheckProvider = DebugAppCheckNoTokenProvider()
}

class DebugAppCheckNoTokenProvider : AppCheckProvider {
    override fun getToken(): Task<AppCheckToken> =
        Tasks.forException(IllegalStateException("Debug switch: App Check is off on purpose, no token is given."))
}
