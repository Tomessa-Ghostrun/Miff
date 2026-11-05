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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.R
import com.tomessa.miff.data.account.AccountDeletionControls
import com.tomessa.miff.data.account.AccountDeletionState
import com.tomessa.miff.domain.auth.SignInProvider
import com.tomessa.miff.ui.auth.AccountLookupFailedScreen
import com.tomessa.miff.ui.auth.AuthMessage
import com.tomessa.miff.ui.miff.MiffChoiceScreen
import com.tomessa.miff.ui.theme.MiffTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The screens of the deletion of the account on the JVM (Robolectric, no phone): A (information), B (dialog), C (re-authentication), the
 * progress / pause / failure / end screens and the banner (ticket AUTH-5, mockups PNG 01 to 09; criteria SC1, SC4 to SC8, SC10, SC11,
 * SC49, SA1, SA4). Writes screenshots to build/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-port-xxhdpi")
class DeleteAccountScreensTest {
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

    // --- A. Information --------------------------------------------------------------------------------------------

    private fun info(
        state: DeleteInfoUiState,
        onContinue: () -> Unit = {},
        onBack: () -> Unit = {},
        onKeep: () -> Unit = {},
        onConfirm: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) = show {
        DeleteAccountInfoScreen(
            state = state,
            onBack = onBack,
            onContinue = onContinue,
            onRetry = onRetry,
            onContact = {},
            onKeep = onKeep,
            onConfirm = onConfirm,
        )
    }

    @Test
    fun info_noMiff_saysWhatDisappears_gives_theContact_andTheCalmReversibilitySentence() {
        var continued = 0
        info(DeleteInfoUiState(inventory = DeleteInventory.NO_MIFFS), onContinue = { continued++ })
        shoot("delete-info-no-miff")
        composeRule.onNodeWithText(text(R.string.settings_delete_intro)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.settings_delete_you_nomiff)).assertIsDisplayed()
        composeRule.onNodeWithText(BuildConfig.SUPPORT_EMAIL, substring = true).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_delete_irreversible), substring = true).assertExists()
        composeRule
            .onNodeWithText(text(R.string.settings_delete_continue))
            .assertIsEnabled()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, continued)
        composeRule
            .onNodeWithText(text(R.string.settings_delete_cancel))
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        // SC5: none of the words that frighten.
        listOf("irrecoverable", "instantly", "irrevocable", "lose all", "danger").forEach {
            composeRule.onAllNodesWithText(it, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }

    @Test
    fun info_cancel_leavesWithNothingChanged() {
        var left = 0
        info(DeleteInfoUiState(inventory = DeleteInventory.NO_MIFFS), onBack = { left++ })
        composeRule.onNodeWithText(text(R.string.settings_delete_cancel)).performClick()
        assertEquals(1, left)
    }

    @Test
    fun info_whileLoading_continueIsGreyed_andTheWaitIsSaid() {
        info(DeleteInfoUiState(inventory = DeleteInventory.LOADING))
        composeRule.onNodeWithText(text(R.string.settings_delete_continue)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.settings_delete_loading)).assertExists()
    }

    @Test
    fun info_offline_theScreenOpens_continueIsGreyed_theMentionIsCalm() {
        info(DeleteInfoUiState(inventory = DeleteInventory.OFFLINE))
        shoot("delete-info-offline")
        composeRule.onNodeWithText(text(R.string.settings_delete_offline_list)).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_delete_continue)).assertIsNotEnabled()
    }

    @Test
    fun info_aFailedRead_offersRetry_andNeverAnIncompleteList() {
        var retried = 0
        info(DeleteInfoUiState(inventory = DeleteInventory.READ_FAILED), onRetry = { retried++ })
        composeRule.onNodeWithText(text(R.string.settings_delete_read_failed)).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_delete_continue)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.settings_delete_retry)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, retried)
    }

    @Test
    fun info_aMiff_isDetected_theCalmComingSoonStateShows_andNothingCanBeDeleted() {
        info(DeleteInfoUiState(inventory = DeleteInventory.HAS_MIFFS))
        shoot("delete-info-has-miffs")
        composeRule.onNodeWithText("coming soon", substring = true).assertExists()
        composeRule.onNodeWithText(BuildConfig.SUPPORT_EMAIL, substring = true).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_delete_continue)).assertIsNotEnabled()
        // The list of what disappears is not shown for an account that cannot go on.
        composeRule.onAllNodesWithText(text(R.string.settings_delete_you_nomiff)).assertCountEquals(0)
    }

    @Test
    fun info_aCompteLinkedToApple_getsTheEncartBeforeThePointOfNoReturn() {
        info(DeleteInfoUiState(inventory = DeleteInventory.NO_MIFFS, appleLinked = true))
        composeRule.onNodeWithText(text(R.string.settings_delete_apple_android_info)).assertExists()
    }

    @Test
    fun info_withoutApple_noAppleEncart() {
        info(DeleteInfoUiState(inventory = DeleteInventory.NO_MIFFS, appleLinked = false))
        composeRule.onAllNodesWithText(text(R.string.settings_delete_apple_android_info)).assertCountEquals(0)
    }

    @Test
    fun info_largestFont_continueStaysReachable() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 2f)) {
                DeleteAccountInfoScreen(
                    state = DeleteInfoUiState(inventory = DeleteInventory.NO_MIFFS, appleLinked = true),
                    onBack = {},
                    onContinue = {},
                    onRetry = {},
                    onContact = {},
                    onKeep = {},
                    onConfirm = {},
                )
            }
        }
        shoot("delete-info-large-font")
        composeRule
            .onNodeWithText(text(R.string.settings_delete_continue))
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    // --- B. The dialog -----------------------------------------------------------------------------------------------

    @Test
    fun dialog_twoButtonsOfTheSameWeight_theSensePlainInTheWords() {
        var kept = 0
        var confirmed = 0
        info(
            DeleteInfoUiState(inventory = DeleteInventory.NO_MIFFS, confirming = true),
            onKeep = { kept++ },
            onConfirm = { confirmed++ },
        )
        shoot("delete-dialog")
        composeRule.onNodeWithText(text(R.string.settings_delete_confirm_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_delete_confirm_body)).assertExists()
        composeRule
            .onNodeWithText(text(R.string.settings_delete_confirm_keep))
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, kept)
        // "Delete my account" is also the title of the screen behind: the BUTTON is the one that can be clicked.
        composeRule
            .onNode(hasText(text(R.string.settings_delete_confirm_ok)) and hasClickAction())
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun dialog_offline_aCalmNotice_andTheButtonStaysActive() {
        info(DeleteInfoUiState(inventory = DeleteInventory.NO_MIFFS, confirming = true, confirmOffline = true))
        composeRule.onNodeWithText(text(R.string.settings_delete_confirm_offline)).assertExists()
        composeRule.onNode(hasText(text(R.string.settings_delete_confirm_ok)) and hasClickAction()).assertIsEnabled()
    }

    // --- C. Re-authentication ----------------------------------------------------------------------------------------

    private fun reauth(
        state: DeleteReauthUiState,
        onSubmitPassword: (String) -> Unit = {},
        onGoogle: () -> Unit = {},
        onForgot: (() -> Unit)? = {},
        onCancel: () -> Unit = {},
    ) = show {
        DeleteAccountReauthScreen(
            state = state,
            onBack = {},
            onSubmitPassword = onSubmitPassword,
            onGoogle = onGoogle,
            onForgotPassword = onForgot,
            onCancelOrLater = onCancel,
            onContact = {},
        )
    }

    private fun passwordState(
        message: AuthMessage? = null,
        last: Boolean = false,
        busy: Boolean = false,
    ) = DeleteReauthUiState("lea@example.org", setOf(SignInProvider.PASSWORD), last, busy, message)

    @Test
    fun reauth_password_confirmIsGreyedUntilTyped_thenSendsOnce_theFieldIsAPassword() {
        val sent = mutableListOf<String>()
        reauth(passwordState(), onSubmitPassword = { sent += it })
        shoot("delete-reauth-password")
        composeRule.onNodeWithText("Account: lea@example.org").assertExists()
        composeRule.onNodeWithText(text(R.string.settings_delete_reauth_confirm)).assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextInput("Mimosa2026!")
        composeRule.onNodeWithText(text(R.string.settings_delete_reauth_confirm)).assertIsEnabled().performClick()
        assertEquals(listOf("Mimosa2026!"), sent)
        composeRule.onNodeWithText(text(R.string.settings_delete_reauth_note)).assertExists()
    }

    @Test
    fun reauth_wrongPassword_theCalmSentenceShows_theFieldStaysAndNoCodeIsShown() {
        reauth(passwordState(message = AuthMessage.REAUTH_PASSWORD_WRONG))
        shoot("delete-reauth-wrong-password")
        composeRule.onNodeWithText(text(R.string.settings_delete_reauth_password_wrong)).assertExists()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
    }

    @Test
    fun reauth_forgotPassword_isOffered_beforeTheDeletionBegins() {
        var forgot = 0
        reauth(passwordState(), onForgot = { forgot++ })
        composeRule
            .onNodeWithText(
                text(R.string.auth_signin_forgot_password),
            ).assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, forgot)
    }

    @Test
    fun reauth_google_hasTheGoogleButtonAndNoPasswordField_andNoAppleAnywhere() {
        var asked = 0
        reauth(
            DeleteReauthUiState("lea@example.org", setOf(SignInProvider.GOOGLE), last = false),
            onGoogle = { asked++ },
        )
        shoot("delete-reauth-google")
        composeRule.onNodeWithText(text(R.string.settings_delete_reauth_google)).assertExists()
        composeRule.onNodeWithText(text(R.string.auth_google_action)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, asked)
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        composeRule.onAllNodesWithText("Apple", substring = true).assertCountEquals(0)
    }

    @Test
    fun reauth_wrongGoogleAccount_isSaidCalmly() {
        reauth(
            DeleteReauthUiState(
                "lea@example.org",
                setOf(SignInProvider.GOOGLE),
                last = false,
                message = AuthMessage.REAUTH_WRONG_ACCOUNT,
            ),
        )
        composeRule.onNodeWithText(text(R.string.settings_delete_reauth_wrong_account)).assertExists()
    }

    @Test
    fun reauth_severalMethods_showBothWithAnOrBetween() {
        reauth(
            DeleteReauthUiState("lea@example.org", setOf(SignInProvider.PASSWORD, SignInProvider.GOOGLE), last = false),
        )
        composeRule.onNodeWithText(text(R.string.auth_google_action)).assertExists()
        composeRule.onNodeWithText(text(R.string.auth_or_divider)).assertExists()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
    }

    @Test
    fun reauth_busy_confirmSaysChecking_andNothingElseCanBeStarted() {
        reauth(passwordState(busy = true))
        composeRule.onNodeWithText(text(R.string.settings_delete_reauth_checking)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.settings_delete_cancel)).assertIsNotEnabled()
    }

    @Test
    fun reauth_lastVerification_hasNoCancel_butLater_andSaysWhatHappensThen() {
        var later = 0
        reauth(passwordState(last = true), onForgot = null, onCancel = { later++ })
        shoot("delete-reauth-last")
        composeRule.onNodeWithText(text(R.string.settings_delete_reauth_last)).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_delete_reauth_last_sub)).assertExists()
        composeRule.onAllNodesWithText(text(R.string.settings_delete_cancel)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.settings_delete_reauth_later_hint)).assertExists()
        composeRule
            .onNodeWithText(
                text(R.string.settings_delete_reauth_later),
            ).assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, later)
        composeRule.onAllNodesWithText(text(R.string.auth_signin_forgot_password)).assertCountEquals(0)
    }

    @Test
    fun reauth_noUsableMethod_saysSoCalmly_andGivesTheContact() {
        reauth(DeleteReauthUiState("lea@example.org", setOf(), last = false))
        composeRule.onNodeWithText(BuildConfig.SUPPORT_EMAIL, substring = true).assertExists()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    // --- The deletion under way, the pauses, the end ----------------------------------------------------------------------

    private class Controls(
        initial: AccountDeletionState,
    ) : AccountDeletionControls {
        override val state: StateFlow<AccountDeletionState> = MutableStateFlow(initial)
        var retries = 0
        var acknowledged = 0

        override fun start() = Unit

        override fun resume() = Unit

        override fun retry() {
            retries++
        }

        override fun reauthenticated() = Unit

        override fun acknowledge() {
            acknowledged++
        }
    }

    private fun host(
        state: AccountDeletionState,
        controls: Controls = Controls(state),
        onLeave: () -> Unit = {},
    ) = show {
        AccountDeletionContent(state = state, controls = controls, reauthHost = {}, onLeave = onLeave)
    }

    @Test
    fun progress_oneLineOfStep_noBackNoCancel_noPercentage() {
        host(AccountDeletionState.Running(resumed = false))
        shoot("delete-progress")
        composeRule.onNodeWithText(text(R.string.settings_delete_progress_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_delete_progress_profile)).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_delete_progress_hint)).assertExists()
        composeRule.onAllNodesWithContentDescription(text(R.string.auth_back_a11y)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.settings_delete_cancel)).assertCountEquals(0)
        composeRule.onAllNodesWithText("%", substring = true).assertCountEquals(0)
    }

    @Test
    fun resumed_atLaunch_saysWeAreFinishing_withoutAskingAnythingAgain() {
        host(AccountDeletionState.Running(resumed = true))
        shoot("delete-resumed")
        composeRule.onNodeWithText(text(R.string.settings_delete_resume_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_delete_resume)).assertExists()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun pausedWithoutNetwork_isCalm_andSaysTheAppCanBeClosed() {
        host(AccountDeletionState.WaitingForNetwork)
        shoot("delete-paused")
        composeRule.onNodeWithText(text(R.string.settings_delete_paused_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_delete_paused)).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_delete_paused_leave)).assertExists()
        listOf("error", "failed", "quota").forEach {
            composeRule.onAllNodesWithText(it, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }

    @Test
    fun stuck_givesTheContact_andARetryButton_withoutAlarm() {
        val controls = Controls(AccountDeletionState.Stuck(miffsRemain = false))
        host(AccountDeletionState.Stuck(miffsRemain = false), controls)
        shoot("delete-stuck")
        composeRule.onNodeWithText(text(R.string.settings_delete_failed_title)).assertExists()
        composeRule.onNodeWithText(BuildConfig.SUPPORT_EMAIL, substring = true).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_delete_retry)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, controls.retries)
    }

    @Test
    fun theEnd_oneCalmSentenceAndOneButton_noAppleStepsWithoutApple() {
        var left = 0
        host(AccountDeletionState.Done(appleLinked = false), onLeave = { left++ })
        shoot("delete-done")
        composeRule.onNodeWithText(text(R.string.settings_delete_done_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_delete_done_body)).assertExists()
        composeRule.onAllNodesWithText(text(R.string.settings_delete_done_apple_android)).assertCountEquals(0)
        composeRule
            .onNodeWithText(
                text(R.string.settings_delete_done_continue),
            ).assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, left)
    }

    @Test
    fun theEnd_forAnAccountLinkedToApple_saysHowToRemoveTheAuthorizationByHand() {
        host(AccountDeletionState.Done(appleLinked = true))
        shoot("delete-done-apple")
        composeRule.onNodeWithText(text(R.string.settings_delete_done_apple_android)).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_delete_apple_howto_title)).assertExists()
        composeRule.onNodeWithText("1. " + text(R.string.settings_delete_apple_howto_step1)).assertExists()
        composeRule.onNodeWithText("3. " + text(R.string.settings_delete_apple_howto_step3)).assertExists()
    }

    @Test
    fun theLastVerification_isDelegatedToItsOwnScreen() {
        var shown = 0
        show {
            AccountDeletionContent(
                state = AccountDeletionState.NeedsRecentLogin,
                controls = Controls(AccountDeletionState.NeedsRecentLogin),
                reauthHost = { shown++ },
                onLeave = {},
            )
        }
        assertEquals(1, shown)
    }

    @Test
    fun theBanner_saysItCalmly_andGoesAtATouch() {
        var dismissed = 0
        show { AccountDeletedBanner(onDismiss = { dismissed++ }) }
        shoot("delete-banner")
        composeRule.onNodeWithText(text(R.string.auth_account_deleted_notice)).assertIsDisplayed().performClick()
        assertEquals(1, dismissed)
    }

    // --- The entry from the waiting screens (a person without a Miff never sees Settings) ----------------------------------

    @Test
    fun theEntry_isOnTheCreateOrJoinScreen_nextToSignOut_aDiscreetRedTextLink() {
        var deleted = 0
        var signedOut = 0
        show {
            MiffChoiceScreen(
                onCreate = {},
                onJoin = {},
                onSignOut = { signedOut++ },
                onDeleteAccount = { deleted++ },
            )
        }
        shoot("delete-entry-miff-choice")
        composeRule.onNodeWithText(text(R.string.auth_signout_action)).assertHasClickAction()
        composeRule
            .onNodeWithText(text(R.string.settings_delete_entry))
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, deleted)
        assertEquals(0, signedOut)
    }

    @Test
    fun theEntry_isOnTheScreenOfAnAccountThatCouldNotBeRead_too() {
        var deleted = 0
        show { AccountLookupFailedScreen(onRetry = {}, onSignOut = {}, onDeleteAccount = { deleted++ }) }
        composeRule
            .onNodeWithText(text(R.string.settings_delete_entry))
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, deleted)
    }
}
