package com.tomessa.miff.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** AC-4: the debug provider must never give a token (that is the whole point of the test of the enforcement). */
class DebugAppCheckNoTokenProviderTest {
    @Test
    fun theProviderNeverGivesAToken() {
        val task = DebugAppCheckNoTokenProvider().getToken()
        assertTrue("the answer is immediate", task.isComplete)
        assertFalse("no token", task.isSuccessful)
        assertNotNull("it ends with an error, not with an empty token", task.exception)
    }

    @Test
    fun theErrorCarriesNeitherASecretNorAPersonalDatum() {
        val message =
            DebugAppCheckNoTokenProvider()
                .getToken()
                .exception
                ?.message
                .orEmpty()
        assertTrue(message.isNotEmpty())
        assertFalse(message.contains("@"))
    }

    @Test
    fun theMarkerSearchedByTheCiIsTheOneOfTheCode() {
        // The CI greps "DEBUGAPPCHECKOFF" and "DebugAppCheck" in release and releaseLike: both must stay in this debug code.
        assertTrue(SharedPreferencesDebugAppCheckSetting.KEY == "DEBUGAPPCHECKOFF")
        assertTrue(DebugAppCheckNoTokenProvider::class.java.name.contains("DebugAppCheck"))
        assertTrue(DebugAppCheckNoTokenProviderFactory::class.java.name.contains("DebugAppCheck"))
    }
}
