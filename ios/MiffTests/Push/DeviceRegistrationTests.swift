import XCTest
@testable import Miff

/// SRV-2I : QUAND ce téléphone écrit son document du registre des appareils, et la FORME EXACTE de ce document (contrat
/// `docs/dev/jetons-appareil-clients.md`). Miroir de `PushRegistrationTest` d'Android.
final class DeviceRegistrationTests: XCTestCase {
    private let day = 24 * 60 * 60 * 1000
    private let now = 1_800_000_000_000

    private func wish(
        token: String = "fp-1", zone: String = "Europe/Paris", updates: Bool = true, device: String = "device-a"
    ) -> RegistrationWish {
        RegistrationWish(
            uid: "uid-1", deviceId: device, tokenFingerprint: token, timeZoneId: zone, notifyUpdates: updates
        )
    }

    private func record(writtenAgo: Int = 0, zone: String = "Europe/Paris") -> RegistrationRecord {
        RegistrationRecord(
            uid: "uid-1", deviceId: "device-a", tokenFingerprint: "fp-1", timeZoneId: zone, notifyUpdates: true,
            writtenAtMillis: now - writtenAgo
        )
    }

    // MARK: - Quand écrire

    func testNothingWrittenYetMeansAFirstWrite() {
        XCTAssertEqual(RegistrationRules.decide(wish: wish(), record: nil, nowMillis: now), .write(.first))
    }

    func testNothingChangedMeansNothingToWrite() {
        let decision = RegistrationRules.decide(wish: wish(), record: record(writtenAgo: 5 * day), nowMillis: now)
        XCTAssertEqual(decision, .none)
    }

    func testANewSessionOrAnotherAccountIsWrittenAgain() {
        XCTAssertEqual(
            RegistrationRules.decide(wish: wish(device: "device-b"), record: record(), nowMillis: now),
            .write(.newSession)
        )
    }

    func testANewTokenIsWrittenAtOnce() {
        XCTAssertEqual(
            RegistrationRules.decide(wish: wish(token: "fp-2"), record: record(), nowMillis: now),
            .write(.tokenChanged)
        )
    }

    func testTheSwitchIsWrittenAtOnce() {
        XCTAssertEqual(
            RegistrationRules.decide(wish: wish(updates: false), record: record(), nowMillis: now),
            .write(.settingsChanged)
        )
    }

    func testAChangeOfTimeZoneIsWrittenOncePerDayAtMost() {
        let moved = wish(zone: "America/New_York")
        let early = RegistrationRules.decide(wish: moved, record: record(writtenAgo: day - 1), nowMillis: now)
        XCTAssertEqual(early, .none)
        XCTAssertEqual(
            RegistrationRules.decide(wish: moved, record: record(writtenAgo: day), nowMillis: now),
            .write(.settingsChanged)
        )
    }

    func testAnUnchangedDocumentIsRefreshedAfterThirtyDaysNotBefore() {
        XCTAssertEqual(RegistrationRules.refreshAfterMillis, 30 * day)
        XCTAssertEqual(
            RegistrationRules.decide(wish: wish(), record: record(writtenAgo: 30 * day - 1), nowMillis: now), .none
        )
        XCTAssertEqual(
            RegistrationRules.decide(wish: wish(), record: record(writtenAgo: 30 * day), nowMillis: now),
            .write(.refresh)
        )
    }

    func testAFailedWriteWaitsTenMinutes() {
        let failed = now
        XCTAssertFalse(RegistrationRules.mayRetry(failedAtMillis: failed, nowMillis: failed + 9 * 60 * 1000))
        XCTAssertTrue(RegistrationRules.mayRetry(failedAtMillis: failed, nowMillis: failed + 10 * 60 * 1000))
        XCTAssertTrue(RegistrationRules.mayRetry(failedAtMillis: nil, nowMillis: failed))
        XCTAssertTrue(RegistrationRules.mayRetry(failedAtMillis: failed, nowMillis: failed - 1), "horloge qui recule")
    }

    // MARK: - Empreinte, version, fuseau

    func testTheFingerprintIsStableAndDoesNotContainTheToken() {
        let token = "fake-fingerprint-input-aaaa"
        let first = TokenFingerprint.of(token)
        XCTAssertEqual(first, TokenFingerprint.of(token))
        XCTAssertNotEqual(first, TokenFingerprint.of(token + "x"))
        XCTAssertEqual(first.count, 64)
        XCTAssertFalse(first.contains("fake-fingerprint"))
    }

    func testTheVersionIsNameAndBuildCutAt32Characters() {
        XCTAssertEqual(DeviceAppVersion.of(name: "0.1.0", build: "27"), "0.1.0 (27)")
        XCTAssertEqual(DeviceAppVersion.of(name: nil, build: nil), "0 (0)")
        XCTAssertEqual(DeviceAppVersion.of(name: "", build: ""), "0 (0)")
        let long = DeviceAppVersion.of(name: String(repeating: "9", count: 60), build: "1")
        XCTAssertEqual(long.count, DeviceEntryLimits.maxAppVersionChars)
    }

    func testTheTimeZoneShapeFollowsTheRule() {
        for good in ["Europe/Paris", "America/Argentina/Buenos_Aires", "Etc/GMT+5", "GMT+01:00", "UTC"] {
            XCTAssertTrue(TimeZoneShape.isValid(good), good)
        }
        for bad in ["", "1Europe", "Europe Paris", "Europe/Pàris", "/Paris", String(repeating: "a", count: 65)] {
            XCTAssertFalse(TimeZoneShape.isValid(bad), bad)
        }
        XCTAssertTrue(TimeZoneShape.isValid(TimeZone.current.identifier) || TimeZone.current.identifier.isEmpty)
    }

    func testTheRecordsAndTheEntryNeverShowTheirContent() {
        XCTAssertEqual("\(record())", "RegistrationRecord(redacted)")
        XCTAssertEqual("\(wish())", "RegistrationWish(redacted)")
        let entry = DeviceEntry.make(token: "secret-token", zone: "Europe/Paris")
        XCTAssertEqual(entry.map { "\($0)" }, "DeviceEntry(redacted)")
    }
}

/// La FORME EXACTE du document écrit (critère J5 du ticket) : sept clés, `notify.updates` imbriqué, `createdAt` et `platform` seulement à la
/// création, dates du serveur. Pas un simple contrôle de texte : on lit les champs tels qu'ils partent vers Firestore.
final class DeviceDocumentTests: XCTestCase {
    private func entry(updates: Bool = true) throws -> DeviceEntry {
        try XCTUnwrap(
            DeviceEntry.make(token: "token-123", zone: "Europe/Paris", updates: updates, version: "0.1.0 (27)")
        )
    }

    func testTheCreationHasExactlyTheSevenKeys() throws {
        let fields = DeviceDocument.creation(try entry())

        XCTAssertEqual(Set(fields.keys), DeviceDocument.keys)
        XCTAssertEqual(Set(fields.keys), ["token", "platform", "appVersion", "tz", "notify", "updatedAt", "createdAt"])
        XCTAssertEqual(fields["token"], .string("token-123"))
        XCTAssertEqual(fields["platform"], .string("ios"))
        XCTAssertEqual(fields["appVersion"], .string("0.1.0 (27)"))
        XCTAssertEqual(fields["tz"], .string("Europe/Paris"))
        XCTAssertEqual(fields["notify"], .map(["updates": .bool(true)]))
        XCTAssertEqual(fields["updatedAt"], .serverTimestamp)
        XCTAssertEqual(fields["createdAt"], .serverTimestamp)
    }

    func testTheSwitchIsWrittenInsideNotifyAndNothingElseIsThere() throws {
        let fields = DeviceDocument.creation(try entry(updates: false))
        XCTAssertEqual(fields["notify"], .map(["updates": .bool(false)]))
    }

    func testTheUpdateNeverCarriesCreatedAtNorPlatform() throws {
        let fields = DeviceDocument.update(try entry())

        XCTAssertEqual(Set(fields.keys), ["token", "appVersion", "tz", "notify", "updatedAt"])
        XCTAssertNil(fields["createdAt"])
        XCTAssertNil(fields["platform"])
        XCTAssertEqual(fields["updatedAt"], .serverTimestamp)
    }

    func testNoForbiddenKeyIsEverWritten() throws {
        let forbidden = [
            "locale", "notificationsEnabled", "lat", "lng", "latitude", "longitude", "email", "name", "firstName",
            "miffId", "deviceName", "idfa", "idfv", "language"
        ]
        for fields in [DeviceDocument.creation(try entry()), DeviceDocument.update(try entry())] {
            for key in forbidden {
                XCTAssertNil(fields[key], key)
            }
        }
    }

    func testTheFieldsReachFirestoreAsPlainValuesWithTheNestedMap() throws {
        let converted = FirestoreFieldConversion.convert(DeviceDocument.creation(try entry()))

        XCTAssertEqual(Set(converted.keys), DeviceDocument.keys)
        XCTAssertEqual(converted["token"] as? String, "token-123")
        XCTAssertEqual(converted["platform"] as? String, "ios")
        let notify = try XCTUnwrap(converted["notify"] as? [String: Any])
        XCTAssertEqual(Set(notify.keys), ["updates"])
        XCTAssertEqual(notify["updates"] as? Bool, true)
        // Les dates ne sont jamais celles du téléphone : ni `Date`, ni `Timestamp` construits ici, mais l'heure du serveur.
        XCTAssertFalse(converted["updatedAt"] is Date)
        XCTAssertFalse(converted["createdAt"] is Date)
    }

    func testThePathNeedsAnAccountWithoutSlashAndASessionIdentifier() {
        let device = "AbCdEfGhIjKlMnOp1234"
        XCTAssertEqual(DeviceDocument.path(uid: "u1", deviceId: device), "users/u1/devices/\(device)")
        XCTAssertNil(DeviceDocument.path(uid: "", deviceId: device))
        XCTAssertNil(DeviceDocument.path(uid: "u1/x", deviceId: device))
        XCTAssertNil(DeviceDocument.path(uid: "u1", deviceId: "court"))
        XCTAssertNil(DeviceDocument.path(uid: "u1", deviceId: "a/b/cdefghijklmnopq"))
    }

    func testAnEntryOutsideTheLimitsOfTheRulesIsRefusedBeforeSending() {
        XCTAssertNil(DeviceEntry.make(token: "", zone: "Europe/Paris"))
        XCTAssertNil(DeviceEntry.make(token: String(repeating: "a", count: 4097), zone: "Europe/Paris"))
        XCTAssertNil(DeviceEntry.make(token: "t", zone: ""))
        XCTAssertNil(DeviceEntry.make(token: "t", zone: "Europe/Paris", version: ""))
        XCTAssertNil(DeviceEntry.make(token: "t", zone: "Europe/Paris", version: String(repeating: "v", count: 33)))
    }
}

private extension DeviceEntry {
    /// Une entrée d'essai, avec des valeurs par défaut pour ne pas allonger les lignes.
    static func make(token: String, zone: String, updates: Bool = true, version: String = "1 (1)") -> DeviceEntry? {
        DeviceEntry.make(token: token, timeZoneId: zone, notifyUpdates: updates, appVersion: version)
    }
}
