// Quelques assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// FEED-ORDRE-PHOTO, cas T20 : ce que les écritures du statut disent de `photoAt` (contrat avec `validPhotoAt` dans `backend/firestore.rules`).
/// `photoAt` est écrit UNIQUEMENT par la mise à jour partielle de la publication d'une photo, avec l'heure du serveur ; jamais par le nettoyage
/// d'une photo expirée, une arrivée en lieu, un départ, la batterie, l'accès, un voyage ni le statut initial. Miroir des tests d'`UpdateWrites`
/// et de `TrackingWrites` d'Android.
final class PhotoDateWritesTests: XCTestCase {
    private let now = UpdateFixtures.noon
    private let uid = "uid-1"

    private func publish(datesPhoto: Bool = true) throws -> [DocWrite] {
        var context = PublishContext(nowMillis: now, batteryPct: 80, deviceId: testDeviceId)
        context.datesPhoto = datesPhoto
        let place = try XCTUnwrap(PhotoPlace.from(UpdateFixtures.fix(), nowMillis: now))
        return UpdateWrites.publish(
            miffId: testMiffId, uid: uid, photo: UpdateFixtures.photo(), place: place, context: context
        )
    }

    private func payload(_ fact: StatusFact, clearTravel: Bool = false) -> StatusPayload {
        var body = StatusPayload(fact: fact, presenceAtMillis: fact.presenceAtMillis, batteryPct: 61, access: .background)
        body.clearTravel = clearTravel
        return body
    }

    /// Chaque autre écriture de statut du client (hors statut initial, voir plus bas).
    private func otherStatusWrites() throws -> [String: DocWrite] {
        let arrival = StatusFact(presence: .inTrusted, placeId: "p1", presenceAtMillis: now, setLastUpdate: true)
        let departure = StatusFact(presence: .out, placeId: nil, presenceAtMillis: now, setLastUpdate: false)
        let period = TravelPeriod(untilMillis: now + 3 * 24 * 3_600_000, untilDay: "2026-11-05")
        return [
            "arrivée": try XCTUnwrap(TrackingWrites.statusUpdate(
                miffId: testMiffId, uid: uid, payload: payload(arrival), deviceId: testDeviceId
            )),
            "départ": try XCTUnwrap(TrackingWrites.statusUpdate(
                miffId: testMiffId, uid: uid, payload: payload(departure), deviceId: testDeviceId
            )),
            "fin de voyage": try XCTUnwrap(TrackingWrites.statusUpdate(
                miffId: testMiffId, uid: uid, payload: payload(departure, clearTravel: true), deviceId: testDeviceId
            )),
            "batterie": try XCTUnwrap(TrackingWrites.batteryUpdate(
                miffId: testMiffId, uid: uid, batteryPct: 50, deviceId: testDeviceId
            )),
            "accès": try XCTUnwrap(TrackingWrites.accessUpdate(
                miffId: testMiffId, uid: uid, batteryPct: 50, access: .foreground, deviceId: testDeviceId
            )),
            "début de voyage": try XCTUnwrap(TrackingWrites.travelStart(
                miffId: testMiffId, uid: uid, period: period, presenceAtMillis: now, deviceId: testDeviceId
            )),
            "fin du voyage changée": try XCTUnwrap(TrackingWrites.travelDate(
                miffId: testMiffId, uid: uid, period: period, deviceId: testDeviceId
            ))
        ]
    }

    func testT20PublishingAPhotoWritesPhotoAtWithTheServerTimeNextToHasPhotoAndLastUpdateAt() throws {
        let status = try XCTUnwrap(try publish().last)

        XCTAssertEqual(status.path, "miffs/\(testMiffId)/status/\(uid)")
        XCTAssertEqual(status.kind, .update, "une mise à jour partielle, jamais un set complet")
        XCTAssertEqual(status.data["photoAt"], .serverTimestamp, "l'heure du serveur, jamais celle du téléphone")
        XCTAssertEqual(status.data["hasPhoto"], .bool(true))
        XCTAssertEqual(status.data["lastUpdateAt"], .serverTimestamp)
        XCTAssertEqual(status.data["deviceId"], .string(testDeviceId))
    }

    func testThePhotoDocumentItselfNeverCarriesPhotoAt() throws {
        let photo = try XCTUnwrap(try publish().first)

        XCTAssertNil(photo.data["photoAt"])
        XCTAssertEqual(Set(photo.data.keys), ["jpeg", "createdAt", "expiresAt", "lat", "lng", "measuredAt"])
    }

    func testT20TheCleanUpOfAnExpiredPhotoNeverWritesPhotoAtSoTheRankStays() throws {
        let writes = UpdateWrites.removeExpired(miffId: testMiffId, uid: uid, batteryPct: 50, deviceId: testDeviceId)
        let direct = UpdateWrites.photoStatus(
            miffId: testMiffId, uid: uid, photoPublished: false, batteryPct: 50, deviceId: testDeviceId
        )

        XCTAssertEqual(writes.last?.data["hasPhoto"], .bool(false))
        XCTAssertNil(writes.last?.data["photoAt"])
        XCTAssertNil(direct?.data["photoAt"])
    }

    func testT20NoOtherWriteOfTheStatusEverCarriesPhotoAt() throws {
        for (name, write) in try otherStatusWrites() {
            XCTAssertNil(write.data["photoAt"], name)
            XCTAssertEqual(write.kind, .update, name)
        }
    }

    func testT20TheInitialStatusDoesNotCarryPhotoAt() {
        let initial = MiffWrites.initialStatus(.background, deviceId: testDeviceId)

        XCTAssertNil(initial["photoAt"])
        XCTAssertEqual(initial["hasPhoto"], .bool(false))
    }

    func testTheReplayWithoutThePhotoDateIsTheSameLotMinusPhotoAtAndNothingElse() throws {
        let full = try publish()
        let replay = try publish(datesPhoto: false)

        XCTAssertEqual(replay.count, 2)
        XCTAssertEqual(replay[0], full[0], "la photo : identique")
        XCTAssertEqual(replay[1].path, full[1].path)
        XCTAssertEqual(replay[1].kind, full[1].kind)
        XCTAssertNil(replay[1].data["photoAt"])
        XCTAssertEqual(
            replay[1].data,
            full[1].data.filter { $0.key != "photoAt" },
            "rien de plus, rien de changé : une clé de moins, donc rien que les règles n'aient déjà jugé"
        )
        XCTAssertEqual(Set(full[1].data.keys).subtracting(replay[1].data.keys), ["photoAt"])
        XCTAssertTrue(SessionWriteGuard.isSendable(replay), "le rejeu porte toujours la session")
    }

    func testRemovingAKeyKeepsThePathTheKindAndTheOtherKeys() {
        let write = DocWrite(path: "a/b", kind: .update, data: ["x": .bool(true), "y": .null])

        let trimmed = write.removing("x")

        XCTAssertEqual(trimmed, DocWrite(path: "a/b", kind: .update, data: ["y": .null]))
        XCTAssertEqual(write.removing("absente"), write, "une clé absente ne change rien")
    }

    func testTheAnnouncementTextsSayThatSomeoneSharedAPhoto() throws {
        let strings = try AlertCatalog.root()

        XCTAssertEqual(AlertCatalog.value("feed_order_updated_one_a11y", language: "fr", in: strings), "%@ a publié une photo")
        XCTAssertEqual(AlertCatalog.value("feed_order_updated_one_a11y", language: "en", in: strings), "%@ shared a photo")
        XCTAssertNotNil(AlertCatalog.value("feed_order_updated_many_a11y", language: "fr", in: strings), "la phrase générale est inchangée")
    }
}
// swiftlint:enable line_length
