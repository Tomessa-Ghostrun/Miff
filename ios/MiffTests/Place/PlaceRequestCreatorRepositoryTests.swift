import XCTest
@testable import Miff

/// Ce que le dépôt des demandes fait pour le CRÉATEUR (ticket LIEU-REQ-C, miroir d'Android `PlaceRequestCreatorRepositoryTest`), face au
/// CONTRAT des règles : accepter est un lot de 3 atomique, refuser un lot de 2, le compteur est toujours une valeur ABSOLUE, les limites de la
/// formule gratuite sont appliquées avant d'écrire, et rien n'est écrit pour le demandeur.
@MainActor
final class PlaceRequestCreatorRepositoryTests: XCTestCase {
    private let creator = testCreatorUid
    private let requests = FakePlaceRequestStore()
    private let places = FakePlaceStore()
    private let placeRepository = FakePlaceRepository()
    private let now = TestBox(1_000_000)
    private let added = TestBox(0)

    private func repository() -> FirestorePlaceRequestRepository {
        let clock = now
        let counter = added
        return FirestorePlaceRequestRepository(
            store: requests,
            places: places,
            placeRepository: placeRepository,
            newPlaceId: { "NEWPLACE00000000001" },
            nowMillis: { clock.value },
            timeoutSeconds: 1,
            onPlaceAdded: { counter.value += 1 }
        )
    }

    private func owned() -> OwnedMiff {
        OwnedMiff(id: testMiffId, name: "Famille", joinedAtMillis: 100)
    }

    private func context(premium: Bool = false, places: [Place] = []) -> PlaceContext {
        PlaceContext(premium: premium, ownedMiffs: [owned()], lastJoinedMiffId: testMiffId, places: places)
    }

    private func threePlaces() -> [Place] {
        ["p1", "p2", "p3"].map { testPlace(id: $0, name: "Lieu \($0)") }
    }

    private func pending(_ request: PlaceRequest = testRequest()) {
        requests.requests = [request]
    }

    // MARK: - Accepter

    func testAcceptingIsOneAtomicLotOfThreeThatCopiesTheRequestAndSetsTheAbsoluteCounter() async {
        placeRepository.context = context(premium: false)
        let request = testRequest(name: "Chez Mamie", category: .relative)
        pending(request)

        let result = await repository().accept(uid: creator, request: request, knownCount: 3)

        XCTAssertEqual(result, .done(placeId: "p1"), "l'emplacement libre de la formule gratuite")
        XCTAssertEqual(requests.commits.count, 1)
        let lot = requests.commits[0]
        XCTAssertEqual(lot.count, 3)
        XCTAssertEqual(lot[0].path, "miffs/\(testMiffId)/places/p1")
        XCTAssertEqual(lot[0].kind, .set)
        XCTAssertEqual(lot[0].data["name"], .string("Chez Mamie"))
        XCTAssertEqual(lot[0].data["category"], .string("relative"), "la catégorie de la demande est copiée")
        XCTAssertEqual(lot[0].data["type"], .string("trusted"))
        XCTAssertEqual(lot[0].data["createdBy"], .string(creator))
        XCTAssertEqual(lot[0].data["priority"], .null)
        XCTAssertEqual(lot[0].data["createdAt"], .serverTimestamp)
        XCTAssertEqual(lot[1].path, "miffs/\(testMiffId)/placeRequests/\(request.id)")
        XCTAssertEqual(lot[1].kind, .delete)
        XCTAssertEqual(lot[2].path, "miffs/\(testMiffId)/meta/placeRequests")
        XCTAssertEqual(lot[2].data["pending"], .integer(2), "la valeur absolue du nombre restant, jamais increment(-1)")
        XCTAssertEqual(lot[2].data["lastReqId"], .null)
        XCTAssertEqual(added.value, 1, "le suivi des lieux est prévenu")
    }

    func testAMiffPlusCreatorGetsAFreshIdentifierAndADangerZoneIsAccepted() async {
        placeRepository.context = context(premium: true, places: threePlaces())
        let request = testRequest(type: .risk)
        pending(request)

        let result = await repository().accept(uid: creator, request: request, knownCount: 1)

        XCTAssertEqual(result, .done(placeId: "NEWPLACE00000000001"))
        XCTAssertEqual(requests.commits[0][0].data["type"], .string("risk"))
        XCTAssertEqual(requests.commits[0][2].data["pending"], .integer(0))
    }

    func testAFreeCreatorWithThreePlacesGetsTheOfferAndNothingIsWritten() async {
        placeRepository.context = context(premium: false, places: threePlaces())
        let request = testRequest()
        pending(request)

        let result = await repository().accept(uid: creator, request: request, knownCount: 1)

        XCTAssertEqual(result, .needsPremium(.limit), "critère C5")
        XCTAssertTrue(requests.commits.isEmpty)
        XCTAssertEqual(added.value, 0)
    }

    func testADangerZoneWithoutMiffPlusGetsTheOfferEvenWithFreeSlots() async {
        placeRepository.context = context(premium: false)
        let request = testRequest(type: .risk)
        pending(request)

        let result = await repository().accept(uid: creator, request: request, knownCount: 1)

        XCTAssertEqual(result, .needsPremium(.danger), "critère C7")
        XCTAssertTrue(requests.commits.isEmpty)
    }

    func testAnUnreadablePlanIsNeverFreeNoOfferOnADoubt() async {
        placeRepository.prepareFailure = .offline
        let request = testRequest()
        pending(request)

        let result = await repository().accept(uid: creator, request: request, knownCount: 1)

        XCTAssertEqual(result, .subscriptionUnknown, "critère C8")
        XCTAssertTrue(requests.commits.isEmpty)
    }

    func testAcceptingWhenTheMiffIsNoLongerTheirsOrTheRequestIsGoneWritesNothing() async {
        placeRepository.context = PlaceContext(premium: true, ownedMiffs: [], lastJoinedMiffId: nil, places: [])
        pending()
        let notTheirs = await repository().accept(uid: creator, request: testRequest(), knownCount: 1)

        placeRepository.context = context(premium: true)
        requests.requests = []
        let gone = await repository().accept(uid: creator, request: testRequest(), knownCount: 1)

        XCTAssertEqual(notTheirs, .gone)
        XCTAssertEqual(gone, .gone, "traitée sur un autre téléphone, ou reprise par son auteur")
        XCTAssertTrue(requests.commits.isEmpty)
    }

    func testAFailedReadingOfTheRequestIsAFailureAndALostAccessIsGone() async {
        placeRepository.context = context(premium: true)
        pending()
        requests.readFailure = firestoreError(FirestoreErrorNumber.unavailable)
        let offline = await repository().accept(uid: creator, request: testRequest(), knownCount: 1)
        requests.readFailure = firestoreError(FirestoreErrorNumber.permissionDenied)
        let lost = await repository().accept(uid: creator, request: testRequest(), knownCount: 1)

        XCTAssertEqual(offline, .failed(.offline))
        XCTAssertEqual(lost, .gone)
    }

    func testALostAnswerThatLandedIsDoneBecauseTheRequestIsGone() async {
        placeRepository.context = context(premium: true)
        pending()
        requests.commitFailures = [firestoreError(FirestoreErrorNumber.unavailable)]
        let server = requests
        let serverPlaces = places
        requests.onCommit = { _ in
            // Le lot atomique est passé : la demande n'existe plus, le lieu oui.
            server.requests = []
            serverPlaces.places[testMiffId] = [testPlace(id: "NEWPLACE00000000001", name: "Club de judo")]
        }

        let result = await repository().accept(uid: creator, request: testRequest(), knownCount: 1)

        XCTAssertEqual(result, .done(placeId: "NEWPLACE00000000001"))
        XCTAssertEqual(added.value, 1)
    }

    func testARequestGoneWithoutItsPlaceIsGoneNotAddedAndAnUnreadablePlaceIsAFailure() async {
        placeRepository.context = context(premium: true)
        pending()
        requests.commitFailures = [firestoreError(FirestoreErrorNumber.unavailable)]
        let server = requests
        requests.onCommit = { _ in
            // Reprise par son auteur pendant que le lot était en route : la demande n'existe plus, le lieu n'a jamais été créé.
            server.requests = []
        }
        let gone = await repository().accept(uid: creator, request: testRequest(), knownCount: 1)

        pending()
        requests.commitFailures = [firestoreError(FirestoreErrorNumber.unavailable)]
        places.placesFailure = firestoreError(FirestoreErrorNumber.unavailable)
        let unknown = await repository().accept(uid: creator, request: testRequest(), knownCount: 1)

        XCTAssertEqual(gone, .gone, "jamais « ajouté » sans avoir vu le lieu")
        XCTAssertEqual(unknown, .failed(.offline))
        XCTAssertEqual(added.value, 0)
    }

    func testARefusalByTheRuleOfThePlaceBecomesTheOfferAndTheRequestStaysPending() async {
        // Le serveur dit encore Miff+ au départ ; au refus, la formule relue est gratuite et pleine : l'offre, jamais une erreur brute.
        let request = testRequest()
        pending(request)
        requests.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied)]
        let fresh = context(premium: false, places: threePlaces())
        let switching = PlaceRepositorySwitch(first: context(premium: true), then: fresh)

        let result = await FirestorePlaceRequestRepository(
            store: requests,
            places: places,
            placeRepository: switching,
            newPlaceId: { "NEWPLACE00000000001" },
            nowMillis: { 1_000_000 },
            timeoutSeconds: 1
        ).accept(uid: creator, request: request, knownCount: 1)

        XCTAssertEqual(result, .needsPremium(.limit))
        XCTAssertEqual(requests.requests.count, 1, "la demande reste en attente")
    }

    func testAnOfflineFailureThatDidNotLandIsTheCalmOfflineMessage() async {
        placeRepository.context = context(premium: true)
        pending()
        requests.commitFailures = [firestoreError(FirestoreErrorNumber.unavailable)]

        let result = await repository().accept(uid: creator, request: testRequest(), knownCount: 1)

        XCTAssertEqual(result, .failed(.offline))
        XCTAssertEqual(added.value, 0)
    }

    func testARequestWithoutACategoryBecomesAPlaceOfTheNeutralCategory() async {
        placeRepository.context = context(premium: true)
        let request = testRequest(category: nil)
        pending(request)

        _ = await repository().accept(uid: creator, request: request, knownCount: 1)

        XCTAssertEqual(requests.commits[0][0].data["category"], .string("other"))
    }

    // MARK: - Refuser

    func testDecliningIsALotOfTwoWithTheAbsoluteCounterAndNothingForTheRequester() async {
        let request = testRequest()
        pending(request)

        let result = await repository().decline(request, knownCount: 4)

        XCTAssertEqual(result, .done)
        XCTAssertEqual(requests.commits.count, 1)
        let lot = requests.commits[0]
        XCTAssertEqual(lot.count, 2)
        XCTAssertEqual(lot[0].path, "miffs/\(testMiffId)/placeRequests/\(request.id)")
        XCTAssertEqual(lot[0].kind, .delete)
        XCTAssertEqual(lot[1].data["pending"], .integer(3))
        XCTAssertEqual(lot[1].data["lastReqId"], .null)
        XCTAssertTrue(
            lot.allSatisfy { !$0.path.hasPrefix("users/") && !$0.path.contains("/notifications") },
            "critère C4 : rien n'est écrit pour la personne qui a demandé"
        )
    }

    func testDecliningARequestAlreadyGoneIsDoneAndAFailureKeepsItsReason() async {
        let request = testRequest()
        requests.commitFailures = [firestoreError(FirestoreErrorNumber.unavailable)]
        let gone = await repository().decline(request, knownCount: 1)

        pending(request)
        requests.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied)]
        let failed = await repository().decline(request, knownCount: 1)

        XCTAssertEqual(gone, .done)
        XCTAssertEqual(failed, .failed(.refused))
    }

    // MARK: - Nettoyer

    func testPurgingDeletesFourAtATimeWithTheAbsoluteRemainingCounter() async {
        let ids = ["aaa_1", "bbb_1", "ccc_1", "ddd_1", "eee_1", "fff_1", "ggg_1"]

        await repository().purge(miffId: testMiffId, requestIds: ids, remaining: 2)

        XCTAssertEqual(requests.commits.count, 2, "7 demandes : 4 puis 3")
        XCTAssertEqual(requests.commits[0].count, 5)
        XCTAssertEqual(requests.commits[1].count, 4)
        for lot in requests.commits {
            XCTAssertEqual(lot.last?.path, "miffs/\(testMiffId)/meta/placeRequests")
            XCTAssertEqual(lot.last?.data["pending"], .integer(2))
        }
    }

    func testAFailedPurgeStopsQuietly() async {
        requests.commitFailures = [firestoreError(FirestoreErrorNumber.unavailable)]

        await repository().purge(miffId: testMiffId, requestIds: ["a_1", "b_1", "c_1", "d_1", "e_1"], remaining: 0)

        XCTAssertTrue(requests.commits.isEmpty, "le premier lot a échoué, le second n'est pas tenté")
    }

    // MARK: - Lire et écouter

    func testReadingAllGivesEveryRequestExpiredOnesIncluded() async {
        let day = 24 * 60 * 60 * 1000
        requests.requests = [testRequest(slot: 1, createdAtMillis: now.value - 40 * day), testRequest(slot: 2)]

        let read = await repository().readAll(miffId: testMiffId)

        XCTAssertEqual(read, .loaded(requests.requests), "le créateur les voit toutes pour supprimer les expirées")
    }

    func testTheRequesterIsFoundNotAMemberOrUnknown() async {
        requests.requesters[testMemberUid] = RequesterCard.of(
            uid: testMemberUid, firstName: "Léa", lastName: "Martin", avatarJpeg: nil
        )

        let found = await repository().readRequester(miffId: testMiffId, uid: testMemberUid)
        let left = await repository().readRequester(miffId: testMiffId, uid: "uidgone1")
        requests.requesterFailure = firestoreError(FirestoreErrorNumber.unavailable)
        let unknown = await repository().readRequester(miffId: testMiffId, uid: testMemberUid)

        guard case .known(let card) = found else {
            return XCTFail("la fiche devrait être lue")
        }
        XCTAssertEqual(card.firstName, "Léa")
        XCTAssertEqual(left, .notMember)
        XCTAssertEqual(unknown, .unknown, "un doute n'est pas « n'est plus membre »")
    }

    func testTheListenerGivesDataAndEndsWithAccessLostOnlyOnARefusal() async {
        let stream = repository().observe(miffId: testMiffId)
        let seen = TestBox<[RequestsObservation]>([])
        let reading = Task {
            for await observation in stream {
                seen.value.append(observation)
            }
        }
        await waitUntil { !self.requests.observers.isEmpty }

        requests.emit([testRequest()], fromCache: true)
        await waitUntil { seen.value.count == 1 }
        requests.failObservation(firestoreError(FirestoreErrorNumber.permissionDenied))
        await reading.value

        XCTAssertEqual(seen.value, [.data(requests: [testRequest()], fromCache: true), .accessLost])
    }

    func testANetworkCutNeverMeansAccessLost() async {
        let stream = repository().observe(miffId: testMiffId)
        let seen = TestBox<[RequestsObservation]>([])
        let reading = Task {
            for await observation in stream {
                seen.value.append(observation)
            }
        }
        await waitUntil { !self.requests.observers.isEmpty }

        requests.failObservation(firestoreError(FirestoreErrorNumber.unavailable))
        await reading.value

        XCTAssertEqual(seen.value, [.failed])
    }
}

/// Un dépôt des lieux qui donne d'abord un contexte, puis un autre (la formule a changé entre l'ouverture et le refus du serveur).
private final class PlaceRepositorySwitch: PlaceRepository {
    private var contexts: [PlaceContext]

    init(first: PlaceContext, then second: PlaceContext) {
        contexts = [first, second]
    }

    func prepare(uid: String) async -> PlaceContextLoad {
        let next = contexts.count > 1 ? contexts.removeFirst() : contexts[0]
        return .loaded(next)
    }

    func create(uid: String, draft: PlaceDraft, context: PlaceContext) async -> PlaceWriteResult {
        .failed(.unknown)
    }

    func update(uid: String, original: PlaceGroup, draft: PlaceDraft, context: PlaceContext) async -> PlaceWriteResult {
        .failed(.unknown)
    }

    func delete(placeId: String, miffIds: [String]) async -> PlaceWriteResult {
        .failed(.unknown)
    }
}
