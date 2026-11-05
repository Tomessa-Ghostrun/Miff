import XCTest
@testable import Miff

/// Les lots d'entrée (créer, rejoindre, reprendre le partage) face à « un compte, un seul appareil » (SESS-3) : le premier statut porte
/// l'identifiant de la session CONFIRMÉE ; sans elle, rien ne part ; après un refus, la session est relue ; une session remplacée n'est
/// jamais présentée comme un code introuvable.
final class MiffRepositorySessionTests: XCTestCase {
    private let uid = "uid-1"
    private let store = FakeMiffStore()
    private let session = TestBox<String?>(testDeviceId)
    private let rechecks = TestBox(0)
    /// Ce que fait la relecture de la session : rien par défaut ; un test peut la faire « confirmer » ou « remplacer ».
    private let onRecheck = TestBox<() -> Void>({})

    private func repo() -> FirestoreMiffRepository {
        let session = self.session
        let rechecks = self.rechecks
        let onRecheck = self.onRecheck
        let rules = MiffRepositoryRules(
            newMiffId: { testMiffId },
            newCode: { testCode },
            timeoutSeconds: 1,
            deviceId: { session.value },
            recheckSession: {
                rechecks.value += 1
                onRecheck.value()
            }
        )
        return FirestoreMiffRepository(
            store: store,
            observer: FakeMiffObserver(),
            location: FakeLocationProbe(access: .foreground),
            cache: FakeCachePurger(timeline: Timeline()),
            rules: rules
        )
    }

    private func readyToJoin() async -> JoinReady {
        store.codes[testCode] = ["miffId": testMiffId, "miffName": "Chez nous"]
        store.miffs[testMiffId] = testMiffDetails()
        guard case .ready(let ready) = await repo().prepareJoin(uid: uid, typedCode: testCode) else {
            XCTFail("la préparation devrait être prête")
            return JoinReady(miffId: "", miffName: "", code: "", premium: false, firstMiff: false)
        }
        return ready
    }

    private func readyToCreate() async -> CreateReady {
        guard case .ready(let ready) = await repo().prepareCreate(uid: uid) else {
            XCTFail("la préparation devrait être prête")
            return CreateReady(premium: false, firstMiff: false)
        }
        return ready
    }

    private func statuses() -> [DocWrite] {
        store.commitsAsLots.joined().filter { $0.path.contains("/status/") }
    }

    // MARK: - Le statut initial porte l'identifiant

    func testCreateWritesTheInitialStatusWithTheIdentifierOfTheConfirmedSession() async {
        let preparation = await readyToCreate()

        let result = await repo().create(uid: uid, name: "Chez nous", preparation: preparation)

        guard case .created = result else {
            return XCTFail("la Miff devrait être créée")
        }
        XCTAssertEqual(statuses().count, 1)
        XCTAssertEqual(statuses().first?.data["deviceId"], .string(testDeviceId))
    }

    func testJoinWritesTheInitialStatusWithTheIdentifierOfTheConfirmedSession() async {
        let prepared = await readyToJoin()

        let result = await repo().join(uid: uid, preparation: prepared)

        XCTAssertEqual(result, .joined(miffId: testMiffId, firstMiff: true))
        XCTAssertEqual(statuses().first?.data["deviceId"], .string(testDeviceId))
    }

    func testResumingTheSharingWritesTheStatusWithTheIdentifierToo() async {
        let result = await repo().setSharing(miffId: testMiffId, uid: uid, enabled: true)

        XCTAssertEqual(result, .done)
        XCTAssertEqual(statuses().first?.data["deviceId"], .string(testDeviceId))
    }

    func testStoppingTheSharingWritesNoStatusSoItNeedsNoSession() async {
        session.value = nil

        let result = await repo().setSharing(miffId: testMiffId, uid: uid, enabled: false)

        XCTAssertEqual(result, .done)
        XCTAssertTrue(statuses().isEmpty)
        XCTAssertEqual(rechecks.value, 0)
    }

    // MARK: - Sans session confirmée, rien ne part

    func testWithoutAConfirmedSessionNoLotIsSentAndTheSessionIsReadAgainOnce() async {
        let preparation = await readyToCreate()
        let prepared = await readyToJoin()
        let storeCallsBefore = store.calls.count
        session.value = nil

        let created = await repo().create(uid: uid, name: "Chez nous", preparation: preparation)
        let joined = await repo().join(uid: uid, preparation: prepared)
        let resumed = await repo().setSharing(miffId: testMiffId, uid: uid, enabled: true)

        XCTAssertEqual(created, .failed(.offline))
        XCTAssertEqual(joined, .failed(.offline))
        XCTAssertEqual(resumed, .failed(.offline))
        XCTAssertTrue(store.commitsAsLots.isEmpty)
        XCTAssertEqual(store.calls.count, storeCallsBefore, "aucune lecture ni écriture de plus, pas même un code tiré")
        XCTAssertEqual(rechecks.value, 3, "une relecture de la session par tentative")
    }

    func testTheReadAgainCanConfirmTheSessionAndTheWriteGoesOn() async {
        let prepared = await readyToJoin()
        session.value = nil
        let confirmed = session
        onRecheck.value = { confirmed.value = testDeviceId }

        let result = await repo().join(uid: uid, preparation: prepared)

        XCTAssertEqual(result, .joined(miffId: testMiffId, firstMiff: true))
        XCTAssertEqual(rechecks.value, 1)
        XCTAssertEqual(statuses().first?.data["deviceId"], .string(testDeviceId))
    }

    // MARK: - Après un refus

    func testARefusedCreateReadsTheSessionAgainAndStaysAnOrdinaryFailure() async {
        let preparation = await readyToCreate()
        store.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied)]

        let result = await repo().create(uid: uid, name: "Chez nous", preparation: preparation)

        XCTAssertEqual(result, .failed(.refused))
        XCTAssertEqual(rechecks.value, 1)
    }

    func testARefusedJoinWithMySessionStillInPlaceIsTheUsualNotFound() async {
        let prepared = await readyToJoin()
        store.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied)]

        let result = await repo().join(uid: uid, preparation: prepared)

        XCTAssertEqual(result, .notFound)
        XCTAssertEqual(rechecks.value, 1)
    }

    func testARefusedJoinBecauseTheSessionWasReplacedIsNeverACodeNotFound() async {
        let prepared = await readyToJoin()
        store.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied)]
        let replaced = session
        onRecheck.value = { replaced.value = nil }

        let result = await repo().join(uid: uid, preparation: prepared)

        XCTAssertEqual(result, .failed(.refused))
        XCTAssertNotEqual(result, .notFound)
    }

    func testARefusedResumeReadsTheSessionAgain() async {
        store.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied)]
        store.sharing = false

        let result = await repo().setSharing(miffId: testMiffId, uid: uid, enabled: true)

        XCTAssertEqual(result, .failed(.refused))
        XCTAssertEqual(rechecks.value, 1)
    }

    func testAnOfflineFailureDoesNotTriggerAReadOfTheSession() async {
        let preparation = await readyToCreate()
        store.commitFailures = [firestoreError(FirestoreErrorNumber.unavailable)]
        store.miffs[testMiffId] = firestoreError(FirestoreErrorNumber.unavailable)

        let result = await repo().create(uid: uid, name: "Chez nous", preparation: preparation)

        XCTAssertEqual(result, .failed(.offline))
        XCTAssertEqual(rechecks.value, 0)
    }
}
