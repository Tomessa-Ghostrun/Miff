package com.tomessa.miff.domain

import com.tomessa.miff.domain.navigation.MainTab
import com.tomessa.miff.domain.navigation.TabBarConfig
import com.tomessa.miff.domain.profile.Initials
import com.tomessa.miff.domain.profile.MyProfile
import com.tomessa.miff.domain.settings.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure rules of ticket ONB-3a: the tab bar, the languages, the initials, the profile model. */
class SettingsDomainTest {
    // --- Tab bar (decision Q1, criteria A1 and TB5) --------------------------------------------------------------------

    @Test
    fun anInternalBuildShowsTheCentralButton_butItCannotBeOpened() {
        val bar = TabBarConfig.forBuild(isInternalBuild = true)
        assertEquals(listOf(MainTab.FEED, MainTab.UPDATE, MainTab.PROFILE), bar.tabs)
        assertTrue(bar.isEnabled(MainTab.FEED))
        assertTrue(bar.isEnabled(MainTab.PROFILE))
        assertFalse("nothing to open until the UPDATE ticket exists", bar.isEnabled(MainTab.UPDATE))
    }

    @Test
    fun aBuildForOutsideTestersHasTwoTabsAndNoCentralButton() {
        val bar = TabBarConfig.forBuild(isInternalBuild = false)
        assertEquals(listOf(MainTab.FEED, MainTab.PROFILE), bar.tabs)
    }

    // --- Languages (decision Q4, criterion A9) -------------------------------------------------------------------------

    @Test
    fun onlyFrenchEnglishAndThePhoneAreOffered_inThisOrder() {
        assertEquals(listOf(AppLanguage.FRENCH, AppLanguage.ENGLISH, AppLanguage.SYSTEM), AppLanguage.entries.toList())
        assertEquals(listOf("fr", "en", null), AppLanguage.entries.map { it.tag })
    }

    @Test
    fun aTagGivenBackBySystemIsReducedToItsLanguage() {
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromTag("fr"))
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromTag("fr-FR"))
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromTag("fr_CA"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-GB"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag(" EN "))
    }

    @Test
    fun anythingElseMeansThePhoneDecides() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("es-ES"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("not a tag at all"))
    }

    // --- Initials ---------------------------------------------------------------------------------------------------------

    @Test
    fun initialsAreTheFirstLettersInCapitals() {
        assertEquals("LM", Initials.of("léa", "martin"))
        assertEquals("ÉB", Initials.of("  émile", "Bernard"))
        assertEquals("AK", Initials.of("Amandine", "Kouajou"))
    }

    @Test
    fun aLetterOutsideTheBasicPlaneOrAnEmojiIsNeverCutInTwo() {
        val emoji = String(Character.toChars(0x1F600))
        assertEquals(emoji + "T", Initials.of(emoji, "Thomas"))
        val rare = String(Character.toChars(0x10400)) // Deseret capital letter, outside the basic plane
        assertEquals(1, Initials.of(rare, "").codePointCount(0, Initials.of(rare, "").length))
    }

    @Test
    fun withoutAnyNameThereIsAPlaceholderRatherThanAnEmptyDisc() {
        assertEquals("?", Initials.of("", "  "))
    }

    // --- The profile model: personal data stays out of logs ------------------------------------------------------------------

    @Test
    fun theProfileNeverShowsItsContentInATextForm() {
        val profile = MyProfile("Léa", "Martin", "+33612345678", ByteArray(3))
        val text = profile.toString()
        assertFalse("Léa" in text || "Martin" in text || "612345678" in text)
    }

    @Test
    fun twoProfilesAreEqualWhenTheirContentIsTheSame_photoIncluded() {
        val a = MyProfile("Léa", "Martin", "+33612345678", byteArrayOf(1, 2, 3))
        val b = MyProfile("Léa", "Martin", "+33612345678", byteArrayOf(1, 2, 3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, MyProfile("Léa", "Martin", "+33612345678", byteArrayOf(1, 2, 4)))
        assertNotEquals(a, MyProfile("Léa", "Martin", "+33612345678", null))
        assertNotEquals(a, MyProfile("Léa", "Martin", "+33612345679", byteArrayOf(1, 2, 3)))
        assertEquals("Léa Martin", a.displayName)
    }
}
