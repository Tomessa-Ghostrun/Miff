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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the deletion of the account stands, as the screens say it (always calm: never a count of what is left). */
sealed interface AccountDeletionState {
    /** Nothing under way (or not started yet in this launch). */
    data object Idle : AccountDeletionState

    /** Under way. [resumed]: started by an earlier launch of the app. [retrying]: after "Réessayer". */
    data class Running(
        val resumed: Boolean,
        val retrying: Boolean = false,
        /** Where the procedure stands ("Miff 1 sur 2"...): one line on the progress screen, never a percentage. */
        val step: DeletionStep = DeletionStep.Checking,
    ) : AccountDeletionState

    /** Paused for lack of network: it carries on by itself when the network is back. */
    data object WaitingForNetwork : AccountDeletionState

    /** Firebase asks for a more recent sign-in: the "Dernière vérification" screen. */
    data object NeedsRecentLogin : AccountDeletionState

    /** Stopped after several tries (or a network that is "there" but does not carry): the contact and "Réessayer". */
    data object Stuck : AccountDeletionState

    /**
     * The session can no longer be used (closed behind our back, token expired or revoked) while the account may still exist: NOTHING says
     * "deleted"; the marker stays and the person signs in again with the same account, which takes the deletion up again.
     */
    data object NeedsSignIn : AccountDeletionState

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

    /** "Me reconnecter" on the [AccountDeletionState.NeedsSignIn] screen: the session is closed, the marker stays. */
    fun signInAgain()
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

    /**
     * Miffs kept showing up while the deletion ran (another phone of the account, AUTH-5b): the deletion is CANCELLED. The marker is already erased; the
     * app goes back on track (the account is read again and the routing decided as usual, what was stopped starts again) and says calmly,
     * once, that the account was not deleted.
     */
    suspend fun cancelAccountDeletion()

    /** The session cannot be used: closed, local cache purged, back to the sign-in screen with the marker KEPT and a calm sentence. */
    suspend fun endSessionKeepingDeletionMarker()
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
    private val maxOnlineFailures: Int = MAX_ONLINE_FAILURES,
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

    override fun signInAgain() {
        scope.launch { session.endSessionKeepingDeletionMarker() }
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
        var offlineWhileOnline = 0
        var fewestMiffsLeft = Int.MAX_VALUE
        var pause = retryPauseMillis
        while (true) {
            mutableState.value = AccountDeletionState.Running(resumed, retrying && failures == 0)
            // How many Miffs this run found left to deal with (the first "Miff i sur n" it announced): see "progressed" below.
            var runTotal: Int? = null
            val outcome =
                procedure.run(uid) { step ->
                    if (step is DeletionStep.Miff && runTotal == null) runTotal = step.total
                    // Only while this run is the one under way (a state set by a pause or a stop is never overwritten).
                    mutableState.update { if (it is AccountDeletionState.Running) it.copy(step = step) else it }
                }
            // A run that found FEWER Miffs left than every earlier run did made progress (a Miff was left or deleted): the tries are counted again from zero,
            // so a long deletion that is slowed down now and then is never given up on. It is strictly decreasing: a step that fails for good still stops after three.
            val progressed = runTotal?.let { it < fewestMiffsLeft } == true
            if (progressed) {
                fewestMiffsLeft = runTotal ?: fewestMiffsLeft
                failures = 0
                offlineWhileOnline = 0
            }
            when (outcome) {
                DeletionRun.Completed -> {
                    finish(uid, apple)
                    return
                }

                DeletionRun.NeedsRecentLogin -> {
                    mutableState.value = AccountDeletionState.NeedsRecentLogin
                    return
                }

                DeletionRun.MiffsRemain -> {
                    // A CANCELLATION, never a dead end: the marker goes, the app is put back on track and says so calmly.
                    marker.clear()
                    mutableState.value = AccountDeletionState.Idle
                    session.cancelAccountDeletion()
                    return
                }

                DeletionRun.SessionExpired -> {
                    mutableState.value = AccountDeletionState.NeedsSignIn
                    return
                }

                DeletionRun.Offline -> {
                    // The phone says it is online and still nothing goes through: after a few tries, a calm stop and "Réessayer".
                    if (network.isOnline() && ++offlineWhileOnline >= maxOnlineFailures) {
                        mutableState.value = AccountDeletionState.Stuck
                        return
                    }
                    mutableState.value = AccountDeletionState.WaitingForNetwork
                    network.onlineChanges().first { it }
                    delay(pause)
                    pause = (pause * 2).coerceAtMost(MAX_PAUSE_MS)
                }

                DeletionRun.Failed -> {
                    if (++failures >= MAX_FAILURES) {
                        mutableState.value = AccountDeletionState.Stuck
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
        // Firebase confirmed: from now on a launch with no session means "deleted", not "signed out behind our back".
        marker.read()?.let { marker.write(it.copy(accountDeleted = true)) }
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
        const val MAX_ONLINE_FAILURES = 6
    }
}
