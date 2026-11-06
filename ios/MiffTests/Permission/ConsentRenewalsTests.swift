import XCTest
@testable import Miff

/// Le renouvellement du consentement (LOC-4a, règle R-LIEU-3 ; miroir de `ConsentRenewalsTest` d'Android) : ce qui est regardé, et
/// le seul lot écrit après un « J'accepte ».
final class ConsentRenewalsTests: XCTestCase {
    private let store = FakeMiffStore()
    private let uid = "uid-1"
    private let first = testMiffId
    private let second = testOtherMiffId

    private func renewals() -> FirestoreConsentRenewals {
        FirestoreConsentRenewals(store: store, timeoutSeconds: 1)
    }

    private func member(_ miffId: String, enabled: Bool = true, version: String? = "2026-10-v2") {
        store.consents[miffId] = SharingConsentRead(enabled: enabled, version: version)
    }

    private func dueMiffs(_ check: ConsentRenewalCheck) -> [RenewalMiff] {
        if case .due(let miffs) = check {
            return miffs
        }
        XCTFail("un renouvellement était attendu : \(check)")
        return []
    }

    // MARK: - La vérification

    func testAnOldTextWhereThePersonSharesIsDueWithTheNameOfTheMiff() async {
        store.memberships = [MembershipRef(key: "free", miffId: first)]
        store.miffs[first] = testMiffDetails(name: "Chez nous")
        member(first)

        let miffs = dueMiffs(await renewals().check(uid: uid))

        XCTAssertEqual(miffs.map { $0.miffId }, [first])
        XCTAssertEqual(miffs.first?.name, "Chez nous")
    }

    func testBothOldTextsOfOctoberAreDueWhateverTheirShape() async {
        store.memberships = [MembershipRef(key: first, miffId: first), MembershipRef(key: second, miffId: second)]
        member(first, version: "2026-10-v1")
        member(second, version: "2026-10-v2")

        let miffs = dueMiffs(await renewals().check(uid: uid))

        XCTAssertEqual(miffs.map { $0.miffId }, [first, second])
    }

    func testTheCurrentTextAStoppedSharingOrNoCardIsNothingToRenew() async {
        store.memberships = [MembershipRef(key: "a", miffId: first), MembershipRef(key: "b", miffId: second)]
        member(first, version: MiffSharingConsent.version)
        member(second, enabled: false, version: "2026-10-v1")
        let current = await renewals().check(uid: uid)
        XCTAssertEqual(current, .nothingToRenew)

        store.consents.removeAll()
        let noCard = await renewals().check(uid: uid)
        XCTAssertEqual(noCard, .nothingToRenew)
    }

    func testANewerTextThanThisBuildIsNeverRenewedBackwards() async {
        store.memberships = [MembershipRef(key: "free", miffId: first)]
        member(first, version: "2026-12-v04")

        let check = await renewals().check(uid: uid)

        XCTAssertEqual(check, .nothingToRenew)
    }

    func testNoNetworkIsUnknownNotAClaim() async {
        store.memberships = [MembershipRef(key: "free", miffId: first)]
        store.consents[first] = firestoreError(FirestoreErrorNumber.unavailable)
        let readFailed = await renewals().check(uid: uid)
        XCTAssertEqual(readFailed, .unknown)

        store.membershipsFailure = firestoreError(FirestoreErrorNumber.unavailable)
        let indexFailed = await renewals().check(uid: uid)
        XCTAssertEqual(indexFailed, .unknown)
    }

    func testARefusalMeansNotInThatMiffAnyMoreNothingToRenewThere() async {
        store.memberships = [MembershipRef(key: "free", miffId: first)]
        store.consents[first] = firestoreError(FirestoreErrorNumber.permissionDenied)

        let check = await renewals().check(uid: uid)

        XCTAssertEqual(check, .nothingToRenew)
    }

    func testTheCheckWritesNothingAndReadsFromTheServer() async {
        store.memberships = [MembershipRef(key: "free", miffId: first)]
        member(first)

        _ = await renewals().check(uid: uid)

        XCTAssertTrue(store.commitsAsLots.isEmpty)
        XCTAssertTrue(store.calls.contains("memberships:server"), "la liste des Miffs est lue auprès du SERVEUR")
        XCTAssertFalse(store.calls.contains("commit"))
        XCTAssertFalse(store.calls.contains("commitWhereSharing"))
    }

    func testTheCreatorIsToldApartFromAMemberAndAnUnreadableNameIsEmpty() async {
        store.memberships = [MembershipRef(key: "free", miffId: first)]
        member(first)
        store.miffs[first] = testMiffDetails(name: "Chez nous", owner: uid)
        let asCreator = dueMiffs(await renewals().check(uid: uid)).first
        XCTAssertEqual(asCreator?.isOwner, true)

        store.miffs[first] = testMiffDetails(name: "Chez nous", owner: "someone-else")
        let asMember = dueMiffs(await renewals().check(uid: uid)).first
        XCTAssertEqual(asMember?.isOwner, false)

        store.miffs[first] = nil
        let unnamed = dueMiffs(await renewals().check(uid: uid)).first
        XCTAssertEqual(unnamed?.name, "")
    }

    func testAMiffIsLookedAtOnlyOnce() async {
        store.memberships = [MembershipRef(key: "a", miffId: first), MembershipRef(key: "b", miffId: first)]
        member(first)

        let miffs = dueMiffs(await renewals().check(uid: uid))

        XCTAssertEqual(miffs.count, 1)
        XCTAssertEqual(store.calls.filter { $0 == "consent:\(first)" }.count, 1)
    }

    // MARK: - Le renouvellement

    func testRenewIsOneLotOfOneUpdatePerMiffToTheCurrentVersion() async throws {
        member(first)
        member(second)

        let result = await renewals().renew(uid: uid, miffIds: [first, second, first])

        XCTAssertEqual(result, .done)
        XCTAssertEqual(store.commitsAsLots.count, 1, "UN seul lot")
        let lot = try XCTUnwrap(store.commitsAsLots.first)
        XCTAssertEqual(lot.map { $0.path }, ["miffs/\(first)/members/\(uid)", "miffs/\(second)/members/\(uid)"])
        XCTAssertTrue(lot.allSatisfy { $0.kind == .update })
        guard case .map(let sharing)? = lot.first?.data["sharing"] else {
            return XCTFail("sharing doit être une carte")
        }
        XCTAssertEqual(sharing["consentVersion"], .string("2026-11-v03"))
        XCTAssertEqual(sharing["enabled"], .bool(true))
        XCTAssertEqual(sharing["consentedAt"], .serverTimestamp)
        XCTAssertEqual(sharing["revokedAt"], .null)
    }

    func testRenewNeverRewritesAMiffAlreadyRenewedMeanwhileOnAnotherPhone() async {
        // A est due ; B a été renouvelée ailleurs entre la vérification et le « J'accepte » : seule A est écrite (les règles
        // refuseraient B, et tout le lot avec elle : la personne verrait un échec pour rien).
        member(first, version: "2026-10-v2")
        member(second, version: MiffSharingConsent.version)

        let result = await renewals().renew(uid: uid, miffIds: [first, second])

        XCTAssertEqual(result, .done)
        XCTAssertEqual(store.commitsAsLots.count, 1)
        XCTAssertEqual(store.commitsAsLots.first?.map { $0.path }, ["miffs/\(first)/members/\(uid)"])
    }

    func testRenewReadsTheCardInTheTransactionAndNeverTurnsAStoppedSharingBackOn() async {
        member(first)
        // Arrêté entre-temps (autre téléphone) : rien n'est écrit pour elle, l'autre Miff est renouvelée.
        member(second, enabled: false)

        let result = await renewals().renew(uid: uid, miffIds: [first, second])

        XCTAssertEqual(result, .done)
        XCTAssertEqual(store.commitsAsLots.first?.map { $0.path }, ["miffs/\(first)/members/\(uid)"])
        XCTAssertTrue(store.calls.contains("commitWhereSharing"))

        // Plus personne ne partage : rien n'est écrit du tout.
        store.clearLots()
        member(first, enabled: false)
        let none = await renewals().renew(uid: uid, miffIds: [first])
        XCTAssertEqual(none, .done)
        XCTAssertTrue(store.commitsAsLots.isEmpty)
    }

    func testRenewRefusesABadIdentifierOrAnEmptyListBeforeAnythingIsWritten() async {
        let empty = await renewals().renew(uid: uid, miffIds: [])
        XCTAssertEqual(empty, .failed(.unknown))
        let bad = await renewals().renew(uid: uid, miffIds: ["../x"])
        XCTAssertEqual(bad, .failed(.unknown))
        let noUid = await renewals().renew(uid: "", miffIds: [first])
        XCTAssertEqual(noUid, .failed(.unknown))
        XCTAssertTrue(store.commitsAsLots.isEmpty)
        XCTAssertFalse(store.calls.contains("commitWhereSharing"))
    }

    func testAFailedLotIsAFailureWritesNothingAndIsNeverRetriedByItself() async {
        store.memberships = [MembershipRef(key: "free", miffId: first)]
        member(first)
        store.commitFailures = [firestoreError(FirestoreErrorNumber.unavailable)]

        let result = await renewals().renew(uid: uid, miffIds: [first])

        XCTAssertEqual(result, .failed(.offline))
        XCTAssertTrue(store.commitsAsLots.isEmpty, "un lot refusé n'écrit rien")
        XCTAssertEqual(store.calls.filter { $0 == "commitWhereSharing" }.count, 1, "jamais rejoué tout seul")
    }

    func testARefusalBecauseAnotherPhoneAlreadyRenewedCountsAsDone() async {
        store.memberships = [MembershipRef(key: "free", miffId: first)]
        // L'autre téléphone a déjà écrit la version en vigueur : les règles veulent une version strictement plus récente.
        member(first, version: MiffSharingConsent.version)
        store.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied)]

        let result = await renewals().renew(uid: uid, miffIds: [first])

        XCTAssertEqual(result, .done)
    }
}
