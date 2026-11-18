package com.tomessa.miff.ui.settings

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.domain.auth.AccountSignIn
import com.tomessa.miff.domain.notification.NotificationPermissionState
import com.tomessa.miff.ui.auth.AuthMessage
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Settings > Confidentialité and the password part of Mon compte on the JVM (ticket ONB-3b, criteria B1 to B6): the switch,
 * the honest sentences about location and notifications, the links, and what must NOT be there (no "Supprimer mon compte",
 * no password form, no password field).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-port-xxhdpi")
class PrivacyScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { MiffTheme { Box(Modifier.fillMaxSize()) { content() } } }
    }

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // --- Privacy ---------------------------------------------------------------------------------------------------------------

    private fun privacy(
        state: PrivacyUiState,
        onToggled: (Boolean) -> Unit = {},
        onRetry: () -> Unit = {},
        notifications: NotificationPermissionState = NotificationPermissionState.ALLOWED,
    ) = show {
        PrivacyScreen(
            state,
            onBack = {},
            onAnalyticsToggled = onToggled,
            onRetryWithdrawal = onRetry,
            notifications = notifications,
        )
    }

    @Test
    fun privacy_hasTheSwitch_theLocationAndNotificationSentences_andTheLinks_butNoContactAnyMore() {
        privacy(PrivacyUiState(analyticsEnabled = true))
        shoot("privacy")
        composeRule.onNodeWithText(text(R.string.settings_privacy_analytics_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_privacy_analytics_body)).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_privacy_location_body)).performScrollTo().assertExists()
        // NOTIF-1A: the block says the REAL state of the notifications (the same words as Settings > Notifications), no longer "coming soon".
        composeRule.onNodeWithText(text(R.string.notif_state_allowed)).performScrollTo().assertExists()
        composeRule.onNodeWithText(text(R.string.notif_privacy_body)).performScrollTo().assertExists()
        composeRule.onNodeWithText(text(R.string.auth_consent_terms_link)).performScrollTo().assertHasClickAction()
        composeRule.onNodeWithText(text(R.string.auth_consent_privacy_link)).performScrollTo().assertHasClickAction()
        // AP7 (decision Q2 of the PM, 2026-11-13): "Nous contacter" MOVED to Settings > À propos et aide, it is not here any more (and not duplicated).
        composeRule.onAllNodesWithText(text(R.string.settings_privacy_contact)).assertCountEquals(0)
        // Allowed: only the location offers the settings of the phone.
        composeRule.onAllNodesWithText(text(R.string.settings_privacy_open_phone_settings)).assertCountEquals(1)
        composeRule.onAllNodesWithText(text(R.string.notif_state_open_settings)).assertCountEquals(0)
    }

    @Test
    fun privacy_whenTheNotificationsAreOffInThePhone_saysSoCalmly_andOffersTheSettingsOfThePhone() {
        privacy(PrivacyUiState(), notifications = NotificationPermissionState.OFF)
        composeRule.onNodeWithText(text(R.string.notif_state_off)).performScrollTo().assertExists()
        composeRule.onNodeWithText(text(R.string.notif_state_off_note)).performScrollTo().assertExists()
        composeRule
            .onNodeWithText(text(R.string.notif_state_open_settings))
            .performScrollTo()
            .assertHasClickAction()
    }

    @Test
    fun privacy_whenTheNotificationsWereNeverAsked_saysSo() {
        privacy(PrivacyUiState(), notifications = NotificationPermissionState.NOT_ASKED)
        composeRule.onNodeWithText(text(R.string.notif_state_notasked)).performScrollTo().assertExists()
    }

    @Test
    fun privacy_theLocationIsSaidHonestly_theDetectionUsesIt_andNoStateIsInvented() {
        privacy(PrivacyUiState())
        // LOC-2b: the location IS used (once the person agreed and gave "Always"): the sentence says what it is used for, who sees it, and that it is erased.
        composeRule
            .onNodeWithText(
                "Miff uses your phone's location, even when the app is closed",
                substring = true,
            ).performScrollTo()
            .assertExists()
        composeRule.onNodeWithText("No history of trips is kept.", substring = true).performScrollTo().assertExists()
        // The permission is not complete in this state: the state says so, it never says "Always allowed".
        composeRule.onAllNodesWithText(text(R.string.settings_privacy_location_status_ok)).assertCountEquals(0)
        composeRule
            .onNodeWithText(
                text(R.string.settings_privacy_location_status_todo),
            ).performScrollTo()
            .assertExists()
        composeRule.onAllNodesWithText("approximate", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun privacy_theSwitchIsOneElementForTheScreenReader_withItsState() {
        privacy(PrivacyUiState(analyticsEnabled = true))
        val node = composeRule.onNodeWithText(text(R.string.settings_privacy_analytics_title)).fetchSemanticsNode()
        val row = generateSequence(node) { it.parent }.first { it.config.contains(SemanticsProperties.ToggleableState) }
        assertEquals(Role.Switch, row.config[SemanticsProperties.Role])
        assertEquals(androidx.compose.ui.state.ToggleableState.On, row.config[SemanticsProperties.ToggleableState])
        assertTrue(row.layoutInfo.height >= 48)
    }

    @Test
    fun privacy_tappingTheLineToggles() {
        val calls = mutableListOf<Boolean>()
        privacy(PrivacyUiState(analyticsEnabled = true), onToggled = { calls += it })
        composeRule.onNodeWithText(text(R.string.settings_privacy_analytics_title)).performClick()
        assertEquals(listOf(false), calls)
    }

    @Test
    fun privacy_whileWriting_theSwitchCannotBeToggledAgain() {
        val calls = mutableListOf<Boolean>()
        privacy(PrivacyUiState(analyticsEnabled = false, isWriting = true), onToggled = { calls += it })
        composeRule.onNodeWithText(text(R.string.settings_privacy_analytics_title)).performClick()
        assertTrue(calls.isEmpty())
    }

    @Test
    fun privacy_aWithdrawalNotRecorded_isSaidCalmly_andOffersToRecordIt() {
        var retried = 0
        privacy(
            PrivacyUiState(
                analyticsEnabled = false,
                withdrawalPending = true,
            ),
            onRetry = { retried++ },
        )
        shoot("privacy-withdrawal-pending")
        composeRule.onNodeWithText(text(R.string.settings_privacy_withdrawal_not_recorded)).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_privacy_retry_action)).assertIsEnabled().performClick()
        assertEquals(1, retried)
    }

    @Test
    fun privacy_noRetryButtonWhenNothingIsPending() {
        privacy(PrivacyUiState(analyticsEnabled = true))
        composeRule.onAllNodesWithText(text(R.string.settings_privacy_retry_action)).assertCountEquals(0)
    }

    @Test
    fun privacy_networkMessageIsWords() {
        privacy(PrivacyUiState(message = PrivacyMessage.NETWORK))
        composeRule.onNodeWithText(text(R.string.auth_error_network)).assertExists()
    }

    @Test
    fun privacy_touchTargetsAreAtLeast48dp_andLargestFontStaysReachable() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 2f)) {
                PrivacyScreen(PrivacyUiState(), onBack = {}, onAnalyticsToggled = {}, onRetryWithdrawal = {})
            }
        }
        composeRule
            .onNodeWithText(
                text(R.string.auth_consent_privacy_link),
            ).performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    // --- Account: password (decision Q5) ------------------------------------------------------------------------------------------

    private fun account(
        state: AccountUiState,
        onChangePassword: () -> Unit = {},
    ) = show { AccountScreen(state, {}, onChangePassword, {}, {}, {}, {}) }

    @Test
    fun account_anEmailAccount_hasOneButtonToChangeThePassword_andNoForm() {
        var asked = 0
        account(
            AccountUiState(email = "lea@example.org", signIn = AccountSignIn.PASSWORD),
            onChangePassword = { asked++ },
        )
        shoot("account-password")
        composeRule.onNodeWithText(text(R.string.settings_account_password_help)).assertExists()
        composeRule
            .onNodeWithText(
                text(R.string.settings_account_password_action),
            ).assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, asked)
        // The mockup had three password fields: no field exists here. ("Delete my account" is the discreet link of AUTH-5.)
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        composeRule.onAllNodesWithText("irrecoverable", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun account_aGoogleAccount_seesHowItSignsIn_andNoPasswordLine() {
        account(AccountUiState(email = "lea@example.org", signIn = AccountSignIn.GOOGLE))
        shoot("account-google")
        composeRule.onNodeWithText(text(R.string.settings_account_google)).assertExists()
        composeRule.onAllNodesWithText(text(R.string.settings_account_password_action)).assertCountEquals(0)
        composeRule.onAllNodesWithText("password", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun account_afterTheSend_oneCalmSentence_andAPausedResendButton() {
        account(
            AccountUiState(
                email = "lea@example.org",
                signIn = AccountSignIn.PASSWORD,
                passwordStatus = PasswordEmailStatus.SENT,
                passwordLocked = true,
            ),
        )
        shoot("account-password-sent")
        composeRule.onNodeWithText(text(R.string.settings_account_password_sent)).assertExists()
        composeRule.onNodeWithText(text(R.string.auth_reset_resend_action)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.auth_reset_resend_wait)).assertExists()
    }

    @Test
    fun account_whileSending_theButtonIsDisabled_andSaysSo() {
        account(
            AccountUiState(
                email = "a@b.c",
                signIn = AccountSignIn.PASSWORD,
                passwordStatus = PasswordEmailStatus.SENDING,
            ),
        )
        composeRule.onNodeWithText(text(R.string.settings_account_password_sending)).assertIsNotEnabled()
    }

    @Test
    fun account_aProblemIsSaidInWords() {
        account(AccountUiState(email = "a@b.c", signIn = AccountSignIn.PASSWORD, passwordMessage = AuthMessage.NETWORK))
        composeRule.onNodeWithText(text(R.string.auth_error_network)).assertExists()
    }
}
