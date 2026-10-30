package com.tomessa.miff.ui.settings

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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.tomessa.miff.R
import com.tomessa.miff.ui.auth.LegalLinks
import com.tomessa.miff.ui.auth.MessageText
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint30
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderStrong
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14

/**
 * Settings > Confidentialité (ticket ONB-3b, criteria B5 and B6; not in the mockups, drawn here from the ticket): the ONE
 * switch for anonymous statistics and crash reports, what the app does with the location and the notifications (said
 * honestly: nothing yet), the links to the terms and the privacy policy (web pages of AUTH-W, in the language of the app),
 * and the contact. The location is never part of anything that is collected.
 */
@Composable
fun PrivacyScreen(
    state: PrivacyUiState,
    onBack: () -> Unit,
    onAnalyticsToggled: (Boolean) -> Unit,
    onRetryWithdrawal: () -> Unit,
) {
    val context = LocalContext.current
    SettingsScaffold(title = stringResource(R.string.settings_privacy_title), onBack = onBack) {
        AnalyticsSwitch(state, onAnalyticsToggled)
        state.message?.let { PrivacyMessageText(it) }
        if (state.withdrawalPending) {
            // The collection is off and the device remembers the choice: said calmly, as long as the account does not know it.
            MessageText(
                text = stringResource(R.string.settings_privacy_withdrawal_not_recorded),
                isError = false,
                modifier = Modifier.padding(bottom = Spacing.s),
            )
            MiffSecondaryButton(
                text = stringResource(R.string.settings_privacy_retry_action),
                onClick = onRetryWithdrawal,
                enabled = !state.isWriting,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Section(R.string.settings_privacy_location_title)
        Text(
            text = stringResource(R.string.settings_privacy_location_body),
            style = textBodyRegular14,
            color = colorTextDescription,
        )
        OpenPhoneSettings { ExternalLinks.openPhoneSettings(context) }

        Section(R.string.settings_privacy_notifications_title)
        Text(
            text = stringResource(R.string.settings_privacy_notifications_body),
            style = textBodyRegular14,
            color = colorTextDescription,
        )
        OpenPhoneSettings { ExternalLinks.openNotificationSettings(context) }

        Section(R.string.settings_privacy_texts_title)
        val browser = stringResource(R.string.settings_privacy_open_browser_a11y)
        LinkRow(stringResource(R.string.auth_consent_terms_link), browser) {
            LegalLinks.open(context, LegalLinks.termsUrl(context))
        }
        LinkRow(stringResource(R.string.auth_consent_privacy_link), browser) {
            LegalLinks.open(context, LegalLinks.privacyUrl(context))
        }
        LinkRow(
            stringResource(R.string.settings_privacy_contact),
            stringResource(R.string.settings_privacy_open_mail_a11y),
        ) {
            ExternalLinks.contactSupport(context)
        }
    }
}

@Composable
private fun Section(title: Int) {
    Spacer(Modifier.size(Spacing.l))
    Text(
        text = stringResource(title),
        style = textBodyBold16,
        color = colorTextForm,
        modifier = Modifier.padding(bottom = Spacing.xs).semantics { heading() },
    )
}

/** The statistics line: one switch, one element for the screen reader ("... , interrupteur, activé"). */
@Composable
private fun AnalyticsSwitch(
    state: PrivacyUiState,
    onToggled: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget + Spacing.l)
                .clip(ShapeMd)
                .toggleable(
                    value = state.analyticsEnabled,
                    enabled = !state.isWriting,
                    role = Role.Switch,
                    onValueChange = onToggled,
                ).padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_privacy_analytics_title),
                style = textBodyBold16,
                color = colorTextForm,
            )
            Text(
                text = stringResource(R.string.settings_privacy_analytics_body),
                style = textBodyRegular14,
                color = colorTextDescription,
            )
        }
        // The row is the control: the switch only draws the state.
        Switch(
            checked = state.analyticsEnabled,
            onCheckedChange = null,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = colorBgSurface,
                    checkedTrackColor = colorActionPrimary,
                    uncheckedThumbColor = colorBgSurface,
                    uncheckedTrackColor = colorBorderStrong,
                    uncheckedBorderColor = colorBorderStrong,
                    disabledCheckedTrackColor = colorActionPrimaryTint30,
                ),
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun PrivacyMessageText(message: PrivacyMessage) {
    MessageText(
        text =
            stringResource(
                when (message) {
                    PrivacyMessage.NETWORK -> R.string.auth_error_network
                    PrivacyMessage.SAVE_FAILED -> R.string.profile_save_failed
                },
            ),
        isError = true,
        modifier = Modifier.padding(bottom = Spacing.s),
    )
}

@Composable
private fun OpenPhoneSettings(onClick: () -> Unit) {
    MiffSecondaryButton(
        text = stringResource(R.string.settings_privacy_open_phone_settings),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A line that opens something outside the app; the screen reader is told where it goes. */
@Composable
private fun LinkRow(
    label: String,
    actionLabel: String,
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
                .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onClick)
                .padding(vertical = Spacing.s),
    )
}
