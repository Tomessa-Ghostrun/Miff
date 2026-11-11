import XCTest
@testable import Miff

/// MODE VOYAGE, les règles pures (VOY-1I) : le jour choisi, la fin à 00:00 du lendemain, le texte du jour, ce qu'un proche voit d'un
/// voyage, la couleur. Mêmes cas qu'Android (`TravelModeTest.kt` : les cas des tests sont le contrat).
final class TravelModeTests: XCTestCase {
    private let paris = TimeZone(identifier: "Europe/Paris") ?? .current
    private let today = TravelDate(year: 2026, month: 11, day: 10)

    /// Un calendrier écrit à part (pas celui du code testé) : le premier instant d'un jour dans un fuseau.
    private func start(of day: TravelDate, in zone: TimeZone) -> Int {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        let parts = DateComponents(year: day.year, month: day.month, day: day.day)
        let date = calendar.date(from: parts) ?? Date(timeIntervalSince1970: 0)
        return Int(date.timeIntervalSince1970 * 1000)
    }

    private func choose(_ day: TravelDate, from base: TravelDate? = nil) -> TravelChoice {
        TravelPlan.choose(day: day, today: base ?? today, zone: paris)
    }

    // MARK: - Le jour choisi

    func testTodayIsTheFirstDayThatCanBeChosenAndTodayPlus89TheLast() {
        guard case .valid = choose(today) else {
            return XCTFail("aujourd'hui doit pouvoir être choisi")
        }
        guard case .valid = choose(today.adding(days: 89)) else {
            return XCTFail("aujourd'hui + 89 doit pouvoir être choisi")
        }
        // Aujourd'hui + 90 finirait à plus de 90 jours : refusé, quelle que soit l'heure.
        XCTAssertEqual(choose(today.adding(days: 90)), .tooLate)
        XCTAssertEqual(choose(today.adding(days: -1)), .tooEarly)
        XCTAssertEqual(choose(today.adding(days: 91)), .tooLate)
    }

    func testTheBoundsOfThePickerAreTheOnesOfTheRuleAndTheDefaultIsInside() {
        XCTAssertEqual(TravelPlan.minDay(today: today), today)
        XCTAssertEqual(TravelPlan.maxDay(today: today), today.adding(days: 89))
        XCTAssertEqual(TravelPlan.defaultDay(today: today), today.adding(days: 7))
        guard case .valid = choose(TravelPlan.defaultDay(today: today)) else {
            return XCTFail("la date proposée doit être valide")
        }
    }

    func testTheTripEndsAtMidnightOfTheDayAfterTheOneChosenSoTheLastDayIsWhole() {
        let day = TravelDate(year: 2026, month: 11, day: 15)
        guard case .valid(let period) = choose(day) else {
            return XCTFail("le 15 doit pouvoir être choisi")
        }

        // Le 16 nov. 2026 00:00 à Paris (UTC+1 en novembre) = le 15 nov. 23:00 UTC.
        XCTAssertEqual(period.untilMillis, start(of: TravelDate(year: 2026, month: 11, day: 16), in: paris))
        XCTAssertEqual(period.untilDay, "2026-11-15")
        // Toute la dernière journée est encore « en voyage » ; le premier instant du lendemain ne l'est plus.
        XCTAssertFalse(period.isOver(nowMillis: period.untilMillis - 1))
        XCTAssertTrue(period.isOver(nowMillis: period.untilMillis))
    }

    func testTheEndFollowsTheDaylightSavingChangeItIsMidnightLocalNotTwentyFourHoursAfterNoon() {
        // Le 26 oct. 2026 : les horloges reculent à Paris (ce jour a 25 heures).
        let day = TravelDate(year: 2026, month: 10, day: 26)
        let base = TravelDate(year: 2026, month: 10, day: 20)
        guard case .valid(let period) = choose(day, from: base) else {
            return XCTFail("le 26 doit pouvoir être choisi")
        }
        XCTAssertEqual(period.untilMillis, start(of: TravelDate(year: 2026, month: 10, day: 27), in: paris))
    }

    func testTheEndIsMidnightOfTheLocalZoneOfThePhone() {
        let tokyo = TimeZone(identifier: "Asia/Tokyo") ?? .current
        let day = TravelDate(year: 2026, month: 11, day: 15)
        let expected = start(of: TravelDate(year: 2026, month: 11, day: 16), in: tokyo)
        XCTAssertEqual(TravelPlan.untilMillis(of: day, zone: tokyo), expected)
        XCTAssertNotEqual(TravelPlan.untilMillis(of: day, zone: tokyo), TravelPlan.untilMillis(of: day, zone: paris))
    }

    // MARK: - Le texte du jour

    func testTheDayTextIsTheIsoDateAndOnlyAnIsoDateIsRead() {
        XCTAssertEqual(TravelDate(year: 2026, month: 11, day: 5).iso, "2026-11-05")
        XCTAssertEqual(TravelDate.parse("2026-11-05"), TravelDate(year: 2026, month: 11, day: 5))
        let notDays: [String?] = [
            nil, "", "2026-1-5", "2026-13-01", "2026-02-30", "05/11/2026", "2026-11-05T00:00", " 2026-11-05",
            "2026-11-05\n", "abcd-ef-gh", "2026/11/05", "2026-11-5 ", "2026-00-10", "2026-11-00"
        ]
        for text in notDays {
            XCTAssertNil(TravelDate.parse(text), "« \(text ?? "nil") » n'est pas un jour")
        }
        XCTAssertNotNil(TravelDate.parse("2028-02-29"), "2028 est bissextile")
        XCTAssertNil(TravelDate.parse("2027-02-29"))
    }

    func testDaysAreAddedOnTheCalendarAcrossMonthsAndYears() {
        let christmas = TravelDate(year: 2026, month: 12, day: 25)
        XCTAssertEqual(christmas.adding(days: 10), TravelDate(year: 2027, month: 1, day: 4))
        let march = TravelDate(year: 2026, month: 3, day: 1)
        XCTAssertEqual(march.adding(days: -1), TravelDate(year: 2026, month: 2, day: 28))
        XCTAssertEqual(today.adding(days: 0), today)
        XCTAssertTrue(today < today.adding(days: 1))
        XCTAssertTrue(TravelDate(year: 2026, month: 12, day: 1) < TravelDate(year: 2027, month: 1, day: 1))
    }

    func testTheYearIsShownOnlyWhenItIsNotTheCurrentOne() {
        let december = TravelDay.of(untilDay: "2026-12-31", untilMillis: nil, today: today, zone: paris)
        let january = TravelDay.of(untilDay: "2027-01-02", untilMillis: nil, today: today, zone: paris)
        let past = TravelDay.of(untilDay: "2025-12-31", untilMillis: nil, today: today, zone: paris)
        XCTAssertEqual(december?.showYear, false)
        XCTAssertEqual(january?.showYear, true)
        XCTAssertEqual(past?.showYear, true)
    }

    func testWithoutAUsableDayTheDayBeforeTheEndIsUsedInTheZoneOfTheReaderAndNothingElseGivesNil() {
        let until = start(of: TravelDate(year: 2026, month: 11, day: 16), in: paris)
        let expected = TravelDate(year: 2026, month: 11, day: 15)
        XCTAssertEqual(TravelDay.of(untilDay: "garbage", untilMillis: until, today: today, zone: paris)?.date, expected)
        XCTAssertEqual(TravelDay.of(untilDay: nil, untilMillis: until, today: today, zone: paris)?.date, expected)
        XCTAssertNil(TravelDay.of(untilDay: "garbage", untilMillis: nil, today: today, zone: paris))
    }

    // MARK: - Ce qu'un proche voit

    private var until: Int {
        start(of: TravelDate(year: 2026, month: 11, day: 16), in: paris)
    }

    private var now: Int {
        start(of: today, in: paris) + 12 * 3_600_000
    }

    private func view(
        presence: PresenceValue? = .travel,
        untilDay: String? = "2026-11-15",
        at moment: Int? = nil
    ) -> TravelView? {
        TravelView.of(
            presence: presence, untilMillis: until, untilDay: untilDay, nowMillis: moment ?? now, zone: paris
        )
    }

    func testBeforeTheEndTheTripIsActiveWithTheDayChosen() {
        guard case .active(let day)? = view() else {
            return XCTFail("le voyage doit être actif")
        }
        XCTAssertEqual(day.date, TravelDate(year: 2026, month: 11, day: 15))
        XCTAssertFalse(day.showYear)
    }

    func testFromTheEndItIsOverNeverAFalseTrip() {
        XCTAssertEqual(view(at: until), .ended)
        XCTAssertEqual(view(at: until + 10 * 24 * 3_600_000), .ended)
        guard case .active? = view(at: until - 1) else {
            return XCTFail("une milliseconde avant la fin : encore en voyage")
        }
    }

    func testWhateverIsNotATripIsNil() {
        XCTAssertNil(view(presence: .out))
        XCTAssertNil(view(presence: .unknown))
        XCTAssertNil(view(presence: nil))
        // Un voyage sans fin (un client modifié) n'est pas un voyage : neutre, sans étiquette.
        let noEnd = TravelView.of(
            presence: .travel, untilMillis: nil, untilDay: "2026-11-15", nowMillis: now, zone: paris
        )
        XCTAssertNil(noEnd)
    }

    func testATripWhoseDayIsGarbageStillShowsTheDayDerivedFromTheEnd() {
        guard case .active(let day)? = view(untilDay: "not a day") else {
            return XCTFail("le voyage doit rester actif")
        }
        XCTAssertEqual(day.date, TravelDate(year: 2026, month: 11, day: 15))
    }

    // MARK: - La couleur, la valeur écrite, l'ancienne application

    func testATravellerIsAlwaysNeutralBeforeAndAfterTheEnd() {
        let facts = MemberStatusFacts(
            sharingEnabled: true,
            presence: .travel,
            presenceAtMillis: now - 1_000,
            lastUpdateAtMillis: now - 10 * 3_600_000,
            travelUntilMillis: until
        )
        XCTAssertEqual(StatusColor.level(facts, nowMillis: now), .neutral)
        XCTAssertEqual(StatusColor.level(facts, nowMillis: until + 1), .neutral)
        XCTAssertEqual(StatusColor.level(facts, nowMillis: until + 30 * 24 * 3_600_000), .neutral)
        let bare = MemberStatusFacts(
            sharingEnabled: true, presence: .travel, presenceAtMillis: nil, lastUpdateAtMillis: nil
        )
        XCTAssertEqual(StatusColor.level(bare, nowMillis: now), .neutral)
    }

    func testTheWireValueIsTravelAndAnOlderApplicationReadsItAsUnknown() {
        XCTAssertEqual(PresenceValue.travel.rawValue, "travel")
        XCTAssertEqual(PresenceValue.fromWire("travel"), .travel)
        // Une version qui ne connaît pas la valeur lit nil, que la couleur traite en neutre (la même fonction, avec nil).
        let unknown = MemberStatusFacts(
            sharingEnabled: true, presence: nil, presenceAtMillis: now, lastUpdateAtMillis: now
        )
        XCTAssertEqual(StatusColor.level(unknown, nowMillis: now), .neutral)
    }

    func testTheLimitsAreTheOnesOfTheRulesAndOfAndroid() {
        XCTAssertEqual(TravelLimits.maxDays, 90)
        XCTAssertEqual(TravelLimits.defaultDays, 7)
        // 90 jours et 23 heures : sous les 91 jours des règles du serveur.
        XCTAssertEqual(TravelLimits.maxEndAheadMillis, (90 * 24 + 23) * 3_600_000)
        XCTAssertLessThan(TravelLimits.maxEndAheadMillis, 91 * 24 * 3_600_000)
    }

    func testNothingOfATripShowsInADescription() {
        let period = TravelPeriod(untilMillis: until, untilDay: "2026-11-15")
        let local = LocalTravel(untilMillis: until, untilDay: "2026-11-15", sinceMillis: now)
        let texts = [
            "\(period)", "\(local)", "\(today)", "\(TravelRead(period: period, sinceMillis: now))",
            "\(TravelDay(date: today, showYear: false))"
        ]
        for text in texts {
            XCTAssertFalse(text.contains("2026"), text)
            XCTAssertTrue(text.contains("redacted"), text)
        }
    }

    // MARK: - La batterie à côté d'un voyage

    func testNoBatteryLevelIsWrittenNextToATripOrNextToItsUncleanedKeys() {
        var state = LocalState(uid: "uid-1")
        state.miffs[testMiffId] = LocalMiff()
        XCTAssertEqual(TravelStatusRules.battery(80, local: state, uid: "uid-1"), 80)
        XCTAssertNil(TravelStatusRules.battery(nil, local: state, uid: "uid-1"))

        var clearing = state
        clearing.update(testMiffId) { $0.clearTravel = true }
        XCTAssertNil(TravelStatusRules.battery(80, local: clearing, uid: "uid-1"))

        var travelling = state
        travelling.travel = LocalTravel(untilMillis: until, untilDay: "2026-11-15", sinceMillis: now)
        XCTAssertNil(TravelStatusRules.battery(80, local: travelling, uid: "uid-1"))
        // La copie d'un autre compte, ou aucune copie : rien à dire de plus.
        XCTAssertEqual(TravelStatusRules.battery(80, local: travelling, uid: "uid-2"), 80)
        XCTAssertEqual(TravelStatusRules.battery(80, local: nil, uid: "uid-1"), 80)
        XCTAssertEqual(TravelStatusRules.battery(80, local: travelling, uid: nil), 80)
    }

    // MARK: - La copie locale

    func testALocalCopyWrittenBeforeTheTripsStillReadsAndTheTripSurvivesTheDisk() throws {
        var state = LocalState(uid: "uid-1")
        state.miffs[testMiffId] = LocalMiff()
        let plain = try JSONEncoder().encode(state)
        let text = String(data: plain, encoding: .utf8) ?? ""
        // Sans voyage, aucune des deux clés n'est écrite : une copie d'avant ce chantier se relit sans elles.
        XCTAssertFalse(text.contains("travel"))
        XCTAssertFalse(text.contains("clearTravelFlag"))
        XCTAssertEqual(try JSONDecoder().decode(LocalState.self, from: plain), state)

        var trip = state
        var travel = LocalTravel(untilMillis: until, untilDay: "2026-11-15", sinceMillis: now)
        travel.startedIn = [testMiffId]
        trip.travel = travel
        trip.update(testMiffId) { $0.clearTravel = true }
        let kept = try JSONDecoder().decode(LocalState.self, from: JSONEncoder().encode(trip))
        XCTAssertEqual(kept, trip)
        XCTAssertEqual(kept.travel?.period, TravelPeriod(untilMillis: until, untilDay: "2026-11-15"))
        XCTAssertEqual(kept.miffs[testMiffId]?.clearTravel, true)
    }
}
