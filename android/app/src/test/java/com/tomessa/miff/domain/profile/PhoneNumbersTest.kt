package com.tomessa.miff.domain.profile

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.tomessa.miff.domain.auth.ProfileDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Criteria TEL1 to TEL5, TEL2 (country by default, "+" number gives the country) and QA7 (limit cases of the phone):
 * the form of the number is checked against the numbering plan of the chosen country, and ONLY the E.164 form comes out
 * (the same shape as the Firestore rules). A table of countries and numbers, plus the example number of EVERY country of
 * the library as a safety net.
 */
class PhoneNumbersTest {
    private val util = PhoneNumberUtil.getInstance()

    private fun valid(
        region: String,
        input: String,
    ): String {
        val result = PhoneNumbers.normalize(region, input)
        assertTrue("$region / \"$input\" should be valid but is $result", result is PhoneResult.Valid)
        return (result as PhoneResult.Valid).e164
    }

    private fun assertInvalid(
        region: String,
        input: String,
    ) = assertEquals("$region / \"$input\"", PhoneResult.Invalid, PhoneNumbers.normalize(region, input))

    // --- Several ways to type the same number give the SAME string (criterion TEL4) -----------------------------

    @Test
    fun france_equivalentInputsGiveTheSameE164() {
        listOf(
            "06 12 34 56 78",
            "6 12 34 56 78",
            "0612345678",
            "612345678",
            "06.12.34.56.78",
            "06-12-34-56-78",
            "(06) 12 34 56 78",
            " 06 12 34 56 78 ",
            "06 12 34 56 78",
            "+33 6 12 34 56 78",
            "+33612345678",
            "+33 (0)6 12 34 56 78",
            "0033 6 12 34 56 78",
        ).forEach { assertEquals(it, "+33612345678", valid("FR", it)) }
    }

    @Test
    fun anInternationalNumberKeepsItsOwnCountry_whateverTheSelectedOne() {
        assertEquals("+33612345678", valid("US", "+33 6 12 34 56 78"))
        assertEquals("+447400123456", valid("FR", "+44 7400 123456"))
    }

    @Test
    fun digitsOfOtherScriptsAreUnderstood() {
        assertEquals("+33612345678", valid("FR", "٠٦١٢٣٤٥٦٧٨")) // Arabic-Indic
        assertEquals("+33612345678", valid("FR", "０６１２３４５６７８")) // full width
    }

    // --- A table of countries ---------------------------------------------------------------------------------------

    @Test
    fun aTableOfCountriesAndNumbers() {
        val table =
            listOf(
                Triple("FR", "07 83 30 00 44", "+33783300044"), // valid mobile (the "error" example of the mockup)
                Triple("FR", "01 23 45 67 89", "+33123456789"), // a landline is accepted too
                Triple("GB", "07400 123456", "+447400123456"),
                Triple("GB", "020 7946 0958", "+442079460958"),
                Triple("US", "(202) 555-0123", "+12025550123"),
                Triple("US", "1 202 555 0123", "+12025550123"),
                Triple("CA", "613-555-0199", "+16135550199"),
                Triple("ES", "612 34 56 78", "+34612345678"),
                Triple("IT", "312 345 6789", "+393123456789"),
                Triple("BE", "0470 12 34 56", "+32470123456"),
                Triple("CH", "079 123 45 67", "+41791234567"),
                Triple("PT", "912 345 678", "+351912345678"),
                Triple("MA", "0612-345678", "+212612345678"),
                Triple("DZ", "0551 23 45 67", "+213551234567"),
                Triple("SN", "77 123 45 67", "+221771234567"),
                Triple("CI", "07 12 34 56 78", "+2250712345678"),
                Triple("JP", "090-1234-5678", "+819012345678"),
                Triple("IN", "98765 43210", "+919876543210"),
                Triple("BR", "(11) 91234-5678", "+5511912345678"),
                Triple("AU", "0412 345 678", "+61412345678"),
            )
        table.forEach { (region, input, expected) -> assertEquals("$region / $input", expected, valid(region, input)) }
    }

    @Test
    fun theCongos_theCountriesOfThePm() {
        // Congo-Brazzaville (+242) and Congo-Kinshasa (+243): the examples of the numbering plan, typed the national way.
        listOf("CG", "CD").forEach { region ->
            val example = util.getExampleNumberForType(region, PhoneNumberUtil.PhoneNumberType.MOBILE)
            val national = util.format(example, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
            val e164 = util.format(example, PhoneNumberUtil.PhoneNumberFormat.E164)
            assertEquals(region, e164, valid(region, national))
            assertEquals(region, e164, valid(region, e164))
            assertEquals(region, e164, valid("FR", e164))
        }
        assertEquals(242, PhoneNumbers.callingCodeOf("CG"))
        assertEquals(243, PhoneNumbers.callingCodeOf("CD"))
    }

    @Test
    fun everyCountryOfTheLibrary_roundTripsItsOwnExampleNumber() {
        var checked = 0
        for (region in util.supportedRegions) {
            val example = util.getExampleNumberForType(region, PhoneNumberUtil.PhoneNumberType.MOBILE) ?: continue
            val e164 = util.format(example, PhoneNumberUtil.PhoneNumberFormat.E164)
            val national = util.format(example, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
            val international = util.format(example, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
            assertEquals("$region national", e164, valid(region, national))
            assertEquals("$region international", e164, valid(region, international))
            assertEquals("$region e164", e164, valid(region, e164))
            assertTrue("$region shape of the rules", ProfileDocument.E164.matches(e164))
            checked++
        }
        assertTrue("only $checked countries checked", checked > 200)
    }

    // --- What is refused (criterion TEL3, QA7) -------------------------------------------------------------------------

    @Test
    fun badNumbersAreInvalid_withoutAnyException() {
        listOf(
            "",
            "   ",
            "abc",
            "06 12 34 56 7a",
            "mon numero",
            "12",
            "0612",
            "06 12 34 56",
            "06 12 34 56 789",
            "00 00 00 00 00",
            "0000000000",
            "+",
            "+33",
            "+0 612345678",
            "0 0 0",
            "06123456789012345678",
            "06 12 34 56 78 ext 5",
            "06 12 34 56 78#",
            "06 12 34 56 78 😀",
            "06+12345678",
            "0612345678".repeat(5),
            "\u0000\u0007",
        ).forEach { assertInvalid("FR", it) }
        assertInvalid("US", "123-456-7890") // an area code cannot start with 1
        assertInvalid("US", "202 555 012")
        assertInvalid("GB", "07400 12345")
    }

    @Test
    fun aVeryLongTextIsRefusedAtOnce() {
        assertInvalid("FR", "1".repeat(100))
        assertInvalid("FR", " ".repeat(100) + "0612345678")
    }

    @Test
    fun theE164OfAnAcceptedNumberAlwaysHasTheShapeOfTheRules() {
        listOf("FR" to "06 12 34 56 78", "GB" to "07400 123456", "US" to "202 555 0123").forEach { (region, input) ->
            val e164 = valid(region, input)
            assertTrue(ProfileDocument.E164.matches(e164))
            assertTrue(e164.length in 8..16)
            assertFalse(e164.any { it == ' ' || it == '-' })
        }
    }

    // --- Valid numbers that are not for calling a person (review I2) ---------------------------------------------------------

    private val refusedTypes =
        listOf(
            PhoneNumberUtil.PhoneNumberType.PREMIUM_RATE,
            PhoneNumberUtil.PhoneNumberType.SHARED_COST,
            PhoneNumberUtil.PhoneNumberType.TOLL_FREE,
            PhoneNumberUtil.PhoneNumberType.UAN,
            PhoneNumberUtil.PhoneNumberType.PAGER,
            PhoneNumberUtil.PhoneNumberType.PERSONAL_NUMBER,
            PhoneNumberUtil.PhoneNumberType.VOICEMAIL,
        )

    @Test
    fun premiumRateAndTollFreeNumbers_areValidForTheLibrary_butRefusedHere() {
        assertEquals(PhoneResult.UnsupportedType, PhoneNumbers.normalize("US", "800 555 0123"))
        for (region in listOf("FR", "GB", "DE", "US", "ES", "IT", "BE")) {
            for (type in listOf(
                PhoneNumberUtil.PhoneNumberType.PREMIUM_RATE,
                PhoneNumberUtil.PhoneNumberType.TOLL_FREE,
            )) {
                val example = util.getExampleNumberForType(region, type) ?: continue
                val national = util.format(example, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
                assertTrue("$region $type is valid for the library", util.isValidNumber(example))
                assertEquals(
                    "$region $type $national",
                    PhoneResult.UnsupportedType,
                    PhoneNumbers.normalize(region, national),
                )
            }
        }
    }

    @Test
    fun everyKindOfNumberThatIsNotALineToCall_isRefused_inEveryCountry() {
        var checked = 0
        for (region in util.supportedRegions) {
            for (type in refusedTypes) {
                val example = util.getExampleNumberForType(region, type) ?: continue
                val e164 = util.format(example, PhoneNumberUtil.PhoneNumberFormat.E164)
                assertEquals("$region $type", PhoneResult.UnsupportedType, PhoneNumbers.normalize(region, e164))
                checked++
            }
        }
        assertTrue("only $checked numbers checked", checked > 100)
    }

    @Test
    fun theKindsAccepted_areLandlineMobileAndInternetTelephony() {
        assertEquals(
            setOf(
                PhoneNumberUtil.PhoneNumberType.FIXED_LINE,
                PhoneNumberUtil.PhoneNumberType.MOBILE,
                PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE,
                PhoneNumberUtil.PhoneNumberType.VOIP,
            ),
            PhoneNumbers.ALLOWED_TYPES,
        )
        for (type in PhoneNumberUtil.PhoneNumberType.entries) {
            if (type == PhoneNumberUtil.PhoneNumberType.UNKNOWN) continue
            val expected = type in PhoneNumbers.ALLOWED_TYPES
            assertEquals(type.toString(), expected, type !in refusedTypes)
        }
    }

    @Test
    fun voipNumbersAreAccepted() {
        var checked = 0
        for (region in util.supportedRegions) {
            val example = util.getExampleNumberForType(region, PhoneNumberUtil.PhoneNumberType.VOIP) ?: continue
            val e164 = util.format(example, PhoneNumberUtil.PhoneNumberFormat.E164)
            assertEquals("$region", e164, valid(region, e164))
            checked++
        }
        assertTrue(checked > 0)
    }

    // --- Button, default country, example, "+" number ------------------------------------------------------------------

    @Test
    fun theArrowIsActiveFromOneDigit() {
        assertFalse(PhoneNumbers.hasDigit(""))
        assertFalse(PhoneNumbers.hasDigit("   "))
        assertFalse(PhoneNumbers.hasDigit("+ ( ) -"))
        assertFalse(PhoneNumbers.hasDigit("abc"))
        assertTrue(PhoneNumbers.hasDigit("0"))
        assertTrue(PhoneNumbers.hasDigit("  7 "))
    }

    @Test
    fun theDefaultCountryIsTheRegionOfTheDevice_elseFrance() {
        assertEquals("FR", PhoneNumbers.defaultRegion("FR"))
        assertEquals("US", PhoneNumbers.defaultRegion("US"))
        assertEquals("CG", PhoneNumbers.defaultRegion("cg"))
        listOf(null, "", "  ", "ZZ", "XX", "001", "123", "France").forEach {
            assertEquals("fallback for $it", "FR", PhoneNumbers.defaultRegion(it))
        }
    }

    @Test
    fun theHintIsAnExampleWithoutCallingCodeNorFirstZero() {
        assertEquals("6 12 34 56 78", PhoneNumbers.exampleHint("FR"))
        assertNotNull(PhoneNumbers.exampleHint("GB"))
        assertFalse(PhoneNumbers.exampleHint("GB")!!.startsWith("+"))
        assertNull(PhoneNumbers.exampleHint("ZZ"))
    }

    @Test
    fun aPlusNumberSelectsItsCountryAndKeepsTheNationalPart() {
        assertEquals(DetectedNumber("FR", "612345678"), PhoneNumbers.detectInternational("+33 6 12 34 56 78"))
        assertEquals(DetectedNumber("GB", "7400123456"), PhoneNumbers.detectInternational("+44 7400 123456"))
        assertEquals("2025550123", PhoneNumbers.detectInternational("+1 202 555 0123")?.national)
        assertEquals(1, PhoneNumbers.callingCodeOf(PhoneNumbers.detectInternational("+1 202 555 0123")!!.region))
    }

    @Test
    fun aTextThatIsNotAPlusNumberLeavesTheFieldAlone() {
        listOf(
            "",
            "0612345678",
            "612345678",
            "+",
            "+3",
            "+33",
            "+33 6 12",
            "+999 123 456",
            "+33 6 12 34 56 7a",
            "abc",
        ).forEach {
            assertNull("\"$it\"", PhoneNumbers.detectInternational(it))
        }
    }

    // --- The country selector --------------------------------------------------------------------------------------------

    @Test
    fun theListHasAllTheCountries_sortedByNameInTheLanguageOfThePhone() {
        val french = PhoneNumbers.countries(Locale.FRENCH)
        assertTrue("only ${french.size} countries", french.size > 200)
        val byRegion = french.associateBy { it.region }
        assertEquals("Allemagne", byRegion.getValue("DE").name)
        assertEquals("France", byRegion.getValue("FR").name)
        assertEquals(33, byRegion.getValue("FR").callingCode)
        assertEquals("+33", byRegion.getValue("FR").dialCode)
        assertEquals(242, byRegion.getValue("CG").callingCode)
        assertEquals(243, byRegion.getValue("CD").callingCode)
        assertTrue(french.indexOfFirst { it.region == "DE" } < french.indexOfFirst { it.region == "FR" })
        assertEquals("Germany", PhoneNumbers.countries(Locale.ENGLISH).first { it.region == "DE" }.name)
        assertTrue("no country is nameless", french.all { it.name.isNotBlank() && it.callingCode > 0 })
    }

    @Test
    fun theFlagIsBuiltFromTheRegionLetters() {
        assertEquals("🇫🇷", PhoneCountry("FR", 33, "France").flag)
        assertEquals("🇨🇬", PhoneCountry("CG", 242, "Congo").flag)
    }

    @Test
    fun searchIgnoresAccentsAndCase_andFindsByNameCodeOrCallingCode() {
        val countries = PhoneNumbers.countries(Locale.FRENCH)

        fun find(query: String) = countries.filter { PhoneNumbers.matchesSearch(it, query) }.map { it.region }
        assertTrue("FR" in find("fran"))
        assertTrue("FR" in find("FRAN"))
        assertTrue("CI" in find("cote"))
        assertTrue("DE" in find("allemagne"))
        assertTrue("FR" in find("+33"))
        assertTrue("FR" in find("33"))
        assertTrue("CG" in find("242"))
        assertTrue("US" in find("us"))
        assertTrue(find("zzzzzz").isEmpty())
        assertEquals(countries.size, find("").size)
        assertEquals(countries.size, find("  ").size)
    }
}
