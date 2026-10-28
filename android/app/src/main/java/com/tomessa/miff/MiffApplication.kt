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
        // The manifest starts with "collect nothing" and the stored device choice is deliberately NOT re-applied here:
        // collection only starts after the account's own choice (users/{uid}) has been read, see SessionManager.start().
        // Also purges a Firestore cache left over by a failed sign-out.
        container.sessionManager.start()
    }
}
