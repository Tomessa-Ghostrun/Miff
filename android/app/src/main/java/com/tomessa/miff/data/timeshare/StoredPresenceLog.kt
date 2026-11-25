package com.tomessa.miff.data.timeshare

import android.content.Context
import com.tomessa.miff.domain.timeshare.LoggedPresence
import com.tomessa.miff.domain.timeshare.PresenceLog
import com.tomessa.miff.domain.timeshare.TimeShareRules
import com.tomessa.miff.domain.tracking.PresenceValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/*
 * PRIVACY, for this file (docs/tickets/profil-repartition-temps.md, TIME-1; criteria TS-2, TS-3, TS-5, TS-7, TS-25): the journal of the presence is kept in a PRIVATE
 * preference file of the app (`miff_presence_log`), EXCLUDED from every backup and every transfer to another phone (res/xml/backup_rules.xml and
 * data_extraction_rules.xml exclude all the shared preferences; a test checks both files), and it never leaves the phone. What is stored: the account it belongs to
 * (a copy of another account is never used), and for each fact the Miff, the state, the identifier of a place and the instant. No coordinate, no name, no address.
 * It is erased at sign-out, at the deletion of the account, when a Miff is left, removed or deleted (for that Miff), after 35 days (fact by fact), and by the button
 * "Effacer mon historique de temps". Next to the facts the file keeps, for each Miff, ONE instant: when the detection was first armed for it (the start of the history of
 * the chart, so that "Ton graphique arrive dans X jours" counts down even before the first arrival or departure); a Miff identifier and an instant, no place, no position,
 * and it goes with the Miff in every case above. Nothing is logged. A file that cannot be read is a file that is erased: the chart starts again from nothing.
 */

/** Where the text of the journal lives (a double in tests). Synchronous on purpose: the receiver of the geofencing has seconds, and the file is small. */
interface PresenceLogStorage {
    fun read(): String?

    /** False when the write did not happen (nothing else can be done: the detection never waits for the journal). */
    fun write(text: String): Boolean

    fun clear()
}

class SharedPreferencesPresenceLogStorage(
    context: Context,
) : PresenceLogStorage {
    private val prefs = context.applicationContext.getSharedPreferences("miff_presence_log", Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY, null)

    override fun write(text: String): Boolean = prefs.edit().putString(KEY, text).commit()

    override fun clear() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val KEY = "facts"
    }
}

@Serializable
private class StoredFact(
    /** The Miff. */
    val m: String,
    /** The state (the wire value of [PresenceValue]). */
    val s: String,
    /** The identifier of the place, when the state names one. */
    val p: String? = null,
    /** The instant, in milliseconds. */
    val t: Long,
)

@Serializable
private class StoredLog(
    val uid: String,
    val facts: List<StoredFact> = emptyList(),
    /** For each Miff, the instant the detection was first armed for it ([PresenceLog.begin]); absent in a file written before. */
    val since: Map<String, Long> = emptyMap(),
)

/**
 * The journal on the phone. [currentUid] is the account signed in: nothing is recorded without one, and a file written for another account is ignored and replaced.
 * One lock for the engine (which writes) and the Profile (which reads).
 */
class StoredPresenceLog(
    private val storage: PresenceLogStorage,
    private val currentUid: () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
    /** At most 60 changes a day for 35 days is about 2,100: a wide margin, never an unbounded file. */
    private val maxFacts: Int = MAX_FACTS,
) : PresenceLog {
    private val lock = Any()
    private val mutableRevision = MutableStateFlow(0)

    override val revision: StateFlow<Int> = mutableRevision.asStateFlow()

    override fun record(
        miffId: String,
        presence: PresenceValue,
        placeId: String?,
        atMillis: Long,
    ) {
        val uid = currentUid() ?: return
        if (!SAFE_ID.matches(miffId)) return
        val state = if (presence == PresenceValue.TRAVEL) PresenceValue.UNKNOWN else presence
        val place =
            if (state == PresenceValue.IN_TRUSTED ||
                state == PresenceValue.IN_RISK
            ) {
                placeId?.takeIf(SAFE_ID::matches)
            } else {
                null
            }
        // A state that names a place without a usable one is an unknown (never a place invented).
        val effective =
            if ((state == PresenceValue.IN_TRUSTED || state == PresenceValue.IN_RISK) &&
                place == null
            ) {
                PresenceValue.UNKNOWN
            } else {
                state
            }
        synchronized(lock) {
            val stored = load(uid)
            val current = stored.facts
            val previous = current.lastOrNull { it.m == miffId }
            // The same state again is not a new fact; an "unknown" with nothing before it says nothing.
            if (previous != null && previous.s == effective.wire && previous.p == place) return
            if (previous == null && effective == PresenceValue.UNKNOWN) return
            val keptSince = clock() - TimeShareRules.RETENTION_MILLIS
            val next =
                (current + StoredFact(miffId, effective.wire, place, atMillis))
                    .filter { it.t >= keptSince }
                    .takeLast(maxFacts)
            save(uid, next, stored.since)
        }
    }

    override fun begin(
        miffId: String,
        atMillis: Long,
    ) {
        val uid = currentUid() ?: return
        if (!SAFE_ID.matches(miffId)) return
        synchronized(lock) {
            val stored = load(uid)
            // Only the first call counts: nothing is written (and the chart not worked out again) once the start is known.
            if (stored.since.containsKey(miffId)) return
            // Never later than now on the clock of this phone... and never in the future either: a start ahead of the clock (a clock set wrong, a late event) would make the history 0 for good.
            save(uid, stored.facts, stored.since + (miffId to minOf(atMillis, clock())))
        }
    }

    override fun startedAt(miffId: String): Long? {
        val uid = currentUid() ?: return null
        return synchronized(lock) {
            val stored = load(uid)
            val keptSince = clock() - TimeShareRules.RETENTION_MILLIS
            val firstFact = stored.facts.firstOrNull { it.m == miffId && it.t >= keptSince }?.t
            listOfNotNull(stored.since[miffId], firstFact).minOrNull()
        }
    }

    override fun facts(miffId: String): List<LoggedPresence> {
        val uid = currentUid() ?: return emptyList()
        return synchronized(lock) {
            val keptSince = clock() - TimeShareRules.RETENTION_MILLIS
            load(uid)
                .facts
                .filter { it.m == miffId && it.t >= keptSince }
                .mapNotNull { fact ->
                    PresenceValue.fromWire(fact.s)?.let { LoggedPresence(fact.t, it, fact.p) }
                }
        }
    }

    override fun forgetPlace(
        miffId: String,
        placeId: String,
    ) {
        val uid = currentUid() ?: return
        synchronized(lock) {
            val stored = load(uid)
            val current = stored.facts
            if (current.none { it.m == miffId && it.p == placeId }) return
            // Only the `in_*` states carry a place identifier: what named the place is no longer known.
            val next =
                current.map {
                    if (it.m == miffId &&
                        it.p == placeId
                    ) {
                        StoredFact(it.m, PresenceValue.UNKNOWN.wire, null, it.t)
                    } else {
                        it
                    }
                }
            save(uid, next, stored.since)
        }
    }

    override fun forgetMiff(miffId: String) {
        val uid = currentUid() ?: return
        synchronized(lock) {
            val stored = load(uid)
            if (stored.facts.none { it.m == miffId } && !stored.since.containsKey(miffId)) return
            save(uid, stored.facts.filter { it.m != miffId }, stored.since - miffId)
        }
    }

    override fun retainOnly(miffIds: Set<String>) {
        val uid = currentUid() ?: return
        synchronized(lock) {
            val stored = load(uid)
            if (stored.facts.all { it.m in miffIds } && stored.since.keys.all { it in miffIds }) return
            save(uid, stored.facts.filter { it.m in miffIds }, stored.since.filterKeys { it in miffIds })
        }
    }

    override fun clear() {
        synchronized(lock) {
            storage.clear()
            mutableRevision.value++
        }
    }

    /** The facts of THIS account. Another account's file, or one that cannot be read, is erased on the spot (never reused, never repaired). */
    private fun load(uid: String): StoredLog {
        val raw = storage.read() ?: return StoredLog(uid)
        val parsed =
            try {
                json.decodeFromString<StoredLog>(raw)
            } catch (e: Exception) {
                null
            }
        if (parsed == null || parsed.uid != uid) {
            storage.clear()
            return StoredLog(uid)
        }
        return parsed
    }

    private fun save(
        uid: String,
        facts: List<StoredFact>,
        since: Map<String, Long>,
    ) {
        if (facts.isEmpty() && since.isEmpty()) {
            storage.clear()
        } else {
            storage.write(json.encodeToString(StoredLog(uid, facts, since)))
        }
        mutableRevision.value++
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        /** What an identifier of a Miff or of a place looks like (the rules of Firestore); anything else is never written. */
        val SAFE_ID = Regex("^[A-Za-z0-9_-]{1,64}$")

        const val MAX_FACTS = 6000
    }
}
