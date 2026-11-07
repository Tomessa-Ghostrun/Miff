package com.tomessa.miff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Where the "Abonnement" line is wired (ticket ABO-1), checked on the SOURCE so that a later change cannot break it unnoticed: it exists in the
 * FULL Settings only, behind the answer of the single read (`subscription?.let`); no other list (the restricted Settings of an account with no
 * Miff, decision of the PM of 2026-11-07) ever receives the callback; the row itself is drawn only when the callback is given; and the screen
 * of the subscription reads nothing (its two values come with the route).
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
    fun theCallbackIsPassedOnce_inTheFullSettingsRoute_behindTheAnswerOfTheRead() {
        val text = mainShell
        assertEquals(
            "one single use of the callback in MainShell",
            1,
            Regex("""\bonOpenSubscription\b""").findAll(text).count(),
        )
        val block = routeBlock(text, "SettingsRoute")
        assertTrue("the callback is in the route of the full Settings", "onOpenSubscription" in block)
        assertTrue("the callback only exists once the read answered", "subscription?.let" in block)
        assertTrue(
            "the read is the ViewModel of that screen",
            "SubscriptionViewModel(container.subscriptionRepository)" in block,
        )
    }

    @Test
    fun theRowIsDrawnOnlyWhenTheCallbackIsGiven() {
        val text = code(File(main, "ui/settings/SettingsScreens.kt"))
        assertTrue("onOpenSubscription: (() -> Unit)? = null" in text)
        assertTrue("if (onOpenSubscription != null)" in text)
    }

    @Test
    fun theSubscriptionScreenReadsNothing_itsValuesComeWithTheRoute() {
        val block = routeBlock(mainShell, "SubscriptionRoute")
        assertTrue("toRoute<SubscriptionRoute>()" in block)
        assertFalse("no second read", "subscriptionRepository" in block || "SubscriptionViewModel" in block)
        val screen = code(File(main, "ui/settings/SubscriptionScreen.kt"))
        assertFalse(
            "the screen has no data access",
            "Repository" in screen || "Firestore" in screen || "Firebase" in screen,
        )
    }

    @Test
    fun nothingWritesEntitlements_noPaymentSdk() {
        val offenders =
            sources.filter {
                val text = code(it)
                Regex("""collection\("entitlements"\)[\s\S]{0,80}\.(set|update|delete)\(""").containsMatchIn(text) ||
                    "billingclient" in text.lowercase() ||
                    "com.android.billingclient" in text
            }
        assertEquals(emptyList<File>(), offenders)
    }
}
