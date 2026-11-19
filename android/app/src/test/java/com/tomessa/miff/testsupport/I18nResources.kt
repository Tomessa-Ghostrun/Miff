package com.tomessa.miff.testsupport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * The languages of the app as the registry `docs/contracts/i18n-languages.json` lists them (ONE source for the scripts, the CI and these tests). The
 * tests that read the texts to forbid a word or a form (privacy, tone, no place or hour in a notification...) loop over [present] instead of
 * naming `values-fr` and `values`: a new language (es, it, pt, de) is then covered the day its file is added, without touching the test.
 *
 * Tests run in `android/app`: the registry is two folders above.
 */
object I18nResources {
    private const val REGISTRY = "../../docs/contracts/i18n-languages.json"
    private const val RES = "src/main/res"

    /** [resDir] is the Android folder of the language (`values` for English, the fallback; `values-fr`, `values-es`...). */
    data class Language(
        val code: String,
        val nativeName: String,
        val resDir: String,
        val status: String,
        val pluralsRequired: Set<String>,
        val pluralsAllowed: Set<String>,
    ) {
        val stringsFile: File get() = File("$RES/$resDir/strings.xml")

        /** The texts of the language, as written in the file (apostrophes escaped, entities not decoded). */
        fun text(): String = stringsFile.readText()
    }

    /** All the languages of the registry, whatever their status. */
    val languages: List<Language> by lazy {
        val root = Json.parseToJsonElement(File(REGISTRY).readText()).jsonObject
        root.getValue("languages").jsonArray.map { element ->
            val language = element.jsonObject
            val android =
                language
                    .getValue("platforms")
                    .jsonObject
                    .getValue("android")
                    .jsonObject
            val plurals = language.getValue("plurals").jsonObject
            Language(
                code = language.getValue("code").jsonPrimitive.content,
                nativeName = language.getValue("nativeName").jsonPrimitive.content,
                resDir = android.getValue("resDir").jsonPrimitive.content,
                status = android.getValue("status").jsonPrimitive.content,
                pluralsRequired = plurals.strings("required"),
                pluralsAllowed = plurals.strings("allowed"),
            )
        }
    }

    /** The languages whose `strings.xml` exists today: `fr` and `en` now, the four others once their texts are merged (lot 4). */
    val present: List<Language> get() = languages.filter { it.stringsFile.isFile }

    fun language(code: String): Language = languages.first { it.code == code }

    /** `name -> text` of the simple strings of a language, restricted to the keys that match [keys]. */
    fun strings(
        language: Language,
        keys: Regex = Regex("[a-z0-9_]+"),
    ): Map<String, String> =
        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(language.text())
            .filter { keys.matches(it.groupValues[1]) }
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun JsonObject.strings(name: String): Set<String> =
        (getValue(name) as JsonArray).map { it.jsonPrimitive.content }.toSet()
}
