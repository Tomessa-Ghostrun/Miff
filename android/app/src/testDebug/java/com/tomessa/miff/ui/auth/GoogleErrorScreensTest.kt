package com.tomessa.miff.ui.auth

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.data.auth.GoogleIdTokenSource
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.GoogleTokenResult
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.SessionHarness
import com.tomessa.miff.ui.theme.MiffTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sign-up and sign-in screens with their REAL view models, a scripted Google dialog and no phone: a failure of the
 * Google sign-in shows a calm message right under the Google button, closing the dialog shows nothing, the message stays
 * until the next attempt, TalkBack announces it, and the Google button (never blocked) tries again.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-port-xxhdpi")
class GoogleErrorScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() = scope.cancel()

    private class ScriptedGoogle(
        private vararg val results: GoogleTokenResult,
    ) : GoogleIdTokenSource {
        var calls = 0

        override suspend fun requestIdToken(): GoogleTokenResult = results[minOf(calls++, results.size - 1)]
    }

    private fun text(id: Int): String = context.getString(id)

    private fun failed(failure: AuthFailure) = GoogleTokenResult.Failed(failure)

    private fun harness(): SessionHarness =
        SessionHarness(scope).also {
            it.accounts.setLoaded(profileExists = true)
            it.manager.start()
        }

    @Composable
    private fun SignUpHost(
        viewModel: SignUpViewModel,
        source: GoogleIdTokenSource,
    ) {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        SignUpScreen(
            state = state,
            onBack = null,
            onSubmitEmail = viewModel::submitEmail,
            onGoogle = { viewModel.submitGoogle(source) },
            onGoToSignIn = {},
        )
    }

    @Composable
    private fun SignInHost(
        viewModel: SignInViewModel,
        source: GoogleIdTokenSource,
    ) {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        SignInScreen(
            state = state,
            onBack = null,
            onSubmitEmail = viewModel::submitEmail,
            onGoogle = { viewModel.submitGoogle(source) },
            onGoToSignUp = {},
            onForgotPassword = {},
        )
    }

    private fun showSignUp(
        source: GoogleIdTokenSource,
        network: FakeNetwork = FakeNetwork(),
    ) {
        val vm = SignUpViewModel(harness().manager, network)
        composeRule.setContent { MiffTheme { SignUpHost(vm, source) } }
    }

    private fun showSignIn(
        source: GoogleIdTokenSource,
        network: FakeNetwork = FakeNetwork(),
    ) {
        val vm = SignInViewModel(harness().manager, network)
        composeRule.setContent { MiffTheme { SignInHost(vm, source) } }
    }

    private fun tapGoogle() {
        composeRule.onNodeWithText(text(R.string.auth_google_action)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private val allGoogleTexts =
        listOf(
            R.string.auth_error_google_no_account,
            R.string.auth_error_google_services,
            R.string.auth_error_google_interrupted,
            R.string.auth_error_unknown,
            R.string.auth_error_network,
        )

    private fun assertNoErrorOnScreen() {
        for (id in allGoogleTexts) composeRule.onAllNodesWithText(text(id)).assertCountEquals(0)
    }

    // --- Sign-up -------------------------------------------------------------------------------------------------

    @Test
    fun signUp_noGoogleAccount_showsTheKindMessage_rightUnderTheGoogleButton() {
        showSignUp(ScriptedGoogle(failed(AuthFailure.GOOGLE_NO_ACCOUNT)))
        assertNoErrorOnScreen()
        tapGoogle()
        val message = composeRule.onNodeWithText(text(R.string.auth_error_google_no_account))
        message.assertExists()
        val google = composeRule.onNodeWithText(text(R.string.auth_google_action)).getUnclippedBoundsInRoot()
        val shown = message.getUnclippedBoundsInRoot()
        val or = composeRule.onNodeWithText(text(R.string.auth_or_divider)).getUnclippedBoundsInRoot()
        assertTrue("under the Google button", google.bottom <= shown.top)
        assertTrue("and above the e-mail form", shown.bottom <= or.top)
    }

    @Test
    fun signUp_theMessageIsAnnouncedByTheScreenReader_inWords() {
        showSignUp(ScriptedGoogle(failed(AuthFailure.GOOGLE_INTERRUPTED)))
        tapGoogle()
        val config =
            composeRule
                .onNodeWithText(text(R.string.auth_error_google_interrupted))
                .fetchSemanticsNode()
                .config
        assertEquals(LiveRegionMode.Polite, config[SemanticsProperties.LiveRegion])
    }

    @Test
    fun signUp_closingTheDialog_showsNothing_andTheButtonStaysUsable() {
        val source = ScriptedGoogle(GoogleTokenResult.Cancelled)
        showSignUp(source)
        tapGoogle()
        assertEquals(1, source.calls)
        assertNoErrorOnScreen()
        composeRule.onNodeWithText(text(R.string.auth_google_action)).assertIsEnabled()
    }

    @Test
    fun signUp_theMessageStays_andTheGoogleButtonTriesAgain_thenTheMessageGoesAway() {
        val source = ScriptedGoogle(failed(AuthFailure.GOOGLE_INTERRUPTED), GoogleTokenResult.Cancelled)
        showSignUp(source)
        tapGoogle()
        composeRule.onNodeWithText(text(R.string.auth_error_google_interrupted)).assertExists()
        composeRule.onNodeWithText(text(R.string.auth_google_action)).assertIsEnabled()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(text(R.string.auth_error_google_interrupted)).assertExists() // still there

        tapGoogle() // the Google button itself relaunches, the screen was never blocked
        assertEquals(2, source.calls)
        assertNoErrorOnScreen()
    }

    @Test
    fun signUp_aSecondFailureReplacesTheFirst() {
        val source =
            ScriptedGoogle(failed(AuthFailure.GOOGLE_NO_ACCOUNT), failed(AuthFailure.GOOGLE_SERVICES_UNAVAILABLE))
        showSignUp(source)
        tapGoogle()
        tapGoogle()
        composeRule.onAllNodesWithText(text(R.string.auth_error_google_no_account)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.auth_error_google_services)).assertExists()
    }

    @Test
    fun signUp_noNetwork_saysNoConnection_underTheButton() {
        val source = ScriptedGoogle(GoogleTokenResult.Token("t"))
        showSignUp(source, network = FakeNetwork(online = false))
        tapGoogle()
        composeRule.onNodeWithText(text(R.string.auth_error_network)).assertExists()
        assertEquals(0, source.calls)
    }

    // --- Sign-in ------------------------------------------------------------------------------------------------

    @Test
    fun signIn_googleServicesUnavailable_showsTheKindMessage_rightUnderTheGoogleButton() {
        showSignIn(ScriptedGoogle(failed(AuthFailure.GOOGLE_SERVICES_UNAVAILABLE)))
        assertNoErrorOnScreen()
        tapGoogle()
        val message = composeRule.onNodeWithText(text(R.string.auth_error_google_services))
        message.assertExists()
        val google = composeRule.onNodeWithText(text(R.string.auth_google_action)).getUnclippedBoundsInRoot()
        val shown = message.getUnclippedBoundsInRoot()
        val or = composeRule.onNodeWithText(text(R.string.auth_or_divider)).getUnclippedBoundsInRoot()
        assertTrue("under the Google button", google.bottom <= shown.top)
        assertTrue("and above the e-mail form", shown.bottom <= or.top)
    }

    @Test
    fun signIn_closingTheDialog_showsNothing() {
        showSignIn(ScriptedGoogle(GoogleTokenResult.Cancelled))
        tapGoogle()
        assertNoErrorOnScreen()
        composeRule.onNodeWithText(text(R.string.auth_google_action)).assertIsEnabled()
    }

    @Test
    fun signIn_theGoogleButtonTriesAgain_afterAnUnknownFailure() {
        val source = ScriptedGoogle(failed(AuthFailure.UNKNOWN), GoogleTokenResult.Cancelled)
        showSignIn(source)
        tapGoogle()
        composeRule.onNodeWithText(text(R.string.auth_error_unknown)).assertExists()
        tapGoogle()
        assertEquals(2, source.calls)
        assertNoErrorOnScreen()
    }

    @Test
    fun signIn_theMessageIsAnnouncedByTheScreenReader() {
        showSignIn(ScriptedGoogle(failed(AuthFailure.GOOGLE_NO_ACCOUNT)))
        tapGoogle()
        val config =
            composeRule
                .onNodeWithText(text(R.string.auth_error_google_no_account))
                .fetchSemanticsNode()
                .config
        assertEquals(LiveRegionMode.Polite, config[SemanticsProperties.LiveRegion])
    }

    // --- The words -----------------------------------------------------------------------------------------------

    private val newTexts =
        listOf(
            R.string.auth_error_google_no_account,
            R.string.auth_error_google_services,
            R.string.auth_error_google_interrupted,
        )

    @Test
    fun englishTexts_areCalm_complete_andWithoutTechnicalCode() {
        for (id in newTexts) assertCalm(text(id))
        assertEquals(
            "Signing in with Google did not go through. Please try again in a moment.",
            text(R.string.auth_error_google_interrupted),
        )
    }

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
    fun frenchTexts_areCalm_inTutoiement_andWithoutTechnicalCode() {
        for (id in newTexts) assertCalm(text(id))
        assertEquals(
            "La connexion avec Google n'a pas abouti. Réessaie dans un instant.",
            text(R.string.auth_error_google_interrupted),
        )
        assertEquals(
            "Aucun compte Google n'est disponible sur ce téléphone pour le moment. " +
                "Tu peux en ajouter un dans les réglages du téléphone, ou continuer avec ton adresse e-mail.",
            text(R.string.auth_error_google_no_account),
        )
        assertEquals(
            "Les services Google ne répondent pas sur ce téléphone pour le moment. " +
                "Vérifie qu'ils sont à jour, ou continue avec ton adresse e-mail.",
            text(R.string.auth_error_google_services),
        )
        // Tutoiement: never the formal "vous" / "votre".
        for (id in newTexts) {
            val words = text(id).lowercase().split(Regex("[^a-zéèêàùç']+"))
            assertFalse(text(id), words.contains("vous") || words.contains("votre") || words.contains("vos"))
        }
    }

    private fun assertCalm(message: String) {
        assertTrue(message, message.endsWith(".") && message.length > 30)
        assertFalse("no exclamation mark: $message", message.contains("!"))
        assertFalse(
            "no code or class name: $message",
            Regex("\\d|exception|error|erreur|\\[|\\]", RegexOption.IGNORE_CASE).containsMatchIn(message),
        )
        val blame =
            listOf("fault", "faute", "should have", "aurais dû", "invalid", "invalide", "failed", "échec", "impossible")
        for (word in blame) assertFalse("$word in: $message", message.lowercase().contains(word))
    }
}
