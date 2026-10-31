package com.tomessa.miff.ui.miff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.miff.CodeChangeResult
import com.tomessa.miff.data.miff.DeletionController
import com.tomessa.miff.data.miff.DeletionState
import com.tomessa.miff.data.miff.MiffRepository
import com.tomessa.miff.data.miff.MiffWriteResult
import com.tomessa.miff.domain.miff.MemberDepartures
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffName
import com.tomessa.miff.domain.miff.MiffObservation
import com.tomessa.miff.domain.miff.MiffPlace
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.miff.MiffSharingConsent
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * SECURITY, for this file: the name, the code, the names and photos of the members are personal data / a secret. They live in
 * memory in this state only: never in a log, an analytics event (none is sent from this screen) or a crash report. The NEW code
 * handed back by a removal or a renewal is held here for the dialog that shows it, and nowhere else.
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

/** The question (or the answer) of the dialogs of MIFF-2: remove, renew, leave, delete (ticket screens H, I, J, K). */
sealed interface MiffAction {
    /** "Retirer {prénom} de la Miff ?" (creator). */
    class Remove(
        val uid: String,
        val firstName: String,
    ) : MiffAction {
        override fun toString(): String = "Remove(redacted)"
    }

    /** "Créer un nouveau code ?" (creator). */
    data object Renew : MiffAction

    /** "Quitter la Miff « X » ?" (a member; never the creator). */
    data object Leave : MiffAction

    /** "Supprimer la Miff « X » ?" (creator), with the number of OTHER members for the sentence. */
    data class Delete(
        val othersCount: Int,
    ) : MiffAction

    /** After a removal: the person is out and this is the NEW code, to share with whoever the creator wants. */
    class RemovedDone(
        val firstName: String,
        val newCode: String,
    ) : MiffAction {
        override fun toString(): String = "RemovedDone(redacted)"
    }
}

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
    /** The dialog of MIFF-2 that is open, if any. */
    val action: MiffAction? = null,
    /** The lot of that dialog is on its way to the server: nothing else can be pressed (one tap = one operation). */
    val actionBusy: Boolean = false,
    /** Where the deletion of THIS Miff stands (null = none under way). */
    val deletion: DeletionState? = null,
    /** "Un proche a quitté la Miff. Tu veux créer un nouveau code ?" (creator, once per departure). */
    val inviteToRenew: Boolean = false,
    /** "Nouveau code créé." until the person does something else. */
    val codeRenewed: Boolean = false,
    /** The person left this Miff: the screen closes (the Miff is no longer theirs to look at). */
    val departed: Boolean = false,
) {
    /** "Enregistrer" is active only when the name changed AND is valid (criterion G3). */
    val canSaveName: Boolean
        get() =
            isOwner && !saving && !offline && !deletionActive && MiffName.isValid(nameInput) &&
                MiffName.stored(nameInput) != name

    /** At 15 members or more "Partager le code" is replaced by a calm sentence (decision QM5, a soft ceiling). */
    val isFull: Boolean get() = members.size >= MiffSharingConsent.SOFT_MEMBER_LIMIT

    /** The deletion is under way (or finished): nothing else is written on this Miff. */
    val deletionActive: Boolean get() = deletion != null

    val canWrite: Boolean get() = !offline && !saving && !sharingBusy && !actionBusy && !deletionActive

    /** The other members, in the order of the list (the creator's own line is never one of them). */
    val others: List<MiffMember> get() = members.filter { it.uid != myUid }

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
        action: MiffAction? = this.action,
        actionBusy: Boolean = this.actionBusy,
        deletion: DeletionState? = this.deletion,
        inviteToRenew: Boolean = this.inviteToRenew,
        codeRenewed: Boolean = this.codeRenewed,
        departed: Boolean = this.departed,
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
        action,
        actionBusy,
        deletion,
        inviteToRenew,
        codeRenewed,
        departed,
    )
}

/**
 * The screen of a Miff, for its creator (F) and for a member (G): name (the creator can change it), code to share, the loved
 * ones, "Partager mon statut" (stop / resume at any time, ticket K5 and K6), the places (a list). No status, no battery, no
 * position, no photo of an update appears here (criterion G7).
 *
 * MIFF-2 adds the actions that change who is in the Miff: the creator removes a loved one (the code is renewed by the same lot),
 * renews the code, or deletes the Miff; a member leaves it. Each is a question first, then ONE lot confirmed by the server: with
 * no network the button is grey and nothing is queued (criteria H1, H5).
 *
 * Reading is live (three listeners, see FirestoreMiffObserver). A loss of access is handed to [onAccessLost]; a network cut is not
 * one (criterion D3). A departure (leaving, deleting) the person chose is never announced to them as a loss.
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
    private val deletions: DeletionController,
    /** After a voluntary "Quitter" the server confirmed: no message for it, purge of the cache, refresh of the list of Miffs. */
    private val onLeft: suspend (miffId: String) -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MiffScreenState(miffId = miffId, myUid = currentUid().orEmpty()))
    val uiState: StateFlow<MiffScreenState> = mutableState.asStateFlow()

    private var lostReported = false

    /** True from the first tap of "Quitter" until the answer: a refusal heard in between is the leaving itself. */
    private var leaving = false

    /** People this creator removed from this screen: their disappearance is not a "départ" worth an invitation. */
    private val removedByMe = mutableSetOf<String>()

    /** The last list of members seen, to find who left (null until a first picture exists). */
    private var previousMembers: List<MiffMember>? = null

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
            launch {
                deletions.states.collect { states -> mutableState.update { it.copy(deletion = states[miffId]) } }
            }
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
                // A Miff the person is leaving or deleting vanishes by their own doing: it is not a loss (nothing is announced).
                if (lostReported || leaving || mutableState.value.departed || mutableState.value.deletionActive) return
                lostReported = true
                val name = mutableState.value.name.takeIf { it.isNotEmpty() }
                // Nothing of the Miff stays on the screen while the server is asked (criterion D7).
                mutableState.value = MiffScreenState(status = MiffScreenStatus.ACCESS_LOST, miffId = miffId)
                if (!onAccessLost(miffId, name)) {
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
        val owner = snapshot.miff.ownerUid == uid
        // Who left since the last picture: an invitation to renew the code, for the creator only, and never while the Miff is
        // being deleted (the members disappear one by one then, by the creator's own doing).
        val departures = MemberDepartures.between(previousMembers, snapshot.members, uid, removedByMe)
        previousMembers = snapshot.members
        mutableState.update { old ->
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
                inviteToRenew = old.inviteToRenew || (owner && departures.isNotEmpty() && !old.deletionActive),
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

    // --- MIFF-2: remove, renew, leave, delete -----------------------------------------------------------------------------

    /** "⋯" > "Retirer de la Miff" on the line of [uid] (creator only; never on the creator's own line). */
    fun onRemoveRequested(uid: String) {
        val state = mutableState.value
        if (!state.isOwner || !state.canWrite || state.action != null) return
        val member = state.members.firstOrNull { it.uid == uid } ?: return
        if (member.uid == state.myUid || member.role == MiffRole.OWNER) return
        mutableState.update { it.copy(action = MiffAction.Remove(member.uid, member.firstName), message = null) }
    }

    /** "Créer un nouveau code" (creator only). */
    fun onRenewRequested() {
        val state = mutableState.value
        if (!state.isOwner || !state.canWrite || state.action != null) return
        mutableState.update { it.copy(action = MiffAction.Renew, message = null, codeRenewed = false) }
    }

    /** "Quitter la Miff" (a member only: the creator has no way out but deleting, decision QM1). */
    fun onLeaveRequested() {
        val state = mutableState.value
        if (state.isOwner || !state.canWrite || state.action != null) return
        mutableState.update { it.copy(action = MiffAction.Leave, message = null) }
    }

    /** "Supprimer la Miff" (creator only). */
    fun onDeleteRequested() {
        val state = mutableState.value
        if (!state.isOwner || !state.canWrite || state.action != null) return
        mutableState.update { it.copy(action = MiffAction.Delete(state.others.size), message = null) }
    }

    /** "Annuler", the back key, or "Terminer" after a removal: closes the dialog, writes nothing. */
    fun onActionDismissed() {
        if (mutableState.value.actionBusy) return
        mutableState.update { it.copy(action = null, message = null) }
    }

    /** "Plus tard" on the invitation: it is closed and not shown again for this departure. */
    fun onInviteLater() {
        mutableState.update { it.copy(inviteToRenew = false) }
    }

    fun onActionConfirmed() {
        val state = mutableState.value
        val action = state.action ?: return
        if (state.actionBusy) return
        when (action) {
            is MiffAction.RemovedDone -> onActionDismissed()
            is MiffAction.Remove -> confirmRemove(action)
            MiffAction.Renew -> confirmRenew()
            MiffAction.Leave -> confirmLeave()
            is MiffAction.Delete -> confirmDelete()
        }
    }

    /** True when the phone has a network; otherwise the dialog says so calmly and nothing is sent (criterion H1). */
    private fun requireNetwork(): Boolean {
        if (network.isOnline()) return true
        mutableState.update { it.copy(message = ActionMessage.OFFLINE) }
        return false
    }

    private fun confirmRemove(action: MiffAction.Remove) {
        if (!requireNetwork()) return
        mutableState.update { it.copy(actionBusy = true, message = null) }
        // Noted BEFORE the lot: the listener can hear the disappearance before the answer arrives, and it is not a "départ".
        removedByMe += action.uid
        viewModelScope.launch {
            when (val result = repository.removeMember(miffId, action.uid)) {
                is CodeChangeResult.Changed -> {
                    mutableState.update {
                        it.copy(
                            actionBusy = false,
                            action = MiffAction.RemovedDone(action.firstName, result.newCode),
                            inviteToRenew = false,
                        )
                    }
                }

                is CodeChangeResult.Failed -> {
                    removedByMe -= action.uid
                    mutableState.update { it.copy(actionBusy = false, message = messageFor(result.failure)) }
                }
            }
        }
    }

    private fun confirmRenew() {
        if (!requireNetwork()) return
        mutableState.update { it.copy(actionBusy = true, message = null) }
        viewModelScope.launch {
            when (val result = repository.renewCode(miffId)) {
                is CodeChangeResult.Changed -> {
                    // The new code reaches the screen through the live view (and is read again, never taken from here).
                    mutableState.update {
                        it.copy(actionBusy = false, action = null, inviteToRenew = false, codeRenewed = true)
                    }
                }

                is CodeChangeResult.Failed -> {
                    mutableState.update { it.copy(actionBusy = false, message = messageFor(result.failure)) }
                }
            }
        }
    }

    private fun confirmLeave() {
        if (!requireNetwork()) return
        val uid = currentUid() ?: return
        mutableState.update { it.copy(actionBusy = true, message = null) }
        leaving = true
        viewModelScope.launch {
            when (val result = repository.leave(uid, miffId)) {
                MiffWriteResult.Done -> {
                    // The purge must finish even if the screen closes meanwhile (the view model is then cleared).
                    withContext(NonCancellable) { onLeft(miffId) }
                    mutableState.update { it.copy(actionBusy = false, action = null, departed = true) }
                }

                is MiffWriteResult.Failed -> {
                    leaving = false
                    mutableState.update { it.copy(actionBusy = false, message = messageFor(result.failure)) }
                    // A refusal heard while leaving was ignored: the listeners are attached again to see where things stand.
                    restarts.update { it + 1 }
                }
            }
        }
    }

    private fun confirmDelete() {
        if (!requireNetwork()) return
        mutableState.update { it.copy(action = null, message = null, inviteToRenew = false) }
        // The deletion goes on in the app (not in this screen): leaving the screen does not stop it.
        deletions.start(miffId)
    }

    /** "Réessayer" after a deletion that stopped. */
    fun onDeletionRetry() {
        if (mutableState.value.deletion == DeletionState.Stuck) deletions.retry(miffId)
    }
}
