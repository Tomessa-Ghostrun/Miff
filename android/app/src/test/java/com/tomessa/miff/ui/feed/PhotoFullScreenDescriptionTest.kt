package com.tomessa.miff.ui.feed

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.domain.update.PhotoLocation
import com.tomessa.miff.domain.update.SharedPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The photo in full screen with a DESCRIPTION (ticket description-update, criteria F1 to F6, F8, A3, A4, V5): the text of the author is ABOVE "Photo de ...", whole, as plain
 * text, read before the title; without a description nothing is added; the footer is at most 45 % of the height and scrolls beyond it, even with the largest font.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w360dp-h640dp", application = android.app.Application::class)
class PhotoFullScreenDescriptionTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = 1_700_000_000_000L
    private val title = "Léa's photo"

    private fun photo(description: String?) =
        SharedPhoto(
            jpeg = ByteArray(10) { 1 },
            createdAtMillis = now - 60_000L,
            expiresAtMillis = now + 3_600_000L,
            location = PhotoLocation(48.85, 2.35, now - 120_000L),
            description = description,
        )

    private fun show(
        description: String?,
        fontScale: Float = 1f,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                PhotoFullScreen(
                    opened = OpenedPhoto("Léa", photo(description)),
                    texts = FeedTexts(context, java.time.ZoneId.of("UTC")),
                    nowMillis = now,
                    onOpenMap = { true },
                    onDismiss = {},
                )
            }
        }
    }

    private fun top(node: androidx.compose.ui.test.SemanticsNodeInteraction) =
        node.fetchSemanticsNode().boundsInRoot.top

    @Test
    fun theDescriptionIsAboveThePhotoTitle() {
        show("Bien arrivée, on mange ensemble ce soir")
        val description = rule.onNodeWithText("Bien arrivée, on mange ensemble ce soir")
        val photoTitle = rule.onNode(hasText(title, substring = true))
        description.assertIsDisplayed()
        photoTitle.assertIsDisplayed()
        assertTrue("the description is above the title", top(description) < top(photoTitle))
        // The place and "Voir où c'était" stay below the title.
        assertTrue(top(photoTitle) < top(rule.onNodeWithText("See where it was")))
    }

    @Test
    fun withoutADescription_theFooterIsWhatItAlwaysWas_noEmptyLine_noLabel() {
        show(null)
        rule.onNode(hasText(title, substring = true)).assertIsDisplayed()
        rule.onNode(hasText("Description", substring = true)).assertDoesNotExist()
        val spoken =
            rule.onAllNodesWithContentDescriptionStartingWith("Description:")
        assertEquals(0, spoken)
    }

    @Test
    fun theDescriptionIsReadWithItsWord_beforeTheTitle() {
        show("Je suis là")
        val node = rule.onNodeWithText("Je suis là").fetchSemanticsNode()
        assertEquals(listOf("Description: Je suis là"), node.config.getOrNull(SemanticsProperties.ContentDescription))
        // The title is a heading (the "head" of the footer for a screen reader).
        val heading = rule.onNode(hasText(title, substring = true)).fetchSemanticsNode()
        assertTrue(heading.config.contains(SemanticsProperties.Heading))
    }

    @Test
    fun theDescriptionIsPlainText_noLinkNoStyle_notSelectable() {
        val text = "Voir https://exemple.fr ou **gras** 06 12 34 56 78"
        show(text)
        val node = rule.onNodeWithText(text).fetchSemanticsNode()
        val shown = node.config.getOrNull(SemanticsProperties.Text)!!.single()
        assertEquals(text, shown.text)
        assertTrue("no span, no style: plain text", shown.spanStyles.isEmpty() && shown.paragraphStyles.isEmpty())
        // Not selectable: no copy action is offered on the text (criterion V5).
        assertNull(node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.CopyText))
    }

    @Test
    fun theWhole140CharactersAreThere_neverCut() {
        val text = "mot ".repeat(34) + "ab" // 138 characters
        show(text)
        rule.onNodeWithText(text).assertTextEquals(text)
    }

    @Test
    fun withTheLargestFont_aLongDescription_scrollsInsideTheFooter_thatStaysUnder45PercentOfTheHeight() {
        val text = "Un très long message pour tester les grandes polices. ".repeat(3).take(140)
        show(text, fontScale = 2f)
        val footer = rule.onNode(hasScrollAction())
        footer.assertIsDisplayed()
        val height = footer.fetchSemanticsNode().boundsInRoot.height
        // 640 dp of height in this configuration: the footer takes at most 45 % of it (a pixel or two of rounding allowed).
        val density = rule.density
        val limit = with(density) { (640.dp * 0.45f).toPx() }
        assertTrue("footer $height px <= $limit px", height <= limit + 2f)
        // The whole text is in the footer (it scrolls, it is not cut).
        rule.onNodeWithText(text).assertTextEquals(text)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithContentDescriptionStartingWith(
        prefix: String,
    ): Int =
        onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher("contentDescription starts with $prefix") {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { text -> text.startsWith(prefix) } ==
                    true
            },
        ).fetchSemanticsNodes().size
}
