package com.tomessa.miff.ui.auth

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
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
 * Screen tests on the JVM (Robolectric, no phone): initial states, order of the elements, masked password, touch
 * targets, and readable at the largest font. Also writes screenshots to build/screenshots for the review.
 * Plain Application on purpose: Firebase is never initialised here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-port-xxhdpi")
class AuthScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun text(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun termsSentence(): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val terms = context.getString(R.string.auth_consent_terms_link)
        val privacy = context.getString(R.string.auth_consent_privacy_link)
        return context.getString(R.string.auth_consent_terms, terms, privacy)
    }

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { MiffTheme { Box(Modifier.fillMaxSize()) { content() } } }
    }

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun signUp(
        state: SignUpUiState = SignUpUiState(),
        onSubmit: (String, String) -> Unit = { _, _ -> },
        onTerms: (Boolean) -> Unit = {},
        onGoogle: () -> Unit = {},
    ) = show {
        SignUpScreen(
            state = state,
            onBack = null,
            onTermsChecked = onTerms,
            onAnalyticsChecked = {},
            onSubmitEmail = onSubmit,
            onGoogle = onGoogle,
            onGoToSignIn = {},
        )
    }

    @Test
    fun signUp_boxesAreUnticked_andTheButtonIsGreyUntilBothFieldsAreFilled() {
        signUp()
        shoot("signup-empty")
        composeRule.onNodeWithText(termsSentence()).assertIsOff()
        composeRule.onNodeWithText(text(R.string.auth_consent_analytics)).assertIsOff()
        composeRule.onNodeWithText(text(R.string.auth_signup_action)).assertIsNotEnabled()

        composeRule.onNodeWithText(text(R.string.auth_email_label)).performTextInput("a@b.fr")
        composeRule.onNodeWithText(text(R.string.auth_signup_action)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.auth_password_label)).performTextInput("x")
        composeRule.onNodeWithText(text(R.string.auth_signup_action)).assertIsEnabled()
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
    fun signUp_bothBoxesComeBeforeEverySignUpButton() {
        signUp()
        val terms = composeRule.onNodeWithText(termsSentence()).getUnclippedBoundsInRoot()
        val analytics = composeRule.onNodeWithText(text(R.string.auth_consent_analytics)).getUnclippedBoundsInRoot()
        val create = composeRule.onNodeWithText(text(R.string.auth_signup_action)).getUnclippedBoundsInRoot()
        val google = composeRule.onNodeWithText(text(R.string.auth_google_action)).getUnclippedBoundsInRoot()
        assertTrue(terms.top < analytics.top)
        assertTrue("(a) and (b) before the e-mail button", analytics.top < create.top)
        assertTrue("(a) and (b) before the Google button", analytics.top < google.top)
    }

    @Test
    fun signUp_ticksAreReported_andTheMissingTermsMessageIsShown() {
        var ticked: Boolean? = null
        signUp(state = SignUpUiState(termsMissing = true), onTerms = { ticked = it })
        composeRule.onNodeWithText(text(R.string.auth_error_terms_required)).assertExists()
        composeRule.onNodeWithText(termsSentence()).performClick()
        assertEquals(true, ticked)
    }

    @Test
    fun signUp_touchTargetsAreAtLeast48dp() {
        signUp()
        listOf(
            termsSentence(),
            text(R.string.auth_consent_analytics),
        ).forEach { composeRule.onNodeWithText(it).performScrollTo().assertHeightIsAtLeast(48.dp) }
        composeRule.onNodeWithText(text(R.string.auth_signup_action)).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText(text(R.string.auth_google_action)).performScrollTo().assertHasClickAction()
        composeRule.onNodeWithText(text(R.string.auth_consent_terms_link)).performScrollTo().assertHasClickAction()
        composeRule.onNodeWithText(text(R.string.auth_consent_privacy_link)).performScrollTo().assertHasClickAction()
    }

    @Test
    fun signUp_errorsAreWrittenInWords() {
        signUp(state = SignUpUiState(message = AuthMessage.EMAIL_IN_USE, termsChecked = true))
        shoot("signup-error")
        composeRule.onNodeWithText(text(R.string.auth_error_email_in_use)).assertExists()
    }

    @Test
    fun signUp_largestFont_everythingStillReachable() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 2f)) {
                SignUpScreen(SignUpUiState(), null, {}, {}, { _, _ -> }, {}, {})
            }
        }
        composeRule.onNodeWithText(text(R.string.auth_signup_action)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.auth_google_action)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.auth_signup_have_account)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun signIn_hasNoConsentBox_andOneErrorUnderTheFields() {
        show {
            SignInScreen(
                state = SignInUiState(message = AuthMessage.INVALID_CREDENTIALS),
                onBack = null,
                onSubmitEmail = { _, _ -> },
                onGoogle = {},
                onGoToSignUp = {},
            )
        }
        shoot("signin-error")
        composeRule.onAllNodesWithText(text(R.string.auth_consent_analytics)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.auth_error_invalid_credentials)).assertExists()
        composeRule.onNodeWithText(text(R.string.auth_signin_action)).assertIsNotEnabled()
    }

    @Test
    fun consentPanel_continueWaitsForBoxA_andBothBoxesStartUnticked() {
        var continued = 0
        var state by mutableStateOf(ConsentPanelUiState())
        show {
            ConsentPanelScreen(
                state = state,
                onTermsChecked = { state = state.copy(termsChecked = it) },
                onAnalyticsChecked = { state = state.copy(analyticsChecked = it) },
                onContinue = { continued++ },
                onRefuse = {},
            )
        }
        shoot("panel-empty")
        composeRule.onNodeWithText(termsSentence()).assertIsOff()
        composeRule.onNodeWithText(text(R.string.auth_consent_analytics)).assertIsOff()
        composeRule.onNodeWithText(text(R.string.auth_panel_continue)).assertIsNotEnabled()

        composeRule.onNodeWithText(termsSentence()).performClick()
        composeRule.onNodeWithText(termsSentence()).assertIsOn()
        composeRule.onNodeWithText(text(R.string.auth_panel_continue)).assertIsEnabled().performClick()
        assertEquals(1, continued)
        composeRule.onNodeWithText(text(R.string.auth_consent_analytics)).assertIsOff()
    }

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
    fun frenchScreens_showTheTutoiementTexts() {
        signUp()
        shoot("signup-fr")
        composeRule.onNodeWithText("Continuer avec Google").performScrollTo().assertExists()
        composeRule.onNodeWithText("Ou :").assertExists()
        composeRule.onNodeWithText("Créer mon compte").assertExists()
        composeRule.onNodeWithText("Déjà un compte ?").assertExists()
    }

    /** Mockups 04 to 06: wordmark header, no visible title, "Ou :", underlined link; the screen is still named for TalkBack. */
    @Test
    fun signUpAndSignIn_followTheMockupStructure() {
        signUp()
        composeRule.onNodeWithContentDescription(text(R.string.home_logo_a11y)).assertExists()
        composeRule.onAllNodesWithText(text(R.string.auth_signup_title)).assertCountEquals(0) // no visible title
        composeRule.onNodeWithText(text(R.string.auth_or_divider)).assertExists()
        composeRule.onNodeWithText(text(R.string.auth_signup_have_account)).assertHasClickAction()
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
    fun signIn_followsTheMockupStructure() {
        show {
            SignInScreen(SignInUiState(), onBack = {}, onSubmitEmail = { _, _ -> }, onGoogle = {}, onGoToSignUp = {})
        }
        shoot("signin-empty")
        composeRule.onNodeWithContentDescription(text(R.string.auth_back_a11y)).assertHasClickAction()
        composeRule.onNodeWithText(text(R.string.auth_signin_no_account)).assertHasClickAction()
        composeRule.onNodeWithText(text(R.string.auth_signin_action)).assertIsNotEnabled()
    }
}
