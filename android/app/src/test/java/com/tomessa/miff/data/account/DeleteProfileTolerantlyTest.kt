package com.tomessa.miff.data.account

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The deletion of the account never fails because of the daily counter of the alerts (backend/data-model.md 2.20): a refusal of its deletion is tolerated. */
class DeleteProfileTolerantlyTest {
    private class Refusal : RuntimeException()

    private val calls = mutableListOf<String>()

    private suspend fun run(
        withQuota: Boolean,
        failWith: Throwable? = null,
    ) = deleteProfileTolerantly(
        withQuota = withQuota,
        deleteWithQuota = {
            calls += "withQuota"
            failWith?.let { throw it }
        },
        deleteProfileOnly = { calls += "profileOnly" },
        refused = { it is Refusal },
    )

    @Test
    fun functionOff_onlyTheProfileIsDeleted_asBefore() =
        runTest {
            run(withQuota = false)
            assertEquals(listOf("profileOnly"), calls)
        }

    @Test
    fun functionOn_theCounterGoesWithTheProfile_inOneTransaction() =
        runTest {
            run(withQuota = true)
            assertEquals(listOf("withQuota"), calls)
        }

    @Test
    fun theCounterIsRefused_anAlertStartedInTheLast24Hours_theProfileIsStillDeleted_noError() =
        runTest {
            run(withQuota = true, failWith = Refusal())
            assertEquals(listOf("withQuota", "profileOnly"), calls)
        }

    @Test
    fun anotherFailure_isNotHidden() =
        runTest {
            val boom = IllegalStateException("network")
            val thrown = runCatching { run(withQuota = true, failWith = boom) }.exceptionOrNull()
            assertTrue(thrown === boom)
            assertEquals(listOf("withQuota"), calls)
        }
}
