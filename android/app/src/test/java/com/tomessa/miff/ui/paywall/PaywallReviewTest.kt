package com.tomessa.miff.ui.paywall

import android.app.Activity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.tomessa.miff.data.billing.CatalogResult
import com.tomessa.miff.data.billing.PremiumCoordinator
import com.tomessa.miff.data.billing.PurchaseEvent
import com.tomessa.miff.data.subscription.SubscriptionRepository
import com.tomessa.miff.domain.paywall.MonotonicClock
import com.tomessa.miff.domain.paywall.PaywallNotice
import com.tomessa.miff.domain.paywall.PaywallOffer
import com.tomessa.miff.domain.paywall.PaywallPrice
import com.tomessa.miff.domain.paywall.PaywallUiState
import com.tomessa.miff.testsupport.FakeBillingBackend
import com.tomessa.miff.testsupport.FakeBillingGateway
import com.tomessa.miff.testsupport.FakeEntitlementStore
import com.tomessa.miff.testsupport.miffPlusStoreOffers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Points of the independent review of SRV-8A on the offer: a subscription on hold, and the label of the saving. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w360dp-h800dp-xhdpi", application = android.app.Application::class)
class PaywallReviewTest {
    @get:Rule
    val rule = createComposeRule()

    private val annual = PaywallPrice("59,88 €", 59_880_000L, "EUR")
    private val monthly = PaywallPrice("6,99 €", 6_990_000L, "EUR")

    private var managed = 0

    private fun show(state: PaywallUiState) {
        val model = PaywallViewModel(MonotonicClock { 0L }, delayMillis = 0)
        rule.setContent {
            PaywallScreen(
                model = model,
                state = state,
                actions = PaywallActions(onClose = {}, onManage = { managed++ }),
                reduceMotion = true,
            )
        }
    }

    @Test
    fun aSubscriptionOnHold_saysSo_withTheLinkToTheStore_andNoRetry() {
        show(PaywallUiState(PaywallOffer.Plans(annual, monthly), notice = PaywallNotice.ON_HOLD))
        rule.onNodeWithText("Miff+ is on hold", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Manage my subscription").performClick()
        assertEquals(1, managed)
        rule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun theSavingLabel_isShownWhileItIsTrue_andHiddenWhenThePricesMakeItFalse() {
        show(PaywallUiState(PaywallOffer.Plans(annual, monthly)))
        rule.onNodeWithText("almost 30% off").assertIsDisplayed()
    }

    @Test
    fun theSavingLabel_isHidden_whenTheAnnualPriceSavesTooLittle() {
        show(PaywallUiState(PaywallOffer.Plans(PaywallPrice("70,00 €", 70_000_000L, "EUR"), monthly)))
        rule.onNodeWithText("Annual · 70,00 € per year", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("almost 30% off").assertDoesNotExist()
    }
}
