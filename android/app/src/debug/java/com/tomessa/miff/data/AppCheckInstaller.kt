package com.tomessa.miff.data

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug: App Check debug provider (for devices and emulators that cannot pass Play Integrity).
 * On first launch the SDK prints a debug secret in Logcat (tag "DebugAppCheckProvider"); it can be registered in
 * Firebase console > App Check > Manage debug tokens. Keep that token private: never commit it, never put it in
 * an issue or a chat. App Check is not enforced yet, so skipping the registration only makes Test "App Check"
 * report "no token".
 */
object AppCheckInstaller {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
