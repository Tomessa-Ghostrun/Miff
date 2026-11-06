package com.tomessa.miff.ui.feed

import android.app.Application
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
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
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset

/**
 * The header of the Feed that hides while scrolling down and comes back as soon as one scrolls up (decision of the PM, 2026-11-06), on the JVM: hidden
 * on the way down, back on the way up, visible when the list is short, while loading or on an error, with an accessibility service on, restored
 * visible after a rotation, and never covering the content.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h640dp-port-xxhdpi")
class FeedHeaderScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val me = "me"
    private val miffId = "AbCdEfGhIjKlMnOpQrSt"
    private val now = Instant.parse("2026-11-05T17:00:00Z").toEpochMilli()

    private fun member(
        uid: String,
        first: String,
    ) = MiffMember(uid, first, "X", null, if (uid == me) MiffRole.OWNER else MiffRole.MEMBER, true, "+33612345678")

    private val directory =
        DirectoryState.Ready(
            listOf(MiffSummary(miffId, "Famille Mfouka")),
            miffId,
            premium = false,
            fromCache = false,
        )

    private fun content(count: Int): FeedContent {
        val members = listOf(member(me, "Camille")) + (1 until count).map { member("u$it", "Proche$it") }
        val statuses =
            members.associate {
                it.uid to
                    MemberStatus(PresenceValue.IN_TRUSTED, null, now - 720_000, now - 720_000, false, 85, null, now)
            }
        return FeedBuilder.build(
            FeedInput(
                miffId,
                "Famille Mfouka",
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
        )
    }

    @Composable
    private fun Screen(feed: FeedUiState) {
        MiffTheme {
            Box(Modifier.fillMaxSize()) {
                FeedScreen(
                    state = directory,
                    scrollToTopSignal = 0,
                    onRetry = {},
                    onSelect = {},
                    onCreate = {},
                    onJoin = {},
                    feed = feed,
                    me = FeedMe("CM"),
                )
            }
        }
    }

    private fun logoTop(): Float =
        composeRule
            .onNodeWithContentDescription(
                composeRule.activity().getString(R.string.home_logo_a11y),
            ).fetchSemanticsNode()
            .boundsInRoot.top

    private fun logoBottom(): Float =
        composeRule
            .onNodeWithContentDescription(
                composeRule.activity().getString(R.string.home_logo_a11y),
            ).fetchSemanticsNode()
            .boundsInRoot.bottom

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.activity(): Context =
        ApplicationProvider.getApplicationContext()

    private fun scrollDown() =
        composeRule.onNode(hasScrollAction()).performTouchInput {
            swipeUp(
                startY = bottom * 0.9f,
                endY =
                    top + 10f,
                durationMillis = 300,
            )
        }

    private fun scrollUp(fraction: Float = 0.5f) =
        composeRule.onNode(hasScrollAction()).performTouchInput {
            swipeDown(
                startY = top + 10f,
                endY =
                    top + (bottom - top) * fraction,
                durationMillis = 300,
            )
        }

    @Test
    fun itHidesWhenScrollingDown() {
        composeRule.setContent { Screen(FeedUiState.Ready(content(12))) }
        assertTrue("at rest the logo is on screen", logoTop() >= 0f)
        scrollDown()
        composeRule.waitForIdle()
        assertTrue("logo bottom ${logoBottom()}: the header is out of the screen", logoBottom() <= 1f)
    }

    @Test
    fun itComesBackAsSoonAsScrollingUp_withoutBeingAtTheTopOfTheList() {
        composeRule.setContent { Screen(FeedUiState.Ready(content(15))) }
        scrollDown()
        scrollDown()
        composeRule.waitForIdle()
        assertTrue(logoBottom() <= 1f)
        // A short movement upward, far from the top of the list: the header is back (fully, the movement being longer than its height).
        scrollUp(fraction = 0.6f)
        composeRule.waitForIdle()
        assertTrue("logo top ${logoTop()}: back on screen", logoTop() >= 0f)
        // And the list is still far from its top: the first card is not on screen.
        composeRule.onNodeWithText("Camille").let { node ->
            assertTrue(runCatching { node.assertDoesNotExist() }.isSuccess)
        }
    }

    @Test
    fun itStaysWhenTheListIsTooShortToScroll() {
        composeRule.setContent { Screen(FeedUiState.Ready(content(1))) }
        val before = logoTop()
        scrollDown()
        composeRule.waitForIdle()
        assertEquals(before, logoTop(), 0.5f)
    }

    @Test
    fun itStaysWithAnAccessibilityServiceOn() {
        val manager =
            ApplicationProvider.getApplicationContext<Application>().getSystemService(
                Context.ACCESSIBILITY_SERVICE,
            ) as AccessibilityManager
        shadowOf(manager).setEnabled(true)
        shadowOf(manager).setTouchExplorationEnabled(true)
        composeRule.setContent { Screen(FeedUiState.Ready(content(15))) }
        val before = logoTop()
        scrollDown()
        composeRule.waitForIdle()
        assertEquals("the header does not hide with TalkBack on", before, logoTop(), 0.5f)
    }

    @Test
    fun itStaysWhileLoading_andOnAnError() {
        composeRule.setContent { Screen(FeedUiState.Loading) }
        assertTrue(logoTop() >= 0f)
    }

    @Test
    fun itStaysOnAnError() {
        composeRule.setContent { Screen(FeedUiState.Unavailable) }
        assertTrue(logoTop() >= 0f)
    }

    @Test
    fun itIsBackVisibleAfterARotation_orAChangeOfTab() {
        // A rotation (the activity is recreated) or a change of tab (the screen leaves the composition) builds the screen again: the position of the
        // header is plain state, NOT saved, so it starts shown (while the position of the list is saved by the tab holder).
        var generation by mutableIntStateOf(0)
        composeRule.setContent { key(generation) { Screen(FeedUiState.Ready(content(15))) } }
        scrollDown()
        composeRule.waitForIdle()
        assertTrue(logoBottom() <= 1f)
        composeRule.runOnIdle { generation++ }
        composeRule.waitForIdle()
        assertTrue("logo top ${logoTop()}: visible after the rotation", logoTop() >= 0f)
    }

    @Test
    fun theContentStartsUnderTheHeader_nothingIsCovered() {
        composeRule.setContent { Screen(FeedUiState.Ready(content(8))) }
        val header =
            composeRule
                .onNodeWithText("Famille Mfouka")
                .fetchSemanticsNode()
                .boundsInRoot.bottom
        val bubble =
            composeRule
                .onNodeWithText("Tout va bien")
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertTrue("the bubble ($bubble) starts under the header ($header)", bubble >= header)
    }

    @Test
    fun theLogoAndTheAvatar_keepTheirTouchZone_whileTheHeaderMoves() {
        composeRule.setContent { Screen(FeedUiState.Ready(content(15))) }
        val logo =
            composeRule
                .onNodeWithContentDescription(
                    composeRule.activity().getString(R.string.home_logo_a11y),
                ).fetchSemanticsNode()
                .boundsInRoot
        assertTrue(logo.height >= 48f * 3f - 1f) // 48 dp at xxhdpi (3 px per dp)
        val avatar =
            composeRule
                .onNodeWithContentDescription(
                    composeRule.activity().getString(R.string.feed_header_avatar_plain_a11y),
                    substring = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        assertTrue(avatar.height >= 48f * 3f - 1f)
    }

    @Test
    fun theSelectorTellsWhenItOpens_soTheHeaderCanStay() {
        val events = mutableListOf<Boolean>()
        val premium =
            DirectoryState.Ready(
                listOf(MiffSummary(miffId, "Famille Mfouka")),
                miffId,
                premium = true,
                fromCache = false,
            )
        composeRule.setContent {
            MiffTheme { MiffPill(premium, onSelect = {}, onCreate = {}, onJoin = {}, onOpenChanged = { events += it }) }
        }
        composeRule.onNodeWithText("Famille Mfouka").performClick()
        composeRule.waitForIdle()
        assertTrue("events $events", events.last())
    }
}
