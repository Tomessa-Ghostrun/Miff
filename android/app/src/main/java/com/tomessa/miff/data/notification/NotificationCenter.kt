package com.tomessa.miff.data.notification

import android.content.Context
import androidx.core.content.edit
import com.tomessa.miff.domain.notification.ItemState
import com.tomessa.miff.domain.notification.NotificationItem
import com.tomessa.miff.domain.notification.NotificationList
import com.tomessa.miff.domain.notification.NotificationRetention
import com.tomessa.miff.domain.notification.NotificationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * PRIVACY, for this file (criteria N-C3, N-C4, N-P0-10): the centre of notifications is LOCAL. It stores a type, a Miff identifier, two times and a
 * state per line, nothing else: no position, no address, no name of a place or of a person, no photo. It is never written to the server, never
 * sent as an event, never logged. Kept 30 days or 100 lines; erased at the sign-out and at the deletion of the account.
 */

/** Where the lines are kept (a double in tests). */
interface NotificationCenterStore {
    fun load(): List<NotificationItem>

    fun save(items: List<NotificationItem>)

    fun clear()
}

@Serializable
private class StoredItem(
    val id: String,
    val type: String,
    val miffId: String? = null,
    val createdAt: Long,
    val readAt: Long? = null,
    val silent: Boolean = false,
)

class SharedPreferencesNotificationCenterStore(
    context: Context,
) : NotificationCenterStore {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            "miff_notification_center",
            Context.MODE_PRIVATE,
        )
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): List<NotificationItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        // A file that cannot be read (damaged, from a newer version) is an empty centre: never a crash, never a message.
        val stored = runCatching { json.decodeFromString<List<StoredItem>>(raw) }.getOrNull() ?: return emptyList()
        return stored.mapNotNull { s ->
            val type = NotificationType.fromCode(s.type) ?: return@mapNotNull null
            NotificationItem(
                id = s.id,
                type = type,
                miffId = s.miffId,
                createdAtMillis = s.createdAt,
                readAtMillis = s.readAt,
                state = if (s.silent) ItemState.SILENT else ItemState.SHOWN,
            )
        }
    }

    override fun save(items: List<NotificationItem>) {
        val stored =
            items.map {
                StoredItem(
                    it.id,
                    it.type.code,
                    it.miffId,
                    it.createdAtMillis,
                    it.readAtMillis,
                    it.state == ItemState.SILENT,
                )
            }
        prefs.edit(commit = true) { putString(KEY_ITEMS, json.encodeToString(stored)) }
    }

    override fun clear() {
        prefs.edit(commit = true) { clear() }
    }

    private companion object {
        const val KEY_ITEMS = "items"
    }
}

/**
 * The centre: the lines, the unread dot of the bell, and the five gestures (add once per key, read, read all, delete, clear all). Thread-safe: the
 * receivers of the alarms write from a background thread while the screen reads.
 *
 * Reading a line (or all) also takes the matching notification out of the tray of the system, through [onRead]: "les notifications du tiroir
 * disparaissent quand l'élément du centre est lu" (ticket 7.2).
 */
class NotificationCenter(
    private val store: NotificationCenterStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    /** Called with the key of each line that was just read or deleted, so that its notification leaves the tray. */
    private val onRead: (String) -> Unit = {},
) {
    private val lock = Any()
    private val state = MutableStateFlow(NotificationRetention.apply(store.load(), nowMillis()))

    /** Newest first. */
    val items: StateFlow<List<NotificationItem>> = state.asStateFlow()

    /** The point on the bell: at least one line is unread. Never a number on the screen. */
    val hasUnread: kotlinx.coroutines.flow.Flow<Boolean> = state.map { NotificationList.unreadCount(it) > 0 }

    fun unreadCount(): Int = NotificationList.unreadCount(state.value)

    /** Adds a line unless one with the same key exists. Returns true when it was added. */
    fun add(item: NotificationItem): Boolean =
        synchronized(lock) {
            val before = state.value
            if (before.any { it.id == item.id }) return false
            commit(NotificationList.add(before, item, nowMillis()))
            true
        }

    fun markRead(id: String) =
        synchronized(lock) {
            val before = state.value
            val after = NotificationList.markRead(before, id, nowMillis())
            if (after != before) commit(after)
            onRead(id)
        }

    fun markAllRead() =
        synchronized(lock) {
            val before = state.value
            val unread = before.filter { it.unread }.map { it.id }
            commit(NotificationList.markAllRead(before, nowMillis()))
            unread.forEach(onRead)
        }

    fun delete(id: String) =
        synchronized(lock) {
            commit(NotificationList.delete(state.value, id))
            onRead(id)
        }

    /**
     * NOTIF-P1A, "Depuis ta dernière visite": the group is REPLACED by [lines] (at most 3), or removed when there is none. Unread, written with no notification of the system.
     * Nothing is added to the tray: a line of this group never had a notification.
     */
    fun replaceVisitLines(lines: List<NotificationItem>) =
        synchronized(lock) {
            val before = state.value
            val after = NotificationList.replaceVisit(before, lines, nowMillis())
            if (after != before) commit(after)
        }

    /** NOTIF-P1A: removes the lines of the group for which [remove] says so (a member who left the Miff, a Miff that is gone). */
    fun removeVisitLines(remove: (NotificationItem) -> Boolean) =
        synchronized(lock) {
            val before = state.value
            val after = NotificationList.removeVisit(before, remove)
            if (after != before) commit(after)
        }

    /** "Tout effacer" (after the confirmation). */
    fun clearAll() =
        synchronized(lock) {
            val ids = state.value.map { it.id }
            commit(emptyList())
            ids.forEach(onRead)
        }

    /** Applies the retention again (called when the centre opens): 30 days or 100 lines. */
    fun prune() =
        synchronized(lock) {
            val kept = NotificationRetention.apply(state.value, nowMillis())
            if (kept != state.value) commit(kept)
        }

    /** The sign-out and the deletion of the account: nothing stays (contract of the purge of the cache, DUR-3). */
    fun purge() =
        synchronized(lock) {
            state.value = emptyList()
            store.clear()
        }

    private fun commit(items: List<NotificationItem>) {
        store.save(items)
        state.value = items
    }
}
