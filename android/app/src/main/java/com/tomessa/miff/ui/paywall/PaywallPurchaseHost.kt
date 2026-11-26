package com.tomessa.miff.ui.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.data.billing.PremiumCoordinator
import com.tomessa.miff.domain.analytics.AnalyticsTracker
import com.tomessa.miff.domain.billing.MiffPlusProducts
import com.tomessa.miff.domain.paywall.MonotonicClock
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.ui.auth.LegalLinks
import com.tomessa.miff.ui.place.PaywallDestination

/** The purchase buttons of the offer screen (they do something only when the purchase is switched on). */
class PaywallStoreActions(
    val onStartTrial: () -> Unit,
    val onBuyAnnual: () -> Unit,
    val onBuyMonthly: () -> Unit,
    val onRetry: () -> Unit,
    val onRestore: () -> Unit,
    val onManage: () -> Unit = {},
)

/**
 * One DISPLAY of the offer WITH the purchase (BuildConfig.PREMIUM_PURCHASE on): the prices and the progress come from [PaywallPurchaseViewModel], the purchase from the
 * [PremiumCoordinator] of the app. It builds NO screen of its own: it goes through [PaywallDestination], so that the single offer screen, the single rule of the close cross and
 * the two analytics events stay in ONE place (criterion CR10).
 */
@Composable
internal fun PurchasePaywallDestination(
    reason: PaywallReason,
    tracker: AnalyticsTracker,
    clock: MonotonicClock,
    premium: PremiumCoordinator,
    onClose: () -> Unit,
    /** Once per display, when the offer is really on view (PAY-PER-A: the counter of the periodic offer goes back to zero). */
    onDisplayed: () -> Unit = {},
) {
    val model: PaywallPurchaseViewModel =
        viewModel(factory = viewModelFactory { initializer { PaywallPurchaseViewModel(premium) } })
    val state by model.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.hostActivity()
    PaywallDestination(
        reason = reason,
        tracker = tracker,
        clock = clock,
        state = state,
        onClose = onClose,
        onDisplayed = onDisplayed,
        store =
            PaywallStoreActions(
                // The sheet of the store needs the activity that is on screen; without one (never in practice) the button does nothing.
                onStartTrial = { activity?.let(model::startTrial) },
                onBuyAnnual = { activity?.let(model::buyAnnual) },
                onBuyMonthly = { activity?.let(model::buyMonthly) },
                onRetry = model::retry,
                onRestore = model::restore,
                // A subscription on hold is mended on the page of the store (the same page as Settings > Abonnement).
                onManage = {
                    activity?.let { LegalLinks.open(it, MiffPlusProducts.manageUrl(BuildConfig.APPLICATION_ID)) }
                },
            ),
    )
}

/** The activity behind a context (the sheet of the store is opened from it). */
internal tailrec fun Context.hostActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.hostActivity()
        else -> null
    }
