package com.tomessa.miff.domain.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CNC-A: the journal of the server turned into lines of the centre (pure rules, same vectors as iOS). */
class NotificationJournalTest {
    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    private fun doc(
        kind: String = "update",
        miff: String = "miffAAAA",
        actor: String = "actor1",
        version: Any? = 1L,
        extra: Map<String, Any?> = mapOf("expireAt" to "later"),
    ): Map<String, Any?> = mapOf("v" to version, "kind" to kind, "miffId" to miff, "actorUid" to actor) + extra

    private fun entry(
        kind: JournalKind = JournalKind.UPDATE,
        actor: String = "actor1",
        at: Long = now - hour,
        id: String = "u-miffAAAA-actor1-1",
    ) = JournalEntry(id, kind, "miffAAAA", actor, at)

    // ---- the form of an entry ----------------------------------------------------------------------------------------------------------

    @Test
    fun aWellFormedEntryIsUnderstood_forEachOfTheThreeKinds() {
        assertEquals(JournalKind.UPDATE, NotificationJournal.parse("u-1", doc("update"), now)!!.kind)
        assertEquals(JournalKind.ALERT_START, NotificationJournal.parse("as-1", doc("alert_start"), now)!!.kind)
        assertEquals(JournalKind.ALERT_END, NotificationJournal.parse("ae-1", doc("alert_end"), now)!!.kind)
        val e = NotificationJournal.parse("u-1", doc(), 1234L)!!
        assertEquals("miffAAAA", e.miffId)
        assertEquals("actor1", e.actorUid)
        assertEquals(1234L, e.createdAtMillis)
    }

    @Test
    fun anUnknownVersionOrKind_isIgnoredWithoutAnError() {
        assertNull(NotificationJournal.parse("u-1", doc(version = 2L), now))
        assertNull(NotificationJournal.parse("u-1", doc(version = null), now))
        assertNull(NotificationJournal.parse("u-1", doc(kind = "something_new"), now))
    }

    @Test
    fun aMissingOrMalformedFieldIsIgnored() {
        assertNull(NotificationJournal.parse("u-1", doc(miff = "bad/miff"), now))
        assertNull(NotificationJournal.parse("u-1", doc(actor = ""), now))
        assertNull(NotificationJournal.parse("u-1", doc(actor = "a".repeat(129)), now))
        assertNull(NotificationJournal.parse("u-1", doc() - "miffId", now))
        assertNull(NotificationJournal.parse("u-1", doc() - "actorUid", now))
        assertNull(NotificationJournal.parse("u-1", doc(), null))
        assertNull(NotificationJournal.parse("u-1", doc(), 0L))
        assertNull(NotificationJournal.parse("bad id with spaces", doc(), now))
        assertNull(NotificationJournal.parse("u-1", doc() + ("kind" to 5), now))
    }

    @Test
    fun keysTheEntryDoesNotUseAreIgnored_aLaterVersionOfTheServerMayAddSome() {
        assertNotNull(NotificationJournal.parse("u-1", doc(extra = mapOf("someday" to true, "firstName" to "Léa")), now))
    }

    // ---- the line an entry stands for ----------------------------------------------------------------------------------------------------

    @Test
    fun anEntryIsALineWithTheSameKeyAsThePushOfThatEntry() {
        val update = entry(JournalKind.UPDATE, id = "u-miffAAAA-actor1-1700")
        val news = CircleNewsPayload("miffAAAA", "actor1", "Léa", now, entryId = "u-miffAAAA-actor1-1700", messageId = "0:abc%x")
        assertEquals(CircleNewsKeys.centerId(news), NotificationJournal.keyOf(update))

        val start = entry(JournalKind.ALERT_START, id = "as-miffAAAA-actor1-1700")
        val startPush = CircleAlertPayload(CircleAlertEvent.START, "miffAAAA", "actor1", "Léa", now, entryId = "as-miffAAAA-actor1-1700")
        assertEquals(CircleAlertKeys.centerId(startPush), NotificationJournal.keyOf(start))

        val end = entry(JournalKind.ALERT_END, id = "ae-miffAAAA-actor1-1700")
        val endPush = CircleAlertPayload(CircleAlertEvent.END, "miffAAAA", "actor1", "Léa", now, entryId = "ae-miffAAAA-actor1-1700")
        assertEquals(CircleAlertKeys.centerId(endPush), NotificationJournal.keyOf(end))
    }

    @Test
    fun theTypeOfTheLineFollowsTheKind() {
        assertEquals(NotificationType.N9, NotificationJournal.typeOf(JournalKind.UPDATE))
        assertEquals(NotificationType.N10, NotificationJournal.typeOf(JournalKind.ALERT_START))
        assertEquals(NotificationType.N11, NotificationJournal.typeOf(JournalKind.ALERT_END))
    }

    @Test
    fun aLineFromTheJournalIsSilent_andHoldsNoName() {
        val item = NotificationJournal.toItem(entry(), "me", now, firstSync = false)!!
        assertEquals(ItemState.SILENT, item.state)
        assertNull("no first name stored: the words are made when the line is drawn", item.who)
        assertEquals("miffAAAA", item.miffId)
        assertEquals(now - hour, item.createdAtMillis)
        assertTrue(item.unread)
    }

    @Test
    fun theFirstSynchronizationOfAPhone_bringsTheEntriesOlderThanADayAlreadyRead_thoseOfTheLastDayUnread() {
        val old = NotificationJournal.toItem(entry(at = now - 25 * hour), "me", now, firstSync = true)!!
        val fresh = NotificationJournal.toItem(entry(at = now - 23 * hour), "me", now, firstSync = true)!!
        assertFalse("older than 24 h: read, no dot on the bell for the past", old.unread)
        assertTrue(fresh.unread)
    }

    @Test
    fun afterTheFirstSynchronization_anEntryIsUnreadWhateverItsAge() {
        val late = NotificationJournal.toItem(entry(at = now - 30 * hour), "me", now, firstSync = false)!!
        assertTrue(late.unread)
    }

    @Test
    fun theEventOfThePersonHerselfNeverMakesALine() {
        assertNull(NotificationJournal.toItem(entry(actor = "me"), "me", now, firstSync = false))
    }

    // ---- the cursor ----------------------------------------------------------------------------------------------------------------------

    @Test
    fun theRequestStartsTwoMinutesBeforeTheCursor_orFromTheStartWhenThereIsNone() {
        assertNull(NotificationJournal.since(null))
        assertEquals(1_000_000L - 120_000L, NotificationJournal.since(1_000_000L))
        assertEquals("never before the beginning of time", 1L, NotificationJournal.since(5L))
    }

    @Test
    fun theCursorIsTheNewestDateSeen_andNeverMovesBack() {
        val a = entry(at = 100L)
        val b = entry(at = 300L)
        assertEquals(300L, NotificationJournal.nextCursor(null, listOf(a, b)))
        assertEquals(500L, NotificationJournal.nextCursor(500L, listOf(a, b)))
        assertEquals(500L, NotificationJournal.nextCursor(500L, emptyList()))
        assertNull(NotificationJournal.nextCursor(null, emptyList()))
    }

    // ---- deleting a line deletes its entry -------------------------------------------------------------------------------------------------

    @Test
    fun theEntryOfALineIsReadInItsKey_andOnlyForTheLinesThatCameFromTheJournal() {
        assertEquals("u-miffAAAA-actor1-1700", NotificationJournal.entryIdOf("n9|u|actor1|u-miffAAAA-actor1-1700"))
        assertEquals("as-miffAAAA-actor1-1700", NotificationJournal.entryIdOf("al|u|actor1|start|as-miffAAAA-actor1-1700"))
        assertEquals("ae-miffAAAA-actor1-1700", NotificationJournal.entryIdOf("al|u|actor1|end|ae-miffAAAA-actor1-1700"))
        // an old line keyed by an FCM message, a time of reception, a Miff, a reminder, a request, "Depuis ta dernière visite": no entry
        assertNull(NotificationJournal.entryIdOf("n9|u|actor1|0:1700%abc"))
        assertNull(NotificationJournal.entryIdOf("n9|u|actor1|1700000000000"))
        assertNull(NotificationJournal.entryIdOf("n9|m|miffAAAA|u-miffAAAA-actor1-1700"))
        assertNull(NotificationJournal.entryIdOf("al|u|actor1|start|0:1700%abc"))
        assertNull(NotificationJournal.entryIdOf("n1|miffAAAA|me|1700"))
        assertNull(NotificationJournal.entryIdOf("n8|miffAAAA|uid_1"))
        assertNull(NotificationJournal.entryIdOf("c1|miffAAAA|a|uid"))
    }

    // ---- the keys of the lines that come from a push --------------------------------------------------------------------------------------

    @Test
    fun theLoveOneOfALineIsReadInItsKey_soAnOldLineKeepsItsPhoto() {
        assertEquals("actor1", CircleNewsKeys.actorOf("n9|u|actor1|0:1700%abc"))
        assertEquals("actor1", CircleNewsKeys.actorOf("n9|u|actor1|1700000000000"))
        assertNull("a line keyed by the Miff has no author: the logo", CircleNewsKeys.actorOf("n9|m|miffAAAA|1700"))
        assertNull(CircleNewsKeys.actorOf("n1|miffAAAA|me|1700"))
        assertNull(CircleNewsKeys.actorOf("n9|u||1700"))
        assertEquals("actor1", CircleAlertKeys.actorOf("al|u|actor1|start|1700"))
        assertNull(CircleAlertKeys.actorOf("al|m|miffAAAA|start|1700"))
        assertNull(CircleAlertKeys.actorOf("n9|u|actor1|1700"))
    }

    @Test
    fun theTrayTagOfAnAlertLineIsTheOneOfItsAuthor() {
        val start = CircleAlertPayload(CircleAlertEvent.START, "miffAAAA", "actor1", "Léa", now, entryId = "as-1")
        val end = CircleAlertPayload(CircleAlertEvent.END, "miffAAAA", "actor1", "Léa", now, entryId = "ae-1")
        assertEquals(CircleAlertKeys.trayTag(start), CircleAlertKeys.trayTagOf(CircleAlertKeys.centerId(start)))
        assertEquals("the end replaces the start", CircleAlertKeys.trayTagOf(CircleAlertKeys.centerId(start)), CircleAlertKeys.trayTagOf(CircleAlertKeys.centerId(end)))
        assertEquals("a key that is not an alert's is left as it is", "n9|u|actor1", CircleAlertKeys.trayTagOf("n9|u|actor1"))
    }

    @Test
    fun theDateOfExpiryIsRequired_aMissingOneMakesAMalformedEntry_andAPastOneIsNotALine() {
        assertNull(NotificationJournal.parse("u-1", doc(), now, null))
        assertNull(NotificationJournal.parse("u-1", doc(), now, 0L))
        val expired = NotificationJournal.parse("u-1", doc(), now - 40 * 24 * hour, now - hour)!!
        assertNull(NotificationJournal.toItem(expired, "me", now, firstSync = false))
        val alive = NotificationJournal.parse("u-1", doc(), now - hour, now + 29 * 24 * hour)!!
        assertNotNull(NotificationJournal.toItem(alive, "me", now, firstSync = false))
    }

    @Test
    fun anEntryWhoseIdentifierIsTheHashOfTheServer_isStillDeletable() {
        // the server hashes an id longer than 128 characters: {u|as|ae}-{64 hex}
        val hash = "a".repeat(64)
        assertEquals("u-$hash", NotificationJournal.entryIdOf("n9|u|actor1|u-$hash"))
        assertEquals("as-$hash", NotificationJournal.entryIdOf("al|u|actor1|start|as-$hash"))
        assertEquals("ae-$hash", NotificationJournal.entryIdOf("al|u|actor1|end|ae-$hash"))
    }
}
