package com.tomessa.miff.data.settings

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.domain.settings.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The language applied to the system (ticket I18N-4L, review of the lot 3): Portuguese is applied as `pt-PT` (plural rules of Portugal, "0 dias"), and a language whose
 * texts are not in the build is never applied (dates and numbers would follow it while the texts are English).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LanguageStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    private val appliedTags: String
        get() = context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()

    @Test
    fun portugueseIsAppliedToTheSystemAsPtPt_andReadBackAsPortuguese() {
        val store = AndroidLanguageStore(context, provided = setOf("en", "fr", "pt"))
        store.select(AppLanguage.PORTUGUESE)
        assertEquals("pt-PT", appliedTags)
        assertEquals(AppLanguage.PORTUGUESE, store.selected.value)
        assertEquals(AppLanguage.PORTUGUESE, AndroidLanguageStore.read(context, setOf("en", "fr", "pt")))
    }

    @Test
    fun otherLanguagesAreAppliedWithTheirOwnTag_andThePhoneEmptiesTheChoice() {
        val store = AndroidLanguageStore(context, provided = setOf("en", "fr", "es", "it", "de", "pt"))
        mapOf(
            AppLanguage.FRENCH to "fr",
            AppLanguage.SPANISH to "es",
            AppLanguage.ITALIAN to "it",
            AppLanguage.GERMAN to "de",
        ).forEach { (language, tag) ->
            store.select(language)
            assertEquals(tag, appliedTags)
            assertEquals(language, store.selected.value)
        }
        store.select(AppLanguage.SYSTEM)
        assertEquals("", appliedTags)
        assertEquals(AppLanguage.SYSTEM, store.selected.value)
    }

    @Test
    fun aLanguageWithoutItsTextsInTheBuildIsNotApplied() {
        val store = AndroidLanguageStore(context, provided = setOf("en", "fr"))
        store.select(AppLanguage.FRENCH)
        store.select(AppLanguage.SPANISH)
        assertEquals("", appliedTags)
        assertEquals(AppLanguage.SYSTEM, store.selected.value)
    }

    private fun contextIn(tag: String): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(tag))
        return context.createConfigurationContext(configuration)
    }

    /**
     * Criterion 19 of the ticket: zero is `other` in Portugal ("0 dias"), `one` in Brazil ("0 dia"). Read through the real plurals of the app (their English texts: "minute" /
     * "minutes") with the locale the store applies: 0 must read like 2, never like 1.
     */
    @Test
    fun theLocaleAppliedForPortuguese_readsZeroAsOther_likeTheTextsOfPortugalExpect() {
        val tag = checkNotNull(AppLanguage.PORTUGUESE.localeTag)
        val plural = R.plurals.countdown_a11y_minutes
        val resources = contextIn(tag).resources
        val other = resources.getQuantityString(plural, 2, 0)
        assertEquals(other, resources.getQuantityString(plural, 0, 0))
        assertNotEquals(other, resources.getQuantityString(plural, 1, 0))
        // The bare `pt` would have been Brazilian: zero reads like one ("0 dia"). This is why the tag applied is pt-PT.
        val brazil = contextIn("pt-BR").resources
        assertEquals(brazil.getQuantityString(plural, 1, 0), brazil.getQuantityString(plural, 0, 0))
    }
}
