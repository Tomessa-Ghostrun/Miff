package com.tomessa.miff.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Decision of the PM of 2026-11-06 ("la couleur est le seul signal"): no glyph (tick, clock, "!") on the ring of the photo of a person, and no
 * pictogram on the label of the state of a member card. The words of the state and the spoken name stay (the screen reader reads them).
 */
class FeedColourAloneRuleTest {
    private val folder = "src/main/java/com/tomessa/miff/ui/feed"

    private fun source(name: String) = File("$folder/$name").readText()

    @Test
    fun theAvatarRing_hasNoGlyph() {
        val style = source("FeedStyle.kt")
        assertFalse("a glyph function is back", style.contains("fun StatusLevel.glyph"))
        listOf("ic_loc_check", "ic_feed_alert", "ic_feed_clock", "Icon(").forEach {
            assertFalse("FeedStyle.kt (the avatar) must not draw \"$it\"", style.contains(it))
        }
    }

    @Test
    fun theStateLabelOfACard_hasNoPictogram() {
        val cards = source("FeedCards.kt")
        val start = cards.indexOf("private fun StateChip(")
        assertTrue(start > 0)
        val end = cards.indexOf("@Composable", start)
        val chip = cards.substring(start, if (end > start) end else cards.length)
        assertFalse("the label of the state draws an icon", chip.contains("Icon("))
        assertFalse("labelIcon is back", cards.contains("labelIcon"))
    }

    /** Decision of the PM of 2026-11-11: no symbol of any kind in the bubble of the Miff (watermark, button icon), whatever the colour. */
    @Test
    fun theBubble_hasNoPictogram_whateverTheColour() {
        val bubble = source("FeedBubble.kt")
        listOf("Icon(", "painterResource", "ic_feed_", "ic_loc_", "ic_travel", "Pictogram", "WATERMARK").forEach {
            assertFalse("FeedBubble.kt must not draw \"$it\" (no symbol in the bubble)", bubble.contains(it))
        }
    }

    @Test
    fun theWordsOfTheStateStay_forTheScreenReader() {
        // The label is still a Text inside the merged block of the card, and the header still says the state of the person.
        val cards = source("FeedCards.kt")
        assertTrue(cards.contains("texts.label(card.label)"))
        assertTrue(File("src/main/res/values-fr/strings.xml").readText().contains("name=\"feed_header_avatar_a11y\""))
    }
}
