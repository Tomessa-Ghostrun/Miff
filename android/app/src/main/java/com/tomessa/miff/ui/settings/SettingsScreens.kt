package com.tomessa.miff.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.domain.settings.AppLanguage
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorBgCanvas
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderStrong
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textDisplayTitle

/**
 * Frame of the Settings screens (mockups "reglages"): a white bar with the back arrow at the start and the title centred,
 * then the content, which scrolls (large fonts, landscape, keyboard). The tab bar is not shown here, like in the mockups.
 * [paneTitle] names the screen for the screen reader.
 */
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colorBgCanvas)
                .semantics { paneTitle = title },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .miffShadow(ShadowDefault, RectangleShape)
                    .background(colorBgSurface)
                    .statusBarsPadding()
                    .heightIn(min = TopBarHeight),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart).size(Spacing.minTouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.auth_back_a11y),
                    tint = colorActionPrimary,
                )
            }
            Text(
                text = title,
                style = textDisplayTitle,
                color = colorTextForm,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .padding(horizontal = Spacing.minTouchTarget + Spacing.xs)
                        .semantics { heading() },
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(HorizontalAndBottom))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.l, vertical = Spacing.m),
            content = content,
        )
    }
}

private val TopBarHeight = 56.dp

private val HorizontalAndBottom = WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom

/** One line of the Settings list: a pale blue tile with the icon, the title and its one-line help, and the chevron. */
@Composable
private fun SettingsRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .heightIn(min = RowMinHeight)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {}
                .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            modifier = Modifier.size(Spacing.xl + Spacing.s).background(colorActionPrimaryTint20, ShapeMd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painter = painterResource(icon), contentDescription = null, tint = colorActionPrimary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = textBodyBold16, color = colorTextForm)
            Text(text = subtitle, style = textBodyRegular14, color = colorTextDescription)
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_end),
            contentDescription = null,
            tint = colorTextDescription,
        )
    }
}

private val RowMinHeight = Spacing.minTouchTarget + Spacing.l

/**
 * Settings list (mockup "reglages", ticket ONB-3a, criterion A4): My information, My account, Language, Privacy (ONB-3b). Nothing else: no
 * Notifications line (nothing is sent yet), no subscription or Premium banner (nothing can be bought yet), no "Supprimer mon
 * compte" (it arrives with the account deletion ticket).
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenMyInfo: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    SettingsScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        SettingsRow(
            icon = R.drawable.ic_edit,
            title = stringResource(R.string.settings_my_info_title),
            subtitle = stringResource(R.string.settings_my_info_subtitle),
            onClick = onOpenMyInfo,
        )
        SettingsRow(
            icon = R.drawable.ic_person,
            title = stringResource(R.string.settings_account_title),
            subtitle = stringResource(R.string.settings_account_subtitle),
            onClick = onOpenAccount,
        )
        SettingsRow(
            icon = R.drawable.ic_flag,
            title = stringResource(R.string.settings_language_title),
            subtitle = stringResource(R.string.settings_language_subtitle),
            onClick = onOpenLanguage,
        )
        SettingsRow(
            icon = R.drawable.ic_lock,
            title = stringResource(R.string.settings_privacy_title),
            subtitle = stringResource(R.string.settings_privacy_subtitle),
            onClick = onOpenPrivacy,
        )
    }
}

/**
 * Settings > Langue (mockup "reglages -5", ticket ONB-3a, criterion A9). Three choices, each name written in its own
 * language so that it can be found whatever language the app is in. A tap applies the choice at once: there is no
 * "Enregistrer" button (decision Q4).
 */
@Composable
fun LanguageScreen(
    selected: AppLanguage,
    onBack: () -> Unit,
    onSelect: (AppLanguage) -> Unit,
) {
    SettingsScaffold(title = stringResource(R.string.settings_language_title), onBack = onBack) {
        Column(modifier = Modifier.selectableGroup()) {
            AppLanguage.entries.forEach { language ->
                LanguageRow(
                    label =
                        when (language) {
                            AppLanguage.FRENCH -> stringResource(R.string.settings_language_french)
                            AppLanguage.ENGLISH -> stringResource(R.string.settings_language_english)
                            AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
                        },
                    selected = language == selected,
                    onClick = { onSelect(language) },
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget + Spacing.s)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = if (selected) textBodyBold16 else textBodyRegular,
            color = if (selected) colorTextForm else colorTextDescription,
            modifier = Modifier.weight(1f),
        )
        // The row is the control (one element for the screen reader): the radio button only draws the state.
        RadioButton(
            selected = selected,
            onClick = null,
            colors =
                RadioButtonDefaults.colors(
                    selectedColor = colorActionPrimary,
                    unselectedColor = colorBorderStrong,
                ),
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
