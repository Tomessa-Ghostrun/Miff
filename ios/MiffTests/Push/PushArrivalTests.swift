import UserNotifications
import XCTest
@testable import Miff

/// CNC-I (critère CNC-9 quater) : ce qu'un message du serveur apporte au centre quand l'app le voit passer (au premier plan, ou au toucher) : validé champ
/// par champ, jamais un prénom, une clé qui est celle du journal, une ligne seulement pour une Miff du compte et jamais pour sa propre update.
@MainActor
final class PushArrivalTests: XCTestCase {
    private let miff = "abcdefghijklmnopqrst"
    private let entry = "u-abcdefghijklmnopqrst-uidB-1799999000000"

    private func update(
        miffId: Any? = "abcdefghijklmnopqrst",
        author: Any? = "uidB",
        entryId: Any? = "u-abcdefghijklmnopqrst-uidB-1799999000000"
    ) -> [AnyHashable: Any] {
        var info: [AnyHashable: Any] = ["type": "circle_update", "aps": ["alert": "x"], "gcm.message_id": "0:12345%abc"]
        info["miffId"] = miffId
        info["authorUid"] = author
        info["entryId"] = entryId
        return info
    }

    private let startEntry = "as-abcdefghijklmnopqrst-uidB-1799999000000"
    private let endEntry = "ae-abcdefghijklmnopqrst-uidB-1799999000000"

    private func alert(event: String? = "start", entryId: Any? = nil) -> [AnyHashable: Any] {
        var info: [AnyHashable: Any] = ["type": "circle_alert", "miffId": miff, "authorUid": "uidB"]
        info["event"] = event
        info["entryId"] = entryId ?? (event == "end" ? endEntry : startEntry)
        return info
    }

    // MARK: - L'analyse

    func testAnUpdateMessageIsUnderstoodWithItsEntryAndItsMessage() {
        let arrival = PushArrivalParser.parse(update())

        XCTAssertEqual(arrival?.kind, .update)
        XCTAssertEqual(arrival?.miffId, miff)
        XCTAssertEqual(arrival?.authorUid, "uidB")
        XCTAssertEqual(arrival?.entryId, entry)
        XCTAssertEqual(arrival?.messageId, "0:12345%abc")
    }

    func testAnAlertStartAndEndAreUnderstood() {
        XCTAssertEqual(PushArrivalParser.parse(alert())?.kind, .alertStart)
        XCTAssertEqual(PushArrivalParser.parse(alert(event: "end"))?.kind, .alertEnd)
        XCTAssertNil(PushArrivalParser.parse(alert(event: "expired")), "l'expiration ne notifie jamais")
        XCTAssertNil(PushArrivalParser.parse(alert(event: nil)))
    }

    func testAServerWithoutJournalSendsNoEntryAndTheLineIsKeyedByTheMessage() {
        let absent = PushArrivalParser.parse(update(entryId: nil))
        let empty = PushArrivalParser.parse(update(entryId: ""))

        XCTAssertNotNil(absent)
        XCTAssertNil(absent?.entryId)
        XCTAssertNil(empty?.entryId)
        XCTAssertEqual(
            absent.map { CircleNewsKeys.pushId($0, receivedAtMillis: 5) }, "n9|u|uidB|0:12345%abc"
        )
    }

    func testAMessageThatDoesNotLookExactlyRightLeavesNoLine() {
        XCTAssertNil(PushArrivalParser.parse([:]))
        XCTAssertNil(PushArrivalParser.parse(["type": "n1", "miffId": miff, "authorUid": "uidB"]))
        XCTAssertNil(PushArrivalParser.parse(update(miffId: nil)))
        XCTAssertNil(PushArrivalParser.parse(update(miffId: "https://example.com")))
        XCTAssertNil(PushArrivalParser.parse(update(miffId: "../miffs/other")))
        XCTAssertNil(PushArrivalParser.parse(update(author: nil)))
        XCTAssertNil(PushArrivalParser.parse(update(author: "a/b")))
        XCTAssertNil(PushArrivalParser.parse(update(author: "")))
        XCTAssertNil(PushArrivalParser.parse(update(entryId: "u/../x")), "un entryId mal formé : message ignoré")
        XCTAssertNil(PushArrivalParser.parse(update(entryId: 12)))
        XCTAssertNil(PushArrivalParser.parse(update(entryId: String(repeating: "a", count: 129))))
    }

    func testAMessageIdThatIsNotAnIdentifierIsDroppedButTheMessageStays() {
        var info = update(entryId: nil)
        info["gcm.message_id"] = "a b/c"

        let arrival = PushArrivalParser.parse(info)

        XCTAssertNotNil(arrival)
        XCTAssertNil(arrival?.messageId)
    }

    func testAnArrivalShowsNothingInALog() throws {
        let arrival = try XCTUnwrap(PushArrivalParser.parse(update()))

        XCTAssertEqual("\(arrival)", "PushArrival(redacted)")
    }

    // MARK: - La ligne du centre

    func testTheLineOfAMessageHasTheKeyOfTheJournalAndNoName() throws {
        let arrival = try XCTUnwrap(PushArrivalParser.parse(update()))

        let line = NotificationJournal.item(for: arrival, nowMillis: 1_800_000_000_000, opened: false)

        XCTAssertEqual(line.id, "n9|u|uidB|\(entry)")
        XCTAssertEqual(line.type, .n9)
        XCTAssertEqual(line.miffId, miff)
        XCTAssertEqual(line.createdAtMillis, 1_800_000_000_000)
        XCTAssertEqual(line.state, .silent, "au premier plan, rien n'a été affiché")
        XCTAssertTrue(line.isUnread)
        XCTAssertEqual(NotificationJournal.entryId(ofCenterId: line.id), entry)
    }

    func testATouchedNotificationWasShownBytheSystem() throws {
        let arrival = try XCTUnwrap(PushArrivalParser.parse(update()))

        let line = NotificationJournal.item(for: arrival, nowMillis: 5, opened: true)

        XCTAssertEqual(line.state, .shown)
    }

    func testAnAlertStartAndEndAreTheTypesTenAndEleven() throws {
        let start = try XCTUnwrap(PushArrivalParser.parse(alert()))
        let end = try XCTUnwrap(PushArrivalParser.parse(alert(event: "end")))

        let startLine = NotificationJournal.item(for: start, nowMillis: 5, opened: false)
        let endLine = NotificationJournal.item(for: end, nowMillis: 5, opened: false)

        XCTAssertEqual(startLine.type, .n10)
        XCTAssertEqual(endLine.type, .n11)
        XCTAssertEqual(startLine.id, "al|u|uidB|start|as-abcdefghijklmnopqrst-uidB-1799999000000")
        XCTAssertEqual(endLine.id, "al|u|uidB|end|ae-abcdefghijklmnopqrst-uidB-1799999000000")
        XCTAssertNotEqual(startLine.id, endLine.id, "la fin est une ligne de plus, pas le début remplacé")
    }

    // MARK: - L'heure de la ligne : la livraison, pas le toucher

    private var paris: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Paris") ?? .current
        return calendar
    }

    func testALineIsDatedFromTheDeliveryOfTheNotificationAndFiledInTheRightSection() throws {
        let now = 1_800_000_000_000
        var arrival = try XCTUnwrap(PushArrivalParser.parse(update()))
        arrival.deliveredAtMillis = now - 20 * 3_600_000

        let line = NotificationJournal.item(for: arrival, nowMillis: now, opened: true)

        XCTAssertEqual(line.createdAtMillis, now - 20 * 3_600_000)
        let sections = NotificationGrouping.groups(of: [line], nowMillis: now, calendar: paris).map { $0.section }
        XCTAssertEqual(sections, [.yesterday], "touchée le lendemain, la ligne est rangée dans « Hier »")
    }

    func testADeliveryInTheFutureIsNotBelievedAndNoDeliveryMeansTheArrival() throws {
        let now = 1_800_000_000_000
        var arrival = try XCTUnwrap(PushArrivalParser.parse(update(entryId: nil)))

        XCTAssertEqual(NotificationJournal.item(for: arrival, nowMillis: now, opened: false).createdAtMillis, now)
        arrival.deliveredAtMillis = now + 3_600_000
        XCTAssertEqual(NotificationJournal.item(for: arrival, nowMillis: now, opened: false).createdAtMillis, now)
    }

    func testTheDelegateGivesTheTimeTheSystemDeliveredTheNotification() {
        let delegate = PushNotificationDelegate()
        var delivered: [Int?] = []
        delegate.onArrival = { arrival, _ in delivered.append(arrival.deliveredAtMillis) }
        let date = Date(timeIntervalSince1970: 1_799_000_000)

        _ = delegate.routeWillPresent(userInfo: update(), deliveredAt: date)
        _ = delegate.routeResponse(
            userInfo: update(), actionIdentifier: UNNotificationDefaultActionIdentifier, deliveredAt: date
        )
        _ = delegate.routeWillPresent(userInfo: update())

        XCTAssertEqual(delivered, [1_799_000_000_000, 1_799_000_000_000, nil])
    }

    // MARK: - Quand garder la ligne

    func testALineIsKeptOnlyForSomeoneSignedInAndNeverForYourOwnEvent() throws {
        let arrival = try XCTUnwrap(PushArrivalParser.parse(update()))

        XCTAssertTrue(PushArrivalRules.accepts(arrival, currentUid: "uidA", miffIds: [miff]))
        XCTAssertFalse(PushArrivalRules.accepts(arrival, currentUid: nil, miffIds: [miff]))
        XCTAssertFalse(PushArrivalRules.accepts(arrival, currentUid: "", miffIds: [miff]))
        XCTAssertFalse(PushArrivalRules.accepts(arrival, currentUid: "uidB", miffIds: [miff]), "sa propre update")
    }

    func testAMiffTheDirectoryKnowsAndThatIsNotTheAccountsLeavesNoLine() throws {
        let arrival = try XCTUnwrap(PushArrivalParser.parse(update()))

        XCTAssertFalse(PushArrivalRules.accepts(arrival, currentUid: "uidA", miffIds: ["autre"]))
        XCTAssertFalse(PushArrivalRules.accepts(arrival, currentUid: "uidA", miffIds: []))
    }

    func testBeforeTheDirectoryIsReadTheLineIsKeptAndHiddenUntilItIs() throws {
        let arrival = try XCTUnwrap(PushArrivalParser.parse(update()))

        XCTAssertTrue(PushArrivalRules.accepts(arrival, currentUid: "uidA", miffIds: nil))
    }

    // MARK: - Le délégué

    func testAnUpdateInTheForegroundWritesItsLineWithoutAnyBanner() {
        let delegate = PushNotificationDelegate()
        var arrivals: [(PushArrival, Bool)] = []
        delegate.onArrival = { arrivals.append(($0, $1)) }

        XCTAssertEqual(delegate.routeWillPresent(userInfo: update()), .suppress, "aucune bannière")
        XCTAssertEqual(arrivals.count, 1)
        XCTAssertEqual(arrivals.first?.0.kind, .update)
        XCTAssertEqual(arrivals.first?.1, false, "pas ouverte : l'app était devant")
    }

    func testATouchOnAnUpdateWritesAndReadsItsLineButADismissDoesNot() {
        let delegate = PushNotificationDelegate()
        var arrivals: [(PushArrival, Bool)] = []
        delegate.onArrival = { arrivals.append(($0, $1)) }

        _ = delegate.routeResponse(userInfo: update(), actionIdentifier: UNNotificationDismissActionIdentifier)
        XCTAssertTrue(arrivals.isEmpty, "un geste de rejet n'écrit rien")
        XCTAssertEqual(
            delegate.routeResponse(userInfo: update(), actionIdentifier: UNNotificationDefaultActionIdentifier), .open
        )
        XCTAssertEqual(arrivals.count, 1)
        XCTAssertEqual(arrivals.first?.1, true)
    }

    func testAForgedMessageOrALocalReminderWritesNoLine() {
        let delegate = PushNotificationDelegate()
        var count = 0
        delegate.onArrival = { _, _ in count += 1 }
        let tap = UNNotificationDefaultActionIdentifier

        _ = delegate.routeWillPresent(userInfo: update(miffId: "https://example.com"))
        _ = delegate.routeWillPresent(userInfo: ["type": "n1", "key": "k", "miffId": "m1"])
        _ = delegate.routeResponse(userInfo: update(author: "a/b"), actionIdentifier: tap)

        XCTAssertEqual(count, 0)
    }

    #if ALERT_DANGER
    func testAnAlertInTheForegroundAndAtATouchWritesItsLine() {
        let delegate = PushNotificationDelegate()
        var arrivals: [(PushArrival, Bool)] = []
        delegate.onArrival = { arrivals.append(($0, $1)) }

        let tap = UNNotificationDefaultActionIdentifier

        XCTAssertEqual(delegate.routeWillPresent(userInfo: alert()), .present, "l'alerte s'affiche, app ouverte")
        XCTAssertEqual(delegate.routeResponse(userInfo: alert(event: "end"), actionIdentifier: tap), .open)

        XCTAssertEqual(arrivals.map { $0.0.kind }, [.alertStart, .alertEnd])
        XCTAssertEqual(arrivals.map { $0.1 }, [false, true])
    }
    #endif
}
