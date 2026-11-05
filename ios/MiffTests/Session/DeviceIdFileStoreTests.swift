import XCTest
@testable import Miff

/// Le fichier de l'identifiant de session (SESS-3) : privé, exclu des sauvegardes, jamais accepté s'il est abîmé. Ce n'est PAS le
/// Trousseau (qui survit à la désinstallation) : voir `DeviceIdFileStore`.
final class DeviceIdFileStoreTests: XCTestCase {
    private var directory = URL(fileURLWithPath: NSTemporaryDirectory())

    override func setUp() {
        super.setUp()
        directory = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("device-id-tests-\(UUID().uuidString)", isDirectory: true)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: directory)
        super.tearDown()
    }

    func testAnIdentifierIsKeptAndReadBackByAnotherInstance() {
        let store = DeviceIdFileStore(directory: directory)
        XCTAssertNil(store.read())

        XCTAssertTrue(store.save(testDeviceId))

        XCTAssertEqual(store.read(), testDeviceId)
        XCTAssertEqual(DeviceIdFileStore(directory: directory).read(), testDeviceId)
    }

    func testAnInvalidIdentifierIsNeverWritten() {
        let store = DeviceIdFileStore(directory: directory)

        XCTAssertFalse(store.save("trop-court"))
        XCTAssertFalse(store.save("contient/un/separateur/de/chemin"))
        XCTAssertNil(store.read())
    }

    func testClearForgetsTheIdentifier() {
        let store = DeviceIdFileStore(directory: directory)
        XCTAssertTrue(store.save(testDeviceId))

        store.clear()

        XCTAssertNil(store.read())
    }

    func testAFileThatWasTamperedWithIsErasedAndReadAsNoIdentifier() throws {
        let store = DeviceIdFileStore(directory: directory)
        XCTAssertTrue(store.save(testDeviceId))
        let file = directory.appendingPathComponent("device-id.txt")
        try Data("pas un identifiant!".utf8).write(to: file)

        XCTAssertNil(store.read())
        XCTAssertFalse(FileManager.default.fileExists(atPath: file.path))
    }

    func testTheFolderIsExcludedFromBackups() throws {
        let store = DeviceIdFileStore(directory: directory)
        XCTAssertTrue(store.save(testDeviceId))

        let values = try directory.resourceValues(forKeys: [.isExcludedFromBackupKey])

        XCTAssertEqual(values.isExcludedFromBackup, true)
    }

    func testANewIdentifierReplacesTheOldOne() {
        let store = DeviceIdFileStore(directory: directory)
        XCTAssertTrue(store.save(testDeviceId))

        XCTAssertTrue(store.save("another-device-id-0002"))

        XCTAssertEqual(store.read(), "another-device-id-0002")
    }
}
