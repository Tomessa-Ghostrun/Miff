package com.tomessa.miff

import android.app.Application
import com.tomessa.miff.data.AppCheckInstaller

class MiffApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Firebase is already initialized by its content provider at this point.
        AppCheckInstaller.install()
        container = AppContainer(this)
        // Manifest defaults are "collect nothing"; this re-applies the user's stored choices (off by default).
        container.privacySettings.applyStoredChoices()
        // Decides where the app opens (session, account state) and purges a Firestore cache left over by a failed sign-out.
        container.sessionManager.start()
    }
}
