package com.tomessa.miff.ui.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.domain.about.AppVersion
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings > "À propos et aide" on the JVM (Robolectric, no phone): ticket reglages-a-propos.md, AP3, AP5, AP6, AP8, AP9, AP10, AP11, AP17. The outside world (browser,
 * e-mail application, clipboard) is a fake: what is checked is WHAT the screen asks for, not what a phone does with it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-port-xxhdpi")
class AboutScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private class FakeActions(
        override val feedbackFormUrl: String? = "https://forms.example/abc",
        var contactOpens: Boolean = true,
        var formOpens: Boolean = true,
        var copyWorks: Boolean = true,
    ) : AboutActions {
        override val supportEmail: String = "support@example.org"
        val log = mutableListOf<String>()
        val copied = mutableListOf<String>()

        override fun openContact(): Boolean {
            log += "contact"
            return contactOpens
        }

        override fun openFeedbackForm(): Boolean {
            log += "form"
            return formOpens
        }

        override fun openTerms() {
            log += "terms"
        }

        override fun openPrivacyPolicy() {
            log += "privacy"
        }

        override fun copy(text: String): Boolean {
            copied += text
            return copyWorks
        }
    }

    private class Taps {
        val log = mutableListOf<String>()
    }

    private fun show(
        actions: AboutActions,
        taps: Taps = Taps(),
        reminders: Boolean = true,
        density: Density? = null,
        direction: LayoutDirection? = null,
    ) {
        composeRule.setContent {
            MiffTheme {
                Provide(density, direction) {
                    Box(Modifier.fillMaxSize()) {
                        AboutScreen(
                            version = AppVersion("0.39.0", "39000"),
                            actions = actions,
                            onBack = { taps.log += "back" },
                            onOpenDetectionHelp = { taps.log += "detection" },
                            onOpenLicenses = { taps.log += "licenses" },
                            onOpenRemindersHelp = if (reminders) ({ taps.log += "reminders" }) else null,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Provide(
        density: Density?,
        direction: LayoutDirection?,
        content: @Composable () -> Unit,
    ) {
        CompositionLocalProvider(
            LocalDensity provides (density ?: LocalDensity.current),
            LocalLayoutDirection provides (direction ?: LocalLayoutDirection.current),
            content = content,
        )
    }

    // --- Version (AP3, Q7) ---------------------------------------------------------------------------------------------------------------

    @Test
    fun theVersion_isWrittenExactly_andATouchCopiesItAndNothingElse() {
        val actions = FakeActions()
        show(actions)
        composeRule
            .onNodeWithText(
                "Version 0.39.0 (build 39000)",
            ).assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onAllNodesWithText(text(R.string.about_copied)).assertCountEquals(0)
        composeRule.onNodeWithText("Version 0.39.0 (build 39000)").performClick()
        assertEquals(listOf("Miff 0.39.0 (39000)"), actions.copied)
        // The confirmation is in words (read out by the screen reader: it is a polite live region).
        composeRule.onNodeWithText(text(R.string.about_copied)).assertIsDisplayed()
        assertEquals(emptyList<String>(), actions.log)
    }

    @Test
    fun ifTheSystemRefusesTheCopy_theScreenDoesNotSayCopied() {
        val actions = FakeActions(copyWorks = false)
        show(actions)
        composeRule.onNodeWithText("Version 0.39.0 (build 39000)").performClick()
        composeRule.onAllNodesWithText(text(R.string.about_copied)).assertCountEquals(0)
    }

    @Test
    fun theVersionLine_isOneElementForTheScreenReader_withItsAction() {
        show(FakeActions())
        val node = composeRule.onNodeWithText("Version 0.39.0 (build 39000)").fetchSemanticsNode()
        val label = node.config.getOrNull(SemanticsActions.OnClick)?.label
        assertEquals(text(R.string.about_version_copy_hint_a11y), label)
        // The hint "Touche pour copier" is merged into the same element (one stop, not two).
        composeRule.onNodeWithText(text(R.string.android_about_copy_hint), useUnmergedTree = false).assertExists()
    }

    // --- Help, contact, form (AP5, AP6, AP8, AP9) -----------------------------------------------------------------------------------

    @Test
    fun theHelpLines_openTheExistingHelpScreens() {
        val taps = Taps()
        show(FakeActions(), taps)
        composeRule
            .onNodeWithText(
                text(R.string.settings_detection_help_title),
            ).performScrollTo()
            .assertHasClickAction()
            .performClick()
        composeRule
            .onNodeWithText(
                text(R.string.notif_settings_help_link),
            ).performScrollTo()
            .assertHasClickAction()
            .performClick()
        assertEquals(listOf("detection", "reminders"), taps.log)
    }

    @Test
    fun theRemindersHelp_isNotThereInTheRestrictedSettings() {
        show(FakeActions(), reminders = false)
        composeRule.onAllNodesWithText(text(R.string.notif_settings_help_link)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.settings_detection_help_title)).assertExists()
    }

    @Test
    fun contact_asksForTheMailApplication_andShowsNoMessageWhenItOpens() {
        val actions = FakeActions()
        show(actions)
        composeRule
            .onNodeWithText(
                text(R.string.settings_privacy_contact),
            ).performScrollTo()
            .assertHasClickAction()
            .performClick()
        assertEquals(listOf("contact"), actions.log)
        composeRule.onAllNodesWithText("support@example.org", substring = true).assertCountEquals(0)
    }

    @Test
    fun contact_withoutAMailApplication_showsTheAddressCalmly_andOffersToCopyIt() {
        val actions = FakeActions(contactOpens = false)
        show(actions)
        composeRule.onNodeWithText(text(R.string.settings_privacy_contact)).performScrollTo().performClick()
        // AP6: the line does not stay silent: the address is written and can be copied.
        val message = context().getString(R.string.about_contact_unavailable, "support@example.org")
        composeRule.onNodeWithText(message).performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithText(
                text(R.string.about_contact_copy_action),
            ).performScrollTo()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(listOf("support@example.org"), actions.copied)
        composeRule.onNodeWithText(text(R.string.about_copied)).assertExists()
    }

    @Test
    fun feedbackForm_isDrawnWithTheExactLabelOfThePm_andOpensTheForm() {
        val actions = FakeActions()
        show(actions)
        // "Un retour sur l'app ?" in French; the English line is its translation (same key).
        composeRule
            .onNodeWithText(
                text(R.string.about_feedback_title),
            ).performScrollTo()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(listOf("form"), actions.log)
        composeRule.onNodeWithText(text(R.string.about_feedback_subtitle)).assertExists()
        // Said plainly: a website outside Miff, in the browser.
        composeRule.onNodeWithText(text(R.string.about_feedback_note)).assertExists()
    }

    @Test
    fun feedbackForm_isNotDrawnWithoutAnAddress_andNothingElseChanges() {
        show(FakeActions(feedbackFormUrl = null))
        composeRule.onAllNodesWithText(text(R.string.about_feedback_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.about_feedback_subtitle)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.about_feedback_note)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.settings_privacy_contact)).assertExists()
        composeRule.onNodeWithText(text(R.string.about_licenses_title)).assertExists()
    }

    @Test
    fun feedbackForm_ifNothingOpens_saysSoCalmly() {
        show(FakeActions(formOpens = false))
        composeRule.onNodeWithText(text(R.string.about_feedback_title)).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.review_open_failed)).performScrollTo().assertIsDisplayed()
    }

    // --- Texts, licences, structure ---------------------------------------------------------------------------------------------------

    @Test
    fun theLegalTexts_andTheLicences_areOneTouchAway() {
        val actions = FakeActions()
        val taps = Taps()
        show(actions, taps)
        composeRule
            .onNodeWithText(
                text(R.string.auth_consent_terms_link),
            ).performScrollTo()
            .assertHasClickAction()
            .performClick()
        composeRule
            .onNodeWithText(
                text(R.string.auth_consent_privacy_link),
            ).performScrollTo()
            .assertHasClickAction()
            .performClick()
        composeRule
            .onNodeWithText(
                text(R.string.about_licenses_title),
            ).performScrollTo()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(listOf("terms", "privacy"), actions.log)
        assertEquals(listOf("licenses"), taps.log)
    }

    @Test
    fun theBackArrow_goesBack() {
        val taps = Taps()
        show(FakeActions(), taps)
        composeRule.onNodeWithContentDescription(text(R.string.auth_back_a11y)).performClick()
        assertEquals(listOf("back"), taps.log)
    }

    @Test
    fun theSectionTitle_isAHeading() {
        show(FakeActions())
        val node = composeRule.onNodeWithText(text(R.string.about_info_title)).performScrollTo().fetchSemanticsNode()
        assertTrue("the Informations title must be a heading", node.config.contains(SemanticsProperties.Heading))
    }

    // --- AP20: order and emphasis (PM, 2026-11-13) --------------------------------------------------------------------------------------

    private fun top(id: Int): Float =
        composeRule
            .onNodeWithText(text(id))
            .fetchSemanticsNode()
            .boundsInRoot.top

    private fun height(id: Int): Float =
        composeRule
            .onNodeWithText(text(id))
            .fetchSemanticsNode()
            .boundsInRoot.height

    @Test
    fun contactAndFeedback_comeFirst_thenTheInformationLines() {
        show(FakeActions())
        val order =
            listOf(
                R.string.settings_privacy_contact,
                R.string.about_feedback_title,
                R.string.about_info_title,
                R.string.settings_detection_help_title,
                R.string.notif_settings_help_link,
                R.string.auth_consent_terms_link,
                R.string.auth_consent_privacy_link,
                R.string.about_licenses_title,
            ).map { top(it) }
        assertEquals("the blocks must come in this order", order.sorted(), order)
        // The version line is part of the lower section too.
        assertTrue(
            top(R.string.about_feedback_title) <
                composeRule
                    .onNodeWithText("Version 0.39.0 (build 39000)")
                    .fetchSemanticsNode()
                    .boundsInRoot.top,
        )
    }

    @Test
    fun theTwoActions_areLargerCards_theOtherLinesAreDiscreet() {
        show(FakeActions())
        val density = context().resources.displayMetrics.density
        listOf(R.string.settings_privacy_contact, R.string.about_feedback_title).forEach {
            assertTrue("the card of ${text(it)} must be at least 72 dp high", height(it) >= 72 * density)
            // A card also has its calm sentence and its icon: it is one element, with a click action.
            composeRule.onNodeWithText(text(it)).assertHasClickAction()
        }
        listOf(R.string.settings_detection_help_title, R.string.auth_consent_privacy_link).forEach {
            assertTrue(
                "the line ${text(it)} must be smaller than a card",
                height(it) < height(R.string.settings_privacy_contact),
            )
        }
        composeRule.onNodeWithText(text(R.string.android_about_contact_subtitle)).assertExists()
    }

    @Test
    fun withoutTheFormAddress_contactIsStillFirst() {
        show(FakeActions(feedbackFormUrl = null))
        assertTrue(top(R.string.settings_privacy_contact) < top(R.string.about_info_title))
    }

    // --- AP17: the largest font, right-to-left ---------------------------------------------------------------------------------------

    @Test
    fun withTheLargestFont_everyLineIsReachableByScrolling_andKeepsItsTouchTarget() {
        show(FakeActions(), density = Density(density = 3f, fontScale = 2f))
        listOf(
            R.string.android_about_copy_hint,
            R.string.settings_detection_help_title,
            R.string.settings_privacy_contact,
            R.string.about_feedback_title,
            R.string.auth_consent_privacy_link,
            R.string.about_licenses_title,
        ).forEach {
            composeRule
                .onNodeWithText(
                    text(it),
                ).performScrollTo()
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun rightToLeft_drawsTheSameLines() {
        show(FakeActions(), direction = LayoutDirection.Rtl)
        composeRule.onNodeWithText(text(R.string.settings_privacy_contact)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.about_licenses_title)).performScrollTo().assertIsDisplayed()
    }
}
