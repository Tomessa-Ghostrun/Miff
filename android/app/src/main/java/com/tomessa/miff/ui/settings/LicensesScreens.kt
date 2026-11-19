package com.tomessa.miff.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.tomessa.miff.R
import com.tomessa.miff.data.about.LicenseSource
import com.tomessa.miff.domain.about.LicenseEntry
import com.tomessa.miff.domain.about.LicenseGroup
import com.tomessa.miff.domain.about.LicenseInventory
import com.tomessa.miff.domain.about.LicenseTextFormat
import com.tomessa.miff.ui.auth.MessageText
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular14

/**
 * The sentences of the inventory that are NOT the name of a library (the lines of the maps and of the addresses, ticket section 5.2) are translated text: the
 * file `licenses.json` only holds their key, and this is where the key becomes a resource. A test checks that every key of the file is known here.
 */
internal object LicenseLabels {
    @StringRes
    fun label(key: String): Int? =
        when (key) {
            "android_about_licenses_map_engine" -> R.string.android_about_licenses_map_engine
            "android_about_licenses_map_tiles" -> R.string.android_about_licenses_map_tiles
            "android_about_licenses_address_search" -> R.string.android_about_licenses_address_search
            else -> null
        }

    @StringRes
    fun notice(key: String): Int? =
        when (key) {
            "android_about_licenses_map_tiles_notice" -> R.string.android_about_licenses_map_tiles_notice
            "android_about_licenses_address_search_notice" -> R.string.android_about_licenses_address_search_notice
            else -> null
        }
}

/** The line of an entry in the list: its sentence when it has one (maps, addresses), otherwise its name and, for ONE library, its version. */
@Composable
private fun titleOf(entry: LicenseEntry): String {
    val label = entry.labelKey?.let(LicenseLabels::label)
    return when {
        label != null -> stringResource(label)
        entry.version != null -> entry.name + " " + entry.version
        else -> entry.name
    }
}

/**
 * Settings > À propos et aide > "Licences et attributions" (ticket `reglages-a-propos.md`, section 5, criteria AP12 to AP15). It reads the app's own files: it opens
 * OFFLINE and sends nothing. Three groups (software, maps and addresses, fonts), one line each; a touch opens the full text of the licence ([LicenseDetailScreen]).
 * The attributions under the map and under the list of addresses stay where they are: this screen completes them, it does not replace them.
 */
@Composable
fun LicensesScreen(
    source: LicenseSource,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    val loaded by produceState<LicensesLoad>(LicensesLoad.Loading, source) {
        value = source.inventory()?.let { LicensesLoad.Ready(it) } ?: LicensesLoad.Failed
    }
    SettingsScaffold(title = stringResource(R.string.about_licenses_title), onBack = onBack) {
        when (val state = loaded) {
            LicensesLoad.Loading -> {
                Unit
            }

            LicensesLoad.Failed -> {
                MessageText(text = stringResource(R.string.android_about_licenses_unavailable), isError = false)
            }

            is LicensesLoad.Ready -> {
                Text(
                    text = stringResource(R.string.about_licenses_intro),
                    style = textBodyRegular14,
                    color = colorTextDescription,
                )
                LicenseGroupBlock(
                    state.inventory,
                    LicenseGroup.SOFTWARE,
                    R.string.about_licenses_software_title,
                    onOpenEntry,
                )
                LicenseGroupBlock(state.inventory, LicenseGroup.MAPS, R.string.about_licenses_maps_title, onOpenEntry)
                // What leaves the phone when an address is typed, said where the person reads about the services (criterion AP18's counterpart: the screen itself sends nothing).
                Text(
                    text = stringResource(R.string.about_licenses_maps_privacy),
                    style = textBodyRegular14,
                    color = colorTextDescription,
                    modifier = Modifier.padding(top = Spacing.s),
                )
                AboutRow(
                    title = stringResource(R.string.auth_consent_privacy_link),
                    actionLabel = stringResource(R.string.settings_privacy_open_browser_a11y),
                    onClick = onOpenPrivacyPolicy,
                )
                LicenseGroupBlock(state.inventory, LicenseGroup.FONTS, R.string.about_licenses_fonts_title, onOpenEntry)
                Spacer(Modifier.size(Spacing.m))
                Text(
                    text = stringResource(R.string.android_about_licenses_footnote),
                    style = textBodyRegular14,
                    color = colorTextDescription,
                )
            }
        }
    }
}

private sealed interface LicensesLoad {
    data object Loading : LicensesLoad

    data object Failed : LicensesLoad

    data class Ready(
        val inventory: LicenseInventory,
    ) : LicensesLoad
}

@Composable
private fun LicenseGroupBlock(
    inventory: LicenseInventory,
    group: LicenseGroup,
    @StringRes heading: Int,
    onOpenEntry: (String) -> Unit,
) {
    val entries = inventory.shown(group)
    if (entries.isEmpty()) return
    AboutSection(heading)
    entries.forEach { entry ->
        AboutRow(
            title = titleOf(entry),
            subtitle =
                inventory
                    .textsOf(entry)
                    .map { it.name }
                    .distinct()
                    .joinToString(" · ")
                    .ifEmpty { null },
            actionLabel = stringResource(R.string.about_licenses_open_hint_a11y),
            onClick = { onOpenEntry(entry.id) },
        )
    }
}

/**
 * The detail of one line: its name, what it includes, and the FULL text of each of its licences, set back in paragraphs ([LicenseTextFormat]), read from the app's own
 * files. For the attributions of data (maps, addresses) it is the notice instead. A text that cannot be read says so calmly; nothing is ever fetched.
 */
@Composable
fun LicenseDetailScreen(
    entryId: String,
    source: LicenseSource,
    onBack: () -> Unit,
) {
    val loaded by produceState<LicensesLoad>(LicensesLoad.Loading, source) {
        value = source.inventory()?.let { LicensesLoad.Ready(it) } ?: LicensesLoad.Failed
    }
    val inventory = (loaded as? LicensesLoad.Ready)?.inventory
    val entry = inventory?.entry(entryId)
    SettingsScaffold(
        title = entry?.let { titleOf(it) } ?: stringResource(R.string.about_licenses_title),
        onBack = onBack,
    ) {
        if (loaded == LicensesLoad.Loading) return@SettingsScaffold
        if (inventory == null || entry == null) {
            MessageText(text = stringResource(R.string.android_about_licenses_text_unavailable), isError = false)
            return@SettingsScaffold
        }
        entry.includes?.let { includes ->
            Text(
                text = stringResource(R.string.android_about_licenses_includes),
                style = textBodyBold16,
                color = colorTextForm,
            )
            Text(text = includes, style = textBodyRegular14, color = colorTextDescription)
            Spacer(Modifier.size(Spacing.m))
        }
        entry.noticeKey?.let(LicenseLabels::notice)?.let { notice ->
            Text(text = stringResource(notice), style = textBodyRegular14, color = colorTextForm)
        }
        inventory.textsOf(entry).forEach { license ->
            LicenseTextBlock(name = license.name, file = license.file, source = source)
        }
    }
}

@Composable
private fun LicenseTextBlock(
    name: String,
    file: String,
    source: LicenseSource,
) {
    val text by produceState<TextLoad>(TextLoad.Loading, file, source) {
        value = source.text(file)?.let { TextLoad.Ready(it) } ?: TextLoad.Failed
    }
    Text(
        text = name,
        style = textBodyBold16,
        color = colorTextForm,
        modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.xs).semantics { heading() },
    )
    when (val body = text) {
        TextLoad.Loading -> {
            Unit
        }

        TextLoad.Failed -> {
            MessageText(text = stringResource(R.string.android_about_licenses_text_unavailable), isError = false)
        }

        is TextLoad.Ready -> {
            Column {
                LicenseTextFormat.paragraphs(body.text).forEach { paragraph ->
                    Text(
                        text = paragraph,
                        style = textBodyRegular14,
                        color = colorTextForm,
                        modifier = Modifier.padding(bottom = Spacing.s),
                    )
                }
            }
        }
    }
}

private sealed interface TextLoad {
    data object Loading : TextLoad

    data object Failed : TextLoad

    data class Ready(
        val text: String,
    ) : TextLoad
}
