import XCTest
@testable import Miff

/// Comment une erreur d'écriture du choix « statistiques et plantages » est lue (ONB-3b) : seul un vrai refus du serveur arrête les essais
/// d'un retrait ; un conflit, une erreur interne ou un quota momentané sont des « pas maintenant », repris avec des pauses croissantes.
final class ConsentWriteErrorsTests: XCTestCase {
    private func firestoreError(_ code: Int) -> Error {
        NSError(domain: FirestoreErrorNumber.domain, code: code)
    }

    func testATransientErrorIsRetriedLikeALackOfNetwork() {
        for code in [
            FirestoreErrorNumber.aborted, FirestoreErrorNumber.internalError, FirestoreErrorNumber.resourceExhausted,
            FirestoreErrorNumber.unavailable, FirestoreErrorNumber.deadlineExceeded, FirestoreErrorNumber.cancelled
        ] {
            XCTAssertEqual(interpretConsentWriteError(firestoreError(code)), .offline, "code \(code)")
        }
    }

    func testOnlyARealRefusalStopsTheTries() {
        XCTAssertEqual(interpretConsentWriteError(firestoreError(FirestoreErrorNumber.permissionDenied)), .refused)
        XCTAssertEqual(interpretConsentWriteError(firestoreError(FirestoreErrorNumber.unauthenticated)), .refused)
    }

    func testAnUnknownErrorIsAFailureAndAUrlErrorIsALackOfNetwork() {
        XCTAssertEqual(interpretConsentWriteError(firestoreError(5)), .failed)
        XCTAssertEqual(interpretConsentWriteError(NSError(domain: "autre", code: 1)), .failed)
        XCTAssertEqual(interpretConsentWriteError(NSError(domain: NSURLErrorDomain, code: -1009)), .offline)
    }

    func testTheProfileWritesKeepTheirOwnReading() {
        // Les écritures du profil ne changent pas : un conflit y reste un échec (`failed`), pas un « hors ligne ».
        XCTAssertEqual(interpretWriteError(firestoreError(FirestoreErrorNumber.aborted)), .failed)
    }
}
