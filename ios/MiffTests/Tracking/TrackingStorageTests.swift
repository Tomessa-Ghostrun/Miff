import XCTest
@testable import Miff

/// La copie locale de la détection et le journal local (critères POS5, VIE2, VIE3) : ce qui est gardé, où, et ce qui est effacé.
final class TrackingStorageTests: XCTestCase {
    private var directory = FileManager.default.temporaryDirectory

    override func setUp() {
        super.setUp()
        directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("miff-tracking-tests-\(UUID().uuidString)", isDirectory: true)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: directory)
        super.tearDown()
    }

    private func sampleState() -> LocalState {
        var state = LocalState(uid: "uid-1")
        var miff = LocalMiff(places: [LocalPlace(fact: TrackingFixtures.home)], readAtMillis: 42)
        miff.derived = DerivedPresence(presence: .inTrusted, placeId: "p1")
        miff.access = "background"
        miff.written = WrittenPosition(lat: 48.85661, lng: 2.35222, atMillis: 1_000)
        let leaving = StatusFact(presence: .out, placeId: "p1", presenceAtMillis: 2_000, setLastUpdate: false)
        miff.pending = PendingStatus(fact: leaving)
        state.miffs[testMiffId] = miff
        state.inside[TrackingFixtures.home.key] = 500
        state.armed = [TrackingFixtures.home.key]
        state.fix = TrackingFixtures.fixHome(at: 3_000)
        state.breaker = BreakerState(dayKey: "2026-11-3", statusWrites: 2, positionWrites: 5)
        state.updatesArmed = true
        state.checkedAtMillis = 99
        state.statusCursor = testMiffId
        return state
    }

    // MARK: - La copie locale

    func testTheLocalCopyRoundTripsThroughTheFile() throws {
        let store = TrackingLocalFileStore(directory: directory)
        let state = sampleState()

        store.save(state)

        XCTAssertEqual(store.load(), state)
        XCTAssertEqual(TrackingLocalFileStore(directory: directory).load(), state, "relue après une relance")
    }

    func testClearErasesTheFileAndTheFolder() {
        let store = TrackingLocalFileStore(directory: directory)
        store.save(sampleState())

        store.clear()

        XCTAssertNil(store.load())
        XCTAssertFalse(FileManager.default.fileExists(atPath: directory.path))
    }

    func testAnUnreadableCopyIsNoCopyAndNeverBelieved() throws {
        let store = TrackingLocalFileStore(directory: directory)
        store.save(sampleState())
        let file = directory.appendingPathComponent("tracking-state.json")
        try Data("{ pas du json".utf8).write(to: file)

        XCTAssertNil(store.load())
    }

    func testTheFolderIsExcludedFromTheBackups() throws {
        let store = TrackingLocalFileStore(directory: directory)

        store.save(sampleState())

        let values = try directory.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(values.isExcludedFromBackup, true)
    }

    func testLoadingWithoutAnyFileGivesNothing() {
        XCTAssertNil(TrackingLocalFileStore(directory: directory).load())
    }

    func testThePendingStatusAndThePlaceConvertBothWays() throws {
        let fact = StatusFact(presence: .inRisk, placeId: "r1", presenceAtMillis: 77, setLastUpdate: false)

        XCTAssertEqual(PendingStatus(fact: fact).fact, fact)
        XCTAssertEqual(LocalPlace(fact: TrackingFixtures.danger).fact, TrackingFixtures.danger)
        let corrupted = try JSONDecoder().decode(
            PendingStatus.self,
            from: Data(#"{"presence":"hacked","placeId":null,"atMillis":1,"setLastUpdate":false}"#.utf8)
        )
        XCTAssertNil(corrupted.fact, "une valeur inconnue n'est jamais écrite")
    }

    func testTheDetectionStateOfAMiffResetsButKeepsItsPlaces() {
        var miff = sampleState().miffs[testMiffId] ?? LocalMiff()

        miff.resetDetection()

        XCTAssertNil(miff.derived)
        XCTAssertNil(miff.written)
        XCTAssertNil(miff.pending)
        XCTAssertEqual(miff.places.count, 1)
    }

    func testTheGenerationCounterFollowsWhatIsSet() {
        let counter = GenerationCounter()
        XCTAssertEqual(counter.current, 0)

        counter.set(3)

        XCTAssertEqual(counter.current, 3)
    }

    // MARK: - Le journal local (VIE2)

    #if DEBUG || INTERNAL
    func testTheJournalKeepsTheKindThePlaceTheTimeAndTheBatteryAndNothingElse() {
        let journal = FileDetectionJournal(directory: directory, clock: { 1_234 })

        journal.record(.statusWritten, placeId: "p1", batteryPct: 61)
        journal.record(.armed)

        XCTAssertEqual(journal.lines(), [
            JournalLine(atMillis: 1_234, kind: .statusWritten, placeId: "p1", batteryPct: 61),
            JournalLine(atMillis: 1_234, kind: .armed, placeId: nil, batteryPct: nil)
        ])
    }

    func testTheJournalRefusesAnythingThatLooksLikeAKeyOrAFreeText() throws {
        let journal = FileDetectionJournal(directory: directory, clock: { 1 })

        journal.record(.transitionEnter, placeId: TrackingFixtures.home.key, batteryPct: 500)
        journal.record(.transitionExit, placeId: "48.85661,2.35222", batteryPct: -3)

        let lines = journal.lines()
        XCTAssertEqual(lines.count, 2)
        XCTAssertTrue(lines.allSatisfy { $0.placeId == nil && $0.batteryPct == nil })
        let file = directory.appendingPathComponent(FileDetectionJournal.marker + ".txt")
        let raw = try String(contentsOf: file, encoding: .utf8)
        XCTAssertFalse(raw.contains("48."))
        XCTAssertFalse(raw.contains("2.35"))
    }

    func testTheJournalKeepsFourHundredLinesAtMostAndForgetsTheOldestFirst() {
        var now = 0
        let journal = FileDetectionJournal(directory: directory, clock: {
            now += 1
            return now
        })

        for _ in 0..<450 {
            journal.record(.positionRead)
        }

        let lines = journal.lines()
        XCTAssertEqual(lines.count, FileDetectionJournal.maxLines)
        XCTAssertEqual(lines.first?.atMillis, 51)
        XCTAssertEqual(lines.last?.atMillis, 450)
    }

    func testClearingTheJournalErasesTheFile() {
        let journal = FileDetectionJournal(directory: directory, clock: { 1 })
        journal.record(.armed)

        journal.clear()

        XCTAssertTrue(journal.lines().isEmpty)
        XCTAssertFalse(FileManager.default.fileExists(atPath: directory.path))
    }

    func testADamagedLineIsIgnoredNotShown() {
        XCTAssertNil(FileDetectionJournal.parse("pas une ligne"))
        XCTAssertNil(FileDetectionJournal.parse("1|inconnu|p1|50"))
        XCTAssertNil(FileDetectionJournal.parse("x|armed|p1|50"))
        XCTAssertEqual(FileDetectionJournal.parse("5|armed|-|-")?.kind, .armed)
    }

    func testTheBuildGetsARealJournalInTestBuilds() {
        XCTAssertTrue(DetectionJournals.make() is FileDetectionJournal)
    }
    #endif

    func testTheJournalOfThePublicationBuildDoesNothing() {
        let journal = NoDetectionJournal()

        journal.record(.armed, placeId: "p1", batteryPct: 3)

        XCTAssertTrue(journal.lines().isEmpty)
    }
}
