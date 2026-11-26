package com.tomessa.miff.data.account

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SRV-8A: the purge of the billing data runs BEFORE the profile is deleted (it needs the sign-in), a failure is tolerated, and a purge that is too slow is cut after 8 seconds: the deletion
 * of the account always goes on. Behaviour tests (virtual time), not a reading of the source.
 */
class DeleteProfileAfterBillingPurgeTest {
    private val calls = mutableListOf<String>()

    @Test
    fun theBillingPurgeRunsBeforeTheProfileIsDeleted() =
        runTest {
            deleteProfileAfterBillingPurge(purge = {
                calls += "purge"
                true
            }) { calls += "profile" }
            assertEquals(listOf("purge", "profile"), calls)
        }

    @Test
    fun aFailedPurgeIsTolerated_theProfileIsDeletedAnyway() =
        runTest {
            deleteProfileAfterBillingPurge(purge = {
                calls += "purge"
                false
            }) { calls += "profile" }
            assertEquals(listOf("purge", "profile"), calls)
        }

    @Test
    fun aPurgeThatIsTooSlowIsCutAfterEightSeconds_thenTheProfileIsDeleted() =
        runTest {
            var timeWhenTheProfileIsDeleted = -1L
            deleteProfileAfterBillingPurge(
                purge = {
                    calls += "purge"
                    delay(60_000)
                    calls += "purge finished"
                    true
                },
            ) {
                timeWhenTheProfileIsDeleted = currentTime
                calls += "profile"
            }
            assertEquals(8_000L, SUBSCRIPTION_PURGE_TIMEOUT_MS)
            assertEquals(8_000L, timeWhenTheProfileIsDeleted)
            assertEquals(listOf("purge", "profile"), calls)
        }

    @Test
    fun aPurgeThatEndsInTimeDoesNotWait() =
        runTest {
            deleteProfileAfterBillingPurge(
                purge = {
                    delay(3_000)
                    true
                },
            ) { calls += "profile" }
            assertEquals(3_000L, currentTime)
            assertTrue(calls == listOf("profile"))
        }

    @Test
    fun withThePurchaseSwitchedOff_thereIsNothingToPurge() =
        runTest {
            deleteProfileAfterBillingPurge(purge = null) { calls += "profile" }
            assertEquals(listOf("profile"), calls)
            assertEquals(0L, currentTime)
        }
}
