import MapKit
import UIKit
import XCTest
@testable import Miff

/// Le zoom de la carte d'un lieu EXÉCUTÉ (ticket `lieu-carte-zoom`) : les gestes sont installés, un double toucher et un pincement
/// changent le niveau dans les bornes, le lieu reste au centre, un changement de rayon ou de lieu remet le cadrage de départ, et le
/// défilement de la page est suspendu pendant un pincement puis rendu. Les doigts eux-mêmes ne peuvent pas être simulés : les trois
/// temps du pincement et les deux toucher sont appelés directement (c'est ce que les reconnaisseurs de gestes appellent).
final class PlaceMapCoordinatorTests: XCTestCase {
    private let place = CLLocationCoordinate2D(latitude: 47.322, longitude: 5.041)
    private let otherPlace = CLLocationCoordinate2D(latitude: 48.8566, longitude: 2.3522)

    private func makeMap(inside container: UIView? = nil) -> (map: PlaceMKMapView, coordinator: PlaceMapCoordinator) {
        let map = PlaceMKMapView(frame: CGRect(x: 0, y: 0, width: 390, height: 176))
        container?.addSubview(map)
        let coordinator = PlaceMapCoordinator()
        map.delegate = coordinator
        coordinator.map = map
        coordinator.installZoomGestures(on: map)
        coordinator.reframe(center: place, radiusM: 100)
        return (map, coordinator)
    }

    private func makeScroll() -> UIScrollView {
        UIScrollView(frame: CGRect(x: 0, y: 0, width: 390, height: 800))
    }

    // MARK: - Les gestes installés

    func testThePinchTheDoubleTapAndTheTwoFingerTapAreInstalledAndMapKitKeepsItsOwnZoomOff() {
        let map = PlaceMKMapView(frame: CGRect(x: 0, y: 0, width: 390, height: 176))
        let coordinator = PlaceMapCoordinator()
        let before = Set((map.gestureRecognizers ?? []).map { ObjectIdentifier($0) })

        coordinator.installZoomGestures(on: map)

        let added = (map.gestureRecognizers ?? []).filter { !before.contains(ObjectIdentifier($0)) }
        XCTAssertEqual(added.count, 3)
        XCTAssertEqual(added.compactMap { $0 as? UIPinchGestureRecognizer }.count, 1)
        let taps = added.compactMap { $0 as? UITapGestureRecognizer }
        XCTAssertEqual(taps.filter { $0.numberOfTapsRequired == 2 && $0.numberOfTouchesRequired == 1 }.count, 1)
        XCTAssertEqual(taps.filter { $0.numberOfTapsRequired == 1 && $0.numberOfTouchesRequired == 2 }.count, 1)
    }

    // MARK: - Le niveau

    func testTheMapStartsAtTheFrameOfTheRadiusWithoutAZoomOfThePerson() {
        let (_, coordinator) = makeMap()

        XCTAssertEqual(coordinator.level, PlaceMapLayout.visibleMeters(radiusM: 100))
        XCTAssertNil(coordinator.userLevel)
    }

    func testADoubleTapComesOneStepCloserAndATwoFingerTapOneStepFarther() {
        let (_, coordinator) = makeMap()
        let start = coordinator.level

        coordinator.doubleTapped()
        XCTAssertEqual(coordinator.level, start / 2)
        XCTAssertEqual(coordinator.userLevel, start / 2)

        coordinator.twoFingerTapped()
        XCTAssertEqual(coordinator.level, start)
    }

    func testRepeatedTapsStopAtTheBounds() {
        let (_, coordinator) = makeMap()

        for _ in 0..<20 {
            coordinator.doubleTapped()
        }
        XCTAssertEqual(coordinator.level, PlaceMapZoom.closestMeters)
        for _ in 0..<20 {
            coordinator.twoFingerTapped()
        }
        XCTAssertEqual(coordinator.level, PlaceMapZoom.farthestMeters(radiusM: 100))
    }

    func testAPinchFollowsTheFingersWithinTheBoundsAndTheMapStaysCentredOnThePlace() {
        let (map, coordinator) = makeMap()
        let start = coordinator.level

        coordinator.pinchBegan()
        coordinator.pinchChanged(scale: 2)
        XCTAssertEqual(coordinator.level, start / 2)
        XCTAssertEqual(map.centerCoordinate.latitude, place.latitude, accuracy: 0.001)
        XCTAssertEqual(map.centerCoordinate.longitude, place.longitude, accuracy: 0.001)
        coordinator.pinchChanged(scale: 1_000)
        XCTAssertEqual(coordinator.level, PlaceMapZoom.closestMeters)
        coordinator.pinchChanged(scale: 0.000_1)
        XCTAssertEqual(coordinator.level, PlaceMapZoom.farthestMeters(radiusM: 100))
        coordinator.pinchEnded()
    }

    // MARK: - Le rayon ou le lieu change (règle commune avec Android, Z7)

    func testTheFrameComesBackWhenTheRadiusChangesAfterAZoom() {
        let (_, coordinator) = makeMap()
        coordinator.pinchBegan()
        coordinator.pinchChanged(scale: 1.1)
        coordinator.pinchEnded()
        XCTAssertNotNil(coordinator.userLevel)

        coordinator.reframe(center: place, radiusM: 800)

        XCTAssertNil(coordinator.userLevel)
        XCTAssertEqual(coordinator.level, PlaceMapLayout.visibleMeters(radiusM: 800))
        XCTAssertGreaterThan(coordinator.level, 800 * 2, "le cercle de 800 m tient entier dans la carte")
    }

    func testTheFrameComesBackWhenThePlaceChangesAfterAZoom() {
        let (_, coordinator) = makeMap()
        coordinator.doubleTapped()

        coordinator.reframe(center: otherPlace, radiusM: 100)

        XCTAssertNil(coordinator.userLevel)
        XCTAssertEqual(coordinator.level, PlaceMapLayout.visibleMeters(radiusM: 100))
    }

    func testTheZoomIsKeptWhenOnlyTheTypeOrTheCategoryChanges() {
        let (_, coordinator) = makeMap()
        coordinator.doubleTapped()
        let zoomed = coordinator.level

        coordinator.reframe(center: place, radiusM: 100)

        XCTAssertEqual(coordinator.userLevel, zoomed)
        XCTAssertEqual(coordinator.level, zoomed)
    }

    // MARK: - Le défilement de la page pendant un pincement

    func testThePageStopsScrollingDuringAPinchAndGetsItBackWhenTheFingersLeave() {
        let scroll = makeScroll()
        let (_, coordinator) = makeMap(inside: scroll)
        XCTAssertTrue(scroll.isScrollEnabled)

        coordinator.pinchBegan()
        XCTAssertFalse(scroll.isScrollEnabled)
        coordinator.pinchChanged(scale: 1.5)
        XCTAssertFalse(scroll.isScrollEnabled)
        coordinator.pinchEnded()

        XCTAssertTrue(scroll.isScrollEnabled)
    }

    func testThePageIsFoundAboveSeveralLevelsOfViews() {
        let scroll = makeScroll()
        let inner = UIView()
        let deeper = UIView()
        scroll.addSubview(inner)
        inner.addSubview(deeper)
        let (_, coordinator) = makeMap(inside: deeper)

        coordinator.pinchBegan()
        XCTAssertFalse(scroll.isScrollEnabled)
        coordinator.pinchEnded()
        XCTAssertTrue(scroll.isScrollEnabled)
    }

    func testThePageGetsItsScrollBackIfTheCoordinatorDisappearsDuringAPinch() {
        let scroll = makeScroll()
        let map = PlaceMKMapView(frame: CGRect(x: 0, y: 0, width: 390, height: 176))
        scroll.addSubview(map)
        var coordinator: PlaceMapCoordinator? = PlaceMapCoordinator()
        coordinator?.map = map
        coordinator?.reframe(center: place, radiusM: 100)

        coordinator?.pinchBegan()
        XCTAssertFalse(scroll.isScrollEnabled)
        coordinator = nil

        XCTAssertTrue(scroll.isScrollEnabled, "un geste interrompu ne laisse jamais la page bloquée")
    }

    func testAMapWithoutAScrollingPageStillZooms() {
        let (_, coordinator) = makeMap()
        let start = coordinator.level

        coordinator.pinchBegan()
        coordinator.pinchChanged(scale: 2)
        coordinator.pinchEnded()

        XCTAssertEqual(coordinator.level, start / 2)
    }
}
