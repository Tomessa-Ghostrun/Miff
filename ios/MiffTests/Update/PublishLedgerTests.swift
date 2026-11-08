import XCTest
@testable import Miff

/// Ce que le téléphone retient de l'Update (critères U6, U7, V8) : des nombres et des moments, un fichier privé. La note de la photo est liée au
/// COMPTE, survit à une déconnexion et part avec la SUPPRESSION du compte ; l'astuce du double toucher survit à tout (elle concerne le
/// téléphone). Miroir de `PublishLedgerTest.kt`.
final class PublishLedgerTests: XCTestCase {
    private var directory = URL(fileURLWithPath: NSTemporaryDirectory())

    override func setUp() {
        super.setUp()
        let name = "ledger-\(UUID().uuidString)"
        directory = FileManager.default.temporaryDirectory.appendingPathComponent(name, isDirectory: true)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: directory)
        super.tearDown()
    }

    private func makeLedger() -> FilePublishLedger {
        FilePublishLedger(directory: directory)
    }

    func testTheCountsAndTheLastPublicationSurviveARestartOfTheApp() {
        let first = makeLedger()
        first.recordPublication(nowMillis: 1_000, dayKey: "2026-11-07", count: 3)

        let second = makeLedger()
        XCTAssertEqual(second.countedDay(), "2026-11-07")
        XCTAssertEqual(second.countToday(), 3)
        XCTAssertEqual(second.lastPublishMillis(), 1_000)
    }

    func testTheNoteOfThePhotoIsTheAccountsOnlyAndASignOutDoesNotForgetIt() {
        let first = makeLedger()
        first.notePhotoSent(uid: "uid-a", millis: 5_000)
        first.recordPublication(nowMillis: 6_000, dayKey: "2026-11-07", count: 1)

        // Déconnexion : les comptes partent, la note de la photo RESTE (la photo reste en base ; le même compte la nettoiera).
        first.clear()

        let second = makeLedger()
        XCTAssertNil(second.countedDay())
        XCTAssertEqual(second.countToday(), 0)
        XCTAssertNil(second.lastPublishMillis())
        XCTAssertEqual(second.photoSentMillis(uid: "uid-a"), 5_000)
        // La note d'un autre compte est ignorée : une photo d'un autre n'est jamais nettoyée par ce compte.
        XCTAssertNil(second.photoSentMillis(uid: "uid-b"))
    }

    func testTheNoteIsErasedByTheDeletionOfThatAccountAndOnlyThatOne() {
        let ledger = makeLedger()
        ledger.notePhotoSent(uid: "uid-a", millis: 5_000)

        ledger.eraseAccountData(uid: "uid-b")
        XCTAssertEqual(ledger.photoSentMillis(uid: "uid-a"), 5_000, "la note d'un autre compte n'est pas touchée")

        ledger.eraseAccountData(uid: "uid-a")
        XCTAssertNil(ledger.photoSentMillis(uid: "uid-a"))
        XCTAssertNil(makeLedger().photoSentMillis(uid: "uid-a"), "effacée aussi du fichier")
    }

    func testTheNoteCanBeForgottenAndAnotherAccountReplacesIt() {
        let ledger = makeLedger()
        ledger.notePhotoSent(uid: "uid-a", millis: 5_000)
        ledger.notePhotoSent(uid: "uid-b", millis: 7_000)

        XCTAssertNil(ledger.photoSentMillis(uid: "uid-a"))
        XCTAssertEqual(ledger.photoSentMillis(uid: "uid-b"), 7_000)
        ledger.clearPhotoSent()
        XCTAssertNil(ledger.photoSentMillis(uid: "uid-b"))
    }

    func testTheDoubleTapIsRememberedNeverBeforeTheFirstOneAndSurvivesARestartAndASignOut() {
        let first = makeLedger()
        XCTAssertFalse(first.doubleTapUsed())

        first.markDoubleTapUsed()
        first.clear()

        XCTAssertTrue(makeLedger().doubleTapUsed())
    }

    func testTheFileIsExcludedFromTheBackupsAndHoldsOnlyNumbersAndMoments() throws {
        let ledger = makeLedger()
        ledger.notePhotoSent(uid: "uid-a", millis: 5_000)

        let folder = try XCTUnwrap(directory)
        let values = try folder.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(values.isExcludedFromBackup, true)
        let data = try Data(contentsOf: directory.appendingPathComponent("update-ledger.json"))
        // Rien d'autre qu'un compte, des moments et des indicateurs : ni photo, ni lieu, ni nom, ni Miff.
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(Set(object.keys), ["count", "photoSentAt", "photoSentUid", "doubleTapUsed"])
    }

    func testAnUnreadableFileIsNotACopyItStartsAgainFromNothing() throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data("pas du json".utf8).write(to: directory.appendingPathComponent("update-ledger.json"))

        let ledger = makeLedger()

        XCTAssertNil(ledger.countedDay())
        XCTAssertEqual(ledger.countToday(), 0)
        XCTAssertFalse(ledger.doubleTapUsed())
    }
}
