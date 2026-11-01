import XCTest
@testable import Miff

/// « Partager mon statut » sur l'écran d'une Miff (ticket Miff, critères K5, K6, H1, H5) : la question vient d'abord, l'écriture
/// est confirmée par le serveur, et sans réseau rien n'est posé ni mis en attente.
@MainActor
final class MiffScreenSharingTests: MiffScreenTestCase {
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

    func testClosingTheQuestionClearsItsMessage() async {
        repo.sharingResult = .failed(.unknown)
        let viewModel = await readyScreen()
        viewModel.onSharingRequested()
        viewModel.onSharingConfirmed()
        await waitUntil { !viewModel.state.sharingBusy }
        XCTAssertEqual(viewModel.state.message, .generic)

        viewModel.onSharingDialogDismissed()

        XCTAssertNil(viewModel.state.sharingDialog)
        XCTAssertNil(viewModel.state.message)
    }
}
