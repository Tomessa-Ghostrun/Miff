package com.tomessa.miff.data.notification

import com.tomessa.miff.data.feed.CircleRead
import com.tomessa.miff.data.feed.CircleStatusReader
import com.tomessa.miff.domain.notification.CircleNewsKeys
import com.tomessa.miff.domain.notification.CircleNewsPayload
import com.tomessa.miff.domain.notification.DailyCounters
import com.tomessa.miff.domain.notification.ItemState
import com.tomessa.miff.domain.notification.NotificationItem
import com.tomessa.miff.domain.notification.NotificationPolicy
import com.tomessa.miff.domain.notification.NotificationPreferences
import com.tomessa.miff.domain.notification.NotificationType
import com.tomessa.miff.domain.notification.NotifiedKeys
import com.tomessa.miff.domain.notification.PlanSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * N9 ("{Prénom} a donné des nouvelles", push of the server) applied by the scheduler, with doubles: shown, written to the centre only, or dropped; the caps, the
 * switches, the foreground, the night, the account, the session. Nothing is read from the server (the reader double would fail the test if it were).
 */
class CircleNewsSchedulerTest {
    private val paris = ZoneId.of("Europe/Paris")

    private fun at(
        hour: Int,
        minute: Int = 0,
    ) = ZonedDateTime.of(2026, 11, 12, hour, minute, 0, 0, paris).toInstant().toEpochMilli()

    private class Snapshots : PlanSnapshotStore {
        override fun load(): PlanSnapshot? = null

        override fun save(snapshot: PlanSnapshot) = Unit

        override fun clear() = Unit
    }

    private class TravelEnds : TravelEndStore {
        override fun load(): TravelEndMemo? = null

        override fun save(memo: TravelEndMemo) = Unit

        override fun clear() = Unit
    }

    private class NoAlarms : NotificationAlarms {
        override fun schedule(
            type: NotificationType,
            alarm: ScheduledAlarm,
        ) = Unit

        override fun cancel(type: NotificationType) = Unit

        override fun cancelAll() = Unit

        override fun scheduled(): Map<NotificationType, ScheduledAlarm> = emptyMap()

        override fun consumed(type: NotificationType) = Unit
    }

    private class MemoryLedger : NotificationLedger {
        var keys = mapOf<String, Long>()
        var counters = DailyCounters("")

        override fun keys(nowMillis: Long) = NotifiedKeys.prune(keys, nowMillis)

        override fun remember(
            key: String,
            nowMillis: Long,
        ) {
            keys = NotifiedKeys.add(keys, key, nowMillis)
        }

        override fun attempts(key: String) = 0

        override fun addAttempt(key: String) = Unit

        override fun counters(day: String) = counters.forDay(day)

        override fun saveCounters(counters: DailyCounters) {
            this.counters = counters
        }

        override fun clear() {
            keys = emptyMap()
            counters = DailyCounters("")
        }
    }

    private class Shown(
        val tag: String,
        val centerId: String,
        val firstName: String,
        val miffId: String,
        val quiet: Boolean,
    )

    private class Presenter : NotificationPresenter {
        val shown = mutableListOf<Shown>()
        val others = mutableListOf<NotificationType>()

        override fun show(
            type: NotificationType,
            key: String,
            quiet: Boolean,
        ) {
            others += type
        }

        override fun showCircleNews(
            news: CircleNewsPayload,
            centerId: String,
            quiet: Boolean,
        ) {
            shown += Shown(CircleNewsKeys.trayTag(news), centerId, news.authorFirstName, news.miffId, quiet)
        }
    }

    private class NoServer : CircleStatusReader {
        var reads = 0

        override suspend fun read(miffId: String): CircleRead {
            reads++
            return CircleRead.Unreachable
        }
    }

    private class Prefs(
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

    private var now = at(14)
    private var foreground = false
    private var permission = true
    private var session = true
    private val ledger = MemoryLedger()
    private val presenter = Presenter()
    private val server = NoServer()
    private val settings = NotificationSettings(Prefs())
    private val centreStore = CentreStore()
    private val center = NotificationCenter(centreStore, nowMillis = { now })

    private val scheduler =
        NotificationScheduler(
            snapshots = Snapshots(),
            travelEnds = TravelEnds(),
            settings = settings,
            permissionAllowed = { permission },
            sessionValid = { session && it == "me" },
            alarms = NoAlarms(),
            ledger = ledger,
            center = center,
            presenter = presenter,
            appInForeground = { foreground },
            reader = server,
            nowMillis = { now },
            zone = { paris },
        )

    private fun news(
        author: String? = "author1",
        name: String = "Amandine",
        sentAt: Long = now,
        miff: String = "miffAAAA",
    ) = CircleNewsPayload(miff, author, name, sentAt)

    private fun lines() = center.items.value

    @Test
    fun aNewsIsShown_andWrittenToTheCentreWithTheFirstNameOfTheAuthorOnly() {
        scheduler.onCircleNews("me", news())
        val shown = presenter.shown.single()
        assertEquals("Amandine", shown.firstName)
        assertEquals("miffAAAA", shown.miffId)
        assertFalse("by day, the normal channel", shown.quiet)
        val line = lines().single()
        assertEquals(NotificationType.N9, line.type)
        assertEquals(ItemState.SHOWN, line.state)
        assertEquals("Amandine", line.who)
        assertEquals("miffAAAA", line.miffId)
        assertEquals(shown.centerId, line.id)
        assertEquals(0, server.reads)
    }

    @Test
    fun theSameMessageDeliveredTwiceMakesOneNotificationAndOneLine() {
        val message = news()
        scheduler.onCircleNews("me", message)
        scheduler.onCircleNews("me", message)
        assertEquals(1, presenter.shown.size)
        assertEquals(1, lines().size)
    }

    @Test
    fun twoNewsOfTheSameAuthorShareTheTrayTag_soTheSecondReplacesTheFirst_butKeepTwoLines() {
        scheduler.onCircleNews("me", news(sentAt = now))
        now += 2 * 3_600_000L
        scheduler.onCircleNews("me", news(sentAt = now))
        assertEquals(2, presenter.shown.size)
        assertEquals(presenter.shown[0].tag, presenter.shown[1].tag)
        assertTrue(presenter.shown[0].centerId != presenter.shown[1].centerId)
        assertEquals(2, lines().size)
    }

    @Test
    fun readingALineTakesTheNotificationOfItsAuthorOutOfTheTray() {
        val cancelled = mutableListOf<String>()
        val c =
            NotificationCenter(CentreStore(), nowMillis = { now }, onRead = {
                cancelled +=
                    CircleNewsKeys.trayTagOf(it)
            })
        val message = news()
        c.add(
            NotificationItem(CircleNewsKeys.centerId(message), NotificationType.N9, "miffAAAA", now, who = "Amandine"),
        )
        c.markRead(CircleNewsKeys.centerId(message))
        assertEquals(listOf(CircleNewsKeys.trayTag(message)), cancelled)
    }

    @Test
    fun theAuthorIsNeverNotifiedOfHerOwnUpdate() {
        scheduler.onCircleNews("me", news(author = "me"))
        assertTrue(presenter.shown.isEmpty())
        assertTrue(lines().isEmpty())
    }

    @Test
    fun theGeneralSwitchOffDropsIt_theSwitchOfN9OffDropsIt_theOthersChangeNothing() {
        settings.setEnabled(false)
        scheduler.onCircleNews("me", news())
        assertTrue(presenter.shown.isEmpty() && lines().isEmpty())
        settings.setEnabled(true)
        settings.setType(NotificationType.N9, false)
        scheduler.onCircleNews("me", news(sentAt = now + 1))
        assertTrue(presenter.shown.isEmpty() && lines().isEmpty())
        settings.setType(NotificationType.N9, true)
        settings.setType(NotificationType.N3, false)
        scheduler.onCircleNews("me", news(sentAt = now + 2))
        assertEquals(1, presenter.shown.size)
    }

    @Test
    fun withoutThePermissionOrAValidSession_nothingAtAll() {
        permission = false
        scheduler.onCircleNews("me", news())
        permission = true
        session = false
        scheduler.onCircleNews("me", news(sentAt = now + 1))
        scheduler.onCircleNews("someoneElse", news(sentAt = now + 2))
        assertTrue(presenter.shown.isEmpty())
        assertTrue(lines().isEmpty())
    }

    @Test
    fun inFrontTheFeedAlreadySpeaks_onlyASilentLineIsWritten() {
        foreground = true
        scheduler.onCircleNews("me", news())
        assertTrue(presenter.shown.isEmpty())
        assertEquals(ItemState.SILENT, lines().single().state)
        assertEquals("Amandine", lines().single().who)
    }

    @Test
    fun aNewsOlderThanTwoHoursIsOnlyAStaleLineOfTheCentre() {
        scheduler.onCircleNews("me", news(sentAt = now - NotificationPolicy.N9_MAX_AGE_MILLIS - 60_000L))
        assertTrue(presenter.shown.isEmpty())
        val line = lines().single()
        assertEquals(ItemState.SILENT, line.state)
        assertTrue("the line is dated when it was sent", line.createdAtMillis < now)
    }

    @Test
    fun aSentTimeInTheFutureIsTreatedAsNow() {
        scheduler.onCircleNews("me", news(sentAt = now + 5 * 3_600_000L))
        assertEquals(1, presenter.shown.size)
    }

    @Test
    fun atNightTheQuietChannelIsUsed_unlessNuitCalmIsOff() {
        now = at(23, 30)
        scheduler.onCircleNews("me", news(sentAt = now))
        assertTrue(presenter.shown.single().quiet)
        settings.setNightCalm(false)
        now = at(23, 59)
        scheduler.onCircleNews("me", news(author = "author2", sentAt = now))
        assertFalse(presenter.shown.last().quiet)
    }

    @Test
    fun theSixthIsShown_theSeventhOnlyWrittenToTheCentre_andTheRemindersKeepTheirOwnCaps() {
        repeat(NotificationPolicy.MAX_N9_PER_DAY) { i ->
            scheduler.onCircleNews("me", news(author = "author$i", sentAt = now + i))
        }
        assertEquals(6, presenter.shown.size)
        scheduler.onCircleNews("me", news(author = "authorX", sentAt = now + 10))
        assertEquals(6, presenter.shown.size)
        assertEquals(ItemState.SILENT, lines().first { it.id.contains("authorX") }.state)
        assertEquals(7, lines().size)
        assertEquals(0, ledger.counters.total)
        // The next day starts from zero again.
        now = at(9) + 24 * 3_600_000L
        scheduler.onCircleNews("me", news(author = "authorY", sentAt = now))
        assertEquals(7, presenter.shown.size)
    }

    @Test
    fun theCentreLineHoldsNoNameOfALongerThanTwentyCharactersNorAFamilyName() {
        // The parser cuts the first name; the scheduler stores what it is given: the cut name is what reaches the line.
        scheduler.onCircleNews("me", news(name = "A".repeat(20) + "…"))
        assertEquals(21, lines().single().who!!.length)
        assertEquals("NotificationItem(redacted)", lines().single().toString())
    }

    @Test
    fun theOtherNotificationsOfThePresenterAreNeverCalledForANews() {
        scheduler.onCircleNews("me", news())
        assertTrue(presenter.others.isEmpty())
    }
}
