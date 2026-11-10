package com.tomessa.miff.ui.profile

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.tomessa.miff.R
import com.tomessa.miff.domain.travel.TravelDay
import com.tomessa.miff.domain.travel.TravelPlan
import com.tomessa.miff.ui.miff.MiffConfirmDialog
import com.tomessa.miff.ui.miff.MiffNotice
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorActionPrimaryTint30
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorBorderStrong
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextOnAction
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular14
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** What the card asks of its screen (the view model of the Profile tab). */
class TravelActions(
    val onToggle: () -> Unit = {},
    val onChangeDate: () -> Unit = {},
    val onPickerDismissed: () -> Unit = {},
    val onDayChosen: (LocalDate) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onConfirmDismissed: () -> Unit = {},
)

/**
 * MODE VOYAGE, the card of the Profile (docs/tickets/mode-voyage.md section 7): a switch, then (when it is on) the line "Date de fin" which opens the date picker of the
 * system, and an honest explanation of what the loved ones see. Open to EVERY account: no plan, no offer. The whole switch row is ONE element for the screen reader
 * ("Mode voyage, interrupteur, activé, jusqu'au 15 novembre 2026"); the airplane is decorative, the state is said by words.
 */
@Composable
fun TravelCard(
    state: TravelUiState,
    actions: TravelActions,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val period = state.period
    val untilDay = period?.let { TravelPlan.parseDay(it.untilDay) }
    // The year is shown in the short form only when it is not the current one; the line "Date de fin" always has it.
    val shortDay = untilDay?.let { TravelDayFormat.short(TravelDay(it, it.year != LocalDate.now().year), locale) }
    val on = period != null
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .miffShadow(ShadowDefault, ShapeMd)
                    .clip(ShapeMd)
                    .background(colorBgSurface)
                    .border(StrokeHairline, colorBorderDefault, ShapeMd),
        ) {
            SwitchRow(
                on = on,
                working = state.working,
                shortDay = shortDay,
                untilDay = untilDay,
                onToggle = actions.onToggle,
            )
            if (on && untilDay != null && shortDay != null) {
                EndDateRow(
                    TravelDayFormat.withYear(untilDay, locale),
                    TravelDayFormat.spoken(untilDay, locale),
                    state.working,
                    actions.onChangeDate,
                )
                Text(
                    text = stringResource(R.string.voyage_card_explain, shortDay),
                    style = textBodyRegular14,
                    color = colorTextDescription,
                    modifier = Modifier.padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.m),
                )
            }
        }
        state.message?.let { MiffNotice(text = messageText(it), isError = it == TravelMessage.REFUSED) }
    }
    state.picker?.let { DayPicker(it, actions) }
    state.confirm?.let { confirm ->
        val day = TravelPlan.parseDay(confirm.untilDay)
        val text = day?.let { TravelDayFormat.short(TravelDay(it, it.year != LocalDate.now().year), locale) }.orEmpty()
        MiffConfirmDialog(
            title = stringResource(R.string.voyage_confirm_title),
            body = stringResource(R.string.voyage_confirm_body, text),
            confirmLabel = stringResource(R.string.voyage_confirm_action),
            cancelLabel = stringResource(R.string.voyage_confirm_cancel),
            onConfirm = actions.onConfirm,
            onCancel = actions.onConfirmDismissed,
        )
    }
}

@Composable
private fun messageText(message: TravelMessage): String =
    stringResource(
        when (message) {
            TravelMessage.RESUMED -> R.string.voyage_off_done
            TravelMessage.OFFLINE -> R.string.voyage_offline_note
            TravelMessage.REFUSED -> R.string.voyage_error_refused
        },
    )

@Composable
private fun SwitchRow(
    on: Boolean,
    working: Boolean,
    shortDay: String?,
    untilDay: LocalDate?,
    onToggle: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val state =
        if (on && untilDay != null) {
            stringResource(R.string.voyage_card_state_on_a11y, TravelDayFormat.spoken(untilDay, locale))
        } else {
            stringResource(R.string.voyage_card_state_off_a11y)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !working, role = Role.Switch, onClick = onToggle)
                .heightIn(min = Spacing.minTouchTarget)
                .padding(horizontal = Spacing.m, vertical = Spacing.s + Spacing.xs)
                .semantics(mergeDescendants = true) { stateDescription = state },
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The airplane is decorative: the state is said by the text.
        Box(
            modifier = Modifier.size(Spacing.xl + Spacing.s).clip(CircleShape).background(colorActionPrimaryTint20),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_travel_plane),
                contentDescription = null,
                tint = colorActionPrimary,
                modifier = Modifier.size(Spacing.l),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.voyage_card_title),
                style = textBodyBold16,
                color = colorTextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text =
                    if (on &&
                        shortDay != null
                    ) {
                        stringResource(R.string.voyage_card_on_status, shortDay)
                    } else {
                        stringResource(R.string.voyage_card_off_body)
                    },
                style = textBodyRegular14,
                color = colorTextDescription,
            )
        }
        // The row carries the click and the semantics: the switch itself is only the picture of the state.
        Switch(
            checked = on,
            onCheckedChange = null,
            enabled = !working,
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

@Composable
private fun EndDateRow(
    written: String,
    spoken: String,
    working: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.voyage_card_end_label)
    val change = stringResource(R.string.voyage_card_end_change_a11y)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !working, onClickLabel = change, role = Role.Button, onClick = onClick)
                .heightIn(min = Spacing.minTouchTarget)
                .padding(horizontal = Spacing.m, vertical = Spacing.xs)
                .semantics(mergeDescendants = true) { stateDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = textBodyRegular14, color = colorTextDescription, modifier = Modifier.weight(1f))
        Text(text = written, style = textBodyBold16, color = colorTextPrimary)
        Icon(
            painter = painterResource(R.drawable.ic_chevron_end),
            contentDescription = null,
            tint = colorActionPrimary,
        )
    }
}

/** The date picker of the system (Material): today at the earliest, today + 90 days at the latest. Days are those of the calendar of the phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPicker(
    picker: TravelPickerState,
    actions: TravelActions,
) {
    val selectable =
        remember(picker) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcDay(utcTimeMillis) in picker.minDay..picker.maxDay

                override fun isSelectableYear(year: Int): Boolean = year in picker.minDay.year..picker.maxDay.year
            }
        }
    val pickerState =
        rememberDatePickerState(
            initialSelectedDateMillis =
                picker.initialDay
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli(),
            selectableDates = selectable,
        )
    DatePickerDialog(
        onDismissRequest = actions.onPickerDismissed,
        confirmButton = {
            TextButton(
                onClick = { pickerState.selectedDateMillis?.let { actions.onDayChosen(utcDay(it)) } },
                enabled = pickerState.selectedDateMillis != null,
            ) { Text(stringResource(R.string.voyage_picker_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = actions.onPickerDismissed) { Text(stringResource(R.string.voyage_confirm_cancel)) }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

/** The calendar day of a date chosen in the Material picker (it hands over midnight UTC of the day). */
private fun utcDay(utcTimeMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
