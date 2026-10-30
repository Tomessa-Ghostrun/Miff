package com.tomessa.miff.ui.profile

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.domain.auth.ConsentVersions
import com.tomessa.miff.domain.auth.PendingConsent
import com.tomessa.miff.testsupport.FakeAvatarCodec
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.FakeUserProfileRepository
import com.tomessa.miff.testsupport.MemoryProfileDraftStore
import com.tomessa.miff.testsupport.SessionHarness
import com.tomessa.miff.ui.theme.MiffTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The whole flow with a real view model (review I4): while the crop screen is open, what is behind it does not exist for
 * the screen reader, and it comes back when the crop is closed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-port-xxhdpi")
class ProfileFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() = scope.cancel()

    private fun viewModel(): ProfileViewModel {
        val h = SessionHarness(scope)
        h.gateway.setSignedIn("uid-1")
        h.pending.save(PendingConsent("uid-1", ConsentVersions.TERMS, analyticsAccepted = true))
        h.accounts.setLoaded(profileExists = false)
        h.manager.start()
        return ProfileViewModel(
            mode = OnboardingMode.FULL,
            session = h.manager,
            pending = h.pending,
            drafts = MemoryProfileDraftStore(),
            profiles = FakeUserProfileRepository(),
            avatars = FakeAvatarCodec(),
            network = FakeNetwork(),
            deviceRegion = { "FR" },
            worker = Dispatchers.Unconfined,
        )
    }

    @Test
    fun whileTheCropIsOpen_theFieldsBehindItAreHiddenFromTheScreenReader() {
        val vm = viewModel()
        composeRule.setContent { MiffTheme { ProfileFlow(vm) } }
        val firstName = context.getString(R.string.profile_first_name_label)
        composeRule
            .onNode(
                hasSetTextAction() and
                    androidx.compose.ui.test
                        .hasText(firstName),
            ).assertExists()

        composeRule.runOnUiThread { vm.onPhotoChosen("content://picked/1") }
        composeRule.waitForIdle()
        assertTrue("the crop is open", vm.uiState.value.crop != null)
        composeRule.onNodeWithText(context.getString(R.string.profile_crop_title)).assertExists()
        composeRule.onAllNodesWithText(firstName).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.profile_photo_add)).assertCountEquals(0)

        composeRule.runOnUiThread { vm.onCropCancelled() }
        composeRule.waitForIdle()
        composeRule
            .onNode(
                hasSetTextAction() and
                    androidx.compose.ui.test
                        .hasText(firstName),
            ).assertExists()
        composeRule.onAllNodesWithText(context.getString(R.string.profile_crop_title)).assertCountEquals(0)
    }
}
