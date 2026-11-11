package com.tomessa.miff.ui.profile

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Decision of the PM of 2026-11-11: the card "Mode voyage" is the LAST block of the Profile tab, after the carousel of Miffs and the places of the Miff. Checked on the
 * source of the screen (the order of the calls in its column).
 */
class ProfileTravelCardOrderTest {
    private val source = File("src/main/java/com/tomessa/miff/ui/profile/ProfileTabScreen.kt").readText()

    @Test
    fun theTravelCard_isDrawnAfterTheCarouselOfMiffs_andAfterThePlaces() {
        val carousel = source.indexOf("        MiffCarouselSection(miffs, carousel, onOpenMiff)")
        val places = source.indexOf("        placesContent()")
        val travel = source.indexOf("        travelContent()")
        assertTrue("the carousel is not found", carousel > 0)
        assertTrue("the places are not found", places > carousel)
        assertTrue("the travel card must come after the carousel and the places", travel > places)
    }

    @Test
    fun theTravelCard_isTheLastBlockOfTheColumn() {
        val travel = source.indexOf("        travelContent()")
        val after = source.substring(travel + "        travelContent()".length)
        // Only the end of the column and of the function follow it.
        assertTrue("something is drawn after the travel card: $after", after.trim().startsWith("}"))
    }
}
