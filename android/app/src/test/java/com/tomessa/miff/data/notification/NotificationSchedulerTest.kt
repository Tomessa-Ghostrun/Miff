package com.tomessa.miff.data.notification

import com.tomessa.miff.data.feed.CircleRead
import com.tomessa.miff.data.feed.CircleStatusReader
import com.tomessa.miff.domain.feed.MemberStatus
import com.tomessa.miff.domain.notification.DailyCounters
import com.tomessa.miff.domain.notification.ItemState
import com.tomessa.miff.domain.notification.NotificationItem
import com.tomessa.miff.domain.notification.NotificationPolicy
import com.tomessa.miff.domain.notification.NotificationPreferences
import com.tomessa.miff.domain.notification.NotificationType
import com.tomessa.miff.domain.notification.NotifiedKeys
import com.tomessa.miff.domain.notification.PlanSnapshot
import com.tomessa.miff.domain.notification.SnapshotMember
import com.tomessa.miff.domain.tracking.PresenceValue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The scheduler of NOTIF-4A and NOTIF-5A on the JVM, with doubles for the alarms, the tray, the server and the disk: what is planned, cancelled, shown, written
 * to the centre only, and read from the server. The vectors T1 to T15 of the ticket are those of `NotificationPlannerTest`; this is their application.
 */
class NotificationSchedulerTest {
    private val paris = ZoneId.of("Europe/Paris")

    private fun at(
        hour: Int,
        minute: Int,
        ms: Long = 0,
    ) = ZonedDateTime.of(2026, 11, 8, hour, minute, 0, 0, paris).toInstant().toEpochMilli() + ms

    private val miff = "miff-1"
    private val me = "me"

    // --- Doubles -------------------------------------------------------------------------------------------------------------------------

    private class MemorySnapshots(
        var value: PlanSnapshot? = null,
    ) : PlanSnapshotStore {
        override fun load() = value

        override fun save(snapshot: PlanSnapshot) {
            value = snapshot
        }

        override fun clear() {
            value = null
        }
    }

    private class MemoryAlarms : NotificationAlarms {
        val map = mutableMapOf<NotificationType, ScheduledAlarm>()
        var scheduleCalls = 0

        override fun schedule(
            type: NotificationType,
            alarm: ScheduledAlarm,
        ) {
            scheduleCalls++
            map[type] = alarm
        }

        override fun cancel(type: NotificationType) {
            map.remove(type)
        }

        override fun cancelAll() = map.clear()

        override fun scheduled() = map.toMap()

        override fun consumed(type: NotificationType) {
            map.remove(type)
        }
    }

    private class MemoryLedger : NotificationLedger {
        var keys = mapOf<String, Long>()
        val attempts = mutableMapOf<String, Int>()
        var counters = DailyCounters("")
        var cleared = 0

        override fun keys(nowMillis: Long) = NotifiedKeys.prune(keys, nowMillis)

        override fun remember(
            key: String,
            nowMillis: Long,
        ) {
            keys = NotifiedKeys.add(keys, key, nowMillis)
        }

        override fun attempts(key: String) = attempts[key] ?: 0

        override fun addAttempt(key: String) {
            attempts[key] = attempts(key) + 1
        }

        override fun counters(day: String) = counters.forDay(day)

        override fun saveCounters(counters: DailyCounters) {
            this.counters = counters
        }

        override fun clear() {
            keys = emptyMap()
            attempts.clear()
            counters = DailyCounters("")
            cleared++
        }
    }

    private class Shown(
        val type: NotificationType,
        val key: String,
        val quiet: Boolean,
    )

    private class MemoryPresenter : NotificationPresenter {
        val shown = mutableListOf<Shown>()

        override fun show(
            type: NotificationType,
            key: String,
            quiet: Boolean,
        ) {
            shown += Shown(type, key, quiet)
        }
    }

    private class FakeReader(
        var answer: CircleRead = CircleRead.Unreachable,
    ) : CircleStatusReader {
        var reads = 0
        var lastMiff: String? = null

        override suspend fun read(miffId: String): CircleRead {
            reads++
            lastMiff = miffId
            return answer
        }
    }

    private class PrefsStore(
        var saved: NotificationPreferences = NotificationPreferences(),
    ) : NotificationPreferencesStore {
        override fun load() = saved

        override fun save(preferences: NotificationPreferences) {
            saved = preferences
        }

        override fun clear() {
            saved = NotificationPreferences()
        }
    }

    private class CentreStore : NotificationCenterStore {
        var items = emptyList<NotificationItem>()

        override fun load() = items

        override fun save(items: List<NotificationItem>) {
            this.items = items
        }

        override fun clear() {
            items = emptyList()
        }
    }

    private var now = at(14, 0)
    private var foreground = false
    private var permission = true
    private var session = true
    private val snapshots = MemorySnapshots()
    private val alarms = MemoryAlarms()
    private val ledger = MemoryLedger()
    private val presenter = MemoryPresenter()
    private val reader = FakeReader()
    private val prefs = PrefsStore()
    private val settings = NotificationSettings(prefs)
    private val centreStore = CentreStore()
    private val center = NotificationCenter(centreStore, nowMillis = { now })

    private val scheduler =
        NotificationScheduler(
            snapshots = snapshots,
            settings = settings,
            permissionAllowed = { permission },
            sessionValid = { session && it == me },
            alarms = alarms,
            ledger = ledger,
            center = center,
            presenter = presenter,
            appInForeground = { foreground },
            reader = reader,
            nowMillis = { now },
            zone = { paris },
        )

    private fun member(
        uid: String,
        presence: PresenceValue?,
        presenceAt: Long?,
        update: Long? = null,
        sharing: Boolean = true,
    ) = SnapshotMember(uid, sharing, presence, presenceAt, update)

    private fun trusted(uid: String) = member(uid, PresenceValue.IN_TRUSTED, at(9, 0), at(9, 0))

    private fun out(
        uid: String,
        ref: Long,
        update: Long? = null,
    ) = member(uid, PresenceValue.OUT, ref, update)

    private fun feedSees(vararg members: SnapshotMember) = scheduler.onStatuses(miff, me, members.toList())

    private fun lines() = center.items.value

    // --- N1 and N2: planned from what the Feed saw --------------------------------------------------------------------------------------------

    @Test
    fun theFeedSeesMeOutSinceTheMorning_n1AndN2AreAsked_atTheFirstMillisecondPastTwoAndFourHours() {
        feedSees(out(me, at(13, 40)), trusted("x"))
        assertEquals(at(15, 40, 1), alarms.map.getValue(NotificationType.N1).dueAtMillis)
        assertEquals(at(17, 40, 1), alarms.map.getValue(NotificationType.N2).dueAtMillis)
        // Responsible: neither N3 nor N4.
        assertNull(alarms.map[NotificationType.N3])
        assertNull(alarms.map[NotificationType.N4])
        assertTrue(presenter.shown.isEmpty())
    }

    @Test
    fun theSamePlanAgain_asksNothingNew_noChurnOfAlarms() {
        feedSees(out(me, at(13, 40)), trusted("x"))
        val calls = alarms.scheduleCalls
        scheduler.reconcile()
        scheduler.reconcile()
        assertEquals(calls, alarms.scheduleCalls)
    }

    @Test
    fun myDepartureNoticedByTheDetection_plansTheRemindersWithoutWaitingForTheFeed() {
        feedSees(trusted(me), trusted("x"))
        assertTrue(alarms.map.isEmpty())
        scheduler.onMyPresence(miff, PresenceValue.OUT, at(14, 0), setLastUpdate = false)
        assertEquals(at(16, 0, 1), alarms.map.getValue(NotificationType.N1).dueAtMillis)
        // Back in a trusted zone: an arrival is an update, everything is cancelled.
        now = at(14, 30)
        scheduler.onMyPresence(miff, PresenceValue.IN_TRUSTED, at(14, 30), setLastUpdate = true)
        assertTrue(alarms.map.isEmpty())
    }

    @Test
    fun anUpdatePublished_cancelsWhatWaited_andPlansFromTheNewReference_criterionNP03() {
        feedSees(out(me, at(13, 40)), trusted("x"))
        now = at(15, 30)
        scheduler.onMyUpdate(listOf(miff), at(15, 30))
        assertEquals(at(17, 30, 1), alarms.map.getValue(NotificationType.N1).dueAtMillis)
        assertEquals(at(19, 30, 1), alarms.map.getValue(NotificationType.N2).dueAtMillis)
        // An update in another Miff does not move this one.
        scheduler.onMyUpdate(listOf("other"), at(16, 0))
        assertEquals(at(17, 30, 1), alarms.map.getValue(NotificationType.N1).dueAtMillis)
    }

    @Test
    fun aSwitchTurnedOff_cancelsWhatItCovers_atOnce_andOnlyThat() {
        feedSees(out(me, at(13, 40)), trusted("x"))
        settings.setType(NotificationType.N1, false)
        scheduler.reconcile()
        assertNull(alarms.map[NotificationType.N1])
        assertEquals(at(17, 40, 1), alarms.map.getValue(NotificationType.N2).dueAtMillis)
        settings.setEnabled(false)
        scheduler.reconcile()
        assertTrue(alarms.map.isEmpty())
        // Turned back on: planned again.
        settings.setEnabled(true)
        settings.setType(NotificationType.N1, true)
        scheduler.reconcile()
        assertEquals(setOf(NotificationType.N1, NotificationType.N2), alarms.map.keys)
    }

    @Test
    fun thePermissionRemoved_theSessionLost_oraStoppedSharing_cancelEverything() {
        feedSees(out(me, at(13, 40)), trusted("x"))
        permission = false
        scheduler.reconcile()
        assertTrue(alarms.map.isEmpty())
        permission = true
        scheduler.reconcile()
        assertEquals(2, alarms.map.size)
        session = false
        scheduler.reconcile()
        assertTrue(alarms.map.isEmpty())
        session = true
        scheduler.reconcile()
        // Sharing stopped: the Feed says it with my sharing flag off.
        feedSees(member(me, PresenceValue.OUT, at(13, 40), sharing = false), trusted("x"))
        assertTrue(alarms.map.isEmpty())
    }

    @Test
    fun aChangeOfActiveMiff_cancelsAllAtOnce_t10() {
        feedSees(out(me, at(13, 40)), trusted("x"))
        scheduler.onActiveMiffChanged("miff-2")
        assertTrue(alarms.map.isEmpty())
        assertNull(snapshots.value)
        // The same Miff chosen again changes nothing.
        feedSees(out(me, at(13, 40)), trusted("x"))
        scheduler.onActiveMiffChanged(miff)
        assertEquals(2, alarms.map.size)
    }

    @Test
    fun clearAll_forgetsEverything_atTheSignOut() {
        feedSees(out(me, at(13, 40)), trusted("x"))
        ledger.remember("k", now)
        scheduler.clearAll()
        assertTrue(alarms.map.isEmpty())
        assertNull(snapshots.value)
        assertEquals(1, ledger.cleared)
        assertTrue(ledger.keys(now).isEmpty())
    }

    // --- An alarm rings ------------------------------------------------------------------------------------------------------------------

    @Test
    fun n1Rings_atOrange_theNotificationIsShown_theLineIsWritten_theKeyIsRemembered() {
        feedSees(out(me, at(13, 40)), trusted("x"))
        val alarm = alarms.map.getValue(NotificationType.N1)
        now = alarm.dueAtMillis + 2 * 60 * 1000
        scheduler.fire(NotificationType.N1, alarm.key)
        val shown = presenter.shown.single()
        assertEquals(NotificationType.N1, shown.type)
        assertFalse("by day: with sound", shown.quiet)
        assertEquals(listOf(alarm.key), lines().map { it.id })
        assertEquals(ItemState.SHOWN, lines().single().state)
        assertTrue(alarm.key in ledger.keys(now))
        assertEquals(1, ledger.counters.count(NotificationType.N1))
        // N2 is still waiting.
        assertTrue(NotificationType.N2 in alarms.map)
    }

    @Test
    fun ringingAgainOrAfterARestart_neverShowsTwice_criterionNM3() {
        feedSees(out(me, at(13, 40)), trusted("x"))
        val alarm = alarms.map.getValue(NotificationType.N1)
        now = alarm.dueAtMillis
        scheduler.fire(NotificationType.N1, alarm.key)
        scheduler.fire(NotificationType.N1, alarm.key)
        scheduler.reconcile(force = true)
        now += 60 * 60 * 1000
        scheduler.reconcile(force = true)
        assertEquals(1, presenter.shown.count { it.type == NotificationType.N1 })
        assertEquals(1, lines().count { it.type == NotificationType.N1 })
    }

    @Test
    fun aStaleAlarm_anUpdateWasPublishedMeanwhile_showsNothing() {
        feedSees(out(me, at(13, 40)), trusted("x"))
        val stale = alarms.map.getValue(NotificationType.N1)
        now = at(15, 30)
        scheduler.onMyUpdate(listOf(miff), at(15, 30))
        // The old alarm rings anyway (the system was a moment late to cancel it): the plan of the moment decides.
        now = stale.dueAtMillis
        scheduler.fire(NotificationType.N1, stale.key)
        assertTrue(presenter.shown.isEmpty())
        assertTrue(lines().isEmpty())
    }

    @Test
    fun aForgedIntent_aKeyThePlanDoesNotHold_showsNothing() {
        feedSees(out(me, at(13, 40)), trusted("x"))
        now = at(16, 0)
        scheduler.fire(NotificationType.N1, "n1|miff-1|me|123")
        scheduler.fire(NotificationType.N2, "anything")
        assertTrue(presenter.shown.isEmpty())
    }

    @Test
    fun n1RingingWhileIAlreadyAmRed_showsNothing_n2TakesOver() {
        feedSees(out(me, at(13, 40)), trusted("x"))
        val n1 = alarms.map.getValue(NotificationType.N1)
        now = at(18, 0) // red since 17:40
        scheduler.fire(NotificationType.N1, n1.key)
        assertTrue(presenter.shown.none { it.type == NotificationType.N1 })
    }

    @Test
    fun inFront_noSystemNotification_aSilentLineOfTheCentre_t12() {
        feedSees(out(me, at(13, 40)), trusted("x"))
        val alarm = alarms.map.getValue(NotificationType.N1)
        now = alarm.dueAtMillis
        foreground = true
        scheduler.fire(NotificationType.N1, alarm.key)
        assertTrue(presenter.shown.isEmpty())
        assertEquals(ItemState.SILENT, lines().single().state)
        assertEquals("a silent line does not use up the cap", 0, ledger.counters.count(NotificationType.N1))
    }

    @Test
    fun atNight_n1IsQuiet_n2KeepsItsLevel_t13() {
        // Left at 01:00: orange at 03:00:00.001, red at 05:00:00.001 (both in the quiet hours).
        now = at(1, 30)
        feedSees(out(me, at(1, 0)), trusted("x"))
        val n1 = alarms.map.getValue(NotificationType.N1)
        now = n1.dueAtMillis
        scheduler.fire(NotificationType.N1, n1.key)
        assertTrue(presenter.shown.single().quiet)
        val n2 = alarms.map.getValue(NotificationType.N2)
        now = n2.dueAtMillis
        scheduler.fire(NotificationType.N2, n2.key)
        assertFalse(presenter.shown.last().quiet)
    }

    @Test
    fun theThirdN1OfTheDay_isOnlyALineOfTheCentre_t9() {
        ledger.counters = DailyCounters("2026-11-08", mapOf(NotificationType.N1 to 2))
        feedSees(out(me, at(13, 40)), trusted("x"))
        val alarm = alarms.map.getValue(NotificationType.N1)
        now = alarm.dueAtMillis
        scheduler.fire(NotificationType.N1, alarm.key)
        assertTrue(presenter.shown.isEmpty())
        assertEquals(ItemState.SILENT, lines().single().state)
    }

    // --- Catch-up (restart, late alarm) ---------------------------------------------------------------------------------------------------------

    @Test
    fun afterARestartWithin15Minutes_theReminderIsStillShown() {
        feedSees(out(me, at(13, 40)), trusted("x"))
        val due = at(15, 40, 1)
        alarms.cancelAll() // the restart erased the alarms
        now = due + 10 * 60 * 1000
        scheduler.reconcile(force = true)
        assertEquals(listOf(NotificationType.N1), presenter.shown.map { it.type })
    }

    @Test
    fun afterARestartMoreThan15MinutesLate_onlyALineOfTheCentreIsWritten_section208() {
        feedSees(out(me, at(13, 40)), trusted("x"))
        val due = at(15, 40, 1)
        alarms.cancelAll()
        now = due + 16 * 60 * 1000
        scheduler.reconcile(force = true)
        assertTrue(presenter.shown.isEmpty())
        assertEquals(ItemState.SILENT, lines().single { it.type == NotificationType.N1 }.state)
    }

    @Test
    fun theRestartAsksTheAlarmsAgain_fromTheSnapshot_t11() {
        feedSees(trusted(me), out("x", at(13, 40)))
        alarms.cancelAll()
        now = at(16, 0)
        scheduler.reconcile(force = true)
        // N3 at 16:10:00.001 is back (a verification of the server will precede it).
        assertEquals(at(16, 10, 1), alarms.map.getValue(NotificationType.N3).dueAtMillis)
    }

    // --- N3 and N4: the server is read a moment before ---------------------------------------------------------------------------------------

    private fun serverSays(vararg statuses: Pair<String, MemberStatus>) = CircleRead.Fresh(statuses.toMap())

    private fun status(
        presence: PresenceValue,
        presenceAt: Long?,
        update: Long? = null,
    ) = MemberStatus(presence, null, presenceAt, update, false, null, null, null)

    private fun circleSetUp(): ScheduledAlarm {
        feedSees(trusted(me), out("x", at(13, 40)))
        return alarms.map.getValue(NotificationType.N3)
    }

    @Test
    fun n3_theServerConfirmsTheMiffIsStillOrange_itIsShown_andTheServerWasReadOnce() =
        runTest {
            val alarm = circleSetUp()
            assertEquals(at(16, 10, 1), alarm.dueAtMillis)
            now = alarm.dueAtMillis + 30_000
            reader.answer =
                serverSays(
                    me to status(PresenceValue.IN_TRUSTED, at(9, 0), at(9, 0)),
                    "x" to status(PresenceValue.OUT, at(13, 40)),
                )
            scheduler.fireCircle(NotificationType.N3, alarm.key)
            assertEquals(1, reader.reads)
            assertEquals(miff, reader.lastMiff)
            assertEquals(listOf(NotificationType.N3), presenter.shown.map { it.type })
            assertTrue(alarm.key in ledger.keys(now))
        }

    @Test
    fun n3_xGaveNewsAt1600_theVerificationAbandonsInSilence_t8() =
        runTest {
            val alarm = circleSetUp()
            now = alarm.dueAtMillis
            reader.answer =
                serverSays(
                    me to status(PresenceValue.IN_TRUSTED, at(9, 0), at(9, 0)),
                    "x" to status(PresenceValue.OUT, at(13, 40), update = at(16, 0)),
                )
            scheduler.fireCircle(NotificationType.N3, alarm.key)
            assertTrue(presenter.shown.isEmpty())
            assertTrue(lines().isEmpty())
            // The episode is settled: it never comes back, and the plan moves on to the new reference (orange only at 18:00:00.001).
            assertTrue(alarm.key in ledger.keys(now))
            assertEquals(at(18, 30, 1), alarms.map.getValue(NotificationType.N3).dueAtMillis)
        }

    @Test
    fun n3_theRulesRefuse_nothingIsShown_noSecondTry() =
        runTest {
            val alarm = circleSetUp()
            now = alarm.dueAtMillis
            reader.answer = CircleRead.Refused
            scheduler.fireCircle(NotificationType.N3, alarm.key)
            assertTrue(presenter.shown.isEmpty())
            assertTrue(NotificationType.N3 !in alarms.map || alarms.map.getValue(NotificationType.N3).key != alarm.key)
            assertEquals(1, reader.reads)
        }

    @Test
    fun n3_offline_oneMoreTryTenMinutesLater_thenNothingIsShown_decisionQ33() =
        runTest {
            val alarm = circleSetUp()
            now = alarm.dueAtMillis
            reader.answer = CircleRead.Unreachable
            scheduler.fireCircle(NotificationType.N3, alarm.key)
            val retry = alarms.map.getValue(NotificationType.N3)
            assertEquals(alarm.key, retry.key)
            assertEquals(now + 10 * 60 * 1000, retry.dueAtMillis)
            assertTrue(presenter.shown.isEmpty())
            // The retry rings: still offline. Nothing is shown, no "we could not check", no third reading.
            now = retry.dueAtMillis
            scheduler.fireCircle(NotificationType.N3, alarm.key)
            assertEquals(2, reader.reads)
            assertTrue(presenter.shown.isEmpty())
            assertTrue(lines().isEmpty())
            assertTrue(alarm.key in ledger.keys(now))
            scheduler.reconcile()
            scheduler.reconcile(force = true)
            assertEquals("never more than two readings per episode", 2, reader.reads)
        }

    @Test
    fun n3_theRetryFindsTheServer_andShowsIt_withinTheFifteenMinutes() =
        runTest {
            val alarm = circleSetUp()
            now = alarm.dueAtMillis
            reader.answer = CircleRead.Unreachable
            scheduler.fireCircle(NotificationType.N3, alarm.key)
            now += 10 * 60 * 1000
            reader.answer =
                serverSays(
                    me to status(PresenceValue.IN_TRUSTED, at(9, 0), at(9, 0)),
                    "x" to status(PresenceValue.OUT, at(13, 40)),
                )
            scheduler.fireCircle(NotificationType.N3, alarm.key)
            assertEquals(listOf(NotificationType.N3), presenter.shown.map { it.type })
        }

    @Test
    fun n3_aStaleAlarm_nothingIsReadFromTheServer() =
        runTest {
            val alarm = circleSetUp()
            now = alarm.dueAtMillis
            settings.setType(NotificationType.N3, false)
            scheduler.fireCircle(NotificationType.N3, alarm.key)
            assertEquals("a notification that is no longer planned costs no reading", 0, reader.reads)
            assertTrue(presenter.shown.isEmpty())
        }

    @Test
    fun n3_dueWhileTheAppIsInFront_noReadingNeeded_theFeedFactsAreLive_aSilentLine() {
        feedSees(trusted(me), out("x", at(13, 40)))
        alarms.cancelAll()
        now = at(16, 12)
        foreground = true
        scheduler.reconcile()
        assertEquals(0, reader.reads)
        assertTrue(presenter.shown.isEmpty())
        assertEquals(NotificationType.N3, lines().single().type)
        assertEquals(ItemState.SILENT, lines().single().state)
    }

    @Test
    fun n3_dueInTheBackground_aVerificationIsAskedForAMomentFromNow_neverReadInline() {
        feedSees(trusted(me), out("x", at(13, 40)))
        alarms.cancelAll()
        now = at(16, 12)
        scheduler.reconcile()
        assertEquals(0, reader.reads)
        val verification = alarms.map.getValue(NotificationType.N3)
        assertTrue(verification.dueAtMillis > now)
        assertTrue(presenter.shown.isEmpty())
    }

    @Test
    fun n4_followsTheSameRoad_andIsNormalAtNight() =
        runTest {
            // X left at 20:30: red at 00:30:00.001 the next day, so N4 would leave at 01:00:00.001 (a quiet hour, but N4 keeps its level).
            now = at(23, 0)
            feedSees(trusted(me), out("x", at(20, 30)))
            val n4 = alarms.map.getValue(NotificationType.N4)
            now = n4.dueAtMillis + 1000
            reader.answer =
                serverSays(
                    me to status(PresenceValue.IN_TRUSTED, at(9, 0), at(9, 0)),
                    "x" to status(PresenceValue.OUT, at(20, 30)),
                )
            scheduler.fireCircle(NotificationType.N4, n4.key)
            assertEquals(listOf(NotificationType.N4), presenter.shown.map { it.type })
            assertFalse(presenter.shown.single().quiet)
        }

    // --- N6: I entered a danger zone (vectors V1 to V10) ------------------------------------------------------------------------------------------

    private val zoneId = "z1"
    private val minute = 60L * 1000L

    private fun n6Lines() = lines().filter { it.type == NotificationType.N6 }

    private fun n6Shown() = presenter.shown.filter { it.type == NotificationType.N6 }

    @Test
    fun v1_iEnterADangerZone_n6IsShownAtOnce_theRemindersToMeAreCancelled_nothingIsReadFromTheServer() {
        feedSees(out(me, at(13, 40)), trusted("x"))
        assertEquals(setOf(NotificationType.N1, NotificationType.N2), alarms.map.keys)
        now = at(14, 10)
        // The detection says both: my presence is now in_risk, and I entered the zone.
        scheduler.onMyPresence(miff, PresenceValue.IN_RISK, now, setLastUpdate = false)
        scheduler.onEnteredDanger(me, miff, zoneId, now)
        assertTrue("N1 and N2 are cancelled (no reminder to me in a danger zone, decision Q27)", alarms.map.isEmpty())
        val shown = n6Shown().single()
        assertEquals("n6|$miff|$zoneId|$now", shown.key)
        assertFalse("by day: with sound", shown.quiet)
        assertEquals(ItemState.SHOWN, n6Lines().single().state)
        assertEquals(miff, n6Lines().single().miffId)
        assertEquals("N6 reads nothing from the server", 0, reader.reads)
        assertEquals(1, ledger.counters.count(NotificationType.N6))
    }

    @Test
    fun v5_aZoneLeftAndEnteredAgainWithinSixtyMinutes_givesOneNotification_afterThatAnotherOne() {
        assertEquals(60 * minute, NotificationPolicy.N6_REARM_MILLIS)
        scheduler.onEnteredDanger(me, miff, zoneId, now)
        now += 30 * minute
        scheduler.onEnteredDanger(me, miff, zoneId, now)
        assertEquals(1, n6Shown().size)
        now += 29 * minute // 59 minutes after the first
        scheduler.onEnteredDanger(me, miff, zoneId, now)
        assertEquals(1, n6Shown().size)
        // Past the 60 minutes (counted from the last notification: 61 minutes later).
        now += 62 * minute
        scheduler.onEnteredDanger(me, miff, zoneId, now)
        assertEquals(2, n6Shown().size)
        // Another zone is not held back by the first.
        scheduler.onEnteredDanger(me, miff, "z2", now + 1)
        assertEquals(3, n6Shown().size)
    }

    @Test
    fun theSameKeyNeverTwice_andAKeyIsPerMiffAndZone() {
        scheduler.onEnteredDanger(me, miff, zoneId, now)
        scheduler.onEnteredDanger(me, miff, zoneId, now)
        assertEquals(1, n6Shown().size)
        scheduler.onEnteredDanger(me, "miff-2", zoneId, now)
        assertEquals("another Miff, same zone identifier: its own notification", 2, n6Shown().size)
    }

    @Test
    fun v6_anEntryAtTwoInTheMorning_isShownQuietly() {
        now = at(2, 0)
        scheduler.onEnteredDanger(me, miff, zoneId, now)
        assertTrue(n6Shown().single().quiet)
    }

    @Test
    fun v7_theFourthEntryOfTheDay_isOnlyALineOfTheCentre_theCapIsThreeOutsideTheTotalOfSix() {
        // Three zones in a row, then a fourth: three shown, the fourth silent.
        listOf("a", "b", "c", "d").forEachIndexed { i, zone ->
            scheduler.onEnteredDanger(me, miff, zone, now + i)
        }
        assertEquals(3, n6Shown().size)
        val lastLine = n6Lines().first { it.id.endsWith("|d|${now + 3}") }
        assertEquals(ItemState.SILENT, lastLine.state)
        // N6 does not count against the total of six, and the six do not block N6.
        ledger.counters =
            DailyCounters(
                "2026-11-08",
                mapOf(
                    NotificationType.N1 to 2,
                    NotificationType.N2 to 2,
                    NotificationType.N3 to 1,
                    NotificationType.N4 to 1,
                ),
            )
        presenter.shown.clear()
        scheduler.onEnteredDanger(me, "miff-9", "e", now + 10)
        // The counters above were replaced by a state with the six of N1 to N4 reached and no N6: N6 is outside that total, so it is still shown.
        assertEquals(1, n6Shown().size)
    }

    @Test
    fun v8_theAppInFront_noSystemNotification_aSilentLine() {
        foreground = true
        scheduler.onEnteredDanger(me, miff, zoneId, now)
        assertTrue(n6Shown().isEmpty())
        assertEquals(ItemState.SILENT, n6Lines().single().state)
        assertEquals("a silent line does not use up the cap", 0, ledger.counters.count(NotificationType.N6))
    }

    @Test
    fun v9_theSwitchOff_theGeneralSwitchOff_thePermissionRefused_orNoSession_nothingAtAll() {
        settings.setType(NotificationType.N6, false)
        scheduler.onEnteredDanger(me, miff, "a", now)
        settings.setType(NotificationType.N6, true)
        settings.setEnabled(false)
        scheduler.onEnteredDanger(me, miff, "b", now + 1)
        settings.setEnabled(true)
        permission = false
        scheduler.onEnteredDanger(me, miff, "c", now + 2)
        permission = true
        session = false
        scheduler.onEnteredDanger(me, miff, "d", now + 3)
        scheduler.onEnteredDanger("someone-else", miff, "e", now + 4)
        session = true
        assertTrue(n6Shown().isEmpty())
        assertTrue("not even a line of the centre", n6Lines().isEmpty())
        // A switch off does not use up the re-arm: turned back on, the very next entry is told.
        scheduler.onEnteredDanger(me, miff, "a", now + 5)
        assertEquals(1, n6Shown().size)
    }

    @Test
    fun v10_aFreeAccountMemberOfAMiffWhoseCreatorPlacedTheZone_receivesN6_likeAnyone() {
        // N6 does not look at the plan of the account: the zone applies to every member of the Miff, and so does the notification.
        scheduler.onEnteredDanger(me, miff, "zone-of-the-creator", now)
        assertEquals(1, n6Shown().size)
    }

    @Test
    fun n6_needsNoSnapshotNoServer_andLeavesTheRemindersOfOtherMiffsAlone() {
        scheduler.onEnteredDanger(me, miff, zoneId, now)
        assertNull(snapshots.value)
        assertEquals(0, reader.reads)
        assertTrue(alarms.map.isEmpty())
    }

    @Test
    fun n6_hasNoAlarm_itIsImmediate() {
        feedSees(trusted(me), out("x", at(13, 40)))
        scheduler.onEnteredDanger(me, miff, zoneId, now)
        assertTrue(alarms.map.keys.none { it == NotificationType.N6 })
        assertTrue(NotificationScheduler.PLANNED_TYPES.none { it == NotificationType.N6 })
    }

    // --- Privacy ---------------------------------------------------------------------------------------------------------------------------

    @Test
    fun whatIsKeptCarriesNoPlaceNoPositionNoName_andPrintsNothing() {
        feedSees(out(me, at(13, 40)), trusted("x"))
        val snapshot = snapshots.value!!
        assertEquals("PlanSnapshot(redacted)", snapshot.toString())
        assertEquals("SnapshotMember(redacted)", snapshot.members.first().toString())
        assertEquals(
            "ScheduledAlarm(redacted)",
            alarms.map.values
                .first()
                .toString(),
        )
    }
}
