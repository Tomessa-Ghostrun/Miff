import XCTest
@testable import Miff

/// Les lots des lieux sont le CONTRAT avec `backend/firestore.rules` (déployées le 2026-11-04) : `validPlaceShape` (les 11 clés, aucune
/// autre), `validPlaceCreate` (createdBy = uid, createdAt = updatedAt = heure du serveur), `validPlaceUpdate` (seulement name, type,
/// category, address, lat, lng, radiusM, priority, updatedAt), 5 Miffs au plus par lot, 20 lectures par lot (miroir de
/// `PlaceWritesTest` d'Android).
final class PlaceWritesTests: XCTestCase {
    private let uid = "u1"
    private let placeId = "AbCdEfGhIjKlMnOpQrSt"

    /// Les clés de validPlaceShape, recopiées des règles.
    private let shapeKeys: Set<String> = [
        "name", "type", "category", "address", "lat", "lng", "radiusM",
        "priority", "createdBy", "createdAt", "updatedAt"
    ]
    private let updatableKeys: Set<String> = [
        "name", "type", "category", "address", "lat", "lng", "radiusM", "updatedAt"
    ]

    private func draft(
        miffIds: [String] = ["m1"],
        type: PlaceType = .trusted,
        name: String = "  Maison du père de Nathan "
    ) -> PlaceDraft {
        PlaceDraft(
            name: name,
            miffIds: miffIds,
            address: "17 Avenue d'Italie, 75013 Paris, France",
            lat: 48.8292675,
            lng: 2.3567938,
            radiusM: 150,
            type: type,
            category: .relative
        )
    }

    func testCreateWritesEveryKeyAndNoOtherInEachMiffWithTheSameId() {
        let writes = PlaceWrites.create(uid: uid, placeId: placeId, draft: draft(miffIds: ["m1", "m2", "m3"]))

        XCTAssertEqual(writes.count, 3)
        XCTAssertEqual(
            writes.map { $0.path },
            ["miffs/m1/places/\(placeId)", "miffs/m2/places/\(placeId)", "miffs/m3/places/\(placeId)"]
        )
        for write in writes {
            XCTAssertEqual(write.kind, .set)
            XCTAssertEqual(Set(write.data.keys), shapeKeys)
        }
    }

    func testCreateValuesFollowTheRules() throws {
        let data = try XCTUnwrap(PlaceWrites.create(uid: uid, placeId: placeId, draft: draft()).first).data

        XCTAssertEqual(data["name"], .string("Maison du père de Nathan"))
        XCTAssertEqual(data["type"], .string("trusted"))
        XCTAssertEqual(data["category"], .string("relative"))
        XCTAssertEqual(data["address"], .string("17 Avenue d'Italie, 75013 Paris, France"))
        XCTAssertEqual(data["lat"], .double(48.82927))
        XCTAssertEqual(data["lng"], .double(2.35679))
        XCTAssertEqual(data["radiusM"], .integer(150), "les règles exigent un entier (`is int`), pas un décimal")
        XCTAssertEqual(data["priority"], .null)
        XCTAssertEqual(data["createdBy"], .string(uid))
        // Les dates sont celles du serveur, jamais une date du téléphone.
        XCTAssertEqual(data["createdAt"], .serverTimestamp)
        XCTAssertEqual(data["updatedAt"], .serverTimestamp)
    }

    func testCreateADangerZoneWritesTheTechnicalValueRisk() throws {
        let write = try XCTUnwrap(PlaceWrites.create(uid: uid, placeId: placeId, draft: draft(type: .risk)).first)

        XCTAssertEqual(write.data["type"], .string("risk"))
    }

    func testUpdateTouchesOnlyTheFieldsTheRulesAllow() {
        let fields = PlaceWrites.updateFields(draft())

        XCTAssertEqual(Set(fields.keys), updatableKeys)
        XCTAssertNil(fields["createdBy"])
        XCTAssertNil(fields["createdAt"])
        XCTAssertNil(fields["priority"])
        XCTAssertEqual(fields["updatedAt"], .serverTimestamp)
    }

    func testChangeKeptAreUpdatedAddedAreCreatedRemovedAreDeletedInOneLot() throws {
        let lots = PlaceWrites.change(
            uid: uid,
            placeId: placeId,
            currentMiffIds: ["m1", "m2"],
            draft: draft(miffIds: ["m2", "m3"]),
            premiumChecks: (typeChangedToRisk: false, addedNeedPremium: true)
        )

        XCTAssertEqual(lots.count, 1)
        let lot = try XCTUnwrap(lots.first)
        func write(_ miffId: String) -> DocWrite? {
            lot.first { $0.path == "miffs/\(miffId)/places/\(placeId)" }
        }
        XCTAssertEqual(write("m2")?.kind, .update)
        XCTAssertEqual(write("m3")?.kind, .set)
        XCTAssertEqual(Set(write("m3")?.data.keys ?? []), shapeKeys)
        XCTAssertEqual(write("m1")?.kind, .delete)
        XCTAssertEqual(lot.count, 3)
    }

    func testChangeALotThatWouldReadMoreThanTwentyDocumentsIsCutInTwoChangesFirst() {
        // 5 Miffs ajoutées (3 lectures chacune : créateur 2 + Miff+ 1) et 5 retirées (2 chacune) = 25 > 20.
        let lots = PlaceWrites.change(
            uid: uid,
            placeId: placeId,
            currentMiffIds: ["a", "b", "c", "d", "e"],
            draft: draft(miffIds: ["f", "g", "h", "i", "j"]),
            premiumChecks: (typeChangedToRisk: false, addedNeedPremium: true)
        )

        XCTAssertEqual(lots.count, 2)
        XCTAssertTrue(lots[0].allSatisfy { $0.kind == .set })
        XCTAssertTrue(lots[1].allSatisfy { $0.kind == .delete })
        XCTAssertEqual(lots[0].count, 5)
        XCTAssertEqual(lots[1].count, 5)
    }

    func testChangeWithinTheBudgetStaysOneAtomicLot() {
        let lots = PlaceWrites.change(
            uid: uid,
            placeId: placeId,
            currentMiffIds: ["a", "b", "c", "d", "e"],
            draft: draft(miffIds: ["a", "b", "c", "d", "e"], type: .risk),
            premiumChecks: (typeChangedToRisk: true, addedNeedPremium: false)
        )

        // 5 mises à jour qui font du lieu une zone de danger : (2 + 1) x 5 = 15 lectures.
        XCTAssertEqual(lots.count, 1)
        XCTAssertEqual(lots.first?.count, 5)
    }

    func testChangeWithNothingToDoWritesNothing() {
        let lots = PlaceWrites.change(
            uid: uid,
            placeId: placeId,
            currentMiffIds: [],
            draft: draft(miffIds: []),
            premiumChecks: (typeChangedToRisk: false, addedNeedPremium: false)
        )

        XCTAssertTrue(lots.isEmpty)
    }

    func testDeleteIsOneLotInEveryMiff() {
        let writes = PlaceWrites.delete(placeId: placeId, miffIds: ["m1", "m2"])

        XCTAssertEqual(writes.count, 2)
        XCTAssertTrue(writes.allSatisfy { $0.kind == .delete && $0.data.isEmpty })
    }

    func testBudgetFiveMiffsAtMiffPlusIsFifteenOfTwenty() {
        XCTAssertEqual(PlaceWrites.createReads(miffCount: PlaceLimits.maxMiffs, needsPremium: true), 15)
        XCTAssertEqual(PlaceWrites.createReads(miffCount: PlaceLimits.maxMiffs, needsPremium: false), 10)
        XCTAssertLessThanOrEqual(
            PlaceWrites.createReads(miffCount: PlaceLimits.maxMiffs, needsPremium: true),
            PlaceLimits.ruleReadBudget
        )
    }

    func testAWriteNeverShowsTheDataInItsDescription() throws {
        let write = try XCTUnwrap(PlaceWrites.create(uid: uid, placeId: placeId, draft: draft()).first)

        XCTAssertFalse("\(write)".contains("Maison"))
        XCTAssertFalse("\(write)".contains("Avenue"))
        XCTAssertFalse("\(draft())".contains("Maison"))
    }
}
