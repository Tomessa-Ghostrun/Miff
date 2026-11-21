package com.tomessa.miff.data.subscription

import com.google.firebase.Timestamp
import com.tomessa.miff.domain.subscription.EntitlementDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

/**
 * `entitlements/{uid}` is written by the SERVER (a real purchase) or typed BY HAND in the console: only the exact types count, anything else reads as a missing field and never
 * throws (the account then stays on the free plan, like the security rules, which compare `active == true` and `expiresAt > request.time`). A document with fields this version
 * does not know, or without the fields of the purchase (the first documents of the team), never breaks the screen (criterion AN7).
 */
class EntitlementFromTest {
    private val expiry = Timestamp(Date(1_800_000_000_000L))

    private fun read(
        active: Any? = true,
        expiresAt: Any? = expiry,
        source: Any? = "manual",
    ) = entitlementFrom(mapOf("active" to active, "expiresAt" to expiresAt, "source" to source))

    @Test
    fun noDocument_isNull() {
        assertNull(entitlementFrom(null))
    }

    @Test
    fun aWellFormedOldDocument_isRead_unknownFieldsIgnored() {
        assertEquals(
            EntitlementDocument(active = true, expiresAtMillis = 1_800_000_000_000L, source = "manual"),
            entitlementFrom(
                mapOf(
                    "active" to true,
                    "expiresAt" to expiry,
                    "source" to "manual",
                    "updatedAt" to expiry,
                    "somethingNew" to 12,
                ),
            ),
        )
    }

    @Test
    fun aDocumentOfAPurchase_isReadWithAllItsFields() {
        assertEquals(
            EntitlementDocument(
                active = true,
                expiresAtMillis = 1_800_000_000_000L,
                source = "google_play",
                state = "trial",
                plan = "yearly",
                trial = true,
                willRenew = true,
                test = false,
            ),
            entitlementFrom(
                mapOf(
                    "active" to true,
                    "expiresAt" to expiry,
                    "source" to "google_play",
                    "updatedAt" to expiry,
                    "state" to "trial",
                    "plan" to "yearly",
                    "trial" to true,
                    "willRenew" to true,
                    "test" to false,
                    "purgeAt" to expiry,
                ),
            ),
        )
    }

    @Test
    fun theNewFieldsWithTheWrongType_readAsMissing_neverAnError() {
        val read =
            entitlementFrom(
                mapOf(
                    "active" to true,
                    "expiresAt" to expiry,
                    "source" to "google_play",
                    "state" to 3,
                    "plan" to true,
                    "trial" to "true",
                    "willRenew" to 1,
                    "test" to "yes",
                ),
            )!!
        assertNull(read.state)
        assertNull(read.plan)
        assertNull(read.trial)
        assertNull(read.willRenew)
        assertNull(read.test)
        assertEquals(true, read.active)
    }

    @Test
    fun activeAsTheText_true_isNotABoolean() {
        assertNull(read(active = "true")?.active)
    }

    @Test
    fun expiresAtAsATextDate_isNotATimestamp() {
        assertNull(read(expiresAt = "2027-01-12")?.expiresAtMillis)
    }

    @Test
    fun expiresAtAsAnInteger_orADouble_isNotATimestamp() {
        assertNull(read(expiresAt = 1_800_000_000_000L)?.expiresAtMillis)
        assertNull(read(expiresAt = 1_800_000_000)?.expiresAtMillis)
        assertNull(read(expiresAt = 1.8e12)?.expiresAtMillis)
    }

    @Test
    fun aMissingField_readsAsNull_theOthersAreKept() {
        val noExpiry = entitlementFrom(mapOf("active" to true, "source" to "manual"))
        assertEquals(EntitlementDocument(active = true, expiresAtMillis = null, source = "manual"), noExpiry)
        val noActive = entitlementFrom(mapOf("expiresAt" to expiry))
        assertEquals(EntitlementDocument(active = null, expiresAtMillis = 1_800_000_000_000L, source = null), noActive)
        assertEquals(EntitlementDocument(null, null, null), entitlementFrom(emptyMap()))
    }

    @Test
    fun aSourceThatIsNotAString_isNull() {
        assertNull(read(source = 1)?.source)
    }

    @Test
    fun aMisspeltFieldName_isAMissingField() {
        // Seen on 2026-11-07: "expiresAT" instead of "expiresAt" (docs/dev/premium-manuel.md): the account stays free.
        val misspelt = entitlementFrom(mapOf("active" to true, "expiresAT" to expiry, "source" to "manual"))
        assertNull(misspelt?.expiresAtMillis)
    }
}
