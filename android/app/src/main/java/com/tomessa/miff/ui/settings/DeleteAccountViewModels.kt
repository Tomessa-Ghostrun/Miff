package com.tomessa.miff.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.account.AccountDeletionProcedure
import com.tomessa.miff.data.account.Inventory
import com.tomessa.miff.data.auth.AuthGateway
import com.tomessa.miff.data.auth.GoogleIdTokenSource
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.GoogleTokenResult
import com.tomessa.miff.domain.auth.SignInProvider
import com.tomessa.miff.ui.auth.AuthMessage
import com.tomessa.miff.ui.auth.orNoConnectionIf
import com.tomessa.miff.ui.auth.toMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
 * SECURITY, for this file: no password, token, e-mail, id or Miff name is logged, put in an event or kept in a state. The password
 * goes from the field to Firebase and nowhere else (criteria SC13 and SC47). NOTHING here changes anything on the server: the
 * deletion itself only starts, in the controller, after a successful re-authentication.
 */

/** What the server said about the Miffs of the account (the information screen reads it from the SERVER, never the cache: SC2). */
enum class DeleteInventory { LOADING, NO_MIFFS, HAS_MIFFS, OFFLINE, READ_FAILED }

data class DeleteInfoUiState(
    val inventory: DeleteInventory = DeleteInventory.LOADING,
    /** The account is also linked to Apple: Android cannot remove that authorization (limit accepted by the PM). */
    val appleLinked: Boolean = false,
    /** The confirmation dialog is open. */
    val confirming: Boolean = false,
    /** The network went away between the information screen and the dialog: a calm notice, the button stays active. */
    val confirmOffline: Boolean = false,
) {
    /** Only an account without Miff can go on in this version (AUTH-5a); a Miff means AUTH-5b. */
    val canContinue: Boolean get() = inventory == DeleteInventory.NO_MIFFS
}

/**
 * Screen A, then the dialog B (ticket AUTH-5, section 3). Reads the Miffs of the account on the server; nothing is written.
 * "Annuler", the back arrow and the system back all leave with nothing changed (SC4): this view model has nothing to undo.
 */
class DeleteAccountInfoViewModel(
    private val procedure: AccountDeletionProcedure,
    private val currentUid: () -> String?,
    private val network: NetworkMonitor,
    appleLinked: Boolean,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DeleteInfoUiState(appleLinked = appleLinked))
    val uiState: StateFlow<DeleteInfoUiState> = mutableState.asStateFlow()
    private var loading = false

    init {
        load()
        // Offline when the screen opened: the list shows by itself as soon as the network is back.
        viewModelScope.launch {
            network.onlineChanges().collect { online ->
                if (online && mutableState.value.inventory == DeleteInventory.OFFLINE) load()
            }
        }
    }

    /** First read, and "Réessayer". */
    fun load() {
        if (loading) return
        val uid = currentUid() ?: return
        loading = true
        mutableState.update { it.copy(inventory = DeleteInventory.LOADING) }
        viewModelScope.launch {
            val result =
                if (!network.isOnline()) {
                    DeleteInventory.OFFLINE
                } else {
                    when (procedure.inventory(uid)) {
                        Inventory.NoMiffs -> DeleteInventory.NO_MIFFS
                        Inventory.HasMiffs -> DeleteInventory.HAS_MIFFS
                        Inventory.Offline -> DeleteInventory.OFFLINE
                        Inventory.Failed -> DeleteInventory.READ_FAILED
                    }
                }
            loading = false
            mutableState.update { it.copy(inventory = result) }
        }
    }

    /** "Continuer": opens the dialog, only on a complete and empty list. */
    fun onContinue() {
        mutableState.update { if (it.canContinue) it.copy(confirming = true, confirmOffline = false) else it }
    }

    /** "Garder mon compte", touch outside, system back: back to the screen, nothing touched. */
    fun onKeep() {
        mutableState.update { it.copy(confirming = false, confirmOffline = false) }
    }

    /** "Supprimer mon compte" in the dialog. True = go on to the re-authentication (nothing is written before it succeeds). */
    fun onConfirm(): Boolean {
        if (!mutableState.value.canContinue) return false
        if (!network.isOnline()) {
            mutableState.update { it.copy(confirmOffline = true) }
            return false
        }
        mutableState.update { it.copy(confirming = false, confirmOffline = false) }
        return true
    }
}

data class DeleteReauthUiState(
    /** Shown to say which account is asked about; never stored anywhere else. */
    val email: String?,
    /** The methods really linked to the account that this phone can use (password, Google). */
    val providers: Set<SignInProvider>,
    /** "Dernière vérification avant de terminer" (the second request, asked by Firebase at the very end). */
    val last: Boolean,
    /** Something is being checked: one gesture = one operation. */
    val busy: Boolean = false,
    val message: AuthMessage? = null,
) {
    val hasPassword: Boolean get() = SignInProvider.PASSWORD in providers
    val hasGoogle: Boolean get() = SignInProvider.GOOGLE in providers

    /** No method this phone can use (an account that only knows Apple, impossible to reach on Android in practice). */
    val noMethod: Boolean get() = !hasPassword && !hasGoogle
}

/**
 * Screen C, and the same screen as "Dernière vérification" ([DeleteReauthUiState.last]): the person proves who they are again, with a
 * method linked to the account. The password is a PARAMETER of [submitPassword], never a field of the state; [onVerified] is called
 * once Firebase accepted. Several attempts are allowed; one at a time.
 */
class DeleteReauthViewModel(
    private val gateway: AuthGateway,
    private val network: NetworkMonitor,
    last: Boolean,
    private val onVerified: () -> Unit,
) : ViewModel() {
    private val providers = gateway.linkedProviders()
    private val mutableState =
        MutableStateFlow(
            DeleteReauthUiState(
                email = gateway.currentEmail(),
                providers = providers.filter { it != SignInProvider.APPLE }.toSet(),
                last = last,
            ),
        )
    val uiState: StateFlow<DeleteReauthUiState> = mutableState.asStateFlow()

    /** "Confirmer" with a password. [password] is used once and not kept. */
    fun submitPassword(password: String) {
        if (mutableState.value.busy || password.isEmpty() || !mutableState.value.hasPassword) return
        if (!network.isOnline()) {
            mutableState.update { it.copy(message = AuthMessage.NETWORK) }
            return
        }
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            finish(gateway.reauthenticateWithPassword(password), google = false)
        }
    }

    /** "Continuer avec Google": the system dialog, then Firebase. Closing the dialog says nothing (SC12). */
    fun submitGoogle(source: GoogleIdTokenSource) {
        if (mutableState.value.busy || !mutableState.value.hasGoogle) return
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            when (val token = source.requestIdTokenForReauthentication()) {
                is GoogleTokenResult.Token -> {
                    finish(gateway.reauthenticateWithGoogle(token.idToken), google = true)
                }

                GoogleTokenResult.Cancelled -> {
                    mutableState.update { it.copy(busy = false) }
                }

                else -> {
                    val message = token.toMessage()?.orNoConnectionIf(!network.isOnline())
                    mutableState.update { it.copy(busy = false, message = message) }
                }
            }
        }
    }

    private fun finish(
        failure: AuthFailure?,
        google: Boolean,
    ) {
        if (failure == null) {
            // Stays "busy": the deletion starts and this screen is replaced; a second tap must not start another operation.
            onVerified()
            return
        }
        mutableState.update { it.copy(busy = false, message = failure.reauthMessage(google)) }
    }
}

/** The calm sentence for each cause of a failed re-authentication; never a code (SC10). */
fun AuthFailure.reauthMessage(google: Boolean): AuthMessage =
    when (this) {
        AuthFailure.INVALID_CREDENTIALS -> AuthMessage.REAUTH_PASSWORD_WRONG
        AuthFailure.TOO_MANY_ATTEMPTS -> if (google) AuthMessage.TOO_MANY_REQUESTS else AuthMessage.TOO_MANY_ATTEMPTS
        else -> toMessage()
    }
