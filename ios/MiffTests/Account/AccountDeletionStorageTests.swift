import XCTest
@testable import Miff

/// Le marqueur local de la suppression du compte, les fichiers de la session d'appareil et ce que la suppression efface du téléphone
/// (AUTH-5c, miroir d'`AccountLocalDataTest` d'Android).
final class AccountDeletionFileMarkerTests: XCTestCase {
    private var directory: URL = URL(fileURLWithPath: NSTemporaryDirectory())

    override func setUp() {
        super.setUp()
        directory = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("marker-\(UUID().uuidString)", isDirectory: true)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: directory)
        super.tearDown()
    }

    func testAMarkerThatCannotBeWrittenIsReportedAndNothingIsClaimed() throws {
        // Un fichier à la place du dossier : rien ne peut être écrit.
        try Data("x".utf8).write(to: directory)
        let marker = AccountDeletionFileMarker(directory: directory)

        XCTAssertFalse(marker.write(AccountDeletionMark(uid: "uid-1")))
        XCTAssertNil(marker.read())
    }

    func testTheOutcomeOfTheAppleRevocationSurvivesAKilledProcess() {
        let marker = AccountDeletionFileMarker(directory: directory)
        XCTAssertTrue(marker.write(AccountDeletionMark(uid: "uid-1", appleRevocation: .failed)))

        XCTAssertEqual(AccountDeletionFileMarker(directory: directory).read()?.appleRevocation, .failed)
    }

    func testAMarkerWrittenBeforeTheAppleFieldReadsAsNoApple() throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let old = Data("{\"uid\":\"uid-1\",\"accountDeleted\":false}".utf8)
        try old.write(to: directory.appendingPathComponent("account-deletion.json"))

        XCTAssertEqual(AccountDeletionFileMarker(directory: directory).read()?.appleRevocation, .notNeeded)
    }

    func testNothingIsThereBeforeTheFirstWrite() {
        XCTAssertNil(AccountDeletionFileMarker(directory: directory).read())
    }

    func testTheMarkerSurvivesAKilledProcessAndKeepsOnlyTheAccountAndTheDeletedFlag() throws {
        AccountDeletionFileMarker(directory: directory).write(AccountDeletionMark(uid: "uid-1"))

        let relaunched = AccountDeletionFileMarker(directory: directory)

        XCTAssertEqual(relaunched.read(), AccountDeletionMark(uid: "uid-1", accountDeleted: false))
        let file = directory.appendingPathComponent("account-deletion.json")
        let text = try String(contentsOf: file, encoding: .utf8)
        XCTAssertTrue(text.contains("uid-1"))
        XCTAssertFalse(text.contains("@"), "ni e-mail")
        let keys = (try JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any])?.keys.sorted()
        // L'identifiant et deux drapeaux fixes, rien d'autre.
        XCTAssertEqual(keys, ["accountDeleted", "appleRevocation", "uid"])
    }

    func testTheConfirmationOfFirebaseIsRecordedAndTheMarkerCanBeErased() {
        let marker = AccountDeletionFileMarker(directory: directory)
        marker.write(AccountDeletionMark(uid: "uid-1"))

        marker.write(AccountDeletionMark(uid: "uid-1", accountDeleted: true))
        XCTAssertEqual(marker.read()?.accountDeleted, true)
        marker.clear()

        XCTAssertNil(marker.read())
        XCTAssertNil(AccountDeletionFileMarker(directory: directory).read())
    }

    func testAnUnreadableFileIsNoMarker() throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data("pas du json".utf8).write(to: directory.appendingPathComponent("account-deletion.json"))

        XCTAssertNil(AccountDeletionFileMarker(directory: directory).read())
    }

    func testTheMarkerFileIsExcludedFromBackups() throws {
        AccountDeletionFileMarker(directory: directory).write(AccountDeletionMark(uid: "uid-1"))

        let values = try directory.resourceValues(forKeys: [.isExcludedFromBackupKey])

        XCTAssertEqual(values.isExcludedFromBackup, true)
    }
}

final class DeviceIdFileStoreGoneTests: XCTestCase {
    private var directory: URL = URL(fileURLWithPath: NSTemporaryDirectory())

    override func setUp() {
        super.setUp()
        directory = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("device-\(UUID().uuidString)", isDirectory: true)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: directory)
        super.tearDown()
    }

    func testTheReasonSurvivesARelaunchAndLeavesWithTheRevocation() {
        let store = DeviceIdFileStore(directory: directory)
        XCTAssertFalse(store.isGone())

        XCTAssertTrue(store.setRevoked(true))
        XCTAssertTrue(store.setGone(true))
        let relaunched = DeviceIdFileStore(directory: directory)
        XCTAssertTrue(relaunched.isRevoked())
        XCTAssertTrue(relaunched.isGone())

        XCTAssertTrue(relaunched.setRevoked(false))
        XCTAssertFalse(relaunched.isRevoked())
        XCTAssertFalse(relaunched.isGone(), "une nouvelle connexion repart de zéro")
    }

    func testClearingTheReasonAloneKeepsTheRevocation() {
        let store = DeviceIdFileStore(directory: directory)
        _ = store.setRevoked(true)
        _ = store.setGone(true)

        XCTAssertTrue(store.setGone(false))

        XCTAssertTrue(store.isRevoked())
        XCTAssertFalse(store.isGone())
    }
}

/// Ce que la suppression efface du téléphone pour le compte : un drapeau par compte, les marqueurs de suppression d'une Miff, la Miff
/// « active », les notifications locales.
@MainActor
final class AccountLocalDataTests: XCTestCase {
    private final class RecordingCleaner: LocalNotificationsCleaner {
        private(set) var removals = 0

        func removeAll() async {
            removals += 1
        }
    }

    private final class RecordingTrackingStore: TrackingLocalStore {
        private(set) var clears = 0

        func load() -> LocalState? {
            nil
        }

        func save(_ state: LocalState) {
            // Rien à garder.
        }

        func clear() {
            clears += 1
        }
    }

    private final class RecordingJournal: DetectionJournal {
        private(set) var clears = 0

        func record(kind: JournalKind, placeId: String?, batteryPct: Int?) {
            // Rien à garder.
        }

        func lines() -> [JournalLine] {
            []
        }

        func clear() {
            clears += 1
        }
    }

    private func suite() -> UserDefaults {
        let name = "account-local-data-\(UUID().uuidString)"
        guard let defaults = UserDefaults(suiteName: name) else {
            XCTFail("suite introuvable")
            return .standard
        }
        defaults.removePersistentDomain(forName: name)
        return defaults
    }

    func testEveryAccountBoundStorageIsErasedForThatAccountOnly() async {
        let defaults = suite()
        let asks = UserDefaultsNotificationAskStore(defaults: defaults)
        asks.markShown(uid: "uid-1")
        asks.markShown(uid: "uid-2")
        let pending = MemoryPendingDeletions()
        pending.add(uid: "uid-1", miffId: testMiffId)
        pending.add(uid: "uid-2", miffId: testOtherMiffId)
        let active = MemoryActiveMiffStore()
        active.choose(testMiffId)
        let cleaner = RecordingCleaner()
        let tracking = RecordingTrackingStore()
        let journal = RecordingJournal()
        let erasers = AccountLocalData.erasers(
            AccountLocalStores(
                notificationAsks: asks, pendingMiffDeletions: pending, activeMiff: active, notifications: cleaner,
                trackingLocal: tracking, detectionJournal: journal
            )
        )

        for erase in erasers {
            await erase("uid-1")
        }

        XCTAssertFalse(asks.wasShown(uid: "uid-1"))
        XCTAssertTrue(asks.wasShown(uid: "uid-2"), "le drapeau d'un autre compte n'est pas touché")
        XCTAssertTrue(pending.ids(uid: "uid-1").isEmpty)
        XCTAssertEqual(pending.ids(uid: "uid-2"), [testOtherMiffId])
        XCTAssertNil(active.chosen())
        XCTAssertEqual(cleaner.removals, 1)
        XCTAssertEqual(tracking.clears, 1, "la copie locale du suivi est effacée directement")
        XCTAssertEqual(journal.clears, 1, "le journal de détection aussi")
    }

    func testAFlagOfAccountsThatNeverHadOneIsHarmless() async {
        let asks = UserDefaultsNotificationAskStore(defaults: suite())

        asks.forget(uid: "nobody")

        XCTAssertFalse(asks.wasShown(uid: "nobody"))
    }
}
