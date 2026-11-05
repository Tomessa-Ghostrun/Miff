package com.tomessa.miff.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.R
import com.tomessa.miff.domain.auth.AuthForm
import com.tomessa.miff.ui.auth.AuthMessage
import com.tomessa.miff.ui.auth.AuthTextField
import com.tomessa.miff.ui.auth.FieldLink
import com.tomessa.miff.ui.auth.GoogleButton
import com.tomessa.miff.ui.auth.LoadingIndicator
import com.tomessa.miff.ui.auth.MessageText
import com.tomessa.miff.ui.auth.OrText
import com.tomessa.miff.ui.auth.text
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.MiffDestructiveButton
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.miff.MiffNotice
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.ShapeSm
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeAccent
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint10
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextError
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyLarge
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textTitleBold20

/*
 * Screens A (information), B (confirmation dialog) and C (re-authentication) of the deletion of the account (ticket AUTH-5, mockups
 * design/screens/suppression-compte/, PNG 01 to 06). Nothing here writes anything: "Annuler", the back arrow and the system back all
 * leave with nothing changed (criterion SC4). The destructive action is a red OUTLINE on white, same weight as the safe choice, and
 * the meaning is in the words (decision of the PM). Tutoiement; no alarming word (criterion SC5).
 */

/** The provisional contact of CLAUDE.md (`BuildConfig.SUPPORT_EMAIL`): shown in the app, to be replaced before the external beta (Q11). */
private val contact: String get() = BuildConfig.SUPPORT_EMAIL

/**
 * A sentence with the contact address inside it: the address is bold and blue, and the whole line opens the e-mail application
 * (one 48 dp target; nothing is sent by itself). [format] is a string resource with ONE placeholder for the address.
 */
@Composable
private fun ContactSentence(
    format: Int,
    onContact: () -> Unit,
    @DrawableRes icon: Int?,
    modifier: Modifier = Modifier,
) {
    val full = stringResource(format, contact)
    val styled =
        buildAnnotatedString {
            val at = full.indexOf(contact)
            if (at < 0) {
                append(full)
            } else {
                append(full.substring(0, at))
                withStyle(SpanStyle(color = colorActionPrimary, fontWeight = FontWeight.Bold)) { append(contact) }
                append(full.substring(at + contact.length))
            }
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .clickable(role = Role.Button, onClick = onContact)
                .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colorTextForm,
                modifier = Modifier.padding(top = 1.dp).size(Spacing.l),
            )
        }
        Text(text = styled, style = textBodyRegular, color = colorTextDescription)
    }
}

/**
 * "Supprimer mon compte": the same discreet red TEXT link everywhere the person can start the deletion (Mon compte, "Créer ou rejoindre",
 * the screen shown when the account cannot be read). Never a filled button, always next to or under "Se déconnecter". It only opens the
 * information screen (A): nothing is written from here. Apple 5.1.1(v): the deletion must be reachable inside the app.
 */
@Composable
fun DeleteAccountLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Spacing.minTouchTarget),
    ) {
        Text(
            text = stringResource(R.string.settings_delete_entry),
            style = textBodyLarge,
            color = colorTextError,
        )
    }
}

/** A white card with a pale blue icon tile and one sentence. */
@Composable
private fun InfoCard(
    @DrawableRes icon: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorBgSurface)
                .padding(Spacing.m)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.s + Spacing.xs),
    ) {
        IconTile(icon)
        Text(text = text, style = textBodyRegular, color = colorTextForm, modifier = Modifier.weight(1f))
    }
}

/** The pale blue tile that holds an icon (information cards, the lock of the re-authentication). */
@Composable
internal fun IconTile(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(40.dp).clip(ShapeSm).background(colorActionPrimaryTint20),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null, // decorative: the sentence next to it says everything
            tint = colorActionPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** An information box (pale blue, never red): "Ce n'est pas réversible…", the Apple limit. The first sentence is bold when [boldLead]. */
@Composable
private fun InfoBox(
    text: String,
    boldLead: Boolean,
    modifier: Modifier = Modifier,
) {
    val styled: AnnotatedString =
        buildAnnotatedString {
            val end = text.indexOf('.')
            if (boldLead && end >= 0) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(0, end + 1)) }
                append(text.substring(end + 1))
            } else {
                append(text)
            }
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorActionPrimaryTint10)
                .border(StrokeHairline, colorActionPrimaryTint20, ShapeMd)
                .padding(Spacing.m)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.s + Spacing.xs),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_loc_info),
            contentDescription = null,
            tint = colorActionPrimary,
            modifier = Modifier.padding(top = 1.dp).size(Spacing.l),
        )
        Text(text = styled, style = textBodyRegular, color = colorTextForm)
    }
}

/**
 * A. "Supprimer mon compte" (information). Reads the Miffs of the account on the SERVER and never shows an incomplete list (SC2): while
 * loading, offline or on a failed read, "Continuer" is greyed and nothing is lost. In this version only an account WITHOUT Miff goes on
 * (AUTH-5a); a Miff gives the calm "bientôt" state and nothing is touched (the deletion with Miffs is AUTH-5b).
 */
@Composable
fun DeleteAccountInfoScreen(
    state: DeleteInfoUiState,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onContact: () -> Unit,
    onKeep: () -> Unit,
    onConfirm: () -> Unit,
) {
    SettingsScaffold(
        title = stringResource(R.string.settings_delete_title),
        onBack = onBack,
        footer = {
            MiffButton(
                text = stringResource(R.string.settings_delete_continue),
                onClick = onContinue,
                enabled = state.canContinue,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = Spacing.minTouchTarget),
            ) {
                Text(
                    text = stringResource(R.string.settings_delete_cancel),
                    style = textBodyBold16,
                    color = colorActionPrimary,
                )
            }
        },
    ) {
        when (state.inventory) {
            DeleteInventory.LOADING -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    LoadingIndicator(Modifier.size(Spacing.l))
                    MessageText(text = stringResource(R.string.settings_delete_loading), isError = false)
                }
            }

            DeleteInventory.OFFLINE -> {
                MiffNotice(text = stringResource(R.string.settings_delete_offline_list))
            }

            DeleteInventory.READ_FAILED -> {
                MiffNotice(text = stringResource(R.string.settings_delete_read_failed))
                Spacer(Modifier.size(Spacing.s))
                MiffSecondaryButton(
                    text = stringResource(R.string.settings_delete_retry),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            DeleteInventory.HAS_MIFFS -> {
                // Calm "coming soon": nothing was touched, nothing will be (AUTH-5b does the Miffs).
                ContactSentence(
                    format = R.string.settings_delete_miffs_soon,
                    onContact = onContact,
                    icon = R.drawable.ic_loc_info,
                )
            }

            DeleteInventory.NO_MIFFS -> {
                Text(
                    text = stringResource(R.string.settings_delete_intro),
                    style = textBodyRegular,
                    color = colorTextForm,
                )
                Spacer(Modifier.size(Spacing.m))
                InfoCard(icon = R.drawable.ic_person, text = stringResource(R.string.settings_delete_you_nomiff))
                Spacer(Modifier.size(Spacing.s))
                ContactSentence(
                    format = R.string.settings_delete_copy,
                    onContact = onContact,
                    icon = R.drawable.ic_mail,
                )
                if (state.appleLinked) {
                    Spacer(Modifier.size(Spacing.s))
                    InfoBox(text = stringResource(R.string.settings_delete_apple_android_info), boldLead = false)
                }
                Spacer(Modifier.size(Spacing.s))
                InfoBox(text = stringResource(R.string.settings_delete_irreversible), boldLead = true)
            }
        }
    }
    if (state.confirming) {
        DeleteAccountConfirmDialog(offline = state.confirmOffline, onKeep = onKeep, onConfirm = onConfirm)
    }
}

/**
 * B. The confirmation dialog: two buttons of the same size and the same weight, "Supprimer mon compte" in red outline on white and
 * "Garder mon compte" in blue outline; the first focus goes to "Garder mon compte" (the safe choice) and the system back is "Garder".
 */
@Composable
fun DeleteAccountConfirmDialog(
    offline: Boolean,
    onKeep: () -> Unit,
    onConfirm: () -> Unit,
) {
    val keepFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { keepFocus.requestFocus() }
    Dialog(
        onDismissRequest = onKeep,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = DialogMaxWidth)
                    .fillMaxWidth()
                    .miffShadow(ShadowDefault, ShapeMd)
                    .clip(ShapeMd)
                    .background(colorBgSurface)
                    .border(StrokeAccent, colorBorderAction, ShapeMd)
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.l)
                    .semantics { isTraversalGroup = true },
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                text = stringResource(R.string.settings_delete_confirm_title),
                style = textTitleBold20,
                color = colorTextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.settings_delete_confirm_body),
                style = textBodyRegular,
                color = colorTextDescription,
            )
            if (offline) MiffNotice(text = stringResource(R.string.settings_delete_confirm_offline))
            Spacer(Modifier.size(Spacing.xs))
            // Reading order: the question, "Supprimer", then "Garder"; the first FOCUS is on "Garder" (see above).
            MiffDestructiveButton(
                text = stringResource(R.string.settings_delete_confirm_ok),
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().semantics { traversalIndex = 1f },
            )
            MiffSecondaryButton(
                text = stringResource(R.string.settings_delete_confirm_keep),
                onClick = onKeep,
                modifier = Modifier.fillMaxWidth().focusRequester(keepFocus).semantics { traversalIndex = 2f },
            )
        }
    }
}

private val DialogMaxWidth = 320.dp

/**
 * C. "Une dernière vérification" (and the same screen as "Dernière vérification avant de terminer" when [DeleteReauthUiState.last]).
 * Full screen, not a window: everything stays visible with the keyboard. Only the methods really linked to the account are offered
 * (password, Google); no Apple button on Android. Nothing is written before Firebase accepts. The password lives in this
 * composable's field only, and is handed to [onSubmitPassword] once.
 */
@Composable
fun DeleteAccountReauthScreen(
    state: DeleteReauthUiState,
    onBack: () -> Unit,
    onSubmitPassword: (String) -> Unit,
    onGoogle: () -> Unit,
    onForgotPassword: (() -> Unit)?,
    onCancelOrLater: () -> Unit,
    onContact: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    val canSubmit = password.isNotEmpty() && !state.busy
    val confirmLabel =
        if (state.busy) R.string.settings_delete_reauth_checking else R.string.settings_delete_reauth_confirm
    val secondaryLabel = if (state.last) R.string.settings_delete_reauth_later else R.string.settings_delete_cancel
    SettingsScaffold(
        title = stringResource(R.string.settings_delete_reauth_title),
        onBack = onBack,
        footer = {
            if (state.hasPassword) {
                MiffButton(
                    text = stringResource(confirmLabel),
                    onClick = { onSubmitPassword(password) },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.settings_delete_reauth_note),
                    style = textBodyRegular14,
                    color = colorTextDescription,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.last) {
                // The point of no return is behind us: no "Annuler", only "Plus tard" (the deletion resumes at the next launch).
                Text(
                    text = stringResource(R.string.settings_delete_reauth_later_hint),
                    style = textBodyRegular14,
                    color = colorTextDescription,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                )
            }
            TextButton(
                onClick = onCancelOrLater,
                enabled = !state.busy,
                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = Spacing.minTouchTarget),
            ) {
                Text(
                    text = stringResource(secondaryLabel),
                    style = textBodyBold16,
                    color = colorActionPrimary,
                )
            }
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s + Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            IconTile(R.drawable.ic_lock)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (state.last) {
                    Text(
                        text = stringResource(R.string.settings_delete_reauth_last),
                        style = textBodyRegular,
                        color = colorTextForm,
                    )
                    Text(
                        text = stringResource(R.string.settings_delete_reauth_last_sub),
                        style = textBodyRegular14,
                        color = colorTextDescription,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.settings_delete_reauth_body),
                        style = textBodyRegular,
                        color = colorTextForm,
                    )
                }
            }
        }
        Spacer(Modifier.size(Spacing.m))
        state.email?.let { email ->
            Text(
                text = stringResource(R.string.settings_delete_reauth_account, email),
                style = textBodyRegular14,
                color = colorTextDescription,
            )
            Spacer(Modifier.size(Spacing.s))
        }
        if (state.noMethod) {
            ContactSentence(
                format = R.string.settings_delete_reauth_unavailable,
                onContact = onContact,
                icon = R.drawable.ic_loc_info,
            )
        }
        if (state.hasGoogle) {
            Text(
                text = stringResource(R.string.settings_delete_reauth_google),
                style = textBodyRegular,
                color = colorTextForm,
            )
            Spacer(Modifier.size(Spacing.s))
            GoogleButton(onClick = onGoogle, enabled = !state.busy)
            if (state.hasPassword) OrText()
        }
        if (state.hasPassword) {
            AuthTextField(
                value = password,
                onValueChange = { password = AuthForm.limitPasswordInput(it) },
                label = stringResource(R.string.auth_password_label),
                errorText = if (state.message == AuthMessage.REAUTH_PASSWORD_WRONG) state.message.text() else null,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                onImeAction = { if (canSubmit) onSubmitPassword(password) },
                isPassword = true,
                outlined = true,
            )
            if (onForgotPassword != null) {
                FieldLink(
                    text = stringResource(R.string.auth_signin_forgot_password),
                    onClick = onForgotPassword,
                    enabled = !state.busy,
                )
            }
        }
        // Every other cause (wrong Google account, too many tries, no network, expired session...) in one calm place; nothing technical.
        val general = state.message?.takeUnless { it == AuthMessage.REAUTH_PASSWORD_WRONG }
        if (general != null) {
            Spacer(Modifier.size(Spacing.s))
            MessageText(text = general.text(), isError = true)
        }
    }
}
