package com.tomessa.miff.ui.review

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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The review window on the JVM (Robolectric, no phone), in French (docs/tickets/pop-ins-avis.md, criterion AV1): the question of the mockup with its five named stars and
 * its two buttons, the cross, the short "Merci !", and the calm thank-you with "Nous écrire" and "Plus tard". Touch targets of 48 dp, the largest font.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
class ReviewPromptWindowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private fun star(number: Int): String = context().getString(R.string.review_star_a11y, number)

    private class Taps {
        val stars = mutableListOf<Int>()
        var love = 0
        var better = 0
        var close = 0
        var write = 0
        var later = 0
    }

    private fun show(
        stage: ReviewStage,
        taps: Taps = Taps(),
        fontScale: Float = 1f,
    ): Taps {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MiffTheme {
                    Box(Modifier.fillMaxSize()) {
                        ReviewPromptWindow(
                            stage = stage,
                            onStar = { taps.stars += it },
                            onLove = { taps.love++ },
                            onBetter = { taps.better++ },
                            onClose = { taps.close++ },
                            onWrite = { taps.write++ },
                            onLater = { taps.later++ },
                        )
                    }
                }
            }
        }
        return taps
    }

    @Test
    fun hidden_drawsNothing() {
        show(ReviewStage.Hidden)
        composeRule.onAllNodesWithText(text(R.string.review_title)).assertCountEquals(0)
    }

    @Test
    fun question_showsTheMockup_atTheInformalYou_withFiveNamedStarsAndTwoButtons() {
        show(ReviewStage.Question)
        composeRule.onNodeWithText("Que penses-tu de Miff ?").assertIsDisplayed()
        for (number in 1..5) composeRule.onNodeWithContentDescription(star(number)).assertIsDisplayed()
        composeRule.onNodeWithText("J'adore ! (5 étoiles)").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Peux faire mieux...").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription(text(R.string.review_close_a11y)).assertIsDisplayed()
        composeRule.onNodeWithTag(REVIEW_PROMPT_PROBE).assertIsDisplayed()
    }

    @Test
    fun theStarsAreNamedForTheScreenReader() {
        show(ReviewStage.Question)
        assertEquals("Étoile 3 sur 5", star(3))
        composeRule.onAllNodesWithContentDescription("Étoile 1 sur 5").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Étoile 5 sur 5").assertCountEquals(1)
    }

    @Test
    fun touchTargets_areAtLeast48dp() {
        show(ReviewStage.Question)
        for (number in 1..5) {
            composeRule
                .onNodeWithContentDescription(
                    star(number),
                ).assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(40.dp)
        }
        composeRule
            .onNodeWithContentDescription(
                text(R.string.review_close_a11y),
            ).assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithText(text(R.string.review_love_action)).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText(text(R.string.review_better_action)).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun touchingTheFifthStar_andTheButtons_callTheRightHandlers() {
        val taps = show(ReviewStage.Question)
        composeRule.onNodeWithContentDescription(star(5)).performClick()
        composeRule.onNodeWithContentDescription(star(2)).performClick()
        composeRule.onNodeWithText(text(R.string.review_love_action)).performClick()
        composeRule.onNodeWithText(text(R.string.review_better_action)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.review_close_a11y)).performClick()
        assertEquals(listOf(5, 2), taps.stars)
        assertEquals(1, taps.love)
        assertEquals(1, taps.better)
        assertEquals(1, taps.close)
    }

    @Test
    fun question_withTheLargestFont_stillShowsTheTwoButtons() {
        show(ReviewStage.Question, fontScale = 2f)
        composeRule.onNodeWithText(text(R.string.review_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.review_love_action)).assertExists()
        composeRule.onNodeWithText(text(R.string.review_better_action)).assertExists()
    }

    @Test
    fun thanksForLove_isShortAndHasNothingToPress() {
        show(ReviewStage.ThanksForLove)
        composeRule.onNodeWithText("Merci !").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.review_thanks_body)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.review_love_action)).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(text(R.string.review_close_a11y)).assertCountEquals(0)
    }

    @Test
    fun thanksForFeedback_isCalm_withWriteToUsAndLater_andNoStoreWording() {
        val taps = show(ReviewStage.ThanksForFeedback)
        composeRule.onNodeWithText("Merci de nous le dire !").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.review_feedback_body)).assertIsDisplayed()
        composeRule
            .onNodeWithText("Nous écrire")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule
            .onNodeWithText("Plus tard")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, taps.write)
        assertEquals(1, taps.later)
        // The store is not mentioned and its window is not offered here.
        composeRule.onAllNodesWithText(text(R.string.review_love_action)).assertCountEquals(0)
    }
}
