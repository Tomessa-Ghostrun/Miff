package com.tomessa.miff.ui.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.domain.subscription.SubscriptionInfo
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * ABO-1 on the JVM (Robolectric): the "Abonnement" line of the full Settings (present only when given), its absence otherwise,
 * and the read-only screen (offer name, date, source, "coming soon" sentence; no price, no purchase button).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-port-xxhdpi")
class SubscriptionScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { MiffTheme { Box(Modifier.fillMaxSize()) { content() } } }
    }

    private val expiry =
        ZonedDateTime
            .of(2027, 1, 12, 10, 0, 0, 0, ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    // --- The line in the Settings list (criterion F1) -----------------------------------------------------------------------------

    @Test
    fun settingsList_forAnActiveMiffPlus_hasTheSubscriptionLine_asOneButton_thatOpensTheScreen() {
        var opened = 0
        show { SettingsScreen({}, {}, {}, {}, {}, onOpenSubscription = { opened++ }) }
        composeRule
            .onNodeWithText(text(R.string.settings_subscription_title))
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithText(text(R.string.settings_subscription_subtitle)).assertExists()
        assertEquals(1, opened)
        // The rest of the list is the usual one.
        composeRule.onNodeWithText(text(R.string.settings_my_info_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_privacy_title)).assertExists()
    }

    @Test
    fun settingsList_withoutAnActiveMiffPlus_hasNoTraceOfASubscription() {
        // The default (no callback): free account, expired, no document, offline, error, and every list that does not pass it.
        show { SettingsScreen({}, {}, {}, {}, {}) }
        composeRule.onAllNodesWithText(text(R.string.settings_subscription_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.settings_subscription_subtitle)).assertCountEquals(0)
        composeRule.onAllNodesWithText("Miff+", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Premium", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("€", substring = true).assertCountEquals(0)
    }

    // --- The screen (criteria F2, F4) ---------------------------------------------------------------------------------------------

    @Test
    fun screen_forAManualGrant_saysTheOffer_theDate_andWhoGrantedIt_inEnglish() {
        show { SubscriptionScreen(SubscriptionInfo(expiry, grantedByTeam = true), onBack = {}) }
        composeRule.onNodeWithText("Miff+").assertExists()
        composeRule.onNodeWithText("Active until January 12, 2027").assertExists()
        composeRule.onNodeWithText("Granted by the Miff team").assertExists()
        composeRule.onNodeWithText("This access doesn't go through a store.").assertExists()
        composeRule.onNodeWithText(text(R.string.settings_subscription_title)).assertExists()
        // No promise without a date: nothing "coming soon".
        composeRule.onAllNodesWithText("soon", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
    fun screen_inFrench_writesTheDateInFull_andNeverSaysPremium() {
        show { SubscriptionScreen(SubscriptionInfo(expiry, grantedByTeam = true), onBack = {}) }
        composeRule.onNodeWithText("Miff+").assertExists()
        composeRule.onNodeWithText("Actif jusqu'au 12 janvier 2027").assertExists()
        composeRule.onNodeWithText("Accordé par l'équipe Miff").assertExists()
        composeRule.onNodeWithText("Cet accès ne passe pas par un store.").assertExists()
        composeRule.onAllNodesWithText("bientôt", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Premium", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun screen_forAnotherSource_usesTheNeutralLabel_andNeverClaimsATeamGrant() {
        show { SubscriptionScreen(SubscriptionInfo(expiry, grantedByTeam = false), onBack = {}) }
        composeRule.onNodeWithText(text(R.string.settings_subscription_source_other)).assertExists()
        composeRule.onAllNodesWithText(text(R.string.settings_subscription_source_manual)).assertCountEquals(0)
        // Nothing is said about stores for another source: the sentence only describes an access granted by hand.
        composeRule.onAllNodesWithText(text(R.string.settings_subscription_no_store_note)).assertCountEquals(0)
        composeRule.onAllNodesWithText("store", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun screen_isReadOnly_noPrice_noTrial_noPurchaseNorChangeNorStopButton_onlyTheBackArrow() {
        var back = 0
        show { SubscriptionScreen(SubscriptionInfo(expiry, grantedByTeam = true), onBack = { back++ }) }
        listOf(
            "€",
            "$",
            "price",
            "trial",
            "month",
            "year",
            "30",
            "Stop",
            "Change",
            "Buy",
            "Subscribe",
            "payment",
        ).forEach {
            composeRule.onAllNodesWithText(it, substring = true, ignoreCase = true).assertCountEquals(0)
        }
        // The only thing that can be tapped is the back arrow.
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        composeRule.onNodeWithContentDescription(text(R.string.auth_back_a11y)).assertHasClickAction().performClick()
        assertEquals(1, back)
    }

    @Test
    fun screen_atTheLargestFontSize_keepsEverythingReachable() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                SubscriptionScreen(SubscriptionInfo(expiry, grantedByTeam = true), onBack = {})
            }
        }
        composeRule.onNodeWithText("Active until January 12, 2027").assertExists()
        composeRule.onNodeWithText(text(R.string.settings_subscription_no_store_note)).assertExists()
    }
}
