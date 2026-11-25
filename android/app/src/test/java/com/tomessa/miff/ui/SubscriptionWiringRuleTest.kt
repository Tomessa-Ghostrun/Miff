package com.tomessa.miff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Where the "Abonnement" line and screen are wired (tickets ABO-1 and premium-achat-reel.md), checked on the SOURCE so that a later change cannot break it unnoticed: the line exists in
 * the FULL Settings only, behind the answer of the view model of the row; no other list (the restricted Settings of an account with no Miff, decision of the PM of 2026-11-07) ever
 * receives the callback; the row itself is drawn only when the callback is given; the screen has no data access of its own and offers no way to cancel; and NOTHING in the app writes
 * `entitlements` (the rights are written by the server only).
 */
class SubscriptionWiringRuleTest {
    private val main = File("src/main/java/com/tomessa/miff")

    private val sources = main.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun code(file: File) =
        file
            .readLines()
            .filter {
                val line = it.trimStart()
                !line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*")
            }.joinToString("\n")

    private val mainShell get() = code(File(main, "ui/main/MainShell.kt"))

    /** The body of a `composable<Route> { ... }` block: from its opening to the next `composable<` (or the end of the file). */
    private fun routeBlock(
        text: String,
        route: String,
    ): String {
        val start = text.indexOf("composable<$route>")
        assertTrue("composable<$route> not found", start >= 0)
        val next = text.indexOf("composable<", start + 1)
        return text.substring(start, if (next < 0) text.length else next)
    }

    @Test
    fun theSourcesAreFound() {
        assertTrue("sources not found from ${File(".").absolutePath}", sources.size > 100)
    }

    @Test
    fun onlyTheSettingsListAndTheFullSettingsRouteKnowTheSubscriptionCallback() {
        val files = sources.filter { "onOpenSubscription" in code(it) }.map { it.name }.sorted()
        assertEquals(listOf("MainShell.kt", "SettingsScreens.kt"), files)
    }

    @Test
    fun theCallbackIsPassedOnce_inTheFullSettingsRoute_behindTheViewModelOfTheRow() {
        val text = mainShell
        assertEquals(
            "one single use of the callback in MainShell",
            1,
            Regex("""\bonOpenSubscription\b""").findAll(text).count(),
        )
        val block = routeBlock(text, "SettingsRoute")
        assertTrue("the callback is in the route of the full Settings", "onOpenSubscription" in block)
        assertTrue("the callback only exists once the row says so", "if (subscriptionVisible)" in block)
        // Whatever the line breaks of the formatter: the view model of THIS row is built from the repository and the switch of the purchase.
        assertTrue(
            "the row is the view model of that screen",
            Regex(
                """SubscriptionRowViewModel\(\s*container\.subscriptionRepository\s*,\s*BuildConfig\.PREMIUM_PURCHASE\s*,?\s*\)""",
            ).containsMatchIn(block),
        )
    }

    @Test
    fun theListOfMiffs_isReadAgain_whenMiffPlusIsGranted_soTheGatesNeverOpenTheOfferForSomeoneWhoHasIt() {
        // Regression: after the trial, "Créer une Miff" opened the offer (the list still held "free"), which answered "Tu as Miff+".
        val text = mainShell
        assertTrue(
            "MainShell follows the grants of Miff+ to put the plan of the list right",
            Regex("""miffDirectory\s*\.followPremiumGrants\(\s*premium\.accessGranted\s*\)""").containsMatchIn(text),
        )
    }

    @Test
    fun theRowIsDrawnOnlyWhenTheCallbackIsGiven() {
        val text = code(File(main, "ui/settings/SettingsScreens.kt"))
        assertTrue("onOpenSubscription: (() -> Unit)? = null" in text)
        assertTrue("if (onOpenSubscription != null)" in text)
    }

    @Test
    fun theSubscriptionScreen_hasItsOwnViewModel_andOnlyAsksTheRefreshWhenThePurchaseIsOn() {
        val block = routeBlock(mainShell, "SubscriptionRoute")
        assertTrue(
            "the view model reads the status and talks to the coordinator",
            Regex(
                """SubscriptionViewModel\(\s*container\.subscriptionRepository\s*,\s*premium\s*,?\s*\)""",
            ).containsMatchIn(block),
        )
        // Whatever the line breaks of the formatter.
        assertTrue(
            "the refresh exists only while the purchase is on",
            Regex("""onRefresh\s*=\s*if \(premium != null\)""").containsMatchIn(block),
        )
        // The offer opens from here with its own trigger, and the page of the store is the only way to cancel.
        assertTrue("PlacePaywallRoute.of(PaywallReason.SUBSCRIPTION)" in block)
        assertTrue(Regex("""MiffPlusProducts\s*\.manageUrlFor""").containsMatchIn(block))
    }

    @Test
    fun theSubscriptionScreen_hasNoDataAccess_andNoButtonToCancel() {
        val screen = code(File(main, "ui/settings/SubscriptionScreen.kt"))
        assertFalse(
            "the screen has no data access",
            "Repository" in screen || "Firestore" in screen || "Firebase" in screen || "billingclient" in screen,
        )
        // Cancelling, resuming and changing the plan happen on the page of the store (criterion AN10): no "Arrêter" in the app.
        assertFalse(
            Regex(
                """(?i)stop|cancel_subscription|arrêter""",
            ).containsMatchIn(screen.replace("R.string.settings_subscription_", "")),
        )
    }

    @Test
    fun nothingWritesEntitlements() {
        val offenders =
            sources.filter {
                Regex("""collection\("entitlements"\)[\s\S]{0,80}\.(set|update|delete)\(""").containsMatchIn(code(it))
            }
        assertEquals(emptyList<File>(), offenders)
    }

    @Test
    fun thePaymentLibraryIsUsedByOneFileOnly_andTheAppNeverAcknowledgesOrConsumesAPurchase() {
        val users = sources.filter { "com.android.billingclient" in code(it) }.map { it.name }
        assertEquals(listOf("PlayBillingGateway.kt"), users)
        // The SERVER acknowledges a purchase after checking it (otherwise Google refunds it): the app never does, and never consumes anything.
        val offenders =
            sources.filter {
                Regex(
                    """acknowledgePurchase|consumePurchase|consumeAsync|AcknowledgePurchaseParams""",
                ).containsMatchIn(code(it))
            }
        assertEquals(emptyList<File>(), offenders)
    }
}
