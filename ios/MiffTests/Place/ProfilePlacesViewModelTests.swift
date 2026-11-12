import XCTest
@testable import Miff

/// Une lecture des lieux d'une Miff : double en mémoire qui compte les lectures (le COÛT Firestore est ce qu'on prouve ici).
private final class FakePlacesReader: PlacesReader {
    var answers: [String: PlacesRead] = [:]
    private(set) var reads: [String] = []

    func read(miffId: String) async -> PlacesRead {
        reads.append(miffId)
        return answers[miffId] ?? .failed
    }
}

private final class ModelClock {
    var now = 1_000_000
}

/// Les lieux sous le carrousel du Profil : UNE lecture par Miff (jamais d'écouteur), zéro pour la Miff du Feed, réutilisée 5 minutes,
/// relue après un passage du créateur, rien de dessiné tant que rien n'est lu, rien de gardé pour une Miff qui disparaît.
@MainActor
final class ProfilePlacesViewModelTests: XCTestCase {
    private let reader = FakePlacesReader()
    private let clock = ModelClock()

    private func makeModel() -> ProfilePlacesViewModel {
        let clock = self.clock
        return ProfilePlacesViewModel(reader: reader, clock: { clock.now })
    }

    private func places(_ ids: [String], miffId: String = "B") -> [Place] {
        ids.map { testPlace(id: $0, name: "Lieu \($0)", miffId: miffId) }
    }

    // MARK: - Rien n'est dessiné avant la lecture

    func testNothingIsSelectedNorDrawnBeforeACardIsShown() {
        let model = makeModel()

        XCTAssertNil(model.state.selectedMiffId)
        XCTAssertEqual(model.state.view, .loading)
    }

    func testWhileTheFirstReadingIsUnderWayTheViewIsLoadingNotEmpty() async {
        reader.answers["B"] = .loaded([])
        let model = makeModel()

        model.onCardShown(miffId: "B")

        XCTAssertEqual(model.state.selectedMiffId, "B")
        XCTAssertEqual(model.state.view, .loading, "pas d'absence inventée")
        await waitUntil { model.state.view != .loading }
        XCTAssertEqual(model.state.view, .ready([]), "une vraie liste vide, une fois lue")
    }

    // MARK: - Une lecture par Miff

    func testAMiffIsReadOnceWhenItsCardAppearsAndThenReusedWhateverHowOftenItComesBack() async {
        reader.answers["B"] = .loaded(places(["p1", "p2"]))
        let model = makeModel()

        model.onCardShown(miffId: "B")
        await waitUntil { model.state.view != .loading }
        model.onCardShown(miffId: "B")
        model.onProfileAppeared()
        model.onCardShown(miffId: "B")

        XCTAssertEqual(reader.reads, ["B"])
        XCTAssertEqual(model.state.view, .ready(places(["p1", "p2"])))
    }

    func testTwoAppearancesAtOnceReadOnlyOnce() async {
        reader.answers["B"] = .loaded(places(["p1"]))
        let model = makeModel()

        model.onCardShown(miffId: "B")
        model.onCardShown(miffId: "B")
        await waitUntil { model.state.view != .loading }

        XCTAssertEqual(reader.reads, ["B"])
    }

    func testAfterFiveMinutesTheMiffIsReadAgainOnce() async {
        reader.answers["B"] = .loaded(places(["p1"]))
        let model = makeModel()
        model.onCardShown(miffId: "B")
        await waitUntil { model.state.view != .loading }

        clock.now += MiffPlacesBook.freshMillis
        reader.answers["B"] = .loaded(places(["p1", "p2"]))
        model.onCardShown(miffId: "B")
        await waitUntil { model.state.view == .ready(self.places(["p1", "p2"])) }

        XCTAssertEqual(reader.reads, ["B", "B"])
    }

    // MARK: - La Miff du Feed : zéro lecture

    func testTheMiffTheFeedReadIsDrawnWithNoReading() {
        let model = makeModel()
        model.seedFromFeed(miffId: "A", places: places(["p1"], miffId: "A"))

        model.onCardShown(miffId: "A")

        XCTAssertEqual(model.state.view, .ready(places(["p1"], miffId: "A")))
        XCTAssertTrue(reader.reads.isEmpty)
    }

    func testWhatTheFeedReadAfterTheCardWasShownReplacesTheListWithNoReading() {
        let model = makeModel()
        model.seedFromFeed(miffId: "A", places: places(["p1"], miffId: "A"))
        model.onCardShown(miffId: "A")

        model.seedFromFeed(miffId: "A", places: places(["p1", "p2"], miffId: "A"))

        XCTAssertEqual(model.state.view, .ready(places(["p1", "p2"], miffId: "A")))
        XCTAssertTrue(reader.reads.isEmpty)
    }

    func testTheFeedGivingAnotherMiffDoesNotChangeTheListShown() {
        let model = makeModel()
        model.seedFromFeed(miffId: "A", places: places(["p1"], miffId: "A"))
        model.onCardShown(miffId: "A")

        model.seedFromFeed(miffId: "C", places: places(["p9"], miffId: "C"))

        XCTAssertEqual(model.state.selectedMiffId, "A")
        XCTAssertEqual(model.state.view, .ready(places(["p1"], miffId: "A")))
    }

    // MARK: - Un échec, « Réessayer »

    func testAFailedReadingSaysUnavailableThenRetryReadsAtOnce() async {
        let model = makeModel()
        model.onCardShown(miffId: "B")
        await waitUntil { model.state.view == .unavailable }

        // Revenir sur la carte tout de suite ne relit pas (30 secondes d'attente, jamais de boucle).
        model.onCardShown(miffId: "B")
        XCTAssertEqual(reader.reads, ["B"])

        reader.answers["B"] = .loaded(places(["p1"]))
        model.onRetry()
        await waitUntil { model.state.view == .ready(self.places(["p1"])) }

        XCTAssertEqual(reader.reads, ["B", "B"])
    }

    func testRetryWhileAReadingIsUnderWayDoesNotDoubleIt() async {
        reader.answers["B"] = .loaded(places(["p1"]))
        let model = makeModel()
        model.onCardShown(miffId: "B")

        model.onRetry()
        await waitUntil { model.state.view != .loading }

        XCTAssertEqual(reader.reads, ["B"])
    }

    // MARK: - Le créateur a touché un lieu

    func testAfterACreatorOpenedAPlaceTheListIsReadAgainWhenItComesBackEvenForTheFeedMiff() async {
        let model = makeModel()
        model.seedFromFeed(miffId: "A", places: places(["p1"], miffId: "A"))
        model.onCardShown(miffId: "A")
        reader.answers["A"] = .loaded(places(["p1", "p2"], miffId: "A"))

        model.onPlacesMayChange()
        XCTAssertTrue(reader.reads.isEmpty, "rien n'est lu au moment où le créateur ouvre le lieu")
        model.onProfileAppeared()
        await waitUntil { model.state.view == .ready(self.places(["p1", "p2"], miffId: "A")) }

        XCTAssertEqual(reader.reads, ["A"])
    }

    func testBackAtTheRootReadsAgainOnlyWhenACreatorTouchedAPlaceInTheMeantime() async {
        let model = makeModel()
        model.seedFromFeed(miffId: "A", places: places(["p1"], miffId: "A"))
        model.onCardShown(miffId: "A")

        model.onReturnedToRoot()
        XCTAssertTrue(reader.reads.isEmpty, "rien ne change : rien n'est lu")

        reader.answers["A"] = .loaded(places(["p1", "p2"], miffId: "A"))
        model.onPlacesMayChange()
        model.onReturnedToRoot()
        await waitUntil { model.state.view == .ready(self.places(["p1", "p2"], miffId: "A")) }
        model.onReturnedToRoot()

        XCTAssertEqual(reader.reads, ["A"], "une seule relecture par changement")
    }

    // MARK: - Une Miff qui disparaît

    func testTheListOfAMiffTheCarouselNoLongerHasIsForgottenAndNothingStaysSelected() async {
        reader.answers["B"] = .loaded(places(["p1"]))
        let model = makeModel()
        model.onCardShown(miffId: "B")
        await waitUntil { model.state.view != .loading }

        model.retainOnly(["A"])

        XCTAssertNil(model.state.selectedMiffId)
        XCTAssertEqual(model.state.view, .loading)
    }

    func testAReadingThatEndsAfterTheMiffWasLeftIsThrownAway() async {
        reader.answers["B"] = .loaded(places(["p1"]))
        let model = makeModel()
        model.onCardShown(miffId: "B")
        model.retainOnly(["A"])
        await pause()

        // La Miff revient (rejointe de nouveau) : rien de ce que la lecture tardive a rapporté n'a été gardé, elle est relue.
        model.retainOnly(["A", "B"])
        model.onCardShown(miffId: "B")

        XCTAssertEqual(model.state.view, .loading)
        await waitUntil { reader.reads.count == 2 }
        XCTAssertEqual(reader.reads, ["B", "B"])
    }

    private func pause() async {
        try? await Task.sleep(nanoseconds: 50_000_000)
    }
}
