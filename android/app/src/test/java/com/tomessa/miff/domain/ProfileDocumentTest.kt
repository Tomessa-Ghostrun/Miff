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

/**
 * The shape of the ONE write that creates users/{uid} (backend/data-model.md section 8, rules AUTH-B and ONB-B).
 * Since ONB-B the phone number is REQUIRED by the rules (E.164, backend/firestore.rules: `hasAll` contains `phone`):
 * a document without it is refused by the server, so the shape here must carry it (review finding I1 of AUTH-1).
 */
class ProfileDocumentTest {
    private val phone = "+33612345678"

    private val accepted = PendingConsent("uid-1", ConsentVersions.TERMS, analyticsAccepted = true)
    private val declined = PendingConsent("uid-1", ConsentVersions.TERMS, analyticsAccepted = false)

    @Suppress("UNCHECKED_CAST")
    private fun consentOf(consent: PendingConsent) =
        ProfileDocument.forCreation("Léa", "Martin", null, phone, consent)["analyticsConsent"] as Map<String, Any?>

    @Test
    fun hasExactlyTheKeysTheRulesAccept_andNothingElse() {
        // Rules `create`: hasOnly(firstName, lastName, avatar, phone, createdAt, termsAcceptedAt, termsVersion,
        // analyticsConsent) and hasAll(firstName, lastName, phone, createdAt, termsAcceptedAt, termsVersion).
        val doc = ProfileDocument.forCreation("Léa", "Martin", null, phone, declined)
        assertEquals(
            setOf("firstName", "lastName", "phone", "createdAt", "termsAcceptedAt", "termsVersion", "analyticsConsent"),
            doc.keys,
        )
        listOf("email", "age", "birthDate", "country", "region", "password").forEach {
            assertFalse(it, doc.containsKey(it))
        }
    }

    @Test
    fun withAPhoto_theEightKeysOfTheRulesAreThere() {
        val doc = ProfileDocument.forCreation("Léa", "Martin", ByteArray(10), phone, accepted)
        assertEquals(
            setOf(
                "firstName",
                "lastName",
                "avatar",
                "phone",
                "createdAt",
                "termsAcceptedAt",
                "termsVersion",
                "analyticsConsent",
            ),
            doc.keys,
        )
    }

    @Test
    fun phoneIsWrittenAsGiven_inE164_neverInANationalForm() {
        assertEquals(phone, ProfileDocument.forCreation("Léa", "Martin", null, phone, accepted)["phone"])
    }

    @Test
    fun aPhoneThatTheRulesWouldRefuse_isRefusedBeforeAnyWrite() {
        listOf(
            "",
            "0612345678",
            "+0612345678",
            "+33 6 12 34 56 78",
            "+336123",
            "+336123456789012345",
            "33612345678",
            "+33abc",
        ).forEach {
            try {
                ProfileDocument.forCreation("Léa", "Martin", null, it, accepted)
                fail("must refuse the phone \"$it\"")
            } catch (expected: IllegalArgumentException) {
                // The message never repeats the number.
                assertFalse(expected.message.orEmpty().contains(it) && it.isNotEmpty())
            }
        }
    }

    @Test
    fun e164ShapeIsTheOneOfTheRules() {
        // backend/firestore.rules isPhone: ^[+][1-9][0-9]{6,14}$
        assertEquals("^[+][1-9][0-9]{6,14}$", ProfileDocument.E164.pattern)
        assertTrue(ProfileDocument.E164.matches("+1234567"))
        assertTrue(ProfileDocument.E164.matches("+123456789012345"))
        assertFalse(ProfileDocument.E164.matches("+12345"))
        assertFalse(ProfileDocument.E164.matches("+1234567890123456"))
    }

    @Test
    fun datesAreServerTimestamps_neverTheClockOfThePhone() {
        val doc = ProfileDocument.forCreation("Léa", "Martin", null, phone, accepted)
        assertTrue(doc["createdAt"] is FieldValue)
        assertTrue(doc["termsAcceptedAt"] is FieldValue)
        assertTrue(consentOf(accepted)["consentedAt"] is FieldValue)
    }

    @Test
    fun termsVersionIsTheOneShownAtSignUp() {
        val doc = ProfileDocument.forCreation("Léa", "Martin", null, phone, accepted.copy(termsVersion = "0.7"))
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
        assertEquals("Léa", ProfileDocument.forCreation("  Léa ", "Martin", null, phone, declined)["firstName"])
        listOf("", "   ", "x".repeat(51)).forEach { bad ->
            try {
                ProfileDocument.forCreation(bad, "Martin", null, phone, declined)
                fail("must refuse a name of length ${bad.length}")
            } catch (expected: IllegalArgumentException) {
                // expected
            }
        }
        ProfileDocument.forCreation("x".repeat(50), "Martin", null, phone, declined)
    }

    @Test
    fun avatar_isOptional_andBounded() {
        assertTrue(ProfileDocument.forCreation("Léa", "Martin", ByteArray(20_000), phone, declined)["avatar"] is Blob)
        try {
            ProfileDocument.forCreation("Léa", "Martin", ByteArray(20_001), phone, declined)
            fail("too big")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
