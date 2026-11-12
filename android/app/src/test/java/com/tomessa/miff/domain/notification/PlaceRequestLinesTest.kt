package com.tomessa.miff.domain.notification

import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.OTHER_MIFF_ID
import com.tomessa.miff.testsupport.testRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lines of the centre of notifications about requests to add a place (ticket LIEU-REQ, section 7.1): pure, no Android. */
class PlaceRequestLinesTest {
    private val now = 1_800_000_000_000L

    private fun line(
        requestId: String,
        miffId: String = MIFF_ID,
        unread: Boolean = true,
        at: Long = now,
    ) = NotificationItem(
        id = PlaceRequestLines.key(miffId, requestId),
        type = NotificationType.N8,
        miffId = miffId,
        createdAtMillis = at,
        readAtMillis = if (unread) null else at,
        state = ItemState.SILENT,
    )

    @Test
    fun theKey_isTheDedupKeyOfTheTicket_andCarriesNoName() {
        val key = PlaceRequestLines.key(MIFF_ID, "u2_1")
        assertEquals("n8|$MIFF_ID|u2_1", key)
        // The same request is never two lines: the centre dedupes by key.
        assertEquals(key, PlaceRequestLines.key(MIFF_ID, "u2_1"))
    }

    @Test
    fun aLineOfARequest_holdsOnlyATypeAMiffATimeAndAKey_neverAPlaceOrAPerson() {
        val lines =
            PlaceRequestLines.linesFor(
                MIFF_ID,
                listOf(testRequest(name = "Chez Mamie", address = "12 rue des Lilas")),
                now,
            )
        val item = lines.single()
        assertEquals(NotificationType.N8, item.type)
        assertEquals(MIFF_ID, item.miffId)
        assertEquals(ItemState.SILENT, item.state)
        assertFalse(item.id.contains("Mamie"))
        assertFalse(item.id.contains("Lilas"))
        assertEquals("NotificationItem(redacted)", item.toString())
    }

    @Test
    fun oneLinePerRequest_datedWhenItWasMade_neverInTheFuture_aRequestNotDatedYetIsNow() {
        val lines =
            PlaceRequestLines.linesFor(
                MIFF_ID,
                listOf(
                    testRequest(slot = 1, createdAtMillis = now - 5_000),
                    testRequest(slot = 2, createdAtMillis = now + 60_000),
                    testRequest(slot = 3, createdAtMillis = null),
                ),
                now,
            )
        assertEquals(listOf(now - 5_000, now, now), lines.map { it.createdAtMillis })
    }

    @Test
    fun requestIdAndRequester_areReadInTheKey() {
        val item = line("u2_3")
        assertEquals("u2_3", PlaceRequestLines.requestIdOf(item))
        assertEquals("u2", PlaceRequestLines.requesterOf(item))
        // A line of another type is never read as a request.
        val other = NotificationItem("n1|miff|uid|ref", NotificationType.N1, MIFF_ID, now)
        assertNull(PlaceRequestLines.requestIdOf(other))
        assertNull(PlaceRequestLines.requesterOf(other))
    }

    @Test
    fun theUnreadLinesOfOnePerson_inOneMiff_areOneEntry_theNewestTime() {
        val entries =
            PlaceRequestLines.entriesOf(
                listOf(line("u2_1", at = now - 3_000), line("u2_2", at = now - 1_000), line("u2_3", at = now - 2_000)),
            )
        val entry = entries.single()
        assertEquals(3, entry.count)
        assertEquals(now - 1_000, entry.createdAtMillis)
        assertEquals("u2", entry.requesterUid)
        assertEquals(MIFF_ID, entry.miffId)
        assertTrue(entry.unread)
    }

    @Test
    fun twoPeople_orTwoMiffs_areNeverMerged() {
        val entries =
            PlaceRequestLines.entriesOf(
                listOf(
                    line("u2_1"),
                    line("u3_1"),
                    line("u2_1", miffId = OTHER_MIFF_ID),
                ),
            )
        assertEquals(3, entries.size)
        assertTrue(entries.all { it.count == 1 })
    }

    @Test
    fun aLineThatWasRead_isNeverMergedWithTheOthers_eachStaysItsOwnEntry() {
        val entries =
            PlaceRequestLines.entriesOf(
                listOf(line("u2_1", unread = false), line("u2_2", unread = false), line("u2_3")),
            )
        assertEquals(3, entries.size)
        assertEquals(1, entries.count { it.unread })
        assertTrue(entries.all { it.count == 1 })
    }

    @Test
    fun otherLines_areNeverTouched() {
        val other = NotificationItem("n1|miff|uid|ref", NotificationType.N1, MIFF_ID, now)
        assertTrue(PlaceRequestLines.entriesOf(listOf(other)).isEmpty())
    }

    @Test
    fun aRequestLine_followsTheRulesOfTheCentre_thirtyDaysAndNoSwitch() {
        // 30 days like every line; always allowed (no switch of its own in Settings), whatever the other switches.
        val old = line("u2_1", at = now - NotificationRetention.MAX_AGE_MILLIS - 1)
        assertTrue(NotificationRetention.apply(listOf(old, line("u2_2")), now).none { it.id == old.id })
        assertTrue(NotificationPreferences().allows(NotificationType.N8))
        assertTrue(
            NotificationPreferences(
                n1 = false,
                n3 = false,
                n4 = false,
                n6 = false,
                n7 = false,
            ).allows(NotificationType.N8),
        )
        assertEquals(NotificationPreferences(), NotificationPreferences().with(NotificationType.N8, false))
    }
}
