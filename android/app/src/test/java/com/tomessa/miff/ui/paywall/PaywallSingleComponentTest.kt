package com.tomessa.miff.ui.paywall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CR10 and CR12 read in the SOURCE (the navigation needs the whole container, so the wiring is checked where it is written): there is ONE offer screen, ONE
 * destination, and the delay is written ONE time. A future entry that opens the offer another way, or a second copy of the delay, makes this test fail.
 */
class PaywallSingleComponentTest {
    private val root = File("src/main/java/com/tomessa/miff")

    private fun sources(): List<File> = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun relative(file: File) = file.relativeTo(root).path.replace('\\', '/')

    @Test
    fun thereIsOneOfferScreen_theOldOneIsGone() {
        val screens =
            sources().filter { file ->
                Regex("""fun\s+\w*Paywall\w*Screen\s*\(""").containsMatchIn(file.readText())
            }
        assertEquals(listOf("ui/paywall/PaywallScreen.kt"), screens.map { relative(it) })
        assertFalse(sources().any { it.readText().contains("PlacePaywallScreen") })
    }

    @Test
    fun theOfferRoute_isOnlyNamedByTheEntriesAndTheDestination() {
        val users = sources().filter { it.readText().contains("PlacePaywallRoute") }.map { relative(it) }.sorted()
        assertEquals(
            "a new entry of the offer must be listed here, and covered by PaywallEntryPointsTest",
            listOf("ui/main/MainShell.kt", "ui/place/PlaceNavigation.kt", "ui/profile/MiffCarousel.kt"),
            users,
        )
    }

    @Test
    fun theOfferIsPluggedInTheGraphOnce_throughTheSharedDestination() {
        val navigation = File(root, "ui/place/PlaceNavigation.kt").readText()
        assertEquals(1, Regex("composable<PlacePaywallRoute>").findAll(navigation).count())
        assertEquals(1, Regex("""paywallRoute\(container""").findAll(navigation).count())
        val all = sources().joinToString("\n") { it.readText() }
        val callers = Regex("""(?<!fun )\bPaywallScreen\(""").findAll(all).count()
        assertEquals("only the destination builds the screen", 1, callers)
    }

    @Test
    fun theDelayIsWrittenOnce_andNothingElseReadsAFixedThreeSeconds() {
        val holders =
            sources()
                .filter {
                    Regex("""\b3_?000L?\b""").containsMatchIn(it.readText()) &&
                        relative(it).contains("paywall/")
                }.map { relative(it) }
        assertEquals(listOf("domain/paywall/PaywallCloseGate.kt"), holders)
        val screen = File(root, "ui/paywall/PaywallScreen.kt").readText()
        assertFalse("the screen never hard-codes the delay", Regex("""\b3_?000\b""").containsMatchIn(screen))
        assertTrue(File(root, "ui/paywall/PaywallViewModel.kt").readText().contains("PaywallCloseDelay.MILLIS"))
    }

    @Test
    fun theScreenHasOneContent_itNeverKnowsWhereItWasOpenedFrom() {
        // Decision of the PM (2026-11-12): no sentence of context, no card highlighted by entry. The entry only travels to the analytics trigger.
        val screen = File(root, "ui/paywall/PaywallScreen.kt").readText()
        assertFalse(screen.contains("PaywallReason"))
        assertFalse(screen.contains("ContextCard"))
        listOf(
            "place_paywall_limit_",
            "place_paywall_danger_",
            "feed_selector_paywall_",
            "place_request_paywall_",
            "place_paywall_soon_body",
        ).forEach {
            assertFalse(it, screen.contains(it))
        }
    }

    @Test
    fun theOfferHasNoWayOutButTheCross_noLaterButton_noContinueForFree() {
        val screen = File(root, "ui/paywall/PaywallScreen.kt").readText()
        assertFalse(screen.contains("place_paywall_later"))
        assertTrue(
            "back and Escape go through the same rule",
            screen.contains("BackHandler") && screen.contains("Key.Escape"),
        )
    }

    @Test
    fun theJoinAndCreateScreens_showAnInlineMessage_notTheOffer() {
        // A 2nd Miff asked with a code (Join) or created (Create) at the limit gets the calm inline message "une seule Miff": the offer screen is NOT shown
        // there (they are reached only after the selector or the card of the carousel have shown the offer). Written as a test so that a future change
        // that opens the offer from them is seen: it would have to be covered by PaywallEntryPointsTest.
        listOf("ui/miff/JoinMiffViewModel.kt", "ui/miff/CreateMiffViewModel.kt").forEach { path ->
            assertFalse(path, File(root, path).readText().contains("PlacePaywallRoute"))
        }
    }

    @Test
    fun noNotificationsIntelligentesInTheOffer() {
        val res = File("src/main/res")
        listOf("values/strings.xml", "values-fr/strings.xml").forEach { file ->
            val text = File(res, file).readText()
            assertFalse(file, text.contains("Notifications intelligentes", ignoreCase = true))
            assertFalse(file, text.contains("Smart notifications", ignoreCase = true))
        }
    }
}
