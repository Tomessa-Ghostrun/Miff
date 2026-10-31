// Scénarios de test longs par nature (un cas par méthode) : les limites de longueur ne s'appliquent pas ici.
// swiftlint:disable file_length type_body_length
import XCTest
@testable import Miff

/// Ce que l'écran d'une Miff a demandé à l'annuaire.
final class ScreenRecorder {
    var lost: [String] = []
    var nameChanges = 0
}

/// L'écran d'une Miff (ticket Miff, critères F, G, K, H, D) : le créateur et le membre, « Partager mon statut », la perte
/// d'accès qui n'est jamais une erreur réseau. Aucun Firestore : un dépôt double qui pilote l'écoute.
@MainActor
final class MiffScreenViewModelTests: XCTestCase {
    private let repo = FakeMiffRepository()
    private let network = FakeNetwork()
    private let recorder = ScreenRecorder()
    private var observing: [Task<Void, Never>] = []

    private func screen(uid: String = "uid-owner", confirmLoss: Bool = true) -> MiffScreenViewModel {
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

    private func data(
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

    private func waitUntil(_ condition: () -> Bool) async {
        for _ in 0..<300 where !condition() {
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
    }

    private func readyScreen(uid: String = "uid-owner", members: [MiffMember]? = nil) async -> MiffScreenViewModel {
        let viewModel = screen(uid: uid)
        repo.observer.emit(data(members: members))
        await waitUntil { viewModel.state.status == .ready }
        return viewModel
    }

    func testTheCreatorCanRenameAMemberCannot() async {
        let owner = await readyScreen(uid: "uid-owner")
        let member = await readyScreen(uid: "uid-2")

        XCTAssertTrue(owner.state.isOwner)
        XCTAssertFalse(member.state.isOwner)
        member.onNameInputChanged("Autre nom")
        XCTAssertEqual(member.state.nameInput, "Chez nous", "ce qu'un membre tape est ignoré")
        member.onSaveName()
        try? await Task.sleep(nanoseconds: 30_000_000)
        XCTAssertTrue(repo.renamed.isEmpty, "un membre ne renomme jamais")
    }

    func testSaveIsActiveOnlyIfTheNameChangedAndIsValidAndRenamesInOneCall() async {
        let viewModel = await readyScreen()
        XCTAssertFalse(viewModel.state.canSaveName, "rien n'a changé")

        viewModel.onNameInputChanged("a")
        XCTAssertFalse(viewModel.state.canSaveName, "trop court")
        viewModel.onNameInputChanged("Chez nous  ")
        XCTAssertFalse(viewModel.state.canSaveName, "le même nom une fois les espaces retirés")
        viewModel.onNameInputChanged("Nouveau nom")
        XCTAssertTrue(viewModel.state.canSaveName)
        viewModel.onSaveName()
        viewModel.onSaveName()
        await waitUntil { viewModel.state.nameSaved && self.recorder.nameChanges == 1 }

        XCTAssertEqual(repo.renamed, [RenameCall(miffId: testMiffId, code: testCode, name: "Nouveau nom")])
        XCTAssertTrue(viewModel.state.nameSaved)
        XCTAssertEqual(recorder.nameChanges, 1)
    }

    func testTheLiveCodeFollowsARenewalAndARemovalOfAMember() async {
        let viewModel = await readyScreen()
        XCTAssertEqual(viewModel.state.members.count, 2)

        repo.observer.emit(data(members: [testMember("uid-owner", role: .owner)], code: "ABCDEFGHJKMN"))
        await waitUntil { viewModel.state.joinCode == "ABCDEFGHJKMN" }

        XCTAssertEqual(viewModel.state.joinCode, "ABCDEFGHJKMN")
        XCTAssertEqual(viewModel.state.members.count, 1)
    }

    func testWhatIsTypedIsNeverOverwrittenByAChangeThatArrivesWhileTyping() async {
        let viewModel = await readyScreen()
        viewModel.onNameInputChanged("Mon nouveau nom")

        repo.observer.emit(data(members: [testMember("uid-owner", role: .owner)], code: "ABCDEFGHJKMN"))
        await waitUntil { viewModel.state.joinCode == "ABCDEFGHJKMN" }

        XCTAssertEqual(viewModel.state.nameInput, "Mon nouveau nom")
    }

    func testTheSoftCeilingReplacesShareByACalmSentenceAtFifteen() async {
        let fifteen = [testMember("uid-owner", role: .owner)] + (2...15).map { testMember("uid-\($0)") }
        let viewModel = await readyScreen(members: fifteen)
        XCTAssertTrue(viewModel.state.isFull)

        repo.observer.emit(data(members: Array(fifteen.dropFirst())))
        await waitUntil { viewModel.state.members.count == 14 }

        XCTAssertFalse(viewModel.state.isFull)
    }

    func testAccessLostIsNotANetworkErrorAndNothingStaysVisible() async {
        let viewModel = await readyScreen()

        // Une défaillance qui n'est pas une perte d'accès : rien n'est nettoyé, la Miff reste.
        repo.observer.emit(.failed)
        try? await Task.sleep(nanoseconds: 30_000_000)
        XCTAssertEqual(viewModel.state.status, .ready)
        XCTAssertTrue(recorder.lost.isEmpty)

        repo.observer.emit(.accessLost)
        await waitUntil { viewModel.state.status == .accessLost }

        XCTAssertEqual(viewModel.state.status, .accessLost)
        XCTAssertTrue(viewModel.state.members.isEmpty, "ni membre, ni code, ni nom ne reste à l'écran (critère D7)")
        XCTAssertEqual(viewModel.state.joinCode, "")
        XCTAssertEqual(viewModel.state.name, "")
        repo.observer.emit(.accessLost)
        try? await Task.sleep(nanoseconds: 30_000_000)
        XCTAssertEqual(recorder.lost, [testMiffId], "l'annuaire est prévenu une fois : aucune boucle")
    }

    func testLeavingTheScreenWhileTheLossIsCheckedDoesNotLeaveABlankPageAndTheLossIsHeardAgain() async {
        let gate = TestGate()
        let dependencies = MiffScreenDependencies(
            repository: repo,
            network: network,
            currentUid: { "uid-owner" },
            onAccessLost: { _, _ in
                await gate.wait()
                return false
            },
            onNameChanged: {}
        )
        let viewModel = MiffScreenViewModel(miffId: testMiffId, dependencies: dependencies)
        let first = Task {
            await viewModel.observe()
        }
        addTeardownBlock {
            first.cancel()
        }
        repo.observer.emit(data())
        await waitUntil { viewModel.state.status == .ready }
        repo.observer.emit(.accessLost)
        await waitUntil { gate.arrivals == 1 }
        XCTAssertEqual(viewModel.state.status, .accessLost)

        // L'écran passe en arrière-plan (ou pivote) au milieu de la vérification : observe() est annulé.
        first.cancel()
        gate.open()
        await first.value

        // Rien n'a été décidé : un écran qui relit, pas une page blanche.
        XCTAssertEqual(viewModel.state.status, .loading)
        // De retour à l'écran : les écoutes sont rattachées et la perte est entendue de nouveau (elle n'a pas été avalée).
        let second = Task {
            await viewModel.observe()
        }
        addTeardownBlock {
            second.cancel()
        }
        // L'écoute rattachée rejoue le dernier état (« accès perdu ») : la perte est vérifiée une seconde fois.
        await waitUntil { gate.arrivals == 2 }
        XCTAssertEqual(gate.arrivals, 2)
    }

    func testStopSharingAsksFirstThenWritesOnceAndTheSwitchOnlyMovesWithTheServer() async {
        let viewModel = await readyScreen()
        XCTAssertEqual(viewModel.state.sharing, true)

        viewModel.onSharingRequested()
        XCTAssertEqual(viewModel.state.sharingDialog, .stop)
        XCTAssertTrue(repo.sharingChanges.isEmpty, "poser la question n'écrit rien")
        viewModel.onSharingDialogDismissed()
        XCTAssertNil(viewModel.state.sharingDialog)
        XCTAssertTrue(repo.sharingChanges.isEmpty)
        viewModel.onSharingRequested()
        viewModel.onSharingConfirmed()
        viewModel.onSharingConfirmed()
        await waitUntil { !viewModel.state.sharingBusy && viewModel.state.sharingDialog == nil }

        XCTAssertEqual(repo.sharingChanges, [false])
        // L'état montré est celui de la fiche, apporté par l'écoute en direct (pas deviné par l'app).
        XCTAssertEqual(viewModel.state.sharing, true)
        repo.observer.emit(data(members: [testMember("uid-owner", role: .owner, sharing: false)]))
        await waitUntil { viewModel.state.sharing == false }
        XCTAssertEqual(viewModel.state.sharing, false)
    }

    func testResumeSharingUsesTheSameQuestionAndWritesTheResume() async {
        let viewModel = await readyScreen(members: [testMember("uid-owner", role: .owner, sharing: false)])

        viewModel.onSharingRequested()
        XCTAssertEqual(viewModel.state.sharingDialog, .resume)
        viewModel.onSharingConfirmed()
        await waitUntil { !viewModel.state.sharingBusy && viewModel.state.sharingDialog == nil }

        XCTAssertEqual(repo.sharingChanges, [true])
    }

    func testSharingOfflineIsRefusedCalmlyNothingIsQueued() async {
        let viewModel = await readyScreen()
        viewModel.onSharingRequested()
        network.online = false

        viewModel.onSharingConfirmed()

        XCTAssertEqual(viewModel.state.message, .offline)
        XCTAssertTrue(repo.sharingChanges.isEmpty)
    }

    func testSharingFailureKeepsTheDialogWithACalmMessage() async {
        repo.sharingResult = .failed(.unknown)
        let viewModel = await readyScreen()
        viewModel.onSharingRequested()

        viewModel.onSharingConfirmed()
        await waitUntil { !viewModel.state.sharingBusy }

        XCTAssertEqual(viewModel.state.message, .generic)
        XCTAssertEqual(viewModel.state.sharingDialog, .stop)
        XCTAssertFalse(viewModel.state.sharingBusy)
    }

    func testOfflineGreysTheButtonsThatWriteButKeepsReading() async {
        let viewModel = await readyScreen()

        network.online = false
        await waitUntil { viewModel.state.offline }

        // La lecture continue depuis le cache ; ce qui écrit est grisé (critères G8 et H5).
        XCTAssertTrue(viewModel.state.offline)
        XCTAssertEqual(viewModel.state.members.count, 2)
        XCTAssertFalse(viewModel.state.canWrite)
        viewModel.onNameInputChanged("Nouveau nom")
        XCTAssertFalse(viewModel.state.canSaveName)
        viewModel.onSharingRequested()
        XCTAssertNil(viewModel.state.sharingDialog, "aucune question n'est posée quand l'action est impossible")
        network.online = true
        await waitUntil { !viewModel.state.offline }
        XCTAssertFalse(viewModel.state.offline)
    }

    func testARefusalTheServerDoesNotConfirmIsNotARemovalNoMessageAndRetryWorks() async {
        let viewModel = screen(confirmLoss: false)
        repo.observer.emit(data())
        await waitUntil { viewModel.state.status == .ready }

        repo.observer.emit(.accessLost)
        await waitUntil { viewModel.state.status == .unavailable }

        XCTAssertEqual(recorder.lost, [testMiffId], "l'annuaire a été interrogé, et a dit non")
        XCTAssertEqual(viewModel.state.status, .unavailable)
        XCTAssertEqual(viewModel.state.joinCode, "", "rien de la Miff n'est montré dans le doute")
        viewModel.retry()
        XCTAssertEqual(viewModel.state.status, .loading)
        XCTAssertEqual(viewModel.state.restart, 1)
        repo.observer.emit(data())
        await waitUntil { viewModel.state.status == .ready }
        XCTAssertEqual(viewModel.state.status, .ready)
    }

    func testTheScreenStopsListeningWhenItIsNotOnView() async {
        let viewModel = await readyScreen()
        XCTAssertGreaterThan(repo.observer.subscriptions, 0)

        for task in observing {
            task.cancel()
        }
        await waitUntil { repo.observer.subscriptions == 0 }

        XCTAssertEqual(repo.observer.subscriptions, 0, "aucune écoute ne reste (l'annulation arrête observe())")
        XCTAssertEqual(viewModel.state.status, .ready)
    }

    func testNothingIsShownAsReadyBeforeTheFirstPicture() {
        let viewModel = screen()

        XCTAssertEqual(viewModel.state.status, .loading)
        XCTAssertEqual(viewModel.state.members.count, 0)
        XCTAssertNil(viewModel.state.sharing)
    }

    func testTheStateNeverShowsPersonalDataInADescription() async {
        let viewModel = await readyScreen()

        XCTAssertFalse(String(describing: viewModel.state).contains(testCode))
        XCTAssertFalse(String(describing: viewModel.state).contains("Chez nous"))
    }
}
