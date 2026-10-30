package com.tomessa.miff.ui.auth

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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.ui.onboarding.WelcomeCarouselScreen
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
 * Screen tests on the JVM (Robolectric, no phone): structure of the screens against the mockups, order of the elements,
 * the explanatory line instead of boxes, masked password, touch targets, large font, the welcome carousel. Also writes
 * screenshots to build/screenshots for the review. Plain Application on purpose: Firebase is never initialised here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-port-xxhdpi")
class AuthScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private fun notice(sentence: Int): String {
        val terms = text(R.string.auth_consent_terms_link)
        val privacy = text(R.string.auth_consent_privacy_link)
        return context().getString(sentence, terms, privacy)
    }

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { MiffTheme { Box(Modifier.fillMaxSize()) { content() } } }
    }

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val isCheckbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    private fun signUp(
        state: SignUpUiState = SignUpUiState(),
        onGoogle: () -> Unit = {},
    ) = show {
        SignUpScreen(
            state = state,
            onBack = null,
            onSubmitEmail = { _, _ -> },
            onGoogle = onGoogle,
            onGoToSignIn = {},
        )
    }

    @Test
    fun signUp_hasNoBoxToTick_andTheButtonIsGreyUntilBothFieldsAreFilled() {
        signUp()
        shoot("signup-empty")
        composeRule.onAllNodes(isCheckbox).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.auth_signup_action)).assertIsNotEnabled()

        composeRule.onNodeWithText(text(R.string.auth_email_label)).performTextInput("a@b.fr")
        composeRule.onNodeWithText(text(R.string.auth_signup_action)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.auth_password_label)).performTextInput("x")
        composeRule.onNodeWithText(text(R.string.auth_signup_action)).assertIsEnabled()
    }

    @Test
    fun signUp_theMainButtonIsCalledJoin_likeTheMockup() {
        signUp()
        assertEquals("Join", text(R.string.auth_signup_action))
        composeRule.onNodeWithText("Join").assertExists()
    }

    @Test
    fun signUp_theExplanatoryLineSitsJustAboveTheMainButton() {
        signUp()
        val line = composeRule.onNodeWithText(notice(R.string.auth_consent_notice)).getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithText(text(R.string.auth_signup_action)).getUnclippedBoundsInRoot()
        val google = composeRule.onNodeWithText(text(R.string.auth_google_action)).getUnclippedBoundsInRoot()
        assertTrue("the line is above the main button", line.bottom <= button.top)
        assertTrue("and close to it (nothing else in between)", button.top - line.bottom < 32.dp)
        assertTrue("the Google button comes first, as in the mockup", google.top < line.top)
    }

    @Test
    fun signUp_theLegalLinksAreReachableWithTheScreenReader() {
        signUp()
        val actions =
            composeRule
                .onNodeWithText(notice(R.string.auth_consent_notice))
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
        assertEquals(
            listOf(text(R.string.auth_consent_terms_link), text(R.string.auth_consent_privacy_link)),
            actions.map { it.label },
        )
    }

    @Test
    fun signUp_theNoticeMentionsAcceptanceByTheTap_termsPolicyAndStatistics() {
        val sentence = notice(R.string.auth_consent_notice)
        assertTrue(sentence, sentence.contains("Terms of Use") && sentence.contains("Privacy Policy"))
        assertTrue(sentence, sentence.contains("usage statistics") && sentence.contains("crash reports"))
        assertTrue(sentence, sentence.contains("Settings"))
    }

    @Test
    fun signUp_theEyeAppearsWithTheFirstCharacter_andThePasswordStaysMasked() {
        signUp()
        composeRule.onAllNodesWithText(text(R.string.auth_password_show_a11y)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.auth_password_label)).performTextInput("motdepasse12")
        composeRule.onNodeWithContentDescription(text(R.string.auth_password_show_a11y)).assertExists()
        val field = composeRule.onNodeWithText(text(R.string.auth_password_label)).fetchSemanticsNode().config
        assertTrue("flagged as a password field", field.contains(SemanticsProperties.Password))
        val shown = field[SemanticsProperties.EditableText].text
        assertTrue("the screen reader never gets the characters: $shown", !shown.contains("motdepasse"))
        composeRule.onNodeWithContentDescription(text(R.string.auth_password_show_a11y)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.auth_password_hide_a11y)).assertExists()
    }

    @Test
    fun signUp_touchTargetsAreAtLeast48dp() {
        signUp()
        composeRule.onNodeWithText(text(R.string.auth_signup_action)).performScrollTo().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText(text(R.string.auth_google_action)).performScrollTo().assertHasClickAction()
        composeRule.onNodeWithText(text(R.string.auth_signup_have_account)).performScrollTo().assertHasClickAction()
    }

    @Test
    fun signUp_errorsAreWrittenInWords() {
        signUp(state = SignUpUiState(message = AuthMessage.EMAIL_IN_USE))
        shoot("signup-error")
        composeRule.onNodeWithText(text(R.string.auth_error_email_in_use)).assertExists()
    }

    @Test
    fun signUp_largestFont_everythingStillReachable() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 2f)) {
                SignUpScreen(SignUpUiState(), null, { _, _ -> }, {}, {})
            }
        }
        composeRule.onNodeWithText(text(R.string.auth_signup_action)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.auth_google_action)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.auth_signup_have_account)).performScrollTo().assertIsDisplayed()
    }

    /** Mockup 04: wordmark header, no visible title, "Ou :", underlined link; the screen is still named for TalkBack. */
    @Test
    fun signUp_followsTheMockupStructure() {
        signUp()
        composeRule.onNodeWithContentDescription(text(R.string.home_logo_a11y)).assertExists()
        composeRule.onAllNodesWithText(text(R.string.auth_signup_title)).assertCountEquals(0) // no visible title
        val google = composeRule.onNodeWithText(text(R.string.auth_google_action)).getUnclippedBoundsInRoot()
        val or = composeRule.onNodeWithText(text(R.string.auth_or_divider)).getUnclippedBoundsInRoot()
        val email = composeRule.onNodeWithText(text(R.string.auth_email_label)).getUnclippedBoundsInRoot()
        assertTrue("social button, then Ou :, then the fields", google.top < or.top && or.top < email.top)
        val pane =
            composeRule
                .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
                .fetchSemanticsNodes()
                .map { it.config[SemanticsProperties.PaneTitle] }
        assertTrue("screen named for the screen reader: $pane", pane.contains(text(R.string.auth_signup_title)))
    }

    @Test
    fun signIn_hasNoNotice_noBox_andOneErrorUnderTheFields() {
        show {
            SignInScreen(
                state = SignInUiState(message = AuthMessage.INVALID_CREDENTIALS),
                onBack = {},
                onSubmitEmail = { _, _ -> },
                onGoogle = {},
                onGoToSignUp = {},
                onForgotPassword = {},
            )
        }
        shoot("signin-error")
        composeRule.onAllNodes(isCheckbox).assertCountEquals(0)
        composeRule.onAllNodesWithText(notice(R.string.auth_consent_notice)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.auth_error_invalid_credentials)).assertExists()
        composeRule.onNodeWithText(text(R.string.auth_signin_action)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.auth_signin_no_account)).assertHasClickAction()
    }

    @Test
    fun consentPanel_hasNoBox_theLineIsAboveContinue_andBothButtonsWork() {
        var continued = 0
        var refused = 0
        show {
            ConsentPanelScreen(
                state = ConsentPanelUiState(),
                onContinue = { continued++ },
                onRefuse = { refused++ },
            )
        }
        shoot("panel")
        composeRule.onAllNodes(isCheckbox).assertCountEquals(0)
        val line = composeRule.onNodeWithText(notice(R.string.auth_panel_notice)).getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithText(text(R.string.auth_panel_continue)).getUnclippedBoundsInRoot()
        assertTrue(line.bottom <= button.top)
        composeRule.onNodeWithText(text(R.string.auth_panel_continue)).assertIsEnabled().performClick()
        composeRule.onNodeWithText(text(R.string.auth_panel_cancel)).performClick()
        assertEquals(1, continued)
        assertEquals(1, refused)
    }

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
    fun frenchScreens_showTheTutoiementTexts() {
        signUp()
        shoot("signup-fr")
        composeRule.onNodeWithText("Continuer avec Google").performScrollTo().assertExists()
        composeRule.onNodeWithText("Ou :").assertExists()
        composeRule.onNodeWithText("Rejoindre").assertExists()
        composeRule.onNodeWithText("Déjà un compte ?").assertExists()
        val line = notice(R.string.auth_consent_notice)
        assertTrue(
            line,
            line.startsWith("En cliquant sur « Rejoindre »") &&
                line.contains("tu acceptes nos Conditions d'utilisation"),
        )
    }

    // --- Sign-in: "Mot de passe oublié ?" (mockup -8) ---------------------------------------------------------

    private fun signIn(
        state: SignInUiState = SignInUiState(),
        onForgotPassword: (String) -> Unit = {},
    ) = show {
        SignInScreen(
            state = state,
            onBack = {},
            onSubmitEmail = { _, _ -> },
            onGoogle = {},
            onGoToSignUp = {},
            onForgotPassword = onForgotPassword,
        )
    }

    @Test
    fun signIn_theForgotPasswordLinkSitsUnderThePasswordField_andIsEasyToHit_C7() {
        signIn()
        shoot("signin-forgot-link")
        val link = composeRule.onNodeWithText(text(R.string.auth_signin_forgot_password))
        link.assertHasClickAction().assertHeightIsAtLeast(48.dp)
        val password = composeRule.onNodeWithText(text(R.string.auth_password_label)).getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithText(text(R.string.auth_signin_action)).getUnclippedBoundsInRoot()
        val bounds = link.getUnclippedBoundsInRoot()
        assertTrue("under the password field", bounds.top >= password.bottom - 16.dp)
        assertTrue("above the main button, as in the mockup", bounds.bottom <= button.top)
    }

    @Test
    fun signIn_theForgotPasswordLink_carriesTheAddressAlreadyTyped_withoutItsSpaces() {
        val received = mutableListOf<String>()
        signIn(onForgotPassword = { received += it })
        composeRule.onNodeWithText(text(R.string.auth_email_label)).performTextInput("  toto@gmail.com ")
        composeRule.onNodeWithText(text(R.string.auth_signin_forgot_password)).performClick()
        assertEquals(listOf("toto@gmail.com"), received)
    }

    @Test
    fun signIn_theLinkWorksEvenWithNothingTyped() {
        val received = mutableListOf<String>()
        signIn(onForgotPassword = { received += it })
        composeRule.onNodeWithText(text(R.string.auth_signin_forgot_password)).performClick()
        assertEquals(listOf(""), received)
    }

    @Test
    fun signIn_errorTexts_sendToTheLink_inEnglish() {
        assertEquals(
            "This combination does not work. Check your e-mail address and your password, " +
                "or choose “Forgot password?”.",
            text(R.string.auth_error_invalid_credentials),
        )
        assertEquals(
            "A few too many attempts for now. Please wait a few minutes or reset your password.",
            text(R.string.auth_error_too_many),
        )
        assertEquals("Forgot password?", text(R.string.auth_signin_forgot_password))
    }

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
    fun signIn_errorTexts_sendToTheLink_inFrench_tutoiement() {
        signIn(state = SignInUiState(message = AuthMessage.INVALID_CREDENTIALS))
        shoot("signin-forgot-fr")
        assertEquals(
            "Cette combinaison ne fonctionne pas. Vérifie ton adresse e-mail et ton mot de passe, " +
                "ou choisis « Mot de passe oublié ? ».",
            text(R.string.auth_error_invalid_credentials),
        )
        assertEquals(
            "Un peu trop d'essais pour le moment. Patiente quelques minutes ou réinitialise ton mot de passe.",
            text(R.string.auth_error_too_many),
        )
        composeRule.onNodeWithText("Mot de passe oublié ?").performScrollTo().assertHasClickAction()
    }

    // --- "Mot de passe oublié" screen (mockup -10) ---------------------------------------------------------------

    private fun reset(
        state: PasswordResetUiState = PasswordResetUiState(),
        initialEmail: String = "",
        onSubmit: (String) -> Unit = {},
        onBackToSignIn: () -> Unit = {},
    ) = show {
        PasswordResetScreen(
            state = state,
            initialEmail = initialEmail,
            onBack = {},
            onEmailChanged = {},
            onSubmit = onSubmit,
            onBackToSignIn = onBackToSignIn,
        )
    }

    @Test
    fun reset_followsTheMockup_titleIntroFieldAndAGreyButton_M1() {
        reset()
        shoot("reset-empty")
        composeRule.onNodeWithText(text(R.string.auth_reset_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.auth_reset_intro)).assertExists()
        composeRule.onNodeWithText(text(R.string.auth_email_label)).assertExists()
        composeRule.onNodeWithText(text(R.string.auth_reset_action)).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(text(R.string.home_logo_a11y)).assertExists()
        composeRule.onNodeWithContentDescription(text(R.string.auth_back_a11y)).assertHasClickAction()
        // No password, no social button, no box to tick: nothing else than the address.
        composeRule.onAllNodesWithText(text(R.string.auth_password_label)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.auth_google_action)).assertCountEquals(0)
        composeRule.onAllNodes(isCheckbox).assertCountEquals(0)
        // Order of the mockup: title, intro, field, button.
        val title = composeRule.onNodeWithText(text(R.string.auth_reset_title)).getUnclippedBoundsInRoot()
        val intro = composeRule.onNodeWithText(text(R.string.auth_reset_intro)).getUnclippedBoundsInRoot()
        val field = composeRule.onNodeWithText(text(R.string.auth_email_label)).getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithText(text(R.string.auth_reset_action)).getUnclippedBoundsInRoot()
        assertTrue(title.bottom <= intro.top && intro.bottom <= field.top && field.bottom <= button.top)
    }

    @Test
    fun reset_theButtonComesAliveWithTheFirstCharacter_andSendsTheAddress_M1() {
        val sent = mutableListOf<String>()
        reset(onSubmit = { sent += it })
        composeRule.onNodeWithText(text(R.string.auth_email_label)).performTextInput("a@b.fr")
        composeRule.onNodeWithText(text(R.string.auth_reset_action)).assertIsEnabled().performClick()
        assertEquals(listOf("a@b.fr"), sent)
    }

    @Test
    fun reset_aPrefilledAddress_isShown_andTheButtonIsReady() {
        reset(initialEmail = "toto@gmail.com")
        composeRule.onNodeWithText("toto@gmail.com").assertExists()
        composeRule.onNodeWithText(text(R.string.auth_reset_action)).assertIsEnabled()
    }

    @Test
    fun reset_aBadAddress_getsTheFormatMessage_inWords_andForTheScreenReader_M1() {
        reset(state = PasswordResetUiState(showFieldError = true), initialEmail = "not-an-address")
        shoot("reset-format-error")
        composeRule.onNodeWithText(text(R.string.auth_error_email_format)).assertExists()
        val field = composeRule.onNode(hasSetTextAction()).fetchSemanticsNode().config
        assertEquals(text(R.string.auth_error_email_format), field[SemanticsProperties.Error])
    }

    @Test
    fun reset_noFormatMessage_whileTyping_orForAGoodAddress() {
        reset(state = PasswordResetUiState(showFieldError = true), initialEmail = "a@b.fr")
        composeRule.onAllNodesWithText(text(R.string.auth_error_email_format)).assertCountEquals(0)
    }

    @Test
    fun reset_aFailedResend_showsTheErrorAlone_noConfirmationNextToIt() {
        // What the view model gives after a resend that failed: sent = false, with the message.
        reset(state = PasswordResetUiState(sent = false, message = AuthMessage.NETWORK), initialEmail = "a@b.fr")
        composeRule.onNodeWithText(text(R.string.auth_error_network)).assertExists()
        composeRule.onAllNodesWithText(text(R.string.auth_reset_sent)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.auth_reset_action)).assertIsEnabled()
    }

    @Test
    fun reset_noFormatMessage_whileTheAddressIsStillBeingTyped() {
        reset()
        composeRule.onNodeWithText(text(R.string.auth_email_label)).performTextInput("not-an")
        composeRule.onAllNodesWithText(text(R.string.auth_error_email_format)).assertCountEquals(0)
    }

    @Test
    fun reset_afterTheSend_oneConfirmation_aPausedButton_andTheWayBack_M2_M5() {
        var back = 0
        reset(
            state = PasswordResetUiState(sent = true, resendLocked = true),
            initialEmail = "a@b.fr",
            onBackToSignIn = { back++ },
        )
        shoot("reset-sent")
        composeRule.onNodeWithText(text(R.string.auth_reset_sent)).assertExists()
        composeRule.onNodeWithText(text(R.string.auth_reset_resend_wait)).assertExists()
        composeRule.onNodeWithText(text(R.string.auth_reset_resend_action)).assertIsNotEnabled()
        composeRule.onAllNodesWithText(text(R.string.auth_reset_action)).assertCountEquals(0)
        composeRule
            .onNodeWithText(
                text(R.string.auth_reset_back_to_signin),
            ).performScrollTo()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText(text(R.string.auth_reset_back_to_signin)).performClick()
        assertEquals(1, back)
    }

    @Test
    fun reset_afterThePause_theButtonIsBack_andTheConfirmationStays() {
        reset(state = PasswordResetUiState(sent = true, resendLocked = false), initialEmail = "a@b.fr")
        composeRule.onNodeWithText(text(R.string.auth_reset_sent)).assertExists()
        composeRule.onAllNodesWithText(text(R.string.auth_reset_resend_wait)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.auth_reset_resend_action)).assertIsEnabled()
    }

    @Test
    fun reset_theConfirmation_makesNoPromiseThatAnAccountExists_M2() {
        // The screen is only given "sent": nothing that could depend on the account. And the words themselves are
        // conditional ("if an account exists").
        assertTrue(text(R.string.auth_reset_sent).startsWith("If an account exists with this address"))
        assertTrue(text(R.string.auth_reset_intro).contains("If it matches an account"))
    }

    @Test
    fun reset_messagesAreAnnouncedPolitely_andLoadingIsDescribed() {
        reset(state = PasswordResetUiState(sent = true, message = AuthMessage.NETWORK, isLoading = true))
        for (id in listOf(R.string.auth_reset_sent, R.string.auth_error_network)) {
            val config = composeRule.onNodeWithText(text(id)).fetchSemanticsNode().config
            assertEquals(LiveRegionMode.Polite, config[SemanticsProperties.LiveRegion])
        }
        composeRule.onNodeWithContentDescription(text(R.string.auth_loading_a11y)).assertExists()
        composeRule.onNodeWithText(text(R.string.auth_reset_resend_action)).assertIsNotEnabled()
    }

    @Test
    fun reset_theScreenIsNamedAndTheTitleIsAHeading_forTheScreenReader() {
        reset()
        val pane =
            composeRule
                .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
                .fetchSemanticsNodes()
                .map { it.config[SemanticsProperties.PaneTitle] }
        assertTrue("screen named: $pane", pane.contains(text(R.string.auth_reset_title)))
        val heading = composeRule.onNodeWithText(text(R.string.auth_reset_title)).fetchSemanticsNode().config
        assertTrue(heading.contains(SemanticsProperties.Heading))
    }

    @Test
    fun reset_theFieldDeclaresAnAutofillHint_andIsNotAPasswordField() {
        reset()
        val config = composeRule.onNode(hasSetTextAction()).fetchSemanticsNode().config
        assertTrue("autofill hint declared", config.contains(SemanticsProperties.ContentType))
        assertTrue("not a password field", !config.contains(SemanticsProperties.Password))
    }

    @Test
    fun reset_largestFont_everythingStillReachable() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 2f)) {
                PasswordResetScreen(
                    state = PasswordResetUiState(sent = true, resendLocked = true),
                    initialEmail = "a@b.fr",
                    onBack = {},
                    onEmailChanged = {},
                    onSubmit = {},
                    onBackToSignIn = {},
                )
            }
        }
        composeRule.onNodeWithText(text(R.string.auth_reset_resend_action)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.auth_reset_back_to_signin)).performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
    fun reset_french_showsTheTutoiementTexts() {
        reset(state = PasswordResetUiState(sent = true, resendLocked = true), initialEmail = "a@b.fr")
        shoot("reset-fr")
        composeRule.onNodeWithText("Mot de passe oublié").assertExists()
        composeRule.onNodeWithText("Renvoyer l'e-mail").assertExists()
        composeRule.onNodeWithText("Tu peux demander un nouvel e-mail dans quelques instants.").assertExists()
        assertEquals(
            "Si un compte existe avec cette adresse, un e-mail vient d'être envoyé. " +
                "Pense à regarder dans tes courriers indésirables. Si tu t'es inscrit avec Apple ou Google, " +
                "utilise plutôt le bouton correspondant sur l'écran de connexion.",
            text(R.string.auth_reset_sent),
        )
        assertEquals("Cette adresse e-mail ne semble pas correcte.", text(R.string.auth_error_email_format))
        assertEquals("Envoyer", text(R.string.auth_reset_action))
    }

    // --- Welcome carousel -------------------------------------------------------------------------------------

    private fun carousel(
        onStart: () -> Unit = {},
        onHaveAccount: () -> Unit = {},
    ) = show { WelcomeCarouselScreen(onStart = onStart, onHaveAccount = onHaveAccount) }

    @Test
    fun carousel_threePages_announcedForTheScreenReader_andManualSwipe() {
        carousel()
        shoot("carousel-1")
        composeRule.onNodeWithContentDescription("Page 1 of 3").assertExists()
        composeRule.onNodeWithText(text(R.string.onboarding_slide1_caption)).assertExists()
        composeRule.onNodeWithContentDescription(text(R.string.onboarding_slide1_illustration_a11y)).assertExists()

        composeRule
            .onNodeWithContentDescription(text(R.string.onboarding_slide1_illustration_a11y))
            .performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Page 2 of 3").assertExists()
        composeRule.onNodeWithText(text(R.string.onboarding_slide2_caption)).assertExists()
        shoot("carousel-2")

        composeRule
            .onNodeWithContentDescription(text(R.string.onboarding_slide2_illustration_a11y))
            .performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Page 3 of 3").assertExists()
        shoot("carousel-3")
    }

    @Test
    fun carousel_hasTheStartButtonAndTheLink_andNoSignUpField() {
        var started = 0
        var haveAccount = 0
        carousel(onStart = { started++ }, onHaveAccount = { haveAccount++ })
        composeRule.onNodeWithText(text(R.string.onboarding_headline)).assertExists()
        composeRule.onNodeWithText(text(R.string.onboarding_start)).assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText(text(R.string.onboarding_have_account)).performClick()
        assertEquals(1, started)
        assertEquals(1, haveAccount)
        composeRule.onAllNodesWithText(text(R.string.auth_email_label)).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
    fun carousel_french() {
        carousel()
        shoot("carousel-fr")
        composeRule.onNodeWithContentDescription("Page 1 sur 3").assertExists()
        composeRule.onNodeWithText("Commencer").assertExists()
        composeRule.onNodeWithText("J'ai déjà un compte").assertExists()
    }

    @Test
    fun carousel_largestFont_theButtonStaysReachable() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 2f)) {
                WelcomeCarouselScreen(onStart = {}, onHaveAccount = {})
            }
        }
        composeRule.onNodeWithText(text(R.string.onboarding_start)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.onboarding_have_account)).performScrollTo().assertIsDisplayed()
    }
}
