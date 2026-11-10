package com.tomessa.miff.ui.review

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.data.review.SharedPreferencesReviewStateStore
import com.tomessa.miff.domain.review.ReviewOutcome
import com.tomessa.miff.domain.review.ReviewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the review window remembers on the phone: a round trip, an unknown value, and the erasure (sign-out, deletion of the account). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReviewStateStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun store() = SharedPreferencesReviewStateStore(context)

    @Test
    fun aFreshPhone_remembersNothing() {
        assertEquals(ReviewState(), store().read())
        assertFalse(store().forceNext())
    }

    @Test
    fun whatIsWritten_isReadBack_byAnotherInstance() {
        val state =
            ReviewState(firstMiffSeenAtMillis = 10L, lastShownAtMillis = 20L, lastOutcome = ReviewOutcome.CAN_DO_BETTER)
        store().write(state)
        assertEquals(state, store().read())
        store().write(ReviewState(firstMiffSeenAtMillis = 10L))
        assertEquals(ReviewState(firstMiffSeenAtMillis = 10L), store().read())
    }

    @Test
    fun anUnknownOutcome_readsAsNone_neverCrashes() {
        context
            .getSharedPreferences(
                "miff_review_prompt",
                Context.MODE_PRIVATE,
            ).edit()
            .putString("last_outcome", "FROM_THE_FUTURE")
            .putLong("last_shown_at", 5L)
            .commit()
        val read = store().read()
        assertNull(read.lastOutcome)
        assertEquals(5L, read.lastShownAtMillis)
    }

    @Test
    fun clear_forgetsEverything_andIsHarmlessWhenThereIsNothing() {
        store().clear()
        store().write(
            ReviewState(firstMiffSeenAtMillis = 1L, lastShownAtMillis = 2L, lastOutcome = ReviewOutcome.LOVED),
        )
        store().setForceNext(true)
        store().clear()
        assertEquals(ReviewState(), store().read())
        assertFalse(store().forceNext())
        assertTrue(true)
    }
}
