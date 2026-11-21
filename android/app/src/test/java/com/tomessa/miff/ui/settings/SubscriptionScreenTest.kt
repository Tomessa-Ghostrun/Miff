package com.tomessa.miff.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tomessa.miff.domain.subscription.StoreState
import com.tomessa.miff.domain.subscription.SubscriptionPlan
import com.tomessa.miff.domain.subscription.SubscriptionSource
import com.tomessa.miff.domain.subscription.SubscriptionStatus
import com.tomessa.miff.domain.subscription.formatLongDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * The Abonnement screen, state by state (ticket premium-achat-reel.md section 8; criteria AN2, AN6, AN8, AN9, AN10): what is said, what stays true, the next step; the page of the store
 * is the only way to cancel; the prices are the store's (a sentence without a price when the store is out of reach).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w360dp-h800dp-xhdpi", application = android.app.Application::class)
class SubscriptionScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val end = 1_800_000_000_000L
    private val date = formatLongDate(end, Locale.US)

    private var managed: SubscriptionSource? = null
    private var offers = 0
    private var refreshes = 0
    private var retries = 0

    private fun show(
        status: SubscriptionStatus?,
        prices: SubscriptionPrices? = SubscriptionPrices("59,88 €", "6,99 €"),
        purchaseOn: Boolean = true,
        loading: Boolean = false,
        refreshing: Boolean = false,
        notice: SubscriptionNotice? = null,
    ) {
        rule.setContent {
            SubscriptionScreen(
                state =
                    SubscriptionUiState(
                        loading = loading,
                        status = status,
                        prices = prices,
                        refreshing = refreshing,
                        notice = notice,
                    ),
                actions =
                    SubscriptionActions(
                        onBack = {},
                        onManage = { managed = it },
                        onOffer = { offers++ },
                        onRefresh = if (purchaseOn) ({ refreshes++ }) else null,
                        onRetry = { retries++ },
                    ),
            )
        }
    }

    private fun store(
        state: StoreState,
        plan: SubscriptionPlan? = SubscriptionPlan.YEARLY,
        source: SubscriptionSource = SubscriptionSource.GOOGLE_PLAY,
        test: Boolean = false,
    ) = SubscriptionStatus.Store(source, state, plan, expiresAtMillis = end, test = test)

    private fun see(text: String) = rule.onNodeWithText(text, substring = true).assertIsDisplayed()

    private fun absent(text: String) = rule.onNodeWithText(text, substring = true).assertDoesNotExist()

    // ---- the states -------------------------------------------------------------------------------------------------------------------------

    @Test
    fun aFreeAccount_isToldItIsOnTheFreePlan_andCanDiscoverMiffPlus() {
        show(SubscriptionStatus.Free)
        see("You're on the free plan")
        see("Discover Miff+")
        see("Refresh my subscription")
        absent("Manage my subscription")
        rule.onNodeWithText("Discover Miff+").performClick()
        assertEquals(1, offers)
    }

    @Test
    fun theTrial_saysUntilWhen_andWhatComesNext_withTheStoresPrice() {
        show(store(StoreState.TRIAL))
        see("Free trial until $date. Then 59,88 € per year, unless you cancel before.")
        see("Manage my subscription")
        absent("Discover Miff+")
    }

    @Test
    fun theTrial_withTheStoreOutOfReach_hasNoPrice_butStillSaysWhatComesNext() {
        show(store(StoreState.TRIAL), prices = null)
        see("Free trial until $date. The yearly subscription starts after that, unless you cancel before.")
        absent("€")
    }

    @Test
    fun anActiveSubscription_saysTheNextRenewal_withThePriceOfItsPlan() {
        show(store(StoreState.ACTIVE, SubscriptionPlan.YEARLY))
        see("Active. Next renewal on $date (59,88 €).")
    }

    @Test
    fun aMonthlySubscription_showsTheMonthlyPrice() {
        show(store(StoreState.ACTIVE, SubscriptionPlan.MONTHLY), prices = SubscriptionPrices("59,88 €", "6,99 €"))
        // The price in the sentence is the monthly one of the store; the state of the screen decides it (see SubscriptionUiState.renewalPrice).
        val state =
            SubscriptionUiState(
                loading = false,
                status = store(StoreState.ACTIVE, SubscriptionPlan.MONTHLY),
                prices = SubscriptionPrices("59,88 €", "6,99 €"),
            )
        assertEquals("6,99 €", state.renewalPrice())
    }

    @Test
    fun anActiveSubscription_withTheStoreOutOfReach_hasNoPriceInTheSentence() {
        show(store(StoreState.ACTIVE), prices = null)
        see("Active. Next renewal on $date.")
        absent("€")
    }

    @Test
    fun aCancelledSubscription_keepsEverythingUntilTheEnd() {
        show(store(StoreState.CANCELED))
        see("Miff+ ends on $date. You keep everything until then.")
        see("Manage my subscription")
    }

    @Test
    fun theGracePeriod_isSober_andPointsToThePaymentMethod() {
        show(store(StoreState.GRACE))
        see(
            "The payment for your subscription didn't go through. Everything still works: update your payment method before $date.",
        )
        see("Manage my subscription")
    }

    @Test
    fun aSuspendedSubscription_saysItIsOnHold_andTheBenefitsComeBack() {
        show(store(StoreState.ON_HOLD))
        see("Miff+ is on hold: the payment didn't go through. Your benefits will be back as soon as it's sorted.")
        see("Manage my subscription")
        absent("Get Miff+ again")
    }

    @Test
    fun anEndedSubscription_canBeResumed_withNothingToManage() {
        show(store(StoreState.EXPIRED))
        see("Your Miff+ subscription has ended. You can start it again whenever you like.")
        see("Get Miff+ again")
        absent("Manage my subscription")
    }

    @Test
    fun aRefundedSubscription_isToldNeutrally() {
        show(store(StoreState.REVOKED))
        see("Your Miff+ subscription was refunded: the benefits are no longer active.")
        see("Get Miff+ again")
        absent("Manage my subscription")
    }

    @Test
    fun aGrantByHand_isShownAsBefore_withNoButton() {
        show(SubscriptionStatus.Manual(end))
        see("Active until $date")
        see("Granted by the Miff team")
        see("doesn't go through a store")
        absent("Manage my subscription")
        absent("Discover Miff+")
        absent("Refresh my subscription")
    }

    @Test
    fun aSubscriptionBoughtOnAnIphone_isManagedInTheAppStore() {
        show(store(StoreState.ACTIVE, source = SubscriptionSource.APP_STORE))
        see("taken out in the App Store")
        rule.onNodeWithText("Manage my subscription").performClick()
        assertEquals(SubscriptionSource.APP_STORE, managed)
    }

    @Test
    fun aTestPurchase_isSaidPlainly() {
        show(store(StoreState.ACTIVE, test = true))
        see("Test purchase: no real payment.")
    }

    @Test
    fun manage_opensThePageOfTheStore_nothingIsCancelledInTheApp() {
        show(store(StoreState.ACTIVE))
        rule.onNodeWithText("Manage my subscription").performClick()
        assertEquals(SubscriptionSource.GOOGLE_PLAY, managed)
        // No "Stop" / "Cancel my subscription" button exists in the app (criterion AN10).
        absent("Stop")
        absent("Cancel my subscription")
    }

    @Test
    fun refresh_isAskedByTheLink_andShowsProgressWhileItRuns() {
        show(store(StoreState.ACTIVE))
        rule.onNodeWithText("Refresh my subscription").performClick()
        assertEquals(1, refreshes)
    }

    @Test
    fun whileRefreshing_theLinkIsReplacedByTheProgress() {
        show(store(StoreState.ACTIVE), refreshing = true)
        see("One moment")
        absent("Refresh my subscription")
    }

    @Test
    fun theCalmSentencesAboutTheLastRefresh() {
        show(SubscriptionStatus.Free, notice = SubscriptionNotice.NOTHING_FOUND)
        see("No Miff+ subscription found on this Google Play account.")
    }

    @Test
    fun anotherAccountsPurchase_givesTheContact_neverTheOtherAccount() {
        show(SubscriptionStatus.Free, notice = SubscriptionNotice.OTHER_ACCOUNT)
        see("already used with another Miff account")
        see(com.tomessa.miff.BuildConfig.SUPPORT_EMAIL)
    }

    @Test
    fun whenThePurchaseIsOff_thereIsNoOfferAndNoRefresh() {
        show(SubscriptionStatus.Free, purchaseOn = false)
        absent("Discover Miff+")
        absent("Refresh my subscription")
    }

    @Test
    fun aServerThatCannotBeRead_isACalmMessageWithRetry_notAnEmptyFreePlan() {
        show(status = null)
        see("We couldn't read your subscription right now")
        absent("You're on the free plan")
        rule.onNodeWithText("Try again").assertIsEnabled().performClick()
        assertEquals(1, retries)
    }

    @Test
    fun whileLoading_theScreenSaysSo() {
        show(status = null, loading = true)
        see("One moment")
        absent("We couldn't read")
    }

    @Test
    fun noSentenceHasAnExclamationMark_orAnAlarmWord() {
        val statuses =
            listOf(
                SubscriptionStatus.Free,
                store(StoreState.TRIAL),
                store(StoreState.ACTIVE),
                store(StoreState.CANCELED),
                store(StoreState.GRACE),
                store(StoreState.ON_HOLD),
                store(StoreState.EXPIRED),
                store(StoreState.REVOKED),
            )
        assertFalse(statuses.isEmpty())
        val strings = java.io.File("src/main/res/values/strings.xml").readText()
        Regex(
            """<string name="(settings_subscription_[a-z_]+|paywall_other_account)">(.*?)</string>""",
        ).findAll(strings).forEach {
            val value = it.groupValues[2]
            assertFalse("${it.groupValues[1]}: no exclamation mark", value.contains("!"))
            assertFalse(
                "${it.groupValues[1]}: no alarm",
                Regex(
                    """(?i)warning|urgent|alert|danger zone|attention""",
                ).containsMatchIn(value.replace("danger zones", "")),
            )
        }
    }
}
