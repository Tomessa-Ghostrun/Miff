package com.tomessa.miff.domain.about

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the screen Settings > "À propos et aide" > "Licences et attributions" shows (ticket reglages-a-propos.md, section 5): the inventory of what the app of
 * release ships and under which licence. It is read from the app's own files (`assets/licenses/licenses.json` and the `.txt` files of `assets/licenses/texts`), so the screen
 * opens OFFLINE (criterion AP12): nothing is fetched, nothing is sent.
 *
 * The same file is read by the CI guard `checkLicenses` (`android/gradle/licenses-guard.gradle.kts`, criterion AP15), which fails when a library shipped in the
 * release build is covered by no entry: the screen cannot silently become false. It never says "conforme" nor "relu par un juriste" (decision of the PM, 2026-10-23).
 */
@Serializable
data class LicenseInventory(
    val schema: Int,
    val texts: Map<String, LicenseText>,
    val entries: List<LicenseEntry>,
) {
    /** The entries of one group that are shown, in the order of the file. Entries that are not shipped (the review window) are never shown (criterion AP14). */
    fun shown(group: LicenseGroup): List<LicenseEntry> = entries.filter { it.shipped && it.group == group.key }

    /** The licence texts of an entry, in the order of the file. A text that is missing from [texts] is skipped (a test fails first). */
    fun textsOf(entry: LicenseEntry): List<LicenseText> = entry.licenses.mapNotNull { texts[it] }

    fun entry(id: String): LicenseEntry? = entries.firstOrNull { it.id == id && it.shipped }

    /** True when a Maven module ("group:artifact") is covered by an entry: its coordinate STARTS WITH one of the prefixes of the entry. */
    fun covers(coordinate: String): Boolean = entries.any { entry -> entry.modules.any { coordinate.startsWith(it) } }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): LicenseInventory = json.decodeFromString(serializer(), text)
    }
}

/** The name of a licence and the file (under `assets/licenses/texts/`) that holds its full text. */
@Serializable
data class LicenseText(
    val name: String,
    val file: String,
)

@Serializable
data class LicenseEntry(
    val id: String,
    val group: String,
    val name: String,
    /** The version shown next to the name, only when the entry is ONE library (null for a family such as AndroidX). */
    val version: String? = null,
    /** The Maven module whose resolved version [version] must be (the CI guard only warns when it differs). */
    val versionOf: String? = null,
    /** Keys of [LicenseInventory.texts]. Empty for the attributions of data (maps, addresses), which carry a [noticeKey] instead. */
    val licenses: List<String> = emptyList(),
    /** What the entry includes, in plain names (not translated: they are the names of the libraries). */
    val includes: String? = null,
    /** The key of the line shown in the list instead of [name], when the line is a sentence (maps and addresses): resolved by the screen. */
    val labelKey: String? = null,
    /** The key of the notice of an attribution of data, resolved by the screen. */
    val noticeKey: String? = null,
    /** Prefixes of "group:artifact" this entry covers (the CI guard checks every module of the release build against them). */
    val modules: List<String> = emptyList(),
    /** False for what the release build does NOT contain (it is listed only so that the CI guard knows it): never shown. */
    val shipped: Boolean = true,
    val reason: String? = null,
)

enum class LicenseGroup(
    val key: String,
) {
    SOFTWARE("software"),
    MAPS("maps"),
    FONTS("fonts"),
}
