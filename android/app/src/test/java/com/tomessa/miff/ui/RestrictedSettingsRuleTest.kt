package com.tomessa.miff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Rule of the deletion of the account (decision of the PM, 2026-11-07): it is reachable ONLY from Settings > Mon compte, and a person with no Miff
 * gets there through the RESTRICTED Settings (the gear of the choice screen). The wiring lives in the container-bound graphs, which the UI tests
 * cannot build: these checks read the sources, so that a merge or a refactor that drops one line fails here.
 *
 * If this test fails, read `docs/tickets/suppression-compte.md` (decision of 2026-11-07) before changing anything.
 */
class RestrictedSettingsRuleTest {
    private fun source(path: String): String {
        val file = File("src/main/java/com/tomessa/miff/$path")
        assertTrue("source not found: ${file.absolutePath}", file.exists())
        return file.readText()
    }

    /** The text of a top-level function, from its signature to the next top-level declaration. */
    private fun body(
        text: String,
        signature: String,
    ): String {
        val start = text.indexOf(signature)
        assertTrue("$signature not found", start >= 0)
        val end =
            Regex(
                "\n(?:internal |private |fun |@Composable|/[*][*])",
            ).find(text, start + signature.length)?.range?.first
                ?: text.length
        return text.substring(start, end)
    }

    @Test
    fun theOnboardingHost_registersTheRestrictedSettings_andTheChoiceScreenOpensThem() {
        val host = body(source("ui/miff/MiffNavigation.kt"), "fun MiffOnboardingHost(")
        assertTrue("MiffOnboardingHost must call restrictedSettingsRoutes", host.contains("restrictedSettingsRoutes("))
        assertTrue(
            "the choice screen must open the settings",
            host.contains("onOpenSettings = { navController.openSettings() }"),
        )
    }

    @Test
    fun theRestrictedSettings_listOnlyMyAccountAndPrivacy_andShareTheScreensOfTheTabs() {
        val shell = source("ui/main/MainShell.kt")
        val list = body(shell, "internal fun NavGraphBuilder.restrictedSettingsListRoute(")
        assertTrue(list.contains("onOpenMyInfo = null") && list.contains("onOpenLanguage = null"))
        val restricted = body(shell, "internal fun NavGraphBuilder.restrictedSettingsRoutes(")
        assertTrue(restricted.contains("restrictedSettingsListRoute(") && restricted.contains("sharedSettingsRoutes("))
        val shared = body(shell, "internal fun NavGraphBuilder.sharedSettingsRoutes(")
        listOf(
            "composable<AccountRoute>",
            "composable<PrivacyRoute>",
            "deleteAccountRoutes(",
            "passwordResetRoute(",
        ).forEach {
            assertTrue("sharedSettingsRoutes must keep $it", shared.contains(it))
        }
        // The full Settings of the tabs use the same shared routes (nothing is duplicated).
        assertTrue(body(shell, "private fun MainNavigation(").contains("sharedSettingsRoutes("))
    }

    @Test
    fun theDeletionEntry_existsOnlyInMyAccount() {
        val offenders =
            File("src/main/java")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { it.readText().contains("R.string.settings_delete_entry") }
                .map { it.name }
                .toList()
        assertEquals("only Settings > Mon compte offers the deletion", listOf("AccountScreen.kt"), offenders)
        val routeUsers =
            File("src/main/java")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter {
                    it.readText().contains("navigate(DeleteAccountRoute)") ||
                        it.readText().contains("open(DeleteAccountRoute)")
                }.map { it.name }
                .toList()
        assertEquals("only the Mon compte route opens the deletion", listOf("MainShell.kt"), routeUsers)
    }
}
