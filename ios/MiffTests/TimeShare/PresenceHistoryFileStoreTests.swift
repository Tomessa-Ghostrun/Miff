import XCTest
@testable import Miff

/// Le journal LOCAL des changements de présence sur le disque (TIME-3) : ce qu'il garde (un identifiant de Miff, un état, un identifiant de lieu,
/// une heure : TS-2), où (exclu des sauvegardes : TS-3), combien de temps (35 jours : TS-7), et comment il s'efface (TS-5, TS-6). Un fichier
/// ILLISIBLE n'est jamais effacé ; un fichier ABÎMÉ est réinitialisé.
final class PresenceHistoryFileStoreTests: XCTestCase {
    private let day = TimeShareLimits.dayMillis
    private let hour = TimeShareLimits.hourMillis
    private var directory = URL(fileURLWithPath: "/")
    private var clockNow = 1_800_000_000_000

    override func setUp() {
        super.setUp()
        directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("presence-history-\(UUID().uuidString)", isDirectory: true)
        clockNow = 1_800_000_000_000
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: directory)
        super.tearDown()
    }

    private func store() -> PresenceHistoryFileStore {
        PresenceHistoryFileStore(directory: directory, clock: { self.clockNow })
    }

    private func fact(
        _ state: PresenceState,
        _ place: String? = nil,
        at: Int,
        miff: String = "miffA"
    ) -> PresenceFact {
        PresenceFact(miffId: miff, state: state, placeId: place, atMillis: at)
    }

    private func facts(_ store: PresenceHistory, miff: String = "miffA") -> [PresenceFact] {
        if case .facts(let found) = store.read(miffId: miff) {
            return found
        }
        XCTFail("le stockage devrait être lisible")
        return []
    }

    private var fileURL: URL {
        directory.appendingPathComponent("presence-history.json")
    }

    // MARK: - Écrire et relire

    func testFactsAreKeptInTheOrderTheyWereRecordedAndSurviveARestart() {
        let first = store()
        first.record(fact(.trusted, "p1", at: clockNow - 5 * hour))
        first.record(fact(.out, nil, at: clockNow - 3 * hour))

        let reopened = store()

        XCTAssertEqual(facts(reopened).map { $0.state }, [.trusted, .out])
        XCTAssertEqual(facts(reopened).first?.placeId, "p1")
    }

    func testAFactIdenticalToTheLastOneOfTheSameMiffIsNotKeptAgain() {
        let history = store()
        history.record(fact(.trusted, "p1", at: clockNow - 5 * hour))
        history.record(fact(.trusted, "p1", at: clockNow - 4 * hour))
        history.record(fact(.trusted, "p1", at: clockNow - 4 * hour, miff: "miffB"))

        XCTAssertEqual(facts(history).count, 1)
        XCTAssertEqual(facts(history, miff: "miffB").count, 1, "une autre Miff a son propre dernier fait")
    }

    func testAFirstUnknownPresenceMeansNothingWasMeasuredSoItIsNotKept() {
        let history = store()

        history.record(fact(.unknown, nil, at: clockNow - hour))
        XCTAssertEqual(facts(history), [])

        history.record(fact(.trusted, "p1", at: clockNow - 40 * 60 * 1000))
        history.record(fact(.unknown, nil, at: clockNow - 20 * 60 * 1000))
        XCTAssertEqual(facts(history).map { $0.state }, [.trusted, .unknown])
    }

    // MARK: - TS-2 : ce qui est écrit

    func testTheFileNeverHoldsACoordinateANameOrAPersonIdentifier() throws {
        let history = store()
        history.record(fact(.trusted, "p1", at: clockNow - 5 * hour))
        history.record(fact(.danger, "r1", at: clockNow - 4 * hour))

        let text = try String(contentsOf: fileURL, encoding: .utf8)
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any])
        let stored = try XCTUnwrap(object["facts"] as? [[String: Any]])

        XCTAssertTrue(Set(object.keys).isSubset(of: ["version", "windowDays", "facts"]))
        for entry in stored {
            XCTAssertTrue(Set(entry.keys).isSubset(of: ["miff", "state", "place", "at"]))
        }
        for forbidden in ["lat", "lng", "latitude", "longitude", "address", "name", "uid", "email"] {
            XCTAssertFalse(text.contains("\"\(forbidden)\""), forbidden)
        }
    }

    func testAZoneKeyWhichCarriesCoordinatesIsRefusedAsAPlaceIdentifier() {
        let history = store()
        history.record(fact(.trusted, TrackingFixtures.home.key, at: clockNow - hour))
        history.record(fact(.trusted, "p1|48.85|2.35|100|trusted", at: clockNow - hour))

        XCTAssertEqual(facts(history), [])
        XCTAssertFalse(FileManager.default.fileExists(atPath: fileURL.path))
    }

    func testAMiffIdentifierWhichIsNotAnIdentifierIsRefused() {
        let history = store()

        history.record(fact(.out, nil, at: clockNow - hour, miff: "Famille Martin"))
        history.record(fact(.out, nil, at: clockNow - hour, miff: ""))

        XCTAssertFalse(FileManager.default.fileExists(atPath: fileURL.path))
    }

    // MARK: - TS-3 : exclu des sauvegardes

    func testTheFolderIsExcludedFromBackups() throws {
        let history = store()
        history.record(fact(.trusted, "p1", at: clockNow - hour))

        let values = try directory.resourceValues(forKeys: [.isExcludedFromBackupKey])

        XCTAssertEqual(values.isExcludedFromBackup, true)
    }

    // MARK: - TS-7 : 35 jours

    func testFactsOlderThanThirtyFiveDaysAreErased() throws {
        let history = store()
        history.record(fact(.trusted, "p1", at: clockNow - 34 * day))
        history.record(fact(.out, nil, at: clockNow - 33 * day))
        clockNow += 2 * day

        XCTAssertEqual(facts(history).map { $0.state }, [.out], "le fait de 36 jours ne se lit plus")
        history.record(fact(.trusted, "p1", at: clockNow - hour))
        let text = try String(contentsOf: fileURL, encoding: .utf8)
        XCTAssertEqual(text.components(separatedBy: "\"miff\"").count - 1, 2, "et il est réécrit sans lui")
    }

    // MARK: - Un stockage illisible n'est jamais effacé ; un fichier abîmé est réinitialisé

    func testAnUnreadableFileKeepsTheFactInMemoryAndIsNeverErased() throws {
        let history = store()
        // Un DOSSIER à la place du fichier : il existe mais ne se lit pas (comme un fichier verrouillé avant le premier déverrouillage).
        try FileManager.default.createDirectory(at: fileURL, withIntermediateDirectories: true)

        history.record(fact(.trusted, "p1", at: clockNow - hour))

        XCTAssertEqual(history.read(miffId: "miffA"), .unreadable)
        XCTAssertTrue(FileManager.default.fileExists(atPath: fileURL.path), "rien n'est effacé")

        try FileManager.default.removeItem(at: fileURL)
        XCTAssertEqual(facts(history).map { $0.state }, [.trusted], "écrit dès que le stockage se lit")
    }

    func testACorruptFileIsResetAndTheChartStartsAgainFromNoData() throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data("pas du JSON {{{".utf8).write(to: fileURL)
        let history = store()

        XCTAssertEqual(facts(history), [])
        XCTAssertFalse(FileManager.default.fileExists(atPath: fileURL.path))
        history.record(fact(.trusted, "p1", at: clockNow - hour))
        XCTAssertEqual(facts(history).count, 1, "il redevient utilisable")
    }

    // MARK: - Effacement

    func testPurgeRemovesOneMiffOnly() {
        let history = store()
        history.record(fact(.trusted, "p1", at: clockNow - 5 * hour))
        history.record(fact(.trusted, "p1", at: clockNow - 5 * hour, miff: "miffB"))

        history.purge(miffId: "miffA")

        XCTAssertEqual(facts(history), [])
        XCTAssertEqual(facts(history, miff: "miffB").count, 1)
    }

    func testRetainOnlyErasesEveryOtherMiff() {
        let history = store()
        for miff in ["miffA", "miffB", "miffC"] {
            history.record(fact(.trusted, "p1", at: clockNow - 5 * hour, miff: miff))
        }

        history.retainOnly(miffIds: ["miffB"])

        XCTAssertEqual(facts(history, miff: "miffA"), [])
        XCTAssertEqual(facts(history, miff: "miffB").count, 1)
        XCTAssertEqual(facts(history, miff: "miffC"), [])
    }

    func testEraseFactsKeepsTheChoiceOfWindowButClearAllDeletesTheFile() {
        let history = store()
        history.setWindowDays(14)
        history.record(fact(.trusted, "p1", at: clockNow - 5 * hour))

        history.eraseFacts()
        XCTAssertEqual(facts(history), [])
        XCTAssertEqual(history.windowDays(), 14, "« Effacer mon historique » ne change pas le choix 7 / 14 / 30")

        history.clearAll()
        XCTAssertFalse(FileManager.default.fileExists(atPath: directory.path))
        XCTAssertEqual(store().windowDays(), TimeShareLimits.defaultWindowDays, "la fenêtre est oubliée aussi")
    }

    func testForgettingAPlaceTurnsItsFactsIntoUnknownAndNeverIntoAnotherPlace() {
        let history = store()
        history.record(fact(.out, nil, at: clockNow - 30 * hour))
        history.record(fact(.trusted, "gone", at: clockNow - 20 * hour))
        history.record(fact(.out, "gone", at: clockNow - 10 * hour))

        history.forgetPlaces(miffId: "miffA", keeping: ["p1"])

        let kept = facts(history)
        XCTAssertEqual(kept.map { $0.state }, [.out, .unknown, .out], "l'état d'avant ne se prolonge pas")
        XCTAssertNil(kept[1].placeId)
        XCTAssertEqual(kept[1].atMillis, clockNow - 20 * hour)
    }

    func testForgettingAPlaceMergesTheUnknownFactsItLeavesNextToEachOther() {
        let history = store()
        history.record(fact(.trusted, "p1", at: clockNow - 20 * hour))
        history.record(fact(.trusted, "gone", at: clockNow - 10 * hour))
        history.record(fact(.danger, "alsoGone", at: clockNow - 5 * hour))

        history.forgetPlaces(miffId: "miffA", keeping: ["p1"])

        XCTAssertEqual(facts(history).map { $0.state }, [.trusted, .unknown])
    }

    // MARK: - Le choix de fenêtre

    func testTheWindowChoiceIsKeptWithTheJournalAndAnInvalidOneIsIgnored() {
        let history = store()
        XCTAssertEqual(history.windowDays(), 7)

        history.setWindowDays(30)
        history.setWindowDays(12)

        XCTAssertEqual(store().windowDays(), 30)
    }

    // MARK: - Un fait « dehors » sans lieu ; oublier un lieu ne raccourcit pas l'historique

    func testAnOutFactIsWrittenWithoutAnyPlaceIdentifier() throws {
        let history = store()
        history.record(fact(.trusted, "p1", at: clockNow - 5 * hour))
        history.record(fact(.out, "p1", at: clockNow - 3 * hour))

        let text = try String(contentsOf: fileURL, encoding: .utf8)
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any])
        let stored = try XCTUnwrap(object["facts"] as? [[String: Any]])

        XCTAssertEqual(stored.count, 2)
        XCTAssertNil(stored[1]["place"], "le lieu qu'on vient de quitter n'est pas gardé")
        XCTAssertNotNil(stored[0]["place"])
    }

    func testAJournalWrittenBeforeTheRuleIsReadWithoutThePlaceOfItsOutFactsAndTheRepetitionsAreMerged() throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let old = """
        {"version":1,"facts":[
        {"miff":"miffA","state":"trusted","place":"p1","at":\(clockNow - 10 * hour)},
        {"miff":"miffA","state":"out","place":"p1","at":\(clockNow - 8 * hour)},
        {"miff":"miffA","state":"out","place":"p2","at":\(clockNow - 6 * hour)}]}
        """
        try Data(old.utf8).write(to: fileURL)

        let read = facts(store())

        XCTAssertEqual(read.map { $0.state }, [.trusted, .out], "deux « dehors » de suite : un seul")
        XCTAssertNil(read[1].placeId)
        XCTAssertEqual(read[1].atMillis, clockNow - 8 * hour, "le premier est gardé")
    }

    func testForgettingThePlaceOfTheFirstFactKeepsTheHistoryLengthWithAnUnknownFirstFact() {
        let history = store()
        let first = clockNow - 6 * day
        history.record(fact(.trusted, "gone", at: first))
        history.record(fact(.out, nil, at: clockNow - 5 * day))

        history.forgetPlaces(miffId: "miffA", keeping: ["p1"])

        let kept = facts(history)
        XCTAssertEqual(kept.map { $0.state }, [.unknown, .out])
        XCTAssertEqual(kept.first?.atMillis, first, "le décompte ne recule pas")
        XCTAssertNil(kept.first?.placeId)
    }

    func testForgettingEveryPlaceLeavesNoPlaceIdentifierInTheFile() throws {
        let history = store()
        history.record(fact(.trusted, "gone", at: clockNow - 5 * hour))
        history.record(fact(.out, "gone", at: clockNow - 4 * hour))
        history.record(fact(.danger, "alsoGone", at: clockNow - 3 * hour))

        history.forgetPlaces(miffId: "miffA", keeping: [])

        let text = try String(contentsOf: fileURL, encoding: .utf8)
        XCTAssertFalse(text.contains("gone"), "aucun identifiant de lieu supprimé ne reste")
        XCTAssertFalse(text.contains("Gone"))
    }
}
