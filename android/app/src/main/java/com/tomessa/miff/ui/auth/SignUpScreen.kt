package com.tomessa.miff.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.tomessa.miff.R
import com.tomessa.miff.domain.auth.AuthForm
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.textBodyBold16

/**
 * Sign-up screen (ticket section 3 A, criteria S1 to S13). Stateless: [state] comes from [SignUpViewModel].
 *
 * Only the e-mail survives a rotation (`rememberSaveable`); the password lives in a plain `remember`: it is never
 * saved anywhere, so a rotation empties it (decision of the INIT-2 review, same as the diagnostic screen).
 */
@Composable
fun SignUpScreen(
    state: SignUpUiState,
    onBack: (() -> Unit)?,
    onTermsChecked: (Boolean) -> Unit,
    onAnalyticsChecked: (Boolean) -> Unit,
    onSubmitEmail: (email: String, password: String) -> Unit,
    onGoogle: () -> Unit,
    onGoToSignIn: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }
    val termsFocus = remember { FocusRequester() }

    // At each tap without (a), the focus goes to the box (screen reader and keyboard users land on it).
    LaunchedEffect(state.termsMissingTick) {
        if (state.termsMissingTick > 0) termsFocus.requestFocus()
    }

    val showEmailError = (emailTouched || state.showFieldErrors) && emailFieldError(email)
    val showPasswordError =
        (passwordTouched || state.showFieldErrors) && password.isNotEmpty() && !AuthForm.isPasswordLongEnough(password)

    AuthScaffold(paneTitle = stringResource(R.string.auth_signup_title), onBack = onBack) {
        // Both boxes come BEFORE any sign-up button (Google, e-mail), decided by the ticket (Q8): this is the one
        // difference of order with mockup 04, where the single consent sentence sits under the fields.
        ConsentCheckbox(
            checked = state.termsChecked,
            onCheckedChange = onTermsChecked,
            text =
                stringResource(
                    R.string.auth_consent_terms,
                    stringResource(R.string.auth_consent_terms_link),
                    stringResource(R.string.auth_consent_privacy_link),
                ),
            errorText = if (state.termsMissing) stringResource(R.string.auth_error_terms_required) else null,
            focusRequester = termsFocus,
            enabled = !state.isLoading,
        )
        LegalLinksRow()
        ConsentCheckbox(
            checked = state.analyticsChecked,
            onCheckedChange = onAnalyticsChecked,
            text = stringResource(R.string.auth_consent_analytics),
            hint = stringResource(R.string.auth_consent_analytics_hint),
            enabled = !state.isLoading,
        )
        Spacer(Modifier.size(Spacing.m))

        GoogleButton(onClick = onGoogle, enabled = !state.isLoading)
        OrText()

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
            errorText = if (showPasswordError) stringResource(R.string.auth_error_password_short) else null,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeAction = { if (AuthForm.hasBothFields(email, password)) onSubmitEmail(email, password) },
            isPassword = true,
            newPassword = true,
            onBlur = { passwordTouched = true },
        )
        Spacer(Modifier.size(Spacing.l))

        AuthMessageArea(state.message, onGoToSignIn = onGoToSignIn)

        MiffButton(
            text = stringResource(R.string.auth_signup_action),
            onClick = { onSubmitEmail(email, password) },
            enabled = AuthForm.hasBothFields(email, password) && !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.isLoading) LoadingIndicator()
        UnderlinedLink(text = stringResource(R.string.auth_signup_have_account), onClick = onGoToSignIn)
    }
}

/** The error under the buttons; "address already used" also offers the way to the sign-in screen (criterion S6). */
@Composable
internal fun AuthMessageArea(
    message: AuthMessage?,
    onGoToSignIn: (() -> Unit)? = null,
) {
    if (message == null) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        MessageText(text = message.text(), isError = true)
        if (message == AuthMessage.EMAIL_IN_USE && onGoToSignIn != null) {
            TextButton(onClick = onGoToSignIn) {
                Text(
                    text = stringResource(R.string.auth_signup_have_account_action),
                    style = textBodyBold16,
                    color = colorActionPrimary,
                )
            }
        }
    }
    Spacer(Modifier.size(Spacing.s))
}
