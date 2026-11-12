package com.tomessa.miff.ui.notification

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.tomessa.miff.R
import com.tomessa.miff.data.notification.NotificationTexts
import com.tomessa.miff.domain.notification.NotificationFamily
import com.tomessa.miff.domain.notification.NotificationPermissionState
import com.tomessa.miff.domain.notification.NotificationType
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.settings.SettingsScaffold
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint30
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderStrong
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14

/** What the buttons and switches of Settings > Notifications do. */
class NotificationSettingsActions(
    val onBack: () -> Unit = {},
    val onGeneralToggled: (Boolean) -> Unit = {},
    val onTypeToggled: (NotificationType, Boolean) -> Unit = { _, _ -> },
    val onNightCalmToggled: (Boolean) -> Unit = {},
    val onOpenPhoneSettings: () -> Unit = {},
    val onSeeNotifications: () -> Unit = {},
    val onOpenHelp: () -> Unit = {},
)

/**
 * Settings > Notifications (NOTIF-2A, ticket section 9 and 20.11). The wireframe of the ticket, in the style of the other Settings screens: the state of
 * the phone's permission first (the same words as in Settings > Confidentialité and the centre), the general switch, the five switches (all ON by
 * default) in two sections, "Nuit calme", then the way to the centre and the help for the phones that stop the apps.
 *
 * Honesty: a refusal of the phone is never hidden by a switch that looks "on" (the general switch is greyed and OFF, and the button says where to go).
 * Only what works is shown: there is no switch for what needs a server (N5, abandoned).
 */
@Composable
fun NotificationSettingsScreen(
    state: NotificationSettingsUiState,
    actions: NotificationSettingsActions,
) {
    SettingsScaffold(title = stringResource(R.string.notif_settings_title), onBack = actions.onBack) {
        SectionTitle(R.string.notif_settings_authorization, first = true)
        PermissionStateBlock(state.permission, onOpenPhoneSettings = actions.onOpenPhoneSettings)

        SwitchRow(
            title = stringResource(R.string.notif_settings_general),
            body = null,
            checked = state.generalChecked,
            enabled = state.generalEnabled,
            onCheckedChange = actions.onGeneralToggled,
        )

        SectionTitle(R.string.notif_settings_section_you)
        TypeRow(NotificationType.N1, state, actions)
        TypeRow(NotificationType.N2, state, actions)
        // MODE VOYAGE: the end of my trip (local, once per trip).
        TypeRow(NotificationType.N7, state, actions)
        ChannelBlockedNote(state.blocked(NotificationFamily.REMINDER), actions.onOpenPhoneSettings)

        SectionTitle(R.string.notif_settings_section_miff)
        TypeRow(NotificationType.N3, state, actions)
        TypeRow(NotificationType.N4, state, actions)
        // N6: turning it off cuts the notification, never what the Miff sees (said on the screen, ticket 20.11).
        TypeRow(NotificationType.N6, state, actions, body = stringResource(R.string.notif_settings_n6_hint))
        ChannelBlockedNote(state.blocked(NotificationFamily.CIRCLE), actions.onOpenPhoneSettings)

        // N9 (push of the server): "Quand un proche donne des nouvelles", ON by default; the switch is also what the server reads (notify.updates).
        SectionTitle(R.string.notif_settings_section_loved_ones)
        TypeRow(NotificationType.N9, state, actions, body = stringResource(R.string.notif_settings_circle_update_hint))
        ChannelBlockedNote(state.blocked(NotificationFamily.NEWS), actions.onOpenPhoneSettings)

        Spacer(Modifier.size(Spacing.m))
        SwitchRow(
            title = stringResource(R.string.notif_settings_night_title),
            body = stringResource(R.string.notif_settings_night_body),
            checked = state.preferences.nightCalm,
            enabled = state.typesEnabled,
            onCheckedChange = actions.onNightCalmToggled,
        )

        Spacer(Modifier.size(Spacing.m))
        LinkRow(stringResource(R.string.notif_settings_see_all), actions.onSeeNotifications)
        LinkRow(stringResource(R.string.notif_settings_help_link), actions.onOpenHelp)
    }
}

@Composable
private fun TypeRow(
    type: NotificationType,
    state: NotificationSettingsUiState,
    actions: NotificationSettingsActions,
    body: String? = null,
) {
    SwitchRow(
        title = stringResource(NotificationTexts.switchLabel(type)),
        body = body,
        checked = state.preferences.switchOf(type),
        enabled = state.typesEnabled,
        onCheckedChange = { actions.onTypeToggled(type, it) },
    )
}

@Composable
private fun SectionTitle(
    title: Int,
    first: Boolean = false,
) {
    if (!first) Spacer(Modifier.size(Spacing.m))
    Text(
        text = stringResource(title),
        style = textBodyBold16,
        color = colorTextForm,
        modifier = Modifier.padding(bottom = Spacing.xs).semantics { heading() },
    )
}

/**
 * The state of the permission of the phone, in words and an icon (never the colour alone). The button only when the phone refuses.
 * Shared with the Privacy screen so that the wording is the same everywhere (criterion N-A4).
 */
@Composable
fun PermissionStateBlock(
    state: NotificationPermissionState,
    onOpenPhoneSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(bottom = Spacing.s)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            if (state == NotificationPermissionState.ALLOWED) {
                Icon(
                    painter = painterResource(R.drawable.ic_loc_check),
                    contentDescription = null,
                    tint = colorActionPrimary,
                    modifier = Modifier.size(Spacing.m),
                )
            }
            Text(
                text =
                    stringResource(
                        when (state) {
                            NotificationPermissionState.ALLOWED -> R.string.notif_state_allowed
                            NotificationPermissionState.OFF -> R.string.notif_state_off
                            NotificationPermissionState.NOT_ASKED -> R.string.notif_state_notasked
                        },
                    ),
                style = textBodyBold14,
                color = colorTextForm,
            )
        }
        if (state == NotificationPermissionState.OFF) {
            Text(
                text = stringResource(R.string.notif_state_off_note),
                style = textBodyRegular14,
                color = colorTextDescription,
            )
            MiffSecondaryButton(
                text = stringResource(R.string.notif_state_open_settings),
                onClick = onOpenPhoneSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "Ce type est coupé dans le téléphone" under the rows of a family whose channel was turned off in the phone settings. */
@Composable
private fun ChannelBlockedNote(
    blocked: Boolean,
    onOpenPhoneSettings: () -> Unit,
) {
    if (!blocked) return
    Column(modifier = Modifier.padding(bottom = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.android_notif_channel_blocked),
            style = textBodyRegular14,
            color = colorTextDescription,
        )
        LinkRow(stringResource(R.string.notif_state_open_settings), onOpenPhoneSettings)
    }
}

/** One line with a switch: the row is the control (one element for the screen reader, "interrupteur, activé"), the switch only draws the state. */
@Composable
private fun SwitchRow(
    title: String,
    body: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget + Spacing.m)
                .clip(ShapeMd)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = textBodyBold16,
                color = if (enabled) colorTextForm else colorTextDescription,
            )
            if (body != null) Text(text = body, style = textBodyRegular14, color = colorTextDescription)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = colorBgSurface,
                    checkedTrackColor = colorActionPrimary,
                    uncheckedThumbColor = colorBgSurface,
                    uncheckedTrackColor = colorBorderStrong,
                    uncheckedBorderColor = colorBorderStrong,
                    disabledCheckedTrackColor = colorActionPrimaryTint30,
                    disabledUncheckedTrackColor = colorBorderStrong.copy(alpha = 0.3f),
                    disabledUncheckedBorderColor = colorBorderStrong.copy(alpha = 0.3f),
                    disabledCheckedThumbColor = colorBgSurface,
                    disabledUncheckedThumbColor = colorBgSurface,
                ),
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** A line that opens another screen (or the settings of the phone). */
@Composable
internal fun LinkRow(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = textBodyRegular,
        color = colorActionPrimary,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .clip(ShapeMd)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = Spacing.s),
    )
}
