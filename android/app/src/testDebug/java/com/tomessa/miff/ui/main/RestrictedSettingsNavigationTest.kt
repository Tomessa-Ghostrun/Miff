package com.tomessa.miff.ui.main

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.ui.miff.MiffChoiceRoute
import com.tomessa.miff.ui.miff.MiffChoiceScreen
import com.tomessa.miff.ui.navigation.popBackStackIfSafe
import com.tomessa.miff.ui.settings.AccountScreen
import com.tomessa.miff.ui.settings.AccountUiState
import com.tomessa.miff.ui.settings.SettingsScreen
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A person with NO Miff reaches Settings > Mon compte > Supprimer mon compte, and ONLY that way (decision of the PM, 2026-11-07, which
 * revises Q13): the gear of the choice screen opens the RESTRICTED list (Mon compte, Confidentialité), no tab bar, no other entry to the
 * deletion. The graph below wires the real screens exactly as [com.tomessa.miff.ui.miff.MiffOnboardingHost] does (the routes are the real
 * ones), without the container, which is not needed to follow the navigation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-port-xxhdpi")
class RestrictedSettingsNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var nav: NavHostController

    private fun text(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    @Composable
    private fun Host() {
        nav = rememberNavController()
        NavHost(navController = nav, startDestination = MiffChoiceRoute) {
            composable<MiffChoiceRoute> {
                MiffChoiceScreen(
                    onCreate = {},
                    onJoin = {},
                    onSignOut = {},
                    onOpenSettings = { nav.openSettings() },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = { nav.popBackStackIfSafe() },
                    onOpenMyInfo = null,
                    onOpenAccount = { nav.navigate(AccountRoute) { launchSingleTop = true } },
                    onOpenLanguage = null,
                    onOpenPrivacy = { nav.navigate(PrivacyRoute) { launchSingleTop = true } },
                )
            }
            composable<AccountRoute> {
                AccountScreen(
                    state = AccountUiState(email = "a@b.fr"),
                    onBack = { nav.popBackStackIfSafe() },
                    onChangePassword = {},
                    onSignOutRequested = {},
                    onSignOutConfirmed = {},
                    onSignOutCancelled = {},
                    onDeleteRequested = { nav.navigate(DeleteAccountRoute) { launchSingleTop = true } },
                )
            }
            composable<DeleteAccountRoute> { Text("deletion-information") }
        }
    }

    private fun show() {
        composeRule.setContent { MiffTheme { Box(Modifier.fillMaxSize()) { Host() } } }
    }

    private fun deleteEntries() = composeRule.onAllNodesWithText(text(R.string.settings_delete_entry))

    @Test
    fun aPersonWithNoMiff_reachesTheDeletion_throughSettingsAndMyAccount_andOnlyThere() {
        show()
        // The choice screen: no deletion entry, only the gear.
        deleteEntries().assertCountEquals(0)
        composeRule.onNodeWithContentDescription(text(R.string.profile_tab_settings_a11y)).performClick()
        // The restricted list: still no deletion entry (it lives inside "Mon compte").
        composeRule.onNodeWithText(text(R.string.settings_account_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.settings_privacy_title)).assertIsDisplayed()
        deleteEntries().assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.settings_account_title)).performClick()
        // Mon compte: the one entry.
        deleteEntries().assertCountEquals(1)
        composeRule.onNodeWithText(text(R.string.settings_delete_entry)).performClick()
        composeRule.onNodeWithText("deletion-information").assertIsDisplayed()
    }

    @Test
    fun theWayBack_isTheSimpleOne_eachScreenToTheOneItWasOpenedFrom() {
        show()
        composeRule.onNodeWithContentDescription(text(R.string.profile_tab_settings_a11y)).performClick()
        composeRule.onNodeWithText(text(R.string.settings_account_title)).performClick()
        composeRule.runOnIdle { assertEquals(true, nav.popBackStackIfSafe()) }
        composeRule.onNodeWithText(text(R.string.settings_privacy_title)).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(true, nav.popBackStackIfSafe()) }
        composeRule.onNodeWithText(text(R.string.miff_choice_title)).assertIsDisplayed()
    }
}
