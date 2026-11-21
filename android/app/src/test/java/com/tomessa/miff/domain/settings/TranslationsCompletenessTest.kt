package com.tomessa.miff.domain.settings

import com.tomessa.miff.testsupport.I18nResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The texts of every language of the registry against the French source (ticket I18N-4L, lot 4): same keys, same inserted values (`%1$s`, `%2$d`, `%2$02d`...), the plural
 * forms the language needs. It loops over [I18nResources.present]: a language added to the registry is covered the day its `strings.xml` exists. The CI runs the same checks
 * in `scripts/check-i18n-parity.mjs` (Node); this test fails earlier, inside the Android build, with the key and the language in the message.
 *
 * The internal diagnostic screen (`diag_*`, `android_diag_*`) is French and English only on purpose (PM decision of 2026-11-19): those keys are NOT expected in the other languages
 * (and lint ignores them in `lint.xml`).
 */
class TranslationsCompletenessTest {
    private val diagnostic = Regex("""^(android_)?diag_""")

    /** The languages other than French (the source) whose texts are in the app: English (`values/`, the fallback) and the four of the lot 4. */
    private val others get() = I18nResources.present.filter { it.code != "fr" }

    private val french get() = I18nResources.language("fr")

    private class PluralItems(
        val forms: Map<String, String>,
    )

    private fun simpleStrings(language: I18nResources.Language): Map<String, String> = I18nResources.strings(language)

    private fun plurals(language: I18nResources.Language): Map<String, PluralItems> =
        Regex("""<plurals name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(language.text())
            .associate { plural ->
                val forms =
                    Regex("""<item quantity="([a-z]+)">(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
                        .findAll(plural.groupValues[2])
                        .associate { it.groupValues[1] to it.groupValues[2] }
                plural.groupValues[1] to PluralItems(forms)
            }

    /** The inserted values of a text as `position:type` (an unnumbered `%s` takes its rank), sorted: the ORDER in the sentence is free, the set is not. */
    private fun inserted(text: String): List<String> {
        var rank = 0
        return Regex("""%(?:(\d+)\$)?[-#+0,(]*\d*(?:\.\d+)?([sdfS])""")
            .findAll(text.replace("%%", ""))
            .map { match ->
                val position = match.groupValues[1].ifEmpty { (++rank).toString() }
                "$position:${match.groupValues[2]}"
            }.sorted()
            .toList()
    }

    /** `translatable="false"` strings of English (brand, names of languages): they are never translated, never repeated in a language folder. */
    private fun untranslatable(): Set<String> =
        Regex("""<string name="([^"]+)"[^>]*translatable="false"""")
            .findAll(I18nResources.language("en").text())
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun theSixLanguagesOfTheRegistryAreInTheBuild() {
        assertEquals(
            listOf("de", "en", "es", "fr", "it", "pt"),
            I18nResources.present.map { it.code }.sorted(),
        )
    }

    @Test
    fun everyCommonKeyOfFrenchExistsInEveryLanguage_exceptTheInternalDiagnosticScreen() {
        val strings = simpleStrings(french).keys.filterNot { diagnostic.containsMatchIn(it) }
        val pluralKeys = plurals(french).keys.filterNot { diagnostic.containsMatchIn(it) }
        val skipped = untranslatable()
        others.forEach { language ->
            val translated = simpleStrings(language)
            val translatedPlurals = plurals(language)
            val missing = strings.filter { it !in skipped && it !in translated }
            assertTrue("${language.code}: missing texts $missing", missing.isEmpty())
            val missingPlurals = pluralKeys.filter { it !in translatedPlurals }
            assertTrue("${language.code}: missing plurals $missingPlurals", missingPlurals.isEmpty())
        }
    }

    @Test
    fun noLanguageHasAKeyThatFrenchDoesNotHave_andNoneRepeatsAnUntranslatableOne() {
        val frenchKeys = simpleStrings(french).keys + plurals(french).keys
        val skipped = untranslatable()
        others.filter { it.code != "en" }.forEach { language ->
            val keys = simpleStrings(language).keys + plurals(language).keys
            assertTrue("${language.code}: unknown keys ${keys - frenchKeys}", (keys - frenchKeys).isEmpty())
            assertTrue(
                "${language.code}: untranslatable keys written ${keys intersect skipped}",
                (
                    keys intersect
                        skipped
                ).isEmpty(),
            )
        }
    }

    @Test
    fun theDiagnosticScreenIsNotTranslated_itStaysInFrenchAndEnglish() {
        others.filter { it.code !in setOf("en") }.forEach { language ->
            val keys = simpleStrings(language).keys + plurals(language).keys
            assertEquals(
                "${language.code}: diagnostic keys",
                emptyList<String>(),
                keys.filter { diagnostic.containsMatchIn(it) },
            )
        }
    }

    @Test
    fun everyTextHasTheSameInsertedValuesAsFrench() {
        val source = simpleStrings(french)
        others.forEach { language ->
            val translated = simpleStrings(language)
            source.forEach { (key, text) ->
                val translation = translated[key] ?: return@forEach
                assertEquals("${language.code}/$key", inserted(text), inserted(translation))
            }
        }
    }

    @Test
    fun everyPluralHasTheSameInsertedValuesAsFrench_inEveryForm() {
        val source = plurals(french)
        others.forEach { language ->
            val translated = plurals(language)
            source.forEach { (key, french) ->
                val translation = translated[key] ?: return@forEach
                // The reference is the French "other" form: it carries every value, the count included.
                val expected = inserted(french.forms.getValue("other"))
                // The "one" form may leave the count out ("Un membre"), never another value (the count is the only %d of a plural).
                val expectedForOne = expected.filterNot { it.endsWith(":d") }
                translation.forms.forEach { (form, text) ->
                    val found = inserted(text)
                    if (form == "one") {
                        assertTrue(
                            "${language.code}/$key/one: $found does not keep $expectedForOne",
                            found.containsAll(expectedForOne),
                        )
                        assertTrue(
                            "${language.code}/$key/one: $found has a value that French does not have ($expected)",
                            expected.containsAll(found),
                        )
                    } else {
                        assertEquals("${language.code}/$key/$form", expected, found)
                    }
                }
            }
        }
    }

    @Test
    fun everyPluralHasTheFormsOfItsLanguage() {
        val frenchPlurals = plurals(french)
        others.forEach { language ->
            plurals(language).forEach { (key, plural) ->
                val forms = plural.forms.keys
                assertTrue(
                    "${language.code}/$key lacks ${language.pluralsRequired - forms}",
                    forms.containsAll(language.pluralsRequired),
                )
                assertTrue(
                    "${language.code}/$key has ${forms - language.pluralsAllowed} (not a form of ${language.code})",
                    language.pluralsAllowed.containsAll(forms),
                )
                // Where the language knows "many" (es, it, pt) and French writes it, the language writes it too: lint (MissingQuantity) asks for it.
                if ("many" in language.pluralsAllowed && "many" in frenchPlurals.getValue(key).forms.keys) {
                    assertTrue("${language.code}/$key lacks many (French has it)", "many" in forms)
                }
            }
        }
    }

    @Test
    fun noTextIsEmptyOrLeftAsAToDo() {
        others.forEach { language ->
            simpleStrings(language).forEach { (key, text) ->
                assertTrue("${language.code}/$key is empty", text.isNotBlank())
                assertTrue(
                    "${language.code}/$key is a to-do",
                    !Regex("""TODO|À TRADUIRE|A TRADUIRE|FIXME""").containsMatchIn(text),
                )
            }
        }
    }

    @Test
    fun theBrandIsWrittenExactly() {
        others.forEach { language ->
            simpleStrings(language).forEach { (key, text) ->
                assertTrue(
                    "${language.code}/$key writes the brand wrong",
                    !Regex("""\bmiff\b|\bMiff Plus\b|\bMIFF\b""").containsMatchIn(text.replace("miff_", "")),
                )
            }
        }
    }
}
