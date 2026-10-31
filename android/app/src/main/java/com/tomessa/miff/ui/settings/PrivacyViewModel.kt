package com.tomessa.miff.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.auth.ProfileWriteResult
import com.tomessa.miff.data.privacy.ConsentWithdrawals
import com.tomessa.miff.data.privacy.PrivacySettings
import com.tomessa.miff.data.session.SessionManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PrivacyMessage {
    /** No network: nothing changed (accepting needs the account to record it). */
    NETWORK,

    /** The server did not take it. Nothing changed. */
    SAVE_FAILED,
}

data class PrivacyUiState(
    /** Statistics and crash reports are collected (the one switch, two SDKs). */
    val analyticsEnabled: Boolean = false,
    val isWriting: Boolean = false,
    val message: PrivacyMessage? = null,
    /**
     * The person withdrew the consent but the account does not hold it yet (no network at the time). The collection is OFF,
     * the device remembers the choice (also after a restart) and records it by itself as soon as the network is back;
     * "Enregistrer mon choix" tries right now.
     */
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
 *   the account holding the consent. If a withdrawal was still waiting, the acceptance replaces it once the server took it.
 * - **Withdrawing** cuts the collection AT ONCE on this phone (and deletes the crash reports not yet sent), marks the choice
 *   on the device, then records it ([ConsentWithdrawals]). Without a network the mark stays, the collection stays off at every
 *   start, and the withdrawal is recorded in the background when the network is back: the screen only says so.
 */
class PrivacyViewModel(
    private val privacy: PrivacySettings,
    private val withdrawals: ConsentWithdrawals,
    private val session: SessionManager,
    private val network: NetworkMonitor,
) : ViewModel() {
    private val uid: String? = session.currentUid

    private val local = MutableStateFlow(PrivacyUiState())

    val uiState: StateFlow<PrivacyUiState> =
        combine(
            local,
            privacy.analyticsEnabled,
            uid?.let(withdrawals::pendingFlow) ?: emptyFlow(),
        ) { state, enabled, pending ->
            state.copy(analyticsEnabled = enabled, withdrawalPending = pending)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            PrivacyUiState(
                analyticsEnabled = privacy.analyticsEnabled.value,
                withdrawalPending = uid?.let(withdrawals::isPending) == true,
            ),
        )

    fun onAnalyticsToggled(enable: Boolean) {
        val me = uid ?: return
        if (local.value.isWriting || session.currentUid != me) return
        if (enable == privacy.analyticsEnabled.value && !withdrawals.isPending(me)) return
        if (enable) accept(me) else withdraw(me)
    }

    /** "Enregistrer mon choix" after a withdrawal that could not be recorded. */
    fun onRetryWithdrawal() {
        val me = uid ?: return
        if (local.value.isWriting || !withdrawals.isPending(me) || session.currentUid != me) return
        local.update { it.copy(isWriting = true, message = null) }
        viewModelScope.launch {
            withContext(NonCancellable) {
                val result = withdrawals.retryNow(me)
                local.update { it.copy(isWriting = false, message = result.toMessage()) }
            }
        }
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
                val result = withdrawals.accept(me)
                if (result == ProfileWriteResult.DONE && session.currentUid == me) {
                    privacy.applyAccountConsent(true)
                    local.update { it.copy(isWriting = false, message = null) }
                } else {
                    local.update { it.copy(isWriting = false, message = result.toMessage()) }
                }
            }
        }
    }

    private fun withdraw(me: String) {
        // At once, whatever the network says: the person asked for nothing to be collected.
        privacy.applyAccountConsent(false)
        local.update { it.copy(isWriting = true, message = null) }
        viewModelScope.launch {
            withContext(NonCancellable) {
                // Marked on the device first, then recorded; if the account cannot be reached the mark does the rest.
                withdrawals.withdraw(me)
                local.update { it.copy(isWriting = false) }
            }
        }
    }

    private fun ProfileWriteResult.toMessage(): PrivacyMessage? =
        when (this) {
            ProfileWriteResult.DONE -> null
            ProfileWriteResult.OFFLINE -> PrivacyMessage.NETWORK
            ProfileWriteResult.REFUSED, ProfileWriteResult.FAILED -> PrivacyMessage.SAVE_FAILED
        }
}
