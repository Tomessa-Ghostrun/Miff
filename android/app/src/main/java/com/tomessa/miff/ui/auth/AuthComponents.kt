package com.tomessa.miff.ui.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.R
import com.tomessa.miff.ui.theme.GradientAuthBackground
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
 * Common frame of the authentication screens (mockups 04 to 06): auth gradient, system bars respected, scrolls when the
 * content is taller than the screen (largest font, landscape, keyboard). The header is the back arrow at the start and
 * the official wordmark centred. The mockups show no title: [paneTitle] names the screen for the screen reader only.
 */
@Composable
fun AuthScaffold(
    paneTitle: String,
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
                .semantics { this.paneTitle = paneTitle }
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = AuthHeaderHeight),
            contentAlignment = Alignment.Center,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.auth_back_a11y),
                        tint = colorTextForm,
                    )
                }
            }
            Image(
                painter = painterResource(R.drawable.miff_wordmark_color),
                contentDescription = stringResource(R.string.home_logo_a11y),
                modifier = Modifier.width(Spacing.logoWidth / 2).semantics { heading() },
            )
        }
        Spacer(Modifier.size(Spacing.l))
        content()
    }
}

private val AuthHeaderHeight = 56.dp

/** A visible title (the consent panel and "Mot de passe oublié": the sign-up and sign-in mockups have none). */
@Composable
fun ScreenTitle(
    title: String,
    style: TextStyle = textDisplayHeadline,
) {
    Text(
        text = title,
        style = style,
        color = colorTextForm,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.l).semantics { heading() },
    )
}

/**
 * Underlined field of the sign-up and sign-in screens (mockups 04 to 06): the label sits where the placeholder is in the
 * mockup and floats up when the field is filled (the screen reader always has the label), the text starts at the left
 * margin of the screen. The error is text under the field, announced by the screen reader, never colour alone.
 * [onBlur] is called when the field loses the focus after having it.
 *
 * [outlined] is the boxed style of the "Mot de passe oublié" mockups (-10 and -11): white rounded box with a thin border
 * (Material outlined field, the label sits on the border when the field is focused or filled). Same behaviour otherwise.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
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
    outlined: Boolean = false,
    onBlur: () -> Unit = {},
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val fieldContentType =
        when {
            isPassword && newPassword -> ContentType.NewPassword
            isPassword -> ContentType.Password
            else -> ContentType.EmailAddress + ContentType.Username
        }
    val hiddenOrPlain =
        if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None
    val keyboardOptions =
        KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false, imeAction = imeAction)
    val keyboardActions =
        KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() },
            onGo = { onImeAction() },
            onSend = { onImeAction() },
        )
    val eyeIcon = if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
    val colors =
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
            unfocusedLabelColor = colorTextDescription, // darker than the mockup grey: AA contrast
            errorLabelColor = colorTextError,
        )
    // Boxed style (mockups -10 and -11): white box, 1 dp border (strong grey, blue when focused, red on error).
    val outlinedColors =
        OutlinedTextFieldDefaults.colors(
            focusedTextColor = colorTextForm,
            unfocusedTextColor = colorTextForm,
            errorTextColor = colorTextForm,
            focusedContainerColor = colorBgSurface,
            unfocusedContainerColor = colorBgSurface,
            errorContainerColor = colorBgSurface,
            cursorColor = colorActionPrimary,
            errorCursorColor = colorTextError,
            focusedBorderColor = colorBorderAction,
            unfocusedBorderColor = colorBorderStrong,
            errorBorderColor = colorTextError,
            focusedLabelColor = colorActionPrimary,
            unfocusedLabelColor = colorTextDescription,
            errorLabelColor = colorTextError,
        )
    Column(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = textBodyRegular.copy(color = colorTextForm),
            cursorBrush = SolidColor(if (errorText != null) colorTextError else colorActionPrimary),
            visualTransformation = hiddenOrPlain,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
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
            decorationBox = { innerTextField ->
                val labelContent: @Composable () -> Unit = { Text(label, style = textBodyRegular) }
                val eye: (@Composable () -> Unit)? =
                    if (isPassword && value.isNotEmpty()) {
                        {
                            // The "eye" appears with the first character (criterion S4); slashed = visible, tap to hide.
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    painter = painterResource(eyeIcon),
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
                    }
                if (outlined) {
                    OutlinedTextFieldDefaults.DecorationBox(
                        value = value,
                        innerTextField = innerTextField,
                        enabled = true,
                        singleLine = true,
                        visualTransformation = hiddenOrPlain,
                        interactionSource = interactionSource,
                        isError = errorText != null,
                        label = labelContent,
                        trailingIcon = eye,
                        colors = outlinedColors,
                        container = {
                            OutlinedTextFieldDefaults.Container(
                                enabled = true,
                                isError = errorText != null,
                                interactionSource = interactionSource,
                                colors = outlinedColors,
                                shape = ShapeMd,
                            )
                        },
                    )
                } else {
                    TextFieldDefaults.DecorationBox(
                        value = value,
                        innerTextField = innerTextField,
                        enabled = true,
                        singleLine = true,
                        visualTransformation = hiddenOrPlain,
                        interactionSource = interactionSource,
                        isError = errorText != null,
                        label = labelContent,
                        trailingIcon = eye,
                        colors = colors,
                        contentPadding = TextFieldDefaults.contentPaddingWithLabel(start = 0.dp, end = 0.dp),
                    )
                }
            },
        )
        if (errorText != null) {
            // Under the field, not on the underline (the mockup superposes them: a design flaw not reproduced).
            MessageText(text = errorText, isError = true, modifier = Modifier.padding(top = Spacing.xs))
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
 * The explanatory line above the main button (no box to tick): the terms and the privacy policy are links inside the
 * sentence (underlined, bold, blue). For the screen reader the two links are also offered as actions of the line, so
 * that they are reachable without having to hit a few words with a finger. [sentence] has two placeholders: the name of
 * the terms, then the name of the privacy policy.
 */
@Composable
fun ConsentNotice(
    @StringRes sentence: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val termsLabel = stringResource(R.string.auth_consent_terms_link)
    val privacyLabel = stringResource(R.string.auth_consent_privacy_link)
    val text = stringResource(sentence, termsLabel, privacyLabel)
    val openTerms = { LegalLinks.open(context, LegalLinks.termsUrl(context)) }
    val openPrivacy = { LegalLinks.open(context, LegalLinks.privacyUrl(context)) }
    val linkStyles =
        TextLinkStyles(
            SpanStyle(
                color = colorActionPrimary,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline,
            ),
        )
    val annotated =
        buildAnnotatedString {
            append(text)
            listOf(termsLabel to openTerms, privacyLabel to openPrivacy).forEach { (label, open) ->
                val start = text.indexOf(label)
                if (start >= 0) {
                    addLink(
                        LinkAnnotation.Clickable("legal", linkStyles) { open() },
                        start,
                        start + label.length,
                    )
                }
            }
        }
    Text(
        text = annotated,
        style = textBodyRegular14,
        color = colorTextPrimary,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(termsLabel) {
                                openTerms()
                                true
                            },
                            CustomAccessibilityAction(privacyLabel) {
                                openPrivacy()
                                true
                            },
                        )
                },
    )
}

// Google branding guidelines (light theme "Sign in with Google" button): white fill, 1 dp border #747775, text #1F1F1F in
// Roboto Medium 14 sp, no added shadow, G logo at 18 dp. These are Google brand values, not Miff design tokens.
private val GoogleButtonBorder = Color(0xFF747775)
private val GoogleButtonText = Color(0xFF1F1F1F)
private val GoogleButtonTextStyle =
    TextStyle(
        fontFamily = FontFamily.SansSerif, // Roboto on Android
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

/**
 * "Continuer avec Google", following Google's branding wording ("Continue with Google", in the language of the phone).
 * STILL TO DO before the Play listing: replace ic_google_g.xml by the official asset (see android/README.md).
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
        color = Color.White,
        border = BorderStroke(StrokeHairline, GoogleButtonBorder),
        modifier =
            modifier
                .fillMaxWidth()
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
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.s))
            Text(
                text = stringResource(R.string.auth_google_action),
                style = GoogleButtonTextStyle,
                color = GoogleButtonText,
            )
        }
    }
}

/** "Ou :" between the social button and the e-mail form (mockups: bold, centred, no rule). */
@Composable
fun OrText(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.auth_or_divider),
        style = textBodyBold16,
        color = colorTextForm,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.m),
    )
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

/** The bold underlined link under the main button ("Déjà un compte ?", "Pas encore de compte ?"). */
@Composable
fun UnderlinedLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(onClick = onClick, modifier = Modifier.heightIn(min = Spacing.minTouchTarget)) {
            Text(
                text = text,
                style = textBodyBold16.copy(textDecoration = TextDecoration.Underline),
                color = colorActionPrimary,
            )
        }
    }
}

/**
 * Small bold link at the start of the line, under a field (mockup -8, "Mot de passe oublié ?"). Underlined like the other
 * links of these screens (the mockup leaves it bare: colour alone must not be the only sign of a link), 48 dp tall to hit
 * easily with a finger, and no side padding so that its text lines up with the field above.
 */
@Composable
fun FieldLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = Spacing.s),
        modifier = modifier.heightIn(min = Spacing.minTouchTarget),
    ) {
        Text(
            text = text,
            style = textBodyBold14.copy(textDecoration = TextDecoration.Underline),
            color = colorActionPrimary,
        )
    }
}
