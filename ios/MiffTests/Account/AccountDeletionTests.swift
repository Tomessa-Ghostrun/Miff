import XCTest
@testable import Miff

/// La procédure de suppression d'un compte (AUTH-5c, miroir de `AccountDeletionTest` d'Android) : l'ordre exact des écritures, la session
/// d'appareil supprimée juste après l'inventaire, le profil en dernier document, le compte d'authentification EN DERNIER, seul
/// « utilisateur introuvable » pour dire « déjà supprimé », la reprise après une coupure à chaque étape.
@MainActor
final class AccountDeletionTests: XCTestCase {
    private let timeline = Timeline()
    private lazy var gateway = FakeAuthGateway(timeline: timeline)
    private lazy var cache = FakeCachePurger(timeline: timeline)
    private lazy var store = FakeAccountDataStore(timeline: timeline)
    private lazy var session = FakeAccountSession(timeline: timeline)
    private lazy var miffs = FakeAccountMiffs(timeline: timeline)
    private var steps: [DeletionStep] = []

    private func makeProcedure() -> AccountDeletion {
        gateway.setSignedIn(testAccountUid)
        return AccountDeletion(store: store, gateway: gateway, cache: cache, session: session, miffs: miffs)
    }

    private func run(_ procedure: AccountDeletion) async -> DeletionRun {
        await procedure.run(uid: testAccountUid) { [weak self] step in
            self?.steps.append(step)
        }
    }

    // MARK: - L'ordre

    func testAnAccountWithNoMiffGoesSessionDevicesProfileThenTheAccountLast() async {
        store.devices = ["device-a"]
        let procedure = makeProcedure()

        let outcome = await run(procedure)

        XCTAssertEqual(outcome, .completed)
        XCTAssertEqual(
            timeline.entries,
            [
                "inventory", "session", "count", "devices", "device:device-a", "session", "count",
                "profile", "markPending", "flush", "delete"
            ]
        )
        XCTAssertTrue(store.profileDeleted)
        XCTAssertEqual(gateway.deleteCalls, 1)
    }

    func testTheSessionIsDeletedRightAfterTheInventoryAndBeforeTheFirstMiffLot() async {
        miffs.inventories = [.read(testInventory(joined: [testRef()], owned: [testOwned()]))]
        store.counts = [0]
        let procedure = makeProcedure()

        _ = await run(procedure)

        let entries = timeline.entries
        let inventory = entries.firstIndex(of: "inventory") ?? Int.max
        let session = entries.firstIndex(of: "session") ?? Int.min
        let firstLot = entries.firstIndex(of: "leave:\(testJoinedMiffId)") ?? Int.min
        XCTAssertLessThan(inventory, session)
        XCTAssertLessThan(session, firstLot, "décision du PM du 2026-11-06 : la session avant les lots de Miffs")
        XCTAssertEqual(entries.filter { $0 == "session" }.count, 2, "puis encore avant le profil (idempotent)")
    }

    func testStaleIndexesFirstThenTheJoinedMiffsThenTheCreatedOnesOneAfterTheOther() async {
        let stale = testRef(key: "stale-key-0000001", miffId: "StaleMiffId00000001")
        miffs.inventories = [.read(testInventory(joined: [testRef()], owned: [testOwned()], stale: [stale]))]
        let procedure = makeProcedure()

        let outcome = await run(procedure)

        XCTAssertEqual(outcome, .completed)
        let prefixes = ["drop:", "leave:", "deleteOwned:"]
        let miffCalls = timeline.entries.filter { entry in prefixes.contains { entry.hasPrefix($0) } }
        XCTAssertEqual(
            miffCalls,
            ["drop:stale-key-0000001", "leave:\(testJoinedMiffId)", "deleteOwned:\(testOwnedMiffId)"]
        )
        XCTAssertEqual(steps.first, .checking)
        XCTAssertTrue(steps.contains(.miff(index: 1, total: 2, longWait: false)))
        XCTAssertTrue(steps.contains(.miff(index: 2, total: 2, longWait: false)))
        XCTAssertEqual(steps.last, .profile)
    }

    func testTheProfileIsTheLastDocumentAndTheAccountComesAfterTheQueuedWritesLeave() async {
        let procedure = makeProcedure()

        _ = await run(procedure)

        let tail = Array(timeline.entries.suffix(4))
        XCTAssertEqual(tail, ["profile", "markPending", "flush", "delete"])
    }

    func testMoreThanThreeRemovalsAnnounceALongWait() async {
        miffs.inventories = [.read(testInventory(owned: [testOwned(others: 4)]))]
        let procedure = makeProcedure()

        _ = await run(procedure)

        XCTAssertTrue(steps.contains(.miff(index: 1, total: 1, longWait: true)))
    }

    // MARK: - Le compte d'authentification : seul « introuvable » veut dire « déjà supprimé »

    func testUserNotFoundOnTheLastStepIsTheDeletionAlreadyDone() async {
        gateway.deleteResults = [.accountGone]
        let procedure = makeProcedure()

        let outcome = await run(procedure)

        XCTAssertEqual(outcome, .completed)
    }

    func testAnExpiredOrRevokedTokenIsNeverADeletion() async {
        gateway.deleteResults = [.sessionExpired]
        let procedure = makeProcedure()

        let outcome = await run(procedure)

        XCTAssertEqual(outcome, .sessionExpired)
    }

    func testAskingForARecentSignInIsReportedForTheLastCheck() async {
        gateway.deleteResults = [.recentLoginRequired]
        let procedure = makeProcedure()

        let outcome = await run(procedure)

        XCTAssertEqual(outcome, .needsRecentLogin)
    }

    func testNoNetworkOnTheLastStepIsAPause() async {
        gateway.deleteResults = [.network]
        let procedure = makeProcedure()

        let outcome = await run(procedure)

        XCTAssertEqual(outcome, .offline)
    }

    func testNobodySignedInIsNeverTakenForDeleted() async {
        let procedure = makeProcedure()
        gateway.setSignedIn(nil)

        let outcome = await run(procedure)

        XCTAssertEqual(outcome, .sessionExpired)
        XCTAssertEqual(gateway.deleteCalls, 0)
        XCTAssertFalse(store.profileDeleted)
    }

    func testNobodySignedInAtTheLastMomentIsNotADeletionEither() async {
        let procedure = makeProcedure()
        cache.onFlush = { [gateway] in gateway.setSignedIn(nil) }

        let outcome = await run(procedure)

        XCTAssertEqual(outcome, .sessionExpired, "deleteCurrentAccount répondrait « fait » sans compte")
        XCTAssertEqual(gateway.deleteCalls, 0)
    }

    func testAnotherAccountThanTheMarkerIsNeverDeleted() async {
        let procedure = makeProcedure()

        let outcome = await procedure.run(uid: "someone-else") { _ in }

        XCTAssertEqual(outcome, .failed)
        XCTAssertTrue(timeline.entries.isEmpty)
    }

    // MARK: - Un refus n'est pas toujours un échec

    func testARefusedProfileDeletionWhenTheAccountIsGoneIsTheSuccessWeWanted() async {
        store.failures["profile"] = firestoreError(FirestoreErrorNumber.permissionDenied)
        gateway.sessionChecks = [.gone]
        let procedure = makeProcedure()

        let outcome = await run(procedure)

        XCTAssertEqual(outcome, .completed)
        XCTAssertEqual(gateway.deleteCalls, 0)
    }

    func testARefusalWithAnExpiredSessionAsksToSignInAgain() async {
        store.failures["profile"] = firestoreError(FirestoreErrorNumber.permissionDenied)
        gateway.sessionChecks = [.expired]
        let procedure = makeProcedure()

        let outcome = await run(procedure)

        XCTAssertEqual(outcome, .sessionExpired)
    }

    func testARefusalWithAValidSessionIsAFailureAndTheAccountIsKept() async {
        store.failures["profile"] = firestoreError(FirestoreErrorNumber.permissionDenied)
        gateway.sessionChecks = [.valid]
        let procedure = makeProcedure()

        let outcome = await run(procedure)

        XCTAssertEqual(outcome, .failed)
        XCTAssertEqual(gateway.deleteCalls, 0, "le compte n'est jamais supprimé avec le profil encore là")
    }

    func testNoNetworkOnAStepIsAPauseAndTheSessionIsNotQuestioned() async {
        store.failures["profile"] = firestoreError(FirestoreErrorNumber.unavailable)
        let procedure = makeProcedure()

        let outcome = await run(procedure)

        XCTAssertEqual(outcome, .offline)
        XCTAssertEqual(gateway.checkSessionCalls, 0)
    }

    // MARK: - La session d'appareil

    func testWithoutTheAcknowledgementOfTheSessionNothingFurtherIsDone() async {
        session.failures = 1
        miffs.inventories = [.read(testInventory(joined: [testRef()]))]
        let procedure = makeProcedure()

        let outcome = await run(procedure)

        XCTAssertNotEqual(outcome, .completed)
        XCTAssertFalse(timeline.entries.contains("leave:\(testJoinedMiffId)"), "aucun lot sans l'accusé de la session")
        XCTAssertFalse(store.profileDeleted)
        XCTAssertEqual(gateway.deleteCalls, 0)
    }

    func testWithoutNetworkTheSessionStepIsAPauseNotAFailure() async {
        session.failures = 1
        session.failure = OperationTimedOut()
        let procedure = makeProcedure()

        let outcome = await run(procedure)

        XCTAssertEqual(outcome, .offline)
    }

    // MARK: - Les Miffs qui apparaissent

    func testAMiffThatAppearsMeanwhileGetsAnotherPassThenTheDeletionContinues() async {
        miffs.inventories = [.read(testInventory(joined: [testRef()])), .read(testInventory())]
        store.counts = [1, 0, 0]
        let procedure = makeProcedure()

        let outcome = await run(procedure)

        XCTAssertEqual(outcome, .completed)
        XCTAssertEqual(miffs.inventoryCalls, 2)
    }

    func testMiffsThatKeepAppearingCancelTheDeletionAndDeleteNeitherTheProfileNorTheAccount() async {
        miffs.inventories = [.read(testInventory(joined: [testRef()]))]
        store.counts = [1]
        let procedure = makeProcedure()

        let outcome = await run(procedure)

        XCTAssertEqual(outcome, .miffsRemain)
        XCTAssertFalse(store.profileDeleted)
        XCTAssertEqual(gateway.deleteCalls, 0)
    }

    func testSomethingLeftThatCannotBeNamedIsAFailureNotACancellation() async {
        miffs.inventories = [.read(testInventory())]
        store.counts = [1]
        let procedure = makeProcedure()

        let outcome = await run(procedure)

        XCTAssertEqual(outcome, .failed)
    }

    func testTheFinalCheckSendsBackToTheMiffsWhenOneAppearedAfterThem() async {
        miffs.inventories = [.read(testInventory()), .read(testInventory(joined: [testRef()])), .read(testInventory())]
        store.counts = [0, 1, 0, 0, 0]
        let procedure = makeProcedure()

        let outcome = await run(procedure)

        XCTAssertEqual(outcome, .completed)
        XCTAssertTrue(timeline.entries.contains("leave:\(testJoinedMiffId)"))
        XCTAssertEqual(timeline.entries.last, "delete")
    }

    func testAFailedMiffStopsEverythingAndNeverReachesTheProfile() async {
        miffs.inventories = [.read(testInventory(owned: [testOwned()]))]
        miffs.steps["deleteOwned:\(testOwnedMiffId)"] = .failed(.refused)
        gateway.sessionChecks = [.valid]
        let procedure = makeProcedure()

        let outcome = await run(procedure)

        XCTAssertEqual(outcome, .failed)
        XCTAssertFalse(store.profileDeleted)
        XCTAssertEqual(gateway.deleteCalls, 0, "jamais de compte supprimé avec une Miff créée derrière lui")
    }

    // MARK: - Reprise après une coupure

    func testAnInterruptionAtEveryStepIsResumedFromTheServerState() async {
        // Chaque opération échoue tour à tour (pas de réseau) ; la relancer, une fois le réseau revenu, termine toujours.
        let operations = ["count", "devices", "profile"]
        for operation in operations {
            let local = Timeline()
            let localStore = FakeAccountDataStore(timeline: local)
            let localGateway = FakeAuthGateway(timeline: local)
            localGateway.setSignedIn(testAccountUid)
            let localMiffs = FakeAccountMiffs(timeline: local)
            let procedure = AccountDeletion(
                store: localStore,
                gateway: localGateway,
                cache: FakeCachePurger(timeline: local),
                session: FakeAccountSession(timeline: local),
                miffs: localMiffs
            )
            localStore.failures[operation] = firestoreError(FirestoreErrorNumber.unavailable)

            let first = await procedure.run(uid: testAccountUid) { _ in }
            localStore.failures[operation] = nil
            let second = await procedure.run(uid: testAccountUid) { _ in }

            XCTAssertEqual(first, .offline, operation)
            XCTAssertEqual(second, .completed, operation)
            XCTAssertEqual(localGateway.deleteCalls, 1, operation)
        }
    }

    func testAnInterruptedInventoryIsAPauseAndNothingIsTouched() async {
        miffs.inventories = [.failed(.offline)]
        let procedure = makeProcedure()

        let outcome = await run(procedure)

        XCTAssertEqual(outcome, .offline)
        XCTAssertEqual(timeline.entries, ["inventory"])
    }

    // MARK: - L'inventaire de l'écran d'information

    func testTheInventoryCountsTheJoinedMiffsAndListsTheCreatedOnes() async {
        miffs.inventories = [.read(testInventory(joined: [testRef()], owned: [testOwned()]))]
        let procedure = makeProcedure()

        let inventory = await procedure.inventory(uid: testAccountUid)

        XCTAssertEqual(inventory, .loaded(AccountMiffsSummary(joined: 1, owned: [testOwned()])))
    }

    func testTheInventoryDistinguishesNoNetworkFromARefusal() async {
        let procedure = makeProcedure()

        miffs.inventories = [.failed(.offline)]
        let offline = await procedure.inventory(uid: testAccountUid)
        miffs.inventories = [.failed(.refused)]
        let failed = await procedure.inventory(uid: testAccountUid)

        XCTAssertEqual(offline, .offline)
        XCTAssertEqual(failed, .failed)
    }
}
