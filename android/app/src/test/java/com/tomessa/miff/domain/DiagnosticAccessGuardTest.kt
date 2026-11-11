package com.tomessa.miff.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Where the internal diagnostic screen may be reached from (PM, 2026-11-12), checked on the source files: the app starts on the app in every build, and
 * the hold detector and the Settings line exist in the DEBUG sources only (the CI also looks for the marker in the release bundle).
 */
class DiagnosticAccessGuardTest {
    private val main = File("src/main/java")
    private val debug = File("src/debug/java")
    private val release = File("src/release/java")

    private fun kotlinFiles(dir: File): List<File> =
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun theNavigationStartsOnTheAppInEveryBuild() {
        val navHost = File("src/main/java/com/tomessa/miff/ui/navigation/MiffNavHost.kt").readText()
        assertTrue(navHost.contains("startDestination = AppRoute"))
        assertFalse(navHost.contains("InternalDestinations.startDestination"))
        assertFalse(
            "the debug build no longer starts on the diagnostic",
            File(
                "src/debug/java/com/tomessa/miff/navigation/InternalDestinations.kt",
            ).readText().contains("startDestination"),
        )
    }

    @Test
    fun theHoldDetectorIsInTheDebugSourcesOnly() {
        assertTrue(debug.resolve("com/tomessa/miff/ui/diagnostic/access/DiagnosticHoldDetector.kt").isFile)
        (kotlinFiles(main) + kotlinFiles(release)).forEach {
            val text = it.readText()
            assertFalse(
                "${it.path} must not know the hold detector",
                text.contains("diagnostic.access") || text.contains("DIAGNOSTIC_HOLD_MARKER"),
            )
            assertFalse("${it.path} must not hold the marker", text.contains("DIAGNOSTICHOLDGESTURE"))
        }
        assertTrue(kotlinFiles(debug).any { it.readText().contains("DIAGNOSTIC_HOLD_MARKER") })
    }

    @Test
    fun theReleaseBuildDrawsNothing() {
        val text = File("src/release/java/com/tomessa/miff/navigation/InternalDestinations.kt").readText()
        assertFalse(text.contains("DiagnosticRoute"))
        assertTrue(text.contains("fun holdModifier(): Modifier = Modifier"))
        assertTrue(text.contains("fun SettingsEntry(row: @Composable (onClick: () -> Unit) -> Unit) = Unit"))
    }
}
