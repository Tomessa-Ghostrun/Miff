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
        // The ONE exception: the airplane of the MODE VOYAGE (a deliberate decision of the ticket, docs/tickets/mode-voyage.md section 6.2; the state is grey and the words
        // say it). Every other state, green, orange, red, neutral, stays without a pictogram.
        val icons = Regex("""Icon\(""").findAll(chip).count()
        val planes = Regex("""ic_travel_plane""").findAll(chip).count()
        assertTrue("the label of the state draws an icon that is not the airplane of the trip", icons == planes)
        assertTrue(
            "the airplane must only be drawn for a trip",
            planes == 0 || chip.contains("card.label is StateLabel.Traveling"),
        )
        assertFalse("labelIcon is back", cards.contains("labelIcon"))
    }

    @Test
    fun theWordsOfTheStateStay_forTheScreenReader() {
        // The label is still a Text inside the merged block of the card, and the header still says the state of the person.
        val cards = source("FeedCards.kt")
        assertTrue(cards.contains("texts.label(card.label)"))
        assertTrue(File("src/main/res/values-fr/strings.xml").readText().contains("name=\"feed_header_avatar_a11y\""))
    }
}
