package com.tomessa.miff.domain.profile

import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.miff.MiffSummary
import com.tomessa.miff.domain.miff.SelectorTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The carousel of Miffs of the Profile tab: what it holds, what its last card does, the faces and the rules of the reading cost. */
class MiffFacesTest {
    private fun member(
        uid: String,
        first: String = "Léa",
        last: String = "Martin",
        avatar: ByteArray? = null,
    ) = MiffMember(uid, first, last, avatar, MiffRole.MEMBER, sharingEnabled = true, phone = "+33612345678")

    private fun members(count: Int) = (1..count).map { member("u$it") }

    // --- The layout of the faces ---------------------------------------------------------------------------------------------

    @Test
    fun layout_upToFiveMembers_everyoneIsDrawn_noMore() {
        assertEquals(FacesLayout(0, 0), FacesLayout.of(0))
        assertEquals(FacesLayout(1, 0), FacesLayout.of(1))
        assertEquals(FacesLayout(3, 0), FacesLayout.of(3))
        assertEquals(FacesLayout(5, 0), FacesLayout.of(5))
    }

    @Test
    fun layout_moreThanFive_fourFacesThenPlusN_neverPlusOne() {
        assertEquals(FacesLayout(4, 2), FacesLayout.of(6))
        assertEquals(FacesLayout(4, 11), FacesLayout.of(15))
        // 5 circles in all, the "+N" one included.
        (6..40).forEach { count ->
            val layout = FacesLayout.of(count)
            assertEquals(5, layout.shown + 1)
            assertEquals(count, layout.shown + layout.more)
            assertTrue(layout.more >= 2)
        }
    }

    @Test
    fun layout_aNegativeCount_isNothing() {
        assertEquals(FacesLayout(0, 0), FacesLayout.of(-3))
    }

    // --- What a card keeps of a member -----------------------------------------------------------------------------------------

    @Test
    fun face_keepsOnlyTheUid_theInitialsAndThePhoto() {
        val photo = ByteArray(100) { 1 }
        val face = MemberFace.of(member("u1", "emmanuelle", "martin", photo))
        assertEquals("u1", face.uid)
        assertEquals("EM", face.initials)
        assertSame(photo, face.avatarJpeg)
    }

    @Test
    fun face_aPhotoLargerThanTheRulesAllow_orEmpty_isNotKept_theInitialsAreDrawn() {
        assertNull(MemberFace.of(member("u1", avatar = ByteArray(20_001))).avatarJpeg)
        assertNull(MemberFace.of(member("u1", avatar = ByteArray(0))).avatarJpeg)
        assertEquals(20_000, MemberFace.of(member("u1", avatar = ByteArray(20_000))).avatarJpeg?.size)
    }

    @Test
    fun face_aMemberWithoutAName_hasAQuestionMark() {
        assertEquals("?", MemberFace.of(member("u1", "", "")).initials)
    }

    @Test
    fun face_hidesItsContentInToString() {
        assertFalse(MemberFace.of(member("secret-uid", "Léa", "Martin")).toString().contains("secret"))
        assertFalse(CardFaces.Ready(1, listOf(MemberFace.of(member("u1")))).toString().contains("Léa"))
        assertFalse(ReadMembers("m1", members(2)).toString().contains("Léa"))
    }

    // --- The carousel -----------------------------------------------------------------------------------------------------------

    private fun miff(id: String) = MiffSummary(id, "Miff $id")

    @Test
    fun items_oneCardPerMiff_thenTheCreateCardLast() {
        val items = MiffCarousel.items(listOf(miff("a"), miff("b")))
        assertEquals(3, items.size)
        assertEquals("a", (items[0] as CarouselItem.Card).miff.id)
        assertEquals("b", (items[1] as CarouselItem.Card).miff.id)
        assertSame(CarouselItem.Create, items[2])
    }

    @Test
    fun items_withNoMiff_theCreateCardIsAlone() {
        assertEquals(listOf<CarouselItem>(CarouselItem.Create), MiffCarousel.items(emptyList()))
    }

    @Test
    fun initialPage_isTheMiffOfTheFeed_elseTheFirstCard() {
        val miffs = listOf(miff("a"), miff("b"), miff("c"))
        assertEquals(1, MiffCarousel.initialPage(miffs, "b"))
        assertEquals(0, MiffCarousel.initialPage(miffs, null))
        assertEquals(0, MiffCarousel.initialPage(miffs, "gone"))
        assertEquals(0, MiffCarousel.initialPage(emptyList(), "b"))
    }

    @Test
    fun createTap_freeAccountWithAMiff_opensTheOffer() {
        assertEquals(SelectorTap.OpenOffer, MiffCarousel.onCreateTap(premium = false, miffCount = 1))
        assertEquals(SelectorTap.OpenOffer, MiffCarousel.onCreateTap(premium = false, miffCount = 2))
    }

    @Test
    fun createTap_miffPlus_orNoMiffYet_goesToTheCreation() {
        assertEquals(SelectorTap.OpenCreate, MiffCarousel.onCreateTap(premium = true, miffCount = 1))
        assertEquals(SelectorTap.OpenCreate, MiffCarousel.onCreateTap(premium = true, miffCount = 4))
        assertEquals(SelectorTap.OpenCreate, MiffCarousel.onCreateTap(premium = false, miffCount = 0))
    }

    @Test
    fun createTap_anUnknownPlan_isNeverTakenForFree() {
        assertEquals(SelectorTap.OpenCreate, MiffCarousel.onCreateTap(premium = null, miffCount = 1))
    }

    // --- The photo of ANOTHER account: decoded only if it looks like one of ours ----------------------------------------

    @Test
    fun decodePlan_refusesWhatIsNotAnAvatarOfTheApp() {
        assertNull(AvatarDecodePlan.sampleSize(0, 256, 96))
        assertNull(AvatarDecodePlan.sampleSize(256, -1, 96))
        assertNull(AvatarDecodePlan.sampleSize(AvatarDecodePlan.MAX_SIDE + 1, 256, 96))
        assertNull(AvatarDecodePlan.sampleSize(256, AvatarDecodePlan.MAX_SIDE + 1, 96))
        assertNull(AvatarDecodePlan.sampleSize(256, 256, 0))
    }

    @Test
    fun decodePlan_subsamplesByAPowerOfTwo_neverBelowTheCircle() {
        assertEquals(2, AvatarDecodePlan.sampleSize(256, 256, 96))
        assertEquals(1, AvatarDecodePlan.sampleSize(150, 150, 96))
        assertEquals(4, AvatarDecodePlan.sampleSize(400, 400, 96))
        assertEquals(8, AvatarDecodePlan.sampleSize(1024, 1024, 96))
        assertEquals(1, AvatarDecodePlan.sampleSize(50, 50, 96))
        // The smaller side decides.
        assertEquals(1, AvatarDecodePlan.sampleSize(2000, 150, 96))
    }

    // --- The rules of the reading cost (MiffFacesBook) --------------------------------------------------------------------

    private var now = 1_000_000L
    private val book = MiffFacesBook { now }

    @Test
    fun book_aMiffNobodyKnows_needsAReading_andShowsNothingYet() {
        assertTrue(book.needsReading("a"))
        assertTrue(book.view().isEmpty())
    }

    @Test
    fun book_whatTheFeedRead_isShown_andNeverRead_evenMuchLater() {
        book.seedFromFeed(ReadMembers("a", members(4)))
        val shown = book.view().getValue("a") as CardFaces.Ready
        assertEquals(4, shown.count)
        assertEquals(4, shown.faces.size)
        assertFalse(book.needsReading("a"))
        now += 6 * 60 * 60 * 1000L
        assertFalse(book.needsReading("a"))
    }

    @Test
    fun book_theCountIsTheWholeMiff_theFacesAreFiveAtMost() {
        book.seedFromFeed(ReadMembers("a", members(15)))
        val shown = book.view().getValue("a") as CardFaces.Ready
        assertEquals(15, shown.count)
        assertEquals(5, shown.faces.size)
    }

    @Test
    fun book_aReading_isNotStartedTwice_andServesFiveMinutes() {
        book.readingStarted("b")
        assertEquals(CardFaces.Loading, book.view()["b"])
        assertFalse("a reading under way is never doubled", book.needsReading("b"))
        book.readingSucceeded("b", members(3))
        assertEquals(3, (book.view().getValue("b") as CardFaces.Ready).count)
        assertFalse(book.needsReading("b"))
        now += MiffFacesBook.TTL_MILLIS - 1
        assertFalse(book.needsReading("b"))
        now += 1
        assertTrue(book.needsReading("b"))
    }

    @Test
    fun book_aFailure_isCalm_andNeverALoop() {
        book.readingStarted("b")
        book.readingFailed("b")
        assertEquals(CardFaces.Unavailable, book.view()["b"])
        assertFalse(book.needsReading("b"))
        now += MiffFacesBook.RETRY_MILLIS - 1
        assertFalse(book.needsReading("b"))
        now += 1
        assertTrue(book.needsReading("b"))
    }

    @Test
    fun book_aFailureAfterASuccess_keepsTheFacesThatWereDrawn() {
        book.readingSucceeded("b", members(3))
        now += MiffFacesBook.TTL_MILLIS
        book.readingStarted("b")
        book.readingFailed("b")
        assertEquals(3, (book.view().getValue("b") as CardFaces.Ready).count)
    }

    @Test
    fun book_openingTheMiff_readsItAgainAtTheNextAppearance_evenTheOneOfTheFeed() {
        book.seedFromFeed(ReadMembers("a", members(4)))
        book.invalidate("a")
        assertTrue(book.needsReading("a"))
        // The Feed listening again (it is live) clears it: nothing to read.
        book.seedFromFeed(ReadMembers("a", members(3)))
        assertFalse(book.needsReading("a"))
    }

    @Test
    fun book_invalidatingAMiffNobodyKnows_doesNothing() {
        book.invalidate("zzz")
        assertTrue(book.view().isEmpty())
    }

    @Test
    fun book_aMiffThatLeftTheList_takesItsFacesWithIt() {
        book.seedFromFeed(ReadMembers("a", members(2)))
        book.readingSucceeded("b", members(2))
        book.retainOnly(setOf("a"))
        assertEquals(setOf("a"), book.view().keys)
    }

    // --- My own face follows my profile, with no reading -------------------------------------------------------------------

    private fun faceOf(
        book: MiffFacesBook,
        miffId: String,
        uid: String,
    ) = (book.view().getValue(miffId) as CardFaces.Ready).faces.first { it.uid == uid }

    @Test
    fun book_myNewFace_replacesMineOnEveryCard_withoutAnyReading_evenForWhatTheFeedGave() {
        book.seedFromFeed(ReadMembers("a", members(4)))
        book.readingSucceeded("b", members(3))
        val photo = ByteArray(50) { 7 }
        book.replaceFace(MemberFace.of("u2", "Zoé", "Durand", photo))
        listOf("a", "b").forEach { miff ->
            val mine = faceOf(book, miff, "u2")
            assertEquals("ZD", mine.initials)
            assertSame(photo, mine.avatarJpeg)
        }
        // The others are left alone, the counts and the rules of the cost too.
        assertEquals("LM", faceOf(book, "a", "u1").initials)
        assertEquals(4, (book.view().getValue("a") as CardFaces.Ready).count)
        assertFalse(book.needsReading("a"))
        assertFalse(book.needsReading("b"))
    }

    @Test
    fun book_myNewFace_isIgnoredWhereIAmNotDrawn_andForCardsWithoutFaces() {
        book.seedFromFeed(ReadMembers("a", members(8)))
        book.readingStarted("b")
        book.readingFailed("c")
        book.replaceFace(MemberFace.of("u7", "Zoé", "Durand", null))
        // u7 is the 7th member: beyond the 5 faces kept, nothing to replace.
        assertEquals(5, (book.view().getValue("a") as CardFaces.Ready).faces.size)
        assertTrue((book.view().getValue("a") as CardFaces.Ready).faces.none { it.uid == "u7" })
        assertEquals(CardFaces.Unavailable, book.view()["c"])
    }
}
