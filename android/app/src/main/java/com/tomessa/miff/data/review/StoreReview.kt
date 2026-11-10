package com.tomessa.miff.data.review

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.domain.review.FeedbackForm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * The request of a review AND a comment to the STORE, after the person touched "J'adore !" (decision of the PM, 2026-11-08: natively, in the store's own
 * window). Miff NEVER listens to what is given in the store and never sends anything about it to anyone: the store does it all.
 * Returns true when something was shown or opened, false when nothing could be (the screen then says so calmly).
 */
fun interface StoreReviewRequester {
    suspend fun request(activity: Activity): Boolean
}

/** The two calls of Google Play In-App Review, behind an interface so that the fallback below is tested without Google Play. */
interface InAppReviewApi {
    /** Asks for the review flow. Throws when the API cannot be used (the app does not come from Google Play, no Play services, no network...). */
    suspend fun requestFlow(activity: Activity): Any

    /** Shows the card of the store for what [requestFlow] returned. Its end says nothing about what the person did there. */
    suspend fun launchFlow(
        activity: Activity,
        flow: Any,
    )
}

/** Google Play In-App Review (library `com.google.android.play:review`, free, by Google): the store decides whether its card appears (it has its own quota). */
class PlayInAppReviewApi : InAppReviewApi {
    override suspend fun requestFlow(activity: Activity): Any =
        ReviewManagerFactory.create(activity).requestReviewFlow().await()

    override suspend fun launchFlow(
        activity: Activity,
        flow: Any,
    ) {
        ReviewManagerFactory.create(activity).launchReviewFlow(activity, flow as ReviewInfo).await()
    }
}

/**
 * FALLBACK: only when the API cannot be used AT ALL (the request of the flow fails), the page of Miff in the store is opened instead ([StoreListing]), once.
 * When the API accepted the request, its card may already have been shown: a failure AFTER that never opens the page on top of it. Nothing is logged.
 */
class PlayStoreReviewRequester(
    private val api: InAppReviewApi = PlayInAppReviewApi(),
    private val openListing: (Context) -> Boolean = StoreListing::open,
) : StoreReviewRequester {
    override suspend fun request(activity: Activity): Boolean {
        val flow =
            try {
                api.requestFlow(activity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return openListing(activity)
            }
        try {
            api.launchFlow(activity, flow)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The card may have appeared: no second window on top of it.
        }
        return true
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

    /** Tries the addresses in order with [start] (true = something opened it); stops at the first that works. */
    fun openFirst(
        addresses: List<String>,
        start: (String) -> Boolean,
    ): Boolean = addresses.any { start(it) }

    /** Opens the first address that something on the phone can open; if none can, nothing happens (no crash, no technical message). */
    fun open(context: Context): Boolean =
        openFirst(candidates(context.packageName)) { address -> ViewLinks.start(context, address) }
}

/** An ACTION_VIEW on an address, refused quietly (false) when nothing can open it or the system forbids it. */
object ViewLinks {
    fun start(
        context: Context,
        address: String,
    ): Boolean =
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, address.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
}

/**
 * Opens the external feedback form ("Peux faire mieux...") in the browser: ACTION_VIEW on an HTTPS address only (anything else is refused here, whatever
 * the setting says), the address AS IT IS. Nothing is added to the intent or to the address.
 */
object FeedbackFormLauncher {
    fun open(
        context: Context,
        url: String,
    ): Boolean {
        val address = FeedbackForm.openableUrl(url) ?: return false
        return ViewLinks.start(context, address)
    }
}

/** The e-mail application of the person on the support address and NOTHING else (no subject, no text, no extra): the same gesture as Settings. */
object SupportMail {
    fun open(context: Context): Boolean =
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_SENDTO,
                    "mailto:${BuildConfig.SUPPORT_EMAIL}".toUri(),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
}
