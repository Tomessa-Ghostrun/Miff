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
        assertTrue(gradle.contains("miff.periodicPaywallOpenings must be a whole number from 1 to 99"))
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
}
