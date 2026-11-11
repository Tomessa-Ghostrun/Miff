package com.tomessa.miff.data

import android.content.Context
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug: App Check debug provider (for devices and emulators that cannot pass Play Integrity).
 * On first launch the SDK prints a debug secret in Logcat (tag "DebugAppCheckProvider"); it can be registered in
 * Firebase console > App Check > Manage debug tokens. Keep that token private: never commit it, never put it in
 * an issue or a chat. App Check is not enforced yet, so skipping the registration only makes Test "App Check"
 * report "no token".
 *
 * AC-4: if the diagnostic screen switched "without App Check" on, the provider gives NO token (see
 * [DebugAppCheckNoTokenProviderFactory]). The choice is read here, once, at launch.
 */
object AppCheckInstaller {
    fun install(context: Context) {
        val off = SharedPreferencesDebugAppCheckSetting(context).isOffRequested()
        SharedPreferencesDebugAppCheckSetting.offInEffect = off
        val appCheck = FirebaseAppCheck.getInstance()
        if (off) {
            installWithoutToken(appCheck, DebugAppCheckNoTokenProviderFactory())
        } else {
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        }
    }

    /**
     * Forgets the token the SDK kept (best effort, see [StoredAppCheckToken]) and installs the provider that gives none. The forgetting is
     * done again when the SDK has finished reading its stored token in the background, which can happen after the first one.
     */
    private fun installWithoutToken(
        appCheck: FirebaseAppCheck,
        factory: AppCheckProviderFactory,
    ) {
        val forgotten = StoredAppCheckToken.forget(appCheck)
        appCheck.installAppCheckProviderFactory(factory)
        if (forgotten) {
            StoredAppCheckToken.retrieval(appCheck)?.addOnCompleteListener {
                StoredAppCheckToken.forget(appCheck)
                appCheck.installAppCheckProviderFactory(factory)
            }
        }
    }
}
