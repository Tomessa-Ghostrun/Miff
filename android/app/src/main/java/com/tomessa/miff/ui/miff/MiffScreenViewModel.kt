package com.tomessa.miff.ui.miff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.miff.MiffRepository
import com.tomessa.miff.data.miff.MiffWriteResult
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffName
import com.tomessa.miff.domain.miff.MiffObservation
import com.tomessa.miff.domain.miff.MiffPlace
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.miff.MiffSharingConsent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
 * SECURITY, for this file: the name, the code, the names and photos of the members are personal data / a secret. They live in
 * memory in this state only: never in a log, an analytics event (none is sent from this screen) or a crash report.
 */

enum class MiffScreenStatus {
    LOADING,
    READY,

    /** The server said "no" or "this Miff does not exist": the one message of ticket QM8 takes over (see [MiffDirectory]). */
    ACCESS_LOST,

    /** Something else went wrong while reading, and there is nothing to show. "Réessayer". */
    UNAVAILABLE,
}

/** Which question the dialog of "Partager mon statut" asks. */
enum class SharingDialog { STOP, RESUME }

class MiffScreenState(
    val status: MiffScreenStatus = MiffScreenStatus.LOADING,
    val miffId: String = "",
    /** The name as the server has it (the title of the screen). */
    val name: String = "",
    val joinCode: String = "",
    val isOwner: Boolean = false,
    val members: List<MiffMember> = emptyList(),
    val places: List<MiffPlace> = emptyList(),
    val myUid: String = "",
    /** Whether I am sharing my status with this Miff (my own card). Null until my card is read. */
    val sharing: Boolean? = null,
    /** What is typed in the name field (the owner only). */
    val nameInput: String = "",
    val nameLimitReached: Boolean = false,
    val saving: Boolean = false,
    val sharingDialog: SharingDialog? = null,
    val sharingBusy: Boolean = false,
    val message: ActionMessage? = null,
    /** The phone has no network: reading goes on from the cache, the buttons that write are grey (criteria G8, H5). */
    val offline: Boolean = false,
    /** What is shown comes from the cache of the phone and the server has not answered yet. */
    val fromCache: Boolean = false,
    val nameSaved: Boolean = false,
) {
    /** "Enregistrer" is active only when the name changed AND is valid (criterion G3). */
    val canSaveName: Boolean
        get() = isOwner && !saving && !offline && MiffName.isValid(nameInput) && MiffName.stored(nameInput) != name

    /** At 15 members or more "Partager le code" is replaced by a calm sentence (decision QM5, a soft ceiling). */
    val isFull: Boolean get() = members.size >= MiffSharingConsent.SOFT_MEMBER_LIMIT

    val canWrite: Boolean get() = !offline && !saving && !sharingBusy

    override fun toString(): String = "MiffScreenState(redacted)"

    fun copy(
        status: MiffScreenStatus = this.status,
        miffId: String = this.miffId,
        name: String = this.name,
        joinCode: String = this.joinCode,
        isOwner: Boolean = this.isOwner,
        members: List<MiffMember> = this.members,
        places: List<MiffPlace> = this.places,
        myUid: String = this.myUid,
        sharing: Boolean? = this.sharing,
        nameInput: String = this.nameInput,
        nameLimitReached: Boolean = this.nameLimitReached,
        saving: Boolean = this.saving,
        sharingDialog: SharingDialog? = this.sharingDialog,
        sharingBusy: Boolean = this.sharingBusy,
        message: ActionMessage? = this.message,
        offline: Boolean = this.offline,
        fromCache: Boolean = this.fromCache,
        nameSaved: Boolean = this.nameSaved,
    ) = MiffScreenState(
        status,
        miffId,
        name,
        joinCode,
        isOwner,
        members,
        places,
        myUid,
        sharing,
        nameInput,
        nameLimitReached,
        saving,
        sharingDialog,
        sharingBusy,
        message,
        offline,
        fromCache,
        nameSaved,
    )
}

/**
 * The screen of a Miff, for its creator (F) and for a member (G): name (the creator can change it), code to share, the loved
 * ones, "Partager mon statut" (stop / resume at any time, ticket K5 and K6), the places (a list). No status, no battery, no
 * position, no photo of an update appears here (criterion G7). The actions that retire, leave, delete or renew belong to MIFF-2.
 *
 * Reading is live (three listeners, see FirestoreMiffObserver). Writing needs the server: with no network the buttons are grey
 * and nothing is queued (criterion H5). A loss of access is handed to [onAccessLost]; a network cut is not one (criterion D3).
 */
class MiffScreenViewModel(
    private val miffId: String,
    private val repository: MiffRepository,
    private val network: NetworkMonitor,
    private val currentUid: () -> String?,
    /**
     * The directory checks with the server, and only if the loss is confirmed cleans up and queues the single message. True =
     * confirmed. False = the server did not say so (or could not be reached): this is NOT a removal, the screen offers
     * "Réessayer" and nobody is told anything.
     */
    private val onAccessLost: suspend (miffId: String, name: String?) -> Boolean,
    private val onNameChanged: suspend () -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MiffScreenState(miffId = miffId, myUid = currentUid().orEmpty()))
    val uiState: StateFlow<MiffScreenState> = mutableState.asStateFlow()

    private var lostReported = false

    /** Goes up at "Réessayer": the listeners are attached again. */
    private val restarts = MutableStateFlow(0)

    /**
     * Reads the Miff live until the caller stops (the screen calls it from `repeatOnLifecycle(STARTED)`: the three Firestore
     * listeners and the network watch are cancelled when the screen goes to the background, and attached again when it
     * comes back; nothing keeps listening to a Miff nobody is looking at).
     */
    suspend fun observe() {
        coroutineScope {
            launch { network.onlineChanges().collect { online -> mutableState.update { it.copy(offline = !online) } } }
            restarts.collectLatest {
                repository.observe(miffId).collect { observation -> handle(observation) }
            }
        }
    }

    private suspend fun handle(observation: MiffObservation) {
        when (observation) {
            MiffObservation.Loading -> {
                Unit
            }

            is MiffObservation.Data -> {
                lostReported = false
                apply(observation)
            }

            MiffObservation.AccessLost -> {
                if (lostReported) return
                lostReported = true
                val name = mutableState.value.name.takeIf { it.isNotEmpty() }
                // Nothing of the Miff stays on the screen while the server is asked (criterion D7).
                mutableState.value = MiffScreenState(status = MiffScreenStatus.ACCESS_LOST, miffId = miffId)
                val confirmed =
                    try {
                        onAccessLost(miffId, name)
                    } catch (e: CancellationException) {
                        // The screen left the foreground (or rotated) while the server was being asked: nothing was decided.
                        // Without this, "lost" would stay reported on a blank page and the next AccessLost would be ignored;
                        // the listeners are attached again when the screen comes back, and say it again.
                        lostReported = false
                        mutableState.value = MiffScreenState(status = MiffScreenStatus.LOADING, miffId = miffId)
                        throw e
                    }
                if (!confirmed) {
                    // Not confirmed by the server: a doubt is not a removal. "Réessayer", no message (ticket 5.7, D3).
                    lostReported = false
                    mutableState.value = MiffScreenState(status = MiffScreenStatus.UNAVAILABLE, miffId = miffId)
                }
            }

            MiffObservation.Failed -> {
                if (mutableState.value.status != MiffScreenStatus.READY) {
                    mutableState.update { it.copy(status = MiffScreenStatus.UNAVAILABLE) }
                }
            }
        }
    }

    /** "Réessayer" after a reading that failed. */
    fun retry() {
        if (mutableState.value.status != MiffScreenStatus.UNAVAILABLE) return
        lostReported = false
        mutableState.update { it.copy(status = MiffScreenStatus.LOADING) }
        restarts.update { it + 1 }
    }

    private fun apply(observation: MiffObservation.Data) {
        val snapshot = observation.snapshot
        val uid = currentUid().orEmpty()
        mutableState.update { old ->
            val owner = snapshot.miff.ownerUid == uid
            val savedName = snapshot.miff.name
            // What the person is typing is never overwritten by a change that arrives while they type.
            val untouched = old.status != MiffScreenStatus.READY || old.nameInput == old.name
            old.copy(
                status = MiffScreenStatus.READY,
                name = savedName,
                joinCode = snapshot.miff.joinCode,
                isOwner = owner,
                members = snapshot.members,
                places = snapshot.places,
                myUid = uid,
                sharing = snapshot.members.firstOrNull { it.uid == uid }?.sharingEnabled,
                nameInput = if (untouched) savedName else old.nameInput,
                fromCache = snapshot.fromCache,
            )
        }
    }

    fun onNameInputChanged(typed: String) {
        val state = mutableState.value
        if (!state.isOwner || state.saving) return
        val limited = MiffName.limit(typed)
        mutableState.update {
            it.copy(
                nameInput = limited.text,
                nameLimitReached = limited.limitReached,
                nameSaved = false,
                message = null,
            )
        }
    }

    fun onSaveName() {
        val state = mutableState.value
        if (!state.canSaveName) return
        if (!network.isOnline()) {
            mutableState.update { it.copy(message = ActionMessage.OFFLINE) }
            return
        }
        mutableState.update { it.copy(saving = true, message = null, nameSaved = false) }
        viewModelScope.launch {
            val result = repository.rename(miffId, state.joinCode, state.nameInput)
            when (result) {
                MiffWriteResult.Done -> {
                    mutableState.update { it.copy(saving = false, nameSaved = true) }
                    onNameChanged()
                }

                is MiffWriteResult.Failed -> {
                    mutableState.update { it.copy(saving = false, message = messageFor(result.failure)) }
                }
            }
        }
    }

    /** The row "Partager mon statut" was tapped: the question comes first (criteria K5 and K6). */
    fun onSharingRequested() {
        val state = mutableState.value
        val sharing = state.sharing ?: return
        if (!state.canWrite) return
        mutableState.update {
            it.copy(sharingDialog = if (sharing) SharingDialog.STOP else SharingDialog.RESUME, message = null)
        }
    }

    fun onSharingDialogDismissed() {
        if (mutableState.value.sharingBusy) return
        mutableState.update { it.copy(sharingDialog = null) }
    }

    fun onSharingConfirmed() {
        val state = mutableState.value
        val dialog = state.sharingDialog ?: return
        if (state.sharingBusy) return
        if (!network.isOnline()) {
            mutableState.update { it.copy(message = ActionMessage.OFFLINE) }
            return
        }
        val uid = currentUid() ?: return
        mutableState.update { it.copy(sharingBusy = true, message = null) }
        viewModelScope.launch {
            val result = repository.setSharing(miffId, uid, enabled = dialog == SharingDialog.RESUME)
            when (result) {
                // The switch only moves once the server said yes: the live card brings the new state (criterion H1).
                MiffWriteResult.Done -> {
                    mutableState.update { it.copy(sharingBusy = false, sharingDialog = null) }
                }

                is MiffWriteResult.Failed -> {
                    mutableState.update { it.copy(sharingBusy = false, message = messageFor(result.failure)) }
                }
            }
        }
    }
}
