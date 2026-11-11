package com.tomessa.miff.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.profile.CurrentProfile
import com.tomessa.miff.domain.profile.Initials
import com.tomessa.miff.domain.profile.MyProfile
import com.tomessa.miff.ui.components.ConnectionGate
import com.tomessa.miff.ui.components.ConnectionHost
import com.tomessa.miff.ui.components.ConnectionPopInState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/*
 * SECURITY, for this file: the name and the photo are personal data. They stay in memory (see CurrentProfile) and are
 * never logged, never in an analytics event. No analytics event is sent by the Profile tab.
 */

enum class ProfileTabStatus {
    LOADING,
    READY,

    /** The profile could not be read (offline, slow): a calm message and "Réessayer". */
    UNAVAILABLE,
}

data class ProfileTabUiState(
    val status: ProfileTabStatus = ProfileTabStatus.LOADING,
    val profile: MyProfile? = null,
) {
    val displayName: String get() = profile?.displayName.orEmpty()
    val initials: String get() = profile?.let { Initials.of(it.firstName, it.lastName) }.orEmpty()
}

/**
 * Header of the Profile tab (Onboarding ticket 6.8): the photo (or the initials), "Prénom Nom", "C'est toi", the gear.
 * Nothing else is shown until the Miff and Places tickets exist: no "Famille Martin", no list of places (criterion PT2).
 */
class ProfileTabViewModel(
    private val current: CurrentProfile,
    /** The pop-in "Pas de connexion" (bug of 2026-11-10); null in a test that does not need it. */
    private val connectionGate: ConnectionGate? = null,
) : ViewModel() {
    private val loadFailed = MutableStateFlow(false)
    private val loading = MutableStateFlow(current.profile.value == null)

    val uiState: StateFlow<ProfileTabUiState> =
        combine(current.profile, loading, loadFailed) { profile, isLoading, failed ->
            when {
                profile != null -> ProfileTabUiState(ProfileTabStatus.READY, profile)
                isLoading -> ProfileTabUiState(ProfileTabStatus.LOADING)
                failed -> ProfileTabUiState(ProfileTabStatus.UNAVAILABLE)
                else -> ProfileTabUiState(ProfileTabStatus.LOADING)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, initialState())

    init {
        if (current.profile.value == null) load()
    }

    /** "Réessayer". */
    fun retry() {
        if (loading.value) return
        load()
    }

    /** The pop-in "Pas de connexion" (same component as the Feed): shown only while the profile is not there and cannot be read. */
    val connection: StateFlow<ConnectionPopInState> =
        (connectionGate?.state ?: MutableStateFlow(ConnectionPopInState()))

    /** What the tab hands to the shell: the state, the two buttons and the watch that lives while the tab is looked at. */
    fun connectionHost(state: ConnectionPopInState): ConnectionHost =
        ConnectionHost(
            state = state,
            onRetry = {
                connectionGate?.restartCountdown()
                retry()
            },
            onDismiss = { connectionGate?.dismiss() },
            observe = ::observe,
        )

    /**
     * Lives while the Profile tab is on screen (the shell calls it inside repeatOnLifecycle(STARTED)): without the profile, no network → the pop-in and an
     * attempt every 10 s that reads only when a network is there; it stops with the tab.
     */
    suspend fun observe() {
        val gate = connectionGate ?: return
        // The profile that cannot be read (with a network) counts as "stuck" too: an attempt now and then, 3 at most, then the button only.
        val stuck = uiState.map { it.status != ProfileTabStatus.READY }
        gate.run(loading = stuck, retry = ::retry)
    }

    private fun load() {
        loading.value = true
        loadFailed.value = false
        viewModelScope.launch {
            val ok = current.refresh()
            loadFailed.value = !ok
            loading.value = false
        }
    }

    private fun initialState(): ProfileTabUiState =
        current.profile.value?.let { ProfileTabUiState(ProfileTabStatus.READY, it) }
            ?: ProfileTabUiState(ProfileTabStatus.LOADING)
}
