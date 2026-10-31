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
    // Exempt files, by PATH (relative to the module, with "/"), not by file name: a file of the same name elsewhere is not
    // exempt. The debug diagnostic card shows the raw values of the phone: the only other file allowed to read them (debug only).
    private val commonFile = "src/main/java/com/tomessa/miff/ui/components/ScreenInsets.kt"
    private val diagnosticFile = "src/debug/java/com/tomessa/miff/ui/diagnostic/InsetsDiagnostic.kt"
    private val central = setOf(commonFile, diagnosticFile)

    private fun relativePath(file: File): String = file.invariantSeparatorsPath.removePrefix("./")

    // Material3 components that pad themselves with the system bars by default (their `windowInsets` / `contentWindowInsets`
    // parameter defaults). Not allowed as is; if one is ever needed, add a wrapper in ScreenInsets.kt that gives it explicit
    // insets, and use that. ModalBottomSheet and AlertDialog are not in this list: they are allowed, but only with the safe
    // areas of the common file for their content (and a `windowInsets =` parameter is forbidden everywhere else, see below).
    private val componentsWithDefaultInsets =
        "Scaffold|TopAppBar|CenterAlignedTopAppBar|MediumTopAppBar|LargeTopAppBar|BottomAppBar|NavigationBar|NavigationRail"

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
            // Material3 parameter (ModalBottomSheet, Scaffold, bars...): giving it a value is a private deal with the insets.
            Regex("""\bwindowInsets\s*="""),
            // Reading or intercepting the raw insets from the view system.
            Regex("""\bWindowInsetsCompat\b"""),
            Regex("""\b(setOnApplyWindowInsetsListener|OnApplyWindowInsetsListener)\b"""),
            // These components pad themselves with the system bars unless told otherwise (and telling them is forbidden above).
            Regex("""\b($componentsWithDefaultInsets)\s*[({]"""),
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
            root
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && relativePath(it) !in central }
                .forEach { file ->
                    file.readLines().forEachIndexed { index, line ->
                        if (isCode(line) &&
                            forbidden.any { it.containsMatchIn(line) }
                        ) {
                            offenders += "${relativePath(file)}:${index + 1}"
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
    fun theExemptFilesExistAtTheirPath() {
        // If one of them moves, the exemption above must move with it (otherwise the moved file would fail the rule, or worse,
        // a new file could take its place unnoticed).
        assertTrue("missing: $commonFile", File(commonFile).isFile)
        assertTrue("missing: $diagnosticFile", File(diagnosticFile).isFile)
    }

    @Test
    fun theRuleSeesWhatItShould() {
        // The patterns themselves, so that a typo in one of them cannot silently let a violation through.
        val samples =
            listOf(
                "ModalBottomSheet(onDismissRequest = f, windowInsets = WindowInsets(0))",
                "    windowInsets = BottomSheetDefaults.windowInsets,",
                "val c = WindowInsetsCompat.Builder()",
                "ViewCompat.setOnApplyWindowInsetsListener(view) { _, i -> i }",
                "Scaffold(topBar = { }) { padding -> }",
                "TopAppBar(title = { })",
                "CenterAlignedTopAppBar(title = { })",
                "NavigationBar { }",
                "Modifier.statusBarsPadding()",
            )
        samples.forEach { sample ->
            assertTrue("not detected: $sample", forbidden.any { it.containsMatchIn(sample) })
        }
        listOf(
            "ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {",
            "MainTabBar(config = tabBar)",
            "Modifier.miffBottomInsets()",
        ).forEach { sample ->
            assertTrue("false positive: $sample", forbidden.none { it.containsMatchIn(sample) })
        }
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
