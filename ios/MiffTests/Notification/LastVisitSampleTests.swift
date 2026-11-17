import XCTest
@testable import Miff

/// « Depuis ta dernière visite » : l'image prise du Feed (C1-T12, C1-T14, Q5), les jours (C1-T18), les lignes du centre et leur vie privée
/// (C1-7), le groupe dans la liste du centre. Miroir des tests d'Android.
final class LastVisitSampleTests: XCTestCase {
    private typealias Fixture = VisitFixtures
    private let hour = VisitFixtures.hour
    private let now = VisitFixtures.now

    private func status(
        _ presence: PresenceValue = .inTrusted,
        place: String? = "p1",
        ago: Int? = nil
    ) -> MemberStatus {
        feedStatus(presence, placeId: place, departedAgo: ago ?? hour, updatedAgo: ago ?? hour)
    }

    // MARK: - C1-T12, T14 : l'image

    func testT12MyOwnCardIsNeverInThePicture() throws {
        let members = [feedMember("me"), feedMember("t")]
        let input = Fixture.sampleInput(
            members: members, statuses: Fixture.loaded(["me": status(), "t": status()])
        )

        let picture = try XCTUnwrap(Fixture.sample(input))

        XCTAssertEqual(picture.members.map { $0.uid }, ["t"])
        XCTAssertEqual(picture.miffId, Fixture.miff)
    }

    func testT14APictureIsOnlyTakenFromFreshStatuses() {
        let members = [feedMember("me"), feedMember("t")]
        let statuses = ["me": status(), "t": status()]

        func sample(
            _ view: StatusesView,
            waiting: Bool = false,
            offline: Bool = false
        ) -> VisitState? {
            Fixture.sample(Fixture.sampleInput(members: members, statuses: view, waiting: waiting, offline: offline))
        }

        XCTAssertNotNil(sample(Fixture.loaded(statuses)))
        // Venue du cache du téléphone, hors connexion, pas lue, accord attendu : rien n'est comparé, rien n'est gardé comme « vu ».
        XCTAssertNil(sample(Fixture.loaded(statuses, fromCache: true)))
        XCTAssertNil(sample(Fixture.loaded(statuses), offline: true))
        XCTAssertNil(sample(.waiting))
        XCTAssertNil(sample(.pending))
        XCTAssertNil(sample(.off))
        XCTAssertNil(sample(.unknown))
        XCTAssertNil(sample(Fixture.loaded(statuses), waiting: true))
        // J'ai arrêté de partager : les nouvelles des autres sont masquées.
        let stopped = [feedMember("me", sharing: false), feedMember("t")]
        XCTAssertNil(Fixture.sample(Fixture.sampleInput(members: stopped, statuses: Fixture.loaded(statuses))))
    }

    func testTheSampleReadsTheCategoryNeverTheNameAndSilenceOnlyOutOfEveryZone() throws {
        let members = [
            feedMember("me"), feedMember("t"), feedMember("a"), feedMember("l"), feedMember("trip"),
            feedMember("stopped", sharing: false)
        ]
        let places = [
            feedPlace("p1", name: "École Jean Moulin", category: .school),
            feedPlace("p2", name: "Club de tango", category: .leisure)
        ]
        let statuses = [
            "t": status(.inTrusted, place: "p1", ago: hour),
            "a": status(.out, place: "p2", ago: 6 * hour),
            "l": status(.inRisk, place: "p1", ago: 6 * hour),
            "trip": status(.travel, place: nil, ago: 6 * hour),
            "stopped": status(.out, place: "p1", ago: hour)
        ]
        let input = Fixture.sampleInput(members: members, places: places, statuses: Fixture.loaded(statuses))

        let picture = try XCTUnwrap(Fixture.sample(input))
        let byUid = Dictionary(uniqueKeysWithValues: picture.members.map { ($0.uid, $0) })

        XCTAssertEqual(byUid["t"]?.place, .school)
        // Loisir : « dans un lieu de confiance » (décision Q5) : aucune catégorie, et le nom du lieu n'est nulle part.
        XCTAssertNil(byUid["a"]?.place)
        XCTAssertEqual(byUid["a"]?.silent, true)
        // Une zone de danger est rouge mais n'est pas un silence ; un voyage et un partage arrêté sont tus.
        XCTAssertEqual(byUid["l"]?.silent, false)
        XCTAssertEqual(byUid["trip"]?.muted, true)
        XCTAssertEqual(byUid["stopped"]?.muted, true)
        XCTAssertEqual(byUid["t"]?.muted, false)
    }

    // MARK: - C1-T18 : les jours

    func testT18TheDayIsTodayYesterdayTheWeekdayOrTheDate() {
        func day(_ daysAgo: Int) -> VisitDay {
            LastVisit.dayOf(atMillis: now - daysAgo * 24 * hour, nowMillis: now, zone: feedZone)
        }

        XCTAssertEqual(day(0), .today)
        XCTAssertEqual(day(1), .yesterday)
        XCTAssertEqual(day(2), .weekday)
        XCTAssertEqual(day(6), .weekday)
        XCTAssertEqual(day(7), .date)
        XCTAssertEqual(day(40), .date)
        // Une heure après maintenant compte pour aujourd'hui.
        XCTAssertEqual(LastVisit.dayOf(atMillis: now + 2 * hour, nowMillis: now, zone: feedZone), .today)
    }

    func testT18bTheDayFollowsTheZoneOfThePhoneNotTheHourCount() {
        let late = 1_794_000_000_000 - 1_794_000_000_000 % 86_400_000 - 30 * 60_000
        let morning = late + 60 * 60_000
        let zone = { (hours: Int) -> TimeZone in TimeZone(secondsFromGMT: hours * 3600) ?? .current }

        // 23 h 30 UTC la veille, vu à 0 h 30 UTC : hier en UTC, mais aujourd'hui pour un téléphone à UTC-5 ou à UTC+1.
        XCTAssertEqual(LastVisit.dayOf(atMillis: late, nowMillis: morning, zone: zone(0)), .yesterday)
        XCTAssertEqual(LastVisit.dayOf(atMillis: late, nowMillis: morning, zone: zone(-5)), .today)
        XCTAssertEqual(LastVisit.dayOf(atMillis: late, nowMillis: morning, zone: zone(1)), .today)
    }

    // MARK: - Les lignes du centre, vie privée (C1-7)

    func testALineOfTheCentreIsAKeyOfIdentifiersAndTimesNeverANameOrAPlace() throws {
        let line = VisitLine(
            uid: "uid-1", kind: .departure, place: .school, atMillis: now - hour, shownAtMillis: now - hour
        )

        let item = try XCTUnwrap(LastVisit.items(miffId: Fixture.miff, lines: [line]).first)
        let parsed = try XCTUnwrap(LastVisit.parse(item))

        XCTAssertEqual(item.type, .c1)
        XCTAssertEqual(item.miffId, Fixture.miff)
        XCTAssertEqual(item.state, .silent)
        XCTAssertTrue(item.isUnread, "non lue par défaut (décision Q2)")
        XCTAssertEqual(parsed.uid, "uid-1")
        XCTAssertEqual(parsed.kind, .departure)
        XCTAssertEqual(parsed.place, .school)
        XCTAssertEqual(parsed.shownAtMillis, now - hour)
        // Rien que des identifiants, une lettre, un mot de catégorie et une heure.
        XCTAssertEqual(item.id, "c1|miff-1|uid-1|d|SCHOOL|\(now - hour)")
        XCTAssertTrue("\(parsed)\(line)".contains("redacted"))
    }

    func testALineWithNoPlaceSaysAGenericPlaceAndABrokenKeyIsNotALine() throws {
        let generic = Fixture.item("u")
        func changed(id: String) -> NotificationItem {
            NotificationItem(
                id: id, type: .c1, miffId: "m", createdAtMillis: now, readAtMillis: nil, state: .silent
            )
        }

        XCTAssertNil(try XCTUnwrap(LastVisit.parse(generic)).place)
        XCTAssertNil(LastVisit.parse(changed(id: "c1|only|three")))
        XCTAssertNil(LastVisit.parse(changed(id: "c1|m|u|x||1")))
        XCTAssertNil(LastVisit.parse(changed(id: "c1|m|u|a|NOWHERE|1")))
        XCTAssertNil(LastVisit.parse(changed(id: "c1|m|u|a||notatime")))
        XCTAssertNil(LastVisit.parse(NotificationItem(
            id: generic.id, type: .n1, miffId: "m", createdAtMillis: now, readAtMillis: nil, state: .silent
        )))
    }

    // MARK: - Le groupe dans la liste du centre

    private func history(_ items: [NotificationItem]) -> NotificationHistory {
        NotificationHistory(items: items, nowMillis: now)
    }

    func testTheGroupIsReplacedAsAWholeTheOtherLinesAreNotTouchedAndAReadLineKeepsItsState() {
        let other = NotificationFixtures.item("n1|miff-1|u|1", at: now - hour)
        var old = Fixture.item("t", at: now - 3 * hour)
        old.readAtMillis = now - hour
        let stay = Fixture.item("a", at: now - 2 * hour)
        let before = history([other, old, stay])
        var unreadAgain = old
        unreadAgain.readAtMillis = nil
        let fresh = Fixture.item("b", at: now - hour)

        let replaced = before.replacingVisit(with: [unreadAgain, fresh], nowMillis: now)

        XCTAssertEqual(Set(replaced.items.map { $0.id }), [other.id, old.id, fresh.id])
        // La ligne encore là garde son état (lue).
        XCTAssertEqual(replaced.items.first { $0.id == old.id }?.isUnread, false)
        // Rien de neuf : le groupe est retiré (jamais une ligne vieille de plusieurs jours).
        XCTAssertEqual(before.replacingVisit(with: [], nowMillis: now).items.map { $0.id }, [other.id])
    }

    func testNeverMoreThanThreeLinesOfTheGroup() {
        let four = (1...4).map { Fixture.item("u\($0)", at: now - $0 * hour) }

        XCTAssertEqual(history([]).replacingVisit(with: four, nowMillis: now).visitItems.count, 3)
    }

    func testTheLinesOfAPersonWhoLeftOrOfAMiffThatIsGoneAreRemovedAndOnlyThoseOfTheGroup() {
        let n1 = NotificationFixtures.item("n1|miff-1|u|1", at: now)
        let items = [n1, Fixture.item("t"), Fixture.item("a"), Fixture.item("t", miffId: "miff-2")]
        let all = history(items)

        let withoutT = all.removingVisit(
            where: { LastVisit.uid(of: $0) == "t" && $0.miffId == Fixture.miff }, nowMillis: now
        )
        let none = all.removingVisit(where: { _ in true }, nowMillis: now)

        XCTAssertEqual(withoutT.items.count, 3)
        XCTAssertEqual(none.items.map { $0.id }, [n1.id])
        XCTAssertEqual(all.dayItems.map { $0.id }, [n1.id], "les jours ne montrent jamais le groupe")
    }

    func testTheGroupIsNotAnyNotificationOfThePhoneNoSwitchNoAlarm() {
        XCTAssertFalse(NotificationType.allCases.contains(.c1))
        XCTAssertEqual(NotificationType.c1.destination, .feed)
        XCTAssertFalse(NotificationType.c1.silentAtNight)
        let card = NotificationDestination.feedCard(miffId: "m", uid: "u")
        XCTAssertEqual(NotificationNavigation.tab(for: card, showsUpdateButton: true), .feed)
    }
}
