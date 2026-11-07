package com.tomessa.miff.domain

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.tomessa.miff.domain.diagnostic.RulesProbeOutcome
import com.tomessa.miff.domain.diagnostic.interpretRulesProbeFailure
import com.tomessa.miff.domain.diagnostic.isExpectedProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DiagnosticLogicTest {
    private fun firestoreError(code: Code) = FirebaseFirestoreException("test", code)

    @Test
    fun permissionDeniedMeansTheRulesAreActive() {
        assertEquals(RulesProbeOutcome.RULES_ACTIVE, interpretRulesProbeFailure(firestoreError(Code.PERMISSION_DENIED)))
    }

    @Test
    fun unavailableOrTimeoutMeansOffline() {
        assertEquals(RulesProbeOutcome.OFFLINE, interpretRulesProbeFailure(firestoreError(Code.UNAVAILABLE)))
        assertEquals(RulesProbeOutcome.OFFLINE, interpretRulesProbeFailure(firestoreError(Code.DEADLINE_EXCEEDED)))
        assertEquals(RulesProbeOutcome.OFFLINE, interpretRulesProbeFailure(FirebaseNetworkException("offline")))
        assertEquals(RulesProbeOutcome.OFFLINE, interpretRulesProbeFailure(IOException("no route")))
    }

    @Test
    fun otherFailuresAreInconclusiveAndNeverReportedAsProtected() {
        assertEquals(RulesProbeOutcome.INCONCLUSIVE, interpretRulesProbeFailure(firestoreError(Code.UNAUTHENTICATED)))
        assertEquals(
            RulesProbeOutcome.INCONCLUSIVE,
            interpretRulesProbeFailure(firestoreError(Code.RESOURCE_EXHAUSTED)),
        )
        assertEquals(RulesProbeOutcome.INCONCLUSIVE, interpretRulesProbeFailure(IllegalStateException("?")))
    }

    @Test
    fun onlyTheMiffProjectIsExpected() {
        assertTrue(isExpectedProject("miff-3d283"))
        assertFalse(isExpectedProject("ghostrun-app"))
        assertFalse(isExpectedProject(null))
        assertFalse(isExpectedProject("MIFF-3D283"))
    }
}
