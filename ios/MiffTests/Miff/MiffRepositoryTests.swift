// Scénarios de test longs par nature (un cas par méthode) : les limites de longueur ne s'appliquent pas ici.
// swiftlint:disable file_length type_body_length
import XCTest
@testable import Miff

/// Un compteur ou une valeur que les closures du dépôt lisent et que le test change.
final class TestBox<Value> {
    var value: Value

    init(_ value: Value) {
        self.value = value
    }
}

/// Un magasin dont la lecture du code échoue comme hors ligne.
private final class OfflineCodeStore: MiffStoreByDelegation {
    override func readJoinCode(_ code: String) async throws -> [String: Any]? {
        throw firestoreError(FirestoreErrorNumber.unavailable)
    }
}

/// Un magasin où la Miff est « absente » auprès du serveur mais que le cache du téléphone connaît encore.
private final class GhostStore: MiffStoreByDelegation {
    override func readMiff(miffId: String, fromServer: Bool) async throws -> MiffRead {
        fromServer ? .missing : .exists(testMiffDetails(name: "Chez nous"))
    }
}

/// La logique du ticket Miff §5.4 à §5.7 et §5.13 avec Firestore remplacé par un double : l'ordre des vérifications, ce qui est
/// écrit et en combien de lots, comment une erreur est lue (une coupure réseau n'est jamais une perte d'accès), le nettoyage
/// d'un index obsolète.
@MainActor
final class MiffRepositoryTests: XCTestCase {
    private let uid = "uid-1"
    private let store = FakeMiffStore()
    private let location = FakeLocationProbe()
    private let drawn = TestBox<[String]>([])
    private let now = TestBox(1_000_000)
    private let timeline = Timeline()
    private lazy var cache = FakeCachePurger(timeline: timeline)

    private func repo(using custom: MiffStore? = nil) -> FirestoreMiffRepository {
        let draws = drawn
        let clock = now
        let rules = MiffRepositoryRules(
            newMiffId: { testMiffId },
            newCode: { draws.value.isEmpty ? testCode : draws.value.removeFirst() },
            epochMillis: { clock.value },
            timeoutSeconds: 1
        )
        return FirestoreMiffRepository(
            store: custom ?? store,
            observer: FakeMiffObserver(),
            location: location,
            cache: cache,
            rules: rules
        )
    }

    private func joinable() {
        store.codes[testCode] = ["miffId": testMiffId, "miffName": "Chez nous"]
        store.miffs[testMiffId] = testMiffDetails()
    }

    private func readyToJoin(_ preparation: JoinPreparation) -> JoinReady {
        guard case .ready(let ready) = preparation else {
            XCTFail("la préparation devrait être prête")
            return JoinReady(miffId: "", miffName: "", code: "", premium: false, firstMiff: false)
        }
        return ready
    }

    private func readyToCreate(_ preparation: CreatePreparation) -> CreateReady {
        guard case .ready(let ready) = preparation else {
            XCTFail("la préparation devrait être prête")
            return CreateReady(premium: false, firstMiff: false)
        }
        return ready
    }

    private func loaded(_ load: MyMiffsLoad) -> MyMiffs? {
        if case .loaded(let value) = load {
            return value
        }
        XCTFail("la lecture devrait avoir réussi")
        return nil
    }

    private func commitCount() -> Int {
        store.calls.filter { $0 == "commit" }.count
    }

    // MARK: - Rejoindre : l'ordre des vérifications (ticket §5.4)

    func testPrepareJoinAWrongShapeIsNotFoundWithoutAnyRead() async {
        let first = await repo().prepareJoin(uid: uid, typedCode: "XTZRSP36798")
        let second = await repo().prepareJoin(uid: uid, typedCode: "XTZRSP36798I")

        XCTAssertEqual(first, .notFound)
        XCTAssertEqual(second, .notFound)
        XCTAssertTrue(store.calls.isEmpty, "un code de mauvaise forme ne coûte aucune lecture réseau (critère J2)")
    }

    func testPrepareJoinReadsTheCodeFirstThenTheIndexesAndWritesNothing() async {
        joinable()

        let result = await repo().prepareJoin(uid: uid, typedCode: "xtzr sp36 7988")

        guard case .ready = result else {
            return XCTFail("le code devrait être reconnu")
        }
        XCTAssertEqual(store.calls.first, "code")
        XCTAssertTrue((store.calls.firstIndex(of: "memberships:server") ?? 0) > 0, "les index sont lus après le code")
        XCTAssertFalse(store.calls.contains("commit"), "rien n'est écrit avant la confirmation (critères J6 et K3)")
        XCTAssertTrue(store.commitsAsLots.isEmpty)
    }

    func testPrepareJoinWrongRenewedOrDeletedCodeIsTheSameAnswer() async {
        // Un code qui n'existe pas (faux, renouvelé ou supprimé : le document n'est tout simplement pas là).
        let missing = await repo().prepareJoin(uid: uid, typedCode: testCode)
        XCTAssertEqual(missing, .notFound)
        // Un document qui n'a pas la forme exigée par les règles.
        store.codes[testCode] = ["miffId": "short", "miffName": "x"]
        let badId = await repo().prepareJoin(uid: uid, typedCode: testCode)
        XCTAssertEqual(badId, .notFound)
        store.codes[testCode] = ["miffId": testMiffId]
        let noName = await repo().prepareJoin(uid: uid, typedCode: testCode)
        XCTAssertEqual(noName, .notFound)
    }

    func testPrepareJoinAnOfflineReadOfTheCodeIsANetworkProblemNotANotFound() async {
        let result = await repo(using: OfflineCodeStore(store)).prepareJoin(uid: uid, typedCode: testCode)

        XCTAssertEqual(result, .failed(.offline))
    }

    func testPrepareJoinCleansTheNameOfTheMiffReadFromTheServer() async {
        store.codes[testCode] = ["miffId": testMiffId, "miffName": "Les\u{202E} copains\u{0007}"]

        let ready = readyToJoin(await repo().prepareJoin(uid: uid, typedCode: testCode))

        XCTAssertEqual(ready.miffName, "Les copains")
    }

    func testPrepareJoinANameWithNothingVisibleLeftIsTheSameAnswerAsAnUnknownCode() async {
        store.codes[testCode] = ["miffId": testMiffId, "miffName": "\u{202E}\u{200B} \u{FEFF}"]
        store.miffs[testMiffId] = testMiffDetails()

        let result = await repo().prepareJoin(uid: uid, typedCode: testCode)

        XCTAssertEqual(result, .notFound)
    }

    func testPrepareJoinAStaleIndexIsCleanedBeforeAnythingElseAndTheEntryGoesOn() async throws {
        joinable()
        // La personne a été retirée d'une autre Miff : l'index est encore là, la Miff répond « absente ».
        store.memberships = [MembershipRef(key: "free", miffId: testOtherMiffId)]

        let result = await repo().prepareJoin(uid: uid, typedCode: testCode)

        guard case .ready = result else {
            return XCTFail("l'index obsolète ne doit pas bloquer l'entrée (critère J8)")
        }
        let cleanup = try XCTUnwrap(store.commitsAsLots.first)
        XCTAssertEqual(store.commitsAsLots.count, 1)
        XCTAssertEqual(cleanup.first?.kind, .delete)
        XCTAssertTrue(cleanup.contains { $0.path == "users/\(uid)/memberships/free" })
    }

    func testPrepareJoinARefusalOfTheRulesOnAnIndexIsALossCleanedByTheLeaveLotThenTheIndexAlone() async {
        joinable()
        store.memberships = [MembershipRef(key: "free", miffId: testOtherMiffId)]
        store.miffs[testOtherMiffId] = firestoreError(FirestoreErrorNumber.permissionDenied)
        // La fiche a disparu (retrait) : le lot « Quitter » complet est refusé, l'index seul est accepté.
        store.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied), nil]

        let result = await repo().prepareJoin(uid: uid, typedCode: testCode)

        guard case .ready = result else {
            return XCTFail("l'entrée devrait continuer")
        }
        XCTAssertEqual(store.commitsAsLots.count, 1, "seul le lot qui est passé est enregistré")
        XCTAssertEqual(store.commitsAsLots.first?.map { $0.path }, ["users/\(uid)/memberships/free"])
        XCTAssertEqual(commitCount(), 2)
    }

    func testARefusalWhenTheServerIsNotReachableIsNotALossNothingIsCleaned() async {
        joinable()
        store.memberships = [MembershipRef(key: "free", miffId: testOtherMiffId)]
        store.miffs[testOtherMiffId] = firestoreError(FirestoreErrorNumber.permissionDenied)
        store.reachable = false

        let result = await repo().prepareJoin(uid: uid, typedCode: testCode)

        XCTAssertEqual(commitCount(), 0, "un doute n'est jamais un retrait : aucun nettoyage")
        // L'index reste (un compte gratuit qui a peut-être encore une Miff) : en rejoindre une autre est l'affaire du ticket Premium.
        XCTAssertEqual(result, .needsPremium)
    }

    func testANetworkCutWhileReadingAMiffIsNeverALossOfAccess() async {
        store.memberships = [MembershipRef(key: "free", miffId: testOtherMiffId)]
        store.miffs[testOtherMiffId] = firestoreError(FirestoreErrorNumber.unavailable)

        let load = loaded(await repo().myMiffs(uid: uid))

        XCTAssertEqual(load?.lost.isEmpty, true)
        XCTAssertEqual(commitCount(), 0)
    }

    func testAlreadyAMemberIsFoundByTheFieldMiffIdNotByTheIdOfTheDocument() async {
        joinable()
        // Le document d'index s'appelle « free » : seul son champ miffId dit de quelle Miff il s'agit.
        store.memberships = [MembershipRef(key: "free", miffId: testMiffId)]

        let result = await repo().prepareJoin(uid: uid, typedCode: testCode)

        XCTAssertEqual(result, .alreadyMember(miffId: testMiffId, miffName: "Chez nous"))
        XCTAssertTrue(store.commitsAsLots.isEmpty, "rien n'est écrit")
    }

    func testAFreeAccountWithAnotherLiveMiffIsNotLetThroughAPremiumOneIs() async {
        joinable()
        store.memberships = [MembershipRef(key: "free", miffId: testOtherMiffId)]
        store.miffs[testOtherMiffId] = testMiffDetails(id: testOtherMiffId, name: "Autre")
        let refused = await repo().prepareJoin(uid: uid, typedCode: testCode)
        XCTAssertEqual(refused, .needsPremium)

        store.entitlement = EntitlementRead(active: true, expiresAtMillis: now.value + 10_000)
        let premium = readyToJoin(await repo().prepareJoin(uid: uid, typedCode: testCode))

        XCTAssertTrue(premium.premium)
        XCTAssertFalse(premium.firstMiff, "ce n'est pas la première Miff")
    }

    func testAnExpiredEntitlementIsNotPremium() async {
        store.entitlement = EntitlementRead(active: true, expiresAtMillis: now.value - 1)
        XCTAssertEqual(loaded(await repo().myMiffs(uid: uid))?.premium, false)

        store.entitlement = EntitlementRead(active: false, expiresAtMillis: now.value + 1_000)
        XCTAssertEqual(loaded(await repo().myMiffs(uid: uid))?.premium, false)
    }

    // MARK: - Rejoindre : le lot (ticket §5.4, étape 7)

    func testJoinIsOneLotOfThreeAndTheSuccessComesAfterTheServer() async {
        joinable()
        let prepared = readyToJoin(await repo().prepareJoin(uid: uid, typedCode: testCode))

        let result = await repo().join(uid: uid, preparation: prepared)

        XCTAssertEqual(result, .joined(miffId: testMiffId, firstMiff: true))
        XCTAssertEqual(store.commitsAsLots.count, 1)
        let lot = store.commitsAsLots.first ?? []
        XCTAssertEqual(lot.count, 3)
        XCTAssertTrue(lot.contains { $0.path == "users/\(uid)/memberships/free" })
    }

    func testJoinIfOneWriteIsRefusedNothingIsWrittenAndItIsTheOneMessageOfNotFound() async {
        joinable()
        let prepared = readyToJoin(await repo().prepareJoin(uid: uid, typedCode: testCode))
        store.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied)]

        let result = await repo().join(uid: uid, preparation: prepared)

        XCTAssertEqual(result, .notFound)
        XCTAssertTrue(store.commitsAsLots.isEmpty, "un lot refusé ne laisse rien derrière lui")
    }

    func testJoinWithoutNetworkIsOfflineNothingIsQueued() async {
        joinable()
        let prepared = readyToJoin(await repo().prepareJoin(uid: uid, typedCode: testCode))
        store.commitFailures = [firestoreError(FirestoreErrorNumber.unavailable)]
        // Le serveur ne peut pas non plus être joint pour confirmer.
        store.miffs[testMiffId] = firestoreError(FirestoreErrorNumber.unavailable)

        let result = await repo().join(uid: uid, preparation: prepared)

        XCTAssertEqual(result, .failed(.offline))
        XCTAssertEqual(commitCount(), 1, "un essai, aucune reprise en arrière-plan")
        XCTAssertTrue(store.commitsAsLots.isEmpty)
    }

    func testJoinALotThatLeftButWasNotConfirmedIsNotShownAsAFailure() async {
        joinable()
        let prepared = readyToJoin(await repo().prepareJoin(uid: uid, typedCode: testCode))
        store.commitFailures = [firestoreError(FirestoreErrorNumber.deadlineExceeded)]

        // Le serveur a bien reçu le lot : la Miff peut être lue.
        let result = await repo().join(uid: uid, preparation: prepared)

        XCTAssertEqual(result, .joined(miffId: testMiffId, firstMiff: true))
    }

    func testJoinTheStatusUsesTheRealPermissionOfThePhone() async {
        joinable()
        location.access = .foreground
        let prepared = readyToJoin(await repo().prepareJoin(uid: uid, typedCode: testCode))

        _ = await repo().join(uid: uid, preparation: prepared)

        let status = store.commitsAsLots.first?.first { $0.path.contains("/status/") }
        XCTAssertEqual(status?.data["locationAccess"], .string("foreground"))
    }

    func testJoinWithoutAProfileIsNotAMessageAboutTheCode() async {
        joinable()
        let prepared = readyToJoin(await repo().prepareJoin(uid: uid, typedCode: testCode))
        store.profile = nil

        let result = await repo().join(uid: uid, preparation: prepared)

        XCTAssertEqual(result, .failed(.noProfile))
    }

    // MARK: - Créer (ticket §5.3, critères C4, C5, C7)

    func testCreateIsOneLotOfFiveWithTheCodeDrawnAtTheSave() async {
        let preparation = readyToCreate(await repo().prepareCreate(uid: uid))
        XCTAssertTrue(store.commitsAsLots.isEmpty, "la préparation ne tire ni n'écrit rien")

        let result = await repo().create(uid: uid, name: "  Chez nous ", preparation: preparation)

        let creation = MiffCreation(miffId: testMiffId, joinCode: testCode, name: "Chez nous", firstMiff: true)
        XCTAssertEqual(result, .created(creation))
        XCTAssertEqual(store.commitsAsLots.count, 1)
        let lot = store.commitsAsLots.first ?? []
        XCTAssertEqual(lot.count, 5)
        // Un compte gratuit : la clé « free ».
        XCTAssertTrue(lot.contains { $0.path == "users/\(uid)/memberships/free" })
    }

    func testCreateAPremiumAccountKeysTheIndexByTheMiffId() async {
        store.entitlement = EntitlementRead(active: true, expiresAtMillis: now.value + 10_000)
        let preparation = readyToCreate(await repo().prepareCreate(uid: uid))

        _ = await repo().create(uid: uid, name: "Chez nous", preparation: preparation)

        let paths = store.commitsAsLots.first?.map { $0.path } ?? []
        XCTAssertTrue(paths.contains("users/\(uid)/memberships/\(testMiffId)"))
    }

    func testCreateACodeAlreadyTakenIsDrawnAgainUpToThreeTimesWithoutAMessage() async {
        drawn.value = ["AAAAAAAAAAAA", "BBBBBBBBBBBB", "CCCCCCCCCCCC"]
        store.codes["AAAAAAAAAAAA"] = ["miffId": testOtherMiffId, "miffName": "x"]
        store.codes["BBBBBBBBBBBB"] = ["miffId": testOtherMiffId, "miffName": "x"]
        let preparation = readyToCreate(await repo().prepareCreate(uid: uid))

        let result = await repo().create(uid: uid, name: "Chez nous", preparation: preparation)

        guard case .created(let creation) = result else {
            return XCTFail("la Miff devrait être créée")
        }
        XCTAssertEqual(creation.joinCode, "CCCCCCCCCCCC")
    }

    func testCreateThreeCodesTakenEndsWithTheGenericFailureAndNothingIsWritten() async {
        drawn.value = ["AAAAAAAAAAAA", "BBBBBBBBBBBB", "CCCCCCCCCCCC"]
        for code in drawn.value {
            store.codes[code] = ["miffId": testOtherMiffId, "miffName": "x"]
        }
        let preparation = readyToCreate(await repo().prepareCreate(uid: uid))

        let result = await repo().create(uid: uid, name: "Chez nous", preparation: preparation)

        XCTAssertEqual(result, .failed(.unknown))
        XCTAssertTrue(store.commitsAsLots.isEmpty)
    }

    func testCreateAnInvalidDrawnCodeIsNeverWritten() async {
        drawn.value = ["", "", ""]
        let preparation = readyToCreate(await repo().prepareCreate(uid: uid))

        let result = await repo().create(uid: uid, name: "Chez nous", preparation: preparation)

        XCTAssertEqual(result, .failed(.unknown))
        XCTAssertEqual(commitCount(), 0)
    }

    func testCreateWithoutNetworkNoMiffAppearsNothingIsQueued() async {
        let preparation = readyToCreate(await repo().prepareCreate(uid: uid))
        store.commitFailures = [firestoreError(FirestoreErrorNumber.unavailable)]
        store.miffs[testMiffId] = firestoreError(FirestoreErrorNumber.unavailable)

        let result = await repo().create(uid: uid, name: "Chez nous", preparation: preparation)

        XCTAssertEqual(result, .failed(.offline))
        XCTAssertTrue(store.commitsAsLots.isEmpty)
        XCTAssertEqual(commitCount(), 1)
    }

    func testCreateIfOneWriteIsRefusedNothingIsWritten() async {
        let preparation = readyToCreate(await repo().prepareCreate(uid: uid))
        store.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied)]

        let result = await repo().create(uid: uid, name: "Chez nous", preparation: preparation)

        XCTAssertEqual(result, .failed(.refused))
        XCTAssertTrue(store.commitsAsLots.isEmpty)
    }

    func testCreateWithoutAProfileIsNotAMessageAboutTheCode() async {
        let preparation = readyToCreate(await repo().prepareCreate(uid: uid))
        store.profile = nil

        let result = await repo().create(uid: uid, name: "Chez nous", preparation: preparation)

        XCTAssertEqual(result, .failed(.noProfile))
    }

    func testCreateAFreeAccountThatAlreadyHasAMiffIsHandedToThePremiumTicketNothingIsWritten() async {
        store.memberships = [MembershipRef(key: "free", miffId: testOtherMiffId)]
        store.miffs[testOtherMiffId] = testMiffDetails(id: testOtherMiffId)

        let preparation = await repo().prepareCreate(uid: uid)

        XCTAssertEqual(preparation, .needsPremium)
        XCTAssertTrue(store.commitsAsLots.isEmpty)
    }

    func testCreateATooShortNameIsRefusedBeforeAnyWrite() async {
        let preparation = readyToCreate(await repo().prepareCreate(uid: uid))

        let result = await repo().create(uid: uid, name: " a ", preparation: preparation)

        XCTAssertEqual(result, .failed(.unknown))
        XCTAssertEqual(commitCount(), 0)
    }

    // MARK: - Les Miffs de la personne

    func testMyMiffsOfflineFallsBackOnTheCacheAndSaysSo() async {
        store.memberships = [MembershipRef(key: "free", miffId: testMiffId)]
        store.membershipsFailure = firestoreError(FirestoreErrorNumber.unavailable)
        store.miffs[testMiffId] = testMiffDetails(name: "Chez nous")

        let load = loaded(await repo().myMiffs(uid: uid))

        XCTAssertEqual(load?.fromCache, true)
        XCTAssertEqual(load?.miffs.map { $0.name }, ["Chez nous"])
        XCTAssertEqual(load?.lost.isEmpty, true)
    }

    func testMyMiffsNothingAtAllToShowIsUnavailableNotAnEmptyListThatLooksLikeNoMiff() async {
        store.membershipsFailure = firestoreError(FirestoreErrorNumber.unavailable)
        store.cachedMembershipsFailure = firestoreError(FirestoreErrorNumber.unavailable)

        let result = await repo().myMiffs(uid: uid)

        XCTAssertEqual(result, .unavailable)
    }

    func testOfflineWithAnEmptyCacheIsNotNoMiffItIsUnknown() async {
        store.membershipsFailure = firestoreError(FirestoreErrorNumber.unavailable)
        store.memberships = []

        // Une installation neuve réaliste : le serveur est injoignable, le cache répond par une liste vide.
        let result = await repo().myMiffs(uid: uid)

        XCTAssertEqual(result, .unavailable)
    }

    func testMyMiffsADeletedMiffIsLostWithItsName() async {
        store.memberships = [MembershipRef(key: "free", miffId: testMiffId)]

        let load = loaded(await repo(using: GhostStore(store)).myMiffs(uid: uid))

        XCTAssertEqual(load?.lost.first?.name, "Chez nous")
        XCTAssertEqual(load?.miffs.isEmpty, true)
    }

    func testNothingHeldMeansNoMiffAndNoReadOfAnyMiff() async {
        store.memberships = []

        let load = loaded(await repo().myMiffs(uid: uid))

        XCTAssertEqual(load?.miffs.isEmpty, true)
        XCTAssertEqual(load?.lost.isEmpty, true)
        XCTAssertFalse(store.calls.contains { $0.hasPrefix("miff:") })
    }

    // MARK: - Renommer, partager, nettoyer

    func testRenameIsOneLotOfTwoAndABadNameWritesNothing() async {
        let done = await repo().rename(miffId: testMiffId, code: testCode, name: "Nouveau nom")
        XCTAssertEqual(done, .done)
        XCTAssertEqual(store.commitsAsLots.first?.count, 2)

        let refused = await repo().rename(miffId: testMiffId, code: testCode, name: "x")

        XCTAssertEqual(refused, .failed(.unknown))
        XCTAssertEqual(store.commitsAsLots.count, 1)
    }

    func testSetSharingStopIsTheLotOfThreeResumeTheLotOfTwo() async {
        let stop = await repo().setSharing(miffId: testMiffId, uid: uid, enabled: false)
        XCTAssertEqual(stop, .done)
        XCTAssertEqual(store.commitsAsLots.last?.count, 3)

        let resume = await repo().setSharing(miffId: testMiffId, uid: uid, enabled: true)

        XCTAssertEqual(resume, .done)
        XCTAssertEqual(store.commitsAsLots.last?.count, 2)
    }

    func testSetSharingOfflineIsAFailureAndNothingIsRecorded() async {
        store.commitFailures = [firestoreError(FirestoreErrorNumber.unavailable)]

        let result = await repo().setSharing(miffId: testMiffId, uid: uid, enabled: false)

        XCTAssertEqual(result, .failed(.offline))
        XCTAssertTrue(store.commitsAsLots.isEmpty)
    }

    func testCleanUpLostTriesTheLeaveLotThenTheIndexAlone() async {
        let lost = LostMiff(ref: MembershipRef(key: "free", miffId: testMiffId), name: "Chez nous")
        let first = await repo().cleanUpLost(uid: uid, lost: lost)
        XCTAssertTrue(first)
        XCTAssertEqual(store.commitsAsLots.first?.count, 4)
        store.clearLots()
        store.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied), nil]

        let second = await repo().cleanUpLost(uid: uid, lost: lost)

        XCTAssertTrue(second)
        XCTAssertEqual(store.commitsAsLots.first?.count, 1)
    }

    func testAStaleIndexCleanedWhileCreatingOrJoiningNeverLeavesThatMiffReadableOffline() async {
        joinable()
        store.memberships = [MembershipRef(key: "free", miffId: testOtherMiffId)]

        _ = await repo().prepareJoin(uid: uid, typedCode: testCode)

        XCTAssertEqual(timeline.entries.first, "markPending")
        XCTAssertEqual(cache.purgeCalls, 1, "le cache du téléphone est purgé après le nettoyage silencieux")
        // Rien à nettoyer : pas de purge (une purge arrête les écoutes de la personne pour rien).
        let before = cache.purgeCalls
        store.memberships = []

        _ = await repo().prepareCreate(uid: uid)

        XCTAssertEqual(cache.purgeCalls, before)
    }

    func testRenameALotWhoseAnswerWasLostIsCheckedOnTheServerBeforeItIsCalledAFailure() async {
        store.commitFailures = [firestoreError(FirestoreErrorNumber.deadlineExceeded)]
        store.miffs[testMiffId] = testMiffDetails(name: "Nouveau nom")
        let landed = await repo().rename(miffId: testMiffId, code: testCode, name: "Nouveau nom")
        XCTAssertEqual(landed, .done)

        store.commitFailures = [firestoreError(FirestoreErrorNumber.deadlineExceeded)]
        store.miffs[testMiffId] = testMiffDetails(name: "Ancien nom")
        let notLanded = await repo().rename(miffId: testMiffId, code: testCode, name: "Nouveau nom")

        XCTAssertEqual(notLanded, .failed(.offline))
    }

    func testSetSharingARefusalBecauseTheCardAlreadyHasTheWantedStateIsASuccess() async {
        // Arrêt demandé, mais un autre téléphone a déjà arrêté : les règles refusent faux -> faux.
        store.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied)]
        store.sharing = false
        let stopped = await repo().setSharing(miffId: testMiffId, uid: uid, enabled: false)
        XCTAssertEqual(stopped, .done)

        // Reprise demandée, mais un autre téléphone a déjà repris.
        store.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied)]
        store.sharing = true
        let resumed = await repo().setSharing(miffId: testMiffId, uid: uid, enabled: true)

        XCTAssertEqual(resumed, .done)
    }

    func testSetSharingARefusalWhileTheCardIsNotInTheWantedStateStaysAFailure() async {
        store.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied)]
        store.sharing = true
        let first = await repo().setSharing(miffId: testMiffId, uid: uid, enabled: false)
        XCTAssertEqual(first, .failed(.refused))

        store.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied)]
        store.sharing = nil

        let second = await repo().setSharing(miffId: testMiffId, uid: uid, enabled: false)

        XCTAssertEqual(second, .failed(.refused))
    }

    func testSetSharingALotWhoseAnswerWasLostIsCheckedOnTheServer() async {
        store.commitFailures = [firestoreError(FirestoreErrorNumber.deadlineExceeded)]
        store.sharing = false
        let landed = await repo().setSharing(miffId: testMiffId, uid: uid, enabled: false)
        XCTAssertEqual(landed, .done)

        store.commitFailures = [firestoreError(FirestoreErrorNumber.deadlineExceeded)]
        store.sharing = true
        let notLanded = await repo().setSharing(miffId: testMiffId, uid: uid, enabled: false)

        XCTAssertEqual(notLanded, .failed(.offline))
    }
}
