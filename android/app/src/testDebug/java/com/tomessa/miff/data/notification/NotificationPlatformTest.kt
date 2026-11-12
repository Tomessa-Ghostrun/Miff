package com.tomessa.miff.data.notification

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.domain.notification.NotificationDestination
import com.tomessa.miff.domain.notification.NotificationFamily
import com.tomessa.miff.domain.notification.NotificationType
import com.tomessa.miff.domain.notification.PlanSnapshot
import com.tomessa.miff.domain.notification.SnapshotMember
import com.tomessa.miff.domain.tracking.PresenceValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The part of the reminders that talks to the system, on the JVM (Robolectric): the alarms (inexact, one per type, replaced and cancelled), the four channels,
 * the notification itself (the words of the PM, the tag, the tap), and what is kept on the disk (snapshot, ledger).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "fr")
class NotificationPlatformTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)
    private val notificationManager get() = context.getSystemService(NotificationManager::class.java)
    private val t0 = 1_800_000_000_000L

    // --- Alarms ---------------------------------------------------------------------------------------------------------------------------

    @Test
    fun anAlarmIsInexactAndAllowedInDoze_neverExact() {
        val alarms = AndroidNotificationAlarms(context)
        alarms.schedule(NotificationType.N1, ScheduledAlarm("n1|m|u|1", "m", t0))
        val scheduled = shadowOf(alarmManager).scheduledAlarms.single()
        assertEquals(AlarmManager.RTC_WAKEUP, scheduled.type)
        assertEquals(t0, scheduled.triggerAtTime)
        // Inexact: `setAndAllowWhileIdle` (allowed in Doze), never `setExact...` and never an alarm clock.
        assertTrue(scheduled.allowWhileIdle)
        assertNull("never an alarm clock", scheduled.getAlarmClockInfo())
        assertEquals(0L, scheduled.interval)
    }

    @Test
    fun oneAlarmPerType_aNewOneReplacesTheOld_andTheyAreRememberedForTheDiff() {
        val alarms = AndroidNotificationAlarms(context)
        alarms.schedule(NotificationType.N1, ScheduledAlarm("k1", "m", t0))
        alarms.schedule(NotificationType.N1, ScheduledAlarm("k1b", "m", t0 + 5))
        alarms.schedule(NotificationType.N2, ScheduledAlarm("k2", "m", t0 + 10))
        val shadow = shadowOf(alarmManager)
        assertEquals("one alarm for N1 (replaced), one for N2", 2, shadow.scheduledAlarms.size)
        assertEquals(t0 + 5, shadow.scheduledAlarms.first { it.triggerAtTime == t0 + 5 }.triggerAtTime)
        assertEquals(setOf(NotificationType.N1, NotificationType.N2), alarms.scheduled().keys)
        assertEquals(ScheduledAlarm("k1b", "m", t0 + 5), alarms.scheduled().getValue(NotificationType.N1))
        // A new instance (the next process) finds the same memory.
        assertEquals(2, AndroidNotificationAlarms(context).scheduled().size)
    }

    @Test
    fun anAlarmTheSystemLost_isNotListed_soTheNextReconcileAsksAgain_i2() {
        val alarms = AndroidNotificationAlarms(context)
        alarms.schedule(NotificationType.N1, ScheduledAlarm("k1", "m", t0))
        alarms.schedule(NotificationType.N2, ScheduledAlarm("k2", "m", t0 + 1))
        assertEquals(2, alarms.scheduled().size)
        // A cleaner of the phone maker erases the alarm and its PendingIntent; the memory of the app still says "asked".
        val intent =
            Intent(context, NotificationAlarmReceiver::class.java)
                .setAction(NotificationAlarmReceiver.ACTION_FIRE)
                .setData(android.net.Uri.parse("miff-notification://alarm/n1"))
        val flags = android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE
        android.app.PendingIntent
            .getBroadcast(context, NotificationType.N1.ordinal, intent, flags)!!
            .cancel()
        assertEquals(setOf(NotificationType.N2), alarms.scheduled().keys)
    }

    @Test
    fun cancelRemovesOnlyThatType_andCancelAllRemovesEverything() {
        val alarms = AndroidNotificationAlarms(context)
        NotificationType.entries.filter { it != NotificationType.N6 }.forEachIndexed { i, type ->
            alarms.schedule(type, ScheduledAlarm("k$i", "m", t0 + i))
        }
        // N1, N2, N3, N4 and N7 (the end of a trip) wait for an hour: N6 is immediate.
        assertEquals(5, shadowOf(alarmManager).scheduledAlarms.size)
        alarms.cancel(NotificationType.N3)
        assertEquals(4, shadowOf(alarmManager).scheduledAlarms.size)
        assertNull(alarms.scheduled()[NotificationType.N3])
        alarms.consumed(NotificationType.N1)
        assertNull(alarms.scheduled()[NotificationType.N1])
        alarms.cancelAll()
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
        assertTrue(alarms.scheduled().isEmpty())
    }

    @Test
    fun theAlarmIsAnExplicitIntentToOurReceiver_carryingOnlyATypeAKeyAMiffAndATime() {
        AndroidNotificationAlarms(context).schedule(NotificationType.N2, ScheduledAlarm("n2|m|u|9", "m", t0))
        val pending = shadowOf(alarmManager).scheduledAlarms.single().operation
        val intent = shadowOf(pending).savedIntent
        assertEquals(NotificationAlarmReceiver.ACTION_FIRE, intent.action)
        assertEquals(NotificationAlarmReceiver::class.java.name, intent.component?.className)
        assertEquals("n2", intent.getStringExtra(NotificationAlarmReceiver.EXTRA_TYPE))
        assertEquals("n2|m|u|9", intent.getStringExtra(NotificationAlarmReceiver.EXTRA_KEY))
        assertEquals("m", intent.getStringExtra(NotificationAlarmReceiver.EXTRA_MIFF))
        assertEquals(t0, intent.getLongExtra(NotificationAlarmReceiver.EXTRA_DUE, 0))
        assertEquals(4, intent.extras?.keySet()?.size ?: -1)
    }

    // --- Channels --------------------------------------------------------------------------------------------------------------------------

    @Test
    fun fourChannels_twoFamiliesInTwoVersions_withTheLevelsOfTheDecision_andNoBadge() {
        NotificationChannels.ensure(context)
        val channels = notificationManager.notificationChannels.associateBy { it.id }
        assertEquals(NotificationChannels.all.toSet(), channels.keys)
        assertEquals(
            NotificationManager.IMPORTANCE_DEFAULT,
            channels.getValue(NotificationChannels.REMINDERS).importance,
        )
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channels.getValue(NotificationChannels.CIRCLE).importance)
        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            channels.getValue(NotificationChannels.REMINDERS_QUIET).importance,
        )
        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            channels.getValue(NotificationChannels.CIRCLE_QUIET).importance,
        )
        assertTrue(channels.values.none { it.canShowBadge() })
        assertEquals("Rappels pour toi", channels.getValue(NotificationChannels.REMINDERS).name.toString())
        assertEquals("Nouvelles de ta Miff", channels.getValue(NotificationChannels.CIRCLE).name.toString())
        // Idempotent: asking again changes nothing.
        NotificationChannels.ensure(context)
        assertEquals(4, notificationManager.notificationChannels.size)
    }

    @Test
    fun theFamilyAndTheQuietVersionChooseTheChannel() {
        assertEquals(
            NotificationChannels.REMINDERS,
            NotificationChannels.idOf(NotificationFamily.REMINDER, quiet = false),
        )
        assertEquals(
            NotificationChannels.REMINDERS_QUIET,
            NotificationChannels.idOf(NotificationFamily.REMINDER, quiet = true),
        )
        assertEquals(NotificationChannels.CIRCLE, NotificationChannels.idOf(NotificationFamily.CIRCLE, quiet = false))
        assertEquals(
            NotificationChannels.CIRCLE_QUIET,
            NotificationChannels.idOf(NotificationFamily.CIRCLE, quiet = true),
        )
        assertEquals(NotificationType.N1.family, NotificationFamily.REMINDER)
        assertEquals(NotificationType.N6.family, NotificationFamily.CIRCLE)
    }

    @Test
    fun aChannelTurnedOffInThePhoneIsDetected() {
        NotificationChannels.ensure(context)
        assertFalse(NotificationChannels.isBlocked(context, NotificationFamily.CIRCLE))
        notificationManager
            .getNotificationChannel(NotificationChannels.CIRCLE)
            .importance = NotificationManager.IMPORTANCE_NONE
        // Robolectric keeps the importance the code sets on the object it was given.
        assertTrue(NotificationChannels.isBlocked(context, NotificationFamily.CIRCLE))
        assertFalse(NotificationChannels.isBlocked(context, NotificationFamily.REMINDER))
    }

    // --- The notification ------------------------------------------------------------------------------------------------------------------

    private fun allowed() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun theNotificationHoldsTheWordsOfThePm_nothingElse_onTheChannelOfItsFamily_underItsKey() {
        allowed()
        val key = "n3|miff-1|1700000000000"
        AndroidNotificationPresenter(context).show(NotificationType.N3, key, quiet = false)
        val shown = shadowOf(notificationManager).getNotification(key, AndroidNotificationTray.TAG_ID)
        assertNotNull(shown)
        assertEquals("Ta Miff est passée en orange", shown.extras.getString("android.title"))
        assertEquals("Prends des nouvelles de tes proches.", shown.extras.getString("android.text"))
        assertEquals(NotificationChannels.CIRCLE, shown.channelId)
        assertTrue(shown.flags and android.app.Notification.FLAG_AUTO_CANCEL != 0)
        // Nothing but the two sentences: no sub-text, no big text, no number, no photo.
        assertNull(shown.extras.getCharSequence("android.subText"))
        assertNull(shown.extras.getCharSequence("android.bigText"))
        assertEquals(0, shown.number)
    }

    @Test
    fun aQuietNotificationUsesTheQuietChannel_n1AndN2UseTheRemindersChannel() {
        allowed()
        val presenter = AndroidNotificationPresenter(context)
        presenter.show(NotificationType.N1, "n1|m|u|1", quiet = true)
        presenter.show(NotificationType.N2, "n2|m|u|1", quiet = false)
        assertEquals(
            NotificationChannels.REMINDERS_QUIET,
            shadowOf(notificationManager).getNotification("n1|m|u|1", 0).channelId,
        )
        val n2 = shadowOf(notificationManager).getNotification("n2|m|u|1", 0)
        assertEquals(NotificationChannels.REMINDERS, n2.channelId)
        assertEquals("Tu es passé rouge", n2.extras.getString("android.title"))
        assertEquals("Indique à ta Miff que tout va bien.", n2.extras.getString("android.text"))
    }

    @Test
    fun theSameKeyNeverMakesTwoNotifications() {
        allowed()
        val presenter = AndroidNotificationPresenter(context)
        repeat(3) { presenter.show(NotificationType.N1, "n1|m|u|1", quiet = false) }
        assertEquals(1, shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun withoutThePermission_orWithTheNotificationsOff_nothingIsPosted() {
        // No permission granted (Android 13+): nothing.
        AndroidNotificationPresenter(context).show(NotificationType.N1, "k", quiet = false)
        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
        allowed()
        shadowOf(notificationManager).setNotificationsEnabled(false)
        AndroidNotificationPresenter(context).show(NotificationType.N1, "k", quiet = false)
        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }

    @Test
    fun readingALineTakesItsNotificationOutOfTheTray() {
        allowed()
        AndroidNotificationPresenter(context).show(NotificationType.N1, "n1|m|u|1", quiet = false)
        assertEquals(1, shadowOf(notificationManager).allNotifications.size)
        AndroidNotificationTray(context).cancel("n1|m|u|1")
        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
        AndroidNotificationPresenter(context).show(NotificationType.N2, "n2|m|u|1", quiet = false)
        AndroidNotificationTray(context).cancelAll()
        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }

    @Test
    fun theTapCarriesAKeywordForTheTabAndTheKey_andOnlyOurIntentIsUnderstood() {
        allowed()
        AndroidNotificationPresenter(context).show(NotificationType.N1, "n1|m|u|1", quiet = false)
        val tap = shadowOf(notificationManager).getNotification("n1|m|u|1", 0).contentIntent
        val intent = shadowOf(tap).savedIntent
        assertEquals(NotificationIntents.ACTION_OPEN, intent.action)
        assertEquals(NotificationDestination.UPDATE.name, intent.getStringExtra(NotificationIntents.EXTRA_DESTINATION))
        val parsed = NotificationIntents.parse(intent)
        assertEquals(NotificationDestination.UPDATE to "n1|m|u|1", parsed)
        // Another application's intent, an unknown keyword, a null: ignored, never an error.
        assertNull(NotificationIntents.parse(null))
        assertNull(NotificationIntents.parse(Intent(Intent.ACTION_VIEW)))
        assertNull(
            NotificationIntents.parse(
                Intent(NotificationIntents.ACTION_OPEN).putExtra(NotificationIntents.EXTRA_DESTINATION, "ADMIN"),
            ),
        )
        assertNull(NotificationIntents.parse(Intent(NotificationIntents.ACTION_OPEN)))
        assertEquals(
            NotificationDestination.FEED to null,
            NotificationIntents.parse(
                Intent(NotificationIntents.ACTION_OPEN)
                    .putExtra(NotificationIntents.EXTRA_DESTINATION, "FEED")
                    .putExtra(NotificationIntents.EXTRA_KEY, "x".repeat(500)),
            ),
        )
    }

    // --- On the disk ------------------------------------------------------------------------------------------------------------------------

    @Test
    fun theSnapshotRoundTripsEveryField_aDamagedFileIsNoSnapshot_andClearErasesIt() {
        val store = SharedPreferencesPlanSnapshotStore(context)
        assertNull(store.load())
        val snapshot =
            PlanSnapshot(
                "miff-1",
                "me",
                listOf(
                    SnapshotMember("me", true, PresenceValue.OUT, 10L, 20L),
                    SnapshotMember("x", false, null, null, null),
                ),
                99L,
            )
        store.save(snapshot)
        val back = SharedPreferencesPlanSnapshotStore(context).load()!!
        assertEquals("miff-1", back.miffId)
        assertEquals("me", back.myUid)
        assertEquals(99L, back.savedAtMillis)
        assertEquals(listOf("me", "x"), back.members.map { it.uid })
        assertEquals(PresenceValue.OUT, back.members[0].presence)
        assertEquals(10L, back.members[0].presenceAtMillis)
        assertEquals(20L, back.members[0].lastUpdateAtMillis)
        assertFalse(back.members[1].sharing)
        assertNull(back.members[1].presence)

        context
            .getSharedPreferences(
                "miff_notification_plan",
                Context.MODE_PRIVATE,
            ).edit()
            .putString("snapshot", "{not json")
            .commit()
        assertNull(store.load())
        store.save(snapshot)
        store.clear()
        assertNull(store.load())
    }

    @Test
    fun theSnapshotFileHoldsNoPlaceNoPositionNoAddressNoName() {
        SharedPreferencesPlanSnapshotStore(context).save(
            PlanSnapshot("miff-1", "me", listOf(SnapshotMember("me", true, PresenceValue.IN_RISK, 1L, 2L)), 3L),
        )
        val raw =
            context
                .getSharedPreferences(
                    "miff_notification_plan",
                    Context.MODE_PRIVATE,
                ).getString("snapshot", "")
                .orEmpty()
        listOf("lat", "lng", "lon", "address", "placeId", "name", "photo", "battery").forEach {
            assertFalse("the snapshot must not hold \"$it\"", raw.contains(it, ignoreCase = true))
        }
        // The presence is a keyword of the status, nothing more precise.
        assertTrue(raw.contains("in_risk"))
    }

    @Test
    fun theLedgerKeepsKeys48Hours_attemptsPerEpisode_andTheCountersOfTheDay() {
        val h = 60L * 60L * 1000L
        val ledger = SharedPreferencesNotificationLedger(context)
        ledger.remember("k1", t0)
        ledger.addAttempt("k1")
        ledger.addAttempt("k1")
        assertEquals(2, SharedPreferencesNotificationLedger(context).attempts("k1"))
        assertEquals(setOf("k1"), SharedPreferencesNotificationLedger(context).keys(t0 + 47 * h).keys)
        assertTrue(ledger.keys(t0 + 49 * h).isEmpty())
        // A day's counters: kept for that day, forgotten the next.
        val counters = ledger.counters("2026-11-08").afterShown(NotificationType.N1).afterShown(NotificationType.N6)
        ledger.saveCounters(counters)
        assertEquals(1, ledger.counters("2026-11-08").count(NotificationType.N1))
        assertEquals(1, ledger.counters("2026-11-08").count(NotificationType.N6))
        assertEquals(0, ledger.counters("2026-11-09").total)
        // The counters survive a new key, the keys survive new counters.
        ledger.remember("k2", t0 + h)
        assertEquals(1, ledger.counters("2026-11-08").count(NotificationType.N1))
        ledger.clear()
        assertTrue(ledger.keys(t0).isEmpty())
        assertEquals(0, ledger.attempts("k1"))
        assertEquals(0, ledger.counters("2026-11-08").total)
    }

    @Test
    fun aKeyForgottenAfter48Hours_forgetsItsAttemptsToo() {
        val h = 60L * 60L * 1000L
        val ledger = SharedPreferencesNotificationLedger(context)
        ledger.remember("old", t0)
        ledger.addAttempt("old")
        ledger.remember("new", t0 + 49 * h)
        assertEquals(setOf("new"), ledger.keys(t0 + 49 * h).keys)
        assertEquals(0, ledger.attempts("old"))
    }
}
