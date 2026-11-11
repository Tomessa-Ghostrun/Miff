package com.tomessa.miff.data

import android.content.Context
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release: Play Integrity provider. App Check is in "unenforced" mode on the Firebase side for now
 * (switching to enforcement is a separate decision, after distribution through Play and TestFlight).
 * Note: an app installed outside Google Play does not pass Play Integrity.
 *
 * The context is only there so that both versions (debug and release) have the same signature; nothing here reads a setting:
 * the debug switch "without App Check" (AC-4) does not exist in this build, and must not.
 */
object AppCheckInstaller {
    @Suppress("UNUSED_PARAMETER")
    fun install(context: Context) {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance(),
        )
    }
}
