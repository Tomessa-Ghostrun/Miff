import XCTest
@testable import Miff

/// Le NETTOYAGE par l'auteur (ticket §6.4, décision D35) : une photo de plus de 24 h (date locale) est supprimée sans lecture du serveur ; sans
/// date (réinstallation), UN `get` de sa propre photo décide sur `expiresAt`, JAMAIS sur un refus. Miroir des cas de `UpdatePublisherTest.kt`.
@MainActor
final class PhotoCleanerTests: XCTestCase {
    private let rig = UpdateRig()
    private let hour = 60 * 60 * 1000

    private func mine(hasPhoto: Bool = true, lastUpdate: Int?) -> MyPhotoFacts {
        MyPhotoFacts(hasPhoto: hasPhoto, lastUpdateAtMillis: lastUpdate)
    }

    private var deletions: [DocWrite] {
        rig.store.writes.filter { $0.kind == .delete }
    }

    // MARK: - Avec une date locale : aucune lecture

    func testAPhotoOlderThan24HoursIsDeletedAndTheStatusSaysHasPhotoFalseWithNoReadingOfTheServer() async {
        rig.ledger.notePhotoSent(uid: updateUid, millis: rig.now - 24 * hour - 1)

        let removed = await rig.makeCleaner().cleanUp(mine(lastUpdate: rig.now - 25 * hour))

        XCTAssertTrue(removed)
        XCTAssertEqual(deletions.map { $0.path }, ["miffs/\(testMiffId)/photos/\(updateUid)"])
        let status = rig.store.writes.first { $0.path.contains("/status/") }
        XCTAssertEqual(status?.data["hasPhoto"], .bool(false))
        XCTAssertNil(status?.data["lastUpdateAt"])
        XCTAssertEqual(rig.store.lots.first?.requireConsent, false, "le nettoyage n'exige pas le consentement")
        XCTAssertEqual(rig.reader.calls, 0, "aucune lecture tant que la date locale existe")
        XCTAssertNil(rig.ledger.photoSentMillis(uid: updateUid), "la note est oubliée une fois la photo retirée")
    }

    func testAPhotoOfExactly24HoursIsNotYetCleanedUp() async {
        rig.ledger.notePhotoSent(uid: updateUid, millis: rig.now - 24 * hour)

        let removed = await rig.makeCleaner().cleanUp(mine(lastUpdate: rig.now - 24 * hour))

        XCTAssertFalse(removed)
        XCTAssertTrue(rig.store.lots.isEmpty)
    }

    func testTheCleanUpWaitsWhenOfflineWhenReplacedOrWhenItFailsTheNoteStaysForNextTime() async {
        rig.ledger.notePhotoSent(uid: updateUid, millis: rig.now - 30 * hour)
        rig.network.online = false
        let offline = await rig.makeCleaner().cleanUp(mine(lastUpdate: nil))
        rig.network.online = true
        rig.device.clearanceResult = .replaced
        let replaced = await rig.makeCleaner().cleanUp(mine(lastUpdate: nil))
        rig.device.clearanceResult = .cleared(deviceId: testDeviceId)
        rig.store.failures = [firestoreError(FirestoreErrorNumber.permissionDenied)]
        let refused = await rig.makeCleaner().cleanUp(mine(lastUpdate: nil))

        XCTAssertFalse(offline || replaced || refused)
        XCTAssertNotNil(rig.ledger.photoSentMillis(uid: updateUid), "la note reste : le prochain lancement réessaie")
        XCTAssertTrue(rig.store.writes.isEmpty)
    }

    func testASignOutDoesNotForgetThePhotoTheSameAccountSigningInAgainCleansItUp() async {
        rig.ledger.notePhotoSent(uid: updateUid, millis: rig.now - 30 * hour)
        rig.ledger.clear()

        let removed = await rig.makeCleaner().cleanUp(mine(lastUpdate: nil))

        XCTAssertTrue(removed)
    }

    func testTheNoteOfAnotherAccountIsIgnoredAPhotoOfSomeoneElseIsNeverCleanedUpByThisPhone() async {
        rig.ledger.notePhotoSent(uid: "uid-autre", millis: rig.now - 30 * hour)

        let removed = await rig.makeCleaner().cleanUp(mine(lastUpdate: nil))

        XCTAssertFalse(removed)
        XCTAssertTrue(rig.store.writes.isEmpty)
    }

    func testEveryMiffWhereIShareIsCleanedInLotsOfFive() async {
        rig.miffIds = (0..<7).map { String(format: "MiffNumber%010d", $0) }
        rig.ledger.notePhotoSent(uid: updateUid, millis: rig.now - 30 * hour)

        let removed = await rig.makeCleaner().cleanUp(mine(lastUpdate: nil))

        XCTAssertTrue(removed)
        XCTAssertEqual(rig.store.lots.map { $0.miffIds.count }, [5, 2])
        XCTAssertEqual(deletions.count, 7)
    }

    // MARK: - Sans date locale (D35)

    func testWithNoNoteAndNoPhotoInTheStatusOrNoDateNothingIsDone() async {
        let cleaner = rig.makeCleaner()

        let nothing = await cleaner.cleanUp(nil)
        let noPhoto = await cleaner.cleanUp(mine(hasPhoto: false, lastUpdate: rig.now - 30 * hour))
        let noDate = await cleaner.cleanUp(mine(lastUpdate: nil))

        XCTAssertFalse(nothing || noPhoto || noDate)
        XCTAssertEqual(rig.reader.calls, 0)
        XCTAssertTrue(rig.store.writes.isEmpty)
    }

    func testAfterAReinstallAStatusOlderThan25HoursMeansAnExpiredPhotoItIsDeletedWithNoReading() async {
        let removed = await rig.makeCleaner().cleanUp(mine(lastUpdate: rig.now - 26 * hour))

        XCTAssertTrue(removed)
        // Une photo de plus de 25 h n'est de toute façon pas lisible : la date de l'update le dit.
        XCTAssertEqual(rig.reader.calls, 0)
        XCTAssertEqual(deletions.count, 1)
    }

    func testWithNoDateAReadablePhotoGivesItsOwnDateOneReadNothingDeletedYet() async {
        rig.reader.result = .found(createdAtMillis: rig.now - 3 * hour, expiresAtMillis: rig.now + 20 * hour)

        let removed = await rig.makeCleaner().cleanUp(mine(lastUpdate: rig.now - 3 * hour))

        XCTAssertFalse(removed)
        XCTAssertEqual(rig.reader.calls, 1, "UN get de MA photo")
        // La date de la photo elle-même devient la note.
        XCTAssertEqual(rig.ledger.photoSentMillis(uid: updateUid), rig.now - 3 * hour)
        XCTAssertTrue(rig.store.writes.isEmpty)
    }

    func testWithNoDateARefusedReadNeverDeletesTheDateOfTheLastUpdateStandsIn() async {
        rig.reader.result = .unreadable

        let removed = await rig.makeCleaner().cleanUp(mine(lastUpdate: rig.now - 3 * hour))

        XCTAssertFalse(removed, "JAMAIS de suppression sur un refus des règles")
        XCTAssertEqual(rig.ledger.photoSentMillis(uid: updateUid), rig.now - 3 * hour)
        XCTAssertTrue(rig.store.writes.isEmpty)
    }

    func testWithNoDateAPhotoThatTheGetShowsExpiredIsDeleted() async {
        rig.reader.result = .found(createdAtMillis: rig.now - 25 * hour, expiresAtMillis: rig.now - hour)

        let removed = await rig.makeCleaner().cleanUp(mine(lastUpdate: rig.now - 24 * hour))

        XCTAssertTrue(removed)
        XCTAssertEqual(deletions.count, 1)
    }

    func testWithoutAReaderTheDateOfTheLastUpdateIsTheNoteAndNothingIsRead() async {
        let removed = await rig.makeCleaner(withReader: false).cleanUp(mine(lastUpdate: rig.now - 3 * hour))

        XCTAssertFalse(removed)
        XCTAssertEqual(rig.ledger.photoSentMillis(uid: updateUid), rig.now - 3 * hour)
    }
}
