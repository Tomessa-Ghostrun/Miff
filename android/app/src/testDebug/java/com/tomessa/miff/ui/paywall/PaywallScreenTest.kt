package com.tomessa.miff.ui.paywall

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.domain.paywall.PaywallNotice
import com.tomessa.miff.domain.paywall.PaywallOffer
import com.tomessa.miff.domain.paywall.PaywallPrice
import com.tomessa.miff.domain.paywall.PaywallProgress
import com.tomessa.miff.domain.paywall.PaywallUiState
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The offer screen on the JVM (Robolectric, no phone): the close cross (criteria CR1 to CR13), the states of the offer (PW1 to PW13) with SIMULATED states
 * (no store exists yet), the accessibility and the largest font. Time is the test's: the clock of the screen IS the clock of Compose, moved by hand, so
 * nothing waits for 3 real seconds. Writes screenshots to build/screenshots for the pull request (compare with design/exports/paywall).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w390dp-h844dp-port-xxhdpi")
class PaywallScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private fun text(
        id: Int,
        vararg args: Any,
    ): String = context().getString(id, *args)

    private val clock = FakePaywallClock()
    private var closed = 0
    private var continued = 0
    private var started = 0
    private var boughtAnnual = 0
    private var boughtMonthly = 0
    private var retried = 0
    private var restored = 0
    private var shown = 0
    private val announced = mutableListOf<String>()
    private lateinit var dispatcher: androidx.activity.OnBackPressedDispatcher

    private val annual = PaywallPrice("59,88 €", 59_880_000L, "EUR")
    private val monthly = PaywallPrice("6,99 €", 6_990_000L, "EUR")
    private val trial = PaywallUiState(PaywallOffer.TrialEligible(annual, monthly))
    private val plans = PaywallUiState(PaywallOffer.Plans(annual, monthly))
    private val soon = PaywallUiState.PRODUCTION

    private val actions
        get() =
            PaywallActions(
                onClose = { closed++ },
                onStartTrial = { started++ },
                onBuyAnnual = { boughtAnnual++ },
                onBuyMonthly = { boughtMonthly++ },
                onRetry = { retried++ },
                onRestore = { restored++ },
                onContinue = { continued++ },
            )

    private fun host(
        reason: PaywallReason = PaywallReason.LIMIT,
        state: PaywallUiState = soon,
        model: PaywallViewModel = PaywallViewModel(clock),
        fontScale: Float? = null,
        reduceMotion: Boolean = false,
        owner: LifecycleOwner? = null,
    ): PaywallViewModel {
        composeRule.setContent {
            val density = LocalDensity.current
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale ?: density.fontScale),
            ) {
                MiffTheme {
                    Box(Modifier.fillMaxSize()) {
                        val content: @Composable () -> Unit = {
                            PaywallScreen(
                                reason = reason,
                                model = model,
                                state = state,
                                actions = actions,
                                onShown = { shown++ },
                                announce = { announced += it },
                                reduceMotion = reduceMotion,
                            )
                        }
                        if (owner == null) {
                            content()
                        } else {
                            CompositionLocalProvider(LocalLifecycleOwner provides owner) { content() }
                        }
                    }
                }
            }
        }
        frames()
        return model
    }

    private fun frames() = composeRule.waitForIdle()

    private fun pass(millis: Long) {
        clock.advance(millis)
        composeRule.waitForIdle()
    }

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun cross() = composeRule.onAllNodesWithContentDescription(text(R.string.place_paywall_close_a11y))

    private fun assertCrossAbsent() = cross().assertCountEquals(0)

    private fun assertCrossThere() = cross().assertCountEquals(1)

    private fun pressBack() = composeRule.runOnUiThread { dispatcher.onBackPressed() }

    private fun textStyleOf(node: SemanticsNodeInteraction): TextStyle {
        val results = mutableListOf<TextLayoutResult>()
        node
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action!!
            .invoke(results)
        return results.first().layoutInput.style
    }

    private fun nothingToBuy() {
        listOf("€", "essai", "Plus tard", "Notifications", "Premium", "%").forEach {
            composeRule.onAllNodesWithText(it, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }

    // --- The cross: absent for 3 s (CR1, CR2) ---------------------------------------------------------------------------------------

    @Test
    fun cross_isAbsentAtTheStart_notInTheTree_notTouchable() {
        host()
        assertCrossAbsent()
        // Absent for the merged tree and the raw tree alike (a screen reader, a keyboard and a finger read nothing there).
        composeRule
            .onAllNodesWithContentDescription(text(R.string.place_paywall_close_a11y), useUnmergedTree = true)
            .assertCountEquals(0)
        // Where the cross will be (start of the screen, top): a touch there does nothing.
        composeRule.onRoot().performTouchInput { click(Offset(40.dp.toPx(), 32.dp.toPx())) }
        assertEquals(0, closed)
        shoot("paywall-croix-absente")
    }

    @Test
    fun cross_appearsAfterThreeSeconds_andNotBefore() {
        host()
        pass(2_800)
        assertCrossAbsent()
        pass(300)
        assertCrossThere()
        composeRule.onNodeWithContentDescription(text(R.string.place_paywall_close_a11y)).assertIsDisplayed()
        shoot("paywall-croix-presente")
    }

    @Test
    fun cross_aTouchAtItsPlaceClosesOnlyOnceItIsThere() {
        host()
        pass(2_800)
        composeRule.onRoot().performTouchInput { click(Offset(40.dp.toPx(), 32.dp.toPx())) }
        assertEquals("a touch before the end does nothing", 0, closed)
        pass(300)
        composeRule.onNodeWithContentDescription(text(R.string.place_paywall_close_a11y)).performClick()
        assertEquals(1, closed)
    }

    @Test
    fun cross_itsPlaceIsReserved_nothingMovesWhenItAppears() {
        host()
        val headline = composeRule.onNodeWithText(text(R.string.place_paywall_headline))
        val before = headline.fetchSemanticsNode().boundsInRoot
        val cardBefore =
            composeRule
                .onNodeWithText(
                    text(R.string.place_paywall_feature_places),
                ).fetchSemanticsNode()
                .boundsInRoot
        pass(3_300)
        assertCrossThere()
        assertEquals(before, headline.fetchSemanticsNode().boundsInRoot)
        assertEquals(
            cardBefore,
            composeRule.onNodeWithText(text(R.string.place_paywall_feature_places)).fetchSemanticsNode().boundsInRoot,
        )
    }

    @Test
    fun cross_touchTargetIs48dp_atTheLargestFontToo() {
        host(fontScale = 2f)
        pass(3_300)
        composeRule
            .onNodeWithContentDescription(text(R.string.place_paywall_close_a11y))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun cross_isLabelledClose_asAButton() {
        host()
        pass(3_300)
        val node =
            composeRule
                .onNodeWithContentDescription(
                    text(R.string.place_paywall_close_a11y),
                ).fetchSemanticsNode()
        assertTrue(node.config.contains(SemanticsActions.OnClick))
        assertEquals("Fermer", text(R.string.place_paywall_close_a11y))
    }

    // --- Back and Escape (CR6) ------------------------------------------------------------------------------------------------------

    @Test
    fun back_isIgnoredDuringTheDelay_thenCloses() {
        host()
        pass(1_000)
        pressBack()
        pressBack()
        assertEquals("the system back does nothing for 3 s", 0, closed)
        assertFalse(composeRule.onAllNodesWithText(text(R.string.place_paywall_soon)).fetchSemanticsNodes().isEmpty())
        pass(2_500)
        assertCrossThere()
        pressBack()
        assertEquals(1, closed)
    }

    @Test
    fun escape_followsTheSameRuleAsBack() {
        // The key handler sits on the root; a keyboard cannot be driven reliably under Robolectric (no focus), so the decision is the tested function.
        assertEquals(EscapeOutcome.NOT_ESCAPE, escapeOutcome(isEscape = false, isKeyUp = true, closeVisible = true))
        assertEquals("swallowed during the delay, press", EscapeOutcome.IGNORED, escapeOutcome(true, false, false))
        assertEquals("swallowed during the delay, release", EscapeOutcome.IGNORED, escapeOutcome(true, true, false))
        assertEquals("press alone does not close", EscapeOutcome.IGNORED, escapeOutcome(true, false, true))
        assertEquals("release closes once the cross is there", EscapeOutcome.CLOSE, escapeOutcome(true, true, true))
    }

    // --- Survives a rotation, a theme, a text size, a trip to the background (CR5) ----------------------------------------------------

    @Test
    fun rotation_keepsTheCross_doesNotRestartTheDelay_doesNotSendShownTwice_doesNotAnnounceTwice() {
        val model = PaywallViewModel(clock)
        val visible = mutableStateOf(true)
        composeRule.setContent {
            MiffTheme {
                if (visible.value) {
                    PaywallScreen(
                        reason = PaywallReason.MIFF,
                        model = model,
                        state = soon,
                        actions = actions,
                        onShown = { shown++ },
                        announce = { announced += it },
                        reduceMotion = true,
                    )
                }
            }
        }
        frames()
        pass(3_300)
        assertCrossThere()
        assertEquals(1, shown)
        assertEquals(1, announced.size)
        // The activity is recreated by the rotation: the screen is built again, the model (scoped to the navigation entry) is the same.
        composeRule.runOnUiThread { visible.value = false }
        frames()
        assertCrossAbsent()
        composeRule.runOnUiThread { visible.value = true }
        frames()
        assertCrossThere()
        assertEquals("paywall_shown is sent once per display", 1, shown)
        assertEquals("the cross was announced once", 1, announced.size)
    }

    @Test
    fun textSizeAndLanguage_afterTheCross_changeNothing() {
        val scale = mutableFloatStateOf(1f)
        val model = PaywallViewModel(clock)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = scale.floatValue)) {
                MiffTheme {
                    PaywallScreen(PaywallReason.DANGER, model, soon, actions, onShown = { shown++ }, announce = {
                        announced +=
                            it
                    })
                }
            }
        }
        frames()
        pass(3_300)
        assertCrossThere()
        composeRule.runOnUiThread { scale.floatValue = 2f }
        frames()
        assertCrossThere()
        composeRule.runOnUiThread { scale.floatValue = 1f }
        frames()
        assertCrossThere()
        assertEquals(1, shown)
        assertEquals(1, announced.size)
    }

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun TestOwner.moveTo(state: Lifecycle.State) = composeRule.runOnUiThread { registry.currentState = state }

    @Test
    fun theCountdownStartsWhenTheScreenIsReallyOnView_notWhenItIsCreated() {
        val owner =
            TestOwner().also {
                it.moveTo(Lifecycle.State.STARTED)
            } // composed, but not in the foreground yet (the entry animation, an app switch)
        host(owner = owner)
        pass(10_000)
        assertEquals("not on view: nothing started, nothing sent", 0, shown)
        assertCrossAbsent()
        owner.moveTo(Lifecycle.State.RESUMED)
        frames()
        assertEquals(1, shown)
        pass(2_800)
        assertCrossAbsent()
        pass(300)
        assertCrossThere()
    }

    @Test
    fun aTripToTheBackground_beforeTheEnd_theTimeKeepsCounting() {
        val owner = TestOwner().also { it.moveTo(Lifecycle.State.RESUMED) }
        host(owner = owner)
        pass(1_000)
        assertCrossAbsent()
        owner.moveTo(Lifecycle.State.STARTED) // another app on top
        frames()
        pass(5_000)
        owner.moveTo(Lifecycle.State.RESUMED) // back
        frames()
        pass(100)
        assertCrossThere()
        assertEquals("one display, one event", 1, shown)
    }

    @Test
    fun aTripToTheBackground_afterTheCross_changesNothing() {
        val owner = TestOwner().also { it.moveTo(Lifecycle.State.RESUMED) }
        host(owner = owner)
        pass(3_300)
        assertCrossThere()
        owner.moveTo(Lifecycle.State.STARTED)
        frames()
        owner.moveTo(Lifecycle.State.RESUMED)
        frames()
        pass(50)
        assertCrossThere()
        assertEquals(1, shown)
        assertEquals(1, announced.size)
    }

    // --- Screen reader (CR8) --------------------------------------------------------------------------------------------------------

    @Test
    fun screenReader_isToldOnceWhenTheCrossAppears_neverBefore() {
        host()
        pass(2_800)
        assertTrue("nothing said before", announced.isEmpty())
        pass(300)
        assertEquals(listOf("Tu peux maintenant fermer cet écran"), announced)
        pass(5_000)
        assertEquals("said once", 1, announced.size)
    }

    @Test
    fun screenReader_theAnnouncementDoesNotMoveTheFocus_itIsASentenceNotANode() {
        host()
        pass(3_300)
        // The sentence is spoken by the announce callback: no node of the tree carries it, so the focus has nothing to jump to.
        composeRule.onAllNodesWithText(text(R.string.paywall_close_available_a11y)).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(text(R.string.paywall_close_available_a11y)).assertCountEquals(0)
    }

    @Test
    fun screenReader_theScreenHasATitle_andNothingBlocksTheExploration() {
        host(state = trial)
        val root = composeRule.onRoot().fetchSemanticsNode()
        assertNotNull(root)
        composeRule.onNodeWithText(text(R.string.place_paywall_headline)).assertIsDisplayed()
        // The cross is not the only way to read on: everything stays reachable while it is absent.
        composeRule.onNodeWithText(text(R.string.paywall_cta_trial)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.paywall_terms_link)).performScrollTo().assertIsDisplayed()
    }

    // --- The delay is ONE value (CR12) and the cross always comes (CR13) ----------------------------------------------------------------

    @Test
    fun aDelayOfZero_switchesTheRuleOff_theCrossIsThereAtOnce() {
        host(model = PaywallViewModel(clock, delayMillis = 0))
        assertCrossThere()
        assertTrue("nothing appears, nothing is announced", announced.isEmpty())
        pressBack()
        assertEquals(1, closed)
    }

    @Test
    fun theCross_appearsEvenIfThePricesNeverLoad() {
        host(state = PaywallUiState(PaywallOffer.Loading))
        pass(3_300)
        assertCrossThere()
    }

    private fun crossComesIn(state: PaywallUiState) {
        host(state = state, reduceMotion = true)
        assertCrossAbsent()
        pass(3_300)
        assertCrossThere()
    }

    @Test
    fun theCross_comesInTheComingSoonState() = crossComesIn(soon)

    @Test
    fun theCross_comesInTheUnavailableState() = crossComesIn(PaywallUiState(PaywallOffer.Unavailable))

    @Test
    fun theCross_comesInTheTrialState() = crossComesIn(trial)

    @Test
    fun theCross_comesInThePlansState() = crossComesIn(plans)

    @Test
    fun theCross_comesWhileAPurchaseIsRunning() = crossComesIn(PaywallUiState(trial.offer, PaywallProgress.PURCHASING))

    @Test
    fun theCross_comesAfterAnError() = crossComesIn(PaywallUiState(trial.offer, notice = PaywallNotice.ERROR))

    // --- The offer, without a purchase (PW1, PW3, PW8, P6) --------------------------------------------------------------------------

    @Test
    fun comingSoon_limit_hasThreeCards_aSentenceOfContext_noPrice_noTrial_noLater() {
        host(PaywallReason.LIMIT, soon)
        shoot("paywall-arrive-bientot-limite")
        composeRule.onNodeWithText(text(R.string.place_paywall_limit_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_paywall_headline)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_paywall_feature_places)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_paywall_feature_miffs)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_paywall_feature_danger)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_paywall_soon)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_paywall_soon_body)).performScrollTo().assertIsDisplayed()
        // Never promise what does not exist: no "Notifications intelligentes"; nothing is sold: no price, no trial, no percentage, no word "Premium".
        nothingToBuy()
        // The old "Plus tard" button is gone: the cross is the only way out.
        composeRule.onAllNodesWithText(text(R.string.place_paywall_later)).assertCountEquals(0)
    }

    @Test
    fun comingSoon_danger_saysWhy() {
        host(PaywallReason.DANGER, soon)
        shoot("paywall-arrive-bientot-danger")
        composeRule.onNodeWithText(text(R.string.place_paywall_danger_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_paywall_danger_body)).assertIsDisplayed()
        nothingToBuy()
    }

    @Test
    fun comingSoon_secondMiff_hasItsOwnSentences_notThoseOfThePlaces() {
        host(PaywallReason.MIFF, soon)
        shoot("paywall-arrive-bientot-2e-miff")
        composeRule.onNodeWithText(text(R.string.feed_selector_paywall_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.feed_selector_paywall_body)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.feed_selector_paywall_soon_body)).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.place_paywall_soon_body)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.place_paywall_limit_title)).assertCountEquals(0)
        nothingToBuy()
    }

    @Test
    fun comingSoon_hasNoPurchaseButtonAndNoRestoreLink() {
        host(PaywallReason.LIMIT, soon)
        composeRule.onAllNodesWithText(text(R.string.paywall_restore)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.paywall_terms_link)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.paywall_cta_trial)).assertCountEquals(0)
    }

    @Test
    fun production_showsTheComingSoonVariant_andOnlyThat() {
        assertEquals(PaywallOffer.ComingSoon, currentPaywallState().offer)
    }

    // --- Eligible for the trial (mockup 1; PW4, PW6) -------------------------------------------------------------------------------

    @Test
    fun trial_showsTheButton_theBilledPriceBiggest_theBubble_andTheLink() {
        host(PaywallReason.LIMIT, trial)
        shoot("paywall-essai-gratuit")
        composeRule.onNodeWithText(text(R.string.paywall_cta_trial)).assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText(text(R.string.paywall_cancel_anytime)).assertIsDisplayed()
        val billed = composeRule.onNodeWithText(text(R.string.paywall_trial_then, "59,88 €"))
        billed.assertIsDisplayed()
        val perMonth = composeRule.onNodeWithText("4,99", substring = true)
        perMonth.assertIsDisplayed()
        // The billed price is the most visible price line: bigger and bolder than the monthly equivalent (Apple 3.1.2, Google subscriptions policy).
        val billedStyle = textStyleOf(billed)
        val monthlyStyle = textStyleOf(perMonth)
        assertTrue(billedStyle.fontSize.value > monthlyStyle.fontSize.value)
        assertTrue((billedStyle.fontWeight?.weight ?: 400) > (monthlyStyle.fontWeight?.weight ?: 400))
        composeRule.onNodeWithText(text(R.string.paywall_see_all_offers)).assertIsDisplayed()
        // Nothing of the old texts: "pendant 1 an", "Économisez 30 %", "Notifications".
        composeRule.onAllNodesWithText("pendant 1 an", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Économisez", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Notifications", substring = true).assertCountEquals(0)
    }

    @Test
    fun trial_theInformationBlockIsComplete_andReachable() {
        host(PaywallReason.LIMIT, trial)
        composeRule.onNodeWithText(text(R.string.paywall_info_trial, "59,88 €")).performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithText(
                text(R.string.paywall_info_cancel, "Google Play"),
            ).performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.paywall_free_plan_stays, 3)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.paywall_terms_link)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.paywall_privacy_link)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.paywall_restore)).performScrollTo().assertIsDisplayed()
        // Android: Google Play, never "App Store", never "24 heures" (iOS only).
        composeRule.onAllNodesWithText("App Store", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("24 heures", substring = true).assertCountEquals(0)
        assertTrue(
            text(R.string.paywall_info_cancel, text(R.string.android_paywall_store_name)).contains("Google Play"),
        )
        // No mention of a monthly price in the information of the trial (only the yearly plan has one).
        composeRule.onAllNodesWithText("6,99", substring = true).assertCountEquals(0)
    }

    @Test
    fun trial_theButtonStartsTheTrial() {
        host(PaywallReason.LIMIT, trial)
        composeRule.onNodeWithText(text(R.string.paywall_cta_trial)).performClick()
        assertEquals(1, started)
    }

    @Test
    fun trial_seeAllOffers_opensTheTwoPlans_withoutATrialButton() {
        host(PaywallReason.LIMIT, trial)
        composeRule.onNodeWithText(text(R.string.paywall_see_all_offers)).performClick()
        shoot("paywall-toutes-les-offres")
        composeRule.onNodeWithText(text(R.string.paywall_plan_annual_title, "59,88 €")).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.paywall_plan_monthly_title, "6,99 €")).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.paywall_cta_trial)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.paywall_see_all_offers)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.paywall_plan_annual_title, "59,88 €")).performClick()
        composeRule.onNodeWithText(text(R.string.paywall_plan_monthly_title, "6,99 €")).performClick()
        assertEquals(1, boughtAnnual)
        assertEquals(1, boughtMonthly)
        // The cross rule is the same on this layout.
        assertCrossAbsent()
    }

    // --- Not eligible (mockup 2; PW5) --------------------------------------------------------------------------------------------------

    @Test
    fun plans_showTheTwoPlans_noTrialMention_theSavingsLabel_theBilledPriceBiggest() {
        host(PaywallReason.DANGER, plans)
        shoot("paywall-annuel-mensuel")
        val annualTitle = composeRule.onNodeWithText(text(R.string.paywall_plan_annual_title, "59,88 €"))
        annualTitle.assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.paywall_annual_savings)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.paywall_plan_monthly_title, "6,99 €")).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.paywall_no_commitment)).assertIsDisplayed()
        composeRule.onAllNodesWithText("essai", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Économisez", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.paywall_info_annual, "59,88 €")).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.paywall_info_monthly, "6,99 €")).performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithText(
                text(R.string.paywall_info_cancel, "Google Play"),
            ).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun plans_theSavingsLabel_isNeverAFalseFigure() {
        assertEquals("presque 30 % d'économie", text(R.string.paywall_annual_savings))
        assertFalse(text(R.string.paywall_annual_savings).contains("Économisez"))
    }

    // --- Loading, unavailable, working, errors (PW9 to PW12) ------------------------------------------------------------------------

    @Test
    fun loading_hasPlaceholders_theButtonAsleep_noPrice() {
        host(PaywallReason.LIMIT, PaywallUiState(PaywallOffer.Loading))
        shoot("paywall-chargement")
        composeRule.onNodeWithText(text(R.string.paywall_loading)).assertIsDisplayed().assertIsNotEnabled()
        composeRule.onAllNodesWithText("€", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.paywall_cta_trial)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.paywall_free_plan_stays, 3)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unavailable_isCalm_noPrice_noPurchaseButton_andCanBeRetried() {
        host(PaywallReason.MIFF, PaywallUiState(PaywallOffer.Unavailable))
        shoot("paywall-indisponible")
        composeRule.onNodeWithText(text(R.string.paywall_unavailable)).assertIsDisplayed()
        composeRule.onAllNodesWithText("€", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.paywall_cta_trial)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.paywall_retry)).performClick()
        assertEquals(1, retried)
        assertFalse("no alarm", text(R.string.paywall_unavailable).contains("!"))
    }

    @Test
    fun purchasing_buttonsAreAsleep_andItSaysItIsWorking() {
        host(PaywallReason.LIMIT, PaywallUiState(trial.offer, PaywallProgress.PURCHASING))
        composeRule.onNodeWithText(text(R.string.paywall_working)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.paywall_cta_trial)).assertIsNotEnabled()
        // The cross rule goes on: closing does not cancel the purchase (its result is taken into account when it arrives).
        pass(3_300)
        assertCrossThere()
    }

    @Test
    fun purchasing_thePlansAreAsleepToo() {
        host(PaywallReason.LIMIT, PaywallUiState(plans.offer, PaywallProgress.PURCHASING))
        composeRule.onNodeWithText(text(R.string.paywall_plan_annual_title, "59,88 €")).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.paywall_plan_monthly_title, "6,99 €")).assertIsNotEnabled()
    }

    @Test
    fun pending_saysItIsBeingConfirmed_andGrantsNothing() {
        host(PaywallReason.LIMIT, PaywallUiState(trial.offer, notice = PaywallNotice.PENDING))
        shoot("paywall-en-attente")
        composeRule.onNodeWithText(text(R.string.paywall_pending)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.paywall_subscribed)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.paywall_continue)).assertCountEquals(0)
    }

    @Test
    fun error_isCalm_noExclamationMark_noCode_withRetryAndRestore() {
        host(PaywallReason.LIMIT, PaywallUiState(trial.offer, notice = PaywallNotice.ERROR))
        shoot("paywall-erreur-calme")
        val sentence = text(R.string.paywall_error_calm)
        composeRule.onNodeWithText(sentence).assertIsDisplayed()
        assertFalse(sentence.contains("!"))
        assertFalse(Regex("[0-9]{3,}").containsMatchIn(sentence))
        composeRule.onNodeWithText(text(R.string.paywall_retry)).performClick()
        assertEquals(1, retried)
        composeRule.onNodeWithText(text(R.string.paywall_restore)).performScrollTo().performClick()
        assertEquals(1, restored)
    }

    @Test
    fun restoreNothingFound_namesGooglePlay() {
        host(PaywallReason.LIMIT, PaywallUiState(trial.offer, notice = PaywallNotice.RESTORE_NONE))
        val sentence = text(R.string.paywall_restore_none, "Google Play")
        composeRule.onNodeWithText(sentence).assertIsDisplayed()
        assertTrue(sentence.contains("Google Play"))
        composeRule.onNodeWithText(text(R.string.paywall_retry)).performClick()
        assertEquals(1, restored)
    }

    @Test
    fun restoring_saysItIsWorking() {
        host(PaywallReason.LIMIT, PaywallUiState(trial.offer, PaywallProgress.RESTORING))
        composeRule.onNodeWithText(text(R.string.paywall_working)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.paywall_cta_trial)).assertIsNotEnabled()
    }

    @Test
    fun theRestoreLink_asksForARestoration() {
        host(PaywallReason.LIMIT, trial)
        composeRule.onNodeWithText(text(R.string.paywall_restore)).performScrollTo().performClick()
        assertEquals(1, restored)
    }

    // --- Subscribed (PW13, Q-PAY1) ------------------------------------------------------------------------------------------------

    @Test
    fun subscribed_showsNoOffer_continueIsActiveAtOnce_andSoIsTheCross() {
        host(PaywallReason.LIMIT, PaywallUiState(PaywallOffer.Subscribed))
        shoot("paywall-abonne")
        composeRule.onNodeWithText(text(R.string.paywall_subscribed)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.paywall_subscribed_detail)).assertIsDisplayed()
        composeRule.onAllNodesWithText("€", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.place_paywall_headline)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.paywall_continue)).assertIsEnabled().performClick()
        assertEquals(1, continued)
        assertCrossThere()
        pressBack()
        assertEquals("back is not held during a confirmation", 1, closed)
    }

    // --- Legal links (PW6) --------------------------------------------------------------------------------------------------------------

    @Test
    fun legalLinks_openThePublicPagesInTheBrowser() {
        host(PaywallReason.LIMIT, trial)
        composeRule.onNodeWithText(text(R.string.paywall_terms_link)).performScrollTo().performClick()
        val first: Intent = shadowOf(context() as Application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, first.action)
        assertTrue(first.data.toString().endsWith("/fr/cgu"))
        composeRule.onNodeWithText(text(R.string.paywall_privacy_link)).performScrollTo().performClick()
        val second: Intent = shadowOf(context() as Application).nextStartedActivity
        assertTrue(second.data.toString().endsWith("/fr/confidentialite"))
        // A legal page opens ON TOP (another app): the offer is still there and the cross rule is untouched.
        assertEquals(0, closed)
        assertCrossAbsent()
    }

    // --- Large fonts (PW7) --------------------------------------------------------------------------------------------------------------

    @Test
    fun largestFont_trial_nothingIsCut_theInformationIsReachable_theBubbleSitsAboveTheButton() {
        host(PaywallReason.LIMIT, trial, fontScale = 2f)
        shoot("paywall-grande-police-essai")
        composeRule.onNodeWithText(text(R.string.paywall_cta_trial)).assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText(text(R.string.paywall_cancel_anytime)).assertIsDisplayed()
        // The label does not cover the button: its bottom edge is above the top of the button.
        val bubble = composeRule.onNodeWithText(text(R.string.paywall_cancel_anytime)).fetchSemanticsNode().boundsInRoot
        val button = composeRule.onNodeWithText(text(R.string.paywall_cta_trial)).fetchSemanticsNode().boundsInRoot
        assertTrue("bubble ${bubble.bottom} above the button text ${button.top}", bubble.bottom <= button.top)
        composeRule.onNodeWithText(text(R.string.paywall_trial_then, "59,88 €")).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.paywall_info_trial, "59,88 €")).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.paywall_terms_link)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.paywall_restore)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun largestFont_plans_bothButtonsAreThere_andTheInformationIsReachable() {
        host(PaywallReason.MIFF, plans, fontScale = 2f)
        shoot("paywall-grande-police-offres")
        composeRule.onNodeWithText(text(R.string.paywall_plan_annual_title, "59,88 €")).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.paywall_plan_monthly_title, "6,99 €")).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.paywall_info_annual, "59,88 €")).performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithText(
                text(R.string.paywall_info_cancel, "Google Play"),
            ).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun normalFont_theBubbleOverlapsTheCornerOfTheButton_likeTheMockup() {
        host(PaywallReason.LIMIT, trial)
        val bubble = composeRule.onNodeWithText(text(R.string.paywall_cancel_anytime)).fetchSemanticsNode().boundsInRoot
        val button = composeRule.onNodeWithText(text(R.string.paywall_cta_trial)).fetchSemanticsNode().boundsInRoot
        // It sits on the top corner at the end side, above the first letter of the text of the button.
        assertTrue(bubble.right > button.center.x)
        assertTrue(bubble.top < button.top)
        assertTrue("it does not cover the text of the button", bubble.bottom <= button.top + 12.dp.value * 3)
    }

    // --- Semantics: the title of the screen ----------------------------------------------------------------------------------------------

    @Test
    fun theScreenIsNamedForAScreenReader() {
        host(PaywallReason.LIMIT, soon)
        val node = composeRule.onRoot().fetchSemanticsNode()

        fun find(n: androidx.compose.ui.semantics.SemanticsNode): String? =
            n.config.getOrNull(SemanticsProperties.PaneTitle) ?: n.children.firstNotNullOfOrNull { find(it) }
        assertEquals(text(R.string.place_paywall_soon), find(node))
    }
}
