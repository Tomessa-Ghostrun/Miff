import XCTest
@testable import Miff

/// Une lecture des membres d'une Miff : double en mémoire qui compte les lectures (le COÛT Firestore est ce qu'on prouve ici).
private final class FakeMemberPreviewReader: MemberPreviewReader {
    var answers: [String: MemberPreview] = [:]
    private(set) var reads: [String] = []

    func read(miffId: String) async -> MemberPreview? {
        reads.append(miffId)
        return answers[miffId]
    }
}

private final class ClockBox {
    var now = 1_000_000
}

/// La lecture paresseuse des membres pour les cartes du Profil : zéro lecture pour la Miff du Feed, une lecture au plus par Miff et par
/// ouverture du Profil, rien si une lecture récente existe, aucun écouteur, rien de gardé pour une Miff qui n'existe plus.
@MainActor
final class MiffMemberPreviewsTests: XCTestCase {
    private func member(_ uid: String) -> MiffMember {
        MiffMember(
            uid: uid, firstName: "Alex", lastName: "Martin", avatarJpeg: nil, role: .member, sharingEnabled: true
        )
    }

    private func preview(_ count: Int) -> MemberPreview {
        MemberPreview(members: (0..<count).map { member("u\($0)") })
    }

    private struct Fixture {
        let reader = FakeMemberPreviewReader()
        let clock = ClockBox()
    }

    private func makeStore(_ fixture: Fixture) -> MiffMemberPreviews {
        let clock = fixture.clock
        return MiffMemberPreviews(reader: fixture.reader, clock: { clock.now })
    }

    // MARK: - La Miff du Feed : zéro lecture

    func testTheMiffTheFeedAlreadyReadIsNeverReadAgain() async {
        let fixture = Fixture()
        let store = makeStore(fixture)
        store.record(miffId: "A", members: [member("u1"), member("u2")])

        store.beginOpening()
        await store.loadIfNeeded(miffId: "A", isActive: true)

        XCTAssertTrue(fixture.reader.reads.isEmpty)
        XCTAssertEqual(store.state(for: "A"), .known(preview(2)))
    }

    func testTheFeedMiffStaysFreshForeverWhileItIsTheDisplayedOne() async {
        let fixture = Fixture()
        let store = makeStore(fixture)
        store.record(miffId: "A", members: [member("u1")])
        fixture.clock.now += 3 * 60 * 60 * 1000

        store.beginOpening()
        await store.loadIfNeeded(miffId: "A", isActive: true)

        XCTAssertTrue(fixture.reader.reads.isEmpty)
    }

    func testAMiffThatIsNoLongerTheDisplayedOneIsReadAgainOnceItsFeedCopyIsOld() async {
        let fixture = Fixture()
        fixture.reader.answers["A"] = preview(3)
        let store = makeStore(fixture)
        store.record(miffId: "A", members: [member("u1")])
        fixture.clock.now += MiffMemberPreviews.freshMillis

        store.beginOpening()
        await store.loadIfNeeded(miffId: "A", isActive: false)

        XCTAssertEqual(fixture.reader.reads, ["A"])
        XCTAssertEqual(store.state(for: "A"), .known(preview(3)))
    }

    func testTheFeedKeepingTheCardUpToDateIsRecordedWithoutAnyRead() {
        let fixture = Fixture()
        let store = makeStore(fixture)

        store.record(miffId: "A", members: [member("u1")])
        store.record(miffId: "A", members: [member("u1"), member("u2")])

        XCTAssertEqual(store.state(for: "A"), .known(preview(2)))
        XCTAssertEqual(fixture.reader.reads.count, 0)
    }

    // MARK: - Les autres Miffs : paresseux, une fois

    func testAnotherMiffIsReadOnceWhenItsCardAppears() async {
        let fixture = Fixture()
        fixture.reader.answers["B"] = preview(4)
        let store = makeStore(fixture)
        XCTAssertEqual(store.state(for: "B"), .loading)

        store.beginOpening()
        await store.loadIfNeeded(miffId: "B", isActive: false)

        XCTAssertEqual(fixture.reader.reads, ["B"])
        XCTAssertEqual(store.state(for: "B"), .known(preview(4)))
    }

    func testTheSameMiffIsReadOnlyOnceEvenIfAskedManyTimesInOneOpening() async {
        let fixture = Fixture()
        fixture.reader.answers["B"] = preview(4)
        let store = makeStore(fixture)

        store.beginOpening()
        await store.loadIfNeeded(miffId: "B", isActive: false)
        await store.loadIfNeeded(miffId: "B", isActive: false)
        await store.loadIfNeeded(miffId: "B", isActive: false)

        XCTAssertEqual(fixture.reader.reads, ["B"])
    }

    func testReopeningTheProfileWithinFiveMinutesReadsNothing() async {
        let fixture = Fixture()
        fixture.reader.answers["B"] = preview(4)
        let store = makeStore(fixture)
        store.beginOpening()
        await store.loadIfNeeded(miffId: "B", isActive: false)

        fixture.clock.now += MiffMemberPreviews.freshMillis - 1
        store.beginOpening()
        await store.loadIfNeeded(miffId: "B", isActive: false)

        XCTAssertEqual(fixture.reader.reads, ["B"])
    }

    func testReopeningTheProfileAfterFiveMinutesReadsOnceMore() async {
        let fixture = Fixture()
        fixture.reader.answers["B"] = preview(4)
        let store = makeStore(fixture)
        store.beginOpening()
        await store.loadIfNeeded(miffId: "B", isActive: false)

        fixture.clock.now += MiffMemberPreviews.freshMillis
        fixture.reader.answers["B"] = preview(5)
        store.beginOpening()
        await store.loadIfNeeded(miffId: "B", isActive: false)

        XCTAssertEqual(fixture.reader.reads, ["B", "B"])
        XCTAssertEqual(store.state(for: "B"), .known(preview(5)))
    }

    func testOnlyTheMiffsWhoseCardIsShownAreRead() async {
        let fixture = Fixture()
        fixture.reader.answers = ["B": preview(2), "C": preview(3)]
        let store = makeStore(fixture)

        store.beginOpening()
        await store.loadIfNeeded(miffId: "B", isActive: false)

        XCTAssertEqual(fixture.reader.reads, ["B"])
        XCTAssertEqual(store.state(for: "C"), .loading)
    }

    // MARK: - Quand la lecture échoue

    func testAFailedReadIsCalmNoRetryInTheSameOpeningThenAnotherTryNextTime() async {
        let fixture = Fixture()
        let store = makeStore(fixture)
        store.beginOpening()

        await store.loadIfNeeded(miffId: "B", isActive: false)
        await store.loadIfNeeded(miffId: "B", isActive: false)

        XCTAssertEqual(fixture.reader.reads, ["B"], "pas de boucle de lecture")
        XCTAssertEqual(store.state(for: "B"), .unavailable)

        fixture.reader.answers["B"] = preview(2)
        store.beginOpening()
        XCTAssertEqual(store.state(for: "B"), .loading)
        await store.loadIfNeeded(miffId: "B", isActive: false)

        XCTAssertEqual(fixture.reader.reads, ["B", "B"])
        XCTAssertEqual(store.state(for: "B"), .known(preview(2)))
    }

    func testAFailedRereadKeepsWhatWasAlreadyKnown() async {
        let fixture = Fixture()
        fixture.reader.answers["B"] = preview(4)
        let store = makeStore(fixture)
        store.beginOpening()
        await store.loadIfNeeded(miffId: "B", isActive: false)

        fixture.clock.now += MiffMemberPreviews.freshMillis
        fixture.reader.answers["B"] = nil
        store.beginOpening()
        await store.loadIfNeeded(miffId: "B", isActive: false)

        XCTAssertEqual(store.state(for: "B"), .known(preview(4)))
    }

    // MARK: - Rien de gardé pour une Miff qui n'existe plus

    func testAMiffThatLeftTheDirectoryIsForgotten() async {
        let fixture = Fixture()
        fixture.reader.answers["B"] = preview(4)
        let store = makeStore(fixture)
        store.record(miffId: "A", members: [member("u1")])
        store.beginOpening()
        await store.loadIfNeeded(miffId: "B", isActive: false)

        store.retain(only: ["A"])

        XCTAssertEqual(store.state(for: "A"), .known(preview(1)))
        XCTAssertEqual(store.state(for: "B"), .loading)
    }
}
