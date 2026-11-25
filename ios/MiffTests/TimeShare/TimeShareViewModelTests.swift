import XCTest
@testable import Miff

/// Le double du téléphone pour la carte : ce que le suivi est « maintenant » (autorisations, voyage, partage, zones).
@MainActor
final class FakeTimeShareContext: TimeShareContextSource {
    var conditions = TimeShareConditions()
    private(set) var asked: [String] = []

    func conditions(miffId: String) async -> TimeShareConditions {
        asked.append(miffId)
        return conditions
    }
}

/// La carte « Répartition de ton temps » (TIME-4) : l'état qu'elle montre selon les lieux lus, le journal, la fenêtre et l'état du suivi
/// (ticket §7), le choix 7 / 14 / 30 jours gardé, « Effacer mon historique ». Aucune lecture Firestore : tout vient du journal local.
@MainActor
final class TimeShareViewModelTests: XCTestCase {
    private let history = MemoryPresenceHistory()
    private let context = FakeTimeShareContext()
    private let miff = "m1"

    private func model() -> TimeShareViewModel {
        TimeShareViewModel(history: history, context: context, clock: { TimeShareFixtures.now })
    }

    private func place(_ id: String, _ name: String, _ type: PlaceType = .trusted) -> Place {
        Place(
            id: id, miffId: miff, name: name, type: type, category: .home, address: nil,
            lat: 48.85, lng: 2.35, radiusM: 100, createdBy: "u1"
        )
    }

    private func record(_ segments: [TimeShareFixtures.Segment]) {
        for fact in TimeShareFixtures.timeline(segments, miff: miff) {
            history.record(fact)
        }
    }

    private func settle() async {
        try? await Task.sleep(nanoseconds: 60_000_000)
    }

    // MARK: - Masquée tant que les lieux ne sont pas lus, et sans lieu

    func testNothingIsDrawnUntilThePlacesAreRead() async {
        let card = model()

        card.show(miffId: miff, places: nil)
        await settle()

        XCTAssertEqual(card.display.state, .hidden)
        XCTAssertTrue(context.asked.isEmpty, "rien n'est demandé au téléphone tant qu'il n'y a pas de carte à dessiner")
    }

    func testAMiffWithoutAnyPlaceHasNoCard() async {
        record([(.trusted, "p1", 30), (.out, nil, 10)])
        let card = model()

        card.show(miffId: miff, places: [])
        await settle()

        XCTAssertEqual(card.display.state, .hidden)
    }

    func testShowingNoMiffHidesTheCardAtOnce() async {
        record([(.trusted, "p1", 30), (.out, nil, 10)])
        let card = model()
        card.show(miffId: miff, places: [place("p1", "Maison")])
        await waitUntil { card.display.state != .hidden }

        card.show(miffId: nil, places: nil)

        XCTAssertEqual(card.display.state, .hidden)
    }

    // MARK: - Les états

    func testEnoughDataDrawsTheChartWithTheCurrentNameOfThePlace() async throws {
        record([(.trusted, "p1", 30), (.out, nil, 10)])
        let card = model()

        card.show(miffId: miff, places: [place("p1", "Chez Mamie")])
        await waitUntil { card.display.state != .hidden }

        guard case .nominal(let result, let pause) = card.display.state else {
            return XCTFail("la répartition devrait être montrée")
        }
        XCTAssertFalse(pause)
        XCTAssertEqual(result.rows.first?.name, "Chez Mamie", "le nom vient de la liste ACTUELLE des lieux")
        XCTAssertEqual(result.rows.map { $0.percent }, [75, 25])
        XCTAssertEqual(context.asked, [miff])
    }

    func testWithoutEnoughDataTheFrozenWordingStateIsShownWhileTrackingRuns() async {
        let card = model()

        card.show(miffId: miff, places: [place("p1", "Maison")])
        await waitUntil { card.display.state != .hidden }

        XCTAssertEqual(card.display.state, .notEnough(daysLeft: 3))
    }

    func testWithoutDataTheRealReasonIsToldInsteadOfWeAreLookingIntoYourSituation() async {
        let card = model()
        context.conditions = TimeShareConditions(travelActive: true)
        card.show(miffId: miff, places: [place("p1", "Maison")])
        await waitUntil { card.display.state != .hidden }
        XCTAssertEqual(card.display.state, .travel)

        context.conditions = TimeShareConditions(sharingStopped: true)
        card.refresh()
        await waitUntil { card.display.state == .sharingStopped }
        XCTAssertEqual(card.display.state, .sharingStopped)

        context.conditions = TimeShareConditions(locationInsufficient: true)
        card.refresh()
        await waitUntil { card.display.state == .locationInsufficient }
        XCTAssertEqual(card.display.state, .locationInsufficient)
    }

    func testEnoughDataWithTheTrackingPausedKeepsTheChartAndAddsThePauseLine() async {
        record([(.trusted, "p1", 30), (.out, nil, 10)])
        context.conditions = TimeShareConditions(locationInsufficient: true, overWatchedLimit: true)
        let card = model()

        card.show(miffId: miff, places: [place("p1", "Maison")])
        await waitUntil { card.display.state != .hidden }

        guard case .nominal(_, let pause) = card.display.state else {
            return XCTFail("la répartition devrait être montrée")
        }
        XCTAssertTrue(pause)
        XCTAssertTrue(card.display.overWatchedLimit)
    }

    func testAnUnreadableStorageIsACalmErrorWithRetry() async {
        history.unreadable = true
        let card = model()
        card.show(miffId: miff, places: [place("p1", "Maison")])
        await waitUntil { card.display.state != .hidden }
        XCTAssertEqual(card.display.state, .unavailable)

        history.unreadable = false
        card.refresh()
        await waitUntil { card.display.state != .unavailable }

        XCTAssertEqual(card.display.state, .notEnough(daysLeft: 3))
    }

    // MARK: - « Ton graphique arrive dans X jours »

    func testAFreshJournalCountsTheDaysLeftFromItsFirstRecord() async {
        // Un journal tout neuf dont le premier fait date de 1 jour : « dans 2 jours ».
        for fact in TimeShareFixtures.timeline([(.trusted, "p1", 12), (.out, nil, 12)], miff: miff, aged: false) {
            history.record(fact)
        }
        let card = model()

        card.show(miffId: miff, places: [place("p1", "Maison")])
        await waitUntil { card.display.state != .hidden }

        XCTAssertEqual(card.display.state, .notEnough(daysLeft: 2))
    }

    func testTheCounterGoesDownDayByDayThenTheChartShowsUpAndErasingStartsAgain() async {
        // Premier fait il y a 12 h : « dans 3 jours ».
        for fact in TimeShareFixtures.timeline([(.trusted, "p1", 6), (.out, nil, 6)], miff: miff, aged: false) {
            history.record(fact)
        }
        var nowMillis = TimeShareFixtures.now
        let card = TimeShareViewModel(history: history, context: context, clock: { nowMillis })
        card.show(miffId: miff, places: [place("p1", "Maison")])
        await waitUntil { card.display.state == .notEnough(daysLeft: 3) }
        XCTAssertEqual(card.display.state, .notEnough(daysLeft: 3))

        // Un jour plus tard (retour sur le Profil, reprise de la scène) : 1 jour entier écoulé, il en reste 2.
        nowMillis += TimeShareLimits.dayMillis
        card.refresh()
        await waitUntil { card.display.state == .notEnough(daysLeft: 2) }
        XCTAssertEqual(card.display.state, .notEnough(daysLeft: 2))

        // Encore un jour : il en reste 1.
        nowMillis += TimeShareLimits.dayMillis
        card.refresh()
        await waitUntil { card.display.state == .notEnough(daysLeft: 1) }
        XCTAssertEqual(card.display.state, .notEnough(daysLeft: 1))

        // Encore un jour : 3 jours écoulés, le graphique s'affiche.
        nowMillis += TimeShareLimits.dayMillis
        card.refresh()
        await waitUntil { self.percents(card) != [] }
        XCTAssertFalse(percents(card).isEmpty)

        card.eraseHistory()
        await waitUntil { card.display.state == .notEnough(daysLeft: 3) }
        XCTAssertEqual(card.display.state, .notEnough(daysLeft: 3))
    }

    // MARK: - La fenêtre et l'effacement

    func testTheChosenWindowIsKeptAndChangesTheChart() async {
        // 10 jours de données : Maison 40 h puis dehors 40 h, trois fois (240 h).
        record([
            (.trusted, "p1", 40), (.out, nil, 40), (.trusted, "p1", 40), (.out, nil, 40), (.trusted, "p1", 40),
            (.out, nil, 40)
        ])
        let card = model()
        card.show(miffId: miff, places: [place("p1", "Maison")])
        await waitUntil { card.display.state != .hidden }
        XCTAssertEqual(card.display.windowDays, 7)
        XCTAssertEqual(percents(card), [48, 52])

        card.selectWindow(14)
        await waitUntil { card.display.windowDays == 14 && self.percents(card) == [50, 50] }

        XCTAssertEqual(percents(card), [50, 50])
        XCTAssertEqual(history.windowDays(), 14, "le choix est gardé sur le téléphone")
        card.selectWindow(12)
        XCTAssertEqual(card.display.windowDays, 14, "une valeur inconnue est ignorée")
    }

    private func percents(_ card: TimeShareViewModel) -> [Int] {
        if case .nominal(let result, _) = card.display.state {
            return result.rows.map { $0.percent }
        }
        return []
    }

    func testTheWindowStartsFromTheOneKeptOnThePhone() {
        history.setWindowDays(30)

        XCTAssertEqual(model().display.windowDays, 30)
    }

    func testErasingTheHistoryStartsFromZeroWithoutAnyServerWrite() async {
        record([(.trusted, "p1", 30), (.out, nil, 10)])
        let card = model()
        card.show(miffId: miff, places: [place("p1", "Maison")])
        await waitUntil { card.display.state != .hidden }
        XCTAssertNotEqual(card.display.state, .notEnough(daysLeft: 3))

        card.eraseHistory()
        await waitUntil { card.display.state == .notEnough(daysLeft: 3) }

        XCTAssertEqual(card.display.state, .notEnough(daysLeft: 3))
        XCTAssertEqual(history.eraseFactsCalls, 1)
        XCTAssertEqual(history.clearAllCalls, 0, "seul le journal part : l'effacement manuel ne déconnecte rien")
    }

    func testTheChartFollowsTheMiffOfTheCarousel() async {
        for fact in TimeShareFixtures.timeline([(.trusted, "p1", 30), (.out, nil, 10)], miff: "other") {
            history.record(fact)
        }
        let card = model()

        card.show(miffId: miff, places: [place("p1", "Maison")])
        await waitUntil { card.display.state != .hidden }
        XCTAssertEqual(card.display.state, .notEnough(daysLeft: 3), "les faits d'une autre Miff ne comptent pas")

        card.show(miffId: "other", places: [place("p1", "Maison")])
        await waitUntil { card.display.state != .notEnough(daysLeft: 3) }

        if case .nominal = card.display.state {
            return
        }
        XCTFail("la Miff « other » a assez de données")
    }
}
