package com.tomessa.miff.data.miff

import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.tomessa.miff.data.AppCheckProbe
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.testsupport.CODE
import com.tomessa.miff.testsupport.FakeActiveDevice
import com.tomessa.miff.testsupport.FakeLocationProbe
import com.tomessa.miff.testsupport.FakeMiffObserver
import com.tomessa.miff.testsupport.FakeMiffStore
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.firestoreError
import com.tomessa.miff.testsupport.miffDetails
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Cet appareil n'est pas reconnu pour le moment" (AC-4, lot B): a refusal of the server, on a phone that holds no App Check token,
 * is told apart from a refusal of the rules. Everything else is unchanged: a refusal with a token is the old answer, and a network
 * problem is never refined.
 */
class MiffRepositoryAppCheckRefusalTest {
    private val uid = "uid-1"
    private val store = FakeMiffStore()

    private class Probe(
        private val missing: Boolean,
    ) : AppCheckProbe {
        var asked = 0

        override suspend fun tokenIsMissing(): Boolean {
            asked++
            return missing
        }
    }

    private fun repo(
        probe: AppCheckProbe,
        source: MiffStore = store,
    ) = FirestoreMiffRepository(
        store = source,
        observer = FakeMiffObserver(),
        location = FakeLocationProbe(),
        device = FakeActiveDevice(),
        newMiffId = { MIFF_ID },
        newCode = { CODE },
        timeoutMillis = 1_000,
        appCheck = probe,
    )

    private fun joinable() {
        store.codes[CODE] = mapOf("miffId" to MIFF_ID, "miffName" to "Chez nous")
        store.miffs[MIFF_ID] = miffDetails()
    }

    private suspend fun readyToJoin(probe: AppCheckProbe): JoinPreparation.Ready {
        joinable()
        return repo(probe).prepareJoin(uid, CODE) as JoinPreparation.Ready
    }

    @Test
    fun join_refusedWithoutAToken_saysTheDeviceIsNotRecognized_notThatTheCodeIsUnknown() =
        runTest {
            val probe = Probe(missing = true)
            val prepared = readyToJoin(probe)
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.PERMISSION_DENIED)))
            assertEquals(JoinResult.Failed(MiffFailure.DEVICE_NOT_RECOGNIZED), repo(probe).join(uid, prepared))
            assertTrue("a refused lot leaves nothing behind", store.commitsAsLots.isEmpty())
        }

    @Test
    fun join_refusedWithAToken_isStillTheOneMessageOfNotFound() =
        runTest {
            val probe = Probe(missing = false)
            val prepared = readyToJoin(probe)
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.PERMISSION_DENIED)))
            assertEquals(JoinResult.NotFound, repo(probe).join(uid, prepared))
        }

    @Test
    fun join_refusedWhenTheTokenCannotBeChecked_isStillTheOneMessageOfNotFound() =
        runTest {
            // The default probe knows nothing: no new answer is ever given on a doubt.
            val prepared = readyToJoin(AppCheckProbe.None)
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.PERMISSION_DENIED)))
            assertEquals(JoinResult.NotFound, repo(AppCheckProbe.None).join(uid, prepared))
        }

    @Test
    fun prepareJoin_theCodeRead_refusedWithoutAToken_isNotRecognized_andNotAWrongCode() =
        runTest {
            val probe = Probe(missing = true)
            val refusing =
                object : MiffStoreByDelegation(store) {
                    override suspend fun readJoinCode(code: String): Map<String, Any?>? =
                        throw firestoreError(Code.PERMISSION_DENIED)
                }
            assertEquals(
                JoinPreparation.Failed(MiffFailure.DEVICE_NOT_RECOGNIZED),
                repo(probe, refusing).prepareJoin(uid, CODE),
            )
        }

    @Test
    fun prepareJoin_theCodeRead_refusedWithAToken_keepsTheGenericRefusal() =
        runTest {
            val refusing =
                object : MiffStoreByDelegation(store) {
                    override suspend fun readJoinCode(code: String): Map<String, Any?>? =
                        throw firestoreError(Code.PERMISSION_DENIED)
                }
            assertEquals(
                JoinPreparation.Failed(MiffFailure.REFUSED),
                repo(Probe(missing = false), refusing).prepareJoin(uid, CODE),
            )
        }

    @Test
    fun create_refusedWithoutAToken_isNotRecognized() =
        runTest {
            val probe = Probe(missing = true)
            val preparation = repo(probe).prepareCreate(uid) as CreatePreparation.Ready
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.PERMISSION_DENIED)))
            assertEquals(
                CreateResult.Failed(MiffFailure.DEVICE_NOT_RECOGNIZED),
                repo(probe).create(uid, "Chez nous", preparation),
            )
        }

    @Test
    fun create_refusedWithAToken_keepsTheGenericRefusal() =
        runTest {
            val probe = Probe(missing = false)
            val preparation = repo(probe).prepareCreate(uid) as CreatePreparation.Ready
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.PERMISSION_DENIED)))
            assertEquals(CreateResult.Failed(MiffFailure.REFUSED), repo(probe).create(uid, "Chez nous", preparation))
        }

    @Test
    fun aNetworkProblem_isNeverRefined_andTheTokenIsNotEvenChecked() =
        runTest {
            val probe = Probe(missing = true)
            val prepared = readyToJoin(probe)
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.UNAVAILABLE)))
            // The server cannot be reached to confirm either (otherwise the fake would find the Miff and call it joined).
            store.miffs[MIFF_ID] = firestoreError(Code.UNAVAILABLE)
            assertEquals(JoinResult.Failed(MiffFailure.OFFLINE), repo(probe).join(uid, prepared))
            assertEquals("no token check for something that is not a refusal", 0, probe.asked)
        }
}
