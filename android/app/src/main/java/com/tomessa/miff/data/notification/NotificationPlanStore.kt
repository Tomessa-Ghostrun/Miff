package com.tomessa.miff.data.notification

import android.content.Context
import androidx.core.content.edit
import com.tomessa.miff.domain.notification.DailyCounters
import com.tomessa.miff.domain.notification.NotificationType
import com.tomessa.miff.domain.notification.NotifiedKeys
import com.tomessa.miff.domain.notification.PlanSnapshot
import com.tomessa.miff.domain.notification.SnapshotMember
import com.tomessa.miff.domain.tracking.PresenceValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * PRIVACY, for this file (criteria N-P0-10, N-C4): what the notifications keep on the phone to plan and to avoid repeating themselves.
 *  - the SNAPSHOT: per member of the active Miff, an identifier, "does he share", the KEYWORD of the presence (in a trusted zone, out, in a danger zone,
 *    unknown) and two times. NO position, NO name of a place, NO address, NO photo, NO name of a person;
 *  - the LEDGER: keys already notified (kept 48 h, a key is never notified twice), how many verifications an episode used, and the counters of the day.
 * Private files, outside every backup, erased at the sign-out and at the deletion of the account. Nothing here is sent anywhere or logged.
 */

/** Where the snapshot of the active Miff is kept (a double in tests). */
interface PlanSnapshotStore {
    fun load(): PlanSnapshot?

    fun save(snapshot: PlanSnapshot)

    fun clear()
}

@Serializable
private class StoredMember(
    val uid: String,
    val sharing: Boolean,
    val presence: String? = null,
    val presenceAt: Long? = null,
    val lastUpdateAt: Long? = null,
)

@Serializable
private class StoredSnapshot(
    val miffId: String,
    val myUid: String,
    val members: List<StoredMember>,
    val savedAt: Long,
)

class SharedPreferencesPlanSnapshotStore(
    context: Context,
) : PlanSnapshotStore {
    private val prefs = context.applicationContext.getSharedPreferences("miff_notification_plan", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): PlanSnapshot? {
        val raw = prefs.getString(KEY, null) ?: return null
        // A file that cannot be read is no snapshot (nothing is planned from a guess), never a crash.
        val stored = runCatching { json.decodeFromString<StoredSnapshot>(raw) }.getOrNull() ?: return null
        return PlanSnapshot(
            miffId = stored.miffId,
            myUid = stored.myUid,
            members =
                stored.members.map {
                    SnapshotMember(
                        it.uid,
                        it.sharing,
                        PresenceValue.fromWire(it.presence),
                        it.presenceAt,
                        it.lastUpdateAt,
                    )
                },
            savedAtMillis = stored.savedAt,
        )
    }

    // commit(): the plan of a status written a moment ago must be on the disk before a receiver wakes a cold process (restart, alarm).
    override fun save(snapshot: PlanSnapshot) {
        val stored =
            StoredSnapshot(
                snapshot.miffId,
                snapshot.myUid,
                snapshot.members.map {
                    StoredMember(it.uid, it.sharing, it.presence?.wire, it.presenceAtMillis, it.lastUpdateAtMillis)
                },
                snapshot.savedAtMillis,
            )
        prefs.edit(commit = true) { putString(KEY, json.encodeToString(stored)) }
    }

    override fun clear() {
        prefs.edit(commit = true) { clear() }
    }

    private companion object {
        const val KEY = "snapshot"
    }
}

/** The memory of what was already notified (a double in tests). */
interface NotificationLedger {
    /** Key -> time it was notified (or settled), 48 h at most. */
    fun keys(nowMillis: Long): Map<String, Long>

    /** Remembers [key]: it is never notified again (a line of the centre, or an abandoned verification). */
    fun remember(
        key: String,
        nowMillis: Long,
    )

    /** How many verifications of the server this episode already used (N3 and N4: at most 2, section 20.5). */
    fun attempts(key: String): Int

    fun addAttempt(key: String)

    /** The counters of the local day [day] (zero when they are those of another day). */
    fun counters(day: String): DailyCounters

    fun saveCounters(counters: DailyCounters)

    fun clear()
}

@Serializable
private class StoredLedger(
    val keys: Map<String, Long> = emptyMap(),
    val attempts: Map<String, Int> = emptyMap(),
    val day: String = "",
    val counts: Map<String, Int> = emptyMap(),
)

class SharedPreferencesNotificationLedger(
    context: Context,
) : NotificationLedger {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            "miff_notification_ledger",
            Context.MODE_PRIVATE,
        )
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private fun read(): StoredLedger =
        prefs.getString(KEY, null)?.let { raw -> runCatching { json.decodeFromString<StoredLedger>(raw) }.getOrNull() }
            ?: StoredLedger()

    private fun write(ledger: StoredLedger) {
        prefs.edit(commit = true) { putString(KEY, json.encodeToString(ledger)) }
    }

    override fun keys(nowMillis: Long): Map<String, Long> =
        synchronized(lock) {
            NotifiedKeys.prune(read().keys, nowMillis)
        }

    override fun remember(
        key: String,
        nowMillis: Long,
    ) = synchronized(lock) {
        val ledger = read()
        val keys = NotifiedKeys.add(ledger.keys, key, nowMillis)
        // The attempts of an episode that is forgotten are forgotten with it.
        write(StoredLedger(keys, ledger.attempts.filterKeys { it in keys }, ledger.day, ledger.counts))
    }

    override fun attempts(key: String): Int = synchronized(lock) { read().attempts[key] ?: 0 }

    override fun addAttempt(key: String) =
        synchronized(lock) {
            val ledger = read()
            write(
                StoredLedger(
                    ledger.keys,
                    ledger.attempts + (key to (ledger.attempts[key] ?: 0) + 1),
                    ledger.day,
                    ledger.counts,
                ),
            )
        }

    override fun counters(day: String): DailyCounters =
        synchronized(lock) {
            val ledger = read()
            if (ledger.day != day) {
                DailyCounters(day)
            } else {
                DailyCounters(
                    day,
                    ledger.counts
                        .mapNotNull { (code, n) ->
                            NotificationType.fromCode(code)?.let {
                                it to
                                    n
                            }
                        }.toMap(),
                )
            }
        }

    override fun saveCounters(counters: DailyCounters) =
        synchronized(lock) {
            val ledger = read()
            write(StoredLedger(ledger.keys, ledger.attempts, counters.day, counters.perType.mapKeys { it.key.code }))
        }

    override fun clear() =
        synchronized(lock) {
            prefs.edit(commit = true) { clear() }
        }

    private companion object {
        const val KEY = "ledger"
    }
}
