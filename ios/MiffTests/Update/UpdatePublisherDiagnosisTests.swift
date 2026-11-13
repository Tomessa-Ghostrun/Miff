import XCTest
@testable import Miff

/// Le classement des échecs de la publication d'une photo et son diagnostic (recette TestFlight, 2026-11-11 : « Pas de connexion » alors que le
/// téléphone avait du réseau). Un refus des règles n'est JAMAIS présenté comme « hors connexion » ; chaque cause de « pas de connexion » laisse
/// un CODE dans le journal local de diagnostic (jamais un message, un chemin ni un identifiant).
@MainActor
final class UpdatePublisherDiagnosisTests: XCTestCase {
    private let rig = UpdateRig()

    private func error(_ code: Int) -> NSError {
        NSError(domain: FirestoreErrorNumber.domain, code: code)
    }

    func testARefusedWriteIsNeverPresentedAsNoConnection() async {
        rig.store.failures = [error(FirestoreErrorNumber.permissionDenied)]

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .refused)
        XCTAssertNotEqual(PreviewNotice(result: result).textKey, PreviewNotice.offline.textKey)
        XCTAssertEqual(rig.journal.kinds, [.updateErrorRefused])
    }

    func testAnUnauthenticatedWriteIsRefusedToo() async {
        rig.store.failures = [error(FirestoreErrorNumber.unauthenticated)]

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .refused)
    }

    func testEachNoConnectionCauseLeavesItsOwnCodeOnly() async {
        let cases: [(Int, JournalKind)] = [
            (FirestoreErrorNumber.unavailable, .updateErrorUnavailable),
            (FirestoreErrorNumber.cancelled, .updateErrorCancelled),
            (FirestoreErrorNumber.resourceExhausted, .updateErrorQuota)
        ]
        for (code, kind) in cases {
            let fresh = UpdateRig()
            fresh.store.failures = [error(code)]

            let result = await fresh.makePublisher().publish(UpdateFixtures.photo())

            XCTAssertEqual(result, .offline, "\(code)")
            XCTAssertEqual(fresh.journal.kinds, [kind], "\(code)")
        }
    }

    func testAUrlErrorIsNoConnectionWithItsOwnCode() async {
        rig.store.failures = [NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet)]

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .offline)
        XCTAssertEqual(rig.journal.kinds, [.updateErrorNetworkUrl])
    }

    func testAnUnknownErrorIsAGenericFailureNotNoConnection() async {
        rig.store.failures = [error(5)]

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .failed)
        XCTAssertEqual(rig.journal.kinds, [.updateErrorOther])
    }

    func testNoNetworkAtAllIsCodedAndNothingIsSent() async {
        rig.network.online = false

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .offline)
        XCTAssertEqual(rig.journal.kinds, [.updateOfflineNoNetwork])
        XCTAssertTrue(rig.store.lots.isEmpty)
    }

    func testASessionThatCannotBeVerifiedIsCodedApartFromTheNetwork() async {
        rig.device.afterRefusalResult = .unverified

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .offline)
        XCTAssertEqual(rig.journal.kinds, [.updateOfflineSessionUnverified])
        XCTAssertTrue(rig.store.lots.isEmpty)
    }

    func testNoConfirmedSessionIdentifierIsCodedToo() async {
        rig.device.clearanceResult = .unverified

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .offline)
        XCTAssertEqual(rig.journal.kinds, [.updateOfflineNoClearance])
    }

    func testAStoppedSharingInTheMiffIsSaidSoNotAsNoConnection() async {
        rig.store.notSharing = [testMiffId]

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .sharingStopped)
        XCTAssertTrue(rig.journal.kinds.isEmpty)
    }

    func testTheDiagnosticCodesHoldNothingButPlainWords() {
        let kinds: [JournalKind] = [
            .updateOfflineNoNetwork, .updateOfflineSessionUnverified, .updateOfflineNoClearance,
            .updateErrorUnavailable, .updateErrorCancelled, .updateErrorQuota, .updateErrorTimedOut,
            .updateErrorNetworkUrl, .updateErrorRefused, .updateErrorOther, .statusAfterResume
        ]
        for kind in kinds {
            XCTAssertNotNil(kind.rawValue.range(of: "^[a-zA-Z]+$", options: .regularExpression), kind.rawValue)
        }
    }
}
