import XCTest
@testable import Miff

/// Le diagnostic interne n'existe que dans les configurations Debug et Internal (critère I13).
/// La preuve pour Release est faite par la CI sur le binaire Release lui-même (ios/ci/check-binaries.sh) ; ce test
/// vérifie que la compilation conditionnelle suit bien le nom de la configuration Xcode.
final class BuildFlavorTests: XCTestCase {
    func testDiagnosticIsPresentExactlyInDebugAndInternalBuilds() {
        let configuration = BuildInfo.current.configuration
        let expected = configuration == "Debug" || configuration == "Internal"

        XCTAssertEqual(BuildFlavor.includesDiagnostic, expected, "Configuration : \(configuration)")
    }

    func testDiagnosticEventIsAllowedOnlyWhenTheDiagnosticIsPresent() {
        XCTAssertEqual(
            AnalyticsContract.allowedEvents["analytics_diagnostic_test"] != nil,
            BuildFlavor.includesDiagnostic
        )
    }
}
