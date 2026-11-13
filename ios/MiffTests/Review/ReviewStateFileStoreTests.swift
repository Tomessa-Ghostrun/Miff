#if REVIEW_PROMPT
import XCTest
@testable import Miff

/// Ce que la fenêtre d'avis retient sur l'appareil : un aller-retour, une valeur inconnue, un fichier abîmé, l'effacement (déconnexion,
/// suppression du compte) et l'exclusion des sauvegardes. Miroir de `ReviewStateStoreTest` d'Android.
final class ReviewStateFileStoreTests: XCTestCase {
    private var directory: URL!

    override func setUp() {
        super.setUp()
        directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("review-store-\(UUID().uuidString)", isDirectory: true)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: directory)
        super.tearDown()
    }

    private func store() -> ReviewStateFileStore {
        ReviewStateFileStore(directory: directory)
    }

    func testAFreshDeviceRemembersNothing() {
        XCTAssertEqual(store().read(), ReviewState())
        XCTAssertFalse(store().forceNext())
    }

    func testWhatIsWrittenIsReadBackByAnotherInstance() {
        let state = ReviewState(firstMiffSeenAtMillis: 10, lastShownAtMillis: 20, lastOutcome: .canDoBetter)

        store().write(state)

        XCTAssertEqual(store().read(), state)
        store().write(ReviewState(firstMiffSeenAtMillis: 10))
        XCTAssertEqual(store().read(), ReviewState(firstMiffSeenAtMillis: 10))
    }

    func testEveryOutcomeSurvivesTheRoundTrip() {
        for outcome in [ReviewOutcome.shown, .closed, .loved, .canDoBetter] {
            store().write(ReviewState(lastOutcome: outcome))

            XCTAssertEqual(store().read().lastOutcome, outcome)
        }
    }

    func testAnUnknownOutcomeReadsAsNoneAndNeverCrashes() throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let json = #"{"lastOutcome":"FROM_THE_FUTURE","lastShownAtMillis":5}"#
        try Data(json.utf8).write(to: directory.appendingPathComponent("review-prompt.json"))

        let read = store().read()

        XCTAssertNil(read.lastOutcome)
        XCTAssertEqual(read.lastShownAtMillis, 5)
    }

    func testAnUnreadableFileReadsAsNothingRememberedNeverACrash() throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data("pas du JSON".utf8).write(to: directory.appendingPathComponent("review-prompt.json"))

        XCTAssertEqual(store().read(), ReviewState())
        XCTAssertFalse(store().forceNext())
    }

    func testTheTestOptionIsKeptAndWritingTheStateDoesNotLoseIt() {
        store().setForceNext(true)

        store().write(ReviewState(firstMiffSeenAtMillis: 1))

        XCTAssertTrue(store().forceNext())
        store().setForceNext(false)
        XCTAssertFalse(store().forceNext())
        XCTAssertEqual(store().read(), ReviewState(firstMiffSeenAtMillis: 1))
    }

    func testClearForgetsEverythingAndIsHarmlessWhenThereIsNothing() {
        store().clear()
        store().write(ReviewState(firstMiffSeenAtMillis: 1, lastShownAtMillis: 2, lastOutcome: .loved))
        store().setForceNext(true)

        store().clear()

        XCTAssertEqual(store().read(), ReviewState())
        XCTAssertFalse(store().forceNext())
    }

    func testTheFolderIsExcludedFromBackupsAndHoldsNothingPersonal() throws {
        store().write(ReviewState(firstMiffSeenAtMillis: 1, lastShownAtMillis: 2, lastOutcome: .closed))

        let values = try directory.resourceValues(forKeys: [.isExcludedFromBackupKey])
        let text = try String(contentsOf: directory.appendingPathComponent("review-prompt.json"), encoding: .utf8)

        XCTAssertEqual(values.isExcludedFromBackup, true)
        // Trois dates et une issue : jamais un nom, un identifiant, une adresse.
        let keys = try XCTUnwrap(
            JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any]
        ).keys
        XCTAssertEqual(Set(keys), ["firstMiffSeenAtMillis", "lastShownAtMillis", "lastOutcome"])
    }
}
#endif
