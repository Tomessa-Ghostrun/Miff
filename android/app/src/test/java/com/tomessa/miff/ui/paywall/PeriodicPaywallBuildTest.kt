package com.tomessa.miff.ui.paywall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the PM decided on 2026-11-25 (P1), checked on the SOURCE so that a later change cannot break it unnoticed (ticket docs/tickets/paywall-periodique.md):
 * - the periodic offer has NO switch: no `BuildConfig` flag turns it on or off, every build (release, internal, debug) has it;
 * - the only build-time knob is the DEBUG-only lowering of the threshold: release and releaseLike always get 0 ("no override"), whatever Gradle property is given;
 * - the shortcuts of the recette (the counter in the diagnostic screen) are in the debug source set only, never in the main code.
 * The code is read with its WHITESPACE NORMALISED, so that a reformatting never breaks a check.
 */
class PeriodicPaywallBuildTest {
    private fun normalised(file: File): String = file.readText().replace(Regex("""\s+"""), " ")

    private val gradle = normalised(File("build.gradle.kts"))
    private val main = File("src/main/java/com/tomessa/miff")

    private fun kotlinFiles(root: File): List<File> =
        root
            .walkTopDown()
            .filter {
                it.isFile && it.extension == "kt"
            }.toList()

    @Test
    fun thereIsNoSwitch_noBooleanFlagForThePeriodicOffer() {
        assertFalse(gradle.contains("""buildConfigField("boolean", "PERIODIC_PAYWALL""""))
        assertFalse(gradle.contains("miff.periodicPaywall\""))
        assertFalse(gradle.contains("allowPeriodicPaywallInRelease"))
        kotlinFiles(main).forEach { file ->
            assertFalse(
                "${file.name}: no BuildConfig switch of the periodic offer",
                Regex("""BuildConfig\.PERIODIC_PAYWALL\b""").containsMatchIn(file.readText()),
            )
        }
    }

    @Test
    fun theThresholdOverride_isZeroEverywhereButInTheDebugBuild() {
        // The default for every build type (release and releaseLike inherit it) is "no override" ...
        assertTrue(gradle.contains("""buildConfigField("int", "PERIODIC_PAYWALL_OPENINGS_DEBUG", "0")"""))
        // ... and only the debug build type reads the property.
        val reading =
            Regex(
                """buildConfigField\("int", "PERIODIC_PAYWALL_OPENINGS_DEBUG", periodicPaywallOpeningsProperty \?: "0"\)""",
            )
        assertEquals(1, reading.findAll(gradle).count())
        val debugBlock = gradle.substringAfter("debug {").substringBefore("release {")
        assertTrue(debugBlock.contains("PERIODIC_PAYWALL_OPENINGS_DEBUG"))
        val rest = gradle.substringAfter("release {").substringBefore("compileOptions")
        assertFalse(rest.contains("periodicPaywallOpeningsProperty"))
        // A bad value is refused at configuration time (never a silent 0 or a huge number).
        assertTrue(gradle.contains("miff.periodicPaywallOpenings must be a whole number from 1 to 9"))
    }

    @Test
    fun theOverrideIsReadInOnePlace_theContainer_throughTheRuleOfTheConstants() {
        val users =
            kotlinFiles(
                main,
            ).filter { Regex("""BuildConfig\.PERIODIC_PAYWALL_OPENINGS_DEBUG""").containsMatchIn(it.readText()) }
        assertEquals(listOf("AppContainer.kt"), users.map { it.name })
        assertTrue(
            normalised(
                users.single(),
            ).contains("PeriodicPaywallDefaults.openingsFor(BuildConfig.PERIODIC_PAYWALL_OPENINGS_DEBUG)"),
        )
    }

    @Test
    fun theShortcutsOfTheRecette_areInTheDebugSourceSetOnly() {
        assertTrue(File("src/debug/java/com/tomessa/miff/ui/diagnostic/PeriodicPaywallDebugCard.kt").exists())
        kotlinFiles(main).forEach { file ->
            assertFalse(
                "${file.name}: the debug card is not in the main code",
                file.readText().contains("PeriodicPaywallDebugCard"),
            )
        }
        val release = File("src/release/java")
        if (release.exists()) {
            kotlinFiles(release).forEach { assertFalse(it.readText().contains("PeriodicPaywallDebugCard")) }
        }
    }

    @Test
    fun theOnlyThingKeptOnThePhone_isOneWholeNumber_andItIsNeverLoggedOrSent() {
        // The rules and the store are the only files that know the counter; none of them logs, sends or writes anything else.
        val rules = File(main, "domain/paywall/PeriodicPaywallRules.kt").readText()
        val store = File(main, "data/paywall/SharedPreferencesPeriodicPaywallStore.kt").readText()
        listOf(rules, store).forEach {
            assertFalse(it.contains("Log."))
            assertFalse(it.contains("println("))
            assertFalse(it.contains("Firebase"))
            assertFalse(it.contains("tracker"))
        }
        assertEquals("exactly one key in the file", 1, Regex("""const val KEY_""").findAll(store).count())
    }

    /**
     * The files of the app that open a dialog, a sheet or a menu WITHOUT telling the periodic offer, each with the reason why that is right. The only valid reason: the overlay is part of a screen
     * that the navigation PUSHES over the tabs (then the tabs are not in front and the offer waits anyway). A dialog hosted anywhere else, even one that looks like a screen's, does NOT belong
     * here: it tells ([com.tomessa.miff.ui.settings.DeletionBlockedDialog] is hosted by AppRoot, over the tabs, and tells). Adding a file to this list is a decision to justify.
     */
    private val overlaysOfPushedScreens =
        mapOf(
            "ui/alert/AlertSentScreen.kt" to "AlertSentRoute, pushed (and the offer never comes during my own alert)",
            "ui/notification/NotificationCenterScreen.kt" to "NotificationCenterRoute, pushed",
            "ui/place/PlaceFormScreen.kt" to "PlaceFormRoute, pushed",
            "ui/settings/AccountScreen.kt" to "AccountRoute, pushed (Settings)",
            "ui/settings/DeleteAccountScreens.kt" to "DeleteAccountRoute, pushed (deletion of the account)",
            "ui/settings/MyInfoScreen.kt" to "MyInfoRoute, pushed (Settings)",
        )

    /** The code of a file without its comment lines (KDoc, block and line comments). */
    private fun codeLines(file: File): List<String> =
        file.readLines().filter { line ->
            line.trimStart().let { !it.startsWith("*") && !it.startsWith("//") && !it.startsWith("/*") }
        }

    private val overlayCall =
        Regex(
            """\b(Dialog|AlertDialog|BasicAlertDialog|ModalBottomSheet|DropdownMenu|ExposedDropdownMenu|DatePickerDialog|TimePickerDialog|Popup)\(""",
        )

    /**
     * Ticket 4.1 "no sheet", PP9 and PP12: an overlay (dialog, bottom sheet, menu, full-screen window) is another window, so it cannot be seen from the state of the screen behind it and its touches
     * never reach the root: it has to say it is open ([com.tomessa.miff.ui.components.OverlayPresent]). Every file of the app that opens one does, EXCEPT the files of [overlaysOfPushedScreens]. For
     * each call, the line `OverlayPresent()` has to be NEAR it (from the line before the call to its next 30 lines, never past the next overlay of the file): a call placed in another part of the file
     * does not count for it. A new overlay in another file fails here until it says so.
     */
    @Test
    fun everyOverlayOfTheAppTellsThePeriodicOfferItIsOpen() {
        val problems = mutableListOf<String>()
        var covered = 0
        kotlinFiles(main).forEach { file ->
            val relative = file.relativeTo(main).path.replace('\\', '/')
            if (relative in overlaysOfPushedScreens) return@forEach
            val code = codeLines(file)
            val calls = code.indices.filter { overlayCall.containsMatchIn(code[it]) }
            if (calls.isEmpty()) return@forEach
            covered += calls.size
            calls.forEachIndexed { k, at ->
                val from = maxOf(at - 1, 0)
                val until = minOf(at + 30, (calls.getOrNull(k + 1)?.minus(1) ?: code.size) - 1, code.size - 1)
                val told = (from..until).any { code[it].trim() == "OverlayPresent()" }
                if (!told) {
                    problems +=
                        "$relative: the overlay near code line ${at + 1} does not call OverlayPresent() just after it"
                }
            }
        }
        assertEquals("every overlay of the tabs says it is open: $problems", emptyList<String>(), problems)
        assertTrue("the scan found the overlays of the tabs ($covered)", covered >= 13)
        val shell = File(main, "ui/main/MainShell.kt").readText()
        assertTrue(
            "the shell gives the registry to the whole app",
            shell.contains("LocalOverlayPresence provides periodic"),
        )
    }

    /** Both overlays that are NOT inside the provider of the shell are given the registry where they are hosted: the root of the app. */
    @Test
    fun theMessageOfTheCancelledDeletion_isHostedWithTheRegistry_andTheReviewWindowIsNotExempt() {
        val root = normalised(File(main, "ui/navigation/AppRoot.kt"))
        assertTrue(
            "AppRoot gives the registry to the cancelled-deletion message",
            root.contains(
                "LocalOverlayPresence provides container.periodicPaywall) { DeletionBlockedNotice(session) }",
            ),
        )
        assertFalse("the review window tells it is open", "ui/review/ReviewPromptWindow.kt" in overlaysOfPushedScreens)
        assertFalse(
            "the deletion message tells it is open",
            "ui/settings/DeleteAccountHost.kt" in overlaysOfPushedScreens,
        )
    }

    /** The list of exceptions does not rot: each file still exists and still opens at least one overlay (otherwise the entry is removed). */
    @Test
    fun theExemptFilesStillExist_andStillOpenAnOverlay() {
        overlaysOfPushedScreens.keys.forEach { relative ->
            val file = File(main, relative)
            assertTrue("$relative still exists", file.exists())
            assertTrue(
                "$relative still opens an overlay (else drop it from the list)",
                codeLines(file).any {
                    overlayCall.containsMatchIn(it)
                },
            )
        }
    }
}
