import XCTest
@testable import Miff

/// Les Miffs de la personne en mémoire, et l'UNIQUE endroit qui traite une perte d'accès (ticket Miff §5.7, critères D1 à D6) :
/// nettoyage, purge du cache, un seul message, aucune boucle.
@MainActor
final class MiffDirectoryTests: XCTestCase {
    private let repo = FakeMiffRepository()
    private let active = MemoryActiveMiffStore()
    private let timeline = Timeline()
    private lazy var cache = FakeCachePurger(timeline: timeline)
    private let uid = TestBox<String?>("uid-1")

    private func makeDirectory() -> MiffDirectory {
        let current = uid
        return MiffDirectory(repository: repo, currentUid: { current.value }, activeStore: active, cache: cache)
    }

    private func loaded(
        miffs: [MiffSummary] = [],
        lost: [LostMiff] = [],
        premium: Bool = false,
        fromCache: Bool = false
    ) -> MyMiffsLoad {
        .loaded(MyMiffs(miffs: miffs, lost: lost, premium: premium, fromCache: fromCache))
    }

    private func lost(id: String = testMiffId, name: String? = "Chez nous") -> LostMiff {
        LostMiff(ref: MembershipRef(key: "free", miffId: id), name: name)
    }

    private func ready(_ directory: MiffDirectory) -> DirectoryReady? {
        if case .ready(let value) = directory.state {
            return value
        }
        return nil
    }

    func testNoMiffIsReadyAndEmptySoTheHomeShowsTheTwoWaysToBegin() async {
        let directory = makeDirectory()
        repo.myMiffsResult = loaded()

        await directory.refresh()

        XCTAssertEqual(ready(directory)?.miffs.isEmpty, true)
        XCTAssertNil(ready(directory)?.active)
    }

    func testTheActiveMiffIsTheLastChosenElseTheFirstAndAChoiceIsKept() async {
        let directory = makeDirectory()
        repo.myMiffsResult = loaded(
            miffs: [MiffSummary(id: testMiffId, name: "A"), MiffSummary(id: testOtherMiffId, name: "B")],
            premium: true
        )
        await directory.refresh()
        XCTAssertEqual(ready(directory)?.activeId, testMiffId)

        directory.select(miffId: testOtherMiffId)

        XCTAssertEqual(active.value, testOtherMiffId)
        await directory.refresh()
        XCTAssertEqual(ready(directory)?.active?.name, "B")
        directory.select(miffId: "not-one-of-mine")
        XCTAssertEqual(active.value, testOtherMiffId)
    }

    func testALostMiffIsCleanedUpThePhoneCacheIsPurgedAndTheMessageIsGivenOnce() async {
        let directory = makeDirectory()
        repo.myMiffsResult = loaded(lost: [lost()])

        await directory.refresh()

        XCTAssertEqual(repo.cleaned.count, 1)
        XCTAssertEqual(timeline.entries.first, "markPending")
        XCTAssertTrue(timeline.entries.contains("purge"))
        let marked = timeline.entries.firstIndex(of: "markPending") ?? Int.max
        let purged = timeline.entries.firstIndex(of: "purge") ?? Int.min
        XCTAssertLessThan(marked, purged, "le nettoyage vient avant la purge")
        XCTAssertEqual(directory.notices.count, 1)
        XCTAssertEqual(directory.notices.first?.name, "Chez nous")
        // La même perte revue au rafraîchissement suivant n'est pas annoncée de nouveau : pas de boucle (critère D1).
        await directory.refresh()
        XCTAssertEqual(directory.notices.count, 1)
        directory.dismissNotice()
        await directory.refresh()
        XCTAssertTrue(directory.notices.isEmpty)
    }

    func testTheMessageIsTheSameWhateverTheCauseAndDoesNotNameOne() async {
        let directory = makeDirectory()
        // Retiré, ou Miff supprimée : l'annuaire sait seulement « perdue » ; il n'y a aucune cause à montrer.
        repo.myMiffsResult = loaded(lost: [lost(name: nil)])

        await directory.refresh()

        XCTAssertEqual(directory.notices.count, 1)
        XCTAssertNil(directory.notices.first?.name)
    }

    func testANetworkProblemNeverMakesAMessageOrACleanUp() async {
        let directory = makeDirectory()
        repo.myMiffsResult = .unavailable

        await directory.refresh()

        XCTAssertEqual(directory.state, .unavailable)
        XCTAssertTrue(directory.notices.isEmpty)
        XCTAssertTrue(repo.cleaned.isEmpty)
        XCTAssertEqual(cache.purgeCalls, 0)
    }

    func testAnAnswerThatFailsAfterAGoodOneKeepsWhatWasKnown() async {
        let directory = makeDirectory()
        repo.myMiffsResult = loaded(miffs: [MiffSummary(id: testMiffId, name: "A")])
        await directory.refresh()

        repo.myMiffsResult = .unavailable
        await directory.refresh()

        XCTAssertNotNil(ready(directory))
    }

    func testTheMiffScreenSeeingTheLossHasTheSameEffectsWithTheNameItKnew() async {
        let directory = makeDirectory()
        repo.myMiffsResult = loaded(miffs: [MiffSummary(id: testMiffId, name: "Chez nous")])
        await directory.refresh()
        repo.myMiffsResult = loaded(lost: [lost()])

        let confirmed = await directory.onAccessLost(miffId: testMiffId, name: "Chez nous")

        XCTAssertTrue(confirmed)
        XCTAssertEqual(directory.notices.first?.name, "Chez nous")
        XCTAssertEqual(repo.cleaned.count, 1)
        XCTAssertEqual(ready(directory)?.miffs.isEmpty, true)
        XCTAssertEqual(cache.purgeCalls, 1)
        // Vue une fois par l'écran, puis par un rafraîchissement : toujours un seul message.
        await directory.refresh()
        XCTAssertEqual(directory.notices.count, 1)
    }

    func testEndOfSessionForgetsTheMiffsTheMessagesAndTheChoice() async {
        let directory = makeDirectory()
        repo.myMiffsResult = loaded(
            miffs: [MiffSummary(id: testMiffId, name: "A")],
            lost: [lost(id: testOtherMiffId)]
        )
        await directory.refresh()
        directory.select(miffId: testMiffId)

        await directory.stop()

        XCTAssertEqual(directory.state, .loading)
        XCTAssertTrue(directory.notices.isEmpty)
        XCTAssertNil(active.value)
    }

    func testWithoutASignedInPersonNothingIsRead() async {
        uid.value = nil

        await makeDirectory().refresh()

        XCTAssertFalse(repo.calls.contains("myMiffs"))
    }

    func testARefusalHeardByTheScreenWhileTheServerCannotBeReachedGivesZeroMessageAndCleansNothing() async {
        let directory = makeDirectory()
        repo.myMiffsResult = .unavailable

        let confirmed = await directory.onAccessLost(miffId: testMiffId, name: "Chez nous")

        XCTAssertFalse(confirmed)
        XCTAssertTrue(directory.notices.isEmpty, "personne n'apprend qu'on l'a retiré sur un doute")
        XCTAssertTrue(repo.cleaned.isEmpty)
        XCTAssertEqual(cache.purgeCalls, 0)
    }

    func testARefusalHeardByTheScreenThatTheServerDoesNotConfirmGivesZeroMessage() async {
        let directory = makeDirectory()
        // Le serveur dit que la Miff est là et en ordre : l'écoute s'est trompée (ou la réponse est arrivée trop tôt).
        repo.myMiffsResult = loaded(miffs: [MiffSummary(id: testMiffId, name: "Chez nous")])

        let confirmed = await directory.onAccessLost(miffId: testMiffId, name: "Chez nous")

        XCTAssertFalse(confirmed)
        XCTAssertTrue(directory.notices.isEmpty)
        XCTAssertTrue(repo.cleaned.isEmpty)
        XCTAssertEqual(ready(directory)?.active?.name, "Chez nous")
    }

    func testAConfirmedLossIsAnnouncedOnlyAfterTheServerAnswered() async {
        let directory = makeDirectory()
        repo.myMiffsResult = loaded(lost: [lost()])

        let confirmed = await directory.onAccessLost(miffId: testMiffId, name: nil)

        XCTAssertTrue(confirmed)
        XCTAssertEqual(directory.notices.first?.name, "Chez nous")
    }

    func testTheFirstReadOfTheSessionHappensOnceNotAtEveryReturnToTheHome() async {
        let directory = makeDirectory()
        repo.myMiffsResult = loaded(miffs: [MiffSummary(id: testMiffId, name: "A")])

        await directory.refreshIfNeeded()
        await directory.refreshIfNeeded()
        await directory.refreshIfNeeded()

        XCTAssertEqual(repo.calls.filter { $0 == "myMiffs" }.count, 1)
    }

    func testAFailedFirstReadIsTriedAgainAtTheNextOpening() async {
        let directory = makeDirectory()
        repo.myMiffsResult = .unavailable
        await directory.refreshIfNeeded()
        XCTAssertEqual(directory.state, .unavailable)

        repo.myMiffsResult = loaded(miffs: [MiffSummary(id: testMiffId, name: "A")])
        await directory.refreshIfNeeded()

        XCTAssertNotNil(ready(directory))
    }

    func testAnAnswerForAnotherAccountIsNeverPublished() async {
        let directory = makeDirectory()
        let current = uid
        // L'identifiant de la personne change PENDANT la lecture : ce qui a été lu appartient à l'ancien compte.
        repo.onMyMiffs = { current.value = "uid-2" }
        repo.myMiffsResult = loaded(miffs: [MiffSummary(id: testMiffId, name: "A")])

        await directory.refresh()

        XCTAssertEqual(directory.state, .loading)
    }
}
