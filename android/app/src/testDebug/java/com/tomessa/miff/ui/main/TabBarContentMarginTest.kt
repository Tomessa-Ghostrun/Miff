package com.tomessa.miff.ui.main

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.domain.feed.FeedBuilder
import com.tomessa.miff.domain.feed.FeedInput
import com.tomessa.miff.domain.feed.MemberStatus
import com.tomessa.miff.domain.feed.StatusesView
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.miff.MiffSummary
import com.tomessa.miff.domain.navigation.MainTab
import com.tomessa.miff.domain.navigation.TabBarConfig
import com.tomessa.miff.domain.profile.MyProfile
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.ui.feed.FeedHost
import com.tomessa.miff.ui.feed.FeedUiState
import com.tomessa.miff.ui.profile.ProfileTabStatus
import com.tomessa.miff.ui.profile.ProfileTabUiState
import com.tomessa.miff.ui.theme.GlassConditions
import com.tomessa.miff.ui.theme.GlassVariant
import com.tomessa.miff.ui.theme.MiffTheme
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
 * The glass bar floats over the tabs, which are drawn full screen: a tab that scrolls must keep a bottom margin of the height of the
 * bar (`LocalTabBarInset`), or its last element would stay hidden behind the bar. Here the REAL Profile tab, inside the real
 * `MainTabs`, is scrolled to its very end, and its last element must END ABOVE the top of the bar (a text check of the source, as in
 * `TabBarInsetRuleTest`, only proves that a line exists; this proves what the person sees).
 *
 * The same check is made for the real Feed (PR FEED-1 to FEED-3: `contentPadding = PaddingValues(bottom = Spacing.m + LocalTabBarInset.current, ...)`,
 * hosted by `GlassTabHost`), whose header hides while scrolling down: no margin is broken by the two together.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-port-xxhdpi")
class TabBarContentMarginTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private val phoneConditions =
        GlassConditions(
            sdkInt = 33,
            powerSaveMode = false,
            animationsRemoved = false,
            contrastIncreased = false,
            lowRamDevice = false,
        )

    private fun barTop(): Dp =
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_feed_a11y)).getUnclippedBoundsInRoot().top - 8.dp

    /**
     * Scrolls the tab to its very END (performScrollTo would only bring the element to the edge of the scrolling area, which is the
     * edge of the SCREEN, not the end of the content), then checks that [lastRow], the last element, ends above the top of the bar.
     */
    private fun assertLastRowAboveBar(lastRow: String) {
        scrollToTheEnd()
        val bottom = composeRule.onNodeWithText(lastRow).getUnclippedBoundsInRoot().bottom
        val top = barTop()
        assertTrue("the last element ends at $bottom, under the bar that starts at $top: it is hidden", bottom <= top)
    }

    private fun scrollToTheEnd() {
        composeRule.waitForIdle()
        composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))[0]
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1_000_000f) }
        composeRule.waitForIdle()
    }

    private fun longList(lastRow: String): @Composable () -> Unit =
        {
            Column {
                repeat(40) { Text("Row $it", Modifier.height(48.dp)) }
                Text(lastRow, Modifier.height(48.dp))
            }
        }

    @Test
    fun theProfileTab_scrolledToTheEnd_endsAboveTheBar() {
        val lastRow = "LAST ROW OF THE PROFILE"
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
                            miffs = DirectoryState.Loading,
                            onRetryMiffs = {},
                            onSelectMiff = {},
                            onCreateMiff = {},
                            onJoinMiff = {},
                            onOpenMiff = {},
                            placesContent = longList(lastRow),
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_profile_a11y)).performClick()
        composeRule.waitForIdle()
        assertLastRowAboveBar(lastRow)
    }

    @Test
    fun theFeedTab_scrolledToTheEnd_endsAboveTheBar_withItsHeaderMoving() {
        val me = "me"
        val miffId = "AbCdEfGhIjKlMnOpQrSt"
        val now = Instant.parse("2026-11-05T17:00:00Z").toEpochMilli()
        val members =
            listOf(MiffMember(me, "Camille", "X", null, MiffRole.OWNER, true, "+33612345678")) +
                (1..10).map { MiffMember("u$it", "Proche$it", "X", null, MiffRole.MEMBER, true, "+33612345678") }
        val statuses =
            members.associate {
                it.uid to
                    MemberStatus(PresenceValue.IN_TRUSTED, null, now - 720_000, now - 720_000, false, 85, null, now)
            }
        val content =
            FeedBuilder.build(
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
            )
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
                            feed = FeedHost(state = FeedUiState.Ready(content)),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        // The last element of the Feed is the button "Ajouter un proche": it must end above the top of the bar.
        assertLastRowAboveBar(text(R.string.feed_add_loved_one))
    }

    /** The control: the same long tab WITHOUT the margin does end under the bar, so the check above can fail. */
    @Test
    fun aTabWithoutTheMargin_endsUnderTheBar_theCheckCanFail() {
        val lastRow = "LAST ROW WITHOUT MARGIN"
        composeRule.setContent {
            MiffTheme {
                CompositionLocalProvider(LocalDensity provides Density(3f, 1f)) {
                    GlassTabHost(
                        variant = GlassVariant.LIGHT,
                        barVisible = true,
                        conditions = phoneConditions,
                        bar = { glass ->
                            MainTabBar(
                                config = TabBarConfig.forBuild(true),
                                selected = MainTab.FEED,
                                onSelect = {},
                                glass = glass,
                            )
                        },
                    ) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(0.dp)) {
                            longList(lastRow)()
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        scrollToTheEnd()
        val bottom = composeRule.onNodeWithText(lastRow).getUnclippedBoundsInRoot().bottom
        assertTrue(
            "without the margin the last element must reach the bar (bottom $bottom, bar ${barTop()})",
            bottom > barTop(),
        )
    }
}
