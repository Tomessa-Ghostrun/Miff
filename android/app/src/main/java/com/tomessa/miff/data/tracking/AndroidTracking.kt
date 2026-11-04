package com.tomessa.miff.data.tracking

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.tomessa.miff.data.permission.DevicePermissions
import com.tomessa.miff.domain.permission.PermissionSnapshot
import com.tomessa.miff.domain.tracking.Fix
import com.tomessa.miff.domain.tracking.TrackedZone
import com.tomessa.miff.domain.tracking.TrackingLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/*
 * PRIVACY / SECURITY, for this file: it is where the system is asked for a position and for zones. A position is read ONLY through
 * [FusedPositionSource.current] and the reports of [FusedMovementUpdates], both called by the engine after the agreement and "Toujours" are checked;
 * the receiver that gets the events of the geofencing NEVER reads the location that comes with them (see TrackingReceiver). Nothing is logged.
 * The PendingIntents are EXPLICIT (our own receiver, which is not exported): another app cannot trigger them. They are mutable because the system
 * fills them with the event (the documented requirement for geofencing and location updates from Android 12).
 */

internal object TrackingIntents {
    const val ACTION_GEOFENCE = "com.tomessa.miff.tracking.GEOFENCE"
    const val ACTION_MOVED = "com.tomessa.miff.tracking.MOVED"
    const val ACTION_NETWORK = "com.tomessa.miff.tracking.NETWORK"

    private const val REQUEST_GEOFENCE = 7101
    private const val REQUEST_MOVED = 7102
    private const val REQUEST_NETWORK = 7103

    private fun mutableFlag(): Int =
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }

    private fun pending(
        context: Context,
        action: String,
        request: Int,
    ): PendingIntent {
        val intent = Intent(context.applicationContext, TrackingReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context.applicationContext,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag(),
        )
    }

    fun geofence(context: Context) = pending(context, ACTION_GEOFENCE, REQUEST_GEOFENCE)

    fun moved(context: Context) = pending(context, ACTION_MOVED, REQUEST_MOVED)

    fun network(context: Context) = pending(context, ACTION_NETWORK, REQUEST_NETWORK)
}

class AndroidTrackingEnvironment(
    context: Context,
    private val permissions: DevicePermissions,
    private val currentUid: () -> String?,
    private val online: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) : TrackingEnvironment {
    private val appContext = context.applicationContext

    override fun uid(): String? = currentUid()

    override fun permissions(): PermissionSnapshot = permissions.snapshot()

    override fun locationServicesOn(): Boolean {
        val manager = appContext.getSystemService(LocationManager::class.java) ?: return false
        // Reads the switch of the location of the phone, never a position.
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    override fun batteryPct(): Int? {
        // The sticky broadcast of the battery: no permission, no receiver registered.
        val status = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return (level * 100 / scale).coerceIn(0, 100)
    }

    override fun isOnline(): Boolean = online()

    override fun nowMillis(): Long = clock()
}

/** The position of the phone, when the engine asks (a few seconds at most). Needs the location permission: the engine checks it first. */
class FusedPositionSource(
    context: Context,
) : PositionSource {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context.applicationContext) }

    @Suppress("MissingPermission")
    override suspend fun current(): Fix? {
        val token = CancellationTokenSource()
        return try {
            val request =
                CurrentLocationRequest
                    .Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setMaxUpdateAgeMillis(MAX_AGE_MILLIS)
                    .setDurationMillis(WAIT_MILLIS)
                    .build()
            val location = client.getCurrentLocation(request, token.token).await() ?: client.lastLocation.await()
            location?.let { Fix(it.latitude, it.longitude, it.time) }
        } catch (e: CancellationException) {
            token.cancel()
            throw e
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val MAX_AGE_MILLIS = 2L * 60L * 1000L
        const val WAIT_MILLIS = 8_000L
    }
}

/** The zones of the system's geofencing. The same PendingIntent for all of them: the receiver reads the identifier of the zone (the place identifier). */
class PlayGeofenceArming(
    private val context: Context,
    clientFactory: () -> GeofencingClient = { LocationServices.getGeofencingClient(context.applicationContext) },
) : GeofenceArming {
    private val client by lazy(clientFactory)

    @Suppress("MissingPermission")
    override suspend fun update(
        add: List<TrackedZone>,
        removeIds: List<String>,
    ): Boolean =
        try {
            if (removeIds.isNotEmpty()) client.removeGeofences(removeIds).await()
            if (add.isNotEmpty()) {
                val request =
                    GeofencingRequest
                        .Builder()
                        // "Already inside when the zone is armed" counts as an arrival (criterion DET3); outside gives nothing (no `out` invented).
                        .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                        .addGeofences(add.map(::toGeofence))
                        .build()
                client.addGeofences(request, TrackingIntents.geofence(context)).await()
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    override suspend fun removeAll() {
        try {
            client.removeGeofences(TrackingIntents.geofence(context)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing was registered, or the system refuses: nothing more to remove.
        }
    }

    private fun toGeofence(zone: TrackedZone): Geofence =
        Geofence
            .Builder()
            .setRequestId(zone.placeId)
            .setCircularRegion(zone.lat, zone.lng, zone.radiusM.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()
}

/** "A move of more than 250 m": the fused provider reports it to the receiver even when the app is closed (a PendingIntent, no service). */
class FusedMovementUpdates(
    private val context: Context,
) : MovementUpdates {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context.applicationContext) }

    @Suppress("MissingPermission")
    override suspend fun start(): Boolean =
        try {
            val request =
                LocationRequest
                    .Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVAL_MILLIS)
                    .setMinUpdateDistanceMeters(TrackingLimits.MOVE_UPDATE_DISTANCE_M)
                    .setMinUpdateIntervalMillis(MIN_INTERVAL_MILLIS)
                    .setWaitForAccurateLocation(false)
                    .build()
            client.requestLocationUpdates(request, TrackingIntents.moved(context)).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    override suspend fun stop() {
        try {
            client.removeLocationUpdates(TrackingIntents.moved(context)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not registered: nothing to stop.
        }
    }

    private companion object {
        const val INTERVAL_MILLIS = 5L * 60L * 1000L
        const val MIN_INTERVAL_MILLIS = 60L * 1000L
    }
}

/**
 * Wakes the receiver when a network with Internet is back, even if the process was killed (a PendingIntent registered with the connectivity service),
 * and only while a status waits to be written (criterion HOR1). No permission beyond ACCESS_NETWORK_STATE, already declared.
 */
class ConnectivityNetworkWake(
    private val context: Context,
) : NetworkWake {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun watch() {
        try {
            val request =
                NetworkRequest
                    .Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    .build()
            manager?.registerNetworkCallback(request, TrackingIntents.network(context))
        } catch (e: Exception) {
            // The system refuses (too many callbacks...): the status goes at the next wake-up instead (opening, move, zone).
        }
    }

    override fun stop() {
        try {
            manager?.unregisterNetworkCallback(TrackingIntents.network(context))
        } catch (e: Exception) {
            // Not registered.
        }
    }
}

/**
 * "The app was opened" and "the app went to the background" (ticket 6.5): counted from the started screens. A screen turned (rotation) is neither.
 * Nothing is read here: the callbacks only tell the engine to look again.
 */
class ForegroundObserver(
    private val onForeground: () -> Unit,
    private val onBackground: () -> Unit,
) : Application.ActivityLifecycleCallbacks {
    private var started = 0
    private var rotating = false

    override fun onActivityStarted(activity: Activity) {
        started++
        if (started == 1) {
            if (rotating) rotating = false else onForeground()
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (started > 0) started--
        if (started == 0) {
            if (activity.isChangingConfigurations) rotating = true else onBackground()
        }
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
