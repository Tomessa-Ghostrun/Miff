package com.tomessa.miff.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.auth.GoogleIdTokenSource
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.session.RefuseResult
import com.tomessa.miff.data.session.SessionManager
import com.tomessa.miff.data.session.SignedOutNotice
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.AuthForm
import com.tomessa.miff.domain.auth.ConsentChoice
import com.tomessa.miff.domain.auth.GoogleTokenResult
import com.tomessa.miff.domain.auth.LoginThrottle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
 * SECURITY, for all the view models of this file:
 * - the password is a parameter of a function call: it is never a field, never in a UI state, never in a
 *   SavedStateHandle, never logged. It lives in the text field of the screen (not saved either) and goes to Firebase;
 * - the e-mail is not logged either.
 */

data class SignUpUiState(
    val isLoading: Boolean = false,
    /** Show the format errors of the fields (after a first tap on the main button). */
    val showFieldErrors: Boolean = false,
    val message: AuthMessage? = null,
)

/**
 * Sign-up screen (ticket section 3 A). There is NO box to tick (PM decision of 2026-10-28, legal and store risk
 * assumed by the PM): the line above the main button says that tapping "Rejoindre" (or "Continuer avec Google")
 * accepts the terms, the privacy policy and the sharing of anonymous statistics. The tap itself is the acceptance,
 * hence [ConsentChoice.IMPLICIT]; nothing is sent or collected before that tap.
 */
class SignUpViewModel(
    private val session: SessionManager,
    private val network: NetworkMonitor,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = mutableState.asStateFlow()

    /** "Rejoindre". [password] is used once and not kept. */
    fun submitEmail(
        email: String,
        password: String,
    ) {
        val state = mutableState.value
        if (state.isLoading) return
        val cleanEmail = AuthForm.normalizeEmail(email)
        val formOk = AuthForm.isEmailFormatValid(cleanEmail) && AuthForm.isPasswordLongEnough(password)
        if (!formOk) {
            // No account, no request. The person is told what is missing, kindly (criteria S2, S3).
            mutableState.update { it.copy(showFieldErrors = true, message = null) }
            return
        }
        if (!network.isOnline()) {
            mutableState.update { it.copy(message = AuthMessage.NETWORK) }
            return
        }
        mutableState.update { it.copy(isLoading = true, message = null) }
        viewModelScope.launch {
            val failure = session.createAccountWithEmail(cleanEmail, password, ConsentChoice.IMPLICIT)
            mutableState.update { it.copy(isLoading = false, message = failure?.toMessage()?.withoutResetAdvice()) }
        }
    }

    /** "Continuer avec Google" on the sign-up screen: a sign-up gesture, so the same implicit acceptance. */
    fun submitGoogle(source: GoogleIdTokenSource) {
        if (mutableState.value.isLoading) return
        if (!network.isOnline()) {
            mutableState.update { it.copy(message = AuthMessage.NETWORK) }
            return
        }
        mutableState.update { it.copy(isLoading = true, message = null) }
        viewModelScope.launch {
            val message =
                when (val result = source.requestIdToken()) {
                    is GoogleTokenResult.Token -> {
                        session
                            .signInWithGoogle(
                                result.idToken,
                                ConsentChoice.IMPLICIT,
                            )?.toMessage()
                    }

                    else -> {
                        result.toMessage()
                    } // closing the dialog: null, no message, the fields stay as they are
                }
            mutableState.update { it.copy(isLoading = false, message = message?.withoutResetAdvice()) }
        }
    }
}

data class SignInUiState(
    val isLoading: Boolean = false,
    val showFieldErrors: Boolean = false,
    val message: AuthMessage? = null,
    /** After a refusal of the consent panel: a reassuring note (not an error). */
    val showAccountNotCreatedNote: Boolean = false,
)

/** Sign-in screen (ticket section 3 B). */
class SignInViewModel(
    private val session: SessionManager,
    private val network: NetworkMonitor,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val throttle = LoginThrottle(nowMillis)
    private val mutableState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = mutableState.asStateFlow()

    init {
        val notice = session.consumeNotice()
        if (notice == SignedOutNotice.ACCOUNT_NOT_CREATED) {
            mutableState.update { it.copy(showAccountNotCreatedNote = true) }
        } else if (notice == SignedOutNotice.ACCOUNT_UNAVAILABLE) {
            mutableState.update { it.copy(message = AuthMessage.ACCOUNT_UNAVAILABLE) }
        }
    }

    /** "Se connecter". [password] is used once and not kept. */
    fun submitEmail(
        email: String,
        password: String,
    ) {
        if (mutableState.value.isLoading) return
        val cleanEmail = AuthForm.normalizeEmail(email)
        if (!AuthForm.isEmailFormatValid(cleanEmail) || password.isEmpty()) {
            mutableState.update { it.copy(showFieldErrors = true, message = null) }
            return
        }
        if (throttle.remainingPauseMillis() > 0) {
            mutableState.update { it.copy(message = AuthMessage.TOO_MANY_ATTEMPTS, showAccountNotCreatedNote = false) }
            return
        }
        if (!network.isOnline()) {
            mutableState.update { it.copy(message = AuthMessage.NETWORK, showAccountNotCreatedNote = false) }
            return
        }
        mutableState.update { it.copy(isLoading = true, message = null, showAccountNotCreatedNote = false) }
        viewModelScope.launch {
            val started = nowMillis()
            val failure = session.signInWithEmail(cleanEmail, password)
            if (failure == null) {
                throttle.recordSuccess()
            } else if (failure == AuthFailure.INVALID_CREDENTIALS) {
                // Wrong password and unknown address look the same, including how long the answer takes
                // (criteria C4 and C6): a floor on the duration of this one answer.
                throttle.recordWrongCredentials()
                val remaining = MIN_WRONG_CREDENTIALS_MILLIS - (nowMillis() - started)
                if (remaining > 0) delay(remaining)
            }
            val message =
                if (failure == AuthFailure.INVALID_CREDENTIALS && throttle.remainingPauseMillis() > 0) {
                    AuthMessage.TOO_MANY_ATTEMPTS
                } else {
                    failure?.toMessage()
                }
            mutableState.update { it.copy(isLoading = false, message = message) }
        }
    }

    /** "Continuer avec Google" on the sign-in screen. A new person gets the consent panel afterwards. */
    fun submitGoogle(source: GoogleIdTokenSource) {
        if (mutableState.value.isLoading) return
        if (!network.isOnline()) {
            mutableState.update { it.copy(message = AuthMessage.NETWORK, showAccountNotCreatedNote = false) }
            return
        }
        mutableState.update { it.copy(isLoading = true, message = null, showAccountNotCreatedNote = false) }
        viewModelScope.launch {
            val message =
                when (val result = source.requestIdToken()) {
                    is GoogleTokenResult.Token -> {
                        session
                            .signInWithGoogle(
                                result.idToken,
                                signUpConsent = null,
                            )?.toMessage()
                    }

                    else -> {
                        result.toMessage()
                    } // closing the dialog: null, no message
                }
            mutableState.update { it.copy(isLoading = false, message = message?.withoutResetAdvice()) }
        }
    }

    companion object {
        /** A wrong answer is never shown faster than this, whatever the reason (unknown address or wrong password). */
        const val MIN_WRONG_CREDENTIALS_MILLIS = 800L
    }
}

data class ConsentPanelUiState(
    val isLoading: Boolean = false,
    val message: AuthMessage? = null,
)

/**
 * Panel of a new person who arrived through "Continuer avec Google" on the sign-in screen (ticket 7.8). No box:
 * the line above the buttons says that "Continuer" accepts the terms, the policy and the statistics (decision of
 * 2026-10-28). "Continuer" is the acceptance; "Annuler", system back and closing are the refusal.
 */
class ConsentPanelViewModel(
    private val session: SessionManager,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ConsentPanelUiState())
    val uiState: StateFlow<ConsentPanelUiState> = mutableState.asStateFlow()

    /** Forgets any message (called when the panel is left). */
    fun reset() {
        mutableState.value = ConsentPanelUiState()
    }

    /** "Continuer": the tap is the acceptance of (a) and (b). */
    fun accept() {
        if (mutableState.value.isLoading) return
        session.acceptConsentPanel(ConsentChoice.IMPLICIT)
    }

    /**
     * "Annuler", system back, closing: the account just created is deleted and nothing is kept.
     * [source] is only used if Firebase wants a recent sign-in first.
     */
    fun refuse(source: GoogleIdTokenSource) {
        if (mutableState.value.isLoading) return
        mutableState.update { it.copy(isLoading = true, message = null) }
        viewModelScope.launch {
            when (val result = session.refuseConsentPanel { source.requestIdToken() }) {
                RefuseResult.Done -> {
                    mutableState.update { it.copy(isLoading = false) }
                }

                RefuseResult.Cancelled -> {
                    mutableState.update { it.copy(isLoading = false) }
                }

                is RefuseResult.Failed -> {
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            message = result.failure.toMessage().withoutResetAdvice(),
                        )
                    }
                }
            }
        }
    }
}
