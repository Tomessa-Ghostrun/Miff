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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.tomessa.miff.R
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.components.miffScreenInsets
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorBgCanvas
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.textBodyRegular14

/**
 * The screen of a Miff (ticket Miff F for the creator, G for a member): the same sections, the same order, and only the creator
 * can change the name. Opened from the Profile tab (decision Q2 of the feed ticket). No status, no battery, no position and no
 * photo of an update is drawn here (criterion G7); the actions that retire, leave, delete or renew are MIFF-2.
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
) {
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
                MiffContent(state, onNameChanged, onSaveName, onSharingRequested)
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
}

@Composable
private fun MiffContent(
    state: MiffScreenState,
    onNameChanged: (String) -> Unit,
    onSaveName: () -> Unit,
    onSharingRequested: () -> Unit,
) {
    val context = LocalContext.current
    val copiedText = stringResource(R.string.miff_code_copied)
    if (state.offline) MiffNotice(stringResource(R.string.miff_offline_notice))
    // The message of a dialog is shown in the dialog itself; the others (rename) are shown here.
    if (state.sharingDialog == null) state.message?.let { MiffNotice(actionMessageText(it)) }

    if (state.isOwner) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            MiffSectionHeader(R.drawable.ic_edit, stringResource(R.string.miff_section_name_title), null)
            NameField(
                name = state.nameInput,
                limitReached = state.nameLimitReached,
                enabled = !state.saving && !state.offline,
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
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        MiffSectionHeader(
            R.drawable.ic_group,
            stringResource(R.string.miff_section_members_title),
            stringResource(R.string.miff_section_members_description),
        )
        state.members.forEach { member -> MiffMemberRow(member = member, isMe = member.uid == state.myUid) }
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
        if (state.places.isEmpty()) {
            Text(
                text = stringResource(R.string.miff_places_empty),
                style = textBodyRegular14,
                color = colorTextDescription,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
            )
        } else {
            state.places.forEach { place ->
                Text(
                    text = place.name,
                    style = textBodyRegular14,
                    color = colorTextDescription,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.xs),
                )
            }
        }
    }
    Spacer(Modifier.size(Spacing.l))
}
