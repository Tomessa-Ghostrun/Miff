import XCTest
@testable import Miff

/// Les lots du ticket Miff §5.4, §5.7 et §5.13 face au contrat de backend/firestore.rules et data-model.md §8 : l'ensemble exact
/// des clés de chaque document (les règles refusent une clé manquante et une clé inconnue), les dates du serveur, le
/// consentement explicite écrit avec l'entrée, aucune coordonnée nulle part (critères C4, K2, K5, K6, V1).
final class MiffWritesTests: XCTestCase {
    private let uid = "uid-1"
    private let miffId = testMiffId
    private let code = testCode
    private let profile = ProfileCopy(
        firstName: "Léa",
        lastName: "Martin",
        phone: "+33612345678",
        avatarJpeg: Data(repeating: 1, count: 100)
    )

    private func entry(
        key: String = "free",
        access: LocationAccess = .unknown,
        profile customProfile: ProfileCopy? = nil
    ) -> MiffEntry {
        MiffEntry(uid: uid, miffId: miffId, membershipKey: key, profile: customProfile ?? profile, access: access)
    }

    private func write(_ writes: [DocWrite], _ path: String) throws -> DocWrite {
        let matching = writes.filter { $0.path == path }
        XCTAssertEqual(matching.count, 1, "un seul document à \(path)")
        return try XCTUnwrap(matching.first)
    }

    private func sharing(of card: [String: FieldValueSpec]) -> [String: FieldValueSpec] {
        guard case .map(let nested)? = card["sharing"] else {
            XCTFail("la fiche n'a pas de partage")
            return [:]
        }
        return nested
    }

    // MARK: - Créer

    func testCreateIsFiveWritesInOneLotWithTheRightPaths() {
        let writes = MiffWrites.create(entry: entry(), code: code, name: "  Chez nous ")

        XCTAssertEqual(
            Set(writes.map { $0.path }),
            [
                "miffs/\(miffId)",
                "joinCodes/\(code)",
                "miffs/\(miffId)/members/\(uid)",
                "users/\(uid)/memberships/free",
                "miffs/\(miffId)/status/\(uid)"
            ]
        )
        XCTAssertEqual(writes.count, 5)
        XCTAssertTrue(writes.allSatisfy { $0.kind == .set })
    }

    func testCreateMiffAndCodeHaveExactlyTheKeysOfTheRulesAndTheTrimmedName() throws {
        let writes = MiffWrites.create(entry: entry(), code: code, name: "  Chez nous ")

        let miff = try write(writes, "miffs/\(miffId)").data
        XCTAssertEqual(Set(miff.keys), ["ownerUid", "name", "joinCode", "createdAt"])
        XCTAssertEqual(miff["name"], .string("Chez nous"))
        XCTAssertEqual(miff["ownerUid"], .string(uid))
        XCTAssertEqual(miff["joinCode"], .string(code))
        XCTAssertEqual(miff["createdAt"], .serverTimestamp)
        let joinCode = try write(writes, "joinCodes/\(code)").data
        XCTAssertEqual(Set(joinCode.keys), ["miffId", "miffName", "createdAt"])
        XCTAssertEqual(joinCode["miffName"], .string("Chez nous"))
        XCTAssertEqual(joinCode["miffId"], .string(miffId))
    }

    func testCreateOwnerCardHasAllKeysNoJoinCodeAndTheExplicitConsentOfTheEntry() throws {
        let writes = MiffWrites.create(entry: entry(), code: code, name: "Chez nous")

        let card = try write(writes, "miffs/\(miffId)/members/\(uid)").data

        XCTAssertEqual(Set(card.keys), ["role", "firstName", "lastName", "avatar", "phone", "joinedAt", "sharing"])
        XCTAssertEqual(card["role"], .string("owner"))
        XCTAssertEqual(card["phone"], .string("+33612345678"))
        XCTAssertEqual(card["joinedAt"], .serverTimestamp)
        XCTAssertEqual(card["avatar"], .bytes(Data(repeating: 1, count: 100)))
        let nested = sharing(of: card)
        XCTAssertEqual(Set(nested.keys), ["enabled", "consentedAt", "consentVersion", "revokedAt"])
        XCTAssertEqual(nested["enabled"], .bool(true))
        XCTAssertEqual(nested["consentedAt"], .serverTimestamp)
        XCTAssertEqual(nested["consentVersion"], .string(MiffSharingConsent.version))
        XCTAssertEqual(nested["revokedAt"], .null)
    }

    func testCreateCardWithoutPhotoHasNoAvatarKeyNotANullOne() throws {
        let noPhoto = ProfileCopy(firstName: "Léa", lastName: "Martin", phone: "+33612345678", avatarJpeg: nil)
        let writes = MiffWrites.create(entry: entry(profile: noPhoto), code: code, name: "Chez nous")

        let card = try write(writes, "miffs/\(miffId)/members/\(uid)").data

        XCTAssertNil(card["avatar"])
        XCTAssertFalse(card.keys.contains("avatar"))
    }

    func testCreateIndexKeyIsFreeForAFreeAccountAndTheMiffIdForAPremiumOne() throws {
        let free = MiffWrites.create(entry: entry(), code: code, name: "Chez nous")
        let freeIndex = try write(free, "users/\(uid)/memberships/free")
        XCTAssertEqual(Set(freeIndex.data.keys), ["miffId", "joinedAt"])
        XCTAssertEqual(freeIndex.data["miffId"], .string(miffId))

        let premium = MiffWrites.create(entry: entry(key: miffId), code: code, name: "Chez nous")
        XCTAssertEqual(try write(premium, "users/\(uid)/memberships/\(miffId)").kind, .set)
    }

    func testCreateInitialStatusIsUnknownWithTheRealPermissionAndNoCoordinate() throws {
        let writes = MiffWrites.create(entry: entry(access: .foreground), code: code, name: "Chez nous")

        let status = try write(writes, "miffs/\(miffId)/status/\(uid)").data

        XCTAssertEqual(
            Set(status.keys),
            [
                "presence", "placeId", "presenceAt", "lastUpdateAt", "updateText", "hasPhoto", "batteryPct",
                "locationAccess", "escalationDueAt", "updatedAt"
            ]
        )
        XCTAssertEqual(status["presence"], .string("unknown"))
        XCTAssertEqual(status["locationAccess"], .string("foreground"))
        XCTAssertEqual(status["hasPhoto"], .bool(false))
        XCTAssertEqual(status["placeId"], .null)
        XCTAssertEqual(status["batteryPct"], .null)
        XCTAssertEqual(status["updatedAt"], .serverTimestamp)
        XCTAssertEqual(status["presenceAt"], .serverTimestamp)
        XCTAssertEqual(status["lastUpdateAt"], .serverTimestamp)
    }

    func testNoLotEverWritesACoordinateAnAddressOrAPlaceName() {
        let forbidden: Set<String> = [
            "lat", "lng", "lon", "latitude", "longitude", "address", "coords", "coordinates", "position"
        ]
        let all = MiffWrites.create(entry: entry(), code: code, name: "Chez nous")
            + MiffWrites.join(entry: entry(), code: code)
            + MiffWrites.rename(miffId: miffId, code: code, name: "Nouveau")
            + MiffWrites.stopSharing(miffId: miffId, uid: uid)
            + MiffWrites.resumeSharing(miffId: miffId, uid: uid, access: .unknown)
            + MiffWrites.leave(miffId: miffId, uid: uid, membershipKey: "free")

        for item in all {
            for key in item.data.keys {
                XCTAssertFalse(forbidden.contains(key.lowercased()), key)
            }
        }
    }

    func testCreateRefusesABadNameOrCodeBeforeAnythingIsWritten() {
        XCTAssertTrue(MiffWrites.create(entry: entry(), code: code, name: "a").isEmpty)
        XCTAssertTrue(MiffWrites.create(entry: entry(), code: "XTZRSP36798I", name: "Chez nous").isEmpty)
        XCTAssertTrue(MiffWrites.create(entry: entry(), code: "", name: "Chez nous").isEmpty)
    }

    // MARK: - Rejoindre

    func testJoinIsThreeWritesCardWithTheCodeIndexAndInitialStatus() throws {
        let writes = MiffWrites.join(entry: entry(access: .denied), code: code)

        XCTAssertEqual(
            Set(writes.map { $0.path }),
            ["miffs/\(miffId)/members/\(uid)", "users/\(uid)/memberships/free", "miffs/\(miffId)/status/\(uid)"]
        )
        XCTAssertEqual(writes.count, 3)
        let card = try write(writes, "miffs/\(miffId)/members/\(uid)").data
        XCTAssertEqual(
            Set(card.keys),
            ["role", "firstName", "lastName", "avatar", "phone", "joinedAt", "joinCode", "sharing"]
        )
        XCTAssertEqual(card["role"], .string("member"))
        XCTAssertEqual(card["joinCode"], .string(code))
        XCTAssertEqual(sharing(of: card)["enabled"], .bool(true))
        XCTAssertEqual(sharing(of: card)["consentVersion"], .string(MiffSharingConsent.version))
        let status = try write(writes, "miffs/\(miffId)/status/\(uid)").data
        XCTAssertEqual(status["locationAccess"], .string("denied"))
    }

    func testJoinRefusesAnInvalidCode() {
        XCTAssertTrue(MiffWrites.join(entry: entry(), code: "short").isEmpty)
    }

    func testConsentVersionFitsTheRulesOneToTwentyCharacters() {
        XCTAssertTrue((1...20).contains(MiffSharingConsent.version.count))
        XCTAssertEqual(MiffSharingConsent.softMemberLimit, 15)
    }

    // MARK: - Renommer, partager

    func testRenameUpdatesTheMiffAndTheNameKeptWithTheCodeInTheSameLot() throws {
        let writes = MiffWrites.rename(miffId: miffId, code: code, name: " Nouvelle  Miff ")

        XCTAssertEqual(writes.count, 2)
        XCTAssertEqual(try write(writes, "miffs/\(miffId)").data, ["name": .string("Nouvelle  Miff")])
        XCTAssertEqual(try write(writes, "joinCodes/\(code)").data, ["miffName": .string("Nouvelle  Miff")])
        XCTAssertTrue(writes.allSatisfy { $0.kind == .update })
        XCTAssertTrue(MiffWrites.rename(miffId: miffId, code: code, name: "x").isEmpty)
    }

    func testStopSharingMarksTheCardAndDeletesTheStatusAndThePhotoInTheSameLot() throws {
        let writes = MiffWrites.stopSharing(miffId: miffId, uid: uid)

        XCTAssertEqual(writes.count, 3)
        let card = try write(writes, "miffs/\(miffId)/members/\(uid)")
        XCTAssertEqual(card.kind, .update)
        // Deux sous-champs seulement changent : la date et la version du premier consentement restent telles quelles.
        XCTAssertEqual(Set(card.data.keys), ["sharing.enabled", "sharing.revokedAt"])
        XCTAssertEqual(card.data["sharing.enabled"], .bool(false))
        XCTAssertEqual(card.data["sharing.revokedAt"], .serverTimestamp)
        XCTAssertEqual(try write(writes, "miffs/\(miffId)/status/\(uid)").kind, .delete)
        XCTAssertEqual(try write(writes, "miffs/\(miffId)/photos/\(uid)").kind, .delete)
    }

    func testResumeSharingWritesANewConsentAndTheInitialStatus() throws {
        let writes = MiffWrites.resumeSharing(miffId: miffId, uid: uid, access: .background)

        XCTAssertEqual(writes.count, 2)
        let card = try write(writes, "miffs/\(miffId)/members/\(uid)")
        XCTAssertEqual(card.kind, .update)
        XCTAssertEqual(card.data["sharing.enabled"], .bool(true))
        XCTAssertEqual(card.data["sharing.consentedAt"], .serverTimestamp)
        XCTAssertEqual(card.data["sharing.consentVersion"], .string(MiffSharingConsent.version))
        XCTAssertEqual(card.data["sharing.revokedAt"], .null)
        let status = try write(writes, "miffs/\(miffId)/status/\(uid)")
        XCTAssertEqual(status.kind, .set)
        XCTAssertEqual(status.data["presence"], .string("unknown"))
        XCTAssertEqual(status.data["locationAccess"], .string("background"))
    }

    // MARK: - Nettoyage

    func testLeaveIsFourDeletionsAndDropIndexIsOne() {
        let leave = MiffWrites.leave(miffId: miffId, uid: uid, membershipKey: "free")

        XCTAssertEqual(leave.count, 4)
        XCTAssertTrue(leave.allSatisfy { $0.kind == .delete })
        XCTAssertEqual(
            Set(leave.map { $0.path }),
            [
                "miffs/\(miffId)/members/\(uid)", "miffs/\(miffId)/status/\(uid)", "miffs/\(miffId)/photos/\(uid)",
                "users/\(uid)/memberships/free"
            ]
        )
        let drop = MiffWrites.dropIndex(uid: uid, membershipKey: "free")
        XCTAssertEqual(drop.map { $0.path }, ["users/\(uid)/memberships/free"])
        XCTAssertEqual(drop.first?.kind, .delete)
    }

    // MARK: - Copie du profil et erreurs

    func testProfileCopyNeedsNamesAndAValidPhone() {
        let good: [String: Any] = ["firstName": "Léa", "lastName": "Martin", "phone": "+33612345678"]

        XCTAssertNotNil(ProfileCopy.from(fields: good))
        XCTAssertNil(ProfileCopy.from(fields: nil))
        XCTAssertNil(ProfileCopy.from(fields: good.filter { $0.key != "phone" }))
        XCTAssertNil(ProfileCopy.from(fields: good.merging(["phone": "0612345678"]) { _, new in new }))
        XCTAssertNil(ProfileCopy.from(fields: good.filter { $0.key != "firstName" }))
    }

    func testProfileCopyDropsAnEmptyOrTooBigPhoto() {
        let good: [String: Any] = ["firstName": "Léa", "lastName": "Martin", "phone": "+33612345678"]

        func avatar(_ bytes: Int) -> Data? {
            ProfileCopy.from(fields: good.merging(["avatar": Data(count: bytes)]) { _, new in new })?.avatarJpeg
        }

        // Une photo vide n'est pas une photo ; une photo de plus de 20 000 octets est écartée (les règles refuseraient la fiche).
        XCTAssertNil(avatar(0))
        XCTAssertNil(avatar(20_001))
        XCTAssertNotNil(avatar(20_000))
    }

    func testProfileCopyShowsNothingInADescription() {
        XCTAssertFalse(String(describing: profile).contains("Léa"))
        XCTAssertFalse(String(describing: profile).contains("3361"))
        XCTAssertFalse(String(describing: MiffWrites.join(entry: entry(), code: code)[0]).contains(code))
    }

    func testErrorsAreReducedToACalmCategoryAndANetworkCutIsNeverALossOfAccess() {
        XCTAssertEqual(interpretMiffError(firestoreError(FirestoreErrorNumber.unavailable)), .offline)
        XCTAssertEqual(interpretMiffError(firestoreError(FirestoreErrorNumber.deadlineExceeded)), .offline)
        // « Quota » est dit « pas de connexion » (critère H4).
        XCTAssertEqual(interpretMiffError(firestoreError(FirestoreErrorNumber.resourceExhausted)), .offline)
        XCTAssertEqual(interpretMiffError(firestoreError(FirestoreErrorNumber.permissionDenied)), .refused)
        XCTAssertEqual(interpretMiffError(firestoreError(FirestoreErrorNumber.unauthenticated)), .refused)
        XCTAssertEqual(interpretMiffError(NSError(domain: NSURLErrorDomain, code: NSURLErrorTimedOut)), .offline)
        XCTAssertEqual(interpretMiffError(OperationTimedOut()), .offline)
        XCTAssertEqual(interpretMiffError(MissingProfileError()), .noProfile)
        XCTAssertEqual(interpretMiffError(NSError(domain: "autre", code: 3)), .unknown)
        XCTAssertEqual(interpretMiffError(firestoreError(5)), .unknown)

        XCTAssertTrue(isAccessLostError(firestoreError(FirestoreErrorNumber.permissionDenied)))
        XCTAssertFalse(isAccessLostError(firestoreError(FirestoreErrorNumber.unavailable)))
        XCTAssertFalse(isAccessLostError(firestoreError(FirestoreErrorNumber.deadlineExceeded)))
        XCTAssertFalse(isAccessLostError(NSError(domain: "autre", code: FirestoreErrorNumber.permissionDenied)))
    }

    func testEveryFailureHasACalmMessage() {
        XCTAssertEqual(ActionMessage(failure: .offline), .offline)
        XCTAssertEqual(ActionMessage(failure: .noProfile), .noProfile)
        XCTAssertEqual(ActionMessage(failure: .refused), .generic)
        XCTAssertEqual(ActionMessage(failure: .unknown), .generic)
    }
}
