import XCTest
@testable import Miff

/// Le zoom de la carte du formulaire d'un lieu (ticket `lieu-carte-zoom`) : bornes, pas, cercle toujours lisible, rayon jamais touché,
/// et ce que les sources de la carte doivent garder (centre fixe, pas de bouton, pas de position de la personne, aucune clé).
final class PlaceMapZoomTests: XCTestCase {
    private let radii = [PlaceLimits.radiusMin, 100, 400, PlaceLimits.radiusMax]

    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Place
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    private func read(_ relative: String) throws -> String {
        let url = iosRoot.appendingPathComponent(relative)
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Fichier introuvable (\(url.path)).")
        }
        return try String(contentsOf: url, encoding: .utf8)
    }

    // MARK: - Le cadrage de départ et les bornes

    func testTheStartingFrameIsInsideTheBoundsForEveryRadiusOfTheSlider() {
        for radius in PlaceRadius.values {
            let start = PlaceMapLayout.visibleMeters(radiusM: radius)

            XCTAssertGreaterThanOrEqual(start, PlaceMapZoom.closestMeters)
            XCTAssertLessThanOrEqual(start, PlaceMapZoom.farthestMeters(radiusM: radius))
            XCTAssertEqual(PlaceMapZoom.clamped(start, radiusM: radius), start, "le départ est dans les bornes")
        }
    }

    func testTheBoundsAreOneHundredMetresAndElevenDiametersOfTheCircle() {
        XCTAssertEqual(PlaceMapZoom.closestMeters, 100)
        XCTAssertEqual(PlaceMapZoom.farthestMeters(radiusM: 50), 1_100)
        XCTAssertEqual(PlaceMapZoom.farthestMeters(radiusM: 100), 2_200)
        XCTAssertEqual(PlaceMapZoom.farthestMeters(radiusM: 800), 17_600)
    }

    func testAnyValueIsBroughtBackInsideTheBounds() {
        for radius in radii {
            XCTAssertEqual(PlaceMapZoom.clamped(1, radiusM: radius), PlaceMapZoom.closestMeters)
            let farthest = PlaceMapZoom.farthestMeters(radiusM: radius)
            XCTAssertEqual(PlaceMapZoom.clamped(1_000_000, radiusM: radius), farthest)
        }
    }

    func testAnAbsurdValueGivesTheStartingFrameBack() {
        let start = PlaceMapLayout.visibleMeters(radiusM: 100)

        for absurd in [0, -5, Double.nan, Double.infinity, -Double.infinity] {
            XCTAssertEqual(PlaceMapZoom.clamped(absurd, radiusM: 100), start)
        }
    }

    // MARK: - Le cercle reste lisible à tout zoom

    func testTheCircleKeepsAtLeastAnElevenththOfTheMapHeightAtTheFarthestZoom() {
        for radius in PlaceRadius.values {
            let farthest = PlaceMapZoom.farthestMeters(radiusM: radius)

            XCTAssertGreaterThanOrEqual(
                PlaceMapZoom.circleShare(radiusM: radius, visibleMeters: farthest),
                1.0 / PlaceMapZoom.farthestDiameters - 0.0001,
                "un cercle de \(radius) m doit rester visible au plus loin"
            )
        }
    }

    func testTheWholeCircleFitsAtTheStartingFrame() {
        for radius in PlaceRadius.values {
            let start = PlaceMapLayout.visibleMeters(radiusM: radius)
            let share = PlaceMapZoom.circleShare(radiusM: radius, visibleMeters: start)

            XCTAssertLessThan(share, 1, "au départ le cercle de \(radius) m tient entier")
        }
    }

    func testTheCircleShareIsZeroWhenNothingIsVisible() {
        XCTAssertEqual(PlaceMapZoom.circleShare(radiusM: 100, visibleMeters: 0), 0)
    }

    // MARK: - Pincer

    func testSpreadingTheFingersZoomsInAndPinchingZoomsOut() {
        let start = PlaceMapLayout.visibleMeters(radiusM: 100)

        XCTAssertEqual(PlaceMapZoom.pinched(from: start, scale: 2, radiusM: 100), start / 2)
        XCTAssertEqual(PlaceMapZoom.pinched(from: start, scale: 0.5, radiusM: 100), start * 2)
        XCTAssertEqual(PlaceMapZoom.pinched(from: start, scale: 1, radiusM: 100), start)
    }

    func testAPinchNeverGoesBeyondTheBounds() {
        XCTAssertEqual(PlaceMapZoom.pinched(from: 480, scale: 100, radiusM: 100), PlaceMapZoom.closestMeters)
        let farthest = PlaceMapZoom.farthestMeters(radiusM: 100)
        XCTAssertEqual(PlaceMapZoom.pinched(from: 480, scale: 0.001, radiusM: 100), farthest)
    }

    func testAPinchWithAnAbsurdScaleChangesNothing() {
        for scale in [0, -1, Double.nan, Double.infinity] {
            XCTAssertEqual(PlaceMapZoom.pinched(from: 480, scale: scale, radiusM: 100), 480)
        }
    }

    // MARK: - Double toucher et toucher à deux doigts

    func testADoubleTapHalvesTheLevelAndATwoFingerTapDoublesIt() {
        XCTAssertEqual(PlaceMapZoom.doubleTapped(from: 480, radiusM: 100), 240)
        XCTAssertEqual(PlaceMapZoom.twoFingerTapped(from: 480, radiusM: 100), 960)
    }

    func testTapsStopAtTheBoundsAndNeverGoBeyond() {
        var level = PlaceMapLayout.visibleMeters(radiusM: 50)
        for _ in 0..<20 {
            level = PlaceMapZoom.doubleTapped(from: level, radiusM: 50)
        }
        XCTAssertEqual(level, PlaceMapZoom.closestMeters)
        for _ in 0..<20 {
            level = PlaceMapZoom.twoFingerTapped(from: level, radiusM: 50)
        }
        XCTAssertEqual(level, PlaceMapZoom.farthestMeters(radiusM: 50))
    }

    // MARK: - Le curseur de rayon pendant un zoom

    func testWithoutAZoomOfThePersonTheFrameFollowsTheRadiusLikeBefore() {
        for radius in PlaceRadius.values {
            XCTAssertEqual(
                PlaceMapZoom.reframed(keeping: nil, radiusM: radius),
                PlaceMapLayout.visibleMeters(radiusM: radius)
            )
        }
    }

    func testAZoomOfThePersonIsKeptWhenTheRadiusChangesAndBroughtBackInsideTheNewBounds() {
        XCTAssertEqual(PlaceMapZoom.reframed(keeping: 300, radiusM: 100), 300)
        XCTAssertEqual(PlaceMapZoom.reframed(keeping: 300, radiusM: 800), 300)
        // Un cercle plus petit : le niveau très éloigné est ramené au plus loin permis pour lui.
        XCTAssertEqual(PlaceMapZoom.reframed(keeping: 10_000, radiusM: 50), PlaceMapZoom.farthestMeters(radiusM: 50))
    }

    func testTheRangeOfTheRadiusSliderIsUnchanged() {
        // Le rayon n'entre que comme borne : la plage du curseur est celle de toujours (50 à 800 m par pas de 50).
        XCTAssertEqual(PlaceLimits.radiusMin, 50)
        XCTAssertEqual(PlaceLimits.radiusMax, 800)
        XCTAssertEqual(PlaceRadius.values.count, 16)
    }

    // MARK: - Les sources de la carte

    func testTheMapKeepsItsCentreOnThePlaceAndAddsNoButtonNorPersonLocation() throws {
        let map = try read("Miff/Views/Place/PlaceMapView.swift")

        // Zoom et déplacement de MapKit éteints : le lieu ne peut pas glisser, le zoom est celui de PlaceMapZoom.
        XCTAssertTrue(map.contains("isZoomEnabled = false"))
        XCTAssertTrue(map.contains("isScrollEnabled = false"))
        XCTAssertFalse(map.contains("isZoomEnabled = true"))
        XCTAssertTrue(map.contains("UIPinchGestureRecognizer"))
        XCTAssertTrue(map.contains("numberOfTapsRequired = 2"))
        XCTAssertTrue(map.contains("numberOfTouchesRequired = 2"))
        // Le centre est toujours le lieu ; aucun geste de la carte ne le change.
        XCTAssertTrue(map.contains("MKCoordinateRegion(center: center"))
        XCTAssertFalse(map.contains("setCenter"))
        XCTAssertFalse(map.contains("regionDidChange"))
        // Pas de bouton + / − (choix du PM) ; pas de position de la personne.
        XCTAssertFalse(map.contains("MKUserTrackingButton"))
        XCTAssertFalse(map.contains("showsUserLocation = true"))
        XCTAssertFalse(map.contains("userTrackingMode"))
    }

    func testOnlyTheFormMakesTheMapZoomableNotTheDetail() throws {
        let form = try read("Miff/Views/Place/PlaceFormChoices.swift")
        let detail = try read("Miff/Views/Place/PlaceDetailView.swift")
        let block = try read("Miff/Views/Place/PlaceMapBlock.swift")

        XCTAssertTrue(form.contains("isZoomable: true"))
        XCTAssertFalse(detail.contains("isZoomable"))
        XCTAssertTrue(block.contains("var isZoomable = false"))
    }

    func testTheMapStillNeedsNoKeyNoPackageAndNoNetworkAddress() throws {
        let map = try read("Miff/Views/Place/PlaceMapView.swift")
        let zoom = try read("Miff/Models/Place/PlaceMapZoom.swift")
        let project = try read("project.yml")

        for source in [map, zoom] {
            XCTAssertFalse(source.contains("http"))
            XCTAssertFalse(source.contains("URLSession"))
        }
        XCTAssertFalse(map.contains("import GoogleMaps"))
        XCTAssertFalse(map.contains("import Mapbox"))
        XCTAssertFalse(project.lowercased().contains("googlemaps"))
        XCTAssertFalse(project.lowercased().contains("mapbox"))
    }
}
