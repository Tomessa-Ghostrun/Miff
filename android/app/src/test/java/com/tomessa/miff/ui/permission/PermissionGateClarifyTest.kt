package com.tomessa.miff.ui.permission

import com.tomessa.miff.data.miff.AwaitingAgreement
import com.tomessa.miff.data.miff.ClarifyAskStore
import com.tomessa.miff.data.miff.ClarifyPolicy
import com.tomessa.miff.data.miff.ConsentRenewalCheck
import com.tomessa.miff.data.miff.ConsentRenewals
import com.tomessa.miff.data.miff.InMemoryClarifyAskStore
import com.tomessa.miff.data.miff.MiffWriteResult
import com.tomessa.miff.data.miff.RenewalMiff
import com.tomessa.miff.data.permission.DevicePermissions
import com.tomessa.miff.data.permission.NotificationAskStore
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.permission.GateScreen
import com.tomessa.miff.domain.permission.PermissionSnapshot
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.OTHER_MIFF_ID
import com.tomessa.miff.ui.miff.ActionMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CONS-4-A, option D of the PM: the three populations at the gate. v1/v2 = the blocking renewal (nothing changes); NEW accounts = nothing (the card is
 * in the entry lot); v3 = the calm card "Une précision", WITHOUT any pause (no "En attente", no screen that holds the Feed), at most 3 times, then
 * only "Revoir et accepter" in Settings > Confidentialité.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PermissionGateClarifyTest {
    private val dispatcher = StandardTestDispatcher()

    private class Phone(
        var snapshot: PermissionSnapshot,
    ) : DevicePermissions {
        override fun snapshot() = snapshot
    }

    /** Notifications already handled: the calm card is the only thing between the person and the Feed. */
    private class Asks : NotificationAskStore {
        override fun wasShown(uid: String) = true

        override fun markShown(uid: String) {}
    }

    private class Renewals(
        var check: ConsentRenewalCheck = ConsentRenewalCheck.NothingToRenew,
        var renewResult: MiffWriteResult = MiffWriteResult.Done,
    ) : ConsentRenewals {
        val renewed = mutableListOf<List<String>>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun check(uid: String): ConsentRenewalCheck = check

        override suspend fun renew(
            uid: String,
            miffIds: List<String>,
        ): MiffWriteResult {
            renewed += miffIds
            gate?.await()
            return renewResult
        }
    }

    private val full = PermissionSnapshot(34, true, true, true, true)
    private val renewals = Renewals()
    private val counts = InMemoryClarifyAskStore()
    private val uid = "uid-1"

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        phone: Phone = Phone(full),
        awaiting: AwaitingAgreement = AwaitingAgreement(),
        asks: ClarifyAskStore = counts,
    ) = PermissionGateViewModel(
        phone,
        Asks(),
        renewals,
        { uid },
        true,
        leaver = { _, _ -> MiffWriteResult.Done },
        onLeft = {},
        awaiting = awaiting,
        clarifyAsks = asks,
    )

    private val v3Only = ConsentRenewalCheck.Due(emptyList(), listOf(RenewalMiff(MIFF_ID, "Chez nous")))

    // --- The three populations -----------------------------------------------------------------------------------------

    @Test
    fun aVersion3Account_seesTheCalmCard_andNothingIsPaused() =
        runTest {
            renewals.check = v3Only
            val awaiting = AwaitingAgreement()
            val model = vm(awaiting = awaiting)
            advanceUntilIdle()
            assertEquals(GateScreen.CLARIFY, model.uiState.value.screen)
            assertNotNull(model.uiState.value.clarify)
            // The blocking renewal screen does not exist for this account, and NOBODY is "waiting" (no pill, no card "En attente").
            assertNull(model.uiState.value.renewal)
            assertEquals(emptySet<String>(), awaiting.waiting.value)
            assertEquals(setOf(MIFF_ID), awaiting.clarifying.value)
            assertTrue("nothing is written before a tap", renewals.renewed.isEmpty())
        }

    @Test
    fun aVersion1Or2Account_keepsTheBlockingRenewal_andIsWaiting() =
        runTest {
            renewals.check = ConsentRenewalCheck.Due(listOf(RenewalMiff(MIFF_ID, "Chez nous")))
            val awaiting = AwaitingAgreement()
            val model = vm(awaiting = awaiting)
            advanceUntilIdle()
            assertEquals(GateScreen.RENEWAL, model.uiState.value.screen)
            assertNull(model.uiState.value.clarify)
            assertEquals(setOf(MIFF_ID), awaiting.waiting.value)
            assertEquals(emptySet<String>(), awaiting.clarifying.value)
        }

    @Test
    fun aNewAccount_hasNoCalmCardAndNoRenewal() =
        runTest {
            val model = vm()
            advanceUntilIdle()
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
            assertNull(model.uiState.value.clarify)
            assertNull(model.uiState.value.renewal)
        }

    @Test
    fun aMixOfPopulations_theBlockingOneComesFirst_thenTheCalmCard() =
        runTest {
            renewals.check =
                ConsentRenewalCheck.Due(
                    listOf(RenewalMiff(MIFF_ID, "Chez nous")),
                    listOf(RenewalMiff(OTHER_MIFF_ID, "Amis")),
                )
            val awaiting = AwaitingAgreement()
            val model = vm(awaiting = awaiting)
            advanceUntilIdle()
            assertEquals(GateScreen.RENEWAL, model.uiState.value.screen)
            // Only the paused Miff is "waiting"; the clarified one is not.
            assertEquals(setOf(MIFF_ID), awaiting.waiting.value)
            assertEquals(setOf(OTHER_MIFF_ID), awaiting.clarifying.value)
            model.onRenewalAccepted()
            advanceUntilIdle()
            assertEquals(GateScreen.CLARIFY, model.uiState.value.screen)
            assertEquals(listOf(listOf(MIFF_ID)), renewals.renewed)
        }

    // --- The buttons -----------------------------------------------------------------------------------------------------

    @Test
    fun iUnderstood_writesTheRenewalOnce_forTheClarifiedMiffs_andClosesTheCard() =
        runTest {
            renewals.check = v3Only
            renewals.gate = CompletableDeferred()
            val awaiting = AwaitingAgreement()
            val model = vm(awaiting = awaiting)
            advanceUntilIdle()
            model.onClarifyAccepted()
            model.onClarifyAccepted()
            advanceUntilIdle()
            assertTrue(
                model.uiState.value.clarify!!
                    .busy,
            )
            assertEquals("one tap = one write", 1, renewals.renewed.size)
            renewals.gate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(listOf(MIFF_ID)), renewals.renewed)
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
            assertNull(model.uiState.value.clarify)
            assertEquals(emptySet<String>(), awaiting.clarifying.value)
        }

    @Test
    fun later_writesNothing_pausesNothing_andTheFeedIsShown() =
        runTest {
            renewals.check = v3Only
            val awaiting = AwaitingAgreement()
            val model = vm(awaiting = awaiting)
            advanceUntilIdle()
            model.onClarifyPutOff()
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
            assertTrue(renewals.renewed.isEmpty())
            assertEquals(emptySet<String>(), awaiting.waiting.value)
            // Still clarifiable from Settings.
            assertEquals(setOf(MIFF_ID), awaiting.clarifying.value)
        }

    @Test
    fun aFailedAgreement_keepsTheCard_withACalmMessage_andNothingIsPaused() =
        runTest {
            renewals.check = v3Only
            renewals.renewResult = MiffWriteResult.Failed(MiffFailure.OFFLINE)
            val awaiting = AwaitingAgreement()
            val model = vm(awaiting = awaiting)
            advanceUntilIdle()
            model.onClarifyAccepted()
            advanceUntilIdle()
            assertEquals(GateScreen.CLARIFY, model.uiState.value.screen)
            assertEquals(
                ActionMessage.OFFLINE,
                model.uiState.value.clarify!!
                    .message,
            )
            assertFalse(
                model.uiState.value.clarify!!
                    .busy,
            )
            assertEquals(emptySet<String>(), awaiting.waiting.value)
        }

    // --- At most three times, then Settings --------------------------------------------------------------------------------

    @Test
    fun theCard_comesBackAtMostThreeTimes_oncePerLaunch_thenOnlyFromSettings() =
        runTest {
            renewals.check = v3Only
            val phone = Phone(full)
            val seen = mutableListOf<GateScreen>()
            repeat(5) {
                val model = vm(phone)
                advanceUntilIdle()
                seen += model.uiState.value.screen
                model.onClarifyPutOff()
            }
            assertEquals(
                listOf(GateScreen.CLARIFY, GateScreen.CLARIFY, GateScreen.CLARIFY, GateScreen.NONE, GateScreen.NONE),
                seen,
            )
            assertEquals(ClarifyPolicy.MAX_SHOWS, counts.shownCount(uid))
        }

    @Test
    fun aLaunch_countsOnce_evenIfTheStateIsPublishedAgain() =
        runTest {
            renewals.check = v3Only
            val model = vm()
            advanceUntilIdle()
            model.onResumed()
            model.onResumed()
            assertEquals(1, counts.shownCount(uid))
        }

    @Test
    fun afterTheThirdTime_settingsStillOffersIt_andAsking_showsItAgain_withoutCountingAndWithoutBlocking() =
        runTest {
            renewals.check = v3Only
            repeat(ClarifyPolicy.MAX_SHOWS) { counts.markShown(uid) }
            val awaiting = AwaitingAgreement()
            val model = vm(awaiting = awaiting)
            advanceUntilIdle()
            assertEquals("not by itself any more", GateScreen.NONE, model.uiState.value.screen)
            assertEquals("Settings still lists it", setOf(MIFF_ID), awaiting.clarifying.value)
            awaiting.requestReview()
            advanceUntilIdle()
            assertEquals(GateScreen.CLARIFY, model.uiState.value.screen)
            assertEquals(
                "a card asked for from Settings is not counted",
                ClarifyPolicy.MAX_SHOWS,
                counts.shownCount(uid),
            )
            model.onClarifyPutOff()
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
        }

    @Test
    fun aReviewRequestedFromSettings_whenNothingIsToClarify_changesNothing() =
        runTest {
            val awaiting = AwaitingAgreement()
            val model = vm(awaiting = awaiting)
            advanceUntilIdle()
            awaiting.requestReview()
            advanceUntilIdle()
            assertEquals(GateScreen.NONE, model.uiState.value.screen)
        }

    @Test
    fun theCountIsPerAccount_andSurvivesANewLaunch() =
        runTest {
            renewals.check = v3Only
            vm()
            advanceUntilIdle()
            assertEquals(1, counts.shownCount(uid))
            assertEquals(0, counts.shownCount("someone-else"))
            counts.forget(uid)
            assertEquals(0, counts.shownCount(uid))
        }

    @Test
    fun theCountNeverGoesPastTheMaximum() {
        repeat(10) { counts.markShown(uid) }
        assertEquals(ClarifyPolicy.MAX_SHOWS, counts.shownCount(uid))
        assertFalse(ClarifyPolicy.mayShowByItself(counts.shownCount(uid)))
        assertTrue(ClarifyPolicy.mayShowByItself(0))
        assertTrue(ClarifyPolicy.mayShowByItself(2))
    }
}
