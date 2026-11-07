import XCTest
@testable import Miff

/// Les Miffs de la suppression d'un compte (AUTH-5c, miroir de `AccountMiffsTest` d'Android) : l'inventaire lu auprès du SERVEUR (rejointes,
/// créées avec le nombre des AUTRES personnes, index périmés), le lot « Quitter » sans changement de code, le repli « index seul » après un
/// refus, la suppression d'une Miff créée par la procédure existante.
@MainActor
final class StoreAccountMiffsTests: XCTestCase {
    private let uid = testAccountUid
    private let store = FakeMiffStore()
    private let deleter = ScriptedDeleter()
    private let staleId = "StaleMiffId00000001"

    private func makeMiffs() -> StoreAccountMiffs {
        StoreAccountMiffs(store: store, deleter: deleter, timeoutSeconds: 2)
    }

    private func readInventory(_ miffs: StoreAccountMiffs) async -> MiffsInventory? {
        if case .read(let found) = await miffs.inventory(uid: uid) {
            return found
        }
        return nil
    }

    // MARK: - L'inventaire

    func testTheInventoryTellsJoinedFromCreatedFromStaleAndCountsTheOthers() async throws {
        store.memberships = [
            testRef(key: "key-joined-0000001", miffId: testJoinedMiffId),
            testRef(key: "key-owned-00000001", miffId: testOwnedMiffId),
            testRef(key: "key-stale-00000001", miffId: staleId)
        ]
        store.miffs[testJoinedMiffId] = testMiffDetails(id: testJoinedMiffId, owner: "someone-else")
        store.miffs[testOwnedMiffId] = testMiffDetails(id: testOwnedMiffId, name: "Chez nous", owner: uid)
        store.memberIds = [uid, "member-a", "member-b"]

        let read = await readInventory(makeMiffs())
        let found = try XCTUnwrap(read)

        XCTAssertEqual(found.joined.map { $0.miffId }, [testJoinedMiffId])
        XCTAssertEqual(found.owned, [OwnedMiff(miffId: testOwnedMiffId, name: "Chez nous", otherMembers: 2)])
        XCTAssertEqual(found.stale.map { $0.miffId }, [staleId], "une Miff disparue : il ne reste que l'index")
        XCTAssertEqual(found.visibleCount, 2, "un index périmé n'est jamais montré")
        XCTAssertTrue(store.calls.contains("memberships:server"), "lu auprès du serveur, jamais du cache")
        XCTAssertFalse(store.calls.contains("memberships:cache"))
    }

    func testACreatedMiffWithNobodyElseCountsZeroOthers() async throws {
        store.memberships = [testRef(key: "key-owned-00000001", miffId: testOwnedMiffId)]
        store.miffs[testOwnedMiffId] = testMiffDetails(id: testOwnedMiffId, owner: uid)
        store.memberIds = [uid]

        let read = await readInventory(makeMiffs())
        let found = try XCTUnwrap(read)

        XCTAssertEqual(found.owned.first?.otherMembers, 0)
        XCTAssertEqual(found.removals, 0)
    }

    func testTwoIndexesOfTheSameMiffAreOneMiff() async throws {
        store.memberships = [
            testRef(key: "key-one-000000001", miffId: testJoinedMiffId),
            testRef(key: "key-two-000000001", miffId: testJoinedMiffId)
        ]
        store.miffs[testJoinedMiffId] = testMiffDetails(id: testJoinedMiffId, owner: "someone-else")

        let read = await readInventory(makeMiffs())
        let found = try XCTUnwrap(read)

        XCTAssertEqual(found.joined.count, 1)
        XCTAssertEqual(found.stale.count, 1)
    }

    func testARefusedReadIsAStaleIndexOnlyWhenTheServerIsOtherwiseReachable() async throws {
        store.memberships = [testRef()]
        store.miffs[testJoinedMiffId] = firestoreError(FirestoreErrorNumber.permissionDenied)
        store.reachable = true

        let read = await readInventory(makeMiffs())
        let reachable = try XCTUnwrap(read)
        XCTAssertEqual(reachable.stale.count, 1)

        store.reachable = false
        let refused = await makeMiffs().inventory(uid: uid)
        XCTAssertEqual(refused, .failed(.refused), "un refus d'App Check refuserait tout : ce n'est pas « retirée »")
    }

    func testNoNetworkOnTheIndexesOrOnAMiffIsAPauseNotAnEmptyList() async {
        store.membershipsFailure = firestoreError(FirestoreErrorNumber.unavailable)
        let first = await makeMiffs().inventory(uid: uid)
        XCTAssertEqual(first, .failed(.offline))

        store.membershipsFailure = nil
        store.memberships = [testRef()]
        store.miffs[testJoinedMiffId] = firestoreError(FirestoreErrorNumber.unavailable)
        let second = await makeMiffs().inventory(uid: uid)
        XCTAssertEqual(second, .failed(.offline))
    }

    func testTheMembersOfACreatedMiffAreReadOnTheServerAndAFailureIsNeverAnIncompleteCount() async {
        store.memberships = [testRef(key: "key-owned-00000001", miffId: testOwnedMiffId)]
        store.miffs[testOwnedMiffId] = testMiffDetails(id: testOwnedMiffId, owner: uid)
        store.membersFailure = firestoreError(FirestoreErrorNumber.unavailable)

        let outcome = await makeMiffs().inventory(uid: uid)

        XCTAssertEqual(outcome, .failed(.offline))
    }

    // MARK: - Quitter

    func testLeavingIsTheLeaveLotAndNeverTouchesTheMiffOrItsCode() async {
        let outcome = await makeMiffs().leave(uid: uid, ref: testRef())

        XCTAssertEqual(outcome, .done)
        let paths = (store.commitsAsLots.first ?? []).map { $0.path }
        XCTAssertEqual(store.commitsAsLots.count, 1)
        XCTAssertTrue(paths.contains("miffs/\(testJoinedMiffId)/members/\(uid)"))
        let position = "miffs/\(testJoinedMiffId)/positions/\(uid)"
        XCTAssertTrue(paths.contains(position), "la dernière position part avec la fiche")
        XCTAssertTrue(paths.contains("users/\(uid)/memberships/\(testJoinedKey)"))
        XCTAssertFalse(paths.contains("miffs/\(testJoinedMiffId)"), "une sortie volontaire ne renouvelle pas le code")
        XCTAssertFalse(paths.contains { $0.hasPrefix("joinCodes/") })
    }

    func testARefusedLeaveFallsBackToTheIndexAloneWithoutAMessage() async {
        store.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied), nil]

        let outcome = await makeMiffs().leave(uid: uid, ref: testRef())

        XCTAssertEqual(outcome, .done)
        XCTAssertEqual(store.commitsAsLots.count, 1)
        let paths = (store.commitsAsLots.first ?? []).map { $0.path }
        XCTAssertTrue(paths.contains("users/\(uid)/memberships/\(testJoinedKey)"))
        XCTAssertFalse(paths.contains("miffs/\(testJoinedMiffId)/members/\(uid)"), "la fiche est déjà partie")
    }

    func testNoNetworkOnTheLeaveLotIsAPauseWithoutTryingTheFallback() async {
        store.commitFailures = [firestoreError(FirestoreErrorNumber.unavailable)]

        let outcome = await makeMiffs().leave(uid: uid, ref: testRef())

        XCTAssertEqual(outcome, .failed(.offline))
        XCTAssertTrue(store.commitsAsLots.isEmpty)
    }

    func testBothLotsRefusedReportsTheFirstReasonOfTheFallback() async {
        store.commitFailures = [
            firestoreError(FirestoreErrorNumber.permissionDenied),
            firestoreError(FirestoreErrorNumber.permissionDenied)
        ]

        let outcome = await makeMiffs().leave(uid: uid, ref: testRef())

        XCTAssertEqual(outcome, .failed(.refused))
    }

    func testDroppingAStaleIndexIsTheSameSilentStep() async {
        let outcome = await makeMiffs().dropStale(uid: uid, ref: testRef(key: "stale-key-0000001", miffId: staleId))

        XCTAssertEqual(outcome, .done)
    }

    // MARK: - Supprimer une Miff créée

    func testACreatedMiffGoesThroughTheExistingDeletionProcedure() async {
        let outcome = await makeMiffs().deleteOwned(uid: uid, miffId: testOwnedMiffId)

        XCTAssertEqual(outcome, .done)
        XCTAssertEqual(deleter.runs, ["\(uid)|\(testOwnedMiffId)"])
    }

    func testAPausedDeletionIsReportedWithItsReasonAndNotOwnerIsNeverGuessed() async {
        deleter.append(.paused(.offline))
        deleter.append(.notOwner)
        let miffs = makeMiffs()

        let paused = await miffs.deleteOwned(uid: uid, miffId: testOwnedMiffId)
        let notOwner = await miffs.deleteOwned(uid: uid, miffId: testOwnedMiffId)

        XCTAssertEqual(paused, .failed(.offline))
        XCTAssertEqual(notOwner, .failed(.unknown))
    }
}
