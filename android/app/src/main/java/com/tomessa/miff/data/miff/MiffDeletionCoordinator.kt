package com.tomessa.miff.data.miff

import android.content.Context
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.domain.miff.MiffFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The local marker "a deletion is under way": the id of the Miff and the id of the account that started it, nothing else (no name,
 * no code: opaque identifiers, useless without the account, in a file that is not backed up). The account is kept so that
 * another person who signs in on the same phone never takes up, or announces, a deletion that is not theirs.
 */
interface PendingDeletionStore {
    fun ids(uid: String): Set<String>

    fun add(
        uid: String,
        miffId: String,
    )

    fun remove(
        uid: String,
        miffId: String,
    )
}

class SharedPreferencesPendingDeletionStore(
    context: Context,
) : PendingDeletionStore {
    private val prefs = context.applicationContext.getSharedPreferences("miff_pending_deletions", Context.MODE_PRIVATE)

    private fun all(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()

    override fun ids(uid: String): Set<String> =
        all().filter { it.startsWith("$uid$SEPARATOR") }.map { it.removePrefix("$uid$SEPARATOR") }.toSet()

    // commit(): the marker is on the disk BEFORE the first write to the server (an app killed at once still resumes).
    override fun add(
        uid: String,
        miffId: String,
    ) {
        prefs.edit().putStringSet(KEY, all() + "$uid$SEPARATOR$miffId").commit()
    }

    override fun remove(
        uid: String,
        miffId: String,
    ) {
        prefs.edit().putStringSet(KEY, all() - "$uid$SEPARATOR$miffId").commit()
    }

    private companion object {
        const val KEY = "miff_ids"
        const val SEPARATOR = "|"
    }
}

/** Where the deletion of one Miff stands, as the screens say it (always calm: never a count of what is left). */
sealed interface DeletionState {
    /** Under way. [resumed]: it was started by an earlier launch of the app. */
    data class Running(
        val resumed: Boolean,
    ) : DeletionState

    /** Paused for lack of network: it carries on by itself when the network is back. */
    data object WaitingForNetwork : DeletionState

    /** Stopped after several tries for another reason: the person can try again, and the next launch tries again too. */
    data object Stuck : DeletionState

    data object Done : DeletionState
}

/** What the Miff screen needs of the deletion (a double in tests). */
interface DeletionController {
    val states: StateFlow<Map<String, DeletionState>>

    /** The creator confirmed: the marker is written, then the deletion runs on, whether the screen stays or not. */
    fun start(miffId: String)

    /** "Réessayer" after [DeletionState.Stuck]. */
    fun retry(miffId: String)
}

/**
 * Runs the deletions of Miffs for as long as the app lives (ticket S3: the operation goes on when the person leaves the screen),
 * pauses without network and carries on when it is back (S7), and resumes at the next launch from the local marker (S4).
 * The marker is removed only once the Miff is gone.
 */
class MiffDeletionCoordinator(
    private val deletion: MiffDeleter,
    private val pending: PendingDeletionStore,
    private val network: NetworkMonitor,
    private val currentUid: () -> String?,
    private val scope: CoroutineScope,
    /** Before the first write: the creator must never be told "you no longer have access" about the Miff they are deleting. */
    private val onStarted: (miffId: String) -> Unit,
    /** After the Miff is gone: purge of the local cache, refresh of the list of Miffs. */
    private val onDeleted: suspend (miffId: String) -> Unit,
    private val retryPauseMillis: Long = RETRY_PAUSE_MS,
) : DeletionController {
    private val mutableStates = MutableStateFlow<Map<String, DeletionState>>(emptyMap())
    override val states: StateFlow<Map<String, DeletionState>> = mutableStates.asStateFlow()

    /** Emits the id of a Miff each time its deletion ends ("La Miff a été supprimée."). */
    private val mutableCompleted = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val completed: SharedFlow<String> = mutableCompleted.asSharedFlow()

    private val jobs = mutableMapOf<String, Job>()

    override fun start(miffId: String) {
        val uid = currentUid() ?: return
        pending.add(uid, miffId)
        launch(miffId, resumed = false)
    }

    override fun retry(miffId: String) {
        launch(miffId, resumed = false)
    }

    /** At launch: every deletion that was not finished is taken up again. Returns the ids taken up (to tell the person, calmly). */
    fun resumePending(): List<String> {
        val uid = currentUid() ?: return emptyList()
        return pending.ids(uid).filter { launch(it, resumed = true) }
    }

    private fun launch(
        miffId: String,
        resumed: Boolean,
    ): Boolean {
        val uid = currentUid() ?: return false
        if (jobs[miffId]?.isActive == true) return false
        onStarted(miffId)
        set(miffId, DeletionState.Running(resumed))
        jobs[miffId] = scope.launch { drive(uid, miffId, resumed) }
        return true
    }

    private suspend fun drive(
        uid: String,
        miffId: String,
        resumed: Boolean,
    ) {
        var otherFailures = 0
        var pause = retryPauseMillis
        while (true) {
            set(miffId, DeletionState.Running(resumed))
            when (val result = deletion.run(uid, miffId)) {
                MiffDeletionResult.Deleted -> {
                    pending.remove(uid, miffId)
                    set(miffId, DeletionState.Done)
                    onDeleted(miffId)
                    mutableCompleted.tryEmit(miffId)
                    return
                }

                MiffDeletionResult.NotOwner -> {
                    // Not this account's Miff: nothing to do, and the marker is no use.
                    pending.remove(uid, miffId)
                    mutableStates.update { it - miffId }
                    return
                }

                is MiffDeletionResult.Paused -> {
                    if (result.failure == MiffFailure.OFFLINE) {
                        set(miffId, DeletionState.WaitingForNetwork)
                        network.onlineChanges().first { it }
                    } else if (++otherFailures >= MAX_OTHER_FAILURES) {
                        set(miffId, DeletionState.Stuck)
                        return
                    }
                    delay(pause)
                    pause = (pause * 2).coerceAtMost(MAX_PAUSE_MS)
                }
            }
        }
    }

    private fun set(
        miffId: String,
        state: DeletionState,
    ) {
        mutableStates.update { it + (miffId to state) }
    }

    companion object {
        const val RETRY_PAUSE_MS = 5_000L
        const val MAX_PAUSE_MS = 60_000L
        const val MAX_OTHER_FAILURES = 3
    }
}
