import XCTest
@testable import Miff

/// Un lecteur de profil qui répond toujours la même chose, et compte ses lectures.
private final class CountingProfileReader: ProfileReading {
    let answer: ProfileSummary?
    private(set) var calls = 0

    init(_ answer: ProfileSummary?) {
        self.answer = answer
    }

    func read(uid: String) async -> ProfileSummary? {
        calls += 1
        return answer
    }
}

/// Un lecteur qui ne répond jamais (le téléphone attend le réseau) : jusqu'à ce que le test libère l'attente.
private final class HangingProfileReader: ProfileReading {
    private let sleeper: ManualSleeper

    init(_ sleeper: ManualSleeper) {
        self.sleeper = sleeper
    }

    func read(uid: String) async -> ProfileSummary? {
        await sleeper.sleep(-1)
        return nil
    }
}

// L'onglet Profil sans réseau : le même composant que le Feed (pop-in « Pas de connexion », « Réessayer », essai toutes les 10 s, arrêt
// en quittant l'onglet), pour que le profil ne reste pas sur un indicateur sans fin.
@MainActor
final class ProfileOfflinePopinTests: XCTestCase {
    private let network = FakeNetwork(online: false)
    private let sleeper = ManualSleeper()
    private var tasks: [Task<Void, Never>] = []

    override func tearDown() async throws {
        tasks.forEach { $0.cancel() }
        sleeper.fire()
        for task in tasks {
            await task.value
        }
        tasks = []
        try await super.tearDown()
    }

    private func makeModel(_ reader: ProfileReading) -> ProfileTabViewModel {
        let sleeper = self.sleeper
        return ProfileTabViewModel(
            reader: reader,
            currentUid: { "u1" },
            network: network,
            sleep: { millis in await sleeper.sleep(millis) }
        )
    }

    private func show(_ model: ProfileTabViewModel) -> Task<Void, Never> {
        let task = Task {
            await model.watchConnectivity()
        }
        tasks.append(task)
        return task
    }

    private var alex: ProfileSummary? {
        ProfileSummary.from(fields: ["firstName": "Alex", "lastName": "Martin"])
    }

    func testOfflineWhileTheProfileIsNeverReadThePopinComes() async {
        let model = makeModel(HangingProfileReader(sleeper))
        XCTAssertEqual(model.state, .loading)

        _ = show(model)
        await sleeper.elapse(1_500)
        await waitUntil { model.offlineRetry.isShown }

        XCTAssertTrue(model.offlineRetry.isShown)
    }

    func testClosingThePopinLeavesThePageToSayItAndTheTabStaysUsable() async {
        let model = makeModel(HangingProfileReader(sleeper))
        _ = show(model)
        await sleeper.elapse(1_500)
        await waitUntil { model.offlineRetry.isShown }

        model.offlineRetry.dismiss()

        XCTAssertFalse(model.offlineRetry.isPopinVisible, "la carte est fermée")
        XCTAssertTrue(
            model.offlineRetry.isInlineVisible,
            "l'onglet dit « Pas de connexion » à la place de l'indicateur"
        )
    }

    func testOfflineWithAnUnreadableProfileThePopinComesAndTheNetworkComingBackRereadsOnce() async {
        let reader = CountingProfileReader(nil)
        let model = makeModel(reader)
        await waitUntil { model.state == .unavailable }
        _ = show(model)
        await waitUntil { model.offlineRetry.isShown }
        XCTAssertEqual(reader.calls, 1)

        network.online = true
        await waitUntil { reader.calls == 2 }

        XCTAssertEqual(reader.calls, 2)
        XCTAssertFalse(model.offlineRetry.isShown)
    }

    func testAProfileAlreadyReadNeverShowsThePopin() async {
        let model = makeModel(CountingProfileReader(alex))
        await waitUntil { model.state.isReady }
        _ = show(model)
        await sleeper.settle()

        XCTAssertFalse(model.offlineRetry.isShown)
    }

    func testRetryOfflineDoesNotReadAndLeavingTheTabStopsEverything() async {
        let reader = CountingProfileReader(nil)
        let model = makeModel(reader)
        await waitUntil { model.state == .unavailable }
        let task = show(model)
        await waitUntil { model.offlineRetry.isShown }

        model.offlineRetry.retryNow()
        XCTAssertEqual(reader.calls, 1, "pas de réseau : aucune lecture")

        task.cancel()
        await task.value
        XCTAssertFalse(model.offlineRetry.isShown)
        network.online = true
        sleeper.fire()
        await sleeper.settle()
        XCTAssertEqual(reader.calls, 1, "plus aucun essai après la sortie de l'onglet")
    }
}
