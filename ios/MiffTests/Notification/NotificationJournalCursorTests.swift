import XCTest
@testable import Miff

/// CNC-I, relecture : le curseur du journal (il avance au-delà des documents ignorés, une date du futur ne le fige pas, il survit à un fichier qu'on ne peut pas
/// écrire) et le filtre des Miffs du compte (pas de point fantôme sur la cloche pour une ligne que l'écran ne dessine pas).
@MainActor
final class NotificationJournalCursorTests: XCTestCase {
    private let miff = "abcdefghijklmnopqrst"
    private let day = NotificationFixtures.day
    private let minute = 60_000

    /// Un fichier de curseur qui ne peut rien écrire (disque plein, protection des données).
    private final class UnwritableCursorStore: NotificationJournalCursorStoring {
        func cursor(uid: String) -> Int? {
            nil
        }

        func setCursor(_ millis: Int, uid: String) {}

        func clear() {}
    }

    private struct Rig {
        let sync: NotificationJournalSync
        let center: NotificationCenterService
        let gateway: FakeJournalGateway
        let clock: JournalTestClock
    }

    private func makeRig(cursors: NotificationJournalCursorStoring) -> Rig {
        let clock = JournalTestClock()
        let gateway = FakeJournalGateway()
        let center = NotificationCenterService(
            store: MemoryNotificationCenterStore(), currentUid: { clock.uid }, nowMillis: { clock.now }
        )
        let sync = NotificationJournalSync(
            gateway: gateway, cursors: cursors, center: center, currentUid: { clock.uid }, nowMillis: { clock.now }
        )
        return Rig(sync: sync, center: center, gateway: gateway, clock: clock)
    }

    private func entry(_ rig: Rig, age: Int = 1_000) -> JournalEntry {
        JournalEntry(
            entryId: "u-abcdefghijklmnopqrst-uidB-1", kind: .update, miffId: miff, actorUid: "uidB",
            createdAtMillis: rig.clock.now - age, expireAtMillis: rig.clock.now + 29 * day
        )
    }

    // MARK: - Le curseur avance au-delà de ce qui est ignoré

    func testTheCursorGoesPastADocumentThatWasIgnored() async {
        let cursors = MemoryJournalCursorStore()
        let rig = makeRig(cursors: cursors)
        rig.gateway.answer = .loaded([], newestSeenMillis: rig.clock.now - 5_000)

        await rig.sync.sync(.foreground)

        XCTAssertEqual(cursors.cursor(uid: "uidA"), rig.clock.now - 5_000, "jamais relu sans fin")
        XCTAssertTrue(rig.center.history.isEmpty)
    }

    func testADateMoreThanADayInTheFutureDoesNotFreezeTheCursor() async {
        let cursors = MemoryJournalCursorStore()
        let rig = makeRig(cursors: cursors)
        rig.gateway.answer = .loaded([], newestSeenMillis: rig.clock.now + 10 * day)

        await rig.sync.sync(.foreground)

        XCTAssertNil(cursors.cursor(uid: "uidA"))
    }

    func testTheCursorToleratesAFewHoursOfClockDifference() async {
        let cursors = MemoryJournalCursorStore()
        let rig = makeRig(cursors: cursors)
        let ahead = rig.clock.now + 3 * 60 * minute
        rig.gateway.answer = .loaded([], newestSeenMillis: ahead)

        await rig.sync.sync(.foreground)

        XCTAssertEqual(cursors.cursor(uid: "uidA"), ahead)
    }

    func testTheNextRequestStartsFromTheCursorEvenWhenTheFileCannotBeWritten() async {
        let rig = makeRig(cursors: UnwritableCursorStore())
        let seen = entry(rig, age: 10 * minute)
        rig.gateway.answer = .loaded([seen])
        await rig.sync.sync(.foreground)
        rig.clock.now += 2 * minute

        await rig.sync.sync(.foreground)

        XCTAssertEqual(rig.gateway.readCalls, [nil, seen.createdAtMillis - 2 * minute], "jamais une relecture complète")
    }

    func testTheMemoryCursorGoesWithTheSession() async {
        let rig = makeRig(cursors: UnwritableCursorStore())
        rig.gateway.answer = .loaded([entry(rig)])
        await rig.sync.sync(.foreground)

        rig.sync.purge()
        rig.clock.now += 2 * minute
        await rig.sync.sync(.foreground)

        XCTAssertEqual(rig.gateway.readCalls, [nil, nil])
    }

    // MARK: - Pas de point fantôme

    func testAnEntryOfAMiffThatIsNotTheAccountsLeavesNoLineAndNoDotOnTheBell() async {
        let cursors = MemoryJournalCursorStore()
        let rig = makeRig(cursors: cursors)
        rig.sync.isMyMiff = { _ in false }
        let gone = entry(rig)
        rig.gateway.answer = .loaded([gone])

        await rig.sync.sync(.foreground)

        XCTAssertTrue(rig.center.history.isEmpty)
        XCTAssertFalse(rig.center.hasUnread, "réinstallation avec une Miff quittée : pas de point")
        XCTAssertEqual(cursors.cursor(uid: "uidA"), gone.createdAtMillis, "le curseur avance quand même")
    }

    func testAnEntryOfAMiffOfTheAccountStaysALine() async {
        let rig = makeRig(cursors: MemoryJournalCursorStore())
        rig.sync.isMyMiff = { [miff] id in id == miff }
        rig.gateway.answer = .loaded([entry(rig)])

        await rig.sync.sync(.foreground)

        XCTAssertEqual(rig.center.history.items.count, 1)
    }

    func testTheLinesOfAMiffThatIsGoneAreRemovedOnceTheDirectoryIsConfirmed() {
        let clock = JournalTestClock()
        let center = NotificationCenterService(
            store: MemoryNotificationCenterStore(), currentUid: { clock.uid }, nowMillis: { clock.now }
        )
        let faces = CenterFaces(reader: SilentFaceReader())
        let watcher = LovedOneLinesWatcher(center: center, faces: faces)
        center.record(NotificationFixtures.item("n9|u|uidB|u-a", type: .n9, miffId: "gone", at: clock.now))
        center.record(NotificationFixtures.item("n9|u|uidB|u-b", type: .n9, miffId: "mine", at: clock.now - 1))
        center.record(NotificationFixtures.item("n1|gone|me|1", type: .n1, miffId: "gone", at: clock.now - 2))

        watcher.onMiffsKnown(["mine"])

        XCTAssertEqual(center.history.items.map { $0.id }, ["n9|u|uidB|u-b", "n1|gone|me|1"])
        XCTAssertEqual(center.unreadCount, 2, "plus de point pour la ligne d'une Miff qui n'est plus la sienne")
    }
}
