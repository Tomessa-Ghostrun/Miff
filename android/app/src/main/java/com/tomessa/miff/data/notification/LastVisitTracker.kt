package com.tomessa.miff.data.notification

import android.content.Context
import androidx.core.content.edit
import com.tomessa.miff.domain.feed.PlaceKind
import com.tomessa.miff.domain.notification.LastVisit
import com.tomessa.miff.domain.notification.VisitMember
import com.tomessa.miff.domain.notification.VisitState
import com.tomessa.miff.domain.tracking.PresenceValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * PRIVACY and COST, for this file (NOTIF-P1A, criteria C1-2, C1-3, C1-7, C1-8):
 *  - what is kept on the phone is the picture of what the Feed last SHOWED of the other members, per Miff: an identifier, the KEYWORD of the presence, the CATEGORY of the place
 *    (a kind, never the name or the address of a place), two times, and one flag. NO position, NO name of a place, NO name of a person, NO photo. Never sent anywhere, never
 *    logged, in a private file outside every backup, erased at the sign-out, at the deletion of the account, at the loss of a Miff;
 *  - the comparison costs NOTHING on the server: it is made between the picture of the last visit and the statuses the Feed has just read for itself (0 read, 0 write more).
 */

/** What is kept between two visits: the last picture of each Miff, and which Miff the last picture was of. */
class LastVisitMemory(
    val states: Map<String, VisitState> = emptyMap(),
    val lastMiffId: String? = null,
) {
    override fun toString(): String = "LastVisitMemory(redacted)"
}

/** Where the pictures are kept (a double in tests). */
interface LastVisitStore {
    fun load(): LastVisitMemory

    fun save(memory: LastVisitMemory)

    fun clear()
}

@Serializable
private class StoredVisitMember(
    val uid: String,
    val muted: Boolean = false,
    val presence: String? = null,
    val place: String? = null,
    val presenceAt: Long? = null,
    val lastUpdateAt: Long? = null,
    val silent: Boolean = false,
)

@Serializable
private class StoredVisitState(
    val miffId: String,
    val members: List<StoredVisitMember>,
)

@Serializable
private class StoredVisitMemory(
    val states: List<StoredVisitState> = emptyList(),
    val lastMiffId: String? = null,
)

class SharedPreferencesLastVisitStore(
    context: Context,
) : LastVisitStore {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            "miff_notification_last_visit",
            Context.MODE_PRIVATE,
        )
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): LastVisitMemory {
        val raw = prefs.getString(KEY, null) ?: return LastVisitMemory()
        // A file that cannot be read (damaged, from a newer version) is no picture: nothing is compared from a guess, never a crash.
        val stored =
            runCatching { json.decodeFromString<StoredVisitMemory>(raw) }.getOrNull() ?: return LastVisitMemory()
        return LastVisitMemory(
            states =
                stored.states.associate { state ->
                    state.miffId to
                        VisitState(
                            state.miffId,
                            state.members.map {
                                VisitMember(
                                    uid = it.uid,
                                    muted = it.muted,
                                    presence = PresenceValue.fromWire(it.presence),
                                    place =
                                        it.place?.let { name ->
                                            PlaceKind.entries.firstOrNull { kind ->
                                                kind.name ==
                                                    name
                                            }
                                        },
                                    presenceAtMillis = it.presenceAt,
                                    lastUpdateAtMillis = it.lastUpdateAt,
                                    silent = it.silent,
                                )
                            },
                        )
                },
            lastMiffId = stored.lastMiffId,
        )
    }

    // commit(): a phone that closes the app by force right after the Feed was drawn must find, at the next opening, what was ON THE SCREEN (criterion C1-T20).
    override fun save(memory: LastVisitMemory) {
        val stored =
            StoredVisitMemory(
                memory.states.values.map { state ->
                    StoredVisitState(
                        state.miffId,
                        state.members.map {
                            StoredVisitMember(
                                it.uid,
                                it.muted,
                                it.presence?.wire,
                                it.place?.name,
                                it.presenceAtMillis,
                                it.lastUpdateAtMillis,
                                it.silent,
                            )
                        },
                    )
                },
                memory.lastMiffId,
            )
        prefs.edit(commit = true) { putString(KEY, json.encodeToString(stored)) }
    }

    override fun clear() {
        prefs.edit(commit = true) { clear() }
    }

    private companion object {
        const val KEY = "memory"
    }
}

/**
 * "Depuis ta dernière visite" (ticket notifications-p1-derniere-visite.md): when the app is OPENED (a cold start, or back to the front after 30 minutes or more) the NEXT fresh
 * picture the Feed shows is compared with the one the Feed showed last time, and the group of the centre is replaced by what changed (at most 3 lines, see [LastVisit]).
 * After that, and at every fresh picture, the stored picture follows the screen: what was seen is not told again.
 *
 * Thread-safe (the Feed calls from one worker, the lifecycle from the main thread). A call made for a session that is no longer valid writes nothing.
 */
class LastVisitTracker(
    private val store: LastVisitStore,
    private val center: NotificationCenter,
    /** Somebody is signed in on this phone with the session of [uid] (answered on the phone, no read of the server). */
    private val sessionValid: (uid: String) -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()

    private var memory: LastVisitMemory? = null

    /** A cold start is an opening: the first fresh picture of the process is compared. */
    private var opened = true

    private var backgroundedAtMillis: Long? = null

    private fun current(): LastVisitMemory = memory ?: store.load().also { memory = it }

    /** The app went to the background. */
    fun onBackground() {
        synchronized(lock) { backgroundedAtMillis = nowMillis() }
    }

    /** The app is back in front: a comparison is due when it was away for 30 minutes or more (a rotation or a quick back-and-forth is not an opening). */
    fun onForeground() {
        synchronized(lock) {
            val away = backgroundedAtMillis?.let { nowMillis() - it }
            backgroundedAtMillis = null
            if (away != null && LastVisit.isReopening(away)) opened = true
        }
    }

    /**
     * The Feed shows a FRESH picture of [state]'s Miff (confirmed by the server, online, my sharing on). If the app was just opened, it is compared first, then the picture is kept.
     * The first visit on this phone, a Miff that is not the one of the last visit (a change of active Miff), a reconnection or a lost access give no line.
     */
    fun onFreshPicture(
        myUid: String,
        state: VisitState,
    ) {
        synchronized(lock) {
            if (!sessionValid(myUid)) return
            val before = current()
            if (opened) {
                opened = false
                val seen = before.states[state.miffId]?.takeIf { before.lastMiffId == state.miffId }
                center.replaceVisitLines(LastVisit.items(state.miffId, LastVisit.compare(seen, state, nowMillis())))
            }
            // A member who is no longer in the Miff keeps no line.
            val present = state.members.map { it.uid }.toSet()
            center.removeVisitLines {
                LastVisit.parse(it)?.let { line ->
                    it.miffId == state.miffId &&
                        line.uid !in present
                } ==
                    true
            }
            if (before.states[state.miffId] != state || before.lastMiffId != state.miffId) {
                val after = LastVisitMemory(before.states + (state.miffId to state), state.miffId)
                store.save(after)
                memory = after
            }
        }
    }

    /** A Miff was left, deleted or lost: its picture and its lines are gone. */
    fun onMiffGone(miffId: String) {
        synchronized(lock) {
            val before = current()
            if (miffId in before.states || before.lastMiffId == miffId) {
                val after = LastVisitMemory(before.states - miffId, before.lastMiffId.takeIf { it != miffId })
                store.save(after)
                memory = after
            }
            center.removeVisitLines { it.miffId == miffId }
        }
    }

    /** The list of the Miffs the person is in is known: the pictures and the lines of any other Miff are forgotten. */
    fun onMiffsKnown(miffIds: Set<String>) {
        synchronized(lock) {
            val before = current()
            val gone = before.states.keys.filter { it !in miffIds }
            gone.forEach { id ->
                val after = LastVisitMemory(current().states - id, current().lastMiffId.takeIf { it != id })
                store.save(after)
                memory = after
            }
            center.removeVisitLines { it.miffId !in miffIds }
        }
    }

    /** The sign-out, the deletion of the account, the loss of the session: nothing stays, and the next session starts as a first visit (criterion C1-8). */
    fun purge() {
        synchronized(lock) {
            memory = LastVisitMemory()
            store.clear()
            opened = true
            backgroundedAtMillis = null
        }
    }
}
