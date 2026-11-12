package com.tomessa.miff.ui.main

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.domain.navigation.MainTab
import com.tomessa.miff.domain.navigation.TabBarConfig
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The dot of the Profile tab (ticket LIEU-REQ-C, question Q-R1): a request waits for the creator; the dot never shows alone, the spoken name of the tab says why. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w390dp-h844dp-port-xxhdpi")
class ProfileTabDotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun text(id: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(id)

    private fun bar(
        dot: Boolean,
        onSelect: (MainTab) -> Unit = {},
    ) = composeRule.setContent {
        MiffTheme {
            MainTabBar(
                config = TabBarConfig(showUpdateButton = true),
                selected = MainTab.FEED,
                onSelect = onSelect,
                profileDot = dot,
            )
        }
    }

    @Test
    fun withARequestWaiting_theProfileTabIsNamedWithTheReason() {
        bar(dot = true)
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_profile_requests_a11y)).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(text(R.string.tabbar_profile_a11y)).assertCountEquals(0)
    }

    @Test
    fun withNothingWaiting_theProfileTabKeepsItsOrdinaryName() {
        bar(dot = false)
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_profile_a11y)).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(text(R.string.tabbar_profile_requests_a11y)).assertCountEquals(0)
    }

    @Test
    fun theDot_isOnTheProfileTabOnly_andTheTabStillOpens() {
        var chosen: MainTab? = null
        bar(dot = true, onSelect = { chosen = it })
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_feed_a11y)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_profile_requests_a11y)).performClick()
        assertEquals(MainTab.PROFILE, chosen)
    }
}
