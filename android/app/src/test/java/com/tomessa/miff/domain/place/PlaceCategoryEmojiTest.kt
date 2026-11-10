package com.tomessa.miff.domain.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The emoji of the seven categories (decision of the PM, 2026-11-10): a CONTRACT shared with iOS (docs/contracts/place-categories.md). The table is
 * written here by code point, so that nothing can silently change one (an editor, an encoding) without a test failing.
 */
class PlaceCategoryEmojiTest {
    private fun codePoints(emoji: String) = emoji.codePoints().toArray().toList()

    @Test
    fun theTable_isTheOneOfTheContract() {
        assertEquals(listOf(0x1F3E0), codePoints(PlaceCategory.HOME.emoji)) // house
        assertEquals(listOf(0x1F4BC), codePoints(PlaceCategory.WORK.emoji)) // briefcase
        assertEquals(listOf(0x1F3EB), codePoints(PlaceCategory.SCHOOL.emoji)) // school
        assertEquals(listOf(0x26BD), codePoints(PlaceCategory.SPORT.emoji)) // soccer ball
        assertEquals(listOf(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467), codePoints(PlaceCategory.RELATIVE.emoji)) // family
        assertEquals(listOf(0x1F389), codePoints(PlaceCategory.LEISURE.emoji)) // party popper
        assertEquals(listOf(0x1F4CD), codePoints(PlaceCategory.OTHER.emoji)) // round pushpin
    }

    @Test
    fun everyCategory_hasItsOwnEmoji() {
        assertEquals(7, PlaceCategory.entries.size)
        assertEquals(7, PlaceCategory.entries.map { it.emoji }.toSet().size)
        assertTrue(PlaceCategory.entries.none { it.emoji.isBlank() })
    }

    @Test
    fun theTechnicalValues_writtenInTheDocumentOfAPlace_areUntouched() {
        assertEquals(
            listOf("home", "work", "school", "sport", "relative", "leisure", "other"),
            PlaceCategory.entries.map { it.value },
        )
    }
}
