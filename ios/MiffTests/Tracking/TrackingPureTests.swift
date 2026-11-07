import XCTest
@testable import Miff

/// La logique pure du suivi des lieux (LOC-4b) : géométrie, clés de zones, sélection des 20 zones, machine de présence, limites. Mêmes
/// cas qu'Android (LOC-2b) : ils sont le contrat entre les deux plateformes.
final class TrackingGeometryTests: XCTestCase {
    func testDistanceIsAboutOneMetreForOneHundredThousandthOfADegreeOfLatitude() {
        let metres = TrackingGeo.distanceMeters(lat1: 48.0, lng1: 2.0, lat2: 48.00001, lng2: 2.0)

        XCTAssertEqual(metres, 1.11, accuracy: 0.05)
    }

    func testDistanceIsZeroBetweenTheSamePointAndSymmetric() {
        XCTAssertEqual(TrackingGeo.distanceMeters(lat1: 48.85, lng1: 2.35, lat2: 48.85, lng2: 2.35), 0, accuracy: 0.001)
        let forward = TrackingGeo.distanceMeters(lat1: 48.85, lng1: 2.35, lat2: 45.76, lng2: 4.83)
        let backward = TrackingGeo.distanceMeters(lat1: 45.76, lng1: 4.83, lat2: 48.85, lng2: 2.35)
        XCTAssertEqual(forward, backward, accuracy: 0.001)
        // Paris - Lyon : environ 392 km.
        XCTAssertEqual(forward / 1000, 392, accuracy: 3)
    }

    func testRoundingKeepsFiveDecimalsAndNothingMore() {
        XCTAssertEqual(TrackingGeo.round5(48.856614), 48.85661)
        XCTAssertEqual(TrackingGeo.round5(2.352228), 2.35223)
        XCTAssertEqual(TrackingGeo.round5(-0.000004), 0)
        XCTAssertEqual(TrackingGeo.round5(-73.9857129), -73.98571)
    }

    func testEdgeDistanceIsZeroInsideAndOnTheEdge() {
        XCTAssertEqual(TrackingGeo.edgeDistanceMeters(distanceToCentre: 40, radiusM: 100), 0)
        XCTAssertEqual(TrackingGeo.edgeDistanceMeters(distanceToCentre: 100, radiusM: 100), 0)
        XCTAssertEqual(TrackingGeo.edgeDistanceMeters(distanceToCentre: 350, radiusM: 100), 250)
    }
}

final class ZoneKeyTests: XCTestCase {
    func testTheKeyNamesTheZoneByIdentifierAndGeometryAndType() {
        let home = TrackingFixtures.home
        let moved = PlaceFact(id: "p1", lat: home.lat + 0.001, lng: home.lng, radiusM: 100, type: .trusted)
        let bigger = PlaceFact(id: "p1", lat: home.lat, lng: home.lng, radiusM: 150, type: .trusted)
        let danger = PlaceFact(id: "p1", lat: home.lat, lng: home.lng, radiusM: 100, type: .risk)

        XCTAssertEqual(Set([home.key, moved.key, bigger.key, danger.key]).count, 4)
        let twin = PlaceFact(id: "other", lat: home.lat, lng: home.lng, radiusM: 100, type: .trusted)
        XCTAssertTrue(home.sameGeometry(twin))
        XCTAssertFalse(home.sameGeometry(moved))
    }

    func testAKeyWeMakeIsValidAndGivesBackTheIdentifier() {
        for place in [TrackingFixtures.home, TrackingFixtures.work, TrackingFixtures.danger] {
            XCTAssertTrue(ZoneKey.isValid(place.key), place.id)
            XCTAssertEqual(ZoneKey.placeId(place.key), place.id)
        }
        let tiny = PlaceFact(id: "AbCdEfGhIjKlMnOpQrSt", lat: 0.000005, lng: -0.00001, radiusM: 50, type: .risk)
        XCTAssertTrue(ZoneKey.isValid(tiny.key), tiny.key)
    }

    func testAnythingThatIsNotOurKeyIsRefused() {
        XCTAssertFalse(ZoneKey.isValid(""))
        XCTAssertFalse(ZoneKey.isValid("p1"))
        XCTAssertFalse(ZoneKey.isValid("p1|48.8|2.3|100|other"))
        XCTAssertFalse(ZoneKey.isValid("p1|48.8|2.3|100|trusted\nextra"))
        XCTAssertFalse(ZoneKey.isValid("p/1|48.8|2.3|100|trusted"))
        XCTAssertFalse(ZoneKey.isValid("p1|abc|2.3|100|trusted"))
        XCTAssertFalse(ZoneKey.isValid(String(repeating: "a", count: 120)))
    }

    func testEverythingThatCarriesACoordinateHidesItInItsDescription() {
        let place = TrackingFixtures.home
        let zone = TrackedZone(
            key: place.key,
            placeId: place.id,
            lat: place.lat,
            lng: place.lng,
            radiusM: 100,
            type: .trusted,
            miffIds: [testMiffId]
        )
        let texts = [
            "\(place)", "\(zone)", "\(TrackingFixtures.fixHome(at: 1))",
            "\(WrittenPosition(lat: place.lat, lng: place.lng, atMillis: 1))",
            "\(LocalPlace(fact: place))", "\(LocalMiff())", "\(LocalState(uid: "uid-1"))",
            "\(ZoneSelection.MiffPlace(miffId: testMiffId, place: place))"
        ]
        for text in texts {
            XCTAssertFalse(text.contains("48.8"), text)
            XCTAssertFalse(text.contains("2.35"), text)
            XCTAssertTrue(text.contains("redacted"), text)
        }
    }
}

final class ZoneSelectionTests: XCTestCase {
    private func zone(_ id: String, lat: Double, lng: Double, radius: Int = 100) -> TrackedZone {
        let fact = PlaceFact(id: id, lat: lat, lng: lng, radiusM: radius, type: .trusted)
        return TrackedZone(
            key: fact.key,
            placeId: id,
            lat: lat,
            lng: lng,
            radiusM: radius,
            type: .trusted,
            miffIds: [testMiffId]
        )
    }

    /// Vingt-deux lieux alignés vers l'est, un tous les 500 m environ.
    private func line() -> [TrackedZone] {
        (0..<22).map { index in zone("z\(index)", lat: 48.0, lng: 2.0 + Double(index) * 0.007) }
    }

    func testTheCopiesOfAPlaceInTwoMiffsAreOneZone() {
        let home = TrackingFixtures.home
        let merged = ZoneSelection.merge([
            ZoneSelection.MiffPlace(miffId: testMiffId, place: home),
            ZoneSelection.MiffPlace(miffId: testOtherMiffId, place: home)
        ])

        XCTAssertEqual(merged.count, 1)
        XCTAssertEqual(merged.first?.miffIds, [testMiffId, testOtherMiffId])
    }

    func testTwoDifferentPlacesWithTheSameFixedIdentifierAreTwoZones() {
        let mine = TrackingFixtures.home
        let theirs = PlaceFact(id: "p1", lat: 45.76, lng: 4.83, radiusM: 100, type: .trusted)
        let merged = ZoneSelection.merge([
            ZoneSelection.MiffPlace(miffId: testMiffId, place: mine),
            ZoneSelection.MiffPlace(miffId: testOtherMiffId, place: theirs)
        ])

        XCTAssertEqual(merged.count, 2)
    }

    func testTwentyOfTwentyTwoAreWatchedTheNearestOnes() {
        let zones = line()

        let chosen = ZoneSelection.select(lat: 48.0, lng: 2.0, zones: zones, insideKeys: [])

        XCTAssertEqual(chosen.count, ZoneSelection.maxWatched)
        let ids = Set(chosen.map { $0.placeId })
        XCTAssertTrue(ids.contains("z0"))
        XCTAssertTrue(ids.contains("z19"))
        XCTAssertFalse(ids.contains("z20"))
        XCTAssertFalse(ids.contains("z21"))
    }

    func testAZoneWhereThePersonIsStaysWatchedEvenWhenItsCentreIsFar() {
        var zones = line()
        // Une très grande zone dont le centre est loin (trente kilomètres) mais dont la personne est à l'intérieur.
        let huge = zone("huge", lat: 48.0, lng: 2.35, radius: 2000)
        zones.append(huge)

        let chosen = ZoneSelection.select(lat: 48.0, lng: 2.35, zones: zones, insideKeys: [huge.key])

        XCTAssertTrue(chosen.contains { $0.key == huge.key })
        XCTAssertEqual(chosen.count, ZoneSelection.maxWatched)
    }

    func testTheEdgeOfAZoneCountsNotItsCentre() {
        // A : centre à 600 m, rayon 500 m -> bord à 100 m. B : centre à 300 m, rayon 100 m -> bord à 200 m. A passe avant B.
        let farBig = zone("a", lat: 48.0, lng: 2.0 + 600 / 74_500, radius: 500)
        let nearSmall = zone("b", lat: 48.0, lng: 2.0 + 300 / 74_500, radius: 100)

        let chosen = ZoneSelection.select(lat: 48.0, lng: 2.0, zones: [nearSmall, farBig], insideKeys: [], limit: 1)

        XCTAssertEqual(chosen.first?.placeId, "a")
    }

    func testTheSameInputAlwaysGivesTheSameList() {
        let zones = line()
        let first = ZoneSelection.select(lat: 48.0, lng: 2.05, zones: zones, insideKeys: [])
        let second = ZoneSelection.select(lat: 48.0, lng: 2.05, zones: zones.reversed(), insideKeys: [])

        XCTAssertEqual(first.map { $0.key }, second.map { $0.key })
    }

    func testAPositionIsInsideOrOutsideAZoneByItsRadius() {
        let home = zone("h", lat: 48.0, lng: 2.0, radius: 100)
        let onEdge = 2.0 + 100 / 74_500.0

        XCTAssertTrue(ZoneSelection.contains(home, lat: 48.0, lng: 2.0))
        XCTAssertFalse(ZoneSelection.contains(home, lat: 48.0, lng: onEdge + 0.001))
    }
}
