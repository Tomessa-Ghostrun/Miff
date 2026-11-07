package com.tomessa.miff.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Rule of the glass tab bar (ticket TABBAR-GLASS): the tabs are drawn FULL SCREEN and pass under the bar, so each scrolling tab must keep a
 * bottom margin of the height of the bar (`LocalTabBarInset`), or its last element would stay hidden behind it. When two branches touch the
 * same screen (the Feed was rewritten by another ticket), a merge can silently drop that one line: this test fails then.
 *
 * If this test fails: add `.padding(bottom = LocalTabBarInset.current)` inside the scrolling column of the tab (see FeedScreen.kt).
 */
class TabBarInsetRuleTest {
    private fun source(path: String): String {
        val file = File("src/main/java/com/tomessa/miff/$path")
        assertTrue("source not found: ${file.absolutePath}", file.exists())
        return file.readText()
    }

    @Test
    fun everyScrollingTab_keepsTheBottomMarginOfTheBar() {
        listOf("ui/feed/FeedScreen.kt", "ui/profile/ProfileTabScreen.kt").forEach { path ->
            assertTrue(
                "$path must pad its content with LocalTabBarInset",
                source(path).contains("LocalTabBarInset.current"),
            )
        }
    }

    @Test
    fun theTabsAreHostedByTheGlassHost_notByAColumnThatReservesTheBarsPlace() {
        val shell = source("ui/main/MainShell.kt")
        assertTrue("MainTabs must use GlassTabHost", shell.contains("GlassTabHost("))
    }

    @Test
    fun theBlurLibraryIsUsedOnlyByTheGlassFile_andOnlyItsBlurModule() {
        val offenders =
            File("src/main/java")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "GlassTabBar.kt" }
                .filter { it.readText().contains("dev.chrisbanes.haze") }
                .map { it.name }
                .toList()
        assertTrue(
            "Haze must stay confined to GlassTabBar.kt (plan B = replace that one file): $offenders",
            offenders.isEmpty(),
        )
        val glass = source("ui/main/GlassTabBar.kt")
        // The Glass module (refraction) and the materials are NOT taken (decision of the PM).
        assertTrue(!glass.contains("dev.chrisbanes.haze.glass") && !glass.contains("dev.chrisbanes.haze.materials"))
    }

    @Test
    fun theBuildTakesTheBlurModuleOnly_noGlassNoMaterials() {
        val catalog = File("../gradle/libs.versions.toml").readText()
        assertTrue(catalog.contains("haze-blur = { module = \"dev.chrisbanes.haze:haze-blur\""))
        assertTrue(
            "haze-glass must not be added",
            !catalog.contains("haze-glass") && !catalog.contains("haze-materials"),
        )
        assertTrue(
            "Haze version must be pinned to an exact version",
            Regex("""(?m)^haze = "\d+\.\d+\.\d+"$""").containsMatchIn(catalog),
        )
    }
}
