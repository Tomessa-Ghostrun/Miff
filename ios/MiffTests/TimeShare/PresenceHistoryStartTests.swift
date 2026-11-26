import XCTest
@testable import Miff

/// Le DÉBUT du suivi d'une Miff dans le journal local sur disque (« Ton graphique arrive dans X jours » compte à partir de l'armement de la
/// détection, avec ou sans premier fait) : seul le premier appel compte, jamais dans le futur, un identifiant et une heure seulement, effacé
/// avec la Miff, avec le bouton de Réglages et à la déconnexion.
final class PresenceHistoryStartTests: XCTestCase {
    private let day = TimeShareLimits.dayMillis
    private let hour = TimeShareLimits.hourMillis
    private var directory = URL(fileURLWithPath: "/")
    private var clockNow = 1_800_000_000_000

    override func setUp() {
        super.setUp()
        directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("presence-start-\(UUID().uuidString)", isDirectory: true)
        clockNow = 1_800_000_000_000
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: directory)
        super.tearDown()
    }

    private func store() -> PresenceHistoryFileStore {
        PresenceHistoryFileStore(directory: directory, clock: { self.clockNow })
    }

    private func fact(_ state: PresenceState, _ place: String? = nil, at: Int) -> PresenceFact {
        PresenceFact(miffId: "miffA", state: state, placeId: place, atMillis: at)
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

    func testTheStartOfTheFollowUpIsKeptOnTheFirstCallOnlyAndSurvivesARestart() {
        let first = clockNow - 2 * day
        store().begin(miffId: "miffA", atMillis: first)
        store().begin(miffId: "miffA", atMillis: clockNow - hour)

        XCTAssertEqual(store().startedAt(miffId: "miffA"), first, "le premier armement compte, relu depuis le fichier")
        XCTAssertNil(store().startedAt(miffId: "miffB"))
        if case .facts(let found) = store().read(miffId: "miffA") {
            XCTAssertTrue(found.isEmpty, "le début du suivi n'est pas un fait")
        } else {
            XCTFail("le stockage devrait être lisible")
        }
    }

    func testAStartInTheFutureIsBroughtBackToNow() {
        let history = store()

        history.begin(miffId: "miffA", atMillis: clockNow + 5 * day)

        XCTAssertEqual(history.startedAt(miffId: "miffA"), clockNow)
    }

    func testTheStartIsTheOlderOfTheArmingAndTheFirstFact() {
        let history = store()
        history.record(fact(.trusted, "p1", at: clockNow - 3 * day))
        history.begin(miffId: "miffA", atMillis: clockNow - day)

        XCTAssertEqual(history.startedAt(miffId: "miffA"), clockNow - 3 * day)
    }

    func testTheStartGoesWithTheMiffTheEraseButtonAndTheSignOut() {
        let history = store()
        history.begin(miffId: "miffA", atMillis: clockNow - day)
        history.begin(miffId: "miffB", atMillis: clockNow - day)
        history.begin(miffId: "miffC", atMillis: clockNow - day)

        history.purge(miffId: "miffA")
        XCTAssertNil(history.startedAt(miffId: "miffA"))
        history.retainOnly(miffIds: ["miffB"])
        XCTAssertNil(history.startedAt(miffId: "miffC"))
        XCTAssertNotNil(history.startedAt(miffId: "miffB"))
        history.eraseFacts()
        XCTAssertNil(history.startedAt(miffId: "miffB"), "« Effacer » : le décompte repart de 3")
        history.begin(miffId: "miffB", atMillis: clockNow)
        history.clearAll()
        XCTAssertNil(history.startedAt(miffId: "miffB"))
        XCTAssertFalse(FileManager.default.fileExists(atPath: fileURL.path), "déconnexion : le fichier part")
    }

    func testAFileWrittenBeforeTheStartExistedStillReadsAndCountsFromItsFirstFact() throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let json = """
        {"version":1,"windowDays":7,"facts":[{"miff":"miffA","state":"out","place":null,"at":\(clockNow - 2 * day)}]}
        """
        try json.write(to: fileURL, atomically: true, encoding: .utf8)
        let history = store()

        XCTAssertEqual(history.startedAt(miffId: "miffA"), clockNow - 2 * day)
        history.begin(miffId: "miffB", atMillis: clockNow - day)
        XCTAssertEqual(facts(history).count, 1, "l'ancien contenu est conservé")
    }

    func testTheStartWritesNothingButAnIdentifierAndAnInstant() throws {
        store().begin(miffId: "miffA", atMillis: clockNow - day)

        let text = try String(contentsOf: fileURL, encoding: .utf8)
        XCTAssertTrue(text.contains("\"since\""))
        XCTAssertTrue(text.contains("miffA"))
        XCTAssertFalse(text.contains("p1"))
    }

    func testTheDaysLeftOfAnEmptyJournalFollowTheArmingAcrossRestarts() {
        let first = clockNow
        store().begin(miffId: "miffA", atMillis: first)
        let home = TimeSharePlace(id: "p1", name: "Maison", type: .trusted, category: .home)

        func outcome(afterHours: Int) -> TimeShareOutcome {
            clockNow = first + afterHours * hour
            let history = store()
            history.begin(miffId: "miffA", atMillis: clockNow)
            return TimeShareCalculator.compute(
                facts: facts(history), places: [home], windowDays: 7, nowMillis: clockNow,
                startedAtMillis: history.startedAt(miffId: "miffA")
            )
        }

        XCTAssertEqual(outcome(afterHours: 1), .notEnough(daysLeft: 3))
        XCTAssertEqual(outcome(afterHours: 30), .notEnough(daysLeft: 2), "aucun fait, et pourtant le compteur baisse")
        XCTAssertEqual(outcome(afterHours: 55), .notEnough(daysLeft: 1))
        XCTAssertEqual(outcome(afterHours: 73), .noTimeMeasured)
    }
}
