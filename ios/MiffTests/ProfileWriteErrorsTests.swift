import XCTest
@testable import Miff

/// Les erreurs d'écriture du profil ne montrent jamais ni code, ni message, ni valeur : ils peuvent citer le document.
final class ProfileWriteErrorsTests: XCTestCase {
    private func firestoreError(_ code: Int, message: String = "") -> NSError {
        NSError(domain: FirestoreErrorNumber.domain, code: code, userInfo: [NSLocalizedDescriptionKey: message])
    }

    func testNetworkAndTimeoutProblemsMeanNothingWasWritten() {
        XCTAssertEqual(interpretWriteError(firestoreError(FirestoreErrorNumber.unavailable)), .offline)
        XCTAssertEqual(interpretWriteError(firestoreError(FirestoreErrorNumber.deadlineExceeded)), .offline)
        XCTAssertEqual(interpretWriteError(firestoreError(FirestoreErrorNumber.cancelled)), .offline)
        XCTAssertEqual(interpretWriteError(NSError(domain: NSURLErrorDomain, code: -1009)), .offline)
    }

    func testRulesAndAppCheckRefusalsAreNotNetworkProblems() {
        XCTAssertEqual(interpretWriteError(firestoreError(FirestoreErrorNumber.permissionDenied)), .refused)
        XCTAssertEqual(interpretWriteError(firestoreError(FirestoreErrorNumber.unauthenticated)), .refused)
    }

    func testAnythingElseFailsWithoutLeakingTheDocument() {
        XCTAssertEqual(interpretWriteError(firestoreError(13, message: "firstName Camille phone +33612345678")), .failed)
        XCTAssertEqual(interpretWriteError(NSError(domain: "Autre", code: 14)), .failed)
    }

    func testAnOlderProfileKeepsOnlyWhatTheRulesAllow() {
        let photo = Data(repeating: 7, count: 1_000)
        let seed = ProfileSeed.from(fields: ["firstName": "Léa", "lastName": "Martin", "avatar": photo, "phone": "+33600000000"])

        XCTAssertEqual(seed, ProfileSeed(firstName: "Léa", lastName: "Martin", avatarJpeg: photo))
    }

    func testAnOlderProfileWithAnOversizedOrEmptyPhotoKeepsNoPhoto() {
        let names: [String: Any] = ["firstName": "Léa", "lastName": "Martin"]

        XCTAssertNil(ProfileSeed.from(fields: names.merging(["avatar": Data(count: 20_001)]) { $1 })?.avatarJpeg)
        XCTAssertNil(ProfileSeed.from(fields: names.merging(["avatar": Data()]) { $1 })?.avatarJpeg)
        XCTAssertNil(ProfileSeed.from(fields: names)?.avatarJpeg)
    }

    func testAnOlderProfileWithoutNamesGivesNothing() {
        XCTAssertNil(ProfileSeed.from(fields: ["firstName": "Léa"]))
        XCTAssertNil(ProfileSeed.from(fields: ["firstName": 1, "lastName": 2]))
    }
}
