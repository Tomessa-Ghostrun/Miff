import XCTest
@testable import Miff

/// Le fichier de « Depuis ta dernière visite » : relu tel qu'il a été écrit, propre à un compte, jamais cru s'il est abîmé, effacé à la
/// demande, hors des sauvegardes, et sans aucun nom (critères C1-7, C1-8, C1-T20). Miroir de `LastVisitStoreTest.kt` d'Android.
final class LastVisitStoreTests: XCTestCase {
    private typealias Fixture = VisitFixtures
    private var directory = URL(fileURLWithPath: NSTemporaryDirectory())

    override func setUp() {
        super.setUp()
        directory = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("last-visit-\(UUID().uuidString)", isDirectory: true)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: directory)
        super.tearDown()
    }

    private var memory: LastVisitMemory {
        let silent = Fixture.member("a", .out, .home, presenceAt: 1_000, lastUpdateAt: 2_000, silent: true)
        let trusted = Fixture.member("b", .inTrusted, nil, presenceAt: 3_000, lastUpdateAt: 3_500)
        let muted = Fixture.member("c", .travel, nil, presenceAt: 4_000, muted: true)
        return LastVisitMemory(
            states: [
                "miff-1": Fixture.state([silent, trusted, muted]),
                "miff-2": Fixture.state([Fixture.member("d", .inRisk, nil)], miffId: "miff-2")
            ],
            lastMiffId: "miff-2"
        )
    }

    func testWhatIsSavedIsReadBackExactly() {
        let store = LastVisitFileStore(directory: directory)

        store.save(memory, uid: "uid-1")

        XCTAssertEqual(LastVisitFileStore(directory: directory).load(uid: "uid-1"), memory)
    }

    func testAnotherAccountOrAMissingFileReadsAsNothing() {
        let store = LastVisitFileStore(directory: directory)
        XCTAssertEqual(store.load(uid: "uid-1"), LastVisitMemory())

        store.save(memory, uid: "uid-1")

        XCTAssertEqual(store.load(uid: "uid-2"), LastVisitMemory())
    }

    func testADamagedFileIsNotBelievedAndNeverCrashes() throws {
        let store = LastVisitFileStore(directory: directory)
        store.save(memory, uid: "uid-1")
        let file = directory.appendingPathComponent("last-visit.json")

        try Data("{ pas du json".utf8).write(to: file)

        XCTAssertEqual(store.load(uid: "uid-1"), LastVisitMemory())
    }

    func testClearingErasesTheFile() {
        let store = LastVisitFileStore(directory: directory)
        store.save(memory, uid: "uid-1")

        store.clear()

        XCTAssertFalse(FileManager.default.fileExists(atPath: directory.path))
        XCTAssertEqual(store.load(uid: "uid-1"), LastVisitMemory())
    }

    func testTheFileHoldsOnlyIdentifiersCategoriesAndTimesAndStaysOutOfTheBackups() throws {
        let store = LastVisitFileStore(directory: directory)
        store.save(memory, uid: "uid-1")
        let file = directory.appendingPathComponent("last-visit.json")

        let text = try String(contentsOf: file, encoding: .utf8)
        let values = try directory.resourceValues(forKeys: [.isExcludedFromBackupKey])

        for word in ["name", "Name", "address", "lat", "lng", "photo", "jpeg", "Thomas"] {
            XCTAssertFalse(text.contains(word), word)
        }
        XCTAssertTrue(text.contains("HOME"), "la catégorie, jamais le nom du lieu")
        XCTAssertEqual(values.isExcludedFromBackup, true)
    }
}
