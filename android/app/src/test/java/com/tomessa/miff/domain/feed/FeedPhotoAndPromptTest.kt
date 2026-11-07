package com.tomessa.miff.domain.feed

import com.tomessa.miff.data.feed.PhotoCache
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.update.PhotoLocation
import com.tomessa.miff.domain.update.PromptLevel
import com.tomessa.miff.domain.update.SharedPhoto
import com.tomessa.miff.domain.update.UpdatePhotoFormat
import com.tomessa.miff.testsupport.testPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/**
 * FEED-4 and UPDATE-2 in the logic of the Feed: when a card reads a photo, what the bubble of encouragement says for ME in the Miff shown (D19), and the rules of
 * what comes from the network (a photo is never shown past its time, never with half a place, never more than the rules allow).
 */
class FeedPhotoAndPromptTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-11-05T17:00:00Z").toEpochMilli()
    private val minute = 60_000L
    private val hour = 60 * minute
    private val me = "me"

    private fun member(
        uid: String,
        sharing: Boolean = true,
    ) = MiffMember(uid, uid, "X", null, if (uid == me) MiffRole.OWNER else MiffRole.MEMBER, sharing, "+33612345678")

    private fun status(
        presence: PresenceValue = PresenceValue.OUT,
        presenceAgo: Long = 90 * minute,
        lastUpdateAgo: Long? = presenceAgo,
        hasPhoto: Boolean = false,
    ) = MemberStatus(
        presence,
        "p1",
        now - presenceAgo,
        lastUpdateAgo?.let { now - it },
        hasPhoto,
        80,
        MemberLocationAccess.BACKGROUND,
        now - minute,
    )

    private fun build(
        statuses: Map<String, MemberStatus>,
        members: List<MiffMember> = listOf(member(me), member("t")),
        waiting: Boolean = false,
    ) = FeedBuilder.build(
        FeedInput(
            "m1",
            "Chez nous",
            "CODE12345678",
            members,
            listOf(testPlace("p1", "Chez Mamie")),
            me,
            StatusesView.Loaded(statuses),
            waiting,
            false,
        ),
        now,
        zone,
    )

    // --- The photo of a card (FEED-4) ---------------------------------------------------------------------------------------------------

    @Test
    fun aStatusWithAPhotoThatIsRecent_makesTheCardReadIt_aStatusWithoutOne_doesNot() {
        val withPhoto =
            build(
                mapOf(
                    me to status(hasPhoto = true, lastUpdateAgo = 10 * minute),
                    "t" to status(hasPhoto = false),
                ),
            )
        assertEquals(PhotoSlot.Candidate(now - 10 * minute), withPhoto.cards.first { it.isMe }.photo)
        assertEquals(PhotoSlot.None, withPhoto.cards.first { it.member.uid == "t" }.photo)
    }

    @Test
    fun aPhotoIsReadOnlyWhileItCanStillBeReadable_theClientWritesTwentyThreeHours() {
        fun slot(ago: Long) =
            build(mapOf("t" to status(hasPhoto = true, lastUpdateAgo = ago)))
                .cards
                .first {
                    it.member.uid ==
                        "t"
                }.photo
        assertTrue(slot(PhotoSlot.READ_WINDOW_MILLIS - 1) is PhotoSlot.Candidate)
        assertEquals(PhotoSlot.None, slot(PhotoSlot.READ_WINDOW_MILLIS))
        assertEquals(PhotoSlot.None, slot(26 * hour))
    }

    @Test
    fun noPhotoIsReadWithoutADateOfUpdate_norForSomeoneWhoStoppedSharing_norWhileIHide() {
        assertEquals(
            PhotoSlot.None,
            build(mapOf("t" to status(hasPhoto = true, lastUpdateAgo = null)))
                .cards
                .first {
                    it.member.uid ==
                        "t"
                }.photo,
        )
        val stopped =
            build(
                mapOf("t" to status(hasPhoto = true, lastUpdateAgo = minute)),
                members = listOf(member(me), member("t", sharing = false)),
            )
        assertEquals(PhotoSlot.None, stopped.cards.first { it.member.uid == "t" }.photo)
        // I stopped sharing: nothing of the others is read, so no photo either.
        val hidden =
            build(
                mapOf("t" to status(hasPhoto = true, lastUpdateAgo = minute)),
                members = listOf(member(me, sharing = false), member("t")),
            )
        assertEquals(PhotoSlot.None, hidden.cards.first { it.member.uid == "t" }.photo)
    }

    @Test
    fun aLastUpdateInTheFuture_aWrongClock_isTreatedAsNow() {
        val content = build(mapOf("t" to status(hasPhoto = true, lastUpdateAgo = -3 * hour)))
        assertTrue(content.cards.first { it.member.uid == "t" }.photo is PhotoSlot.Candidate)
    }

    // --- The bubble of MY status (UPDATE-2, D19) ----------------------------------------------------------------------------------------------

    @Test
    fun theBubbleFollowsMyStatusInTheMiffShown_orange_red_orNothing() {
        assertEquals(PromptLevel.NONE, build(mapOf(me to status(presenceAgo = 30 * minute))).myPrompt)
        assertEquals(PromptLevel.ORANGE, build(mapOf(me to status(presenceAgo = 90 * minute))).myPrompt)
        assertEquals(PromptLevel.RED, build(mapOf(me to status(presenceAgo = 3 * hour))).myPrompt)
    }

    @Test
    fun theOthersBeingOrangeOrRed_neverMakesMyBubble() {
        val content = build(mapOf(me to status(presenceAgo = 10 * minute), "t" to status(presenceAgo = 5 * hour)))
        assertEquals(PromptLevel.NONE, content.myPrompt)
    }

    @Test
    fun noBubbleWhenIDoNotShare_orMyAgreementIsWaited_orMyStatusIsNotPublished() {
        val old = status(presenceAgo = 3 * hour)
        assertEquals(
            PromptLevel.NONE,
            build(mapOf(me to old), members = listOf(member(me, sharing = false), member("t"))).myPrompt,
        )
        assertEquals(PromptLevel.NONE, build(mapOf(me to old), waiting = true).myPrompt)
        assertEquals(PromptLevel.NONE, build(emptyMap()).myPrompt)
    }

    // --- What comes from the network ------------------------------------------------------------------------------------------------------------

    private val jpeg = ByteArray(1000) { 1 }

    private fun shared(
        bytes: ByteArray? = jpeg,
        created: Long? = now - minute,
        expires: Long? = now + hour,
        lat: Number? = 48.85661,
        lng: Number? = 2.35222,
        measured: Long? = now - 2 * minute,
    ) = SharedPhoto.fromServer(bytes, created, expires, lat, lng, measured, now)

    @Test
    fun aPhotoPastItsTime_isNeverShown_evenFromACopy() {
        assertNotNull(shared(expires = now + 1))
        assertNull(shared(expires = now))
        assertNull(shared(expires = now - hour))
        val cache = PhotoCache()
        cache.putRead("m1", "t", shared()!!, lastUpdateAtMillis = 5L)
        assertNotNull(cache.get("m1", "t", 5L, now))
        assertNull("expired copies are dropped", cache.get("m1", "t", 5L, now + 2 * hour))
    }

    @Test
    fun aPhotoOfMoreThanTheRulesAllow_orWithNoBytes_orNoTimes_isNotAPhoto() {
        assertNotNull(shared(bytes = ByteArray(UpdatePhotoFormat.MAX_BYTES)))
        assertNull(shared(bytes = ByteArray(UpdatePhotoFormat.MAX_BYTES + 1)))
        assertNull(shared(bytes = ByteArray(0)))
        assertNull(shared(bytes = null))
        assertNull(shared(created = null))
        assertNull(shared(expires = null))
    }

    @Test
    fun aPlaceIsKeptOnlyWhenTheThreeValuesAreThereAndValid_neverHalfOfOne() {
        assertNotNull(shared()!!.location)
        assertNull(shared(lat = null)!!.location)
        assertNull(shared(lng = null)!!.location)
        assertNull(shared(measured = null)!!.location)
        assertNull(shared(lat = 91.0)!!.location)
        assertNull(shared(lng = -181.0)!!.location)
        assertNull(shared(lat = Double.NaN)!!.location)
        // The photo itself is still shown without it.
        assertNotNull(shared(lat = 91.0))
    }

    @Test
    fun aCopyIsKeptWhileTheStatusIsTheSame_andReadAgainWhenItChanges_theAuthorsOwnIsValidUntilItExpires() {
        val cache = PhotoCache()
        val photo = shared()!!
        cache.putRead("m1", "t", photo, lastUpdateAtMillis = 10L)
        assertEquals(photo, cache.get("m1", "t", 10L, now))
        assertNull("a new update: the photo is read again", cache.get("m1", "t", 11L, now))
        cache.putOwn("m1", me, photo)
        assertEquals(photo, cache.get("m1", me, 999L, now))
        assertEquals(photo, cache.get("m1", me, null, now))
    }

    @Test
    fun theCacheKeepsAtMostSixteenPhotos_andIsEmptiedAtSignOut() {
        val cache = PhotoCache()
        repeat(20) { cache.putRead("m1", "u$it", shared()!!, 1L) }
        assertNull("the oldest are gone", cache.get("m1", "u0", 1L, now))
        assertNotNull(cache.get("m1", "u19", 1L, now))
        cache.clear()
        assertNull(cache.get("m1", "u19", 1L, now))
    }

    @Test
    fun aPhotoAndItsPlaceShowNothingOfThemselves() {
        assertEquals("SharedPhoto(redacted)", shared().toString())
        assertEquals("PhotoLocation(redacted)", PhotoLocation(1.0, 2.0, 3L).toString())
        assertFalse(shared().toString().contains("48"))
    }

    @Test
    fun aRefusalIsTheReciprocityOnlyWhenIHaveNotPublishedMyPosition_otherwiseNothingIsShown() {
        assertEquals(
            com.tomessa.miff.data.feed.PhotoLookup.NeedsOwnPosition,
            com.tomessa.miff.data.feed
                .refusalLookup(ownPositionPublished = false),
        )
        assertEquals(
            com.tomessa.miff.data.feed.PhotoLookup.Missing,
            com.tomessa.miff.data.feed
                .refusalLookup(ownPositionPublished = true),
        )
    }

    @Test
    fun myStatusTellsTheCleanUpAboutMyPhoto_only_whenIShare() {
        val mine = status(hasPhoto = true, lastUpdateAgo = 5 * minute)
        assertEquals(MyPhotoFacts(true, now - 5 * minute), build(mapOf(me to mine)).myPhoto)
        assertNull(build(emptyMap()).myPhoto)
        assertNull(build(mapOf(me to mine), members = listOf(member(me, sharing = false), member("t"))).myPhoto)
        assertNull(build(mapOf(me to mine), waiting = true).myPhoto)
    }

    @Test
    fun aPhotoThatTheServerSaidIsNotThere_isNotAskedAgainForTheSameStatus_butIsForANewOne() {
        val cache = PhotoCache()
        assertFalse(cache.isMissing("m1", "t", 10L))
        cache.putMissing("m1", "t", 10L)
        assertTrue(cache.isMissing("m1", "t", 10L))
        assertFalse("a new update: the server is asked again", cache.isMissing("m1", "t", 11L))
        // A photo read afterwards replaces the memory of "nothing"; the sign-out empties everything.
        cache.putRead("m1", "t", shared()!!, 11L)
        cache.putMissing("m1", "u", 5L)
        cache.clear()
        assertFalse(cache.isMissing("m1", "u", 5L))
    }
}
