package com.tomessa.miff.data.feed

import com.tomessa.miff.domain.update.PhotoLocation
import com.tomessa.miff.domain.update.SharedPhoto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sentence "Partage ta position pour voir cette photo." (bug reported by the PM, 2026-11-12): it must only appear when it is KNOWN that I have not published
 * my own position AND the rules refused a photo. A photo that is absent, expired, still loading or refused for another reason shows nothing.
 */
class PhotoGateTest {
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    private fun photo() =
        SharedPhoto(
            ByteArray(10) { 1 },
            now - hour,
            now + 22 * hour,
            PhotoLocation(
                48.0,
                2.0,
                now - hour,
            ),
        )

    private class FakeSource(
        var answer: PhotoFetch,
    ) : PhotoSource {
        var fetches = 0

        override suspend fun fetch(
            miffId: String,
            uid: String,
        ): PhotoFetch {
            fetches++
            return answer
        }
    }

    private class FakeProbe(
        var answer: Boolean?,
    ) : OwnPositionProbe {
        var reads = 0

        override suspend fun exists(miffId: String): Boolean? {
            reads++
            return answer
        }
    }

    private fun resolver(
        probe: OwnPositionProbe,
        local: Boolean = false,
        uid: String? = "me",
        clock: () -> Long = { now },
    ) = OwnPositionResolver(myUid = { uid }, localPublished = { local }, probe = probe, nowMillis = clock)

    private fun reader(
        source: PhotoSource,
        resolver: OwnPositionResolver,
        cache: PhotoCache = PhotoCache(),
    ) = CachedPhotoReader(cache, source, resolver::state, { now })

    // --- Sharing active (my position is published on the server) ---------------------------------------------------------------------------

    @Test
    fun myPositionPublished_andAbsentPhoto_showsNoSentence() =
        runTest {
            val found = reader(FakeSource(PhotoFetch.Absent), resolver(FakeProbe(true))).read("m", "semi", 1L)
            assertEquals(PhotoLookup.Missing, found)
        }

    @Test
    fun myPositionPublished_andPhotoRefusedBecauseExpired_showsNoSentence_evenWhenTheLocalNoteIsEmpty() =
        runTest {
            // The case of the PM: the photo of the other is refused (expired), my position IS on the server, the local note of the detection says nothing.
            val probe = FakeProbe(true)
            val found = reader(FakeSource(PhotoFetch.Denied), resolver(probe, local = false)).read("m", "semi", 1L)
            assertEquals(PhotoLookup.Missing, found)
            assertEquals(1, probe.reads)
        }

    @Test
    fun myPositionPublishedAccordingToTheLocalNote_neverReadsTheServerForIt() =
        runTest {
            val probe = FakeProbe(false)
            val found = reader(FakeSource(PhotoFetch.Denied), resolver(probe, local = true)).read("m", "semi", 1L)
            assertEquals(PhotoLookup.Missing, found)
            assertEquals(0, probe.reads)
        }

    @Test
    fun myPositionPublished_andPhotoPresent_showsThePhoto() =
        runTest {
            val found = reader(FakeSource(PhotoFetch.Valid(photo())), resolver(FakeProbe(true))).read("m", "semi", 1L)
            assertTrue(found is PhotoLookup.Found)
        }

    // --- Sharing not active (my position is not published) ---------------------------------------------------------------------------------

    @Test
    fun myPositionNotPublished_andPhotoRefused_showsTheSentence() =
        runTest {
            val found = reader(FakeSource(PhotoFetch.Denied), resolver(FakeProbe(false))).read("m", "semi", 1L)
            assertEquals(PhotoLookup.NeedsOwnPosition, found)
        }

    @Test
    fun myPositionNotPublished_andAbsentPhoto_stillShowsNoSentence_thereIsNothingToLock() =
        runTest {
            val probe = FakeProbe(false)
            val found = reader(FakeSource(PhotoFetch.Absent), resolver(probe)).read("m", "semi", 1L)
            assertEquals(PhotoLookup.Missing, found)
            assertEquals("an absent photo is not even a reason to look at my position", 0, probe.reads)
        }

    // --- Loading / not known: never the sentence --------------------------------------------------------------------------------------------

    @Test
    fun myPositionNotKnown_offlineOrRefusedOrNotSignedIn_neverShowsTheSentence() =
        runTest {
            val denied = FakeSource(PhotoFetch.Denied)
            assertEquals(PhotoLookup.Missing, reader(denied, resolver(FakeProbe(null))).read("m", "semi", 1L))
            assertEquals(
                PhotoLookup.Missing,
                reader(denied, resolver(FakeProbe(false), uid = null)).read("m", "semi", 1L),
            )
        }

    @Test
    fun refusalLookup_isTheSentenceOnlyForNotPublished() {
        assertEquals(PhotoLookup.NeedsOwnPosition, refusalLookup(OwnPosition.NotPublished))
        assertEquals(PhotoLookup.Missing, refusalLookup(OwnPosition.Published))
        assertEquals(PhotoLookup.Missing, refusalLookup(OwnPosition.Unknown))
    }

    @Test
    fun otherFailures_neverShowTheSentence() =
        runTest {
            val probe = FakeProbe(false)
            assertEquals(
                PhotoLookup.Missing,
                reader(FakeSource(PhotoFetch.Failed), resolver(probe)).read("m", "semi", 1L),
            )
            assertEquals(
                PhotoLookup.Missing,
                reader(FakeSource(PhotoFetch.Invalid), resolver(probe)).read("m", "semi", 1L),
            )
            assertEquals(
                PhotoLookup.Offline,
                reader(FakeSource(PhotoFetch.Unreachable), resolver(probe)).read("m", "semi", 1L),
            )
            assertEquals(0, probe.reads)
        }

    // --- Cost ------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun theServerIsAskedAboutMyPositionAtMostOncePerHalfMinutePerMiff() =
        runTest {
            var clock = now
            val probe = FakeProbe(true)
            val r = resolver(probe, clock = { clock })
            repeat(5) { assertEquals(OwnPosition.Published, r.state("m")) }
            assertEquals(1, probe.reads)
            assertEquals(OwnPosition.Published, r.state("other"))
            assertEquals(2, probe.reads)
            clock += OwnPositionResolver.FRESH_MILLIS + 1
            r.state("m")
            assertEquals(3, probe.reads)
        }

    @Test
    fun anUnknownAnswerIsNotKept_theNextCardAsksAgain() =
        runTest {
            val probe = FakeProbe(null)
            val r = resolver(probe)
            assertEquals(OwnPosition.Unknown, r.state("m"))
            probe.answer = false
            assertEquals(OwnPosition.NotPublished, r.state("m"))
            assertEquals(2, probe.reads)
        }

    @Test
    fun aRefusalIsNotKeptInTheCacheOfMissingPhotos_soTheSentenceGoesAwayOnceIPublish() =
        runTest {
            val cache = PhotoCache()
            val source = FakeSource(PhotoFetch.Denied)
            val probe = FakeProbe(false)
            val r = reader(source, resolver(probe), cache)
            assertEquals(PhotoLookup.NeedsOwnPosition, r.read("m", "semi", 1L))
            // I publish my position, the photo becomes readable: the next appearance of the card reads it (a refusal was never remembered).
            probe.answer = true
            source.answer = PhotoFetch.Valid(photo())
            assertTrue(r.read("m", "semi", 1L) is PhotoLookup.Found)
        }
}
