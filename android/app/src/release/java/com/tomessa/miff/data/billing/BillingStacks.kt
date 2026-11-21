package com.tomessa.miff.data.billing

import android.content.Context
import com.tomessa.miff.data.subscription.FirestoreSubscriptionStore

/**
 * RELEASE BUILD (and releaseLike): ALWAYS the real stack. There is no fake store here: a file of the same name in src/debug builds one for the debug build, and the CI looks for its
 * marker in the bundle (ReleaseSigningGuardTest, check-release-bundle.sh).
 */
object BillingStacks {
    fun create(context: Context): BillingStack =
        BillingStack(
            gateway = PlayBillingGateway(context),
            backend = FirebasePurchaseBackend(),
            entitlements = FirestoreSubscriptionStore(),
        )
}
