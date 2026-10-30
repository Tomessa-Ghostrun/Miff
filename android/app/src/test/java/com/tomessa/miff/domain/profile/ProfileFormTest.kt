package com.tomessa.miff.domain.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules of the first name and last name fields (Onboarding ticket 6.3, criterion PR2, cases limites 4). */
class ProfileFormTest {
    @Test
    fun aNameIsCompleteFromOneCharacter_afterRemovingTheSpacesAround() {
        assertTrue(ProfileForm.isNameComplete("A"))
        assertTrue(ProfileForm.isNameComplete("  Li  "))
        assertFalse(ProfileForm.isNameComplete(""))
        assertFalse(ProfileForm.isNameComplete("     "))
        assertFalse("tabs and new lines are spaces too", ProfileForm.isNameComplete(" \t\n "))
    }

    @Test
    fun fiftyCharactersAreAccepted_fiftyOneAreCutWithAMessage() {
        val fifty = "a".repeat(50)
        assertEquals(ProfileForm.Limited(fifty, wasCut = false), ProfileForm.limitInput(fifty))
        assertEquals(ProfileForm.Limited(fifty, wasCut = true), ProfileForm.limitInput(fifty + "b"))
        assertEquals(ProfileForm.Limited(fifty, wasCut = true), ProfileForm.limitInput("a".repeat(300)))
    }

    @Test
    fun theLimitNeverCutsAnEmojiInTwoHalves() {
        val text = "a".repeat(49) + "😀" // 49 letters + one emoji made of two UTF-16 units
        val limited = ProfileForm.limitInput(text)
        assertTrue(limited.wasCut)
        assertEquals("a".repeat(49), limited.text)
        assertTrue(limited.text.length <= 50)
    }

    @Test
    fun anyAlphabetIsAccepted() {
        listOf("Élodie-Anne", "O'Brien", "Müller", "Ñandú", "李小龍", "Анна", "محمد", "Zoë 😀").forEach {
            assertEquals(it, ProfileForm.normalizeName(it))
            assertTrue(it, ProfileForm.isNameComplete(it))
        }
    }

    @Test
    fun spacesAroundAreRemoved_andRepeatedSpacesBecomeOne() {
        assertEquals("Marie Claire", ProfileForm.normalizeName("  Marie    Claire \t"))
        assertEquals("Léa", ProfileForm.normalizeName(" Léa "))
    }

    @Test
    fun controlAndSpoofingCharactersAreRemoved() {
        // A new line would break a card; a right-to-left override could reverse the text around it.
        assertEquals("Marie Claire", ProfileForm.normalizeName("Marie\nClaire"))
        assertEquals("Eve", ProfileForm.normalizeName("E\u0000v\u0007e"))
        assertEquals("evilname", ProfileForm.normalizeName("evil‮name"))
        assertEquals("ab", ProfileForm.normalizeName("a​b‏⁦⁩﻿"))
        assertEquals("Hi", ProfileForm.limitInput("H‮i").text)
    }

    @Test
    fun aNameMadeOnlyOfInvisibleCharactersIsNotAName() {
        assertFalse(ProfileForm.isNameComplete("​‮⁦"))
    }

    @Test
    fun theCounterAppearsWhenTheLimitGetsClose() {
        assertFalse(ProfileForm.showCounter(44))
        assertTrue(ProfileForm.showCounter(45))
        assertTrue(ProfileForm.showCounter(50))
    }

    @Test
    fun theNameGivenByGoogleIsSplitIntoTwoEditableSuggestions() {
        assertEquals("Marie" to "Dupont", ProfileForm.splitDisplayName("Marie Dupont"))
        assertEquals("Marie" to "Claire Dupont", ProfileForm.splitDisplayName("Marie Claire Dupont"))
        assertEquals("Madonna" to "", ProfileForm.splitDisplayName("Madonna"))
        assertEquals("" to "", ProfileForm.splitDisplayName(null))
        assertEquals("" to "", ProfileForm.splitDisplayName("   "))
    }

    @Test
    fun theSuggestionIsCutTo50Characters() {
        val (first, last) = ProfileForm.splitDisplayName("a".repeat(80) + " " + "b".repeat(80))
        assertEquals(50, first.length)
        assertEquals(50, last.length)
    }
}
