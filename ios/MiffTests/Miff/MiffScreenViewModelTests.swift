import XCTest
@testable import Miff

/// L'écran d'une Miff, côté lecture (ticket Miff, critères F, G, H, D) : le créateur et le membre, le nom, le code en direct,
/// la perte d'accès qui n'est jamais une erreur réseau. Aucun Firestore : un dépôt double qui pilote l'écoute.
@MainActor
final class MiffScreenViewModelTests: MiffScreenTestCase {
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
        await waitUntil { self.repo.observer.subscriptions == 0 }

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
