import XCTest
@testable import Miff

/// MIFF-4 dans le dépôt (critères N1, R1 à R7, Q1 à Q4) avec Firestore remplacé par un double : un lot par action, toujours
/// confirmé par le serveur, un nouveau code valide et différent, une réponse perdue vérifiée avant d'être appelée un échec.
@MainActor
final class MiffRepositoryMemberActionsTests: XCTestCase {
    private let uid = "uid-owner"
    private let newCode = "BCDFGHK23456"
    /// La Miff existe, et la personne en est le créateur.
    private let store: FakeMiffStore = {
        let made = FakeMiffStore()
        made.miffs[testMiffId] = testMiffDetails(owner: "uid-owner")
        return made
    }()
    private let drawn = TestBox<[String]>([])
    private lazy var timeline = Timeline()

    private func repo() -> FirestoreMiffRepository {
        let draws = drawn
        let code = newCode
        let rules = MiffRepositoryRules(
            newMiffId: { testMiffId },
            newCode: { draws.value.isEmpty ? code : draws.value.removeFirst() },
            timeoutSeconds: 1
        )
        return FirestoreMiffRepository(
            store: store,
            observer: FakeMiffObserver(),
            location: FakeLocationProbe(),
            cache: FakeCachePurger(timeline: timeline),
            rules: rules
        )
    }

    private func changed(_ result: CodeChangeResult) -> String? {
        if case .changed(let code) = result {
            return code
        }
        return nil
    }

    // MARK: - Retirer

    func testRemovingIsOneLotOfSevenReadInsideTheTransactionAndReturnsTheNewCode() async {
        let result = await repo().removeMember(miffId: testMiffId, targetUid: "uid-2")

        XCTAssertEqual(changed(result), newCode)
        XCTAssertEqual(store.commitsAsLots.count, 1)
        let lot = store.commitsAsLots[0]
        XCTAssertEqual(lot.count, 7)
        // Le code à remplacer est celui que la transaction a lu, pas un code tenu par l'écran.
        XCTAssertEqual(lot.last?.path, "joinCodes/\(testCode)")
        XCTAssertEqual(lot.last?.kind, .delete)
        XCTAssertEqual(lot[5].path, "joinCodes/\(newCode)")
        XCTAssertEqual(store.calls.last, "commitWithMiff")
    }

    func testTheNewCodeIsValidFreeAndNeverTheOldOne() async {
        store.codes["AAAAAAAAAAAA"] = ["miffId": "x", "miffName": "y"]
        drawn.value = ["AAAAAAAAAAAA", "CCCCCCCCCCCC"]

        let result = await repo().removeMember(miffId: testMiffId, targetUid: "uid-2")

        XCTAssertEqual(changed(result), "CCCCCCCCCCCC")
        XCTAssertTrue(JoinCode.isValid("CCCCCCCCCCCC"))
        XCTAssertEqual(store.calls.filter { $0 == "code" }.count, 2, "une lecture du code avant qu'il serve")
    }

    func testRemovingOfflineIsAFailureNothingIsWrittenAndTheSentenceIsTheOfflineOne() async {
        store.commitFailures = [firestoreError(FirestoreErrorNumber.unavailable)]
        // La vérification qui suit ne peut pas joindre le serveur non plus.
        store.miffs[testMiffId] = firestoreError(FirestoreErrorNumber.unavailable)

        let result = await repo().removeMember(miffId: testMiffId, targetUid: "uid-2")

        XCTAssertEqual(result, .failed(.offline))
        XCTAssertTrue(store.commitsAsLots.isEmpty)
    }

    func testALotWhoseAnswerWasLostIsCheckedOnTheServerAndNotShownAsAFailure() async {
        // Le lot est parti (la Miff porte le nouveau code) mais la réponse n'est jamais revenue.
        let fake = store
        let owner = uid
        let renewed = newCode
        store.onCommit = {
            fake.miffs[testMiffId] = testMiffDetails(code: renewed, owner: owner)
        }
        store.commitFailures = [firestoreError(FirestoreErrorNumber.deadlineExceeded)]

        let result = await repo().removeMember(miffId: testMiffId, targetUid: "uid-2")

        XCTAssertEqual(changed(result), newCode)
    }

    func testARefusalOfTheRulesIsTheGenericSentence() async {
        store.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied)]

        let result = await repo().removeMember(miffId: testMiffId, targetUid: "uid-2")

        XCTAssertEqual(result, .failed(.refused))
    }

    func testAMiffThatNoLongerExistsIsAFailureNotACrash() async {
        store.miffs[testMiffId] = nil

        let result = await repo().removeMember(miffId: testMiffId, targetUid: "uid-2")

        guard case .failed = result else {
            return XCTFail("un échec était attendu")
        }
        XCTAssertTrue(store.commitsAsLots.isEmpty)
    }

    func testWhenTheOldCodeDocumentIsMissingTheLotHasSixWrites() async {
        store.codeDocumentExists = false

        _ = await repo().removeMember(miffId: testMiffId, targetUid: "uid-2")

        XCTAssertEqual(store.commitsAsLots.first?.count, 6)
    }

    func testAnIdentifierThatCouldLeaveItsPathWritesNothing() async {
        let result = await repo().removeMember(miffId: testMiffId, targetUid: "a/b")

        guard case .failed = result else {
            return XCTFail("un échec était attendu")
        }
        XCTAssertTrue(store.commitsAsLots.isEmpty)
    }

    // MARK: - Renouveler

    func testRenewingIsOneLotOfThreeAndReturnsTheNewCode() async {
        let result = await repo().renewCode(miffId: testMiffId)

        XCTAssertEqual(changed(result), newCode)
        XCTAssertEqual(store.commitsAsLots.first?.count, 3)
        XCTAssertFalse(store.commitsAsLots.joined().contains { $0.path.contains("/members/") })
    }

    func testThreeCollisionsInARowIsAFailureWithoutAnyLot() async {
        store.codes["AAAAAAAAAAAA"] = ["miffId": "x", "miffName": "y"]
        drawn.value = ["AAAAAAAAAAAA", "AAAAAAAAAAAA", "AAAAAAAAAAAA"]

        let result = await repo().renewCode(miffId: testMiffId)

        XCTAssertEqual(result, .failed(.unknown))
        XCTAssertTrue(store.commitsAsLots.isEmpty)
    }

    // MARK: - Quitter

    func testLeavingFindsTheIndexByItsFieldAndWritesTheLotOfFive() async {
        store.miffs[testMiffId] = testMiffDetails(owner: "someone-else")
        // L'identifiant du document est « free » : la Miff se trouve par le CHAMP miffId (critère R11).
        store.memberships = [MembershipRef(key: "free", miffId: testMiffId)]

        let result = await repo().leave(uid: uid, miffId: testMiffId)

        XCTAssertEqual(result, .done)
        let lot = store.commitsAsLots[0]
        XCTAssertEqual(lot.count, 5)
        XCTAssertEqual(lot.last?.path, "users/\(uid)/memberships/free")
        XCTAssertTrue(lot.allSatisfy { $0.kind == .delete })
    }

    func testLeavingNeverRenewsTheCode() async {
        // Décision du PM du 2026-10-31 : une sortie volontaire touche aux documents de la personne seulement.
        store.miffs[testMiffId] = testMiffDetails(owner: "someone-else")
        store.memberships = [MembershipRef(key: "free", miffId: testMiffId)]

        _ = await repo().leave(uid: uid, miffId: testMiffId)

        let paths = store.commitsAsLots.joined().map { $0.path }
        XCTAssertFalse(paths.contains { $0 == "miffs/\(testMiffId)" || $0.hasPrefix("joinCodes") })
        XCTAssertTrue(paths.allSatisfy { $0.contains("/\(uid)") })
    }

    func testLeavingWithAPremiumIndexKeyedByTheMiffIdDeletesThatOne() async {
        store.memberships = [
            MembershipRef(key: testMiffId, miffId: testMiffId),
            MembershipRef(key: "free", miffId: testOtherMiffId)
        ]

        _ = await repo().leave(uid: uid, miffId: testMiffId)

        XCTAssertEqual(store.commitsAsLots.first?.last?.path, "users/\(uid)/memberships/\(testMiffId)")
    }

    func testLeavingOfflineWritesNothingAndIsNotConfirmed() async {
        store.membershipsFailure = firestoreError(FirestoreErrorNumber.unavailable)

        let result = await repo().leave(uid: uid, miffId: testMiffId)

        XCTAssertEqual(result, .failed(.offline))
        XCTAssertTrue(store.commitsAsLots.isEmpty)
    }

    func testALeaveWhoseAnswerWasLostIsDoneOnceTheServerRefusesTheMiff() async {
        store.memberships = [MembershipRef(key: "free", miffId: testMiffId)]
        store.commitFailures = [firestoreError(FirestoreErrorNumber.deadlineExceeded)]
        // Après le lot, le serveur ne laisse plus la personne lire la Miff : le lot est bien parti.
        let fake = store
        store.onCommit = {
            fake.miffs[testMiffId] = firestoreError(FirestoreErrorNumber.permissionDenied)
        }

        let result = await repo().leave(uid: uid, miffId: testMiffId)

        XCTAssertEqual(result, .done)
    }

    func testALeaveWhoseAnswerWasLostIsAFailureIfThePersonCanStillReadTheMiff() async {
        store.memberships = [MembershipRef(key: "free", miffId: testMiffId)]
        store.commitFailures = [firestoreError(FirestoreErrorNumber.unavailable)]

        let result = await repo().leave(uid: uid, miffId: testMiffId)

        XCTAssertEqual(result, .failed(.offline))
    }

    func testARefusalWhileLeavingIsTheGenericSentence() async {
        store.memberships = [MembershipRef(key: "free", miffId: testMiffId)]
        store.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied)]

        let result = await repo().leave(uid: uid, miffId: testMiffId)

        XCTAssertEqual(result, .failed(.refused))
    }
}
