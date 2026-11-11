import XCTest
@testable import Miff

/// « Position introuvable » à la publication d'une photo (recette TestFlight build 24) : quand le suivi n'a jamais pu écrire la position de la
/// personne (par exemple parce que sa propre mesure de 3 s n'a rien donné), l'Update lui REMET la position qu'il vient de mesurer ; le suivi
/// la publie lui-même, avec toutes ses garanties, et laisse un code de cause dans le journal local quand il ne la publie pas.
final class TrackingEngineHandOverTests: XCTestCase {
    private let rig = TrackingRig()

    func testWithoutAnyPositionTheTrackingNeverPublishesOneAndSaysWhy() async {
        rig.position.fix = nil

        await rig.engine.refresh(.sharingChanged)

        XCTAssertTrue(rig.store.positionWrites().isEmpty)
        XCTAssertTrue(rig.journal.kinds.contains(.positionUnavailable))
        XCTAssertNil(rig.local.state?.miffs[testMiffId]?.positionAtMillis, "rien n'est publié : l'Update le verra")
    }

    func testAPositionHandedOverByTheUpdatePublishesMineWithoutAskingTheSystemAgain() async {
        rig.position.fix = nil
        let handed = TrackingFixtures.fixHome(at: rig.env.now - 20 * 1000)

        await rig.engine.refresh(.sharingChanged, supplied: handed)

        XCTAssertEqual(rig.position.calls, 0, "la mesure de « Publier » est utilisée telle quelle")
        XCTAssertEqual(rig.store.positionWrites().count, 1)
        // La copie locale dit « publiée » : la photo peut partir.
        XCTAssertNotNil(rig.local.state?.miffs[testMiffId]?.positionAtMillis)
    }

    func testAHandedOverPositionOfMoreThanTenMinutesIsNotPublished() async {
        rig.position.fix = nil
        let old = TrackingFixtures.fixHome(at: rig.env.now - 11 * TrackingFixtures.minute)

        await rig.engine.refresh(.sharingChanged, supplied: old)

        XCTAssertTrue(rig.store.positionWrites().isEmpty)
    }

    func testAHandedOverPositionIsNeverPublishedWithoutAlwaysAndTheExactPosition() async {
        rig.env.snapshot = PermissionSnapshot(location: .always, preciseLocation: false, notifications: .authorized)

        await rig.engine.refresh(.sharingChanged, supplied: TrackingFixtures.fixHome(at: rig.env.now))

        XCTAssertTrue(rig.store.positionWrites().isEmpty)
    }

    func testAHandedOverPositionIsNeverPublishedWithoutTheConsentOfVersionThree() async {
        rig.store.consents[testMiffId] = SharingConsentRead(enabled: true, version: "2026-10-v2")

        await rig.engine.refresh(.sharingChanged, supplied: TrackingFixtures.fixHome(at: rig.env.now))

        XCTAssertTrue(rig.store.positionWrites().isEmpty)
        XCTAssertTrue(rig.journal.kinds.contains(.notEligible))
    }

    func testARefusedPositionWriteLeavesItsCodeInTheJournal() async {
        rig.store.commitFailure = firestoreError(FirestoreErrorNumber.permissionDenied)

        await rig.engine.refresh(.sharingChanged, supplied: TrackingFixtures.fixHome(at: rig.env.now))

        XCTAssertTrue(rig.journal.kinds.contains(.positionRefused))
        XCTAssertNil(rig.local.state?.miffs[testMiffId]?.positionAtMillis)
    }

    func testTheJournalKindsOfTheDiagnosisHoldNoCoordinateNorName() {
        let kinds: [JournalKind] = [
            .positionSkippedOffline, .positionSkippedSession, .positionRefused, .updatePositionNotPublished,
            .updateTrackingUnawareOfMiff, .updatePermissionBlocked, .updateMeasureFailed, .updateHandOverWorked,
            .updateHandOverFailed
        ]
        for kind in kinds {
            XCTAssertNotNil(kind.rawValue.range(of: "^[a-zA-Z]+$", options: .regularExpression), kind.rawValue)
        }
    }
}
