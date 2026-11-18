package com.tomessa.miff.domain.about

import com.tomessa.miff.ui.settings.LicenseLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The inventory behind the screen "Licences et attributions" (ticket reglages-a-propos.md, AP12 to AP15), checked on the REAL files of the app
 * (`src/main/assets/licenses`): every licence it names has its full text, the text is what the fonts and libraries ship, the lines of the maps are the ones of
 * the ticket, nothing that is not in the release build is shown, and a library added to the dependencies without a licence entry FAILS here (AP15, second line
 * of defence: the first one is the Gradle task `checkLicenses`, which checks the whole resolved graph of the release build).
 */
class LicenseInventoryTest {
    private val assets = File("src/main/assets/licenses")
    private val inventory: LicenseInventory by lazy { LicenseInventory.parse(File(assets, "licenses.json").readText()) }

    private fun textOf(file: String): String = File(assets, "texts/$file").readText()

    @Test
    fun everyLicenceNamedByAnEntry_existsAndHasItsFullTextInTheApp() {
        assertTrue(inventory.entries.isNotEmpty())
        inventory.entries.forEach { entry ->
            entry.licenses.forEach { id ->
                val license = inventory.texts[id]
                assertNotNull("entry ${entry.id}: licence \"$id\" is not in \"texts\"", license)
                val file = File(assets, "texts/${license!!.file}")
                assertTrue("entry ${entry.id}: ${file.path} is missing", file.isFile)
                assertTrue("entry ${entry.id}: ${file.path} is too short to be a licence", file.readText().length > 400)
            }
        }
        // And no text is left lying around without a line that uses it.
        val used =
            inventory.texts
                .filterKeys { id ->
                    inventory.entries.any { id in it.licenses }
                }.values
                .map { it.file }
                .toSet()
        val onDisk =
            File(assets, "texts")
                .listFiles()
                .orEmpty()
                .map { it.name }
                .toSet()
        assertEquals("licence texts that no entry uses", emptySet<String>(), onDisk - used)
    }

    @Test
    fun theApacheTextIsTheOneOfTheApache20Licence() {
        val apache = textOf(inventory.texts.getValue("apache-2.0").file)
        assertTrue(apache.contains("Apache License"))
        assertTrue(apache.contains("Version 2.0, January 2004"))
        assertTrue(apache.contains("END OF TERMS AND CONDITIONS"))
    }

    @Test
    fun theFontsShowTheirOwnCopyrightAndLicence_copiedFromThirdParty() {
        val byteOrderMark = 0xFEFF.toChar().toString()
        val fonts = listOf("OFL-BalooThambi2.txt" to "ofl-baloo", "OFL-HankenGrotesk.txt" to "ofl-hanken")
        fonts.forEach { (original, id) ->
            val shipped = textOf(inventory.texts.getValue(id).file)
            val source = File("../third_party/fonts/$original").readText().removePrefix(byteOrderMark)
            assertEquals("$original is copied unchanged (the byte-order mark apart)", source.trim(), shipped.trim())
            assertTrue(shipped.contains("SIL OPEN FONT LICENSE Version 1.1"))
        }
        assertTrue(textOf("ofl-baloo-thambi-2.txt").startsWith("Copyright 2019 The Baloo 2 Project Authors"))
        assertTrue(textOf("ofl-hanken-grotesk.txt").startsWith("Copyright 2021 The Hanken Grotesk Project Authors"))
        assertEquals(listOf("Baloo Thambi 2", "Hanken Grotesk"), inventory.shown(LicenseGroup.FONTS).map { it.name })
    }

    // --- AP13: the lines of the maps and addresses, Android only ----------------------------------------------------------------

    @Test
    fun theMapsGroup_hasTheThreeLinesOfTheTicket_withTheWordsOfTheTicket() {
        val maps = inventory.shown(LicenseGroup.MAPS)
        assertEquals(listOf("maplibre", "openfreemap", "photon"), maps.map { it.id })
        val french = frenchStrings()
        assertEquals("Moteur de carte : MapLibre Native", french["android_about_licenses_map_engine"])
        assertEquals(
            "Carte : OpenFreeMap, © OpenMapTiles, données © contributeurs OpenStreetMap",
            french["android_about_licenses_map_tiles"],
        )
        assertEquals(
            "Adresses : service Photon de komoot, données © contributeurs OpenStreetMap",
            french["android_about_licenses_address_search"],
        )
        maps.forEach { assertNotNull("label of ${it.id}", it.labelKey?.let(LicenseLabels::label)) }
    }

    @Test
    fun maplibre_showsTheTextOfItsLicenceAndTheCopyrightOfMapLibre() {
        val maplibre = inventory.entry("maplibre")!!
        val text = inventory.textsOf(maplibre).joinToString("\n") { textOf(it.file) }
        assertTrue(text.contains("BSD 2-Clause License"))
        assertTrue(text.contains("Copyright (c) 2021 MapLibre contributors"))
    }

    @Test
    fun everyKeyOfTheFile_isKnownByTheScreen() {
        inventory.entries.forEach { entry ->
            entry.labelKey?.let { assertNotNull("label key $it", LicenseLabels.label(it)) }
            entry.noticeKey?.let { assertNotNull("notice key $it", LicenseLabels.notice(it)) }
            // An entry with no licence text must have a notice instead: there would be nothing to show.
            if (entry.shipped &&
                entry.licenses.isEmpty()
            ) {
                assertNotNull("entry ${entry.id} shows nothing", entry.noticeKey)
            }
        }
    }

    // --- AP14: only what the release build ships ----------------------------------------------------------------------------------

    @Test
    fun whatIsNotInTheReleaseBuild_isNeverShown() {
        val shown = LicenseGroup.entries.flatMap { inventory.shown(it) }
        assertFalse("the review window is not shipped", shown.any { it.id == "play-review" })
        assertNotNull(
            "but the guard still knows it",
            inventory.entries.firstOrNull {
                it.id == "play-review" &&
                    !it.shipped
            },
        )
        val words =
            Regex("junit|robolectric|espresso|mockk|ui-tooling|appcheck-debug|review|test", RegexOption.IGNORE_CASE)
        shown.forEach { entry ->
            val written = listOfNotNull(entry.name, entry.includes, entry.version, entry.labelKey)
            written.forEach {
                assertFalse(
                    "entry ${entry.id} names something that is not shipped: $it",
                    words.containsMatchIn(it),
                )
            }
        }
        assertEquals(null, inventory.entry("play-review"))
    }

    // --- AP15: a library without a licence fails ---------------------------------------------------------------------------------

    @Test
    fun aLibraryWithNoEntry_isNotCovered() {
        assertTrue(inventory.covers("androidx.core:core-ktx"))
        assertTrue(inventory.covers("org.maplibre.gl:android-sdk-opengl"))
        assertFalse(
            "a brand new dependency must be written in licenses.json first",
            inventory.covers("com.example:brand-new-library"),
        )
        assertFalse(inventory.covers("org.maplibre:other-group"))
    }

    @Test
    fun everyLibraryTheAppDeclaresForTheReleaseBuild_hasALicenceEntry() {
        val catalog = File("../gradle/libs.versions.toml").readLines()
        val modules =
            catalog
                .mapNotNull { Regex("""^([A-Za-z0-9_-]+)\s*=\s*\{\s*module\s*=\s*"([^"]+)"""").find(it) }
                .associate { it.groupValues[1] to it.groupValues[2] }
        assertTrue("the version catalog was not read", modules.size > 20)
        val build = File("build.gradle.kts").readText()
        val declared =
            Regex(
                """(?<![A-Za-z])(?:implementation|api|releaseImplementation|runtimeOnly)\((?:platform\()?libs\.([A-Za-z0-9.]+)\)""",
            ).findAll(build)
                .map { it.groupValues[1].replace('.', '-') }
                .toSet()
        assertTrue("no dependency was read from build.gradle.kts", declared.size > 20)
        declared.forEach { alias ->
            val module = modules[alias]
            assertNotNull("libs.$alias is not in libs.versions.toml", module)
            assertTrue(
                "$module is shipped in the release build but has no licence entry: write it in assets/licenses/licenses.json (ticket reglages-a-propos.md, AP15)",
                inventory.covers(module!!),
            )
        }
    }

    // --- the text of the lines ----------------------------------------------------------------------------------------------------

    @Test
    fun reflowingATextNeverChangesItsWords() {
        inventory.texts.values.forEach { license ->
            val original = textOf(license.file)
            val reflowed = LicenseTextFormat.paragraphs(original).joinToString(" ")
            assertEquals(license.file, words(original), words(reflowed))
        }
    }

    private fun words(text: String): List<String> = text.split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun frenchStrings(): Map<String, String> =
        Regex("""<string name="([a-z0-9_]+)"[^>]*>([^<]*)</string>""")
            .findAll(File("src/main/res/values-fr/strings.xml").readText())
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\'", "'") }
}
