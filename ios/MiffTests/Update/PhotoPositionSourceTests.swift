import XCTest
@testable import Miff

/// La mesure de la position d'une photo (décision D33 : au toucher de « Publier », une seule fois) : l'état des autorisations d'abord, puis UNE
/// position ; « localisation éteinte » et « signal introuvable » ne se disent pas pareil (risque R15). Et les petits assemblages de
/// `UpdateServices`.
@MainActor
final class PhotoPositionSourceTests: XCTestCase {
    private let source = FakePositionSource()

    private func measure(
        _ location: LocationAuthorization,
        services: Bool = true,
        precise: Bool = true
    ) async -> PhotoPosition {
        let snapshot = PermissionSnapshot(
            location: location, servicesEnabled: services, preciseLocation: precise, notifications: .authorized
        )
        return await TrackingPhotoPositionSource(position: source, permissions: { snapshot }).measure()
    }

    func testAnAllowedLocationGivesTheOnePositionOfTheBridge() async {
        source.fix = UpdateFixtures.fix()

        for location in [LocationAuthorization.always, .whenInUse] {
            let result = await measure(location)

            XCTAssertEqual(result, .found(UpdateFixtures.fix()), "\(location)")
        }
        XCTAssertEqual(source.calls, 2, "une mesure par appel, jamais une boucle")
    }

    func testAPositionTheSystemCannotGiveIsNotFoundASlowSignalNotAnOffLocation() async {
        source.fix = nil

        let result = await measure(.always)

        XCTAssertEqual(result, .notFound)
    }

    func testAPermissionTakenBackOrALocationOffOrApproximateMeansOffAndNothingIsMeasured() async {
        source.fix = UpdateFixtures.fix()
        var cases: [PhotoPosition] = []
        cases.append(await measure(.denied))
        cases.append(await measure(.restricted))
        cases.append(await measure(.notDetermined))
        cases.append(await measure(.always, services: false))
        cases.append(await measure(.always, precise: false))

        XCTAssertEqual(cases.count, 5)
        XCTAssertTrue(cases.allSatisfy { $0 == .locationOff })
        XCTAssertEqual(source.calls, 0, "aucune position n'est demandée sans l'autorisation")
    }

    func testAPhotoPositionShowsNothingOfItself() {
        XCTAssertEqual("\(PhotoPosition.found(UpdateFixtures.fix()))", "PhotoPosition.found(redacted)")
    }

    // MARK: - UpdateServices

    func testTheMiffsOfThePersonAreTheOnesTheDirectoryKnowsEmptyWhileItHasNotRead() {
        let ready = DirectoryReady(
            miffs: [MiffSummary(id: testMiffId, name: "A"), MiffSummary(id: testOtherMiffId, name: "B")],
            activeId: testMiffId, premium: true, fromCache: false
        )

        XCTAssertEqual(UpdateServices.miffIds(of: .ready(ready)), [testMiffId, testOtherMiffId])
        XCTAssertEqual(UpdateServices.miffIds(of: .loading), [])
        XCTAssertEqual(UpdateServices.miffIds(of: .unavailable), [])
    }
}
