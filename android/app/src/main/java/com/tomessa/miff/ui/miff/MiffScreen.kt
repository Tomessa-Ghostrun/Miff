package com.tomessa.miff.ui.miff

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DeletionState
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.components.RecentsPrivacy
import com.tomessa.miff.ui.components.miffScreenInsets
import com.tomessa.miff.ui.place.PlaceListUi
import com.tomessa.miff.ui.place.PlaceRows
import com.tomessa.miff.ui.place.PlacesExtras
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorBgCanvas
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextError
import com.tomessa.miff.ui.theme.textBodyRegular14

/**
 * The screen of a Miff (ticket Miff F for the creator, G for a member): the same sections, the same order, and only the creator
 * can change the name. Opened from the Profile tab (decision Q2 of the feed ticket). No status, no battery, no position and no
 * photo of an update is drawn here (criterion G7).
 *
 * MIFF-2: the creator has a "⋯" menu on the lines of the other loved ones ("Retirer de la Miff"), "Créer un nouveau code" under the
 * code, and "Supprimer la Miff" at the bottom; a member has "Quitter la Miff" at the bottom. Each opens a calm question first.
 * The names, the code and the photos of this screen are hidden from the list of recent apps (Android 13 and later).
 */
@Composable
fun MiffScreen(
    state: MiffScreenState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onNameChanged: (String) -> Unit,
    onSaveName: () -> Unit,
    onSharingRequested: () -> Unit,
    onSharingConfirmed: () -> Unit,
    onSharingDismissed: () -> Unit,
    onRemoveRequested: (uid: String) -> Unit = {},
    onRenewRequested: () -> Unit = {},
    onLeaveRequested: () -> Unit = {},
    onDeleteRequested: () -> Unit = {},
    onActionConfirmed: () -> Unit = {},
    onActionDismissed: () -> Unit = {},
    onInviteLater: () -> Unit = {},
    onDeletionRetry: () -> Unit = {},
    /** LOC-1: Miff+ or not (null while unknown), the name of the place just saved, and the two ways into the places. */
    placesExtras: PlacesExtras = PlacesExtras(),
) {
    RecentsPrivacy()
    val context = LocalContext.current
    val title = state.name.ifEmpty { stringResource(R.string.miff_screen_title_loading) }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colorBgCanvas)
                .miffScreenInsets()
                .verticalScroll(rememberScrollState())
                .semantics { paneTitle = title }
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        MiffHeader(title = title, onBack = onBack)
        when (state.status) {
            MiffScreenStatus.LOADING -> {
                MiffLoading()
            }

            // The single message of a lost access is drawn over everything by the host; nothing of the Miff stays here.
            MiffScreenStatus.ACCESS_LOST -> {
                Unit
            }

            MiffScreenStatus.UNAVAILABLE -> {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    MiffNotice(stringResource(R.string.miff_unavailable))
                    MiffSecondaryButton(
                        text = stringResource(R.string.auth_retry_action),
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            MiffScreenStatus.READY -> {
                MiffContent(
                    state = state,
                    onNameChanged = onNameChanged,
                    onSaveName = onSaveName,
                    onSharingRequested = onSharingRequested,
                    onRemoveRequested = onRemoveRequested,
                    onRenewRequested = onRenewRequested,
                    onLeaveRequested = onLeaveRequested,
                    onDeleteRequested = onDeleteRequested,
                    onInviteLater = onInviteLater,
                    placesExtras = placesExtras,
                )
            }
        }
    }
    state.sharingDialog?.let { dialog ->
        val stopping = dialog == SharingDialog.STOP
        MiffConfirmDialog(
            title =
                stringResource(
                    if (stopping) R.string.miff_sharing_stop_title else R.string.miff_sharing_resume_title,
                ),
            body = if (stopping) stringResource(R.string.miff_sharing_stop_body) else null,
            content =
                if (stopping) {
                    null
                } else {
                    {
                        // The same consent card and the same consent version as at the entry (criterion K6); only the opening sentence differs
                        // (miff_sharing_resume_body, "Tu partages à nouveau…", instead of miff_consent_create).
                        SharingConsentCard(
                            sentence = stringResource(R.string.miff_sharing_resume_body),
                            systemNote = stringResource(R.string.miff_consent_system_note_create),
                        )
                    }
                },
            confirmLabel =
                stringResource(
                    if (stopping) R.string.miff_sharing_stop_action else R.string.miff_sharing_resume_action,
                ),
            cancelLabel = stringResource(R.string.common_cancel),
            busy = state.sharingBusy,
            message = state.message,
            onConfirm = onSharingConfirmed,
            onCancel = onSharingDismissed,
        )
    }
    state.action?.let { action ->
        MiffActionDialog(
            action = action,
            state = state,
            onConfirm = onActionConfirmed,
            onCancel = onActionDismissed,
            onShareCode = { code -> MiffSharing.share(context, state.name, code) },
            onNotifyLovedOnes = { MiffSharing.shareClosingNotice(context, state.name) },
        )
    }
    state.deletion?.let { deletion ->
        MiffDeletionDialog(deletion = deletion, onClose = onBack, onRetry = onDeletionRetry)
    }
}

@Composable
private fun MiffContent(
    state: MiffScreenState,
    onNameChanged: (String) -> Unit,
    onSaveName: () -> Unit,
    onSharingRequested: () -> Unit,
    onRemoveRequested: (uid: String) -> Unit,
    onRenewRequested: () -> Unit,
    onLeaveRequested: () -> Unit,
    onDeleteRequested: () -> Unit,
    onInviteLater: () -> Unit,
    placesExtras: PlacesExtras,
) {
    val context = LocalContext.current
    val copiedText = stringResource(R.string.miff_code_copied)
    if (state.offline) MiffNotice(stringResource(R.string.miff_offline_notice))
    // The message of a dialog is shown in the dialog itself; the others (rename) are shown here.
    if (state.sharingDialog == null && state.action == null) state.message?.let { MiffNotice(actionMessageText(it)) }
    if (state.isOwner && state.inviteToRenew && state.action == null) {
        RenewInvitation(enabled = state.canWrite, onRenew = onRenewRequested, onLater = onInviteLater)
    }

    if (state.isOwner) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            MiffSectionHeader(R.drawable.ic_edit, stringResource(R.string.miff_section_name_title), null)
            NameField(
                name = state.nameInput,
                limitReached = state.nameLimitReached,
                enabled = !state.saving && !state.offline && !state.deletionActive,
                onNameChanged = onNameChanged,
                onDone = onSaveName,
            )
            MiffButton(
                text = stringResource(if (state.saving) R.string.miff_name_saving else R.string.miff_name_save_action),
                onClick = onSaveName,
                enabled = state.canSaveName,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.nameSaved) {
                Text(
                    text = stringResource(R.string.miff_name_saved),
                    style = textBodyRegular14,
                    color = colorTextDescription,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        MiffSectionHeader(
            R.drawable.ic_lock,
            stringResource(R.string.miff_section_code_title),
            stringResource(R.string.miff_section_code_description),
        )
        MiffCodeDisplay(code = state.joinCode)
        if (state.isFull) {
            Text(
                text = stringResource(R.string.miff_full_notice),
                style = textBodyRegular14,
                color = colorTextDescription,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else {
            MiffButton(
                text = stringResource(R.string.miff_share_code_action),
                onClick = { MiffSharing.share(context, state.name, state.joinCode) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MiffSecondaryButton(
            text = stringResource(R.string.miff_copy_code_action),
            onClick = {
                if (MiffSharing.copy(context, state.joinCode)) {
                    Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.isOwner) {
            MiffLinkButton(
                text = stringResource(R.string.miff_renew_link),
                onClick = onRenewRequested,
                enabled = state.canWrite,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.codeRenewed) {
                Text(
                    text = stringResource(R.string.miff_renew_done),
                    style = textBodyRegular14,
                    color = colorTextDescription,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        MiffSectionHeader(
            R.drawable.ic_group,
            stringResource(R.string.miff_section_members_title),
            stringResource(R.string.miff_section_members_description),
        )
        state.members.forEach { member ->
            val isMe = member.uid == state.myUid
            MiffMemberRow(
                member = member,
                isMe = isMe,
                // The creator's menu, on the OTHER lines only: nobody removes themselves, and a member has no menu.
                onRemove =
                    if (state.isOwner && !isMe && member.role != MiffRole.OWNER) {
                        { onRemoveRequested(member.uid) }
                    } else {
                        null
                    },
                menuEnabled = state.canWrite,
            )
        }
        if (state.members.size <= 1) {
            Text(
                text = stringResource(R.string.miff_members_alone),
                style = textBodyRegular14,
                color = colorTextDescription,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
            )
        }
    }

    state.sharing?.let { sharing ->
        MiffSwitchRow(
            title = stringResource(R.string.miff_sharing_title),
            description =
                stringResource(
                    if (sharing) R.string.miff_sharing_on_description else R.string.miff_sharing_off_description,
                ),
            checked = sharing,
            enabled = state.canWrite,
            stateText =
                stringResource(
                    if (sharing) R.string.miff_sharing_state_on else R.string.miff_sharing_state_off,
                ),
            onClick = onSharingRequested,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        MiffSectionHeader(
            R.drawable.ic_place,
            stringResource(R.string.miff_section_places_title),
            stringResource(R.string.miff_section_places_description),
        )
        PlaceRows(
            ui = PlaceListUi(state.places, state.isOwner, placesExtras.premium, placesExtras.savedName),
            onOpen = placesExtras.onOpenPlace,
            onAdd = {
                placesExtras.onAddPlace(
                    PlaceListUi(state.places, state.isOwner, placesExtras.premium).atFreeLimit,
                )
            },
        )
    }
    MiffFoot(state = state, onLeaveRequested = onLeaveRequested, onDeleteRequested = onDeleteRequested)
    Spacer(Modifier.size(Spacing.l))
}

/**
 * The bottom of the screen. The creator has no "Quitter" (decision QM1): a sentence says what they can do instead, and
 * "Supprimer la Miff" is a quiet link in the colour of errors (no red fill, no alarm). A member reads that they can leave at any time.
 */
@Composable
private fun MiffFoot(
    state: MiffScreenState,
    onLeaveRequested: () -> Unit,
    onDeleteRequested: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = stringResource(if (state.isOwner) R.string.miff_owner_foot else R.string.miff_leave_hint),
            style = textBodyRegular14,
            color = colorTextDescription,
            textAlign = TextAlign.Center,
        )
        if (state.isOwner) {
            MiffLinkButton(
                text = stringResource(R.string.miff_delete_link),
                onClick = onDeleteRequested,
                enabled = state.canWrite,
                color = colorTextError,
            )
        } else {
            MiffLinkButton(
                text = stringResource(R.string.miff_leave_link),
                onClick = onLeaveRequested,
                enabled = state.canWrite,
                color = colorTextError,
            )
        }
    }
}

/** "Un proche a quitté la Miff. Tu veux créer un nouveau code ? C'est facultatif." with "Plus tard" (shown once per departure). */
@Composable
private fun RenewInvitation(
    enabled: Boolean,
    onRenew: () -> Unit,
    onLater: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        MiffNotice(stringResource(R.string.miff_renew_invite))
        MiffButton(
            text = stringResource(R.string.miff_renew_action),
            onClick = onRenew,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        MiffLinkButton(
            text = stringResource(R.string.miff_renew_invite_later),
            onClick = onLater,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The dialogs of MIFF-2 (ticket screens H, I, J, K): one model, calm wording, "Annuler" always available while nothing is sent. */
@Composable
private fun MiffActionDialog(
    action: MiffAction,
    state: MiffScreenState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onShareCode: (String) -> Unit,
    onNotifyLovedOnes: () -> Unit,
) {
    val cancel = stringResource(R.string.common_cancel)
    when (action) {
        is MiffAction.Remove -> {
            MiffConfirmDialog(
                title = stringResource(R.string.miff_remove_title, action.firstName),
                body = stringResource(R.string.miff_remove_body, action.firstName),
                confirmLabel = stringResource(R.string.miff_remove_action),
                cancelLabel = cancel,
                tone = DialogTone.SENSITIVE,
                busy = state.actionBusy,
                message = state.message,
                onConfirm = onConfirm,
                onCancel = onCancel,
            )
        }

        MiffAction.Renew -> {
            MiffConfirmDialog(
                title = stringResource(R.string.miff_renew_title),
                body = stringResource(R.string.miff_renew_body),
                confirmLabel = stringResource(R.string.miff_renew_action),
                cancelLabel = cancel,
                busy = state.actionBusy,
                message = state.message,
                onConfirm = onConfirm,
                onCancel = onCancel,
            )
        }

        MiffAction.Leave -> {
            MiffConfirmDialog(
                title = stringResource(R.string.miff_leave_title, state.name),
                body = stringResource(R.string.miff_leave_body),
                confirmLabel = stringResource(R.string.miff_leave_action),
                cancelLabel = cancel,
                tone = DialogTone.SENSITIVE,
                busy = state.actionBusy,
                message = state.message,
                onConfirm = onConfirm,
                onCancel = onCancel,
            )
        }

        is MiffAction.Delete -> {
            val others = action.othersCount > 0
            MiffConfirmDialog(
                title = stringResource(R.string.miff_delete_title, state.name),
                body =
                    if (others) {
                        pluralStringResource(R.plurals.miff_delete_body_others, action.othersCount, action.othersCount)
                    } else {
                        stringResource(R.string.miff_delete_body_alone)
                    },
                confirmLabel = stringResource(R.string.miff_delete_action),
                cancelLabel = cancel,
                tone = DialogTone.SENSITIVE,
                busy = state.actionBusy,
                message = state.message,
                onConfirm = onConfirm,
                onCancel = onCancel,
                // Only when there are other loved ones to tell. It PREPARES a message that the creator sends themselves.
                extraLabel = if (others) stringResource(R.string.miff_delete_notify_action) else null,
                extraHint = if (others) stringResource(R.string.miff_delete_notify_hint) else null,
                onExtra = if (others) onNotifyLovedOnes else null,
            )
        }

        is MiffAction.RemovedDone -> {
            MiffConfirmDialog(
                title = stringResource(R.string.miff_remove_done_title, action.firstName),
                body = stringResource(R.string.miff_remove_done_body),
                confirmLabel = stringResource(R.string.miff_remove_done_close),
                cancelLabel = cancel,
                onConfirm = onConfirm,
                onCancel = onCancel,
                showCancel = false,
                content = { MiffCodeDisplay(code = action.newCode) },
                extraLabel = stringResource(R.string.miff_share_code_action),
                onExtra = { onShareCode(action.newCode) },
            )
        }
    }
}

/**
 * The deletion under way (J3, J4): a calm sentence and a progress circle, no count of what is left, no "Annuler" (the procedure
 * can be resumed, never half-undone). "Retour" only closes the screen: the deletion goes on in the app (criterion AND1).
 */
@Composable
private fun MiffDeletionDialog(
    deletion: DeletionState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
) {
    val close = stringResource(R.string.miff_delete_close)
    when (deletion) {
        is DeletionState.Running, DeletionState.WaitingForNetwork -> {
            MiffProgressDialog(
                title = stringResource(R.string.miff_delete_progress),
                body =
                    if (deletion ==
                        DeletionState.WaitingForNetwork
                    ) {
                        stringResource(R.string.miff_delete_paused)
                    } else {
                        null
                    },
                onDismiss = onClose,
            )
        }

        DeletionState.Stuck -> {
            MiffConfirmDialog(
                title = stringResource(R.string.miff_delete_progress),
                body = stringResource(R.string.miff_message_generic),
                confirmLabel = stringResource(R.string.auth_retry_action),
                cancelLabel = close,
                onConfirm = onRetry,
                onCancel = onClose,
            )
        }

        DeletionState.Done -> {
            MiffConfirmDialog(
                title = stringResource(R.string.miff_delete_done),
                body = null,
                confirmLabel = close,
                cancelLabel = close,
                onConfirm = onClose,
                onCancel = onClose,
                showCancel = false,
            )
        }
    }
}
