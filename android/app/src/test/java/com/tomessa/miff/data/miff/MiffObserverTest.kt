package com.tomessa.miff.data.miff

import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffObservation
import com.tomessa.miff.domain.miff.MiffPlace
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.testsupport.miffDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the three listeners of a Miff become ONE answer for the screen (ticket Miff 5.7, criteria D1, D3, G8). */
class MiffObserverTest {
    private val member = MiffMember("u1", "Léa", "Martin", null, MiffRole.OWNER, sharingEnabled = true)

    private fun miff(fromCache: Boolean = false) = ObservedPiece(miffDetails(), fromCache)

    private fun members(fromCache: Boolean = false) = ObservedPiece(listOf(member), fromCache)

    private fun places() = ObservedPiece(listOf(MiffPlace("p1", "Maison")))

    @Test
    fun nothingKnownYet_isLoading() {
        assertEquals(MiffObservation.Loading, assembleObservation(null, null, null))
        assertEquals(MiffObservation.Loading, assembleObservation(miff(), null, null))
        assertEquals(MiffObservation.Loading, assembleObservation(miff(), members(), null))
    }

    @Test
    fun theThreeListeners_makeOnePicture() {
        val data = assembleObservation(miff(), members(), places()) as MiffObservation.Data
        assertEquals(1, data.snapshot.members.size)
        assertEquals(
            "Maison",
            data.snapshot.places
                .single()
                .name,
        )
        assertFalse(data.snapshot.fromCache)
    }

    @Test
    fun whatComesFromTheCache_isSaid() {
        val data = assembleObservation(miff(), members(fromCache = true), places()) as MiffObservation.Data
        assertTrue(data.snapshot.fromCache)
    }

    @Test
    fun aLossOnTheMiffOrOnItsMembers_winsOverEverything() {
        val lost = ObservedPiece<com.tomessa.miff.domain.miff.MiffDetails>(lost = true)
        assertEquals(MiffObservation.AccessLost, assembleObservation(lost, members(), places()))
        val lostMembers = ObservedPiece<List<MiffMember>>(lost = true)
        assertEquals(MiffObservation.AccessLost, assembleObservation(miff(), lostMembers, places()))
        val failed = ObservedPiece<List<MiffMember>>(failed = true)
        assertEquals(MiffObservation.AccessLost, assembleObservation(lost, failed, places()))
    }

    @Test
    fun aFailureThatIsNotALoss_isNeverAnAccessLoss() {
        val failed = ObservedPiece<List<MiffMember>>(failed = true)
        assertEquals(MiffObservation.Failed, assembleObservation(miff(), failed, places()))
    }

    @Test
    fun unreadablePlaces_areJustAnEmptyList() {
        val noPlaces = ObservedPiece<List<MiffPlace>>(emptyList())
        val data = assembleObservation(miff(), members(), noPlaces) as MiffObservation.Data
        assertTrue(data.snapshot.places.isEmpty())
    }
}
