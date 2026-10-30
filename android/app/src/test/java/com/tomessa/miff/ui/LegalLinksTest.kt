package com.tomessa.miff.ui

import com.tomessa.miff.BuildConfig
import com.tomessa.miff.domain.auth.legalPageUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The addresses of the legal pages are kept in ONE place (base address in `BuildConfig`, one path per language in the
 * resources) and must be those of the web pages of AUTH-W (`web/src/pages.json`): if a page moves, this test fails instead of
 * a link breaking silently in the app (ticket ONB-3b, criterion B5). The pages are not deployed yet: the links lead to the
 * address where they will be.
 */
class LegalLinksTest {
    private fun resource(
        folder: String,
        name: String,
    ): String {
        val xml = File("src/main/res/$folder/strings.xml").readText()
        return Regex("""<string name="$name"[^>]*>([^<]*)</string>""").find(xml)!!.groupValues[1]
    }

    private fun pageUrl(
        id: String,
        language: String,
    ): String {
        val json = File("../../web/src/pages.json").readText()
        val block = Regex(""""id":\s*"$id"[\s\S]*?"$language":\s*\{\s*"url":\s*"([^"]+)"""").find(json)!!
        return block.groupValues[1]
    }

    @Test
    fun theTermsAndPrivacyPathsAreThoseOfTheWebPages_inBothLanguages() {
        assertEquals(pageUrl("terms", "fr"), resource("values-fr", "android_legal_terms_path"))
        assertEquals(pageUrl("terms", "en"), resource("values", "android_legal_terms_path"))
        assertEquals(pageUrl("privacy", "fr"), resource("values-fr", "android_legal_privacy_path"))
        assertEquals(pageUrl("privacy", "en"), resource("values", "android_legal_privacy_path"))
    }

    @Test
    fun theBaseAddressIsHttpsOnly_andTheJoinNeverDoublesOrLosesASlash() {
        assertTrue(BuildConfig.LEGAL_BASE_URL.startsWith("https://"))
        assertEquals("https://example.org/fr/cgu", legalPageUrl("https://example.org/", "/fr/cgu"))
        assertEquals("https://example.org/fr/cgu", legalPageUrl("https://example.org", "fr/cgu"))
    }

    @Test
    fun theSupportAddressIsTheProvisionalContactOfTheProject() {
        // CLAUDE.md, decision of 2026-10-24: provisional, replaceable by -Pmiff.supportEmail.
        assertTrue(BuildConfig.SUPPORT_EMAIL.contains("@"))
    }
}
