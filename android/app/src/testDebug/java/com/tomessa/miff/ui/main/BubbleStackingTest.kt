package com.tomessa.miff.ui.main

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.domain.feed.FeedBuilder
import com.tomessa.miff.domain.feed.FeedContent
import com.tomessa.miff.domain.feed.FeedInput
import com.tomessa.miff.domain.feed.MemberStatus
import com.tomessa.miff.domain.feed.StatusesView
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.miff.MiffSummary
import com.tomessa.miff.domain.navigation.TabBarConfig
import com.tomessa.miff.domain.profile.MyProfile
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.update.PromptLevel
import com.tomessa.miff.ui.feed.FeedHost
import com.tomessa.miff.ui.feed.FeedUiState
import com.tomessa.miff.ui.profile.ProfileTabStatus
import com.tomessa.miff.ui.profile.ProfileTabUiState
import com.tomessa.miff.ui.theme.MiffTheme
import com.tomessa.miff.ui.update.BUBBLE_TAG
import com.tomessa.miff.ui.update.CONFIRMATION_TAG
import com.tomessa.miff.ui.update.UpdateBottomLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset

/**
 * What stacks above the glass bar when the bubble of encouragement is shown (UPDATE-2, review of the merge with the glass bar of TABBAR-GLASS):
 * (a) the bottom of the bubble is at the top of the bar (`LocalTabBarInset`), neither under it nor floating above it; (b) the last card of the Feed and the
 * last element of the Profile can still be scrolled ABOVE the bubble (`LocalBottomOverlayPadding`); (c) the calm message after a publication is above the
 * bubble, never on it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-port-xxhdpi")
class BubbleStackingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private val me = "me"
    private val miffId = "AbCdEfGhIjKlMnOpQrSt"

    private fun content(prompt: PromptLevel): FeedContent {
        val now = Instant.parse("2026-11-05T17:00:00Z").toEpochMilli()
        val members =
            listOf(MiffMember(me, "Camille", "X", null, MiffRole.OWNER, true, "+33612345678")) +
                (1..10).map { MiffMember("u$it", "Proche$it", "X", null, MiffRole.MEMBER, true, "+33612345678") }
        val statuses =
            members.associate {
                it.uid to
                    MemberStatus(PresenceValue.IN_TRUSTED, null, now - 720_000, now - 720_000, false, 85, null, now)
            }
        return FeedBuilder
            .build(
                FeedInput(
                    miffId,
                    "Famille",
                    "XTZRSP367988",
                    members,
                    emptyList(),
                    me,
                    StatusesView.Loaded(statuses),
                    false,
                    false,
                ),
                now,
                ZoneOffset.UTC,
            ).copy(myPrompt = prompt)
    }

    private fun showTabs(
        prompt: PromptLevel,
        placesContent: @Composable () -> Unit = {},
    ) {
        composeRule.setContent {
            MiffTheme {
                CompositionLocalProvider(LocalDensity provides Density(3f, 1f)) {
                    Box(Modifier.fillMaxSize()) {
                        MainTabs(
                            tabBar = TabBarConfig.forBuild(isInternalBuild = true),
                            profileState =
                                ProfileTabUiState(
                                    ProfileTabStatus.READY,
                                    MyProfile("Emmanuelle", "Martin", "+33612345678", null),
                                ),
                            onOpenMyInfo = {},
                            onOpenSettings = {},
                            onRetryProfile = {},
                            miffs =
                                DirectoryState.Ready(
                                    listOf(MiffSummary(miffId, "Famille")),
                                    miffId,
                                    premium = false,
                                    fromCache = false,
                                ),
                            onRetryMiffs = {},
                            onSelectMiff = {},
                            onCreateMiff = {},
                            onJoinMiff = {},
                            onOpenMiff = {},
                            feed = FeedHost(state = FeedUiState.Ready(content(prompt))),
                            placesContent = placesContent,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Top of the card of the bar: the entries are 48 dp tall in a bar of 64 dp. */
    private fun barTop(): Dp =
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_feed_a11y)).getUnclippedBoundsInRoot().top - 8.dp

    private fun bubble() = composeRule.onNodeWithTag(BUBBLE_TAG).getUnclippedBoundsInRoot()

    private fun scrollToTheEnd() {
        composeRule.waitForIdle()
        composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))[0]
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1_000_000f) }
        composeRule.waitForIdle()
    }

    @Test
    fun theBubble_endsAtTheTopOfTheBar_withoutOverlappingIt_onTheFeed() {
        showTabs(PromptLevel.ORANGE)
        val bubble = bubble()
        assertEquals("the bottom of the bubble is at the top of the bar", barTop().value, bubble.bottom.value, 1f)
        assertTrue("the bubble has a height", bubble.bottom > bubble.top)
    }

    @Test
    fun theBubble_isAtTheSamePlaceOnTheProfile() {
        showTabs(PromptLevel.RED)
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_profile_a11y)).performClick()
        composeRule.waitForIdle()
        assertEquals(barTop().value, bubble().bottom.value, 1f)
    }

    @Test
    fun noBubble_whenThereIsNoPrompt() {
        showTabs(PromptLevel.NONE)
        composeRule.onNodeWithTag(BUBBLE_TAG).assertDoesNotExist()
    }

    @Test
    fun theLastCardOfTheFeed_canBeScrolledAboveTheBubble() {
        showTabs(PromptLevel.ORANGE)
        scrollToTheEnd()
        val lastBottom = composeRule.onNodeWithText(text(R.string.feed_add_loved_one)).getUnclippedBoundsInRoot().bottom
        val bubbleTop = bubble().top
        assertTrue(
            "the last element ends at $lastBottom, under the bubble that starts at $bubbleTop",
            lastBottom <= bubbleTop,
        )
    }

    @Test
    fun theLastElementOfTheProfile_canBeScrolledAboveTheBubble() {
        val lastRow = "LAST ROW OF THE PROFILE"
        showTabs(PromptLevel.ORANGE) {
            Column {
                repeat(40) { Text("Row $it", Modifier.height(48.dp)) }
                Text(lastRow, Modifier.height(48.dp))
            }
        }
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_profile_a11y)).performClick()
        composeRule.waitForIdle()
        scrollToTheEnd()
        val lastBottom = composeRule.onNodeWithText(lastRow).getUnclippedBoundsInRoot().bottom
        val bubbleTop = bubble().top
        assertTrue(
            "the last element ends at $lastBottom, under the bubble that starts at $bubbleTop",
            lastBottom <= bubbleTop,
        )
    }

    // --- The message after a publication and the bubble: one column, no overlap -----------------------------------------------------

    private fun showLayer(
        prompt: PromptLevel,
        confirmation: String?,
    ) {
        composeRule.setContent {
            MiffTheme {
                CompositionLocalProvider(LocalDensity provides Density(3f, 1f)) {
                    Box(Modifier.fillMaxSize()) {
                        UpdateBottomLayer(
                            barInset = 86.dp,
                            prompt = prompt,
                            confirmation = confirmation,
                            maxBodyHeight = 320.dp,
                            onBubbleHeight = {},
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun rootBottom(): Float =
        composeRule
            .onRoot()
            .getUnclippedBoundsInRoot()
            .bottom.value

    @Test
    fun theMessageAfterAPublication_isAboveTheBubble_neverOnIt() {
        showLayer(PromptLevel.ORANGE, "Published")
        val toast = composeRule.onNodeWithTag(CONFIRMATION_TAG).getUnclippedBoundsInRoot()
        val bubble = bubble()
        assertTrue(
            "the message (bottom ${toast.bottom}) must end above the bubble (top ${bubble.top})",
            toast.bottom <= bubble.top,
        )
        assertEquals("the bubble keeps its place on the bar", rootBottom() - 86f, bubble.bottom.value, 1f)
    }

    @Test
    fun theMessageAlone_sitsJustAboveTheBar() {
        showLayer(PromptLevel.NONE, "Published")
        val toast = composeRule.onNodeWithTag(CONFIRMATION_TAG).getUnclippedBoundsInRoot()
        assertEquals(rootBottom() - 86f - 16f, toast.bottom.value, 1f)
    }

    @Test
    fun theBubbleAlone_isOnTheBar_whateverTheMessage() {
        showLayer(PromptLevel.RED, null)
        assertEquals(rootBottom() - 86f, bubble().bottom.value, 1f)
    }
}
