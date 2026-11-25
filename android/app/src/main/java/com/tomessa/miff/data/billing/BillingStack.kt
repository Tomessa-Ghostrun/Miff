package com.tomessa.miff.data.billing

import com.tomessa.miff.data.subscription.SubscriptionStore

/**
 * The three things the purchase of Miff+ talks to: the store, the Miff server, and the place where the rights are read. In a release build (and in a debug build run with
 * `-Pmiff.billingFake=false`) they are the REAL ones: the Google Play Billing Library, the callable functions of the server, the document `entitlements/{uid}`. In the DEBUG
 * build only, they can be a FAKE trio (src/debug) so that the whole interface is tried without the app being in the Play Console. The choice is made by `BillingStacks.create`,
 * which has one version in src/debug and one in src/release (the fake is not even compiled into a release).
 */
class BillingStack(
    val gateway: BillingGateway,
    val backend: PurchaseBackend,
    val entitlements: SubscriptionStore,
)
