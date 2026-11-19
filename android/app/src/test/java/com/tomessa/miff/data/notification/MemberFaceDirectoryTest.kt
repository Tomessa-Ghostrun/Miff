package com.tomessa.miff.data.notification

import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.notification.ActorFace
import com.tomessa.miff.domain.notification.FaceLookup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** CNC-A (8.5, CNC-19): the faces of the centre, in memory: free for the Miff the Feed read, one reading per other member, a dozen at the most, never in a loop. */
class MemberFaceDirectoryTest {
    private var now = 1_000_000L

    private class Reader : MemberFaceReader {
        val reads = mutableListOf<Pair<String, String>>()
        val answers = mutableMapOf<Pair<String, String>, FaceRead>()
        var default: FaceRead = FaceRead.Failed

        override suspend fun read(
            miffId: String,
            uid: String,
        ): FaceRead {
            reads += miffId to uid
            return answers[miffId to uid] ?: default
        }
    }

    private val reader = Reader()
    private val faces = MemberFaceDirectory(reader) { now }

    private fun member(
        uid: String,
        first: String = "Léa",
        last: String = "Durand",
        avatar: ByteArray? = null,
    ) = MiffMember(uid, first, last, avatar, MiffRole.MEMBER, true)

    private fun found(
        name: String,
        avatar: ByteArray? = null,
    ) = FaceRead.Found(ActorFace(name, name.take(1), avatar))

    @Test
    fun theMembersTheFeedRead_areKnownForFree_withTheirPhoto_theirInitials_andTheirFirstName() {
        val photo = ByteArray(500) { 1 }
        faces.seedFromFeed("miffAAAA", listOf(member("u1", avatar = photo), member("u2", "Max", "Petit")))
        val k1 = faces.lookup("miffAAAA", "u1") as FaceLookup.Known
        assertEquals("Léa", k1.face.firstName)
        assertEquals("LD", k1.face.initials)
        assertTrue(k1.face.avatarJpeg!!.contentEquals(photo))
        assertEquals("MP", (faces.lookup("miffAAAA", "u2") as FaceLookup.Known).face.initials)
        assertTrue(reader.reads.isEmpty())
    }

    @Test
    fun aPhotoLargerThanTheRulesAllow_isNeverKept() {
        faces.seedFromFeed("miffAAAA", listOf(member("u1", avatar = ByteArray(20_001))))
        assertNull((faces.lookup("miffAAAA", "u1") as FaceLookup.Known).face.avatarJpeg)
    }

    @Test
    fun aFirstNameIsCutLikeTheOneOfTheNotification_twentyCharactersAtMost() {
        faces.seedFromFeed("miffAAAA", listOf(member("u1", first = "A".repeat(40))))
        assertEquals(20, (faces.lookup("miffAAAA", "u1") as FaceLookup.Known).face.firstName.length)
    }

    @Test
    fun aMemberWhoIsNotInTheListTheFeedRead_isGone_withoutAnyReading() = runTest {
        faces.seedFromFeed("miffAAAA", listOf(member("u1")))
        assertSame(FaceLookup.Gone, faces.lookup("miffAAAA", "left"))
        faces.ensure(listOf("miffAAAA" to "left"))
        assertTrue(reader.reads.isEmpty())
    }

    @Test
    fun aMiffTheFeedDidNotRead_isPendingUntilItsMemberIsRead_once() = runTest {
        assertSame(FaceLookup.Pending, faces.lookup("miffBBBB", "u9"))
        reader.answers["miffBBBB" to "u9"] = found("Max")
        faces.ensure(listOf("miffBBBB" to "u9", "miffBBBB" to "u9"))
        assertEquals("Max", (faces.lookup("miffBBBB", "u9") as FaceLookup.Known).face.firstName)
        // asked again within five minutes: no new reading
        now += 4 * 60_000L
        faces.ensure(listOf("miffBBBB" to "u9"))
        assertEquals(1, reader.reads.size)
        // after five minutes: read again
        now += 2 * 60_000L
        faces.ensure(listOf("miffBBBB" to "u9"))
        assertEquals(2, reader.reads.size)
    }

    @Test
    fun aCardThatDoesNotExist_orIsRefused_isGone() = runTest {
        reader.answers["miffBBBB" to "u9"] = FaceRead.Gone
        faces.ensure(listOf("miffBBBB" to "u9"))
        assertSame(FaceLookup.Gone, faces.lookup("miffBBBB", "u9"))
    }

    @Test
    fun aFailure_isNotAskedAgainBeforeThirtySeconds_neverInALoop() = runTest {
        reader.default = FaceRead.Failed
        faces.ensure(listOf("miffBBBB" to "u9"))
        assertSame(FaceLookup.Failed, faces.lookup("miffBBBB", "u9"))
        faces.ensure(listOf("miffBBBB" to "u9"))
        assertEquals(1, reader.reads.size)
        now += 31_000L
        reader.answers["miffBBBB" to "u9"] = found("Max")
        faces.ensure(listOf("miffBBBB" to "u9"))
        assertEquals(2, reader.reads.size)
        assertTrue(faces.lookup("miffBBBB", "u9") is FaceLookup.Known)
    }

    @Test
    fun whatWasDrawnStaysDrawn_whenASecondReadingFails() = runTest {
        reader.answers["miffBBBB" to "u9"] = found("Max")
        faces.ensure(listOf("miffBBBB" to "u9"))
        now += 6 * 60_000L
        reader.answers["miffBBBB" to "u9"] = FaceRead.Failed
        faces.ensure(listOf("miffBBBB" to "u9"))
        assertTrue(faces.lookup("miffBBBB", "u9") is FaceLookup.Known)
    }

    @Test
    fun aDozenReadingsAtTheMostEachTime_theRestWaitsForTheNextOpening() = runTest {
        val wanted = (1..20).map { "miffBBBB" to "u$it" }
        reader.default = found("X")
        faces.ensure(wanted)
        assertEquals(MemberFaceDirectory.MAX_READS_PER_CALL, reader.reads.size)
        faces.ensure(wanted)
        assertEquals("the next opening reads the rest", 20, reader.reads.size)
    }

    @Test
    fun theFeedReadingTheMiffAgain_replacesWhatWasRead_andForgetsWhoLeft() = runTest {
        reader.answers["miffAAAA" to "u1"] = found("Old")
        faces.ensure(listOf("miffAAAA" to "u1"))
        faces.seedFromFeed("miffAAAA", listOf(member("u2")))
        assertSame(FaceLookup.Gone, faces.lookup("miffAAAA", "u1"))
        assertTrue(faces.lookup("miffAAAA", "u2") is FaceLookup.Known)
    }

    @Test
    fun theVersionChangesWheneverSomethingIsLearned_sotheScreenDrawsAgain() = runTest {
        val before = faces.version.value
        faces.seedFromFeed("miffAAAA", listOf(member("u1")))
        assertTrue(faces.version.value > before)
        val seeded = faces.version.value
        reader.answers["miffBBBB" to "u9"] = found("Max")
        faces.ensure(listOf("miffBBBB" to "u9"))
        assertTrue(faces.version.value > seeded)
    }

    @Test
    fun aMiffThatIsGoneTakesItsFacesWithIt_andTheEndOfTheSessionTakesAll() = runTest {
        faces.seedFromFeed("miffAAAA", listOf(member("u1")))
        faces.seedFromFeed("miffBBBB", listOf(member("u2")))
        faces.retainOnly(setOf("miffBBBB"))
        assertSame("nothing left of a Miff the account left", FaceLookup.Pending, faces.lookup("miffAAAA", "u1"))
        assertTrue(faces.lookup("miffBBBB", "u2") is FaceLookup.Known)
        faces.stop()
        assertSame(FaceLookup.Pending, faces.lookup("miffBBBB", "u2"))
    }

    @Test
    fun aReadingThatEndsAfterTheSession_isNeverKept() = runTest {
        lateinit var slow: MemberFaceDirectory
        val reader2 =
            object : MemberFaceReader {
                override suspend fun read(
                    miffId: String,
                    uid: String,
                ): FaceRead {
                    slow.stop()
                    return found("Max")
                }
            }
        slow = MemberFaceDirectory(reader2) { now }
        slow.ensure(listOf("miffBBBB" to "u9"))
        assertSame(FaceLookup.Pending, slow.lookup("miffBBBB", "u9"))
    }
}
