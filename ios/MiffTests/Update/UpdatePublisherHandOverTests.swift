import XCTest
@testable import Miff

/// « Position introuvable » alors que la personne partage (recette TestFlight build 24) : quand le suivi n'a pas publié MA position dans une Miff,
/// l'Update mesure au toucher de « Publier » et REMET la mesure au suivi, qui la publie lui-même (l'Update n'écrit jamais `positions/{moi}`,
/// critère U8) ; la photo ne part que là où la position est ensuite publiée. Le journal de diagnostic ne porte que des codes.
@MainActor
final class UpdatePublisherHandOverTests: XCTestCase {
    private let rig = UpdateRig()

    private func photoWrites() -> [DocWrite] {
        rig.store.writes.filter { $0.path.contains("/photos/") }
    }

    /// Une boîte pour que le « suivi » du test dise « publiée » après la remise (une variable capturée par deux fermetures).
    private final class PublishedFlag {
        var on = false
    }

    func testAMissingPositionIsHandedToTheTrackingAfterTheMeasureAndNoPhotoGoesIfTheTrackingDoesNotPublishIt() async {
        rig.published = { _ in false }

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .positionNotFound)
        XCTAssertTrue(rig.store.writes.isEmpty)
        XCTAssertEqual(rig.position.calls, 1, "la position est mesurée au toucher de « Publier »")
        XCTAssertEqual(rig.handedOver.count, 1, "la mesure est remise au suivi, une fois")
        XCTAssertEqual(rig.journal.kinds, [.updatePositionNotPublished, .updateHandOverFailed])
    }

    func testWhenTheTrackingPublishesTheHandedPositionThePhotoGoesWithTheSamePlaceAndNeverAPositionDocument() async {
        let flag = PublishedFlag()
        rig.published = { _ in flag.on }
        rig.onHandOver = { _ in flag.on = true }

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .published(sharedIn: 1))
        XCTAssertEqual(rig.handedOver.first, UpdateFixtures.fix(), "le suivi reçoit la mesure de « Publier »")
        XCTAssertEqual(photoWrites()[0].data["lat"], .double(48.85661))
        // L'Update n'écrit jamais positions/{moi} (U8).
        XCTAssertTrue(rig.store.writes.allSatisfy { !$0.path.contains("/positions/") })
        XCTAssertTrue(rig.journal.kinds.contains(.updateHandOverWorked))
    }

    func testTheJournalSaysWhenTheTrackingDoesNotEvenKnowTheMiffCodesOnly() async {
        rig.published = { _ in false }
        rig.trackingKnows = { _ in false }

        _ = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertTrue(rig.journal.kinds.contains(.updateTrackingUnawareOfMiff))
        for line in rig.journal.recorded {
            XCTAssertNil(line.placeId, "aucun identifiant ni coordonnée dans le journal")
        }
    }

    func testNothingIsHandedToTheTrackingWhenThePositionIsAlreadyPublished() async {
        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .published(sharedIn: 1))
        XCTAssertTrue(rig.handedOver.isEmpty)
        XCTAssertTrue(rig.journal.kinds.isEmpty)
    }

    func testAMeasureThatFailsIsJournalledAndNothingIsHandedOver() async {
        rig.published = { _ in false }
        rig.position.result = .notFound

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .positionNotFound)
        XCTAssertTrue(rig.handedOver.isEmpty)
        XCTAssertTrue(rig.journal.kinds.contains(.updateMeasureFailed))
    }
}
