package com.tomessa.miff.data.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/*
 * SRV-2: the service that receives what the server sends (Firebase Cloud Messaging). Declared in the manifest NOT exported: only the system's own messaging component
 * (the Firebase receiver, which is protected by the permission of Google's servers) can start it.
 *
 * SECURITY / PRIVACY, for this file: the token and the content of a message never reach a log, an event or a crash report (nothing here logs anything). The message is
 * DATA-ONLY on purpose: FCM never displays anything by itself, the app composes the notification (language of the app, switches, caps, quiet hours). Every failure
 * is swallowed: a message that cannot be handled is dropped, it never takes the process down.
 *
 * Both callbacks run on a worker thread of the library (never the main thread), for a few seconds at most: the work is small and bounded.
 */

/** What the messaging service needs from the application (a seam for the tests): the application implements it. */
interface PushHost {
    /** A DATA message of the server. Returns true when it was understood. */
    fun handlePushMessage(
        data: Map<String, String>,
        messageId: String?,
    ): Boolean

    /** The system gave a new token. */
    suspend fun onNewPushToken(token: String)
}

class MiffMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val host = application as? PushHost ?: return
        try {
            host.handlePushMessage(message.data, message.messageId)
        } catch (e: Exception) {
            // Dropped without a trace.
        }
    }

    /**
     * The system gave a new token (first token after the consent, rotation, restore). It is registered at once when the person consented; otherwise it is forgotten (the
     * registrar looks at the consent first). A write that does not finish in time is made again the next time the app opens: the token is compared by fingerprint.
     * Uses the classic callback, as long as the new registration (onRegistered) is not switched on in the manifest.
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        val host = application as? PushHost ?: return
        try {
            runBlocking { withTimeoutOrNull(WAIT_MILLIS) { host.onNewPushToken(token) } }
        } catch (e: Exception) {
            // Tried again at the next opening.
        }
    }

    private companion object {
        const val WAIT_MILLIS = 8_000L
    }
}
