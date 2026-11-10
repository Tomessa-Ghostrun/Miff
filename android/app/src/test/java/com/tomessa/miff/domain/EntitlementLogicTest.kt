package com.tomessa.miff.domain

import com.tomessa.miff.domain.entitlement.OfferState
import com.tomessa.miff.domain.entitlement.interpretEntitlement
import org.junit.Assert.assertEquals
import org.junit.Test

/** `entitlements/{uid}`: the same reading as the security rules (`isPremiumOneRead`), shared by the diagnostic screen and the Settings. */
class EntitlementLogicTest {
    private val now = 1_000_000L

    @Test
    fun missingDocumentIsClassic() {
        assertEquals(OfferState.Classic, interpretEntitlement(false, null, null, now))
    }

    @Test
    fun activeAndNotExpiredIsPremium() {
        assertEquals(OfferState.Premium(now + 1), interpretEntitlement(true, true, now + 1, now))
    }

    @Test
    fun expiredOrInactiveOrIncompleteIsClassic() {
        assertEquals(OfferState.Classic, interpretEntitlement(true, true, now - 1, now))
        assertEquals(OfferState.Classic, interpretEntitlement(true, true, now, now))
        assertEquals(OfferState.Classic, interpretEntitlement(true, false, now + 1, now))
        assertEquals(OfferState.Classic, interpretEntitlement(true, true, null, now))
        assertEquals(OfferState.Classic, interpretEntitlement(true, null, now + 1, now))
    }
}
