package com.tomessa.miff.ui.miff

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffObservation
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.miff.MiffSnapshot
import com.tomessa.miff.testsupport.FakeDeletionController
import com.tomessa.miff.testsupport.FakeMiffRepository
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.miffDetails
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The screen of a Miff, wired as the app wires it ([MiffScreenHost]): the loved ones that the observer sends must reach the
 * screen. This test FAILS if the line that attaches the listeners to the lifecycle is missing or commented out (the screen
 * would then read nothing: an empty, frozen list).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
class MiffScreenHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val repo = FakeMiffRepository()

    private fun viewModel() =
        MiffScreenViewModel(
            miffId = MIFF_ID,
            repository = repo,
            network = FakeNetwork(),
            currentUid = { "me" },
            onAccessLost = { _, _ -> true },
            onNameChanged = {},
            deletions = FakeDeletionController(),
            onLeft = {},
        )

    private fun members(vararg others: String) =
        listOf(MiffMember("me", "Léa", "Martin", null, MiffRole.OWNER, true)) +
            others.map { MiffMember("u-$it", it, "Pluchard", null, MiffRole.MEMBER, true) }

    private fun send(list: List<MiffMember>) {
        val snapshot =
            MiffSnapshot(miffDetails(owner = "me", name = "Famille Martin"), list, emptyList(), fromCache = false)
        assertTrue(repo.observer.flowOf(MIFF_ID).tryEmit(MiffObservation.Data(snapshot)))
    }

    @Test
    fun theLovedOnesSentByTheObserver_reachTheScreen_andFollowTheLiveUpdates() {
        val viewModel = viewModel()
        composeRule.setContent { MiffTheme { MiffScreenHost(viewModel = viewModel, onBack = {}) } }
        send(members("Emmanuelle"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Emmanuelle Pluchard").performScrollTo().assertIsDisplayed()
        // A person who comes in while the screen is open appears by itself.
        send(members("Emmanuelle", "Ryadh"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ryadh Pluchard").performScrollTo().assertIsDisplayed()
    }
}
