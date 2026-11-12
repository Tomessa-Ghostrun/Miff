import XCTest
@testable import Miff

/// Les petites règles pures que les écrans des lieux utilisent (ticket LOC-3 ; critères PL1, PL10, LIM1, LIM5).
final class PlacePresentationTests: XCTestCase {
    private func places(_ ids: [String]) -> [Place] {
        ids.map { testPlace(id: $0, name: "Lieu \($0)") }
    }

    // MARK: - La liste (PL1, PL10, LIM1)

    func testACreatorOnTheFreePlanWithThreePlacesIsAtTheLimit() {
        let ui = PlaceListUi(places: places(["p1", "p2", "p3"]), isCreator: true, premium: false, notice: nil)

        XCTAssertTrue(ui.atFreeLimit)
        XCTAssertEqual(ui.distinctCount, 3)
    }

    private func list(_ ids: [String], isCreator: Bool, premium: Bool?) -> PlaceListUi {
        PlaceListUi(places: places(ids), isCreator: isCreator, premium: premium, notice: nil)
    }

    func testTheLimitIsNeverClaimedWhenThePlanIsUnknownPremiumOrTheOneWhoLooksIsAMember() {
        XCTAssertFalse(list(["p1", "p2", "p3"], isCreator: true, premium: nil).atFreeLimit)
        XCTAssertFalse(list(["p1", "p2", "p3"], isCreator: true, premium: true).atFreeLimit)
        XCTAssertFalse(list(["p1", "p2", "p3"], isCreator: false, premium: false).atFreeLimit)
        // 2 lieux sur 3 : il reste une place.
        XCTAssertFalse(list(["p1", "p2"], isCreator: true, premium: false).atFreeLimit)
    }

    func testTheFreeCounterIsOnlyForACreatorWhoseFreePlanIsKnown() {
        XCTAssertTrue(list(["p1"], isCreator: true, premium: false).showsFreeCounter)
        XCTAssertFalse(list(["p1"], isCreator: true, premium: nil).showsFreeCounter, "formule inconnue")
        XCTAssertFalse(list(["p1"], isCreator: true, premium: true).showsFreeCounter)
        XCTAssertFalse(list(["p1"], isCreator: false, premium: false).showsFreeCounter)
    }

    func testTheCounterCountsPlacesNotDocuments() {
        let ui = PlaceListUi(places: places(["p1", "p1", "p2"]), isCreator: true, premium: false, notice: nil)

        XCTAssertEqual(ui.distinctCount, 2)
    }

    func testTheListShowsNothingInItsDescription() {
        let ui = PlaceListUi(places: places(["p1"]), isCreator: true, premium: false, notice: .saved(name: "Maison"))

        XCTAssertEqual(ui.description, "PlaceListUi(redacted)")
        XCTAssertFalse("\(ui)".contains("Maison"))
    }

    // MARK: - L'offre (LIM5)

    func testOnlyTheThirdPlaceHasAnAnalyticsTriggerNothingForTheDangerZone() {
        XCTAssertEqual(PlacePaywall.shown(for: .limit), .paywallShown(trigger: .extraTrustedPlace))
        XCTAssertEqual(PlacePaywall.dismissed(for: .limit), .paywallDismissed(trigger: .extraTrustedPlace))
        XCTAssertNil(PlacePaywall.shown(for: .danger))
        XCTAssertNil(PlacePaywall.dismissed(for: .danger))
    }

    func testThePaywallEventsCarryNoPlace() throws {
        let event = try XCTUnwrap(PlacePaywall.shown(for: .limit))

        XCTAssertEqual(event.name, "paywall_shown")
        XCTAssertEqual(event.params.keys.sorted(), ["trigger"])
        XCTAssertEqual(AnalyticsContract.validate(event.name, params: event.params), .allowed)
    }

    // MARK: - Les événements de l'offre, une fois par apparition

    func testThePaywallIsCountedOncePerAppearanceAndNeverClosedWithoutBeingOpened() {
        let tracking = PlacePaywallTracking(reason: .limit)

        XCTAssertNil(tracking.disappeared(), "jamais « fermée » sans avoir été « ouverte »")
        XCTAssertEqual(tracking.appeared(), .paywallShown(trigger: .extraTrustedPlace))
        XCTAssertNil(tracking.appeared(), "une seule ouverture")
        XCTAssertEqual(tracking.disappeared(), .paywallDismissed(trigger: .extraTrustedPlace))
        XCTAssertNil(tracking.disappeared(), "une seule fermeture (croix, « Plus tard » ou geste retour)")
    }

    func testTheDangerZonePaywallSendsNothing() {
        let tracking = PlacePaywallTracking(reason: .danger)

        XCTAssertNil(tracking.appeared())
        XCTAssertNil(tracking.disappeared())
    }

    // MARK: - « Voir sur la carte »

    func testTheMapLinkOpensThePlansAppOnThePlaceWithADotWhateverTheLanguage() throws {
        let url = try XCTUnwrap(PlaceMapLink.url(lat: 48.82927, lng: 2.35679))

        XCTAssertEqual(url.absoluteString, "maps://?ll=48.82927,2.35679&q=48.82927,2.35679")
        XCTAssertEqual(url.scheme, "maps", "jamais une page web")
    }

    func testTheMapLinkKeepsFiveDecimalsAndNegativeNumbers() throws {
        let url = try XCTUnwrap(PlaceMapLink.url(lat: -33.8688, lng: 151.2093))

        XCTAssertEqual(url.absoluteString, "maps://?ll=-33.86880,151.20930&q=-33.86880,151.20930")
    }

    func testTheMapLinkRefusesCoordinatesOffThePlanet() {
        XCTAssertNil(PlaceMapLink.url(lat: 120, lng: 0))
        XCTAssertNil(PlaceMapLink.url(lat: .nan, lng: 0))
    }

    // MARK: - La carte

    func testTheMapIsWideEnoughForTheWholeCircleAndNeverTighterThan400Metres() {
        XCTAssertEqual(PlaceMapLayout.visibleMeters(radiusM: 50), 400)
        XCTAssertEqual(PlaceMapLayout.visibleMeters(radiusM: 100), 480)
        XCTAssertEqual(PlaceMapLayout.visibleMeters(radiusM: 800), 3_840)
        for radius in PlaceRadius.values {
            XCTAssertGreaterThan(PlaceMapLayout.visibleMeters(radiusM: radius), Double(radius) * 2)
        }
    }

    // MARK: - L'adresse sur deux lignes

    func testTheAddressIsSplitAtTheFirstComma() {
        let lines = PlaceAddressLines.split("26 rue du Volnay, 21000 Dijon, France")

        XCTAssertEqual(lines.title, "26 rue du Volnay")
        XCTAssertEqual(lines.subtitle, "21000 Dijon, France")
    }

    func testAnAddressWithoutACommaOrWithAnEmptyFirstPartStaysWhole() {
        XCTAssertEqual(PlaceAddressLines.split("Paris").title, "Paris")
        XCTAssertEqual(PlaceAddressLines.split("Paris").subtitle, "")
        XCTAssertEqual(PlaceAddressLines.split(", Paris").title, ", Paris")
        XCTAssertEqual(PlaceAddressLines.split(", Paris").subtitle, "")
    }
}
