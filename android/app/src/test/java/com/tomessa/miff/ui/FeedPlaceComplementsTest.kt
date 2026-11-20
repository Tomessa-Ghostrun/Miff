package com.tomessa.miff.ui

import com.tomessa.miff.testsupport.I18nResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The 14 complements of place of the assembled sentences (ticket I18N-4L, lot 3, glossary section 5.1): `feed_place_leave_*` (after "a quitté") and `feed_place_arrive_*`
 * (after "a rejoint"), 6 categories + a named place each. They replace the six `feed_place_noun_*` (a noun with its article), and French and English must say EXACTLY what they
 * said before ("A quitté la maison", "Left the gym"): the sentences the PM validated do not change with the refactoring. The values below are those of `main` before the lot 3.
 */
class FeedPlaceComplementsTest {
    private val categories = listOf("home", "work", "school", "sport", "relative", "generic")
    private val verbs = listOf("leave", "arrive")

    // As written in the XML (apostrophes escaped), the values of `feed_place_noun_*` of `main` before the lot 3.
    private val french =
        mapOf(
            "home" to "la maison",
            "work" to "le travail",
            "school" to "l\\'école",
            "sport" to "le sport",
            "relative" to "le domicile d\\'un proche",
            "generic" to "un lieu de confiance",
        )
    private val english =
        mapOf(
            "home" to "home",
            "work" to "work",
            "school" to "school",
            "sport" to "the gym",
            "relative" to "a loved one\\'s place",
            "generic" to "a trusted place",
        )

    private fun complements(code: String) =
        I18nResources.strings(I18nResources.language(code), Regex("feed_place_(leave|arrive)_[a-z]+"))

    @Test
    fun frenchAndEnglish_sayExactlyWhatTheyDidBefore_forDepartureArrivalAndNamedPlace() {
        mapOf("fr" to french, "en" to english).forEach { (code, expected) ->
            val texts = complements(code)
            verbs.forEach { verb ->
                categories.forEach { category ->
                    assertEquals(
                        "$code feed_place_${verb}_$category",
                        expected.getValue(category),
                        texts["feed_place_${verb}_$category"],
                    )
                }
                // A named place is the name alone, as before (the name entered by the person, nothing around it).
                assertEquals("$code feed_place_${verb}_named", "%1\$s", texts["feed_place_${verb}_named"])
            }
        }
    }

    @Test
    fun everyLanguage_has14Complements_withAValueInsertedOnlyInTheNamedOnes_andNoOldNounLeft() {
        I18nResources.present.forEach { language ->
            val texts = complements(language.code)
            val expectedKeys = verbs.flatMap { verb -> (categories + "named").map { "feed_place_${verb}_$it" } }.toSet()
            assertEquals("${language.code}: the 14 keys", expectedKeys, texts.keys)
            texts.forEach { (key, value) ->
                val placeholders = Regex("%\\d\\$[sd]").findAll(value).map { it.value }.toList()
                if (key.endsWith("_named")) {
                    assertEquals("${language.code} $key holds the name once", listOf("%1\$s"), placeholders)
                } else {
                    assertEquals("${language.code} $key holds no value inserted", emptyList<String>(), placeholders)
                }
                assertFalse("${language.code} $key is empty", value.isBlank())
            }
            assertFalse("${language.code} still has feed_place_noun_*", language.text().contains("feed_place_noun_"))
        }
    }
}
