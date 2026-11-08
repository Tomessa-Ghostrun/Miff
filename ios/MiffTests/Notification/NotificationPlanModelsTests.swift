import XCTest
@testable import Miff

/// Les modèles autour du planificateur : l'instantané, le registre, les identifiants de demandes, le décorateur du suivi des lieux, le fichier
/// de l'instantané, et les demandes d'onglet qui attendent la coquille.
final class NotificationPlanModelsTests: XCTestCase {
    private let now = 1_800_000_000_000

    // MARK: - L'instantané

    private func status(_ presence: PresenceValue?, at presenceAt: Int?, update: Int? = nil) -> MemberStatus {
        MemberStatus(
            presence: presence, placeId: "p1", presenceAtMillis: presenceAt, lastUpdateAtMillis: update,
            hasPhoto: true, batteryPct: 40, locationAccess: nil, updatedAtMillis: nil
        )
    }

    func testTheSnapshotKeepsOnlyColorFactsNoPlaceNoPhotoNoBattery() throws {
        let snapshot = NotificationPlanSnapshot.make(
            miffId: "miff-1",
            myUid: "me",
            members: [(uid: "me", sharing: true), (uid: "x", sharing: false), (uid: "y", sharing: true)],
            statuses: ["me": status(.out, at: now, update: now - 1_000), "x": status(.inTrusted, at: now)],
            savedAtMillis: now
        )

        XCTAssertEqual(snapshot.members.map { $0.uid }, ["me", "x", "y"])
        XCTAssertEqual(snapshot.members[0].presence, "out")
        XCTAssertEqual(snapshot.members[0].lastUpdateAtMillis, now - 1_000)
        XCTAssertFalse(snapshot.members[1].sharing)
        XCTAssertNil(snapshot.members[2].presence, "pas de statut : neutre")
        let data = try JSONEncoder().encode(snapshot)
        let text = String(decoding: data, as: UTF8.self)
        for forbidden in ["placeId", "p1", "hasPhoto", "battery", "lat", "lng", "jpeg"] {
            XCTAssertFalse(text.contains(forbidden), forbidden)
        }
        XCTAssertEqual(snapshot.description, "NotificationPlanSnapshot(redacted)")
        XCTAssertEqual(snapshot.members[0].description, "PlanMember(redacted)")
    }

    func testOnlyMyLineChangesWhenThePresenceOrAnUpdateIsDetected() {
        let snapshot = NotificationPlanSnapshot.make(
            miffId: "miff-1",
            myUid: "me",
            members: [(uid: "me", sharing: true), (uid: "x", sharing: true)],
            statuses: [
                "me": status(.inTrusted, at: now - 5_000, update: now - 5_000),
                "x": status(.out, at: now - 9_000)
            ],
            savedAtMillis: now - 60_000
        )

        let saved = now - 60_000
        let left = snapshot.withMyFacts(
            presence: .out, presenceAtMillis: now, setLastUpdate: false, savedAtMillis: saved
        )
        let arrived = snapshot.withMyFacts(
            presence: .inTrusted, presenceAtMillis: now, setLastUpdate: true, savedAtMillis: 1
        )
        let updated = left.withMyUpdate(atMillis: now + 10, savedAtMillis: saved)

        XCTAssertEqual(left.members[0].presence, "out")
        XCTAssertEqual(left.members[0].lastUpdateAtMillis, now - 5_000, "un départ ne touche pas la dernière update")
        XCTAssertEqual(left.members[1], snapshot.members[1], "les autres ne bougent pas")
        XCTAssertEqual(left.savedAtMillis, now - 60_000, "la fraîcheur des autres ne change pas")
        XCTAssertEqual(arrived.members[0].lastUpdateAtMillis, now, "une arrivée est aussi une update")
        XCTAssertEqual(updated.members[0].lastUpdateAtMillis, now + 10)
        XCTAssertEqual(updated.members[0].presence, "out")
    }

    func testMyLineIsCreatedWhenTheFeedHasNotAnsweredYet() {
        let snapshot = NotificationPlanSnapshot(miffId: "miff-1", myUid: "me", members: [], savedAtMillis: now)

        let updated = snapshot.withMyFacts(
            presence: .out, presenceAtMillis: now, setLastUpdate: false, savedAtMillis: now
        )

        XCTAssertEqual(updated.members.map { $0.uid }, ["me"])
        XCTAssertTrue(updated.members[0].sharing)
    }

    func testThePlanMemberBuildsTheFactsOfTheColor() {
        let member = PlanMember(
            uid: "x", sharing: true, presence: "in_risk", presenceAtMillis: 5, lastUpdateAtMillis: 7
        )
        let unknown = PlanMember(
            uid: "y", sharing: true, presence: "nonsense", presenceAtMillis: 5, lastUpdateAtMillis: nil
        )

        XCTAssertEqual(member.facts.presence, .inRisk)
        XCTAssertEqual(member.facts.lastUpdateAtMillis, 7)
        XCTAssertNil(unknown.facts.presence, "une valeur inconnue est neutre, jamais une supposition")
    }

    // MARK: - Le registre

    func testTheLedgerForgetsKeysAfterFortyEightHoursExactly() {
        let hours48 = NotificationPlanner.keysRetentionMillis
        let ledger = NotificationLedger()
            .marking(key: "old", atMillis: now - hours48 - 1)
            .marking(key: "edge", atMillis: now - hours48)
            .marking(key: "fresh", atMillis: now)

        XCTAssertEqual(ledger.pruned(nowMillis: now).notifiedKeys, ["edge", "fresh"])
        XCTAssertEqual(hours48, 48 * 3_600_000)
    }

    func testTheLedgerKeepsWhatIsScheduledAndCountsFollowUps() {
        let record = ScheduledRecord(
            type: .n3, miffId: "miff-1", key: "n3|miff-1|1", fireAtMillis: now, quiet: false, language: "fr"
        )
        var ledger = NotificationLedger().scheduling(record, identifier: "miff.notif.n3.miff-1")
        XCTAssertEqual(ledger.scheduled.count, 1)

        ledger = ledger.unscheduling(["miff.notif.n3.miff-1"])
        XCTAssertTrue(ledger.scheduled.isEmpty)
        ledger = ledger.countingFollowUp(falseAlert: true).countingFollowUp(falseAlert: false)
        XCTAssertEqual(ledger.judgedFollowUps, 2)
        XCTAssertEqual(ledger.falseAlerts, 1)
    }

    // MARK: - Les identifiants

    func testTheRequestIdentifierIsOnePerTypeAndMiffAndCanBeParsedBack() {
        let identifier = NotificationIdentifier.make(type: .n4, miffId: "abc.def")

        XCTAssertEqual(identifier, "miff.notif.n4.abc.def")
        XCTAssertEqual(NotificationIdentifier.parse(identifier)?.type, .n4)
        XCTAssertEqual(NotificationIdentifier.parse(identifier)?.miffId, "abc.def")
        XCTAssertNil(NotificationIdentifier.parse("other.n1.x"))
        XCTAssertNil(NotificationIdentifier.parse("miff.notif.n5.x"), "N5 n'existe pas")
        XCTAssertNil(NotificationIdentifier.parse("miff.notif.n1"))
    }

    func testTheNightHoursAreFixedByTheDecision() {
        XCTAssertEqual(NotificationPlanner.nightStartHour, 22)
        XCTAssertEqual(NotificationPlanner.nightEndHour, 7)
        XCTAssertEqual(NotificationPlanner.followUpDelayMillis, 30 * 60_000)
        XCTAssertEqual(NotificationPlanner.iosHorizonMillis, 3 * 3_600_000)
        XCTAssertEqual(NotificationPlanner.maxPerTypePerDay, 2)
        XCTAssertEqual(NotificationPlanner.maxPerDay, 6)
    }

    // MARK: - Le décorateur du suivi des lieux

    private final class MemoryTrackingStore: TrackingLocalStore {
        var state: LocalState?
        private(set) var clears = 0

        func load() -> LocalState? {
            state
        }

        func save(_ state: LocalState) {
            self.state = state
        }

        func clear() {
            clears += 1
            state = nil
        }
    }

    private func local(pending: StatusFact?) -> LocalState {
        var miff = LocalMiff()
        miff.pending = pending.map { PendingStatus(fact: $0) }
        return LocalState(uid: "me", miffs: ["miff-1": miff])
    }

    func testANewPendingStatusIsAnnouncedOnceAndEverythingElsePassesThrough() {
        let inner = MemoryTrackingStore()
        var heard: [(String, StatusFact)] = []
        let store = PresenceNotifyingLocalStore(wrapping: inner) { heard.append(($0, $1)) }
        let leave = StatusFact(presence: .out, placeId: "p1", presenceAtMillis: now, setLastUpdate: false)

        store.save(local(pending: leave))
        store.save(local(pending: leave))
        store.save(local(pending: nil))
        store.save(local(pending: leave))
        let arrive = StatusFact(presence: .inTrusted, placeId: "p1", presenceAtMillis: now + 5, setLastUpdate: true)
        store.save(local(pending: arrive))

        XCTAssertEqual(heard.map { $0.1.presence }, [.out, .out, .inTrusted])
        XCTAssertEqual(heard.map { $0.0 }, ["miff-1", "miff-1", "miff-1"])
        XCTAssertEqual(inner.state?.uid, "me", "la copie locale est sauvegardée telle quelle")
        XCTAssertEqual(store.load()?.uid, "me")
        store.clear()
        XCTAssertEqual(inner.clears, 1)
        XCTAssertNil(store.load())
    }

    // MARK: - Le fichier de l'instantané

    func testTheSnapshotAndTheLedgerAreKeptPerAccountAndExcludedFromBackups() throws {
        let directory = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("plan-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = NotificationPlanFileStore(directory: directory)
        let snapshot = NotificationPlanSnapshot(
            miffId: "miff-1", myUid: "me",
            members: [
                PlanMember(uid: "me", sharing: true, presence: "out", presenceAtMillis: now, lastUpdateAtMillis: nil)
            ],
            savedAtMillis: now
        )
        let ledger = NotificationLedger().marking(key: "n1|a", atMillis: now)

        store.saveSnapshot(snapshot, uid: "me")
        store.saveLedger(ledger, uid: "me")

        XCTAssertEqual(store.loadSnapshot(uid: "me"), snapshot)
        XCTAssertEqual(store.loadLedger(uid: "me"), ledger)
        XCTAssertNil(store.loadSnapshot(uid: "other"), "le fichier d'un autre compte n'est jamais lu")
        XCTAssertEqual(store.loadLedger(uid: "other"), NotificationLedger())
        let values = try directory.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(values.isExcludedFromBackup, true)
        store.clear()
        XCTAssertNil(store.loadSnapshot(uid: "me"))
    }

    // MARK: - Les demandes d'onglet

    @MainActor
    func testATabRequestWaitsForTheShellAndIsTakenOnlyOnce() {
        let requests = TabRequests()
        XCTAssertNil(requests.takePending())

        requests.request(.update)
        XCTAssertEqual(requests.takePending()?.tab, .update)
        XCTAssertNil(requests.takePending(), "une seule fois")

        requests.request(.update)
        XCTAssertEqual(requests.takePending()?.tab, .update, "deux demandes du même onglet sont deux demandes")
    }
}
