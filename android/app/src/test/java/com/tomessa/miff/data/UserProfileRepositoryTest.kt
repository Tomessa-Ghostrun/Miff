package com.tomessa.miff.data

import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.data.auth.FirestoreUserProfileRepository
import com.tomessa.miff.data.auth.ProfileWriteResult
import com.tomessa.miff.data.auth.UsersTransactions
import com.tomessa.miff.domain.auth.AccountSnapshot
import com.tomessa.miff.domain.auth.ConsentVersions
import com.tomessa.miff.domain.auth.PendingConsent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * The writes of the Onboarding (review I5: M16 to M33). The Firestore operations are behind [UsersTransactions]; here a
 * fake records them, so that the checks, the time limit and the wording of errors are tested without Firestore.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileRepositoryTest {
    private class FakeTransactions : UsersTransactions {
        val calls = mutableListOf<String>()
        var created: Pair<String, Map<String, Any?>>? = null
        var phone: Pair<String, String>? = null
        var fail: Throwable? = null
        var hang = false
        var stored: Map<String, Any?>? = null

        private suspend fun act(name: String) {
            calls += name
            if (hang) awaitCancellation()
            fail?.let { throw it }
        }

        override suspend fun create(
            uid: String,
            data: Map<String, Any?>,
        ) {
            act("create")
            created = uid to data
        }

        override suspend fun updatePhone(
            uid: String,
            phone: String,
        ) {
            act("updatePhone")
            this.phone = uid to phone
        }

        override suspend fun delete(uid: String) = act("delete")

        override suspend fun read(uid: String): Map<String, Any?>? {
            act("read")
            return stored
        }
    }

    private val tx = FakeTransactions()
    private val repo = FirestoreUserProfileRepository(tx, timeoutMillis = 5_000)
    private val consent = PendingConsent("uid-1", ConsentVersions.TERMS, analyticsAccepted = true)

    private suspend fun create(
        uid: String = "uid-1",
        consent: PendingConsent = this.consent,
        phone: String = "+33612345678",
    ) = repo.create(uid, "Léa", "Martin", null, phone, consent)

    // --- Creation ---------------------------------------------------------------------------------------------------------

    @Test
    fun create_sendsTheWholeDocument_toTheRightAccount_inOneOperation() =
        runTest {
            assertEquals(ProfileWriteResult.DONE, create())
            assertEquals(listOf("create"), tx.calls)
            val (uid, data) = tx.created!!
            assertEquals("uid-1", uid)
            assertEquals("+33612345678", data["phone"])
            assertEquals(
                setOf(
                    "firstName",
                    "lastName",
                    "phone",
                    "createdAt",
                    "termsAcceptedAt",
                    "termsVersion",
                    "analyticsConsent",
                ),
                data.keys,
            )
        }

    @Test
    fun create_neverUsesTheConsentOfAnotherAccount_M17() =
        runTest {
            try {
                create(uid = "uid-2")
                fail("a consent recorded for uid-1 must not create the profile of uid-2")
            } catch (expected: IllegalArgumentException) {
                // expected
            }
            assertTrue("nothing was sent", tx.calls.isEmpty())
        }

    @Test
    fun create_refusesAPhoneTheRulesWouldRefuse_beforeAnyOperation() =
        runTest {
            listOf("0612345678", "", "+33 6 12").forEach {
                try {
                    create(phone = it)
                    fail("must refuse \"$it\"")
                } catch (expected: IllegalArgumentException) {
                    // expected
                }
            }
            assertTrue(tx.calls.isEmpty())
        }

    // --- Phone of an older account (M33) ---------------------------------------------------------------------------------------

    @Test
    fun addPhone_updatesOnlyTheNumber() =
        runTest {
            assertEquals(ProfileWriteResult.DONE, repo.addPhone("uid-1", "+33612345678"))
            assertEquals("uid-1" to "+33612345678", tx.phone)
        }

    @Test
    fun addPhone_requiresTheE164Shape_beforeAnyOperation_M33() =
        runTest {
            listOf("0612345678", "+0612345678", "06 12 34 56 78", "", "+33").forEach {
                try {
                    repo.addPhone("uid-1", it)
                    fail("must refuse \"$it\"")
                } catch (expected: IllegalArgumentException) {
                    assertFalse(
                        "the number is not repeated in the message",
                        expected.message.orEmpty().contains("0612"),
                    )
                }
            }
            assertTrue(tx.calls.isEmpty())
        }

    @Test
    fun delete_isATransactionalOperation() =
        runTest {
            assertEquals(ProfileWriteResult.DONE, repo.delete("uid-1"))
            assertEquals(listOf("delete"), tx.calls)
        }

    // --- Time limit and errors --------------------------------------------------------------------------------------------------

    @Test
    fun noAnswerInTime_isOffline_andNothingLingers() =
        runTest {
            tx.hang = true
            assertEquals(ProfileWriteResult.OFFLINE, create())
            assertEquals(ProfileWriteResult.OFFLINE, repo.addPhone("uid-1", "+33612345678"))
            assertEquals(ProfileWriteResult.OFFLINE, repo.delete("uid-1"))
        }

    @Test
    fun errorsAreWorded_withoutTheirMessage() =
        runTest {
            tx.fail = FirebaseFirestoreException("Léa +33612345678", FirebaseFirestoreException.Code.UNAVAILABLE)
            assertEquals(ProfileWriteResult.OFFLINE, create())
            tx.fail = FirebaseFirestoreException("x", FirebaseFirestoreException.Code.PERMISSION_DENIED)
            assertEquals(ProfileWriteResult.REFUSED, create())
            tx.fail = IllegalStateException("boom")
            assertEquals(ProfileWriteResult.FAILED, create())
        }

    // --- Transactions only (M24) -------------------------------------------------------------------------------------------------

    @Test
    fun everyFirestoreWriteOfTheOnboarding_isInsideATransaction_neverAPlainSet_M24() {
        val source = File("src/main/java/com/tomessa/miff/data/auth/UserProfileRepository.kt").readText()
        assertEquals("create, updatePhone and delete", 3, Regex("""\.runTransaction\s*\{""").findAll(source).count())
        val write = Regex("""\.(set|update|delete)\(""")
        val plainWrites =
            source.lines().filter { line ->
                val code = line.trim()
                val isCode = !code.startsWith("*") && !code.startsWith("//")
                val inTransaction = code.contains("transaction.") || code.contains("transactions.")
                isCode && write.containsMatchIn(code) && !inTransaction
            }
        assertTrue("a write outside a transaction would be queued offline: $plainWrites", plainWrites.isEmpty())
    }

    // --- Reading an older profile (to prefill after the deletion) ------------------------------------------------------------

    @Test
    fun read_givesNamesAndPhoto_ofAProfileThatHasThem() =
        runTest {
            tx.stored =
                mapOf("firstName" to "Léa", "lastName" to "Martin", "avatar" to ByteArray(4) { 1 }, "extra" to 5)
            val seed = repo.read("uid-1")!!
            assertEquals("Léa", seed.firstName)
            assertEquals("Martin", seed.lastName)
            assertArrayEquals(ByteArray(4) { 1 }, seed.avatarJpeg)
        }

    @Test
    fun read_givesNull_ifThereIsNoProfile_orItIsOddlyShaped_orTheReadFails() =
        runTest {
            tx.stored = null
            assertNull(repo.read("uid-1"))
            tx.stored = mapOf("firstName" to 5, "lastName" to "x")
            assertNull(repo.read("uid-1"))
            tx.stored = mapOf("firstName" to "a")
            assertNull(repo.read("uid-1"))
            tx.stored = mapOf("firstName" to "a", "lastName" to "b")
            tx.fail = FirebaseFirestoreException("x", FirebaseFirestoreException.Code.UNAVAILABLE)
            assertNull(repo.read("uid-1"))
            tx.fail = null
            tx.hang = true
            assertNull("no answer in time", repo.read("uid-1"))
        }

    @Test
    fun read_dropsAPhotoTheRulesWouldRefuse() =
        runTest {
            tx.stored = mapOf("firstName" to "a", "lastName" to "b", "avatar" to ByteArray(20_001))
            assertNull(repo.read("uid-1")!!.avatarJpeg)
            tx.stored = mapOf("firstName" to "a", "lastName" to "b", "avatar" to "not bytes")
            assertNull(repo.read("uid-1")!!.avatarJpeg)
            tx.stored = mapOf("firstName" to "a", "lastName" to "b")
            assertNotNull(repo.read("uid-1"))
        }

    // --- What the account read says (M18) ---------------------------------------------------------------------------------------------

    private fun snapshot(
        exists: Boolean = true,
        membership: Boolean = false,
        consent: Boolean = false,
        phone: Any? = null,
        terms: Any? = null,
    ) = AccountSnapshot.fromProfileFields(exists, membership, consent, phone, terms)

    @Test
    fun hasPhone_onlyForAStringOfTheE164Shape() {
        assertTrue(snapshot(phone = "+33612345678").hasPhone)
        listOf(
            null,
            "",
            "0612345678",
            "+0612345678",
            "+33 6 12 34 56 78",
            33612345678L,
            5,
            true,
            listOf("+33612345678"),
            "+3361",
        ).forEach { assertFalse("$it", snapshot(phone = it).hasPhone) }
    }

    @Test
    fun hasTermsVersion_onlyForANonEmptyString() {
        assertTrue(snapshot(terms = "0.1").hasTermsVersion)
        listOf(null, "", 1, 0.1, true).forEach { assertFalse("$it", snapshot(terms = it).hasTermsVersion) }
    }

    @Test
    fun withoutAProfile_everyFlagIsFalse_whateverTheRawValues() {
        val s = snapshot(exists = false, membership = true, consent = true, phone = "+33612345678", terms = "0.1")
        assertFalse(s.profileExists)
        assertFalse(s.hasMembership)
        assertFalse(s.analyticsConsentEnabled)
        assertFalse(s.hasPhone)
        assertFalse(s.hasTermsVersion)
    }

    @Test
    fun theOtherFlagsFollowTheProfile() {
        val s = snapshot(membership = true, consent = true, phone = "+33612345678", terms = "0.1")
        assertTrue(s.profileExists && s.hasMembership && s.analyticsConsentEnabled && s.hasPhone && s.hasTermsVersion)
    }

    // --- The draft goes with the session (M26) ----------------------------------------------------------------------------------------

    @Test
    fun theContainerRegistersTheDraftAsATeardown_andGivesItToTheSession_M26() {
        val source = File("src/main/java/com/tomessa/miff/AppContainer.kt").readText()
        // The profile kept in memory (ONB-3a) goes with the session too, next to the draft.
        assertTrue(source.contains("teardown = listOf(profileDrafts.asTeardown(), currentProfile)"))
        assertTrue(source.contains("drafts = profileDrafts"))
    }
}
