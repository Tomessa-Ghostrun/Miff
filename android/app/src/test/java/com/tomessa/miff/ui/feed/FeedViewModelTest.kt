package com.tomessa.miff.ui.feed

import com.tomessa.miff.data.feed.FeedRefresher
import com.tomessa.miff.data.feed.PositionReader
import com.tomessa.miff.data.feed.ServerRead
import com.tomessa.miff.data.feed.StatusFeed
import com.tomessa.miff.data.feed.StatusObserver
import com.tomessa.miff.data.miff.LostMiff
import com.tomessa.miff.data.miff.MiffDirectory
import com.tomessa.miff.data.miff.MyMiffsLoad
import com.tomessa.miff.domain.feed.Bubble
import com.tomessa.miff.domain.feed.FeedBanner
import com.tomessa.miff.domain.feed.LocateResult
import com.tomessa.miff.domain.feed.MemberLocationAccess
import com.tomessa.miff.domain.feed.MemberStatus
import com.tomessa.miff.domain.feed.PositionLookup
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffObservation
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.miff.MiffSnapshot
import com.tomessa.miff.domain.miff.MiffSummary
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.StatusLevel
import com.tomessa.miff.testsupport.FakeCachePurger
import com.tomessa.miff.testsupport.FakeMiffRepository
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.MemoryActiveMiffStore
import com.tomessa.miff.testsupport.Timeline
import com.tomessa.miff.testsupport.miffDetails
import com.tomessa.miff.testsupport.testPlace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/** A listener of statuses that the test drives: each attachment takes the next channel; `Ended` ends it, like a refusal of the rules. */
private class FakeStatusObserver : StatusObserver {
    val started = mutableListOf<String>()
    var live = 0
    private val queue = ArrayDeque<Channel<StatusFeed>>()

    fun enqueue(): Channel<StatusFeed> = Channel<StatusFeed>(Channel.UNLIMITED).also { queue.addLast(it) }

    override fun observe(miffId: String): Flow<StatusFeed> =
        flow {
            started += miffId
            live++
            try {
                val channel = queue.removeFirstOrNull() ?: Channel(Channel.UNLIMITED)
                for (item in channel) {
                    emit(item)
                    if (item === StatusFeed.Ended) return@flow
                }
            } finally {
                live--
            }
        }
}

private class FakePositionReader : PositionReader {
    var answer: PositionLookup = PositionLookup.NotFound
    var gate: CompletableDeferred<Unit>? = null
    var failure: Exception? = null
    val reads = mutableListOf<Pair<String, String>>()

    override suspend fun read(
        miffId: String,
        uid: String,
    ): PositionLookup {
        reads += miffId to uid
        gate?.await()
        failure?.let { throw it }
        return answer
    }
}

private class FakeRefresher : FeedRefresher {
    var answer: ServerRead = ServerRead.Fresh(emptyMap())
    var gate: CompletableDeferred<Unit>? = null
    val calls = mutableListOf<Pair<String, Boolean>>()

    override suspend fun refresh(
        miffId: String,
        readStatuses: Boolean,
    ): ServerRead {
        calls += miffId to readStatuses
        gate?.await()
        return answer
    }
}

/** The view model of the Feed: what it listens to, when, what it computes, and the sheet "Localiser" (no screen, no Firestore). */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeMiffRepository()
    private val directory = MiffDirectory(repo, { "me" }, MemoryActiveMiffStore(), FakeCachePurger(Timeline()))
    private val statuses = FakeStatusObserver()
    private val positions = FakePositionReader()
    private val refresher = FakeRefresher()
    private val network = FakeNetwork()
    private val waiting = MutableStateFlow<Set<String>>(emptySet())
    private val base = Instant.parse("2026-11-05T17:00:00Z").toEpochMilli()
    private val minute = 60_000L
    private val hour = 60 * minute

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        FeedViewModel(
            directory = directory,
            repository = repo,
            statuses = statuses,
            positions = positions,
            refresher = refresher,
            network = network,
            currentUid = { "me" },
            waitingAgreement = waiting,
            nowMillis = { base + dispatcher.scheduler.currentTime },
            zone = { ZoneOffset.UTC },
        )

    private fun member(
        uid: String,
        first: String,
        sharing: Boolean = true,
    ) = MiffMember(uid, first, "X", null, if (uid == "me") MiffRole.OWNER else MiffRole.MEMBER, sharing, "+33612345678")

    private fun snapshot(vararg members: MiffMember) =
        MiffObservation.Data(
            MiffSnapshot(
                miffDetails(owner = "me"),
                members.toList(),
                listOf(testPlace("p1", "Chez nous")),
                fromCache = false,
            ),
        )

    private fun status(
        presence: PresenceValue = PresenceValue.IN_TRUSTED,
        presenceAgo: Long = 5 * minute,
    ) = MemberStatus(
        presence = presence,
        placeId = "p1",
        presenceAtMillis = base + dispatcher.scheduler.currentTime - presenceAgo,
        lastUpdateAtMillis = base + dispatcher.scheduler.currentTime - presenceAgo,
        hasPhoto = false,
        batteryPct = 80,
        locationAccess = MemberLocationAccess.BACKGROUND,
        updatedAtMillis = base + dispatcher.scheduler.currentTime,
    )

    private suspend fun ready() {
        repo.myMiffsResult =
            MyMiffsLoad.Loaded(
                listOf(MiffSummary(MIFF_ID, "Chez nous")),
                emptyList(),
                premium = false,
                fromCache = false,
            )
        directory.refresh()
    }

    private fun TestScope.observing(vm: FeedViewModel): Job = launch { vm.observe() }

    private fun FeedViewModel.content() = (uiState.value as FeedUiState.Ready).content

    private val trio get() = arrayOf(member("me", "Camille"), member("t", "Thomas"), member("a", "Amandine"))

    @Test
    fun showsTheSkeletonUntilTheMiffAndTheStatusesAnswered_thenTheContent() =
        runTest(dispatcher) {
            ready()
            val feed = statuses.enqueue()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            assertEquals(FeedUiState.Loading, vm.uiState.value)
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            runCurrent()
            // The Miff is known, the statuses have not answered: still the skeleton, never a colour nobody knows.
            assertEquals(FeedUiState.Loading, vm.uiState.value)
            feed.trySend(StatusFeed.Data(mapOf("me" to status(), "t" to status()), fromCache = false))
            runCurrent()
            val content = vm.content()
            // Me LAST (decision of the PM, 2026-11-07).
            assertEquals(listOf("t", "a", "me"), content.cards.map { it.member.uid })
            assertTrue(content.bubble is Bubble.AllGood)
            job.cancel()
        }

    @Test
    fun theColourIsRecomputedEachMinute_withoutANewReading() =
        runTest(dispatcher) {
            ready()
            val feed = statuses.enqueue()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            feed.trySend(
                StatusFeed.Data(
                    mapOf("me" to status(), "t" to status(PresenceValue.OUT, presenceAgo = 50 * minute)),
                    fromCache = false,
                ),
            )
            runCurrent()
            assertEquals(
                StatusLevel.GREEN,
                vm
                    .content()
                    .cards
                    .first { it.member.uid == "t" }
                    .level,
            )
            advanceTimeBy(11 * minute + 1)
            runCurrent()
            assertEquals(
                StatusLevel.ORANGE,
                vm
                    .content()
                    .cards
                    .first { it.member.uid == "t" }
                    .level,
            )
            // One listener the whole time: the minute did not read anything.
            assertEquals(1, statuses.started.size)
            job.cancel()
        }

    @Test
    fun withoutAnyAnswerOfTheStatuses_theCardsStopWaiting_neutral_afterAMoment() =
        runTest(dispatcher) {
            ready()
            statuses.enqueue()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            runCurrent()
            assertEquals(FeedUiState.Loading, vm.uiState.value)
            // Long on purpose: the skeleton ("Chargement des nouvelles…") first, the sentence "Pas encore de nouvelles" only much later.
            advanceTimeBy(14_000)
            runCurrent()
            assertEquals(FeedUiState.Loading, vm.uiState.value)
            advanceTimeBy(2_000)
            runCurrent()
            val content = vm.content()
            assertEquals(StatusLevel.NEUTRAL, content.miffLevel)
            assertEquals(Bubble.NoNews, content.bubble)
            job.cancel()
        }

    @Test
    fun theStatusesAreNotListenedTo_whenIDoNotShare() =
        runTest(dispatcher) {
            ready()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer
                .flowOf(
                    MIFF_ID,
                ).tryEmit(snapshot(member("me", "Camille", sharing = false), member("t", "Thomas")))
            runCurrent()
            assertTrue(statuses.started.isEmpty())
            assertEquals(Bubble.SharingStopped, vm.content().bubble)
            job.cancel()
        }

    @Test
    fun theStatusesAreNotListenedTo_whileMyAgreementIsWaitedFor_andAreAfterwards() =
        runTest(dispatcher) {
            ready()
            waiting.value = setOf(MIFF_ID)
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            runCurrent()
            assertTrue(statuses.started.isEmpty())
            assertEquals(Bubble.AwaitingAgreement, vm.content().bubble)
            val feed = statuses.enqueue()
            waiting.value = emptySet()
            runCurrent()
            feed.trySend(StatusFeed.Data(mapOf("me" to status(), "t" to status()), fromCache = false))
            runCurrent()
            assertEquals(1, statuses.started.size)
            assertTrue(vm.content().bubble is Bubble.AllGood)
            job.cancel()
        }

    @Test
    fun aRefusalOfTheRules_isTheBannerOfPublishing_thenItListensAgainAfterAPause() =
        runTest(dispatcher) {
            ready()
            val refused = statuses.enqueue()
            val second = statuses.enqueue()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            refused.trySend(StatusFeed.Ended)
            runCurrent()
            assertTrue(vm.content().banners.contains(FeedBanner.Publishing))
            assertEquals(1, statuses.started.size)
            advanceTimeBy(9_000)
            runCurrent()
            assertEquals(1, statuses.started.size)
            advanceTimeBy(1_500)
            runCurrent()
            assertEquals(2, statuses.started.size)
            second.trySend(StatusFeed.Data(mapOf("me" to status(), "t" to status()), fromCache = false))
            runCurrent()
            assertFalse(vm.content().banners.contains(FeedBanner.Publishing))
            job.cancel()
        }

    @Test
    fun theAttemptsAfterARefusal_areFewAndSpaced_neverALoop() =
        runTest(dispatcher) {
            ready()
            repeat(10) { statuses.enqueue().trySend(StatusFeed.Ended) }
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            advanceTimeBy(60 * minute)
            runCurrent()
            // One attachment, then four spaced attempts, then it waits for the screen to come back.
            assertEquals(5, statuses.started.size)
            job.cancel()
        }

    @Test
    fun leavingTheScreen_detachesEveryListener_andClosesTheSheet() =
        runTest(dispatcher) {
            ready()
            val feed = statuses.enqueue()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            feed.trySend(StatusFeed.Data(mapOf("me" to status(), "t" to status()), fromCache = false))
            runCurrent()
            positions.answer = PositionLookup.Found(48.0, 2.0, base)
            vm.onLocate("t")
            runCurrent()
            assertTrue(vm.locate.value is LocateUiState.Shown)
            assertEquals(1, statuses.live)
            job.cancel()
            runCurrent()
            assertEquals(0, statuses.live)
            // No position is kept when the Feed leaves the screen.
            assertEquals(LocateUiState.Idle, vm.locate.value)
        }

    @Test
    fun offline_theBannerAppears_andComesBack() =
        runTest(dispatcher) {
            ready()
            val feed = statuses.enqueue()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            feed.trySend(StatusFeed.Data(mapOf("me" to status(), "t" to status()), fromCache = true))
            runCurrent()
            // Online, a copy of the cache is not the present: the skeleton, until the server confirms (or a few seconds pass).
            assertEquals(FeedUiState.Loading, vm.uiState.value)
            network.online = false
            runCurrent()
            assertTrue(vm.content().banners.any { it is FeedBanner.Offline })
            network.online = true
            runCurrent()
            feed.trySend(StatusFeed.Data(mapOf("me" to status(), "t" to status()), fromCache = false))
            runCurrent()
            assertTrue(vm.content().banners.isEmpty())
            job.cancel()
        }

    @Test
    fun aCachedAlert_isNotShownWhileOnline_untilTheServerConfirmsIt() =
        runTest(dispatcher) {
            ready()
            val feed = statuses.enqueue()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            // The copy kept by the phone says "orange" (somebody who has since come home): no false alert is drawn from it.
            val cached = mapOf("me" to status(), "t" to status(PresenceValue.OUT, presenceAgo = 80 * minute))
            feed.trySend(StatusFeed.Data(cached, fromCache = true))
            runCurrent()
            assertEquals(FeedUiState.Loading, vm.uiState.value)
            // The server confirms: Thomas is back home, never an alert.
            feed.trySend(StatusFeed.Data(mapOf("me" to status(), "t" to status()), fromCache = false))
            runCurrent()
            val content = vm.content()
            assertTrue(content.bubble is Bubble.AllGood)
            assertTrue(content.banners.isEmpty())
            job.cancel()
        }

    @Test
    fun aCachedAnswerNeverConfirmed_isShownAfterAMoment_withItsDate_neverAsThePresent() =
        runTest(dispatcher) {
            ready()
            val feed = statuses.enqueue()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            feed.trySend(StatusFeed.Data(mapOf("me" to status(), "t" to status()), fromCache = true))
            runCurrent()
            assertEquals(FeedUiState.Loading, vm.uiState.value)
            advanceTimeBy(5_500)
            runCurrent()
            // Shown, but told as "Dernières informations de …": never a green without a date.
            val banner =
                vm
                    .content()
                    .banners
                    .filterIsInstance<FeedBanner.Offline>()
                    .single()
            assertNotNull(banner.latest)
            job.cancel()
        }

    @Test
    fun offlineAtOnce_aCachedAnswerIsShownWithItsDate_withoutWaiting() =
        runTest(dispatcher) {
            ready()
            network.online = false
            val feed = statuses.enqueue()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            feed.trySend(StatusFeed.Data(mapOf("me" to status(), "t" to status()), fromCache = true))
            runCurrent()
            assertTrue(vm.content().banners.any { it is FeedBanner.Offline })
            job.cancel()
        }

    @Test
    fun cachedAnswersThenARefusal_repeated_areNotALoopOfAttempts() =
        runTest(dispatcher) {
            ready()
            // Each attachment gives a copy of the cache then ends on a refusal: the attempts are NOT given back by a copy.
            repeat(12) {
                val channel = statuses.enqueue()
                channel.trySend(StatusFeed.Data(mapOf("me" to status(), "t" to status()), fromCache = true))
                channel.trySend(StatusFeed.Ended)
            }
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            advanceTimeBy(60 * minute)
            runCurrent()
            assertEquals(5, statuses.started.size)
            job.cancel()
        }

    @Test
    fun aLostAccess_isHandedToTheDirectory_andNothingOfTheMiffStaysOnScreen() =
        runTest(dispatcher) {
            ready()
            statuses.enqueue()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            runCurrent()
            repo.myMiffsResult =
                MyMiffsLoad.Loaded(
                    emptyList(),
                    listOf(LostMiff(MembershipRef("free", MIFF_ID), "Chez nous")),
                    false,
                    false,
                )
            repo.observer.flowOf(MIFF_ID).tryEmit(MiffObservation.AccessLost)
            runCurrent()
            assertEquals(1, directory.notice.value.size)
            assertEquals(FeedUiState.Loading, vm.uiState.value)
            job.cancel()
        }

    @Test
    fun anAccessLostThatTheServerDoesNotConfirm_isNotARemoval_butACalmUnavailable() =
        runTest(dispatcher) {
            ready()
            statuses.enqueue()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            runCurrent()
            // The server still lists the Miff: the refusal heard was not a removal.
            repo.observer.flowOf(MIFF_ID).tryEmit(MiffObservation.AccessLost)
            runCurrent()
            assertTrue(directory.notice.value.isEmpty())
            assertEquals(FeedUiState.Unavailable, vm.uiState.value)
            job.cancel()
        }

    // --- Localiser ------------------------------------------------------------------------------------------------------

    private suspend fun TestScope.withMember(block: suspend (FeedViewModel) -> Unit) {
        ready()
        val feed = statuses.enqueue()
        val vm = viewModel()
        val job = observing(vm)
        runCurrent()
        repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
        feed.trySend(StatusFeed.Data(mapOf("me" to status(), "t" to status()), fromCache = false))
        runCurrent()
        block(vm)
        job.cancel()
    }

    @Test
    fun locate_oneTap_oneReading_thenTheSheet() =
        runTest(dispatcher) {
            withMember { vm ->
                positions.answer = PositionLookup.Found(48.85661, 2.35222, base - 20 * minute)
                vm.onLocate("t")
                assertEquals(LocateUiState.Loading("t"), vm.locate.value)
                runCurrent()
                val shown = vm.locate.value as LocateUiState.Shown
                assertEquals("Thomas", shown.firstName)
                assertTrue(shown.result is LocateResult.Recent)
                assertEquals(listOf(MIFF_ID to "t"), positions.reads)
            }
        }

    @Test
    fun locate_aSecondTapWhileReading_doesNothing() =
        runTest(dispatcher) {
            withMember { vm ->
                positions.gate = CompletableDeferred()
                vm.onLocate("t")
                runCurrent()
                vm.onLocate("t")
                vm.onLocate("a")
                runCurrent()
                assertEquals(1, positions.reads.size)
                positions.gate?.complete(Unit)
                runCurrent()
                assertTrue(vm.locate.value is LocateUiState.Shown)
            }
        }

    @Test
    fun locate_theSixAnswers() =
        runTest(dispatcher) {
            withMember { vm ->
                val cases =
                    listOf(
                        PositionLookup.Found(1.0, 2.0, base - 5 * minute) to LocateResult.Recent::class,
                        PositionLookup.Found(1.0, 2.0, base - 30 * hour) to LocateResult.Old::class,
                        PositionLookup.NotFound to LocateResult.NotFound::class,
                        PositionLookup.Denied to LocateResult.Denied::class,
                        PositionLookup.Failed to LocateResult.Unavailable::class,
                    )
                cases.forEach { (lookup, expected) ->
                    positions.answer = lookup
                    vm.onLocate("t")
                    runCurrent()
                    assertEquals(expected, (vm.locate.value as LocateUiState.Shown).result::class)
                    vm.onLocateDismissed()
                    assertEquals(LocateUiState.Idle, vm.locate.value)
                }
            }
        }

    @Test
    fun locate_withoutNetwork_saysSo_withoutReadingAnything() =
        runTest(dispatcher) {
            withMember { vm ->
                network.online = false
                vm.onLocate("t")
                runCurrent()
                assertEquals(LocateResult.Offline, (vm.locate.value as LocateUiState.Shown).result)
                assertTrue(positions.reads.isEmpty())
            }
        }

    @Test
    fun locate_isRefusedForMyselfAndForSomebodyWhoIsNotInTheMiff() =
        runTest(dispatcher) {
            withMember { vm ->
                vm.onLocate("me")
                vm.onLocate("nobody")
                runCurrent()
                assertEquals(LocateUiState.Idle, vm.locate.value)
                assertTrue(positions.reads.isEmpty())
            }
        }

    @Test
    fun locate_aMemberWithNoStatusYet_canBeLocated_andTheAnswerIsCalm() =
        runTest(dispatcher) {
            withMember { vm ->
                // "a" has no status document yet (neutral), but shares: ticket 5.6, the button exists and "not found" is a calm answer.
                positions.answer = PositionLookup.NotFound
                vm.onLocate("a")
                runCurrent()
                assertEquals(LocateResult.NotFound, (vm.locate.value as LocateUiState.Shown).result)
                assertEquals(listOf(MIFF_ID to "a"), positions.reads)
            }
        }

    @Test
    fun locate_anUnexpectedFailure_isTheSixthAnswer_neverNoConnection() =
        runTest(dispatcher) {
            withMember { vm ->
                // Quota reached, time exceeded, sign-in expired...: the reader says Failed, or the call itself throws.
                positions.answer = PositionLookup.Failed
                vm.onLocate("t")
                runCurrent()
                assertEquals(LocateResult.Unavailable, (vm.locate.value as LocateUiState.Shown).result)
                vm.onLocateDismissed()
                positions.failure = IllegalStateException("boom")
                vm.onLocate("t")
                runCurrent()
                assertEquals(LocateResult.Unavailable, (vm.locate.value as LocateUiState.Shown).result)
                vm.onLocateDismissed()
                // "Unreachable" while the phone HAS a network is not "no connection" either.
                positions.failure = null
                positions.answer = PositionLookup.Offline
                vm.onLocate("t")
                runCurrent()
                assertEquals(LocateResult.Unavailable, (vm.locate.value as LocateUiState.Shown).result)
            }
        }

    @Test
    fun locate_dismissedWhileReading_dropsTheAnswer() =
        runTest(dispatcher) {
            withMember { vm ->
                positions.gate = CompletableDeferred()
                vm.onLocate("t")
                runCurrent()
                vm.onLocateDismissed()
                positions.gate?.complete(Unit)
                runCurrent()
                assertEquals(LocateUiState.Idle, vm.locate.value)
            }
        }

    @Test
    fun theStateOfTheSheet_hidesThePositionInToString() =
        runTest(dispatcher) {
            withMember { vm ->
                positions.answer = PositionLookup.Found(48.85661, 2.35222, base - 5 * minute)
                vm.onLocate("t")
                runCurrent()
                val text = vm.locate.value.toString()
                assertFalse(text, text.contains("48.8") || text.contains("2.35") || text.contains("Thomas"))
                assertNotNull(vm.locate.value)
            }
        }

    // --- Actualiser (pull to refresh, decision of the PM, 2026-11-06) ----------------------------------------------------

    @Test
    fun refresh_readsTheServerOnce_andRecomputesTheColoursFromWhatItSaid() =
        runTest(dispatcher) {
            withMember { vm ->
                refresher.answer =
                    ServerRead.Fresh(
                        mapOf("me" to status(), "t" to status(PresenceValue.OUT, presenceAgo = 70 * minute)),
                    )
                vm.onRefresh()
                runCurrent()
                assertEquals(listOf(MIFF_ID to true), refresher.calls)
                assertTrue(vm.content().bubble is Bubble.Alerts)
                assertFalse(vm.refreshing.value)
            }
        }

    @Test
    fun refresh_theIndicatorIsOnWhileItReads_andASecondPullIsIgnored() =
        runTest(dispatcher) {
            withMember { vm ->
                refresher.gate = CompletableDeferred()
                vm.onRefresh()
                runCurrent()
                assertTrue(vm.refreshing.value)
                vm.onRefresh()
                runCurrent()
                assertEquals(1, refresher.calls.size)
                refresher.gate?.complete(Unit)
                runCurrent()
                assertFalse(vm.refreshing.value)
            }
        }

    @Test
    fun refresh_atMostOneServerReadingEvery15Seconds_aCloserPullEndsAtOnce() =
        runTest(dispatcher) {
            withMember { vm ->
                vm.onRefresh()
                runCurrent()
                assertEquals(1, refresher.calls.size)
                advanceTimeBy(14_000)
                vm.onRefresh()
                runCurrent()
                // Too soon: no new reading, and the indicator never even started.
                assertEquals(1, refresher.calls.size)
                assertFalse(vm.refreshing.value)
                advanceTimeBy(1_500)
                vm.onRefresh()
                runCurrent()
                assertEquals(2, refresher.calls.size)
            }
        }

    @Test
    fun refresh_offline_endsAtOnce_withoutReadingAnything() =
        runTest(dispatcher) {
            withMember { vm ->
                network.online = false
                runCurrent()
                vm.onRefresh()
                runCurrent()
                assertTrue(refresher.calls.isEmpty())
                assertFalse(vm.refreshing.value)
                // The dated banner of "offline" is there already.
                assertTrue(vm.content().banners.any { it is FeedBanner.Offline })
            }
        }

    @Test
    fun refresh_withoutAnAnswer_endsAfter8Seconds_withTheHonestBanner_neverAnAlarm() =
        runTest(dispatcher) {
            ready()
            val feed = statuses.enqueue()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            feed.trySend(StatusFeed.Data(mapOf("me" to status(), "t" to status()), fromCache = false))
            runCurrent()
            refresher.gate = CompletableDeferred() // the server never answers
            vm.onRefresh()
            runCurrent()
            assertTrue(vm.refreshing.value)
            advanceTimeBy(7_900)
            runCurrent()
            assertTrue(vm.refreshing.value)
            advanceTimeBy(200)
            runCurrent()
            assertFalse(vm.refreshing.value)
            // What is shown is told as "Dernières informations de …".
            assertTrue(vm.content().banners.any { it is FeedBanner.Offline })
            // The server speaks later (the listener): nothing is stale any more.
            feed.trySend(StatusFeed.Data(mapOf("me" to status(), "t" to status()), fromCache = false))
            runCurrent()
            assertTrue(vm.content().banners.none { it is FeedBanner.Offline })
            job.cancel()
        }

    @Test
    fun refresh_leavesTheCacheMode_whenTheServerAnswers() =
        runTest(dispatcher) {
            ready()
            val feed = statuses.enqueue()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            feed.trySend(StatusFeed.Data(mapOf("me" to status(), "t" to status()), fromCache = true))
            runCurrent()
            // Online, a copy of the cache is held back (the skeleton).
            assertEquals(FeedUiState.Loading, vm.uiState.value)
            refresher.answer = ServerRead.Fresh(mapOf("me" to status(), "t" to status()))
            vm.onRefresh()
            runCurrent()
            // The server spoke: shown at once, as the present, without the banner of the cache.
            assertTrue(vm.content().banners.isEmpty())
            assertTrue(vm.content().bubble is Bubble.AllGood)
            job.cancel()
        }

    @Test
    fun refresh_attachesTheListenerAgain_whenItEnded_andTheAttemptsStartAgainFromZero() =
        runTest(dispatcher) {
            ready()
            // The five attachments of a series (the first and four attempts), all refused; the sixth is the one of the refresh.
            repeat(5) { statuses.enqueue().trySend(StatusFeed.Ended) }
            val sixth = statuses.enqueue()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            advanceTimeBy(60 * minute)
            runCurrent()
            assertEquals(5, statuses.started.size)
            refresher.answer = ServerRead.Fresh(mapOf("me" to status(), "t" to status()))
            vm.onRefresh()
            runCurrent()
            // Attached again (and the content is the server's, not a skeleton, not "Partage de ton statut en cours…").
            assertEquals(6, statuses.started.size)
            assertTrue(vm.content().banners.none { it is FeedBanner.Publishing })
            // It ends again (a refusal): the whole series of attempts is back, 10 s later, not "never again".
            sixth.trySend(StatusFeed.Ended)
            runCurrent()
            advanceTimeBy(11_000)
            runCurrent()
            assertEquals(7, statuses.started.size)
            job.cancel()
        }

    @Test
    fun refresh_whenTheRulesStillRefuse_isThePublishingBanner_notAnError() =
        runTest(dispatcher) {
            withMember { vm ->
                refresher.answer = ServerRead.Fresh(null)
                vm.onRefresh()
                runCurrent()
                assertTrue(vm.content().banners.contains(FeedBanner.Publishing))
            }
        }

    @Test
    fun refresh_whenIDoNotShare_doesNotAskForTheStatuses() =
        runTest(dispatcher) {
            ready()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer
                .flowOf(
                    MIFF_ID,
                ).tryEmit(snapshot(member("me", "Camille", sharing = false), member("t", "Thomas")))
            runCurrent()
            vm.onRefresh()
            runCurrent()
            assertEquals(listOf(MIFF_ID to false), refresher.calls)
            assertTrue(statuses.started.isEmpty())
            job.cancel()
        }

    @Test
    fun refresh_anUnreachableServer_isCalm_theStateIsKept() =
        runTest(dispatcher) {
            withMember { vm ->
                refresher.answer = ServerRead.Unreachable
                vm.onRefresh()
                runCurrent()
                assertFalse(vm.refreshing.value)
                assertTrue(vm.content().banners.any { it is FeedBanner.Offline })
                assertTrue(vm.content().bubble is Bubble.AllGood)
            }
        }

    @Test
    fun refresh_changingMiffDuringTheReading_neverPutsTheStatusesOfAOnTheCardsOfB() =
        runTest(dispatcher) {
            val otherId = "ZyXwVuTsRqPoNmLkJiHg"
            repo.myMiffsResult =
                MyMiffsLoad.Loaded(
                    listOf(MiffSummary(MIFF_ID, "Chez nous"), MiffSummary(otherId, "Les copains")),
                    emptyList(),
                    premium = true,
                    fromCache = false,
                )
            directory.refresh()
            val feedA = statuses.enqueue()
            val feedB = statuses.enqueue()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            feedA.trySend(StatusFeed.Data(mapOf("me" to status(), "t" to status()), fromCache = false))
            runCurrent()
            assertTrue(vm.content().bubble is Bubble.AllGood)
            // The server is being read for A ...
            refresher.gate = CompletableDeferred()
            refresher.answer = ServerRead.Fresh(mapOf("me" to status(), "t" to status()))
            vm.onRefresh()
            runCurrent()
            assertTrue(vm.refreshing.value)
            // ... and the person chooses B, whose statuses have not answered yet.
            vm.onSelect(otherId)
            runCurrent()
            repo.observer.flowOf(otherId).tryEmit(
                MiffObservation.Data(
                    MiffSnapshot(
                        miffDetails(id = otherId, name = "Les copains", owner = "me"),
                        listOf(member("me", "Camille"), member("t", "Thomas")),
                        listOf(testPlace("p1", "Chez nous")),
                        fromCache = false,
                    ),
                ),
            )
            runCurrent()
            refresher.gate?.complete(Unit)
            runCurrent()
            assertFalse(vm.refreshing.value)
            // The answer about A is dropped: B still waits for ITS statuses (the skeleton), nothing of A is on its cards.
            assertEquals(FeedUiState.Loading, vm.uiState.value)
            // B's own statuses arrive: only they are drawn (Thomas has said nothing here).
            feedB.trySend(StatusFeed.Data(mapOf("me" to status()), fromCache = false))
            runCurrent()
            val content = vm.content()
            assertEquals(otherId, content.miffId)
            assertEquals(StatusLevel.NEUTRAL, content.cards.first { it.member.uid == "t" }.level)
            assertTrue(content.banners.none { it is FeedBanner.Offline })
            job.cancel()
        }

    @Test
    fun refresh_changingMiffDuringTheReading_theUnreachableMentionOfAIsNotCarriedToB() =
        runTest(dispatcher) {
            val otherId = "ZyXwVuTsRqPoNmLkJiHg"
            repo.myMiffsResult =
                MyMiffsLoad.Loaded(
                    listOf(MiffSummary(MIFF_ID, "Chez nous"), MiffSummary(otherId, "Les copains")),
                    emptyList(),
                    premium = true,
                    fromCache = false,
                )
            directory.refresh()
            val feedA = statuses.enqueue()
            val feedB = statuses.enqueue()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            feedA.trySend(StatusFeed.Data(mapOf("me" to status(), "t" to status()), fromCache = false))
            runCurrent()
            refresher.gate = CompletableDeferred()
            refresher.answer = ServerRead.Unreachable
            vm.onRefresh()
            runCurrent()
            vm.onSelect(otherId)
            runCurrent()
            repo.observer.flowOf(otherId).tryEmit(
                MiffObservation.Data(
                    MiffSnapshot(
                        miffDetails(id = otherId, name = "Les copains", owner = "me"),
                        listOf(member("me", "Camille"), member("t", "Thomas")),
                        listOf(testPlace("p1", "Chez nous")),
                        fromCache = false,
                    ),
                ),
            )
            feedB.trySend(StatusFeed.Data(mapOf("me" to status(), "t" to status()), fromCache = false))
            runCurrent()
            refresher.gate?.complete(Unit)
            runCurrent()
            assertTrue(vm.content().banners.none { it is FeedBanner.Offline })
            job.cancel()
        }

    // --- Bug of 2026-11-10: "le Feed se charge à l'infini sans connexion" ------------------------------------------------

    private fun readsOfTheMiffs() = repo.calls.count { it == "myMiffs" }

    @Test
    fun noNetworkAndNoCopyOfTheMiff_theSkeletonNeverEnds_theCauseOfTheBug_butThePopInSaysIt() =
        runTest(dispatcher) {
            ready()
            network.online = false
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            // The listener of the Miff finds nothing in the cache and the server cannot be reached: it says "loading" and nothing else, for ever.
            advanceTimeBy(10 * minute)
            assertEquals(FeedUiState.Loading, vm.uiState.value)
            assertTrue(vm.connection.value.popInVisible)
            job.cancel()
        }

    @Test
    fun popIn_theAutomaticAttemptEvery10Seconds_readsNothingWhileThereIsNoNetwork() =
        runTest(dispatcher) {
            ready()
            network.online = false
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            val before = readsOfTheMiffs()
            advanceTimeBy(2 * minute)
            assertTrue(vm.connection.value.popInVisible)
            // Twelve rounds of the timer with no network: not one reading more (no cost).
            assertEquals(before, readsOfTheMiffs())
            job.cancel()
        }

    @Test
    fun popIn_theNetworkComesBack_oneReadingAtOnce_andThePopInLeavesWhenTheContentArrives() =
        runTest(dispatcher) {
            ready()
            network.online = false
            val feed = statuses.enqueue()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            advanceTimeBy(30_000)
            assertTrue(vm.connection.value.popInVisible)
            val before = readsOfTheMiffs()
            network.online = true
            runCurrent()
            assertEquals(before + 1, readsOfTheMiffs())
            // The pop-in left at once (the network is back); the content arrives from the server.
            assertFalse(vm.connection.value.active)
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            feed.trySend(StatusFeed.Data(mapOf("me" to status(), "t" to status()), fromCache = false))
            runCurrent()
            assertTrue(vm.content().banners.isEmpty())
            advanceTimeBy(60_000)
            assertFalse(vm.connection.value.active)
            job.cancel()
        }

    @Test
    fun popIn_réessayer_readsAgainAtOnce_andStartsTheCountdownAgain() =
        runTest(dispatcher) {
            ready()
            network.online = false
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            advanceTimeBy(6_000)
            assertTrue(vm.connection.value.secondsLeft!! < 10)
            val before = readsOfTheMiffs()
            vm.onConnectionRetry()
            runCurrent()
            assertEquals(before + 1, readsOfTheMiffs())
            assertEquals(10, vm.connection.value.secondsLeft)
            assertTrue(vm.connection.value.popInVisible)
            job.cancel()
        }

    @Test
    fun popIn_réessayer_tappedManyTimes_neverPilesUpReadings() =
        runTest(dispatcher) {
            ready()
            network.online = false
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            val before = readsOfTheMiffs()
            repeat(10) { vm.onConnectionRetry() }
            runCurrent()
            // The first tap reads; the others ask for ONE more at most after it.
            assertTrue(readsOfTheMiffs() - before <= 2)
            job.cancel()
        }

    @Test
    fun withACopyOfTheCache_noPopIn_theBannerAndTheDateStayAsBefore() =
        runTest(dispatcher) {
            ready()
            network.online = false
            val feed = statuses.enqueue()
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            repo.observer.flowOf(MIFF_ID).tryEmit(snapshot(*trio))
            feed.trySend(StatusFeed.Data(mapOf("me" to status(), "t" to status()), fromCache = true))
            advanceTimeBy(5 * minute)
            assertTrue(vm.content().banners.any { it is FeedBanner.Offline })
            assertFalse(vm.connection.value.active)
            job.cancel()
        }

    @Test
    fun popIn_closedByTheBackGesture_thePageSaysItAndTheAttemptsGoOn() =
        runTest(dispatcher) {
            ready()
            network.online = false
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            advanceTimeBy(5_000)
            vm.onConnectionDismissed()
            assertFalse(vm.connection.value.popInVisible)
            assertTrue(vm.connection.value.inlineVisible)
            val before = readsOfTheMiffs()
            network.online = true
            runCurrent()
            assertEquals(before + 1, readsOfTheMiffs())
            job.cancel()
        }

    @Test
    fun leavingTheScreen_stopsThePopInAndTheAttempts() =
        runTest(dispatcher) {
            ready()
            network.online = true
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            advanceTimeBy(9_000)
            assertTrue(vm.connection.value.popInVisible)
            job.cancel()
            runCurrent()
            val before = readsOfTheMiffs()
            advanceTimeBy(5 * minute)
            assertFalse(vm.connection.value.active)
            assertEquals(before, readsOfTheMiffs())
        }

    @Test
    fun aMiffWithNothingYet_andANetwork_thePopInOnlyComesAfterAShortDelay_withBoundedAttempts() =
        runTest(dispatcher) {
            ready()
            network.online = true
            val vm = viewModel()
            val job = observing(vm)
            runCurrent()
            advanceTimeBy(7_000)
            assertFalse(vm.connection.value.active)
            advanceTimeBy(2_000)
            assertTrue(vm.connection.value.popInVisible)
            val before = readsOfTheMiffs()
            advanceTimeBy(10 * minute)
            // Three automatic attempts at most for a network that answers nothing, then the button only.
            assertEquals(before + 3, readsOfTheMiffs())
            job.cancel()
        }
}
