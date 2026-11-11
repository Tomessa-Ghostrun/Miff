package com.tomessa.miff.data.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.tomessa.miff.domain.tracking.Fix
import com.tomessa.miff.domain.tracking.TrackingLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/*
 * PRIVACY, for this file: the position that goes with a photo (decision of the PM of 2026-11-06). It lives in data/tracking because a position is read
 * ONLY here (PermissionPrivacyTest, criterion AUT7). The [Fix] is returned to the caller, which keeps it in memory until the lot is sent; nothing here
 * logs it, sends it as an event or keeps it.
 */

/** What the phone said when a position was asked for a photo: a position, or WHY there is none (the calm sentence is not the same). */
sealed interface PhotoPosition {
    class Found(
        val fix: Fix,
    ) : PhotoPosition {
        override fun toString(): String = "Found(redacted)"
    }

    /** The permission of the location was taken back, or the location of the phone is switched off: only the person can change that (the settings). */
    data object LocationOff : PhotoPosition

    /** The phone could not say now (no signal, slow): "réessaie dans un instant". */
    data object NotFound : PhotoPosition
}

/** Where the person is when a photo is published: ONE measure, never a loop. */
fun interface PhotoPositionSource {
    /**
     * The last measure of the phone if it is less than 10 minutes old ([TrackingLimits.FIX_MAX_AGE_MILLIS]), else ONE fresh measure (high accuracy, at
     * most [FusedPhotoPositionSource.WAIT_MILLIS] of waiting). [PhotoPosition.LocationOff] when the permission was taken back or the location of the phone is
     * off (risk R15: not the same message as a slow signal), [PhotoPosition.NotFound] when the phone cannot say now. Reads the position: only called once the
     * person pressed "Publier".
     */
    suspend fun measure(): PhotoPosition
}

class FusedPhotoPositionSource(
    context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : PhotoPositionSource {
    private val appContext = context.applicationContext
    private val client by lazy { LocationServices.getFusedLocationProviderClient(appContext) }

    /** At least the approximate location is granted (either is enough to tell "not taken back"). */
    private fun permitted(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** The switch of the location of the phone (the quick setting), never a position. */
    private fun switchedOn(): Boolean {
        val manager = appContext.getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    @Suppress("MissingPermission")
    override suspend fun measure(): PhotoPosition {
        if (!permitted() || !switchedOn()) return PhotoPosition.LocationOff
        val token = CancellationTokenSource()
        return try {
            val request =
                CurrentLocationRequest
                    .Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    // A measure younger than this is returned as it is (the last one of the detection or of any app); an older one is replaced by a fresh one.
                    .setMaxUpdateAgeMillis(TrackingLimits.FIX_MAX_AGE_MILLIS)
                    .setDurationMillis(WAIT_MILLIS)
                    .build()
            val location = client.getCurrentLocation(request, token.token).await() ?: client.lastLocation.await()
            location
                // Whatever the source, an old position is never used: the person has moved since.
                ?.takeIf { nowMillis() - it.time <= TrackingLimits.FIX_MAX_AGE_MILLIS }
                ?.let { PhotoPosition.Found(Fix(it.latitude, it.longitude, it.time)) }
                ?: PhotoPosition.NotFound
        } catch (e: CancellationException) {
            token.cancel()
            throw e
        } catch (e: SecurityException) {
            // The permission went while we were asking.
            PhotoPosition.LocationOff
        } catch (e: Exception) {
            PhotoPosition.NotFound
        }
    }

    companion object {
        /**
         * Bounded, but long enough for a cold GPS or an indoor start (5 s was too short: "Position introuvable" although the person shares, as on iOS, recette build 24).
         * The person waits anyway for the end of the publication (the button is busy). One attempt; the screen offers "Réessayer". Deviation from D33 ("3 s"), to be confirmed by the PM.
         */
        const val WAIT_MILLIS = 10_000L
    }
}
