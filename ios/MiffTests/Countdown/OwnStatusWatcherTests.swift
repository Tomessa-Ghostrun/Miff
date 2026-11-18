import XCTest
@testable import Miff

/// Une écoute de MON statut que le test pilote : il dit ce que le serveur dit, et compte les écoutes ouvertes.
private final class FakeOwnStatusObserver: OwnStatusObserver {
    private var continuations: [AsyncStream<OwnStatusFeed>.Continuation] = []
    private(set) var active = 0
    private(set) var total = 0
    private(set) var requests: [String] = []

    func observe(miffId: String, uid: String) -> AsyncStream<OwnStatusFeed> {
        AsyncStream { continuation in
            self.continuations.append(continuation)
            self.active += 1
            self.total += 1
            self.requests.append("\(miffId)/\(uid)")
            continuation.onTermination = { [weak self] _ in
                self?.active -= 1
            }
        }
    }

    func emit(_ feed: OwnStatusFeed) {
        for continuation in continuations {
            continuation.yield(feed)
        }
    }
}

/// Le compte à rebours du PROFIL reste juste quand le Feed n'écoute pas (revue de la PR #244, « Important 2 ») : MON seul statut est écouté
/// sur les onglets autres que le Feed, et l'encart suit une update publiée (CD-24) ou une arrivée dans un lieu de confiance (CD-22).
@MainActor
final class OwnStatusWatcherTests: XCTestCase {
    private let observer = FakeOwnStatusObserver()
    private let store = MyStatusStore()
    private var uid: String? = "me"
    private var waiting: Set<String> = []

    private func makeWatcher() -> OwnStatusWatcher {
        OwnStatusWatcher(
            observer: observer,
            currentUid: { self.uid },
            isWaitingAgreement: { self.waiting.contains($0) },
            store: store
        )
    }

    private func start(_ watcher: OwnStatusWatcher, miffId: String? = testMiffId) -> Task<Void, Never> {
        Task {
            await watcher.watch(miffId: miffId)
        }
    }

    private func stop(_ task: Task<Void, Never>) async {
        task.cancel()
        await task.value
    }

    private func state() -> CountdownState {
        CountdownRules.state(store.facts, sharingStopped: store.sharingStopped, nowMillis: feedNow)
    }

    private func displayedMinutes() -> Int? {
        if case .visible(let display) = state() {
            return display.minutes
        }
        return nil
    }

    private func out(departedAgo: Int, updatedAgo: Int? = nil) -> MemberStatus {
        feedStatus(.out, placeId: nil, departedAgo: departedAgo, updatedAgo: updatedAgo)
    }

    // MARK: - CD-24 : une update publiée remet l'encart à 2 h

    func testAnUpdatePublishedWhileTheProfileIsShownRestartsTheCountdownAtTwoHours() async {
        // Le Feed avait donné le repère du départ, il y a 1 h 30 : il reste 30 min.
        store.update(MyStatusFacts(out(departedAgo: feedMinutes(90))))
        XCTAssertEqual(displayedMinutes(), 30)
        let watcher = makeWatcher()
        let task = start(watcher)
        await waitUntil { self.observer.active == 1 }

        // L'update est publiée (depuis l'encart du Profil, puis retour) : le serveur dit la nouvelle heure.
        observer.emit(.data(out(departedAgo: feedMinutes(90), updatedAgo: 0)))
        await waitUntil { self.displayedMinutes() == 120 }

        XCTAssertEqual(displayedMinutes(), 120, "repart d'une fenêtre pleine, jamais l'ancien repère")
        await stop(task)
    }

    // MARK: - CD-22 : arrivée dans un lieu de confiance

    func testArrivingInATrustedPlaceWhileTheProfileIsShownHidesTheCountdown() async {
        store.update(MyStatusFacts(out(departedAgo: feedMinutes(40))))
        XCTAssertTrue(state().isVisible)
        let watcher = makeWatcher()
        let task = start(watcher)
        await waitUntil { self.observer.active == 1 }

        observer.emit(.data(feedStatus(.inTrusted, placeId: "p1", departedAgo: 0)))
        await waitUntil { !self.state().isVisible }

        XCTAssertEqual(state(), .hidden, "plus de « Tu es en dehors de tes lieux de confiance »")
        await stop(task)
    }

    // MARK: - Ce que l'écoute fait et ne fait pas

    func testItListensToOneMiffAndOneAccountAndStopsWhenCancelled() async {
        let watcher = makeWatcher()
        let task = start(watcher)
        await waitUntil { self.observer.active == 1 }

        XCTAssertEqual(observer.requests, ["\(testMiffId)/me"], "UN document : ma fiche dans la Miff affichée")
        await stop(task)
        await waitUntil { self.observer.active == 0 }
        XCTAssertEqual(observer.active, 0, "changement d'onglet, arrière-plan ou autre Miff : l'écoute est retirée")
    }

    func testNothingIsListenedToWithoutAMiffOrWithoutAnAccount() async {
        let watcher = makeWatcher()
        await watcher.watch(miffId: nil)
        uid = nil
        await watcher.watch(miffId: testMiffId)

        XCTAssertEqual(observer.total, 0)
    }

    func testAnAbsentDocumentMeansNoStatusAndKeepsWhatTheFeedSaidAboutStoppedSharing() async {
        store.update(MyStatusFacts(out(departedAgo: feedMinutes(10))), sharingStopped: false)
        let watcher = makeWatcher()
        let task = start(watcher)
        await waitUntil { self.observer.active == 1 }

        observer.emit(.data(nil))
        await waitUntil { self.store.facts == nil }

        XCTAssertNil(store.facts)
        XCTAssertFalse(store.sharingStopped)
        XCTAssertEqual(state(), .hidden)
        await stop(task)
    }

    func testAnEndedListenerMeansNoReadableStatusSoTheCountdownIsHiddenNotWrong() async {
        store.update(MyStatusFacts(out(departedAgo: feedMinutes(10))), sharingStopped: true)
        let watcher = makeWatcher()
        let task = start(watcher)
        await waitUntil { self.observer.active == 1 }

        observer.emit(.ended)
        await waitUntil { self.store.facts == nil }

        XCTAssertNil(store.facts)
        XCTAssertTrue(store.sharingStopped, "« partage arrêté » reste ce que le Feed a dit")
        await stop(task)
    }

    func testWhileMyAgreementIsAwaitedTheStatusIsNotGivenLikeTheFeedDoes() async {
        waiting = [testMiffId]
        store.update(nil)
        let watcher = makeWatcher()
        let task = start(watcher)
        await waitUntil { self.observer.active == 1 }

        observer.emit(.data(out(departedAgo: feedMinutes(10))))
        try? await Task.sleep(nanoseconds: 50_000_000)

        XCTAssertNil(store.facts)
        await stop(task)
    }

    func testTheStatusFactsKeepOnlyWhatTheColourAndThePhotoNeed() {
        let status = MemberStatus(
            presence: .out, placeId: nil, presenceAtMillis: 10, lastUpdateAtMillis: 20, hasPhoto: true,
            batteryPct: 50, locationAccess: .background, updatedAtMillis: 30
        )
        let facts = MyStatusFacts(status)

        XCTAssertEqual(facts.presence, .out)
        XCTAssertEqual(facts.presenceAtMillis, 10)
        XCTAssertEqual(facts.lastUpdateAtMillis, 20)
        XCTAssertTrue(facts.hasPhoto)
    }

    // MARK: - Quand l'écoute tourne (jamais en même temps que celle du Feed)

    func testItListensOnlyOnTheTabsWhereTheFeedDoesNotAndNeverInTheBackground() {
        let feedKey = OwnStatusKey.make(miffId: "m", tab: .feed, inBackground: false)
        XCTAssertFalse(feedKey.listening, "le Feed écoute déjà")
        XCTAssertTrue(OwnStatusKey.make(miffId: "m", tab: .profile, inBackground: false).listening)
        XCTAssertTrue(OwnStatusKey.make(miffId: "m", tab: .update, inBackground: false).listening)
        XCTAssertFalse(OwnStatusKey.make(miffId: "m", tab: .profile, inBackground: true).listening)
        XCTAssertFalse(OwnStatusKey.make(miffId: "m", tab: .update, inBackground: true).listening)
    }

    func testAnotherMiffRestartsTheListening() {
        XCTAssertNotEqual(
            OwnStatusKey.make(miffId: "a", tab: .profile, inBackground: false),
            OwnStatusKey.make(miffId: "b", tab: .profile, inBackground: false)
        )
    }
}
