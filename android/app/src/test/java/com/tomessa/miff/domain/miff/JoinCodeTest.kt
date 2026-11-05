package com.tomessa.miff.domain.miff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/** The secret code of a Miff (ticket Miff 5.3, criteria C4, J1, J2). */
class JoinCodeTest {
    @Test
    fun alphabet_hasThe31SymbolsOfTheRules_withoutTheLookAlikes() {
        assertEquals(31, JoinCode.ALPHABET.length)
        assertEquals(31, JoinCode.ALPHABET.toSet().size)
        listOf(
            'I',
            'L',
            'O',
            '0',
            '1',
        ).forEach { assertFalse("$it must not be in the alphabet", it in JoinCode.ALPHABET) }
        // The same alphabet as the rule isJoinCode of firestore.rules: [A-HJ-KM-NP-Z2-9].
        val fromRules = ('A'..'H') + ('J'..'K') + ('M'..'N') + ('P'..'Z') + ('2'..'9')
        assertEquals(fromRules.joinToString(""), JoinCode.ALPHABET)
    }

    @Test
    fun generate_1000Codes_allValid_noLookAlike_andNotAllTheSame() {
        val random = SecureRandom()
        val codes = List(1000) { JoinCode.generate(random) }
        codes.forEach { code ->
            assertEquals(12, code.length)
            assertTrue("valid shape", JoinCode.isValid(code))
            assertTrue(code.none { it in "ILO01" })
        }
        assertTrue("1000 draws are not 1000 times the same code", codes.toSet().size > 990)
        // Every symbol shows up over 12 000 characters (no symbol is left out by the draw).
        assertEquals(31, codes.joinToString("").toSet().size)
    }

    @Test
    fun generate_hasNoModuloBias_byRejection() {
        // A generator that only ever returns the byte 255 (rejected: >= 248) must not loop forever nor give a skewed symbol:
        // it is fed with 255 first, then with 0..247 which map to the alphabet evenly.
        val feed = ArrayDeque<Int>()
        repeat(40) { feed.add(255) }
        (0 until 248).forEach { feed.add(it) }
        val random =
            object : SecureRandom() {
                override fun nextBytes(bytes: ByteArray) {
                    for (i in bytes.indices) bytes[i] = (feed.removeFirstOrNull() ?: 0).toByte()
                }
            }
        val code = JoinCode.generate(random)
        assertTrue(JoinCode.isValid(code))
        // The first accepted values are 0, 1, 2, ... : the first symbols of the alphabet, in order.
        assertEquals(JoinCode.ALPHABET.take(12), code)
    }

    @Test
    fun normalize_capitalsAndNoSpacesOrDashes() {
        assertEquals("XTZRSP367988", JoinCode.normalize("xtzr sp36 7988"))
        assertEquals("XTZRSP367988", JoinCode.normalize(" XTZR-SP36-7988 "))
        assertEquals("XTZRSP367988", JoinCode.normalize("xtzr–sp36 7988\n"))
        assertEquals("XTZRSP367988", JoinCode.normalize("XTZR_SP36\t7988"))
    }

    @Test
    fun analyze_twelveValidCharacters_isComplete() {
        val input = JoinCode.analyze("xtzr sp36 7988")
        assertEquals("XTZRSP367988", input.code)
        assertTrue(input.isComplete)
        assertFalse(input.hasForeignCharacter)
    }

    @Test
    fun analyze_partialCode_isNotComplete_butNotAnError() {
        val input = JoinCode.analyze("XTZR SP")
        assertFalse(input.isComplete)
        assertFalse(input.hasForeignCharacter)
    }

    @Test
    fun analyze_aLookAlikeCharacter_isForeign_andNeverComplete() {
        listOf(
            "XTZRSP36798I",
            "XTZRSP36798L",
            "XTZRSP36798O",
            "XTZRSP367980",
            "XTZRSP367981",
            "XTZRSP36798!",
            "XTZRSP36798é",
        ).forEach {
            val input = JoinCode.analyze(it)
            assertTrue("$it is foreign", input.hasForeignCharacter)
            assertFalse("$it is not complete", input.isComplete)
        }
    }

    @Test
    fun analyze_longerThan12_keepsTheFirst12() {
        val input = JoinCode.analyze("XTZRSP367988K")
        assertEquals("XTZRSP367988", input.code)
        assertEquals(12, input.code.length)
    }

    @Test
    fun grouped_andSpoken_byFour() {
        assertEquals("XTZR SP36 7988", JoinCode.grouped("XTZRSP367988"))
        assertEquals("X T Z R, S P 3 6, 7 9 8 8", JoinCode.spoken("XTZRSP367988"))
        assertEquals("XTZR SP", JoinCode.grouped("XTZRSP"))
    }

    @Test
    fun toString_neverShowsTheCode() {
        assertFalse(JoinCode.analyze("XTZRSP367988").toString().contains("XTZR"))
    }
}
