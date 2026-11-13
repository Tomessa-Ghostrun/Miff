package com.tomessa.miff.domain.notification

import java.time.Instant
import java.time.ZoneId

/*
 * The centre of notifications (ticket section 20.4): a LOCAL list of what Miff told the person on this phone. It holds a type, a Miff identifier and
 * times: NO position, NO address, NO name of a place, NO name of a person, NO photo (criteria N-C4, N-P0-10). The text shown is the text of the type.
 */

/** How the notification came to the centre: shown by the system, or only written there (app in front, late catch-up, daily cap reached). */
enum class ItemState { SHOWN, SILENT }

/**
 * One line of the centre. [id] is the key of deduplication of the ticket (`n1|miff|uid|ref`, `n6|miff|place|presenceAt`...): the same key is never
 * written twice. It is also the tag of the notification of the system, so that reading the line removes the notification from the tray.
 */
data class NotificationItem(
    val id: String,
    val type: NotificationType,
    val miffId: String?,
    val createdAtMillis: Long,
    val readAtMillis: Long? = null,
    val state: ItemState = ItemState.SHOWN,
) {
    val unread: Boolean get() = readAtMillis == null

    /** Redacted: an identifier of a Miff is not for a log. */
    override fun toString(): String = "NotificationItem(redacted)"
}

/** The rules of keeping (ticket 7.3 and 20.4): 30 days or 100 lines, whichever comes first. */
object NotificationRetention {
    const val MAX_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L
    const val MAX_ITEMS = 100

    /**
     * Newest first. A line older than 30 days goes (exactly 30 days is still kept), then only the 100 most recent stay. A line dated in the future
     * (a phone whose clock was wrong) is kept as it is: it ages normally once the clock is right.
     */
    fun apply(
        items: List<NotificationItem>,
        nowMillis: Long,
    ): List<NotificationItem> =
        items
            .filter { nowMillis - it.createdAtMillis <= MAX_AGE_MILLIS }
            .sortedByDescending { it.createdAtMillis }
            .take(MAX_ITEMS)
}

/** The three groups of the centre. */
enum class ItemSection { TODAY, YESTERDAY, OLDER }

/** The centre cut into days, in the local time of the phone (ticket 7.2: "Aujourd'hui", "Hier", "Plus ancien"). */
object NotificationSections {
    fun sectionOf(
        createdAtMillis: Long,
        nowMillis: Long,
        zone: ZoneId,
    ): ItemSection {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val day = Instant.ofEpochMilli(createdAtMillis).atZone(zone).toLocalDate()
        return when {
            !day.isBefore(today) -> ItemSection.TODAY
            day == today.minusDays(1) -> ItemSection.YESTERDAY
            else -> ItemSection.OLDER
        }
    }

    /** The non-empty sections in the order of the screen, each with its lines (newest first). [createdAt] reads the time of a line. */
    fun <T> group(
        items: List<T>,
        createdAt: (T) -> Long,
        nowMillis: Long,
        zone: ZoneId,
    ): List<Pair<ItemSection, List<T>>> {
        val sorted = items.sortedByDescending(createdAt)
        return ItemSection.entries.mapNotNull { section ->
            val inSection = sorted.filter { sectionOf(createdAt(it), nowMillis, zone) == section }
            if (inSection.isEmpty()) null else section to inSection
        }
    }
}

/**
 * The centre as a pure value: add (once per key), read, delete. The store and the clock are the data layer's business
 * ([com.tomessa.miff.data.notification.NotificationCenter]).
 */
object NotificationList {
    /** Adds [item] unless a line with the same key exists (the key is never notified twice), then applies the retention. */
    fun add(
        items: List<NotificationItem>,
        item: NotificationItem,
        nowMillis: Long,
    ): List<NotificationItem> {
        if (items.any { it.id == item.id }) return NotificationRetention.apply(items, nowMillis)
        return NotificationRetention.apply(items + item, nowMillis)
    }

    fun markRead(
        items: List<NotificationItem>,
        id: String,
        nowMillis: Long,
    ): List<NotificationItem> = items.map { if (it.id == id && it.unread) it.copy(readAtMillis = nowMillis) else it }

    fun markAllRead(
        items: List<NotificationItem>,
        nowMillis: Long,
    ): List<NotificationItem> = items.map { if (it.unread) it.copy(readAtMillis = nowMillis) else it }

    fun delete(
        items: List<NotificationItem>,
        id: String,
    ): List<NotificationItem> = items.filterNot { it.id == id }

    fun unreadCount(items: List<NotificationItem>): Int = items.count { it.unread }

    /**
     * NOTIF-P1A: the group "Depuis ta dernière visite" is REPLACED as a whole (or removed when [lines] is empty): there are never more than [LastVisit.MAX_LINES] lines
     * of it, and never an old one. A line whose key is still there keeps what the person did with it (read), so an identical comparison changes nothing.
     */
    fun replaceVisit(
        items: List<NotificationItem>,
        lines: List<NotificationItem>,
        nowMillis: Long,
    ): List<NotificationItem> {
        val before = items.filter { it.type == NotificationType.C1 }.associateBy { it.id }
        val kept = items.filter { it.type != NotificationType.C1 }
        return NotificationRetention.apply(
            kept + lines.take(LastVisit.MAX_LINES).map { before[it.id] ?: it },
            nowMillis,
        )
    }

    /** NOTIF-P1A: the lines of the group that are about nobody still in the Miff, or about a Miff that is gone, are removed (the retreat of a member, the loss of access). */
    fun removeVisit(
        items: List<NotificationItem>,
        remove: (NotificationItem) -> Boolean,
    ): List<NotificationItem> = items.filterNot { it.type == NotificationType.C1 && remove(it) }
}
