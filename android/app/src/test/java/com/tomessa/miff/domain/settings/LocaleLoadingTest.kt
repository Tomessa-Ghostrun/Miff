package com.tomessa.miff.domain.settings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.testsupport.I18nResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Each language of the registry really loads its own texts, with the right plural rules (ticket I18N-4L, lot 4): the resources of the language are read for a device (or an
 * app, `LocaleManager`) set to it; a region of the same language is served by the same folder (`es-MX`, `de-AT`, `pt-BR`); a language the app does not have falls back to
 * ENGLISH (`unqualifiedResLocale=en-US`). Plural forms are the ones of the CLDR rules the texts are written for: 0 is "other" in es, it, de and (Portugal) pt; 0 and 1 are
 * "one" in French.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en", application = android.app.Application::class)
class LocaleLoadingTest {
    private fun inLocale(tag: String): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val config =
            Configuration(
                base.resources.configuration,
            ).apply { setLocales(LocaleList(Locale.forLanguageTag(tag))) }
        return base.createConfigurationContext(config)
    }

    /** What `strings.xml` says for a key, as the resources give it back (only the escapes that the texts used here have). */
    private fun written(
        code: String,
        key: String,
    ): String {
        val text = I18nResources.strings(I18nResources.language(code), Regex(key)).getValue(key)
        return text
            .replace("\\'", "'")
            .replace("&#160;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
    }

    private val keys =
        listOf(
            "home_tagline",
            "auth_signin_title",
            "common_cancel",
            "settings_language_system",
            "paywall_continue",
            "alert_not_emergency_note",
        )

    @Test
    fun eachLanguageLoadsItsOwnTexts() {
        listOf("de", "en", "es", "fr", "it", "pt").forEach { code ->
            val tag = if (code == "pt") "pt-PT" else code
            val context = inLocale(tag)
            keys.forEach { key ->
                val id = R.string::class.java.getField(key).getInt(null)
                assertEquals("$code/$key", written(code, key), context.getString(id))
            }
        }
    }

    @Test
    fun theFourNewLanguagesAreNotEnglishOrFrench() {
        listOf("de", "es", "it", "pt-PT").forEach { tag ->
            val context = inLocale(tag)
            assertNotEquals(tag, written("en", "home_tagline"), context.getString(R.string.home_tagline))
            assertNotEquals(tag, written("fr", "home_tagline"), context.getString(R.string.home_tagline))
        }
    }

    @Test
    fun aRegionOfTheLanguageIsServedByTheSameTexts() {
        mapOf(
            "es-MX" to "es",
            "es-419" to "es",
            "de-AT" to "de",
            "de-CH" to "de",
            "pt-BR" to "pt",
            "pt-PT" to "pt",
            "it-CH" to "it",
        ).forEach { (tag, code) ->
            assertEquals(tag, written(code, "home_tagline"), inLocale(tag).getString(R.string.home_tagline))
        }
    }

    @Test
    fun aLanguageTheAppDoesNotHaveFallsBackToEnglish() {
        listOf("nl", "ja", "pl").forEach { tag ->
            assertEquals(tag, written("en", "home_tagline"), inLocale(tag).getString(R.string.home_tagline))
        }
    }

    @Test
    fun pluralsFollowTheRulesOfEachLanguage() {
        // profile_tab_miff_members: "%1$d member(s)". The text of "one" and of "other" differ in every language; 0 and 2 are "other", 1 is "one" (French: 0 and 1 are "one").
        val one =
            mapOf(
                "es" to "1 miembro",
                "it" to "1 membro",
                "pt-PT" to "1 membro",
                "de" to "1 Mitglied",
                "fr" to "1 membre",
                "en" to "1 member",
            )
        val other =
            mapOf(
                "es" to "5 miembros",
                "it" to "5 membri",
                "pt-PT" to "5 membros",
                "de" to "5 Mitglieder",
                "fr" to "5 membres",
                "en" to "5 members",
            )
        one.forEach { (tag, expected) ->
            val context = inLocale(tag)
            assertEquals(tag, expected, context.resources.getQuantityString(R.plurals.profile_tab_miff_members, 1, 1))
            assertEquals(
                tag,
                other.getValue(tag),
                context.resources.getQuantityString(R.plurals.profile_tab_miff_members, 5, 5),
            )
        }
        listOf("es", "it", "pt-PT", "de").forEach { tag ->
            val context = inLocale(tag)
            val zero = context.resources.getQuantityString(R.plurals.profile_tab_miff_members, 0, 0)
            assertEquals("$tag: 0 is written like the plural", other.getValue(tag).replace("5", "0"), zero)
            assertEquals(
                tag,
                other.getValue(tag).replace("5", "21"),
                context.resources.getQuantityString(R.plurals.profile_tab_miff_members, 21, 21),
            )
        }
        // The French rule keeps 0 in the singular.
        assertEquals("0 membre", inLocale("fr").resources.getQuantityString(R.plurals.profile_tab_miff_members, 0, 0))
    }
}
