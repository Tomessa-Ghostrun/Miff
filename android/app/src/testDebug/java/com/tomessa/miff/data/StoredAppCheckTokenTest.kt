package com.tomessa.miff.data

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** AC-4: forgetting the token kept by the SDK is a best effort by reflection; it must say honestly whether it worked. */
class StoredAppCheckTokenTest {
    /** Looks like the SDK implementation: a public `resetAppCheckState()` and the private field of the background reading. */
    class LikeTheSdk {
        var resets = 0

        @Suppress("unused")
        private val retrieveStoredTokenTask: Task<Void> = TaskCompletionSource<Void>().task

        fun resetAppCheckState() {
            resets++
        }
    }

    class WithoutTheMethod

    class Throwing {
        fun resetAppCheckState(): Unit = throw IllegalStateException("x")
    }

    @Test
    fun whenTheMethodExists_itRuns_andTheAnswerIsTrue() {
        val sdk = LikeTheSdk()
        assertTrue(StoredAppCheckToken.forget(sdk))
        assertEquals(1, sdk.resets)
    }

    @Test
    fun whenTheMethodIsMissing_theAnswerIsFalse_andNothingIsThrown() {
        assertFalse(StoredAppCheckToken.forget(WithoutTheMethod()))
    }

    @Test
    fun whenTheMethodFails_theAnswerIsFalse() {
        assertFalse(StoredAppCheckToken.forget(Throwing()))
    }

    @Test
    fun theBackgroundReadingIsFoundByItsField_orIsNull() {
        assertNotNull(StoredAppCheckToken.retrieval(LikeTheSdk()))
        assertNull(StoredAppCheckToken.retrieval(WithoutTheMethod()))
    }
}
