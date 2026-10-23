import XCTest
@testable import Miff

final class BuildInfoTests: XCTestCase {
    func testInfoDictionaryValuesAreRead() {
        let info = BuildInfo(infoDictionary: [
            "CFBundleShortVersionString": "1.2.3",
            "CFBundleVersion": "45",
            "MiffBuildConfiguration": "Internal"
        ])

        XCTAssertEqual(info, BuildInfo(version: "1.2.3", build: "45", configuration: "Internal"))
    }

    func testMissingOrBlankValuesFallBackToPlaceholder() {
        let placeholder = BuildInfo.placeholder

        XCTAssertEqual(
            BuildInfo(infoDictionary: nil),
            BuildInfo(version: placeholder, build: placeholder, configuration: placeholder)
        )
        XCTAssertEqual(BuildInfo(version: "  \n", build: "").version, placeholder)
        XCTAssertEqual(BuildInfo(version: "  \n", build: "").build, placeholder)
    }

    func testValuesAreTrimmed() {
        let info = BuildInfo(version: " 0.1.0 ", build: "7\n", configuration: " Debug ")

        XCTAssertEqual(info.version, "0.1.0")
        XCTAssertEqual(info.build, "7")
        XCTAssertEqual(info.configuration, "Debug")
    }

    func testTheRunningBuildReportsItsConfiguration() {
        // La CI exécute les tests en configuration Debug.
        XCTAssertEqual(BuildInfo.current.configuration, "Debug")
    }
}
