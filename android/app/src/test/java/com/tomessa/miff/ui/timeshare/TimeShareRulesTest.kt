package com.tomessa.miff.ui.timeshare

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.tomessa.miff.ui.theme.chartPlaceColors
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorChartDanger
import com.tomessa.miff.ui.theme.colorChartElsewhere
import com.tomessa.miff.ui.theme.colorChartOther
import com.tomessa.miff.ui.theme.colorChartReserve
import com.tomessa.miff.ui.theme.colorStatusDanger
import com.tomessa.miff.ui.theme.colorStatusDangerText
import com.tomessa.miff.ui.theme.colorStatusSafeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

/**
 * The rules of docs/tickets/profil-repartition-temps.md that are about WHERE things are, not what they compute (checked on the sources, like the other rule tests of the app):
 * nothing leaves the phone (TS-1, TS-4), the order of the Profile (TS-12), the way out of every session (TS-5), the tool of test data is in the debug sources only (TS-23), no
 * chart library (TS-24), no colour or text written in a screen (TS-22), the palette (section 9.2), the texts (TS-15, TS-17, TS-22).
 */
class TimeShareRulesTest {
    private val main = File("src/main/java/com/tomessa/miff")
    private val timeShareSources =
        listOf("domain/timeshare", "data/timeshare", "ui/timeshare").flatMap { dir ->
            File(main, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }

    private fun code(file: File): String =
        file
            .readLines()
            .filterNot {
                it.trim().startsWith("*") || it.trim().startsWith("//") ||
                    it.trim().startsWith("/*")
            }.joinToString("\n")

    private fun read(path: String) = File(path).readText()

    // --- Nothing leaves the phone (TS-1, TS-2, TS-4, TS-25) ----------------------------------------------------------------------------------------------

    @Test
    fun theChartAndItsJournal_touchNoFirestore_noAnalytics_noLog_noNetwork() {
        assertTrue(timeShareSources.size >= 8)
        timeShareSources.forEach { file ->
            val text = code(file)
            assertFalse(
                "${file.name} talks to Firebase",
                Regex("com\\.google\\.firebase|FirebaseFirestore|FirebaseAnalytics|Crashlytics").containsMatchIn(text),
            )
            assertFalse(
                "${file.name} sends an analytics event",
                Regex("(?i)analytics|AnalyticsTracker|trackEvent").containsMatchIn(text),
            )
            assertFalse(
                "${file.name} logs",
                Regex("android\\.util\\.Log|Log\\.[dievw]\\(|Timber|println\\(").containsMatchIn(text),
            )
            assertFalse(
                "${file.name} uses the network",
                Regex("java\\.net|okhttp|HttpURLConnection|NetworkMonitor|ConnectivityManager").containsMatchIn(text),
            )
            assertFalse(
                "${file.name} handles a coordinate",
                Regex("\\blat\\b|\\blng\\b|latitude|longitude|Fix\\b|PositionSource").containsMatchIn(text),
            )
        }
    }

    @Test
    fun theTrackingSource_readsOnlyTheLocalCopyAndThePermissions_neverAPosition() {
        val text = code(File(main, "data/timeshare/EngineTimeShareTracking.kt"))
        assertTrue(text.contains("local.load()"))
        assertFalse(Regex("PositionSource|FusedLocation|LocationManager|requestLocationUpdates").containsMatchIn(text))
    }

    @Test
    fun theJournalSharesNoCodeWithTheDetectionJournal() {
        val log =
            code(File(main, "domain/timeshare/PresenceLog.kt")) +
                code(File(main, "data/timeshare/StoredPresenceLog.kt"))
        assertFalse(Regex("DetectionJournal|JournalKind|NoDetectionJournal|DetectionJournals").containsMatchIn(log))
    }

    // --- The order of the Profile (TS-12, TS-19b) ----------------------------------------------------------------------------------------------------------

    @Test
    fun theCard_isBetweenTheCarouselAndThePlaces_andTheTravelCardStaysLast_andTheRecentsAreStillHidden() {
        val source = read("src/main/java/com/tomessa/miff/ui/profile/ProfileTabScreen.kt")
        val carousel = source.indexOf("        MiffCarouselSection(miffs, carousel, onOpenMiff)")
        val card = source.indexOf("        timeShareContent()")
        val places = source.indexOf("        placesContent()")
        val travel = source.indexOf("        travelContent()")
        assertTrue(carousel in 1 until card)
        assertTrue(card in 1 until places)
        assertTrue(places in 1 until travel)
        assertTrue("the app's own mask of the recents stays on the tab", source.contains("    RecentsPrivacy()"))
        // The Profile hands the card its Miff and its places as the places section does: the Miff of the carousel, the places already read.
        val shell = read("src/main/java/com/tomessa/miff/ui/main/MainShell.kt")
        assertTrue(shell.contains("miffId = profilePlaces.selectedMiffId"))
        assertTrue(shell.contains("(profilePlaces.view as? MiffPlacesView.Ready)?.places"))
    }

    // --- The way out of every session (TS-5) --------------------------------------------------------------------------------------------------------------

    @Test
    fun theJournalAndTheWindow_goWithTheSession_theAccount_andTheMiff() {
        val container = read("src/main/java/com/tomessa/miff/AppContainer.kt")
        // The engine writes into it and erases it in its own stop.
        assertTrue(container.contains("presenceLog = presenceLog,"))
        val engine = read("src/main/java/com/tomessa/miff/data/tracking/TrackingEngine.kt")
        assertTrue(
            engine
                .substringAfter(
                    "private suspend fun doStop()",
                ).substringBefore("private fun logPresence")
                .contains("presenceLog.clear()"),
        )
        // Sign-out, deletion of the account, session lost: the teardown of the session and the erasers of the account.
        assertTrue(Regex("presenceLog\\.clear\\(\\)\\s+timeShareWindow\\.clear\\(\\)").containsMatchIn(container))
        assertTrue(Regex("reviewStateStore,\\s+presenceLog,\\s+timeShareWindow").containsMatchIn(container))
        // ... and the teardown that does it is in the list of the session.
        assertTrue(Regex("reviewStateStore\\.clear\\(\\) \\},\\s+timeShareTeardown,").containsMatchIn(container))
        // A Miff that is left on purpose, or that is gone from the list the server confirmed.
        assertTrue(container.contains("presenceLog.forgetMiff(miffId)"))
        assertTrue(container.contains("if (!state.fromCache) presenceLog.retainOnly("))
        // The button of Settings.
        val shell = read("src/main/java/com/tomessa/miff/ui/main/MainShell.kt")
        assertTrue(shell.contains("presenceLog = container.presenceLog"))
        assertTrue(shell.contains("onEraseTimeHistory = viewModel::onEraseTimeHistory"))
    }

    @Test
    fun theEngineTellsTheJournalAtEveryPlaceWherePresenceChanges() {
        val engine = read("src/main/java/com/tomessa/miff/data/tracking/TrackingEngine.kt")
        // One entry point for every change (never recalculated elsewhere): the arrival and departure, the restart, the resumed sharing, the moved place, the stop of the tracking, the trip.
        val calls = Regex("logPresence\\(|logUnknown\\(").findAll(engine).count()
        assertTrue("expected the journal to be told at every change of presence, found $calls call sites", calls >= 9)
        assertTrue(engine.contains("logPresence(miffId, derived.presence, derived.placeId, at ?: now)"))
        assertTrue(engine.contains("logPresence(miffId, fact.presence, fact.placeId, fact.presenceAtMillis)"))
    }

    // --- No tool of test data anywhere (removed after the recette of the PM) -----------------------------------------------------------------------

    @Test
    fun noToolOfTestData_isLeftInTheSources_orTheCi() {
        val markers = listOf("TIMESHAREDEMO", "DemoPresence", "TimeShareDemoTools")
        File("src")
            .walkTopDown()
            .filter {
                it.isFile && it.extension in setOf("kt", "xml") &&
                    it.name != "TimeShareRulesTest.kt"
            }.forEach { file ->
                markers.forEach { assertFalse("${file.name} still mentions $it", file.readText().contains(it)) }
            }
        assertFalse(read("../../.github/workflows/android.yml").contains("TIMESHAREDEMO"))
        assertFalse(read("../ci/check-release-bundle.sh").contains("TIMESHAREDEMO"))
    }

    // --- No chart library, no payment (TS-24) --------------------------------------------------------------------------------------------------------------

    @Test
    fun noChartLibrary_isInTheBuild() {
        val build =
            read("build.gradle.kts") + File("../gradle/libs.versions.toml").takeIf { it.exists() }?.readText().orEmpty()
        assertFalse(
            Regex(
                "(?i)mpandroidchart|philjay|vico|compose-charts|ycharts|aachartmodel|charts-compose|patrykandpatrick|chartjs",
            ).containsMatchIn(build),
        )
        // And nothing in the sources imports one.
        main.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach {
            assertFalse(
                "${it.name} imports a chart library",
                Regex(
                    "import (com\\.github\\.mikephil|com\\.patrykandpatrick|co\\.yml|io\\.github\\..*chart)",
                ).containsMatchIn(it.readText()),
            )
        }
    }

    // --- No colour and no text written in the screen (TS-22) ------------------------------------------------------------------------------------------------

    @Test
    fun theScreen_writesNoColourAndNoVisibleText_onlyTokensAndResources() {
        listOf("ui/timeshare/TimeShareCard.kt", "ui/timeshare/TimeShareViewModel.kt").forEach { path ->
            val text = code(File(main, path))
            assertFalse(
                "$path writes a colour",
                Regex("Color\\(0x|Color\\.(Red|Green|Blue|Yellow|Gray|Black|White)\\b").containsMatchIn(text),
            )
            assertFalse("$path writes a text", Regex("Text\\(\\s*(text\\s*=\\s*)?\"").containsMatchIn(text))
            assertFalse(
                "$path writes a content description",
                Regex("contentDescription\\s*=\\s*\"").containsMatchIn(text),
            )
        }
        // The tokens of the chart are in their own file, in hexadecimal like the other tokens.
        assertTrue(File(main, "ui/theme/ChartColor.kt").readText().contains("val colorChart2 = Color(0x"))
    }

    // --- The palette (section 9.2) -----------------------------------------------------------------------------------------------------------------------------

    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double {
        val argb = color.toArgb()
        return 0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) +
            0.0722 * channel(argb and 0xFF)
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val high = maxOf(luminance(a), luminance(b))
        val low = minOf(luminance(a), luminance(b))
        return (high + 0.05) / (low + 0.05)
    }

    @Test
    fun everyColourOfTheRing_reachesThreeToOneAgainstTheWhiteCard() {
        // "Ailleurs" is the light grey of the mockup (under 3:1 on purpose, see ChartColor.kt); every other colour of the ring reaches 3:1.
        val all =
            chartPlaceColors.mapIndexed { i, c -> "chart${i + 1}" to c } +
                listOf("other" to colorChartOther, "danger" to colorChartDanger, "reserve" to colorChartReserve)
        all.forEach { (name, color) ->
            val ratio = contrast(color, colorBgSurface)
            assertTrue("$name has a contrast of $ratio against the card, below 3", ratio >= 3.0)
        }
        // The hatching that marks the danger zone is the dark danger text colour: readable on the card (8:1); against the slice itself it is 2.6:1, which is enough to see
        // a pattern (the slice as a whole holds 3.1:1 against the card, and the pattern is never the only cue: the line has its icon and its words).
        assertTrue(contrast(colorStatusDangerText, colorBgSurface) >= 4.5)
        assertTrue(contrast(colorStatusDangerText, colorChartDanger) >= 2.5)
    }

    @Test
    fun thePalette_isMiffGreenAndBlueForThePlaces_theExactDangerRed_andTheGreyOfTheMockup() {
        assertEquals(5, chartPlaceColors.toSet().size)
        // Blues and greens alternate, so that neighbours are told apart (the ring and the legend follow the same order).
        assertEquals(colorActionPrimary.toArgb(), chartPlaceColors[0].toArgb())
        assertEquals(colorStatusSafeText.toArgb(), chartPlaceColors[3].toArgb())
        (chartPlaceColors + colorChartOther + colorChartReserve).forEach {
            val argb = it.toArgb()
            val red = (argb shr 16) and 0xFF
            val green = (argb shr 8) and 0xFF
            val blue = argb and 0xFF
            // Green or blue, never a warm colour: neither the red of the danger nor an orange.
            assertTrue("not a green or a blue: ${Integer.toHexString(argb)}", blue > red || green > red)
        }
        // The danger zone is EXACTLY the red of the danger status of the bubble, "Ailleurs" the grey of the mockup.
        assertEquals(colorStatusDanger.toArgb(), colorChartDanger.toArgb())
        assertEquals(0xFFD4D7DF.toInt(), colorChartElsewhere.toArgb())
    }

    // --- The texts (TS-15, TS-17, TS-22) --------------------------------------------------------------------------------------------------------------------------

    private fun strings(path: String): Map<String, String> =
        Regex(
            "<string name=\"(profile_timeshare_[a-z0-9_]+|settings_privacy_time_[a-z0-9_]+)\"[^>]*>(.*?)</string>",
        ).findAll(read(path)).associate {
            it.groupValues[1] to it.groupValues[2]
        }

    private fun plurals(path: String): Set<String> =
        Regex("<plurals name=\"(profile_timeshare_[a-z0-9_]+)\"").findAll(read(path)).map { it.groupValues[1] }.toSet()

    @Test
    fun frenchAndEnglish_haveTheSameKeys_andTheStatesUseTheWordingFixedByThePm() {
        val fr = strings("src/main/res/values-fr/strings.xml")
        val en = strings("src/main/res/values/strings.xml")
        assertEquals(fr.keys, en.keys)
        assertTrue(fr.size >= 28)
        assertEquals(plurals("src/main/res/values-fr/strings.xml"), plurals("src/main/res/values/strings.xml"))
        assertEquals("Effacer l\\'historique ?", fr["settings_privacy_time_confirm_title"])
        assertEquals(
            "Ta répartition repartira de zéro. Cela ne change rien pour tes proches.",
            fr["settings_privacy_time_confirm_body"],
        )
        assertEquals("Effacer mon historique de temps", fr["settings_privacy_time_erase"])
        // The keys the PM removed (the subtitle under the title, the line about the data staying on the phone) are gone from both languages.
        listOf(
            "profile_timeshare_subtitle_7",
            "profile_timeshare_subtitle_14",
            "profile_timeshare_subtitle_30",
            "profile_timeshare_privacy",
        ).forEach {
            assertFalse("$it must be gone", fr.containsKey(it) || en.containsKey(it))
        }
        assertEquals("Hors-zone", fr["profile_timeshare_elsewhere"])
        assertEquals("Outside zones", en["profile_timeshare_elsewhere"])
        // The libellé of the app, not the one of the mockup.
        assertFalse(read("src/main/res/values-fr/strings.xml").contains("Zone à risque"))
    }

    @Test
    fun noExclamationMarkAndNoFormulaOfCongratulationOrBlame_inAnyLanguage() {
        listOf("src/main/res/values-fr/strings.xml", "src/main/res/values/strings.xml").forEach { path ->
            strings(path).forEach { (key, value) ->
                assertFalse("$key has an exclamation mark", value.contains("!"))
                assertFalse(
                    "$key congratulates or blames",
                    Regex(
                        "(?i)bravo|félicit|congrat|well done|great job|tant mieux|dommage|you should|tu devrais|il faut que",
                    ).containsMatchIn(value),
                )
            }
        }
    }

    @Test
    fun theSentenceOfTheBand_neverNamesTheDangerZone_andTheWordingOfTheStatesIsTheOneFixedByThePm() {
        val fr = strings("src/main/res/values-fr/strings.xml")
        val en = strings("src/main/res/values/strings.xml")
        listOf(fr, en).forEach { language ->
            val sentence = language.getValue("profile_timeshare_summary")
            assertFalse("the band names the danger zone", Regex("(?i)danger|risk").containsMatchIn(sentence))
            assertTrue("the band has the figure", sentence.contains("%1\$s"))
        }
        assertEquals("Tu as passé %1\$s de ton temps dans tes zones de confiance", fr["profile_timeshare_summary"])
        // The wording of "pas assez de données", with its plural (decision of the PM, 2026-11-17), is in the plurals.
        val plural =
            read(
                "src/main/res/values-fr/strings.xml",
            ).substringAfter("<plurals name=\"profile_timeshare_not_enough\">").substringBefore("</plurals>")
        assertTrue(plural.contains("Ton graphique arrive dans %1\$d jour, nous analysons tes habitudes"))
        assertTrue(plural.contains("Ton graphique arrive dans %1\$d jours, nous analysons tes habitudes"))
        val enPlural =
            read(
                "src/main/res/values/strings.xml",
            ).substringAfter("<plurals name=\"profile_timeshare_not_enough\">").substringBefore("</plurals>")
        assertTrue(enPlural.contains("Your chart arrives in %1\$d day, we\\'re analysing your habits"))
        assertTrue(enPlural.contains("Your chart arrives in %1\$d days, we\\'re analysing your habits"))
    }
}
