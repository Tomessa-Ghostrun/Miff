package com.tomessa.miff.domain.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules of the form and of the limits (ticket LOC-1, 6.1 to 6.3; criteria PL2 to PL5, LIM1 to LIM3, MIF1). */
class PlaceRulesTest {
    // --- Name (PL2, PL3) --------------------------------------------------------------------------------------------------

    @Test
    fun name_buttonWorksFromOneCharacter_butTheMinimumIsTwo() {
        assertFalse(PlaceName.hasSomething(""))
        assertFalse(PlaceName.hasSomething("   "))
        assertTrue(PlaceName.hasSomething("M"))
        assertFalse(PlaceName.isValid("M"))
        assertTrue(PlaceName.isValid("Ma"))
        assertTrue(PlaceName.isValid("  Ma  "))
        assertFalse(PlaceName.isValid(" M "))
    }

    @Test
    fun name_isCutAtFifty_andSaysSo() {
        val limited = PlaceName.limit("a".repeat(60))
        assertEquals(50, limited.text.length)
        assertTrue(limited.limitReached)
        assertFalse(PlaceName.limit("a".repeat(49)).limitReached)
        assertTrue(PlaceName.limit("a".repeat(50)).limitReached)
        assertEquals(50, PlaceName.stored("a".repeat(80)).length)
    }

    @Test
    fun name_isNeverCutInTheMiddleOfAnEmoji() {
        val typed = "a".repeat(49) + "😀"
        val text = PlaceName.limit(typed).text
        assertTrue(text.length <= 50)
        assertFalse(Character.isHighSurrogate(text.last()))
    }

    @Test
    fun name_losesInvisibleControls() {
        assertEquals("Maison", PlaceName.stored("\u202EMaison\u200B"))
    }

    // --- Radius (PL4) ----------------------------------------------------------------------------------------------------------

    @Test
    fun radius_is50To800ByStepsOf50_withSixteenValues() {
        assertEquals(16, PlaceRadius.values.size)
        assertEquals(50, PlaceRadius.values.first())
        assertEquals(800, PlaceRadius.values.last())
        assertTrue(PlaceRadius.values.zipWithNext().all { (a, b) -> b - a == 50 })
        assertEquals(100, PlaceLimits.RADIUS_DEFAULT)
    }

    @Test
    fun radius_snapsToTheNearestAllowedValue() {
        assertEquals(50, PlaceRadius.snap(0f))
        assertEquals(50, PlaceRadius.snap(74f))
        assertEquals(100, PlaceRadius.snap(76f))
        assertEquals(150, PlaceRadius.snap(149f))
        assertEquals(800, PlaceRadius.snap(5_000f))
        assertTrue(PlaceRadius.isAllowed(450))
        assertFalse(PlaceRadius.isAllowed(120))
        assertFalse(PlaceRadius.isAllowed(850))
    }

    @Test
    fun radius_noteShowsBelowOneHundred_only() {
        assertTrue(PlaceRadius.isBelowRecommended(50))
        assertFalse(PlaceRadius.isBelowRecommended(100))
        assertFalse(PlaceRadius.isBelowRecommended(800))
    }

    // --- Coordinates and address ----------------------------------------------------------------------------------------------

    @Test
    fun coordinates_haveFiveDecimals_andStayOnThePlanet() {
        assertEquals(48.82927, PlaceCoordinates.round5(48.8292675), 0.0)
        assertEquals(2.35679, PlaceCoordinates.round5(2.3567938), 0.0)
        assertTrue(PlaceCoordinates.isValid(48.0, 2.0))
        assertFalse(PlaceCoordinates.isValid(91.0, 0.0))
        assertFalse(PlaceCoordinates.isValid(0.0, 181.0))
        assertFalse(PlaceCoordinates.isValid(Double.NaN, 0.0))
        assertFalse(PlaceCoordinates.isValid(0.0, Double.POSITIVE_INFINITY))
    }

    @Test
    fun address_isBoundedLikeTheRules() {
        assertEquals(200, PlaceAddressText.stored("x".repeat(300)).length)
        assertEquals("Rue", PlaceAddressText.stored("  Rue \u200B"))
    }

    // --- The draft (mirror of validPlaceShape) ----------------------------------------------------------------------------------

    private fun draft(
        name: String = "Maison",
        miffIds: List<String> = listOf("m1"),
        address: String = "17 Avenue d'Italie, 75013 Paris",
        lat: Double = 48.82927,
        lng: Double = 2.35679,
        radiusM: Int = 100,
    ) = PlaceDraft(name, miffIds, address, lat, lng, radiusM, PlaceType.TRUSTED, PlaceCategory.HOME)

    @Test
    fun draft_validOne_hasNoProblem() {
        assertNull(draft().problem)
    }

    @Test
    fun draft_problems() {
        assertEquals(DraftProblem.NAME, draft(name = "M").problem)
        assertEquals(DraftProblem.MIFFS, draft(miffIds = emptyList()).problem)
        assertEquals(DraftProblem.MIFFS, draft(miffIds = listOf("a", "b", "c", "d", "e", "f")).problem)
        assertEquals(DraftProblem.MIFFS, draft(miffIds = listOf("a", "a")).problem)
        assertEquals(DraftProblem.ADDRESS, draft(address = " ").problem)
        assertEquals(DraftProblem.ADDRESS, draft(lat = 120.0).problem)
        assertEquals(DraftProblem.RADIUS, draft(radiusM = 120).problem)
        assertNull(draft(miffIds = listOf("a", "b", "c", "d", "e")).problem)
    }

    // --- The limits of the free plan (LIM1 to LIM3) -------------------------------------------------------------------------------

    private fun slot(
        premium: Boolean = false,
        type: PlaceType = PlaceType.TRUSTED,
        targets: List<String> = listOf("m1"),
        existing: Map<String, Set<String>> = mapOf("m1" to emptySet()),
    ) = PlaceQuota.forCreate(premium, type, targets, existing) { "NEWID123456789012345" }

    @Test
    fun free_takesTheFirstFreeFixedSlot() {
        assertEquals("p1", (slot() as PlaceSlot.Use).placeId)
        assertEquals("p2", (slot(existing = mapOf("m1" to setOf("p1"))) as PlaceSlot.Use).placeId)
        assertEquals("p1", (slot(existing = mapOf("m1" to setOf("p2"))) as PlaceSlot.Use).placeId)
    }

    @Test
    fun free_thirdPlace_needsMiffPlus_andNothingIsWritten() {
        val decision = slot(existing = mapOf("m1" to setOf("p1", "p2")))
        assertEquals(PaywallReason.LIMIT, (decision as PlaceSlot.NeedsPremium).reason)
    }

    @Test
    fun free_dangerZone_needsMiffPlus_evenWithRoomLeft() {
        val decision = slot(type = PlaceType.RISK)
        assertEquals(PaywallReason.DANGER, (decision as PlaceSlot.NeedsPremium).reason)
    }

    @Test
    fun free_theLimitCountsThePlacesOfThePersonInAllTheirMiffs() {
        // A place attached to two Miffs counts once; two different places in two Miffs are the two of the plan.
        val same = slot(targets = listOf("m1"), existing = mapOf("m1" to setOf("p1"), "m2" to setOf("p1")))
        assertEquals("p2", (same as PlaceSlot.Use).placeId)
        val two = slot(targets = listOf("m2"), existing = mapOf("m1" to setOf("p1"), "m2" to setOf("p2")))
        assertEquals(PaywallReason.LIMIT, (two as PlaceSlot.NeedsPremium).reason)
    }

    @Test
    fun free_theSlotMustBeFreeInEveryChosenMiff() {
        // p1 is free in m1 but taken in m2 (another place): p2 is the only slot free in both... and is taken nowhere.
        val decision = slot(targets = listOf("m1", "m2"), existing = mapOf("m1" to emptySet(), "m2" to setOf("p1")))
        assertEquals("p2", (decision as PlaceSlot.Use).placeId)
    }

    @Test
    fun premium_getsAFreshIdentifier_anyType_anyNumber() {
        val decision = slot(premium = true, type = PlaceType.RISK, existing = mapOf("m1" to setOf("p1", "p2", "x")))
        assertEquals("NEWID123456789012345", (decision as PlaceSlot.Use).placeId)
    }

    @Test
    fun typeChange_toDangerNeedsMiffPlus_backToTrustedNever() {
        assertEquals(PaywallReason.DANGER, PlaceQuota.forTypeChange(false, PlaceType.TRUSTED, PlaceType.RISK))
        assertNull(PlaceQuota.forTypeChange(true, PlaceType.TRUSTED, PlaceType.RISK))
        assertNull(PlaceQuota.forTypeChange(false, PlaceType.RISK, PlaceType.TRUSTED))
        // An account whose Miff+ ended keeps editing the danger zone it already has (nothing changes the type).
        assertNull(PlaceQuota.forTypeChange(false, PlaceType.RISK, PlaceType.RISK))
    }

    @Test
    fun atFreeLimit_onlyForTheFreePlanWithTwoPlaces() {
        assertTrue(PlaceQuota.isAtFreeLimit(false, 2))
        assertFalse(PlaceQuota.isAtFreeLimit(false, 1))
        assertFalse(PlaceQuota.isAtFreeLimit(true, 7))
    }

    // --- The Miffs ticked when the form opens (MIF1) -----------------------------------------------------------------------------

    private fun miff(
        id: String,
        joinedAt: Long,
    ) = OwnedMiff(id, "Miff $id", joinedAt)

    @Test
    fun default_isTheLastJoinedMiff_ifThePersonCreatedIt() {
        val owned = listOf(miff("a", 10), miff("b", 20))
        assertEquals("b", DefaultMiffChoice.of(owned, lastJoinedId = "b"))
        assertEquals("a", DefaultMiffChoice.of(owned, lastJoinedId = "a"))
    }

    @Test
    fun default_isTheFirstCreated_whenTheLastJoinedIsSomebodyElses() {
        val owned = listOf(miff("b", 20), miff("a", 10))
        assertEquals("a", DefaultMiffChoice.of(owned, lastJoinedId = "someone-elses"))
        assertEquals("a", DefaultMiffChoice.of(owned, lastJoinedId = null))
        assertNull(DefaultMiffChoice.of(emptyList(), lastJoinedId = "x"))
    }
}
