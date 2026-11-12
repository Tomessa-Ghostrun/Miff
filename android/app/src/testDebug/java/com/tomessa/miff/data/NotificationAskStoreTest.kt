package com.tomessa.miff.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.data.notification.SharedPreferencesKnownMiffs
import com.tomessa.miff.data.notification.SharedPreferencesNotificationCenterStore
import com.tomessa.miff.data.notification.SharedPreferencesNotificationPreferencesStore
import com.tomessa.miff.data.permission.SharedPreferencesNotificationAskStore
import com.tomessa.miff.domain.notification.ItemState
import com.tomessa.miff.domain.notification.NotificationItem
import com.tomessa.miff.domain.notification.NotificationPreferences
import com.tomessa.miff.domain.notification.NotificationType
import com.tomessa.miff.domain.notification.SecondChance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The flag "notification screen already shown" is per account, and really gone once the account is forgotten (AUTH-5); the local memory of the
 * notifications (choices, centre, second chance) is kept on the phone, per account where it concerns one, and cleared with the session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotificationAskStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    private fun store() = SharedPreferencesNotificationAskStore(context)

    private fun centreFile() = context.getSharedPreferences("miff_notification_center", Context.MODE_PRIVATE)

    @Test
    fun forget_removesOnlyThatAccount_evenForANewInstance() {
        store().markShown("uid-1")
        store().markShown("uid-2")
        store().forget("uid-1")
        assertFalse(store().wasShown("uid-1"))
        assertTrue(store().wasShown("uid-2"))
    }

    @Test
    fun theSystemWindowIsAFactOfThePhone_notOfAnAccount_andSurvivesTheForgettingOfAnAccount() {
        assertFalse(store().systemAsked())
        store().markSystemAsked()
        store().forget("uid-1")
        assertTrue(store().systemAsked())
    }

    @Test
    fun theSecondChanceIsPerAccount_survivesARestart_andGoesWithTheAccount() {
        assertEquals(SecondChance.NONE, store().secondChance("uid-1"))
        store().setSecondChance("uid-1", SecondChance.READY)
        store().setSecondChance("uid-2", SecondChance.DONE)
        assertEquals(SecondChance.READY, store().secondChance("uid-1"))
        assertEquals(SecondChance.DONE, store().secondChance("uid-2"))
        store().forget("uid-1")
        assertEquals(SecondChance.NONE, store().secondChance("uid-1"))
        assertEquals(SecondChance.DONE, store().secondChance("uid-2"))
    }

    @Test
    fun theChoicesOfSettings_areKeptOnThePhone_andClearedAtTheSignOut() {
        val prefs = SharedPreferencesNotificationPreferencesStore(context)
        assertEquals(NotificationPreferences(), prefs.load())
        val chosen = NotificationPreferences(enabled = true, n1 = false, n4 = false, nightCalm = false)
        prefs.save(chosen)
        assertEquals(chosen, SharedPreferencesNotificationPreferencesStore(context).load())
        prefs.clear()
        assertEquals(NotificationPreferences(), SharedPreferencesNotificationPreferencesStore(context).load())
    }

    @Test
    fun theCentre_roundTripsEveryField_dropsATypeItDoesNotKnow_andReadsADamagedFileAsEmpty() {
        val store = SharedPreferencesNotificationCenterStore(context)
        val items =
            listOf(
                NotificationItem("n1|m|u|1", NotificationType.N1, "m", 1_000L, readAtMillis = 2_000L),
                NotificationItem("n6|m|p|3", NotificationType.N6, null, 3_000L, state = ItemState.SILENT),
            )
        store.save(items)
        assertEquals(items, SharedPreferencesNotificationCenterStore(context).load())

        // A line of a type this version does not know (a newer one wrote it) is dropped, the others stay.
        centreFile()
            .edit()
            .putString(
                "items",
                """[{"id":"a","type":"n99","createdAt":1},{"id":"b","type":"n2","miffId":"m","createdAt":2}]""",
            ).commit()
        assertEquals(listOf("b"), store.load().map { it.id })

        centreFile().edit().putString("items", "not json at all").commit()
        assertTrue(store.load().isEmpty())

        store.save(items)
        store.clear()
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun theConsentToBeNotifiedIsPerAccount_nothingByDefault_survivesARestart_andGoesWithTheAccount() {
        assertFalse(store().pushConsent("uid-1"))
        store().markPushConsent("uid-1")
        assertTrue(store().pushConsent("uid-1"))
        assertFalse("another account has not agreed", store().pushConsent("uid-2"))
        assertTrue("a new instance finds it again", SharedPreferencesNotificationAskStore(context).pushConsent("uid-1"))
        store().forget("uid-1")
        assertFalse(store().pushConsent("uid-1"))
    }

    @Test
    fun theMiffsOfTheAccount_surviveAColdStart_areForOneAccountOnly_andGoWithTheSession() {
        val known = SharedPreferencesKnownMiffs(context)
        assertFalse("nothing remembered: unknown means no", known.contains("u1", "m1"))
        known.remember("u1", setOf("m1", "m2"))
        val cold = SharedPreferencesKnownMiffs(context)
        assertTrue(cold.contains("u1", "m1"))
        assertFalse(cold.contains("u1", "zz"))
        assertFalse("another account has none of them", cold.contains("u2", "m1"))
        cold.forget("u1", "m1")
        assertFalse(cold.contains("u1", "m1"))
        assertTrue(cold.contains("u1", "m2"))
        cold.forget("u2", "m2")
        assertTrue("forgetting for another account changes nothing", cold.contains("u1", "m2"))
        known.remember("u2", setOf("m9"))
        assertFalse("the list of the previous account is replaced", known.contains("u1", "m2"))
        known.clear()
        assertFalse(known.contains("u2", "m9"))
    }

    @Test
    fun theConsentTellsWhoListens_soTheFirstRegistrationDoesNotWait() {
        var calls = 0
        val asks = SharedPreferencesNotificationAskStore(context, onPushConsent = { calls++ })
        assertEquals(0, calls)
        asks.markPushConsent("uid-1")
        assertEquals(1, calls)
        assertTrue(asks.pushConsent("uid-1"))
    }

    @Test
    fun aLineOfTheNewsKeepsTheFirstNameOfTheAuthor_andNoOtherTypeDoes() {
        val store = SharedPreferencesNotificationCenterStore(context)
        val items =
            listOf(
                NotificationItem("n9|u|a|1", NotificationType.N9, "miff", 5L, who = "Amandine"),
                NotificationItem("n6|m|p|1", NotificationType.N6, "miff", 6L),
            )
        store.save(items)
        val loaded = store.load()
        assertEquals("Amandine", loaded.first { it.type == NotificationType.N9 }.who)
        assertEquals(null, loaded.first { it.type == NotificationType.N6 }.who)
        store.clear()
        assertTrue("gone with the session", store.load().isEmpty())
    }

    @Test
    fun theSwitchOfTheNewsIsKeptOnThePhone_onByDefault() {
        val store = SharedPreferencesNotificationPreferencesStore(context)
        assertTrue(store.load().n9)
        store.save(NotificationPreferences(n9 = false))
        assertFalse(SharedPreferencesNotificationPreferencesStore(context).load().n9)
        store.clear()
        assertTrue(store.load().n9)
    }

    @Test
    fun theStoredLinesCarryNoPositionNoPlaceNoAddress() {
        // What the centre writes is a type, an identifier, a Miff identifier, two times and a flag: nothing else can be there to leak.
        val store = SharedPreferencesNotificationCenterStore(context)
        store.save(listOf(NotificationItem("k", NotificationType.N6, "miff", 5L)))
        val raw = centreFile().getString("items", "").orEmpty()
        listOf("lat", "lng", "lon", "address", "place", "photo").forEach {
            assertFalse("the centre must not store \"$it\"", raw.contains(it, ignoreCase = true))
        }
    }
}
