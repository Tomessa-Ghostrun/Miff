import XCTest
@testable import Miff

/// Un départ que la personne a choisi (quitter, supprimer) ne lui est jamais annoncé comme une perte d'accès (MIFF-4), mais le
/// cache du téléphone est purgé et la liste des Miffs relue.
@MainActor
final class MiffDirectoryDepartureTests: XCTestCase {
    private let repo = FakeMiffRepository()
    private let active = MemoryActiveMiffStore()
    private let timeline = Timeline()
    private lazy var cache = FakeCachePurger(timeline: timeline)

    private func makeDirectory() -> MiffDirectory {
        MiffDirectory(repository: repo, currentUid: { "uid-1" }, activeStore: active, cache: cache)
    }

    private func lost() -> MyMiffsLoad {
        let item = LostMiff(ref: MembershipRef(key: "free", miffId: testMiffId), name: "Chez nous")
        return .loaded(MyMiffs(miffs: [], lost: [item], premium: false, fromCache: false))
    }

    func testAfterAnOwnDepartureTheCacheIsPurgedAndTheListIsReadAgain() async {
        let directory = makeDirectory()

        await directory.afterOwnDeparture(miffId: testMiffId)

        XCTAssertEqual(timeline.entries.first, "markPending")
        XCTAssertTrue(timeline.entries.contains("purge"))
        XCTAssertTrue(repo.calls.contains("myMiffs"))
        XCTAssertTrue(directory.notices.isEmpty)
    }

    func testTheLossOfAMiffThePersonLeftOnPurposeIsNeverAnnounced() async {
        let directory = makeDirectory()
        directory.expectDeparture(miffId: testMiffId)
        repo.myMiffsResult = lost()

        let confirmed = await directory.onAccessLost(miffId: testMiffId, name: "Chez nous")

        XCTAssertTrue(confirmed, "le nettoyage a bien lieu")
        XCTAssertEqual(repo.cleaned.count, 1)
        XCTAssertTrue(directory.notices.isEmpty, "mais aucun message « tu n'as plus accès »")
    }

    func testTheSameLossStillAnnouncedForAMiffTheyDidNotLeave() async {
        let directory = makeDirectory()
        directory.expectDeparture(miffId: testOtherMiffId)
        repo.myMiffsResult = lost()

        _ = await directory.onAccessLost(miffId: testMiffId, name: "Chez nous")

        XCTAssertEqual(directory.notices.count, 1)
    }

    func testTheExpectedDeparturesAreForgottenAtTheEndOfTheSession() async {
        let directory = makeDirectory()
        directory.expectDeparture(miffId: testMiffId)
        await directory.stop()
        repo.myMiffsResult = lost()

        _ = await directory.onAccessLost(miffId: testMiffId, name: "Chez nous")

        XCTAssertEqual(directory.notices.count, 1)
    }
}
