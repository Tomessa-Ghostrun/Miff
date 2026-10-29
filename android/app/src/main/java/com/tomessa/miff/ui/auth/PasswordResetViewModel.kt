package com.tomessa.miff.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.auth.PasswordResetGateway
import com.tomessa.miff.domain.auth.AuthForm
import com.tomessa.miff.domain.auth.PasswordResetOutcome
import com.tomessa.miff.domain.auth.ResendCooldown
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/*
 * SECURITY: the address typed here is not secret, but it is still personal data: it is kept in memory only (never saved,
 * never logged, never sent to Analytics: this screen has no access to the tracker, so no event can leave from it).
 */

data class PasswordResetUiState(
    val isLoading: Boolean = false,
    /** Show the format error of the field (after a first tap on the main button). */
    val showFieldError: Boolean = false,
    /** The server found the address malformed although the phone did not: same words as the local format error. */
    val emailRejected: Boolean = false,
    /** Network, quota or unknown problem. Never says anything about the existence of an account. */
    val message: AuthMessage? = null,
    /** The confirmation of sending is shown. IDENTICAL whether or not the address has an account (criterion M2). */
    val sent: Boolean = false,
    /** The send button is paused (about 60 s after a send to THIS address, criterion M5). */
    val resendLocked: Boolean = false,
)

/**
 * "Mot de passe oublié" screen (ticket section 3 C). The person types the address of the account; the app asks Firebase to
 * send the e-mail and shows ONE confirmation, the same for every address (no enumeration). The new password is chosen on
 * the web page of AUTH-W, not in the app (decision Q4).
 */
class PasswordResetViewModel(
    private val gateway: PasswordResetGateway,
    private val network: NetworkMonitor,
    private val cooldown: ResendCooldown,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PasswordResetUiState())
    val uiState: StateFlow<PasswordResetUiState> = mutableState.asStateFlow()

    /** The address as the person has it in the field now (normalised), and the one the e-mail was last sent to. */
    private var typed = ""
    private var sentTo: String? = null
    private var unlockJob: Job? = null

    /** The text of the field changed (also called once when the screen appears, with the pre-filled address). */
    fun onEmailChanged(raw: String) {
        val email = normalise(raw)
        if (email == typed) return
        typed = email
        mutableState.update {
            it.copy(
                message = null,
                emailRejected = false,
                // A confirmation is about one address: editing the address takes it away (and typing it back brings it back).
                sent = sentTo != null && sentTo == typed,
                resendLocked = cooldown.remainingMillis(typed) > 0,
            )
        }
        scheduleUnlock()
    }

    /** "Envoyer" / "Renvoyer l'e-mail". */
    fun submit(raw: String) {
        if (mutableState.value.isLoading) return
        // The screen reports each change; this keeps the view model right even if it was not told yet.
        onEmailChanged(raw)
        val email = AuthForm.normalizeEmail(raw)
        if (!AuthForm.isEmailFormatValid(email)) {
            // No request for an address that cannot exist. The person is told kindly (criterion M1).
            mutableState.update { it.copy(showFieldError = true, message = null) }
            return
        }
        if (cooldown.remainingMillis(email) > 0) {
            mutableState.update { it.copy(resendLocked = true) }
            return
        }
        if (!network.isOnline()) {
            // Neutral message, the field keeps what was typed (criterion X2).
            mutableState.update { it.copy(message = AuthMessage.NETWORK) }
            return
        }
        mutableState.update { it.copy(isLoading = true, message = null, emailRejected = false) }
        viewModelScope.launch {
            val started = nowMillis()
            val outcome = gateway.sendResetEmail(email)
            if (outcome == PasswordResetOutcome.Accepted) {
                // "Unknown address" can answer faster than a real send: a floor on the duration of the answer, so that
                // the time it takes says nothing either (same idea as the sign-in screen, criteria C4 and C6).
                val remaining = MIN_ANSWER_MILLIS - (nowMillis() - started)
                if (remaining > 0) delay(remaining)
            }
            when (outcome) {
                PasswordResetOutcome.Accepted -> {
                    cooldown.start(email)
                    sentTo = normalise(email)
                    // The person may have edited the field while waiting: the confirmation is about the sent address.
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            showFieldError = false,
                            sent = sentTo == typed,
                            resendLocked = cooldown.remainingMillis(typed) > 0,
                        )
                    }
                    scheduleUnlock()
                }

                PasswordResetOutcome.EmailRejected -> {
                    mutableState.update { it.copy(isLoading = false, emailRejected = true) }
                }

                is PasswordResetOutcome.Failed -> {
                    // No pause is started: nothing was sent, trying again soon is fine.
                    val message = outcome.failure.toMessage().withoutResetAdvice()
                    mutableState.update { it.copy(isLoading = false, message = message) }
                }
            }
        }
    }

    /** Re-enables the button when the pause of the current address ends (no ticking clock in the screen). */
    private fun scheduleUnlock() {
        unlockJob?.cancel()
        val remaining = cooldown.remainingMillis(typed)
        if (remaining <= 0) return
        unlockJob =
            viewModelScope.launch {
                delay(remaining)
                mutableState.update { it.copy(resendLocked = cooldown.remainingMillis(typed) > 0) }
            }
    }

    private fun normalise(raw: String): String = AuthForm.normalizeEmail(raw).lowercase(Locale.ROOT)

    companion object {
        /** An accepted request is never shown faster than this, whatever the address. */
        const val MIN_ANSWER_MILLIS = 800L
    }
}
