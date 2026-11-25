package com.tomessa.miff.data.billing

import com.android.billingclient.api.Purchase
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the Google Play Billing Library lists is turned into [OwnedPurchase] (the only part of [PlayBillingGateway] that does not need the Play Store): only the purchases of Miff+
 * count, "pending" is not "purchased", and the token never leaves the object in a text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PlayBillingMappingTest {
    private fun purchase(
        token: String,
        state: Int,
        acknowledged: Boolean,
        vararg products: String,
    ): Purchase {
        val json =
            JSONObject()
                .put("orderId", "GPA.0000-0000-0000-00000")
                .put("packageName", "com.tomessa.miff")
                .put("productIds", JSONArray(products.toList()))
                .put("purchaseTime", 1L)
                .put("purchaseState", state)
                .put("purchaseToken", token)
                .put("quantity", 1)
                .put("autoRenewing", true)
                .put("acknowledged", acknowledged)
        return Purchase(json.toString(), "signature")
    }

    @Test
    fun aPaidPurchaseOfMiffPlus_isPurchased_withItsAcknowledgement() {
        val owned = PlayBillingGateway.ownedOf(listOf(purchase("token-aaaaaaaaaaaaaaaaaaaa", 0, false, "miff_plus")))
        assertEquals(1, owned.size)
        assertEquals(OwnedState.PURCHASED, owned[0].state)
        assertFalse("not acknowledged yet: the server must check and acknowledge it", owned[0].acknowledged)
        assertEquals("token-aaaaaaaaaaaaaaaaaaaa", owned[0].token)
        val acknowledged =
            PlayBillingGateway.ownedOf(
                listOf(purchase("token-bbbbbbbbbbbbbbbbbbbb", 0, true, "miff_plus")),
            )
        assertTrue(acknowledged[0].acknowledged)
    }

    @Test
    fun aPendingPayment_isPending_notPurchased() {
        val owned = PlayBillingGateway.ownedOf(listOf(purchase("token-cccccccccccccccccccc", 4, false, "miff_plus")))
        assertEquals(OwnedState.PENDING, owned.single().state)
    }

    @Test
    fun whatIsNotMiffPlus_isIgnored() {
        val owned =
            PlayBillingGateway.ownedOf(
                listOf(
                    purchase("token-dddddddddddddddddddd", 0, true, "another_product"),
                    purchase("token-eeeeeeeeeeeeeeeeeeee", 0, true, "another_product", "miff_plus"),
                ),
            )
        assertEquals(listOf("token-eeeeeeeeeeeeeeeeeeee"), owned.map { it.token })
        assertTrue(PlayBillingGateway.ownedOf(emptyList()).isEmpty())
    }

    @Test
    fun theTokenIsNotInTheText() {
        val text =
            PlayBillingGateway
                .ownedOf(
                    listOf(purchase("token-ffffffffffffffffffff", 0, true, "miff_plus")),
                ).single()
                .toString()
        assertFalse(text.contains("token-ffff"))
    }
}
