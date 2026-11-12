package com.tomessa.miff.domain.place

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Profile draws follows the role: the buttons of the creator are never drawn on a doubt (decision of the PM, 2026-11-11). */
class PlaceViewerRoleTest {
    @Test
    fun theCreator_isTheOneWhoseUidIsTheOwners() {
        assertTrue(PlaceViewerRole.isCreator(ownerUid = "u1", myUid = "u1"))
    }

    @Test
    fun anotherMember_isNotTheCreator() {
        assertFalse(PlaceViewerRole.isCreator(ownerUid = "u1", myUid = "u2"))
    }

    @Test
    fun anUnknownOwner_orAnUnknownViewer_isNeverTheCreator() {
        assertFalse(PlaceViewerRole.isCreator(ownerUid = null, myUid = null))
        assertFalse(PlaceViewerRole.isCreator(ownerUid = null, myUid = "u1"))
        assertFalse(PlaceViewerRole.isCreator(ownerUid = "u1", myUid = null))
        assertFalse(PlaceViewerRole.isCreator(ownerUid = "", myUid = ""))
    }
}
