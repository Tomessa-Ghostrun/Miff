package com.tomessa.miff.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.ui.auth.AuthHeader
import com.tomessa.miff.ui.auth.MessageText
import com.tomessa.miff.ui.theme.GradientAuthBackground
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeAccent
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint30
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorBorderStrong
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextError
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.colorTextOnAction
import com.tomessa.miff.ui.theme.colorTextPlaceholder
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyCaption
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textTitleBold20

/** Space kept at the bottom of the content so that the round arrow never hides the last field or message. */
private val NextButtonClearance = 96.dp

/**
 * Frame of the profile steps (mockups "Welcoming screen -1", "-2" and "-6"): auth gradient, wordmark (with the back arrow
 * on the phone step only), a title, the content, and the ROUND arrow at the bottom end. The content scrolls (large font,
 * landscape, keyboard); the arrow stays in reach above the keyboard. [paneTitle] names the screen for the screen reader.
 */
@Composable
fun ProfileScaffold(
    paneTitle: String,
    title: String,
    onBack: (() -> Unit)?,
    nextButton: @Composable BoxScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GradientAuthBackground)
                .safeDrawingPadding()
                .semantics { this.paneTitle = paneTitle },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.l, vertical = Spacing.s),
        ) {
            AuthHeader(onBack)
            Spacer(Modifier.size(Spacing.l))
            Text(
                text = title,
                style = textTitleBold20,
                color = colorTextForm,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.l)
                        .semantics { heading() },
            )
            content()
            Spacer(Modifier.size(NextButtonClearance))
        }
        nextButton()
    }
}

/**
 * The round arrow of the mockups, bottom end: blue when it can be used, the same blue at 30 % when it cannot (like the
 * main button of the design system). While the write is going on it shows a spinner, announced by the screen reader.
 */
@Composable
fun BoxScope.RoundNextButton(
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.profile_next_a11y)
    val loading = stringResource(R.string.auth_loading_a11y)
    Surface(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = CircleShape,
        color = if (enabled || isLoading) colorActionPrimary else colorActionPrimaryTint30,
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Spacing.l, bottom = Spacing.m)
                .size(Spacing.minTouchTarget + Spacing.s)
                .then(if (enabled && !isLoading) Modifier.miffShadow(ShadowDefault, CircleShape) else Modifier)
                .semantics { role = Role.Button },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = colorTextOnAction,
                    strokeWidth = 3.dp,
                    modifier =
                        Modifier
                            .size(Spacing.l)
                            .semantics {
                                contentDescription = loading
                                liveRegion = LiveRegionMode.Polite
                            },
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_forward),
                    contentDescription = description,
                    tint = colorTextOnAction,
                )
            }
        }
    }
}

/**
 * Field of the mockups: the label sits ABOVE a white box with a thin rounded border (blue and thicker when focused, red
 * with a message under it when in error). Label and field are ONE element for the screen reader ("Prénom, edit box"),
 * the error is announced politely. [prefix] is the visual "+33" before the digits (the screen reader gets the country
 * from the selector). [counter] is the small "45/50" shown when the limit gets close.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LabeledOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    placeholder: String? = null,
    prefix: String? = null,
    counter: String? = null,
    counterA11y: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    fieldContentType: ContentType? = null,
    enabled: Boolean = true,
    /** Called when the field loses the focus after having had it (the person has left it). */
    onBlur: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val hasError = errorText != null
    val stroke =
        when {
            hasError -> BorderStroke(StrokeAccent, colorTextError)
            focused -> BorderStroke(StrokeAccent, colorBorderAction)
            else -> BorderStroke(StrokeHairline, colorBorderStrong)
        }
    Column(modifier = modifier.fillMaxWidth()) {
        // The label is drawn INSIDE the decoration of the text field, like the Material fields do: it then belongs to the
        // same element for the screen reader ("Prénom, edit box"), and a tap on it puts the cursor in the field.
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = textBodyRegular.copy(color = colorTextForm),
            cursorBrush = SolidColor(if (hasError) colorTextError else colorActionPrimary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        if (fieldContentType != null) contentType = fieldContentType
                        if (errorText != null) error(errorText)
                    }.onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) {
                            hadFocus = true
                        } else if (hadFocus) {
                            hadFocus = false
                            onBlur()
                        }
                    },
            decorationBox = { innerTextField ->
                Column {
                    Text(text = label, style = textBodyRegular, color = colorTextDescription)
                    Spacer(Modifier.size(Spacing.xs))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Spacing.minTouchTarget)
                                .clip(ShapeMd)
                                .background(colorBgSurface)
                                .border(stroke, ShapeMd)
                                .padding(horizontal = Spacing.m, vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (prefix != null) {
                            Text(
                                text = prefix,
                                style = textBodyRegular,
                                color = colorTextForm,
                                // Visual only: the country selector already says the calling code.
                                modifier = Modifier.clearAndSetSemantics {}.padding(end = Spacing.s),
                            )
                        }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(
                                    text = placeholder,
                                    style = textBodyRegular,
                                    color = colorTextPlaceholder,
                                    modifier = Modifier.clearAndSetSemantics {},
                                )
                            }
                            innerTextField()
                        }
                    }
                }
            },
        )
        if (errorText != null) {
            MessageText(text = errorText, isError = true, modifier = Modifier.padding(top = Spacing.xs))
        }
        if (counter != null) {
            Text(
                text = counter,
                style = textBodyCaption,
                color = colorTextDescription,
                textAlign = TextAlign.End,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs)
                        .semantics { if (counterA11y != null) contentDescription = counterA11y },
            )
        }
    }
}
