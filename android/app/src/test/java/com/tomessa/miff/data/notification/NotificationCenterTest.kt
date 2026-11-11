package com.tomessa.miff.data.notification

import com.tomessa.miff.domain.notification.ItemState
import com.tomessa.miff.domain.notification.NotificationItem
import com.tomessa.miff.domain.notification.NotificationPreferences
import com.tomessa.miff.domain.notification.NotificationType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The centre of notifications (local): one line per key, reading takes the notification out of the tray, retention, purge. No server in sight. */
class NotificationCenterTest {
    private val day = 24L * 60L * 60L * 1000L

    private class MemoryStore(
        var items: List<NotificationItem> = emptyList(),
    ) : NotificationCenterStore {
        var saves = 0
        var cleared = 0

        override fun load() = items

        override fun save(items: List<NotificationItem>) {
            this.items = items
            saves++
        }

        override fun clear() {
            items = emptyList()
            cleared++
        }
    }

    private var now = 1_800_000_000_000L
    private val store = MemoryStore()
    private val read = mutableListOf<String>()

    private fun center() = NotificationCenter(store, nowMillis = { now }, onRead = { read += it })

    private fun item(
        id: String,
        at: Long = now,
        state: ItemState = ItemState.SHOWN,
    ) = NotificationItem(id, NotificationType.N1, "miff-1", at, state = state)

    @Test
    fun aKeyIsWrittenOnce_andTheSecondTimeNothingChanges() {
        val c = center()
        assertTrue(c.add(item("k")))
        assertFalse(c.add(item("k")))
        assertEquals(1, c.items.value.size)
        assertEquals(1, store.saves)
    }

    @Test
    fun theLinesAreKeptOnTheDisk_andComeBackAtTheNextStart() {
        center().add(item("a"))
        center().add(item("b", at = now + 1))
        assertEquals(listOf("b", "a"), center().items.value.map { it.id })
    }

    @Test
    fun hasUnreadFollowsTheLines_neverANumber() =
        runTest {
            val c = center()
            assertFalse(c.hasUnread.first())
            c.add(item("a"))
            assertTrue(c.hasUnread.first())
            assertEquals(1, c.unreadCount())
            c.markRead("a")
            assertFalse(c.hasUnread.first())
        }

    @Test
    fun readingALine_takesItsNotificationOutOfTheTray_evenWhenItWasAlreadyRead() {
        val c = center()
        c.add(item("a"))
        c.markRead("a")
        c.markRead("a")
        assertEquals(listOf("a", "a"), read)
        assertEquals(
            now,
            c.items.value
                .single()
                .readAtMillis,
        )
    }

    @Test
    fun readAll_takesOutOnlyTheUnreadOnes() {
        val c = center()
        c.add(item("a", at = now - 3))
        c.add(item("b", at = now - 2))
        c.markRead("a")
        read.clear()
        c.markAllRead()
        assertEquals(listOf("b"), read)
        assertEquals(0, c.unreadCount())
    }

    @Test
    fun deleteAndClearAll_leaveNothingInTheTray() {
        val c = center()
        c.add(item("a", at = now - 3))
        c.add(item("b", at = now - 2))
        c.add(item("c", at = now - 1))
        c.delete("b")
        assertEquals(listOf("c", "a"), c.items.value.map { it.id })
        read.clear()
        c.clearAll()
        assertTrue(c.items.value.isEmpty())
        assertEquals(setOf("a", "c"), read.toSet())
    }

    @Test
    fun retention_30DaysOr100Lines_isAppliedWhenTheCentreOpens() {
        val c = center()
        c.add(item("old", at = now - 29 * day))
        assertEquals(1, c.items.value.size)
        now += 2 * day // the line is now 31 days old
        c.prune()
        assertTrue(c.items.value.isEmpty())

        (1..105).forEach { c.add(item("i$it", at = now - it * 1000L)) }
        assertEquals(100, c.items.value.size)
        assertEquals(
            "i1",
            c.items.value
                .first()
                .id,
        )
    }

    @Test
    fun aLineThatIsTooOldAtStartIsLeftOut() {
        store.items = listOf(item("stale", at = now - 31 * day), item("fresh", at = now - 1))
        assertEquals(listOf("fresh"), center().items.value.map { it.id })
    }

    @Test
    fun purge_emptiesTheMemoryAndTheDisk() {
        val c = center()
        c.add(item("a"))
        c.purge()
        assertTrue(c.items.value.isEmpty())
        assertEquals(1, store.cleared)
        assertTrue(center().items.value.isEmpty())
    }

    @Test
    fun aSilentLineIsAnOrdinaryLine_itKeepsItsState() {
        val c = center()
        c.add(item("quiet", state = ItemState.SILENT))
        assertEquals(
            ItemState.SILENT,
            c.items.value
                .single()
                .state,
        )
    }
}

class NotificationSettingsTest {
    private class MemoryPrefs(
        var saved: NotificationPreferences = NotificationPreferences(),
    ) : NotificationPreferencesStore {
        var clears = 0

        override fun load() = saved

        override fun save(preferences: NotificationPreferences) {
            saved = preferences
        }

        override fun clear() {
            saved = NotificationPreferences()
            clears++
        }
    }

    @Test
    fun aSwitchIsWrittenAtOnce_andSurvivesARestart() {
        val store = MemoryPrefs()
        val settings = NotificationSettings(store)
        settings.setType(NotificationType.N2, false)
        settings.setEnabled(false)
        settings.setNightCalm(false)
        assertEquals(NotificationPreferences(enabled = false, n2 = false, nightCalm = false), store.saved)
        assertEquals(store.saved, NotificationSettings(store).preferences.value)
    }

    @Test
    fun current_readsTheDiskAgain_soAReceiverNeverActsOnAStaleCopy() {
        val store = MemoryPrefs()
        val settings = NotificationSettings(store)
        store.saved = NotificationPreferences(enabled = false)
        assertEquals(NotificationPreferences(enabled = false), settings.current())
        assertEquals(NotificationPreferences(enabled = false), settings.preferences.value)
    }

    @Test
    fun reset_putsTheDefaultsBack_atTheSignOut() {
        val store = MemoryPrefs()
        val settings = NotificationSettings(store)
        settings.setEnabled(false)
        settings.reset()
        assertEquals(NotificationPreferences(), settings.preferences.value)
        assertEquals(1, store.clears)
    }
}
