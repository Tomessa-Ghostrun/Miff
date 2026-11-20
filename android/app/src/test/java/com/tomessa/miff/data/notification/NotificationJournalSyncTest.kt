package com.tomessa.miff.data.notification

import com.tomessa.miff.domain.notification.ItemState
import com.tomessa.miff.domain.notification.JournalEntry
import com.tomessa.miff.domain.notification.JournalKind
import com.tomessa.miff.domain.notification.NotificationItem
import com.tomessa.miff.domain.notification.NotificationJournal
import com.tomessa.miff.domain.notification.NotificationType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CNC-A: the reading of the journal of the server, with doubles (no Firestore): the switch, the spacing, the cursor, the lines, the duplicates, the first synchronization, the refusal, the
 * session, the deletion of an entry with its line, the purge. Nothing is ever shown about a failure (the double returns, never throws).
 */
class NotificationJournalSyncTest {
    private val hour = 3_600_000L
    private var now = 1_800_000_000_000L

    private class Gateway : JournalGateway {
        var answer: JournalRead = JournalRead.Loaded(emptyList())
        val reads = mutableListOf<Pair<String, Long?>>()
        val deleted = mutableListOf<List<String>>()
        var deletedAll = 0

        override suspend fun deleteAll(uid: String) {
            deletedAll++
        }

        var fail = false

        override suspend fun read(
            uid: String,
            sinceMillis: Long?,
        ): JournalRead {
            if (fail) throw IllegalStateException("boom")
            reads += uid to sinceMillis
            return answer
        }

        override fun delete(
            uid: String,
            entryIds: List<String>,
        ) {
            deleted += entryIds
        }
    }

    private class Cursors : JournalCursorStore {
        var uid: String? = null
        var value: Long? = null

        override fun get(uid: String) = if (this.uid == uid) value else null

        override fun set(
            uid: String,
            cursorMillis: Long,
        ) {
            this.uid = uid
            value = cursorMillis
        }

        override fun clear() {
            uid = null
            value = null
        }
    }

    private class Store : NotificationCenterStore {
        var items = emptyList<NotificationItem>()

        override fun load() = items

        override fun save(items: List<NotificationItem>) {
            this.items = items
        }

        override fun clear() {
            items = emptyList()
        }
    }

    private val gateway = Gateway()
    private val cursors = Cursors()
    private val deletedOnServer = mutableListOf<List<String>>()
    private val center = NotificationCenter(Store(), nowMillis = { now }, onDeleted = { deletedOnServer += it })
    private var uid: String? = "me"
    private var session = true

    private fun sync(enabled: Boolean = true) =
        NotificationJournalSync(
            enabled = enabled,
            gateway = gateway,
            cursors = cursors,
            center = center,
            currentUid = { uid },
            sessionValid = { session && it == "me" },
            nowMillis = { now },
        )

    private val journal = sync()

    private fun entry(
        id: String = "u-miffAAAA-actor1-1",
        kind: JournalKind = JournalKind.UPDATE,
        actor: String = "actor1",
        at: Long = now - hour,
    ) = JournalEntry(id, kind, "miffAAAA", actor, at)

    private fun lines() = center.items.value

    @Test
    fun theEntriesBecomeLinesOfTheCentre_silent_unread_withTheKeyOfTheirPush() =
        runTest {
            gateway.answer =
                JournalRead.Loaded(
                    listOf(
                        entry("u-miffAAAA-actor1-1"),
                        entry("as-miffAAAA-actor2-2", JournalKind.ALERT_START, "actor2"),
                        entry("ae-miffAAAA-actor2-3", JournalKind.ALERT_END, "actor2"),
                    ),
                )
            assertTrue(journal.sync(JournalSyncReason.CENTER_OPENED))
            assertEquals(3, lines().size)
            assertEquals(
                setOf(NotificationType.N9, NotificationType.N10, NotificationType.N11),
                lines().map { it.type }.toSet(),
            )
            assertTrue(lines().all { it.state == ItemState.SILENT && it.unread && it.who == null })
            assertTrue(lines().any { it.id == "n9|u|actor1|u-miffAAAA-actor1-1" })
        }

    @Test
    fun readingTwice_neverMakesADouble_andTheCursorMovesToTheNewestDate() =
        runTest {
            val at = now - 5_000L
            gateway.answer = JournalRead.Loaded(listOf(entry(at = at)))
            journal.sync(JournalSyncReason.CENTER_OPENED)
            now += 2 * 60_000L
            // the overlap brings the same entry again: still one line
            journal.sync(JournalSyncReason.CENTER_OPENED)
            assertEquals(1, lines().size)
            assertEquals(
                "the second request starts 2 minutes before the newest date seen",
                at - NotificationJournal.OVERLAP_MILLIS,
                gateway.reads[1].second,
            )
            assertEquals(at, cursors.value)
        }

    @Test
    fun theFirstRequestHasNoCursor_thenEachOneStartsTwoMinutesBeforeTheNewestDateSeen() =
        runTest {
            val at = now - 10_000L
            gateway.answer = JournalRead.Loaded(listOf(entry(at = at)))
            journal.sync(JournalSyncReason.FOREGROUND)
            assertNull(gateway.reads[0].second)
            assertEquals(at, cursors.value)
            now += 61_000L
            journal.sync(JournalSyncReason.FOREGROUND)
            assertEquals(at - NotificationJournal.OVERLAP_MILLIS, gateway.reads[1].second)
        }

    @Test
    fun anEmptyJournal_costsOneReading_andKeepsNoCursor() =
        runTest {
            assertTrue(journal.sync(JournalSyncReason.FOREGROUND))
            assertEquals(1, gateway.reads.size)
            assertTrue(lines().isEmpty())
            assertNull(cursors.value)
        }

    @Test
    fun onTheFirstSynchronization_theEntriesOlderThanADayArriveRead_theRecentOnesUnread() =
        runTest {
            gateway.answer =
                JournalRead.Loaded(
                    listOf(
                        entry("u-a-actor1-1", at = now - 30 * hour),
                        entry(
                            "u-b-actor1-2",
                            at =
                                now - 2 * hour,
                        ),
                    ),
                )
            journal.sync(JournalSyncReason.FOREGROUND)
            assertEquals(1, lines().count { it.unread })
            assertEquals(1, center.items.value.count { !it.unread })
            assertTrue(!lines().first { it.createdAtMillis == now - 30 * hour }.unread)
        }

    @Test
    fun afterTheFirstSynchronization_aLateEntryIsUnread() =
        runTest {
            gateway.answer = JournalRead.Loaded(listOf(entry("u-a-actor1-1", at = now - 2 * hour)))
            journal.sync(JournalSyncReason.FOREGROUND)
            now += 61_000L
            gateway.answer = JournalRead.Loaded(listOf(entry("u-b-actor1-2", at = now - 30 * hour)))
            journal.sync(JournalSyncReason.FOREGROUND)
            assertTrue(lines().all { it.unread })
        }

    @Test
    fun aPushThatCameFirst_andTheEntryOfTheJournalForIt_areOneLine() =
        runTest {
            center.add(
                NotificationItem("n9|u|actor1|u-miffAAAA-actor1-1", NotificationType.N9, "miffAAAA", now, who = "Léa"),
            )
            gateway.answer = JournalRead.Loaded(listOf(entry("u-miffAAAA-actor1-1")))
            journal.sync(JournalSyncReason.CENTER_OPENED)
            assertEquals(1, lines().size)
            assertEquals("the line the push wrote is kept as it is", "Léa", lines().single().who)
        }

    @Test
    fun theEventOfThePersonHerself_isNeverALine() =
        runTest {
            gateway.answer = JournalRead.Loaded(listOf(entry(actor = "me")))
            assertTrue(journal.sync(JournalSyncReason.CENTER_OPENED))
            assertTrue(lines().isEmpty())
        }

    // ---- the switch, the session, the failures ---------------------------------------------------------------------------------------------------

    @Test
    fun switchedOff_nothingIsReadAndNothingIsDeleted() =
        runTest {
            val off = sync(enabled = false)
            assertFalse(off.sync(JournalSyncReason.FOREGROUND))
            off.deleteOnServer(listOf("n9|u|actor1|u-miffAAAA-actor1-1"))
            assertTrue(gateway.reads.isEmpty())
            assertTrue(gateway.deleted.isEmpty())
        }

    @Test
    fun withoutAValidSession_orNobody_nothingIsRead() =
        runTest {
            session = false
            assertFalse(journal.sync(JournalSyncReason.FOREGROUND))
            session = true
            uid = null
            assertFalse(journal.sync(JournalSyncReason.FOREGROUND))
            assertTrue(gateway.reads.isEmpty())
        }

    @Test
    fun theSessionEndingWhileTheServerIsRead_writesNothingForTheOldAccount() =
        runTest {
            gateway.answer = JournalRead.Loaded(listOf(entry()))
            val ending =
                NotificationJournalSync(
                    true,
                    object : JournalGateway by gateway {
                        override suspend fun read(
                            uid: String,
                            sinceMillis: Long?,
                        ): JournalRead {
                            session = false
                            return gateway.read(uid, sinceMillis)
                        }
                    },
                    cursors,
                    center,
                    { uid },
                    { session && it == "me" },
                    { now },
                )
            assertFalse(ending.sync(JournalSyncReason.FOREGROUND))
            assertTrue(lines().isEmpty())
            assertNull(cursors.value)
        }

    @Test
    fun offline_isCalm_nothingChanges_aNewTryIsAllowedSoon_butNotInALoop() =
        runTest {
            gateway.answer = JournalRead.Unavailable
            assertFalse(journal.sync(JournalSyncReason.FOREGROUND))
            assertFalse("not in a loop", journal.sync(JournalSyncReason.FOREGROUND))
            assertEquals(1, gateway.reads.size)
            now += NotificationJournalSync.OFFLINE_GAP_MILLIS + 1
            gateway.answer = JournalRead.Loaded(listOf(entry()))
            assertTrue("back online: the catch-up", journal.sync(JournalSyncReason.FOREGROUND))
            assertEquals(1, lines().size)
        }

    @Test
    fun aGatewayThatThrows_isCalm() =
        runTest {
            gateway.fail = true
            assertFalse(journal.sync(JournalSyncReason.FOREGROUND))
            assertTrue(lines().isEmpty())
        }

    @Test
    fun aRefusalByTheRules_isNotRenewedForHalfAnHour() =
        runTest {
            gateway.answer = JournalRead.Refused
            assertFalse(journal.sync(JournalSyncReason.FOREGROUND))
            now += 10 * 60_000L
            assertFalse(journal.sync(JournalSyncReason.PUSH_RECEIVED))
            assertEquals(1, gateway.reads.size)
            now += 21 * 60_000L
            gateway.answer = JournalRead.Loaded(listOf(entry()))
            assertTrue(journal.sync(JournalSyncReason.FOREGROUND))
            assertEquals(1, lines().size)
        }

    // ---- the spacing -----------------------------------------------------------------------------------------------------------------------------

    @Test
    fun atMostOneRequestAMinute_exceptAPushWhichWaitsTenSeconds() =
        runTest {
            assertTrue(journal.sync(JournalSyncReason.FOREGROUND))
            now += 30_000L
            assertFalse(journal.sync(JournalSyncReason.CENTER_OPENED))
            now += 31_000L
            assertTrue(journal.sync(JournalSyncReason.CENTER_OPENED))
            // after a push request: ten seconds
            val pushed = sync()
            now += 100_000L
            assertTrue(pushed.sync(JournalSyncReason.PUSH_RECEIVED))
            now += 5_000L
            assertFalse(pushed.sync(JournalSyncReason.PUSH_RECEIVED))
            now += 6_000L
            assertTrue(pushed.sync(JournalSyncReason.PUSH_RECEIVED))
        }

    @Test
    fun onlyOneRequestAtATime() =
        runTest {
            // a second request asked while one runs is dropped: the running one reads everything
            var inner: Boolean? = null
            lateinit var reentrant: NotificationJournalSync
            val slow =
                object : JournalGateway by gateway {
                    override suspend fun read(
                        uid: String,
                        sinceMillis: Long?,
                    ): JournalRead {
                        inner = reentrant.sync(JournalSyncReason.PUSH_RECEIVED)
                        return JournalRead.Loaded(emptyList())
                    }
                }
            reentrant = NotificationJournalSync(true, slow, cursors, center, { uid }, { it == "me" }, { now })
            assertTrue(reentrant.sync(JournalSyncReason.FOREGROUND))
            assertEquals(false, inner)
        }

    // ---- deleting ----------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun deletingALineThatCameFromTheJournal_deletesItsEntryOnTheServer_aReminderHasNothingToDelete() {
        journal.deleteOnServer(
            listOf("n9|u|actor1|u-miffAAAA-actor1-1", "n1|miffAAAA|me|1700", "al|u|actor1|start|as-miffAAAA-actor1-9"),
        )
        assertEquals(listOf(listOf("u-miffAAAA-actor1-1", "as-miffAAAA-actor1-9")), gateway.deleted)
        journal.deleteOnServer(listOf("n1|miffAAAA|me|1700"))
        assertEquals(1, gateway.deleted.size)
    }

    @Test
    fun theCentreTellsItsDeletions_oneLineAndClearAll() {
        center.add(NotificationItem("a", NotificationType.N1, "miffAAAA", now))
        center.add(NotificationItem("b", NotificationType.N1, "miffAAAA", now))
        center.delete("a")
        assertEquals(listOf(listOf("a")), deletedOnServer)
        center.clearAll()
        assertEquals(listOf(listOf("a"), listOf("b")), deletedOnServer)
    }

    @Test
    fun aLineDeletedHere_doesNotComeBackWithinTheOverlapOfTheNextRequest() =
        runTest {
            val key = "n9|u|actor1|u-miffAAAA-actor1-1"
            gateway.answer = JournalRead.Loaded(listOf(entry(at = now - 1_000L)))
            journal.sync(JournalSyncReason.FOREGROUND)
            center.delete(key)
            journal.deleteOnServer(listOf(key))
            assertTrue(lines().isEmpty())
            now += 61_000L
            journal.sync(JournalSyncReason.FOREGROUND)
            assertTrue(
                "the entry may still be on the server for a moment (offline): the line stays gone",
                lines().isEmpty(),
            )
        }

    // ---- the end of the session -----------------------------------------------------------------------------------------------------------------------

    @Test
    fun thePurgeForgetsTheCursor_andTheNextAccountStartsFromNothing() =
        runTest {
            gateway.answer = JournalRead.Loaded(listOf(entry()))
            journal.sync(JournalSyncReason.FOREGROUND)
            assertTrue(cursors.value != null)
            journal.purge()
            assertNull(cursors.value)
            assertTrue(journal.sync(JournalSyncReason.FOREGROUND))
            assertNull("no cursor: the whole journal", gateway.reads.last().second)
        }

    @Test
    fun theCursorOfAnotherAccountIsNeverUsed() =
        runTest {
            cursors.set("someoneElse", now - 1_000L)
            journal.sync(JournalSyncReason.FOREGROUND)
            assertNull(gateway.reads.single().second)
        }

    @Test
    fun theRetentionOfTheCentreStillApplies_100Lines() =
        runTest {
            gateway.answer = JournalRead.Loaded((1..120).map { entry("u-m-actor1-$it", at = now - it * 1_000L) })
            journal.sync(JournalSyncReason.FOREGROUND)
            assertEquals(100, lines().size)
        }

    @Test
    fun theLinesAboutTheLovedOnesOfAMiffThatIsGone_leaveThePhone_theRemindersStay() {
        center.add(NotificationItem("n9|u|a|u-1", NotificationType.N9, "miffGone", now))
        center.add(NotificationItem("al|u|a|start|as-1", NotificationType.N10, "miffGone", now))
        center.add(NotificationItem("n9|u|a|u-2", NotificationType.N9, "miffKept", now))
        center.add(NotificationItem("n1|miffGone|me|1", NotificationType.N1, "miffGone", now))
        center.removeLovedOneLines { it.miffId == "miffGone" }
        assertEquals(setOf("n9|u|a|u-2", "n1|miffGone|me|1"), lines().map { it.id }.toSet())
        assertTrue("nothing is deleted on the server for this", deletedOnServer.isEmpty())
    }

    @Test
    fun clearAll_deletesEveryEntryOfTheAccountOnTheServer_theOnesThePhoneNeverSawIncluded() =
        runTest {
            journal.deleteAllOnServer()
            assertEquals(1, gateway.deletedAll)
            sync(enabled = false).deleteAllOnServer()
            assertEquals("switched off: nothing", 1, gateway.deletedAll)
            uid = null
            journal.deleteAllOnServer()
            assertEquals(1, gateway.deletedAll)
        }

    @Test
    fun theCentreTellsThatEverythingWasCleared() {
        var cleared = 0
        val c = NotificationCenter(Store(), nowMillis = { now }, onClearedAll = { cleared++ })
        c.add(NotificationItem("a", NotificationType.N1, "miffAAAA", now))
        c.delete("a")
        assertEquals(0, cleared)
        c.clearAll()
        assertEquals(1, cleared)
    }

    @Test
    fun anEntryWhoseExpiryIsPast_isIgnored_evenIfTheServerDidNotDeleteItYet() =
        runTest {
            gateway.answer =
                JournalRead.Loaded(
                    listOf(
                        JournalEntry(
                            "u-a-actor1-1",
                            JournalKind.UPDATE,
                            "miffAAAA",
                            "actor1",
                            now - 40 * 24 * hour,
                            expireAtMillis =
                                now - 10 * hour,
                        ),
                        JournalEntry(
                            "u-b-actor1-2",
                            JournalKind.UPDATE,
                            "miffAAAA",
                            "actor1",
                            now - hour,
                            expireAtMillis =
                                now + 29 * 24 * hour,
                        ),
                    ),
                )
            journal.sync(JournalSyncReason.FOREGROUND)
            assertEquals(listOf("n9|u|actor1|u-b-actor1-2"), lines().map { it.id })
        }
}
