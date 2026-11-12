package com.tomessa.miff.ui.paywall

import android.app.Application
import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.domain.analytics.AnalyticsSink
import com.tomessa.miff.domain.analytics.AnalyticsTracker
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.ui.place.PlacePaywallRoute
import com.tomessa.miff.ui.place.paywallRoute
import com.tomessa.miff.ui.profile.miffOfferRoute
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * CR10, one test per ENTRY of the offer, through the REAL destination of the app (`paywallRoute`, the same one `placeRoutes` plugs in): the route that the
 * entry opens leads to the same screen with the same rule of the cross. Each entry: the cross is absent at the start, "back" is ignored until the end of the
 * delay, then the cross is there and "back" closes; `paywall_shown` once, `paywall_dismissed` once, with the trigger of the contract. A reopening starts the
 * 3 seconds again (CR4). A real NavHost, real transitions (Compose's clock, moved by hand).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w390dp-h844dp-port-xxhdpi")
class PaywallEntryPointsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private class Event(
        val name: String,
        val params: Map<String, Any>,
        val at: Long,
    )

    private val events = mutableListOf<Event>()
    private val clock = FakePaywallClock()
    private val tracker =
        AnalyticsTracker(
            sink =
                object : AnalyticsSink {
                    override fun log(
                        eventName: String,
                        params: Map<String, Any>,
                    ) {
                        events += Event(eventName, params, clock.now)
                    }
                },
            isEnabled = { true },
        )

    private lateinit var nav: NavHostController
    private lateinit var dispatcher: OnBackPressedDispatcher

    private fun host() {
        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            nav = rememberNavController()
            MiffTheme {
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") { Text("home") }
                    // The destination the app plugs into its graph (PlaceNavigation.placeRoutes): the only one.
                    paywallRoute(tracker, clock, nav)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun open(route: Any) {
        composeRule.runOnUiThread { nav.navigate(route) { launchSingleTop = true } }
        composeRule.waitForIdle()
    }

    private fun shownEvents() = events.filter { it.name == "paywall_shown" }

    private fun dismissedEvents() = events.filter { it.name == "paywall_dismissed" }

    /** The entry is on view once its transition is over (the clock of Compose runs it): the event says when, on OUR clock. */
    private fun waitUntilShown(expected: Int): Long {
        composeRule.waitForIdle()
        assertEquals("paywall_shown was sent", expected, shownEvents().size)
        return shownEvents().last().at
    }

    private fun pass(millis: Long) {
        clock.advance(millis)
        composeRule.waitForIdle()
    }

    private fun onPaywall(): Boolean {
        var result = false
        composeRule.runOnUiThread {
            result =
                nav.currentBackStackEntry
                    ?.destination
                    ?.route
                    ?.contains("PlacePaywallRoute") == true
        }
        return result
    }

    private fun cross() = composeRule.onAllNodesWithContentDescription(text(R.string.place_paywall_close_a11y))

    private fun back() = composeRule.runOnUiThread { dispatcher.onBackPressed() }

    /** The whole life of one display, for one entry. */
    private fun entryBehaves(
        route: Any,
        trigger: String,
    ) {
        host()
        open(route)
        val shownAt = waitUntilShown(1)
        assertTrue("the offer is on screen", onPaywall())
        assertEquals(trigger, shownEvents().single().params["trigger"])
        cross().assertCountEquals(0)

        // Before the end: back does nothing, a touch where the cross will be does nothing.
        pass(1_500)
        back()
        pass(300)
        assertTrue("back is ignored: still on the offer", onPaywall())
        assertEquals(0, dismissedEvents().size)
        pass(shownAt + 2_800 - clock.now)
        cross().assertCountEquals(0)

        // At the end: the cross is there.
        pass(300)
        cross().assertCountEquals(1)
        composeRule.onNodeWithContentDescription(text(R.string.place_paywall_close_a11y)).performClick()
        pass(1_000)
        assertTrue("the cross closed the offer: back on the previous screen", !onPaywall())
        composeRule.onAllNodesWithText("home").assertCountEquals(1)
        assertEquals("paywall_shown stays at one", 1, shownEvents().size)
        assertEquals(1, dismissedEvents().size)
        assertEquals(trigger, dismissedEvents().single().params["trigger"])
        // Nothing but the fixed trigger travels (no place, no name, no position, no duration).
        (shownEvents() + dismissedEvents()).forEach { assertEquals(setOf("trigger"), it.params.keys) }
    }

    @Test
    fun entry_aFourthPlace_theLimitOfThreePlaces() =
        entryBehaves(PlacePaywallRoute.of(PaywallReason.LIMIT), "extra_trusted_place")

    @Test
    fun entry_aSecondMiff_fromTheSelectorOfTheFeed() =
        entryBehaves(PlacePaywallRoute.of(PaywallReason.MIFF), "extra_miff")

    @Test
    fun entry_aSecondMiff_fromTheCardOfTheCarouselOfTheProfile() = entryBehaves(miffOfferRoute(), "extra_miff")

    // "Accepter" on a place request received (LIEU-REQ-C): the same route with the request flag, through `openRequestOffer`.
    @Test
    fun entry_acceptingARequest_atTheLimit() =
        entryBehaves(PlacePaywallRoute.of(PaywallReason.LIMIT, request = true), "extra_trusted_place")

    @Test
    fun entry_acceptingARequest_forADangerZone() =
        entryBehaves(PlacePaywallRoute.of(PaywallReason.DANGER, request = true), "danger_zone")

    @Test
    fun entry_aDangerZone() = entryBehaves(PlacePaywallRoute.of(PaywallReason.DANGER), "danger_zone")

    @Test
    fun entry_backClosesAfterTheDelay_andTheDismissalIsCounted() {
        host()
        open(PlacePaywallRoute.of(PaywallReason.LIMIT))
        val shownAt = waitUntilShown(1)
        pass(shownAt + 3_300 - clock.now)
        back()
        pass(1_000)
        assertTrue(!onPaywall())
        assertEquals("back after the delay closes like the cross, and counts like it", 1, dismissedEvents().size)
    }

    @Test
    fun reopening_startsTheThreeSecondsAgain_fromAnyEntry() {
        host()
        open(PlacePaywallRoute.of(PaywallReason.LIMIT))
        var shownAt = waitUntilShown(1)
        pass(shownAt + 3_300 - clock.now)
        cross().assertCountEquals(1)
        composeRule.onNodeWithContentDescription(text(R.string.place_paywall_close_a11y)).performClick()
        pass(1_000)
        assertTrue(!onPaywall())

        // One second later, from ANOTHER entry: a new display, a new countdown, nothing shared.
        pass(1_000)
        open(miffOfferRoute())
        shownAt = waitUntilShown(2)
        cross().assertCountEquals(0)
        back()
        pass(200)
        assertTrue("back is ignored again", onPaywall())
        pass(shownAt + 2_800 - clock.now)
        cross().assertCountEquals(0)
        pass(300)
        cross().assertCountEquals(1)
        assertEquals(2, shownEvents().size)
    }

    @Test
    fun twoQuickOpenings_showOneOffer() {
        host()
        composeRule.runOnUiThread {
            nav.navigate(PlacePaywallRoute.of(PaywallReason.LIMIT)) { launchSingleTop = true }
            nav.navigate(PlacePaywallRoute.of(PaywallReason.LIMIT)) { launchSingleTop = true }
        }
        waitUntilShown(1)
        pass(500)
        assertEquals(1, shownEvents().size)
        composeRule.runOnUiThread {
            assertEquals(2, nav.currentBackStack.value.count { it.destination.route != null })
        }
    }
}
