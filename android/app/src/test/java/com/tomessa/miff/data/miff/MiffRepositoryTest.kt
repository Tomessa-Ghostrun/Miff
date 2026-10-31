package com.tomessa.miff.data.miff

import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.tomessa.miff.domain.miff.LocationAccess
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.testsupport.CODE
import com.tomessa.miff.testsupport.FakeCachePurger
import com.tomessa.miff.testsupport.FakeLocationProbe
import com.tomessa.miff.testsupport.FakeMiffObserver
import com.tomessa.miff.testsupport.FakeMiffStore
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.OTHER_MIFF_ID
import com.tomessa.miff.testsupport.Timeline
import com.tomessa.miff.testsupport.firestoreError
import com.tomessa.miff.testsupport.miffDetails
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The logic of ticket Miff 5.4 to 5.7 and 5.13 with Firestore replaced by a double: the order of the checks, what is written
 * and in how many lots, how an error is read (a network cut is never a loss of access), the clean-up of a stale index.
 */
class MiffRepositoryTest {
    private val uid = "uid-1"
    private val store = FakeMiffStore()
    private val location = FakeLocationProbe()
    private var drawn = ArrayDeque<String>()
    private var now = 1_000_000L
    private val timeline = Timeline()
    private val cache = FakeCachePurger(timeline)

    private fun repo() =
        FirestoreMiffRepository(
            store = store,
            observer = FakeMiffObserver(),
            location = location,
            newMiffId = { MIFF_ID },
            newCode = { drawn.removeFirstOrNull() ?: CODE },
            nowMillis = { now },
            timeoutMillis = 1_000,
            cache = cache,
        )

    private fun joinable() {
        store.codes[CODE] = mapOf("miffId" to MIFF_ID, "miffName" to "Chez nous")
        store.miffs[MIFF_ID] = miffDetails()
    }

    private fun ready(preparation: JoinPreparation) = preparation as JoinPreparation.Ready

    // --- Join: order of the checks (ticket 5.4) -------------------------------------------------------------------

    @Test
    fun prepareJoin_aWrongShape_isNotFound_withoutAnyRead() =
        runTest {
            assertEquals(JoinPreparation.NotFound, repo().prepareJoin(uid, "XTZRSP36798"))
            assertEquals(JoinPreparation.NotFound, repo().prepareJoin(uid, "XTZRSP36798I"))
            assertTrue("a code of the wrong shape costs no network read (criterion J2)", store.calls.isEmpty())
        }

    @Test
    fun prepareJoin_readsTheCodeFirst_thenTheIndexes_andWritesNothing() =
        runTest {
            joinable()
            val result = repo().prepareJoin(uid, "xtzr sp36 7988")
            assertTrue(result is JoinPreparation.Ready)
            assertEquals("code", store.calls.first())
            assertTrue("memberships are read after the code", store.calls.indexOf("memberships:server") > 0)
            assertFalse("nothing is written before the confirmation (criteria J6 and K3)", "commit" in store.calls)
            assertTrue(store.commitsAsLots.isEmpty())
        }

    @Test
    fun prepareJoin_wrongRenewedOrDeletedCode_isTheSameAnswer() =
        runTest {
            // A code that does not exist (wrong, renewed or deleted: the document is simply not there).
            assertEquals(JoinPreparation.NotFound, repo().prepareJoin(uid, CODE))
            // A document that does not have the shape the rules require.
            store.codes[CODE] = mapOf("miffId" to "short", "miffName" to "x")
            assertEquals(JoinPreparation.NotFound, repo().prepareJoin(uid, CODE))
            store.codes[CODE] = mapOf("miffId" to MIFF_ID)
            assertEquals(JoinPreparation.NotFound, repo().prepareJoin(uid, CODE))
        }

    @Test
    fun prepareJoin_theRead_ofTheCodeFailingOffline_isANetworkProblem_notANotFound() =
        runTest {
            val offline =
                object : MiffStoreByDelegation(store) {
                    override suspend fun readJoinCode(code: String): Map<String, Any?>? =
                        throw firestoreError(Code.UNAVAILABLE)
                }
            val result =
                FirestoreMiffRepository(offline, FakeMiffObserver(), location, {
                    MIFF_ID
                }, { CODE }).prepareJoin(uid, CODE)
            assertEquals(JoinPreparation.Failed(MiffFailure.OFFLINE), result)
        }

    @Test
    fun prepareJoin_aStaleIndex_isCleanedBeforeAnythingElse_andTheEntryGoesOn() =
        runTest {
            joinable()
            // The person was removed from another Miff: the index is still there, the Miff answers "missing".
            store.memberships = listOf(MembershipRef("free", OTHER_MIFF_ID))
            val result = repo().prepareJoin(uid, CODE)
            assertTrue("the stale index does not block the entry (criterion J8)", result is JoinPreparation.Ready)
            val cleanup = store.commitsAsLots.single()
            assertEquals(WriteKind.DELETE, cleanup.first().kind)
            assertTrue(cleanup.any { it.path == "users/$uid/memberships/free" })
        }

    @Test
    fun prepareJoin_aRefusalOfTheRules_onAnIndex_isALoss_cleanedByTheLeaveLot_thenTheIndexAlone() =
        runTest {
            joinable()
            store.memberships = listOf(MembershipRef("free", OTHER_MIFF_ID))
            store.miffs[OTHER_MIFF_ID] = firestoreError(Code.PERMISSION_DENIED)
            // The card is gone (removed): the full "Quitter" lot is refused, the index alone is accepted.
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.PERMISSION_DENIED), null))
            val result = repo().prepareJoin(uid, CODE)
            assertTrue(result is JoinPreparation.Ready)
            assertEquals("only the lot that went through is recorded", 1, store.commitsAsLots.size)
            assertEquals(listOf("users/$uid/memberships/free"), store.commitsAsLots.single().map { it.path })
            assertEquals(2, store.calls.count { it == "commit" })
        }

    @Test
    fun aRefusal_whenTheServerIsNotReachable_isNotALoss_nothingIsCleaned() =
        runTest {
            joinable()
            store.memberships = listOf(MembershipRef("free", OTHER_MIFF_ID))
            store.miffs[OTHER_MIFF_ID] = firestoreError(Code.PERMISSION_DENIED)
            store.reachable = false
            val result = repo().prepareJoin(uid, CODE)
            assertEquals("a doubt is never a removal: no clean-up", 0, store.calls.count { it == "commit" })
            // The index stays (free account with a Miff it may still have): joining another one needs the Premium ticket.
            assertEquals(JoinPreparation.NeedsPremium, result)
        }

    @Test
    fun aNetworkCut_whileReadingAMiff_isNeverALossOfAccess() =
        runTest {
            joinable()
            store.memberships = listOf(MembershipRef("free", OTHER_MIFF_ID))
            store.miffs[OTHER_MIFF_ID] = firestoreError(Code.UNAVAILABLE)
            val load = repo().myMiffs(uid) as MyMiffsLoad.Loaded
            assertTrue(load.lost.isEmpty())
            assertEquals(0, store.calls.count { it == "commit" })
        }

    @Test
    fun alreadyMember_isFoundByTheFieldMiffId_notByTheIdOfTheDocument() =
        runTest {
            joinable()
            // The index document is called "free": only its field miffId says which Miff it is.
            store.memberships = listOf(MembershipRef("free", MIFF_ID))
            val result = repo().prepareJoin(uid, CODE)
            assertTrue(result is JoinPreparation.AlreadyMember)
            assertEquals(MIFF_ID, (result as JoinPreparation.AlreadyMember).miffId)
            assertTrue("nothing is written", store.commitsAsLots.isEmpty())
        }

    @Test
    fun aFreeAccount_withAnotherLiveMiff_isNotLetThrough_aPremiumOneIs() =
        runTest {
            joinable()
            store.memberships = listOf(MembershipRef("free", OTHER_MIFF_ID))
            store.miffs[OTHER_MIFF_ID] = miffDetails(id = OTHER_MIFF_ID, name = "Autre")
            assertEquals(JoinPreparation.NeedsPremium, repo().prepareJoin(uid, CODE))
            store.entitlement = EntitlementRead(active = true, expiresAtMillis = now + 10_000)
            val premium = ready(repo().prepareJoin(uid, CODE))
            assertTrue(premium.premium)
            assertFalse("the Miff is not the first one", premium.firstMiff)
        }

    @Test
    fun anExpiredEntitlement_isNotPremium() =
        runTest {
            store.entitlement = EntitlementRead(active = true, expiresAtMillis = now - 1)
            assertFalse((repo().myMiffs(uid) as MyMiffsLoad.Loaded).premium)
            store.entitlement = EntitlementRead(active = false, expiresAtMillis = now + 1000)
            assertFalse((repo().myMiffs(uid) as MyMiffsLoad.Loaded).premium)
        }

    // --- Join: the lot (ticket 5.4 step 7) ------------------------------------------------------------------------

    @Test
    fun join_isOneLotOfThree_andTheSuccessComesAfterTheServer() =
        runTest {
            joinable()
            val prepared = ready(repo().prepareJoin(uid, CODE))
            val result = repo().join(uid, prepared)
            assertEquals(JoinResult.Joined(MIFF_ID, firstMiff = true), result)
            val lot = store.commitsAsLots.single()
            assertEquals(3, lot.size)
            assertEquals("users/$uid/memberships/free", lot.first { it.path.startsWith("users/") }.path)
        }

    @Test
    fun join_ifOneWriteIsRefused_nothingIsWritten_andItIsTheOneMessageOfNotFound() =
        runTest {
            joinable()
            val prepared = ready(repo().prepareJoin(uid, CODE))
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.PERMISSION_DENIED)))
            assertEquals(JoinResult.NotFound, repo().join(uid, prepared))
            assertTrue("a refused lot leaves nothing behind", store.commitsAsLots.isEmpty())
        }

    @Test
    fun join_withoutNetwork_isOffline_nothingIsQueued() =
        runTest {
            joinable()
            val prepared = ready(repo().prepareJoin(uid, CODE))
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.UNAVAILABLE)))
            // The server cannot be reached to confirm either.
            store.miffs[MIFF_ID] = firestoreError(Code.UNAVAILABLE)
            assertEquals(JoinResult.Failed(MiffFailure.OFFLINE), repo().join(uid, prepared))
            assertEquals("one try, no retry in the background", 1, store.calls.count { it == "commit" })
            assertTrue(store.commitsAsLots.isEmpty())
        }

    @Test
    fun join_aLotThatLeftButWasNotConfirmed_isNotShownAsAFailure() =
        runTest {
            joinable()
            val prepared = ready(repo().prepareJoin(uid, CODE))
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.DEADLINE_EXCEEDED)))
            // The server did get the lot: the Miff can be read.
            assertEquals(JoinResult.Joined(MIFF_ID, firstMiff = true), repo().join(uid, prepared))
        }

    @Test
    fun join_theStatusUsesTheRealPermissionOfThePhone() =
        runTest {
            joinable()
            location.access = LocationAccess.FOREGROUND
            val prepared = ready(repo().prepareJoin(uid, CODE))
            repo().join(uid, prepared)
            val status = store.commitsAsLots.single().single { it.path.contains("/status/") }
            assertEquals("foreground", status.data["locationAccess"])
        }

    // --- Create (ticket 5.3, criteria C4, C5, C7) -----------------------------------------------------------------

    @Test
    fun create_isOneLotOfFive_withTheCodeDrawnAtTheSave() =
        runTest {
            val preparation = repo().prepareCreate(uid) as CreatePreparation.Ready
            assertTrue("nothing is drawn nor written by the preparation", store.commitsAsLots.isEmpty())
            val result = repo().create(uid, "  Chez nous ", preparation)
            assertEquals(CreateResult.Created(MIFF_ID, CODE, "Chez nous", firstMiff = true), result)
            val lot = store.commitsAsLots.single()
            assertEquals(5, lot.size)
            assertEquals(
                "a free account keys its index by 'free'",
                true,
                lot.any {
                    it.path ==
                        "users/$uid/memberships/free"
                },
            )
        }

    @Test
    fun create_aPremiumAccount_keysTheIndexByTheMiffId() =
        runTest {
            store.entitlement = EntitlementRead(active = true, expiresAtMillis = now + 10_000)
            val preparation = repo().prepareCreate(uid) as CreatePreparation.Ready
            repo().create(uid, "Chez nous", preparation)
            assertTrue(store.commitsAsLots.single().any { it.path == "users/$uid/memberships/$MIFF_ID" })
        }

    @Test
    fun create_aCodeAlreadyTaken_isDrawnAgain_upToThreeTimes_withoutAMessage() =
        runTest {
            drawn = ArrayDeque(listOf("AAAAAAAAAAAA", "BBBBBBBBBBBB", "CCCCCCCCCCCC"))
            store.codes["AAAAAAAAAAAA"] = mapOf("miffId" to OTHER_MIFF_ID, "miffName" to "x")
            store.codes["BBBBBBBBBBBB"] = mapOf("miffId" to OTHER_MIFF_ID, "miffName" to "x")
            val preparation = repo().prepareCreate(uid) as CreatePreparation.Ready
            val result = repo().create(uid, "Chez nous", preparation) as CreateResult.Created
            assertEquals("CCCCCCCCCCCC", result.joinCode)
        }

    @Test
    fun create_threeCodesTaken_endsWithTheGenericFailure_andNothingIsWritten() =
        runTest {
            drawn = ArrayDeque(listOf("AAAAAAAAAAAA", "BBBBBBBBBBBB", "CCCCCCCCCCCC"))
            drawn.forEach { store.codes[it] = mapOf("miffId" to OTHER_MIFF_ID, "miffName" to "x") }
            val preparation = repo().prepareCreate(uid) as CreatePreparation.Ready
            assertEquals(CreateResult.Failed(MiffFailure.UNKNOWN), repo().create(uid, "Chez nous", preparation))
            assertTrue(store.commitsAsLots.isEmpty())
        }

    @Test
    fun create_withoutNetwork_noMiffAppears_nothingIsQueued() =
        runTest {
            val preparation = repo().prepareCreate(uid) as CreatePreparation.Ready
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.UNAVAILABLE)))
            store.miffs[MIFF_ID] = firestoreError(Code.UNAVAILABLE)
            assertEquals(CreateResult.Failed(MiffFailure.OFFLINE), repo().create(uid, "Chez nous", preparation))
            assertTrue(store.commitsAsLots.isEmpty())
            assertEquals(1, store.calls.count { it == "commit" })
        }

    @Test
    fun create_ifOneWriteIsRefused_nothingIsWritten() =
        runTest {
            val preparation = repo().prepareCreate(uid) as CreatePreparation.Ready
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.PERMISSION_DENIED)))
            assertEquals(CreateResult.Failed(MiffFailure.REFUSED), repo().create(uid, "Chez nous", preparation))
            assertTrue(store.commitsAsLots.isEmpty())
        }

    @Test
    fun create_withoutAProfile_isNotAMessageAboutTheCode() =
        runTest {
            val preparation = repo().prepareCreate(uid) as CreatePreparation.Ready
            store.profile = null
            assertEquals(CreateResult.Failed(MiffFailure.NO_PROFILE), repo().create(uid, "Chez nous", preparation))
        }

    @Test
    fun create_aFreeAccountThatAlreadyHasAMiff_isHandedToThePremiumTicket_nothingIsWritten() =
        runTest {
            store.memberships = listOf(MembershipRef("free", OTHER_MIFF_ID))
            store.miffs[OTHER_MIFF_ID] = miffDetails(id = OTHER_MIFF_ID)
            assertEquals(CreatePreparation.NeedsPremium, repo().prepareCreate(uid))
            assertTrue(store.commitsAsLots.isEmpty())
        }

    @Test
    fun create_aTooShortName_isRefusedBeforeAnyWrite() =
        runTest {
            val preparation = repo().prepareCreate(uid) as CreatePreparation.Ready
            assertEquals(CreateResult.Failed(MiffFailure.UNKNOWN), repo().create(uid, " a ", preparation))
            assertEquals(0, store.calls.count { it == "commit" })
        }

    // --- The Miffs of the person ----------------------------------------------------------------------------------

    @Test
    fun myMiffs_offline_fallsBackOnTheCache_andSaysSo() =
        runTest {
            store.memberships = listOf(MembershipRef("free", MIFF_ID))
            store.membershipsFailure = firestoreError(Code.UNAVAILABLE)
            store.miffs[MIFF_ID] = miffDetails(name = "Chez nous")
            val load = repo().myMiffs(uid) as MyMiffsLoad.Loaded
            assertTrue(load.fromCache)
            assertEquals(listOf("Chez nous"), load.miffs.map { it.name })
            assertTrue(load.lost.isEmpty())
        }

    @Test
    fun myMiffs_nothingAtAllToShow_isUnavailable_notAnEmptyListThatLooksLikeNoMiff() =
        runTest {
            store.membershipsFailure = firestoreError(Code.UNAVAILABLE)
            store.cachedMembershipsFailure = firestoreError(Code.UNAVAILABLE)
            assertEquals(MyMiffsLoad.Unavailable, repo().myMiffs(uid))
        }

    @Test
    fun myMiffs_aDeletedMiff_isLost_withItsName() =
        runTest {
            store.memberships = listOf(MembershipRef("free", MIFF_ID))
            // Missing on the server, but the phone still knows its name from the cache.
            val ghost =
                object : MiffStoreByDelegation(store) {
                    override suspend fun readMiff(
                        miffId: String,
                        fromServer: Boolean,
                    ): MiffRead = if (fromServer) MiffRead.Missing else MiffRead.Exists(miffDetails(name = "Chez nous"))
                }
            val load =
                FirestoreMiffRepository(
                    ghost,
                    FakeMiffObserver(),
                    location,
                    { MIFF_ID },
                    { CODE },
                ).myMiffs(uid) as MyMiffsLoad.Loaded
            assertEquals("Chez nous", load.lost.single().name)
            assertTrue(load.miffs.isEmpty())
        }

    // --- Rename, sharing, clean-up --------------------------------------------------------------------------------

    @Test
    fun rename_isOneLotOfTwo_andABadNameWritesNothing() =
        runTest {
            assertEquals(MiffWriteResult.Done, repo().rename(MIFF_ID, CODE, "Nouveau nom"))
            assertEquals(2, store.commitsAsLots.single().size)
            assertEquals(MiffWriteResult.Failed(MiffFailure.UNKNOWN), repo().rename(MIFF_ID, CODE, "x"))
            assertEquals(1, store.commitsAsLots.size)
        }

    @Test
    fun setSharing_stop_isTheLotOfThree_resume_theLotOfTwo() =
        runTest {
            assertEquals(MiffWriteResult.Done, repo().setSharing(MIFF_ID, uid, enabled = false))
            assertEquals(3, store.commitsAsLots.last().size)
            assertEquals(MiffWriteResult.Done, repo().setSharing(MIFF_ID, uid, enabled = true))
            assertEquals(2, store.commitsAsLots.last().size)
        }

    @Test
    fun setSharing_offline_isAFailure_andNothingIsRecorded() =
        runTest {
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.UNAVAILABLE)))
            assertEquals(MiffWriteResult.Failed(MiffFailure.OFFLINE), repo().setSharing(MIFF_ID, uid, enabled = false))
            assertTrue(store.commitsAsLots.isEmpty())
        }

    @Test
    fun cleanUpLost_triesTheLeaveLot_thenTheIndexAlone() =
        runTest {
            val lost = LostMiff(MembershipRef("free", MIFF_ID), "Chez nous")
            assertTrue(repo().cleanUpLost(uid, lost))
            assertEquals(4, store.commitsAsLots.single().size)
            store.commitsAsLots.clear()
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.PERMISSION_DENIED), null))
            assertTrue(repo().cleanUpLost(uid, lost))
            assertEquals(1, store.commitsAsLots.single().size)
        }

    @Test
    fun aStaleIndexCleanedWhileCreatingOrJoining_neverLeavesThatMiffReadableOffline() =
        runTest {
            joinable()
            store.memberships = listOf(MembershipRef("free", OTHER_MIFF_ID))
            repo().prepareJoin(uid, CODE)
            assertEquals("markPending", timeline.entries.first())
            assertEquals("the cache of the phone is purged after the silent clean-up", 1, cache.purgeCalls)
            // Nothing to clean: no purge (a purge ends the listeners of the person for nothing).
            timeline.entries.clear()
            val before = cache.purgeCalls
            store.memberships = emptyList()
            repo().prepareCreate(uid)
            assertEquals(before, cache.purgeCalls)
        }

    @Test
    fun offlineWithAnEmptyCache_isNotNoMiff_itIsUnknown() =
        runTest {
            store.membershipsFailure = firestoreError(Code.UNAVAILABLE)
            store.memberships = emptyList()
            // A realistic fresh install: the server cannot be reached, the cache answers an empty list.
            assertEquals(MyMiffsLoad.Unavailable, repo().myMiffs(uid))
        }

    @Test
    fun rename_aLotWhoseAnswerWasLost_isCheckedOnTheServer_beforeItIsCalledAFailure() =
        runTest {
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.DEADLINE_EXCEEDED)))
            store.miffs[MIFF_ID] = miffDetails(name = "Nouveau nom")
            assertEquals(MiffWriteResult.Done, repo().rename(MIFF_ID, CODE, "Nouveau nom"))
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.DEADLINE_EXCEEDED)))
            store.miffs[MIFF_ID] = miffDetails(name = "Ancien nom")
            assertEquals(MiffWriteResult.Failed(MiffFailure.OFFLINE), repo().rename(MIFF_ID, CODE, "Nouveau nom"))
        }

    @Test
    fun setSharing_aLotWhoseAnswerWasLost_isCheckedOnTheServer() =
        runTest {
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.DEADLINE_EXCEEDED)))
            store.sharing = false
            assertEquals(MiffWriteResult.Done, repo().setSharing(MIFF_ID, uid, enabled = false))
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.DEADLINE_EXCEEDED)))
            store.sharing = true
            assertEquals(MiffWriteResult.Failed(MiffFailure.OFFLINE), repo().setSharing(MIFF_ID, uid, enabled = false))
        }
}

/** A store that forwards to [FakeMiffStore] so that one call can be replaced. */
open class MiffStoreByDelegation(
    private val inner: FakeMiffStore,
) : MiffStore {
    override suspend fun readJoinCode(code: String) = inner.readJoinCode(code)

    override suspend fun readMemberships(
        uid: String,
        fromServer: Boolean,
    ) = inner.readMemberships(uid, fromServer)

    override suspend fun readMiff(
        miffId: String,
        fromServer: Boolean,
    ) = inner.readMiff(miffId, fromServer)

    override suspend fun readEntitlement(uid: String) = inner.readEntitlement(uid)

    override suspend fun serverReachable(uid: String) = inner.serverReachable(uid)

    override suspend fun readSharing(
        miffId: String,
        uid: String,
    ) = inner.readSharing(miffId, uid)

    override suspend fun commitWithProfile(
        uid: String,
        build: (com.tomessa.miff.domain.miff.ProfileCopy) -> List<com.tomessa.miff.domain.miff.DocWrite>,
    ) = inner.commitWithProfile(uid, build)

    override suspend fun commit(writes: List<com.tomessa.miff.domain.miff.DocWrite>) = inner.commit(writes)
}
