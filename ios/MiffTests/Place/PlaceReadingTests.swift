import XCTest
@testable import Miff

/// La lecture des lieux : le détail (maquette 16), la liste de la Miff (maquette 17) et le message d'après un enregistrement
/// (04L) (ticket LOC-3, miroir de `PlaceReadingTest`).
@MainActor
final class PlaceReadingTests: XCTestCase {
    private let repo = FakeMiffRepository()
    private let network = FakeNetwork()
    private var observing: [Task<Void, Never>] = []
    private var lost: [String] = []
    private var confirmLoss = true

    private let creator = MiffMember(
        uid: "uid-owner",
        firstName: "Emmanuelle",
        lastName: "Martin",
        avatarJpeg: nil,
        role: .owner,
        sharingEnabled: true
    )
    private let other = MiffMember(
        uid: "u2",
        firstName: "Ryadh",
        lastName: "Pluchard",
        avatarJpeg: nil,
        role: .member,
        sharingEnabled: true
    )

    private func data(fromCache: Bool = false, places: [Place] = []) -> MiffObservation {
        .data(
            MiffSnapshot(
                miff: testMiffDetails(name: "Famille Martin", owner: "uid-owner"),
                members: [creator, other],
                places: places,
                fromCache: fromCache
            )
        )
    }

    private func detail(_ uid: String) -> PlaceDetailViewModel {
        let dependencies = PlaceDetailDependencies(
            repository: repo,
            network: network,
            currentUid: { uid },
            onAccessLost: { [weak self] miffId, _ in
                self?.lost.append(miffId)
                return self?.confirmLoss ?? false
            }
        )
        let model = PlaceDetailViewModel(miffId: testMiffId, placeId: "p1", dependencies: dependencies)
        let listening = Task {
            await model.observe()
        }
        observing.append(listening)
        addTeardownBlock {
            listening.cancel()
        }
        return model
    }

    private func emit(_ observation: MiffObservation) {
        repo.observer.emit(observation, miffId: testMiffId)
    }

    // MARK: - Le détail

    func testDetailCreatorCanEditAndIsToldTheyMadeIt() async {
        let model = detail("uid-owner")

        emit(data(places: [testPlace(id: "p1", name: "Maison de Papa", createdBy: "uid-owner")]))
        await waitUntil { model.state.status == .ready }

        let state = model.state
        XCTAssertTrue(state.canEdit)
        XCTAssertTrue(state.creatorIsMe)
        XCTAssertEqual(state.creatorName, "Emmanuelle Martin")
        XCTAssertEqual(state.miffName, "Famille Martin")
        XCTAssertEqual(state.place?.name, "Maison de Papa")
    }

    func testDetailMemberReadsOnlyAndSeesWhoMadeIt() async {
        let model = detail("u2")

        emit(data(places: [testPlace(id: "p1", name: "Maison de Papa", createdBy: "uid-owner")]))
        await waitUntil { model.state.status == .ready }

        XCTAssertFalse(model.state.canEdit)
        XCTAssertFalse(model.state.creatorIsMe)
        XCTAssertEqual(model.state.creatorName, "Emmanuelle Martin")
    }

    func testDetailAPlaceThatIsGoneForTheServerIsNoticedButNotWhenTheCacheSimplyLacksIt() async {
        let model = detail("u2")

        emit(data(fromCache: true))
        await pause()
        // « Pas dans le cache » n'est pas « supprimé ».
        XCTAssertEqual(model.state.status, .loading)

        emit(data(fromCache: false))
        await waitUntil { model.state.status == .gone }
        XCTAssertEqual(model.state.status, .gone)
    }

    func testDetailALostAccessIsConfirmedByTheDirectoryAndLeavesTheMessageToTheHost() async {
        let model = detail("u2")

        emit(.accessLost)
        await waitUntil { !lost.isEmpty }

        XCTAssertEqual(lost, [testMiffId])
        XCTAssertEqual(model.state.status, .accessLost)
        XCTAssertNil(model.state.place)
        // La même perte n'est pas signalée deux fois.
        emit(.accessLost)
        await pause()
        XCTAssertEqual(lost, [testMiffId])
    }

    func testDetailALossThatTheServerDoesNotConfirmIsNotARemovalButARetry() async {
        confirmLoss = false
        let model = detail("u2")

        emit(.accessLost)
        await waitUntil { model.state.status == .unavailable }

        XCTAssertEqual(model.state.status, .unavailable)
        model.retry()
        XCTAssertEqual(model.state.status, .loading)
    }

    func testDetailAFailureThatIsNotALossKeepsWhatIsShownOrSaysItCalmly() async {
        let model = detail("u2")

        emit(.failed)
        await waitUntil { model.state.status == .unavailable }
        XCTAssertEqual(model.state.status, .unavailable)

        emit(data(places: [testPlace(id: "p1", name: "Maison")]))
        await waitUntil { model.state.status == .ready }
        emit(.failed)
        await pause()
        XCTAssertEqual(model.state.status, .ready)
    }

    func testDetailTheNetworkIsToldToTheScreen() async {
        let model = detail("u2")
        network.online = false

        await waitUntil { model.state.offline }

        XCTAssertTrue(model.state.offline)
        network.online = true
        await waitUntil { !model.state.offline }
        XCTAssertFalse(model.state.offline)
    }

    func testDetailShowsNothingInItsDescription() {
        XCTAssertEqual(PlaceDetailState().description, "PlaceDetailState(redacted)")
    }

    private func pause() async {
        try? await Task.sleep(nanoseconds: 80_000_000)
    }

    // MARK: - Le message après un enregistrement

    func testNoticeIsKeptInMemoryUntilTheScreenIsLeftAndGoesWithTheSession() {
        let notices = PlaceNotices()
        XCTAssertNil(notices.notice)

        notices.saved(name: "Maison de Papa")
        XCTAssertEqual(notices.notice, .saved(name: "Maison de Papa"))
        XCTAssertFalse("\(String(describing: notices.notice))".contains("Papa"))

        notices.consume()
        XCTAssertNil(notices.notice)

        notices.gone()
        XCTAssertEqual(notices.notice, .gone)

        notices.saved(name: "Autre")
        notices.stop()
        XCTAssertNil(notices.notice)
    }
}
