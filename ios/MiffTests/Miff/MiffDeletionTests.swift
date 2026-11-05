import XCTest
@testable import Miff

/// La suppression d'une Miff sans écran (critères S2, S4, S7, S8) : l'ordre, et surtout qu'elle peut être arrêtée à N'IMPORTE
/// quel moment puis relancée jusqu'au bout, sans écriture en double et sans Miff à moitié vidée. Le double simule ce que
/// Firestore détiendrait après chaque lot (`FakeMiffStore.simulate`).
@MainActor
final class MiffDeletionTests: XCTestCase {
    private let uid = "uid-owner"
    private var store = FakeMiffStore()
    private let counter = TestBox(0)

    /// Un code valide et différent à chaque tirage (jamais un I, L, O, 0 ou 1).
    private func nextCode() -> String {
        counter.value += 1
        let alphabet = Array(JoinCode.alphabet)
        let number = counter.value
        let tail = String([alphabet[number / alphabet.count % alphabet.count], alphabet[number % alphabet.count]])
        return "KKKKKKKKKK" + tail
    }

    private func world(
        others: [String] = ["uid-a", "uid-b"],
        places: [String] = ["p1", "p2", "p3", "p4", "p5", "p6", "p7"]
    ) {
        store.simulate = true
        store.miffs[testMiffId] = testMiffDetails(owner: uid)
        store.memberIds = [uid] + others
        store.placeIds = places
        store.memberships = [MembershipRef(key: "free", miffId: testMiffId)]
    }

    private func deletion() -> MiffDeletion {
        let rules = MiffRepositoryRules(newMiffId: { testMiffId }, newCode: { self.nextCode() }, timeoutSeconds: 1)
        let repository = FirestoreMiffRepository(
            store: store,
            observer: FakeMiffObserver(),
            location: FakeLocationProbe(),
            cache: FakeCachePurger(timeline: Timeline()),
            rules: rules
        )
        return MiffDeletion(store: store, repository: repository, timeoutSeconds: 1)
    }

    private var lotSizes: [Int] {
        store.commitsAsLots.map { $0.count }
    }

    private var paths: [String] {
        store.commitsAsLots.joined().map { $0.path }
    }

    /// Les codes écrits par les deux retraits (5e écriture de chaque lot : la fiche, le statut, la photo et la position viennent avant).
    private func removalCodes() -> [String] {
        store.commitsAsLots.prefix(2).compactMap { lot -> String? in
            if case .string(let code)? = lot[4].data["joinCode"] {
                return code
            }
            return nil
        }
    }

    func testTheWholeDeletionIsRemovalsThenPlacesThenOneLastLotInThatOrder() async {
        world()

        let result = await deletion().run(uid: uid, miffId: testMiffId)

        XCTAssertEqual(result, .deleted)
        // Deux retraits de 7 (la dernière position connue comprise), les lieux 5 + 2, puis le dernier lot de 8.
        XCTAssertEqual(lotSizes, [7, 7, 5, 2, 8])
        XCTAssertEqual(
            store.commitsAsLots.prefix(2).compactMap { $0.first?.path },
            ["miffs/\(testMiffId)/members/uid-a", "miffs/\(testMiffId)/members/uid-b"]
        )
        let last = store.commitsAsLots[4]
        XCTAssertEqual(last.first?.path, "miffs/\(testMiffId)")
        XCTAssertTrue(last.contains { $0.path == "miffsDeleted/\(testMiffId)" && $0.kind == .set })
        XCTAssertTrue(store.memberships.isEmpty)
        XCTAssertTrue(store.placeIds.isEmpty)
    }

    func testEveryRemovalRenewsTheCodeAndTheLastLotDeletesTheCodeThatIsCurrentThen() async {
        world()

        _ = await deletion().run(uid: uid, miffId: testMiffId)

        let renewed = removalCodes()
        XCTAssertEqual(Set(renewed).count, 2)
        guard let expected = renewed.last else {
            return XCTFail("le dernier retrait devait écrire un code")
        }
        let lot = store.commitsAsLots[4]
        XCTAssertEqual(lot.first { $0.path.hasPrefix("joinCodes/") }?.path, "joinCodes/\(expected)")
    }

    func testAMiffAloneHasNoRemovalAndNoPlacesJustTheLastLot() async {
        world(others: [], places: [])

        let result = await deletion().run(uid: uid, miffId: testMiffId)

        XCTAssertEqual(result, .deleted)
        XCTAssertEqual(lotSizes, [8])
    }

    func testStoppedBeforeAnyLotAnywhereInTheSequenceThenRunAgainEndsDeletedWithoutADoubleWrite() async {
        // Les lots dans l'ordre : 0 et 1 les retraits, 2 et 3 les lieux, 4 le dernier. Le réseau est coupé avant chacun.
        for cut in 0...4 {
            store = FakeMiffStore()
            world()
            var failures = [Error?](repeating: nil, count: cut)
            failures.append(firestoreError(FirestoreErrorNumber.unavailable))
            store.commitFailures = failures

            let first = await deletion().run(uid: uid, miffId: testMiffId)

            XCTAssertEqual(first, .paused(.offline), "coupé avant le lot \(cut)")
            XCTAssertEqual(store.commitsAsLots.count, cut, "seul ce que le serveur a confirmé est noté")
            // On la relance (le lancement suivant) : elle continue.
            let second = await deletion().run(uid: uid, miffId: testMiffId)
            XCTAssertEqual(second, .deleted, "relancée après \(cut)")
            XCTAssertEqual(paths.filter { $0 == "miffs/\(testMiffId)/members/uid-a" }.count, 1)
            XCTAssertEqual(paths.filter { $0 == "miffs/\(testMiffId)/members/uid-b" }.count, 1)
            XCTAssertEqual(paths.filter { $0.contains("/places/") }.count, 7, "chaque lieu une seule fois")
            XCTAssertEqual(paths.filter { $0 == "miffsDeleted/\(testMiffId)" }.count, 1)
            XCTAssertTrue(store.memberships.isEmpty)
        }
    }

    func testTheAnswerOfAnyLotIsLostAfterTheLotLeftTheNextRunFinishesWithoutRedoingIt() async {
        for lost in 0...4 {
            store = FakeMiffStore()
            world()
            store.loseAnswerAt = lost

            let first = await deletion().run(uid: uid, miffId: testMiffId)

            if lost == 4 {
                // Le dernier lot est parti : la Miff refuse maintenant son ancien créateur, et le serveur répond : supprimée.
                XCTAssertEqual(first, .deleted, "réponse perdue sur le dernier lot")
            } else {
                var acceptable = false
                switch first {
                case .deleted, .paused:
                    acceptable = true
                case .notOwner:
                    acceptable = false
                }
                XCTAssertTrue(acceptable, "réponse perdue sur le lot \(lost) : \(first)")
                store.loseAnswerAt = -1
                let second = await deletion().run(uid: uid, miffId: testMiffId)
                XCTAssertEqual(second, .deleted)
            }
            let placeDeletions = paths.filter { $0.contains("/places/") }.count
            XCTAssertEqual(placeDeletions, 7, "chaque lieu une seule fois (lot \(lost))")
            XCTAssertEqual(paths.filter { $0 == "miffsDeleted/\(testMiffId)" }.count, 1)
        }
    }

    func testWhenTheMiffIsAlreadyGoneButTheIndexRemainsItIsDroppedAndTheResultIsDeleted() async {
        world()
        store.miffs[testMiffId] = firestoreError(FirestoreErrorNumber.permissionDenied)

        let result = await deletion().run(uid: uid, miffId: testMiffId)

        XCTAssertEqual(result, .deleted)
        XCTAssertEqual(paths, ["users/\(uid)/memberships/free"])
    }

    func testWhenTheMiffIsRefusedButTheServerCannotBeTrustedNothingIsConcluded() async {
        world()
        store.miffs[testMiffId] = firestoreError(FirestoreErrorNumber.permissionDenied)
        // Un refus d'App Check refuse tout : la vérification du serveur échoue aussi, donc personne n'apprend « supprimée ».
        store.reachable = false

        let result = await deletion().run(uid: uid, miffId: testMiffId)

        guard case .paused = result else {
            return XCTFail("la suppression devait s'arrêter, pas conclure : \(result)")
        }
        XCTAssertTrue(store.commitsAsLots.isEmpty)
    }

    func testAPersonWhoIsNotTheCreatorDeletesNothing() async {
        world()
        store.miffs[testMiffId] = testMiffDetails(owner: "someone-else")

        let result = await deletion().run(uid: uid, miffId: testMiffId)

        XCTAssertEqual(result, .notOwner)
        XCTAssertTrue(store.commitsAsLots.isEmpty)
    }

    func testNoNetworkAtTheStartPausesWithoutWritingAnything() async {
        world()
        store.miffs[testMiffId] = firestoreError(FirestoreErrorNumber.unavailable)

        let result = await deletion().run(uid: uid, miffId: testMiffId)

        XCTAssertEqual(result, .paused(.offline))
        XCTAssertTrue(store.commitsAsLots.isEmpty)
    }

    func testTheMembersAreListedFromTheServerAndTheListCanFail() async {
        world()
        store.membersFailure = firestoreError(FirestoreErrorNumber.unavailable)

        let result = await deletion().run(uid: uid, miffId: testMiffId)

        XCTAssertEqual(result, .paused(.offline))
        XCTAssertTrue(store.commitsAsLots.isEmpty)
    }

    func testSomeoneWhoJoinsDuringTheDeletionIsRemovedTooBeforeTheLastLot() async {
        world(others: ["uid-a"])
        let fake = store
        let added = TestBox(false)
        store.onCommit = {
            if !added.value {
                added.value = true
                fake.memberIds.append("uid-late")
            }
        }

        let result = await deletion().run(uid: uid, miffId: testMiffId)

        XCTAssertEqual(result, .deleted)
        let removed = paths.filter { $0.contains("/members/") }.map { String($0.split(separator: "/").last ?? "") }
        XCTAssertTrue(removed.contains("uid-late"))
        XCTAssertEqual(removed.last, uid)
    }

    func testAMiffThatKeepsFillingUpStopsAfterAFewPassesInsteadOfRunningForEver() async {
        world(others: ["uid-a"])
        let fake = store
        let number = TestBox(0)
        store.onCommit = {
            fake.memberIds.append("uid-late-\(number.value)")
            number.value += 1
        }

        let result = await deletion().run(uid: uid, miffId: testMiffId)

        XCTAssertEqual(result, .paused(.unknown))
        XCTAssertFalse(paths.contains("miffsDeleted/\(testMiffId)"))
    }

    func testTheLastLotLeavesOutTheCodeDocumentThatIsAlreadyMissingAndAnIndexThatWasNotFound() async {
        world(others: [], places: [])
        store.codeDocumentExists = false
        store.memberships = []

        let result = await deletion().run(uid: uid, miffId: testMiffId)

        XCTAssertEqual(result, .deleted)
        XCTAssertEqual(lotSizes, [6])
        XCTAssertFalse(paths.contains { $0.hasPrefix("joinCodes/") || $0.hasPrefix("users/") })
    }

    func testAPremiumIndexKeyedByTheMiffIdIsTheOneThatIsDeleted() async {
        world(others: [], places: [])
        store.memberships = [
            MembershipRef(key: testOtherMiffId, miffId: testOtherMiffId),
            MembershipRef(key: testMiffId, miffId: testMiffId)
        ]

        _ = await deletion().run(uid: uid, miffId: testMiffId)

        XCTAssertEqual(store.commitsAsLots.first?.last?.path, "users/\(uid)/memberships/\(testMiffId)")
    }

    func testTheCodesDrawnForTheRemovalsAreNeverTheOldOnes() async {
        world()

        _ = await deletion().run(uid: uid, miffId: testMiffId)

        let used = removalCodes()
        XCTAssertEqual(used.count, 2)
        XCTAssertNotEqual(used[0], used[1])
        XCTAssertTrue(used.allSatisfy { $0.count == 12 })
    }
}
