import XCTest
@testable import Miff

/// L'enregistrement de « Mes informations » sur le profil et sur la fiche de chaque Miff (critères MI7 à MI11 et MI15 du ticket
/// `docs/tickets/modifier-infos-perso.md`) : un seul lot de 15 fiches au plus, le profil d'abord au-delà, le repli quand une Miff a
/// disparu, la reprise quand le réseau manque, jamais de file d'attente.
final class StoreMyProfileRepositoryTests: XCTestCase {
    private let uid = "user-1"
    private let alex = ProfileSummary(firstName: "Alex", lastName: "Martin", phone: "+33612345678", avatarJpeg: nil)

    private func miffId(_ index: Int) -> String {
        "M" + String(format: "%019d", index)
    }

    private func makeStore(miffs count: Int) -> FakeMiffStore {
        let store = FakeMiffStore()
        store.memberships = (0..<count).map { MembershipRef(key: miffId($0), miffId: miffId($0)) }
        return store
    }

    private func makeRepository(_ store: MiffStore, timeout: TimeInterval = 5) -> StoreMyProfileRepository {
        StoreMyProfileRepository(store: store, timeoutSeconds: timeout)
    }

    private func paths(_ lot: [DocWrite]) -> [String] {
        lot.map { $0.path }
    }

    private func commitCount(_ store: FakeMiffStore) -> Int {
        store.calls.filter { $0 == "commit" }.count
    }

    // MARK: - Un seul lot (MI7)

    func testOneMiffIsOneLotWithTheProfileAndTheCopy() async {
        let store = makeStore(miffs: 1)

        let result = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(result, .saved)
        XCTAssertEqual(store.commitsAsLots.count, 1)
        XCTAssertEqual(paths(store.commitsAsLots[0]), ["users/user-1", "miffs/\(miffId(0))/members/user-1"])
        XCTAssertTrue(store.commitsAsLots[0].allSatisfy { $0.kind == .update })
    }

    func testTwoMiffsAreOneLotOfThreeWrites() async {
        let store = makeStore(miffs: 2)

        let result = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(result, .saved)
        XCTAssertEqual(store.commitsAsLots.count, 1)
        XCTAssertEqual(store.commitsAsLots[0].count, 3, "1 profil + 2 fiches = 1 + N écritures")
    }

    func testAnAccountWithoutMiffWritesTheProfileAlone() async {
        let store = makeStore(miffs: 0)

        let result = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(result, .saved)
        XCTAssertEqual(store.commitsAsLots.map(paths), [["users/user-1"]])
    }

    func testFifteenMiffsStillFitInOneLot() async {
        let store = makeStore(miffs: 15)

        let result = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(result, .saved)
        XCTAssertEqual(store.commitsAsLots.count, 1)
        // Le profil et 15 fiches : 15 lectures de règles au plus, sur 20 permises.
        XCTAssertEqual(store.commitsAsLots[0].count, 16)
    }

    func testSixteenMiffsGoInTwoLotsTheProfileFirst() async {
        let store = makeStore(miffs: 16)

        let result = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(result, .saved)
        XCTAssertEqual(store.commitsAsLots.count, 2)
        XCTAssertEqual(store.commitsAsLots[0].first?.path, "users/user-1")
        XCTAssertEqual(store.commitsAsLots[0].count, 16)
        // Le profil n'est pas réécrit.
        XCTAssertEqual(paths(store.commitsAsLots[1]), ["miffs/\(miffId(15))/members/user-1"])
    }

    func testThirtyOneMiffsGoInThreeLotsOfFifteenAtMost() async {
        let store = makeStore(miffs: 31)

        let result = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(result, .saved)
        XCTAssertEqual(store.commitsAsLots.map { $0.count }, [16, 15, 1])
        let copies = store.commitsAsLots.flatMap(paths).filter { $0.hasPrefix("miffs/") }
        XCTAssertEqual(Set(copies).count, 31, "chaque Miff est écrite une fois, aucune n'est oubliée")
    }

    /// Critère MI18 : le magasin sert d'espion ; le nombre d'écritures est 1 + N (N = Miffs), l'index est lu une seule fois.
    func testTheWriteCountIsOnePlusN() async {
        for count in [0, 1, 2, 5, 15, 16, 20, 31] {
            let store = makeStore(miffs: count)

            let result = await makeRepository(store).save(uid: uid, profile: alex)

            XCTAssertEqual(result, .saved)
            XCTAssertEqual(store.commitsAsLots.flatMap { $0 }.count, 1 + count, "\(count) Miffs")
            XCTAssertEqual(store.calls.filter { $0 == "memberships:server" }.count, 1)
            XCTAssertLessThanOrEqual(store.commitsAsLots.map { $0.count }.max() ?? 0, 16)
        }
    }

    func testTheIndexIsReadFromTheServerNeverFromTheCache() async {
        let store = makeStore(miffs: 2)

        _ = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(store.calls.first, "memberships:server")
        XCTAssertFalse(store.calls.contains("memberships:cache"))
    }

    func testTwoIndexesOfTheSameMiffAndMalformedIdsMakeOneCopyAndNoBadPath() async {
        let store = FakeMiffStore()
        store.memberships = [
            MembershipRef(key: "free", miffId: miffId(1)),
            MembershipRef(key: miffId(1), miffId: miffId(1)),
            MembershipRef(key: "x", miffId: "../users/other"),
            MembershipRef(key: "y", miffId: "court")
        ]

        let result = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(result, .saved)
        XCTAssertEqual(store.commitsAsLots.map(paths), [["users/user-1", "miffs/\(miffId(1))/members/user-1"]])
    }

    // MARK: - Le contenu des écritures (MI15)

    func testEverySaveWritesTheFourKeysAndNothingElse() async {
        let store = makeStore(miffs: 1)
        let photo = Data(repeating: 7, count: 1_000)
        let profile = ProfileSummary(firstName: "Alex", lastName: "Martin", phone: "+33612345678", avatarJpeg: photo)

        _ = await makeRepository(store).save(uid: uid, profile: profile)

        for write in store.commitsAsLots[0] {
            XCTAssertEqual(Set(write.data.keys), ["firstName", "lastName", "phone", "avatar"], write.path)
            XCTAssertEqual(write.data["firstName"], FieldValueSpec.string("Alex"))
            XCTAssertEqual(write.data["lastName"], FieldValueSpec.string("Martin"))
            XCTAssertEqual(write.data["phone"], FieldValueSpec.string("+33612345678"))
            XCTAssertEqual(write.data["avatar"], FieldValueSpec.bytes(photo))
        }
    }

    func testWithoutAPhotoTheKeyIsRemovedNotSetToNull() async {
        let store = makeStore(miffs: 1)

        _ = await makeRepository(store).save(uid: uid, profile: alex)

        for write in store.commitsAsLots[0] {
            XCTAssertEqual(write.data["avatar"], FieldValueSpec.deleteField, write.path)
        }
    }

    func testNoStatusOrPositionDocumentIsEverTouched() async {
        let store = makeStore(miffs: 20)

        _ = await makeRepository(store).save(uid: uid, profile: alex)

        let touched = store.commitsAsLots.flatMap(paths)
        XCTAssertTrue(touched.allSatisfy { $0 == "users/user-1" || $0.hasSuffix("/members/user-1") })
        let others = ["/status/", "/photos/", "/positions/"]
        XCTAssertFalse(touched.contains { path in others.contains { path.contains($0) } })
    }

    // MARK: - Une Miff disparue ne bloque rien (MI8)

    func testAMiffThatNoLongerExistsDoesNotBlockTheProfile() async {
        let store = makeStore(miffs: 3)
        // Le lot entier est refusé ; le profil passe seul ; la fiche de la Miff disparue est refusée seule ; les deux autres passent.
        store.commitFailures = [
            firestoreError(FirestoreErrorNumber.permissionDenied), nil,
            nil, firestoreError(FirestoreErrorNumber.permissionDenied), nil
        ]

        let result = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(result, .saved, "aucun message d'erreur : la fiche absente est ignorée")
        XCTAssertEqual(
            store.commitsAsLots.map(paths),
            [["users/user-1"], ["miffs/\(miffId(0))/members/user-1"], ["miffs/\(miffId(2))/members/user-1"]]
        )
    }

    func testIfEveryCopyIsRefusedTheProfileIsStillSaved() async {
        let store = makeStore(miffs: 2)
        let refused = firestoreError(FirestoreErrorNumber.permissionDenied)
        store.commitFailures = [refused, nil, refused, refused]

        let result = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(result, .saved)
        XCTAssertEqual(store.commitsAsLots.map(paths), [["users/user-1"]])
    }

    func testAnExtraGroupRefusedFallsBackToOneCopyAtATime() async {
        let store = makeStore(miffs: 17)
        store.commitFailures = [nil, firestoreError(FirestoreErrorNumber.permissionDenied), nil, nil]

        let result = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(result, .saved)
        XCTAssertEqual(store.commitsAsLots.count, 3)
        XCTAssertEqual(paths(store.commitsAsLots[1]), ["miffs/\(miffId(15))/members/user-1"])
        XCTAssertEqual(paths(store.commitsAsLots[2]), ["miffs/\(miffId(16))/members/user-1"])
    }

    // MARK: - Hors connexion (MI9, MI10)

    func testOfflineNothingIsWrittenAndNothingIsQueued() async {
        let store = makeStore(miffs: 2)
        store.commitFailures = [firestoreError(FirestoreErrorNumber.unavailable)]

        let result = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(result, .offline)
        XCTAssertTrue(store.commitsAsLots.isEmpty)
        XCTAssertEqual(commitCount(store), 1, "aucune nouvelle tentative automatique")
    }

    func testAnIndexThatCannotBeReadIsOfflineAndWritesNothing() async {
        let store = makeStore(miffs: 2)
        store.membershipsFailure = firestoreError(FirestoreErrorNumber.unavailable)

        let result = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(result, .offline)
        XCTAssertEqual(commitCount(store), 0)
    }

    func testAnIndexRefusedIsARefusal() async {
        let store = makeStore(miffs: 2)
        store.membershipsFailure = firestoreError(FirestoreErrorNumber.permissionDenied)

        let result = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(result, .refused)
        XCTAssertEqual(commitCount(store), 0)
    }

    func testTheNetworkLostAfterTheFirstLotLeavesTheLaterCopiesPendingWithoutTryingEachOne() async {
        let store = makeStore(miffs: 46)
        store.commitFailures = [nil, firestoreError(FirestoreErrorNumber.unavailable)]

        let result = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(result, .savedPartial)
        XCTAssertEqual(store.commitsAsLots.count, 1, "le profil et 15 fiches sont écrits")
        XCTAssertEqual(commitCount(store), 2, "ni le troisième groupe, ni les fiches une à une")
    }

    func testTheNetworkLostDuringTheCopyByCopyFallbackLeavesTheRestPending() async {
        let store = makeStore(miffs: 4)
        store.commitFailures = [
            firestoreError(FirestoreErrorNumber.permissionDenied), nil,
            nil, firestoreError(FirestoreErrorNumber.unavailable)
        ]

        let result = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(result, .savedPartial)
        // Le lot, le profil seul, une fiche écrite, une fiche hors ligne : puis on s'arrête.
        XCTAssertEqual(commitCount(store), 4)
    }

    func testSavingAgainWritesEveryCopyAgainSoALateCopyIsRepaired() async {
        let store = makeStore(miffs: 2)

        _ = await makeRepository(store).save(uid: uid, profile: alex)
        _ = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(store.commitsAsLots.count, 2)
        XCTAssertEqual(paths(store.commitsAsLots[0]), paths(store.commitsAsLots[1]))
    }

    func testASlowServerIsTreatedAsOfflineAfterTheTimeout() async {
        let slow = SlowCommitStore(makeStore(miffs: 1))

        let result = await makeRepository(slow, timeout: 0.1).save(uid: uid, profile: alex)

        XCTAssertEqual(result, .offline)
    }

    // MARK: - Refus du serveur (MI11) et ancien compte sans termsVersion

    func testARefusalWithoutMiffIsARefusalAndTheProfileAloneIsNotRetried() async {
        // Un ancien compte sans `termsVersion` : les règles refusent toute mise à jour du profil.
        let store = makeStore(miffs: 0)
        store.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied)]

        let result = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(result, .refused)
        XCTAssertEqual(commitCount(store), 1)
    }

    func testAnOlderAccountWithMiffsIsRefusedAfterTheProfileAloneIsRefusedToo() async {
        let store = makeStore(miffs: 2)
        let refused = firestoreError(FirestoreErrorNumber.permissionDenied)
        store.commitFailures = [refused, refused]

        let result = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(result, .refused)
        XCTAssertTrue(store.commitsAsLots.isEmpty, "rien n'est écrit")
        XCTAssertEqual(commitCount(store), 2)
    }

    func testAnUnknownErrorIsAFailureNotAnOfflineMessage() async {
        let store = makeStore(miffs: 0)
        store.commitFailures = [NSError(domain: "autre", code: 1)]

        let result = await makeRepository(store).save(uid: uid, profile: alex)

        XCTAssertEqual(result, .failed)
    }

    // MARK: - Valeurs refusées avant tout envoi

    func testAValueTheRulesWouldRefuseIsNeverSent() async {
        let store = makeStore(miffs: 1)
        let repo = makeRepository(store)
        let tooLongName = String(repeating: "a", count: 51)
        let bigPhoto = Data(count: ProfileDocument.maxAvatarBytes + 1)
        let invalid: [ProfileSummary] = [
            ProfileSummary(firstName: "Alex", lastName: "Martin", phone: nil, avatarJpeg: nil),
            ProfileSummary(firstName: "Alex", lastName: "Martin", phone: "0612345678", avatarJpeg: nil),
            ProfileSummary(firstName: tooLongName, lastName: "Martin", phone: "+33612345678", avatarJpeg: nil),
            ProfileSummary(firstName: "Alex", lastName: " ", phone: "+33612345678", avatarJpeg: nil),
            ProfileSummary(firstName: "Alex", lastName: "Martin", phone: "+33612345678", avatarJpeg: bigPhoto)
        ]

        for profile in invalid {
            let result = await repo.save(uid: uid, profile: profile)
            XCTAssertEqual(result, .failed)
        }
        let strangeUid = await repo.save(uid: "a/b", profile: alex)
        let emptyUid = await repo.save(uid: "", profile: alex)

        XCTAssertEqual(strangeUid, .failed)
        XCTAssertEqual(emptyUid, .failed)
        XCTAssertTrue(store.calls.isEmpty, "rien n'est lu ni écrit")
    }

    func testTheLimitOfFiftyUnitsAndOfTheTwentyThousandBytesIsAccepted() async {
        let store = makeStore(miffs: 0)
        let name = String(repeating: "é", count: 50)
        let photo = Data(count: ProfileDocument.maxAvatarBytes)
        let profile = ProfileSummary(firstName: name, lastName: name, phone: "+33612345678", avatarJpeg: photo)

        let result = await makeRepository(store).save(uid: uid, profile: profile)

        XCTAssertEqual(result, .saved)
    }
}

/// Un magasin dont les écritures ne répondent jamais (réseau qui s'arrête en route).
private final class SlowCommitStore: MiffStoreByDelegation {
    override func commit(_ writes: [DocWrite]) async throws {
        try await Task.sleep(nanoseconds: 2_000_000_000)
    }
}
