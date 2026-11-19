import XCTest
@testable import Miff

/// Le contrat de l'alerte « Je suis en danger » avec `backend/firestore.rules` (`match /alerts/{uid}`, data-model.md §2.18 et §2.20) : les sept clés
/// exactement, les dates du serveur, la fenêtre de `expiresAt`, le compteur du jour. Miroir d'`AlertDomainTest` d'Android (les cas des tests sont le contrat).
final class DangerAlertRulesTests: XCTestCase {
    private let deviceId = "abcdefghijklmnopqrstuvwxyz012345"
    private let now = 1_794_063_600_000
    private let miffId = "abcdefghijklmnopqrst"
    private let day = 24 * 3_600_000

    // MARK: - Le début

    func testAStartWritesExactlySevenKeysAndNeverAPositionOrAText() throws {
        let fields = try XCTUnwrap(DangerAlertWrites.start(deviceId: deviceId, deviceNowMillis: now, seq: 1))

        XCTAssertEqual(Set(fields.keys), DangerAlertWrites.startKeys)
        XCTAssertEqual(fields.count, 7)
        for forbidden in ["lat", "lng", "latitude", "longitude", "text", "message", "name", "photo", "miffId", "uid"] {
            XCTAssertNil(fields[forbidden], forbidden)
        }
    }

    func testAStartUsesTheServerTimeForStartedAtAndANullEndedAt() throws {
        let fields = try XCTUnwrap(DangerAlertWrites.start(deviceId: deviceId, deviceNowMillis: now, seq: 1))

        XCTAssertEqual(fields["startedAt"], .serverTimestamp)
        XCTAssertEqual(fields["endedAt"], FieldValueSpec.null)
        XCTAssertEqual(fields["status"], .string("active"))
        XCTAssertEqual(fields["seq"], .integer(1))
        XCTAssertEqual(fields["deviceId"], .string(deviceId))
    }

    func testExpiresAtIsThePhoneClockPlusSevenDaysMinusTwoHours() throws {
        let fields = try XCTUnwrap(DangerAlertWrites.start(deviceId: deviceId, deviceNowMillis: now, seq: 1))
        let expected = now + 7 * day - 2 * 3_600_000

        XCTAssertEqual(fields["expiresAt"], .timestamp(millis: expected))
        XCTAssertEqual(fields["purgeAt"], .timestamp(millis: expected + 2 * day))
        XCTAssertEqual(DangerAlertRules.expiresAtMillis(deviceNowMillis: now), expected)
    }

    func testTheExpiryStaysInTheWindowOfTheRulesForAClockUpToTwoHoursAheadAndTwentyTwoBehind() {
        for skew in [-22 * 3_600_000 + 60_000, 0, 2 * 3_600_000] {
            // `skew` : de combien l'horloge du téléphone est en AVANCE sur le serveur.
            let serverNow = now - skew
            let expires = DangerAlertRules.expiresAtMillis(deviceNowMillis: now)
            XCTAssertGreaterThan(expires, serverNow + 6 * day, "skew \(skew)")
            XCTAssertLessThanOrEqual(expires, serverNow + 7 * day, "skew \(skew)")
        }
    }

    func testAStartWithABadSessionIdentifierOrSeqIsNeverBuilt() {
        XCTAssertNil(DangerAlertWrites.start(deviceId: "short", deviceNowMillis: now, seq: 1))
        XCTAssertNil(DangerAlertWrites.start(deviceId: deviceId, deviceNowMillis: now, seq: 0))
        XCTAssertNil(DangerAlertWrites.end(deviceId: "bad id with spaces", deviceNowMillis: now))
    }

    func testARearmingCarriesTheNextSeq() throws {
        let fields = try XCTUnwrap(DangerAlertWrites.start(deviceId: deviceId, deviceNowMillis: now, seq: 5))

        XCTAssertEqual(fields["seq"], .integer(5))
    }

    // MARK: - La fin

    func testTheEndChangesOnlyFourKeysAndTheEndDateIsTheServers() throws {
        let fields = try XCTUnwrap(DangerAlertWrites.end(deviceId: deviceId, deviceNowMillis: now))

        XCTAssertEqual(Set(fields.keys), DangerAlertWrites.endKeys)
        XCTAssertEqual(fields["status"], .string("ended"))
        XCTAssertEqual(fields["endedAt"], .serverTimestamp)
        XCTAssertEqual(fields["purgeAt"], .timestamp(millis: now + 2 * day))
    }

    // MARK: - Le compteur du jour

    func testTheCounterIsCreatedWithTheFirstSlotAtTheServerTime() {
        let fields = DangerAlertWrites.quotaCreate(miffId: miffId, deviceNowMillis: now)

        XCTAssertEqual(Set(fields.keys), DangerAlertWrites.quotaKeys)
        XCTAssertEqual(fields["s0"], .serverTimestamp)
        XCTAssertEqual(fields["s1"], FieldValueSpec.null)
        XCTAssertEqual(fields["s2"], FieldValueSpec.null)
        XCTAssertEqual(fields["lastMiffId"], .string(miffId))
        XCTAssertEqual(fields["updatedAt"], .serverTimestamp)
        XCTAssertEqual(fields["expireAt"], .timestamp(millis: now + 2 * day))
    }

    func testAnUpdateOfTheCounterChangesOneSlotAndNothingElse() throws {
        let fields = try XCTUnwrap(DangerAlertWrites.quotaUpdate(slot: 1, miffId: miffId, deviceNowMillis: now))

        XCTAssertEqual(Set(fields.keys), ["s1", "lastMiffId", "updatedAt", "expireAt"])
        XCTAssertEqual(fields["s1"], .serverTimestamp)
        XCTAssertNil(DangerAlertWrites.quotaUpdate(slot: 3, miffId: miffId, deviceNowMillis: now))
        XCTAssertNil(DangerAlertWrites.quotaUpdate(slot: -1, miffId: miffId, deviceNowMillis: now))
    }

    func testTheCounterOffersAFreeSlotFirstThenAStaleOneAndNothingWhenThreeAreFresh() {
        XCTAssertEqual(DangerAlertQuota.empty.slotToReplace(nowMillis: now), 0)
        XCTAssertEqual(DangerAlertQuota(slots: [now - 10, nil, nil]).slotToReplace(nowMillis: now), 1)
        XCTAssertNil(DangerAlertQuota(slots: [now - 10, now - 20, now - 30]).slotToReplace(nowMillis: now))
        // Passé 24 h plus la marge de l'horloge du téléphone : la case est périmée.
        let stale = now - day - DangerAlertRules.quotaSafetyMillis
        XCTAssertEqual(DangerAlertQuota(slots: [now - 10, stale, now - 30]).slotToReplace(nowMillis: now), 1)
        // Juste avant : pas encore (le serveur seul sait mieux).
        XCTAssertNil(DangerAlertQuota(slots: [now - 10, stale + 1, now - 30]).slotToReplace(nowMillis: now))
    }

    func testTheDailyLimitIsThree() {
        XCTAssertEqual(DangerAlertRules.maxStartsPerDay, 3)
        XCTAssertEqual(DangerAlertQuota.empty.slots.count, 3)
    }

    // MARK: - Les suppressions des lots

    func testTheAlertDeletionIsAddedOnlyWhenTheFunctionIsOn() {
        let on = DangerAlertWrites.deletion(miffId: miffId, uid: "u1", enabled: true)

        XCTAssertEqual(on, [DocWrite(path: "miffs/\(miffId)/alerts/u1", kind: .delete)])
        XCTAssertTrue(DangerAlertWrites.deletion(miffId: miffId, uid: "u1", enabled: false).isEmpty)
    }

    // MARK: - Un document lu sur le réseau

    func testAnAlertIsLiveOnlyWhileActiveAndBeforeItsDate() throws {
        let doc = try XCTUnwrap(
            DangerAlertDoc.parse(uid: "u1", status: "active", startedAtMillis: now, expiresAtMillis: now + 1000, seq: 1)
        )

        XCTAssertTrue(doc.isLive(nowMillis: now))
        // À sa date elle disparaît : aucune trace, aucune notification.
        XCTAssertFalse(doc.isLive(nowMillis: now + 1000))
        let ended = try XCTUnwrap(
            DangerAlertDoc.parse(uid: "u1", status: "ended", startedAtMillis: now, expiresAtMillis: now + 1000, seq: 1)
        )
        XCTAssertFalse(ended.isLive(nowMillis: now))
    }

    private func parse(_ uid: String, _ status: String, expires: Int?) -> DangerAlertDoc? {
        DangerAlertDoc.parse(uid: uid, status: status, startedAtMillis: now, expiresAtMillis: expires, seq: 1)
    }

    func testADocumentThatIsNotOneOfOursIsIgnored() {
        XCTAssertNil(parse("u1", "weird", expires: 5))
        XCTAssertNil(parse("u1", "active", expires: nil))
        XCTAssertNil(parse("u/1", "active", expires: 5))
        XCTAssertNil(parse("", "active", expires: 5))
    }

    private func doc(_ uid: String, started: Int, expires: Int) throws -> DangerAlertDoc {
        try XCTUnwrap(
            DangerAlertDoc.parse(uid: uid, status: "active", startedAtMillis: started, expiresAtMillis: expires, seq: 1)
        )
    }

    func testLiveAlertsAreSortedOldestFirstAndStable() throws {
        let late = try doc("b", started: 200, expires: 9_999)
        let early = try doc("z", started: 100, expires: 9_999)
        let gone = try doc("a", started: 50, expires: 10)

        XCTAssertEqual([late, early, gone].live(at: 20).map { $0.uid }, ["z", "b"])
    }

    func testADescriptionNeverShowsTheContent() throws {
        let alert = try doc("secretUid", started: now, expires: now + 1)

        XCTAssertFalse("\(alert)".contains("secretUid"))
        XCTAssertFalse("\(DangerAlertQuota(slots: [now, nil, nil]))".contains("\(now)"))
    }
}
