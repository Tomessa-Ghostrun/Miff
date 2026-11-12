package com.tomessa.miff.data.push

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.messaging.RemoteMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An application that records what the service hands to it (the real one builds the whole app). */
class RecordingPushApplication :
    Application(),
    PushHost {
    val messages = mutableListOf<Map<String, String>>()
    val tokens = mutableListOf<String>()
    var failing = false

    override fun handlePushMessage(data: Map<String, String>): Boolean {
        if (failing) error("boom")
        messages += data
        return true
    }

    override suspend fun onNewPushToken(token: String) {
        if (failing) error("boom")
        tokens += token
    }
}

/**
 * The messaging service on the JVM (Robolectric): it hands the DATA of a message to the application, whatever comes with it; it never throws; the manifest declares it
 * NOT exported with the one action of Firebase Messaging.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = RecordingPushApplication::class)
class MiffMessagingServiceTest {
    private val app get() = ApplicationProvider.getApplicationContext<RecordingPushApplication>()

    private fun service() = Robolectric.buildService(MiffMessagingService::class.java).create().get()

    private fun message(data: Map<String, String>) =
        RemoteMessage.Builder("miff-3d283@fcm.googleapis.com").setData(data).build()

    @Test
    fun theDataOfAMessageReachesTheApplicationUntouched() {
        val data = mapOf("type" to "n9_update", "miffId" to "m1", "authorFirstName" to "Amandine")
        service().onMessageReceived(message(data))
        assertEquals(listOf(data), app.messages)
    }

    @Test
    fun aMessageWithNoDataIsHandedOnAsEmpty_theApplicationIgnoresIt() {
        service().onMessageReceived(message(emptyMap()))
        assertEquals(listOf(emptyMap<String, String>()), app.messages)
    }

    @Test
    fun aFailureInsideNeverTakesTheServiceDown() {
        app.failing = true
        service().onMessageReceived(message(mapOf("type" to "n9_update")))
        service().onNewToken("token-xyz")
        assertTrue(app.messages.isEmpty() && app.tokens.isEmpty())
    }

    @Test
    fun aNewTokenReachesTheApplication() {
        service().onNewToken("token-xyz")
        assertEquals(listOf("token-xyz"), app.tokens)
    }

    @Test
    fun theServiceIsDeclaredNotExported_withTheOneActionOfFirebaseMessaging() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info =
            context.packageManager.getServiceInfo(
                android.content.ComponentName(context, MiffMessagingService::class.java),
                PackageManager.GET_META_DATA,
            )
        assertNotNull(info)
        assertFalse("not exported", info.exported)
        val query = android.content.Intent("com.google.firebase.MESSAGING_EVENT").setPackage(context.packageName)
        val resolved = context.packageManager.queryIntentServices(query, 0).map { it.serviceInfo.name }
        assertTrue(MiffMessagingService::class.java.name in resolved)
        assertEquals(0, info.flags and ServiceInfo.FLAG_EXTERNAL_SERVICE)
    }

    @Test
    fun theAutomaticStartOfTheTokenIsOffInTheManifest_soNoTokenExistsBeforeTheConsent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val meta =
            context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData
        assertTrue(meta.containsKey("firebase_messaging_auto_init_enabled"))
        assertFalse(meta.getBoolean("firebase_messaging_auto_init_enabled", true))
    }
}
