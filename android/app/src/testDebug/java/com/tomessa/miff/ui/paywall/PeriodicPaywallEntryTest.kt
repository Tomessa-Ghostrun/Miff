package com.tomessa.miff.ui.paywall

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tomessa.miff.domain.analytics.AnalyticsSink
import com.tomessa.miff.domain.analytics.AnalyticsTracker
import com.tomessa.miff.domain.paywall.PeriodicPaywallCounterStore
import com.tomessa.miff.domain.paywall.PeriodicPaywallDecision
import com.tomessa.miff.domain.paywall.PeriodicPaywallRules
import com.tomessa.miff.domain.paywall.PeriodicPaywallSituation
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.ui.place.PlacePaywallRoute
import com.tomessa.miff.ui.place.paywallRoute
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
 * PP11 and PP21, through the REAL offer destination of the app: EVERY entry (a 4th place, a 2nd Miff, a danger zone, the Abonnement screen, and the periodic rule itself) sends the same
 * signal when the offer is really on view, and it sends it even without the consent to the statistics; the periodic entry has its own fixed trigger and nothing else travels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w390dp-h844dp-port-xxhdpi")
class PeriodicPaywallEntryTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class MemoryStore(
        var value: Int = 10,
    ) : PeriodicPaywallCounterStore {
        override fun read() = value

        override fun write(count: Int) {
            value = count
        }

        override fun clear() {
            value = 0
        }
    }

    private class Event(
        val name: String,
        val params: Map<String, Any>,
    )

    private val events = mutableListOf<Event>()
    private val clock = FakePaywallClock()
    private val store = MemoryStore()
    private val rules = PeriodicPaywallRules(store, clock)
    private var consent = true
    private lateinit var nav: NavHostController

    private val tracker =
        AnalyticsTracker(
            sink =
                object : AnalyticsSink {
                    override fun log(
                        eventName: String,
                        params: Map<String, Any>,
                    ) {
                        events += Event(eventName, params)
                    }
                },
            isEnabled = { consent },
        )

    private val calm =
        PeriodicPaywallSituation(
            miffPlus = false,
            tabsInFront = true,
            updateScreenOpen = false,
            keyboardOpen = false,
            messageOpen = false,
            ownAlertInProgress = false,
            lovedOneAlertUnseen = false,
        )

    private fun host() {
        rules.onForeground()
        rules.onShellShown()
        composeRule.setContent {
            nav = rememberNavController()
            MiffTheme {
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") { Text("home") }
                    paywallRoute(tracker, clock, nav, periodic = rules)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun open(reason: PaywallReason) {
        composeRule.runOnUiThread { nav.navigate(PlacePaywallRoute.of(reason)) { launchSingleTop = true } }
        composeRule.waitForIdle()
    }

    private fun tapAndSettle() {
        rules.onTouch(pressed = true)
        rules.onActivation()
        rules.onTouch(pressed = false)
        clock.advance(2_000L)
    }

    private fun everyEntryResetsTheCounter(reason: PaywallReason) {
        host()
        assertEquals(10, store.value)
        open(reason)
        assertEquals("${reason.name}: back to zero when the offer is on view", 0, store.value)
        // And the periodic rule leaves this session alone: the person has just seen the offer.
        store.value = 10
        tapAndSettle()
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm))
    }

    @Test
    fun aFourthPlace_resetsTheCounter() = everyEntryResetsTheCounter(PaywallReason.LIMIT)

    @Test
    fun aSecondMiff_resetsTheCounter() = everyEntryResetsTheCounter(PaywallReason.MIFF)

    @Test
    fun aDangerZone_resetsTheCounter() = everyEntryResetsTheCounter(PaywallReason.DANGER)

    @Test
    fun theAbonnementScreen_resetsTheCounter() = everyEntryResetsTheCounter(PaywallReason.SUBSCRIPTION)

    @Test
    fun thePeriodicOffer_resetsTheCounter() = everyEntryResetsTheCounter(PaywallReason.PERIODIC)

    @Test
    fun theResetDoesNotDependOnTheConsentToTheStatistics_andNoEventIsSentWithout() {
        consent = false
        host()
        open(PaywallReason.PERIODIC)
        assertEquals(0, store.value)
        assertTrue("no consent, no event", events.isEmpty())
    }

    @Test
    fun withConsent_thePeriodicOfferSendsOnlyTheFixedTrigger() {
        host()
        open(PaywallReason.PERIODIC)
        val shown = events.single { it.name == "paywall_shown" }
        assertEquals(mapOf<String, Any>("trigger" to "periodic"), shown.params)
        assertTrue("the counter is in no event", events.none { e -> e.params.values.any { it is Int } })
    }

    @Test
    fun theWholeChain_theRuleDecides_theRouteOpens_theCounterGoesToZero() {
        host()
        tapAndSettle()
        assertEquals(PeriodicPaywallDecision.Show, rules.evaluate(calm))
        rules.onLaunched()
        open(PaywallReason.PERIODIC)
        assertEquals(0, store.value)
        assertEquals(PeriodicPaywallDecision.No, rules.evaluate(calm))
    }
}
