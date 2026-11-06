import XCTest
@testable import Miff

/// Les règles du formulaire et des limites (ticket LOC-3, miroir de `PlaceRulesTest` d'Android ; ticket §6.1 à §6.3, critères PL2 à
/// PL5, LIM1 à LIM3, MIF1).
final class PlaceRulesTests: XCTestCase {
    // MARK: - Nom (PL2, PL3)

    func testNameButtonWorksFromOneCharacterButTheMinimumIsTwo() {
        XCTAssertFalse(PlaceName.hasSomething(""))
        XCTAssertFalse(PlaceName.hasSomething("   "))
        XCTAssertTrue(PlaceName.hasSomething("M"))
        XCTAssertFalse(PlaceName.isValid("M"))
        XCTAssertTrue(PlaceName.isValid("Ma"))
        XCTAssertTrue(PlaceName.isValid("  Ma  "))
        XCTAssertFalse(PlaceName.isValid(" M "))
    }

    func testNameIsCutAtFiftyAndSaysSo() {
        let limited = PlaceName.limit(String(repeating: "a", count: 60))
        XCTAssertEqual(limited.text.utf16.count, 50)
        XCTAssertTrue(limited.limitReached)
        XCTAssertFalse(PlaceName.limit(String(repeating: "a", count: 49)).limitReached)
        XCTAssertTrue(PlaceName.limit(String(repeating: "a", count: 50)).limitReached)
        XCTAssertEqual(PlaceName.stored(String(repeating: "a", count: 80)).utf16.count, 50)
    }

    func testNameIsNeverCutInTheMiddleOfAnEmoji() {
        let typed = String(repeating: "a", count: 49) + "😀"
        let text = PlaceName.limit(typed).text

        XCTAssertLessThanOrEqual(text.utf16.count, 50)
        XCTAssertFalse(text.contains("😀"), "l'émoji ne tient pas : il est retiré en entier, jamais coupé")
        XCTAssertEqual(text, String(repeating: "a", count: 49))
    }

    func testNameLosesInvisibleControls() {
        XCTAssertEqual(PlaceName.stored("\u{202E}Maison\u{200B}"), "Maison")
    }

    // MARK: - Rayon (PL4)

    func testRadiusIs50To800ByStepsOf50WithSixteenValues() {
        XCTAssertEqual(PlaceRadius.values.count, 16)
        XCTAssertEqual(PlaceRadius.values.first, 50)
        XCTAssertEqual(PlaceRadius.values.last, 800)
        for (previous, next) in zip(PlaceRadius.values, PlaceRadius.values.dropFirst()) {
            XCTAssertEqual(next - previous, 50)
        }
        XCTAssertEqual(PlaceLimits.radiusDefault, 100)
    }

    func testRadiusSnapsToTheNearestAllowedValue() {
        XCTAssertEqual(PlaceRadius.snap(0), 50)
        XCTAssertEqual(PlaceRadius.snap(74), 50)
        XCTAssertEqual(PlaceRadius.snap(76), 100)
        XCTAssertEqual(PlaceRadius.snap(149), 150)
        XCTAssertEqual(PlaceRadius.snap(5_000), 800)
        XCTAssertEqual(PlaceRadius.snap(-300), 50)
        XCTAssertEqual(PlaceRadius.snap(1e300), 800, "une valeur énorme ne fait jamais planter la conversion")
        XCTAssertEqual(PlaceRadius.snap(.nan), PlaceLimits.radiusDefault)
        XCTAssertTrue(PlaceRadius.isAllowed(450))
        XCTAssertFalse(PlaceRadius.isAllowed(120))
        XCTAssertFalse(PlaceRadius.isAllowed(850))
    }

    func testRadiusNoteShowsBelowOneHundredOnly() {
        XCTAssertTrue(PlaceRadius.isBelowRecommended(50))
        XCTAssertFalse(PlaceRadius.isBelowRecommended(100))
        XCTAssertFalse(PlaceRadius.isBelowRecommended(800))
    }

    // MARK: - Coordonnées et adresse

    func testCoordinatesHaveFiveDecimalsAndStayOnThePlanet() {
        XCTAssertEqual(PlaceCoordinates.round5(48.8292675), 48.82927)
        XCTAssertEqual(PlaceCoordinates.round5(2.3567938), 2.35679)
        XCTAssertTrue(PlaceCoordinates.isValid(lat: 48, lng: 2))
        XCTAssertFalse(PlaceCoordinates.isValid(lat: 91, lng: 0))
        XCTAssertFalse(PlaceCoordinates.isValid(lat: 0, lng: 181))
        XCTAssertFalse(PlaceCoordinates.isValid(lat: .nan, lng: 0))
        XCTAssertFalse(PlaceCoordinates.isValid(lat: 0, lng: .infinity))
    }

    func testAddressIsBoundedLikeTheRules() {
        XCTAssertEqual(PlaceAddressText.stored(String(repeating: "x", count: 300)).utf16.count, 200)
        XCTAssertEqual(PlaceAddressText.stored("  Rue \u{200B}"), "Rue")
    }

    // MARK: - Le brouillon (miroir de validPlaceShape)

    private func draft(
        name: String = "Maison",
        miffIds: [String] = ["m1"],
        address: String = "17 Avenue d'Italie, 75013 Paris",
        lat: Double = 48.82927,
        radiusM: Int = 100
    ) -> PlaceDraft {
        PlaceDraft(
            name: name,
            miffIds: miffIds,
            address: address,
            lat: lat,
            lng: 2.35679,
            radiusM: radiusM,
            type: .trusted,
            category: .home
        )
    }

    func testDraftValidOneHasNoProblem() {
        XCTAssertNil(draft().problem)
    }

    func testDraftProblems() {
        XCTAssertEqual(draft(name: "M").problem, .name)
        XCTAssertEqual(draft(miffIds: []).problem, .miffs)
        XCTAssertEqual(draft(miffIds: ["a", "b", "c", "d", "e", "f"]).problem, .miffs)
        XCTAssertEqual(draft(miffIds: ["a", "a"]).problem, .miffs)
        XCTAssertEqual(draft(address: " ").problem, .address)
        XCTAssertEqual(draft(lat: 120).problem, .address)
        XCTAssertEqual(draft(radiusM: 120).problem, .radius)
        XCTAssertNil(draft(miffIds: ["a", "b", "c", "d", "e"]).problem)
    }

    // MARK: - Les limites de la formule gratuite (LIM1 à LIM3)

    private func slot(
        premium: Bool = false,
        type: PlaceType = .trusted,
        existing: [String: Set<String>] = ["m1": []],
        distinct: Int? = nil
    ) -> PlaceSlot {
        let count = distinct ?? existing.values.reduce(into: Set<String>()) { $0.formUnion($1) }.count
        return PlaceQuota.forCreate(
            premium: premium,
            type: type,
            existingIdsByMiff: existing,
            distinctPlaces: count,
            newId: { "NEWID123456789012345" }
        )
    }

    func testFreeTakesTheFirstFreeFixedSlot() {
        XCTAssertEqual(slot(), .use("p1"))
        XCTAssertEqual(slot(existing: ["m1": ["p1"]]), .use("p2"))
        XCTAssertEqual(slot(existing: ["m1": ["p2"]]), .use("p1"))
    }

    func testFreeThirdPlaceNeedsMiffPlusAndNothingIsWritten() {
        XCTAssertEqual(slot(existing: ["m1": ["p1", "p2"]]), .needsPremium(.limit))
    }

    func testFreeDangerZoneNeedsMiffPlusEvenWithRoomLeft() {
        XCTAssertEqual(slot(type: .risk), .needsPremium(.danger))
    }

    /// Le scénario de la revue d'Android : « p1 » dans la Miff A, un lieu NEUF dans la Miff B seulement. B n'a pas de « p1 », mais le
    /// prendre ferait partager un identifiant à deux lieux différents (lus comme UN lieu dans deux Miffs : le compte de la limite
    /// serait faux).
    func testFreeASlotTakenInAnotherOfMyMiffsIsNeverReusedForAnotherPlace() {
        XCTAssertEqual(slot(existing: ["a": ["p1"], "b": []]), .use("p2"))
    }

    func testFreeOnePlaceInTwoMiffsCountsOnceAndTwoDifferentPlacesCountTwice() {
        // p1 dans A et dans B = UN lieu : le 2e lieu prend p2.
        XCTAssertEqual(slot(existing: ["a": ["p1"], "b": ["p1"]], distinct: 1), .use("p2"))
        // p1 dans A et p2 dans B = DEUX lieux : la limite.
        XCTAssertEqual(slot(existing: ["a": ["p1"], "b": ["p2"]], distinct: 2), .needsPremium(.limit))
    }

    func testFreeTwoDifferentPlacesThatShareAnIdentifierStillCountTwo() {
        // Écrits par une ancienne version : p1 dans A et p1 dans B sont deux lieux (le modèle les groupe à part) : la limite.
        XCTAssertEqual(slot(existing: ["a": ["p1"], "b": ["p1"]], distinct: 2), .needsPremium(.limit))
    }

    func testPremiumGetsAFreshIdentifierAnyTypeAnyNumber() {
        XCTAssertEqual(
            slot(premium: true, type: .risk, existing: ["m1": ["p1", "p2", "x"]]),
            .use("NEWID123456789012345")
        )
    }

    func testTypeChangeToDangerNeedsMiffPlusBackToTrustedNever() {
        XCTAssertEqual(PlaceQuota.forTypeChange(premium: false, from: .trusted, to: .risk), .danger)
        XCTAssertNil(PlaceQuota.forTypeChange(premium: true, from: .trusted, to: .risk))
        XCTAssertNil(PlaceQuota.forTypeChange(premium: false, from: .risk, to: .trusted))
        // Un compte dont Miff+ est fini garde la zone de danger qu'il a (rien ne change le type).
        XCTAssertNil(PlaceQuota.forTypeChange(premium: false, from: .risk, to: .risk))
    }

    func testAtFreeLimitOnlyForTheFreePlanWithTwoPlaces() {
        XCTAssertTrue(PlaceQuota.isAtFreeLimit(premium: false, distinctPlaces: 2))
        XCTAssertFalse(PlaceQuota.isAtFreeLimit(premium: false, distinctPlaces: 1))
        XCTAssertFalse(PlaceQuota.isAtFreeLimit(premium: true, distinctPlaces: 7))
    }

    // MARK: - Les Miffs cochées à l'ouverture (MIF1)

    private func miff(_ id: String, _ joinedAt: Int) -> OwnedMiff {
        OwnedMiff(id: id, name: "Miff \(id)", joinedAtMillis: joinedAt)
    }

    func testDefaultIsTheLastJoinedMiffIfThePersonCreatedIt() {
        let owned = [miff("a", 10), miff("b", 20)]

        XCTAssertEqual(DefaultMiffChoice.of(owned: owned, lastJoinedId: "b"), "b")
        XCTAssertEqual(DefaultMiffChoice.of(owned: owned, lastJoinedId: "a"), "a")
    }

    func testDefaultIsTheFirstCreatedWhenTheLastJoinedIsSomebodyElses() {
        let owned = [miff("b", 20), miff("a", 10)]

        XCTAssertEqual(DefaultMiffChoice.of(owned: owned, lastJoinedId: "someone-elses"), "a")
        XCTAssertEqual(DefaultMiffChoice.of(owned: owned, lastJoinedId: nil), "a")
        XCTAssertNil(DefaultMiffChoice.of(owned: [], lastJoinedId: "x"))
    }
}
