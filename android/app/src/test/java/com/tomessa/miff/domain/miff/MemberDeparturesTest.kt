package com.tomessa.miff.domain.miff

import org.junit.Assert.assertEquals
import org.junit.Test

class MemberDeparturesTest {
    private fun member(uid: String) = MiffMember(uid, "P-$uid", "N", null, MiffRole.MEMBER, true)

    private val me = "uid-owner"

    @Test
    fun noEarlierPicture_noDeparture_soNoInvitationForOldDeparturesOnANewPhone() {
        assertEquals(emptySet<String>(), MemberDepartures.between(null, listOf(member(me)), me, emptySet()))
    }

    @Test
    fun someoneWhoDisappears_isADeparture() {
        val before = listOf(member(me), member("a"), member("b"))
        val after = listOf(member(me), member("b"))
        assertEquals(setOf("a"), MemberDepartures.between(before, after, me, emptySet()))
    }

    @Test
    fun aPersonTheCreatorJustRemoved_isNotADeparture() {
        val before = listOf(member(me), member("a"))
        assertEquals(emptySet<String>(), MemberDepartures.between(before, listOf(member(me)), me, setOf("a")))
    }

    @Test
    fun theCreatorsOwnLine_andNewArrivals_neverCount() {
        val before = listOf(member(me), member("a"))
        val after = listOf(member("a"), member("c"))
        assertEquals(emptySet<String>(), MemberDepartures.between(before, after, me, emptySet()))
    }

    @Test
    fun noChange_noDeparture() {
        val same = listOf(member(me), member("a"))
        assertEquals(emptySet<String>(), MemberDepartures.between(same, same, me, emptySet()))
    }
}
