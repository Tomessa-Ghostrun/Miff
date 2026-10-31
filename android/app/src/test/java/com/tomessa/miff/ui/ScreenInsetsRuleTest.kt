package com.tomessa.miff.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Rule "every screen takes its safe areas (status bar, navigation bar, cut-out, keyboard) from ui/components/ScreenInsets.kt":
 * the raw Compose functions that pad or consume system insets may only appear in that file. A screen that calls them by itself
 * is how the Feed logo ended up drawn over the clock (a Scaffold consumed the status bar before the screen could use it).
 *
 * If this test fails: use MiffScreen / miffScreenInsets / miffTopInsets / miffBottomInsets instead (see android/README.md).
 */
class ScreenInsetsRuleTest {
    private val central = "ScreenInsets.kt"

    private val forbidden =
        listOf(
            Regex(
                """\b(safeDrawing|safeContent|safeGestures|systemBars|statusBars|navigationBars|displayCutout|ime|captionBar)Padding\b""",
            ),
            Regex("""\bwindowInsetsPadding\b"""),
            Regex("""\b(consumeWindowInsets|onConsumedWindowInsetsChanged)\b"""),
            Regex("""\bcontentWindowInsets\b"""),
            Regex("""\bWindowInsets\b"""),
            Regex("""\bsetDecorFitsSystemWindows\b"""),
            Regex("""\bfitsSystemWindows\b"""),
            Regex("""\bisImeVisible\b"""),
        )

    private fun sourceRoots() =
        listOf("src/main/java", "src/debug/java", "src/release/java")
            .map { File(it) }
            .filter { it.exists() }

    private fun isCode(line: String): Boolean {
        val trimmed = line.trimStart()
        return !trimmed.startsWith("*") && !trimmed.startsWith("//") && !trimmed.startsWith("/*")
    }

    @Test
    fun onlyTheCommonFileTouchesTheSystemInsets() {
        val roots = sourceRoots()
        assertTrue("source folders not found from ${File(".").absolutePath}", roots.isNotEmpty())
        val offenders = mutableListOf<String>()
        roots.forEach { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != central }.forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (isCode(line) &&
                        forbidden.any { it.containsMatchIn(line) }
                    ) {
                        offenders += "${file.name}:${index + 1}"
                    }
                }
            }
        }
        assertTrue(
            "safe areas must come from ui/components/ScreenInsets.kt (MiffScreen, miffScreenInsets, miffTopInsets, " +
                "miffBottomInsets); raw use found in: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun theCommonFileExists() {
        assertTrue(sourceRoots().any { root -> root.walkTopDown().any { it.name == central } })
    }

    @Test
    fun theManifestKeepsTheKeyboardResizingTheScreen() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "MainActivity needs android:windowSoftInputMode=\"adjustResize\" (keyboard height on Android 8 to 10)",
            manifest.contains("""android:windowSoftInputMode="adjustResize""""),
        )
    }
}
