package com.tomessa.miff.data

import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.tomessa.miff.data.profile.FirestoreMyProfileRepository
import com.tomessa.miff.data.profile.MyProfileLoad
import com.tomessa.miff.data.profile.ProfileSaveResult
import com.tomessa.miff.domain.profile.MyProfile
import com.tomessa.miff.testsupport.FakeMyProfileStore
import com.tomessa.miff.testsupport.firestoreError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Writing of "Mes informations" (ticket ONB-3a, criterion A7): the profile and the copy of each Miff in ONE confirmed
 * write; the stale-Miff fallback; what a missing network leaves; nothing from the server is trusted as a path.
 */
class MyProfileRepositoryTest {
    private val store = FakeMyProfileStore()
    private val repo = FirestoreMyProfileRepository(store, timeoutMillis = 1_000)
    private val profile = MyProfile("Léa", "Martin", "+33612345678", null)

    private fun miffId(n: Int) = "Miff" + n.toString().padStart(16, '0')

    @Test
    fun withoutAMiff_theProfileAloneIsWrittenInOneTransaction() =
        runTest {
            assertEquals(ProfileSaveResult.SAVED, repo.save("uid-1", profile))
            assertEquals(1, store.commits.size)
            val commit = store.commits.single()
            assertTrue(commit.writeProfile)
            assertTrue(commit.miffIds.isEmpty())
            assertEquals(setOf("firstName", "lastName", "phone", "avatar"), commit.data.keys)
            assertEquals("+33612345678", commit.data["phone"])
        }

    @Test
    fun theProfileAndTheCopyOfEachMiffGoInTheSameTransaction() =
        runTest {
            store.memberships = listOf(miffId(1), miffId(2))
            assertEquals(ProfileSaveResult.SAVED, repo.save("uid-1", profile))
            assertEquals(1, store.commits.size)
            assertTrue(store.commits.single().writeProfile)
            assertEquals(listOf(miffId(1), miffId(2)), store.commits.single().miffIds)
        }

    @Test
    fun beyondFifteenMiffs_theCopiesFollowByGroupsOfFifteen_theProfileOnlyOnce() =
        runTest {
            store.memberships = (1..31).map(::miffId)
            assertEquals(ProfileSaveResult.SAVED, repo.save("uid-1", profile))
            assertEquals(listOf(15, 15, 1), store.commits.map { it.miffIds.size })
            assertEquals(listOf(true, false, false), store.commits.map { it.writeProfile })
        }

    @Test
    fun aStaleMiffNeverPreventsChangingOneOwnName() =
        runTest {
            store.memberships = listOf(miffId(1), miffId(2))
            // The Miff 2 no longer exists: every transaction that touches its copy is refused.
            store.commitBehaviour = { if (miffId(2) in it.miffIds) firestoreError(Code.NOT_FOUND) else null }
            assertEquals(ProfileSaveResult.SAVED, repo.save("uid-1", profile))
            // First the whole transaction (refused), then the profile alone, then each copy on its own.
            assertEquals(
                listOf(true to 2, true to 0, false to 1, false to 1),
                store.commits.map { it.writeProfile to it.miffIds.size },
            )
        }

    @Test
    fun aCopyThatCannotBeReachedForWantOfNetworkIsLeftPending_theProfileIsSaved() =
        runTest {
            store.memberships = listOf(miffId(1), miffId(2))
            store.commitBehaviour = {
                when {
                    it.writeProfile && it.miffIds.size == 2 -> firestoreError(Code.PERMISSION_DENIED)
                    miffId(2) in it.miffIds -> firestoreError(Code.UNAVAILABLE)
                    else -> null
                }
            }
            assertEquals(ProfileSaveResult.SAVED_PARTIAL, repo.save("uid-1", profile))
        }

    @Test
    fun withoutNetworkNothingIsWritten_andNothingIsAskedTwice() =
        runTest {
            store.membershipsFailure = firestoreError(Code.UNAVAILABLE)
            assertEquals(ProfileSaveResult.OFFLINE, repo.save("uid-1", profile))
            assertTrue(store.commits.isEmpty())

            store.membershipsFailure = null
            store.memberships = listOf(miffId(1))
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.UNAVAILABLE)))
            assertEquals(ProfileSaveResult.OFFLINE, repo.save("uid-1", profile))
            assertEquals("no fallback when the network is the problem", 1, store.commits.size)
        }

    @Test
    fun aRefusedProfileIsReportedAsRefused() =
        runTest {
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.PERMISSION_DENIED)))
            assertEquals(ProfileSaveResult.REFUSED, repo.save("uid-1", profile))

            store.commitFailures = ArrayDeque(listOf(IllegalStateException("anything")))
            assertEquals(ProfileSaveResult.FAILED, repo.save("uid-1", profile))
        }

    @Test
    fun whenEvenTheProfileAloneIsRefused_theCopiesAreNotTried() =
        runTest {
            store.memberships = listOf(miffId(1))
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.PERMISSION_DENIED)))
            assertEquals(ProfileSaveResult.REFUSED, repo.save("uid-1", profile))
            assertEquals(listOf(1, 0), store.commits.map { it.miffIds.size })
        }

    @Test
    fun anIdentifierFromTheServerThatIsNotAMiffIdentifierNeverBecomesAPath() =
        runTest {
            store.memberships = listOf("../users/other", "free", miffId(1), miffId(1), "x".repeat(21), "")
            assertEquals(ProfileSaveResult.SAVED, repo.save("uid-1", profile))
            assertEquals(listOf(miffId(1)), store.commits.single().miffIds)
            assertTrue(FirestoreMyProfileRepository.isMiffId("A".repeat(20)))
            assertFalse(FirestoreMyProfileRepository.isMiffId("A".repeat(19)))
            assertFalse(FirestoreMyProfileRepository.isMiffId("A".repeat(19) + "/"))
        }

    @Test
    fun aValueThatTheRulesWouldRefuseIsNeverSent_andTheErrorQuotesNothing() =
        runTest {
            val bad = MyProfile("Léa", "Martin", "0612345678", null)
            try {
                repo.save("uid-1", bad)
                fail("a number that is not E.164 must be refused before any write")
            } catch (e: IllegalArgumentException) {
                assertFalse("the number is never put in a message", "0612345678" in e.message.orEmpty())
            }
            assertTrue(store.commits.isEmpty())
            val noName = runCatching { repo.save("uid-1", MyProfile(" ", "Martin", "+33612345678", null)) }
            assertTrue(noName.isFailure)
            val bigPhoto =
                runCatching { repo.save("uid-1", MyProfile("Léa", "Martin", "+33612345678", ByteArray(20_001))) }
            assertTrue(bigPhoto.isFailure)
            assertTrue(store.commits.isEmpty())
        }

    // --- Reading -----------------------------------------------------------------------------------------------------

    @Test
    fun aProfileIsReadWithItsNumberAndPhoto() =
        runTest {
            store.fields =
                mapOf(
                    "firstName" to "Léa",
                    "lastName" to "Martin",
                    "phone" to "+33612345678",
                    "avatar" to ByteArray(10),
                )
            val loaded = (repo.read("uid-1") as MyProfileLoad.Loaded).profile
            assertEquals("Léa Martin", loaded.displayName)
            assertEquals("+33612345678", loaded.phone)
            assertEquals(10, loaded.avatarJpeg?.size)
        }

    @Test
    fun anOlderProfileWithoutANumber_orWithAnOversizedPhoto_isReadWithoutThem() =
        runTest {
            store.fields = mapOf("firstName" to "Léa", "lastName" to "Martin", "avatar" to ByteArray(20_001))
            val loaded = (repo.read("uid-1") as MyProfileLoad.Loaded).profile
            assertNull(loaded.phone)
            assertNull(loaded.avatarJpeg)
            store.fields = mapOf("firstName" to "Léa", "lastName" to "Martin", "phone" to "not a number")
            assertNull((repo.read("uid-1") as MyProfileLoad.Loaded).profile.phone)
        }

    @Test
    fun aMissingOrUnreadableProfileIsSimplyUnavailable() =
        runTest {
            store.fields = null
            assertEquals(MyProfileLoad.Unavailable, repo.read("uid-1"))
            store.fields = mapOf("firstName" to 12, "lastName" to "Martin")
            assertEquals(MyProfileLoad.Unavailable, repo.read("uid-1"))
            assertNotNull(MyProfileLoad.Unavailable)
        }
}
