// Scénarios de test longs par nature (un cas par méthode) : la limite de longueur d'un type ne s'applique pas ici.
// swiftlint:disable type_body_length
import XCTest
@testable import Miff

/// Ce que le dépôt des demandes fait avec Firestore (ticket LIEU-REQ-B, miroir d'Android `PlaceRequestRepositoryTest`), face au CONTRAT des
/// règles déployées : trois emplacements par membre, une zone de danger demande Miff+ au demandeur, un lot de deux écritures, un retrait avec
/// repli à 0. Les magasins sont des doubles : les vraies règles sont exercées par la PR des règles.
final class PlaceRequestRepositoryTests: XCTestCase {
    private let uid = testMemberUid
    private let requests = FakePlaceRequestStore()
    private let places = FakePlaceStore()
    private let now = TestBox(1_000_000)
    private let online = TestBox(true)

    private func repository() -> FirestorePlaceRequestRepository {
        let clock = now
        let network = online
        return FirestorePlaceRequestRepository(
            store: requests,
            places: places,
            nowMillis: { clock.value },
            timeoutSeconds: 1,
            online: { network.value }
        )
    }

    private func offline() -> NSError {
        firestoreError(FirestoreErrorNumber.unavailable)
    }

    private func refused() -> NSError {
        firestoreError(FirestoreErrorNumber.permissionDenied)
    }

    /// La Miff existe et son créateur est quelqu'un d'autre que la personne.
    private func setUpMiff(owner: String = testCreatorUid) {
        places.miffs[testMiffId] = testMiffDetails(id: testMiffId, name: "Famille", owner: owner)
    }

    private func premium() {
        places.entitlement = EntitlementRead(active: true, expiresAtMillis: now.value + 10_000)
    }

    private func context(premium: Bool = false, mine: [PlaceRequest] = []) -> RequestContext {
        RequestContext(miffName: "Famille", premium: premium, mine: mine)
    }

    private func submit(
        _ draft: PlaceRequestDraft? = nil,
        context: RequestContext? = nil,
        online: Bool = true
    ) async -> RequestSubmitResult {
        await repository().submit(
            uid: uid,
            miffId: testMiffId,
            draft: draft ?? testRequestDraft(),
            context: context ?? self.context(),
            online: online
        )
    }

    /// Une demande faite sans réseau, avec le dépôt donné (les emplacements en file se retiennent dans le dépôt).
    private func offlineSubmit(_ repo: FirestorePlaceRequestRepository, name: String) async -> RequestSubmitResult {
        await repo.submit(
            uid: uid,
            miffId: testMiffId,
            draft: testRequestDraft(name: name),
            context: context(),
            online: false
        )
    }

    // MARK: - Préparer

    func testPrepareReadsThePlanTheMiffAndTheRequestsOfThePersonFromTheServer() async {
        setUpMiff()
        premium()
        requests.requests = [testRequest(slot: 2)]

        let load = await repository().prepare(uid: uid, miffId: testMiffId)

        guard case .loaded(let loaded) = load else {
            return XCTFail("le formulaire devrait pouvoir s'ouvrir")
        }
        XCTAssertEqual(loaded.miffName, "Famille")
        XCTAssertTrue(loaded.premium)
        XCTAssertEqual(loaded.mine.map { $0.id }, ["\(uid)_2"])
        XCTAssertEqual(requests.reads, ["server"], "l'emplacement se choisit sur le serveur, jamais sur une copie")
        XCTAssertTrue(requests.commits.isEmpty && requests.enqueued.isEmpty, "rien n'est écrit à l'ouverture")
    }

    func testPrepareNeverOpensForTheCreatorOfTheMiffOrAMissingMiff() async {
        setUpMiff(owner: uid)
        let creator = await repository().prepare(uid: uid, miffId: testMiffId)
        places.miffs[testMiffId] = nil
        let missing = await repository().prepare(uid: uid, miffId: testMiffId)

        XCTAssertEqual(creator, .unavailable, "le créateur ajoute lui-même ses lieux")
        XCTAssertEqual(missing, .unavailable)
    }

    func testPrepareWithAnUnknownPlanFailsInsteadOfGuessingFree() async {
        setUpMiff()
        places.entitlementFailure = offline()

        let load = await repository().prepare(uid: uid, miffId: testMiffId)

        XCTAssertEqual(load, .failed(.offline), "un abonnement inconnu n'est pas « gratuit »")
    }

    func testPrepareLostAccessToTheMiffIsUnavailableButANetworkCutIsNot() async {
        places.miffs[testMiffId] = refused()
        let lost = await repository().prepare(uid: uid, miffId: testMiffId)
        places.miffs[testMiffId] = offline()
        let cut = await repository().prepare(uid: uid, miffId: testMiffId)

        XCTAssertEqual(lost, .unavailable)
        XCTAssertEqual(cut, .failed(.offline))
    }

    func testPrepareStillOpensWhenTheRulesRefuseToReadTheRequests() async {
        setUpMiff()
        requests.readFailure = refused()

        let load = await repository().prepare(uid: uid, miffId: testMiffId)

        guard case .loaded(let loaded) = load else {
            return XCTFail("le formulaire s'ouvre quand même : ses emplacements ne servent qu'à envoyer")
        }
        XCTAssertTrue(loaded.mine.isEmpty)
    }

    func testPrepareDeletesAnExpiredRequestSoThatItsSlotIsFreeAgain() async {
        setUpMiff()
        let day = 24 * 60 * 60 * 1000
        requests.requests = [testRequest(slot: 1, createdAtMillis: now.value - 31 * day), testRequest(slot: 2)]

        let load = await repository().prepare(uid: uid, miffId: testMiffId)

        guard case .loaded(let loaded) = load else {
            return XCTFail("le formulaire devrait s'ouvrir")
        }
        XCTAssertEqual(loaded.mine.map { $0.id }, ["\(uid)_2"])
        XCTAssertEqual(requests.commits.count, 1)
        XCTAssertEqual(requests.commits[0][0].path, "miffs/\(testMiffId)/placeRequests/\(uid)_1")
        XCTAssertEqual(requests.commits[0][0].kind, .delete)
    }

    func testPrepareKeepsAnExpiredRequestWhoseDeletionFailed() async {
        setUpMiff()
        let day = 24 * 60 * 60 * 1000
        requests.requests = [testRequest(slot: 1, createdAtMillis: now.value - 31 * day)]
        requests.commitFailures = [offline(), offline()]

        let load = await repository().prepare(uid: uid, miffId: testMiffId)

        guard case .loaded(let loaded) = load else {
            return XCTFail("le formulaire devrait s'ouvrir")
        }
        XCTAssertEqual(loaded.mine.count, 1, "elle occupe encore son emplacement")
    }

    // MARK: - Envoyer

    func testSubmitIsOneLotOfTwoAndTheRequestAppearsOnlyAfterTheServerConfirmed() async {
        let result = await submit()

        guard case .done(let sent) = result else {
            return XCTFail("le serveur a accepté le lot")
        }
        XCTAssertEqual(sent.id, "\(uid)_1")
        XCTAssertEqual(sent.name, "Club de judo")
        XCTAssertNil(sent.createdAtMillis, "toute neuve : la date est celle du serveur")
        XCTAssertEqual(requests.commits.count, 1)
        XCTAssertEqual(requests.commits[0].count, 2)
        XCTAssertEqual(requests.commits[0][0].path, "miffs/\(testMiffId)/placeRequests/\(uid)_1")
        XCTAssertEqual(requests.commits[0][1].data["lastReqId"], .string("\(uid)_1"))
        XCTAssertTrue(requests.enqueued.isEmpty)
    }

    func testSubmitTakesTheFirstFreeSlot() async {
        let result = await submit(context: context(mine: [testRequest(slot: 1), testRequest(slot: 3)]))

        guard case .done(let sent) = result else {
            return XCTFail("un emplacement est libre")
        }
        XCTAssertEqual(sent.id, "\(uid)_2")
    }

    func testTheFourthRequestWritesNothingAtAll() async {
        let full = context(mine: [1, 2, 3].map { testRequest(slot: $0) })

        let result = await submit(context: full)

        XCTAssertEqual(result, .memberLimit, "critère M5 : aucune écriture tentée à la 4e")
        XCTAssertTrue(requests.commits.isEmpty && requests.enqueued.isEmpty)
        XCTAssertTrue(requests.reads.isEmpty)
    }

    func testADangerZoneNeedsPremiumForThePersonWhoAsksAndWritesNothing() async {
        let result = await submit(testRequestDraft(type: .risk), context: context(premium: false))

        XCTAssertEqual(result, .needsPremium(.danger))
        XCTAssertTrue(requests.commits.isEmpty && requests.enqueued.isEmpty)
    }

    func testAPremiumMemberCanAskForADangerZone() async {
        let result = await submit(testRequestDraft(type: .risk), context: context(premium: true))

        guard case .done(let sent) = result else {
            return XCTFail("un demandeur Miff+ peut demander une zone de danger")
        }
        XCTAssertEqual(sent.type, .risk)
        XCTAssertEqual(requests.commits[0][0].data["type"], .string("risk"))
    }

    func testAnInvalidDraftIsRefusedBeforeAnythingIsSent() async {
        let result = await submit(testRequestDraft(name: "A"))

        XCTAssertEqual(result, .failed(.unknown))
        XCTAssertTrue(requests.commits.isEmpty && requests.enqueued.isEmpty)
    }

    func testOfflineTheLotIsHandedToThePhoneQueueAndNothingIsCommitted() async {
        let result = await submit(online: false)

        XCTAssertEqual(result, .queued, "critère M11 : « Ta demande partira dès que tu seras connecté »")
        XCTAssertEqual(requests.enqueued.count, 1)
        XCTAssertEqual(requests.enqueued[0].count, 2)
        XCTAssertTrue(requests.commits.isEmpty)
    }

    func testASecondRequestMadeOfflineDoesNotTakeTheSlotOfTheFirst() async {
        let repo = repository()

        _ = await offlineSubmit(repo, name: "Club de judo")
        _ = await offlineSubmit(repo, name: "Chez Mamie")

        let slots = requests.enqueued.map { $0[0].path }
        let base = "miffs/\(testMiffId)/placeRequests/\(uid)"
        XCTAssertEqual(slots, ["\(base)_1", "\(base)_2"])
    }

    func testTheThreeQueuedSlotsCountTowardTheLimitOfThree() async {
        let repo = repository()
        for index in 0..<3 {
            let result = await offlineSubmit(repo, name: "Lieu \(index)")
            XCTAssertEqual(result, .queued)
        }

        let fourth = await offlineSubmit(repo, name: "Un de trop")

        XCTAssertEqual(fourth, .memberLimit)
        XCTAssertEqual(requests.enqueued.count, 3)
    }

    func testALostAnswerThatLandedIsDoneNotAnError() async {
        requests.commitFailures = [offline()]
        let server = requests
        requests.onCommit = { _ in
            // Le lot est parti et le serveur l'a écrit, mais la réponse s'est perdue.
            server.requests = [testRequest(slot: 1, createdAtMillis: nil)]
        }

        let result = await submit()

        guard case .done(let sent) = result else {
            return XCTFail("la demande est bien là : c'est fait")
        }
        XCTAssertEqual(sent.id, "\(uid)_1")
    }

    func testAnOfflineFailureThatDidNotLandIsTheCalmOfflineMessage() async {
        requests.commitFailures = [offline()]

        let result = await submit()

        XCTAssertEqual(result, .failed(.offline))
    }

    func testARefusalOnAMiffThatIsGoneOrNoLongerTheirsIsMiffUnavailable() async {
        requests.commitFailures = [refused()]
        places.miffs[testMiffId] = nil

        let result = await submit()

        XCTAssertEqual(result, .miffUnavailable)
    }

    func testARefusalWhenTheMiffStillHoldsTenRequestsIsTheMiffLimit() async {
        setUpMiff()
        premium()
        requests.commitFailures = [refused()]

        let result = await submit()

        XCTAssertEqual(result, .miffLimit, "tous les emplacements étaient libres : la Miff porte ses 10 demandes")
        XCTAssertEqual(requests.commits.count, 0)
    }

    func testARefusedDangerZoneAfterThePlanEndedIsTheOfferAgain() async {
        setUpMiff()
        places.entitlement = EntitlementRead(active: true, expiresAtMillis: now.value - 1)
        requests.commitFailures = [refused()]

        let result = await submit(testRequestDraft(type: .risk), context: context(premium: true))

        XCTAssertEqual(result, .needsPremium(.danger))
    }

    func testARaceOnTheSlotIsRetriedOnceWithTheFreeSlot() async {
        setUpMiff()
        requests.commitFailures = [refused()]
        // Un autre téléphone a pris l'emplacement 1 pendant que le formulaire était ouvert.
        requests.requests = [testRequest(slot: 1)]

        let result = await submit()

        guard case .done(let sent) = result else {
            return XCTFail("l'emplacement 2 est libre : le lot repart une fois")
        }
        XCTAssertEqual(sent.id, "\(uid)_2")
        XCTAssertEqual(requests.commits.last?[0].path, "miffs/\(testMiffId)/placeRequests/\(uid)_2")
    }

    func testARaceThatLeavesNoSlotIsTheMemberLimit() async {
        setUpMiff()
        requests.commitFailures = [refused()]
        requests.requests = [1, 2, 3].map { testRequest(slot: $0) }

        let result = await submit()

        XCTAssertEqual(result, .memberLimit)
    }

    func testWhenEvenTheReadingOfTheOwnRequestsIsRefusedTheRequestsAreNotAvailableYet() async {
        setUpMiff()
        requests.commitFailures = [refused()]
        requests.readFailure = refused()

        let result = await submit()

        XCTAssertEqual(result, .requestsUnavailable, "ce n'est pas la limite de la Miff")
    }

    // MARK: - Reprendre

    func testWithdrawingIsOneLotOfTwo() async {
        let request = testRequest(slot: 2)
        requests.requests = [request]

        let result = await repository().withdraw(request)

        XCTAssertEqual(result, .done)
        XCTAssertEqual(requests.commits.count, 1)
        XCTAssertEqual(requests.commits[0][1].data["pending"], .increment(-1))
        XCTAssertTrue(requests.requests.isEmpty)
    }

    func testARefusedWithdrawalIsRetriedOnceWithTheAbsoluteValueZero() async {
        let request = testRequest()
        requests.requests = [request]
        requests.commitFailures = [refused(), nil]

        let result = await repository().withdraw(request)

        XCTAssertEqual(result, .done)
        XCTAssertEqual(requests.commits.count, 1, "seul le repli a été accepté")
        XCTAssertEqual(requests.commits[0][1].data["pending"], .integer(0))
    }

    func testWhenTheFallbackIsRefusedTooItIsAFailureUnlessTheRequestIsGone() async {
        let request = testRequest()
        requests.requests = [request]
        requests.commitFailures = [refused(), refused()]

        let failed = await repository().withdraw(request)

        XCTAssertEqual(failed, .failed(.refused))

        requests.requests = []
        requests.commitFailures = [refused(), refused()]
        let gone = await repository().withdraw(request)

        // Déjà supprimée (par le créateur, sur un autre téléphone) : c'est ce qui était demandé.
        XCTAssertEqual(gone, .done)
    }

    func testAWithdrawalWhoseAnswerWasLostIsDoneWhenTheRequestIsGone() async {
        let request = testRequest()
        requests.requests = [request]
        requests.commitFailures = [offline()]
        let server = requests
        requests.onCommit = { _ in
            server.requests = []
        }

        let result = await repository().withdraw(request)

        XCTAssertEqual(result, .done)
    }

    // MARK: - Lire

    func testReadMineGivesTheNonExpiredRequestsFromTheCopyWhenTheServerIsOutOfReach() async {
        let day = 24 * 60 * 60 * 1000
        requests.requests = [testRequest(slot: 1, createdAtMillis: now.value - 31 * day), testRequest(slot: 2)]

        let read = await repository().readMine(uid: uid, miffId: testMiffId)

        XCTAssertEqual(read, .loaded([testRequest(slot: 2)]))
        XCTAssertEqual(requests.reads, ["default"])
    }

    func testReadMineFailureIsReducedToAFailure() async {
        requests.readFailure = offline()

        let read = await repository().readMine(uid: uid, miffId: testMiffId)

        XCTAssertEqual(read, .failed(.offline))
    }

    // MARK: - Quitter une Miff (critère L1)

    func testLeavingTakesBackEveryPendingRequestOneLotEach() async {
        requests.requests = [testRequest(slot: 1), testRequest(slot: 3), testRequest(slot: 1, miffId: testOtherMiffId)]

        await repository().withdrawAllMine(uid: uid, miffId: testMiffId)

        XCTAssertEqual(requests.commits.count, 2)
        // Les demandes d'une autre Miff ne sont pas touchées.
        XCTAssertEqual(requests.requests.map { $0.miffId }, [testOtherMiffId])
    }

    func testLeavingWithoutANetworkTriesNothing() async {
        requests.requests = [testRequest()]
        online.value = false

        await repository().withdrawAllMine(uid: uid, miffId: testMiffId)

        XCTAssertTrue(requests.reads.isEmpty && requests.commits.isEmpty)
    }

    func testAFailureWhenLeavingIsSilentAndNeverBlocks() async {
        requests.requests = [testRequest()]
        requests.readFailure = offline()

        await repository().withdrawAllMine(uid: uid, miffId: testMiffId)

        XCTAssertTrue(requests.commits.isEmpty)
    }
}
// swiftlint:enable type_body_length
