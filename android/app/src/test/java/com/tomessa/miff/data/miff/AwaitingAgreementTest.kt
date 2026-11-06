package com.tomessa.miff.data.miff

import com.tomessa.miff.ui.miff.AgreementUi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Miffs that wait for the agreement (design 23): in memory, per session, nothing about a place or a position. */
class AwaitingAgreementTest {
    @Test
    fun replace_remove_andTheEndOfTheSession() =
        runTest {
            val awaiting = AwaitingAgreement()
            assertTrue(awaiting.waiting.value.isEmpty())
            awaiting.replace(setOf("a", "b"))
            assertEquals(setOf("a", "b"), awaiting.waiting.value)
            awaiting.remove("a")
            assertEquals(setOf("b"), awaiting.waiting.value)
            awaiting.stop()
            assertTrue(awaiting.waiting.value.isEmpty())
        }

    @Test
    fun eachReviewRequest_goesUp() {
        val awaiting = AwaitingAgreement()
        val before = awaiting.reviewRequests.value
        awaiting.requestReview()
        awaiting.requestReview()
        assertEquals(before + 2, awaiting.reviewRequests.value)
    }

    @Test
    fun theUiOnlyConcernsTheMiffsThatWait() {
        val ui = AgreementUi(setOf("a"))
        assertTrue(ui.isWaiting("a"))
        assertFalse(ui.isWaiting("b"))
        assertFalse(ui.isWaiting(null))
        assertFalse(AgreementUi().isWaiting("a"))
    }
}
