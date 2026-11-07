import XCTest
@testable import Miff

/// Une porte que le test ouvre : tant qu'elle est fermée, la lecture reste suspendue (pour agir pendant qu'elle dure).
private final class ReadGate {
    private(set) var isOpen = false

    func open() {
        isOpen = true
    }

    func wait() async {
        while !isOpen {
            try? await Task.sleep(nanoseconds: 5_000_000)
        }
    }
}

private final class GatedRefresher: FeedRefresher {
    let gate = ReadGate()
    var answer = ServerRead.fresh(statuses: [:])
    private(set) var calls = 0

    func refresh(miffId: String, readStatuses: Bool) async -> ServerRead {
        calls += 1
        await gate.wait()
        return answer
    }
}

private final class GatedPositions: PositionReader {
    let gate = ReadGate()
    var result = PositionLookup.notFound

    func read(miffId: String, uid: String) async -> PositionLookup {
        await gate.wait()
        return result
    }
}

// Les cas limites du view model du Feed (revue indépendante de la PR #109) : changer de Miff pendant une lecture, le plafond de 8 s
// d'« Actualiser », le retour d'onglet, une session qui finit pendant « Localiser ».
@MainActor
final class FeedViewModelEdgeTests: XCTestCase {
    private let repo = FakeMiffRepository()
    private let statuses = FakeStatusObserver()
    private let refresher = GatedRefresher()
    private let positions = GatedPositions()
    private let other = feedMember("a", first: "Thomas")
    private let mine = feedMember("me", first: "Camille")

    private func makeModel(timings: FeedTimings = FeedTimings(statusRetryDelaysMillis: [5])) async -> FeedViewModel {
        let miffs = [
            MiffSummary(id: testMiffId, name: "Chez nous"),
            MiffSummary(id: testOtherMiffId, name: "Collègues")
        ]
        repo.myMiffsResult = .loaded(MyMiffs(miffs: miffs, lost: [], premium: true, fromCache: false))
        let directory = MiffDirectory(
            repository: repo,
            currentUid: { "me" },
            activeStore: MemoryActiveMiffStore(),
            cache: FakeCachePurger(timeline: Timeline())
        )
        await directory.refresh()
        return FeedViewModel(
            dependencies: FeedDependencies(
                directory: directory,
                repository: repo,
                services: FeedServices(statuses: statuses, positions: positions, refresher: refresher),
                network: FakeNetwork(),
                awaiting: AwaitingAgreement(),
                currentUid: { "me" },
                clock: { feedNow },
                sleep: { millis in try? await Task.sleep(nanoseconds: UInt64(max(millis, 0)) * 1_000_000) },
                timings: timings
            )
        )
    }

    private func observe(_ model: FeedViewModel, _ miffId: String = testMiffId) -> Task<Void, Never> {
        Task {
            await model.observe(miffId: miffId)
        }
    }

    private func stop(_ task: Task<Void, Never>) async {
        task.cancel()
        await task.value
    }

    private func miffData(_ members: [MiffMember], miffId: String = testMiffId) {
        let details = testMiffDetails(id: miffId)
        let snapshot = MiffSnapshot(miff: details, members: members, places: [], fromCache: false)
        repo.observer.emit(.data(snapshot), miffId: miffId)
    }

    private func ready(_ model: FeedViewModel) -> Bool {
        if case .ready = model.uiState {
            return true
        }
        return false
    }

    private func readyFeed() async -> (FeedViewModel, Task<Void, Never>) {
        let model = await makeModel()
        let task = observe(model)
        miffData([other, mine])
        await waitUntil { self.statuses.active == 1 }
        statuses.emit(["a": feedStatus(.inTrusted, departedAgo: feedMinutes(5))])
        await waitUntil { self.ready(model) }
        return (model, task)
    }

    func testChangingMiffDuringARefreshNeverPutsItsAnswerOnTheOtherMiff() async {
        let (model, first) = await readyFeed()
        refresher.answer = .fresh(statuses: ["a": feedStatus(.inRisk, departedAgo: feedMinutes(5))])
        let refreshing = Task { await model.pullToRefresh() }
        await waitUntil { self.refresher.calls == 1 }

        // La personne choisit l'autre Miff (Premium) : l'écran relance son observation sur elle.
        model.onSelect(testOtherMiffId)
        await stop(first)
        let second = observe(model, testOtherMiffId)
        miffData([other, mine], miffId: testOtherMiffId)
        refresher.gate.open()
        await refreshing.value

        let refused = StatusesView.loaded(
            byUid: ["a": feedStatus(.inRisk, departedAgo: feedMinutes(5))],
            fromCache: false
        )
        XCTAssertNotEqual(model.statusView, refused, "jamais l'état de la première Miff sur la seconde")
        XCTAssertFalse(model.staleAfterRefresh)
        await stop(second)
    }

    func testARefreshThatTheServerNeverAnswersEndsAfterItsCeilingWithTheHonestMention() async {
        let timings = FeedTimings(statusRetryDelaysMillis: [5], refreshTimeoutSeconds: 0.2)
        let model = await makeModel(timings: timings)
        let task = observe(model)
        miffData([other, mine])
        await waitUntil { self.statuses.active == 1 }
        statuses.emit(["a": feedStatus(.inTrusted, writtenAgo: feedMinutes(5))])
        await waitUntil { self.ready(model) }

        await model.pullToRefresh()

        XCTAssertFalse(model.refreshing, "l'indicateur se termine au plus tard après le plafond")
        XCTAssertTrue(model.staleAfterRefresh, "« Dernières informations de… », jamais une phrase alarmante")
        refresher.gate.open()
        await stop(task)
    }

    func testComingBackToTheTabAttachesOneListenerNeverTwo() async {
        let (model, first) = await readyFeed()
        await stop(first)
        await waitUntil { self.statuses.active == 0 }

        let again = observe(model)
        await waitUntil { self.statuses.active == 1 }
        try? await Task.sleep(nanoseconds: 100_000_000)

        XCTAssertEqual(statuses.active, 1, "jamais deux écoutes de statuts en même temps")
        XCTAssertEqual(repo.observer.subscriptions, 1)
        XCTAssertEqual(statuses.total, 2)
        await stop(again)
    }

    func testTheSessionEndingDuringLocateKeepsNothingOfThePosition() async {
        let (model, task) = await readyFeed()
        positions.result = .found(lat: 48.85, lng: 2.35, measuredAtMillis: feedNow)

        model.onLocate(uid: "a")
        XCTAssertEqual(model.locate, .loading(uid: "a"))
        // Le Feed quitte l'écran (déconnexion, retrait de l'onglet) avant la réponse.
        await stop(task)
        positions.gate.open()
        try? await Task.sleep(nanoseconds: 100_000_000)

        XCTAssertEqual(model.locate, .idle, "la réponse tardive n'est jamais montrée ni gardée")
    }
}
