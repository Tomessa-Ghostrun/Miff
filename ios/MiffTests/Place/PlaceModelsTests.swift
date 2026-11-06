import XCTest
@testable import Miff

/// Un lieu tel que le serveur le décrit : nettoyé, et laissé de côté quand il ne peut pas en être un (ticket LOC-3, miroir de
/// `PlaceModelsTest` ; critères PL6, ADR4).
final class PlaceModelsTests: XCTestCase {
    private func full() -> [String: Any] {
        [
            "name": "Maison de Papa",
            "type": "trusted",
            "category": "home",
            "address": "26 rue du Volnay, 21000 Dijon, France",
            "lat": 47.32, // Firestore rend un décimal...
            "lng": 5.04,
            "radiusM": Int64(150), // ... et un entier pour un entier
            "createdBy": "u1"
        ]
    }

    private func makePlace(_ changes: [String: Any?] = [:], id: String = "p1", miffId: String = "m1") -> Place? {
        var fields = full()
        for (key, value) in changes {
            fields[key] = value
        }
        return Place.fromServer(id: id, miffId: miffId, fields: fields)
    }

    func testAWholeDocumentBecomesAPlace() throws {
        let place = try XCTUnwrap(makePlace())

        XCTAssertEqual(place.name, "Maison de Papa")
        XCTAssertEqual(place.type, .trusted)
        XCTAssertEqual(place.category, .home)
        XCTAssertEqual(place.radiusM, 150)
        XCTAssertEqual(place.createdBy, "u1")
        XCTAssertEqual(place.miffId, "m1")
    }

    func testAnIntegerWrittenAsADecimalIsStillAnIntegerRadius() throws {
        XCTAssertEqual(try XCTUnwrap(makePlace(["radiusM": 150.0])).radiusM, 150)
        XCTAssertEqual(try XCTUnwrap(makePlace(["radiusM": 150])).radiusM, 150)
        XCTAssertNil(makePlace(["radiusM": 150.5]), "un rayon décimal n'est pas un rayon des règles")
    }

    func testTheTextsAreCleaned() throws {
        let place = try XCTUnwrap(makePlace(["name": "\u{202E}Maison\u{200B} ", "address": "\u{200B}26 rue"]))

        XCTAssertEqual(place.name, "Maison")
        XCTAssertEqual(place.address, "26 rue")
    }

    func testAPlaceWithNothingVisibleInItsNameIsNotShown() {
        XCTAssertNil(makePlace(["name": "\u{200B}\u{202E} "]))
    }

    func testADocumentThatCannotBeAPlaceIsLeftOut() {
        XCTAssertNil(Place.fromServer(id: "p1", miffId: "m1", fields: nil))
        XCTAssertNil(makePlace(id: "not an id!"))
        XCTAssertNil(makePlace(id: ""))
        XCTAssertNil(makePlace(id: String(repeating: "a", count: 21)))
        XCTAssertNil(makePlace(["type": "weird"]))
        XCTAssertNil(makePlace(["lat": 200.0]))
        XCTAssertNil(makePlace(["lng": Double.nan]))
        XCTAssertNil(makePlace(["radiusM": Int64(10)]))
        XCTAssertNil(makePlace(["radiusM": Int64(9_999)]))
        XCTAssertNil(makePlace(["lat": nil]))
        XCTAssertNil(makePlace(["lat": "47.3"]), "un texte n'est pas une latitude")
    }

    func testAPlaceWrittenBeforeTheCategoriesHasNoneAndOnlyTheCategoryIsMissing() throws {
        let legacy = try XCTUnwrap(makePlace(["category": nil]))

        XCTAssertNil(legacy.category)
        XCTAssertEqual(legacy.type, .trusted)
        XCTAssertNil(try XCTUnwrap(makePlace(["category": "pirate"])).category)
    }

    func testTheDangerZoneKeepsItsTechnicalValueRisk() throws {
        XCTAssertEqual(PlaceType.risk.rawValue, "risk")
        XCTAssertEqual(try XCTUnwrap(makePlace(["type": "risk"])).type, .risk)
    }

    func testTheSevenCategoriesAreTheClosedListOfTheRules() {
        XCTAssertEqual(
            PlaceCategory.allCases.map { $0.rawValue },
            ["home", "work", "school", "sport", "relative", "leisure", "other"]
        )
        XCTAssertEqual(PlaceCategory.preselected, .home)
    }

    func testCopiesOfOnePlaceInSeveralMiffsMakeOneGroup() throws {
        let first = try XCTUnwrap(makePlace(id: "p1", miffId: "m1"))
        let second = try XCTUnwrap(makePlace(id: "p1", miffId: "m2"))
        let third = try XCTUnwrap(makePlace(id: "p2", miffId: "m1"))

        let groups = PlaceGroup.groupById([first, second, third])

        XCTAssertEqual(groups.count, 2)
        XCTAssertEqual(groups.first { $0.id == "p1" }?.miffIds, ["m1", "m2"])
        XCTAssertEqual(groups.last?.id, "p2")
    }

    func testTwoDifferentPlacesThatShareAnIdentifierAreNotMergedIntoOne() throws {
        let first = try XCTUnwrap(makePlace(id: "p1", miffId: "m1"))
        let other = try XCTUnwrap(makePlace(["name": "Le stade", "lat": 10.0], id: "p1", miffId: "m2"))

        let groups = PlaceGroup.groupById([first, other])

        XCTAssertEqual(groups.count, 2)
        XCTAssertEqual(groups[0].miffIds, ["m1"])
        XCTAssertEqual(groups[1].miffIds, ["m2"])
    }

    func testPlaceIdsAreAlphanumericAndShort() {
        XCTAssertTrue(Place.isPlaceId("p1"))
        XCTAssertTrue(Place.isPlaceId("AbCdEfGhIjKlMnOpQrSt"))
        XCTAssertFalse(Place.isPlaceId(""))
        XCTAssertFalse(Place.isPlaceId("p1/../x"))
        XCTAssertFalse(Place.isPlaceId("é"))
    }

    func testNoDescriptionShowsAnything() throws {
        let place = try XCTUnwrap(makePlace())

        XCTAssertEqual(place.description, "Place(redacted)")
        XCTAssertEqual(PlaceGroup(id: "p1", copies: [place]).description, "PlaceGroup(redacted)")
        XCTAssertTrue(OwnedMiff(id: "m1", name: "Famille", joinedAtMillis: 1).description.contains("redacted"))
        XCTAssertFalse("\(place)".contains("Maison"))
        XCTAssertFalse("\(place)".contains("Dijon"))
    }
}
