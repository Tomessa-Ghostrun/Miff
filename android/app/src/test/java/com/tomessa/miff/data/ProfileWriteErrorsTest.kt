package com.tomessa.miff.data

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.tomessa.miff.data.auth.ProfileWriteResult
import com.tomessa.miff.data.auth.interpretWriteError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/** How a refused or failed write of the profile is worded: a missing network is never confused with a refusal (X2). */
class ProfileWriteErrorsTest {
    private fun result(code: Code) =
        interpretWriteError(FirebaseFirestoreException("message with a name and a number", code))

    @Test
    fun noNetworkOrTooSlow_isOffline() {
        assertEquals(ProfileWriteResult.OFFLINE, result(Code.UNAVAILABLE))
        assertEquals(ProfileWriteResult.OFFLINE, result(Code.DEADLINE_EXCEEDED))
    }

    @Test
    fun aRefusalIsARefusal_notAMissingNetwork() {
        assertEquals(ProfileWriteResult.REFUSED, result(Code.PERMISSION_DENIED))
        assertEquals(ProfileWriteResult.REFUSED, result(Code.UNAUTHENTICATED))
    }

    @Test
    fun anythingElseIsAGenericFailure() {
        assertEquals(ProfileWriteResult.FAILED, result(Code.ABORTED))
        assertEquals(ProfileWriteResult.FAILED, result(Code.INTERNAL))
        assertEquals(ProfileWriteResult.FAILED, interpretWriteError(IOException("boom")))
        assertEquals(ProfileWriteResult.FAILED, interpretWriteError(IllegalStateException()))
    }
}
