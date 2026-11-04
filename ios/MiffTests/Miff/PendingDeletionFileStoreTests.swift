import XCTest
@testable import Miff

/// Le marqueur « une suppression est en cours » (critère S4, IOS3) : sur le disque, par compte, qui ne contient que des
/// identifiants opaques, et que le prochain lancement relit.
final class PendingDeletionFileStoreTests: XCTestCase {
    /// Un dossier neuf pour le test, effacé à la fin.
    private func makeDirectory() -> URL {
        let url = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("pending-deletions-\(UUID().uuidString)", isDirectory: true)
        addTeardownBlock {
            try? FileManager.default.removeItem(at: url)
        }
        return url
    }

    func testAMarkerIsStillThereForANewStoreOnTheSameFolderLikeAfterTheAppWasKilled() {
        let directory = makeDirectory()
        PendingDeletionFileStore(directory: directory).add(uid: "uid-1", miffId: testMiffId)

        let afterRestart = PendingDeletionFileStore(directory: directory)

        XCTAssertEqual(afterRestart.ids(uid: "uid-1"), [testMiffId])
    }

    func testAnotherAccountNeverSeesTheMarkerOfSomeoneElse() {
        let store = PendingDeletionFileStore(directory: makeDirectory())
        store.add(uid: "uid-1", miffId: testMiffId)
        store.add(uid: "uid-2", miffId: testOtherMiffId)

        XCTAssertEqual(store.ids(uid: "uid-1"), [testMiffId])
        XCTAssertEqual(store.ids(uid: "uid-2"), [testOtherMiffId])
        XCTAssertTrue(store.ids(uid: "uid-3").isEmpty)
    }

    func testRemovingOneMarkerKeepsTheOthersAndTheLastRemovalDeletesTheFile() {
        let directory = makeDirectory()
        let store = PendingDeletionFileStore(directory: directory)
        store.add(uid: "uid-1", miffId: testMiffId)
        store.add(uid: "uid-1", miffId: testOtherMiffId)

        store.remove(uid: "uid-1", miffId: testMiffId)
        XCTAssertEqual(store.ids(uid: "uid-1"), [testOtherMiffId])
        store.remove(uid: "uid-1", miffId: testOtherMiffId)

        XCTAssertTrue(store.ids(uid: "uid-1").isEmpty)
        let files = (try? FileManager.default.contentsOfDirectory(atPath: directory.path)) ?? []
        XCTAssertTrue(files.isEmpty, "plus rien sur le disque quand il ne reste aucune suppression")
    }

    func testAddingTheSameMarkerTwiceKeepsOne() {
        let store = PendingDeletionFileStore(directory: makeDirectory())

        store.add(uid: "uid-1", miffId: testMiffId)
        store.add(uid: "uid-1", miffId: testMiffId)

        XCTAssertEqual(store.ids(uid: "uid-1").count, 1)
    }

    func testTheFileHoldsOnlyOpaqueIdentifiersNeverANameOrACode() throws {
        let directory = makeDirectory()
        PendingDeletionFileStore(directory: directory).add(uid: "uid-1", miffId: testMiffId)

        let files = try FileManager.default.contentsOfDirectory(atPath: directory.path)
        let file = try XCTUnwrap(files.first)
        let text = try String(contentsOf: directory.appendingPathComponent(file), encoding: .utf8)

        XCTAssertTrue(text.contains(testMiffId))
        XCTAssertFalse(text.contains(testCode))
        XCTAssertFalse(text.contains("Chez nous"))
    }

    func testTheFolderIsExcludedFromBackups() throws {
        let directory = makeDirectory()
        PendingDeletionFileStore(directory: directory).add(uid: "uid-1", miffId: testMiffId)

        let values = try directory.resourceValues(forKeys: [.isExcludedFromBackupKey])

        XCTAssertEqual(values.isExcludedFromBackup, true)
    }

    func testAnUnreadableFileIsTreatedAsNoMarker() throws {
        let directory = makeDirectory()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data("pas du JSON".utf8).write(to: directory.appendingPathComponent("pending-deletions.json"))

        XCTAssertTrue(PendingDeletionFileStore(directory: directory).ids(uid: "uid-1").isEmpty)
    }
}
