import XCTest
@testable import Miff

final class BuildInfoTests: XCTestCase {
    func testSummaryShowsVersionAndBuild() {
        let info = BuildInfo(version: "0.1.0", build: "12")

        XCTAssertEqual(info.summary, "Version 0.1.0 · build 12")
    }

    func testInfoDictionaryValuesAreRead() {
        let info = BuildInfo(infoDictionary: [
            "CFBundleShortVersionString": "1.2.3",
            "CFBundleVersion": "45"
        ])

        XCTAssertEqual(info, BuildInfo(version: "1.2.3", build: "45"))
    }

    func testMissingOrBlankValuesFallBackToPlaceholder() {
        XCTAssertEqual(
            BuildInfo(infoDictionary: nil),
            BuildInfo(version: BuildInfo.placeholder, build: BuildInfo.placeholder)
        )
        XCTAssertEqual(BuildInfo(version: "  \n", build: "").summary, "Version ? · build ?")
    }

    func testValuesAreTrimmed() {
        XCTAssertEqual(BuildInfo(version: " 0.1.0 ", build: "7\n").summary, "Version 0.1.0 · build 7")
    }
}
