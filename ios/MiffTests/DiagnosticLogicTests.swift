import XCTest
@testable import Miff

/// Interprétation des résultats du diagnostic (mêmes cas que les tests Android, DiagnosticLogicTest).
final class DiagnosticLogicTests: XCTestCase {
    private func firestoreError(_ code: Int) -> NSError {
        NSError(domain: "FIRFirestoreErrorDomain", code: code)
    }

    func testPermissionDeniedMeansTheRulesAreActive() {
        XCTAssertEqual(interpretRulesProbeFailure(firestoreError(7)), .rulesActive)
    }

    func testUnavailableOrTimeoutMeansOffline() {
        XCTAssertEqual(interpretRulesProbeFailure(firestoreError(14)), .offline)
        XCTAssertEqual(interpretRulesProbeFailure(firestoreError(4)), .offline)
        XCTAssertEqual(interpretRulesProbeFailure(OperationTimeoutError()), .offline)
        XCTAssertEqual(
            interpretRulesProbeFailure(NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet)),
            .offline
        )
    }

    func testOtherFailuresAreInconclusiveAndNeverReportedAsProtected() {
        XCTAssertEqual(interpretRulesProbeFailure(firestoreError(16)), .inconclusive)
        XCTAssertEqual(interpretRulesProbeFailure(firestoreError(8)), .inconclusive)
        XCTAssertEqual(interpretRulesProbeFailure(NSError(domain: "Autre", code: 7)), .inconclusive)
    }

    func testOnlyTheMiffProjectIsExpected() {
        XCTAssertTrue(isExpectedProject("miff-3d283"))
        XCTAssertFalse(isExpectedProject("ghostrun-app"))
        XCTAssertFalse(isExpectedProject(nil))
        XCTAssertFalse(isExpectedProject("MIFF-3D283"))
    }

    // MARK: - entitlements/{uid} : même lecture que les règles de sécurité

    private let now = Date(timeIntervalSince1970: 1_000_000)

    func testMissingDocumentIsClassic() {
        XCTAssertEqual(interpretEntitlement(documentExists: false, active: nil, expiresAt: nil, now: now), .classic)
    }

    func testActiveAndNotExpiredIsPremium() {
        let expiry = now.addingTimeInterval(1)

        XCTAssertEqual(
            interpretEntitlement(documentExists: true, active: true, expiresAt: expiry, now: now),
            .premium(expiresAt: expiry)
        )
    }

    func testExpiredOrInactiveOrIncompleteIsClassic() {
        let past = now.addingTimeInterval(-1)
        let future = now.addingTimeInterval(1)

        XCTAssertEqual(interpretEntitlement(documentExists: true, active: true, expiresAt: past, now: now), .classic)
        XCTAssertEqual(interpretEntitlement(documentExists: true, active: true, expiresAt: now, now: now), .classic)
        XCTAssertEqual(interpretEntitlement(documentExists: true, active: false, expiresAt: future, now: now), .classic)
        XCTAssertEqual(interpretEntitlement(documentExists: true, active: true, expiresAt: nil, now: now), .classic)
        XCTAssertEqual(interpretEntitlement(documentExists: true, active: nil, expiresAt: future, now: now), .classic)
    }
}
