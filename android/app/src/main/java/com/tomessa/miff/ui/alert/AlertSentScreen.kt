package com.tomessa.miff.ui.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomessa.miff.R
import com.tomessa.miff.domain.alert.AlertDialTarget
import com.tomessa.miff.domain.alert.AlertRules
import com.tomessa.miff.domain.alert.EmergencyLabel
import com.tomessa.miff.domain.alert.EmergencyNumber
import com.tomessa.miff.ui.components.RecentsPrivacy
import com.tomessa.miff.ui.components.miffTopInsets
import com.tomessa.miff.ui.feed.BubbleShape
import com.tomessa.miff.ui.miff.MiffConfirmDialog
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorAlertFill
import com.tomessa.miff.ui.theme.colorBgCanvas
import com.tomessa.miff.ui.theme.colorBgPlaceholder
import com.tomessa.miff.ui.theme.colorOnAlert
import com.tomessa.miff.ui.theme.colorStatusDangerTint
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyCaption
import com.tomessa.miff.ui.theme.textBodyLarge
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textDisplayHeadline

/*
 * The screen "Tes proches de la Miff {nom} viennent d'être avertis" (maquettes 03 and 06, docs/tickets/alerte-danger.md 7.3 and 8): where the person lands at the 5 seconds. A pink
 * blob with the sentence, the list of who to call (the people of THAT Miff, then the emergency services), the big red "Appeler". The call goes through the DIALLER of the system
 * (the caller of [onCall] uses ACTION_DIAL): Miff never calls by itself.
 *
 * HONEST: the title says "viennent d'être avertis" ONLY once the server accepted the alert; before, it says "Envoi de l'alerte à la Miff {nom}…", and without a network "Pas de
 * réseau pour le moment. Ton alerte partira dès que possible." The calls work in every case (they do not go through Miff). Never "il va répondre", never a delay promised.
 */

const val ALERT_SENT_TITLE_TAG = "alert_sent_title"
const val ALERT_SENT_CALL_TAG = "alert_sent_call"
const val ALERT_SENT_PICKER_TAG = "alert_sent_picker"
const val ALERT_SENT_EMERGENCY_LINK_TAG = "alert_sent_emergency_link"
const val ALERT_SENT_CANCEL_TAG = "alert_sent_cancel"
const val ALERT_SENT_RETRY_TAG = "alert_sent_retry"

/** What the screen says in its blob. */
enum class SentPhase {
    /** Written on its way: "Envoi de l'alerte à la Miff {nom}…". */
    SENDING,

    /** No network: "Pas de réseau pour le moment. Ton alerte partira dès que possible." */
    WAITING,

    /** More than 15 minutes without leaving: the question "Veux-tu toujours envoyer ton alerte ?". */
    NEEDS_CONFIRMATION,

    /** The server said no and the session is fine: a calm sentence and "Réessayer". */
    REFUSED,

    /** No alert can be sent now (no Miff known yet, no camera): a calm sentence and the emergency numbers, nothing else. */
    UNAVAILABLE,

    /** The daily limit of 3 alerts is reached: a calm sentence, the emergency services selected, no retry. */
    QUOTA,

    /** The server accepted it: "Tes proches de la Miff {nom} viennent d'être avertis". */
    SENT,

    /** I do not share with that Miff (any more): "Tu as arrêté de partager avec la Miff {nom} : reprends le partage…". The alert did NOT leave. Numbers stay. */
    SHARING_STOPPED,
}

class AlertSentActions(
    val onBack: () -> Unit = {},
    val onSelect: (AlertDialTarget) -> Unit = {},
    val onCall: (AlertDialTarget) -> Unit = {},
    val onShowEmergency: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onCancelPending: () -> Unit = {},
    val onConfirmStale: () -> Unit = {},
    val onResumeSharing: () -> Unit = {},
)

@Composable
fun AlertSentScreen(
    phase: SentPhase,
    miffName: String?,
    /** The person has several Miffs: "Seule cette Miff est prévenue." */
    several: Boolean,
    call: CallState,
    actions: AlertSentActions,
    modifier: Modifier = Modifier,
) {
    // Like the Feed, the Miff and the Profile: the screen is hidden in the recent apps (it names a Miff and says that the person declared herself in danger).
    RecentsPrivacy()
    val pane = stringResource(R.string.alert_sent_pane_a11y)
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colorBgCanvas)
                .miffTopInsets()
                .semantics { paneTitle = pane },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            IconButton(onClick = actions.onBack, modifier = Modifier.size(Spacing.minTouchTarget)) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.alert_back_a11y),
                    tint = colorTextPrimary,
                )
            }
            TitleBlob(phase, miffName)
            if (phase == SentPhase.SHARING_STOPPED) {
                TextButton(
                    onClick = actions.onResumeSharing,
                    modifier = Modifier.heightIn(min = Spacing.minTouchTarget).align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = stringResource(R.string.alert_stopped_resume),
                        style = textBodyBold16,
                        color = colorActionPrimary,
                    )
                }
            }
            when (call) {
                is CallState.Ready -> {
                    CallPicker(
                        call,
                        menuOpen,
                        onOpen = { menuOpen = true },
                        onClose = { menuOpen = false },
                        actions = actions,
                    )
                    if (call.contactsUnavailable) {
                        Text(
                            text = stringResource(R.string.alert_contacts_unavailable),
                            style = textBodyRegular14,
                            color = colorTextDescription,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    CallButton(call.selected, onClick = { actions.onCall(call.selected) })
                    // Two touches saved in the stress: the same list, on the section of the emergency services (ticket 8.5, a deviation from maquette 03, removable).
                    TextButton(
                        onClick = {
                            actions.onShowEmergency()
                            menuOpen = true
                        },
                        modifier =
                            Modifier
                                .heightIn(
                                    min = Spacing.minTouchTarget,
                                ).align(Alignment.CenterHorizontally)
                                .testTag(ALERT_SENT_EMERGENCY_LINK_TAG),
                    ) {
                        Text(
                            text = stringResource(R.string.alert_call_emergency_link),
                            style = textBodyBold16,
                            color = colorActionPrimary,
                        )
                    }
                }
            }
            if (phase == SentPhase.WAITING || phase == SentPhase.REFUSED || phase == SentPhase.QUOTA ||
                phase == SentPhase.NEEDS_CONFIRMATION
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (phase == SentPhase.REFUSED) {
                        TextButton(
                            onClick = actions.onRetry,
                            modifier = Modifier.heightIn(min = Spacing.minTouchTarget).testTag(ALERT_SENT_RETRY_TAG),
                        ) {
                            Text(
                                text = stringResource(R.string.alert_refused_retry),
                                style = textBodyBold16,
                                color = colorActionPrimary,
                            )
                        }
                    }
                    TextButton(
                        onClick = actions.onCancelPending,
                        modifier = Modifier.heightIn(min = Spacing.minTouchTarget).testTag(ALERT_SENT_CANCEL_TAG),
                    ) {
                        Text(
                            text = stringResource(R.string.alert_cancel_pending),
                            style = textBodyBold16,
                            color = colorActionPrimary,
                        )
                    }
                }
            }
            Notes(phase, several)
        }
    }
    if (phase == SentPhase.NEEDS_CONFIRMATION) {
        MiffConfirmDialog(
            title = stringResource(R.string.alert_stale_title),
            body = stringResource(R.string.alert_stale_body),
            confirmLabel = stringResource(R.string.alert_stale_send),
            cancelLabel = stringResource(R.string.alert_stale_cancel),
            onConfirm = actions.onConfirmStale,
            onCancel = actions.onCancelPending,
        )
    }
}

/** The pink blob with the sentence of the phase (maquettes 03 and 06). */
@Composable
private fun TitleBlob(
    phase: SentPhase,
    miffName: String?,
) {
    val full =
        when (phase) {
            SentPhase.SENDING, SentPhase.NEEDS_CONFIRMATION -> {
                miffName?.let { stringResource(R.string.alert_sending_title, it) }
                    ?: stringResource(R.string.alert_sending_title_no_name)
            }

            SentPhase.WAITING -> {
                stringResource(R.string.alert_waiting_title)
            }

            SentPhase.REFUSED -> {
                stringResource(R.string.alert_refused_title)
            }

            SentPhase.QUOTA -> {
                pluralStringResource(
                    R.plurals.alert_quota_title,
                    AlertRules.MAX_STARTS_PER_DAY,
                    AlertRules.MAX_STARTS_PER_DAY,
                )
            }

            SentPhase.UNAVAILABLE -> {
                stringResource(R.string.alert_unavailable_title)
            }

            SentPhase.SENT -> {
                miffName?.let { stringResource(R.string.alert_sent_title, it) }
                    ?: stringResource(R.string.alert_sent_title_no_name)
            }

            SentPhase.SHARING_STOPPED -> {
                miffName?.let { stringResource(R.string.alert_stopped_title, it) }
                    ?: stringResource(R.string.alert_stopped_title_no_name)
            }
        }
    // The screen takes the focus of the screen reader at once, on its title: nothing of the Feed behind is read before the sentence and "Appeler" (review of #275, iOS review d).
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 176.dp)
                .clip(BubbleShape)
                .background(colorStatusDangerTint)
                .focusRequester(focus)
                .focusable()
                .testTag(ALERT_SENT_TITLE_TAG)
                // The screen reader reads the WHOLE sentence, whatever is cut or shrunk on the screen (ticket 7.3, AD10b).
                .semantics(mergeDescendants = true) {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = full
                }.padding(horizontal = 40.dp, vertical = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        val name =
            miffName?.takeIf {
                phase == SentPhase.SENT || phase == SentPhase.SENDING ||
                    phase == SentPhase.SHARING_STOPPED
            }
        val at = name?.let { full.indexOf(it) } ?: -1
        if (name != null && at >= 0) {
            // The name of the Miff on its OWN line, in bold, smaller when it is long; a very long word breaks by letters, never overflows the blob (ticket 7.3).
            val before = full.substring(0, at).trim()
            val after = full.substring(at + name.length).trim()
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics {}) {
                if (before.isNotEmpty()) BlobText(before)
                Text(
                    text = name,
                    style =
                        HEADLINE.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = nameSize(name.length),
                            lineHeight =
                                (
                                    nameSize(name.length).value *
                                        1.25f
                                ).sp,
                        ),
                    color = colorTextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (after.isNotEmpty()) BlobText(after)
            }
        } else {
            BlobText(full, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

private val HEADLINE = textDisplayHeadline.copy(lineBreak = LineBreak.Heading)

private fun nameSize(length: Int) =
    when {
        length <= 12 -> 26.sp
        length <= 24 -> 22.sp
        else -> 18.sp
    }

@Composable
private fun BlobText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text = text, style = HEADLINE, color = colorTextPrimary, textAlign = TextAlign.Center, modifier = modifier)
}

/** The grey box with a chevron and the name (maquette 03): a touch opens the list in two sections, "Tes proches" then "Services d'urgence". */
@Composable
private fun CallPicker(
    call: CallState.Ready,
    open: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    actions: AlertSentActions,
) {
    val pick = stringResource(R.string.alert_picker_a11y)
    val selectedText = targetText(call.selected)
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colorBgPlaceholder)
                    .clickable(role = Role.Button, onClick = onOpen)
                    .padding(horizontal = Spacing.m)
                    .testTag(ALERT_SENT_PICKER_TAG)
                    // A change of the choice (the creator replacing 112 when the people arrive) is announced politely, never silently.
                    .semantics {
                        contentDescription = "$pick, $selectedText"
                        liveRegion = LiveRegionMode.Polite
                    },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_drop_down),
                contentDescription = null,
                tint = colorTextPrimary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = selectedText,
                style = textBodyBold16.copy(fontSize = 20.sp),
                color = colorTextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = onClose) {
            if (call.list.people.isNotEmpty()) {
                SectionTitle(stringResource(R.string.alert_section_people))
                call.list.people.forEach { person ->
                    val target = AlertDialTarget.Person(person)
                    DropdownMenuItem(
                        text = { Text(text = person.name, style = textBodyRegular) },
                        onClick = {
                            actions.onSelect(target)
                            onClose()
                        },
                    )
                }
                HorizontalDivider()
            }
            SectionTitle(stringResource(R.string.alert_section_emergency))
            call.list.emergency.forEach { entry ->
                val target = AlertDialTarget.Emergency(entry)
                DropdownMenuItem(
                    text = { Text(text = emergencyText(entry), style = textBodyRegular) },
                    onClick = {
                        actions.onSelect(target)
                        onClose()
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = textBodyCaption,
        color = colorTextDescription,
        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs).semantics { heading() },
    )
}

/** The big red button of the maquette: "Appeler" (and who, for the screen reader). */
@Composable
private fun CallButton(
    target: AlertDialTarget,
    onClick: () -> Unit,
) {
    val spoken = stringResource(R.string.alert_call_a11y, targetText(target))
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colorAlertFill)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Spacing.m)
                .testTag(ALERT_SENT_CALL_TAG)
                .semantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_feed_call),
            contentDescription = null,
            tint = colorOnAlert,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = stringResource(R.string.alert_call_action),
            style = textBodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
            color = colorOnAlert,
            modifier = Modifier.padding(start = Spacing.s),
        )
    }
}

@Composable
private fun Notes(
    phase: SentPhase,
    several: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (phase == SentPhase.SENT) {
            Text(
                text = stringResource(R.string.alert_delivery_note),
                style = textBodyCaption,
                color = colorTextDescription,
                textAlign = TextAlign.Center,
            )
            if (several) {
                Text(
                    text = stringResource(R.string.alert_only_this_miff),
                    style = textBodyCaption,
                    color = colorTextDescription,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // The honest mention, always there (AD18): calm, never alarming.
        Text(
            text = stringResource(R.string.alert_not_emergency_note),
            style = textBodyCaption,
            color = colorTextDescription,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun targetText(target: AlertDialTarget): String =
    when (target) {
        is AlertDialTarget.Person -> target.contact.name
        is AlertDialTarget.Emergency -> emergencyText(target.entry)
    }

/** "Police secours (17)": the label of the catalogue of texts and the number. */
@Composable
fun emergencyText(entry: EmergencyNumber): String {
    val label =
        stringResource(
            when (entry.label) {
                EmergencyLabel.POLICE -> R.string.alert_emergency_police
                EmergencyLabel.AMBULANCE -> R.string.alert_emergency_ambulance
                EmergencyLabel.FIRE -> R.string.alert_emergency_fire
                EmergencyLabel.EUROPEAN_112 -> R.string.alert_emergency_european
                EmergencyLabel.INTERNATIONAL_112 -> R.string.alert_emergency_international
            },
        )
    return stringResource(R.string.alert_emergency_line, label, entry.number)
}
