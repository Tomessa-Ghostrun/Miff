package com.tomessa.miff.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.domain.update.PhotoDescription
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14

/*
 * The optional DESCRIPTION field under the preview of the photo (ticket docs/tickets/description-update.md, DESC-A). PRIVACY, for this file: the text lives in
 * the memory of the screen and of the view model, nothing else: no `rememberSaveable` (not in the state saved by the system), no log, no event, no clipboard
 * written from here. It is typed on a dark background (the preview), so the colours are the ones of the other overlays of the Update (white on a light veil).
 *
 * What it does: a field that grows from 1 to 4 lines then scrolls inside; the keyboard action is "Terminé" (it closes the keyboard and publishes NOTHING:
 * "Publier" is the only gesture that publishes); control, invisible and direction characters are removed as the text is typed or pasted, and a new line
 * becomes a space (so no line break is ever typed); at most 140 UTF-16 units, never cutting an emoji; a small counter only from 120 units, calm, never red.
 */

/** The private option of Gboard "no personalized learning": a request (other keyboards may ignore it), the same as the name and address fields. */
private const val NO_LEARNING = "com.google.android.inputmethod.latin.noPersonalizedLearning"

private const val MAX_LINES = 4

/** The field is white on a light veil over the dark stage of the preview: text and placeholder keep a contrast of 4.5:1 and more (see ColorContrastTest). */
internal val DescriptionFieldVeil = Color.White.copy(alpha = 0.14f)
internal val DescriptionPlaceholderColor = Color.White.copy(alpha = 0.78f)
internal val DescriptionDisabledTextColor = Color.White.copy(alpha = 0.6f)

@Composable
internal fun DescriptionField(
    initial: String,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The source of truth is the view model (it survives a rotation); [field] only holds what the keyboard needs on top of the text (cursor, composition).
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    var focused by remember { mutableStateOf(false) }
    var limitHit by remember { mutableStateOf(initial.length >= PhotoDescription.MAX_LENGTH) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val label = stringResource(R.string.update_description_a11y_label)
    val hint = stringResource(R.string.update_description_a11y_hint)
    val length = field.text.length
    val done = {
        // "Terminé": the keyboard goes away, nothing is published.
        focusManager.clearFocus()
        keyboard?.hide()
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        BasicTextField(
            value = field,
            onValueChange = { incoming ->
                if (incoming.text == field.text) {
                    // Only the cursor or the selection moved (or the composition changed without a change of text).
                    field = incoming
                } else {
                    val limited = PhotoDescription.limitInput(incoming.text)
                    field =
                        if (limited.text == incoming.text) {
                            incoming
                        } else {
                            // The cursor stays where the person was typing (a paste in the middle that is shortened or cleaned does not throw it to the end):
                            // its place is the length of what is kept before it.
                            fun kept(position: Int) =
                                PhotoDescription
                                    .limitInput(incoming.text.take(position.coerceIn(0, incoming.text.length)))
                                    .text.length
                                    .coerceAtMost(limited.text.length)
                            TextFieldValue(
                                limited.text,
                                TextRange(kept(incoming.selection.start), kept(incoming.selection.end)),
                            )
                        }
                    limitHit = limited.wasCut || limited.text.length >= PhotoDescription.MAX_LENGTH
                    onTextChange(limited.text)
                }
            },
            enabled = enabled,
            textStyle = textBodyRegular.copy(color = if (enabled) Color.White else DescriptionDisabledTextColor),
            cursorBrush = SolidColor(Color.White),
            minLines = 1,
            maxLines = MAX_LINES,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                    platformImeOptions = PlatformImeOptions(privateImeOptions = NO_LEARNING),
                ),
            keyboardActions = KeyboardActions(onDone = { done() }),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    // A keyboard with a physical "Entrée" does what "Terminé" does: no line break is ever typed.
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                            if (event.type == KeyEventType.KeyDown) done()
                            true
                        } else {
                            false
                        }
                    }
                    // The field is named for a screen reader (the placeholder is not the only label), with what the people close to the person will see.
                    .semantics { contentDescription = "$label. $hint" },
            decorationBox = { inner ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Spacing.minTouchTarget)
                            .clip(ShapeMd)
                            .background(DescriptionFieldVeil)
                            .border(
                                if (focused) 2.dp else 1.dp,
                                Color.White.copy(alpha = if (focused) 1f else 0.55f),
                                ShapeMd,
                            ).padding(horizontal = Spacing.m, vertical = Spacing.s + Spacing.xs),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (field.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.update_description_placeholder),
                            style = textBodyRegular,
                            color = DescriptionPlaceholderColor,
                        )
                    }
                    inner()
                }
            },
        )
        if (PhotoDescription.showCounter(length)) {
            DescriptionCounter(length = length, atLimit = limitHit || length >= PhotoDescription.MAX_LENGTH)
        }
    }
}

/**
 * The counter "125/140", only from 120 units (never before), in a neutral colour (never red, never "error"). The phrase of the limit appears at 140 and is
 * announced once (a polite live region present only while it is there). The counter itself is read "125 caractères sur 140", and said ONCE when it appears,
 * never at each key.
 */
@Composable
private fun DescriptionCounter(
    length: Int,
    atLimit: Boolean,
) {
    val max = PhotoDescription.MAX_LENGTH
    val spoken = stringResource(R.string.update_description_counter_a11y, length, max)
    // Remembered when the counter appears: the announcement does not change (so it is not repeated) while the person keeps typing.
    val onAppearance = remember { spoken }
    Box(
        Modifier.size(0.dp).semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = onAppearance
        },
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (atLimit) {
            Text(
                text = stringResource(R.string.update_description_limit, max),
                style = textBodyRegular14,
                color = Color.White,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else {
            Box(Modifier.weight(1f))
        }
        Text(
            text = stringResource(R.string.update_description_counter, length, max),
            style = textBodyRegular14,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.semantics { contentDescription = spoken },
        )
    }
}
