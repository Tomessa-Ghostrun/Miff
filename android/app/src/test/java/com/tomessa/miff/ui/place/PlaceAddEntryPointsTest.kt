package com.tomessa.miff.ui.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "Ajouter un lieu" opens the FORM in every case (LOC-1 fix): the form reads the subscription on the server and shows the offer
 * itself at the real limit. These two entry points hold the navigation (they need the whole container and a NavController), so
 * the wiring is checked in the source: neither may decide "free plan" from its own copy of the plan, nor open the offer.
 * The behaviour of the Miff screen is also tested for real in `MiffScreenHostTest`.
 */
class PlaceAddEntryPointsTest {
    private fun source(path: String): String = File("src/main/java/com/tomessa/miff/$path").readText()

    @Test
    fun profilePlacesSection_passesTheAddActionStraightToTheForm() {
        val code = source("ui/place/PlaceHosts.kt")
        assertTrue(code.contains("onAdd = onAddPlace,"))
        assertFalse("no offer from the list", code.contains("PaywallReason"))
        assertFalse("no decision on the plan from the list", code.contains("atFreeLimit"))
        assertFalse(code.contains("onOpenOffer"))
    }

    @Test
    fun profileTab_opensTheForm_whateverThePlan() {
        val code = source("ui/main/MainShell.kt")
        assertTrue(code.contains("onAddPlace = { navController.open(PlaceFormRoute()) },"))
        // The only offer opened from the shell is the 2nd Miff, asked from the selector of the Feed (decision of the PM, 2026-11-08); never for a place.
        // (The last card of the carousel of the Profile asks for the same offer through `miffOfferRoute()`, in ui/profile.) Zero or one such route here,
        // depending on whether the selector of the Feed already is in the shell: never anything else.
        val offers = Regex("""PlacePaywallRoute[.]of[(][^)]*[)]""").findAll(code).map { it.value }.toList()
        assertTrue("unexpected offer: $offers", offers.all { it == "PlacePaywallRoute.of(PaywallReason.MIFF)" })
        assertFalse(code.contains("PaywallReason.LIMIT"))
        assertFalse(code.contains("PaywallReason.DANGER"))
    }

    @Test
    fun miffScreenRoute_opensTheForm_whateverThePlan() {
        val code = source("ui/miff/MiffNavigation.kt")
        assertTrue(code.contains("onAddPlace = { navController.openMiffRoute(PlaceFormRoute()) },"))
        assertFalse(
            "the offer is only opened by the form itself",
            code.contains("PlacePaywallRoute.of(PaywallReason.LIMIT)"),
        )
        assertEquals("no `atLimit` branch is left", 0, Regex("atLimit|atFreeLimit").findAll(code).count())
    }

    @Test
    fun miffScreen_passesTheAddActionStraightThrough() {
        val code = source("ui/miff/MiffScreen.kt")
        assertTrue(code.contains("onAdd = placesExtras.onAddPlace,"))
        assertFalse(code.contains("atFreeLimit"))
    }
}
