package com.tomessa.miff.domain.notification

import com.tomessa.miff.domain.profile.MyProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CNC-L and CNC-A: who a line of the centre is about, and so what its round picture is (decisions of the PM of 2026-11-19): "the photo says who, the logo says Miff". The twelve vectors
 * `(type, actor) -> picture + text` of the ticket (13), the same on iOS.
 */
class CenterAvatarsTest {
    private val photo = ByteArray(1_000) { 7 }
    private val me = MyProfile("Moi", "Même", "+33612345678", photo)
    private val meWithoutPhoto = MyProfile("Moi", "Même", "+33612345678", null)

    private fun known(
        name: String = "Léa",
        initials: String = "LD",
        jpeg: ByteArray? = photo,
    ): (String, String) -> FaceLookup = { _, _ -> FaceLookup.Known(ActorFace(name, initials, jpeg)) }

    private val person = CenterSubject.Person("miffAAAA", "actor1")

    private fun look(
        subject: CenterSubject,
        lookup: (String, String) -> FaceLookup = { _, _ -> FaceLookup.Pending },
        profile: MyProfile? = me,
        stored: String? = null,
    ) = CenterAvatars.look(subject, lookup, profile, stored)

    // ---- a person ----------------------------------------------------------------------------------------------------------------------

    @Test
    fun aPersonWithAPhoto_isHerPhoto_andHerFirstNameComesFromTheSameCard() {
        val seen = look(person, known(name = "Léa"))
        val avatar = seen.avatar as CenterAvatar.Photo
        assertTrue(avatar.jpeg.contentEquals(photo))
        assertEquals("LD", avatar.initials)
        assertEquals("Léa", seen.name)
    }

    @Test
    fun aPersonWithoutAPhoto_isHerInitials() {
        val seen = look(person, known(jpeg = null))
        assertEquals("LD", (seen.avatar as CenterAvatar.Initials).text)
        assertEquals("Léa", seen.name)
    }

    @Test
    fun aPhotoLargerThanTheRulesAllow_isNeverUsed_theInitialsStandIn() {
        val tooBig = ByteArray(20_001)
        val seen = look(person, known(jpeg = tooBig))
        assertEquals("LD", (seen.avatar as CenterAvatar.Initials).text)
        val limit = look(person, known(jpeg = ByteArray(20_000)))
        assertTrue("exactly 20 000 bytes is allowed", limit.avatar is CenterAvatar.Photo)
        val empty = look(person, known(jpeg = ByteArray(0)))
        assertTrue(empty.avatar is CenterAvatar.Initials)
    }

    @Test
    fun theNameAndThePhotoChangeTogether_aProfileChangedMeanwhile_wins_overTheNameStoredInTheLine() {
        val seen = look(person, known(name = "Léana"), stored = "Léa")
        assertEquals("Léana", seen.name)
    }

    @Test
    fun aPersonWhoLeft_isTheLogoAndUnProche_evenIfThePushWroteHerName() {
        val seen = look(person, { _, _ -> FaceLookup.Gone }, stored = "Léa")
        assertSame(CenterAvatar.Logo, seen.avatar)
        assertNull("no identity is resolved for somebody who is not in the Miff any more", seen.name)
    }

    @Test
    fun whileTheCardIsBeingRead_theRoundIsNeutral_andTheNameOfThePushStandsIn_neverALogoThatWouldFlicker() {
        val seen = look(person, { _, _ -> FaceLookup.Pending }, stored = "Léa")
        assertSame(CenterAvatar.Placeholder, seen.avatar)
        assertEquals("Léa", seen.name)
        val noName = look(person, { _, _ -> FaceLookup.Pending })
        assertSame(CenterAvatar.Placeholder, noName.avatar)
        assertNull(noName.name)
    }

    @Test
    fun aCardThatCannotBeRead_isTheFirstLetterOfTheStoredName_orTheLogoAndUnProcheWithoutOne() {
        val withName = look(person, { _, _ -> FaceLookup.Failed }, stored = "léa")
        assertEquals("L", (withName.avatar as CenterAvatar.Initials).text)
        assertEquals("léa", withName.name)
        val without = look(person, { _, _ -> FaceLookup.Failed })
        assertSame(CenterAvatar.Logo, without.avatar)
        assertNull(without.name)
    }

    @Test
    fun aCardWithABlankFirstName_saysUnProche_notAnEmptyName() {
        assertNull(look(person, known(name = "")).name)
    }

    // ---- the whole Miff, nobody, myself -----------------------------------------------------------------------------------------------------

    @Test
    fun theWholeMiffAndNobody_areTheLogo() {
        assertSame(CenterAvatar.Logo, look(CenterSubject.WholeMiff).avatar)
        assertSame(CenterAvatar.Logo, look(CenterSubject.Nobody).avatar)
        assertEquals("Léa", look(CenterSubject.Nobody, stored = "Léa").name)
        assertNull(look(CenterSubject.WholeMiff, stored = "Léa").name)
    }

    @Test
    fun myOwnReminders_areMyPhoto_myInitialsWithoutOne_theNeutralRoundWhileMyProfileIsNotRead() {
        val withPhoto = look(CenterSubject.Myself).avatar as CenterAvatar.Photo
        assertEquals("MM", withPhoto.initials)
        val initials = look(CenterSubject.Myself, profile = meWithoutPhoto).avatar as CenterAvatar.Initials
        assertEquals("MM", initials.text)
        assertSame(CenterAvatar.Placeholder, look(CenterSubject.Myself, profile = null).avatar)
    }

    // ---- who a line is about, by type -------------------------------------------------------------------------------------------------------

    private fun item(
        type: NotificationType,
        id: String,
        miff: String? = "miffAAAA",
    ) = NotificationItem(id, type, miff, 1L)

    @Test
    fun theSubjectOfEachType() {
        listOf(NotificationType.N1, NotificationType.N2, NotificationType.N6, NotificationType.N7).forEach {
            assertSame("$it is about me", CenterSubject.Myself, CenterAvatars.subjectOf(item(it, "x")))
        }
        listOf(NotificationType.N3, NotificationType.N4).forEach {
            assertSame("$it is about the whole Miff", CenterSubject.WholeMiff, CenterAvatars.subjectOf(item(it, "x")))
        }
        val n9 = CenterAvatars.subjectOf(item(NotificationType.N9, "n9|u|actor1|1700")) as CenterSubject.Person
        assertEquals("actor1", n9.uid)
        assertEquals("miffAAAA", n9.miffId)
        val start =
            CenterAvatars.subjectOf(
                item(NotificationType.N10, "al|u|actor1|start|1700"),
            ) as CenterSubject.Person
        assertEquals("actor1", start.uid)
        val end = CenterAvatars.subjectOf(item(NotificationType.N11, "al|u|actor1|end|1700")) as CenterSubject.Person
        assertEquals("actor1", end.uid)
    }

    @Test
    fun aLineWithoutAnAuthorOrWithoutAMiff_isNobody_thusTheLogo() {
        assertSame(CenterSubject.Nobody, CenterAvatars.subjectOf(item(NotificationType.N9, "n9|m|miffAAAA|1700")))
        assertSame(
            CenterSubject.Nobody,
            CenterAvatars.subjectOf(item(NotificationType.N9, "n9|u|actor1|1700", miff = null)),
        )
        assertSame(CenterSubject.Nobody, CenterAvatars.subjectOf(item(NotificationType.N10, "garbage")))
    }

    @Test
    fun theRequesterAndTheMemberOfAVisitLineAreChosenByTheCaller_theTypeAloneSaysNobody() {
        assertSame(CenterSubject.Nobody, CenterAvatars.subjectOf(item(NotificationType.N8, "n8|miffAAAA|uid_1")))
        assertSame(CenterSubject.Nobody, CenterAvatars.subjectOf(item(NotificationType.C1, "c1|miffAAAA|a|uid")))
        assertTrue(CenterAvatars.person("miffAAAA", "uid") is CenterSubject.Person)
        assertSame(CenterSubject.Nobody, CenterAvatars.person(null, "uid"))
        assertSame(CenterSubject.Nobody, CenterAvatars.person("miffAAAA", null))
    }

    @Test
    fun aLineDoesNotCarryWhatItDoesNotNeed_nothingOfTheFacesIsPrintable() {
        assertEquals("Photo(redacted)", CenterAvatar.Photo(photo, "LD").toString())
        assertEquals("Initials(redacted)", CenterAvatar.Initials("LD").toString())
        assertEquals("ActorFace(redacted)", ActorFace("Léa", "LD", photo).toString())
        assertEquals("CenterLook(redacted)", CenterLook(CenterAvatar.Logo, "Léa").toString())
        assertEquals("Person(redacted)", person.toString())
    }

    // ---- the family of the types --------------------------------------------------------------------------------------------------------------

    @Test
    fun theAlertTypesAreLinesOfTheCentreOnly_outsideThePerimeterOfTheSwitches() {
        assertTrue(NotificationType.N10 !in NotificationType.PERIMETER)
        assertTrue(NotificationType.N11 !in NotificationType.PERIMETER)
        assertTrue(NotificationType.N9 in NotificationType.PERIMETER)
        assertTrue(NotificationPreferences().allows(NotificationType.N10))
        assertEquals(NotificationPreferences(), NotificationPreferences().with(NotificationType.N10, false))
        assertEquals(
            setOf(NotificationType.N9, NotificationType.N10, NotificationType.N11),
            NotificationType.ABOUT_A_LOVED_ONE,
        )
    }
}
