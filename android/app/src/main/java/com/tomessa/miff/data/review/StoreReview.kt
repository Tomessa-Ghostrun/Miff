package com.tomessa.miff.data.review

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * The request of a review to the STORE, after the person touched "J'adore ! (5 étoiles)" (or the five stars). Miff NEVER listens to the
 * rating given in the store and never sends one anywhere: the store does it all, in its own window.
 */
fun interface StoreReviewRequester {
    suspend fun request(activity: Activity)
}

/**
 * Google Play In-App Review (library `com.google.android.play:review`, free, by Google): the store decides whether its window appears
 * (it has its own quota, and says nothing when it does not show it), and the app learns nothing about what the person did there.
 *
 * FALLBACK: if the API is not usable (the app does not come from Google Play, no Play services, no network...), the page of the app in the
 * store is opened instead ([StoreListing]), once. Nothing is logged: a failure is only "use the fallback".
 */
class PlayStoreReviewRequester : StoreReviewRequester {
    override suspend fun request(activity: Activity) {
        val shown =
            try {
                val manager = ReviewManagerFactory.create(activity)
                val info = manager.requestReviewFlow().await()
                manager.launchReviewFlow(activity, info).await()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        if (!shown) StoreListing.open(activity)
    }
}

/**
 * The page of Miff in the Play Store: first the store application (`market://`), then the web address (`https://`, never a clear-text
 * address). Pure intents; the package name is the one of the app itself, nothing else is added (no account, no data).
 */
object StoreListing {
    fun marketAddress(packageName: String): String = "market://details?id=$packageName"

    fun webAddress(packageName: String): String = "https://play.google.com/store/apps/details?id=$packageName"

    /** The addresses to try, in order. */
    fun candidates(packageName: String): List<String> = listOf(marketAddress(packageName), webAddress(packageName))

    /** Opens the first address that something on the phone can open; if none can, nothing happens (no crash, no technical message). */
    fun open(context: Context): Boolean {
        for (uri in candidates(context.packageName)) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return true
            } catch (e: ActivityNotFoundException) {
                // Try the next address.
            }
        }
        return false
    }
}
