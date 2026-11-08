package com.tomessa.miff.domain.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** The centre of notifications as pure rules: keeping 30 days or 100 lines, the groups of days, one line per key, reading and deleting. */
class NotificationItemsTest {
    private val day = 24L * 60L * 60L * 1000L
    private val now = 1_800_000_000_000L

    private fun item(
        id: String,
        at: Long,
        type: NotificationType = NotificationType.N1,
        readAt: Long? = null,
    ) = NotificationItem(id, type, "miff-1", at, readAt)

    // --- Retention: 30 days or 100 lines --------------------------------------------------------------------------------------------

    @Test
    fun exactly30DaysIsKept_30DaysAndOneMillisecondIsGone() {
        val kept = item("a", now - 30 * day)
        val gone = item("b", now - 30 * day - 1)
        assertEquals(listOf("a"), NotificationRetention.apply(listOf(kept, gone), now).map { it.id })
    }

    @Test
    fun only100Remain_theMostRecentOnes_newestFirst() {
        val items = (1..101).map { item("i$it", now - it * 1000L) }
        val kept = NotificationRetention.apply(items, now)
        assertEquals(100, kept.size)
        assertEquals("i1", kept.first().id)
        assertEquals("i100", kept.last().id)
        assertFalse(kept.any { it.id == "i101" })
    }

    @Test
    fun exactly100AreAllKept() {
        val items = (1..100).map { item("i$it", now - it * 1000L) }
        assertEquals(100, NotificationRetention.apply(items, now).size)
    }

    @Test
    fun aLineDatedInTheFutureIsKept() {
        val future = item("f", now + 5 * day)
        assertEquals(listOf("f"), NotificationRetention.apply(listOf(future), now).map { it.id })
    }

    // --- Groups of days -------------------------------------------------------------------------------------------------------------

    private val paris = ZoneId.of("Europe/Paris")

    private fun at(
        y: Int,
        m: Int,
        d: Int,
        h: Int,
        min: Int,
    ) = ZonedDateTime.of(y, m, d, h, min, 0, 0, paris).toInstant().toEpochMilli()

    @Test
    fun todayYesterdayOlder_followTheLocalMidnight() {
        val nowLocal = at(2026, 11, 8, 0, 30)
        assertEquals(ItemSection.TODAY, NotificationSections.sectionOf(at(2026, 11, 8, 0, 0), nowLocal, paris))
        assertEquals(ItemSection.YESTERDAY, NotificationSections.sectionOf(at(2026, 11, 7, 23, 59), nowLocal, paris))
        assertEquals(ItemSection.YESTERDAY, NotificationSections.sectionOf(at(2026, 11, 7, 0, 0), nowLocal, paris))
        assertEquals(ItemSection.OLDER, NotificationSections.sectionOf(at(2026, 11, 6, 23, 59), nowLocal, paris))
    }

    @Test
    fun theSectionOfAMomentDependsOnTheTimeZoneOfThePhone() {
        val instant = at(2026, 11, 8, 23, 30)
        val tokyo = ZoneId.of("Asia/Tokyo")
        val nowTokyo = at(2026, 11, 9, 10, 0)
        // 23:30 in Paris on the 8th is 07:30 on the 9th in Tokyo: the same instant is "yesterday" for Paris, "today" for Tokyo.
        assertEquals(ItemSection.YESTERDAY, NotificationSections.sectionOf(instant, nowTokyo, paris))
        assertEquals(ItemSection.TODAY, NotificationSections.sectionOf(instant, nowTokyo, tokyo))
    }

    @Test
    fun theGroupsAreInOrder_emptyOnesAreLeftOut_andLinesAreNewestFirst() {
        val nowLocal = at(2026, 11, 8, 18, 0)
        val items =
            listOf(
                item("old", at(2026, 10, 1, 9, 0)),
                item("today-early", at(2026, 11, 8, 8, 0)),
                item("today-late", at(2026, 11, 8, 16, 0)),
            )
        val groups = NotificationSections.group(items, { it.createdAtMillis }, nowLocal, paris)
        assertEquals(listOf(ItemSection.TODAY, ItemSection.OLDER), groups.map { it.first })
        assertEquals(listOf("today-late", "today-early"), groups[0].second.map { it.id })
    }

    // --- The list -------------------------------------------------------------------------------------------------------------------

    @Test
    fun aKeyIsAddedOnce() {
        val first = NotificationList.add(emptyList(), item("k", now - 1000), now)
        val again = NotificationList.add(first, item("k", now), now)
        assertEquals(1, again.size)
        assertEquals(now - 1000, again.single().createdAtMillis)
    }

    @Test
    fun readingAndDeleting() {
        val items = listOf(item("a", now - 2000), item("b", now - 1000))
        assertEquals(2, NotificationList.unreadCount(items))
        val readA = NotificationList.markRead(items, "a", now)
        assertEquals(1, NotificationList.unreadCount(readA))
        assertEquals(now, readA.first { it.id == "a" }.readAtMillis)
        // Reading again keeps the first time of reading.
        assertEquals(now, NotificationList.markRead(readA, "a", now + 5).first { it.id == "a" }.readAtMillis)
        assertEquals(0, NotificationList.unreadCount(NotificationList.markAllRead(items, now)))
        assertEquals(listOf("b"), NotificationList.delete(items, "a").map { it.id })
    }

    @Test
    fun anItemIsRedactedInALog_andCarriesNothingBeyondATypeAnIdentifierAndTimes() {
        val i = item("n1|miff-1|uid|123", now)
        assertEquals("NotificationItem(redacted)", i.toString())
        assertTrue(i.unread)
        assertNull(i.readAtMillis)
    }
}

class SecondChanceRulesTest {
    @Test
    fun theCardIsDueOnlyAfterLater_thenTheFirstUpdate_andOnlyWhileTheWindowCanStillBeShown() {
        var c = SecondChance.NONE
        // An update before any "Plus tard": nothing to offer (the person was never asked to wait).
        assertEquals(SecondChance.NONE, SecondChanceRules.afterUpdatePublished(c))
        c = SecondChanceRules.afterLater(c)
        assertEquals(SecondChance.DEFERRED, c)
        assertFalse(SecondChanceRules.visible(c, NotificationPermissionState.NOT_ASKED))
        c = SecondChanceRules.afterUpdatePublished(c)
        assertEquals(SecondChance.READY, c)
        assertTrue(SecondChanceRules.visible(c, NotificationPermissionState.NOT_ASKED))
        // The permission was given elsewhere, or refused for good: no card.
        assertFalse(SecondChanceRules.visible(c, NotificationPermissionState.ALLOWED))
        assertFalse(SecondChanceRules.visible(c, NotificationPermissionState.OFF))
    }

    @Test
    fun anAnswerEndsItForGood_notEvenASecondUpdateOrASecondLaterBringsItBack() {
        var c = SecondChance.READY
        c = SecondChanceRules.afterAnswer(c)
        assertEquals(SecondChance.DONE, c)
        assertEquals(SecondChance.DONE, SecondChanceRules.afterUpdatePublished(c))
        assertEquals(SecondChance.DONE, SecondChanceRules.afterLater(c))
        assertFalse(SecondChanceRules.visible(c, NotificationPermissionState.NOT_ASKED))
    }

    @Test
    fun laterTwiceDoesNotResetTheStateAfterTheFirstUpdate() {
        var c = SecondChanceRules.afterLater(SecondChance.NONE)
        c = SecondChanceRules.afterUpdatePublished(c)
        assertEquals(SecondChance.READY, SecondChanceRules.afterLater(c))
    }
}
