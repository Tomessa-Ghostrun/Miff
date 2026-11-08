package com.tomessa.miff.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.account.AccountDeletionProcedure
import com.tomessa.miff.data.account.AccountMiffsSummary
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
enum class DeleteInventory { LOADING, LOADED, OFFLINE, READ_FAILED }

data class DeleteInfoUiState(
    val inventory: DeleteInventory = DeleteInventory.LOADING,
    /** The Miffs joined and created, as the SERVER said them (complete: only meaningful once [inventory] is LOADED). */
    val miffs: AccountMiffsSummary = AccountMiffsSummary.NONE,
    /** The account is also linked to Apple: Android cannot remove that authorization (limit accepted by the PM). */
    val appleLinked: Boolean = false,
    /** The confirmation dialog is open. */
    val confirming: Boolean = false,
    /** The network went away between the information screen and the dialog: a calm notice, the button stays active. */
    val confirmOffline: Boolean = false,
) {
    /** Only on a complete list (SC2): loading, offline and a failed read all keep "Continuer" grey. */
    val canContinue: Boolean get() = inventory == DeleteInventory.LOADED

    /** The account created at least one Miff: they are deleted with it (the information screen and the dialog say so). */
    val ownsMiffs: Boolean get() = miffs.owned.isNotEmpty()

    /** The buttons "Prévenir mes proches" (SC7): only for a Miff created that has at least one OTHER person in it. */
    val warnPlan: WarnPlan
        get() {
            val listed = miffs.owned.take(MAX_LISTED_OWNED)
            val named = listed.filter { it.otherMembers > 0 && it.name.isNotBlank() }.map { it.name }
            // A Miff that cannot be named in a message (blank name, or beyond the three listed) is covered by ONE message without a name.
            val generic =
                listed.any { it.otherMembers > 0 && it.name.isBlank() } ||
                    miffs.owned.drop(MAX_LISTED_OWNED).any { it.otherMembers > 0 }
            return WarnPlan(named, generic)
        }
}

/** The Miffs the information screen names (the others are counted: "et {k} autres"). */
const val MAX_LISTED_OWNED = 3

/**
 * Who the person can warn before deleting. [named]: one button per Miff listed that has other people (the name is in the message). [generic]: one more button,
 * for the Miffs that cannot be named, with a message without a name. Names are personal data: never in a `toString`.
 */
class WarnPlan(
    val named: List<String>,
    val generic: Boolean,
) {
    /** The usual case: ONE Miff to warn about, no other button: the plain "Prévenir mes proches" (maquette 10). */
    val single: Boolean get() = named.size == 1 && !generic

    val isEmpty: Boolean get() = named.isEmpty() && !generic

    override fun toString(): String = "WarnPlan(redacted)"
}

/**
 * Screen A, then the dialog B (ticket AUTH-5, section 3). Reads the Miffs of the account on the server (joined, and created with the number of other people
 * in each: AUTH-5b); nothing is written.
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
            var loaded: AccountMiffsSummary? = null
            val result =
                if (!network.isOnline()) {
                    DeleteInventory.OFFLINE
                } else {
                    when (val read = procedure.inventory(uid)) {
                        is Inventory.Loaded -> {
                            loaded = read.miffs
                            DeleteInventory.LOADED
                        }

                        Inventory.Offline -> {
                            DeleteInventory.OFFLINE
                        }

                        Inventory.Failed -> {
                            DeleteInventory.READ_FAILED
                        }
                    }
                }
            loading = false
            mutableState.update { it.copy(inventory = result, miffs = loaded ?: AccountMiffsSummary.NONE) }
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
 * once Firebase accepted (it returns true when the deletion went on, false when it could not start: nothing was touched, the person
 * can try again). Several attempts are allowed; one at a time.
 */
class DeleteReauthViewModel(
    private val gateway: AuthGateway,
    private val network: NetworkMonitor,
    last: Boolean,
    private val onVerified: () -> Boolean,
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

    /** The screen was left (the deletion went on or stopped): a later visit starts with nothing busy and no old message. */
    fun resetBusy() {
        mutableState.update { it.copy(busy = false, message = null) }
    }

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
                    finish(gateway.reauthenticateWithGoogleForDeletion(token.idToken), google = true)
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
            if (!onVerified()) {
                // The marker could not be written: nothing started, nothing was touched. A calm sentence, and the person can try again.
                mutableState.update { it.copy(busy = false, message = AuthMessage.UNKNOWN) }
            }
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
