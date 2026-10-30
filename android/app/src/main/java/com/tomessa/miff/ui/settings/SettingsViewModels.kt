package com.tomessa.miff.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.settings.LanguageStore
import com.tomessa.miff.domain.settings.AppLanguage
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

data class AccountUiState(
    /** Shown, never copied anywhere (criterion RG8). Null when the provider gave none: the line is then left out. */
    val email: String? = null,
    val confirmingSignOut: Boolean = false,
    val isSigningOut: Boolean = false,
)

/**
 * Settings > Mon compte (ticket ONB-3a/3b). The e-mail address is read-only; "Se déconnecter" asks for a confirmation that
 * says honestly what a sign-out means for the loved ones (data-model.md, Q20), then runs the sign-out procedure of the
 * session (listeners stopped, cache purged, back to the sign-in screen: [signOut] is `SessionManager.signOut`).
 */
class AccountViewModel(
    email: String?,
    private val signOut: suspend () -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountUiState(email = email))
    val uiState: StateFlow<AccountUiState> = mutableState.asStateFlow()

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
}
