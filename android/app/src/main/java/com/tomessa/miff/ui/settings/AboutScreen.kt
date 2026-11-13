package com.tomessa.miff.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.domain.about.AppVersion
import com.tomessa.miff.ui.auth.MessageText
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14

/**
 * Settings > "À propos et aide" (ticket `reglages-a-propos.md`, decisions of the PM of 2026-11-13; not in the mockups: drawn here with the template of the
 * Settings screens, as decided in Q8). REORGANISED on the PM's feedback of 2026-11-13 (order and emphasis, criterion AP20): the two actions the team wants people
 * to see and use come FIRST and stand out, everything else is lower and discreet.
 *
 *  1. TOP, two large filled cards with an icon, a clear title and a calm sentence: "Nous contacter" (moved here from Confidentialité, Q2) and "Un retour sur
 *     l'app ?" (the external form, drawn ONLY when its address is set, AP8, AP9);
 *  2. under a heading "Informations", plain list lines in the discreet style: the VERSION ("Version 0.40.0 (build 40000)", a touch copies "Miff 0.40.0 (40000)"
 *     and nothing else, AP3, Q7), the existing helps (Q6), the terms and the privacy policy (the links in Confidentialité stay, AP11) and "Licences et
 *     attributions" (AP12 to AP15).
 *
 * Nothing here reads or writes anything on the server, asks for a permission, counts an event or calls the network when it opens (AP18): only a touch on a link
 * leaves the app. [onOpenRemindersHelp] is null in the RESTRICTED Settings of a person with no Miff (nothing to remind without a Miff, like Settings >
 * Notifications): the line is not drawn.
 */
@Composable
fun AboutScreen(
    version: AppVersion,
    actions: AboutActions,
    onBack: () -> Unit,
    onOpenDetectionHelp: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenRemindersHelp: (() -> Unit)? = null,
) {
    var versionCopied by rememberSaveable { mutableStateOf(false) }
    var contactFailed by rememberSaveable { mutableStateOf(false) }
    var addressCopied by rememberSaveable { mutableStateOf(false) }
    var formFailed by rememberSaveable { mutableStateOf(false) }

    SettingsScaffold(title = stringResource(R.string.about_title), onBack = onBack) {
        // --- The two actions that stand out, at the TOP (AP20) ------------------------------------------------------------------------
        ActionCard(
            icon = R.drawable.ic_mail,
            title = stringResource(R.string.settings_privacy_contact),
            subtitle = stringResource(R.string.about_contact_subtitle),
            actionLabel = stringResource(R.string.settings_privacy_open_mail_a11y),
            onClick = {
                addressCopied = false
                contactFailed = !actions.openContact()
            },
        )
        if (contactFailed) {
            // No e-mail application: the card does not stay silent (AP6). The address comes from the build configuration, never from a translated text.
            MessageText(
                text = stringResource(R.string.about_contact_unavailable, actions.supportEmail),
                isError = false,
                modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.s),
            )
            MiffSecondaryButton(
                text = stringResource(R.string.about_contact_copy_action),
                onClick = { addressCopied = actions.copy(actions.supportEmail) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (addressCopied) {
                MessageText(
                    text = stringResource(R.string.about_copied),
                    isError = false,
                    modifier = Modifier.padding(top = Spacing.s),
                )
            }
        }
        if (actions.feedbackFormUrl != null) {
            Spacer(Modifier.size(Spacing.m))
            ActionCard(
                icon = R.drawable.ic_feed_chat,
                title = stringResource(R.string.about_feedback_title),
                subtitle = stringResource(R.string.about_feedback_subtitle),
                actionLabel = stringResource(R.string.about_feedback_hint_a11y),
                onClick = { formFailed = !actions.openFeedbackForm() },
            )
            // Said plainly, as in the iOS screen: the form is a website outside Miff, opened in the browser.
            Text(
                text = stringResource(R.string.about_feedback_note),
                style = textBodyRegular14,
                color = colorTextDescription,
                modifier = Modifier.padding(top = Spacing.xs, start = Spacing.xs, end = Spacing.xs),
            )
            if (formFailed) {
                MessageText(
                    text = stringResource(R.string.review_open_failed),
                    isError = false,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }

        // --- Everything else: lower, discreet, standard list lines ----------------------------------------------------------------------
        AboutSection(R.string.about_info_title)
        VersionRow(version = version, onCopy = { versionCopied = actions.copy(version.clipboardText) })
        if (versionCopied) {
            // Said in words (and announced by the screen reader): the system's own "copied" preview only exists on recent Android versions.
            MessageText(text = stringResource(R.string.about_copied), isError = false)
        }
        AboutRow(title = stringResource(R.string.settings_detection_help_title), onClick = onOpenDetectionHelp)
        if (onOpenRemindersHelp != null) {
            AboutRow(title = stringResource(R.string.notif_settings_help_link), onClick = onOpenRemindersHelp)
        }
        val browser = stringResource(R.string.settings_privacy_open_browser_a11y)
        AboutRow(
            title = stringResource(R.string.auth_consent_terms_link),
            actionLabel = browser,
            onClick = actions::openTerms,
        )
        AboutRow(
            title = stringResource(R.string.auth_consent_privacy_link),
            actionLabel = browser,
            onClick = actions::openPrivacyPolicy,
        )
        AboutRow(title = stringResource(R.string.about_licenses_title), onClick = onOpenLicenses)
    }
}

/**
 * A prominent action (AP20): a filled card in the pale blue of the design system, an icon tile, a bold title, a calm sentence and the chevron. One element for the
 * screen reader ("{title}, {sentence}, bouton"), at least 72 dp high, and it grows with the text (large fonts: nothing is cut).
 */
@Composable
private fun ActionCard(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorActionPrimaryTint20, ShapeMd)
                .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onClick)
                .heightIn(min = ActionCardMinHeight)
                .padding(horizontal = Spacing.m, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            modifier = Modifier.size(Spacing.xl + Spacing.s).background(colorBgSurface, ShapeMd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painter = painterResource(icon), contentDescription = null, tint = colorActionPrimary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = textBodyBold16, color = colorTextForm)
            Text(text = subtitle, style = textBodyRegular14, color = colorTextForm)
        }
        Icon(painter = painterResource(R.drawable.ic_chevron_end), contentDescription = null, tint = colorActionPrimary)
    }
}

private val ActionCardMinHeight = 72.dp

/** A heading of the screen (read as a heading by the screen reader), with room above it. */
@Composable
internal fun AboutSection(title: Int) {
    Spacer(Modifier.size(Spacing.l))
    Text(
        text = stringResource(title),
        style = textBodyBold16,
        color = colorTextForm,
        modifier = Modifier.padding(bottom = Spacing.xs).semantics { heading() },
    )
}

/** "Version 0.40.0 (build 40000)": ONE element for the screen reader, whose action is "copier la version" (AP3, AP17). Discreet style, like the lines around it. */
@Composable
private fun VersionRow(
    version: AppVersion,
    onCopy: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .clip(ShapeMd)
                .clickable(
                    onClickLabel = stringResource(R.string.about_version_copy_hint_a11y),
                    role = Role.Button,
                    onClick = onCopy,
                ).padding(vertical = Spacing.s),
    ) {
        Text(
            text = stringResource(R.string.about_version_label, version.name, version.build),
            style = textBodyRegular,
            color = colorTextForm,
        )
        Text(
            text = stringResource(R.string.android_about_copy_hint),
            style = textBodyRegular14,
            color = colorTextDescription,
        )
    }
}

/**
 * One DISCREET line of the screen: a regular title, an optional one-line help, and the chevron. One element for the screen reader ("{title}, {subtitle}, bouton");
 * at least 48 dp high, and it grows with the text (large fonts: nothing is cut). [actionLabel] says where a touch goes when it leaves the app ("ouvrir la page
 * dans ton navigateur").
 */
@Composable
internal fun AboutRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    actionLabel: String? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .clip(ShapeMd)
                .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onClick)
                .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = textBodyRegular, color = colorTextForm)
            if (subtitle != null) {
                Text(text = subtitle, style = textBodyRegular14, color = colorTextDescription)
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_end),
            contentDescription = null,
            tint = colorTextDescription,
        )
    }
}
