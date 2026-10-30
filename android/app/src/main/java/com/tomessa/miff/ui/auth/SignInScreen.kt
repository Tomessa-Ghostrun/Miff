package com.tomessa.miff.ui.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.tomessa.miff.R
import com.tomessa.miff.domain.auth.AuthForm
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.theme.Spacing

/**
 * Sign-in screen (ticket section 3 B, criteria C1 to C9). No consent box here: an existing account has none to give,
 * and a new person arriving through Google gets the consent panel afterwards (ticket 7.8).
 *
 * "Mot de passe oublié ?" (mockup -8) sits under the password field and opens the reset screen with the address already typed.
 * Only the e-mail survives a rotation; the password is never saved.
 */
@Composable
fun SignInScreen(
    state: SignInUiState,
    onBack: (() -> Unit)?,
    onSubmitEmail: (email: String, password: String) -> Unit,
    onGoogle: () -> Unit,
    onGoToSignUp: () -> Unit,
    onForgotPassword: (email: String) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailTouched by remember { mutableStateOf(false) }

    val showEmailError = (emailTouched || state.showFieldErrors) && emailFieldError(email)

    AuthScaffold(paneTitle = stringResource(R.string.auth_signin_title), onBack = onBack) {
        GoogleButton(onClick = onGoogle, enabled = !state.isLoading)
        OrText()

        if (state.showAccountNotCreatedNote) {
            // A reassurance, not an error: neutral colour.
            MessageText(text = stringResource(R.string.auth_panel_refused_notice), isError = false)
            Spacer(Modifier.size(Spacing.m))
        }

        AuthTextField(
            value = email,
            onValueChange = { email = AuthForm.limitEmailInput(it) },
            label = stringResource(R.string.auth_email_label),
            errorText = if (showEmailError) stringResource(R.string.auth_error_email_format) else null,
            keyboardType = KeyboardType.Email,
            onBlur = { emailTouched = true },
        )
        Spacer(Modifier.size(Spacing.s))
        AuthTextField(
            value = password,
            onValueChange = { password = AuthForm.limitPasswordInput(it) },
            label = stringResource(R.string.auth_password_label),
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeAction = { if (AuthForm.hasBothFields(email, password)) onSubmitEmail(email, password) },
            isPassword = true,
        )
        FieldLink(
            text = stringResource(R.string.auth_signin_forgot_password),
            onClick = { onForgotPassword(AuthForm.normalizeEmail(email)) },
            enabled = !state.isLoading,
        )
        Spacer(Modifier.size(Spacing.m))

        // One message, under the fields, for "unknown address" and "wrong password" alike (criteria C4 and C6).
        AuthMessageArea(state.message)

        MiffButton(
            text = stringResource(R.string.auth_signin_action),
            onClick = { onSubmitEmail(email, password) },
            enabled = AuthForm.hasBothFields(email, password) && !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.isLoading) LoadingIndicator()
        UnderlinedLink(text = stringResource(R.string.auth_signin_no_account), onClick = onGoToSignUp)
    }
}
