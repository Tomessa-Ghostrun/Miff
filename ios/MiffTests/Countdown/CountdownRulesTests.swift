import XCTest
@testable import Miff

/// Les règles d'affichage du compte à rebours (ticket `compte-a-rebours-orange`, §2.1 à §2.5 et §3), avec une horloge injectée : la fonction est
/// pure. Les vecteurs de §2.3 sont le contrat commun avec Android (`CountdownRulesTest`).
final class CountdownRulesTests: XCTestCase {
    private let now = 1_794_063_600_000
    private let minute = 60 * 1000
    private let window = StatusThresholds.orangeAfterMillis

    private func facts(
        _ presence: PresenceValue? = .out, presenceAt: Int? = nil, lastUpdateAt: Int? = nil
    ) -> MyStatusFacts {
        MyStatusFacts(
            presence: presence, presenceAtMillis: presenceAt, lastUpdateAtMillis: lastUpdateAt, hasPhoto: false
        )
    }

    /// Dehors, vert, avec `remaining` millisecondes avant l'échéance.
    private func out(remaining: Int) -> MyStatusFacts {
        facts(.out, presenceAt: now - (window - remaining))
    }

    private func display(at moment: Int, _ status: MyStatusFacts) -> CountdownDisplay? {
        if case .visible(let value) = CountdownRules.state(status, sharingStopped: false, nowMillis: moment) {
            return value
        }
        return nil
    }

    private func display(remaining: Int) -> CountdownDisplay? {
        display(at: now, out(remaining: remaining))
    }

    // MARK: - La constante unique

    func testTheWindowIsTheExistingGreenThresholdNeverACopy() {
        XCTAssertEqual(CountdownRules.windowMillis, StatusThresholds.orangeAfterMillis)
    }

    // MARK: - Les vecteurs communs de §2.3 : durée, fraction

    private struct Vector {
        let remainingSeconds: Int
        let duration: CountdownDuration
        let fraction: Double
    }

    private var vectors: [Vector] {
        [
            Vector(remainingSeconds: 7200, duration: .hours(2), fraction: 1.000),
            Vector(remainingSeconds: 7199, duration: .hours(2), fraction: 1.000),
            Vector(remainingSeconds: 7140, duration: .hoursMinutes(hours: 1, minutes: 59), fraction: 0.992),
            Vector(remainingSeconds: 4320, duration: .hoursMinutes(hours: 1, minutes: 12), fraction: 0.600),
            Vector(remainingSeconds: 3601, duration: .hoursMinutes(hours: 1, minutes: 1), fraction: 0.508),
            Vector(remainingSeconds: 3600, duration: .hours(1), fraction: 0.500),
            Vector(remainingSeconds: 3541, duration: .hours(1), fraction: 0.500),
            Vector(remainingSeconds: 3540, duration: .minutes(59), fraction: 0.492),
            Vector(remainingSeconds: 120, duration: .minutes(2), fraction: 0.017),
            Vector(remainingSeconds: 61, duration: .minutes(2), fraction: 0.017),
            Vector(remainingSeconds: 60, duration: .lessThanMinute, fraction: 0.008),
            Vector(remainingSeconds: 1, duration: .lessThanMinute, fraction: 0.008)
        ]
    }

    func testTheSharedVectorsGiveTheSameDurationAndBorderFraction() throws {
        for vector in vectors {
            let label = "r = \(vector.remainingSeconds) s"
            let shown = try XCTUnwrap(display(remaining: vector.remainingSeconds * 1000), label)
            XCTAssertEqual(shown.duration, vector.duration, label)
            XCTAssertEqual(shown.fraction, vector.fraction, accuracy: 0.0005, label)
        }
    }

    func testZeroOrLessIsHiddenNeverZeroMinutesNeverNegative() {
        XCTAssertNil(display(remaining: 0))
        XCTAssertNil(display(remaining: -1))
        XCTAssertNil(display(remaining: -window))
    }

    func testTheDurationIsRoundedUpToTheNextMinute() {
        XCTAssertEqual(CountdownRules.displayedMinutes(remainingMillis: 1), 1)
        XCTAssertEqual(CountdownRules.displayedMinutes(remainingMillis: minute), 1)
        XCTAssertEqual(CountdownRules.displayedMinutes(remainingMillis: minute + 1), 2)
        XCTAssertEqual(CountdownRules.displayedMinutes(remainingMillis: window), 120)
    }

    // MARK: - Quand l'encart s'affiche (C1 à C8)

    func testOutsideAndGreenWithAKnownReferenceIsVisibleWithTheRightRemainingTime() throws {
        let shown = try XCTUnwrap(display(remaining: 72 * minute))
        XCTAssertEqual(shown.remainingMillis, 72 * minute)
        XCTAssertEqual(shown.duration, .hoursMinutes(hours: 1, minutes: 12))
    }

    func testInsideATrustedPlaceIsHidden() {
        let inside = facts(.inTrusted, presenceAt: now - 10 * minute)
        XCTAssertEqual(CountdownRules.state(inside, sharingStopped: false, nowMillis: now), .hidden)
    }

    func testNeverPassedThroughAPlaceOrNoPresenceIsHidden() {
        XCTAssertEqual(CountdownRules.state(facts(.unknown), sharingStopped: false, nowMillis: now), .hidden)
        let noPresence = facts(nil, presenceAt: now - minute)
        XCTAssertEqual(CountdownRules.state(noPresence, sharingStopped: false, nowMillis: now), .hidden)
    }

    func testOutsideWithNoReferenceAtAllIsHidden() {
        XCTAssertEqual(CountdownRules.state(facts(.out), sharingStopped: false, nowMillis: now), .hidden)
    }

    func testOrangeAndRedAreHiddenWithoutException() {
        let orange = facts(.out, presenceAt: now - window - 1)
        let red = facts(.out, presenceAt: now - StatusThresholds.redAfterMillis - 1)
        XCTAssertEqual(CountdownRules.state(orange, sharingStopped: false, nowMillis: now), .hidden)
        XCTAssertEqual(CountdownRules.state(red, sharingStopped: false, nowMillis: now), .hidden)
    }

    func testTravelModeIsHidden() {
        let travelling = facts(.travel, presenceAt: now - 10 * minute)
        XCTAssertEqual(CountdownRules.state(travelling, sharingStopped: false, nowMillis: now), .hidden)
    }

    func testStoppedSharingIsHiddenEvenWithGreenFacts() {
        let status = out(remaining: 90 * minute)
        XCTAssertEqual(CountdownRules.state(status, sharingStopped: true, nowMillis: now), .hidden)
    }

    func testDangerZoneIsHiddenEvenWhenGreenForAnHourAfterAnUpdate() {
        let danger = facts(.inRisk, presenceAt: now - 30 * minute, lastUpdateAt: now - 10 * minute)
        // Le cas qui compte : le niveau partagé est vert (une update après l'entrée), mais ce n'est pas « dehors ».
        XCTAssertEqual(UpdatePrompt.level(danger, sharing: true, nowMillis: now), .green)
        XCTAssertEqual(CountdownRules.state(danger, sharingStopped: false, nowMillis: now), .hidden)
    }

    func testStatusNotKnownYetIsHiddenWithoutAFlash() {
        XCTAssertEqual(CountdownRules.state(nil, sharingStopped: false, nowMillis: now), .hidden)
        XCTAssertEqual(CountdownRules.state(nil, sharingStopped: true, nowMillis: now), .hidden)
    }

    // MARK: - Le calcul est celui du niveau de la carte « moi » (CD-8, CD-10)

    func testItIsVisibleExactlyWhileTheSharedLevelIsGreenAndHiddenAtTheDeadline() {
        let status = facts(.out, presenceAt: now - 100 * minute)
        let deadline = now - 100 * minute + window
        for offset in [-minute, -1000, -1] {
            let at = deadline + offset
            XCTAssertTrue(display(at: at, status) != nil, "E\(offset)")
            XCTAssertEqual(UpdatePrompt.level(status, sharing: true, nowMillis: at), .green)
        }
        // À E pile le niveau partagé est encore vert (« plus de 2 h » est strict) : l'encart, lui, n'a plus rien à compter. Écart d'1 ms
        // documenté ; à E + 1 ms le niveau est orange et l'encart est caché.
        XCTAssertNil(display(at: deadline, status))
        XCTAssertNil(display(at: deadline + 1, status))
        XCTAssertEqual(UpdatePrompt.level(status, sharing: true, nowMillis: deadline + 1), .orange)
    }

    func testNeverVisibleWhenTheSharedLevelIsNotGreen() {
        // Toutes les 7 minutes sur 5 h : « visible » implique « vert », jamais en orange ou en rouge.
        let start = now - 5 * 60 * minute
        let status = facts(.out, presenceAt: start)
        for step in stride(from: 0, through: 5 * 60, by: 7) {
            let at = start + step * minute
            if display(at: at, status) != nil {
                XCTAssertEqual(UpdatePrompt.level(status, sharing: true, nowMillis: at), .green, "minute \(step)")
            }
        }
    }

    // MARK: - Le repère et le reset (CD-9, CD-7)

    func testTheReferenceIsTheMostRecentOfDepartureAndLastUpdate() throws {
        let afterUpdate = facts(.out, presenceAt: now - 90 * minute, lastUpdateAt: now - 20 * minute)
        let shown = try XCTUnwrap(display(at: now, afterUpdate))
        XCTAssertEqual(shown.remainingMillis, window - 20 * minute)
        let staleUpdate = facts(.out, presenceAt: now - 30 * minute, lastUpdateAt: now - 90 * minute)
        let other = try XCTUnwrap(display(at: now, staleUpdate))
        XCTAssertEqual(other.remainingMillis, window - 30 * minute, "un départ plus récent que l'update est le repère")
    }

    func testAnUpdateResetsToAFullWindowAndShowsTwoHours() throws {
        let before = facts(.out, presenceAt: now - 100 * minute)
        XCTAssertEqual(try XCTUnwrap(display(at: now, before)).remainingMillis, 20 * minute)
        let after = facts(.out, presenceAt: now - 100 * minute, lastUpdateAt: now)
        let reset = try XCTUnwrap(display(at: now, after))
        XCTAssertEqual(reset.duration, .hours(2))
        XCTAssertEqual(reset.fraction, 1, accuracy: 0.0001)
        XCTAssertEqual(reset.remainingMillis, window)
    }

    func testAnUpdateFromOrangeOrRedBringsTheCardBackAtTwoHours() throws {
        let orange = facts(.out, presenceAt: now - 3 * 60 * minute)
        let red = facts(.out, presenceAt: now - 6 * 60 * minute)
        for old in [orange, red] {
            XCTAssertNil(display(at: now, old))
            let updated = facts(.out, presenceAt: old.presenceAtMillis, lastUpdateAt: now)
            let shown = try XCTUnwrap(display(at: now, updated), "l'encart doit réapparaître")
            XCTAssertEqual(shown.duration, .hours(2))
        }
    }

    func testAReferenceInTheFutureIsCappedAtTwoHoursNeverMore() throws {
        let clockSetBack = facts(.out, presenceAt: now + 5 * 60 * minute)
        let shown = try XCTUnwrap(display(at: now, clockSetBack))
        XCTAssertEqual(shown.remainingMillis, window)
        XCTAssertEqual(shown.duration, .hours(2))
        XCTAssertEqual(shown.fraction, 1, accuracy: 0.0001)
    }

    // MARK: - Le rafraîchissement : une fois par minute, aligné sur l'échéance (CD-18)

    func testTheNextChangeIsTheNextMinuteBoundaryOfTheDeadline() throws {
        let deadline = now + 72 * minute - 17_000
        let status = facts(.out, presenceAt: deadline - window)
        let shown = try XCTUnwrap(display(at: now, status))
        // 71 min 43 s restent : « 1 h 12 min » ; la minute suivante tombe à E - 71 min, dans 43 s.
        XCTAssertEqual(shown.minutes, 72)
        XCTAssertEqual(shown.nextChangeMillis, deadline - 71 * minute)
        XCTAssertEqual(shown.nextChangeMillis - now, 43_000)
        let later = try XCTUnwrap(display(at: shown.nextChangeMillis, status))
        XCTAssertEqual(later.minutes, 71, "à cet instant la minute affichée est passée à 71")
    }

    func testTheLastRefreshIsTheDeadlineWhereTheCardDisappears() throws {
        let deadline = now + 40_000
        let status = facts(.out, presenceAt: deadline - window)
        let shown = try XCTUnwrap(display(at: now, status))
        XCTAssertEqual(shown.minutes, 1)
        XCTAssertEqual(shown.nextChangeMillis, deadline)
        XCTAssertNil(display(at: deadline, status))
    }

    func testAtMostOneChangePerMinuteAcrossTheWholeWindow() {
        let status = facts(.out, presenceAt: now - 13_000)
        var cursor = now
        var changes = 0
        while let shown = display(at: cursor, status) {
            XCTAssertGreaterThan(shown.nextChangeMillis, cursor)
            XCTAssertLessThanOrEqual(shown.nextChangeMillis - cursor, minute)
            cursor = shown.nextChangeMillis
            changes += 1
            XCTAssertLessThan(changes, 200, "boucle")
        }
        XCTAssertEqual(changes, 120, "120 minutes affichées, une mise à jour par minute")
    }

    // MARK: - Hors connexion : seuls l'heure de l'appareil et les instants locaux comptent (CD-6)

    func testTheCalculationNeedsNothingButTheLocalFactsAndTheClock() {
        let status = out(remaining: 50 * minute)
        let first = CountdownRules.state(status, sharingStopped: false, nowMillis: now)
        XCTAssertEqual(first, CountdownRules.state(status, sharingStopped: false, nowMillis: now))
        XCTAssertTrue(first.isVisible)
    }

    // MARK: - Jamais pour un autre compte (CD-5)

    func testTheFeedGivesOnlyMyFactsSoAnotherMembersStatusNeverCountsDown() {
        let others = feedStatus(.out, placeId: nil, departedAgo: feedMinutes(30))
        let content = buildFeed(
            feedInput(members: [feedMember("lea"), feedMember("me")], statuses: ["lea": others])
        )
        XCTAssertNil(content.myFacts, "seul le statut de ma propre fiche nourrit l'Update, donc le compte à rebours")
        XCTAssertEqual(CountdownRules.state(content.myFacts, sharingStopped: false, nowMillis: feedNow), .hidden)
    }

    func testTheFeedGivesMyOwnFactsEvenWhenTheOtherMemberIsAlsoOutside() {
        let mine = feedStatus(.out, placeId: nil, departedAgo: feedMinutes(48))
        let others = feedStatus(.out, placeId: nil, departedAgo: feedMinutes(10))
        let content = buildFeed(
            feedInput(members: [feedMember("lea"), feedMember("me")], statuses: ["me": mine, "lea": others])
        )
        let state = CountdownRules.state(content.myFacts, sharingStopped: false, nowMillis: feedNow)
        guard case .visible(let shown) = state else {
            return XCTFail("visible attendu pour moi")
        }
        let expected = StatusThresholds.orangeAfterMillis - feedMinutes(48)
        XCTAssertEqual(shown.remainingMillis, expected, "ma valeur, pas celle de Léa")
    }

    func testTheDescriptionNeverShowsAValue() {
        let shown = CountdownDisplay(remainingMillis: 1, minutes: 1, nextChangeMillis: 1)
        XCTAssertEqual("\(shown)", "CountdownDisplay(redacted)")
    }
}
