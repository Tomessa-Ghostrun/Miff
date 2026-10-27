package com.tomessa.miff.ui.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.R
import com.tomessa.miff.ui.theme.GradientAuthBackground
import com.tomessa.miff.ui.theme.ShadowSubtle
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorBorderStrong
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextError
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textDisplayHeadline

/** Public pages of the legal texts, in the language of the phone. The domain comes from ONE place: BuildConfig. */
object LegalLinks {
    fun termsUrl(context: Context): String =
        BuildConfig.LEGAL_BASE_URL + context.getString(R.string.android_legal_terms_path)

    fun privacyUrl(context: Context): String =
        BuildConfig.LEGAL_BASE_URL + context.getString(R.string.android_legal_privacy_path)

    /** Opens a page in the browser. If nothing can open it, nothing happens (no crash, no technical message). */
    fun open(
        context: Context,
        url: String,
    ) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // No browser: the person can still read the pages later.
        }
    }
}

/**
 * Common frame of the authentication screens: auth gradient, system bars respected, scrolls when the content is taller
 * than the screen (largest font, landscape, keyboard), optional back arrow.
 */
@Composable
fun AuthScaffold(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(GradientAuthBackground)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget)) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.auth_back_a11y),
                        tint = colorTextForm,
                    )
                }
            }
        }
        content()
    }
}

/** Provisional logo (see README) above the title of the screen; the title is a heading for the screen reader. */
@Composable
fun AuthHeader(title: String) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null, // decorative: the title right below names the screen
        modifier =
            Modifier
                .size(Spacing.logoSize / 2)
                .clip(ShapeMd)
                .background(colorActionPrimary),
    )
    Spacer(Modifier.size(Spacing.m))
    Text(
        text = title,
        style = textDisplayHeadline,
        color = colorTextForm,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { heading() },
    )
    Spacer(Modifier.size(Spacing.l))
}

/**
 * Underlined field of the sign-up and sign-in screens (design 04 to 06). The error is text under the field, announced
 * by the screen reader, never colour alone. [onBlur] is called when the field loses the focus after having it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    isPassword: Boolean = false,
    newPassword: Boolean = false,
    onBlur: () -> Unit = {},
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val fieldContentType =
        when {
            isPassword && newPassword -> ContentType.NewPassword
            isPassword -> ContentType.Password
            else -> ContentType.EmailAddress + ContentType.Username
        }
    val hiddenOrPlain =
        if (isPassword &&
            !passwordVisible
        ) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        }
    val keyboardOptions =
        KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false, imeAction = imeAction)
    Column(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, style = textBodyRegular) },
            singleLine = true,
            isError = errorText != null,
            visualTransformation = hiddenOrPlain,
            keyboardOptions = keyboardOptions,
            keyboardActions =
                KeyboardActions(
                    onNext = { onImeAction() },
                    onDone = { onImeAction() },
                    onGo = { onImeAction() },
                ),
            textStyle = textBodyRegular,
            trailingIcon =
                if (isPassword && value.isNotEmpty()) {
                    {
                        // The "eye" appears with the first character (criterion S4).
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                                    ),
                                contentDescription =
                                    stringResource(
                                        if (passwordVisible) {
                                            R.string.auth_password_hide_a11y
                                        } else {
                                            R.string.auth_password_show_a11y
                                        },
                                    ),
                                tint = colorTextForm,
                            )
                        }
                    }
                } else {
                    null
                },
            colors =
                TextFieldDefaults.colors(
                    focusedTextColor = colorTextForm,
                    unfocusedTextColor = colorTextForm,
                    errorTextColor = colorTextForm,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                    cursorColor = colorActionPrimary,
                    errorCursorColor = colorTextError,
                    focusedIndicatorColor = colorBorderAction,
                    unfocusedIndicatorColor = colorBorderStrong,
                    errorIndicatorColor = colorTextError,
                    focusedLabelColor = colorActionPrimary,
                    unfocusedLabelColor = colorTextDescription,
                    errorLabelColor = colorTextError,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentType = fieldContentType
                        if (errorText != null) error(errorText)
                    }.onFocusChanged {
                        if (it.isFocused) {
                            hadFocus = true
                        } else if (hadFocus) {
                            hadFocus = false
                            onBlur()
                        }
                    },
        )
        if (errorText != null) {
            // Under the field, not on the underline (the mockup superposes them: a design flaw not reproduced).
            MessageText(
                text = errorText,
                isError = true,
                modifier = Modifier.padding(start = Spacing.m, top = Spacing.xs),
            )
        }
    }
}

/** A message announced politely by the screen reader when it appears. Error colour AND words, never colour alone. */
@Composable
fun MessageText(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = textBodyRegular14,
        color = if (isError) colorTextError else colorTextPrimary,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * One consent box: the whole row (box + sentence) is one touch target of at least 48 dp, read by the screen reader as
 * "sentence, checkbox, checked / not checked". [errorText] is shown (and announced) under the box.
 */
@Composable
fun ConsentCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    errorText: String? = null,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.minTouchTarget)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = onCheckedChange,
                    ).semantics { if (errorText != null) error(errorText) },
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null, // handled by the row (single accessibility node)
                enabled = enabled,
                colors =
                    CheckboxDefaults.colors(
                        checkedColor = colorActionPrimary,
                        uncheckedColor = if (errorText != null) colorTextError else colorBorderStrong,
                        checkmarkColor = Color.White,
                    ),
            )
            Column(modifier = Modifier.padding(top = Spacing.s, start = Spacing.xs, end = Spacing.xs)) {
                Text(text = text, style = textBodyRegular14, color = colorTextPrimary)
                if (hint != null) {
                    Text(text = hint, style = textBodyRegular14, color = colorTextDescription)
                }
            }
        }
        if (errorText != null) {
            MessageText(text = errorText, isError = true, modifier = Modifier.padding(start = Spacing.xl + Spacing.s))
        }
    }
}

/** The two "read the text" links of the consent (a): separate, focusable, 48 dp high, reachable with TalkBack. */
@Composable
fun LegalLinksRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier.fillMaxWidth().padding(start = Spacing.xl + Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { LegalLinks.open(context, LegalLinks.termsUrl(context)) }) {
            Text(stringResource(R.string.auth_consent_terms_link), style = textBodyBold14, color = colorActionPrimary)
        }
        TextButton(onClick = { LegalLinks.open(context, LegalLinks.privacyUrl(context)) }) {
            Text(stringResource(R.string.auth_consent_privacy_link), style = textBodyBold14, color = colorActionPrimary)
        }
    }
}

/**
 * "Continuer avec Google". The label follows Google's branding wording ("Continue with Google", in the language of the
 * phone) with the multicolour G on a white button. The G drawable must be replaced by the official asset before
 * publication (see ic_google_g.xml).
 */
@Composable
fun GoogleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ShapeMd,
        color = colorBgSurface,
        border = BorderStroke(StrokeHairline, colorBorderStrong),
        modifier =
            modifier
                .fillMaxWidth()
                .miffShadow(ShadowSubtle, ShapeMd)
                .heightIn(min = Spacing.minTouchTarget),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_google_g),
                contentDescription = null, // decorative: the label names the button
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Spacing.s))
            Text(
                text = stringResource(R.string.auth_google_action),
                style = textBodyBold16,
                color = colorTextForm,
            )
        }
    }
}

/** "ou" between the e-mail form and the Google button. */
@Composable
fun OrDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = colorBorderStrong)
        Text(stringResource(R.string.auth_or_divider), style = textBodyRegular14, color = colorTextDescription)
        HorizontalDivider(modifier = Modifier.weight(1f), color = colorBorderStrong)
    }
}

/** Busy indicator announced by the screen reader. Replaces nothing: the screen stays visible behind it. */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.auth_loading_a11y)
    CircularProgressIndicator(
        color = colorActionPrimary,
        modifier =
            modifier
                .size(Spacing.xl)
                .semantics {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                },
    )
}

/** A link-like button in a flow: a sentence, then the action under it. */
@Composable
fun QuestionWithAction(
    question: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = question, style = textBodyRegular, color = colorTextDescription, textAlign = TextAlign.Center)
        TextButton(onClick = onAction) {
            Text(text = action, style = textBodyBold16, color = colorActionPrimary)
        }
    }
}
