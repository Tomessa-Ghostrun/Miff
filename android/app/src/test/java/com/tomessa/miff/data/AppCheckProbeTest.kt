package com.tomessa.miff.data

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseException
import com.google.firebase.appcheck.AppCheckToken
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** AC-4: the token is "missing" ONLY when the SDK says so itself; any doubt leaves the answer of before. */
class AppCheckProbeTest {
    private val token =
        object : AppCheckToken() {
            override fun getToken(): String = "not-a-real-token"

            override fun getExpireTimeMillis(): Long = 0L
        }

    private fun probe(task: () -> Task<AppCheckToken>) = FirebaseAppCheckProbe(timeoutMillis = 50, askForToken = task)

    @Test
    fun aTokenInHand_isNotMissing() = runTest { assertFalse(probe { Tasks.forResult(token) }.tokenIsMissing()) }

    @Test
    fun theSdkSayingItCannotGiveOne_isMissing() =
        runTest { assertTrue(probe { Tasks.forException(FirebaseException("no token")) }.tokenIsMissing()) }

    @Test
    fun anErrorOfAnotherKind_isADoubt_notMissing() =
        runTest { assertFalse(probe { Tasks.forException(IllegalStateException("x")) }.tokenIsMissing()) }

    @Test
    fun noAnswerInTime_isADoubt_notMissing() =
        runTest { assertFalse(probe { TaskCompletionSource<AppCheckToken>().task }.tokenIsMissing()) }

    @Test
    fun theDefaultProbe_neverSaysMissing() = runTest { assertFalse(AppCheckProbe.None.tokenIsMissing()) }
}
