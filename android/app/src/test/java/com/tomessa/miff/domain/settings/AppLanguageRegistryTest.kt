package com.tomessa.miff.domain.settings

import com.tomessa.miff.testsupport.I18nResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.Collator
import java.util.Locale

/**
 * The languages of the app are listed in the registry `docs/contracts/i18n-languages.json` (ticket I18N-4L, criteria 1, 6 and 10). Android declares them in three
 * places that must say the same thing: [AppLanguage] (Settings > Langue), `localeFilters` of the release build (otherwise the language is removed from the APK) and the
 * folders of resources. `scripts/check-i18n-parity.mjs` checks the same in the CI; this test fails earlier, in the Android build, with a message that names the language.
 *
 * It lists the languages whatever their status (`planned` or `required`): a language that is `planned` in the registry is already in the list of the settings (its texts
 * arrive with the lot 4 of the ticket; until then the app shows English for it).
 */
class AppLanguageRegistryTest {
    private val registryCodes: List<String> get() = I18nResources.languages.map { it.code }

    @Test
    fun theLanguagesOfTheSettingsAreExactlyTheOnesOfTheRegistry() {
        val offered = AppLanguage.entries.mapNotNull { it.tag }
        assertEquals(registryCodes.sorted(), offered.sorted())
    }

    @Test
    fun theLanguagesAreSortedByTheirOwnName_thenThePhoneComesLast() {
        val names = AppLanguage.entries.filter { it.tag != null }.map { I18nResources.language(it.tag!!).nativeName }
        val collator = Collator.getInstance(Locale.ROOT)
        assertEquals(names.sortedWith(collator), names)
        assertEquals(AppLanguage.SYSTEM, AppLanguage.entries.last())
    }

    @Test
    fun theNameOfEachLanguageIsTheRegistryOneWrittenInItsOwnLanguage_andIsNeverTranslated() {
        val english = File("src/main/res/values/strings.xml").readText()
        AppLanguage.entries.filter { it.tag != null }.forEach { language ->
            val key = "settings_language_${language.name.lowercase()}"
            val declaration = Regex("""<string name="$key"([^>]*)>([^<]*)</string>""").find(english)
            assertTrue("missing $key in values/strings.xml", declaration != null)
            assertEquals(key, I18nResources.language(language.tag!!).nativeName, declaration!!.groupValues[2])
            assertTrue(
                "$key must be translatable=\"false\"",
                declaration.groupValues[1].contains("translatable=\"false\""),
            )
        }
    }

    @Test
    fun theReleaseBuildKeepsTheLanguagesOfTheRegistry_otherwiseTheyAreRemovedFromTheApk() {
        val gradle = File("build.gradle.kts").readText()
        val kept =
            Regex("""localeFilters\s*\.\s*addAll\s*\(([^)]*)\)""")
                .findAll(gradle)
                .flatMap { Regex(""""([A-Za-z-]+)"""").findAll(it.groupValues[1]).map { tag -> tag.groupValues[1] } }
                .toSet()
        assertEquals(registryCodes.toSet(), kept)
    }

    @Test
    fun theFolderOfEachLanguageIsTheOneOfTheRegistry_andEveryRequiredLanguageHasItsTexts() {
        I18nResources.languages.forEach { language ->
            val expected = if (language.code == "en") "values" else "values-${language.code}"
            assertEquals(language.code, expected, language.resDir)
            if (language.status ==
                "required"
            ) {
                assertTrue("${language.resDir}/strings.xml is missing", language.stringsFile.isFile)
            }
        }
        // No empty folder of language (no `values-es` without its texts: the lot 4 of the ticket brings them all at once).
        File("src/main/res")
            .listFiles { file -> file.isDirectory && Regex("values-[a-z]{2}(-r[A-Z]{2})?").matches(file.name) }
            .orEmpty()
            .forEach { folder ->
                assertTrue("${folder.name} has no strings.xml", File(folder, "strings.xml").isFile)
            }
    }

    @Test
    fun everyPluralOfEveryLanguageHasItsRequiredForms_andNoneThatTheLanguageDoesNotKnow() {
        I18nResources.present.forEach { language ->
            val plurals =
                Regex(
                    """<plurals name="([^"]+)">(.*?)</plurals>""",
                    RegexOption.DOT_MATCHES_ALL,
                ).findAll(language.text()).toList()
            assertTrue("${language.code} has no plurals at all", plurals.isNotEmpty())
            plurals.forEach { plural ->
                val forms =
                    Regex(
                        """<item quantity="([a-z]+)">""",
                    ).findAll(plural.groupValues[2]).map { it.groupValues[1] }.toList()
                assertEquals(
                    "${language.code}/${plural.groupValues[1]}: a form is written twice",
                    forms.size,
                    forms.toSet().size,
                )
                assertTrue(
                    "${language.code}/${plural.groupValues[1]} lacks ${language.pluralsRequired - forms.toSet()}",
                    forms.toSet().containsAll(language.pluralsRequired),
                )
                assertTrue(
                    "${language.code}/${plural.groupValues[1]} has a form that ${language.code} does not use: ${forms.toSet() - language.pluralsAllowed}",
                    language.pluralsAllowed.containsAll(forms),
                )
            }
        }
    }
}
