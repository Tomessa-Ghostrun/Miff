package com.tomessa.miff.ui.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.tomessa.miff.R
import com.tomessa.miff.domain.auth.AuthForm
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textTitleBold20

/**
 * "Mot de passe oublié" (mockup "Welcoming screen -10", ticket section 3 C). Stateless: [state] comes from
 * [PasswordResetViewModel]. One field (the address of the account), one button; after the send, ONE confirmation that is
 * the same words for every address (no enumeration of accounts, criterion M2), then the button pauses for about a minute
 * (M5) and becomes "Renvoyer l'e-mail". The new password is chosen on the web page opened by the e-mail, not here.
 *
 * [initialEmail] is the address already typed on the sign-in screen, if any (less typing). Only the e-mail survives a
 * rotation (`rememberSaveable`); there is no password on this screen.
 */
@Composable
fun PasswordResetScreen(
    state: PasswordResetUiState,
    initialEmail: String,
    onBack: () -> Unit,
    onEmailChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onBackToSignIn: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var emailTouched by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    // Tells the view model the address now and when the screen appears (pre-filled address, restored after a rotation).
    LaunchedEffect(email) { onEmailChanged(email) }

    val showEmailError =
        ((emailTouched || state.showFieldError) && emailFieldError(email)) || state.emailRejected
    val canSend = email.isNotBlank() && !state.isLoading && !state.resendLocked
    val send = {
        focus.clearFocus() // hides the keyboard so that the confirmation is in view
        onSubmit(email)
    }

    AuthScaffold(paneTitle = stringResource(R.string.auth_reset_title), onBack = onBack) {
        ScreenTitle(title = stringResource(R.string.auth_reset_title), style = textTitleBold20)
        Text(
            text = stringResource(R.string.auth_reset_intro),
            style = textBodyRegular14,
            color = colorTextForm,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(Spacing.xl))

        AuthTextField(
            value = email,
            onValueChange = { email = AuthForm.limitEmailInput(it) },
            label = stringResource(R.string.auth_email_label),
            errorText = if (showEmailError) stringResource(R.string.auth_error_email_format) else null,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Send,
            onImeAction = { if (canSend) send() },
            outlined = true,
            onBlur = { emailTouched = true },
        )
        Spacer(Modifier.size(Spacing.l))

        if (state.sent) {
            // The same words whether or not the address has an account (ticket 7.3): never a hint either way.
            MessageText(
                text = stringResource(R.string.auth_reset_sent),
                isError = false,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(Spacing.s))
        }
        if (state.resendLocked) {
            Text(
                text = stringResource(R.string.auth_reset_resend_wait),
                style = textBodyRegular14,
                color = colorTextDescription,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(Spacing.s))
        }
        AuthMessageArea(state.message)

        MiffButton(
            text =
                stringResource(
                    if (state.sent) R.string.auth_reset_resend_action else R.string.auth_reset_action,
                ),
            onClick = send,
            enabled = canSend,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.isLoading) LoadingIndicator()
        if (state.sent) {
            UnderlinedLink(text = stringResource(R.string.auth_reset_back_to_signin), onClick = onBackToSignIn)
        }
    }
}
