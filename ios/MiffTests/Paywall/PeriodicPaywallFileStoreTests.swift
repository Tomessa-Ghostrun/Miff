import XCTest
@testable import Miff

/// Ce que l'offre périodique retient sur l'appareil (critères PP4 et PP6) : UN entier, rien d'autre ; un aller-retour, une valeur absurde, un fichier abîmé, l'effacement
/// (déconnexion, suppression du compte), l'exclusion des sauvegardes. Miroir de `PeriodicPaywallStoreTest` d'Android.
final class PeriodicPaywallFileStoreTests: XCTestCase {
    private var directory: URL!

    override func setUp() {
        super.setUp()
        directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("periodic-paywall-\(UUID().uuidString)", isDirectory: true)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: directory)
        super.tearDown()
    }

    private func store() -> PeriodicPaywallFileStore {
        PeriodicPaywallFileStore(directory: directory)
    }

    private var fileURL: URL {
        directory.appendingPathComponent("periodic-paywall.json")
    }

    func testAFreshDeviceHasCountedNothing() {
        XCTAssertEqual(store().read(), 0)
    }

    func testWhatIsWrittenIsReadBackByAnotherInstance() {
        store().write(7)

        XCTAssertEqual(store().read(), 7)
        store().write(0)
        XCTAssertEqual(store().read(), 0)
    }

    func testTheFileContainsOneWholeNumberAndNothingElse() throws {
        store().write(7)

        let object = try JSONSerialization.jsonObject(with: Data(contentsOf: fileURL))
        let dictionary = try XCTUnwrap(object as? [String: Any])

        XCTAssertEqual(Set(dictionary.keys), ["openings"], "aucune date, aucun identifiant, aucun nom, aucune position")
        XCTAssertEqual(dictionary["openings"] as? Int, 7)
        let text = try String(contentsOf: fileURL, encoding: .utf8)
        XCTAssertLessThan(text.count, 30, "un seul petit entier")
    }

    func testAValueThatIsNotACounterIsBroughtBackToTheLimit() {
        store().write(5_000)
        XCTAssertEqual(store().read(), PeriodicPaywallFileStore.maxStored)

        store().write(-4)
        XCTAssertEqual(store().read(), 0)
    }

    func testAnUnreadableFileReadsAsZeroNeverACrash() throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data("pas du JSON".utf8).write(to: fileURL)

        XCTAssertEqual(store().read(), 0)
    }

    func testAFileOfAnUnknownFormatReadsAsZero() throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data(#"{"openings":"beaucoup"}"#.utf8).write(to: fileURL)

        XCTAssertEqual(store().read(), 0)
    }

    func testClearForgetsTheCounterAndRemovesTheFile() {
        let first = store()
        first.write(9)

        first.clear()

        XCTAssertEqual(first.read(), 0)
        XCTAssertEqual(store().read(), 0)
        XCTAssertFalse(FileManager.default.fileExists(atPath: fileURL.path))
    }

    func testClearWithNothingStoredIsHarmless() {
        store().clear()

        XCTAssertEqual(store().read(), 0)
    }

    func testTheFolderIsExcludedFromEveryBackup() throws {
        store().write(3)

        let values = try directory.resourceValues(forKeys: [.isExcludedFromBackupKey])

        XCTAssertEqual(values.isExcludedFromBackup, true)
    }
}
