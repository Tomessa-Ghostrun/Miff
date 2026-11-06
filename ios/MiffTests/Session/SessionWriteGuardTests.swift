import XCTest
@testable import Miff

/// Le contrat « `deviceId` dans CHAQUE écriture de statut et de position, mises à jour partielles comprises » (SESS-1, contrat client
/// I-1 de `backend/data-model.md`). Les règles Firestore ne peuvent pas l'exiger d'une mise à jour partielle : ces tests sont la seule
/// protection. Ils échouent si une future écriture (LOC-4b) part sans la clé.
final class SessionWriteGuardTests: XCTestCase {
    private let uid = "uid-1"
    private let miffId = testMiffId

    private var statusPath: String {
        "miffs/\(miffId)/status/\(uid)"
    }

    private var positionPath: String {
        "miffs/\(miffId)/positions/\(uid)"
    }

    // MARK: - Ce qui doit porter l'identifiant

    func testAStatusOrAPositionThatIsCreatedOrUpdatedMustCarryTheIdentifier() {
        for kind in [WriteKind.set, WriteKind.update] {
            XCTAssertTrue(SessionWriteGuard.requiresDeviceId(DocWrite(path: statusPath, kind: kind)))
            XCTAssertTrue(SessionWriteGuard.requiresDeviceId(DocWrite(path: positionPath, kind: kind)))
        }
    }

    func testDeletionsAndOtherDocumentsDoNotNeedIt() {
        XCTAssertFalse(SessionWriteGuard.requiresDeviceId(DocWrite(path: statusPath, kind: .delete)))
        XCTAssertFalse(SessionWriteGuard.requiresDeviceId(DocWrite(path: positionPath, kind: .delete)))
        for path in [
            "miffs/\(miffId)",
            "miffs/\(miffId)/members/\(uid)",
            "miffs/\(miffId)/photos/\(uid)",
            "miffs/\(miffId)/places/place-1",
            "users/\(uid)/memberships/free",
            "users/\(uid)/session/active",
            "joinCodes/\(testCode)"
        ] {
            XCTAssertFalse(SessionWriteGuard.requiresDeviceId(DocWrite(path: path, kind: .set)), path)
        }
    }

    // MARK: - Ce qui est accepté

    func testAStampedWriteIsCompliantInFullAndInPartialForm() {
        let full = DocWrite(
            path: statusPath,
            kind: .set,
            data: SessionWriteGuard.stamped(["presence": .string("home")], deviceId: testDeviceId)
        )
        let partial = DocWrite(
            path: statusPath,
            kind: .update,
            data: SessionWriteGuard.stamped(["batteryPct": .null], deviceId: testDeviceId)
        )
        let position = DocWrite(
            path: positionPath,
            kind: .set,
            data: SessionWriteGuard.stamped(["measuredAt": .serverTimestamp], deviceId: testDeviceId)
        )

        XCTAssertTrue(SessionWriteGuard.isCompliant([full, partial, position]))
        XCTAssertEqual(full.data["deviceId"], .string(testDeviceId))
    }

    // MARK: - Ce qui est refusé avant d'être envoyé

    func testAStatusWithoutTheKeyIsRefused() {
        let write = DocWrite(path: statusPath, kind: .set, data: ["presence": .string("home")])

        XCTAssertFalse(SessionWriteGuard.isCompliant([write]))
        XCTAssertEqual(SessionWriteGuard.violations(in: [write]).count, 1)
    }

    func testAPartialStatusUpdateWithoutTheKeyIsRefused() {
        // Le cas que les règles laissent passer (SF9) : seule la discipline du client le protège.
        let write = DocWrite(path: statusPath, kind: .update, data: ["batteryPct": .null])

        XCTAssertFalse(SessionWriteGuard.isCompliant([write]))
    }

    func testAPositionWithoutTheKeyIsRefused() {
        let write = DocWrite(path: positionPath, kind: .set, data: ["measuredAt": .serverTimestamp])

        XCTAssertFalse(SessionWriteGuard.isCompliant([write]))
    }

    func testAMalformedOrEmptyOrWrongTypeKeyIsRefused() {
        let wrong: [FieldValueSpec] = [.string("court"), .string(""), .null, .bool(true), .string("avec un espace ici")]
        for value in wrong {
            let write = DocWrite(path: statusPath, kind: .update, data: ["deviceId": value])
            XCTAssertFalse(SessionWriteGuard.isCompliant([write]))
        }
    }

    func testOneBadWriteInAMixedLotRefusesTheWholeLot() {
        let good = DocWrite(
            path: statusPath,
            kind: .set,
            data: SessionWriteGuard.stamped([:], deviceId: testDeviceId)
        )
        let bad = DocWrite(path: "miffs/other/status/\(uid)", kind: .update, data: ["presence": .string("home")])
        let unrelated = DocWrite(path: "users/\(uid)/memberships/free", kind: .set, data: [:])

        XCTAssertFalse(SessionWriteGuard.isCompliant([good, unrelated, bad]))
        XCTAssertEqual(SessionWriteGuard.violations(in: [good, unrelated, bad]).map { $0.path }, [bad.path])
    }

    // MARK: - Chaque lot que l'app sait construire

    func testEveryLotTheAppBuildsPassesTheGuard() {
        let entry = MiffEntry(
            uid: uid,
            miffId: miffId,
            membershipKey: "free",
            profile: testProfile,
            access: .unknown,
            deviceId: testDeviceId
        )
        let lots: [[DocWrite]] = [
            MiffWrites.create(entry: entry, code: testCode, name: "Chez nous"),
            MiffWrites.join(entry: entry, code: testCode),
            MiffWrites.resumeSharing(miffId: miffId, uid: uid, access: .background, deviceId: testDeviceId),
            MiffWrites.stopSharing(miffId: miffId, uid: uid),
            MiffWrites.leave(miffId: miffId, uid: uid, membershipKey: "free"),
            MiffWrites.rename(miffId: miffId, code: testCode, name: "Nouveau nom"),
            MiffWrites.dropIndex(miffId: miffId, uid: uid, membershipKey: "free")
        ]

        for lot in lots {
            XCTAssertFalse(lot.isEmpty)
            XCTAssertTrue(SessionWriteGuard.isCompliant(lot))
        }
    }

    func testEveryStatusTheAppBuildsCarriesTheIdentifierOfTheConfirmedSession() {
        let entry = MiffEntry(
            uid: uid,
            miffId: miffId,
            membershipKey: "free",
            profile: testProfile,
            access: .foreground,
            deviceId: testDeviceId
        )
        let lots = MiffWrites.create(entry: entry, code: testCode, name: "Chez nous")
            + MiffWrites.join(entry: entry, code: testCode)
            + MiffWrites.resumeSharing(miffId: miffId, uid: uid, access: .foreground, deviceId: testDeviceId)

        let statuses = lots.filter { $0.path.contains("/status/") }

        XCTAssertEqual(statuses.count, 3)
        for status in statuses {
            XCTAssertEqual(status.data["deviceId"], .string(testDeviceId))
        }
    }

    func testALotWithoutAValidIdentifierIsRefusedBeforeAnythingIsBuilt() {
        for bad in ["", "court", "contient/un/separateur"] {
            let entry = MiffEntry(
                uid: uid,
                miffId: miffId,
                membershipKey: "free",
                profile: testProfile,
                access: .unknown,
                deviceId: bad
            )
            XCTAssertTrue(MiffWrites.create(entry: entry, code: testCode, name: "Chez nous").isEmpty)
            XCTAssertTrue(MiffWrites.join(entry: entry, code: testCode).isEmpty)
            XCTAssertTrue(MiffWrites.resumeSharing(miffId: miffId, uid: uid, access: .unknown, deviceId: bad).isEmpty)
        }
    }

    // MARK: - Le magasin refuse ce que le garde refuse

    func testTheStoreNeverSendsAStatusWithoutTheKey() async {
        let store = FakeMiffStore()
        let unstamped = DocWrite(path: statusPath, kind: .set, data: ["presence": .string("home")])

        do {
            try await store.commit([unstamped])
            XCTFail("un statut sans identifiant de session ne doit pas partir")
        } catch {
            XCTAssertTrue(error is InvalidWriteError)
        }
        XCTAssertTrue(store.commitsAsLots.isEmpty)
    }
}
