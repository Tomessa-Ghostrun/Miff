package com.tomessa.miff.ui.miff

import com.tomessa.miff.data.miff.CreatePreparation
import com.tomessa.miff.data.miff.CreateResult
import com.tomessa.miff.data.miff.JoinPreparation
import com.tomessa.miff.data.miff.JoinResult
import com.tomessa.miff.data.miff.MiffWriteResult
import com.tomessa.miff.domain.analytics.AnalyticsEvent
import com.tomessa.miff.domain.analytics.AnalyticsTracker
import com.tomessa.miff.domain.miff.JoinAttemptLimiter
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffObservation
import com.tomessa.miff.domain.miff.MiffPlace
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.miff.MiffSnapshot
import com.tomessa.miff.testsupport.CODE
import com.tomessa.miff.testsupport.FakeDeletionController
import com.tomessa.miff.testsupport.FakeMiffRepository
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.RecordingSink
import com.tomessa.miff.testsupport.miffDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The view models of the Miff flow (ticket Miff criteria C, J, K, G, H, D). No Firestore: a repository double. */
@OptIn(ExperimentalCoroutinesApi::class)
class MiffViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeMiffRepository()
    private val network = FakeNetwork()
    private val sink = RecordingSink()
    private val tracker = AnalyticsTracker(sink) { true }
    private val entered = mutableListOf<AnalyticsEvent.OnboardingVia>()
    private var directoryChanges = 0
    private var now = 0L
    private var profileMissing = 0
    private val observing = CoroutineScope(dispatcher + SupervisorJob())
    private val deletions = FakeDeletionController()
    private val left = mutableListOf<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        observing.cancel()
        Dispatchers.resetMain()
    }

    private fun create() =
        CreateMiffViewModel(
            repository = repo,
            network = network,
            currentUid = { "uid-1" },
            tracker = tracker,
            onEntered = { entered += it },
            onDirectoryChanged = { directoryChanges++ },
            onProfileMissing = { profileMissing++ },
        )

    private fun join() =
        JoinMiffViewModel(
            repository = repo,
            network = network,
            currentUid = { "uid-1" },
            tracker = tracker,
            onEntered = { entered += it },
            onDirectoryChanged = { directoryChanges++ },
            onProfileMissing = { profileMissing++ },
            limiter = JoinAttemptLimiter(nowMillis = { now }),
        )

    // --- B and C: create ------------------------------------------------------------------------------------------

    @Test
    fun create_theButtonIsGreyBelowTwoCharacters_activeFromTwo() {
        val vm = create()
        assertFalse(vm.uiState.value.canSubmit)
        vm.onNameChanged("a")
        assertFalse(vm.uiState.value.canSubmit)
        vm.onNameChanged("  a ")
        assertFalse("spaces at both ends do not count", vm.uiState.value.canSubmit)
        vm.onNameChanged("ab")
        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun create_atFortyCharacters_theLimitIsSaid_andNothingMoreIsKept() {
        val vm = create()
        vm.onNameChanged("a".repeat(45))
        assertEquals(40, vm.uiState.value.name.length)
        assertTrue(vm.uiState.value.limitReached)
    }

    @Test
    fun create_success_showsReadyOnlyAfterTheServer_countsTheEvents_andNothingElseIsSent() =
        runTest(dispatcher) {
            val vm = create()
            vm.onNameChanged("Chez nous")
            var stateWhenWriting: CreateStep? = null
            repo.onCreate = { stateWhenWriting = vm.uiState.value.step }
            vm.onSubmit()
            assertTrue("the button is locked at once", vm.uiState.value.saving)
            advanceUntilIdle()
            assertEquals("still on the name step while the server has not answered", CreateStep.NAME, stateWhenWriting)
            assertEquals(CreateStep.READY, vm.uiState.value.step)
            assertEquals(
                CODE,
                vm.uiState.value.created
                    ?.joinCode,
            )
            assertEquals(listOf(AnalyticsEvent.OnboardingVia.CREATED), entered)
            assertEquals(1, directoryChanges)
            assertEquals(listOf("miff_created"), sink.names)
            assertEquals(mapOf<String, Any>("is_first_miff" to true), sink.events.single().second)
            // No name, no code, no id in the event (criterion C9).
            assertFalse(
                sink.events
                    .single()
                    .second.values
                    .any { it == CODE || it == "Chez nous" },
            )
        }

    @Test
    fun create_aDoubleTap_makesOneMiff() =
        runTest(dispatcher) {
            val vm = create()
            vm.onNameChanged("Chez nous")
            vm.onSubmit()
            vm.onSubmit()
            advanceUntilIdle()
            assertEquals(1, repo.created.size)
        }

    @Test
    fun create_offline_saysSo_atOnce_andTriesNothing() =
        runTest(dispatcher) {
            network.online = false
            val vm = create()
            vm.onNameChanged("Chez nous")
            vm.onSubmit()
            advanceUntilIdle()
            assertEquals(ActionMessage.OFFLINE, vm.uiState.value.message)
            assertTrue("nothing was sent, nothing is waiting", repo.calls.isEmpty())
            assertFalse(vm.uiState.value.saving)
            assertEquals(CreateStep.NAME, vm.uiState.value.step)
        }

    @Test
    fun create_aFailure_isCalm_keepsTheName_andLetsTheButtonBeUsedAgain() =
        runTest(dispatcher) {
            repo.createResult = CreateResult.Failed(MiffFailure.REFUSED)
            val vm = create()
            vm.onNameChanged("Chez nous")
            vm.onSubmit()
            advanceUntilIdle()
            assertEquals(ActionMessage.GENERIC, vm.uiState.value.message)
            assertEquals("Chez nous", vm.uiState.value.name)
            assertTrue(vm.uiState.value.canSubmit)
            assertTrue("no event, no onboarding end on a failure", sink.events.isEmpty() && entered.isEmpty())
        }

    @Test
    fun create_aSecondMiff_isNotTheFirstOne() =
        runTest(dispatcher) {
            repo.createPreparation = CreatePreparation.Ready(premium = true, firstMiff = false)
            repo.createResult = CreateResult.Created(MIFF_ID, CODE, "Chez nous", firstMiff = false)
            val vm = create()
            vm.onNameChanged("Chez nous")
            vm.onSubmit()
            advanceUntilIdle()
            assertEquals(mapOf<String, Any>("is_first_miff" to false), sink.events.single().second)
        }

    @Test
    fun create_needingPremium_saysNothingAboutPremium_justTheHonestOneMiffSentence() =
        runTest(dispatcher) {
            repo.createPreparation = CreatePreparation.NeedsPremium
            val vm = create()
            vm.onNameChanged("Chez nous")
            vm.onSubmit()
            advanceUntilIdle()
            assertEquals(ActionMessage.ONE_MIFF, vm.uiState.value.message)
            assertFalse("create" in repo.calls)
        }

    // --- D and E: join --------------------------------------------------------------------------------------------

    @Test
    fun join_theButtonNeeds12ValidCharacters_andALookAlikeIsAnswered_locally() =
        runTest(dispatcher) {
            val vm = join()
            vm.onCodeChanged("xtzr sp36 798")
            assertFalse(vm.uiState.value.canContinue)
            vm.onCodeChanged("xtzr sp36 7980")
            assertTrue("a zero is not in the alphabet", vm.uiState.value.foreignCharacter)
            assertFalse(vm.uiState.value.canContinue)
            vm.onContinue()
            advanceUntilIdle()
            assertTrue("no network read for a code that cannot exist (criterion J2)", repo.calls.isEmpty())
            vm.onCodeChanged("xtzr sp36 7988")
            assertEquals("XTZRSP367988", vm.uiState.value.code)
            assertTrue(vm.uiState.value.canContinue)
        }

    @Test
    fun join_aGoodCode_showsTheConsent_andWritesNothing() =
        runTest(dispatcher) {
            val vm = join()
            vm.onCodeChanged(CODE)
            vm.onContinue()
            advanceUntilIdle()
            assertEquals(JoinStep.CONFIRM, vm.uiState.value.step)
            assertEquals("Chez nous", vm.uiState.value.miffName)
            assertEquals(listOf("prepareJoin"), repo.calls)
            assertTrue(entered.isEmpty() && sink.events.isEmpty())
        }

    @Test
    fun join_cancelOnTheConsent_writesNothing_andGoesBackToTheCode() =
        runTest(dispatcher) {
            val vm = join()
            vm.onCodeChanged(CODE)
            vm.onContinue()
            advanceUntilIdle()
            vm.onCancelConfirm()
            assertEquals(JoinStep.CODE, vm.uiState.value.step)
            assertFalse("join" in repo.calls)
        }

    @Test
    fun join_onlyTheButtonOfTheConsentWrites_once_andTheEventsFollowTheServer() =
        runTest(dispatcher) {
            val vm = join()
            vm.onCodeChanged(CODE)
            vm.onContinue()
            advanceUntilIdle()
            vm.onConfirm()
            vm.onConfirm()
            advanceUntilIdle()
            assertEquals(1, repo.calls.count { it == "join" })
            assertEquals(MIFF_ID, vm.uiState.value.joinedMiffId)
            assertEquals(listOf(AnalyticsEvent.OnboardingVia.JOINED), entered)
            assertEquals(listOf("miff_joined"), sink.names)
            assertEquals(1, directoryChanges)
        }

    @Test
    fun join_wrongRenewedOrDeletedCode_showTheSameMessage() =
        runTest(dispatcher) {
            val vm = join()
            vm.onCodeChanged(CODE)
            repo.joinPreparation = JoinPreparation.NotFound
            vm.onContinue()
            advanceUntilIdle()
            val fromAWrongCode = vm.uiState.value.problem
            // The code was fine when read but renewed before the confirmation: back to the code, same message.
            repo.joinPreparation = JoinPreparation.Ready(MIFF_ID, "Chez nous", CODE, premium = false, firstMiff = true)
            repo.joinResult = JoinResult.NotFound
            vm.onCodeChanged(CODE)
            vm.onContinue()
            advanceUntilIdle()
            vm.onConfirm()
            advanceUntilIdle()
            assertEquals(JoinProblem.NOT_FOUND, fromAWrongCode)
            assertEquals(JoinProblem.NOT_FOUND, vm.uiState.value.problem)
            assertEquals(JoinStep.CODE, vm.uiState.value.step)
        }

    @Test
    fun join_afterFiveWrongCodes_aPauseOfOneMinute_thenItIsOver() =
        runTest(dispatcher) {
            repo.joinPreparation = JoinPreparation.NotFound
            val vm = join()
            repeat(5) {
                vm.onCodeChanged(CODE)
                vm.onContinue()
                runCurrent()
            }
            assertEquals(JoinProblem.PAUSED, vm.uiState.value.problem)
            assertFalse(vm.uiState.value.canContinue)
            assertEquals(5, repo.calls.count { it == "prepareJoin" })
            // During the pause nothing is read.
            vm.onContinue()
            runCurrent()
            assertEquals(5, repo.calls.count { it == "prepareJoin" })
            now = 61_000
            advanceTimeBy(61_000)
            advanceUntilIdle()
            assertNull(vm.uiState.value.problem)
            assertTrue(vm.uiState.value.canContinue)
        }

    @Test
    fun join_alreadyAMember_offersToOpenTheMiff_andWritesNothing() =
        runTest(dispatcher) {
            repo.joinPreparation = JoinPreparation.AlreadyMember(MIFF_ID, "Chez nous")
            val vm = join()
            vm.onCodeChanged(CODE)
            vm.onContinue()
            advanceUntilIdle()
            assertEquals(
                MIFF_ID,
                vm.uiState.value.alreadyMember
                    ?.miffId,
            )
            assertEquals(JoinStep.CODE, vm.uiState.value.step)
            assertFalse("join" in repo.calls)
        }

    @Test
    fun join_offline_isAnswered_atOnce_onTheCodeAndOnTheConsent() =
        runTest(dispatcher) {
            val vm = join()
            vm.onCodeChanged(CODE)
            network.online = false
            vm.onContinue()
            advanceUntilIdle()
            assertEquals(JoinProblem.OFFLINE, vm.uiState.value.problem)
            assertTrue(repo.calls.isEmpty())
            network.online = true
            vm.onContinue()
            advanceUntilIdle()
            network.online = false
            vm.onConfirm()
            advanceUntilIdle()
            assertEquals(ActionMessage.OFFLINE, vm.uiState.value.confirmMessage)
            assertFalse("join" in repo.calls)
        }

    @Test
    fun join_aNetworkProblem_doesNotCountAsAWrongCode() =
        runTest(dispatcher) {
            repo.joinPreparation = JoinPreparation.Failed(MiffFailure.OFFLINE)
            val vm = join()
            repeat(6) {
                vm.onCodeChanged(CODE)
                vm.onContinue()
                advanceUntilIdle()
            }
            assertEquals(JoinProblem.OFFLINE, vm.uiState.value.problem)
            assertEquals(6, repo.calls.count { it == "prepareJoin" })
        }

    // --- F and G: the screen of a Miff ----------------------------------------------------------------------------

    private fun member(
        uid: String,
        role: MiffRole = MiffRole.MEMBER,
        sharing: Boolean = true,
    ) = MiffMember(uid, "Prénom-$uid", "Nom", null, role, sharing)

    private fun data(
        me: String = "uid-owner",
        owner: String = "uid-owner",
        members: List<MiffMember> = listOf(member("uid-owner", MiffRole.OWNER), member("uid-2")),
    ) = MiffObservation
        .Data(
            MiffSnapshot(miffDetails(owner = owner), members, listOf(MiffPlace("p1", "Maison")), fromCache = false),
        ).also { check(me.isNotEmpty()) }

    private fun screen(
        uid: String = "uid-owner",
        lost: MutableList<String> = mutableListOf(),
        confirmLoss: Boolean = true,
    ) = MiffScreenViewModel(
        miffId = MIFF_ID,
        repository = repo,
        network = network,
        currentUid = { uid },
        onAccessLost = { id, _ ->
            lost += id
            confirmLoss
        },
        onNameChanged = { directoryChanges++ },
        deletions = deletions,
        onLeft = { left += it },
    ).also { vm -> observing.launch { vm.observe() } }

    @Test
    fun screen_theCreatorCanRename_aMemberCannot() =
        runTest(dispatcher) {
            val owner = screen("uid-owner")
            val member = screen("uid-2")
            advanceUntilIdle()
            repo.observer.flowOf(MIFF_ID).emit(data())
            advanceUntilIdle()
            assertTrue(owner.uiState.value.isOwner)
            assertFalse(member.uiState.value.isOwner)
            member.onNameInputChanged("Autre nom")
            assertEquals("a member's typing is ignored", "Chez nous", member.uiState.value.nameInput)
            member.onSaveName()
            advanceUntilIdle()
            assertTrue("a member never renames", repo.renamed.isEmpty())
        }

    @Test
    fun screen_saveIsActiveOnlyIfTheNameChangedAndIsValid_andRenamesInOneCall() =
        runTest(dispatcher) {
            val vm = screen()
            advanceUntilIdle()
            repo.observer.flowOf(MIFF_ID).emit(data())
            advanceUntilIdle()
            assertFalse("nothing changed", vm.uiState.value.canSaveName)
            vm.onNameInputChanged("a")
            assertFalse("too short", vm.uiState.value.canSaveName)
            vm.onNameInputChanged("Chez nous  ")
            assertFalse("the same name once trimmed", vm.uiState.value.canSaveName)
            vm.onNameInputChanged("Nouveau nom")
            assertTrue(vm.uiState.value.canSaveName)
            vm.onSaveName()
            vm.onSaveName()
            advanceUntilIdle()
            assertEquals(listOf(Triple(MIFF_ID, CODE, "Nouveau nom")), repo.renamed)
            assertTrue(vm.uiState.value.nameSaved)
            assertEquals(1, directoryChanges)
        }

    @Test
    fun screen_theLiveCode_followsARenewal_andARemovalOfAMember() =
        runTest(dispatcher) {
            val vm = screen()
            advanceUntilIdle()
            val flow = repo.observer.flowOf(MIFF_ID)
            flow.emit(data())
            advanceUntilIdle()
            assertEquals(2, vm.uiState.value.members.size)
            val renewed =
                MiffObservation.Data(
                    MiffSnapshot(
                        miffDetails(code = "ABCDEFGHJKMN"),
                        listOf(member("uid-owner", MiffRole.OWNER)),
                        emptyList(),
                        false,
                    ),
                )
            flow.emit(renewed)
            advanceUntilIdle()
            assertEquals("ABCDEFGHJKMN", vm.uiState.value.joinCode)
            assertEquals(1, vm.uiState.value.members.size)
        }

    @Test
    fun screen_theSoftCeiling_replacesShareByACalmSentenceAtFifteen() =
        runTest(dispatcher) {
            val vm = screen()
            advanceUntilIdle()
            val fifteen = listOf(member("uid-owner", MiffRole.OWNER)) + (2..15).map { member("uid-$it") }
            repo.observer.flowOf(MIFF_ID).emit(data(members = fifteen))
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isFull)
            repo.observer.flowOf(MIFF_ID).emit(data(members = fifteen.drop(1)))
            advanceUntilIdle()
            assertFalse(vm.uiState.value.isFull)
        }

    @Test
    fun screen_accessLost_isNotANetworkError_andNothingStaysVisible() =
        runTest(dispatcher) {
            val lost = mutableListOf<String>()
            val vm = screen(lost = lost)
            advanceUntilIdle()
            val flow = repo.observer.flowOf(MIFF_ID)
            flow.emit(data())
            advanceUntilIdle()
            // A failure that is not a loss of access: nothing is cleaned, the Miff stays.
            flow.emit(MiffObservation.Failed)
            advanceUntilIdle()
            assertEquals(MiffScreenStatus.READY, vm.uiState.value.status)
            assertTrue(lost.isEmpty())
            flow.emit(MiffObservation.AccessLost)
            advanceUntilIdle()
            assertEquals(MiffScreenStatus.ACCESS_LOST, vm.uiState.value.status)
            assertTrue(
                "no member, no code, no name left on the screen (criterion D7)",
                vm.uiState.value.members
                    .isEmpty(),
            )
            assertEquals("", vm.uiState.value.joinCode)
            assertEquals("", vm.uiState.value.name)
            flow.emit(MiffObservation.AccessLost)
            advanceUntilIdle()
            assertEquals("the directory is told once: no loop", listOf(MIFF_ID), lost)
        }

    @Test
    fun screen_stopSharing_asksFirst_thenWritesOnce_andTheSwitchOnlyMovesWithTheServer() =
        runTest(dispatcher) {
            val vm = screen()
            advanceUntilIdle()
            repo.observer.flowOf(MIFF_ID).emit(data())
            advanceUntilIdle()
            assertEquals(true, vm.uiState.value.sharing)
            vm.onSharingRequested()
            assertEquals(SharingDialog.STOP, vm.uiState.value.sharingDialog)
            assertTrue("asking writes nothing", repo.sharingChanges.isEmpty())
            vm.onSharingDialogDismissed()
            assertNull(vm.uiState.value.sharingDialog)
            assertTrue(repo.sharingChanges.isEmpty())
            vm.onSharingRequested()
            vm.onSharingConfirmed()
            vm.onSharingConfirmed()
            advanceUntilIdle()
            assertEquals(listOf(false), repo.sharingChanges)
            // The state shown is the one of the card, brought by the live listener (not guessed by the app).
            assertEquals(true, vm.uiState.value.sharing)
            repo.observer
                .flowOf(
                    MIFF_ID,
                ).emit(data(members = listOf(member("uid-owner", MiffRole.OWNER, sharing = false))))
            advanceUntilIdle()
            assertEquals(false, vm.uiState.value.sharing)
        }

    @Test
    fun screen_resumeSharing_usesTheSameQuestion_andWritesTheResume() =
        runTest(dispatcher) {
            val vm = screen()
            advanceUntilIdle()
            repo.observer
                .flowOf(
                    MIFF_ID,
                ).emit(data(members = listOf(member("uid-owner", MiffRole.OWNER, sharing = false))))
            advanceUntilIdle()
            vm.onSharingRequested()
            assertEquals(SharingDialog.RESUME, vm.uiState.value.sharingDialog)
            vm.onSharingConfirmed()
            advanceUntilIdle()
            assertEquals(listOf(true), repo.sharingChanges)
        }

    @Test
    fun screen_sharingOffline_isRefusedCalmly_nothingIsQueued() =
        runTest(dispatcher) {
            val vm = screen()
            advanceUntilIdle()
            repo.observer.flowOf(MIFF_ID).emit(data())
            advanceUntilIdle()
            vm.onSharingRequested()
            network.online = false
            vm.onSharingConfirmed()
            advanceUntilIdle()
            assertEquals(ActionMessage.OFFLINE, vm.uiState.value.message)
            assertTrue(repo.sharingChanges.isEmpty())
        }

    @Test
    fun screen_sharingFailure_keepsTheDialog_withACalmMessage() =
        runTest(dispatcher) {
            repo.sharingResult = MiffWriteResult.Failed(MiffFailure.UNKNOWN)
            val vm = screen()
            advanceUntilIdle()
            repo.observer.flowOf(MIFF_ID).emit(data())
            advanceUntilIdle()
            vm.onSharingRequested()
            vm.onSharingConfirmed()
            advanceUntilIdle()
            assertEquals(ActionMessage.GENERIC, vm.uiState.value.message)
            assertEquals(SharingDialog.STOP, vm.uiState.value.sharingDialog)
            assertFalse(vm.uiState.value.sharingBusy)
        }

    @Test
    fun screen_offline_greysTheButtonsThatWrite_butKeepsReading() =
        runTest(dispatcher) {
            val vm = screen()
            advanceUntilIdle()
            repo.observer.flowOf(MIFF_ID).emit(data())
            advanceUntilIdle()
            network.online = false
            advanceUntilIdle()
            // Reading goes on from the cache; what writes is grey (criteria G8 and H5).
            assertTrue(vm.uiState.value.offline)
            assertEquals(2, vm.uiState.value.members.size)
            assertFalse(vm.uiState.value.canWrite)
            vm.onNameInputChanged("Nouveau nom")
            assertFalse(vm.uiState.value.canSaveName)
            vm.onSharingRequested()
            assertNull("no question is asked when it cannot be done", vm.uiState.value.sharingDialog)
            network.online = true
            advanceUntilIdle()
            assertFalse(vm.uiState.value.offline)
        }

    @Test
    fun screen_aRefusalTheServerDoesNotConfirm_isNotARemoval_noMessage_andRetryWorks() =
        runTest(dispatcher) {
            val lost = mutableListOf<String>()
            val vm = screen(lost = lost, confirmLoss = false)
            advanceUntilIdle()
            val flow = repo.observer.flowOf(MIFF_ID)
            flow.emit(data())
            advanceUntilIdle()
            flow.emit(MiffObservation.AccessLost)
            advanceUntilIdle()
            assertEquals("the directory was asked, and said no", listOf(MIFF_ID), lost)
            assertEquals(MiffScreenStatus.UNAVAILABLE, vm.uiState.value.status)
            assertEquals("nothing of the Miff is shown while in doubt", "", vm.uiState.value.joinCode)
            vm.retry()
            assertEquals(MiffScreenStatus.LOADING, vm.uiState.value.status)
            advanceUntilIdle()
            flow.emit(data())
            advanceUntilIdle()
            assertEquals(MiffScreenStatus.READY, vm.uiState.value.status)
        }

    @Test
    fun screen_stopsListening_whenTheScreenIsNotOnView() =
        runTest(dispatcher) {
            val vm = screen()
            advanceUntilIdle()
            assertTrue(
                "listening",
                repo.observer
                    .flowOf(MIFF_ID)
                    .subscriptionCount.value > 0,
            )
            observing.coroutineContext[kotlinx.coroutines.Job]!!.children.forEach { it.cancel() }
            advanceUntilIdle()
            assertEquals(
                "no listener left (the lifecycle cancels observe())",
                0,
                repo.observer
                    .flowOf(MIFF_ID)
                    .subscriptionCount.value,
            )
            assertEquals(MiffScreenStatus.LOADING, vm.uiState.value.status)
        }

    @Test
    fun create_withoutAProfile_saysItHonestly_andAsksTheRoutingAgain() =
        runTest(dispatcher) {
            repo.createResult = CreateResult.Failed(MiffFailure.NO_PROFILE)
            val vm = create()
            vm.onNameChanged("Chez nous")
            vm.onSubmit()
            advanceUntilIdle()
            assertEquals(ActionMessage.NO_PROFILE, vm.uiState.value.message)
            assertEquals(1, profileMissing)
        }

    @Test
    fun join_aFreeAccountWithAMiff_isToldHonestly_andItDoesNotCountAsAWrongCode() =
        runTest(dispatcher) {
            repo.joinPreparation = JoinPreparation.NeedsPremium
            val vm = join()
            repeat(6) {
                vm.onCodeChanged(CODE)
                vm.onContinue()
                advanceUntilIdle()
            }
            assertEquals(JoinProblem.ONE_MIFF, vm.uiState.value.problem)
            assertEquals(6, repo.calls.count { it == "prepareJoin" })
        }

    @Test
    fun join_confirmWithoutAProfile_saysItHonestly_andAsksTheRoutingAgain() =
        runTest(dispatcher) {
            val vm = join()
            vm.onCodeChanged(CODE)
            vm.onContinue()
            advanceUntilIdle()
            repo.joinResult = JoinResult.Failed(MiffFailure.NO_PROFILE)
            vm.onConfirm()
            advanceUntilIdle()
            assertEquals(ActionMessage.NO_PROFILE, vm.uiState.value.confirmMessage)
            assertEquals(1, profileMissing)
        }
}
