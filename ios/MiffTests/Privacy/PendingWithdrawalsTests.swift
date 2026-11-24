import XCTest
@testable import Miff

/// Le disque des marques de retrait, en mémoire.
final class MemoryWithdrawalStorage: WithdrawalStorage {
    var entries: [String: Int] = [:]
    private(set) var writes = 0
    /// Faux : le disque refuse d'écrire.
    var succeeds = true

    func read() -> [String: Int] {
        entries
    }

    func write(_ entries: [String: Int]) -> Bool {
        writes += 1
        guard succeeds else {
            return false
        }
        self.entries = entries
        return true
    }
}

/// Les marques de retrait du consentement que le compte ne connaît pas encore (ONB-3b) : gardées sur l'appareil pour survivre à un
/// redémarrage, une par compte, sans jamais contenir l'identifiant du compte.
@MainActor
final class PendingWithdrawalsTests: XCTestCase {
    private var clock = 1_000

    private func make(_ storage: MemoryWithdrawalStorage = MemoryWithdrawalStorage()) -> PendingWithdrawals {
        PendingWithdrawals(storage: storage, nowMillis: { [unowned self] in
            self.clock += 1
            return self.clock
        })
    }

    func testNothingIsPendingByDefault() {
        let pending = make()

        XCTAssertFalse(pending.isPending(uid: "uid-1"))
        XCTAssertTrue(pending.keys.isEmpty)
    }

    func testAMarkIsWrittenToTheDiskBeforeTheCallReturnsAndSurvivesARestart() {
        let storage = MemoryWithdrawalStorage()
        let first = make(storage)

        first.mark(uid: "uid-1")

        XCTAssertEqual(storage.writes, 1)
        XCTAssertTrue(first.isPending(uid: "uid-1"))
        // « Redémarrage » : une nouvelle instance relit le disque.
        let restarted = make(storage)
        XCTAssertTrue(restarted.isPending(uid: "uid-1"))
    }

    func testAMarkBelongsToOneAccountOnly() {
        let pending = make()

        pending.mark(uid: "uid-1")

        XCTAssertTrue(pending.isPending(uid: "uid-1"))
        XCTAssertFalse(pending.isPending(uid: "uid-2"))
    }

    func testMarkingTwiceWritesOnce() {
        let storage = MemoryWithdrawalStorage()
        let pending = make(storage)

        pending.mark(uid: "uid-1")
        pending.mark(uid: "uid-1")

        XCTAssertEqual(storage.writes, 1)
    }

    func testClearingRemovesOnlyThatAccountAndIsWrittenToTheDisk() {
        let storage = MemoryWithdrawalStorage()
        let pending = make(storage)
        pending.mark(uid: "uid-1")
        pending.mark(uid: "uid-2")

        pending.clear(uid: "uid-1")

        XCTAssertFalse(pending.isPending(uid: "uid-1"))
        XCTAssertTrue(pending.isPending(uid: "uid-2"))
        XCTAssertFalse(make(storage).isPending(uid: "uid-1"))
        XCTAssertTrue(make(storage).isPending(uid: "uid-2"))
    }

    func testClearingWhatIsNotMarkedWritesNothing() {
        let storage = MemoryWithdrawalStorage()
        let pending = make(storage)

        pending.clear(uid: "uid-1")

        XCTAssertEqual(storage.writes, 0)
    }

    func testTheKeyIsAOneWayFingerprintAndNeverTheAccountIdentifier() {
        let key = PendingWithdrawals.keyOf("uid-secret-1234")

        XCTAssertEqual(key.count, 32)
        XCTAssertNil(key.range(of: "[^0-9a-f]", options: .regularExpression))
        XCTAssertFalse(key.contains("secret"))
        XCTAssertEqual(key, PendingWithdrawals.keyOf("uid-secret-1234"))
        XCTAssertNotEqual(key, PendingWithdrawals.keyOf("uid-secret-1235"))
    }

    func testWhatIsKeptOnTheDiskNeverContainsTheAccountIdentifier() {
        let storage = MemoryWithdrawalStorage()
        let pending = make(storage)

        pending.mark(uid: "uid-secret-1234")

        for (key, time) in storage.entries {
            XCTAssertFalse(key.contains("uid-secret"))
            XCTAssertGreaterThan(time, 0)
        }
    }

    func testOnlyTheTwentyMostRecentAccountsAreRemembered() {
        let storage = MemoryWithdrawalStorage()
        let pending = make(storage)

        for index in 1...(PendingWithdrawals.maxEntries + 3) {
            pending.mark(uid: "uid-\(index)")
        }

        XCTAssertEqual(pending.keys.count, PendingWithdrawals.maxEntries)
        // Les plus anciens sont oubliés, les plus récents restent.
        XCTAssertFalse(pending.isPending(uid: "uid-1"))
        XCTAssertFalse(pending.isPending(uid: "uid-3"))
        XCTAssertTrue(pending.isPending(uid: "uid-4"))
        XCTAssertTrue(pending.isPending(uid: "uid-\(PendingWithdrawals.maxEntries + 3)"))
        XCTAssertEqual(storage.entries.count, PendingWithdrawals.maxEntries)
    }

    func testADiskThatRefusesKeepsTheMarkInMemoryForThisRun() {
        let storage = MemoryWithdrawalStorage()
        storage.succeeds = false
        let pending = make(storage)

        pending.mark(uid: "uid-1")

        // La collecte reste coupée tant que l'app vit ; la marque ne survivra pas à un redémarrage (limite connue, documentée).
        XCTAssertTrue(pending.isPending(uid: "uid-1"))
        XCTAssertFalse(make(storage).isPending(uid: "uid-1"))
    }

    func testTheKeysArePublishedSoTheScreenFollowsABackgroundRecord() {
        let pending = make()
        var seen: [Int] = []
        let subscription = pending.$keys.sink { seen.append($0.count) }

        pending.mark(uid: "uid-1")
        pending.clear(uid: "uid-1")

        XCTAssertEqual(seen, [0, 1, 0])
        subscription.cancel()
    }
}

/// Le fichier privé des marques : écriture atomique, une ligne par compte, hors des sauvegardes, rien de personnel.
final class WithdrawalFileStorageTests: XCTestCase {
    private var directory: URL!

    override func setUpWithError() throws {
        directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("withdrawals-\(UUID().uuidString)", isDirectory: true)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: directory)
    }

    func testNothingIsReadFromAMissingFile() {
        XCTAssertTrue(WithdrawalFileStorage(directory: directory).read().isEmpty)
    }

    func testWhatIsWrittenIsReadBackByANewInstance() {
        let entries = ["aaaa1111": 1_700_000_000_000, "bbbb2222": 42]

        XCTAssertTrue(WithdrawalFileStorage(directory: directory).write(entries))

        XCTAssertEqual(WithdrawalFileStorage(directory: directory).read(), entries)
    }

    func testTheFolderIsExcludedFromEveryBackup() throws {
        XCTAssertTrue(WithdrawalFileStorage(directory: directory).write(["aaaa1111": 1]))

        let values = try directory.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(values.isExcludedFromBackup, true)
    }

    func testAnEmptyListLeavesAnEmptyFile() {
        let storage = WithdrawalFileStorage(directory: directory)
        XCTAssertTrue(storage.write(["aaaa1111": 1]))

        XCTAssertTrue(storage.write([:]))

        XCTAssertTrue(storage.read().isEmpty)
    }

    func testMalformedLinesAreIgnored() throws {
        let storage = WithdrawalFileStorage(directory: directory)
        XCTAssertTrue(storage.write(["aaaa1111": 1]))
        let file = directory.appendingPathComponent("pending-withdrawals.txt")
        try Data("aaaa1111:1\nn'importe quoi\n:12\ncccc:abc\ndddd:5:6\neeee3333:7".utf8).write(to: file)

        XCTAssertEqual(storage.read(), ["aaaa1111": 1, "eeee3333": 7])
    }

    func testTheFileHoldsOnlyKeysAndTimes() throws {
        let storage = WithdrawalFileStorage(directory: directory)
        let key = PendingWithdrawals.keyOf("uid-secret-1234")

        XCTAssertTrue(storage.write([key: 1_700_000_000_000]))

        let text = try String(contentsOf: directory.appendingPathComponent("pending-withdrawals.txt"), encoding: .utf8)
        XCTAssertEqual(text, "\(key):1700000000000")
        XCTAssertFalse(text.contains("uid-secret"))
    }
}
