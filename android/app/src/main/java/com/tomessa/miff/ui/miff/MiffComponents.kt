package com.tomessa.miff.ui.miff

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tomessa.miff.R
import com.tomessa.miff.domain.miff.JoinCode
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.profile.Initials
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.profile.ProfileAvatar
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeFull
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeAccent
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint10
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorActionPrimaryTint30
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorBorderStrong
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextError
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.colorTextOnAction
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyCaption
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textCaptionBold12
import com.tomessa.miff.ui.theme.textDisplayLabel
import com.tomessa.miff.ui.theme.textDisplayTitle
import com.tomessa.miff.ui.theme.textTitleBold20
import com.tomessa.miff.ui.theme.textTitleBold24

/*
 * Components of the Miff flow that the design system does not have yet (ticket Miff 3.3: input field, loved-one row, confirmation
 * dialog, role pill, row with a switch, section header, notice). Built from the tokens of ui/theme; no colour, size or font of
 * their own, except the few sizes named below, taken from the fiches design/screens/miff-*.md.
 */

private val SectionIconBox = 33.dp
private val SectionIconSize = 20.dp
private val PillHeight = 20.dp
private val MemberAvatar = 46.dp
private val CodeLetterSpacing = 2.sp

/** The header of the screens of the flow: the back arrow at the start, the title in the middle (mockups, "Nouvelle Miff"). */
@Composable
fun MiffHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget + Spacing.s),
        contentAlignment = Alignment.Center,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart).size(Spacing.minTouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.auth_back_a11y),
                    tint = colorTextForm,
                )
            }
        }
        Text(
            text = title,
            style = textDisplayTitle,
            color = colorTextForm,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier =
                Modifier
                    .padding(horizontal = Spacing.minTouchTarget + Spacing.xs)
                    .semantics { heading() },
        )
    }
}

/** "Nom", "Ajouter des proches"...: a pale blue square with an icon, a title and a short line (mockups). */
@Composable
fun MiffSectionHeader(
    @DrawableRes icon: Int,
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(SectionIconBox).clip(ShapeMd).background(colorActionPrimaryTint20),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colorActionPrimary,
                modifier = Modifier.size(SectionIconSize),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = textDisplayTitle,
                color = colorTextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            if (description != null) {
                Text(text = description, style = textBodyCaption, color = colorTextDescription)
            }
        }
    }
}

/** A calm sentence in a bordered box (never a red alarm): information, or the "no connection" mention. */
@Composable
fun MiffNotice(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Text(
        text = text,
        style = textBodyRegular14,
        color = if (isError) colorTextError else colorTextPrimary,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorBgSurface)
                .border(StrokeHairline, if (isError) colorTextError else colorBorderStrong, ShapeMd)
                .padding(Spacing.m)
                .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** The text of an [ActionMessage], as the screens say it. */
@Composable
fun actionMessageText(message: ActionMessage): String =
    stringResource(
        if (message ==
            ActionMessage.OFFLINE
        ) {
            R.string.miff_message_offline
        } else {
            R.string.miff_message_generic
        },
    )

/** A text field of the flow (name of the Miff). The label floats, the help or the error sits under the field. */
@Composable
fun MiffTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
) {
    val colors =
        OutlinedTextFieldDefaults.colors(
            focusedTextColor = colorTextForm,
            unfocusedTextColor = colorTextForm,
            disabledTextColor = colorTextDescription,
            focusedContainerColor = colorBgSurface,
            unfocusedContainerColor = colorBgSurface,
            disabledContainerColor = colorBgSurface,
            errorContainerColor = colorBgSurface,
            cursorColor = colorActionPrimary,
            focusedBorderColor = colorBorderAction,
            unfocusedBorderColor = colorBorderStrong,
            errorBorderColor = colorTextError,
            focusedLabelColor = colorActionPrimary,
            unfocusedLabelColor = colorTextDescription,
            errorLabelColor = colorTextError,
            focusedPlaceholderColor = colorTextDescription,
            unfocusedPlaceholderColor = colorTextDescription,
            focusedSupportingTextColor = colorTextDescription,
            unfocusedSupportingTextColor = colorTextDescription,
            errorSupportingTextColor = colorTextError,
        )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        label = { Text(label, style = textBodyRegular) },
        placeholder = placeholder?.let { { Text(it, style = textBodyRegular) } },
        isError = errorText != null,
        supportingText =
            (errorText ?: supportingText)?.let {
                {
                    Text(
                        it,
                        style = textBodyCaption,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            },
        shape = ShapeMd,
        colors = colors,
        textStyle = textBodyRegular,
        // Names are private: no learning keyboard, no suggestions; a name may hold emojis, so the keyboard is the normal one.
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                imeAction = imeAction,
            ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Shows a code by groups of 4 as it is typed ("XTZRSP367988" -> "XTZR SP36 7988"), without changing what is stored. */
private object CodeGrouping : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val grouped = JoinCode.grouped(raw)
        val mapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int =
                    (offset + (offset - 1).coerceAtLeast(0) / JoinCode.GROUP_SIZE).coerceAtMost(grouped.length)

                override fun transformedToOriginal(offset: Int): Int =
                    (offset - offset / (JoinCode.GROUP_SIZE + 1)).coerceIn(0, raw.length)
            }
        return TransformedText(AnnotatedString(grouped), mapping)
    }
}

/**
 * The field of the code (ticket Miff 5.3, criteria J1, J2, AND3): capitals, no correction and no suggestion, an ASCII keyboard, the
 * code drawn by groups of 4. The text it holds is the cleaned code; cleaning (spaces, dashes, small letters) is done by the
 * view model. The help or the problem is announced to the screen reader when it appears.
 */
@Composable
fun MiffCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    enabled: Boolean = true,
    onDone: () -> Unit = {},
) {
    val colors =
        OutlinedTextFieldDefaults.colors(
            focusedTextColor = colorTextForm,
            unfocusedTextColor = colorTextForm,
            disabledTextColor = colorTextDescription,
            focusedContainerColor = colorBgSurface,
            unfocusedContainerColor = colorBgSurface,
            disabledContainerColor = colorBgSurface,
            errorContainerColor = colorBgSurface,
            cursorColor = colorActionPrimary,
            focusedBorderColor = colorBorderAction,
            unfocusedBorderColor = colorBorderStrong,
            errorBorderColor = colorTextError,
            focusedLabelColor = colorActionPrimary,
            unfocusedLabelColor = colorTextDescription,
            errorLabelColor = colorTextError,
            focusedPlaceholderColor = colorTextDescription,
            unfocusedPlaceholderColor = colorTextDescription,
            errorSupportingTextColor = colorTextError,
        )
    OutlinedTextField(
        value = code,
        onValueChange = onCodeChange,
        enabled = enabled,
        singleLine = true,
        label = { Text(label, style = textBodyRegular) },
        placeholder = { Text(placeholder, style = textTitleBold20.copy(letterSpacing = CodeLetterSpacing)) },
        isError = errorText != null,
        supportingText =
            errorText?.let {
                {
                    Text(
                        it,
                        style = textBodyCaption,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            },
        visualTransformation = CodeGrouping,
        textStyle = textTitleBold20.copy(letterSpacing = CodeLetterSpacing),
        shape = ShapeMd,
        colors = colors,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
        keyboardActions =
            androidx.compose.foundation.text
                .KeyboardActions(onDone = { onDone() }),
        modifier = modifier.fillMaxWidth(),
    )
}

/** The code, large, by groups of 4; read by the screen reader group by group, letter by letter (criterion T2). */
@Composable
fun MiffCodeDisplay(
    code: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val spoken = stringResource(R.string.miff_code_a11y, JoinCode.spoken(code))
    Text(
        text = JoinCode.grouped(code),
        style = (if (large) textTitleBold24 else textTitleBold20).copy(letterSpacing = CodeLetterSpacing),
        color = colorActionPrimary,
        textAlign = TextAlign.Center,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorBgSurface)
                .border(StrokeHairline, colorBorderStrong, ShapeMd)
                .padding(vertical = Spacing.m, horizontal = Spacing.s)
                .semantics { contentDescription = spoken },
    )
}

/** A small label next to a name: "Créateur", "Toi". Never green (that colour belongs to the status "all is well"). */
@Composable
fun MiffRolePill(
    text: String,
    isOwner: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = textCaptionBold12,
        color = if (isOwner) colorActionPrimary else colorTextPrimary,
        modifier =
            modifier
                .heightIn(min = PillHeight)
                .clip(ShapeFull)
                .background(if (isOwner) colorActionPrimaryTint10 else colorBorderDefault)
                .padding(horizontal = Spacing.s, vertical = Spacing.xs / 2),
    )
}

/**
 * A loved one: round photo (or initials), first and last name, and the pills "Créateur" and "Toi". No ring of colour around the
 * photo: this screen shows no status (criterion G7).
 */
@Composable
fun MiffMemberRow(
    member: MiffMember,
    isMe: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorBgSurface)
                .border(StrokeHairline, colorBorderDefault, ShapeMd)
                .padding(Spacing.s + Spacing.xs)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(
            jpeg = member.avatarJpeg,
            initials = Initials.of(member.firstName, member.lastName),
            size = MemberAvatar,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = member.displayName, style = textDisplayLabel, color = colorTextPrimary)
            if (member.role == MiffRole.OWNER || isMe) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (member.role ==
                        MiffRole.OWNER
                    ) {
                        MiffRolePill(stringResource(R.string.miff_role_owner), isOwner = true)
                    }
                    if (isMe) MiffRolePill(stringResource(R.string.miff_role_you), isOwner = false)
                }
            }
        }
    }
}

/**
 * "Partager mon statut": the whole row is the switch (48 dp tall at least), the state is said in words by the screen reader.
 * The switch does not move by itself: the person is asked first, and it only changes once the server has answered.
 */
@Composable
fun MiffSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    stateText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .miffShadow(ShadowDefault, ShapeMd)
                .clip(ShapeMd)
                .background(colorBgSurface)
                .border(StrokeHairline, colorBorderDefault, ShapeMd)
                .clickable(enabled = enabled, role = Role.Switch, onClick = onClick)
                .padding(horizontal = Spacing.m, vertical = Spacing.s + Spacing.xs)
                .semantics(mergeDescendants = true) { stateDescription = stateText },
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = textBodyBold16, color = colorTextPrimary)
            Text(text = description, style = textBodyRegular14, color = colorTextDescription)
        }
        // The row carries the click and the semantics: the switch itself is only the picture of the state.
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors =
                SwitchDefaults.colors(
                    checkedTrackColor = colorActionPrimary,
                    checkedThumbColor = colorTextOnAction,
                    uncheckedTrackColor = colorBgSurface,
                    uncheckedThumbColor = colorTextDescription,
                    uncheckedBorderColor = colorBorderStrong,
                    disabledCheckedTrackColor = colorActionPrimaryTint30,
                    disabledCheckedThumbColor = colorTextOnAction,
                ),
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** How heavy the question of a dialog is: [SENSITIVE] has a white button with a red outline (never a red fill: calm). */
enum class DialogTone { DEFAULT, SENSITIVE }

/**
 * THE confirmation dialog of the Miff flow (one model for every question, ticket 3.3): a title, one or two calm sentences,
 * the main button, and "Annuler" which is the safe way out (read first by the screen reader, and what "Retour" does). While the
 * operation runs, the main button shows a progress circle and nothing else can be pressed (one tap = one operation).
 */
@Composable
fun MiffConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    tone: DialogTone = DialogTone.DEFAULT,
    busy: Boolean = false,
    message: ActionMessage? = null,
) {
    Dialog(
        onDismissRequest = { if (!busy) onCancel() },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = false),
    ) {
        Column(
            modifier =
                modifier
                    .widthIn(max = Spacing.dialogWidth)
                    .fillMaxWidth()
                    .miffShadow(ShadowDefault, ShapeMd)
                    .clip(ShapeMd)
                    .background(colorBgSurface)
                    .border(StrokeAccent, colorBorderAction, ShapeMd)
                    .padding(horizontal = Spacing.m + Spacing.xs, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = textTitleBold20,
                color = colorTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = body, style = textBodyRegular, color = colorTextDescription, textAlign = TextAlign.Center)
            if (message != null) MiffNotice(text = actionMessageText(message))
            if (tone == DialogTone.DEFAULT) {
                MiffButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedButton(
                    onClick = onConfirm,
                    enabled = !busy,
                    shape = ShapeMd,
                    border = BorderStroke(StrokeAccent, colorTextError),
                    colors =
                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = colorBgSurface,
                            contentColor = colorTextError,
                            disabledContentColor = colorTextError.copy(alpha = DISABLED_ALPHA),
                        ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget),
                ) {
                    Text(confirmLabel, style = textBodyBold16)
                }
            }
            if (busy) {
                val working = stringResource(R.string.miff_dialog_working_a11y)
                CircularProgressIndicator(
                    color = colorActionPrimary,
                    modifier =
                        Modifier.size(Spacing.l).semantics {
                            contentDescription = working
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }
            TextButton(
                onClick = onCancel,
                enabled = !busy,
                modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
            ) {
                Text(cancelLabel, style = textBodyBold16, color = colorActionPrimary)
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.5f
