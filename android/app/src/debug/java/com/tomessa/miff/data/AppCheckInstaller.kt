package com.tomessa.miff.data

import android.content.Context
import com.google.firebase.FirebaseApp
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
        if (off) {
            forgetStoredToken(context)
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckNoTokenProviderFactory())
        } else {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        }
    }

    /**
     * The SDK keeps the last token (valid up to one hour) in its own private file and would hand it out again after the
     * switch: without this, the proof "no token, request refused" could be wrong for the first hour. Removed BEFORE the SDK
     * is asked for its instance (it reads the file at creation). Best effort and debug only: the file name is the SDK's own
     * (firebase-appcheck 20.0.0, `StorageHelper`), a change of name would only bring the one-hour delay back.
     */
    private fun forgetStoredToken(context: Context) {
        val key = FirebaseApp.getInstance().persistenceKey
        context.applicationContext.deleteSharedPreferences("com.google.firebase.appcheck.store.$key")
    }
}
