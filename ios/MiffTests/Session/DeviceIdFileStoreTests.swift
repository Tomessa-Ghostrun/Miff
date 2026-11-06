import XCTest
@testable import Miff

/// Les fichiers de la session d'appareil (SESS-3) : privés, exclus des sauvegardes, jamais acceptés s'ils sont abîmés, et une
/// erreur de lecture n'est jamais lue comme « aucun identifiant ». Ce n'est PAS le Trousseau (qui survit à la désinstallation) :
/// voir `DeviceIdFileStore`.
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

    private var pendingMarkerExists: Bool {
        FileManager.default.fileExists(atPath: directory.appendingPathComponent("device-id.pending").path)
    }

    func testACandidateIsKeptThenConfirmedAndReadBackByAnotherInstance() {
        let store = DeviceIdFileStore(directory: directory)
        XCTAssertEqual(store.read(), .absent)

        XCTAssertTrue(store.saveCandidate(testDeviceId))
        XCTAssertEqual(store.read(), .candidate(testDeviceId))
        let afterKill = DeviceIdFileStore(directory: directory).read()
        XCTAssertEqual(afterKill, .candidate(testDeviceId), "survit à un processus tué")

        XCTAssertTrue(store.confirm())
        XCTAssertEqual(DeviceIdFileStore(directory: directory).read(), .confirmed(testDeviceId))
    }

    func testAnInvalidIdentifierIsNeverWritten() {
        let store = DeviceIdFileStore(directory: directory)

        XCTAssertFalse(store.saveCandidate("trop-court"))
        XCTAssertFalse(store.saveCandidate("contient/un/separateur/de/chemin"))
        XCTAssertEqual(store.read(), .absent)
    }

    func testClearForgetsTheIdentifierAndTheCandidateMarker() {
        let store = DeviceIdFileStore(directory: directory)
        XCTAssertTrue(store.saveCandidate(testDeviceId))

        store.clear()

        XCTAssertEqual(store.read(), .absent)
        XCTAssertFalse(pendingMarkerExists)
    }

    func testAFileThatWasTamperedWithIsErasedAndReadAsNoIdentifier() throws {
        let store = DeviceIdFileStore(directory: directory)
        XCTAssertTrue(store.saveCandidate(testDeviceId))
        XCTAssertTrue(store.confirm())
        let file = directory.appendingPathComponent("device-id.txt")
        try Data("pas un identifiant!".utf8).write(to: file)

        XCTAssertEqual(store.read(), .absent)
        XCTAssertFalse(FileManager.default.fileExists(atPath: file.path))
    }

    func testAnOrphanCandidateMarkerWithoutIdentifierIsDropped() throws {
        let store = DeviceIdFileStore(directory: directory)
        XCTAssertTrue(store.saveCandidate(testDeviceId))
        try FileManager.default.removeItem(at: directory.appendingPathComponent("device-id.txt"))

        XCTAssertEqual(store.read(), .absent)
        XCTAssertFalse(pendingMarkerExists)
    }

    func testAReadErrorIsNeverReadAsNoIdentifier() throws {
        let store = DeviceIdFileStore(directory: directory)
        XCTAssertTrue(store.saveCandidate(testDeviceId))
        // Un DOSSIER à la place du fichier : la lecture échoue, le fichier n'est pas « absent ».
        let file = directory.appendingPathComponent("device-id.txt")
        try FileManager.default.removeItem(at: file)
        try FileManager.default.createDirectory(at: file, withIntermediateDirectories: true)

        XCTAssertEqual(store.read(), .unreadable)
    }

    func testTheRevokedMarkerSurvivesAnotherInstanceAndCanBeRemoved() {
        let store = DeviceIdFileStore(directory: directory)
        XCTAssertFalse(store.isRevoked())

        XCTAssertTrue(store.setRevoked(true))
        XCTAssertTrue(DeviceIdFileStore(directory: directory).isRevoked(), "survit à un processus tué")
        XCTAssertTrue(store.setRevoked(true), "poser deux fois est sans effet")

        XCTAssertTrue(store.setRevoked(false))
        XCTAssertFalse(DeviceIdFileStore(directory: directory).isRevoked())
        XCTAssertTrue(store.setRevoked(false), "retirer un marqueur absent réussit")
    }

    func testClearingTheIdentifierKeepsTheRevokedMarker() {
        let store = DeviceIdFileStore(directory: directory)
        XCTAssertTrue(store.saveCandidate(testDeviceId))
        XCTAssertTrue(store.setRevoked(true))

        store.clear()

        XCTAssertTrue(store.isRevoked())
    }

    func testConfirmingWithoutAnIdentifierFails() {
        XCTAssertFalse(DeviceIdFileStore(directory: directory).confirm())
    }

    func testTheFolderIsExcludedFromBackups() throws {
        let store = DeviceIdFileStore(directory: directory)
        XCTAssertTrue(store.saveCandidate(testDeviceId))

        let values = try directory.resourceValues(forKeys: [.isExcludedFromBackupKey])

        XCTAssertEqual(values.isExcludedFromBackup, true)
    }

    func testANewCandidateReplacesTheOldIdentifier() {
        let store = DeviceIdFileStore(directory: directory)
        XCTAssertTrue(store.saveCandidate(testDeviceId))
        XCTAssertTrue(store.confirm())

        XCTAssertTrue(store.saveCandidate("another-device-id-0002"))

        XCTAssertEqual(store.read(), .candidate("another-device-id-0002"))
    }
}
