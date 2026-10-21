package com.tomessa.miff.data

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release: Play Integrity provider. App Check is in "unenforced" mode on the Firebase side for now
 * (switching to enforcement is a separate decision, after distribution through Play and TestFlight).
 * Note: an app installed outside Google Play does not pass Play Integrity.
 */
object AppCheckInstaller {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance(),
        )
    }
}
