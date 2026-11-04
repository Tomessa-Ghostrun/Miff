package com.tomessa.miff.ui.miff

import com.tomessa.miff.data.miff.CodeChangeResult
import com.tomessa.miff.data.miff.DeletionState
import com.tomessa.miff.data.miff.MiffWriteResult
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffObservation
import com.tomessa.miff.domain.miff.MiffPlace
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.miff.MiffSnapshot
import com.tomessa.miff.testsupport.FakeDeletionController
import com.tomessa.miff.testsupport.FakeMiffRepository
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.miffDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The screen of a Miff, MIFF-2 part (ticket R, N, Q, S, D): who can ask for what, the question before the lot, one lot per tap,
 * calm answers, and a departure the person chose is never announced to them as a loss.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MiffScreenMiff2Test {
    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeMiffRepository()
    private val network = FakeNetwork()
    private val deletions = FakeDeletionController()
    private val observing = CoroutineScope(dispatcher + SupervisorJob())
    private val left = mutableListOf<String>()
    private val lost = mutableListOf<String>()
    private var directoryChanges = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        observing.cancel()
        Dispatchers.resetMain()
    }

    private fun person(
        uid: String,
        role: MiffRole = MiffRole.MEMBER,
    ) = MiffMember(uid, "Prénom-$uid", "Nom", null, role, true)

    private fun data(
        owner: String = "uid-owner",
        members: List<MiffMember> = listOf(person("uid-owner", MiffRole.OWNER), person("uid-2"), person("uid-3")),
        code: String = "XTZRSP367988",
    ) = MiffObservation.Data(
        MiffSnapshot(
            miffDetails(code = code, owner = owner),
            members,
            listOf(MiffPlace("p1", "Maison")),
            fromCache = false,
        ),
    )

    /** The live view now holds the creator and these members (and, if given, this code). */
    private suspend fun emitMembers(
        vararg others: String,
        code: String = "XTZRSP367988",
    ) {
        val members = listOf(person("uid-owner", MiffRole.OWNER)) + others.map { person(it) }
        repo.observer.flowOf(MIFF_ID).emit(data(members = members, code = code))
    }

    private fun screen(uid: String = "uid-owner") =
        MiffScreenViewModel(
            miffId = MIFF_ID,
            repository = repo,
            network = network,
            currentUid = { uid },
            onAccessLost = { id, _ ->
                lost += id
                true
            },
            onNameChanged = { directoryChanges++ },
            deletions = deletions,
            onLeft = { left += it },
        ).also { vm -> observing.launch { vm.observe() } }

    private suspend fun kotlinx.coroutines.test.TestScope.ready(
        uid: String = "uid-owner",
        observation: MiffObservation = data(),
    ): MiffScreenViewModel {
        val vm = screen(uid)
        advanceUntilIdle()
        repo.observer.flowOf(MIFF_ID).emit(observation)
        advanceUntilIdle()
        return vm
    }

    // --- R: remove --------------------------------------------------------------------------------------------------

    @Test
    fun remove_askFirst_nothingIsWrittenUntilTheConfirmation() =
        runTest(dispatcher) {
            val vm = ready()
            vm.onRemoveRequested("uid-2")
            val action = vm.uiState.value.action as MiffAction.Remove
            assertEquals("Prénom-uid-2", action.firstName)
            assertTrue("no lot before the confirmation", repo.removed.isEmpty())
            vm.onActionDismissed()
            assertNull(vm.uiState.value.action)
            assertTrue(repo.removed.isEmpty())
        }

    @Test
    fun remove_confirm_writesOnce_andShowsTheNewCode_onlyAfterTheServer() =
        runTest(dispatcher) {
            val vm = ready()
            vm.onRemoveRequested("uid-2")
            var stateWhenWriting: MiffAction? = null
            repo.onRemove = { stateWhenWriting = vm.uiState.value.action }
            vm.onActionConfirmed()
            vm.onActionConfirmed()
            assertTrue("one tap, one operation", vm.uiState.value.actionBusy)
            advanceUntilIdle()
            assertEquals(listOf("uid-2"), repo.removed)
            assertTrue("while the lot is on its way the question is still shown", stateWhenWriting is MiffAction.Remove)
            val done = vm.uiState.value.action as MiffAction.RemovedDone
            assertEquals("NEWCODE23456", done.newCode)
            assertEquals("Prénom-uid-2", done.firstName)
            assertFalse(vm.uiState.value.actionBusy)
            vm.onActionConfirmed() // "Terminer"
            assertNull(vm.uiState.value.action)
        }

    @Test
    fun remove_onlyTheCreator_andNeverTheCreatorsOwnLine() =
        runTest(dispatcher) {
            val member = ready(uid = "uid-2")
            member.onRemoveRequested("uid-3")
            assertNull("a member cannot remove anybody", member.uiState.value.action)
            val creator = ready()
            creator.onRemoveRequested("uid-owner")
            assertNull("nobody removes themselves", creator.uiState.value.action)
            creator.onRemoveRequested("unknown")
            assertNull(creator.uiState.value.action)
        }

    @Test
    fun remove_offline_saysSoCalmly_andSendsNothing() =
        runTest(dispatcher) {
            val vm = ready()
            vm.onRemoveRequested("uid-2")
            network.online = false
            advanceUntilIdle()
            assertFalse("the buttons that write are grey when offline", vm.uiState.value.canWrite)
            vm.onActionConfirmed()
            assertEquals(ActionMessage.OFFLINE, vm.uiState.value.message)
            advanceUntilIdle()
            assertTrue(repo.removed.isEmpty())
            assertFalse(vm.uiState.value.actionBusy)
        }

    @Test
    fun remove_aFailure_keepsTheQuestion_withACalmMessage_andCanBeTriedAgain() =
        runTest(dispatcher) {
            repo.removeResult = CodeChangeResult.Failed(MiffFailure.REFUSED)
            val vm = ready()
            vm.onRemoveRequested("uid-2")
            vm.onActionConfirmed()
            advanceUntilIdle()
            assertEquals(ActionMessage.GENERIC, vm.uiState.value.message)
            assertTrue(vm.uiState.value.action is MiffAction.Remove)
            repo.removeResult = CodeChangeResult.Changed("NEWCODE23456")
            vm.onActionConfirmed()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.action is MiffAction.RemovedDone)
        }

    @Test
    fun remove_whileTheLotIsOnItsWay_theQuestionCannotBeDismissed() =
        runTest(dispatcher) {
            val vm = ready()
            vm.onRemoveRequested("uid-2")
            vm.onActionConfirmed()
            vm.onActionDismissed()
            assertTrue(vm.uiState.value.action is MiffAction.Remove)
            advanceUntilIdle()
        }

    // --- N: renew ---------------------------------------------------------------------------------------------------

    @Test
    fun renew_asksFirst_thenOneLot_andTheNewCodeComesFromTheLiveView() =
        runTest(dispatcher) {
            val vm = ready()
            vm.onRenewRequested()
            assertEquals(MiffAction.Renew, vm.uiState.value.action)
            assertTrue(repo.calls.none { it == "renewCode" })
            vm.onActionConfirmed()
            advanceUntilIdle()
            assertEquals(1, repo.calls.count { it == "renewCode" })
            assertNull(vm.uiState.value.action)
            assertTrue(vm.uiState.value.codeRenewed)
            // The screen shows the code the server holds, read live: not a copy made here.
            repo.observer.flowOf(MIFF_ID).emit(data(code = "BCDFGHK23456"))
            advanceUntilIdle()
            assertEquals("BCDFGHK23456", vm.uiState.value.joinCode)
        }

    @Test
    fun renew_onlyTheCreator() =
        runTest(dispatcher) {
            val vm = ready(uid = "uid-2")
            vm.onRenewRequested()
            assertNull(vm.uiState.value.action)
        }

    @Test
    fun renew_aFailure_isCalm_andOffline_sendsNothing() =
        runTest(dispatcher) {
            repo.renewResult = CodeChangeResult.Failed(MiffFailure.OFFLINE)
            val vm = ready()
            vm.onRenewRequested()
            vm.onActionConfirmed()
            advanceUntilIdle()
            assertEquals(ActionMessage.OFFLINE, vm.uiState.value.message)
            assertFalse(vm.uiState.value.codeRenewed)
            network.online = false
            advanceUntilIdle()
            repo.calls.clear()
            vm.onActionConfirmed()
            advanceUntilIdle()
            assertTrue("offline: nothing is sent", repo.calls.none { it == "renewCode" })
        }

    // --- N3: the invitation after a departure -----------------------------------------------------------------------

    @Test
    fun invitation_aMemberWhoLeaves_invitesTheCreator_once_andLaterClosesIt() =
        runTest(dispatcher) {
            val vm = ready()
            assertFalse("nothing to invite about at the first picture", vm.uiState.value.inviteToRenew)
            emitMembers("uid-3")
            advanceUntilIdle()
            assertTrue(vm.uiState.value.inviteToRenew)
            vm.onInviteLater()
            assertFalse(vm.uiState.value.inviteToRenew)
            // The same list again does not bring it back.
            emitMembers("uid-3", code = "BCDFGHK23456")
            advanceUntilIdle()
            assertFalse(vm.uiState.value.inviteToRenew)
            // Another departure is another invitation.
            emitMembers()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.inviteToRenew)
        }

    @Test
    fun invitation_neverForARemovalTheCreatorJustMade() =
        runTest(dispatcher) {
            val vm = ready()
            vm.onRemoveRequested("uid-2")
            vm.onActionConfirmed()
            // The listener hears the disappearance BEFORE the answer comes back.
            emitMembers("uid-3")
            advanceUntilIdle()
            assertFalse(vm.uiState.value.inviteToRenew)
        }

    @Test
    fun invitation_notForAMember_andClosedByAnyRenewal() =
        runTest(dispatcher) {
            val member = ready(uid = "uid-2")
            emitMembers("uid-2")
            advanceUntilIdle()
            assertFalse(member.uiState.value.inviteToRenew)
        }

    @Test
    fun invitation_renewingClosesIt() =
        runTest(dispatcher) {
            val vm = ready()
            emitMembers()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.inviteToRenew)
            vm.onRenewRequested()
            vm.onActionConfirmed()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.inviteToRenew)
        }

    // --- Q: leave ---------------------------------------------------------------------------------------------------

    @Test
    fun leave_theCreatorHasNone_aMemberAsksFirst() =
        runTest(dispatcher) {
            val creator = ready()
            creator.onLeaveRequested()
            assertNull("the creator can only delete the Miff (decision QM1)", creator.uiState.value.action)
            val member = ready(uid = "uid-2")
            member.onLeaveRequested()
            assertEquals(MiffAction.Leave, member.uiState.value.action)
            assertTrue(repo.calls.none { it == "leave" })
        }

    @Test
    fun leave_confirm_writesOnce_thenTheCacheAndTheListAreDealtWith_andTheScreenCloses_withoutAnyLostAccessMessage() =
        runTest(dispatcher) {
            val vm = ready(uid = "uid-2")
            vm.onLeaveRequested()
            vm.onActionConfirmed()
            vm.onActionConfirmed()
            // The listener hears the refusal at once, as the lot lands: this is the leaving itself, not a loss.
            repo.observer.flowOf(MIFF_ID).emit(MiffObservation.AccessLost)
            advanceUntilIdle()
            assertEquals(1, repo.calls.count { it == "leave" })
            assertEquals(listOf(MIFF_ID), left)
            assertTrue(vm.uiState.value.departed)
            assertTrue("no message of lost access for what the person chose", lost.isEmpty())
            assertNull(vm.uiState.value.action)
        }

    @Test
    fun leave_offline_orRefused_keepsTheScreen_andStaysAMember() =
        runTest(dispatcher) {
            repo.leaveResult = MiffWriteResult.Failed(MiffFailure.OFFLINE)
            val vm = ready(uid = "uid-2")
            vm.onLeaveRequested()
            vm.onActionConfirmed()
            advanceUntilIdle()
            assertEquals(ActionMessage.OFFLINE, vm.uiState.value.message)
            assertFalse(vm.uiState.value.departed)
            assertTrue(left.isEmpty())
            assertEquals(MiffAction.Leave, vm.uiState.value.action)
            // A real removal that happens afterwards is still seen (the listeners were attached again).
            repo.observer.flowOf(MIFF_ID).emit(MiffObservation.AccessLost)
            advanceUntilIdle()
            assertEquals(listOf(MIFF_ID), lost)
        }

    // --- S: delete --------------------------------------------------------------------------------------------------

    @Test
    fun delete_theQuestionKnowsTheNumberOfOthers_andOnlyTheCreatorAsks() =
        runTest(dispatcher) {
            val vm = ready()
            vm.onDeleteRequested()
            assertEquals(MiffAction.Delete(2), vm.uiState.value.action)
            val member = ready(uid = "uid-2")
            member.onDeleteRequested()
            assertNull(member.uiState.value.action)
            val alone = ready(observation = data(members = listOf(person("uid-owner", MiffRole.OWNER))))
            alone.onDeleteRequested()
            // The count is 0: the screen then says another sentence (never "0 proche").
            assertEquals(MiffAction.Delete(0), alone.uiState.value.action)
        }

    @Test
    fun delete_confirm_startsTheDeletionOnce_andTheScreenFollowsItsState() =
        runTest(dispatcher) {
            val vm = ready()
            vm.onDeleteRequested()
            vm.onActionConfirmed()
            advanceUntilIdle()
            assertEquals(listOf(MIFF_ID), deletions.started)
            assertNull(vm.uiState.value.action)
            assertEquals(DeletionState.Running(resumed = false), vm.uiState.value.deletion)
            assertFalse("nothing else is written on a Miff under deletion", vm.uiState.value.canWrite)
        }

    @Test
    fun delete_theMembersDisappearOneByOne_withoutInvitationNorLostAccess_untilTheEnd() =
        runTest(dispatcher) {
            val vm = ready()
            vm.onDeleteRequested()
            vm.onActionConfirmed()
            advanceUntilIdle()
            val flow = repo.observer.flowOf(MIFF_ID)
            emitMembers("uid-3")
            advanceUntilIdle()
            assertFalse(vm.uiState.value.inviteToRenew)
            // The last lot: the Miff no longer exists. It is the creator's own doing, not a loss.
            flow.emit(MiffObservation.AccessLost)
            advanceUntilIdle()
            assertTrue(lost.isEmpty())
            deletions.set(MIFF_ID, DeletionState.Done)
            advanceUntilIdle()
            assertEquals(DeletionState.Done, vm.uiState.value.deletion)
            assertEquals(MiffScreenStatus.READY, vm.uiState.value.status)
        }

    @Test
    fun delete_stuck_canBeTriedAgain_onlyWhenStuck() =
        runTest(dispatcher) {
            val vm = ready()
            vm.onDeletionRetry()
            assertTrue(deletions.retried.isEmpty())
            deletions.set(MIFF_ID, DeletionState.Stuck)
            advanceUntilIdle()
            vm.onDeletionRetry()
            assertEquals(listOf(MIFF_ID), deletions.retried)
        }

    @Test
    fun delete_offline_isRefusedCalmly_andStartsNothing() =
        runTest(dispatcher) {
            val vm = ready()
            vm.onDeleteRequested()
            network.online = false
            advanceUntilIdle()
            vm.onActionConfirmed()
            assertEquals(ActionMessage.OFFLINE, vm.uiState.value.message)
            assertTrue(deletions.started.isEmpty())
        }

    @Test
    fun aDeletionAlreadyUnderWay_blocksEveryOtherAction() =
        runTest(dispatcher) {
            deletions.set(MIFF_ID, DeletionState.Running(resumed = true))
            val vm = ready()
            vm.onRemoveRequested("uid-2")
            vm.onRenewRequested()
            vm.onDeleteRequested()
            vm.onSharingRequested()
            assertNull(vm.uiState.value.action)
            assertNull(vm.uiState.value.sharingDialog)
            assertFalse(vm.uiState.value.canSaveName)
        }
}
