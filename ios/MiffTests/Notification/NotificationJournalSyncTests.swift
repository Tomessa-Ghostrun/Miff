import XCTest
@testable import Miff

/// CNC-I : la lecture du journal du serveur, SANS serveur (une passerelle en mémoire) : la première synchronisation, le curseur et son recouvrement, la
/// fusion sans doublon avec les lignes que les messages écrivent (dans les deux sens), l'absence du journal (refus des règles, hors ligne, build sans la
/// lecture) sans erreur ni boucle, le rythme (une par minute), le compte qui change, la suppression avec la ligne.
@MainActor
final class NotificationJournalSyncTests: XCTestCase {
    private let miff = "abcdefghijklmnopqrst"
    private let minute = 60_000
    private let day = NotificationFixtures.day
    private let hour = NotificationFixtures.hour

    private struct Rig {
        let sync: NotificationJournalSync
        let center: NotificationCenterService
        let gateway: FakeJournalGateway
        let cursors: MemoryJournalCursorStore
        let clock: JournalTestClock
    }

    private func makeRig(enabled: Bool = true) -> Rig {
        let clock = JournalTestClock()
        let gateway = FakeJournalGateway()
        let cursors = MemoryJournalCursorStore()
        let center = NotificationCenterService(
            store: MemoryNotificationCenterStore(), currentUid: { clock.uid }, nowMillis: { clock.now }
        )
        let sync = NotificationJournalSync(
            gateway: enabled ? gateway : nil,
            cursors: cursors,
            center: center,
            currentUid: { clock.uid },
            nowMillis: { clock.now }
        )
        sync.sessionValid = { _ in clock.sessionValid }
        return Rig(sync: sync, center: center, gateway: gateway, cursors: cursors, clock: clock)
    }

    private func entry(
        _ rig: Rig,
        kind: NotificationJournalKind = .update,
        id: String = "u-abcdefghijklmnopqrst-uidB-1",
        actor: String = "uidB",
        age: Int = 0
    ) -> JournalEntry {
        JournalEntry(
            entryId: id, kind: kind, miffId: miff, actorUid: actor,
            createdAtMillis: rig.clock.now - age, expireAtMillis: rig.clock.now + 29 * day
        )
    }

    private func arrival(entryId: String = "u-abcdefghijklmnopqrst-uidB-1") -> PushArrival {
        PushArrival(kind: .update, miffId: miff, authorUid: "uidB", entryId: entryId, messageId: "m1")
    }

    // MARK: - La première synchronisation

    func testTheFirstSyncReadsEverythingAndBringsTheLinesInUnreadAndSilent() async throws {
        let rig = makeRig()
        rig.gateway.answer = .loaded([entry(rig, age: hour)])

        let done = await rig.sync.sync(.foreground)

        XCTAssertTrue(done)
        XCTAssertEqual(rig.gateway.readCalls, [nil], "sans curseur : tout le journal")
        let line = try XCTUnwrap(rig.center.history.items.first)
        XCTAssertEqual(rig.center.history.items.count, 1)
        XCTAssertEqual(line.type, .n9)
        XCTAssertEqual(line.state, .silent)
        XCTAssertTrue(line.isUnread)
        XCTAssertTrue(rig.center.hasUnread, "le point de la cloche")
        XCTAssertEqual(rig.cursors.cursor(uid: "uidA"), rig.clock.now - hour)
    }

    func testOnTheFirstSyncWhatIsOlderThanADayArrivesAlreadyRead() async {
        let rig = makeRig()
        rig.gateway.answer = .loaded([entry(rig, id: "u-a", age: 2 * day), entry(rig, id: "u-b", age: hour)])

        await rig.sync.sync(.foreground)

        XCTAssertEqual(rig.center.history.items.count, 2)
        XCTAssertEqual(rig.center.unreadCount, 1, "aucun point pour le passé : seule la ligne des 24 h est non lue")
    }

    func testAnEmptyJournalIsASuccessThatWritesNothing() async {
        let rig = makeRig()

        let done = await rig.sync.sync(.foreground)

        XCTAssertTrue(done, "une réponse vide est une réponse")
        XCTAssertTrue(rig.center.history.isEmpty)
        XCTAssertNil(rig.cursors.cursor(uid: "uidA"), "rien de lu : le curseur ne bouge pas")
    }

    // MARK: - Le curseur

    func testTheNextRequestStartsTwoMinutesBeforeTheNewestEntryAlreadySeen() async {
        let rig = makeRig()
        let first = entry(rig, age: 10 * minute)
        rig.gateway.answer = .loaded([first])
        await rig.sync.sync(.foreground)
        rig.clock.now += 2 * minute

        await rig.sync.sync(.foreground)

        XCTAssertEqual(rig.gateway.readCalls, [nil, first.createdAtMillis - 2 * minute])
    }

    func testALaterEntryThatIsOldIsNotMarkedReadBecauseOnlyTheFirstSyncDoesThat() async {
        let rig = makeRig()
        await rig.sync.sync(.foreground)
        rig.gateway.answer = .loaded([entry(rig, id: "u-late", age: 3 * day)])
        rig.clock.now += 2 * minute
        rig.cursors.setCursor(rig.clock.now - 10 * minute, uid: "uidA")

        await rig.sync.sync(.foreground)

        XCTAssertEqual(rig.center.unreadCount, 1)
    }

    // MARK: - Aucun doublon

    func testAMessageSeenFirstAndTheJournalAfterMakeOneLine() async {
        let rig = makeRig()
        let message = NotificationJournal.item(for: arrival(), nowMillis: rig.clock.now, opened: false)
        rig.center.record(message)
        rig.gateway.answer = .loaded([entry(rig, age: 1_000)])

        await rig.sync.sync(.foreground)

        XCTAssertEqual(rig.center.history.items.count, 1, "la même clé : une seule ligne")
    }

    func testTheJournalFirstAndTheMessageAfterMakeOneLine() async {
        let rig = makeRig()
        rig.gateway.answer = .loaded([entry(rig, age: 1_000)])
        await rig.sync.sync(.foreground)

        rig.center.record(NotificationJournal.item(for: arrival(), nowMillis: rig.clock.now, opened: true))

        XCTAssertEqual(rig.center.history.items.count, 1)
    }

    func testTheOverlapOfTheNextRequestDoesNotDoubleALine() async {
        let rig = makeRig()
        rig.gateway.answer = .loaded([entry(rig, age: 10 * minute)])
        await rig.sync.sync(.foreground)
        rig.clock.now += 2 * minute

        await rig.sync.sync(.foreground)

        XCTAssertEqual(rig.center.history.items.count, 1, "le serveur rend encore l'entrée dans le recouvrement")
    }

    func testTwoPeopleMakeTwoLinesEachWithItsOwnKey() async {
        let rig = makeRig()
        rig.gateway.answer = .loaded([
            entry(rig, id: "u-m-uidB-1", actor: "uidB"),
            entry(rig, id: "u-m-uidC-1", actor: "uidC")
        ])

        await rig.sync.sync(.foreground)

        XCTAssertEqual(rig.center.history.items.count, 2, "une ligne par personne, aucun regroupement")
    }

    // MARK: - Ce qui n'est jamais une ligne

    func testYourOwnEventIsNeverALine() async {
        let rig = makeRig()
        rig.gateway.answer = .loaded([entry(rig, actor: "uidA")])

        await rig.sync.sync(.foreground)

        XCTAssertTrue(rig.center.history.isEmpty)
    }

    func testAnAlertStartAndEndAreLines() async {
        let rig = makeRig()
        rig.gateway.answer = .loaded([
            entry(rig, kind: .alertStart, id: "as-m-uidB-1", age: 2_000),
            entry(rig, kind: .alertEnd, id: "ae-m-uidB-1", age: 1_000)
        ])

        await rig.sync.sync(.foreground)

        XCTAssertEqual(Set(rig.center.history.items.map { $0.type }), [.n10, .n11])
    }

    // MARK: - L'absence du journal : calme, jamais en boucle (le serveur n'est pas encore déployé)

    func testRefusedByTheRulesIsCalmWritesNothingAndIsNotRetriedForThirtyMinutes() async {
        let rig = makeRig()
        rig.gateway.answer = .refused

        let first = await rig.sync.sync(.foreground)
        rig.clock.now += 10 * minute
        let tooSoon = await rig.sync.sync(.centerOpened)

        XCTAssertFalse(first)
        XCTAssertFalse(tooSoon)
        XCTAssertEqual(rig.gateway.readCalls.count, 1, "une requête refusée se paie : pas d'insistance")
        XCTAssertTrue(rig.center.history.isEmpty)
        XCTAssertNil(rig.cursors.cursor(uid: "uidA"))

        rig.clock.now += 21 * minute
        await rig.sync.sync(.foreground)
        XCTAssertEqual(rig.gateway.readCalls.count, 2, "au bout de 30 minutes, une nouvelle tentative")
    }

    func testOfflineIsCalmKeepsTheLinesOfThePhoneAndTriesAgainSoonButNotInALoop() async {
        let rig = makeRig()
        rig.center.record(NotificationFixtures.item("n1|a", at: rig.clock.now))
        rig.gateway.answer = .unavailable

        let first = await rig.sync.sync(.foreground)
        let immediately = await rig.sync.sync(.foreground)
        rig.clock.now += 11_000
        let later = await rig.sync.sync(.foreground)

        XCTAssertFalse(first)
        XCTAssertFalse(immediately)
        XCTAssertFalse(later)
        XCTAssertEqual(rig.gateway.readCalls.count, 2, "pas de boucle : une nouvelle tentative après 10 secondes")
        XCTAssertEqual(rig.center.history.items.map { $0.id }, ["n1|a"], "les lignes du téléphone restent")
        XCTAssertNil(rig.cursors.cursor(uid: "uidA"), "le curseur ne bouge pas")
    }

    func testBackOnlineTheMissedLinesComeInWithoutADouble() async {
        let rig = makeRig()
        rig.gateway.answer = .unavailable
        await rig.sync.sync(.foreground)
        rig.gateway.answer = .loaded([entry(rig, age: 5_000)])
        rig.clock.now += 11_000

        let done = await rig.sync.sync(.foreground)

        XCTAssertTrue(done)
        XCTAssertEqual(rig.center.history.items.count, 1)
    }

    func testABuildWithoutTheJournalNeverReadsNorDeletesAnything() async {
        let rig = makeRig(enabled: false)

        let done = await rig.sync.sync(.foreground)
        rig.sync.deleteOnServer(centerIds: ["n9|u|uidB|u-abcdefghijklmnopqrst-uidB-1"])
        await rig.sync.deleteAllOnServer()

        XCTAssertFalse(rig.sync.isEnabled)
        XCTAssertFalse(done)
        XCTAssertTrue(rig.gateway.readCalls.isEmpty)
        XCTAssertTrue(rig.gateway.deletedBatches.isEmpty)
        XCTAssertEqual(rig.gateway.deleteAllCalls, 0)
        XCTAssertTrue(rig.center.history.isEmpty)
    }

    // MARK: - Le rythme

    func testAtMostOneRequestAMinuteApartFromAMessage() async {
        let rig = makeRig()

        await rig.sync.sync(.foreground)
        rig.clock.now += 30_000
        let tooSoon = await rig.sync.sync(.centerOpened)
        rig.clock.now += 31_000
        let allowed = await rig.sync.sync(.centerOpened)

        XCTAssertFalse(tooSoon)
        XCTAssertTrue(allowed)
        XCTAssertEqual(rig.gateway.readCalls.count, 2)
    }

    func testAMessageBringsARequestAfterTenSecondsNotAMinute() async {
        let rig = makeRig()

        await rig.sync.sync(.pushReceived)
        rig.clock.now += 5_000
        let burst = await rig.sync.sync(.pushReceived)
        rig.clock.now += 6_000
        let allowed = await rig.sync.sync(.pushReceived)

        XCTAssertFalse(burst, "jamais une rafale")
        XCTAssertTrue(allowed)
    }

    // MARK: - Le compte

    func testNobodySignedInNothingIsAsked() async {
        let rig = makeRig()
        rig.clock.uid = nil

        let done = await rig.sync.sync(.foreground)

        XCTAssertFalse(done)
        XCTAssertTrue(rig.gateway.readCalls.isEmpty)
    }

    func testAnEndingSessionIsNotRead() async {
        let rig = makeRig()
        rig.clock.sessionValid = false

        let done = await rig.sync.sync(.foreground)

        XCTAssertFalse(done)
        XCTAssertTrue(rig.gateway.readCalls.isEmpty)
    }

    func testIfThePersonSignsOutWhileTheServerAnswersNothingIsWrittenForTheNextOne() async {
        let rig = makeRig()
        rig.gateway.answer = .loaded([entry(rig)])
        let clock = rig.clock
        rig.gateway.whileReading = { clock.uid = "uidZ" }

        let done = await rig.sync.sync(.foreground)

        XCTAssertFalse(done)
        XCTAssertTrue(rig.center.history.isEmpty)
        XCTAssertNil(rig.cursors.cursor(uid: "uidA"))
        XCTAssertNil(rig.cursors.cursor(uid: "uidZ"))
    }

    func testSigningOutForgetsTheCursorSoTheNextAccountStartsFromNothing() async {
        let rig = makeRig()
        rig.gateway.answer = .loaded([entry(rig)])
        await rig.sync.sync(.foreground)

        rig.sync.purge()
        rig.clock.now += 2 * minute
        await rig.sync.sync(.foreground)

        XCTAssertEqual(rig.cursors.clearCalls, 1)
        XCTAssertEqual(rig.gateway.readCalls, [nil, nil], "repart de zéro")
    }

    // MARK: - La suppression avec la ligne (CNC-15)

    func testDeletingALineDeletesItsEntryOnTheServerAndNothingForALineThatHadNone() async {
        let rig = makeRig()
        let key = "n9|u|uidB|u-abcdefghijklmnopqrst-uidB-1"

        rig.sync.deleteOnServer(centerIds: [key, "n1|miff|moi|1", "c1|m|uid|a|b|1"])

        XCTAssertEqual(rig.gateway.deletedBatches, [["u-abcdefghijklmnopqrst-uidB-1"]])
    }

    func testALineThatHasJustBeenDeletedDoesNotComeBackWithTheOverlap() async {
        let rig = makeRig()
        rig.gateway.answer = .loaded([entry(rig, age: 10 * minute)])
        await rig.sync.sync(.foreground)
        let key = "n9|u|uidB|u-abcdefghijklmnopqrst-uidB-1"
        rig.center.delete(id: key)
        rig.sync.deleteOnServer(centerIds: [key])
        rig.clock.now += 2 * minute

        await rig.sync.sync(.foreground)

        XCTAssertTrue(rig.center.history.isEmpty, "l'entrée n'est peut-être pas encore supprimée (hors ligne)")
    }

    func testClearAllAsksTheServerToEmptyTheWholeJournal() async {
        let rig = makeRig()

        await rig.sync.deleteAllOnServer()

        XCTAssertEqual(rig.gateway.deleteAllCalls, 1)
    }

    func testNobodySignedInNothingIsDeletedOnTheServer() async {
        let rig = makeRig()
        rig.clock.uid = nil

        rig.sync.deleteOnServer(centerIds: ["n9|u|uidB|u-abcdefghijklmnopqrst-uidB-1"])
        await rig.sync.deleteAllOnServer()

        XCTAssertTrue(rig.gateway.deletedBatches.isEmpty)
        XCTAssertEqual(rig.gateway.deleteAllCalls, 0)
    }
}
