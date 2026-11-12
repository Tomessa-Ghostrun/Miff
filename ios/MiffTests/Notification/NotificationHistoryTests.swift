import XCTest
@testable import Miff

/// Le centre de notifications local : conservation de 30 jours ou 100 entrées (le premier atteint), ordre, lu / non lu, suppression,
/// groupes « Aujourd'hui / Hier / Plus ancien » (ticket Notifications §7 et §20.4). Tout aux bornes.
final class NotificationHistoryTests: XCTestCase {
    private let now = 1_800_000_000_000
    private let hour = NotificationFixtures.hour
    private let day = NotificationFixtures.day

    private func item(_ id: String, ageMillis: Int, readAt: Int? = nil) -> NotificationItem {
        NotificationFixtures.item(id, at: now - ageMillis, readAt: readAt)
    }

    // MARK: - Conservation

    func testAnEntryExactlyThirtyDaysOldIsKeptAndOneMillisecondOlderIsDropped() {
        let history = NotificationHistory(
            items: [item("kept", ageMillis: 30 * day), item("dropped", ageMillis: 30 * day + 1)],
            nowMillis: now
        )

        XCTAssertEqual(history.items.map { $0.id }, ["kept"])
    }

    func testAtMostAHundredEntriesAreKeptAndTheOldestOnesGo() {
        let items = (0..<101).map { item("n1|\($0)", ageMillis: $0 * 1_000) }

        let history = NotificationHistory(items: items, nowMillis: now)

        XCTAssertEqual(history.items.count, 100)
        XCTAssertEqual(history.items.first?.id, "n1|0", "le plus récent d'abord")
        XCTAssertFalse(history.contains(id: "n1|100"), "la 101e, la plus ancienne, est partie")
    }

    func testExactlyAHundredEntriesAreAllKept() {
        let items = (0..<100).map { item("n1|\($0)", ageMillis: $0 * 1_000) }

        XCTAssertEqual(NotificationHistory(items: items, nowMillis: now).items.count, 100)
    }

    func testAddingAHundredAndFirstEntryDropsTheOldest() {
        let items = (1...100).map { item("n1|\($0)", ageMillis: $0 * 1_000) }
        let full = NotificationHistory(items: items, nowMillis: now)

        let updated = full.adding(item("fresh", ageMillis: 0), nowMillis: now)

        XCTAssertEqual(updated.items.count, 100)
        XCTAssertEqual(updated.items.first?.id, "fresh")
        XCTAssertFalse(updated.contains(id: "n1|100"))
    }

    func testTheSameKeyIsNeverRecordedTwice() {
        let first = NotificationHistory(nowMillis: now).adding(item("n1|a", ageMillis: 0), nowMillis: now)

        let again = first.adding(item("n1|a", ageMillis: 5_000), nowMillis: now)

        XCTAssertEqual(again, first)
        XCTAssertEqual(again.items.count, 1)
    }

    func testEntriesAreSortedNewestFirstAndTiesAreBrokenByTheKey() {
        let history = NotificationHistory(
            items: [item("b", ageMillis: 1_000), item("c", ageMillis: 0), item("a", ageMillis: 1_000)],
            nowMillis: now
        )

        XCTAssertEqual(history.items.map { $0.id }, ["c", "a", "b"])
    }

    func testAnEmptyHistoryIsEmptyAndHasNothingUnread() {
        let history = NotificationHistory(nowMillis: now)

        XCTAssertTrue(history.isEmpty)
        XCTAssertEqual(history.unreadCount, 0)
    }

    // MARK: - Lu / non lu, suppression

    func testReadAndUnreadCountsFollowTheActions() {
        let history = NotificationHistory(
            items: [item("a", ageMillis: 0), item("b", ageMillis: 1_000), item("c", ageMillis: 2_000, readAt: now)],
            nowMillis: now
        )
        XCTAssertEqual(history.unreadCount, 2)

        let oneRead = history.markingRead(id: "a", nowMillis: now)
        XCTAssertEqual(oneRead.unreadCount, 1)
        XCTAssertEqual(oneRead.items.first { $0.id == "a" }?.readAtMillis, now)

        let allRead = oneRead.markingAllRead(nowMillis: now + 1)
        XCTAssertEqual(allRead.unreadCount, 0)
        let keptTime = allRead.items.first { $0.id == "a" }?.readAtMillis
        XCTAssertEqual(keptTime, now, "une entrée déjà lue garde son heure de lecture")
    }

    func testMarkingAnUnknownKeyReadChangesNothing() {
        let history = NotificationHistory(items: [item("a", ageMillis: 0)], nowMillis: now)

        XCTAssertEqual(history.markingRead(id: "zzz", nowMillis: now), history)
    }

    func testDeletingRemovesOnlyThatEntry() {
        let history = NotificationHistory(items: [item("a", ageMillis: 0), item("b", ageMillis: 1)], nowMillis: now)

        XCTAssertEqual(history.deleting(id: "a", nowMillis: now).items.map { $0.id }, ["b"])
        XCTAssertEqual(history.deleting(id: "zzz", nowMillis: now), history)
    }

    func testPruningDropsWhatHasBecomeTooOld() {
        let history = NotificationHistory(items: [item("old", ageMillis: 30 * day)], nowMillis: now)

        XCTAssertTrue(history.pruned(nowMillis: now + 1).isEmpty)
        XCTAssertFalse(history.pruned(nowMillis: now).isEmpty)
    }

    // MARK: - Groupes (jour local)

    private var paris: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Paris") ?? .current
        return calendar
    }

    private func millis(day: Int, hour: Int, minute: Int = 0, second: Int = 0) -> Int {
        let parts = DateComponents(
            calendar: paris, timeZone: paris.timeZone, year: 2026, month: 11, day: day,
            hour: hour, minute: minute, second: second
        )
        return Int((parts.date?.timeIntervalSince1970 ?? 0) * 1000)
    }

    func testTheSectionsFollowTheLocalDayAtMidnight() {
        let current = millis(day: 8, hour: 15)
        let sections: [(Int, NotificationSection)] = [
            (millis(day: 8, hour: 0), .today),
            (millis(day: 7, hour: 23, minute: 59, second: 59), .yesterday),
            (millis(day: 7, hour: 0), .yesterday),
            (millis(day: 6, hour: 23, minute: 59, second: 59), .older),
            (millis(day: 1, hour: 12), .older)
        ]

        for (created, expected) in sections {
            XCTAssertEqual(
                NotificationGrouping.section(createdAtMillis: created, nowMillis: current, calendar: paris),
                expected,
                "\(created)"
            )
        }
    }

    func testAnEntryDatedInTheFutureCountsAsToday() {
        let current = millis(day: 8, hour: 15)

        XCTAssertEqual(
            NotificationGrouping.section(createdAtMillis: current + 3 * day, nowMillis: current, calendar: paris),
            .today
        )
    }

    func testGroupsKeepTheOrderAndSkipTheEmptyOnes() {
        let current = millis(day: 8, hour: 15)
        let items = [
            NotificationFixtures.item("t", at: millis(day: 8, hour: 14)),
            NotificationFixtures.item("y", at: millis(day: 7, hour: 20)),
            NotificationFixtures.item("o", at: millis(day: 2, hour: 9))
        ]

        let groups = NotificationGrouping.groups(of: items, nowMillis: current, calendar: paris)

        XCTAssertEqual(groups.map { $0.section }, [.today, .yesterday, .older])
        XCTAssertEqual(groups.map { $0.items.map { $0.id } }, [["t"], ["y"], ["o"]])
        let onlyOlder = NotificationGrouping.groups(of: [items[2]], nowMillis: current, calendar: paris)
        XCTAssertEqual(onlyOlder.map { $0.section }, [.older])
        XCTAssertTrue(NotificationGrouping.groups(of: [], nowMillis: current, calendar: paris).isEmpty)
    }

    func testTheTimeLineUsesTheSystemFormatterInTheLocalTimeZone() {
        let created = millis(day: 8, hour: 13, minute: 40)
        let fr = Locale(identifier: "fr_FR")

        let today = NotificationTimeText.text(
            createdAtMillis: created, section: .today, locale: fr, timeZone: paris.timeZone
        )
        let older = NotificationTimeText.text(
            createdAtMillis: created, section: .older, locale: fr, timeZone: paris.timeZone
        )

        XCTAssertTrue(today.contains("13"), today)
        XCTAssertTrue(today.contains("40"), today)
        XCTAssertFalse(today.contains("2026"), "l'heure seule pour Aujourd'hui")
        XCTAssertTrue(older.contains("2026"), older)
        XCTAssertTrue(older.contains("40"), older)
    }

    func testTheSpokenLabelSaysTheTitleTheTimeAndWhetherItIsUnread() {
        let unread = NotificationTimeText.spokenLabel(title: "Tu es passé orange", time: "13:40", unread: true)
        let read = NotificationTimeText.spokenLabel(title: "Tu es passé orange", time: "13:40", unread: false)

        XCTAssertTrue(unread.contains("Tu es passé orange"))
        XCTAssertTrue(unread.contains("13:40"))
        XCTAssertNotEqual(unread, read, "l'état n'est jamais porté par la seule pastille")
    }
}
