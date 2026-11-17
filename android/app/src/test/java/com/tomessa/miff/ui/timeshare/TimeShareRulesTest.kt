package com.tomessa.miff.ui.timeshare

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.tomessa.miff.ui.theme.chartPlaceColors
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorChartDanger
import com.tomessa.miff.ui.theme.colorChartElsewhere
import com.tomessa.miff.ui.theme.colorChartOther
import com.tomessa.miff.ui.theme.colorChartReserve
import com.tomessa.miff.ui.theme.colorStatusDangerText
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

    // --- The tool of test data exists in the debug build only (TS-23) --------------------------------------------------------------------------------------

    @Test
    fun theToolOfTestData_isInTheDebugSources_theReleaseHasNone() {
        val debug = read("src/debug/java/com/tomessa/miff/domain/timeshare/DemoPresence.kt")
        val debugPanel = read("src/debug/java/com/tomessa/miff/ui/timeshare/TimeShareDemoTools.kt")
        val release = read("src/release/java/com/tomessa/miff/ui/timeshare/TimeShareDemoTools.kt")
        assertTrue(debug.contains("const val MARKER = \"TIMESHAREDEMO\""))
        assertTrue(debugPanel.contains("DemoPresence.fill("))
        // The release draws nothing: neither the generator, nor the marker, nor a write to the journal.
        assertFalse(release.contains("DemoPresence") || release.contains("TIMESHAREDEMO"))
        assertFalse(
            Regex(
                "\\.record\\(|\\.fill\\(|forgetMiff|testTag",
            ).containsMatchIn(code(File("src/release/java/com/tomessa/miff/ui/timeshare/TimeShareDemoTools.kt"))),
        )
        // The main sources know the tool only through its name, never the generator or the marker.
        timeShareSources.forEach {
            val text = code(it)
            assertFalse(
                "${it.name} knows the generator",
                text.contains("DemoPresence") || text.contains("TIMESHAREDEMO"),
            )
        }
        val everythingElse = main.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        everythingElse.forEach { assertFalse("${it.name} knows the generator", code(it).contains("DemoPresence")) }
        // The CI looks for the marker in the release bundle and in releaseLike, and for its presence in the debug APK.
        val workflow = read("../../.github/workflows/android.yml")
        assertTrue(workflow.contains("TIMESHAREDEMO"))
        assertTrue(read("../ci/check-release-bundle.sh").contains("TIMESHAREDEMO"))
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
        val all =
            chartPlaceColors.mapIndexed { i, c -> "chart${i + 1}" to c } +
                listOf(
                    "other" to colorChartOther,
                    "elsewhere" to colorChartElsewhere,
                    "danger" to colorChartDanger,
                    "reserve" to colorChartReserve,
                )
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
    fun theFivePlacesHaveFiveDifferentColours_noneIsAStatusColour() {
        assertEquals(5, chartPlaceColors.toSet().size)
        // No red, orange or yellow-green of the statuses among the colours of the places (only the danger zone uses the colour of the danger).
        val statusHues = listOf(0xFFD1D828, 0xFFFFB650, 0xFFFF5650).map { it.toInt() }
        (chartPlaceColors + colorChartOther + colorChartElsewhere + colorChartReserve).forEach {
            assertFalse(it.toArgb() in statusHues)
            val argb = it.toArgb()
            val red = (argb shr 16) and 0xFF
            val blue = argb and 0xFF
            val green = (argb shr 8) and 0xFF
            assertFalse(
                "a warm colour is a status colour: ${Integer.toHexString(argb)}",
                red > 200 && blue < 100 && green < 200,
            )
        }
        assertEquals(colorChartDanger.toArgb(), 0xFFFF5650.toInt())
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
        assertTrue(fr.size >= 35)
        assertEquals(plurals("src/main/res/values-fr/strings.xml"), plurals("src/main/res/values/strings.xml"))
        // The wording of "pas assez de données" is the PM's, word for word.
        assertEquals(
            "Ton graphique arrive dans quelques jours, nous analysons ta situation",
            fr["profile_timeshare_not_enough"],
        )
        assertEquals(
            "Your chart will be ready in a few days, we are looking into your situation",
            en["profile_timeshare_not_enough"],
        )
        // The confirmation of the erasure is the one of the ticket.
        assertEquals("Effacer l\\'historique ?", fr["settings_privacy_time_confirm_title"])
        assertEquals(
            "Ta répartition repartira de zéro. Cela ne change rien pour tes proches.",
            fr["settings_privacy_time_confirm_body"],
        )
        assertEquals("Effacer mon historique de temps", fr["settings_privacy_time_erase"])
        assertEquals(
            "Ces données restent sur ton téléphone. Personne d\\'autre ne peut les voir.",
            fr["profile_timeshare_privacy"],
        )
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
    fun theSentenceOfTheChart_neverNamesTheDangerZone_neverGivesAPercentage() {
        val fr = strings("src/main/res/values-fr/strings.xml")
        val en = strings("src/main/res/values/strings.xml")
        listOf(fr, en).forEach { language ->
            language.filterKeys { it.startsWith("profile_timeshare_summary_") }.forEach { (key, value) ->
                assertFalse("$key names the danger zone", Regex("(?i)danger|risk").containsMatchIn(value))
                assertFalse("$key gives a percentage", Regex("[0-9]\\s*(%|pour cent|percent)").containsMatchIn(value))
            }
        }
    }
}
