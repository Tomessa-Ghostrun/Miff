import XCTest
@testable import Miff

/// Le DÉBUT du suivi dans le calcul de l'historique (« Ton graphique arrive dans X jours » : le plus ancien de l'armement de la détection et du
/// premier fait), et l'état « assez d'historique mais aucun temps mesuré » (un texte sans décompte). Mêmes valeurs qu'Android.
final class TimeShareStartTests: XCTestCase {
    private typealias Scn = TimeShareFixtures
    private let hour = TimeShareFixtures.hour

    private func compute(startedHoursAgo: Double?, facts: [PresenceFact] = []) -> TimeShareOutcome {
        TimeShareCalculator.compute(
            facts: facts, places: [Scn.home], windowDays: 7, nowMillis: Scn.now,
            startedAtMillis: startedHoursAgo.map { Scn.now - Int($0 * Double(hour)) }
        )
    }

    func testAnEmptyJournalCountsDownFromTheArmingOfTheDetection() {
        XCTAssertEqual(compute(startedHoursAgo: 1), .notEnough(daysLeft: 3), "1 h : 0 jour entier")
        XCTAssertEqual(compute(startedHoursAgo: 24), .notEnough(daysLeft: 2), "1 jour d'armement : 2, pas 3")
        XCTAssertEqual(compute(startedHoursAgo: 48), .notEnough(daysLeft: 1))
        XCTAssertEqual(compute(startedHoursAgo: 71), .notEnough(daysLeft: 1))
    }

    func testTheCounterOfAnEmptyJournalStopsOnTheWordsThatSayWhatIsMissingAfterThreeDays() {
        XCTAssertEqual(compute(startedHoursAgo: 72), .noTimeMeasured)
        XCTAssertEqual(compute(startedHoursAgo: 200), .noTimeMeasured, "jamais « dans 1 jour » indéfiniment")
    }

    func testTheHistoryStartsFromTheOlderOfTheArmingAndTheFirstFact() {
        // Le premier fait (48 h) est plus ancien que l'armement (10 h) : 48 h comptent. L'inverse : l'armement (60 h) l'emporte.
        let facts = [Scn.fact(.trusted, "p1", hoursAgo: 48)]

        XCTAssertEqual(compute(startedHoursAgo: 10, facts: facts), .notEnough(daysLeft: 1))
        XCTAssertEqual(compute(startedHoursAgo: 60, facts: facts), .notEnough(daysLeft: 1))
        XCTAssertNotNil(Scn.ready(compute(startedHoursAgo: 80, facts: facts)), "l'armement plus ancien suffit")
    }

    func testAnArmingInTheFutureCountsZeroAndTheHistoryAlreadyMeasuredCountsAtOnce() {
        XCTAssertEqual(compute(startedHoursAgo: -5), .notEnough(daysLeft: 3), "un début dans le futur compte 0")
        // Un journal de 5 jours déjà mesuré, sans armement mémorisé (avant cette version) : le graphique s'affiche tout de suite.
        let facts = Scn.timeline([(.trusted, "p1", 60), (.out, nil, 60)], aged: false)
        XCTAssertNotNil(Scn.ready(compute(startedHoursAgo: nil, facts: facts)))
    }
}
