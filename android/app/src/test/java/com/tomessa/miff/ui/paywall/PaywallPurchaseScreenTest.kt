package com.tomessa.miff.ui.paywall

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.domain.paywall.MonotonicClock
import com.tomessa.miff.domain.paywall.PaywallNotice
import com.tomessa.miff.domain.paywall.PaywallOffer
import com.tomessa.miff.domain.paywall.PaywallPrice
import com.tomessa.miff.domain.paywall.PaywallProgress
import com.tomessa.miff.domain.paywall.PaywallUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The offer screen with the purchase (ticket paywall-v2.md section 5.2, criteria PW4 to PW6, PW9 to PW13; premium-achat-reel.md AN1, AN3, AN5, AN13): every price on the screen is the
 * one given in the state (the store's), the billed price is written in the line that is most visible, the buttons sleep while the store works, and every calm sentence is there.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w360dp-h800dp-xhdpi", application = android.app.Application::class)
class PaywallPurchaseScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val annual = PaywallPrice("59,88 €", 59_880_000L, "EUR")
    private val monthly = PaywallPrice("6,99 €", 6_990_000L, "EUR")

    private var trial = 0
    private var buyAnnual = 0
    private var buyMonthly = 0
    private var retried = 0
    private var restored = 0

    private fun show(state: PaywallUiState) {
        // No delay of the cross here (the rule is tested elsewhere): it is there at once.
        val model = PaywallViewModel(MonotonicClock { 0L }, delayMillis = 0)
        rule.setContent {
            PaywallScreen(
                model = model,
                state = state,
                actions =
                    PaywallActions(
                        onClose = {},
                        onStartTrial = { trial++ },
                        onBuyAnnual = { buyAnnual++ },
                        onBuyMonthly = { buyMonthly++ },
                        onRetry = { retried++ },
                        onRestore = { restored++ },
                    ),
                reduceMotion = true,
            )
        }
    }

    private fun see(text: String) = rule.onNodeWithText(text, substring = true).assertIsDisplayed()

    private fun absent(text: String) = rule.onNodeWithText(text, substring = true).assertDoesNotExist()

    @Test
    fun eligibleToTheTrial_theButtonThePriceAndTheInformationOfTheStoresAreThere() {
        show(PaywallUiState(PaywallOffer.TrialEligible(annual, monthly)))
        see("Start your 7-day free trial")
        // The bubble of the trial button (exact text: the information block also starts with "Cancel anytime in your ... account settings").
        rule.onNodeWithText("Cancel anytime").assertIsDisplayed()
        see("Then 59,88 € per year")
        see("See all plans")
        // The information the stores require, with the store's price and the name of the store.
        rule
            .onNodeWithText(
                "7-day free trial, then 59,88 € per year",
                substring = true,
            ).performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("Google Play", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Terms of Use", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Privacy Policy", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Restore purchases").performScrollTo().assertIsDisplayed()
        // The monthly equivalent is a smaller complement, written from the annual price of the store.
        assertEquals(1, rule.onAllNodesWithText("per month", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun theTrialButton_startsTheTrial_theOtherPlansAreReachedByTheLink() {
        show(PaywallUiState(PaywallOffer.TrialEligible(annual, monthly)))
        rule.onNodeWithText("Start your 7-day free trial").performClick()
        assertEquals(1, trial)
        rule.onNodeWithText("See all plans").performClick()
        see("Annual · 59,88 € per year")
        see("Monthly · 6,99 € per month")
        rule.onNodeWithText("Annual · 59,88 € per year", substring = true).performClick()
        rule.onNodeWithText("Monthly · 6,99 € per month", substring = true).performClick()
        assertEquals(1, buyAnnual)
        assertEquals(1, buyMonthly)
    }

    @Test
    fun withoutTheTrial_theTwoPlansAreThere_withNoMentionOfATrial() {
        show(PaywallUiState(PaywallOffer.Plans(annual, monthly)))
        see("Annual · 59,88 € per year")
        see("Monthly · 6,99 € per month")
        see("almost 30% off")
        see("No commitment")
        absent("free trial")
        absent("Start your 7-day")
    }

    @Test
    fun whileTheStoreWorks_theButtonsSleep_andTheScreenSaysSo() {
        show(PaywallUiState(PaywallOffer.Plans(annual, monthly), progress = PaywallProgress.PURCHASING))
        rule.onNodeWithText("Annual · 59,88 € per year", substring = true).assertIsNotEnabled()
        rule.onNodeWithText("Monthly · 6,99 € per month", substring = true).assertIsNotEnabled()
        see("One moment")
    }

    @Test
    fun theButtonsAreActive_whenNothingRuns() {
        show(PaywallUiState(PaywallOffer.Plans(annual, monthly)))
        rule.onNodeWithText("Annual · 59,88 € per year", substring = true).assertIsEnabled()
    }

    @Test
    fun loading_showsNoPrice_andNoActiveButton() {
        show(PaywallUiState(PaywallOffer.Loading))
        see("Loading")
        absent("€")
        rule.onNodeWithText("Loading", substring = true).assertIsNotEnabled()
    }

    @Test
    fun unavailable_isACalmSentence_noPrice_noPurchaseButton_andRetry() {
        show(PaywallUiState(PaywallOffer.Unavailable))
        see("Plans aren't available right now")
        absent("€")
        absent("free trial")
        rule.onNodeWithText("Try again").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun aPendingPayment_saysItIsBeingConfirmed() {
        show(PaywallUiState(PaywallOffer.Plans(annual, monthly), notice = PaywallNotice.PENDING))
        see("Your payment is being confirmed")
        absent("Try again")
    }

    @Test
    fun anError_isCalm_withRetryAndRestore_noCode_noExclamation() {
        show(PaywallUiState(PaywallOffer.Plans(annual, monthly), notice = PaywallNotice.ERROR))
        see("We couldn't complete the purchase this time")
        rule.onNodeWithText("Try again").performClick()
        assertEquals(1, retried)
        rule.onNodeWithText("Restore purchases").performScrollTo().performClick()
        assertEquals(1, restored)
    }

    @Test
    fun aSubscriptionOfAnotherMiffAccount_givesTheContact_andNoRetry() {
        show(PaywallUiState(PaywallOffer.Plans(annual, monthly), notice = PaywallNotice.OTHER_ACCOUNT))
        see("already used with another Miff account")
        see(BuildConfig.SUPPORT_EMAIL)
        absent("Try again")
    }

    @Test
    fun aRestorationThatFindsNothing_namesTheStore() {
        show(PaywallUiState(PaywallOffer.Plans(annual, monthly), notice = PaywallNotice.RESTORE_NONE))
        see("No Miff+ subscription found on this Google Play account.")
    }

    @Test
    fun aPersonWhoHasMiffPlus_isNeverOfferedAPurchase() {
        show(PaywallUiState(PaywallOffer.Subscribed))
        see("You have Miff+")
        absent("€")
        absent("Start your")
        absent("Annual")
    }

    @Test
    fun theVersionWithoutPurchase_showsNoPriceAndNoButton() {
        show(PaywallUiState.PRODUCTION)
        see("Miff+ is coming soon")
        absent("€")
        absent("free trial")
        absent("Restore purchases")
    }
}
