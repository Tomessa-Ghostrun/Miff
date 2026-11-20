import XCTest
@testable import Miff

/// CNC-I : le journal du serveur (`users/{uid}/notifications`), en règles pures : la forme d'une entrée, ce qui est ignoré sans erreur, la clé de la ligne
/// (la MÊME que celle du message de cette entrée), les lignes lues / non lues à la première synchronisation, le curseur, l'identifiant d'entrée d'une ligne.
/// Miroir de `NotificationJournalTest` d'Android : mêmes vecteurs, mêmes résultats.
final class NotificationJournalTests: XCTestCase {
    private let miff = "abcdefghijklmnopqrst"
    private let now = 1_800_000_000_000
    private let hour = NotificationFixtures.hour
    private let day = NotificationFixtures.day
    private let updateId = "u-abcdefghijklmnopqrst-uidB-1799999000000"
    private let startId = "as-abcdefghijklmnopqrst-uidB-1799999000000"
    private let endId = "ae-abcdefghijklmnopqrst-uidB-1799999000000"

    private func fields(
        kind: Any = "update",
        version: Any = 1,
        miffId: Any = "abcdefghijklmnopqrst",
        actor: Any = "uidB"
    ) -> [String: Any] {
        ["v": version, "kind": kind, "miffId": miffId, "actorUid": actor]
    }

    private func entry(
        _ kind: NotificationJournalKind = .update,
        id: String? = nil,
        actor: String = "uidB",
        age: Int = 0,
        expiresIn: Int? = nil
    ) -> JournalEntry {
        let defaultId = kind == .update ? updateId : (kind == .alertStart ? startId : endId)
        return JournalEntry(
            entryId: id ?? defaultId, kind: kind, miffId: miff, actorUid: actor,
            createdAtMillis: now - age, expireAtMillis: now + (expiresIn ?? 29 * day)
        )
    }

    private func key(_ kind: NotificationJournalKind) -> String {
        NotificationJournal.key(of: entry(kind))
    }

    /// La ligne d'une entrée pour la personne « uidA », à `now`.
    private func line(_ item: JournalEntry, firstSync: Bool = false) -> NotificationItem? {
        NotificationJournal.item(for: item, myUid: "uidA", nowMillis: now, firstSync: firstSync)
    }

    // MARK: - La forme d'une entrée

    func testAnEntryOfTheContractIsUnderstood() {
        let parsed = NotificationJournal.parse(
            docId: updateId, data: fields(), createdAtMillis: now - hour, expireAtMillis: now + 29 * day
        )

        XCTAssertEqual(parsed?.entryId, updateId)
        XCTAssertEqual(parsed?.kind, .update)
        XCTAssertEqual(parsed?.miffId, miff)
        XCTAssertEqual(parsed?.actorUid, "uidB")
        XCTAssertEqual(parsed?.createdAtMillis, now - hour)
        XCTAssertEqual(
            NotificationJournal.parse(docId: startId, data: fields(kind: "alert_start"), createdAtMillis: 1)?.kind,
            .alertStart
        )
        XCTAssertEqual(
            NotificationJournal.parse(docId: endId, data: fields(kind: "alert_end"), createdAtMillis: 1)?.kind,
            .alertEnd
        )
    }

    private struct MalformedCase {
        let docId: String
        let data: [String: Any]
        var createdAt: Int? = 1_799_999_000_000
        var expireAt: Int? = 1_800_000_000_000
    }

    func testAnythingThatDoesNotLookExactlyRightIsIgnoredWithoutAnError() {
        let cases: [MalformedCase] = [
            MalformedCase(docId: updateId, data: fields(version: 2)),
            MalformedCase(docId: updateId, data: fields(version: "1")),
            MalformedCase(docId: updateId, data: fields(kind: "mystery")),
            MalformedCase(docId: updateId, data: fields(kind: 7)),
            MalformedCase(docId: updateId, data: fields(miffId: "a/b")),
            MalformedCase(docId: updateId, data: fields(miffId: "")),
            MalformedCase(docId: updateId, data: fields(actor: "../users/x")),
            MalformedCase(docId: updateId, data: fields(actor: 12)),
            MalformedCase(docId: "../x", data: fields()),
            MalformedCase(docId: "", data: fields()),
            MalformedCase(docId: updateId, data: fields(), createdAt: nil),
            MalformedCase(docId: updateId, data: fields(), createdAt: 0),
            MalformedCase(docId: updateId, data: fields(), expireAt: nil),
            MalformedCase(docId: updateId, data: fields(), expireAt: 0),
            MalformedCase(docId: updateId, data: [:])
        ]
        for (index, item) in cases.enumerated() {
            let parsed = NotificationJournal.parse(
                docId: item.docId, data: item.data, createdAtMillis: item.createdAt, expireAtMillis: item.expireAt
            )
            XCTAssertNil(parsed, "cas \(index)")
        }
    }

    func testAKeyAddedByALaterVersionOfTheServerIsIgnored() {
        var data = fields()
        data["extra"] = "plus tard"

        XCTAssertNotNil(NotificationJournal.parse(docId: updateId, data: data, createdAtMillis: now))
    }

    func testAnIdentifierLongerThanTheShapeIsRefused() {
        let tooLong = String(repeating: "a", count: 129)

        XCTAssertNil(NotificationJournal.parse(docId: tooLong, data: fields(), createdAtMillis: now))
        let longest = String(repeating: "a", count: 128)
        XCTAssertNotNil(NotificationJournal.parse(docId: longest, data: fields(), createdAtMillis: now))
    }

    func testAnEntryShowsNothingInALog() {
        let text = "\(entry())"

        XCTAssertFalse(text.contains("uidB"))
        XCTAssertFalse(text.contains(miff))
        XCTAssertEqual(text, "JournalEntry(redacted)")
    }

    // MARK: - La clé de la ligne

    func testTheKeyOfALineIsTheKeyTheMessageOfThatEntryWrites() {
        let update = PushArrival(kind: .update, miffId: miff, authorUid: "uidB", entryId: updateId, messageId: "m1")
        let start = PushArrival(kind: .alertStart, miffId: miff, authorUid: "uidB", entryId: startId, messageId: "m2")
        let end = PushArrival(kind: .alertEnd, miffId: miff, authorUid: "uidB", entryId: endId, messageId: "m3")

        XCTAssertEqual(key(.update), "n9|u|uidB|\(updateId)")
        XCTAssertEqual(key(.alertStart), "al|u|uidB|start|\(startId)")
        XCTAssertEqual(key(.alertEnd), "al|u|uidB|end|\(endId)")
        let received = now
        XCTAssertEqual(CircleNewsKeys.pushId(update, receivedAtMillis: received), key(.update))
        XCTAssertEqual(CircleAlertKeys.pushId(start, receivedAtMillis: received), key(.alertStart))
        XCTAssertEqual(CircleAlertKeys.pushId(end, receivedAtMillis: received), key(.alertEnd))
    }

    func testAMessageFromAServerWithoutJournalIsKeyedByItsMessageThenByTheTimeOfArrival() {
        let withMessage = PushArrival(kind: .update, miffId: miff, authorUid: "uidB", entryId: nil, messageId: "m-1")
        let bare = PushArrival(kind: .update, miffId: miff, authorUid: "uidB", entryId: nil, messageId: nil)

        XCTAssertEqual(CircleNewsKeys.pushId(withMessage, receivedAtMillis: now), "n9|u|uidB|m-1")
        XCTAssertEqual(CircleNewsKeys.pushId(bare, receivedAtMillis: now), "n9|u|uidB|\(now)")
    }

    func testTheLovedOneOfALineIsReadInItsKeyAndAnOldOrForeignKeyHasNone() {
        XCTAssertEqual(CircleNewsKeys.actorOf("n9|u|uidB|\(updateId)"), "uidB")
        XCTAssertEqual(CircleAlertKeys.actorOf("al|u|uidB|start|\(startId)"), "uidB")
        XCTAssertNil(CircleNewsKeys.actorOf("n9|m|\(miff)|x"), "une clé par Miff : le logo")
        XCTAssertNil(CircleNewsKeys.actorOf("n9|u|a/b|x"))
        XCTAssertNil(CircleNewsKeys.actorOf("n1|u|uidB|x"))
        XCTAssertNil(CircleAlertKeys.actorOf("n9|u|uidB|x"))
        XCTAssertNil(CircleAlertKeys.actorOf("al|u|uidB"))
    }

    // MARK: - La ligne d'une entrée

    func testAnEntryBecomesAnUnreadSilentLineWithItsTimeAndNoName() throws {
        let item = try XCTUnwrap(line(entry(age: hour)))

        XCTAssertEqual(item.id, "n9|u|uidB|\(updateId)")
        XCTAssertEqual(item.type, .n9)
        XCTAssertEqual(item.miffId, miff)
        XCTAssertEqual(item.createdAtMillis, now - hour)
        XCTAssertEqual(item.state, .silent, "aucune notification du système : le message a déjà eu sa chance")
        XCTAssertTrue(item.isUnread)
    }

    func testTheThreeKindsGiveTheThreeTypes() {
        XCTAssertEqual(NotificationJournal.lineType(of: .update), .n9)
        XCTAssertEqual(NotificationJournal.lineType(of: .alertStart), .n10)
        XCTAssertEqual(NotificationJournal.lineType(of: .alertEnd), .n11)
    }

    func testOnTheFirstSyncAnEntryOfMoreThanADayArrivesAlreadyRead() throws {
        let old = try XCTUnwrap(line(entry(age: day + 1), firstSync: true))
        let fresh = try XCTUnwrap(line(entry(age: day), firstSync: true))

        XCTAssertFalse(old.isUnread, "aucun point sur la cloche pour le passé")
        XCTAssertEqual(old.readAtMillis, old.createdAtMillis)
        XCTAssertTrue(fresh.isUnread, "pile 24 h : encore récent")
    }

    func testAfterTheFirstSyncAnOldEntryStaysUnread() throws {
        let item = try XCTUnwrap(line(entry(age: 5 * day)))

        XCTAssertTrue(item.isUnread)
    }

    func testYourOwnEventAndAnExpiredEntryAreNeverALine() {
        XCTAssertNil(line(entry(actor: "uidA")))
        XCTAssertNil(line(entry(expiresIn: 0)))
        XCTAssertNil(line(entry(expiresIn: -1)))
        XCTAssertNotNil(line(entry(expiresIn: 1)))
    }

    // MARK: - Le curseur

    func testARequestStartsTwoMinutesBeforeTheCursorAndTheCursorNeverMovesBack() {
        XCTAssertNil(NotificationJournal.since(cursorMillis: nil), "rien de lu : tout le journal")
        XCTAssertEqual(NotificationJournal.since(cursorMillis: now), now - 120_000)
        XCTAssertEqual(NotificationJournal.since(cursorMillis: 10), 1)

        XCTAssertNil(NotificationJournal.nextCursor(previous: nil, entries: []))
        XCTAssertEqual(NotificationJournal.nextCursor(previous: 500, entries: []), 500)
        XCTAssertEqual(NotificationJournal.nextCursor(previous: nil, entries: [entry(age: 5), entry(age: 1)]), now - 1)
        XCTAssertEqual(NotificationJournal.nextCursor(previous: now + 10, entries: [entry(age: 1)]), now + 10)
    }

    // MARK: - L'identifiant d'une entrée, lu dans la clé d'une ligne

    func testTheEntryOfALineIsReadInItsKeySoDeletingTheLineDeletesIt() {
        XCTAssertEqual(NotificationJournal.entryId(ofCenterId: "n9|u|uidB|\(updateId)"), updateId)
        XCTAssertEqual(NotificationJournal.entryId(ofCenterId: "al|u|uidB|start|\(startId)"), startId)
        XCTAssertEqual(NotificationJournal.entryId(ofCenterId: "al|u|uidB|end|\(endId)"), endId)
    }

    func testALineThatNeverHadAnEntryHasNoneToDelete() {
        let keys = [
            "n1|miff|moi|1", "n9|u|uidB|fcm-message-id", "n9|u|uidB|1799999000000", "n9|m|\(miff)|u-x",
            "al|u|uidB|start|m-1", "c1|miff|uid|a|b|1", "n8|miff|req", "n9|u|uidB", "al|u|uidB|start|u-a|b"
        ]
        for key in keys {
            XCTAssertNil(NotificationJournal.entryId(ofCenterId: key), key)
        }
    }

    // MARK: - Les lignes qui viennent du journal et leurs sections

    private var paris: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Paris") ?? .current
        return calendar
    }

    func testLinesFromTheJournalAreGroupedByDayLikeEveryOther() {
        let current = now
        let todayEntry = entry(.update, id: "u-a", age: hour)
        let yesterdayEntry = entry(.alertStart, id: "as-b", age: hour + 20 * hour)
        let olderEntry = entry(.alertEnd, id: "ae-c", age: 3 * day)
        let items = [todayEntry, yesterdayEntry, olderEntry].compactMap { line($0) }

        let groups = NotificationGrouping.groups(of: items, nowMillis: current, calendar: paris)

        XCTAssertEqual(groups.map { $0.section }, [.today, .yesterday, .older])
        XCTAssertEqual(groups.flatMap { $0.items.map { $0.type } }, [.n9, .n10, .n11])
    }

    func testManyLinesFromTheJournalKeepTheNewestHundredAndDropWhatIsOlderThanThirtyDays() throws {
        let entries = (0..<120).map { index in
            JournalEntry(
                entryId: "u-m-uidB-\(index)", kind: .update, miffId: miff, actorUid: "uidB",
                createdAtMillis: now - index * 1_000, expireAtMillis: now + day
            )
        }
        let lines = entries.compactMap { line($0) }
        let tooOld = JournalEntry(
            entryId: "u-m-uidB-old", kind: .update, miffId: miff, actorUid: "uidB",
            createdAtMillis: now - 31 * day, expireAtMillis: now + day
        )

        let oldLine = try XCTUnwrap(line(tooOld))

        let history = NotificationHistory(nowMillis: now).adding(contentsOf: lines + [oldLine], nowMillis: now)

        XCTAssertEqual(history.items.count, 100, "100 lignes au plus")
        XCTAssertEqual(history.items.first?.id, "n9|u|uidB|u-m-uidB-0", "le plus récent d'abord")
        XCTAssertFalse(history.contains(id: "n9|u|uidB|u-m-uidB-old"), "30 jours au plus")
    }

    func testAddingTheSameLinesTwiceKeepsOneOfEach() throws {
        let made = try XCTUnwrap(line(entry()))

        let once = NotificationHistory(nowMillis: now).adding(contentsOf: [made, made], nowMillis: now)
        let twice = once.adding(contentsOf: [made], nowMillis: now)

        XCTAssertEqual(once.items.count, 1)
        XCTAssertEqual(twice, once)
    }

    func testTheUnreadCountOfJournalLinesFollowsTheirReadState() throws {
        let fresh = try XCTUnwrap(line(entry(.update, id: "u-a", age: hour), firstSync: true))
        let old = try XCTUnwrap(line(entry(.alertEnd, id: "ae-b", age: 2 * day), firstSync: true))

        let history = NotificationHistory(items: [fresh, old], nowMillis: now)

        XCTAssertEqual(history.unreadCount, 1)
        XCTAssertEqual(history.markingAllRead(nowMillis: now).unreadCount, 0)
        XCTAssertEqual(history.lovedOneItems.count, 2)
    }

    func testLovedOneLinesCanBeRemovedByMiffWithoutTouchingTheOthers() {
        let loved = NotificationFixtures.item("n9|u|uidB|u-a", type: .n9, miffId: "gone", at: now)
        let kept = NotificationFixtures.item("n9|u|uidB|u-b", type: .n9, miffId: "mine", at: now - 1)
        let reminder = NotificationFixtures.item("n1|gone|me|1", type: .n1, miffId: "gone", at: now - 2)
        let history = NotificationHistory(items: [loved, kept, reminder], nowMillis: now)

        let after = history.removingLovedOnes(where: { $0.miffId == "gone" }, nowMillis: now)

        XCTAssertEqual(
            after.items.map { $0.id }, ["n9|u|uidB|u-b", "n1|gone|me|1"], "un rappel n'est jamais retiré par ce chemin"
        )
    }

    // MARK: - Les types ajoutés

    func testTheNewTypesAreLinesOfTheCentreOnlyAndTheirTextsExist() {
        let added: [NotificationType] = [.n9, .n10, .n11]

        for type in added {
            XCTAssertFalse(NotificationType.allCases.contains(type), "hors réglages, hors programmation")
            XCTAssertTrue(type.aboutALovedOne)
            XCTAssertEqual(type.destination, .feed)
            XCTAssertFalse(type.silentAtNight)
            for key in [type.titleKey, type.bodyKey, type.settingTitleKey] {
                XCTAssertNotEqual(L10n.string(key), key, "Pas de texte pour la clé \(key)")
            }
        }
        XCTAssertFalse(NotificationType.n1.aboutALovedOne)
        XCTAssertFalse(NotificationType.c1.aboutALovedOne)
        XCTAssertFalse(NotificationType.n8.aboutALovedOne)
    }

    func testTheNewTypesSurviveTheFileOfTheCentre() throws {
        let item = NotificationFixtures.item("n9|u|uidB|u-a", type: .n9, at: now, state: .silent)

        let data = try JSONEncoder().encode([item])
        let back = try JSONDecoder().decode([NotificationItem].self, from: data)

        XCTAssertEqual(back, [item])
    }
}
