package com.tomessa.miff.data.tracking

import android.content.Context
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

/** Where the person is when a photo is published: ONE measure, never a loop. */
fun interface PhotoPositionSource {
    /**
     * The last measure of the phone if it is less than 10 minutes old ([TrackingLimits.FIX_MAX_AGE_MILLIS]), else ONE fresh measure (high accuracy, at
     * most [FusedPhotoPositionSource.WAIT_MILLIS] of waiting). Null when the phone cannot say (no permission, location switched off, no signal): the
     * photo is then not published and the screen says so calmly. Reads the position: only called once the person pressed "Publier".
     */
    suspend fun measure(): Fix?
}

class FusedPhotoPositionSource(
    context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : PhotoPositionSource {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context.applicationContext) }

    @Suppress("MissingPermission")
    override suspend fun measure(): Fix? {
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
                ?.let { Fix(it.latitude, it.longitude, it.time) }
        } catch (e: CancellationException) {
            token.cancel()
            throw e
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /** Short and bounded: the person is looking at the screen. One attempt; the screen offers "Réessayer". */
        const val WAIT_MILLIS = 5_000L
    }
}
