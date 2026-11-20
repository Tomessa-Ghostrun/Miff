package com.tomessa.miff.data.notification

import android.content.Context
import androidx.core.content.edit
import com.tomessa.miff.domain.notification.JournalEntry
import com.tomessa.miff.domain.notification.NotificationJournal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

/*
 * CNC-A (ticket docs/tickets/centre-notifications-complet.md, 8.4): the READING of the journal of the server (`users/{uid}/notifications/{entryId}`) that fills the centre with what the phone
 * did not receive (a push expired after 2 hours, the app stopped by force, the permission withdrawn, an iPhone with its app closed).
 *
 * SECURITY and PRIVACY: only the owner reads or deletes his entries (rules of the server); an entry holds identifiers and dates, never a name, a photo, a place or a position; every field
 * that comes from the network is validated by [NotificationJournal.parse] before it is used; nothing here is logged, sent as an event, or kept in a message of an exception.
 * COST: NO permanent listener, ever. ONE request from a cursor (2 minutes before the newest entry already seen), 100 entries at most per page and 2 pages at most, at the foreground, at the
 * opening of the centre and at the arrival of a push; at most once a minute apart from a push (10 seconds); an empty answer costs ONE reading. A request that the rules refuse (the backend is not
 * deployed yet) is not renewed for 30 minutes. The whole reading is behind `BuildConfig.NOTIF_JOURNAL` (false in a release until the PM has deployed the backend): then nothing is read.
 * CALM: any failure (offline, refused, too slow, an entry that does not look right) shows NOTHING; the centre goes on with the lines it has, and the next occasion tries again.
 */

/** What a request of the journal gave. */
sealed interface JournalRead {
    class Loaded(
        val entries: List<JournalEntry>,
    ) : JournalRead {
        override fun toString(): String = "Loaded(redacted)"
    }

    /** Offline, too slow, or any failure that is not a refusal: nothing is known, nothing is lost, the cursor does not move. */
    data object Unavailable : JournalRead

    /** The rules refuse the request (the backend is not deployed yet, or App Check says no): not renewed for a while. */
    data object Refused : JournalRead
}

/** The two operations on the journal of the server (a double in tests). */
interface JournalGateway {
    /** The entries newer than [sinceMillis] (all of them, 30 days, when null), newest first, at most [NotificationJournal.MAX_PAGES] pages. */
    suspend fun read(
        uid: String,
        sinceMillis: Long?,
    ): JournalRead

    /** Deletes the entries [entryIds] of [uid] on the server: asked and forgotten (offline, the SDK keeps the order and sends it later). Never throws. */
    fun delete(
        uid: String,
        entryIds: List<String>,
    )

    /** "Tout effacer" and the deletion of the account: deletes EVERY entry of [uid] on the server (a list of 100, a batch, again, until it is empty). Best effort, bounded, never throws. */
    suspend fun deleteAll(uid: String)
}

/** Where the cursor of the journal is kept: the newest date seen, for ONE account (identifiers and a date, never a name). Erased with the session. */
interface JournalCursorStore {
    fun get(uid: String): Long?

    fun set(
        uid: String,
        cursorMillis: Long,
    )

    fun clear()
}

class SharedPreferencesJournalCursorStore(
    context: Context,
) : JournalCursorStore {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            "miff_notification_journal",
            Context.MODE_PRIVATE,
        )

    override fun get(uid: String): Long? =
        if (prefs.getString(KEY_UID, null) == uid &&
            prefs.contains(KEY_CURSOR)
        ) {
            prefs.getLong(KEY_CURSOR, 0L).takeIf { it > 0L }
        } else {
            null
        }

    override fun set(
        uid: String,
        cursorMillis: Long,
    ) {
        prefs.edit(commit = true) {
            putString(KEY_UID, uid)
            putLong(KEY_CURSOR, cursorMillis)
        }
    }

    override fun clear() {
        prefs.edit(commit = true) { clear() }
    }

    private companion object {
        const val KEY_UID = "uid"
        const val KEY_CURSOR = "cursor"
    }
}

/** Why a synchronization was asked: the occasions of the ticket (8.4). */
enum class JournalSyncReason { FOREGROUND, CENTER_OPENED, PUSH_RECEIVED }

/**
 * Brings the lines of the journal into the centre. One request at a time (a second one asked meanwhile is dropped: the running one reads everything); each occasion is spaced from the last
 * ([MIN_GAP_MILLIS], [PUSH_GAP_MILLIS]); a refusal by the rules is not renewed for [REFUSED_BACKOFF_MILLIS]. [enabled] is `BuildConfig.NOTIF_JOURNAL`: when it is false nothing is read, nothing
 * is deleted on the server, and nothing here has any effect.
 */
class NotificationJournalSync(
    private val enabled: Boolean,
    private val gateway: JournalGateway,
    private val cursors: JournalCursorStore,
    private val center: NotificationCenter,
    private val currentUid: () -> String?,
    private val sessionValid: (uid: String) -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val running = Mutex()
    private var nextAllowedAtMillis = 0L

    /** The keys of the lines deleted in this session: an entry the server has not deleted yet (offline) must not bring its line back within the overlap of the next request. Memory only. */
    private val deleted = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * One synchronization. True when the journal was read and its lines are in the centre (even when there were none). False when nothing was done: switched off, nobody signed in, too soon,
     * offline, refused. Never throws, never shows anything.
     */
    suspend fun sync(reason: JournalSyncReason): Boolean {
        if (!enabled) return false
        val uid = currentUid() ?: return false
        if (!sessionValid(uid)) return false
        if (!running.tryLock()) return false
        try {
            val startedAt = nowMillis()
            if (startedAt < nextAllowedAtMillis) return false
            val cursor = cursors.get(uid)
            val read =
                try {
                    gateway.read(uid, NotificationJournal.since(cursor))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    JournalRead.Unavailable
                }
            when (read) {
                JournalRead.Refused -> {
                    nextAllowedAtMillis = startedAt + REFUSED_BACKOFF_MILLIS
                    return false
                }

                JournalRead.Unavailable -> {
                    // Offline: it costs nothing to try again soon (next foreground or opening), but not in a loop.
                    nextAllowedAtMillis = startedAt + OFFLINE_GAP_MILLIS
                    return false
                }

                is JournalRead.Loaded -> {
                    nextAllowedAtMillis =
                        startedAt + (if (reason == JournalSyncReason.PUSH_RECEIVED) PUSH_GAP_MILLIS else MIN_GAP_MILLIS)
                    // The person may have signed out, or another one in, while the server was read: nothing is written for her.
                    if (currentUid() != uid || !sessionValid(uid)) return false
                    val now = nowMillis()
                    val items =
                        read.entries
                            .mapNotNull { NotificationJournal.toItem(it, uid, now, firstSync = cursor == null) }
                            .filter { it.id !in deleted }
                    center.addAll(items)
                    NotificationJournal.nextCursor(cursor, read.entries)?.let { cursors.set(uid, it) }
                    return true
                }
            }
        } finally {
            running.unlock()
        }
    }

    /**
     * The person deleted lines (one, or "Tout effacer"): the entries they came from are deleted on the server too, so that they do not come back after a reinstallation (CNC-15). Lines
     * that never had an entry (a reminder, a request, a line written by a push of a server without journal) have nothing to delete.
     */
    fun deleteOnServer(centerIds: Collection<String>) {
        if (!enabled) return
        deleted.addAll(centerIds)
        val uid = currentUid() ?: return
        val entryIds = centerIds.mapNotNull(NotificationJournal::entryIdOf).distinct()
        if (entryIds.isNotEmpty()) gateway.delete(uid, entryIds)
    }

    /**
     * "Tout effacer" (CNC-15, contract 2.21 point 3): every entry of the account is deleted on the server, the ones this phone never saw included. Best effort (offline: nothing, the entries
     * this phone knew were already asked to be deleted one by one, and the SDK keeps those orders). Never throws.
     */
    suspend fun deleteAllOnServer() {
        if (!enabled) return
        val uid = currentUid() ?: return
        try {
            gateway.deleteAll(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No error shown: what is left expires by itself after 30 days.
        }
    }

    /** The sign-out and the deletion of the account: the cursor goes (the next account starts from nothing). */
    fun purge() {
        cursors.clear()
        nextAllowedAtMillis = 0L
        deleted.clear()
    }

    companion object {
        /** "At most once a minute" apart from a push (ticket 8.4). */
        const val MIN_GAP_MILLIS = 60_000L

        /** A push may bring a request sooner, but never a burst. */
        const val PUSH_GAP_MILLIS = 10_000L

        /** After a failure to reach the server: a new try is allowed after this. */
        const val OFFLINE_GAP_MILLIS = 10_000L

        /** After a refusal by the rules: the backend is probably not deployed; do not insist (a refused request is paid like a reading). */
        const val REFUSED_BACKOFF_MILLIS = 30L * 60L * 1000L
    }
}
