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

    func testAPermissionTakenBackOrALocationOffMeansOffAndNothingIsMeasured() async {
        source.fix = UpdateFixtures.fix()
        var cases: [PhotoPosition] = []
        cases.append(await measure(.denied))
        cases.append(await measure(.restricted))
        cases.append(await measure(.notDetermined))
        cases.append(await measure(.always, services: false))
        cases.append(await measure(.always, services: false, precise: false))

        XCTAssertEqual(cases.count, 5)
        XCTAssertTrue(cases.allSatisfy { $0 == .locationOff })
        XCTAssertEqual(source.calls, 0, "aucune position n'est demandée sans l'autorisation")
    }

    func testAnApproximateLocationIsSaidApartFromAnOffOneAndNothingIsMeasured() async {
        source.fix = UpdateFixtures.fix()

        for location in [LocationAuthorization.always, .whenInUse] {
            let result = await measure(location, precise: false)

            XCTAssertEqual(result, .approximate, "\(location)")
        }
        XCTAssertEqual(source.calls, 0, "aucune position n'est demandée avec une autorisation approximative")
    }

    func testTheMeasureWaitsLongerThanTheEngineAndFallsBackOnTheKeptPositionOfTheTracking() async {
        source.fix = nil
        let kept = UpdateFixtures.fix()
        let snapshot = PermissionSnapshot(location: .always, notifications: .authorized)
        let measuring = TrackingPhotoPositionSource(
            position: source, permissions: { snapshot }, keptFix: { kept }
        )

        let result = await measuring.measure()

        // La position gardée par le suivi ; son âge est jugé ensuite (10 minutes au plus).
        XCTAssertEqual(result, .found(kept))
        XCTAssertEqual(source.waits, [TrackingLimits.photoPositionWaitSeconds])
        XCTAssertGreaterThan(TrackingLimits.photoPositionWaitSeconds, TrackingLimits.positionWaitSeconds)
    }

    func testWithoutAnyPositionNorKeptOneItIsNotFound() async {
        source.fix = nil

        let result = await measure(.always)

        XCTAssertEqual(result, .notFound)
    }

    func testTheBlockerOnlyReadsThePermissionsAndWhenInUseIsAnObstacleOnlyForAPublishedPosition() async {
        func blocker(
            _ location: LocationAuthorization, precise: Bool = true, background: Bool
        ) async -> PhotoPosition? {
            let snapshot = PermissionSnapshot(
                location: location, servicesEnabled: true, preciseLocation: precise, notifications: .authorized
            )
            return await TrackingPhotoPositionSource(position: source, permissions: { snapshot })
                .blocker(needsBackground: background)
        }

        let whenInUseForMeasure = await blocker(.whenInUse, background: false)
        let whenInUseForSuivi = await blocker(.whenInUse, background: true)
        let alwaysOk = await blocker(.always, background: true)
        let alwaysApproximate = await blocker(.always, precise: false, background: true)
        let denied = await blocker(.denied, background: true)

        XCTAssertNil(whenInUseForMeasure)
        XCTAssertEqual(whenInUseForSuivi, .locationOff)
        XCTAssertNil(alwaysOk)
        XCTAssertEqual(alwaysApproximate, .approximate)
        XCTAssertEqual(denied, .locationOff)
        XCTAssertEqual(source.calls, 0, "aucune position n'est lue")
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

    func testTheKeptPositionIsOnlyTheOneOfThisAccountAndOfLessThanTenMinutes() {
        let now = TrackingFixtures.noon
        let store = MemoryTrackingLocalStore()
        var state = LocalState(uid: "me")
        state.fix = TrackingFixtures.fixHome(at: now - 9 * TrackingFixtures.minute)
        store.state = state

        let own = UpdateServices.keptFix(uid: "me", local: store, nowMillis: now)
        let other = UpdateServices.keptFix(uid: "someone-else", local: store, nowMillis: now)
        let signedOut = UpdateServices.keptFix(uid: nil, local: store, nowMillis: now)
        let tooOld = UpdateServices.keptFix(uid: "me", local: store, nowMillis: now + 2 * TrackingFixtures.minute)

        XCTAssertEqual(own, TrackingFixtures.fixHome(at: now - 9 * TrackingFixtures.minute))
        XCTAssertNil(other, "la copie d'un autre compte ne sert jamais")
        XCTAssertNil(signedOut)
        XCTAssertNil(tooOld, "plus de 10 minutes : refusée")
    }

    func testNoKeptPositionMeansNil() {
        let store = MemoryTrackingLocalStore()

        XCTAssertNil(UpdateServices.keptFix(uid: "me", local: store, nowMillis: TrackingFixtures.noon))
        store.state = LocalState(uid: "me")
        XCTAssertNil(UpdateServices.keptFix(uid: "me", local: store, nowMillis: TrackingFixtures.noon))
    }
}
