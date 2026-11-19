import XCTest
@testable import Miff

/// La légende de la carte « Répartition du temps » : 4 lignes au plus, les parts les plus longues ; la ligne « Zone de danger » est toujours
/// montrée dès que sa part arrondie est d'au moins 1 % (elle remplace alors la 4e), jamais en dessous ; la description VoiceOver la cite une seule
/// fois (décision du PM, 2026-11-18).
final class TimeShareLegendTests: XCTestCase {
    private typealias Scn = TimeShareFixtures

    /// Une ligne : `percent` est la part arrondie (0 : « < 1 % » ou rien).
    private func row(
        _ kind: TimeShareRowKind,
        _ hours: Double,
        percent: Int = 10,
        name: String? = nil
    ) -> TimeShareRow {
        TimeShareRow(
            kind: kind, name: name, category: nil, millis: Int(hours * Double(TimeShareLimits.hourMillis)),
            percent: percent, isUnderOnePercent: percent == 0 && hours > 0
        )
    }

    private func result(_ rows: [TimeShareRow]) -> TimeShareResult {
        TimeShareResult(
            windowDays: 7, measuredMillis: rows.reduce(0) { $0 + $1.millis }, rows: rows, dangerDetails: [],
            trustedPercent: 0
        )
    }

    private func place(_ id: String, _ hours: Double) -> TimeShareRow {
        row(.place(id), hours, name: id)
    }

    func testALegendOfFourPartsOrLessShowsEverything() {
        XCTAssertEqual(result([place("a", 10)]).legendIndices, [0], "un seul lieu")
        XCTAssertEqual(result([place("a", 10), row(.elsewhere, 5)]).legendIndices, [0, 1])
        let four = [place("a", 40), place("b", 30), row(.danger(1), 20), row(.elsewhere, 10)]
        XCTAssertEqual(result(four).legendIndices, [0, 1, 2, 3])
    }

    func testTheFourLongestPartsAreShownInTheOrderOfTheRing() {
        let rows = [
            place("a", 50), place("b", 40), place("c", 5), place("d", 30), place("e", 20), row(.elsewhere, 10)
        ]

        XCTAssertEqual(result(rows).legendIndices, [0, 1, 3, 4], "c et Hors-zone sont dans l'anneau seulement")
    }

    func testASmallDangerZoneOfAtLeastOnePercentTakesTheFourthPlaceOfTheBiggerPlaces() {
        // 4 plus gros, puis une zone de danger à 1 % : elle prend la place du 4e plus gros.
        let rows = [
            place("a", 50), place("b", 40), place("c", 30), place("d", 20), row(.danger(1), 2, percent: 1),
            row(.elsewhere, 25)
        ]

        XCTAssertEqual(result(rows).legendIndices, [0, 1, 2, 4], "le 4e (Hors-zone, 25 h) cède sa place")
    }

    func testADangerZoneJustUnderOnePercentHasNoLineAndItsPlaceGoesToTheNextPart() {
        let rows = [
            place("a", 50), place("b", 40), place("c", 30), row(.danger(1), 1, percent: 0), place("d", 20),
            place("e", 10)
        ]

        XCTAssertEqual(result(rows).legendIndices, [0, 1, 2, 4], "pas de ligne « < 1 % » : d (20 h) garde sa place")
    }

    func testADangerZoneAlreadyInTheTopFourChangesNothing() {
        let rows = [place("a", 50), row(.danger(2), 40, percent: 30), place("b", 30), place("c", 20), place("d", 5)]

        XCTAssertEqual(result(rows).legendIndices, [0, 1, 2, 3])
    }

    func testSingleNullAndTinyParts() {
        XCTAssertEqual(result([place("a", 10)]).legendIndices, [0], "un seul lieu")
        XCTAssertEqual(result([]).legendIndices, [])
        // Part nulle ou « < 1 % » alors qu'il y a peu de parts : jamais de ligne de zone de danger.
        let few = [place("a", 50), row(.danger(1), 1, percent: 0), row(.elsewhere, 5)]
        XCTAssertEqual(result(few).legendIndices, [0, 2])
        let zero = [place("a", 50), place("b", 40), place("c", 30), place("d", 20), row(.danger(1), 0, percent: 0)]
        XCTAssertEqual(result(zero).legendIndices, [0, 1, 2, 3])
    }

    func testTiesKeepTheOrderOfTheRingAndTheDangerZoneStillWins() {
        let rows = [
            place("a", 10), place("b", 10), place("c", 10), place("d", 10), place("e", 10),
            row(.danger(1), 10, percent: 15)
        ]

        // Égalité parfaite : les 4 premiers de l'anneau ; la zone de danger (dernière) prend la place du 4e.
        XCTAssertEqual(result(rows).legendIndices, [0, 1, 2, 5])
    }

    func testTheDangerLineOfARealResultIsInTheLegendFromOnePercentAndCitedOnceByVoiceOver() throws {
        // 4 lieux et Hors-zone plus longs ; une zone de danger de 4 h (1,7 %) : elle est dans la légende, pas dans la description.
        let segments: [TimeShareFixtures.Segment] = [
            (.trusted, "p1", 40), (.out, nil, 30), (.trusted, "p2", 30), (.out, nil, 30), (.trusted, "p3", 25),
            (.out, nil, 20), (.trusted, "p4", 20), (.out, nil, 20), (.danger, "r1", 4), (.out, nil, 20)
        ]
        let gym = TimeSharePlace(id: "p4", name: "Gym", type: .trusted, category: nil)
        let places = [Scn.home, Scn.sport, Scn.work, gym, Scn.risk1]
        let real = try XCTUnwrap(Scn.ready(Scn.compute(Scn.timeline(segments), places: places, days: 30)))

        let kinds = real.legendIndices.map { real.rows[$0].kind }
        let spoken = try XCTUnwrap(TimeShareTexts.spokenSummary(real, locale: Locale(identifier: "en_US")))

        XCTAssertEqual(kinds.count, 4)
        XCTAssertTrue(kinds.contains(.danger(1)))
        XCTAssertFalse(spoken.contains(TimeShareTexts.dangerZones(1)), spoken)
    }

    func testAUnderOnePercentDangerOfARealResultHasNoLineAndIsSpokenOnce() throws {
        // La même journée avec une zone de danger de 1 h (0,4 %) : pas de ligne, mais la description la cite une fois (« < 1 % »).
        let segments: [TimeShareFixtures.Segment] = [
            (.trusted, "p1", 40), (.out, nil, 30), (.trusted, "p2", 30), (.out, nil, 30), (.trusted, "p3", 25),
            (.out, nil, 20), (.trusted, "p4", 20), (.out, nil, 20), (.danger, "r1", 1), (.out, nil, 20)
        ]
        let gym = TimeSharePlace(id: "p4", name: "Gym", type: .trusted, category: nil)
        let places = [Scn.home, Scn.sport, Scn.work, gym, Scn.risk1]
        let real = try XCTUnwrap(Scn.ready(Scn.compute(Scn.timeline(segments), places: places, days: 30)))

        let kinds = real.legendIndices.map { real.rows[$0].kind }
        let spoken = try XCTUnwrap(TimeShareTexts.spokenSummary(real, locale: Locale(identifier: "en_US")))

        XCTAssertFalse(kinds.contains(.danger(1)))
        XCTAssertEqual(spoken.components(separatedBy: TimeShareTexts.dangerZones(1)).count - 1, 1, spoken)
    }

    func testWithOnlyADangerZoneTheRingHasNothingToSayAndIsHidden() {
        let only = result([row(.danger(1), 10, percent: 100)])

        XCTAssertEqual(only.legendIndices, [0], "sa ligne est dans la légende")
        XCTAssertNil(TimeShareTexts.spokenSummary(only), "déjà dite par sa ligne : l'anneau est simplement caché")
    }

    func testTwoPlacesOfExactlyTheSameDurationAreRankedByPlaceIdentifierWhateverTheListOrder() throws {
        // Deux lieux de confiance de 24 h chacun, déclarés dans l'ordre inverse de leurs identifiants.
        let late = TimeSharePlace(id: "pB", name: "B", type: .trusted, category: nil)
        let early = TimeSharePlace(id: "pA", name: "A", type: .trusted, category: nil)
        let facts = Scn.timeline([(.trusted, "pB", 24), (.out, nil, 10), (.trusted, "pA", 24), (.out, nil, 10)])

        for places in [[late, early], [early, late]] {
            let real = try XCTUnwrap(Scn.ready(Scn.compute(facts, places: places)))

            XCTAssertEqual(real.rows.prefix(2).map { $0.kind }, [.place("pA"), .place("pB")])
        }
    }
}
