package com.tomessa.miff.ui.miff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.miff.CreatePreparation
import com.tomessa.miff.data.miff.CreateResult
import com.tomessa.miff.data.miff.MiffRepository
import com.tomessa.miff.domain.analytics.AnalyticsEvent
import com.tomessa.miff.domain.analytics.AnalyticsTracker
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.MiffName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
 * SECURITY, for this file: the name of the Miff and its code are personal data / a secret. They live in memory in this state
 * only, never in a log, an analytics event (the events carry `is_first_miff` and nothing else) or a crash report.
 */

/** The calm sentences an action can end with (never "quota", never a code, never a technical word). */
enum class ActionMessage {
    /** "Pas de connexion pour le moment. Réessaie dès que le réseau est revenu." */
    OFFLINE,

    /** "Ça n'a pas fonctionné. Réessaie dans un instant." */
    GENERIC,

    /**
     * "Pour l'instant, ce compte peut faire partie d'une seule Miff à la fois." A free account that already has a Miff: the
     * Premium ticket decides what is offered, this only says honestly what is.
     */
    ONE_MIFF,

    /** The profile (or its phone number) is missing: the person goes back to the profile step. */
    NO_PROFILE,
}

internal fun messageFor(failure: MiffFailure): ActionMessage =
    when (failure) {
        MiffFailure.OFFLINE -> ActionMessage.OFFLINE
        MiffFailure.NO_PROFILE -> ActionMessage.NO_PROFILE
        else -> ActionMessage.GENERIC
    }

enum class CreateStep { NAME, READY }

/** The Miff that was just created, for the "Ta Miff est prête" screen. */
class CreatedMiff(
    val miffId: String,
    val name: String,
    val joinCode: String,
) {
    override fun toString(): String = "CreatedMiff(redacted)"
}

class CreateUiState(
    val step: CreateStep = CreateStep.NAME,
    val name: String = "",
    val limitReached: Boolean = false,
    val saving: Boolean = false,
    val message: ActionMessage? = null,
    val created: CreatedMiff? = null,
) {
    /** "Créer et partager" is grey until the name has 2 characters (spaces at both ends do not count), ticket C2. */
    val canSubmit: Boolean get() = !saving && MiffName.isValid(name)

    fun copy(
        step: CreateStep = this.step,
        name: String = this.name,
        limitReached: Boolean = this.limitReached,
        saving: Boolean = this.saving,
        message: ActionMessage? = this.message,
        created: CreatedMiff? = this.created,
    ) = CreateUiState(step, name, limitReached, saving, message, created)

    override fun toString(): String = "CreateUiState(redacted)"
}

/**
 * "Nouvelle Miff" then "Ta Miff est prête" (ticket Miff B and C, decision QM15: two steps). The code is generated at the save,
 * never before; the lot is ONE confirmed transaction; nothing is shown as a success before the server said yes, and nothing
 * stays queued when it did not (criterion C5). One tap = one operation (criterion H2).
 */
class CreateMiffViewModel(
    private val repository: MiffRepository,
    private val network: NetworkMonitor,
    private val currentUid: () -> String?,
    private val tracker: AnalyticsTracker,
    /** The session learns that the account now has a Miff (end of the onboarding event, no move yet). */
    private val onEntered: (AnalyticsEvent.OnboardingVia) -> Unit,
    /** The Feed and the Profile learn about the new Miff. */
    private val onDirectoryChanged: suspend () -> Unit,
    /** The profile is missing: the routing is asked again and sends the person back to the profile step. */
    private val onProfileMissing: () -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CreateUiState())
    val uiState: StateFlow<CreateUiState> = mutableState.asStateFlow()

    fun onNameChanged(typed: String) {
        if (mutableState.value.saving || mutableState.value.step != CreateStep.NAME) return
        val limited = MiffName.limit(typed)
        mutableState.update { it.copy(name = limited.text, limitReached = limited.limitReached, message = null) }
    }

    fun onSubmit() {
        val state = mutableState.value
        if (!state.canSubmit || state.step != CreateStep.NAME) return
        // Nothing to try without a network: the answer is immediate and nothing is left waiting (ticket 5.11).
        if (!network.isOnline()) {
            mutableState.update { it.copy(message = ActionMessage.OFFLINE) }
            return
        }
        mutableState.update { it.copy(saving = true, message = null) }
        val uid = currentUid()
        if (uid == null) {
            mutableState.update { it.copy(saving = false, message = ActionMessage.GENERIC) }
            return
        }
        viewModelScope.launch {
            val preparation = repository.prepareCreate(uid)
            val result =
                when (preparation) {
                    is CreatePreparation.Ready -> repository.create(uid, state.name, preparation)
                    CreatePreparation.NeedsPremium -> CreateResult.NeedsPremium
                    is CreatePreparation.Failed -> CreateResult.Failed(preparation.failure)
                }
            when (result) {
                is CreateResult.Created -> {
                    tracker.track(AnalyticsEvent.MiffCreated(isFirstMiff = result.firstMiff))
                    onEntered(AnalyticsEvent.OnboardingVia.CREATED)
                    onDirectoryChanged()
                    mutableState.update {
                        it.copy(
                            step = CreateStep.READY,
                            saving = false,
                            created = CreatedMiff(result.miffId, result.name, result.joinCode),
                        )
                    }
                }

                // Nothing is said about the Premium offer here: that is the Premium ticket's to decide.
                CreateResult.NeedsPremium -> {
                    mutableState.update { it.copy(saving = false, message = ActionMessage.ONE_MIFF) }
                }

                is CreateResult.Failed -> {
                    mutableState.update { it.copy(saving = false, message = messageFor(result.failure)) }
                    if (result.failure == MiffFailure.NO_PROFILE) onProfileMissing()
                }
            }
        }
    }
}
