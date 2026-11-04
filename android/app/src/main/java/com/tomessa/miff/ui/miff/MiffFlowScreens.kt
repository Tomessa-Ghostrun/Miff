package com.tomessa.miff.ui.miff

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.tomessa.miff.R
import com.tomessa.miff.domain.miff.MiffName
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.components.RecentsPrivacy
import com.tomessa.miff.ui.components.miffScreenInsets
import com.tomessa.miff.ui.theme.GradientAuthBackground
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyCaption
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textDisplayHeadline

/*
 * The screens of the creation and entry of a Miff (ticket Miff A to E, L; mockups design/exports/miff and onboarding-original).
 * They are state-in, events-out: the view models decide, these only draw (and give a name to every part for the screen reader).
 */

/** The frame of the flow: auth gradient, system bars respected, scrolls when the content is taller than the screen. */
@Composable
fun MiffFrame(
    title: String?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    background: Brush = GradientAuthBackground,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(background)
                .miffScreenInsets()
                .verticalScroll(rememberScrollState())
                .semantics { if (title != null) paneTitle = title }
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (title != null) MiffHeader(title = title, onBack = onBack)
        content()
    }
}

// --- A. Choice -------------------------------------------------------------------------------------------------------

/**
 * "Choisis comment tu veux commencer" (mockup onboarding-original -3): two big cards, "Créer une nouvelle Miff" and "Rejoindre une
 * Miff existante". The code field of the original card moved to its own page (one single field for the code, ticket 4 #9).
 * No back arrow: the account was just created (decision Q11 of the sign-up ticket); a quiet "Se déconnecter" is the way out.
 */
@Composable
fun MiffChoiceScreen(
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onSignOut: () -> Unit,
) {
    val pane = stringResource(R.string.miff_choice_title)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GradientAuthBackground)
                .miffScreenInsets()
                .verticalScroll(rememberScrollState())
                .semantics { paneTitle = pane }
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Spacer(Modifier.size(Spacing.s))
        Image(
            painter = painterResource(R.drawable.miff_wordmark_color),
            contentDescription = stringResource(R.string.home_logo_a11y),
            modifier = Modifier.width(Spacing.logoWidth / 2),
        )
        Text(
            text = pane,
            style = textDisplayHeadline,
            color = colorTextForm,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = Spacing.m).semantics { heading() },
        )
        ChoiceCard(
            icon = R.drawable.ic_add,
            title = stringResource(R.string.miff_choice_create_title),
            body = stringResource(R.string.miff_choice_create_body),
            onClick = onCreate,
        )
        ChoiceCard(
            icon = R.drawable.ic_group,
            title = stringResource(R.string.miff_choice_join_title),
            body = stringResource(R.string.miff_choice_join_body),
            onClick = onJoin,
        )
        TextButton(onClick = onSignOut, modifier = Modifier.heightIn(min = Spacing.minTouchTarget)) {
            Text(stringResource(R.string.auth_signout_action), style = textBodyBold16, color = colorActionPrimary)
        }
    }
}

@Composable
private fun ChoiceCard(
    icon: Int,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .miffShadow(ShadowDefault, ShapeMd)
                .clip(ShapeMd)
                .background(colorActionPrimaryTint20)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(Spacing.m)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(Spacing.minTouchTarget + Spacing.s).clip(CircleShape).background(colorBgSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painter = painterResource(icon), contentDescription = null, tint = colorActionPrimary)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = title, style = textBodyBold16, color = colorActionPrimary)
            Text(text = body, style = textBodyRegular14, color = colorTextForm)
        }
    }
}

// --- B and C. Create -------------------------------------------------------------------------------------------------

/** The explicit consent: the sentence of the PM, what the members see of the person, the note about the phone's permission. */
@Composable
fun SharingConsentCard(
    sentence: String,
    systemNote: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorBgSurface)
                .border(StrokeHairline, colorBorderAction, ShapeMd)
                .padding(Spacing.m)
                .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(text = sentence, style = textBodyRegular14, color = colorTextPrimary)
        Text(
            text = stringResource(R.string.miff_consent_members_see_title),
            style = textBodyBold14,
            color = colorTextPrimary,
        )
        Text(
            text = stringResource(R.string.miff_consent_members_see_body),
            style = textBodyRegular14,
            color = colorTextPrimary,
        )
        Text(
            text = stringResource(R.string.miff_consent_places),
            style = textBodyRegular14,
            color = colorTextPrimary,
        )
        Text(text = systemNote, style = textBodyCaption, color = colorTextDescription)
    }
}

/** B ("Nouvelle Miff": one field, the consent, one button) then C ("Ta Miff est prête": the code, share, copy, continue). */
@Composable
fun CreateMiffScreen(
    state: CreateUiState,
    onBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onContinue: () -> Unit,
) {
    RecentsPrivacy()
    when (state.step) {
        CreateStep.NAME -> CreateNameStep(state, onBack, onNameChanged, onSubmit)
        CreateStep.READY -> CreateReadyStep(state, onContinue)
    }
}

@Composable
private fun CreateNameStep(
    state: CreateUiState,
    onBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    // Back is a way out only before the Miff is being written (one tap = one operation).
    BackHandler(enabled = state.saving) {}
    MiffFrame(title = stringResource(R.string.miff_create_title), onBack = if (state.saving) null else onBack) {
        NameField(
            name = state.name,
            limitReached = state.limitReached,
            enabled = !state.saving,
            onNameChanged = onNameChanged,
            onDone = onSubmit,
        )
        SharingConsentCard(
            sentence = stringResource(R.string.miff_consent_create),
            systemNote = stringResource(R.string.miff_consent_system_note_create),
        )
        state.message?.let { MiffNotice(text = actionMessageText(it)) }
        MiffButton(
            text = stringResource(if (state.saving) R.string.miff_create_saving else R.string.miff_create_action),
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The name field with the help under it, the limit message and the counter (from 30 characters). */
@Composable
fun NameField(
    name: String,
    limitReached: Boolean,
    enabled: Boolean,
    onNameChanged: (String) -> Unit,
    onDone: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        MiffTextField(
            value = name,
            onValueChange = onNameChanged,
            label = stringResource(R.string.miff_name_label),
            placeholder = stringResource(R.string.miff_name_placeholder),
            supportingText =
                if (limitReached) {
                    stringResource(R.string.miff_name_limit)
                } else {
                    stringResource(R.string.miff_name_help)
                },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        if (name.length >= COUNTER_FROM) {
            Text(
                text = stringResource(R.string.miff_name_counter, name.length, MiffName.MAX_LENGTH),
                style = textBodyCaption,
                color = colorTextDescription,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The counter "34 / 40" appears from this length (design fiche `miff-creation.md`). */
private const val COUNTER_FROM = 30

@Composable
private fun CreateReadyStep(
    state: CreateUiState,
    onContinue: () -> Unit,
) {
    val created = state.created
    // The Miff exists: the system "back" does nothing here, "Continuer" is the way on (ticket design fiche, annex Android).
    BackHandler {}
    val context = LocalContext.current
    val copiedText = stringResource(R.string.miff_code_copied)
    MiffFrame(title = null, onBack = null) {
        Spacer(Modifier.size(Spacing.s))
        Image(
            painter = painterResource(R.drawable.miff_wordmark_color),
            contentDescription = stringResource(R.string.home_logo_a11y),
            modifier = Modifier.width(Spacing.logoWidth / 2).align(Alignment.CenterHorizontally),
        )
        if (created != null) {
            Text(
                text = stringResource(R.string.miff_ready_title, created.name),
                style = textDisplayHeadline,
                color = colorTextForm,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            Text(
                text = stringResource(R.string.miff_ready_body),
                style = textBodyRegular,
                color = colorTextDescription,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            MiffCodeDisplay(code = created.joinCode, large = true)
            MiffButton(
                text = stringResource(R.string.miff_share_code_action),
                onClick = { MiffSharing.share(context, created.name, created.joinCode) },
                modifier = Modifier.fillMaxWidth(),
            )
            MiffSecondaryButton(
                text = stringResource(R.string.miff_copy_code_action),
                onClick = {
                    if (MiffSharing.copy(context, created.joinCode)) {
                        Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.miff_ready_hint),
                style = textBodyCaption,
                color = colorTextDescription,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MiffButton(
            text = stringResource(R.string.miff_ready_continue_action),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// --- D and E. Join ---------------------------------------------------------------------------------------------------

@Composable
fun JoinMiffScreen(
    state: JoinUiState,
    onBack: () -> Unit,
    onCodeChanged: (String) -> Unit,
    onContinue: () -> Unit,
    onConfirm: () -> Unit,
    onCancelConfirm: () -> Unit,
    onOpenMiff: (String) -> Unit,
) {
    RecentsPrivacy()
    when (state.step) {
        JoinStep.CODE -> JoinCodeStep(state, onBack, onCodeChanged, onContinue, onOpenMiff)
        JoinStep.CONFIRM -> JoinConfirmStep(state, onConfirm, onCancelConfirm)
    }
}

@Composable
private fun JoinCodeStep(
    state: JoinUiState,
    onBack: () -> Unit,
    onCodeChanged: (String) -> Unit,
    onContinue: () -> Unit,
    onOpenMiff: (String) -> Unit,
) {
    val context = LocalContext.current
    val error =
        when {
            state.foreignCharacter -> stringResource(R.string.miff_join_foreign)
            state.problem == JoinProblem.NOT_FOUND -> stringResource(R.string.miff_join_not_found)
            else -> null
        }
    MiffFrame(title = stringResource(R.string.miff_join_title), onBack = if (state.checking) null else onBack) {
        Text(
            text = stringResource(R.string.miff_join_help),
            style = textBodyRegular,
            color = colorTextDescription,
        )
        MiffCodeField(
            code = state.code,
            onCodeChange = onCodeChanged,
            label = stringResource(R.string.miff_join_code_label),
            placeholder = stringResource(R.string.miff_join_code_hint),
            errorText = error,
            enabled = !state.checking,
            onDone = onContinue,
        )
        // Never read by itself: the clipboard is only looked at when the person taps this button (criterion J3).
        TextButton(
            onClick = { MiffSharing.pastedText(context)?.let(onCodeChanged) },
            enabled = !state.checking,
            modifier = Modifier.align(Alignment.End).heightIn(min = Spacing.minTouchTarget),
        ) {
            Text(stringResource(R.string.miff_join_paste_action), style = textBodyBold16, color = colorActionPrimary)
        }
        when (state.problem) {
            JoinProblem.PAUSED -> MiffNotice(stringResource(R.string.miff_join_paused))
            JoinProblem.OFFLINE -> MiffNotice(actionMessageText(ActionMessage.OFFLINE))
            JoinProblem.GENERIC -> MiffNotice(actionMessageText(ActionMessage.GENERIC))
            JoinProblem.ONE_MIFF -> MiffNotice(actionMessageText(ActionMessage.ONE_MIFF))
            else -> Unit
        }
        state.alreadyMember?.let { member ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(ShapeMd)
                        .background(colorBgSurface)
                        .border(StrokeHairline, colorBorderDefault, ShapeMd)
                        .padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(
                    text = stringResource(R.string.miff_join_already_member, member.miffName),
                    style = textBodyRegular,
                    color = colorTextPrimary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                MiffButton(
                    text = stringResource(R.string.miff_join_open_action),
                    onClick = { onOpenMiff(member.miffId) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        MiffButton(
            text =
                stringResource(
                    if (state.checking) R.string.miff_join_checking else R.string.miff_join_continue_action,
                ),
            onClick = onContinue,
            enabled = state.canContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * E: "Rejoindre la Miff « X » ?" with the consent in full view BEFORE the button: nothing is hidden, folded, or reachable only by
 * scrolling on a normal phone. "Annuler" writes nothing.
 */
@Composable
private fun JoinConfirmStep(
    state: JoinUiState,
    onConfirm: () -> Unit,
    onCancelConfirm: () -> Unit,
) {
    BackHandler(enabled = !state.joining, onBack = onCancelConfirm)
    BackHandler(enabled = state.joining) {}
    val title = stringResource(R.string.miff_join_confirm_title, state.miffName.orEmpty())
    MiffFrame(title = null, onBack = null) {
        Spacer(Modifier.size(Spacing.s))
        Text(
            text = title,
            style = textDisplayHeadline,
            color = colorTextForm,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        SharingConsentCard(
            sentence = stringResource(R.string.miff_consent_join),
            systemNote = stringResource(R.string.miff_consent_system_note_join),
        )
        state.confirmMessage?.let { MiffNotice(text = actionMessageText(it)) }
        MiffButton(
            text = stringResource(if (state.joining) R.string.miff_join_joining else R.string.miff_join_action),
            onClick = onConfirm,
            enabled = !state.joining,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = onCancelConfirm,
            enabled = !state.joining,
            modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = Spacing.minTouchTarget),
        ) {
            Text(stringResource(R.string.common_cancel), style = textBodyBold16, color = colorActionPrimary)
        }
    }
}

// --- L. Access lost --------------------------------------------------------------------------------------------------

/**
 * The one message when a Miff cannot be reached any more (decision QM8): the same for a removal and for a deletion, calm, no
 * cause, no reproach. Shown once, then the app goes on with another Miff or the empty Feed.
 */
@Composable
fun AccessLostScreen(
    miffName: String?,
    onAcknowledge: () -> Unit,
) {
    RecentsPrivacy()
    val title =
        if (miffName != null) {
            stringResource(R.string.miff_lost_title, miffName)
        } else {
            stringResource(R.string.miff_lost_title_unknown)
        }
    BackHandler(onBack = onAcknowledge)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GradientAuthBackground)
                .miffScreenInsets()
                .verticalScroll(rememberScrollState())
                .semantics {
                    paneTitle = title
                    liveRegion = LiveRegionMode.Polite
                }.padding(horizontal = Spacing.l, vertical = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
    ) {
        Image(
            painter = painterResource(R.drawable.miff_wordmark_color),
            contentDescription = stringResource(R.string.home_logo_a11y),
            modifier = Modifier.width(Spacing.logoWidth / 2),
        )
        Text(
            text = title,
            style = textDisplayHeadline,
            color = colorTextForm,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.miff_lost_body),
            style = textBodyRegular,
            color = colorTextDescription,
            textAlign = TextAlign.Center,
        )
        MiffButton(
            text = stringResource(R.string.miff_lost_ok_action),
            onClick = onAcknowledge,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Shared by the screens that wait for something. */
@Composable
internal fun MiffLoading(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.auth_loading_a11y)
    Box(modifier = modifier.fillMaxWidth().padding(Spacing.l), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = colorActionPrimary,
            modifier =
                Modifier.size(Spacing.xl).semantics {
                    contentDescription = loading
                    liveRegion = LiveRegionMode.Polite
                },
        )
    }
}
