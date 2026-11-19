package com.tomessa.miff.domain.notification

/*
 * CNC-A (ticket docs/tickets/centre-notifications-complet.md, 8.1 and 8.4): the JOURNAL of the server, `users/{uid}/notifications/{entryId}`. The server writes one small entry per
 * recipient of each push it sends (an update of a loved one, the start or the end of an alert), kept 30 days; the app only READS it (a request from a cursor, never a listener) and turns
 * each entry into a line of the centre, so that nothing is lost when the phone was off, the app stopped by force, the permission withdrawn or the message too old. Pure rules (no Android
 * class, no Firestore), tested on the JVM.
 *
 * PRIVACY: an entry holds a version, a kind, the identifier of the Miff, the identifier of the loved one and two dates. NEVER a first name, a photo, a place, a position or the name of a
 * Miff: the words and the picture of a line are resolved when it is DRAWN, from what the app is allowed to know about the person at that moment (a member who left shows the logo and
 * "Un proche"). Everything that comes from the network is validated here before it is used; an entry of an unknown version or kind is ignored without an error (compatibility).
 */

/** What the server notified. The three kinds of push it sends today. */
enum class JournalKind(
    val code: String,
) {
    UPDATE("update"),
    ALERT_START("alert_start"),
    ALERT_END("alert_end"),
    ;

    companion object {
        fun fromCode(code: String?): JournalKind? = entries.firstOrNull { it.code == code }
    }
}

/** One entry of the journal, validated. [toString] says nothing: identifiers are not for a log. */
class JournalEntry(
    val entryId: String,
    val kind: JournalKind,
    val miffId: String,
    val actorUid: String,
    val createdAtMillis: Long,
    /** The end of the 30 days of the entry: a line whose date is past is ignored (the TTL of the server deletes it only "typically within 24 h" after). */
    val expireAtMillis: Long = Long.MAX_VALUE,
) {
    override fun toString(): String = "JournalEntry(redacted)"
}

object NotificationJournal {
    /** The version of the form this app understands; any other is ignored (a later server may write more). */
    const val VERSION = 1L

    const val COLLECTION = "notifications"

    /** At most this many entries per request (the rules refuse a `limit` above 100), and two requests at most per synchronization. */
    const val PAGE_SIZE = 100
    const val MAX_PAGES = 2

    /** A request starts this long BEFORE the cursor: an entry written a moment before another, committed after it, is never skipped (the clocks of the server are not strictly ordered). */
    const val OVERLAP_MILLIS = 2L * 60L * 1000L

    /** On the first synchronization of a phone, the entries older than this arrive already read: no dot on the bell for the past (ticket 8.4 and 8.6). */
    const val FRESH_MILLIS = 24L * 60L * 60L * 1000L

    private val identifier = Regex("^[A-Za-z0-9_-]{1,128}$")

    /**
     * The entry [docId] holding [data], or null when it must be ignored: a version or a kind this app does not know, a missing or malformed field, a date that is not a time.
     * STRICT on what it uses; any other key a later version adds is ignored. [expireAtMillis] is the date of the key `expireAt`: absent or not a time = a malformed entry.
     */
    fun parse(
        docId: String,
        data: Map<String, Any?>,
        createdAtMillis: Long?,
        expireAtMillis: Long? = Long.MAX_VALUE,
    ): JournalEntry? {
        if (!identifier.matches(docId)) return null
        if ((data["v"] as? Number)?.toLong() != VERSION) return null
        val kind = JournalKind.fromCode(data["kind"] as? String) ?: return null
        val miffId = (data["miffId"] as? String)?.takeIf { identifier.matches(it) } ?: return null
        val actorUid = (data["actorUid"] as? String)?.takeIf { identifier.matches(it) } ?: return null
        if (createdAtMillis == null || createdAtMillis <= 0L || expireAtMillis == null ||
            expireAtMillis <= 0L
        ) {
            return null
        }
        return JournalEntry(docId, kind, miffId, actorUid, createdAtMillis, expireAtMillis)
    }

    /** The line of the centre an entry stands for, with the SAME key as the line the push of that entry writes (a push and the journal for one entry are one line). */
    fun keyOf(entry: JournalEntry): String =
        when (entry.kind) {
            JournalKind.UPDATE -> CircleNewsKeys.journalId(entry.actorUid, entry.entryId)
            JournalKind.ALERT_START -> CircleAlertKeys.journalId(entry.actorUid, CircleAlertEvent.START, entry.entryId)
            JournalKind.ALERT_END -> CircleAlertKeys.journalId(entry.actorUid, CircleAlertEvent.END, entry.entryId)
        }

    fun typeOf(kind: JournalKind): NotificationType =
        when (kind) {
            JournalKind.UPDATE -> NotificationType.N9
            JournalKind.ALERT_START -> NotificationType.N10
            JournalKind.ALERT_END -> NotificationType.N11
        }

    /**
     * The line for [entry], or null when it is not for this person ([myUid] never has a line of its own event). It is written WITHOUT a notification of the system (silent: the push
     * already had its chance). It arrives unread, except on the [firstSync] of a phone for an entry older than [FRESH_MILLIS]: then it is already read. The line holds no name.
     */
    fun toItem(
        entry: JournalEntry,
        myUid: String,
        nowMillis: Long,
        firstSync: Boolean,
    ): NotificationItem? {
        if (entry.actorUid == myUid || nowMillis >= entry.expireAtMillis) return null
        val old = nowMillis - entry.createdAtMillis > FRESH_MILLIS
        return NotificationItem(
            id = keyOf(entry),
            type = typeOf(entry.kind),
            miffId = entry.miffId,
            createdAtMillis = entry.createdAtMillis,
            readAtMillis = if (firstSync && old) entry.createdAtMillis else null,
            state = ItemState.SILENT,
        )
    }

    /** Where the next request starts: [OVERLAP_MILLIS] before the cursor, or null (nothing read yet: the whole journal, 30 days). */
    fun since(cursorMillis: Long?): Long? = cursorMillis?.let { (it - OVERLAP_MILLIS).coerceAtLeast(1L) }

    /** The new cursor: the newest date seen, never moving back. */
    fun nextCursor(
        previous: Long?,
        entries: List<JournalEntry>,
    ): Long? = listOfNotNull(previous, entries.maxOfOrNull { it.createdAtMillis }).maxOrNull()

    /**
     * The identifier of the entry of the journal a line of the centre came from, read in its key, so that deleting the line deletes the entry on the server too (CNC-15). Null for
     * a line that never had one (a reminder, a request, "Depuis ta dernière visite", an old line keyed by a message).
     */
    fun entryIdOf(centerId: String): String? {
        val pieces = centerId.split('|')
        val entryId =
            when {
                pieces.size == 4 && pieces[0] == "n9" && pieces[1] == "u" -> pieces[3]
                pieces.size == 5 && pieces[0] == "al" && pieces[1] == "u" -> pieces[4]
                else -> return null
            }
        // The entry ids the server writes are `u-…`, `as-…`, `ae-…`: a key that ends with anything else (an FCM message id, a time of reception) has no entry.
        return entryId.takeIf { identifier.matches(it) && ENTRY_PREFIXES.any(entryId::startsWith) }
    }

    private val ENTRY_PREFIXES = listOf("u-", "as-", "ae-")
}
