package com.tomessa.miff.ui.home

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * AND-003: the welcome screen (the only screen of the release build) must stay reachable when the content is taller
 * than the screen: largest system font + landscape. Runs on the JVM (Robolectric), no emulator needed.
 *
 * Robolectric simulates Android 15 (API 35): API 36 needs Java 21, and the project builds with Java 17. A plain
 * Application is used so that the test never initializes Firebase (MiffApplication).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class HomeScreenScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun text(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    /** Short landscape screen (phone on its side) with the largest system font (2x). */
    @Test
    @Config(qualifiers = "en-w640dp-h240dp-land-mdpi")
    fun largestFontInLandscape_everythingCanBeScrolledTo() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                HomeScreen()
            }
        }

        // The screen exposes a scroll action, and its last element (tagline) can be brought into view.
        val scrollable = composeRule.onNode(hasScrollAction())
        scrollable.assertExists()
        scrollable.performScrollToNode(hasText(text(R.string.home_tagline)))
        composeRule.onNodeWithText(text(R.string.home_tagline)).assertIsDisplayed()

        // And back to the top: the logo (wordmark "Miff", heading of the screen) is reachable too.
        scrollable.performScrollToNode(hasContentDescription(text(R.string.home_logo_a11y)))
        composeRule.onNodeWithContentDescription(text(R.string.home_logo_a11y)).assertIsDisplayed()
    }

    /** Normal portrait phone: logo and tagline visible without scrolling. */
    @Test
    @Config(qualifiers = "en-w360dp-h800dp-port-xxhdpi")
    fun normalPortrait_logoAndTaglineVisibleWithoutScrolling() {
        composeRule.setContent { HomeScreen() }

        composeRule.onNodeWithContentDescription(text(R.string.home_logo_a11y)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.home_tagline)).assertIsDisplayed()
    }
}
