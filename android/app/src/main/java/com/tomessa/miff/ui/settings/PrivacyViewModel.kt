package com.tomessa.miff.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.auth.ProfileWriteResult
import com.tomessa.miff.data.privacy.AnalyticsConsentRepository
import com.tomessa.miff.data.privacy.PrivacySettings
import com.tomessa.miff.data.session.SessionManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PrivacyMessage {
    /** No network: nothing changed (accepting needs the account to record it). */
    NETWORK,

    /** The server did not take it. Nothing changed. */
    SAVE_FAILED,

    /** The collection IS cut on this phone, but the withdrawal could not be recorded in the account yet. */
    WITHDRAWAL_NOT_RECORDED,
}

data class PrivacyUiState(
    /** Statistics and crash reports are collected (the one switch, two SDKs). */
    val analyticsEnabled: Boolean = false,
    val isWriting: Boolean = false,
    val message: PrivacyMessage? = null,
    /** The person withdrew the consent but the account still holds "accepted": "Enregistrer mon choix" finishes it. */
    val withdrawalPending: Boolean = false,
)

/*
 * No analytics event is sent by this screen (nothing about the settings is in docs/contracts/analytics-events.md), and no
 * position is involved: the block written is `analyticsConsent` (four keys about a choice).
 */

/**
 * Settings > Confidentialité, the statistics switch (ticket ONB-3b, criterion RG6 / B5): ONE switch for "statistiques
 * d'usage et rapports de plantage anonymes", read and written through the consent of the account (`analyticsConsent`).
 *
 * - **Accepting** is recorded FIRST (online only); only then does the collection start: nothing is ever collected without
 *   the account holding the consent.
 * - **Withdrawing** cuts the collection AT ONCE on this phone (and deletes the crash reports not yet sent), then records the
 *   withdrawal. If it cannot be recorded (no network), the collection stays off, the person is told so, and a button records
 *   the choice later. **Known limit**: if the app is closed before that, the account still says "accepted" and the next start
 *   would resume the collection; hence the message and the button, shown until it is recorded.
 */
class PrivacyViewModel(
    private val privacy: PrivacySettings,
    private val consents: AnalyticsConsentRepository,
    private val session: SessionManager,
    private val network: NetworkMonitor,
) : ViewModel() {
    private val uid: String? = session.currentUid

    private val local = MutableStateFlow(PrivacyUiState())

    val uiState: StateFlow<PrivacyUiState> =
        combine(local, privacy.analyticsEnabled) { state, enabled -> state.copy(analyticsEnabled = enabled) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                PrivacyUiState(analyticsEnabled = privacy.analyticsEnabled.value),
            )

    fun onAnalyticsToggled(enable: Boolean) {
        val me = uid ?: return
        if (local.value.isWriting || session.currentUid != me) return
        if (enable == privacy.analyticsEnabled.value && !local.value.withdrawalPending) return
        if (enable) accept(me) else withdraw(me)
    }

    /** "Enregistrer mon choix" after a withdrawal that could not be recorded. */
    fun onRetryWithdrawal() {
        val me = uid ?: return
        if (local.value.isWriting || !local.value.withdrawalPending || session.currentUid != me) return
        withdraw(me)
    }

    fun onMessageDismissed() {
        local.update { it.copy(message = null) }
    }

    private fun accept(me: String) {
        if (!network.isOnline()) {
            local.update { it.copy(message = PrivacyMessage.NETWORK) }
            return
        }
        local.update { it.copy(isWriting = true, message = null) }
        viewModelScope.launch {
            // The write is followed to its end whatever happens to the screen: a collection started or cut must match it.
            withContext(NonCancellable) {
                val result = consents.set(me, enable = true)
                if (result == ProfileWriteResult.DONE && session.currentUid == me) {
                    privacy.applyAccountConsent(true)
                    local.update { it.copy(isWriting = false, withdrawalPending = false, message = null) }
                } else {
                    local.update { it.copy(isWriting = false, message = result.toMessage()) }
                }
            }
        }
    }

    private fun withdraw(me: String) {
        // At once, whatever the network says: the person asked for nothing to be collected.
        privacy.applyAccountConsent(false)
        if (!network.isOnline()) {
            local.update { it.copy(withdrawalPending = true, message = PrivacyMessage.WITHDRAWAL_NOT_RECORDED) }
            return
        }
        local.update { it.copy(isWriting = true, message = null) }
        viewModelScope.launch {
            withContext(NonCancellable) {
                val result = consents.set(me, enable = false)
                if (result == ProfileWriteResult.DONE) {
                    local.update { it.copy(isWriting = false, withdrawalPending = false, message = null) }
                } else {
                    local.update {
                        it.copy(
                            isWriting = false,
                            withdrawalPending = true,
                            message = PrivacyMessage.WITHDRAWAL_NOT_RECORDED,
                        )
                    }
                }
            }
        }
    }

    private fun ProfileWriteResult.toMessage(): PrivacyMessage =
        if (this == ProfileWriteResult.OFFLINE) PrivacyMessage.NETWORK else PrivacyMessage.SAVE_FAILED
}
