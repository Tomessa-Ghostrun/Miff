package com.tomessa.miff.ui.profile

import com.tomessa.miff.data.notification.NotificationCenter
import com.tomessa.miff.data.notification.NotificationCenterStore
import com.tomessa.miff.data.place.RequestAcceptResult
import com.tomessa.miff.data.place.RequestDeclineResult
import com.tomessa.miff.data.place.RequesterDirectory
import com.tomessa.miff.data.place.RequestsObservation
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.notification.NotificationItem
import com.tomessa.miff.domain.notification.NotificationType
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.domain.place.PlaceRequest
import com.tomessa.miff.domain.place.RequesterCard
import com.tomessa.miff.domain.place.RequesterState
import com.tomessa.miff.domain.place.RequestsView
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.FakePlaceRequestRepository
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.OTHER_MIFF_ID
import com.tomessa.miff.testsupport.testRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The creator who RECEIVES requests (ticket LIEU-REQ-C, criteria C1 to C11, L2): ONE listener on ONE Miff, ONE line per request in the centre, the dot of the
 * Profile tab until the list is seen, who asked, accepting and declining, the offer at the limit, the hidden and deleted requests. Doubles, no Firestore.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRequestsCreatorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = FakePlaceRequestRepository()
    private val network = FakeNetwork()
    private val directory = RequesterDirectory()
    private val tray = mutableListOf<String>()
    private var now = 1_800_000_000_000L
    private val center = NotificationCenter(MemoryCentre(), nowMillis = { now }, onRead = { tray += it })

    private class MemoryCentre : NotificationCenterStore {
        var items = emptyList<NotificationItem>()

        override fun load() = items

        override fun save(items: List<NotificationItem>) {
            this.items = items
        }

        override fun clear() {
            items = emptyList()
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ProfileRequestsViewModel(repo, network, { "u1" }, center, directory, { now })

    private fun listener(
        vm: ProfileRequestsViewModel,
        miffId: String = MIFF_ID,
    ): Job {
        val job = CoroutineScope(dispatcher).launch { vm.watch(miffId) }
        dispatcher.scheduler.runCurrent()
        return job
    }

    private fun ProfileRequestsViewModel.asCreatorOf(miffId: String = MIFF_ID) {
        onActiveMiff(miffId, creator = true)
    }

    private fun push(vararg requests: PlaceRequest) {
        repo.observations.tryEmit(RequestsObservation.Data(requests.toList(), fromCache = false))
        dispatcher.scheduler.runCurrent()
    }

    private fun ProfileRequestsViewModel.ids(miffId: String = MIFF_ID): List<String> =
        ((state.value.views[miffId]) as? RequestsView.Ready)?.requests?.map { it.id }.orEmpty()

    private fun lea() = RequesterState.Known(RequesterCard.of("u2", "Léa", "Martin", null))

    // --- Who is listened to -------------------------------------------------------------------------------------------------------

    @Test
    fun theCreator_listensToTheActiveMiff_atOnce_neverAMemberOfIt() {
        val vm = viewModel()
        vm.onActiveMiff(MIFF_ID, creator = true)
        assertEquals(MIFF_ID, vm.tracked.value)
        val member = viewModel()
        member.onActiveMiff(MIFF_ID, creator = false)
        assertNull(member.tracked.value)
    }

    @Test
    fun theCardShown_decidesWhichMiffIsListenedTo_whenTheyCreatedIt() {
        val vm = viewModel()
        vm.onActiveMiff(MIFF_ID, creator = true)
        vm.onCardShown(OTHER_MIFF_ID, creator = true)
        assertEquals(OTHER_MIFF_ID, vm.tracked.value)
        // A card of a Miff they did not create: back to the active one (only ONE listener, ever).
        vm.onCardShown("someone-elses", creator = false)
        assertEquals(MIFF_ID, vm.tracked.value)
    }

    @Test
    fun aCreator_readsNothingWhenACardAppears_theListenerFeedsTheList() {
        val vm = viewModel()
        vm.asCreatorOf()
        vm.onCardShown(MIFF_ID, creator = true)
        dispatcher.scheduler.runCurrent()
        assertEquals(0, repo.readMineCalls)
        assertEquals(0, repo.observed)
    }

    // --- Receiving (C1, C2) ----------------------------------------------------------------------------------------------------------

    @Test
    fun aNewRequest_isInTheList_oldestFirst_hasOneLineInTheCentre_andLightsTheDot() {
        val vm = viewModel()
        vm.asCreatorOf()
        val job = listener(vm)
        push(
            testRequest(uid = "u3", slot = 1, createdAtMillis = now - 1_000),
            testRequest(uid = "u2", slot = 1, createdAtMillis = now - 5_000),
        )
        assertEquals(listOf("u2_1", "u3_1"), vm.ids())
        assertEquals(2, center.items.value.count { it.type == NotificationType.N8 })
        assertTrue(vm.state.value.profileDot)
        // The same request again (a later answer of the listener): never a second line.
        push(
            testRequest(uid = "u3", slot = 1, createdAtMillis = now - 1_000),
            testRequest(uid = "u2", slot = 1, createdAtMillis = now - 5_000),
        )
        assertEquals(2, center.items.value.count { it.type == NotificationType.N8 })
        job.cancel()
    }

    @Test
    fun theLineInTheCentre_carriesNoName_neverThePlaceOrThePerson() {
        val vm = viewModel()
        vm.asCreatorOf()
        val job = listener(vm)
        repo.requesterStates["u2"] = lea()
        push(testRequest(uid = "u2", slot = 1, name = "Chez Mamie", createdAtMillis = now))
        val item = center.items.value.single { it.type == NotificationType.N8 }
        assertEquals("n8|$MIFF_ID|u2_1", item.id)
        assertEquals(MIFF_ID, item.miffId)
        // The first name is kept in memory (for the words of the line), never in the line itself.
        assertEquals("Léa", directory.firstNames.value[RequesterDirectory.key(MIFF_ID, "u2")])
        assertFalse(item.id.contains("Léa") || item.id.contains("Mamie"))
        job.cancel()
    }

    @Test
    fun whoAsked_isReadOncePerPerson_andShownWithTheirFirstName() {
        val vm = viewModel()
        vm.asCreatorOf()
        val job = listener(vm)
        repo.requesterStates["u2"] = lea()
        push(
            testRequest(uid = "u2", slot = 1, createdAtMillis = now),
            testRequest(uid = "u2", slot = 2, createdAtMillis = now),
        )
        push(
            testRequest(uid = "u2", slot = 1, createdAtMillis = now),
            testRequest(uid = "u2", slot = 2, createdAtMillis = now),
        )
        assertEquals(listOf("u2"), repo.requesterReads)
        val request = testRequest(uid = "u2", slot = 1, createdAtMillis = now)
        assertEquals("Léa", ((vm.state.value.requester(request)) as RequesterState.Known).card.firstName)
        job.cancel()
    }

    @Test
    fun aRequesterThatCannotBeRead_isShownWithoutAName_neverHidden_andIsTriedAgainLater() {
        val vm = viewModel()
        vm.asCreatorOf()
        val job = listener(vm)
        push(testRequest(uid = "u2", slot = 1, createdAtMillis = now))
        assertEquals(RequesterState.Unknown, vm.state.value.requester(testRequest(uid = "u2", slot = 1)))
        assertEquals(listOf("u2_1"), vm.ids())
        // Not again at once (never a loop) ...
        push(testRequest(uid = "u2", slot = 1, createdAtMillis = now))
        assertEquals(1, repo.requesterReads.size)
        // ... but after a minute, with the next answer.
        now += 61_000
        repo.requesterStates["u2"] = lea()
        push(testRequest(uid = "u2", slot = 1, createdAtMillis = now))
        assertEquals(2, repo.requesterReads.size)
        assertTrue(vm.state.value.requester(testRequest(uid = "u2", slot = 1)) is RequesterState.Known)
        job.cancel()
    }

    @Test
    fun theDot_goesWhenTheListIsSeen_andTheLinesOfThatMiffAreRead_andComesBackForANewRequest() {
        val vm = viewModel()
        vm.asCreatorOf()
        val job = listener(vm)
        push(testRequest(uid = "u2", slot = 1, createdAtMillis = now))
        assertTrue(vm.state.value.profileDot)
        vm.onListSeen(MIFF_ID)
        assertFalse(vm.state.value.profileDot)
        assertTrue(
            center.items.value
                .single { it.type == NotificationType.N8 }
                .readAtMillis != null,
        )
        assertEquals(listOf("n8|$MIFF_ID|u2_1"), tray)
        push(
            testRequest(uid = "u2", slot = 1, createdAtMillis = now),
            testRequest(uid = "u3", slot = 1, createdAtMillis = now),
        )
        assertTrue(vm.state.value.profileDot)
        job.cancel()
    }

    @Test
    fun noRequest_noDot_noLine() {
        val vm = viewModel()
        vm.asCreatorOf()
        val job = listener(vm)
        push()
        assertFalse(vm.state.value.profileDot)
        assertTrue(center.items.value.isEmpty())
        job.cancel()
    }

    @Test
    fun whenTheListenerStops_theListIsKept_butItAgesLikeAReading() {
        val vm = viewModel()
        vm.asCreatorOf()
        val job = listener(vm)
        push(testRequest(uid = "u2", slot = 1, createdAtMillis = now))
        job.cancel()
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("u2_1"), vm.ids())
    }

    // --- Hidden and deleted (C10, L2) --------------------------------------------------------------------------------------------------

    @Test
    fun aRequestOfMoreThanThirtyDays_isHidden_noLine_andDeletedByTheCreator() {
        val vm = viewModel()
        vm.asCreatorOf()
        val job = listener(vm)
        val old = testRequest(uid = "u2", slot = 1, createdAtMillis = now - 31L * 24 * 60 * 60 * 1000)
        val fresh = testRequest(uid = "u3", slot = 1, createdAtMillis = now)
        push(old, fresh)
        assertEquals(listOf("u3_1"), vm.ids())
        assertEquals(1, center.items.value.count { it.type == NotificationType.N8 })
        val purge = repo.purged.single()
        assertEquals(listOf("u2_1"), purge.second)
        assertEquals(1, purge.third)
        job.cancel()
    }

    @Test
    fun aRequestOfSomebodyWhoLeft_isHidden_andDeleted_withoutBeingAskedTwice() {
        val vm = viewModel()
        vm.asCreatorOf()
        val job = listener(vm)
        repo.requesterStates["u2"] = RequesterState.NotMember
        push(
            testRequest(uid = "u2", slot = 1, createdAtMillis = now),
            testRequest(uid = "u3", slot = 1, createdAtMillis = now),
        )
        assertEquals(listOf("u3_1"), vm.ids())
        assertEquals(listOf(listOf("u2_1")), repo.purged.map { it.second })
        // The next answer of the listener (still holding it for a moment) deletes nothing twice.
        push(testRequest(uid = "u3", slot = 1, createdAtMillis = now))
        assertEquals(1, repo.purged.size)
        job.cancel()
    }

    @Test
    fun theLossOfAccess_forgetsTheMiff() {
        val vm = viewModel()
        vm.asCreatorOf()
        val job = listener(vm)
        push(testRequest(uid = "u2", slot = 1, createdAtMillis = now))
        repo.observations.tryEmit(RequestsObservation.AccessLost)
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.state.value.views[MIFF_ID] is RequestsView.Loading)
        assertFalse(vm.state.value.profileDot)
        job.cancel()
    }

    // --- Accepting (C3, C5 to C8) --------------------------------------------------------------------------------------------------------

    private fun ProfileRequestsViewModel.collect(into: MutableList<RequestEvent>) {
        CoroutineScope(dispatcher).launch { events.collect { into += it } }
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun accept_removesTheRequestAtOnce_andSaysItOnce_withTheNumberOfRequestsKnown() {
        val vm = viewModel()
        vm.asCreatorOf()
        val job = listener(vm)
        val asked = testRequest(uid = "u2", slot = 1, name = "Chez Mamie", createdAtMillis = now)
        push(asked, testRequest(uid = "u3", slot = 1, createdAtMillis = now))
        val events = mutableListOf<RequestEvent>()
        vm.collect(events)
        repo.acceptResult = RequestAcceptResult.Done("p3")
        vm.onAccept(asked)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(2), repo.accepted.map { it.second })
        assertEquals(listOf("u3_1"), vm.ids())
        assertEquals(listOf<RequestEvent>(RequestEvent.Accepted(MIFF_ID, "Chez Mamie")), events)
        job.cancel()
    }

    @Test
    fun accept_atTheLimit_opensTheOffer_andTheRequestStaysPending() {
        val vm = viewModel()
        vm.asCreatorOf()
        val job = listener(vm)
        val asked = testRequest(uid = "u2", slot = 1, createdAtMillis = now)
        push(asked)
        val events = mutableListOf<RequestEvent>()
        vm.collect(events)
        repo.acceptResult = RequestAcceptResult.NeedsPremium(PaywallReason.LIMIT)
        vm.onAccept(asked)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf<RequestEvent>(RequestEvent.NeedsPremium(PaywallReason.LIMIT)), events)
        assertEquals(listOf("u2_1"), vm.ids())
        assertNull(vm.state.value.busyRequestId)
        job.cancel()
    }

    @Test
    fun accept_anUnknownPlan_saysSoCalmly_noOffer() {
        val vm = viewModel()
        vm.asCreatorOf()
        val job = listener(vm)
        val asked = testRequest(uid = "u2", slot = 1, createdAtMillis = now)
        push(asked)
        val events = mutableListOf<RequestEvent>()
        vm.collect(events)
        repo.acceptResult = RequestAcceptResult.SubscriptionUnknown
        vm.onAccept(asked)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf<RequestEvent>(RequestEvent.SubscriptionUnknown), events)
        job.cancel()
    }

    @Test
    fun accept_aRequestHandledElsewhere_isGone_andLeavesTheList() {
        val vm = viewModel()
        vm.asCreatorOf()
        val job = listener(vm)
        val asked = testRequest(uid = "u2", slot = 1, createdAtMillis = now)
        push(asked)
        val events = mutableListOf<RequestEvent>()
        vm.collect(events)
        repo.acceptResult = RequestAcceptResult.Gone
        vm.onAccept(asked)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf<RequestEvent>(RequestEvent.Gone), events)
        assertEquals(emptyList<String>(), vm.ids())
        job.cancel()
    }

    @Test
    fun accept_withNoNetwork_changesNothing_andAFailureKeepsTheRequest() {
        val vm = viewModel()
        vm.asCreatorOf()
        val job = listener(vm)
        val asked = testRequest(uid = "u2", slot = 1, createdAtMillis = now)
        push(asked)
        val events = mutableListOf<RequestEvent>()
        vm.collect(events)
        network.online = false
        vm.onAccept(asked)
        dispatcher.scheduler.runCurrent()
        assertTrue(repo.accepted.isEmpty())
        network.online = true
        repo.acceptResult = RequestAcceptResult.Failed(MiffFailure.UNKNOWN)
        vm.onAccept(asked)
        dispatcher.scheduler.runCurrent()
        assertEquals(
            listOf<RequestEvent>(RequestEvent.Failed(offline = true), RequestEvent.Failed(offline = false)),
            events,
        )
        assertEquals(listOf("u2_1"), vm.ids())
        job.cancel()
    }

    @Test
    fun aSecondTapWhileAnAnswerIsOnItsWay_doesNothing() {
        val vm = viewModel()
        vm.asCreatorOf()
        val job = listener(vm)
        val asked = testRequest(uid = "u2", slot = 1, createdAtMillis = now)
        push(asked, testRequest(uid = "u3", slot = 1, createdAtMillis = now))
        repo.gate = CompletableDeferred()
        vm.onAccept(asked)
        dispatcher.scheduler.runCurrent()
        assertEquals("u2_1", vm.state.value.busyRequestId)
        vm.onDecline(testRequest(uid = "u3", slot = 1, createdAtMillis = now))
        repo.gate!!.complete(Unit)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, repo.accepted.size)
        assertTrue(repo.declined.isEmpty())
        job.cancel()
    }

    // --- Declining (C4) -----------------------------------------------------------------------------------------------------------------------

    @Test
    fun decline_removesTheRequest_saysItOnce_andNothingElse() {
        val vm = viewModel()
        vm.asCreatorOf()
        val job = listener(vm)
        val asked = testRequest(uid = "u2", slot = 1, createdAtMillis = now)
        push(asked)
        val events = mutableListOf<RequestEvent>()
        vm.collect(events)
        vm.onDecline(asked)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(1), repo.declined.map { it.second })
        assertEquals(emptyList<String>(), vm.ids())
        assertEquals(listOf<RequestEvent>(RequestEvent.Declined), events)
        job.cancel()
    }

    @Test
    fun decline_thatFails_keepsTheRequest() {
        val vm = viewModel()
        vm.asCreatorOf()
        val job = listener(vm)
        val asked = testRequest(uid = "u2", slot = 1, createdAtMillis = now)
        push(asked)
        repo.declineResult = RequestDeclineResult.Failed(MiffFailure.OFFLINE)
        vm.onDecline(asked)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("u2_1"), vm.ids())
        job.cancel()
    }

    // --- The detail opened with nothing known ---------------------------------------------------------------------------------------------

    @Test
    fun aDetailOpenedWithNothingKnown_readsTheMiffOnce_forACreator() {
        val vm = viewModel()
        repo.readAllResult =
            com.tomessa.miff.data.place.MineRead.Loaded(
                listOf(testRequest(uid = "u2", slot = 1, createdAtMillis = now)),
            )
        vm.onRequestOpened(MIFF_ID, creator = true)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("u2_1"), vm.ids())
        vm.onRequestOpened(MIFF_ID, creator = true)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("u2"), repo.requesterReads)
    }

    @Test
    fun aPhoneWhoseClockRunsAhead_hidesTheRequests_butDeletesNothingThatTheServerDatedRecently() {
        val vm = viewModel()
        vm.asCreatorOf()
        val job = listener(vm)
        // The server dated this one 'now'; the phone's clock is then 60 days ahead.
        val recent = testRequest(uid = "u2", slot = 1, createdAtMillis = now)
        now += 60L * 24 * 60 * 60 * 1000
        push(recent)
        assertEquals(emptyList<String>(), vm.ids())
        assertTrue(repo.purged.isEmpty())
        // A request 40 days older than the newest one is really old by the server's own dates: it goes.
        val old =
            testRequest(
                uid = "u3",
                slot = 1,
                createdAtMillis =
                    recent.createdAtMillis!! - 40L * 24 * 60 * 60 * 1000,
            )
        push(recent, old)
        assertEquals(listOf(listOf("u3_1")), repo.purged.map { it.second })
        job.cancel()
    }
}
