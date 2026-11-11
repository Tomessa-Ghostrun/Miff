package com.tomessa.miff.data.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.LocationResult
import com.tomessa.miff.MiffApplication
import com.tomessa.miff.domain.tracking.Fix
import com.tomessa.miff.domain.tracking.TransitionKind
import com.tomessa.miff.domain.tracking.ZoneKey
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * PRIVACY / SECURITY, for this file (criteria VIE1, DET1, AND2):
 * - THE LOCATION THAT COMES WITH A GEOFENCE EVENT IS NEVER READ: only the kind of transition and the identifiers of the zones are taken from it. The
 *   engine asks for a position itself, when it needs one, once the agreement and "Toujours" are checked. (TrackingPrivacyTest checks the source.)
 * - The receiver is NOT exported: only the system (boot, update of the app) and our own PendingIntents reach it. A zone identifier that is not the
 *   shape of a place identifier is ignored.
 * - It does only what the engine does for each wake-up, within the few seconds a receiver has; what cannot finish is already kept (the status waiting)
 *   and goes at the next wake-up. Nothing is logged.
 */
class TrackingReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.applicationContext as? MiffApplication ?: return
        val container = app.container
        val engine = container.trackingEngine
        val work: suspend () -> Unit =
            when (intent.action) {
                TrackingIntents.ACTION_GEOFENCE -> {
                    geofenceWork(intent, engine) ?: return
                }

                TrackingIntents.ACTION_MOVED -> {
                    // The reports of the provider of positions ARE positions (asked for by the engine); they are used as the fix of this occasion.
                    val last = LocationResult.extractResult(intent)?.lastLocation
                    val fix = last?.let { Fix(it.latitude, it.longitude, it.time) }
                    ({ engine.refresh(TrackingEngine.Reason.MOVED, fix) })
                }

                TrackingIntents.ACTION_NETWORK -> {
                    ({ engine.onNetworkBack() })
                }

                TrackingIntents.ACTION_TRAVEL_END, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                    // MODE VOYAGE: the alarm of the end of a trip, or the clock / the time zone changed: the trip is looked at again (nothing happens when there is none).
                    ({ engine.refresh(TrackingEngine.Reason.TRAVEL_ENDED) })
                }

                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                    // The geofences of the system are gone after a restart of the phone or an update of the app: they are armed again (criterion AND2).
                    ({ engine.refresh(TrackingEngine.Reason.REARM) })
                }

                else -> {
                    return
                }
            }
        val pending = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(BUDGET_MILLIS) {
                    // A purge of the cache left by a failed sign-out comes before any other access to Firestore.
                    container.cachePurger.purgeIfPending()
                    work()
                }
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * What to do for a message of the geofencing, or null to ignore it. An error (GEOFENCE_NOT_AVAILABLE and the like) means the system lost or cannot
     * keep the zones: they are all armed again, as after a restart (review B2). Otherwise only the kind of transition and the zone keys are taken.
     */
    private fun geofenceWork(
        intent: Intent,
        engine: TrackingEngine,
    ): (suspend () -> Unit)? {
        val event = GeofencingEvent.fromIntent(intent) ?: return null
        if (event.hasError()) return { engine.refresh(TrackingEngine.Reason.REARM) }
        val kind =
            when (event.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> TransitionKind.ENTER
                Geofence.GEOFENCE_TRANSITION_EXIT -> TransitionKind.EXIT
                else -> return null
            }
        val keys =
            event.triggeringGeofences
                .orEmpty()
                .map { it.requestId }
                .filter(ZoneKey::isValid)
        // The time of the event is the time it reaches the app (the system gives no other, short of reading the location, which is not done).
        val at = System.currentTimeMillis()
        return { keys.forEach { engine.onTransition(kind, it, at) } }
    }

    private companion object {
        /** A receiver has about ten seconds: stopping a little before, what is not finished waits for the next wake-up. */
        const val BUDGET_MILLIS = 8_500L

        // A failure of the detection must never take the app down from a receiver: swallowed here (nothing is logged: it could hold a place).
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })
    }
}
