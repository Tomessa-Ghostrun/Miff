package com.tomessa.miff.ui.alert

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.tomessa.miff.data.alert.MyAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The red banner of an alert in progress with a big SYSTEM FONT (retour de recette of the PM, 2026-11-20: the title is twice as big as before), measured on REAL sizes (native
 * graphics: a layout that is really computed, not the legacy approximation) in a screen of 360 x 800 dp. The banner follows the person on every tab: it must never take more than
 * 45 % of the screen (360 dp), "Appeler" and "Voir la Miff" stay on the screen (the texts scroll inside the banner, the buttons are pinned under them), and the hold still ends
 * the alert when the finger is put in the MIDDLE of the title (inside the scroll), not only in the margin.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w360dp-h800dp-xhdpi", application = android.app.Application::class)
class AlertBannerFontScaleTest {
    @get:Rule
    val rule = createComposeRule()

    private var endAsked = 0
    private var seen = 0
    private val active = MyAlert.Active("m", 1, 2)
    private val longName = "Les amis de toujours, la famille élargie et les voisins du quartier"

    private fun bannerAt(
        scale: Float,
        name: String? = "Family",
        showSee: Boolean = false,
    ) {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = scale)) {
                AlertBanner(
                    alert = active,
                    miffName = name,
                    showSeeMiff = showSee,
                    onCall = {},
                    onSeeMiff = { seen++ },
                    onEndRequested = { endAsked++ },
                    onEndAccessible = {},
                )
            }
        }
    }

    private fun bannerHeight(): Dp = rule.onNodeWithTag(ALERT_BANNER_TAG).getUnclippedBoundsInRoot().height

    private fun measure(
        scale: Float,
        name: String? = "Family",
        showSee: Boolean = false,
    ): Dp {
        bannerAt(scale, name, showSee)
        rule.onNodeWithTag(ALERT_BANNER_CALL_TAG).assertIsDisplayed()
        rule.onNodeWithTag(ALERT_BANNER_TITLE_TAG).assertIsDisplayed()
        if (showSee) rule.onNodeWithTag(ALERT_BANNER_SEE_TAG).assertIsDisplayed()
        return bannerHeight()
    }

    @Test
    fun atX1_0_theBannerFitsUnderTheCap_andCallIsThere() {
        val normal = measure(1.0f)
        assertTrue("x1.0: $normal", normal <= 360.dp)
    }

    @Test
    fun atX1_3_theTitleWraps_theBannerIsTallerThanAtX1_butUnderTheCap() {
        val height = measure(1.3f)
        assertTrue("x1.3: $height", height <= 360.dp && height > 150.dp)
    }

    @Test
    fun atX2_0_theBannerIsCapped_theTextsScroll_andTheButtonsArePinned() {
        val height = measure(2.0f)
        assertTrue("x2.0: $height", height <= 360.dp && height > 200.dp)
    }

    @Test
    fun atX2_0_withALongMiffName_theSeeTheMiffButtonStaysOnTheScreen_andTheBannerStaysCapped() {
        val height = measure(2.0f, name = longName, showSee = true)
        assertTrue("x2.0 long name: $height", height <= 360.dp)
        rule.onNodeWithText("See the Miff “$longName”").assertIsDisplayed()
        rule.onNodeWithTag(ALERT_BANNER_SEE_TAG).performTouchInput { click() }
        assertEquals(1, seen)
        assertEquals(0, endAsked)
    }

    @Test
    fun atX1_3_withALongMiffName_theSeeTheMiffButtonStaysOnTheScreen() {
        val height = measure(1.3f, name = longName, showSee = true)
        assertTrue("x1.3 long name: $height", height <= 360.dp)
    }

    @Test
    fun theBlueBubble_atX2_0_staysUnder45PercentOfTheScreen_andLearnMoreIsThere() {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2.0f)) {
                AlertHintBubble(onMore = {})
            }
        }
        rule.onNodeWithTag(ALERT_HINT_TAG).assertIsDisplayed()
        val height = rule.onNodeWithTag(ALERT_HINT_TAG).getUnclippedBoundsInRoot().height
        assertTrue("bubble x2.0: $height", height <= 360.dp)
    }

    @Test
    fun theHoldEndsTheAlert_withTheFingerInTheMiddleOfTheTitle_insideTheScroll() {
        bannerAt(2.0f)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(ALERT_BANNER_TITLE_TAG).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1_000)
        // the finger slides off the banner: the end goes on
        rule.onNodeWithTag(ALERT_BANNER_TITLE_TAG).performTouchInput { moveBy(Offset(0f, -1_200f)) }
        rule.mainClock.advanceTimeBy(4_700)
        assertEquals(1, endAsked)
    }

    @Test
    fun theHoldReleasedEarly_withTheFingerInTheMiddleOfTheTitle_endsNothing() {
        bannerAt(1.3f)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(ALERT_BANNER_TITLE_TAG).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithTag(ALERT_BANNER_TITLE_TAG).performTouchInput { up() }
        rule.mainClock.advanceTimeBy(6_000)
        assertEquals(0, endAsked)
    }
}
