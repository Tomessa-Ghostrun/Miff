import XCTest
@testable import Miff

/// Les lignes du centre de notifications au sujet des demandes d'ajout d'un lieu (ticket LIEU-REQ, critère C1, miroir d'Android
/// `PlaceRequestLinesTest`) : la clé, une ligne par demande, le regroupement par demandeur, les mots faits à l'affichage, l'onglet visé.
@MainActor
final class PlaceRequestLinesTests: XCTestCase {
    private let now = 1_800_000_000_000
    private let day = NotificationFixtures.day

    private func line(
        slot: Int,
        uid: String = testMemberUid,
        miff: String = testMiffId,
        at createdAt: Int? = nil,
        readAt: Int? = nil
    ) -> NotificationItem {
        NotificationFixtures.item(
            PlaceRequestLines.key(miffId: miff, requestId: PlaceRequestLimits.idOf(uid: uid, slot: slot)),
            type: .n8,
            miffId: miff,
            at: createdAt ?? now - slot,
            readAt: readAt,
            state: .silent
        )
    }

    // MARK: - La clé et la personne

    func testTheKeyCarriesTheMiffAndTheRequestAndNothingElse() {
        let key = PlaceRequestLines.key(miffId: "miffA", requestId: "uid1_2")

        XCTAssertEqual(key, "n8|miffA|uid1_2")
    }

    func testTheRequestAndThePersonAreReadInTheKey() {
        let item = line(slot: 2)

        XCTAssertEqual(PlaceRequestLines.requestId(of: item), "\(testMemberUid)_2")
        XCTAssertEqual(PlaceRequestLines.requester(of: item), testMemberUid)
    }

    func testALineThatIsNotARequestHasNoRequestAndNoRequester() {
        let other = NotificationFixtures.item("n1|miff|moi|1", type: .n1, at: now)
        let malformed = NotificationFixtures.item("n8|only-two", type: .n8, at: now)

        XCTAssertNil(PlaceRequestLines.requestId(of: other))
        XCTAssertNil(PlaceRequestLines.requester(of: other))
        XCTAssertNil(PlaceRequestLines.requestId(of: malformed))
    }

    // MARK: - Une ligne par demande

    func testOneSilentLineIsWrittenPerRequestDatedWhenItWasMadeAndNeverInTheFuture() {
        let old = testRequest(slot: 1, createdAtMillis: now - 14 * day)
        let future = testRequest(slot: 2, createdAtMillis: now + day)
        let undated = testRequest(slot: 3, createdAtMillis: nil)

        let lines = PlaceRequestLines.lines(miffId: testMiffId, requests: [old, future, undated], nowMillis: now)

        XCTAssertEqual(lines.map { $0.createdAtMillis }, [now - 14 * day, now, now])
        XCTAssertTrue(lines.allSatisfy { $0.type == .n8 && $0.state == .silent && $0.isUnread })
        XCTAssertTrue(lines.allSatisfy { $0.miffId == testMiffId })
        XCTAssertEqual(lines.map { $0.id }, [old, future, undated].map { "n8|\(testMiffId)|\($0.id)" })
    }

    func testALineHoldsNeitherAPlaceNorAPersonName() {
        let request = testRequest(slot: 1, name: "Club de judo")

        let encoded = PlaceRequestLines.lines(miffId: testMiffId, requests: [request], nowMillis: now)
            .map { String(describing: $0) }
            .joined()

        XCTAssertFalse(encoded.contains("Club de judo"))
        XCTAssertFalse(encoded.contains("Dijon"))
    }

    // MARK: - Le regroupement

    func testTheUnreadRequestsOfOnePersonInOneMiffAreOneEntryWithTheNewestTime() {
        let items = [line(slot: 1, at: now - 300), line(slot: 2, at: now - 100), line(slot: 3, at: now - 200)]

        let entries = PlaceRequestLines.entries(of: items)

        XCTAssertEqual(entries.count, 1)
        XCTAssertEqual(entries[0].count, 3)
        XCTAssertEqual(entries[0].createdAtMillis, now - 100)
        XCTAssertEqual(entries[0].requesterUid, testMemberUid)
        XCTAssertTrue(entries[0].unread)
    }

    func testTwoPeopleOrTwoMiffsAreNeverGrouped() {
        let items = [
            line(slot: 1, uid: "uidone"), line(slot: 1, uid: "uidtwo"), line(slot: 1, uid: "uidone", miff: "autreMiff")
        ]

        XCTAssertEqual(PlaceRequestLines.entries(of: items).count, 3)
    }

    func testEveryReadLineIsItsOwnEntry() {
        let items = [line(slot: 1, readAt: now), line(slot: 2, readAt: now), line(slot: 3)]

        let entries = PlaceRequestLines.entries(of: items)

        XCTAssertEqual(entries.count, 3, "deux lues + une non lue")
        XCTAssertEqual(entries.filter { $0.unread }.count, 1)
    }

    func testTheOtherLinesOfTheCentreAreNeverTouched() {
        let other = NotificationFixtures.item("n1|a", type: .n1, at: now - 10)
        let items = [other, line(slot: 1, at: now - 5), line(slot: 2, at: now - 20)]

        let displayed = PlaceRequestLines.displayed(items)

        XCTAssertEqual(displayed.count, 2, "la ligne N1 et UNE ligne de demandes")
        XCTAssertEqual(displayed.map { $0.type }, [.n8, .n1], "la plus récente d'abord")
        XCTAssertTrue(PlaceRequestLines.entries(of: [other]).isEmpty)
    }

    func testTheKeysOfAMiffAreThoseOfItsRequestLinesOnly() {
        let other = NotificationFixtures.item("n1|a", type: .n1, at: now)
        let items = [line(slot: 1), line(slot: 1, miff: "autreMiff"), other]

        XCTAssertEqual(PlaceRequestLines.ids(of: items, miffId: testMiffId), [items[0].id])
    }

    // MARK: - Hors périmètre des notifications du système

    func testN8IsALineOfTheCentreOnlyNeverAnInterruptorNorANightNotification() {
        XCTAssertFalse(NotificationType.allCases.contains(.n8), "aucun réglage, aucune programmation")
        XCTAssertEqual(NotificationType.n8.destination, .profile)
        XCTAssertFalse(NotificationType.n8.silentAtNight)
        for key in [NotificationType.n8.titleKey, NotificationType.n8.bodyKey] {
            XCTAssertNotEqual(L10n.string(key), key)
        }
    }

    func testANotificationOfTheCentreCanBeDecodedAsN8() throws {
        let data = Data(#""n8""#.utf8)

        XCTAssertEqual(try JSONDecoder().decode(NotificationType.self, from: data), .n8)
    }

    func testTheTapOnARequestLineLeadsToTheProfileTab() {
        for showsUpdate in [true, false] {
            let forRequests = NotificationNavigation.tab(
                for: .placeRequests(miffId: "m"), showsUpdateButton: showsUpdate
            )
            let forProfile = NotificationNavigation.tab(for: .profile, showsUpdateButton: showsUpdate)
            XCTAssertEqual(forRequests, .profile)
            XCTAssertEqual(forProfile, .profile)
        }
    }

    // MARK: - Les mots, faits à l'affichage

    func testTheSentenceNamesThePersonAndTheMiffAndCountsTheRequests() {
        let one = NotificationRowTexts.text(
            for: line(slot: 1), count: 1, miffName: nil, requestMiffName: "Famille", firstName: "Léa"
        )
        let many = NotificationRowTexts.text(
            for: line(slot: 1), count: 3, miffName: nil, requestMiffName: "Famille", firstName: "Léa"
        )

        XCTAssertTrue(one.title.contains("Léa") && one.title.contains("Famille"), one.title)
        XCTAssertNil(one.body)
        XCTAssertNil(one.miffName, "la phrase nomme déjà la Miff")
        for part in ["Léa", "3", "Famille"] {
            XCTAssertTrue(many.title.contains(part), many.title)
        }
    }

    func testWithoutAFirstNameOrAMiffNameTheLineKeepsItsGenericWords() {
        let generic = NotificationRowText(
            title: L10n.string(NotificationType.n8.titleKey),
            body: L10n.string(NotificationType.n8.bodyKey),
            miffName: nil
        )

        let noName = NotificationRowTexts.text(
            for: line(slot: 1), count: 1, miffName: nil, requestMiffName: "Famille", firstName: nil
        )
        let emptyName = NotificationRowTexts.text(
            for: line(slot: 1), count: 2, miffName: nil, requestMiffName: "Famille", firstName: ""
        )
        let noMiff = NotificationRowTexts.text(
            for: line(slot: 1), count: 1, miffName: nil, requestMiffName: nil, firstName: "Léa"
        )

        XCTAssertEqual(noName, generic)
        XCTAssertEqual(emptyName, generic)
        XCTAssertEqual(noMiff, generic)
    }

    func testAnotherLineKeepsItsTypeWordsAndTheMiffName() {
        let item = NotificationFixtures.item("n1|a", type: .n1, at: now)

        let text = NotificationRowTexts.text(
            for: item, count: 1, miffName: "Famille", requestMiffName: "Famille", firstName: "Léa"
        )

        XCTAssertEqual(text.title, L10n.string(NotificationType.n1.titleKey))
        XCTAssertEqual(text.body, L10n.string(NotificationType.n1.bodyKey))
        XCTAssertEqual(text.miffName, "Famille")
    }

    // MARK: - L'annuaire des prénoms (mémoire seulement)

    func testTheDirectoryKeepsFirstNamesByMiffAndPersonAndForgetsThemAtTheEndOfTheSession() {
        let directory = RequesterDirectory()

        directory.put(miffId: "m1", uid: "u1", firstName: "Léa")
        directory.put(miffId: "m1", uid: "u2", firstName: "")

        XCTAssertEqual(directory.firstName(miffId: "m1", uid: "u1"), "Léa")
        XCTAssertNil(directory.firstName(miffId: "m2", uid: "u1"), "une autre Miff")
        XCTAssertNil(directory.firstName(miffId: "m1", uid: "u2"), "un prénom vide n'est pas gardé")
        XCTAssertFalse(String(describing: directory).contains("Léa"))

        directory.stop()

        XCTAssertNil(directory.firstName(miffId: "m1", uid: "u1"))
    }

    func testTheEndOfTheSessionClearsTheDirectoryAndTheDirectoryIsNeverWrittenToAFile() throws {
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Miff")
        let services = try String(
            contentsOf: root.appendingPathComponent("App/NotificationServices.swift"), encoding: .utf8
        )
        let directory = try String(
            contentsOf: root.appendingPathComponent("Services/Notification/RequesterDirectory.swift"), encoding: .utf8
        )

        XCTAssertTrue(services.contains("requesters.stop()"), "vidé avec la session")
        for forbidden in ["UserDefaults", "FileManager", "Keychain", "print(", "NSLog", "Logger"] {
            XCTAssertFalse(directory.contains(forbidden), "aucun prénom hors de la mémoire : \(forbidden)")
        }
    }
}
