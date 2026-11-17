import XCTest
@testable import Miff

/// « Depuis ta dernière visite » (ticket notifications-p1-derniere-visite.md) : les vecteurs C1-T1 à C1-T20 de la suite de tests COMMUNE
/// (la même table qu'Android, `LastVisitTest.kt`), sur la comparaison pure. Aucune horloge, aucun serveur.
final class LastVisitTests: XCTestCase {
    private typealias Fixture = VisitFixtures
    private let minute = VisitFixtures.minute
    private let hour = VisitFixtures.hour
    private let now = VisitFixtures.now

    private func member(
        _ uid: String,
        _ presence: PresenceValue? = .inTrusted,
        _ place: PlaceKind? = .school,
        presenceAt: Int? = nil,
        lastUpdateAt: Int? = nil,
        silent: Bool = false
    ) -> VisitMember {
        Fixture.member(uid, presence, place, presenceAt: presenceAt, lastUpdateAt: lastUpdateAt, silent: silent)
    }

    // MARK: - C1-T1, T2 : rien à dire

    func testT1NoSeenPictureIsNoLine() {
        XCTAssertTrue(Fixture.compare(nil, Fixture.state([member("t")])).isEmpty)
    }

    func testT2NoChangeIsNoLine() {
        let picture = Fixture.state([member("t"), member("a", .out, .home)])

        XCTAssertTrue(Fixture.compare(picture, picture).isEmpty)
    }

    // MARK: - C1-T3 à T5 : les trois sortes de changement

    func testT3AnArrivalIsOneLineWithTheTimeAndTheCategoryOfThePlace() throws {
        let seen = Fixture.state([member("t", .out, .school, presenceAt: now - 14 * hour)])
        let current = Fixture.state([
            member("t", .inTrusted, .home, presenceAt: now - 2 * hour, lastUpdateAt: now - 2 * hour + minute)
        ])

        let line = try XCTUnwrap(Fixture.compare(seen, current).first)

        XCTAssertEqual(line.kind, .arrival)
        XCTAssertEqual(line.place, .home)
        XCTAssertEqual(line.shownAtMillis, now - 2 * hour)
        XCTAssertEqual(line.uid, "t")
    }

    func testT4ADepartureIsOneLineWithTheTimeOfTheExit() throws {
        let seen = Fixture.state([member("t", .inTrusted, .school, presenceAt: now - 6 * hour)])
        let current = Fixture.state([
            member("t", .out, .school, presenceAt: now - hour, lastUpdateAt: now - 6 * hour)
        ])

        let lines = Fixture.compare(seen, current)

        XCTAssertEqual(lines.count, 1)
        XCTAssertEqual(lines.first?.kind, .departure)
        XCTAssertEqual(lines.first?.place, .school)
        XCTAssertEqual(lines.first?.shownAtMillis, now - hour)
    }

    func testT5AnUpdateIsOneLineWithTheTimeOfTheUpdate() {
        let seen = Fixture.state([member("a", .out, .home, presenceAt: now - 5 * hour)])
        let current = Fixture.state([
            member("a", .out, .home, presenceAt: now - 5 * hour, lastUpdateAt: now - 30 * minute)
        ])

        let lines = Fixture.compare(seen, current)

        XCTAssertEqual(lines.count, 1)
        XCTAssertEqual(lines.first?.kind, .update)
        XCTAssertNil(lines.first?.place)
        XCTAssertEqual(lines.first?.shownAtMillis, now - 30 * minute)
    }

    func testAnArrivalWritesTheLastUpdateTooAndIsNotAlsoAnUpdate() {
        let seen = Fixture.state([member("t", .out, presenceAt: now - 9 * hour)])
        // Même minute : un seul événement. Sans la règle, l'update écrite un instant plus tard cacherait l'arrivée.
        let sameMinute = Fixture.state([
            member("t", .inTrusted, presenceAt: now - 3 * hour, lastUpdateAt: now - 3 * hour + 2 * minute)
        ])
        // Une update bien plus tardive est une nouvelle à part entière, et la plus récente.
        let later = Fixture.state([member("t", .inTrusted, presenceAt: now - 3 * hour, lastUpdateAt: now - hour)])

        XCTAssertEqual(Fixture.compare(seen, sameMinute).first?.kind, .arrival)
        XCTAssertEqual(Fixture.compare(seen, later).first?.kind, .update)
    }

    // MARK: - C1-T6 à T8 : le silence

    func testT6APersonWhoBecameRedIsOneSilenceLineWithTheTimeOfTheLatestNews() {
        let last = now - 5 * hour
        let seen = Fixture.state([member("a", .out, .home, presenceAt: last, lastUpdateAt: last)])
        let current = Fixture.state([member("a", .out, .home, presenceAt: last, lastUpdateAt: last, silent: true)])

        let lines = Fixture.compare(seen, current)

        XCTAssertEqual(lines.first?.kind, .silence)
        XCTAssertEqual(lines.first?.shownAtMillis, last)
        // Datée de l'instant où elle est devenue rouge : les lignes sont ordonnées par le moment du changement.
        XCTAssertEqual(lines.first?.atMillis, last + StatusThresholds.redAfterMillis + 1)
    }

    func testT6bAPersonWhoLeftAndIsNowRedIsOneSilenceLineNotALeftLine() {
        let left = now - 5 * hour
        let seen = Fixture.state([member("a", .inTrusted, .home, presenceAt: now - 20 * hour)])
        let current = Fixture.state([
            member("a", .out, .home, presenceAt: left, lastUpdateAt: now - 20 * hour, silent: true)
        ])

        let lines = Fixture.compare(seen, current)

        XCTAssertEqual(lines.first?.kind, .silence)
        XCTAssertEqual(lines.first?.shownAtMillis, left)
    }

    func testT7APersonAlreadyRedAtTheLastVisitAndStillRedIsNoLine() {
        let last = now - 9 * hour
        let picture = Fixture.state([member("a", .out, presenceAt: last, lastUpdateAt: last, silent: true)])

        XCTAssertTrue(Fixture.compare(picture, picture).isEmpty)
    }

    func testT8APersonOnlyOrangeHasNoSilenceLineButHerDepartureIfSheLeft() {
        let seen = Fixture.state([member("a", .inTrusted, .work, presenceAt: now - 20 * hour)])
        let orange = Fixture.state([
            member("a", .out, .work, presenceAt: now - 3 * hour, lastUpdateAt: now - 20 * hour)
        ])

        XCTAssertEqual(Fixture.compare(seen, orange).first?.kind, .departure)
        // Orange et rien d'autre n'a changé : on ne dit rien.
        XCTAssertTrue(Fixture.compare(orange, orange).isEmpty)
    }

    // MARK: - C1-T9 : combien, dans quel ordre

    func testT9FourPeopleChangedIsThreeLinesTheMostRecentFirst() {
        let uids = ["p1", "p2", "p3", "p4"]
        let seen = Fixture.state(uids.map { member($0, .out, presenceAt: now - 20 * hour) })
        func updated(_ uid: String, _ ago: Int) -> VisitMember {
            member(uid, .out, presenceAt: now - 20 * hour, lastUpdateAt: now - ago)
        }
        let current = Fixture.state([
            updated("p1", 3 * hour), updated("p2", hour), updated("p3", 4 * hour), updated("p4", 2 * hour)
        ])

        XCTAssertEqual(Fixture.compare(seen, current).map { $0.uid }, ["p2", "p4", "p1"])
    }

    func testASilenceIsOrderedByTheMomentSheBecameRed() {
        let seen = Fixture.state([
            member("quiet", .out, presenceAt: now - 6 * hour),
            member("news", .out, presenceAt: now - 20 * hour)
        ])
        let current = Fixture.state([
            member("quiet", .out, presenceAt: now - 6 * hour, silent: true),
            member("news", .out, presenceAt: now - 20 * hour, lastUpdateAt: now - 5 * hour)
        ])

        // « quiet » est devenue rouge il y a 2 h (6 h - 4 h), « news » a donné des nouvelles il y a 5 h : le silence est le plus récent.
        XCTAssertEqual(Fixture.compare(seen, current).map { $0.uid }, ["quiet", "news"])
    }

    // MARK: - C1-T10 à T13 : jamais évoqué

    func testT10ATripOrStoppedSharingOrHiddenNewsIsNoLine() {
        let seen = Fixture.state([member("t"), member("a"), member("c")])
        let current = Fixture.state([
            VisitFixtures.member("t", .travel, nil, presenceAt: now - hour, muted: true),
            VisitFixtures.member("a", .out, presenceAt: now - hour, muted: true),
            VisitFixtures.member("c", .out, presenceAt: now - hour, muted: true)
        ])

        XCTAssertTrue(Fixture.compare(seen, current).isEmpty)
        // Et dans l'autre sens : de retour d'un voyage, rien non plus.
        XCTAssertTrue(Fixture.compare(current, seen).isEmpty)
    }

    func testT11APersonInADangerZoneIsNoLineAndLeavingItIsNotADeparture() {
        let seen = Fixture.state([member("t", .out, presenceAt: now - 3 * hour)])
        let danger = Fixture.state([member("t", .inRisk, nil, presenceAt: now - hour)])
        let leftDanger = Fixture.state([
            member("t", .out, .home, presenceAt: now - 10 * minute, lastUpdateAt: now - 3 * hour)
        ])

        XCTAssertTrue(Fixture.compare(seen, danger).isEmpty)
        XCTAssertTrue(Fixture.compare(danger, leftDanger).isEmpty)
    }

    func testT13ANewMemberOrAMemberWhoLeftIsNoLine() {
        let seen = Fixture.state([member("t"), member("gone")])
        let current = Fixture.state([member("t"), member("new", .inTrusted, presenceAt: now - hour)])

        XCTAssertTrue(Fixture.compare(seen, current).isEmpty)
    }

    // MARK: - C1-T15 à T17

    func testT15AnAbsenceUnderThirtyMinutesIsNotAnOpening() {
        XCTAssertFalse(LastVisit.isReopening(absenceMillis: 0))
        XCTAssertFalse(LastVisit.isReopening(absenceMillis: 29 * minute + 59_000))
        XCTAssertTrue(LastVisit.isReopening(absenceMillis: 30 * minute))
        XCTAssertTrue(LastVisit.isReopening(absenceMillis: 3 * hour))
        // Une horloge reculée : mieux vaut comparer que rester muet.
        XCTAssertTrue(LastVisit.isReopening(absenceMillis: -1))
    }

    func testT16OneLinePerPersonTheMostRecentAndNoStoryIsInvented() {
        // Arrivé puis reparti entre deux visites : le statut ne garde que le départ.
        let seen = Fixture.state([member("t", .inTrusted, .home, presenceAt: now - 20 * hour)])
        let current = Fixture.state([
            member("t", .out, .school, presenceAt: now - 2 * hour, lastUpdateAt: now - 10 * hour)
        ])

        let lines = Fixture.compare(seen, current)

        XCTAssertEqual(lines.count, 1)
        XCTAssertEqual(lines.first?.kind, .departure)
    }

    func testT17AnotherMiffIsNeitherComparedNorMixed() {
        let other = Fixture.state([member("t")], miffId: "miff-2")
        let current = Fixture.state([member("t", presenceAt: now - hour)])

        XCTAssertTrue(Fixture.compare(other, current).isEmpty)
    }

    // MARK: - C1-T18, T19 : les heures

    func testT19AClockSetBackMakesNoAbsurdLineAndNoCrash() throws {
        let seen = Fixture.state([member("t", .out, presenceAt: now - 20 * hour)])
        // L'événement est deux heures dans le futur de ce téléphone : rien n'est dit.
        let future = Fixture.state([member("t", .inTrusted, presenceAt: now + 2 * hour)])
        // Quelques minutes d'avance (le serveur en accepte 5) : dit comme « maintenant », jamais dans le futur.
        let close = Fixture.state([member("t", .inTrusted, presenceAt: now + 2 * minute)])
        // Une heure qui RECULE (un autre téléphone, une horloge) n'est pas un changement.
        let older = Fixture.state([member("t", .inTrusted, presenceAt: now - 30 * hour)])

        XCTAssertTrue(Fixture.compare(seen, future).isEmpty)
        XCTAssertEqual(try XCTUnwrap(Fixture.compare(seen, close).first).shownAtMillis, now)
        XCTAssertTrue(Fixture.compare(seen, older).isEmpty)
    }
}
