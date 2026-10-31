package com.tomessa.miff.data.miff

import android.content.Context
import com.tomessa.miff.data.auth.CachePurger
import com.tomessa.miff.data.session.SessionTeardown
import com.tomessa.miff.domain.miff.MiffSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
 * SECURITY, for this file: names of Miffs are personal data. They stay in memory for the session (the only thing kept on the
 * disk is the id of the Miff last chosen, which is not a secret: it is useless without the account), never logged.
 */

/** Which Miff the Feed shows when there are several (Premium): a local preference, erased at sign-out (Feed F8). */
interface ActiveMiffStore {
    fun get(): String?

    fun set(miffId: String)

    fun clear()
}

class SharedPreferencesActiveMiffStore(
    context: Context,
) : ActiveMiffStore {
    private val prefs = context.applicationContext.getSharedPreferences("miff_active", Context.MODE_PRIVATE)

    override fun get(): String? = prefs.getString(KEY, null)

    override fun set(miffId: String) {
        prefs.edit().putString(KEY, miffId).commit()
    }

    override fun clear() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val KEY = "active_miff_id"
    }
}

sealed interface DirectoryState {
    data object Loading : DirectoryState

    /** Nothing could be read, and nothing is known yet (offline on a fresh install): the screens say it calmly. */
    data object Unavailable : DirectoryState

    class Ready(
        val miffs: List<MiffSummary>,
        /** The Miff shown by the Feed: the last one chosen, else the first. Null when there is none. */
        val activeId: String?,
        /** A Premium account: the Feed offers its selector, "+ Rejoindre" and "+ Créer". */
        val premium: Boolean,
        /** What is shown comes from the cache of the phone (no answer of the server): a calm mention, no alarm. */
        val fromCache: Boolean,
    ) : DirectoryState {
        val active: MiffSummary? get() = miffs.firstOrNull { it.id == activeId }

        override fun toString(): String = "Ready(redacted)"
    }
}

/** The one message of a loss of access (decision QM8). [name] is null when the phone no longer knows it. */
class LostNotice(
    val miffId: String,
    val name: String?,
) {
    override fun toString(): String = "LostNotice(redacted)"
}

/**
 * The Miffs of the person signed in, in memory for the session: read by the Feed and the Profile tab, refreshed after each
 * creation / entry, and the ONE place where a loss of access is dealt with (ticket Miff 5.7): the clean-up (the "Quitter"
 * lot, else the index alone), the purge of the cache, and the single message.
 *
 * A network problem is never a loss of access: the repository only reports a loss when the server itself answered "no".
 */
class MiffDirectory(
    private val repository: MiffRepository,
    private val currentUid: () -> String?,
    private val activeStore: ActiveMiffStore,
    private val cache: CachePurger,
) : SessionTeardown {
    private val mutableState = MutableStateFlow<DirectoryState>(DirectoryState.Loading)
    val state: StateFlow<DirectoryState> = mutableState.asStateFlow()

    private val mutableNotices = MutableStateFlow<List<LostNotice>>(emptyList())

    /** The message to show now (the first of a queue; normally there is none or one). */
    val notice: StateFlow<List<LostNotice>> = mutableNotices.asStateFlow()

    private val mutex = Mutex()

    /** Goes up at each end of a session: an answer awaited across it belongs to the previous person. */
    private var epoch = 0

    /** Losses already announced in this session: never the same message twice (no loop, ticket D1). */
    private val announced = mutableSetOf<String>()

    /** Reads the Miffs from the server (the cache when offline), cleans up the lost ones, and publishes the result. */
    suspend fun refresh() {
        mutex.withLock {
            val uid = currentUid() ?: return
            val startedIn = epoch
            val load = repository.myMiffs(uid)
            if (epoch != startedIn || currentUid() != uid) return
            when (load) {
                MyMiffsLoad.Unavailable -> {
                    if (mutableState.value !is DirectoryState.Ready) mutableState.value = DirectoryState.Unavailable
                }

                is MyMiffsLoad.Loaded -> {
                    publish(load.miffs, load.premium, load.fromCache)
                    if (load.lost.isNotEmpty()) handleLost(uid, startedIn, load.lost)
                }
            }
        }
    }

    /** The Miff screen saw the access go (listener refused, Miff deleted) while it was open. */
    suspend fun onAccessLost(
        miffId: String,
        name: String?,
    ) {
        mutex.withLock {
            val uid = currentUid() ?: return
            val startedIn = epoch
            val key = (mutableState.value as? DirectoryState.Ready)?.miffs?.firstOrNull { it.id == miffId }
            announce(LostNotice(miffId, name ?: key?.name))
            // The index key is read again from the server: it is "free" or the id of the Miff.
            val load = repository.myMiffs(uid)
            if (epoch != startedIn || currentUid() != uid) return
            if (load is MyMiffsLoad.Loaded) {
                val lost = load.lost.filter { it.ref.miffId == miffId }
                publish(load.miffs, load.premium, load.fromCache)
                if (lost.isNotEmpty()) cleanUpAndPurge(uid, startedIn, lost)
            }
        }
    }

    fun dismissNotice() {
        mutableNotices.value = mutableNotices.value.drop(1)
    }

    /** The person picked another Miff in the selector (Premium). */
    fun select(miffId: String) {
        val ready = mutableState.value as? DirectoryState.Ready ?: return
        if (ready.miffs.none { it.id == miffId }) return
        activeStore.set(miffId)
        mutableState.value = ready.withActive(miffId)
    }

    override suspend fun stop() {
        epoch++
        announced.clear()
        mutableNotices.value = emptyList()
        mutableState.value = DirectoryState.Loading
        activeStore.clear()
    }

    private fun publish(
        miffs: List<MiffSummary>,
        premium: Boolean,
        fromCache: Boolean,
    ) {
        val wanted = activeStore.get()
        val active = miffs.firstOrNull { it.id == wanted }?.id ?: miffs.firstOrNull()?.id
        mutableState.value = DirectoryState.Ready(miffs, active, premium, fromCache)
    }

    private fun DirectoryState.Ready.withActive(id: String) = DirectoryState.Ready(miffs, id, premium, fromCache)

    private suspend fun handleLost(
        uid: String,
        startedIn: Int,
        lost: List<LostMiff>,
    ) {
        lost.forEach { announce(LostNotice(it.ref.miffId, it.name)) }
        cleanUpAndPurge(uid, startedIn, lost)
    }

    /** The lot "Quitter", else the index alone; then the whole cache goes (nothing of that Miff may stay readable offline). */
    private suspend fun cleanUpAndPurge(
        uid: String,
        startedIn: Int,
        lost: List<LostMiff>,
    ) {
        cache.markPurgePending()
        lost.forEach { repository.cleanUpLost(uid, it) }
        if (epoch != startedIn) return
        cache.purge()
    }

    private fun announce(notice: LostNotice) {
        if (!announced.add(notice.miffId)) return
        mutableNotices.value = mutableNotices.value + notice
    }
}
