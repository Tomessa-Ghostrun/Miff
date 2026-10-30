package com.tomessa.miff.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.auth.PasswordResetGateway
import com.tomessa.miff.data.settings.LanguageStore
import com.tomessa.miff.domain.auth.AccountSignIn
import com.tomessa.miff.domain.auth.PasswordResetOutcome
import com.tomessa.miff.domain.auth.ResendCooldown
import com.tomessa.miff.domain.settings.AppLanguage
import com.tomessa.miff.ui.auth.AuthMessage
import com.tomessa.miff.ui.auth.toMessage
import com.tomessa.miff.ui.auth.withoutResetAdvice
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Settings > Langue (ticket ONB-3a, criterion A9): French, English, the phone's. A tap applies the choice at once, there
 * is no "Enregistrer" button. No analytics event: the language is not something we collect.
 */
class LanguageViewModel(
    private val store: LanguageStore,
) : ViewModel() {
    val selected: StateFlow<AppLanguage> = store.selected

    init {
        // The language can also have been changed in the settings of the phone since the last time.
        store.refresh()
    }

    fun onSelect(language: AppLanguage) {
        if (language == store.selected.value) return
        store.select(language)
    }
}

enum class PasswordEmailStatus { IDLE, SENDING, SENT }

data class AccountUiState(
    /** Shown, never copied anywhere (criterion RG8). Null when the provider gave none: the line is then left out. */
    val email: String? = null,
    val signIn: AccountSignIn = AccountSignIn.OTHER,
    val confirmingSignOut: Boolean = false,
    val isSigningOut: Boolean = false,
    val passwordStatus: PasswordEmailStatus = PasswordEmailStatus.IDLE,
    /** Network, quota or unknown problem while sending the e-mail. Never anything about the account itself. */
    val passwordMessage: AuthMessage? = null,
    /** The send button is paused (about 60 s after a send), like on the "Mot de passe oublié" screen. */
    val passwordLocked: Boolean = false,
) {
    /** A password can be changed only on an account that has one. */
    val canChangePassword: Boolean get() = signIn == AccountSignIn.PASSWORD && !email.isNullOrBlank()
}

/**
 * Settings > Mon compte (tickets ONB-3a and ONB-3b, criteria B1 to B4, decision Q5).
 *
 * - The e-mail address is read-only (no change of address in this version).
 * - "Changer mon mot de passe" sends the reset e-mail of AUTH-2 to the address of the account (the new password is chosen on
 *   the web page of AUTH-W): no form with three fields, no re-authentication. Shown for an e-mail account only: a Google
 *   account has no password here ("Connecté avec Google").
 * - "Se déconnecter" asks for a confirmation that says honestly what a sign-out means for the loved ones, then runs the
 *   sign-out procedure of the session (listeners stopped, cache purged, back to the sign-in screen: [signOut] is
 *   `SessionManager.signOut`).
 * - No "Supprimer mon compte" line until the account deletion ticket (AUTH-5) exists.
 */
class AccountViewModel(
    email: String?,
    signIn: AccountSignIn = AccountSignIn.OTHER,
    private val signOut: suspend () -> Unit,
    private val passwordReset: PasswordResetGateway? = null,
    private val network: NetworkMonitor? = null,
    private val cooldown: ResendCooldown? = null,
    /** Monotonic clock (`SystemClock.elapsedRealtime` in the app), same as the pause of [ResendCooldown]. */
    private val nowMillis: () -> Long = { 0L },
) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountUiState(email = email, signIn = signIn))
    val uiState: StateFlow<AccountUiState> = mutableState.asStateFlow()
    private var unlockJob: Job? = null

    init {
        refreshLock()
    }

    fun onSignOutRequested() {
        mutableState.update { if (it.isSigningOut) it else it.copy(confirmingSignOut = true) }
    }

    fun onSignOutCancelled() {
        mutableState.update { if (it.isSigningOut) it else it.copy(confirmingSignOut = false) }
    }

    fun onSignOutConfirmed() {
        if (mutableState.value.isSigningOut) return
        mutableState.update { it.copy(confirmingSignOut = false, isSigningOut = true) }
        viewModelScope.launch { signOut() }
    }

    /** "Changer mon mot de passe" / "Renvoyer l'e-mail". */
    fun onChangePasswordRequested() {
        val state = mutableState.value
        val email = state.email
        val gateway = passwordReset
        if (!state.canChangePassword || email == null || gateway == null) return
        if (state.passwordStatus == PasswordEmailStatus.SENDING || state.isSigningOut) return
        if ((cooldown?.remainingMillis(email) ?: 0L) > 0L) {
            mutableState.update { it.copy(passwordLocked = true) }
            return
        }
        if (network?.isOnline() == false) {
            // Neutral message, nothing was sent.
            mutableState.update { it.copy(passwordMessage = AuthMessage.NETWORK) }
            return
        }
        mutableState.update { it.copy(passwordStatus = PasswordEmailStatus.SENDING, passwordMessage = null) }
        viewModelScope.launch {
            when (val outcome = gateway.sendResetEmail(email)) {
                PasswordResetOutcome.Accepted -> {
                    cooldown?.start(email)
                    mutableState.update { it.copy(passwordStatus = PasswordEmailStatus.SENT, passwordLocked = true) }
                    refreshLock()
                }

                PasswordResetOutcome.EmailRejected -> {
                    mutableState.update {
                        it.copy(
                            passwordStatus = PasswordEmailStatus.IDLE,
                            passwordMessage = AuthMessage.UNKNOWN,
                        )
                    }
                }

                is PasswordResetOutcome.Failed -> {
                    // Nothing was sent: no pause, trying again soon is fine.
                    val message = outcome.failure.toMessage().withoutResetAdvice()
                    mutableState.update {
                        it.copy(
                            passwordStatus = PasswordEmailStatus.IDLE,
                            passwordMessage = message,
                        )
                    }
                }
            }
        }
    }

    /** Re-enables the button when the pause ends (no ticking clock in the screen). */
    private fun refreshLock() {
        val email = mutableState.value.email ?: return
        val clock = cooldown ?: return
        unlockJob?.cancel()
        val remaining = clock.remainingMillis(email)
        mutableState.update { it.copy(passwordLocked = remaining > 0) }
        if (remaining <= 0) return
        unlockJob =
            viewModelScope.launch {
                delay(remaining)
                mutableState.update { it.copy(passwordLocked = clock.remainingMillis(email) > 0) }
            }
    }
}
