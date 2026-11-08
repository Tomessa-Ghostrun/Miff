import XCTest
@testable import Miff

/// Les règles pures de l'Update (UPDATE-3) : le lieu d'une photo, les écritures (contrat avec backend/firestore.rules), le garde-fou des
/// publications, l'accès à la caméra, les états de l'écran. Miroir d'`UpdateRulesTest.kt` d'Android.
final class UpdateRulesTests: XCTestCase {
    private let now = UpdateFixtures.noon
    private let minute = 60 * 1000
    private let zone = TimeZone(secondsFromGMT: 0) ?? TimeZone.current

    private func makePlace() throws -> PhotoPlace {
        try XCTUnwrap(PhotoPlace.from(UpdateFixtures.fix(), nowMillis: now))
    }

    private func place(_ fix: Fix?) -> PhotoPlace? {
        PhotoPlace.from(fix, nowMillis: now)
    }

    private func photoWrite(
        _ photo: UpdatePhoto = UpdateFixtures.photo(),
        miff: String = testMiffId,
        uid: String = updateUid
    ) throws -> DocWrite? {
        UpdateWrites.photo(miffId: miff, uid: uid, photo: photo, place: try makePlace(), nowMillis: now)
    }

    private func publishWrites(deviceId: String = testDeviceId, battery: Int? = 80) throws -> [DocWrite] {
        let context = PublishContext(nowMillis: now, batteryPct: battery, deviceId: deviceId)
        let place = try makePlace()
        return UpdateWrites.publish(
            miffId: testMiffId, uid: updateUid, photo: UpdateFixtures.photo(), place: place, context: context
        )
    }

    private func removal(deviceId: String) -> [DocWrite] {
        UpdateWrites.removeExpired(miffId: testMiffId, uid: updateUid, batteryPct: 50, deviceId: deviceId)
    }

    private func verdict(day: String?, count: Int, last: Int?) -> PublishGuard.Verdict {
        PublishGuard.check(nowMillis: now, timeZone: zone, countDay: day, countToday: count, lastMillis: last)
    }

    // MARK: - Le lieu d'une photo

    func testARecentMeasureBecomesAPlaceWithFiveDecimalsAndTheTimeOfTheMeasure() throws {
        let fix = Fix(lat: 48.8566123456, lng: 2.3522198765, measuredAtMillis: now - 2 * minute)
        let found = try XCTUnwrap(place(fix))

        XCTAssertEqual(found.lat, 48.85661, accuracy: 0.0000001)
        XCTAssertEqual(found.lng, 2.35222, accuracy: 0.0000001)
        XCTAssertEqual(found.measuredAtMillis, now - 2 * minute)
    }

    func testNoMeasureNoPlaceAndNothingThatTheRulesWouldRefuse() {
        XCTAssertNil(place(nil))
        XCTAssertNil(place(Fix(lat: 91, lng: 0, measuredAtMillis: now)))
        XCTAssertNil(place(Fix(lat: 0, lng: 181, measuredAtMillis: now)))
        XCTAssertNil(place(Fix(lat: Double.nan, lng: 0, measuredAtMillis: now)))
        XCTAssertNil(place(Fix(lat: 0, lng: Double.infinity, measuredAtMillis: now)))
        // Plus vieille que 10 minutes : la personne a bougé depuis.
        XCTAssertNil(place(Fix(lat: 48, lng: 2, measuredAtMillis: now - 10 * minute - 1)))
        XCTAssertNotNil(place(Fix(lat: 48, lng: 2, measuredAtMillis: now - 10 * minute)))
    }

    func testAMeasureInTheFutureAWrongClockIsCappedAtNowNeverAfterThePhoto() throws {
        let found = try XCTUnwrap(place(Fix(lat: 48, lng: 2, measuredAtMillis: now + 3 * minute)))

        XCTAssertEqual(found.measuredAtMillis, now)
    }

    func testAPlaceShowsNothingOfItself() throws {
        let found = try makePlace()

        XCTAssertEqual("\(found)", "PhotoPlace(redacted)")
        XCTAssertFalse("\(found)".contains("48"))
    }

    // MARK: - Les écritures (contrat avec les règles)

    func testThePhotoHasExactlyTheKeysOfTheRulesTheThreeOfThePlaceAlwaysTogetherAndNoDeviceId() throws {
        let write = try XCTUnwrap(photoWrite())

        XCTAssertEqual(write.path, "miffs/\(testMiffId)/photos/\(updateUid)")
        XCTAssertEqual(write.kind, .set, "un set : la nouvelle photo REMPLACE l'ancienne")
        XCTAssertEqual(Set(write.data.keys), ["jpeg", "createdAt", "expiresAt", "lat", "lng", "measuredAt"])
        XCTAssertNil(write.data["deviceId"], "les règles refusent deviceId sur la photo")
    }

    func testTheTimesOfThePhotoAreTheServersForCreatedAtAndThePhonesPlus23HoursForExpiresAt() throws {
        let write = try XCTUnwrap(photoWrite())

        XCTAssertEqual(write.data["createdAt"], .serverTimestamp)
        XCTAssertEqual(write.data["expiresAt"], .timestamp(millis: now + 23 * 60 * 60 * 1000))
        XCTAssertLessThan(UpdateWrites.photoLifetimeMillis, 25 * 60 * 60 * 1000, "les règles refusent plus de 25 h")
        XCTAssertEqual(write.data["lat"], .double(48.85661))
        XCTAssertEqual(write.data["lng"], .double(2.35222))
        XCTAssertEqual(write.data["measuredAt"], .timestamp(millis: now))
        XCTAssertEqual(write.data["jpeg"], .bytes(UpdateFixtures.photo().jpeg))
    }

    func testAPhotoThatIsEmptyOrTooBigOrForAnInvalidPathIsNeverBuilt() throws {
        let empty = UpdatePhoto(jpeg: Data(), width: 1, height: 1, takenAtMillis: now)

        XCTAssertNil(try photoWrite(UpdateFixtures.photo(bytes: 150_001)))
        XCTAssertNotNil(try photoWrite(UpdateFixtures.photo(bytes: 150_000)))
        XCTAssertNil(try photoWrite(empty))
        XCTAssertNil(try photoWrite(miff: "../x"))
        XCTAssertNil(try photoWrite(uid: "a/b"))
    }

    func testPublishingWritesThePhotoAndTheStatusTogetherTheStatusSaysHasPhotoAndCarriesTheSession() throws {
        let writes = try publishWrites()

        let expected = ["miffs/\(testMiffId)/photos/\(updateUid)", "miffs/\(testMiffId)/status/\(updateUid)"]
        XCTAssertEqual(writes.map { $0.path }, expected)
        let status = writes[1]
        XCTAssertEqual(status.kind, .update)
        XCTAssertEqual(status.data["hasPhoto"], .bool(true))
        XCTAssertEqual(status.data["lastUpdateAt"], .serverTimestamp)
        XCTAssertEqual(status.data["updateText"], .null)
        XCTAssertEqual(status.data["batteryPct"], .integer(80))
        XCTAssertEqual(status.data["updatedAt"], .serverTimestamp)
        XCTAssertEqual(status.data["deviceId"], .string(testDeviceId))
        XCTAssertTrue(SessionWriteGuard.isSendable(writes))
    }

    func testTheStatusOfAnUpdateNeverTouchesThePresenceNorCarriesACoordinate() throws {
        let status = try publishWrites(battery: nil)[1]

        for forbidden in ["presence", "placeId", "presenceAt", "locationAccess", "lat", "lng", "measuredAt", "jpeg"] {
            XCTAssertNil(status.data[forbidden], forbidden)
        }
        XCTAssertEqual(status.data["batteryPct"], .null, "une batterie inconnue est nulle")
    }

    func testThePublicationNeverWritesThePositionDocument() throws {
        let cleanup = removal(deviceId: testDeviceId)

        XCTAssertFalse((try publishWrites() + cleanup).contains { $0.path.contains("/positions/") })
    }

    func testTheCleanUpOfAnExpiredPhotoDeletesItAndSaysHasPhotoFalseWithoutTouchingTheLastUpdate() {
        let writes = removal(deviceId: testDeviceId)

        XCTAssertEqual(writes.map { $0.kind }, [.delete, .update])
        XCTAssertEqual(writes[0].path, "miffs/\(testMiffId)/photos/\(updateUid)")
        XCTAssertEqual(writes[1].data["hasPhoto"], .bool(false))
        XCTAssertNil(writes[1].data["lastUpdateAt"])
        XCTAssertNil(writes[1].data["updateText"])
        XCTAssertEqual(writes[1].data["deviceId"], .string(testDeviceId))
    }

    func testAStatusCannotBeBuiltWithoutAWellFormedSession() throws {
        XCTAssertTrue(try publishWrites(deviceId: "court").isEmpty)
        XCTAssertTrue(removal(deviceId: "").isEmpty)
        let status = UpdateWrites.photoStatus(
            miffId: testMiffId, uid: updateUid, photoPublished: true, batteryPct: 1, deviceId: "x"
        )
        XCTAssertNil(status)
    }

    // MARK: - Le garde-fou des publications

    func testTheTwentyFirstPublicationOfTheDayIsRefusedTheTwentiethIsNot() {
        let day = PublishGuard.dayKey(nowMillis: now, timeZone: zone)
        let before = now - PublishGuard.minGapMillis - 1

        XCTAssertEqual(verdict(day: day, count: 19, last: before), .go)
        XCTAssertEqual(verdict(day: day, count: 20, last: before), .tooMany)
    }

    func testThirtySecondsBetweenTwoPublicationsTheLimitIsIncluded() {
        XCTAssertEqual(verdict(day: nil, count: 0, last: now - 29_999), .tooSoon)
        XCTAssertEqual(verdict(day: nil, count: 0, last: now - 30_000), .go)
    }

    func testTheCountOfAnotherDayDoesNotCountTheNextMidnightStartsAgain() {
        let yesterday = PublishGuard.dayKey(nowMillis: now - 24 * 60 * minute, timeZone: zone)
        let today = PublishGuard.dayKey(nowMillis: now, timeZone: zone)

        XCTAssertNotEqual(yesterday, today)
        XCTAssertEqual(verdict(day: yesterday, count: 20, last: nil), .go)
        let next = PublishGuard.next(nowMillis: now, timeZone: zone, countDay: yesterday, countToday: 20)
        XCTAssertEqual(next, PublishCount(day: today, count: 1))
        XCTAssertEqual(PublishGuard.next(nowMillis: now, timeZone: zone, countDay: next.day, countToday: 3).count, 4)
    }

    func testALastPublicationInTheFutureAClockSetBackDoesNotBlockForHours() {
        XCTAssertEqual(verdict(day: nil, count: 0, last: now + 5 * 60 * minute), .go)
    }

    // MARK: - L'accès à la caméra

    func testTheCameraIsAskedOnlyAfterTheExplanationNeverCold() {
        XCTAssertEqual(CameraAccess.forOpening(.notDetermined), .explain)
        XCTAssertEqual(CameraAccess.forOpening(.authorized), .viewfinder)
    }

    func testAfterARefusalOnlyTheSettingsCanAllowItAndARestrictionHasNoSettingsButton() {
        XCTAssertEqual(CameraAccess.forOpening(.denied), .disabled)
        XCTAssertEqual(CameraAccess.forOpening(.restricted), .restricted)
        XCTAssertEqual(CameraAccess.afterRequest(granted: true), .viewfinder)
        // iPhone : la boîte ne revient jamais (D31) ; pas de « On réessaie ? », pas de repli sur l'appareil photo du système.
        XCTAssertEqual(CameraAccess.afterRequest(granted: false), .disabled)
    }

    // MARK: - Les états de l'écran

    func testOverTheViewfinderAndThePreviewTheGlassIsTheDarkOneOverThePopInsTheLightOne() {
        let preview = UpdateStage.preview(PreviewState(photo: UpdateFixtures.photo()))
        let viewfinder = UpdateStage.viewfinder(showTip: true, prepareFailed: false)

        XCTAssertEqual(viewfinder.glassVariant(updateTabSelected: true), .onPhoto)
        XCTAssertEqual(preview.glassVariant(updateTabSelected: true), .onPhoto)
        let lights: [UpdateStage] = [
            .idle, .explain, .permissionDisabled, .cameraRestricted, .cameraUnavailable, .sharingStopped
        ]
        for light in lights {
            XCTAssertEqual(light.glassVariant(updateTabSelected: true), .light, "\(light)")
        }
    }

    func testOnTheOtherTabsTheGlassIsAlwaysTheLightOneWhateverTheStageOfTheUpdate() {
        let viewfinder = UpdateStage.viewfinder(showTip: false, prepareFailed: false)

        XCTAssertEqual(viewfinder.glassVariant(updateTabSelected: false), .light)
    }

    func testEveryNoticeHasACalmTextInBothLanguagesAndNeverTheWordError() {
        let notices: [PreviewNotice] = [
            .offline, .positionNotFound, .tooMany, .tooSoon, .stale, .refused, .failed, .locationOff, .unconfirmed,
            .needsAgreement, .sharingStopped, .partial(sharedIn: 1, total: 2)
        ]
        for notice in notices {
            let text = L10n.string(notice.textKey)
            XCTAssertNotEqual(text, notice.textKey, "pas de texte pour \(notice)")
            XCTAssertFalse(text.lowercased().contains("erreur") || text.lowercased().contains("error"), "\(notice)")
            XCTAssertFalse(text.contains("!"), "\(notice)")
        }
    }

    func testOnlyAStalePhotoOrTooManyPublicationsBlockPublishing() {
        let photo = UpdateFixtures.photo()

        XCTAssertFalse(PreviewState(photo: photo, notice: .stale).canPublish)
        XCTAssertFalse(PreviewState(photo: photo, notice: .tooMany).canPublish)
        XCTAssertFalse(PreviewState(photo: photo, publishing: true).canPublish)
        for notice in [PreviewNotice.offline, .positionNotFound, .failed, .unconfirmed, .tooSoon, .locationOff] {
            XCTAssertTrue(PreviewState(photo: photo, notice: notice).canPublish, "\(notice)")
        }
    }

    // MARK: - Les octets : aucune métadonnée

    /// Un JPEG minimal bien formé : SOI, APP0 (JFIF), DQT, SOF0, SOS, données, EOI. `extra` est inséré avant le balayage.
    private func jpeg(with extra: [UInt8] = []) -> Data {
        let jfif: [UInt8] = [
            0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00
        ]
        let table: [UInt8] = [0xFF, 0xDB, 0x00, 0x05, 0x00, 0x08, 0x08]
        let frame: [UInt8] = [0xFF, 0xC0, 0x00, 0x0B, 0x08, 0x00, 0x01, 0x00, 0x01, 0x01, 0x01, 0x11, 0x00]
        let scan: [UInt8] = [0xFF, 0xDA, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3F, 0x00, 0x12, 0x34, 0xFF, 0xD9]
        var bytes: [UInt8] = [0xFF, 0xD8]
        bytes += jfif
        bytes += extra
        bytes += table
        bytes += frame
        bytes += scan
        return Data(bytes)
    }

    private func exifSegment() -> [UInt8] {
        var payload: [UInt8] = Array("Exif".utf8)
        payload += [0x00, 0x00, 0x4D, 0x4D, 0x00, 0x2A]
        let length = payload.count + 2
        var segment: [UInt8] = [0xFF, 0xE1, UInt8(length >> 8), UInt8(length & 0xFF)]
        segment += payload
        return segment
    }

    func testACleanJpegHasNoMetadataAndAnExifOrXmpOrCommentIsFoundAndRemoved() throws {
        XCTAssertFalse(JpegMetadata.containsMetadata(jpeg()))
        let comment: [UInt8] = [0xFF, 0xFE, 0x00, 0x05, 0x61, 0x62, 0x63]
        let dirty = jpeg(with: exifSegment() + comment)

        XCTAssertTrue(JpegMetadata.containsMetadata(dirty))
        let clean = try XCTUnwrap(JpegMetadata.stripped(dirty))
        XCTAssertFalse(JpegMetadata.containsMetadata(clean))
        XCTAssertEqual(clean, jpeg(), "tout le reste est gardé tel quel : JFIF, tables, cadre, balayage")
        XCTAssertNil(clean.range(of: Data("Exif".utf8)))
    }

    func testBytesThatAreNotAJpegAreNeverTrustedAndNeverStripped() {
        XCTAssertTrue(JpegMetadata.containsMetadata(Data([1, 2, 3, 4, 5, 6])))
        XCTAssertNil(JpegMetadata.stripped(Data([1, 2, 3, 4, 5, 6])))
        XCTAssertNil(JpegMetadata.stripped(Data()))
        // Un segment dont la longueur dépasse le fichier : mal formé.
        XCTAssertNil(JpegMetadata.stripped(Data([0xFF, 0xD8, 0xFF, 0xE1, 0xFF, 0xFF, 0x00])))
    }

    // MARK: - La photo part avec le partage (critère D7)

    func testEveryLotThatStopsTheSharingLeavesOrRemovesSomeoneDeletesThePhotoToo() {
        let photoPath = "miffs/\(testMiffId)/photos/\(updateUid)"
        let removal = MiffWrites.removeMember(
            miffId: testMiffId, targetUid: updateUid, name: "Chez nous", oldCode: testCode, newCode: "ABCDEFGH2345"
        )
        let finalDelete = MiffWrites.finalDelete(
            miffId: testMiffId, uid: updateUid, code: testCode, codeExists: true, membershipKey: "key"
        )
        let lots: [(String, [DocWrite])] = [
            ("arrêt du partage", MiffWrites.stopSharing(miffId: testMiffId, uid: updateUid)),
            ("départ", MiffWrites.leave(miffId: testMiffId, uid: updateUid, membershipKey: "key")),
            ("retrait d'un proche", removal),
            ("suppression de la Miff", finalDelete)
        ]
        for (name, writes) in lots {
            let deleted = writes.contains { $0.path == photoPath && $0.kind == .delete }
            XCTAssertTrue(deleted, "\(name) : la photo n'est pas supprimée")
        }
    }

    // MARK: - Les réponses de publication

    func testEveryResultThatDidNotPublishHasACalmNoticeAndPublishedHasNone() {
        let results: [PublishResult] = [
            .positionNotFound, .locationOff, .unconfirmed, .offline, .sharingStopped, .needsAgreement,
            .tooMany, .tooSoon, .stale, .refused, .failed
        ]
        for result in results {
            let notice = PreviewNotice(result: result)
            XCTAssertNotEqual(L10n.string(notice.textKey), notice.textKey, "\(result)")
        }
        XCTAssertEqual(PreviewNotice(result: .refused), .refused)
        let partial = PublishResult.partial(sharedIn: 1, total: 3, remaining: ["a", "b"])
        XCTAssertEqual(PreviewNotice(result: partial), .partial(sharedIn: 1, total: 3))
    }
}
