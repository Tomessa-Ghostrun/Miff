// Scénarios de test longs par nature (un cas par méthode) : les limites de longueur ne s'appliquent pas ici.
// swiftlint:disable file_length type_body_length
import XCTest
@testable import Miff

/// Ce que les lieux font avec Firestore (ticket LOC-3, miroir de `PlaceRepositoryTest`), face au CONTRAT des règles déployées : deux
/// emplacements gratuits p1 / p2, une zone de danger et tout lieu de plus demandent Miff+, un document par Miff avec le même
/// identifiant, 5 Miffs au plus, la personne doit avoir créé la Miff. Le magasin est un double : les vraies règles sont exercées par
/// la PR des règles.
final class PlaceRepositoryTests: XCTestCase {
    private let uid = "uid-1"
    private let store = FakePlaceStore()
    private let now = TestBox(1_000_000)
    private let issued = TestBox(0)

    private func repository() -> FirestorePlaceRepository {
        let clock = now
        let counter = issued
        return FirestorePlaceRepository(
            store: store,
            newId: {
                counter.value += 1
                return "FRESHID" + String(format: "%013ld", counter.value)
            },
            nowMillis: { clock.value },
            timeoutSeconds: 1
        )
    }

    private func offline() -> NSError {
        firestoreError(FirestoreErrorNumber.unavailable)
    }

    private func refused() -> NSError {
        firestoreError(FirestoreErrorNumber.permissionDenied)
    }

    /// J'ai créé `testMiffId` (en premier) et `testOtherMiffId` ; je n'ai fait que rejoindre `testThirdMiffId` (la dernière entrée).
    private func setUpMiffs() {
        store.memberships = [
            PlaceMembership(miffId: testOtherMiffId, joinedAtMillis: 200),
            PlaceMembership(miffId: testMiffId, joinedAtMillis: 100),
            PlaceMembership(miffId: testThirdMiffId, joinedAtMillis: 300)
        ]
        store.miffs[testMiffId] = testMiffDetails(id: testMiffId, name: "Famille", owner: uid)
        store.miffs[testOtherMiffId] = testMiffDetails(id: testOtherMiffId, name: "Les tuches", owner: uid)
        store.miffs[testThirdMiffId] = testMiffDetails(id: testThirdMiffId, name: "Copains", owner: "somebody-else")
    }

    private func premium() {
        store.entitlement = EntitlementRead(active: true, expiresAtMillis: now.value + 10_000)
    }

    private func context() async -> PlaceContext {
        guard case .loaded(let loaded) = await repository().prepare(uid: uid) else {
            XCTFail("la lecture devrait avoir réussi")
            return PlaceContext(premium: false, ownedMiffs: [], lastJoinedMiffId: nil, places: [])
        }
        return loaded
    }

    private func draft(
        miffIds: [String] = [testMiffId],
        type: PlaceType = .trusted,
        name: String = "Maison",
        radiusM: Int = 100
    ) -> PlaceDraft {
        PlaceDraft(
            name: name,
            miffIds: miffIds,
            address: "17 Avenue d'Italie, 75013 Paris",
            lat: 48.82927,
            lng: 2.35679,
            radiusM: radiusM,
            type: type,
            category: .home
        )
    }

    // MARK: - Lire : qui est la personne

    func testPrepareKeepsOnlyTheMiffsThePersonCreatedFirstCreatedFirstAndKnowsTheLastJoined() async {
        setUpMiffs()

        let loaded = await context()

        XCTAssertEqual(loaded.ownedMiffs.map { $0.id }, [testMiffId, testOtherMiffId])
        XCTAssertEqual(loaded.lastJoinedMiffId, testThirdMiffId)
        XCTAssertFalse(loaded.premium)
    }

    func testPrepareReadsEverythingFromTheServerNeverFromAGuess() async {
        setUpMiffs()

        _ = await context()

        XCTAssertEqual(store.calls.first, "entitlement")
        XCTAssertTrue(store.calls.contains("memberships"))
        XCTAssertFalse(store.calls.contains("commit"), "rien n'est écrit pour ouvrir le formulaire")
    }

    func testPrepareMiffPlusIsAnActiveEntitlementThatHasNotExpired() async {
        setUpMiffs()
        store.entitlement = EntitlementRead(active: true, expiresAtMillis: now.value - 1)
        let expired = await context().premium
        store.entitlement = EntitlementRead(active: false, expiresAtMillis: now.value + 10)
        let inactive = await context().premium
        premium()
        let active = await context().premium

        XCTAssertFalse(expired)
        XCTAssertFalse(inactive)
        XCTAssertTrue(active)
    }

    func testPrepareReadsThePlacesOfMyMiffsNotOfTheOthers() async {
        setUpMiffs()
        store.places[testMiffId] = [testPlace(id: "p1", name: "Maison", miffId: testMiffId)]
        store.places[testThirdMiffId] = [testPlace(id: "p9", name: "Pas à moi", miffId: testThirdMiffId)]

        let loaded = await context()

        XCTAssertEqual(loaded.places.map { $0.id }, ["p1"])
        XCTAssertEqual(loaded.distinctPlaces, 1)
    }

    func testPrepareOfflineIsAFailureNeverAGuess() async {
        setUpMiffs()
        store.membershipsFailure = offline()

        let result = await repository().prepare(uid: uid)

        XCTAssertEqual(result, .failed(.offline))
    }

    func testPrepareAMiffThatCannotBeReadAnyMoreIsLeftOut() async {
        setUpMiffs()
        store.miffs[testOtherMiffId] = refused()

        let loaded = await context()

        XCTAssertEqual(loaded.ownedMiffs.map { $0.id }, [testMiffId])
    }

    func testPrepareAnotherFailureWhileReadingAMiffStopsTheReading() async {
        setUpMiffs()
        store.miffs[testOtherMiffId] = offline()

        let result = await repository().prepare(uid: uid)

        XCTAssertEqual(result, .failed(.offline))
    }

    func testPrepareTheSubscriptionIsUnknownWhenItCannotBeReadNeverFree() async {
        setUpMiffs()
        store.entitlementFailure = offline()

        // Une lecture qui échoue veut dire « réessaie », jamais « gratuit » : l'offre ne s'ouvre pas parce que le réseau est coupé.
        let first = await repository().prepare(uid: uid)
        store.entitlementFailure = refused()
        let second = await repository().prepare(uid: uid)

        XCTAssertEqual(first, .failed(.offline))
        XCTAssertEqual(second, .failed(.refused))
    }

    func testPrepareNoEntitlementDocumentIsFreeNotUnknown() async {
        setUpMiffs()
        store.entitlement = nil

        let premium = await context().premium

        XCTAssertFalse(premium)
    }

    // MARK: - Créer : la formule gratuite (LIM1, LIM3)

    func testCreateFreeFirstPlaceTakesP1SecondTakesP2OneDocumentEach() async {
        setUpMiffs()
        let first = await repository().create(uid: uid, draft: draft(), context: await context())

        XCTAssertEqual(first, .done(placeId: "p1"))
        XCTAssertEqual(store.commits.first?.map { $0.path }, ["miffs/\(testMiffId)/places/p1"])
        store.places[testMiffId] = [testPlace(id: "p1", name: "Maison", miffId: testMiffId)]
        let second = await repository().create(uid: uid, draft: draft(name: "Travail"), context: await context())
        XCTAssertEqual(second, .done(placeId: "p2"))
    }

    func testCreateFreeThirdPlaceIsRefusedBeforeAnythingIsSent() async {
        setUpMiffs()
        store.places[testMiffId] = [
            testPlace(id: "p1", name: "A", miffId: testMiffId),
            testPlace(id: "p2", name: "B", miffId: testMiffId)
        ]

        let result = await repository().create(uid: uid, draft: draft(), context: await context())

        XCTAssertEqual(result, .needsPremium(.limit))
        XCTAssertTrue(store.commits.isEmpty)
    }

    func testCreateFreeDangerZoneIsRefusedBeforeAnythingIsSent() async {
        setUpMiffs()

        let result = await repository().create(uid: uid, draft: draft(type: .risk), context: await context())

        XCTAssertEqual(result, .needsPremium(.danger))
        XCTAssertTrue(store.commits.isEmpty)
    }

    // MARK: - Créer : Miff+ et plusieurs Miffs (MIF2)

    func testCreatePremiumOneLotTheSameFreshIdInEveryMiff() async throws {
        setUpMiffs()
        premium()

        let result = await repository().create(
            uid: uid,
            draft: draft(miffIds: [testMiffId, testOtherMiffId], type: .risk),
            context: await context()
        )

        guard case .done(let id) = result else {
            return XCTFail("l'écriture devrait avoir réussi")
        }
        XCTAssertEqual(id.count, 20)
        XCTAssertTrue(Place.isPlaceId(id))
        XCTAssertEqual(store.commits.count, 1)
        let lot = try XCTUnwrap(store.commits.first)
        XCTAssertEqual(
            lot.map { $0.path },
            ["miffs/\(testMiffId)/places/\(id)", "miffs/\(testOtherMiffId)/places/\(id)"]
        )
        XCTAssertTrue(lot.allSatisfy { $0.kind == .set && $0.data["type"] == .string("risk") })
    }

    func testCreateAMiffThePersonDidNotCreateIsRefusedAndNothingIsSent() async {
        setUpMiffs()
        premium()

        let result = await repository().create(
            uid: uid,
            draft: draft(miffIds: [testMiffId, testThirdMiffId]),
            context: await context()
        )

        XCTAssertEqual(result, .failed(.unknown))
        XCTAssertTrue(store.commits.isEmpty)
    }

    func testCreateMoreThanFiveMiffsIsRefused() async {
        setUpMiffs()
        premium()
        let six = (1...6).map { "m\($0)" }

        let result = await repository().create(uid: uid, draft: draft(miffIds: six), context: await context())

        XCTAssertEqual(result, .failed(.unknown))
        XCTAssertTrue(store.commits.isEmpty)
    }

    func testCreateADraftTheRulesWouldRefuseNeverLeaves() async {
        setUpMiffs()

        let result = await repository().create(uid: uid, draft: draft(name: "M"), context: await context())

        XCTAssertEqual(result, .failed(.unknown))
        XCTAssertTrue(store.commits.isEmpty)
    }

    // MARK: - Créer : quand ça se passe mal (PL9, cas limite 14)

    func testCreateOfflineSaysSoAndNothingIsQueued() async {
        setUpMiffs()
        store.commitFailures = [offline()]

        let result = await repository().create(uid: uid, draft: draft(), context: await context())

        XCTAssertEqual(result, .failed(.offline))
        XCTAssertTrue(store.commits.isEmpty)
    }

    func testCreateAnswerLostButTheLotLandedIsNotShownAsAFailure() async {
        setUpMiffs()
        store.commitFailures = [offline()]
        let landed = testPlace(id: "p1", name: "Maison", miffId: testMiffId, address: "17 Avenue d'Italie, 75013 Paris")
        // Le lot est parti et le serveur l'a écrit ; seule la réponse s'est perdue.
        store.onCommit = { [store] writes in
            store.places[testMiffId] = [
                Place(
                    id: landed.id,
                    miffId: testMiffId,
                    name: "Maison",
                    type: .trusted,
                    category: .home,
                    address: landed.address,
                    lat: 48.82927,
                    lng: 2.35679,
                    radiusM: 100,
                    createdBy: "uid-1"
                )
            ]
            XCTAssertEqual(writes.count, 1)
        }

        let result = await repository().create(uid: uid, draft: draft(), context: await context())

        XCTAssertEqual(result, .done(placeId: "p1"))
    }

    func testCreateRefusedBecauseAMiffIsGoneSaysThatMiffNotAGenericError() async {
        setUpMiffs()
        premium()
        let loaded = await context()
        store.miffs[testOtherMiffId] = nil // supprimée entre l'ouverture du formulaire et le toucher
        store.commitFailures = [refused()]

        let result = await repository().create(
            uid: uid,
            draft: draft(miffIds: [testMiffId, testOtherMiffId]),
            context: loaded
        )

        XCTAssertEqual(result, .miffUnavailable)
        XCTAssertTrue(store.commits.isEmpty)
    }

    func testCreateRefusedForAnotherReasonIsTheGenericSentence() async {
        setUpMiffs()
        store.commitFailures = [refused()]

        let result = await repository().create(uid: uid, draft: draft(), context: await context())

        XCTAssertEqual(result, .failed(.refused))
    }

    // MARK: - Modifier (PL7, MIF2, LIM3)

    private func group(_ id: String, _ miffs: [String], type: PlaceType = .trusted) -> PlaceGroup {
        PlaceGroup(id: id, copies: miffs.map { testPlace(id: id, name: "Maison", miffId: $0, type: type) })
    }

    func testUpdateChangesTheFieldsInEveryCopyInOneLot() async throws {
        setUpMiffs()
        premium()
        store.seed(testPlaceId, miffs: [testMiffId, testOtherMiffId])

        let result = await repository().update(
            uid: uid,
            original: group(testPlaceId, [testMiffId, testOtherMiffId]),
            draft: draft(miffIds: [testMiffId, testOtherMiffId], name: "Chez Papa", radiusM: 300),
            context: await context()
        )

        XCTAssertEqual(result, .done(placeId: testPlaceId))
        let lot = try XCTUnwrap(store.commits.first)
        XCTAssertEqual(store.commits.count, 1)
        XCTAssertTrue(lot.allSatisfy { $0.kind == .update })
        XCTAssertEqual(lot.map { $0.data["name"] }, [.string("Chez Papa"), .string("Chez Papa")])
        XCTAssertEqual(lot.map { $0.data["radiusM"] }, [.integer(300), .integer(300)])
    }

    func testUpdateTheMiffsCanChangeAddOneRemoveOne() async throws {
        setUpMiffs()
        premium()
        store.seed(testPlaceId, miffs: [testMiffId])

        let result = await repository().update(
            uid: uid,
            original: group(testPlaceId, [testMiffId]),
            draft: draft(miffIds: [testOtherMiffId]),
            context: await context()
        )

        XCTAssertEqual(result, .done(placeId: testPlaceId))
        let lot = try XCTUnwrap(store.commits.first)
        let added = lot.first { $0.path == "miffs/\(testOtherMiffId)/places/\(testPlaceId)" }
        let removed = lot.first { $0.path == "miffs/\(testMiffId)/places/\(testPlaceId)" }
        XCTAssertEqual(added?.kind, .set)
        XCTAssertEqual(removed?.kind, .delete)
    }

    func testUpdateFreeToADangerZoneIsRefusedBeforeAnythingIsSent() async {
        setUpMiffs()
        store.seed("p1", miffs: [testMiffId])

        let result = await repository().update(
            uid: uid,
            original: group("p1", [testMiffId]),
            draft: draft(type: .risk),
            context: await context()
        )

        XCTAssertEqual(result, .needsPremium(.danger))
        XCTAssertTrue(store.commits.isEmpty)
    }

    func testUpdateFreeBackFromADangerZoneIsAlwaysAllowed() async {
        setUpMiffs()
        store.seed("p1", miffs: [testMiffId], type: .risk)

        let result = await repository().update(
            uid: uid,
            original: group("p1", [testMiffId], type: .risk),
            draft: draft(type: .trusted),
            context: await context()
        )

        XCTAssertEqual(result, .done(placeId: "p1"))
    }

    func testUpdateFreeAMiffAddedToAPlaceOfAFixedSlotStaysFree() async {
        setUpMiffs()
        store.seed("p1", miffs: [testMiffId])

        let result = await repository().update(
            uid: uid,
            original: group("p1", [testMiffId]),
            draft: draft(miffIds: [testMiffId, testOtherMiffId]),
            context: await context()
        )

        XCTAssertEqual(result, .done(placeId: "p1"))
    }

    func testUpdateFreeAMiffAddedWhereTheSlotIsTakenByAnotherPlaceIsTheLimit() async {
        setUpMiffs()
        store.seed("p1", miffs: [testMiffId])
        store.places[testOtherMiffId] = [testPlace(id: "p1", name: "Le stade", miffId: testOtherMiffId)]

        let result = await repository().update(
            uid: uid,
            original: group("p1", [testMiffId]),
            draft: draft(miffIds: [testMiffId, testOtherMiffId]),
            context: await context()
        )

        XCTAssertEqual(result, .needsPremium(.limit))
        XCTAssertTrue(store.commits.isEmpty)
    }

    func testUpdateAPlaceDeletedElsewhereIsNoticed() async {
        setUpMiffs()
        premium()
        store.seed(testPlaceId, miffs: [testMiffId])
        let loaded = await context()
        store.places[testMiffId] = [] // supprimé depuis un autre téléphone
        store.commitFailures = [firestoreError(5)]

        let result = await repository().update(
            uid: uid,
            original: group(testPlaceId, [testMiffId]),
            draft: draft(),
            context: loaded
        )

        XCTAssertEqual(result, .placeGone)
    }

    func testUpdateOfflineIsAFailureAndTheOldPlaceStays() async {
        setUpMiffs()
        premium()
        store.seed(testPlaceId, miffs: [testMiffId])
        store.commitFailures = [offline()]

        let result = await repository().update(
            uid: uid,
            original: group(testPlaceId, [testMiffId]),
            draft: draft(name: "Autre nom"),
            context: await context()
        )

        XCTAssertEqual(result, .failed(.offline))
        XCTAssertTrue(store.commits.isEmpty)
    }

    func testUpdateSecondAttemptReadsTheCopiesAgainAndUpdatesTheMiffAlreadyAddedNeverCreatesItTwice() async throws {
        setUpMiffs()
        premium()
        store.seed(testPlaceId, miffs: [testMiffId])
        let staleForm = group(testPlaceId, [testMiffId]) // ce avec quoi le formulaire s'est ouvert : UNE Miff
        let wanted = draft(miffIds: [testMiffId, testOtherMiffId], name: "Chez Papa")
        let loaded = await context()
        // Un premier essai (réponse perdue) a déjà écrit la copie de la Miff ajoutée, avec les champs voulus.
        store.places[testOtherMiffId] = [
            Place(
                id: testPlaceId,
                miffId: testOtherMiffId,
                name: "Chez Papa",
                type: .trusted,
                category: .home,
                address: "17 Avenue d'Italie, 75013 Paris",
                lat: 48.82927,
                lng: 2.35679,
                radiusM: 100,
                createdBy: uid
            )
        ]

        let result = await repository().update(uid: uid, original: staleForm, draft: wanted, context: loaded)

        XCTAssertEqual(result, .done(placeId: testPlaceId))
        let writes = try XCTUnwrap(store.commits.first)
        // Les DEUX copies sont mises à jour : écrire de nouveau la Miff ajoutée comme une création serait refusé par les règles
        // (createdAt change).
        XCTAssertEqual(writes.map { $0.kind }, [.update, .update])
    }

    func testUpdateAPlaceThatIsGoneOnTheServerIsNoticedBeforeAnythingIsWritten() async {
        setUpMiffs()
        premium()
        store.seed(testPlaceId, miffs: [testMiffId])
        let loaded = await context()
        store.places[testMiffId] = []

        let result = await repository().update(
            uid: uid,
            original: group(testPlaceId, [testMiffId]),
            draft: draft(),
            context: loaded
        )

        XCTAssertEqual(result, .placeGone)
        XCTAssertTrue(store.commits.isEmpty)
    }

    // MARK: - Supprimer (PL8)

    func testDeleteIsOneLotInEveryMiffWithNoScreen() async throws {
        let result = await repository().delete(placeId: testPlaceId, miffIds: [testMiffId, testOtherMiffId])

        XCTAssertEqual(result, .done(placeId: testPlaceId))
        let lot = try XCTUnwrap(store.commits.first)
        XCTAssertEqual(store.commits.count, 1)
        XCTAssertEqual(lot.count, 2)
        XCTAssertTrue(lot.allSatisfy { $0.kind == .delete })
    }

    func testDeleteOfflineIsAFailure() async {
        store.places[testMiffId] = [testPlace(id: "p1", name: "Maison", miffId: testMiffId)]
        store.commitFailures = [offline()]

        let result = await repository().delete(placeId: "p1", miffIds: [testMiffId])

        XCTAssertEqual(result, .failed(.offline))
    }

    func testDeleteAPlaceAlreadyGoneIsDone() async {
        store.places[testMiffId] = []
        store.commitFailures = [refused()]

        let result = await repository().delete(placeId: "p1", miffIds: [testMiffId])

        XCTAssertEqual(result, .done(placeId: "p1"))
    }

    func testDeleteMoreThanFiveMiffsIsRefused() async {
        let six = (1...6).map { String(format: "MIFF%016ld", $0) }

        let result = await repository().delete(placeId: "p1", miffIds: six)

        XCTAssertEqual(result, .failed(.unknown))
        XCTAssertTrue(store.commits.isEmpty)
    }

    func testDeleteNeverBuildsAPathFromAnInvalidIdentifier() async {
        let badPlace = await repository().delete(placeId: "p1/../x", miffIds: [testMiffId])
        let badMiff = await repository().delete(placeId: "p1", miffIds: ["not-a-miff"])

        XCTAssertEqual(badPlace, .failed(.unknown))
        XCTAssertEqual(badMiff, .failed(.unknown))
        XCTAssertTrue(store.commits.isEmpty)
    }

    // MARK: - Revue d'Android (PR #56) : les scénarios

    func testScenarioAHasP1InMiffACreatingInMiffBTakesP2AndTheNextOneIsTheLimit() async {
        // Le scénario de la revue (I4) : deux Miffs créées par un compte gratuit dont Miff+ est fini.
        setUpMiffs()
        store.seed("p1", miffs: [testMiffId])
        let first = await repository().create(
            uid: uid,
            draft: draft(miffIds: [testOtherMiffId], name: "Chez Papa"),
            context: await context()
        )

        // p1 est libre dans la Miff B mais pris dans la Miff A par un AUTRE lieu : il n'est pas réutilisé.
        XCTAssertEqual(first, .done(placeId: "p2"))
        store.places[testOtherMiffId, default: []].append(
            testPlace(id: "p2", name: "Chez Papa", miffId: testOtherMiffId)
        )
        let loaded = await context()
        XCTAssertEqual(loaded.distinctPlaces, 2)
        let third = await repository().create(uid: uid, draft: draft(name: "Troisième"), context: loaded)
        XCTAssertEqual(third, .needsPremium(.limit))
    }

    func testScenarioTwoDifferentPlacesSharingAnIdentifierAreTwoPlacesForTheLimit() async {
        setUpMiffs()
        store.seed("p1", miffs: [testMiffId])
        store.places[testOtherMiffId] = [testPlace(id: "p1", name: "Le stade", miffId: testOtherMiffId)]
        let loaded = await context()

        let result = await repository().create(uid: uid, draft: draft(), context: loaded)

        XCTAssertEqual(loaded.groups.count, 2)
        XCTAssertEqual(loaded.distinctPlaces, 2)
        XCTAssertEqual(result, .needsPremium(.limit))
        XCTAssertTrue(store.commits.isEmpty)
    }
}
