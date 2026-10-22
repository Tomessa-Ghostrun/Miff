package com.tomessa.miff.ui.diagnostic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.AppCheckOutcome
import com.tomessa.miff.data.DiagnosticRepository
import com.tomessa.miff.data.Test2Result
import com.tomessa.miff.data.privacy.PrivacySettings
import com.tomessa.miff.domain.analytics.AnalyticsContract
import com.tomessa.miff.domain.analytics.AnalyticsEvent
import com.tomessa.miff.domain.analytics.AnalyticsTracker
import com.tomessa.miff.domain.analytics.RefusalReason
import com.tomessa.miff.domain.analytics.TrackResult
import com.tomessa.miff.domain.auth.CredentialsInput
import com.tomessa.miff.domain.auth.CredentialsInputProblem
import com.tomessa.miff.domain.diagnostic.RulesProbeOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Test1State {
    data object Idle : Test1State

    data object Running : Test1State

    data class Done(
        val outcome: RulesProbeOutcome,
    ) : Test1State
}

sealed interface Test2State {
    data object Idle : Test2State

    data object Running : Test2State

    data class InputProblem(
        val problem: CredentialsInputProblem,
    ) : Test2State

    data class Done(
        val result: Test2Result,
    ) : Test2State
}

sealed interface AppCheckState {
    data object Idle : AppCheckState

    data object Running : AppCheckState

    data class Done(
        val outcome: AppCheckOutcome,
    ) : AppCheckState
}

sealed interface EventFeedback {
    data object Sent : EventFeedback

    data object Disabled : EventFeedback

    data class Refused(
        val reason: RefusalReason,
    ) : EventFeedback

    data object LocationRefused : EventFeedback

    data object LocationNotRefused : EventFeedback
}

data class DiagnosticUiState(
    val firebaseProjectId: String?,
    val test1: Test1State = Test1State.Idle,
    val test2: Test2State = Test2State.Idle,
    val appCheck: AppCheckState = AppCheckState.Idle,
    val analyticsEnabled: Boolean = false,
    val crashReportsEnabled: Boolean = false,
    val eventFeedback: EventFeedback? = null,
)

private data class LocalState(
    val test1: Test1State = Test1State.Idle,
    val test2: Test2State = Test2State.Idle,
    val appCheck: AppCheckState = AppCheckState.Idle,
    val eventFeedback: EventFeedback? = null,
)

class DiagnosticViewModel(
    private val repository: DiagnosticRepository,
    private val privacy: PrivacySettings,
    private val tracker: AnalyticsTracker,
) : ViewModel() {
    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<DiagnosticUiState> =
        combine(local, privacy.analyticsEnabled, privacy.crashReportsEnabled) { l, analytics, crash ->
            DiagnosticUiState(
                firebaseProjectId = repository.firebaseProjectId,
                test1 = l.test1,
                test2 = l.test2,
                appCheck = l.appCheck,
                analyticsEnabled = analytics,
                crashReportsEnabled = crash,
                eventFeedback = l.eventFeedback,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue =
                DiagnosticUiState(
                    firebaseProjectId = repository.firebaseProjectId,
                    analyticsEnabled = privacy.analyticsEnabled.value,
                    crashReportsEnabled = privacy.crashReportsEnabled.value,
                ),
        )

    fun runTest1() {
        if (local.value.test1 is Test1State.Running) return
        local.update { it.copy(test1 = Test1State.Running) }
        viewModelScope.launch {
            val outcome = repository.probeRulesUnauthenticated()
            local.update { it.copy(test1 = Test1State.Done(outcome)) }
        }
    }

    /** The password is passed straight to the repository and never kept in the view model state. */
    fun runTest2(
        email: String,
        password: String,
    ) {
        if (local.value.test2 is Test2State.Running) return
        val problem = CredentialsInput.check(email, password)
        if (problem != null) {
            local.update { it.copy(test2 = Test2State.InputProblem(problem)) }
            return
        }
        local.update { it.copy(test2 = Test2State.Running) }
        viewModelScope.launch {
            val result = repository.signInAndReadOwnDocuments(email, password)
            local.update { it.copy(test2 = Test2State.Done(result)) }
        }
    }

    fun checkAppCheck() {
        if (local.value.appCheck is AppCheckState.Running) return
        local.update { it.copy(appCheck = AppCheckState.Running) }
        viewModelScope.launch {
            val outcome = repository.fetchAppCheckToken()
            local.update { it.copy(appCheck = AppCheckState.Done(outcome)) }
        }
    }

    fun setAnalyticsEnabled(enabled: Boolean) = privacy.setAnalyticsEnabled(enabled)

    fun setCrashReportsEnabled(enabled: Boolean) = privacy.setCrashReportsEnabled(enabled)

    fun sendTestEvent() {
        val result = tracker.track(AnalyticsEvent.DiagnosticTest)
        val feedback =
            when (result) {
                TrackResult.Sent -> EventFeedback.Sent
                TrackResult.Disabled -> EventFeedback.Disabled
                is TrackResult.Refused -> EventFeedback.Refused(result.validation.reason)
            }
        local.update { it.copy(eventFeedback = feedback) }
    }

    /** QA helper (Q3): tries to log an event carrying a position-like parameter. It must always be refused. */
    fun tryToSendPosition() {
        val result = tracker.track(AnalyticsContract.EVENT_DIAGNOSTIC_TEST, mapOf("lat" to 0L, "lng" to 0L))
        val feedback =
            if (result is TrackResult.Refused) EventFeedback.LocationRefused else EventFeedback.LocationNotRefused
        local.update { it.copy(eventFeedback = feedback) }
    }

    fun triggerTestCrash() {
        throw IllegalStateException("Miff diagnostic: deliberate test crash")
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
