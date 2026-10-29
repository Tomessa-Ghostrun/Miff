package com.tomessa.miff.domain

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.tomessa.miff.domain.auth.ConsentVersions
import com.tomessa.miff.domain.auth.PendingConsent
import com.tomessa.miff.domain.auth.ProfileDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The shape of the ONE write that creates users/{uid} (backend/data-model.md section 8, rules AUTH-B). */
class ProfileDocumentTest {
    private val accepted = PendingConsent("uid-1", ConsentVersions.TERMS, analyticsAccepted = true)
    private val declined = PendingConsent("uid-1", ConsentVersions.TERMS, analyticsAccepted = false)

    @Suppress("UNCHECKED_CAST")
    private fun consentOf(consent: PendingConsent) =
        ProfileDocument.forCreation("Léa", "Martin", null, consent)["analyticsConsent"] as Map<String, Any?>

    @Test
    fun hasExactlyTheKeysTheRulesAccept_andNothingElse() {
        val doc = ProfileDocument.forCreation("Léa", "Martin", null, declined)
        assertEquals(
            setOf("firstName", "lastName", "createdAt", "termsAcceptedAt", "termsVersion", "analyticsConsent"),
            doc.keys,
        )
        listOf("email", "age", "birthDate", "phone", "password").forEach { assertFalse(it, doc.containsKey(it)) }
    }

    @Test
    fun datesAreServerTimestamps_neverTheClockOfThePhone() {
        val doc = ProfileDocument.forCreation("Léa", "Martin", null, accepted)
        assertTrue(doc["createdAt"] is FieldValue)
        assertTrue(doc["termsAcceptedAt"] is FieldValue)
        assertTrue(consentOf(accepted)["consentedAt"] is FieldValue)
    }

    @Test
    fun termsVersionIsTheOneShownAtSignUp() {
        val doc = ProfileDocument.forCreation("Léa", "Martin", null, accepted.copy(termsVersion = "0.7"))
        assertEquals("0.7", doc["termsVersion"])
    }

    @Test
    fun analyticsConsent_isAlwaysWritten_withAllFourKeys_boxBTicked() {
        val consent = consentOf(accepted)
        assertEquals(setOf("enabled", "consentedAt", "consentVersion", "revokedAt"), consent.keys)
        assertEquals(true, consent["enabled"])
        assertEquals(ConsentVersions.ANALYTICS, consent["consentVersion"])
        assertNull(consent["revokedAt"])
    }

    @Test
    fun analyticsConsent_isAlwaysWritten_withAllFourKeys_boxBUnticked() {
        val consent = consentOf(declined)
        assertEquals(setOf("enabled", "consentedAt", "consentVersion", "revokedAt"), consent.keys)
        assertEquals(false, consent["enabled"])
        assertNull(consent["consentedAt"])
        assertNull(consent["consentVersion"])
        assertNull(consent["revokedAt"])
    }

    @Test
    fun consentVersionFormatMatchesTheRules() {
        assertTrue(Regex("""\d{4}-\d{2}-v\d{2}""").matches(ConsentVersions.ANALYTICS))
        assertTrue(ConsentVersions.TERMS.length in 1..20)
    }

    @Test
    fun namesAreTrimmed_andBoundedLikeTheRules() {
        assertEquals("Léa", ProfileDocument.forCreation("  Léa ", "Martin", null, declined)["firstName"])
        listOf("", "   ", "x".repeat(51)).forEach { bad ->
            try {
                ProfileDocument.forCreation(bad, "Martin", null, declined)
                fail("must refuse a name of length ${bad.length}")
            } catch (expected: IllegalArgumentException) {
                // expected
            }
        }
        ProfileDocument.forCreation("x".repeat(50), "Martin", null, declined)
    }

    @Test
    fun avatar_isOptional_andBounded() {
        assertTrue(ProfileDocument.forCreation("Léa", "Martin", ByteArray(20_000), declined)["avatar"] is Blob)
        try {
            ProfileDocument.forCreation("Léa", "Martin", ByteArray(20_001), declined)
            fail("too big")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
