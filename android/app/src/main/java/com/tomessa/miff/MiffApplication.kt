package com.tomessa.miff

import android.app.Application
import com.tomessa.miff.data.AppCheckInstaller
import com.tomessa.miff.domain.startup.StartupMark
import com.tomessa.miff.startup.StartupTrace
import com.tomessa.miff.ui.splash.SplashArtwork
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Everything here runs BEFORE the first screen can even be created, with the system launch screen ("M" alone) on display:
 * keep it to what is indispensable, and measured (see [StartupTrace], "Miff.*" sections of the system trace).
 *
 * What stays here, in this order, and why:
 * 1. App Check is installed BEFORE anything uses Firebase (it must be in place for the very first request).
 * 2. The collection of statistics and crash reports is PAUSED (the manifest says off, but Crashlytics remembers a previous
 *    "on" on its own: nothing may be sent until the account's own choice has been read). Synchronous on purpose.
 *
 * What moved out (PM recette 2026-10-31, "the M stays for seconds"): the construction of the whole container, the
 * initialisation of Firebase Authentication, the opening of the private files and the read of the session. The first
 * ones now run on a background thread ([startWarmUp]); the session starts after the first image of the animation
 * ([startSession]).
 */
class MiffApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val sessionStarted = AtomicBoolean(false)

    override fun onCreate() {
        StartupTrace.mark(StartupMark.APP_CREATE_START)
        super.onCreate()
        StartupTrace.section("Miff.Application.onCreate") {
            // Firebase is already initialized by its content provider at this point.
            StartupTrace.section("Miff.AppCheck.install") { AppCheckInstaller.install() }
            // Nothing is built yet: every member of the container is created on first use.
            container = AppContainer(this)
            // The manifest starts with "collect nothing" and the stored device choice is deliberately NOT re-applied here:
            // collection only starts after the account's own choice (users/{uid}) has been read, see SessionManager.start().
            StartupTrace.section("Miff.privacy.pauseCollection") { container.privacySettings.pauseCollection() }
            startWarmUp()
        }
        StartupTrace.mark(StartupMark.APP_CREATE_END)
    }

    /**
     * Builds the rest on another thread while the main thread creates the screen. Only an optimisation: the main thread
     * builds anything it finds missing, exactly as before, so a failure here changes nothing but the speed. Runs AFTER
     * App Check has been installed (same thread, sequence above).
     */
    private fun startWarmUp() {
        Thread(
            {
                try {
                    SplashArtwork.prepare()
                    container.warmUp()
                } catch (e: Throwable) {
                    // Throwable on purpose, not only Exception: an Error escaping a background thread would take the whole
                    // process down at launch (e.g. a LinkageError or an OutOfMemoryError while building an object), for an
                    // optimisation. The main thread builds what is missing and reports a real problem itself, where it is
                    // handled as before. Nothing is logged (the app never logs) and nothing is kept: no personal data here.
                }
            },
            "miff-warmup",
        ).start()
    }

    /**
     * Starts what can wait for the first image of the animation: reading the session (and the purge of a Firestore cache
     * left by a failed sign-out, which comes before any other Firestore access) and the cleaning of the camera leftovers.
     * Called by [MainActivity] once the first image is drawn; safe to call twice.
     *
     * A future entry point that does not go through the screen (a geofence receiver, a push service) MUST call this before
     * touching Firestore: the purge of a failed sign-out has to come first.
     */
    fun startSession() {
        if (!sessionStarted.compareAndSet(false, true)) return
        StartupTrace.section("Miff.session.start") { container.sessionManager.start() }
        Thread({ container.deleteCameraLeftovers() }, "miff-cleanup").start()
    }
}
