package com.tomessa.miff.ui.place

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.domain.place.AddressSuggestion
import com.tomessa.miff.domain.place.OwnedMiff
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.domain.place.PlaceLimits
import com.tomessa.miff.domain.place.PlaceRadius
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.RecentsPrivacy
import com.tomessa.miff.ui.components.miffBottomInsets
import com.tomessa.miff.ui.components.miffScreenInsets
import com.tomessa.miff.ui.miff.DialogTone
import com.tomessa.miff.ui.miff.MiffConfirmDialog
import com.tomessa.miff.ui.miff.MiffHeader
import com.tomessa.miff.ui.miff.MiffNotice
import com.tomessa.miff.ui.miff.MiffSectionHeader
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
import com.tomessa.miff.ui.theme.colorBgCanvas
import com.tomessa.miff.ui.theme.colorBgPlaceholder
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorBorderStrong
import com.tomessa.miff.ui.theme.colorBorderSubtle
import com.tomessa.miff.ui.theme.colorStatusDanger
import com.tomessa.miff.ui.theme.colorStatusDangerTint
import com.tomessa.miff.ui.theme.colorStatusSafe
import com.tomessa.miff.ui.theme.colorStatusSafeTint
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextError
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.colorTextOnAction
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyCaption
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textDisplayTitle

/** Everything the form can ask of its view model (one object so that the screen and its tests stay readable). */
class PlaceFormActions(
    val onBack: () -> Unit = {},
    val onRetryLoad: () -> Unit = {},
    val onNameChanged: (String) -> Unit = {},
    val onMiffSheetOpened: () -> Unit = {},
    val onMiffSheetClosed: () -> Unit = {},
    val onMiffToggled: (String) -> Unit = {},
    val onAddressChanged: (String) -> Unit = {},
    val onSuggestionChosen: (AddressSuggestion) -> Unit = {},
    val onAddressCleared: () -> Unit = {},
    val onRetrySearch: () -> Unit = {},
    val onRadiusChanged: (Float) -> Unit = {},
    val onTypeSelected: (PlaceType) -> Unit = {},
    val onCategorySelected: (PlaceCategory) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDeleteRequested: () -> Unit = {},
    val onDeleteDismissed: () -> Unit = {},
    val onDeleteConfirmed: () -> Unit = {},
)

/**
 * "Nouveau lieu" and "Modifier le lieu" (mockups 01 to 07 of the PM, fiches nouveau-lieu.md and modifier-lieu.md). One scrolling
 * body, one fixed foot with THE button "Enregistrer" (no tick at the top, decision of the PM). The map is a still picture: the
 * address and the slider are enough to make a place (screen reader, no map).
 */
@Composable
fun PlaceFormScreen(
    state: PlaceFormState,
    actions: PlaceFormActions,
) {
    // The name and the address typed here are hidden from the list of recent apps (Android 13 and later).
    RecentsPrivacy()
    val title =
        stringResource(
            if (state.mode ==
                PlaceFormMode.CREATE
            ) {
                R.string.place_form_title_new
            } else {
                R.string.place_form_title_edit
            },
        )
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colorBgCanvas)
                .miffScreenInsets()
                .semantics { paneTitle = title },
    ) {
        MiffHeader(title = title, onBack = actions.onBack, modifier = Modifier.padding(horizontal = Spacing.l))
        when {
            state.loading -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val loading = stringResource(R.string.auth_loading_a11y)
                    CircularProgressIndicator(
                        color = colorActionPrimary,
                        modifier = Modifier.semantics { contentDescription = loading },
                    )
                }
            }

            state.loadFailed != null -> {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    MiffNotice(
                        stringResource(
                            if (state.loadFailed ==
                                PlaceMessage.OFFLINE
                            ) {
                                R.string.miff_message_offline
                            } else {
                                R.string.place_form_unavailable
                            },
                        ),
                    )
                    com.tomessa.miff.ui.components.MiffSecondaryButton(
                        text = stringResource(R.string.auth_retry_action),
                        onClick = actions.onRetryLoad,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            else -> {
                Body(state, actions, Modifier.weight(1f))
                Foot(state, actions)
            }
        }
    }
    if (state.miffSheetOpen) MiffPickerSheet(state, actions)
    if (state.deleteDialogOpen) DeleteDialog(state, actions)
}

@Composable
private fun Body(
    state: PlaceFormState,
    actions: PlaceFormActions,
    modifier: Modifier,
) {
    val bringAddress = remember { BringIntoViewRequester() }
    LaunchedEffect(state.suggestions) {
        // The keyboard must not hide the list: the form scrolls to put the field and its suggestions in view (design 02).
        if (state.suggestions !is SuggestionsState.Idle) bringAddress.bringIntoView()
    }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        NameSection(state, actions)
        MiffsSection(state, actions)
        Column(
            modifier = Modifier.bringIntoViewRequester(bringAddress),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            AddressSection(state, actions)
        }
        RadiusSection(state, actions)
        TypeSection(state, actions)
        CategorySection(state, actions)
        Spacer(Modifier.size(Spacing.s))
    }
}

// ---- Name ------------------------------------------------------------------------------------------------------------

@Composable
private fun NameSection(
    state: PlaceFormState,
    actions: PlaceFormActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        MiffSectionHeader(
            R.drawable.ic_edit,
            stringResource(R.string.place_name_title),
            stringResource(R.string.place_name_help),
        )
        val error = state.nameTooShort || state.nameLimitReached
        PlaceTextField(
            value = state.name,
            onValueChange = actions.onNameChanged,
            placeholder = stringResource(R.string.place_name_placeholder),
            enabled = !state.busy,
            isError = state.nameTooShort,
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Next,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text =
                    when {
                        state.nameTooShort -> stringResource(R.string.place_name_too_short)
                        state.nameLimitReached -> stringResource(R.string.place_name_limit)
                        else -> ""
                    },
                style = textBodyCaption,
                color = if (error) colorTextError else colorTextDescription,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (state.name.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.place_name_counter, state.name.length, PlaceLimits.NAME_MAX),
                    style = textBodyCaption,
                    color = colorTextDescription,
                )
            }
        }
    }
}

// ---- Miffs -----------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MiffsSection(
    state: PlaceFormState,
    actions: PlaceFormActions,
) {
    val single = state.singleMiff
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        MiffSectionHeader(
            R.drawable.ic_group,
            stringResource(if (single) R.string.place_miff_title else R.string.place_miffs_title),
            stringResource(if (single) R.string.place_miff_help else R.string.place_miffs_help),
        )
        val chips = state.owned.filter { it.id in state.selectedMiffIds }
        val openLabel = stringResource(R.string.place_miffs_open_a11y)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(ShapeMd)
                    .background(colorBgSurface)
                    .border(
                        if (state.miffSheetOpen) StrokeAccent else StrokeHairline,
                        if (state.miffSheetOpen) colorBorderAction else colorBorderStrong,
                        ShapeMd,
                    ).then(
                        if (single || !state.canEditAddress) {
                            Modifier
                        } else {
                            Modifier.clickable(
                                onClickLabel = openLabel,
                                role = Role.Button,
                                onClick = actions.onMiffSheetOpened,
                            )
                        },
                    ).heightIn(min = Spacing.minTouchTarget)
                    .padding(horizontal = Spacing.s, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                chips.forEach { miff ->
                    MiffChip(
                        miff = miff,
                        removable = !single && !state.busy,
                        onRemove = { actions.onMiffToggled(miff.id) },
                    )
                }
            }
            if (!single) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    tint = colorTextDescription,
                    modifier = Modifier.size(Spacing.l),
                )
            }
        }
        VisibilityLine(stringResource(if (single) R.string.place_miff_visibility else R.string.place_miffs_visibility))
    }
}

@Composable
private fun MiffChip(
    miff: OwnedMiff,
    removable: Boolean,
    onRemove: () -> Unit,
) {
    val removeLabel = stringResource(R.string.place_miffs_remove_a11y, miff.name)
    Row(
        modifier =
            Modifier
                .clip(ShapeFull)
                .background(colorActionPrimaryTint10)
                .border(StrokeHairline, colorActionPrimaryTint30, ShapeFull)
                .padding(start = Spacing.s + Spacing.xs, end = if (removable) Spacing.xs else Spacing.s + Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = miff.name,
            style = textBodyBold14,
            color = colorActionPrimary,
            maxLines = 1,
            modifier = Modifier.padding(vertical = Spacing.xs),
        )
        if (removable) {
            Box(
                modifier =
                    Modifier
                        .size(Spacing.minTouchTarget - Spacing.s)
                        .clickable(onClickLabel = removeLabel, role = Role.Button, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_loc_close),
                    contentDescription = null,
                    tint = colorActionPrimary,
                    modifier = Modifier.size(Spacing.m),
                )
            }
        }
    }
}

/** "Les membres de ces Miffs verront ce lieu : nom, adresse, carte et rayon." (what the rules already allow: §6.8 of the ticket). */
@Composable
private fun VisibilityLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.Top) {
        Icon(
            painter = painterResource(R.drawable.ic_visibility),
            contentDescription = null,
            tint = colorTextDescription,
            modifier = Modifier.size(Spacing.m),
        )
        Text(text = text, style = textBodyCaption, color = colorTextDescription)
    }
}

/** The sheet of the Miffs (design 05): tick one or several, 5 at most; the chips of the form follow each tick. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MiffPickerSheet(
    state: PlaceFormState,
    actions: PlaceFormActions,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = actions.onMiffSheetClosed,
        sheetState = sheetState,
        containerColor = colorBgSurface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .miffBottomInsets()
                    .padding(horizontal = Spacing.l, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.place_miffs_sheet_title),
                style = textDisplayTitle,
                color = colorTextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.place_miffs_sheet_subtitle),
                style = textBodyCaption,
                color = colorTextDescription,
            )
            val full = state.selectedMiffIds.size >= PlaceLimits.MAX_MIFFS
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            ) {
                state.owned.forEach { miff ->
                    val checked = miff.id in state.selectedMiffIds
                    val enabled = checked || !full
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Spacing.minTouchTarget + Spacing.s)
                                .toggleable(
                                    value = checked,
                                    enabled = enabled,
                                    role = Role.Checkbox,
                                    onValueChange = { actions.onMiffToggled(miff.id) },
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                            enabled = enabled,
                            colors =
                                CheckboxDefaults.colors(
                                    checkedColor = colorActionPrimary,
                                    checkmarkColor = colorTextOnAction,
                                ),
                        )
                        Text(
                            text = miff.name,
                            style = textBodyRegular,
                            color = if (enabled) colorTextPrimary else colorTextDescription.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f),
                        )
                        if (miff.id == state.lastJoinedId) {
                            Text(
                                text = stringResource(R.string.place_miffs_last_joined),
                                style = textCaptionBoldPill,
                                color = colorTextForm,
                                modifier =
                                    Modifier
                                        .clip(ShapeFull)
                                        .background(colorStatusSafeTint)
                                        .padding(horizontal = Spacing.s, vertical = Spacing.xs / 2),
                            )
                        }
                    }
                }
            }
            val none = state.selectedMiffIds.isEmpty()
            Text(
                text =
                    when {
                        none -> {
                            stringResource(R.string.place_miffs_none_selected)
                        }

                        full -> {
                            stringResource(R.string.place_miffs_max_reached)
                        }

                        state.selectedMiffIds.size > 1 -> {
                            stringResource(
                                R.string.place_miffs_count,
                                state.selectedMiffIds.size,
                                PlaceLimits.MAX_MIFFS,
                            )
                        }

                        else -> {
                            ""
                        }
                    },
                style = textBodyCaption,
                color = colorTextDescription,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            MiffButton(
                text = stringResource(R.string.place_miffs_sheet_done),
                onClick = actions.onMiffSheetClosed,
                enabled = !none,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val textCaptionBoldPill = com.tomessa.miff.ui.theme.textCaptionBold12

// ---- Address -----------------------------------------------------------------------------------------------------------

@Composable
private fun AddressSection(
    state: PlaceFormState,
    actions: PlaceFormActions,
) {
    MiffSectionHeader(
        R.drawable.ic_loc_pin,
        stringResource(R.string.place_address_title),
        stringResource(R.string.place_address_help),
    )
    val chosen = state.chosen != null
    PlaceTextField(
        value = state.addressText,
        onValueChange = actions.onAddressChanged,
        placeholder = stringResource(R.string.place_address_placeholder),
        enabled = state.canEditAddress,
        leadingIcon = if (chosen) R.drawable.ic_loc_pin else R.drawable.ic_loc_search,
        leadingTint = if (chosen) colorActionPrimary else colorTextDescription,
        onClear = if (state.addressText.isNotEmpty()) actions.onAddressCleared else null,
        capitalization = KeyboardCapitalization.Words,
        keyboardType = KeyboardType.Text,
        // The service of suggestions must see no more than what is needed: no learning keyboard (see PlaceTextField).
        imeAction = ImeAction.Done,
    )
    when (val suggestions = state.suggestions) {
        SuggestionsState.Idle -> {
            val typed = state.addressText.trim().length >= PlaceLimits.SUGGESTION_MIN_CHARS
            if (!chosen && typed) {
                // Typed in full but not picked: the button stays asleep and a quiet line says why (design 04F).
                InfoLine(stringResource(R.string.place_address_pick_hint))
            }
        }

        SuggestionsState.Loading -> {
            SuggestionsPanel {
                repeat(SKELETON_ROWS) { SkeletonRow() }
            }
        }

        is SuggestionsState.Found -> {
            SuggestionsPanel {
                suggestions.items.forEach { SuggestionRow(it, state.addressText.trim(), actions.onSuggestionChosen) }
                Text(
                    text = stringResource(R.string.place_address_credit),
                    style = textBodyCaption,
                    color = colorTextDescription,
                    modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                )
            }
        }

        SuggestionsState.NoResults -> {
            SuggestionsPanel {
                MessageRow(R.drawable.ic_loc_search, stringResource(R.string.place_address_none), null, null)
            }
        }

        SuggestionsState.Unavailable -> {
            SuggestionsPanel {
                MessageRow(
                    R.drawable.ic_loc_cloud_off,
                    stringResource(R.string.place_address_unavailable),
                    stringResource(R.string.auth_retry_action),
                    actions.onRetrySearch,
                )
            }
        }
    }
}

private const val SKELETON_ROWS = 3

@Composable
private fun InfoLine(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_loc_info),
            contentDescription = null,
            tint = colorTextDescription,
            modifier = Modifier.size(Spacing.m),
        )
        Text(text = text, style = textBodyCaption, color = colorTextDescription)
    }
}

@Composable
private fun SuggestionsPanel(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .miffShadow(ShadowDefault, ShapeMd)
                .clip(ShapeMd)
                .background(colorBgSurface)
                .border(StrokeHairline, colorBorderDefault, ShapeMd)
                .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        content()
    }
}

@Composable
private fun SuggestionRow(
    suggestion: AddressSuggestion,
    typed: String,
    onChosen: (AddressSuggestion) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onChosen(suggestion) }
                .heightIn(min = Spacing.minTouchTarget + Spacing.s)
                .padding(horizontal = Spacing.m, vertical = Spacing.s)
                .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box(
            modifier = Modifier.size(Spacing.l + Spacing.s).clip(CircleShape).background(colorActionPrimaryTint20),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_loc_pin),
                contentDescription = null,
                tint = colorActionPrimary,
                modifier = Modifier.size(Spacing.m),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = boldPrefix(suggestion.title, typed), style = textBodyRegular, color = colorTextPrimary)
            if (suggestion.subtitle.isNotEmpty()) {
                Text(text = suggestion.subtitle, style = textBodyRegular14, color = colorTextDescription)
            }
        }
    }
}

/** The part of the line that the person typed is bold (design 02). */
private fun boldPrefix(
    line: String,
    typed: String,
): AnnotatedString {
    val matches = typed.isNotEmpty() && line.startsWith(typed, ignoreCase = true)
    if (!matches) return AnnotatedString(line)
    return buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.substring(0, typed.length)) }
        append(line.substring(typed.length))
    }
}

@Composable
private fun SkeletonRow() {
    val loading = stringResource(R.string.place_address_loading_a11y)
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s).semantics {
                contentDescription =
                    loading
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box(Modifier.size(Spacing.l + Spacing.s).clip(CircleShape).background(colorBgPlaceholder.copy(alpha = 0.5f)))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Box(
                Modifier
                    .size(
                        width = 180.dp,
                        height = Spacing.s,
                    ).clip(ShapeFull)
                    .background(colorBgPlaceholder.copy(alpha = 0.5f)),
            )
            Box(
                Modifier
                    .size(
                        width = 100.dp,
                        height = Spacing.s,
                    ).clip(ShapeFull)
                    .background(colorBgPlaceholder.copy(alpha = 0.5f)),
            )
        }
    }
}

@Composable
private fun MessageRow(
    icon: Int,
    text: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colorTextDescription,
                modifier = Modifier.size(Spacing.l),
            )
            Text(text = text, style = textBodyRegular, color = colorTextPrimary)
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = Spacing.minTouchTarget)) {
                Text(actionLabel, style = textBodyBold16, color = colorActionPrimary)
            }
        }
    }
}

// ---- Radius -----------------------------------------------------------------------------------------------------------

@Composable
private fun RadiusSection(
    state: PlaceFormState,
    actions: PlaceFormActions,
) {
    val ready = state.chosen != null
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        MiffSectionHeader(
            R.drawable.ic_loc_radius,
            stringResource(R.string.place_radius_title),
            stringResource(R.string.place_radius_help),
        )
        val address = state.chosen?.label.orEmpty()
        PlaceMapBox(
            spec =
                state.chosen?.let {
                    PlaceMapSpec(
                        lat = it.lat,
                        lng = it.lng,
                        radiusM = state.radiusM,
                        danger = state.type == PlaceType.RISK,
                        description = stringResource(R.string.place_map_a11y, state.radiusM, address),
                    )
                },
            height = MAP_HEIGHT,
            enabled = ready,
        )
        val radiusName = stringResource(R.string.place_radius_a11y)
        val radiusState = stringResource(R.string.place_radius_state_a11y, state.radiusM)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Slider(
                value = state.radiusM.toFloat(),
                onValueChange = actions.onRadiusChanged,
                valueRange = PlaceLimits.RADIUS_MIN.toFloat()..PlaceLimits.RADIUS_MAX.toFloat(),
                // 16 allowed values (50, 100... 800): 14 stops between the two ends.
                steps = PlaceRadius.values.size - 2,
                enabled = ready && !state.busy,
                colors =
                    SliderDefaults.colors(
                        thumbColor = colorBgSurface,
                        activeTrackColor = colorActionPrimary,
                        inactiveTrackColor = colorBorderSubtle,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                        disabledThumbColor = colorBgSurface,
                        disabledActiveTrackColor = colorBorderSubtle,
                        disabledInactiveTrackColor = colorBorderSubtle,
                        disabledActiveTickColor = Color.Transparent,
                        disabledInactiveTickColor = Color.Transparent,
                    ),
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = radiusName
                            stateDescription = radiusState
                        },
            )
            Text(
                text = stringResource(R.string.place_radius_value, state.radiusM),
                style = textBodyBold16,
                color = if (ready) colorTextForm else colorTextDescription,
                modifier =
                    Modifier
                        .clip(ShapeMd)
                        .background(colorBgSurface)
                        .border(StrokeHairline, colorBorderDefault, ShapeMd)
                        .padding(horizontal = Spacing.s + Spacing.xs, vertical = Spacing.xs)
                        .clearAndSetSemantics {},
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = RADIUS_LABEL_END),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.place_radius_value, PlaceLimits.RADIUS_MIN),
                style = textBodyCaption,
                color = colorTextDescription,
            )
            Text(
                stringResource(R.string.place_radius_value, PlaceLimits.RADIUS_MAX),
                style = textBodyCaption,
                color = colorTextDescription,
            )
        }
        if (ready && PlaceRadius.isBelowRecommended(state.radiusM)) {
            // Discreet and calm (never red): the value stays allowed (decision Q12).
            InfoLine(stringResource(R.string.place_radius_small_note))
        }
    }
}

private val MAP_HEIGHT = 176.dp

/** The labels under the slider stop above the value pill, so that "800 m" sits under the end of the track. */
private val RADIUS_LABEL_END = 72.dp

// ---- Type -------------------------------------------------------------------------------------------------------------

@Composable
private fun TypeSection(
    state: PlaceFormState,
    actions: PlaceFormActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        MiffSectionHeader(
            R.drawable.ic_visibility,
            stringResource(R.string.place_type_title),
            stringResource(R.string.place_type_help),
        )
        // At the largest text sizes the two cards pile up instead of being squeezed.
        val stacked = LocalConfiguration.current.fontScale > STACK_FROM_FONT_SCALE
        val cards: @Composable (Modifier) -> Unit = { cardModifier ->
            TypeCard(
                type = PlaceType.TRUSTED,
                selected = state.type == PlaceType.TRUSTED,
                locked = false,
                enabled = state.canEditAddress,
                onClick = { actions.onTypeSelected(PlaceType.TRUSTED) },
                modifier = cardModifier,
            )
            TypeCard(
                type = PlaceType.RISK,
                selected = state.type == PlaceType.RISK,
                // A free account sees at once that this one is Miff+ (design decision 11): no surprise when touching it.
                locked = !state.premium && state.type != PlaceType.RISK,
                enabled = state.canEditAddress,
                onClick = { actions.onTypeSelected(PlaceType.RISK) },
                modifier = cardModifier,
            )
        }
        if (stacked) {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) { cards(Modifier.fillMaxWidth()) }
        } else {
            Row(
                modifier = Modifier.selectableGroup().fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) { cards(Modifier.weight(1f)) }
        }
    }
}

private const val STACK_FROM_FONT_SCALE = 1.3f

@Composable
private fun TypeCard(
    type: PlaceType,
    selected: Boolean,
    locked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val danger = type == PlaceType.RISK
    val accent = if (danger) colorStatusDanger else colorStatusSafe
    val description = stringResource(if (danger) R.string.place_type_danger_desc else R.string.place_type_trusted_desc)
    val selectedText = stringResource(R.string.place_type_selected_a11y)
    val lockedText = stringResource(R.string.place_type_locked_a11y)
    Box(
        modifier =
            modifier
                .clip(ShapeMd)
                .background(
                    when {
                        selected && danger -> colorStatusDangerTint
                        selected -> colorStatusSafeTint
                        else -> colorBgSurface
                    },
                ).border(StrokeAccent, if (selected) accent else accent.copy(alpha = 0.5f), ShapeMd)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    stateDescription =
                        if (selected) {
                            selectedText
                        } else if (locked) {
                            lockedText
                        } else {
                            ""
                        }
                },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.s + Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(
                    painter = painterResource(type.iconRes),
                    contentDescription = null,
                    tint = if (danger) colorTextError else colorTextForm,
                    modifier = Modifier.size(Spacing.l),
                )
                Text(
                    text = stringResource(type.labelRes),
                    style = textBodyBold16,
                    color = colorTextForm,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Text(text = description, style = textBodyRegular14, color = colorTextDescription)
            if (locked) PremiumPill()
        }
        if (selected) {
            // A tick as well as the colour: the choice is never told by colour alone (design decision 7).
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Spacing.xs)
                        .size(Spacing.l - Spacing.xs)
                        .clip(CircleShape)
                        .background(colorActionPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_loc_check),
                    contentDescription = null,
                    tint = colorTextOnAction,
                    modifier = Modifier.size(Spacing.m - Spacing.xs),
                )
            }
        }
    }
}

// ---- Category ---------------------------------------------------------------------------------------------------------

@Composable
private fun CategorySection(
    state: PlaceFormState,
    actions: PlaceFormActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        MiffSectionHeader(
            R.drawable.ic_loc_grid,
            stringResource(R.string.place_category_title),
            stringResource(R.string.place_category_help),
        )
        Column(modifier = Modifier.selectableGroup()) {
            PlaceCategory.entries.forEach { category ->
                val selected = state.category == category
                val label = stringResource(category.labelRes)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Spacing.minTouchTarget)
                            .selectable(
                                selected = selected,
                                enabled = !state.busy,
                                role = Role.RadioButton,
                                onClick = { actions.onCategorySelected(category) },
                            ).semantics(mergeDescendants = true) { this.selected = selected },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = null,
                        colors =
                            RadioButtonDefaults.colors(
                                selectedColor = colorActionPrimary,
                                unselectedColor = colorBorderStrong,
                            ),
                    )
                    CategoryIcon(category, size = Spacing.l + Spacing.xs)
                    Text(text = label, style = textBodyRegular, color = colorTextForm)
                }
            }
        }
    }
}

// ---- Foot, dialogs -----------------------------------------------------------------------------------------------------

@Composable
private fun Foot(
    state: PlaceFormState,
    actions: PlaceFormActions,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    colorBgCanvas,
                ).padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        state.message?.let { message ->
            MiffNotice(
                text =
                    stringResource(
                        when (message) {
                            PlaceMessage.OFFLINE -> R.string.miff_message_offline
                            PlaceMessage.GENERIC -> R.string.miff_message_generic
                            PlaceMessage.MIFF_GONE -> R.string.place_message_miff_gone
                        },
                    ),
                isError = message != PlaceMessage.OFFLINE,
            )
        }
        MiffButton(
            text = stringResource(if (state.saving) R.string.place_saving else R.string.place_save_action),
            onClick = actions.onSave,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.mode == PlaceFormMode.EDIT) {
            // "Supprimer ce lieu": white with a red outline, never a red fill (design decision 1).
            OutlinedButton(
                onClick = actions.onDeleteRequested,
                enabled = !state.busy,
                shape = ShapeMd,
                border = BorderStroke(StrokeAccent, colorTextError),
                colors =
                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        containerColor = colorBgSurface,
                        contentColor = colorTextError,
                        disabledContentColor = colorTextError.copy(alpha = 0.5f),
                    ),
                modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget),
            ) {
                Text(stringResource(R.string.place_delete_action), style = textBodyBold16)
            }
        }
    }
}

@Composable
private fun DeleteDialog(
    state: PlaceFormState,
    actions: PlaceFormActions,
) {
    val severalMiffs = state.selectedMiffIds.size > 1
    MiffConfirmDialog(
        title = stringResource(R.string.place_delete_title),
        body =
            stringResource(if (severalMiffs) R.string.place_delete_body_several else R.string.place_delete_body),
        confirmLabel = stringResource(if (state.deleting) R.string.place_deleting else R.string.place_delete_action),
        cancelLabel = stringResource(R.string.common_cancel),
        tone = DialogTone.SENSITIVE,
        busy = state.deleting,
        message = state.deleteMessage,
        onConfirm = actions.onDeleteConfirmed,
        onCancel = actions.onDeleteDismissed,
        content =
            if (severalMiffs) {
                {
                    // The place is deleted from EVERY Miff it is attached to: the person sees which ones (design 07B).
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(ShapeMd)
                                .background(colorBgCanvas)
                                .padding(Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        state.owned.filter { it.id in state.initial?.miffIds.orEmpty() }.forEach { miff ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_group),
                                    contentDescription = null,
                                    tint = colorActionPrimary,
                                    modifier = Modifier.size(Spacing.m),
                                )
                                Text(text = miff.name, style = textBodyRegular14, color = colorTextPrimary)
                            }
                        }
                    }
                }
            } else {
                null
            },
    )
}

// ---- Field -------------------------------------------------------------------------------------------------------------

/** A text field of the form: no floating label (the section header says what it is), a placeholder, optional icon and cross. */
@Composable
private fun PlaceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    leadingIcon: Int? = null,
    leadingTint: Color = colorTextDescription,
    onClear: (() -> Unit)? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
) {
    val clearLabel = stringResource(R.string.place_address_clear_a11y)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        placeholder = { Text(placeholder, style = textBodyRegular) },
        isError = isError,
        leadingIcon =
            leadingIcon?.let {
                {
                    Icon(
                        painter = painterResource(it),
                        contentDescription = null,
                        tint = leadingTint,
                        modifier = Modifier.size(Spacing.l),
                    )
                }
            },
        trailingIcon =
            onClear?.let {
                {
                    androidx.compose.material3.IconButton(onClick = it, enabled = enabled) {
                        Icon(
                            painter = painterResource(R.drawable.ic_loc_close),
                            contentDescription = clearLabel,
                            tint = colorTextDescription,
                            modifier = Modifier.size(Spacing.l),
                        )
                    }
                }
            },
        shape = ShapeMd,
        colors =
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
                focusedPlaceholderColor = colorTextDescription,
                unfocusedPlaceholderColor = colorTextDescription,
            ),
        textStyle = textBodyRegular,
        // A name and an address are private: no learning keyboard, no correction, no suggestion built from them.
        keyboardOptions =
            KeyboardOptions(
                capitalization = capitalization,
                autoCorrectEnabled = false,
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
        modifier = modifier.fillMaxWidth(),
    )
}
