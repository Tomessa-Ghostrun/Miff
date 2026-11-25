import XCTest
@testable import Miff

/// Le calcul de la répartition du temps (TIME-3) : les cas T1 à T23 du contrat commun iOS / Android
/// (`docs/tickets/profil-repartition-temps.md` §6.3), fenêtre de 7 jours = 168 h sauf mention. Mêmes valeurs qu'Android.
final class TimeShareCalculatorTests: XCTestCase {
    private typealias Scn = TimeShareFixtures
    private let hour = TimeShareFixtures.hour

    private func totals(_ facts: [PresenceFact], places: [TimeSharePlace], days: Int = 7) -> TimeShareTotals {
        TimeShareCalculator.measure(facts: facts, places: places, windowDays: days, nowMillis: Scn.now)
    }

    private func row(_ result: TimeShareResult, _ kind: TimeShareRowKind) -> TimeShareRow? {
        result.rows.first { $0.kind == kind }
    }

    // MARK: - T1 à T6

    func testT1HomeSportAndElsewhereRoundByTheLargestRemainderAndTheTieGoesToTheBiggestRealTime() throws {
        // Maison 84 h (deux séjours), Sport 21 h, dehors 63 h.
        let facts = Scn.timeline([
            (.trusted, "p1", 42), (.trusted, "p2", 21), (.out, nil, 21), (.trusted, "p1", 42), (.out, nil, 42)
        ])

        let result = try XCTUnwrap(Scn.ready(Scn.compute(facts, places: [Scn.home, Scn.sport])))

        XCTAssertEqual(result.rows.map { $0.percent }, [50, 12, 38])
        XCTAssertEqual(result.rows.map { $0.kind }, [.place("p1"), .place("p2"), .elsewhere])
        XCTAssertEqual(result.trustedPercent, 62)
        XCTAssertEqual(result.rows.map { $0.percent }.reduce(0, +), 100)
    }

    func testT2AStateThatStartedBeforeTheWindowIsCutAtTheStartOfTheWindow() {
        // « Maison » depuis 10 h AVANT la fenêtre, fait suivant 20 h après le début de la fenêtre : 20 h comptées.
        let facts = [Scn.fact(.trusted, "p1", hoursAgo: 178), Scn.fact(.out, nil, hoursAgo: 148)]

        let tally = totals(facts, places: [Scn.home])

        XCTAssertEqual(tally.trusted["p1"], 20 * hour)
    }

    func testT3AnOpenStateCountsUntilNow() {
        let tally = totals([Scn.fact(.trusted, "p1", hoursAgo: 5)], places: [Scn.home])

        XCTAssertEqual(tally.trusted["p1"], 5 * hour)
    }

    func testT4AnOpenStateIsCountedFortyEightHoursAtMost() {
        let tally = totals([Scn.fact(.trusted, "p1", hoursAgo: 60)], places: [Scn.home])

        XCTAssertEqual(tally.trusted["p1"], 48 * hour)
        XCTAssertEqual(tally.measured, 48 * hour, "les 12 h de plus sont non mesurées, exclues")
    }

    func testT5TwoIdenticalFactsInARowAreOne() {
        let facts = [Scn.fact(.trusted, "p1", hoursAgo: 100), Scn.fact(.trusted, "p1", hoursAgo: 60)]

        let tally = totals(facts, places: [Scn.home])

        // Un seul fait de 100 h : plafonné à 48 h (deux faits auraient donné 88 h).
        XCTAssertEqual(tally.trusted["p1"], 48 * hour)
    }

    func testT6AFactDatedBeforeThePreviousOneIsBroughtToIt() {
        let facts = [Scn.fact(.trusted, "p1", hoursAgo: 50), Scn.fact(.out, nil, hoursAgo: 60)]

        let tally = totals(facts, places: [Scn.home])

        XCTAssertEqual(tally.trusted["p1"] ?? 0, 0, "durée 0, jamais négative")
        XCTAssertEqual(tally.elsewhere, 48 * hour)
        XCTAssertGreaterThanOrEqual(tally.measured, 0)
    }

    // MARK: - T7 à T12

    func testT7TwoTrustedPlacesDoNotCountTwice() {
        // A entré à 0 h, B à 2 h, sortie de B à 3 h (la présence repasse sur A), sortie de A à 6 h.
        let facts = Scn.timeline([(.trusted, "p1", 2), (.trusted, "p2", 1), (.trusted, "p1", 3), (.out, nil, 34)])

        let tally = totals(facts, places: [Scn.home, Scn.sport])

        XCTAssertEqual(tally.trusted["p1"], 5 * hour)
        XCTAssertEqual(tally.trusted["p2"], 1 * hour)
        XCTAssertEqual(tally.measured, 40 * hour, "rien n'est compté deux fois")
    }

    func testT8UnknownTimeIsNotMeasuredAndLeavesTheDenominator() throws {
        let facts = Scn.timeline([
            (.trusted, "p1", 40), (.out, nil, 40), (.trusted, "p1", 20), (.unknown, nil, 30), (.trusted, "p1", 38)
        ])

        let tally = totals(facts, places: [Scn.home])
        let result = try XCTUnwrap(Scn.ready(Scn.compute(facts, places: [Scn.home])))

        XCTAssertEqual(tally.measured, 138 * hour)
        XCTAssertEqual(tally.elsewhere, 40 * hour, "l'inconnu n'est jamais compté en « Ailleurs »")
        XCTAssertEqual(result.rows.map { $0.percent }, [71, 29])
    }

    func testT9UnderThreeDaysOfHistoryThereIsNoChart() {
        // 20 h mesurées dans un journal tout neuf : « Ton graphique arrive dans 3 jours ».
        let outcome = Scn.compute(Scn.timeline([(.trusted, "p1", 20)], aged: false), places: [Scn.home])

        XCTAssertEqual(outcome, .notEnough(daysLeft: 3))
    }

    func testT9bExactlyThreeDaysOfHistoryIsEnough() {
        // Le premier enregistrement a exactement 3 jours (72 h) : le graphique s'affiche.
        let segments: [TimeShareFixtures.Segment] = [(.trusted, "p1", 24), (.out, nil, 24), (.trusted, "p1", 24)]
        let facts = Scn.timeline(segments, aged: false)

        XCTAssertNotNil(Scn.ready(Scn.compute(facts, places: [Scn.home])))
    }

    func testT9cOneHourShortOfThreeDaysIsStillNotEnough() {
        let segments: [TimeShareFixtures.Segment] = [(.trusted, "p1", 24), (.out, nil, 24), (.trusted, "p1", 23)]
        let facts = Scn.timeline(segments, aged: false)

        XCTAssertEqual(Scn.compute(facts, places: [Scn.home]), .notEnough(daysLeft: 1))
    }

    func testT9dThreeDaysOfHistoryButNothingMeasuredInTheWindowSaysWhatIsMissingInsteadOfACountdown() {
        // Rien de mesuré (jamais dans un lieu, voyage, suivi arrêté) : pas de graphique, jamais « dans 0 jour » ni « dans 1 jour » sans fin.
        let facts = Scn.timeline([(.unknown, nil, 100)])

        XCTAssertEqual(Scn.compute(facts, places: [Scn.home]), .noTimeMeasured)
    }

    func testTheThresholdIsOneSingleConstantOfThreeDays() {
        XCTAssertEqual(TimeShareLimits.minimumHistoryDays, 3)
        XCTAssertEqual(TimeShareLimits.minimumHistoryMillis, 3 * TimeShareLimits.dayMillis)
    }

    // MARK: - « Ton graphique arrive dans X jours »

    func testTheDaysLeftFollowTheWholeDaysSinceTheFirstRecord() {
        func left(_ days: Int, _ extraHours: Int = 0, _ extraMinutes: Int = 0) -> Int {
            let elapsed = days * TimeShareLimits.dayMillis + extraHours * hour + extraMinutes * 60_000
            return TimeShareLimits.daysLeft(elapsedMillis: elapsed)
        }

        XCTAssertEqual(left(0), 3, "aucun jour écoulé")
        XCTAssertEqual(left(0, 23, 59), 3, "23 h 59 : toujours 0 jour entier")
        XCTAssertEqual(left(1), 2)
        XCTAssertEqual(left(1, 12), 2)
        XCTAssertEqual(left(2), 1)
        XCTAssertEqual(left(2, 2), 1, "2 jours et 2 h : « dans 1 jour »")
        XCTAssertEqual(left(2, 23, 59), 1, "jamais sous 1 tant que le graphique n'est pas affichable")
        XCTAssertEqual(left(3), 1, "jamais sous 1")
        XCTAssertEqual(left(9), 1, "jamais sous 1")
    }

    func testTheCounterGoesDownOneDayAtATimeThenTheChartShowsUp() {
        // Un premier enregistrement, puis le même journal relu 12 h, 1 jour, 2 jours, 3 jours plus tard : 3, 2, 1, puis le graphique.
        let places = [Scn.home]
        let first = Scn.now
        let facts = [
            PresenceFact(miffId: "m1", state: .trusted, placeId: "p1", atMillis: first),
            PresenceFact(miffId: "m1", state: .out, placeId: nil, atMillis: first + 6 * hour)
        ]
        func outcome(afterHours: Int) -> TimeShareOutcome {
            let now = first + afterHours * hour
            return TimeShareCalculator.compute(facts: facts, places: places, windowDays: 7, nowMillis: now)
        }

        XCTAssertEqual(outcome(afterHours: 12), .notEnough(daysLeft: 3))
        XCTAssertEqual(outcome(afterHours: 36), .notEnough(daysLeft: 2))
        XCTAssertEqual(outcome(afterHours: 60), .notEnough(daysLeft: 1))
        XCTAssertNotNil(Scn.ready(outcome(afterHours: 72)))
        XCTAssertNotNil(Scn.ready(outcome(afterHours: 84)))
    }

    func testTheThresholdIsCountedInElapsedMillisecondsSoADaylightSavingChangeDoesNotMoveIt() {
        // Le seuil est une durée écoulée (72 h exactes), jamais « 3 jours de calendrier » : un changement d'heure ou de fuseau dans la
        // période ne le décale pas (le calcul n'utilise ni `Calendar` ni fuseau). 72 h exactes : graphique ; 71 h : « dans 1 jour ».
        let places = [Scn.home]
        func outcome(afterHours: Int) -> TimeShareOutcome {
            let facts = [
                PresenceFact(miffId: "m1", state: .trusted, placeId: "p1", atMillis: Scn.now - afterHours * hour),
                PresenceFact(miffId: "m1", state: .out, placeId: nil, atMillis: Scn.now - (afterHours - 24) * hour)
            ]
            return Scn.compute(facts, places: places)
        }

        XCTAssertNotNil(Scn.ready(outcome(afterHours: 72)))
        XCTAssertEqual(outcome(afterHours: 71), .notEnough(daysLeft: 1))
        XCTAssertEqual(TimeShareLimits.daysLeft(elapsedMillis: 72 * hour - 1), 1)
    }

    func testAnEmptyJournalAndAFutureFirstRecordSayTheMaximum() {
        XCTAssertEqual(Scn.compute([], places: [Scn.home]), .notEnough(daysLeft: 3))
        XCTAssertEqual(TimeShareLimits.daysLeft(elapsedMillis: -5 * hour), 3)
        let future = [Scn.fact(.trusted, "p1", hoursAgo: -3)]
        XCTAssertEqual(Scn.compute(future, places: [Scn.home]), .notEnough(daysLeft: 3))
    }

    func testTheDaysLeftOfTheOutcomeCountFromTheFirstFactOfTheJournal() {
        let places = [Scn.home]
        let oneDay = [Scn.fact(.trusted, "p1", hoursAgo: 24)]
        let twoDaysTwoHours = [Scn.fact(.trusted, "p1", hoursAgo: 50), Scn.fact(.out, nil, hoursAgo: 20)]

        XCTAssertEqual(Scn.compute(oneDay, places: places), .notEnough(daysLeft: 2))
        XCTAssertEqual(Scn.compute(twoDaysTwoHours, places: places), .notEnough(daysLeft: 1))
    }

    func testThePastHistoryAlreadyMeasuredCountsAtOnce() throws {
        // Un journal qui remonte déjà à 5 jours (mesuré avec l'ancien seuil de 7 jours) : le graphique s'affiche tout de suite.
        let facts = Scn.timeline([(.trusted, "p1", 60), (.out, nil, 60)], aged: false)

        XCTAssertNotNil(Scn.ready(Scn.compute(facts, places: [Scn.home])))
    }

    func testT10SevenPlacesGiveFiveNamedAndTwoOthers() throws {
        let places = (1...7).map { TimeSharePlace(id: "p\($0)", name: "Lieu \($0)", type: .trusted, category: nil) }
        var segments: [TimeShareFixtures.Segment] = []
        for index in 1...7 {
            segments.append((.trusted, "p\(index)", Double(15 - index)))
            segments.append((.out, nil, 1))
        }

        let result = try XCTUnwrap(Scn.ready(Scn.compute(Scn.timeline(segments), places: places)))

        let kinds = result.rows.map { $0.kind }
        XCTAssertEqual(kinds.count, 7)
        XCTAssertEqual(Array(kinds.prefix(5)), (1...5).map { TimeShareRowKind.place("p\($0)") })
        XCTAssertEqual(kinds[5], .otherPlaces(2))
        XCTAssertEqual(kinds[6], .elsewhere)
        XCTAssertEqual(result.rows.map { $0.percent }.reduce(0, +), 100)
    }

    func testT11ADeletedPlaceIsNotMeasuredAndNeverBecomesElsewhere() {
        let facts = Scn.timeline([(.out, nil, 30), (.trusted, "gone", 10), (.out, nil, 10)])

        let tally = totals(facts, places: [Scn.home])

        XCTAssertEqual(tally.trusted, [:])
        XCTAssertEqual(tally.elsewhere, 40 * hour, "ces 10 h ne sont pas du temps « Ailleurs »")
    }

    func testT12AWindowAcrossADaylightSavingChangeIsExactlyOneHundredSixtyEightHours() {
        // 2026-03-30 12:00 à Paris : l'heure d'été a commencé le 29 mars, la semaine ne fait pourtant que 168 h écoulées.
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Paris") ?? .current
        let parts = DateComponents(year: 2026, month: 3, day: 30, hour: 12)
        let now = Int((calendar.date(from: parts) ?? Date()).timeIntervalSince1970 * 1000)
        var facts: [PresenceFact] = []
        for index in 0..<7 {
            let state: PresenceState = index.isMultiple(of: 2) ? .trusted : .out
            let at = now - (168 - index * 24) * hour
            let place = state == .trusted ? "p1" : nil
            facts.append(PresenceFact(miffId: "m1", state: state, placeId: place, atMillis: at))
        }

        let tally = TimeShareCalculator.measure(facts: facts, places: [Scn.home], windowDays: 7, nowMillis: now)

        XCTAssertEqual(tally.measured, 168 * hour)
        XCTAssertEqual(tally.trusted["p1"], 96 * hour)
    }

    // MARK: - T13 à T16

    func testT13AShareUnderOnePercentIsTinyAndTheOthersTotalOneHundred() throws {
        // Fenêtre de 30 jours : Maison 200 h, Sport 1,2 h (0,3 %), dehors 200 h.
        let segments: [TimeShareFixtures.Segment] = [
            (.trusted, "p1", 40), (.out, nil, 40), (.trusted, "p1", 40), (.out, nil, 40), (.trusted, "p2", 1.2),
            (.out, nil, 40), (.trusted, "p1", 40), (.out, nil, 40), (.trusted, "p1", 40), (.out, nil, 40)
        ]

        let outcome = Scn.compute(Scn.timeline(segments), places: [Scn.home, Scn.sport], days: 30)
        let result = try XCTUnwrap(Scn.ready(outcome))

        let sport = try XCTUnwrap(row(result, .place("p2")))
        XCTAssertTrue(sport.isUnderOnePercent)
        XCTAssertEqual(sport.percent, 0)
        XCTAssertEqual(result.rows.map { $0.percent }.reduce(0, +), 100)
        XCTAssertEqual(TimeShareTexts.percent(of: sport), L10n.string("profile_timeshare_percent_tiny"))
    }

    func testT14NeverInAPlaceIsZeroInTheCentreAndMostlyElsewhere() throws {
        // « Dehors » de 100 h en trois séjours séparés par un temps « inconnue » (deux faits « dehors » de suite n'en font qu'un, plafonné à 48 h).
        let segments: [TimeShareFixtures.Segment] = [
            (.out, nil, 40), (.unknown, nil, 1), (.out, nil, 40), (.unknown, nil, 1), (.out, nil, 20)
        ]
        let facts = Scn.timeline(segments)

        let result = try XCTUnwrap(Scn.ready(Scn.compute(facts, places: [Scn.home, Scn.sport])))

        XCTAssertEqual(result.rows.map { $0.kind }, [.elsewhere])
        XCTAssertEqual(result.rows.first?.percent, 100)
        XCTAssertEqual(result.trustedPercent, 0)
    }

    func testT15AFreePlanWithOnePlaceHasTwoParts() throws {
        let facts = Scn.timeline([(.trusted, "p1", 15), (.out, nil, 35), (.trusted, "p1", 15), (.out, nil, 35)])

        let result = try XCTUnwrap(Scn.ready(Scn.compute(facts, places: [Scn.home])))

        XCTAssertEqual(result.rows.map { $0.percent }, [30, 70])
        XCTAssertEqual(result.rows.map { $0.kind }, [.place("p1"), .elsewhere])
    }

    func testT16AFactInTheFutureIsBroughtToNow() {
        let facts = [Scn.fact(.out, nil, hoursAgo: 40), Scn.fact(.trusted, "p1", hoursAgo: -5)]

        let tally = totals(facts, places: [Scn.home])

        XCTAssertEqual(tally.elsewhere, 40 * hour)
        XCTAssertEqual(tally.trusted["p1"] ?? 0, 0)
    }

    // MARK: - T17 à T23

    func testT17TheTrustedZoneWinsOverTheDangerZone() throws {
        // Les faits du moteur : A (confiance) à 0 h ; l'entrée et la sortie de R (danger) ne changent rien ; sortie de A à 6 h.
        let facts = Scn.timeline([(.trusted, "p1", 6), (.out, nil, 34)])

        let result = try XCTUnwrap(Scn.ready(Scn.compute(facts, places: [Scn.home, Scn.risk1])))

        XCTAssertEqual(totals(facts, places: [Scn.home, Scn.risk1]).trusted["p1"], 6 * hour)
        XCTAssertNil(row(result, .danger(1)))
        XCTAssertFalse(result.rows.contains { if case .danger = $0.kind { return true } else { return false } })
    }

    func testT18ADangerZoneAloneCountsItsTime() {
        let facts = Scn.timeline([(.out, nil, 30), (.danger, "r1", 2), (.out, nil, 10)])

        let tally = totals(facts, places: [Scn.risk1])

        XCTAssertEqual(tally.danger["r1"], 2 * hour)
    }

    func testT19TwoDangerPlacesAreOneLineWhoseDetailAddsUp() throws {
        let segments: [TimeShareFixtures.Segment] = [
            (.trusted, "p1", 25), (.out, nil, 19), (.trusted, "p1", 25), (.out, nil, 19), (.danger, "r1", 10),
            (.out, nil, 19), (.trusted, "p1", 25), (.out, nil, 19), (.danger, "r2", 14), (.trusted, "p1", 25)
        ]

        let result = try XCTUnwrap(
            Scn.ready(Scn.compute(Scn.timeline(segments), places: [Scn.home, Scn.risk1, Scn.risk2], days: 14))
        )

        XCTAssertEqual(result.rows.map { $0.percent }, [50, 12, 38])
        XCTAssertEqual(result.rows.map { $0.kind }, [.place("p1"), .danger(2), .elsewhere])
        XCTAssertEqual(result.dangerDetails.map { $0.placeId }, ["r2", "r1"])
        XCTAssertEqual(result.dangerDetails.map { $0.percent }, [7, 5])
        XCTAssertEqual(result.trustedPercent, 50)
        XCTAssertEqual(result.rows.map { $0.percent }.reduce(0, +), 100)
    }

    func testT20NoDangerLineWithoutTimeInIt() throws {
        let facts = Scn.timeline([(.trusted, "p1", 20), (.out, nil, 20)])

        let result = try XCTUnwrap(Scn.ready(Scn.compute(facts, places: [Scn.home, Scn.risk1])))

        XCTAssertEqual(result.rows.map { $0.kind }, [.place("p1"), .elsewhere], "jamais une ligne à « 0 % »")
        XCTAssertTrue(result.dangerDetails.isEmpty)
    }

    func testT21TheDangerZoneAsTheBiggestPartIsStillOneLineAndNotInTheCentre() throws {
        let facts = Scn.timeline([
            (.trusted, "p1", 10), (.danger, "r1", 20), (.out, nil, 10), (.danger, "r1", 20), (.out, nil, 10)
        ])

        // Deux faits « danger » du même lieu séparés par « dehors » : 40 h de danger, 10 h de Maison, 20 h dehors.
        let result = try XCTUnwrap(Scn.ready(Scn.compute(facts, places: [Scn.home, Scn.risk1])))

        XCTAssertEqual(result.rows.map { $0.kind }, [.place("p1"), .danger(1), .elsewhere])
        XCTAssertEqual(result.rows.map { $0.millis }.max(), result.rows[1].millis)
        XCTAssertEqual(result.trustedPercent, result.rows[0].percent, "le centre ne compte que les lieux de confiance")
    }

    func testT22APlaceWhoseTypeChangedIsNotMeasuredAnymore() {
        // Le lieu était de confiance quand le fait a été écrit ; il est maintenant une zone de danger.
        let facts = Scn.timeline([(.out, nil, 30), (.trusted, "p1", 10), (.out, nil, 10)])
        let changed = TimeSharePlace(id: "p1", name: "Maison", type: .risk, category: .home)

        let tally = totals(facts, places: [changed])

        XCTAssertEqual(tally.trusted, [:])
        XCTAssertEqual(tally.danger, [:])
        XCTAssertEqual(tally.elsewhere, 40 * hour)
    }

    func testT22bAndTheOtherWayRound() {
        let facts = Scn.timeline([(.out, nil, 30), (.danger, "r1", 10), (.out, nil, 10)])
        let changed = TimeSharePlace(id: "r1", name: "Chantier", type: .trusted, category: nil)

        let tally = totals(facts, places: [changed])

        XCTAssertEqual(tally.danger, [:])
        XCTAssertEqual(tally.trusted, [:])
    }

    func testT23ATripIsNotMeasured() {
        let facts = Scn.timeline([(.out, nil, 20), (.unknown, nil, 60), (.out, nil, 20)])

        let tally = totals(facts, places: [Scn.home])

        XCTAssertEqual(tally.measured, 40 * hour, "les 60 h de voyage sont non mesurées")
    }

    // MARK: - L'encart de synthèse

    func testTheSummaryGivesTheCentrePercentAndNeverNamesTheDangerZone() throws {
        let facts = Scn.timeline([(.trusted, "p1", 30), (.danger, "r1", 10), (.out, nil, 10)])
        let result = try XCTUnwrap(Scn.ready(Scn.compute(facts, places: [Scn.home, Scn.risk1])))

        let text = TimeShareTexts.summary(trustedPercent: result.trustedPercent, locale: Locale(identifier: "en_US"))

        XCTAssertTrue(text.contains("60%"), text)
        XCTAssertFalse(text.localizedCaseInsensitiveContains("danger"))
    }

    func testAnOutFactNeverKeepsAPlaceIdentifier() {
        let fact = PresenceFact(miffId: "m1", state: .out, placeId: "p1", atMillis: 1)
        let unknown = PresenceFact(miffId: "m1", state: .unknown, placeId: "p1", atMillis: 1)
        let inPlace = PresenceFact(miffId: "m1", state: .trusted, placeId: "p1", atMillis: 1)

        XCTAssertNil(fact.placeId)
        XCTAssertNil(unknown.placeId)
        XCTAssertEqual(inPlace.placeId, "p1")
    }

    func testTwoOutFactsWhateverTheirQuitPlaceAreOneInTheCalculation() {
        // Un journal écrit avant la règle : « dehors » du lieu p1, puis « dehors » du lieu p2 : même état, un seul fait (plafonné à 48 h).
        let facts = [Scn.fact(.out, "p1", hoursAgo: 100), Scn.fact(.out, "p2", hoursAgo: 60)]

        XCTAssertEqual(totals(facts, places: [Scn.home]).elsewhere, 48 * hour)
    }
}
