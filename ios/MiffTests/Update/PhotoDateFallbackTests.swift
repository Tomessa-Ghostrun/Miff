// Quelques assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// FEED-ORDRE-PHOTO, le repli : les règles qui connaissent `photoAt` sont déployées AVANT l'app, mais si elles ne l'étaient pas, la publication
/// d'une photo ne doit jamais être perdue. Un lot REFUSÉ (permission refusée) est rejoué UNE fois, identique mais sans `photoAt`. Le rejeu ne
/// contourne rien : il écrit un sous-ensemble de la première tentative, par la même transaction et les mêmes règles ; un vrai refus est refusé
/// de nouveau et c'est lui qui est rendu. Miroir des tests du repli d'Android.
@MainActor
final class PhotoDateFallbackTests: XCTestCase {
    private let rig = UpdateRig()

    private func denied() -> NSError {
        NSError(domain: FirestoreErrorNumber.domain, code: FirestoreErrorNumber.permissionDenied)
    }

    private func ids(_ count: Int) -> [String] {
        (0..<count).map { String(format: "MiffNumber%010d", $0) }
    }

    private func statusWrites() -> [DocWrite] {
        rig.store.writes.filter { $0.path.contains("/status/") }
    }

    private func photoWrites() -> [DocWrite] {
        rig.store.writes.filter { $0.path.contains("/photos/") }
    }

    func testWithTheRulesDeployedTheStatusCarriesPhotoAtAndNothingIsReplayed() async {
        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .published(sharedIn: 1))
        XCTAssertEqual(rig.store.lots.count, 1, "une seule tentative")
        XCTAssertEqual(statusWrites().first?.data["photoAt"], .serverTimestamp)
        XCTAssertTrue(rig.journal.kinds.isEmpty)
    }

    func testRulesNotDeployedYetTheLotIsReplayedOnceWithoutPhotoAtAndThePhotoIsNotLost() async {
        rig.store.failures = [denied(), nil]

        let result = await rig.makePublisher().publish(UpdateFixtures.photo(), description: "Coucou")

        XCTAssertEqual(result, .published(sharedIn: 1), "la photo est publiée")
        XCTAssertEqual(rig.store.lots.count, 2, "la tentative refusée, puis UN rejeu")
        XCTAssertEqual(rig.store.lots[0].miffIds, rig.store.lots[1].miffIds, "le même lot")
        XCTAssertEqual(rig.store.lots[0].requireConsent, rig.store.lots[1].requireConsent, "le même consentement exigé")
        XCTAssertEqual(photoWrites().count, 1)
        XCTAssertEqual(photoWrites().first?.data["description"], .string("Coucou"), "la photo et sa description sont intactes")
        XCTAssertEqual(statusWrites().count, 1)
        XCTAssertNil(statusWrites().first?.data["photoAt"], "le rejeu n'écrit pas la date")
        XCTAssertEqual(statusWrites().first?.data["hasPhoto"], .bool(true))
        XCTAssertEqual(statusWrites().first?.data["deviceId"], .string(testDeviceId), "la session est toujours là")
        XCTAssertEqual(rig.journal.kinds, [.updatePhotoDateRefused])
        XCTAssertEqual(rig.ledger.countToday(), 1, "UNE publication comptée, pas deux")
    }

    func testARealRefusalIsRefusedTwiceAndTheSecondAnswerIsTheOneGivenNothingIsWritten() async {
        rig.store.failures = [denied(), denied()]

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .refused)
        XCTAssertEqual(rig.store.lots.count, 2, "un seul rejeu, jamais une boucle")
        XCTAssertTrue(rig.store.writes.isEmpty)
        XCTAssertEqual(rig.ledger.countToday(), 0)
        XCTAssertNil(rig.ledger.photoSentMillis(uid: updateUid), "l'envoi connu pour ne pas être parti remet la note d'avant")
    }

    func testOnlyAPermissionRefusalIsReplayedNeverANetworkErrorNorADeadlineNorAnUnauthenticatedCall() async {
        let others: [Error] = [
            NSError(domain: FirestoreErrorNumber.domain, code: FirestoreErrorNumber.unavailable),
            NSError(domain: FirestoreErrorNumber.domain, code: FirestoreErrorNumber.unauthenticated),
            NSError(domain: FirestoreErrorNumber.domain, code: FirestoreErrorNumber.deadlineExceeded),
            NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet),
            NSError(domain: "autre", code: 1)
        ]
        for error in others {
            let fresh = UpdateRig()
            fresh.store.failures = [error, nil]

            _ = await fresh.makePublisher().publish(UpdateFixtures.photo())

            XCTAssertEqual(fresh.store.lots.count, 1, "\(error) n'est pas rejoué")
            XCTAssertTrue(fresh.store.writes.isEmpty, "\(error)")
        }
    }

    func testEachLotDecidesAloneAndTheNextLotTriesWithThePhotoDateAgain() async {
        rig.miffIds = ids(7)
        rig.store.failures = [denied(), nil, nil]

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .published(sharedIn: 7))
        XCTAssertEqual(rig.store.lots.map { $0.miffIds.count }, [5, 5, 2])
        let withDate = statusWrites().filter { $0.data["photoAt"] != nil }
        XCTAssertEqual(statusWrites().count, 7)
        XCTAssertEqual(withDate.count, 2, "les 5 premières Miffs sans date (repli), les 2 suivantes avec")
    }
}
// swiftlint:enable line_length
