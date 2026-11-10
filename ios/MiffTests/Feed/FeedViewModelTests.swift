// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// Le view model du Feed, avec des doubles (ticket FEED, critères Q1, Q2, E1 à E4, L1 à L4) : rien n'écoute avant l'écran ni après lui,
// les statuts ne sont lus que si je partage et que mon accord n'est pas attendu, le squelette reste tant que rien n'a répondu,
// une copie du cache n'est pas le présent, « Actualiser » est borné, « Localiser » lit UNE position et ne garde rien.
@MainActor
final class FeedViewModelTests: XCTestCase {
    private let repo = FakeMiffRepository()
    private let statuses = FakeStatusObserver()
    private let positions = FakePositionReader()
    private let refresher = FakeFeedRefresher()
    private let network = FakeNetwork()
    private let awaiting = AwaitingAgreement()
    private let clock = TestBox<Int>(feedNow)
    private let other = feedMember("a", first: "Thomas")
    private let mine = feedMember("me", first: "Camille")

    /// Les minuteries du Feed, accélérées : la minute dure 20 ms, les longues attentes ne finissent jamais pendant un test.
    nonisolated private static func nanoseconds(_ millis: Int) -> UInt64 {
        if millis == FeedMillis.minute {
            return 20_000_000
        }
        return UInt64(max(millis, 0)) * 1_000_000
    }

    private func makeModel(timings: FeedTimings = FeedTimings(
        statusRetryDelaysMillis: [5], firstStatusAnswerMillis: 600_000, cacheGraceMillis: 30
    )) async -> FeedViewModel {
        repo.myMiffsResult = .loaded(
            MyMiffs(miffs: [MiffSummary(id: testMiffId, name: "Chez nous")], lost: [], premium: false, fromCache: false)
        )
        let directory = MiffDirectory(
            repository: repo,
            currentUid: { "me" },
            activeStore: MemoryActiveMiffStore(),
            cache: FakeCachePurger(timeline: Timeline())
        )
        await directory.refresh()
        let now = clock
        return FeedViewModel(
            dependencies: FeedDependencies(
                directory: directory,
                repository: repo,
                services: FeedServices(statuses: statuses, positions: positions, refresher: refresher),
                network: network,
                awaiting: awaiting,
                currentUid: { "me" },
                clock: { now.value },
                sleep: { millis in try? await Task.sleep(nanoseconds: FeedViewModelTests.nanoseconds(millis)) },
                timings: timings
            )
        )
    }

    private func observe(_ model: FeedViewModel) -> Task<Void, Never> {
        Task {
            await model.observe(miffId: testMiffId)
        }
    }

    private func stop(_ task: Task<Void, Never>) async {
        task.cancel()
        await task.value
    }

    private func miffData(_ members: [MiffMember]) {
        repo.observer.emit(.data(MiffSnapshot(miff: testMiffDetails(), members: members, places: [], fromCache: false)))
    }

    private func content(_ model: FeedViewModel) -> FeedContent? {
        if case .ready(let value) = model.uiState {
            return value
        }
        return nil
    }

    // MARK: - Ce qui est lu, quand

    func testNothingListensBeforeTheScreenAndEverythingStopsWhenItLeaves() async {
        let model = await makeModel()
        XCTAssertEqual(repo.observer.subscriptions, 0)
        XCTAssertEqual(statuses.active, 0)

        let task = observe(model)
        miffData([other, mine])
        await waitUntil { self.statuses.active == 1 }

        XCTAssertEqual(repo.observer.subscriptions, 1, "UNE écoute de la Miff, la Miff affichée seulement")
        XCTAssertEqual(statuses.active, 1)
        await stop(task)
        await waitUntil { self.statuses.active == 0 && self.repo.observer.subscriptions == 0 }
        XCTAssertEqual(statuses.active, 0, "écoutes détachées quand l'onglet change ou que l'app part")
        XCTAssertEqual(repo.observer.subscriptions, 0)
    }

    func testTheSkeletonStaysUntilTheStatusesAnswerThenTheCardsAppearMeLast() async {
        let model = await makeModel()
        let task = observe(model)
        miffData([mine, other])
        await waitUntil { self.statuses.active == 1 }
        XCTAssertEqual(model.uiState, .loading, "jamais une couleur que personne ne connaît")

        statuses.emit(["a": feedStatus(.inTrusted, departedAgo: feedMinutes(12))])
        await waitUntil { self.content(model) != nil }

        XCTAssertEqual(content(model)?.cards.map { $0.member.uid }, ["a", "me"])
        XCTAssertEqual(model.me.initials, "CN")
        await stop(task)
    }

    func testIStoppedSharingNothingOfTheOthersIsRead() async {
        let model = await makeModel()
        let task = observe(model)

        miffData([other, feedMember("me", first: "Camille", sharing: false)])
        await waitUntil { self.content(model) != nil }

        XCTAssertEqual(content(model)?.bubble, .sharingStopped)
        XCTAssertEqual(statuses.total, 0, "zéro lecture des statuts")
        await stop(task)
    }

    func testWhileMyAgreementIsWaitedForNothingIsReadAndItIsReadAgainOnceGiven() async {
        awaiting.replace([testMiffId])
        let model = await makeModel()
        let task = observe(model)

        miffData([other, mine])
        await waitUntil { self.content(model) != nil }
        XCTAssertEqual(content(model)?.bubble, .awaitingAgreement)
        XCTAssertEqual(statuses.total, 0)

        awaiting.replace([])
        await waitUntil { self.statuses.active == 1 }
        XCTAssertEqual(statuses.total, 1)
        await stop(task)
    }

    func testARefusalShowsPublishingNeverAnErrorAndTheListenerIsAttachedAgain() async {
        let model = await makeModel()
        let task = observe(model)
        miffData([other, mine])
        await waitUntil { self.statuses.active == 1 }

        statuses.end()
        await waitUntil { self.content(model)?.banners == [.publishing] }
        XCTAssertEqual(content(model)?.banners, [.publishing])
        await waitUntil { self.statuses.total == 2 }

        XCTAssertEqual(statuses.total, 2, "rattachée après une pause, une seule fois par essai")
        await stop(task)
    }

    func testACopyOfTheCacheIsNotThePresentItIsHeldBackThenShownDated() async {
        let model = await makeModel()
        let task = observe(model)
        miffData([other, mine])
        await waitUntil { self.statuses.active == 1 }

        statuses.emit(["a": feedStatus(.out, departedAgo: feedMinutes(70), writtenAgo: feedMinutes(30))], fromCache: true)
        XCTAssertEqual(model.uiState, .loading, "aucune alerte tirée d'une copie que le serveur n'a pas confirmée")
        await waitUntil { self.content(model) != nil }

        XCTAssertEqual(content(model)?.banners.first, .offline(latest: Moment(atMillis: feedNow - feedMinutes(30), day: .today)))
        statuses.emit(["a": feedStatus(.inTrusted)], fromCache: false)
        await waitUntil { self.content(model)?.banners.isEmpty == true }
        XCTAssertEqual(content(model)?.banners, [])
        await stop(task)
    }

    func testNoNetworkIsNotALostAccessAndTheCopyIsShownAtOnceWithItsDate() async {
        network.online = false
        let model = await makeModel()
        let task = observe(model)
        miffData([other, mine])
        await waitUntil { self.statuses.active == 1 }

        statuses.emit(["a": feedStatus(.inTrusted, writtenAgo: feedMinutes(30))], fromCache: true)
        await waitUntil { self.content(model) != nil }

        XCTAssertEqual(content(model)?.banners.count, 1)
        await stop(task)
    }

    func testAnAccessThatTheServerDoesNotConfirmIsNeverTheMessageOfALostAccess() async {
        let model = await makeModel()
        let task = observe(model)

        repo.observer.emit(.accessLost)
        await waitUntil { model.uiState == .unavailable }

        XCTAssertEqual(model.uiState, .unavailable)
        XCTAssertTrue(model.directory.notices.isEmpty)
        await stop(task)
    }

    func testTheColourIsRecomputedWithTheClockWithoutReadingTheServer() async {
        let model = await makeModel()
        let task = observe(model)
        miffData([other, mine])
        await waitUntil { self.statuses.active == 1 }
        statuses.emit(["a": feedStatus(.out, departedAgo: feedMinutes(110))])
        await waitUntil { self.content(model)?.card("a")?.level == .green }

        clock.value += feedMinutes(15)
        await waitUntil { self.content(model)?.card("a")?.level == .orange }

        XCTAssertEqual(content(model)?.card("a")?.level, .orange, "125 min : orange, recalculé par l'horloge")
        XCTAssertEqual(statuses.total, 1, "aucune lecture de plus pour cela")
        await stop(task)
    }

    // MARK: - Actualiser

    func testPullToRefreshReadsOnceEvery15SecondsAndNeverOffline() async {
        let model = await makeModel()
        let task = observe(model)
        miffData([other, mine])
        await waitUntil { self.statuses.active == 1 }
        statuses.emit(["a": feedStatus(.inTrusted)])
        await waitUntil { self.content(model) != nil }
        refresher.answer = .fresh(statuses: ["a": feedStatus(.inTrusted, departedAgo: feedMinutes(3))])

        await model.pullToRefresh()
        await model.pullToRefresh()
        XCTAssertEqual(refresher.calls.count, 1, "un geste plus rapproché que 15 s ne lit rien")

        clock.value += 16_000
        network.online = false
        await model.pullToRefresh()
        XCTAssertEqual(refresher.calls.count, 1, "hors connexion : se termine tout de suite, sans lecture")

        network.online = true
        await model.pullToRefresh()
        XCTAssertEqual(refresher.calls.count, 2)
        XCTAssertFalse(model.refreshing)
        await stop(task)
    }

    func testARefreshTheServerDoesNotAnswerSaysLatestInformationNeverAnAlarm() async {
        let model = await makeModel()
        let task = observe(model)
        miffData([other, mine])
        await waitUntil { self.statuses.active == 1 }
        statuses.emit(["a": feedStatus(.inTrusted, writtenAgo: feedMinutes(5))])
        await waitUntil { self.content(model) != nil }
        refresher.answer = .unreachable

        await model.pullToRefresh()

        XCTAssertEqual(content(model)?.banners.count, 1, "« Dernières informations de… », jamais vert sans date")
        await stop(task)
    }

    func testARefreshOnlyAsksForTheStatusesWhenTheyAreRead() async {
        let model = await makeModel()
        let task = observe(model)
        miffData([other, feedMember("me", sharing: false)])
        await waitUntil { self.content(model) != nil }

        await model.pullToRefresh()

        XCTAssertEqual(refresher.calls, [false])
        await stop(task)
    }

    // MARK: - Localiser

    private func readyForLocate(_ model: FeedViewModel) async -> Task<Void, Never> {
        let task = observe(model)
        miffData([other, mine])
        await waitUntil { self.statuses.active == 1 }
        statuses.emit(["a": feedStatus(.inTrusted, departedAgo: feedMinutes(12))])
        await waitUntil { self.content(model) != nil }
        return task
    }

    func testLocateReadsOnePositionAndTheSheetKeepsNothingOnceClosed() async {
        let model = await makeModel()
        let task = await readyForLocate(model)
        positions.result = .found(lat: 48.85, lng: 2.35, measuredAtMillis: feedNow - feedMinutes(20))

        model.onLocate(uid: "a")
        model.onLocate(uid: "a")
        await waitUntil {
            if case .shown = model.locate {
                return true
            }
            return false
        }

        XCTAssertEqual(positions.calls, ["a"], "UNE lecture, un second toucher pendant la lecture ne fait rien")
        guard case .shown(let uid, let firstName, let result) = model.locate, case .recent(_, _, let age) = result else {
            return XCTFail("la feuille montre une position récente")
        }
        XCTAssertEqual(uid, "a")
        XCTAssertEqual(firstName, "Thomas")
        XCTAssertEqual(age, .minutes(20))
        model.onLocateDismissed()
        XCTAssertEqual(model.locate, .idle)
        await stop(task)
    }

    func testLocateAnswersCalmlyWhenRefusedOrNotFoundOrOffline() async {
        let model = await makeModel()
        let task = await readyForLocate(model)

        positions.result = .denied
        model.onLocate(uid: "a")
        await waitUntil { model.locate != .loading(uid: "a") }
        guard case .shown(_, _, let denied) = model.locate else {
            return XCTFail("une réponse")
        }
        XCTAssertEqual(denied, .denied)
        model.onLocateDismissed()

        network.online = false
        model.onLocate(uid: "a")
        await waitUntil { model.locate != .loading(uid: "a") }
        guard case .shown(_, _, let offline) = model.locate else {
            return XCTFail("une réponse")
        }
        XCTAssertEqual(offline, .offline)
        XCTAssertEqual(positions.calls, ["a"], "hors connexion : rien n'est demandé au serveur")
        await stop(task)
    }

    func testLocateIsRefusedOnMyOwnCardAndTheSheetClosesWhenTheFeedLeavesTheScreen() async {
        let model = await makeModel()
        let task = await readyForLocate(model)

        model.onLocate(uid: "me")
        XCTAssertEqual(model.locate, .idle)
        positions.result = .found(lat: 1, lng: 2, measuredAtMillis: feedNow)
        model.onLocate(uid: "a")
        await waitUntil { model.locate != .loading(uid: "a") }
        await stop(task)

        XCTAssertEqual(model.locate, .idle, "l'écran ne garde aucune position une fois parti")
    }
}
// swiftlint:enable line_length
