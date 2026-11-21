package com.tomessa.miff.data.billing

import android.content.Context
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.data.subscription.FirestoreSubscriptionStore

/**
 * DEBUG BUILD ONLY. With `BuildConfig.BILLING_FAKE` (the default of the debug build) the purchase of Miff+ is played by a FAKE store, a FAKE server and a FAKE entitlement
 * ([FakeBilling]): the app does not have to be in the Play Console, nothing is paid, nothing is granted on the real server (the fake never writes anything anywhere). With
 * `-Pmiff.billingFake=false` the debug build uses the REAL stack, like a release (to test with a licence tester, from a build installed from an internal track).
 */
object BillingStacks {
    fun create(context: Context): BillingStack =
        if (BuildConfig.BILLING_FAKE) {
            FakeBilling.stack()
        } else {
            BillingStack(
                gateway = PlayBillingGateway(context),
                backend = FirebasePurchaseBackend(),
                entitlements = FirestoreSubscriptionStore(),
            )
        }
}
