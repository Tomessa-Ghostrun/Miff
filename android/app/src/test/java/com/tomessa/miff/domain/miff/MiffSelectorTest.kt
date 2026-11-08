package com.tomessa.miff.domain.miff

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The selector of Miffs in the header of the Feed (decision of the PM, 2026-11-08, which replaces Feed D12 / Q2): who sees what, and what a touch on
 * "Rejoindre une Miff" / "Créer une Miff" does according to the plan.
 */
class MiffSelectorTest {
    @Test
    fun everyone_seesTheTwoEntries_inTheSameOrder() {
        assertEquals(listOf(SelectorEntry.JOIN, SelectorEntry.CREATE), MiffSelector.entries)
    }

    @Test
    fun freeAccountWithAMiff_isTakenToTheOffer_forBothEntries() {
        assertEquals(SelectorTap.OpenOffer, MiffSelector.onTap(SelectorEntry.JOIN, premium = false, miffCount = 1))
        assertEquals(SelectorTap.OpenOffer, MiffSelector.onTap(SelectorEntry.CREATE, premium = false, miffCount = 1))
    }

    @Test
    fun freeAccountWithSeveralMiffs_afterAnEndedSubscription_isStillTakenToTheOffer() {
        assertEquals(SelectorTap.OpenOffer, MiffSelector.onTap(SelectorEntry.JOIN, premium = false, miffCount = 3))
        assertEquals(SelectorTap.OpenOffer, MiffSelector.onTap(SelectorEntry.CREATE, premium = false, miffCount = 3))
    }

    @Test
    fun premiumAccount_goesOnToTheScreens_neverToTheOffer() {
        assertEquals(SelectorTap.OpenJoin, MiffSelector.onTap(SelectorEntry.JOIN, premium = true, miffCount = 1))
        assertEquals(SelectorTap.OpenCreate, MiffSelector.onTap(SelectorEntry.CREATE, premium = true, miffCount = 1))
        assertEquals(SelectorTap.OpenJoin, MiffSelector.onTap(SelectorEntry.JOIN, premium = true, miffCount = 5))
        assertEquals(SelectorTap.OpenCreate, MiffSelector.onTap(SelectorEntry.CREATE, premium = true, miffCount = 5))
    }

    // Offline, or the subscription could not be read: the plan is UNKNOWN (null). Never taken for "free": the screens and, in the end, the rules decide.
    @Test
    fun unknownPlan_isNeverTakenForFree() {
        assertEquals(SelectorTap.OpenJoin, MiffSelector.onTap(SelectorEntry.JOIN, premium = null, miffCount = 1))
        assertEquals(SelectorTap.OpenCreate, MiffSelector.onTap(SelectorEntry.CREATE, premium = null, miffCount = 1))
    }

    @Test
    fun freeAccountWithoutAMiff_hasReachedNoLimit_soTheOfferIsNotShown() {
        assertEquals(SelectorTap.OpenJoin, MiffSelector.onTap(SelectorEntry.JOIN, premium = false, miffCount = 0))
        assertEquals(SelectorTap.OpenCreate, MiffSelector.onTap(SelectorEntry.CREATE, premium = false, miffCount = 0))
    }
}
