package com.tomessa.miff.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The description of an update (ticket description-update, criteria S2, S3, S5 to S7, P1 to P3, C5, 11 "cas limites"): the cleaning shared with the first names, the limit
 * of 140 UTF-16 units that never cuts an emoji, the empty text that means "no description", and the counter that only shows from 120.
 *
 * The special characters are built from their code points (never typed in the source: an invisible character in a file cannot be reviewed).
 */
class PhotoDescriptionTest {
    private fun cp(vararg codes: Int): String = codes.joinToString("") { String(Character.toChars(it)) }

    private val joiner = cp(0x200D)
    private val man = cp(0x1F468)
    private val woman = cp(0x1F469)
    private val girl = cp(0x1F467)
    private val family = man + joiner + woman + joiner + girl // 8 units
    private val smile = cp(0x1F600) // 2 units
    private val flagFr = cp(0x1F1EB, 0x1F1F7) // 4 units

    // --- Cleaning ------------------------------------------------------------------------------------------------------------------

    @Test
    fun anOrdinaryText_isKeptAsItIs() {
        assertEquals(
            "Je suis bien arrivé, à tout à l'heure",
            PhotoDescription.normalize("Je suis bien arrivé, à tout à l'heure"),
        )
    }

    @Test
    fun newLinesAndTabs_becomeASpace_neverALineBreak() {
        assertEquals("a b c d", PhotoDescription.normalize("a\nb\r\nc\td"))
        val typed = PhotoDescription.limitInput("a\nb\tc" + cp(0x2028) + "d" + cp(0x2029) + "e").text
        assertEquals("a b c d e", typed)
        assertFalse(typed.contains('\n'))
    }

    @Test
    fun controlInvisibleAndDirectionCharacters_areRemoved_likeTheFirstNames() {
        val unsafe =
            listOf(
                0x0000,
                0x0007,
                0x007F,
                0x200B,
                0x200E,
                0x200F,
                0x202A,
                0x202B,
                0x202C,
                0x202D,
                0x202E,
                0x2060,
                0x2066,
                0x2067,
                0x2068,
                0x2069,
                0xFEFF,
            )
        unsafe.forEach { code ->
            val c = cp(code)
            assertEquals("U+" + code.toString(16), "ab", PhotoDescription.normalize("a" + c + "b"))
            assertEquals("U+" + code.toString(16), "ab", PhotoDescription.limitInput("a" + c + "b").text)
        }
    }

    @Test
    fun theZeroWidthJoiner_isKept_aFamilyStaysWhole() {
        assertEquals(family, PhotoDescription.normalize(family))
        assertEquals("Nous $family", PhotoDescription.limitInput("Nous $family").text)
    }

    @Test
    fun anUnpairedSurrogate_isDropped_neverSent() {
        assertEquals("ab", PhotoDescription.normalize("a" + 0xD83D.toChar() + "b"))
        assertEquals("ab", PhotoDescription.normalize("a" + 0xDE00.toChar() + "b"))
    }

    @Test
    fun onPublication_repeatedSpacesAreReducedToOne_andTheEdgesAreTrimmed() {
        assertEquals("a b c", PhotoDescription.normalize("   a   b " + cp(0xA0) + " c   "))
        // While typing the spaces are left alone (the cursor of the person must not jump).
        assertEquals("a  b ", PhotoDescription.limitInput("a  b ").text)
    }

    @Test
    fun theTextIsNeverInterpreted_aLinkOrMarkdownStaysAsWritten() {
        val text = "**bien** https://exemple.fr 06 12 34 56 78"
        assertEquals(text, PhotoDescription.normalize(text))
    }

    // --- Empty = no description ------------------------------------------------------------------------------------------------------

    @Test
    fun anEmptyText_orOnlySpaces_orOnlyInvisibleCharacters_isNoDescription() {
        listOf(
            "",
            " ",
            "     ",
            "\t\n\r",
            cp(0xA0, 0x2003),
            cp(0x200B, 0x200E, 0xFEFF),
            " \n " + cp(0x202E) + " ",
        ).forEach {
            assertNull("length ${it.length}", PhotoDescription.normalize(it))
        }
    }

    // --- The limit of 140 UTF-16 units -------------------------------------------------------------------------------------------------

    @Test
    fun exactly140Units_isAccepted_141IsCut() {
        val at140 = "a".repeat(140)
        assertEquals(at140, PhotoDescription.normalize(at140))
        assertFalse(PhotoDescription.limitInput(at140).wasCut)
        val cut = PhotoDescription.limitInput("a".repeat(141))
        assertEquals(at140, cut.text)
        assertTrue(cut.wasCut)
    }

    @Test
    fun theLimitCountsUnitsNotCharacters_70SimpleEmojisFit_71DoNot() {
        val seventy = smile.repeat(70)
        assertEquals(140, seventy.length)
        assertEquals(seventy, PhotoDescription.limitInput(seventy).text)
        val cut = PhotoDescription.limitInput(smile.repeat(71))
        assertEquals(seventy, cut.text)
        assertTrue(cut.wasCut)
    }

    @Test
    fun aPasteThatEndsInTheMiddleOfAnEmoji_dropsTheWholeEmoji_neverHalfOfIt() {
        val cut = PhotoDescription.limitInput("a".repeat(139) + smile)
        assertEquals("a".repeat(139), cut.text)
        assertTrue(cut.wasCut)
        assertTrue("no lone surrogate", cut.text.none { Character.isSurrogate(it) })
    }

    @Test
    fun aComposedEmojiThatDoesNotFit_isDroppedWhole_notLeftAsAnIncompleteFamily() {
        // 135 units + a family of 8 = 143: the whole family goes (never a man alone, nor a dangling joiner).
        assertEquals("a".repeat(135), PhotoDescription.limitInput("a".repeat(135) + family).text)
        // Two families: the first fits (132 + 8 = 140), the second goes whole.
        val tight = PhotoDescription.limitInput("a".repeat(132) + family + family)
        assertEquals("a".repeat(132) + family, tight.text)
        assertFalse(tight.text.endsWith(joiner))
        // A cut that would fall just after the joiner of a family that does not fit.
        val afterJoiner = PhotoDescription.limitInput("a".repeat(134) + family)
        assertEquals("a".repeat(134), afterJoiner.text)
    }

    @Test
    fun aFlagIsNeverCutInTwo() {
        assertEquals("a".repeat(138), PhotoDescription.limitInput("a".repeat(138) + flagFr).text)
        assertEquals("a".repeat(136) + flagFr, PhotoDescription.limitInput("a".repeat(136) + flagFr).text)
    }

    @Test
    fun aSkinTone_aKeycapAndAnAccent_stayWithTheirBaseSymbol() {
        val thumbs = cp(0x1F44D, 0x1F3FD) // thumbs up + medium skin tone: 4 units
        assertEquals("a".repeat(138), PhotoDescription.limitInput("a".repeat(138) + thumbs).text)
        assertEquals("a".repeat(136) + thumbs, PhotoDescription.limitInput("a".repeat(136) + thumbs).text)
        // An "e" followed by a combining accent: the accent is not left alone at the start of the cut.
        val accent = PhotoDescription.limitInput("a".repeat(139) + "e" + cp(0x0301))
        assertEquals("a".repeat(139), accent.text)
        val keycap = PhotoDescription.limitInput("a".repeat(138) + "1" + cp(0xFE0F, 0x20E3))
        assertEquals("a".repeat(138), keycap.text)
    }

    @Test
    fun theTextPublished_isNeverLongerThan140_evenWhenTheInputWas() {
        val published = PhotoDescription.normalize("mot ".repeat(100))
        assertNotNull(published)
        assertTrue(published!!.length in 1..140)
    }

    // --- The counter ------------------------------------------------------------------------------------------------------------------

    @Test
    fun theCounter_onlyShowsFrom120_andNeverBefore() {
        assertFalse(PhotoDescription.showCounter(0))
        assertFalse(PhotoDescription.showCounter(119))
        assertTrue(PhotoDescription.showCounter(120))
        assertTrue(PhotoDescription.showCounter(140))
    }

    @Test
    fun theLimitIsReachedAt140() {
        assertFalse(PhotoDescription.isAtLimit(139))
        assertTrue(PhotoDescription.isAtLimit(140))
    }

    // --- What is read from the network (somebody else wrote it) ---------------------------------------------------------------------------

    @Test
    fun aValueFromTheServer_isShownOnlyWhenItIsATextOf1To140Units_andCleaned() {
        assertEquals("Bien rentré", PhotoDescription.fromServer("Bien rentré"))
        assertEquals("a b", PhotoDescription.fromServer("a\n" + cp(0x202E) + "b"))
        assertEquals("a".repeat(140), PhotoDescription.fromServer("a".repeat(140)))
    }

    @Test
    fun aValueFromTheServer_thatIsNotAText_orEmpty_orTooLong_isIgnored() {
        assertNull(PhotoDescription.fromServer(null))
        assertNull(PhotoDescription.fromServer(""))
        assertNull(PhotoDescription.fromServer("  \n "))
        assertNull(PhotoDescription.fromServer(12))
        assertNull(PhotoDescription.fromServer(true))
        assertNull(PhotoDescription.fromServer(listOf("a")))
        assertNull(PhotoDescription.fromServer(ByteArray(3)))
        // A text the rules would never have accepted is not shown cut short either.
        assertNull(PhotoDescription.fromServer("a".repeat(141)))
    }

    @Test
    fun aPhotoFromTheServer_carriesTheCleanedDescription_orNone() {
        fun read(description: Any?) =
            SharedPhoto.fromServer(
                jpeg = ByteArray(10) { 1 },
                createdAtMillis = 1_000L,
                expiresAtMillis = 9_000_000L,
                lat = 48.0,
                lng = 2.0,
                measuredAtMillis = 900L,
                nowMillis = 2_000L,
                description = description,
            )
        assertEquals("Bien arrivé", read("Bien arrivé")!!.description)
        assertEquals("a b", read("a\n" + cp(0x202E) + "b")!!.description)
        assertNull(read(null)!!.description)
        assertNull(read(42)!!.description)
        assertNull(read("")!!.description)
        // The photo itself is still shown when the description is not valid.
        assertNotNull(read("x".repeat(500)))
        assertNull(read("x".repeat(500))!!.description)
    }

    @Test
    fun aPhotoOfTheOldKind_hasNoDescription_andShowsNothingOfItself() {
        assertNull(SharedPhoto(ByteArray(3) { 1 }, 1L, 2L, null).description)
        val withText = SharedPhoto(ByteArray(3) { 1 }, 1L, 2L, null, "secret words")
        assertEquals("SharedPhoto(redacted)", withText.toString())
        assertFalse(withText.toString().contains("secret"))
    }

    @Test
    fun limited_showsNothingOfTheText_inItsToString() {
        val limited = PhotoDescription.limitInput("secret words")
        assertEquals("Limited(redacted)", limited.toString())
        assertFalse(limited.toString().contains("secret"))
        assertEquals("the text itself stays readable", "secret words", limited.text)
    }
}
