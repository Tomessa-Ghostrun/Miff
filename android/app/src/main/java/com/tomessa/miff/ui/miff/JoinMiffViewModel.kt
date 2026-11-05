package com.tomessa.miff.ui.miff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.miff.JoinPreparation
import com.tomessa.miff.data.miff.JoinResult
import com.tomessa.miff.data.miff.MiffRepository
import com.tomessa.miff.domain.analytics.AnalyticsEvent
import com.tomessa.miff.domain.analytics.AnalyticsTracker
import com.tomessa.miff.domain.miff.JoinAttemptLimiter
import com.tomessa.miff.domain.miff.JoinCode
import com.tomessa.miff.domain.miff.MiffFailure
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
 * SECURITY, for this file: the code typed here is a secret. It lives in memory in this state only (never in a log, an
 * analytics event, a crash report or an exception message); nothing is read from the clipboard unless the person taps "Coller".
 */

enum class JoinStep { CODE, CONFIRM }

/** What the code screen says under the field. One message for "not found" whatever the cause (ticket 5.4, criterion J4). */
enum class JoinProblem {
    /** "On ne trouve pas de Miff avec ce code. Vérifie-le auprès de la personne qui te l'a donné." */
    NOT_FOUND,

    /** "Ce code ne ressemble pas à un code Miff." : a local message, no network read (criterion J2). */
    FOREIGN_CHARACTER,

    /** "On fait une petite pause d'une minute avant de réessayer." (criterion J5) */
    PAUSED,

    OFFLINE,
    GENERIC,

    /** A free account that already has a Miff ("une seule Miff à la fois"): the Premium ticket decides what is offered. */
    ONE_MIFF,
}

class AlreadyMember(
    val miffId: String,
    val miffName: String,
) {
    override fun toString(): String = "AlreadyMember(redacted)"
}

class JoinUiState(
    val step: JoinStep = JoinStep.CODE,
    /** The normalized code, at most 12 characters. */
    val code: String = "",
    val foreignCharacter: Boolean = false,
    val checking: Boolean = false,
    val problem: JoinProblem? = null,
    val alreadyMember: AlreadyMember? = null,
    /** On the confirmation step: the name of the Miff the code opens. */
    val miffName: String? = null,
    val joining: Boolean = false,
    val confirmMessage: ActionMessage? = null,
    /** The Miff just joined: the host leaves the screen. */
    val joinedMiffId: String? = null,
) {
    val isComplete: Boolean get() = JoinCode.analyze(code).isComplete

    /** "Continuer" is grey until 12 valid characters are typed, and during a pause or a check (criterion J2). */
    val canContinue: Boolean get() = isComplete && !checking && problem != JoinProblem.PAUSED

    fun copy(
        step: JoinStep = this.step,
        code: String = this.code,
        foreignCharacter: Boolean = this.foreignCharacter,
        checking: Boolean = this.checking,
        problem: JoinProblem? = this.problem,
        alreadyMember: AlreadyMember? = this.alreadyMember,
        miffName: String? = this.miffName,
        joining: Boolean = this.joining,
        confirmMessage: ActionMessage? = this.confirmMessage,
        joinedMiffId: String? = this.joinedMiffId,
    ) = JoinUiState(
        step,
        code,
        foreignCharacter,
        checking,
        problem,
        alreadyMember,
        miffName,
        joining,
        confirmMessage,
        joinedMiffId,
    )

    override fun toString(): String = "JoinUiState(redacted)"
}

/**
 * "Rejoindre une Miff" (ticket Miff D and E, criteria J and K). The order of the checks is the one of ticket 5.4: format
 * (local), reading of the code, clean-up of a stale index, "already a member?", then the screen of the explicit consent, then
 * ONE lot. Nothing is written before the person taps "Rejoindre et partager" (criteria J6 and K3).
 */
class JoinMiffViewModel(
    private val repository: MiffRepository,
    private val network: NetworkMonitor,
    private val currentUid: () -> String?,
    private val tracker: AnalyticsTracker,
    private val onEntered: (AnalyticsEvent.OnboardingVia) -> Unit,
    private val onDirectoryChanged: suspend () -> Unit,
    private val limiter: JoinAttemptLimiter,
    private val onProfileMissing: () -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow(JoinUiState())
    val uiState: StateFlow<JoinUiState> = mutableState.asStateFlow()

    /** What the last good reading of the code gave: kept here, never in the state that is drawn. */
    private var ready: JoinPreparation.Ready? = null
    private var pauseJob: Job? = null

    /** Typed, or pasted by the person (never read from the clipboard by itself). */
    fun onCodeChanged(raw: String) {
        val current = mutableState.value
        if (current.step != JoinStep.CODE || current.checking) return
        val input = JoinCode.analyze(raw)
        mutableState.update {
            it.copy(
                code = input.code,
                foreignCharacter = input.hasForeignCharacter,
                // A new code leaves the old message behind, but a pause stays (it ends by itself).
                problem = if (it.problem == JoinProblem.PAUSED) it.problem else null,
                alreadyMember = null,
            )
        }
    }

    fun onContinue() {
        val state = mutableState.value
        if (state.step != JoinStep.CODE || state.checking) return
        val input = JoinCode.analyze(state.code)
        if (input.hasForeignCharacter) {
            // Local answer: no network read at all (criterion J2).
            mutableState.update { it.copy(problem = JoinProblem.FOREIGN_CHARACTER) }
            return
        }
        if (!input.isComplete) return
        if (limiter.isPaused()) {
            startPause()
            return
        }
        if (!network.isOnline()) {
            mutableState.update { it.copy(problem = JoinProblem.OFFLINE) }
            return
        }
        val uid = currentUid() ?: return
        mutableState.update { it.copy(checking = true, problem = null, alreadyMember = null) }
        viewModelScope.launch {
            when (val prepared = repository.prepareJoin(uid, input.code)) {
                is JoinPreparation.Ready -> {
                    limiter.recordSuccess()
                    ready = prepared
                    mutableState.update {
                        it.copy(
                            step = JoinStep.CONFIRM,
                            checking = false,
                            miffName = prepared.miffName,
                            confirmMessage = null,
                        )
                    }
                }

                is JoinPreparation.AlreadyMember -> {
                    limiter.recordSuccess()
                    mutableState.update {
                        it.copy(checking = false, alreadyMember = AlreadyMember(prepared.miffId, prepared.miffName))
                    }
                }

                JoinPreparation.NotFound -> {
                    limiter.recordFailure()
                    mutableState.update { it.copy(checking = false, problem = JoinProblem.NOT_FOUND) }
                    if (limiter.isPaused()) startPause()
                }

                // Not a mistake of the person: it does not count as a failed try.
                JoinPreparation.NeedsPremium -> {
                    mutableState.update { it.copy(checking = false, problem = JoinProblem.ONE_MIFF) }
                }

                is JoinPreparation.Failed -> {
                    val problem =
                        if (messageFor(prepared.failure) ==
                            ActionMessage.OFFLINE
                        ) {
                            JoinProblem.OFFLINE
                        } else {
                            JoinProblem.GENERIC
                        }
                    mutableState.update { it.copy(checking = false, problem = problem) }
                }
            }
        }
    }

    /** "Rejoindre et partager": the only gesture that writes anything. */
    fun onConfirm() {
        val state = mutableState.value
        val preparation = ready
        if (state.step != JoinStep.CONFIRM || state.joining || preparation == null) return
        if (!network.isOnline()) {
            mutableState.update { it.copy(confirmMessage = ActionMessage.OFFLINE) }
            return
        }
        val uid = currentUid() ?: return
        mutableState.update { it.copy(joining = true, confirmMessage = null) }
        viewModelScope.launch {
            when (val result = repository.join(uid, preparation)) {
                is JoinResult.Joined -> {
                    tracker.track(AnalyticsEvent.MiffJoined(isFirstMiff = result.firstMiff))
                    onEntered(AnalyticsEvent.OnboardingVia.JOINED)
                    onDirectoryChanged()
                    ready = null
                    mutableState.update { it.copy(joining = false, joinedMiffId = result.miffId) }
                }

                // Renewed or deleted in the meantime: back to the code with the one message (criterion J10).
                JoinResult.NotFound -> {
                    ready = null
                    mutableState.update {
                        it.copy(step = JoinStep.CODE, joining = false, problem = JoinProblem.NOT_FOUND, miffName = null)
                    }
                }

                JoinResult.NeedsPremium -> {
                    mutableState.update { it.copy(joining = false, confirmMessage = ActionMessage.ONE_MIFF) }
                }

                is JoinResult.Failed -> {
                    mutableState.update { it.copy(joining = false, confirmMessage = messageFor(result.failure)) }
                    if (result.failure == MiffFailure.NO_PROFILE) onProfileMissing()
                }
            }
        }
    }

    /** "Annuler" or back on the confirmation: nothing was written, the person is back on the code. */
    fun onCancelConfirm() {
        if (mutableState.value.joining) return
        ready = null
        mutableState.update { it.copy(step = JoinStep.CODE, miffName = null, confirmMessage = null) }
    }

    private fun startPause() {
        mutableState.update { it.copy(problem = JoinProblem.PAUSED) }
        pauseJob?.cancel()
        pauseJob =
            viewModelScope.launch {
                delay(limiter.remainingPauseMillis())
                mutableState.update { if (it.problem == JoinProblem.PAUSED) it.copy(problem = null) else it }
            }
    }
}
