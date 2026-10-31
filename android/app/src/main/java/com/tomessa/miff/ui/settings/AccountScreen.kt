package com.tomessa.miff.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tomessa.miff.R
import com.tomessa.miff.domain.auth.AccountSignIn
import com.tomessa.miff.ui.auth.MessageText
import com.tomessa.miff.ui.auth.text
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorBgPlaceholder
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14

/**
 * Settings > Mon compte (mockup "reglages -4", ticket ONB-3a/3b, criteria B1 and B2). The e-mail address is shown and cannot
 * be changed (no change of e-mail in this version). An e-mail account can ask for the reset e-mail ("Changer mon mot de
 * passe", decision Q5: no form); a Google account only sees how it signs in. "Se déconnecter" is a SECONDARY button (the mockup made it as prominent
 * as the main action) and asks for a confirmation with two buttons of the same weight. No "Supprimer mon compte" line: it
 * arrives with the account deletion ticket (AUTH-5), never with the text of the mockup.
 */
@Composable
fun AccountScreen(
    state: AccountUiState,
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onSignOutRequested: () -> Unit,
    onSignOutConfirmed: () -> Unit,
    onSignOutCancelled: () -> Unit,
) {
    SettingsScaffold(title = stringResource(R.string.settings_account_title), onBack = onBack) {
        state.email?.let { email -> ReadOnlyEmail(email) }
        Spacer(Modifier.size(Spacing.l))
        when (state.signIn) {
            AccountSignIn.PASSWORD -> PasswordBlock(state, onChangePassword)
            AccountSignIn.GOOGLE -> SignedInWithGoogle()
            AccountSignIn.OTHER -> Unit
        }
        MiffSecondaryButton(
            text = stringResource(R.string.settings_account_sign_out_action),
            onClick = onSignOutRequested,
            enabled = !state.isSigningOut,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (state.confirmingSignOut) {
        AlertDialog(
            onDismissRequest = onSignOutCancelled,
            title = { Text(stringResource(R.string.settings_account_sign_out_title), style = textBodyBold16) },
            text = { Text(stringResource(R.string.settings_account_sign_out_body), style = textBodyRegular) },
            confirmButton = {
                TextButton(onClick = onSignOutConfirmed) {
                    Text(stringResource(R.string.settings_account_sign_out_action), style = textBodyBold16)
                }
            },
            dismissButton = {
                TextButton(onClick = onSignOutCancelled) {
                    Text(stringResource(R.string.settings_account_sign_out_cancel), style = textBodyBold16)
                }
            },
            containerColor = colorBgSurface,
        )
    }
}

/**
 * The address, in a box that does not look like a field you can type in (grey, no border colour): read by the screen reader
 * as ONE sentence that says it cannot be changed here. Never copied anywhere else (criterion RG8).
 */
@Composable
private fun ReadOnlyEmail(email: String) {
    val description = stringResource(R.string.settings_account_email_a11y, email)
    Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        Text(
            text = stringResource(R.string.settings_account_email_label),
            style = textBodyRegular,
            color = colorTextDescription,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Spacer(Modifier.size(Spacing.xs))
        Text(
            text = email,
            style = textBodyRegular,
            color = colorTextForm,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.minTouchTarget)
                    .clip(ShapeMd)
                    .background(colorBgPlaceholder.copy(alpha = EMAIL_BOX_ALPHA))
                    .border(StrokeHairline, colorBorderDefault, ShapeMd)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s)
                    .clearAndSetSemantics {},
        )
    }
}

private const val EMAIL_BOX_ALPHA = 0.35f

/** A Google account has no password in Miff: the sentence says how the person signs in, and nothing is offered. */
@Composable
private fun SignedInWithGoogle() {
    Text(
        text = stringResource(R.string.settings_account_google),
        style = textBodyRegular,
        color = colorTextDescription,
        modifier = Modifier.padding(bottom = Spacing.l),
    )
}

/**
 * "Changer mon mot de passe" (decision Q5): a button that sends the reset e-mail, no form. After the send, one calm
 * sentence (live region for the screen reader) and a paused "Renvoyer l'e-mail" button.
 */
@Composable
private fun PasswordBlock(
    state: AccountUiState,
    onChangePassword: () -> Unit,
) {
    val sent = state.passwordStatus == PasswordEmailStatus.SENT
    val sending = state.passwordStatus == PasswordEmailStatus.SENDING
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.l)) {
        if (!sent) {
            Text(
                text = stringResource(R.string.settings_account_password_help),
                style = textBodyRegular14,
                color = colorTextDescription,
            )
            Spacer(Modifier.size(Spacing.s))
        }
        MiffSecondaryButton(
            text =
                stringResource(
                    when {
                        sending -> R.string.settings_account_password_sending
                        sent -> R.string.auth_reset_resend_action
                        else -> R.string.settings_account_password_action
                    },
                ),
            onClick = onChangePassword,
            enabled = !sending && !state.passwordLocked,
            modifier = Modifier.fillMaxWidth(),
        )
        if (sent) {
            MessageText(
                text = stringResource(R.string.settings_account_password_sent),
                isError = false,
                modifier = Modifier.padding(top = Spacing.s),
            )
            if (state.passwordLocked) {
                Text(
                    text = stringResource(R.string.auth_reset_resend_wait),
                    style = textBodyRegular14,
                    color = colorTextDescription,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
        state.passwordMessage?.let { message ->
            MessageText(
                text = message.text(),
                isError = true,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
    }
}
