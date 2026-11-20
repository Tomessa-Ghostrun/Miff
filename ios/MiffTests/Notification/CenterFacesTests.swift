import XCTest
@testable import Miff

/// CNC-I (critères CNC-10 et CNC-19) : les visages que le centre dessine, en mémoire seulement : 0 lecture pour la Miff que le Feed a lue et pour soi, UNE
/// lecture par membre et par session (relue après 5 minutes), une douzaine au plus à chaque ouverture, jamais en boucle après un échec, plus rien après la
/// fin de session. Miroir de `MemberFaceDirectoryTest` d'Android.
@MainActor
final class CenterFacesTests: XCTestCase {
    private let miff = "abcdefghijklmnopqrst"
    private let minute = 60_000
    private let photo = Data(repeating: 3, count: 500)

    private final class Clock {
        var now = 1_800_000_000_000
    }

    @MainActor
    private final class FakeReader: MemberFaceReading {
        var answers: [String: FaceRead] = [:]
        var fallback: FaceRead = .gone
        var whileReading: (() -> Void)?
        private(set) var reads: [String] = []

        func read(miffId: String, uid: String) async -> FaceRead {
            reads.append(uid)
            whileReading?()
            return answers[uid] ?? fallback
        }
    }

    private func member(_ uid: String, _ first: String, avatar: Data? = nil) -> MiffMember {
        MiffMember(
            uid: uid, firstName: first, lastName: "", avatarJpeg: avatar, role: .member, sharingEnabled: true
        )
    }

    private func face(_ name: String, photo: Data? = nil) -> ActorFace {
        ActorFace(firstName: name, initials: String(name.prefix(1)), photo: photo)
    }

    private func keys(_ uids: [String]) -> [FaceKey] {
        uids.map { FaceKey(miffId: miff, uid: $0) }
    }

    private func makeFaces(_ reader: FakeReader? = nil, clock: Clock = Clock()) -> CenterFaces {
        CenterFaces(reader: reader ?? FakeReader(), nowMillis: { clock.now })
    }

    // MARK: - Ce que le Feed a déjà lu : zéro lecture

    func testTheMembersTheFeedReadAreKnownWithoutAnyReading() async {
        let reader = FakeReader()
        let faces = makeFaces(reader)
        faces.seedFromFeed(miffId: miff, members: [member("uidB", "Léa", avatar: photo), member("uidC", "Zoé")])

        await faces.ensure(keys(["uidB", "uidC"]))

        XCTAssertTrue(reader.reads.isEmpty, "0 lecture")
        XCTAssertEqual(faces.lookup(miffId: miff, uid: "uidB"), .known(face("Léa", photo: photo)))
        XCTAssertEqual(faces.lookup(miffId: miff, uid: "uidC"), .known(face("Zoé")))
    }

    func testSomeoneWhoIsNotInAMiffTheFeedReadIsGoneWithoutAReading() async {
        let reader = FakeReader()
        let faces = makeFaces(reader)
        faces.seedFromFeed(miffId: miff, members: [member("uidB", "Léa")])

        await faces.ensure(keys(["uidX"]))

        XCTAssertEqual(faces.lookup(miffId: miff, uid: "uidX"), .gone, "parti : le logo et « Un proche »")
        XCTAssertTrue(reader.reads.isEmpty)
    }

    func testAMemberWhoLeftDisappearsWhenTheFeedReadsTheMiffAgain() {
        let faces = makeFaces()
        faces.seedFromFeed(miffId: miff, members: [member("uidB", "Léa"), member("uidC", "Zoé")])

        faces.seedFromFeed(miffId: miff, members: [member("uidB", "Léa")])

        XCTAssertEqual(faces.lookup(miffId: miff, uid: "uidC"), .gone)
    }

    func testTheNameAndThePhotoFollowAProfileTheFeedReadsAgain() {
        let faces = makeFaces()
        faces.seedFromFeed(miffId: miff, members: [member("uidB", "Léa", avatar: photo)])

        faces.seedFromFeed(miffId: miff, members: [member("uidB", "Léonie")])

        XCTAssertEqual(faces.lookup(miffId: miff, uid: "uidB"), .known(face("Léonie")), "les deux changent ensemble")
    }

    func testAPhotoLargerThanTheRulesAllowIsNeverKept() {
        let heavy = Data(repeating: 1, count: ProfileDocument.maxAvatarBytes + 1)
        let faces = makeFaces()

        faces.seedFromFeed(miffId: miff, members: [member("uidB", "Léa", avatar: heavy)])

        XCTAssertEqual(faces.lookup(miffId: miff, uid: "uidB"), .known(face("Léa")), "les initiales, jamais la photo")
    }

    func testAMemberWithoutAFirstNameIsCalledByTheFamilyName() {
        let nameless = MiffMember(
            uid: "uidB", firstName: "  ", lastName: "Martin", avatarJpeg: nil, role: .member, sharingEnabled: true
        )

        XCTAssertEqual(CenterFaces.face(of: nameless).firstName, "Martin")
    }

    // MARK: - Moi

    func testMyOwnFaceComesFromMyProfileWithoutAReading() {
        let faces = makeFaces()
        XCTAssertNil(faces.mine)

        faces.setMine(initials: "TM", photo: photo)

        XCTAssertEqual(faces.mine, ActorFace(firstName: "", initials: "TM", photo: photo))
        faces.setMine(initials: "TM", photo: Data(repeating: 1, count: ProfileDocument.maxAvatarBytes + 1))
        XCTAssertNil(faces.mine?.photo, "une photo trop lourde n'est jamais gardée")
    }

    // MARK: - Une lecture par membre et par session

    func testOneReadingPerMemberAndNoSecondOneWithinFiveMinutes() async {
        let reader = FakeReader()
        reader.answers["uidB"] = .found(face("Léa", photo: photo))
        let clock = Clock()
        let faces = makeFaces(reader, clock: clock)

        await faces.ensure(keys(["uidB"]))
        await faces.ensure(keys(["uidB"]))
        clock.now += 4 * minute
        await faces.ensure(keys(["uidB"]))

        XCTAssertEqual(reader.reads, ["uidB"])
        XCTAssertEqual(faces.lookup(miffId: miff, uid: "uidB"), .known(face("Léa", photo: photo)))

        clock.now += 2 * minute
        await faces.ensure(keys(["uidB"]))
        XCTAssertEqual(reader.reads, ["uidB", "uidB"], "relue après 5 minutes")
    }

    func testTheSameMemberAskedTwiceInOneCallIsReadOnce() async {
        let reader = FakeReader()
        let faces = makeFaces(reader)

        await faces.ensure(keys(["uidB", "uidB"]))

        XCTAssertEqual(reader.reads, ["uidB"])
    }

    func testADozenReadingsAtMostEachTimeAndTheRestWaitsForTheNextOpening() async {
        let reader = FakeReader()
        let faces = makeFaces(reader)
        let uids = (1...15).map { "uid\($0)" }

        await faces.ensure(keys(uids))
        XCTAssertEqual(reader.reads.count, 12)

        await faces.ensure(keys(uids))
        XCTAssertEqual(reader.reads.count, 15, "les trois restants à l'ouverture suivante")
    }

    func testTheThirteenthPersonShowsTheLogoUntilTheNextOpeningReadsHer() async {
        let reader = FakeReader()
        reader.answers["uid13"] = .found(face("Zoé"))
        let faces = makeFaces(reader)
        let uids = (1...13).map { "uid\($0)" }

        await faces.ensure(keys(uids))

        XCTAssertEqual(faces.lookup(miffId: miff, uid: "uid13"), .failed, "le logo, pas un rond neutre")
        await faces.ensure(keys(uids))
        XCTAssertEqual(faces.lookup(miffId: miff, uid: "uid13"), .known(face("Zoé")))
    }

    func testAMemberTheReadingSaysIsGoneShowsTheLogoAndIsNotReadAgainSoon() async {
        let reader = FakeReader()
        reader.answers["uidB"] = .gone
        let faces = makeFaces(reader)

        await faces.ensure(keys(["uidB"]))
        await faces.ensure(keys(["uidB"]))

        XCTAssertEqual(faces.lookup(miffId: miff, uid: "uidB"), .gone)
        XCTAssertEqual(reader.reads.count, 1)
    }

    func testBeforeTheReadingTheMemberIsPendingNotGone() {
        XCTAssertEqual(makeFaces().lookup(miffId: miff, uid: "uidB"), .pending, "un rond neutre, sans clignotement")
    }

    // MARK: - Un échec n'est jamais une boucle

    func testAFailedReadingIsNotAskedAgainBeforeThirtySecondsAndShowsNoError() async {
        let reader = FakeReader()
        reader.answers["uidB"] = .failed
        let clock = Clock()
        let faces = makeFaces(reader, clock: clock)

        await faces.ensure(keys(["uidB"]))
        await faces.ensure(keys(["uidB"]))
        clock.now += 20_000
        await faces.ensure(keys(["uidB"]))

        XCTAssertEqual(reader.reads.count, 1)
        XCTAssertEqual(faces.lookup(miffId: miff, uid: "uidB"), .failed)

        clock.now += 11_000
        await faces.ensure(keys(["uidB"]))
        XCTAssertEqual(reader.reads.count, 2, "à une nouvelle ouverture, après 30 secondes")
    }

    func testAPhotoDoesNotVanishBecauseTheNetworkBlinkedOnASecondReading() async {
        let reader = FakeReader()
        reader.answers["uidB"] = .found(face("Léa", photo: photo))
        let clock = Clock()
        let faces = makeFaces(reader, clock: clock)
        await faces.ensure(keys(["uidB"]))
        clock.now += 6 * minute
        reader.answers["uidB"] = .failed

        await faces.ensure(keys(["uidB"]))

        XCTAssertEqual(faces.lookup(miffId: miff, uid: "uidB"), .known(face("Léa", photo: photo)))
    }

    // MARK: - Les Miffs qui ne sont plus les siennes, la fin de session

    func testAMiffThatIsNotTheAccountsAnyMoreTakesItsFacesWithIt() {
        let faces = makeFaces()
        faces.seedFromFeed(miffId: miff, members: [member("uidB", "Léa")])
        faces.seedFromFeed(miffId: "autre", members: [member("uidC", "Zoé")])

        faces.retainOnly(miffIds: ["autre"])

        XCTAssertEqual(faces.lookup(miffId: miff, uid: "uidB"), .pending)
        XCTAssertEqual(faces.lookup(miffId: "autre", uid: "uidC"), .known(face("Zoé")))
    }

    func testTheEndOfTheSessionForgetsEveryFaceAndMine() {
        let faces = makeFaces()
        faces.seedFromFeed(miffId: miff, members: [member("uidB", "Léa", avatar: photo)])
        faces.setMine(initials: "TM", photo: photo)

        faces.stop()

        XCTAssertEqual(faces.lookup(miffId: miff, uid: "uidB"), .pending)
        XCTAssertNil(faces.mine)
    }

    func testAReadingThatArrivesAfterTheEndOfTheSessionBelongsToThePreviousPersonAndIsDropped() async {
        let reader = FakeReader()
        reader.answers["uidB"] = .found(face("Léa", photo: photo))
        let faces = makeFaces(reader)
        reader.whileReading = { faces.stop() }

        await faces.ensure(keys(["uidB"]))

        XCTAssertEqual(faces.lookup(miffId: miff, uid: "uidB"), .pending)
    }

    func testTheVersionMovesWhenSomethingIsLearnedSoTheLinesAreDrawnAgain() async {
        let reader = FakeReader()
        reader.answers["uidB"] = .found(face("Léa"))
        let faces = makeFaces(reader)
        let before = faces.version

        await faces.ensure(keys(["uidB"]))
        let afterReading = faces.version
        faces.seedFromFeed(miffId: miff, members: [member("uidC", "Zoé")])

        XCTAssertGreaterThan(afterReading, before)
        XCTAssertGreaterThan(faces.version, afterReading)
    }

    func testFacesShowNothingInALog() {
        XCTAssertEqual("\(makeFaces())", "CenterFaces(redacted)")
    }
}
