// Quelques lignes appellent des fabriques à nombreux paramètres : elles dépassent la limite.
// swiftlint:disable line_length
#if ALERT_DANGER
import XCTest
@testable import Miff

/// Tous les chemins par lesquels la personne PERD la Miff de son alerte (revue de l'Android, v3 et v4) : retirée par le créateur, Miff supprimée par un autre membre (y compris quand
/// seul l'index local est nettoyé), départ dont la réponse s'est perdue, suppression par le créateur, et jamais une simple liste de Miffs. Dans chaque cas la bannière part et
/// une nouvelle alerte devient possible dans une autre Miff : sinon l'état restait coincé jusqu'à 7 jours (« Une alerte est déjà en cours », geste de fin refusé).
@MainActor
final class DangerAlertLossPathsTests: DangerAlertControllerTestCase {
    private let uid = "me"
    private let repoStore = FakeMiffStore()
    private let timeline = Timeline()
    private lazy var cache = FakeCachePurger(timeline: timeline)

    private func repository(telling controller: DangerAlertController) -> FirestoreMiffRepository {
        var rules = MiffRepositoryRules(newMiffId: { testMiffId }, timeoutSeconds: 1, deviceId: { testDeviceId })
        rules.alertsEnabled = true
        rules.onAlertGone = { miffId in
            Task { @MainActor in controller.onAlertRemoved(miffId: miffId) }
        }
        return FirestoreMiffRepository(
            store: repoStore, observer: FakeMiffObserver(), location: FakeLocationProbe(), cache: cache, rules: rules
        )
    }

    private func assertGoneAndFree(_ controller: DangerAlertController, file: StaticString = #filePath, line: UInt = #line) async {
        await eventually("la bannière devait partir", file: file, line: line) { controller.state == .idle }
        XCTAssertNil(local.saved, "le pointeur du téléphone est effacé", file: file, line: line)
        store.startResults = [started()]
        XCTAssertEqual(controller.trigger(miffId: miffB), .started, "une nouvelle alerte est possible ailleurs", file: file, line: line)
    }

    func testAMemberRemovedByTheCreatorLosesTheBannerAndMayStartAnotherAlert() async {
        let controller = makeController()
        await activate(controller)
        let lost = LostMiff(ref: MembershipRef(key: "free", miffId: miffA), name: "Chez nous")

        let cleaned = await repository(telling: controller).cleanUpLost(uid: uid, lost: lost)

        XCTAssertTrue(cleaned)
        await assertGoneAndFree(controller)
    }

    func testWhenTheCleanUpOnlyDropsTheLocalIndexTheAlertIsGoneAllTheSame() async {
        let controller = makeController()
        await activate(controller)
        let lost = LostMiff(ref: MembershipRef(key: "free", miffId: miffA), name: "Chez nous")
        // Le lot de départ est refusé (avec, puis sans l'alerte) : il ne reste que l'index.
        repoStore.commitFailures = [
            firestoreError(FirestoreErrorNumber.permissionDenied), firestoreError(FirestoreErrorNumber.permissionDenied), nil
        ]

        let cleaned = await repository(telling: controller).cleanUpLost(uid: uid, lost: lost)

        XCTAssertTrue(cleaned)
        await assertGoneAndFree(controller)
    }

    func testEvenWhenTheCleanUpFailsEntirelyTheAlertOfALostMiffIsGone() async {
        let controller = makeController()
        await activate(controller)
        let lost = LostMiff(ref: MembershipRef(key: "free", miffId: miffA), name: "Chez nous")
        repoStore.commitFailures = (0..<3).map { _ -> Error? in firestoreError(FirestoreErrorNumber.permissionDenied) }

        let cleaned = await repository(telling: controller).cleanUpLost(uid: uid, lost: lost)

        XCTAssertFalse(cleaned)
        await assertGoneAndFree(controller)
    }

    func testALeaveWhoseAnswerWasLostButLandedRemovesTheAlert() async {
        let controller = makeController()
        await activate(controller)
        repoStore.memberships = [MembershipRef(key: "free", miffId: miffA)]
        repoStore.commitFailures = [firestoreError(FirestoreErrorNumber.unavailable)]
        // La Miff est maintenant refusée par le serveur, qui répond par ailleurs : le lot est bien parti.
        repoStore.miffs[miffA] = firestoreError(FirestoreErrorNumber.permissionDenied)

        let result = await repository(telling: controller).leave(uid: uid, miffId: miffA)

        XCTAssertEqual(result, .done)
        await assertGoneAndFree(controller)
    }

    func testALeaveThatReallyFailedKeepsTheAlert() async {
        let controller = makeController()
        await activate(controller)
        repoStore.memberships = [MembershipRef(key: "free", miffId: miffA)]
        repoStore.commitFailures = [firestoreError(FirestoreErrorNumber.unavailable)]
        // La Miff est toujours lisible : le lot n'est pas parti.
        repoStore.miffs[miffA] = testMiffDetails(name: "Chez nous")

        let result = await repository(telling: controller).leave(uid: uid, miffId: miffA)

        XCTAssertNotEqual(result, .done)
        try? await Task.sleep(nanoseconds: 20_000_000)
        XCTAssertTrue(isActive(controller), "jamais une bannière retirée sur un doute")
    }

    func testTheDeletionOfTheMiffByItsCreatorTellsTheControllerBeforeThePurge() throws {
        let sources = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Alert
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/App/MiffServices.swift")
        let code = try String(contentsOf: sources, encoding: .utf8)
        let hook = try XCTUnwrap(code.range(of: "onDeleted: { miffId in"))
        let after = String(code[hook.upperBound...])
        let alert = try XCTUnwrap(after.range(of: "onAlertGone(miffId)"))
        let purge = try XCTUnwrap(after.range(of: "directory.afterOwnDeparture(miffId: miffId)"))

        XCTAssertLessThan(alert.lowerBound, purge.lowerBound, "le dialogue dit que l'alerte sera retirée : elle l'est aussi sur le téléphone")
    }

    func testAListOfMiffsNeverEndsAnAlertAndTheControllerHasNoSuchDoor() async {
        let controller = makeController()
        await activate(controller)

        // Une Miff dont la lecture échoue manque dans la liste sans être perdue : la liste n'est même plus donnée au contrôleur (seulement à l'adoption d'un nouveau téléphone).
        store.mineReads = [.value(nil)]
        await controller.adoptFromServer(miffIds: [miffB])

        XCTAssertTrue(isActive(controller))
        XCTAssertEqual(local.saved?.phase, .active)
    }

    func testAnAbandonedPointerOlderThanSevenDaysIsDroppedAtTheLaunchWithoutAnyRead() {
        local.save(DangerAlertPointer(miffId: miffA, phase: .abandoned, armedAtMillis: clock))
        clock += DangerAlertRules.durationMillis + 1
        let controller = makeController()

        controller.restore()

        XCTAssertNil(local.saved)
        XCTAssertEqual(store.mineCalls, 0, "aucune alerte ne peut plus être vivante : rien à lire")
    }

    func testARefusalIsConfirmedByASecondOneBeforeThePointerIsForgotten() async {
        local.save(DangerAlertPointer(miffId: miffA, phase: .abandoned, armedAtMillis: clock))
        store.mineReads = [.denied]
        let controller = makeController()

        controller.restore()

        await eventually { self.local.saved == nil }
        XCTAssertEqual(store.mineCalls, 2, "deux refus, jamais un seul (un refus peut être passager : App Check)")
    }

    func testAPassingRefusalFollowedByALiveAlertMakesItActiveInsteadOfErasingIt() async throws {
        local.save(DangerAlertPointer(miffId: miffA, phase: .abandoned, armedAtMillis: clock))
        store.mineReads = [.denied, .value(try liveDoc())]
        let controller = makeController()

        controller.restore()

        await eventually { self.isActive(controller) }
        XCTAssertEqual(local.saved?.phase, .active)
    }

    func testARefusalOnAnActiveAlertNeverRemovesTheBanner() async {
        let controller = makeController()
        await activate(controller)
        store.mineReads = [.denied]

        await controller.verifyActive()

        XCTAssertTrue(isActive(controller))
    }
}
#endif
// swiftlint:enable line_length
