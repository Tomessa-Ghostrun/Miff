package com.tomessa.miff.data.account

import com.tomessa.miff.data.auth.NetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Where the deletion of the account stands, as the screens say it (always calm: never a count of what is left). */
sealed interface AccountDeletionState {
    /** Nothing under way (or not started yet in this launch). */
    data object Idle : AccountDeletionState

    /** Under way. [resumed]: started by an earlier launch of the app. [retrying]: after "Réessayer". */
    data class Running(
        val resumed: Boolean,
        val retrying: Boolean = false,
    ) : AccountDeletionState

    /** Paused for lack of network: it carries on by itself when the network is back. */
    data object WaitingForNetwork : AccountDeletionState

    /** Firebase asks for a more recent sign-in: the "Dernière vérification" screen. */
    data object NeedsRecentLogin : AccountDeletionState

    /** Stopped after several tries ([miffsRemain] false), or because a Miff is still there (AUTH-5b): contact and "Réessayer". */
    data class Stuck(
        val miffsRemain: Boolean,
    ) : AccountDeletionState

    /** Everything is deleted and the local data cleaned up. [appleLinked]: the Apple authorization is still to be removed by hand. */
    data class Done(
        val appleLinked: Boolean,
    ) : AccountDeletionState
}

/** What the screens need of the deletion (a double in tests). */
interface AccountDeletionControls {
    val state: StateFlow<AccountDeletionState>

    /** The person has just proved who they are: the marker is written, the app stops what runs, and the deletion goes on. */
    fun start()

    /** Launch with a marker and a valid session: the deletion is taken up again, with no new re-authentication. */
    fun resume()

    /** "Réessayer" on the failure screen. */
    fun retry()

    /** The person proved who they are again ("Dernière vérification"): the last step is tried again. */
    fun reauthenticated()

    /** The end screen was dismissed. */
    fun acknowledge()
}

/** What the controller asks of the session (implemented by the SessionManager). */
interface AccountDeletionSession {
    /**
     * Step 0: stops the listeners, the monitoring of the places and every write of status or position, and puts the app on the
     * deletion screens. Nothing may then react to the documents disappearing (no "access lost" message, no status written again).
     */
    suspend fun beginAccountDeletion()

    /**
     * Step 9: the account is gone. Purges the local cache, the preferences linked to the person, the collection of statistics, and
     * erases the marker LAST (an app killed before that purges again at the next launch).
     */
    suspend fun eraseAfterAccountDeletion(uid: String)
}

/**
 * Runs the deletion of the account for as long as the app lives (the screen can go away: criterion "un seul geste = une
 * opération"), pauses without network and carries on when it is back (SC30), stops at the "Dernière vérification" when Firebase
 * asks for it (SC13), and gives up calmly after three failures of the same kind (SC23). Modelled on
 * [com.tomessa.miff.data.miff.MiffDeletionCoordinator].
 */
class AccountDeletionController(
    private val procedure: AccountDeletionProcedure,
    private val marker: AccountDeletionMarker,
    private val session: AccountDeletionSession,
    private val network: NetworkMonitor,
    private val currentUid: () -> String?,
    private val isAppleLinked: () -> Boolean,
    private val scope: CoroutineScope,
    private val retryPauseMillis: Long = RETRY_PAUSE_MS,
) : AccountDeletionControls {
    private val mutableState = MutableStateFlow<AccountDeletionState>(AccountDeletionState.Idle)
    override val state: StateFlow<AccountDeletionState> = mutableState.asStateFlow()

    private var job: Job? = null

    override fun start() {
        val uid = currentUid() ?: return
        if (isRunning()) return
        // On the disk BEFORE the first change: an app killed at once still resumes (SC26).
        marker.write(AccountDeletionMark(uid, isAppleLinked()))
        launch(uid, resumed = false, retrying = false)
    }

    override fun resume() {
        val uid = currentUid() ?: return
        val mark = marker.read()
        // Never take up a deletion that is not this account's.
        if (mark == null || mark.uid != uid || isRunning()) return
        launch(uid, resumed = true, retrying = false)
    }

    override fun retry() {
        val uid = currentUid() ?: return
        if (isRunning()) return
        launch(uid, resumed = false, retrying = true)
    }

    override fun reauthenticated() {
        val uid = currentUid() ?: return
        if (isRunning()) return
        launch(uid, resumed = false, retrying = false)
    }

    override fun acknowledge() {
        if (mutableState.value is AccountDeletionState.Done) mutableState.value = AccountDeletionState.Idle
    }

    private fun isRunning() = job?.isActive == true

    private fun launch(
        uid: String,
        resumed: Boolean,
        retrying: Boolean,
    ) {
        mutableState.value = AccountDeletionState.Running(resumed, retrying)
        job = scope.launch { drive(uid, resumed, retrying) }
    }

    private suspend fun drive(
        uid: String,
        resumed: Boolean,
        retrying: Boolean,
    ) {
        session.beginAccountDeletion()
        val apple = marker.read()?.appleLinked ?: false
        var failures = 0
        var pause = retryPauseMillis
        while (true) {
            mutableState.value = AccountDeletionState.Running(resumed, retrying && failures == 0)
            when (procedure.run(uid)) {
                DeletionRun.Completed -> {
                    finish(uid, apple)
                    return
                }

                DeletionRun.NeedsRecentLogin -> {
                    mutableState.value = AccountDeletionState.NeedsRecentLogin
                    return
                }

                DeletionRun.MiffsRemain -> {
                    mutableState.value = AccountDeletionState.Stuck(miffsRemain = true)
                    return
                }

                DeletionRun.Offline -> {
                    mutableState.value = AccountDeletionState.WaitingForNetwork
                    network.onlineChanges().first { it }
                    delay(pause)
                    pause = (pause * 2).coerceAtMost(MAX_PAUSE_MS)
                }

                DeletionRun.Failed -> {
                    if (++failures >= MAX_FAILURES) {
                        mutableState.value = AccountDeletionState.Stuck(miffsRemain = false)
                        return
                    }
                    delay(pause)
                    pause = (pause * 2).coerceAtMost(MAX_PAUSE_MS)
                }
            }
        }
    }

    private suspend fun finish(
        uid: String,
        apple: Boolean,
    ) {
        try {
            session.eraseAfterAccountDeletion(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The account is gone; what could not be erased is erased again at the next launch (the marker is still there).
        }
        mutableState.value = AccountDeletionState.Done(apple)
    }

    companion object {
        const val RETRY_PAUSE_MS = 5_000L
        const val MAX_PAUSE_MS = 60_000L
        const val MAX_FAILURES = 3
    }
}
