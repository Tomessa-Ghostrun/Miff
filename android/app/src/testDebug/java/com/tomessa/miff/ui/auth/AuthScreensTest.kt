package com.tomessa.miff.ui.auth

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
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
    fun legalLinks_pointToTheHttpsPagesOfTheEnglishSite() {
        val terms = LegalLinks.termsUrl(context())
        val privacy = LegalLinks.privacyUrl(context())
        assertTrue(terms, terms.startsWith("https://") && terms.endsWith("/en/terms"))
        assertTrue(privacy, privacy.startsWith("https://") && privacy.endsWith("/en/privacy"))
        assertFalse("no double slash after the address", terms.removePrefix("https://").contains("//"))
        assertFalse(privacy.removePrefix("https://").contains("//"))
    }

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
    fun legalLinks_pointToTheHttpsPagesOfTheFrenchSite() {
        val terms = LegalLinks.termsUrl(context())
        val privacy = LegalLinks.privacyUrl(context())
        assertTrue(terms, terms.startsWith("https://") && terms.endsWith("/fr/cgu"))
        assertTrue(privacy, privacy.startsWith("https://") && privacy.endsWith("/fr/confidentialite"))
    }

    @Test
    fun signUp_theLegalLinksOpenTheBrowserOnThoseAddresses() {
        signUp()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val actions =
            composeRule
                .onNodeWithText(notice(R.string.auth_consent_notice))
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
        composeRule.runOnIdle { actions.first { it.label == text(R.string.auth_consent_terms_link) }.action() }
        composeRule.runOnIdle { actions.first { it.label == text(R.string.auth_consent_privacy_link) }.action() }
        val shadow = Shadows.shadowOf(app)
        val first = shadow.nextStartedActivity
        val second = shadow.nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, first.action)
        assertEquals(Intent.ACTION_VIEW, second.action)
        // Robolectric does not promise the order in which started activities are handed back.
        assertEquals(
            setOf(LegalLinks.termsUrl(context()), LegalLinks.privacyUrl(context())),
            setOf(first.dataString, second.dataString),
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
        composeRule.onNodeWithContentDescription(text(R.string.onboarding_slide1_illustration_a11y)).assertExists()

        composeRule
            .onNodeWithContentDescription(text(R.string.onboarding_slide1_illustration_a11y))
            .performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Page 2 of 3").assertExists()
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
