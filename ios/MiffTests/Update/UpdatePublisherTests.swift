import XCTest
@testable import Miff

/// La PUBLICATION d'une update (ticket Update §5.7 ; décision du PM du 2026-11-06 « une photo doit être attachée à une position ») : chaque
/// garantie du fichier `UpdatePublisher.swift` est prouvée ici avec des doubles. Aucun réseau, aucune caméra. Miroir d'`UpdatePublisherTest.kt`.
@MainActor
final class UpdatePublisherTests: XCTestCase {
    private let rig = UpdateRig()

    private func ids(_ count: Int) -> [String] {
        (0..<count).map { String(format: "MiffNumber%010d", $0) }
    }

    private func photoWrites() -> [DocWrite] {
        rig.store.writes.filter { $0.path.contains("/photos/") }
    }

    private func statusWrites() -> [DocWrite] {
        rig.store.writes.filter { $0.path.contains("/status/") }
    }

    // MARK: - Le cas nominal

    func testPublishingWritesThePhotoAndTheStatusInOneLotWithThePlaceAndTheSession() async {
        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .published(sharedIn: 1))
        XCTAssertEqual(rig.store.lots.count, 1, "UNE transaction")
        XCTAssertTrue(rig.store.lots[0].requireConsent, "le consentement qui couvre la position est exigé")
        let expected = ["miffs/\(testMiffId)/photos/\(updateUid)", "miffs/\(testMiffId)/status/\(updateUid)"]
        XCTAssertEqual(rig.store.writes.map { $0.path }, expected)
        XCTAssertEqual(photoWrites()[0].data["lat"], .double(48.85661))
        XCTAssertEqual(statusWrites()[0].data["deviceId"], .string(testDeviceId))
        XCTAssertEqual(statusWrites()[0].data["hasPhoto"], .bool(true))
    }

    func testASuccessIsCountedAndTheDateOfTheSendingIsNoted() async {
        _ = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(rig.ledger.countToday(), 1)
        XCTAssertEqual(rig.ledger.lastPublishMillis(), rig.now)
        XCTAssertEqual(rig.ledger.photoSentMillis(uid: updateUid), rig.now)
    }

    func testEveryPhotoEverWrittenCarriesTheThreeKeysOfThePlaceTogetherNeverOneWithoutTheOthersNeverTheSession() async {
        rig.miffIds = ids(7)
        rig.store.failures = [nil, nil]

        _ = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(photoWrites().count, 7)
        for write in photoWrites() {
            XCTAssertEqual(Set(write.data.keys), ["jpeg", "createdAt", "expiresAt", "lat", "lng", "measuredAt"])
            XCTAssertEqual(write.kind, .set)
        }
    }

    func testTheStatusAndThePhotoAreAlwaysInTheSameCommitNeverOneAlone() async {
        rig.miffIds = ids(3)

        _ = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(photoWrites().count, statusWrites().count)
        XCTAssertEqual(rig.store.lots.count, 1)
    }

    func testTheLotsAreOfFiveMiffsAtMost() async {
        rig.miffIds = ids(12)

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .published(sharedIn: 12))
        XCTAssertEqual(rig.store.lots.map { $0.miffIds.count }, [5, 5, 2])
    }

    // MARK: - Pas de photo sans position récente

    func testWithoutAPositionNothingIsPublishedThePhotoStaysTheSecondTryMeasuresAgain() async {
        rig.position.result = .notFound
        let publisher = rig.makePublisher()

        let first = await publisher.publish(UpdateFixtures.photo())
        let second = await publisher.publish(UpdateFixtures.photo())

        XCTAssertEqual(first, .positionNotFound)
        XCTAssertEqual(second, .positionNotFound)
        XCTAssertTrue(rig.store.writes.isEmpty)
        XCTAssertEqual(rig.position.calls, 2, "chaque essai mesure de nouveau")
        XCTAssertEqual(rig.ledger.countToday(), 0)
        XCTAssertNil(rig.ledger.photoSentMillis(uid: updateUid))
    }

    func testAPositionOutOfRangeIsNotUsedTheRulesWouldRefuseTheWholePhoto() async {
        rig.position.result = .found(Fix(lat: 95, lng: 2, measuredAtMillis: rig.now))

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .positionNotFound)
        XCTAssertTrue(rig.store.writes.isEmpty)
    }

    func testAMeasureOlderThanTenMinutesIsNotUsed() async {
        rig.position.result = .found(Fix(lat: 48, lng: 2, measuredAtMillis: rig.now - 11 * 60 * 1000))

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .positionNotFound)
        XCTAssertTrue(rig.store.writes.isEmpty)
    }

    func testThePositionIsMeasuredOnceAtThePressOfPublierAndNotWhenSomethingElseStopsTheWay() async {
        // Chaque refus qui précède la mesure ne mesure RIEN : personne n'est localisé pour une photo jamais publiée (D33).
        let stale = UpdateFixtures.photo(takenAt: rig.now - 6 * 60 * 1000)
        let publisher = rig.makePublisher()
        let staleResult = await publisher.publish(stale)
        rig.network.online = false
        let offline = await publisher.publish(UpdateFixtures.photo())
        rig.network.online = true
        rig.miffIds = []
        let nobody = await publisher.publish(UpdateFixtures.photo())

        XCTAssertEqual(staleResult, .stale)
        XCTAssertEqual(offline, .offline)
        XCTAssertEqual(nobody, .sharingStopped)
        XCTAssertEqual(rig.position.calls, 0)
        rig.miffIds = [testMiffId]

        _ = await publisher.publish(UpdateFixtures.photo())

        XCTAssertEqual(rig.position.calls, 1)
    }

    func testAPositionThatIsNotPublishedInAMiffGetsNoPhotoThereAndTheDetectionIsAsked() async {
        rig.published = { _ in false }

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .positionNotFound)
        XCTAssertTrue(rig.store.writes.isEmpty)
        XCTAssertEqual(rig.position.calls, 0, "rien n'est mesuré")
        XCTAssertEqual(rig.positionMissingCalls, 1)
    }

    func testWithTwoMiffsThePhotoOnlyGoesWhereMyPositionIsPublishedTheOtherIsLeftForTheNextTry() async {
        rig.miffIds = ids(2)
        let first = ids(2)[0]
        rig.published = { $0 == first }

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .partial(sharedIn: 1, total: 2, remaining: [ids(2)[1]]))
        XCTAssertEqual(photoWrites().map { $0.path }, ["miffs/\(first)/photos/\(updateUid)"])
    }

    func testALocationThatIsOffOrNotAllowedSaysSoNotTheSentenceOfASlowSignalNothingIsWritten() async {
        rig.position.result = .locationOff

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .locationOff)
        XCTAssertTrue(rig.store.writes.isEmpty)
        XCTAssertNil(rig.ledger.photoSentMillis(uid: updateUid))
    }

    // MARK: - Hors ligne, session, garde-fou

    func testOfflineNothingIsWrittenNorQueuedThePhotoStaysOnTheScreen() async {
        rig.network.online = false

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .offline)
        XCTAssertTrue(rig.store.lots.isEmpty, "aucune transaction même tentée : rien ne peut partir plus tard")
        XCTAssertEqual(rig.ledger.countToday(), 0)
    }

    func testAServerThatDoesNotAnswerIsUnconfirmedNotNotSentTheCountIsNotMadeButTheDateOfTheSendingIsKept() async {
        rig.store.failures = [OperationTimedOut()]

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .unconfirmed)
        XCTAssertEqual(rig.ledger.countToday(), 0)
        // Elle a PEUT-ÊTRE été envoyée : le nettoyage la retrouvera.
        XCTAssertEqual(rig.ledger.photoSentMillis(uid: updateUid), rig.now)
    }

    func testADeadlineOfTheServerIsUnconfirmedToo() async {
        rig.store.failures = [firestoreError(FirestoreErrorNumber.deadlineExceeded)]

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .unconfirmed)
    }

    func testTheDateOfThePhotoIsNotedBeforeTheSendingNotAfterTheSuccess() async {
        // Le double lit la note AU MOMENT de la transaction : elle y est déjà.
        var noteDuringSending: Int?
        let ledger = rig.ledger
        let sniffer = SniffingStore(inner: rig.store) { noteDuringSending = ledger.photoSentMillis(uid: updateUid) }
        var dependencies = UpdatePublisherDependencies(
            store: sniffer, device: rig.device, position: rig.position, network: rig.network, ledger: rig.ledger,
            environment: rig.environment
        )
        dependencies.timeoutSeconds = 5

        _ = await UpdatePublisher(dependencies).publish(UpdateFixtures.photo())

        XCTAssertEqual(noteDuringSending, rig.now)
    }

    func testASendingKnownNotToHaveLeftPutsThePreviousNoteBack() async {
        rig.ledger.notePhotoSent(uid: updateUid, millis: 5)
        rig.store.failures = [firestoreError(FirestoreErrorNumber.permissionDenied)]

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .refused)
        XCTAssertEqual(rig.ledger.photoSentMillis(uid: updateUid), 5)
    }

    func testARefusalOfTheRulesIsRefusedANetworkErrorIsOfflineAnythingElseIsFailedNothingIsCounted() async {
        let cases: [(Error, PublishResult)] = [
            (firestoreError(FirestoreErrorNumber.permissionDenied), .refused),
            (NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet), .offline),
            (firestoreError(FirestoreErrorNumber.unavailable), .offline),
            (NSError(domain: "autre", code: 1), .failed)
        ]
        for (error, expected) in cases {
            rig.store.failures = [error]
            let result = await rig.makePublisher().publish(UpdateFixtures.photo())

            XCTAssertEqual(result, expected, "\(error)")
        }
        XCTAssertEqual(rig.ledger.countToday(), 0)
        XCTAssertNil(rig.ledger.photoSentMillis(uid: updateUid))
    }

    func testAPhoneThatWasReplacedWritesNothingAndItIsAskedBeforeAnyWrite() async {
        rig.device.afterRefusalResult = .replaced

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .refused)
        XCTAssertEqual(rig.device.refusalChecks, 1, "la session est relue auprès du serveur avant l'écriture")
        XCTAssertTrue(rig.store.lots.isEmpty)
    }

    func testASessionThatCannotBeVerifiedIsTreatedAsNoConnection() async {
        rig.device.afterRefusalResult = .unverified

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .offline)
        XCTAssertTrue(rig.store.lots.isEmpty)
    }

    func testNoOneSignedInNothingIsPublished() async {
        rig.uid = nil

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .failed)
        XCTAssertTrue(rig.store.lots.isEmpty)
    }

    func testTheGuardRailRefusesTheStalePhotoTheTwentyFirstOfTheDayAndTheOneLessThan30SecondsAfter() async {
        let publisher = rig.makePublisher()
        let zone = TimeZone(secondsFromGMT: 0) ?? TimeZone.current
        let day = PublishGuard.dayKey(nowMillis: rig.now, timeZone: zone)

        rig.ledger.recordPublication(nowMillis: rig.now - 10_000, dayKey: day, count: 1)
        let tooSoon = await publisher.publish(UpdateFixtures.photo())
        rig.ledger.recordPublication(nowMillis: rig.now - 60_000, dayKey: day, count: 20)
        let tooMany = await publisher.publish(UpdateFixtures.photo())

        XCTAssertEqual(tooSoon, .tooSoon)
        XCTAssertEqual(tooMany, .tooMany)
        XCTAssertTrue(rig.store.writes.isEmpty)
        XCTAssertEqual(rig.position.calls, 0)
    }

    func testAPhotoOver150000BytesIsNeverSentAndTheAnswerIsCalm() async {
        let result = await rig.makePublisher().publish(UpdateFixtures.photo(bytes: 150_001))

        XCTAssertEqual(result, .failed)
        XCTAssertTrue(rig.store.writes.isEmpty)
    }

    // MARK: - Partage arrêté, consentement, plusieurs Miffs

    func testWithNoMiffWhereIShareTheAnswerIsSharingStoppedNothingIsMeasured() async {
        rig.miffIds = []

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .sharingStopped)
        XCTAssertEqual(rig.position.calls, 0)
    }

    func testWhenTheCardSaysIStoppedSharingNothingIsWrittenAndTheAnswerIsSharingStopped() async {
        rig.store.notSharing = [testMiffId]

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .sharingStopped)
        XCTAssertTrue(rig.store.writes.isEmpty)
        XCTAssertNil(rig.ledger.photoSentMillis(uid: updateUid), "rien n'est parti : pas de note")
    }

    func testWhenTheTextOfConsentDoesNotCoverThePositionNothingIsWrittenAndTheWayBackIsTheAgreement() async {
        rig.store.needsAgreement = [testMiffId]

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .needsAgreement)
        XCTAssertTrue(rig.store.writes.isEmpty)
    }

    func testWithTwoMiffsTheOneWhereIStoppedSharingIsSkippedTheOtherReceivesThePhoto() async {
        rig.miffIds = ids(2)
        rig.store.notSharing = [ids(2)[0]]

        let result = await rig.makePublisher().publish(UpdateFixtures.photo())

        XCTAssertEqual(result, .published(sharedIn: 1))
        XCTAssertEqual(photoWrites().map { $0.path }, ["miffs/\(ids(2)[1])/photos/\(updateUid)"])
    }

    func testWhenTheSecondLotFailsThePhotoStaysWhereItWentAndTheNewTryTargetsTheOthersOnly() async {
        rig.miffIds = ids(6)
        rig.store.failures = [nil, firestoreError(FirestoreErrorNumber.unavailable)]
        let publisher = rig.makePublisher()

        let first = await publisher.publish(UpdateFixtures.photo())

        XCTAssertEqual(first, .partial(sharedIn: 5, total: 6, remaining: [ids(6)[5]]))
        XCTAssertEqual(rig.ledger.countToday(), 1, "une publication réussie dans une partie du cercle est comptée")
        rig.now += 60_000
        let retry = await publisher.publish(UpdateFixtures.photo(takenAt: rig.now), only: [ids(6)[5]])

        XCTAssertEqual(retry, .published(sharedIn: 1))
        XCTAssertEqual(rig.store.lots.last?.miffIds, [ids(6)[5]])
    }
}

/// Une transaction qui regarde le carnet au moment où elle part, avant de passer la main à la vraie.
private final class SniffingStore: PhotoStore {
    private let inner: PhotoStore
    private let onCommit: () -> Void

    init(inner: PhotoStore, onCommit: @escaping () -> Void) {
        self.inner = inner
        self.onCommit = onCommit
    }

    func commit(
        uid: String,
        miffIds: [String],
        requireConsent: Bool,
        build: @escaping ([String]) -> [DocWrite]
    ) async throws -> PhotoCommit {
        onCommit()
        return try await inner.commit(uid: uid, miffIds: miffIds, requireConsent: requireConsent, build: build)
    }
}
