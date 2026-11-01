import XCTest
@testable import Miff

/// Ce que l'écran d'une Miff a demandé à l'annuaire.
final class ScreenRecorder {
    var lost: [String] = []
    var nameChanges = 0
}

/// Ce que partagent les tests de l'écran d'une Miff : un dépôt double qui pilote l'écoute, un réseau double, et l'écoute lancée
/// dans une tâche (l'écran la lance à l'apparition, le système l'annule à la disparition).
@MainActor
class MiffScreenTestCase: XCTestCase {
    let repo = FakeMiffRepository()
    let network = FakeNetwork()
    let recorder = ScreenRecorder()
    var observing: [Task<Void, Never>] = []

    func screen(uid: String = "uid-owner", confirmLoss: Bool = true) -> MiffScreenViewModel {
        let log = recorder
        let dependencies = MiffScreenDependencies(
            repository: repo,
            network: network,
            currentUid: { uid },
            onAccessLost: { miffId, _ in
                log.lost.append(miffId)
                return confirmLoss
            },
            onNameChanged: { log.nameChanges += 1 }
        )
        let viewModel = MiffScreenViewModel(miffId: testMiffId, dependencies: dependencies)
        let listening = Task {
            await viewModel.observe()
        }
        observing.append(listening)
        // L'écoute s'arrête avec le test.
        addTeardownBlock {
            listening.cancel()
        }
        return viewModel
    }

    func data(
        owner: String = "uid-owner",
        members: [MiffMember]? = nil,
        code: String = testCode
    ) -> MiffObservation {
        let people = members ?? [testMember("uid-owner", role: .owner), testMember("uid-2")]
        return .data(
            MiffSnapshot(
                miff: testMiffDetails(code: code, owner: owner),
                members: people,
                places: [MiffPlace(id: "p1", name: "Maison")],
                fromCache: false
            )
        )
    }

    func readyScreen(uid: String = "uid-owner", members: [MiffMember]? = nil) async -> MiffScreenViewModel {
        let viewModel = screen(uid: uid)
        repo.observer.emit(data(members: members))
        await waitUntil { viewModel.state.status == .ready }
        return viewModel
    }
}
