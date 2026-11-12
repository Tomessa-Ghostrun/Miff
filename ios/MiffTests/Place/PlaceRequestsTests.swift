import XCTest
@testable import Miff

/// Les demandes d'ajout d'un lieu en données (ticket LIEU-REQ, miroir d'Android `PlaceRequestsTest`) : lire un document sans jamais
/// croire le réseau, l'expiration à 30 jours, l'ordre des listes, les emplacements, et les lots tels que les règles les veulent.
final class PlaceRequestsTests: XCTestCase {
    private let uid = testMemberUid

    // MARK: - Lire un document

    func testAValidDocumentBecomesARequestWithItsCategoryAndDate() {
        let request = PlaceRequest.fromServer(
            id: "\(uid)_2", miffId: testMiffId, fields: requestFields(), createdAtMillis: 5_000
        )

        XCTAssertEqual(request?.name, "Club de judo")
        XCTAssertEqual(request?.category, .sport)
        XCTAssertEqual(request?.type, .trusted)
        XCTAssertEqual(request?.radiusM, 100)
        XCTAssertEqual(request?.slot, 2)
        XCTAssertEqual(request?.createdAtMillis, 5_000)
        XCTAssertEqual(request?.requestedBy, uid)
    }

    func testADocumentWithoutACategoryIsStillARequestButADocumentWithAnUnknownOneHasNone() {
        let without = PlaceRequest.fromServer(
            id: "\(uid)_1", miffId: testMiffId, fields: requestFields(category: nil), createdAtMillis: nil
        )
        let unknown = PlaceRequest.fromServer(
            id: "\(uid)_1", miffId: testMiffId, fields: requestFields(category: "museum"), createdAtMillis: nil
        )

        XCTAssertNotNil(without)
        XCTAssertNil(without?.category)
        XCTAssertNil(unknown?.category)
        XCTAssertNil(without?.createdAtMillis, "une demande dont l'écriture est en route n'a pas de date")
    }

    func testABadIdentifierOrAForgedAuthorIsNeverShown() {
        let fields = requestFields()

        XCTAssertNil(PlaceRequest.fromServer(id: "\(uid)_4", miffId: testMiffId, fields: fields, createdAtMillis: nil))
        XCTAssertNil(PlaceRequest.fromServer(id: "\(uid)_0", miffId: testMiffId, fields: fields, createdAtMillis: nil))
        XCTAssertNil(PlaceRequest.fromServer(id: "\(uid)", miffId: testMiffId, fields: fields, createdAtMillis: nil))
        XCTAssertNil(PlaceRequest.fromServer(id: "_1", miffId: testMiffId, fields: fields, createdAtMillis: nil))
        XCTAssertNil(PlaceRequest.fromServer(id: "bad/uid_1", miffId: testMiffId, fields: fields, createdAtMillis: nil))
        // `requestedBy` n'est pas l'uid de l'identifiant : un document forgé.
        XCTAssertNil(
            PlaceRequest.fromServer(id: "otheruid_1", miffId: testMiffId, fields: fields, createdAtMillis: nil)
        )
        XCTAssertNil(PlaceRequest.fromServer(id: "\(uid)_1", miffId: testMiffId, fields: nil, createdAtMillis: nil))
    }

    func testOutOfRangeOrInvisibleValuesAreNeverShown() {
        func parse(_ fields: [String: Any]) -> PlaceRequest? {
            PlaceRequest.fromServer(id: "\(uid)_1", miffId: testMiffId, fields: fields, createdAtMillis: nil)
        }
        var badLat = requestFields()
        badLat["lat"] = 91.0
        var badLng = requestFields()
        badLng["lng"] = -181.0
        var notANumber = requestFields()
        notANumber["lat"] = Double.nan

        XCTAssertNil(parse(requestFields(name: "\u{200B}\u{202E}")), "un nom sans rien de visible")
        XCTAssertNil(parse(requestFields(type: "danger")))
        XCTAssertNil(parse(requestFields(radiusM: Int64(49))))
        XCTAssertNil(parse(requestFields(radiusM: Int64(2001))))
        XCTAssertNil(parse(requestFields(radiusM: 100.5)))
        XCTAssertNil(parse(badLat))
        XCTAssertNil(parse(badLng))
        XCTAssertNil(parse(notANumber))
        XCTAssertNotNil(parse(requestFields(radiusM: Int64(2000))), "les règles acceptent jusqu'à 2 000 m")
    }

    func testTheNameIsCleanedOfInvisibleCharacters() {
        let request = PlaceRequest.fromServer(
            id: "\(uid)_1",
            miffId: testMiffId,
            fields: requestFields(name: "Club\u{202E} de judo"),
            createdAtMillis: nil
        )

        XCTAssertEqual(request?.name, "Club de judo")
    }

    func testARequestNeverShowsItsContentInADescription() {
        let request = testRequest()

        XCTAssertEqual(request.description, "PlaceRequest(redacted)")
        XCTAssertFalse("\(request)".contains("judo"))
        XCTAssertFalse("\(testRequestDraft())".contains("judo"))
        XCTAssertFalse("\(RequestsView.ready([request]))".contains("judo"))
        XCTAssertFalse("\(RequestContext(miffName: "Famille", premium: false, mine: [request]))".contains("judo"))
    }

    // MARK: - Expiration à 30 jours

    func testARequestExpiresAfterThirtyDaysAndNeverBefore() {
        let day = 24 * 60 * 60 * 1000
        let request = testRequest(createdAtMillis: 1_000)

        XCTAssertFalse(request.isExpired(nowMillis: 1_000 + 30 * day))
        XCTAssertTrue(request.isExpired(nowMillis: 1_000 + 30 * day + 1))
    }

    func testARequestWithoutADateIsBrandNewNeverExpired() {
        XCTAssertFalse(testRequest(createdAtMillis: nil).isExpired(nowMillis: Int.max))
    }

    // MARK: - Ordre

    func testTheCreatorSeesTheOldestFirstAndAnUndatedOneLast() {
        let old = testRequest(slot: 1, createdAtMillis: 100)
        let recent = testRequest(slot: 2, createdAtMillis: 300)
        let undated = testRequest(slot: 3, createdAtMillis: nil)

        XCTAssertEqual(
            PlaceRequestOrder.forCreator([undated, recent, old]).map { $0.id }, [old.id, recent.id, undated.id]
        )
    }

    func testTheMemberSeesTheNewestFirstAndAJustSentOneOnTop() {
        let old = testRequest(slot: 1, createdAtMillis: 100)
        let recent = testRequest(slot: 2, createdAtMillis: 300)
        let undated = testRequest(slot: 3, createdAtMillis: nil)

        XCTAssertEqual(
            PlaceRequestOrder.forMember([old, recent, undated]).map { $0.id }, [undated.id, recent.id, old.id]
        )
    }

    // MARK: - Emplacements

    func testTheFirstFreeSlotIsTheFirstWhoseDocumentDoesNotExist() {
        XCTAssertEqual(PlaceRequestSlots.firstFree(uid: uid, existing: []), 1)
        XCTAssertEqual(PlaceRequestSlots.firstFree(uid: uid, existing: [testRequest(slot: 1)]), 2)
        XCTAssertEqual(PlaceRequestSlots.firstFree(uid: uid, existing: [testRequest(slot: 2)]), 1)
        XCTAssertEqual(
            PlaceRequestSlots.firstFree(uid: uid, existing: [testRequest(slot: 1), testRequest(slot: 3)]), 2
        )
    }

    func testWithThreeSlotsTakenThereIsNoneAndAnotherPersonsRequestsDoNotCount() {
        let mine = [1, 2, 3].map { testRequest(slot: $0) }
        let other = testRequest(slot: 1, uid: "uidother1")

        XCTAssertNil(PlaceRequestSlots.firstFree(uid: uid, existing: mine))
        XCTAssertEqual(PlaceRequestSlots.firstFree(uid: uid, existing: [other]), 1)
    }

    func testAnExpiredRequestStillOccupiesItsSlotUntilItIsDeleted() {
        let expired = testRequest(slot: 1, createdAtMillis: 1)

        XCTAssertEqual(PlaceRequestSlots.firstFree(uid: uid, existing: [expired]), 2)
    }

    // MARK: - Brouillon

    func testADraftHasTheProblemsOfAPlaceWithoutTheMiffs() {
        XCTAssertNil(testRequestDraft().problem)
        XCTAssertEqual(testRequestDraft(name: "A").problem, .name)
        XCTAssertEqual(testRequestDraft(radiusM: 75).problem, .radius)
        XCTAssertEqual(
            PlaceRequestDraft(
                name: "Club", address: "  ", lat: 1, lng: 1, radiusM: 100, type: .trusted, category: .sport
            ).problem,
            .address
        )
        XCTAssertEqual(
            PlaceRequestDraft(
                name: "Club", address: "x", lat: 95, lng: 1, radiusM: 100, type: .trusted, category: .sport
            ).problem,
            .address
        )
    }

    // MARK: - Rôle

    func testTheRoleIsNeverDeducedFromAnUnknownUid() {
        let id = "\(uid)_1"

        XCTAssertTrue(PlaceRequestRole.isAuthor(requestId: id, myUid: uid))
        XCTAssertFalse(PlaceRequestRole.isAuthor(requestId: id, myUid: nil))
        XCTAssertFalse(PlaceRequestRole.isAuthor(requestId: id, myUid: ""))
        XCTAssertFalse(PlaceRequestRole.isAuthor(requestId: id, myUid: "autreuid1"))
        XCTAssertFalse(PlaceRequestRole.isAuthor(requestId: "pas un id", myUid: uid))
        XCTAssertTrue(PlaceRequestRole.isCreator(miffId: testMiffId, creatorMiffs: [testMiffId]))
        XCTAssertFalse(PlaceRequestRole.isCreator(miffId: testMiffId, creatorMiffs: []), "Miff inconnue : pas créateur")
    }

    // MARK: - Lots

    func testCreatingIsALotOfTwoWithTheNineKeysAndAMergedCounter() {
        let lot = PlaceRequestWrites.create(uid: uid, miffId: testMiffId, slot: 2, draft: testRequestDraft())

        XCTAssertEqual(lot.count, 2)
        XCTAssertEqual(lot[0].path, "miffs/\(testMiffId)/placeRequests/\(uid)_2")
        XCTAssertEqual(lot[0].kind, .set)
        XCTAssertEqual(
            Set(lot[0].data.keys),
            ["requestedBy", "name", "type", "category", "address", "lat", "lng", "radiusM", "createdAt"],
            "chaque clé, et aucune autre : les règles refusent une clé de plus et une clé de moins"
        )
        XCTAssertEqual(lot[0].data["requestedBy"], .string(uid))
        XCTAssertEqual(lot[0].data["category"], .string("sport"))
        XCTAssertEqual(lot[0].data["createdAt"], .serverTimestamp, "jamais la date du téléphone")
        XCTAssertEqual(lot[0].data["radiusM"], .integer(100), "un entier, pas un décimal")
        XCTAssertEqual(lot[0].data["lat"], .double(47.32207), "5 décimales")
        XCTAssertEqual(lot[0].data["lng"], .double(5.04148))
        XCTAssertEqual(lot[1].path, "miffs/\(testMiffId)/meta/placeRequests")
        // Un set avec fusion : un set simple remplacerait le compteur au lieu de l'incrémenter.
        XCTAssertEqual(lot[1].kind, .merge)
        XCTAssertEqual(lot[1].data["pending"], .increment(1))
        XCTAssertEqual(lot[1].data["lastReqId"], .string("\(uid)_2"), "lié à CETTE demande")
        XCTAssertEqual(lot[1].data["updatedAt"], .serverTimestamp)
        XCTAssertEqual(Set(lot[1].data.keys), ["pending", "lastReqId", "updatedAt"])
    }

    func testTheNameAndAddressAreCleanedAndTrimmedBeforeTheyAreWritten() {
        let draft = PlaceRequestDraft(
            name: "  Club de judo \u{200B}", address: "12 rue\u{202E} des Écoles ", lat: 1, lng: 1,
            radiusM: 100, type: .risk, category: .other
        )

        let document = PlaceRequestWrites.requestDocument(uid: uid, draft: draft)

        XCTAssertEqual(document["name"], .string("Club de judo"))
        XCTAssertEqual(document["address"], .string("12 rue des Écoles"))
        XCTAssertEqual(document["type"], .string("risk"))
        XCTAssertEqual(document["category"], .string("other"))
    }

    func testWithdrawingIsALotOfTwoWithTheCounterDownByOne() {
        let lot = PlaceRequestWrites.withdraw(miffId: testMiffId, requestId: "\(uid)_1")

        XCTAssertEqual(lot.map { $0.kind }, [.delete, .merge])
        XCTAssertEqual(lot[0].path, "miffs/\(testMiffId)/placeRequests/\(uid)_1")
        XCTAssertEqual(lot[1].data["pending"], .increment(-1))
        XCTAssertEqual(lot[1].data["lastReqId"], .string("\(uid)_1"))
    }

    func testTheFallbackOfAWithdrawalWritesTheAbsoluteValueZero() {
        let lot = PlaceRequestWrites.withdrawWithCounterAtZero(miffId: testMiffId, requestId: "\(uid)_3")

        XCTAssertEqual(lot.map { $0.kind }, [.delete, .merge])
        XCTAssertEqual(lot[1].data["pending"], .integer(0))
        XCTAssertEqual(lot[1].data["lastReqId"], .string("\(uid)_3"))
        XCTAssertEqual(Set(lot[1].data.keys), ["pending", "lastReqId", "updatedAt"])
    }

    func testNoWriteOfARequestIsAStatusOrAPosition() {
        let lots = [
            PlaceRequestWrites.create(uid: uid, miffId: testMiffId, slot: 1, draft: testRequestDraft()),
            PlaceRequestWrites.withdraw(miffId: testMiffId, requestId: "\(uid)_1"),
            PlaceRequestWrites.withdrawWithCounterAtZero(miffId: testMiffId, requestId: "\(uid)_1")
        ]

        for lot in lots {
            XCTAssertTrue(SessionWriteGuard.isSendable(lot), "aucun lot de demande n'écrit un statut ni une position")
        }
    }

    func testTheLimitsAreTheOnesOfTheTicket() {
        XCTAssertEqual(PlaceRequestLimits.perMember, 3)
        XCTAssertEqual(PlaceRequestLimits.perMiff, 10)
        XCTAssertEqual(PlaceRequestLimits.expiryMillis, 30 * 24 * 60 * 60 * 1000)
        XCTAssertEqual(PlaceRequestLimits.slots, [1, 2, 3])
    }

    func testARequestIsDrawnAsThePlaceItWouldBecomeButNeverAsOneOfTheMiff() {
        let place = testRequest(name: "Chez Mamie", category: .relative).asPlace

        XCTAssertEqual(place.id, "\(testMemberUid)_1", "l'identifiant est celui de la demande")
        XCTAssertEqual(place.name, "Chez Mamie")
        XCTAssertEqual(place.category, .relative)
        XCTAssertEqual(place.radiusM, 100)
        XCTAssertFalse(Place.isPlaceId(place.id), "ce n'est pas un identifiant de lieu : `_` n'en fait pas partie")
    }
}
