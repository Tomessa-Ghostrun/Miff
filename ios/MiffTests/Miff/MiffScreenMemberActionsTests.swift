import XCTest
@testable import Miff

/// L'écran d'une Miff, partie MIFF-4 (critères R, N, Q, S, D) : qui peut demander quoi, la question avant le lot, un lot par
/// appui, des réponses calmes, et un départ que la personne a choisi n'est jamais annoncé comme une perte.
@MainActor
final class MiffScreenMemberActionsTests: MiffScreenTestCase {
    private var three: [MiffMember] {
        [testMember("uid-owner", role: .owner), testMember("uid-2"), testMember("uid-3")]
    }

    private func readyWithThree(uid: String = "uid-owner") async -> MiffScreenViewModel {
        await readyScreen(uid: uid, members: three)
    }

    /// La lecture en direct dit maintenant que la Miff a le créateur et ces membres (et, si on le donne, ce code).
    private func emit(others: [String], code: String = testCode, viewModel: MiffScreenViewModel) async {
        let people = [testMember("uid-owner", role: .owner)] + others.map { testMember($0) }
        repo.observer.emit(data(members: people, code: code))
        await waitUntil { viewModel.state.members.count == people.count && viewModel.state.joinCode == code }
    }

    // MARK: - Retirer

    func testRemoveAsksFirstNothingIsWrittenUntilTheConfirmation() async {
        let viewModel = await readyWithThree()

        viewModel.onRemoveRequested(uid: "uid-2")

        XCTAssertEqual(viewModel.state.action, .remove(uid: "uid-2", firstName: "Prénom-uid-2"))
        XCTAssertTrue(repo.removed.isEmpty, "aucun lot avant la confirmation")
        viewModel.onActionDismissed()
        XCTAssertNil(viewModel.state.action)
        XCTAssertTrue(repo.removed.isEmpty)
    }

    func testRemoveForAPersonWithNoVisibleNameUsesANeutralPhrase() async {
        let blank = MiffMember(
            uid: "uid-2", firstName: " ", lastName: " ", avatarJpeg: nil, role: .member, sharingEnabled: true
        )
        let viewModel = await readyScreen(members: [testMember("uid-owner", role: .owner), blank])

        viewModel.onRemoveRequested(uid: "uid-2")

        XCTAssertEqual(viewModel.state.action, .remove(uid: "uid-2", firstName: L10n.string("ios_member_unnamed")))
        XCTAssertFalse(L10n.string("ios_member_unnamed").isEmpty)
    }

    func testRemoveConfirmWritesOnceAndShowsTheNewCodeOnlyAfterTheServer() async {
        let viewModel = await readyWithThree()
        viewModel.onRemoveRequested(uid: "uid-2")

        viewModel.onActionConfirmed()
        viewModel.onActionConfirmed()

        XCTAssertTrue(viewModel.state.actionBusy, "un appui, une opération")
        XCTAssertEqual(
            viewModel.state.action,
            .remove(uid: "uid-2", firstName: "Prénom-uid-2"),
            "tant que le lot est en route la question reste affichée"
        )
        await waitUntil { viewModel.state.action == .removedDone(firstName: "Prénom-uid-2", newCode: "NEWCODE23456") }
        XCTAssertEqual(repo.removed, ["uid-2"])
        XCTAssertEqual(viewModel.state.action, .removedDone(firstName: "Prénom-uid-2", newCode: "NEWCODE23456"))
        XCTAssertFalse(viewModel.state.actionBusy)
        viewModel.onActionConfirmed() // « Terminer »
        XCTAssertNil(viewModel.state.action)
    }

    func testRemoveIsForTheCreatorOnlyAndNeverOnTheCreatorsOwnLine() async {
        let member = await readyWithThree(uid: "uid-2")
        member.onRemoveRequested(uid: "uid-3")
        XCTAssertNil(member.state.action, "un membre ne retire personne")

        let creator = await readyWithThree()
        creator.onRemoveRequested(uid: "uid-owner")
        XCTAssertNil(creator.state.action, "personne ne se retire soi-même")
        creator.onRemoveRequested(uid: "unknown")
        XCTAssertNil(creator.state.action)
    }

    func testRemoveOfflineSaysSoCalmlyAndSendsNothing() async {
        let viewModel = await readyWithThree()
        viewModel.onRemoveRequested(uid: "uid-2")
        network.online = false
        await waitUntil { viewModel.state.offline }

        XCTAssertFalse(viewModel.state.canWrite, "les boutons qui écrivent sont gris hors connexion")
        viewModel.onActionConfirmed()

        XCTAssertEqual(viewModel.state.message, .offline)
        try? await Task.sleep(nanoseconds: 30_000_000)
        XCTAssertTrue(repo.removed.isEmpty)
        XCTAssertFalse(viewModel.state.actionBusy)
    }

    func testRemoveAFailureKeepsTheQuestionWithACalmMessageAndCanBeTriedAgain() async {
        repo.removeResult = .failed(.refused)
        let viewModel = await readyWithThree()
        viewModel.onRemoveRequested(uid: "uid-2")

        viewModel.onActionConfirmed()
        await waitUntil { viewModel.state.message != nil }

        XCTAssertEqual(viewModel.state.message, .generic)
        XCTAssertEqual(viewModel.state.action, .remove(uid: "uid-2", firstName: "Prénom-uid-2"))
        repo.removeResult = .changed(newCode: "NEWCODE23456")
        viewModel.onActionConfirmed()
        await waitUntil { viewModel.state.action != .remove(uid: "uid-2", firstName: "Prénom-uid-2") }
        XCTAssertEqual(viewModel.state.action, .removedDone(firstName: "Prénom-uid-2", newCode: "NEWCODE23456"))
    }

    func testRemoveWhileTheLotIsOnItsWayTheQuestionCannotBeDismissed() async {
        let viewModel = await readyWithThree()
        viewModel.onRemoveRequested(uid: "uid-2")
        viewModel.onActionConfirmed()

        viewModel.onActionDismissed()

        XCTAssertEqual(viewModel.state.action, .remove(uid: "uid-2", firstName: "Prénom-uid-2"))
        await waitUntil { !viewModel.state.actionBusy }
    }

    // MARK: - Renouveler

    func testRenewAsksFirstThenOneLotAndTheNewCodeComesFromTheLiveView() async {
        let viewModel = await readyWithThree()

        viewModel.onRenewRequested()
        XCTAssertEqual(viewModel.state.action, .renew)
        XCTAssertFalse(repo.calls.contains("renewCode"))
        viewModel.onActionConfirmed()
        await waitUntil { viewModel.state.codeRenewed }

        XCTAssertEqual(repo.calls.filter { $0 == "renewCode" }.count, 1)
        XCTAssertNil(viewModel.state.action)
        // L'écran montre le code que le serveur détient, lu en direct : pas une copie faite ici.
        repo.observer.emit(data(members: three, code: "BCDFGHK23456"))
        await waitUntil { viewModel.state.joinCode == "BCDFGHK23456" }
        XCTAssertEqual(viewModel.state.joinCode, "BCDFGHK23456")
    }

    func testRenewIsForTheCreatorOnly() async {
        let viewModel = await readyWithThree(uid: "uid-2")

        viewModel.onRenewRequested()

        XCTAssertNil(viewModel.state.action)
    }

    func testRenewAFailureIsCalmAndOfflineSendsNothing() async {
        repo.renewResult = .failed(.offline)
        let viewModel = await readyWithThree()
        viewModel.onRenewRequested()

        viewModel.onActionConfirmed()
        await waitUntil { viewModel.state.message != nil }

        XCTAssertEqual(viewModel.state.message, .offline)
        XCTAssertFalse(viewModel.state.codeRenewed)
        network.online = false
        await waitUntil { viewModel.state.offline }
        let before = repo.calls.filter { $0 == "renewCode" }.count
        viewModel.onActionConfirmed()
        try? await Task.sleep(nanoseconds: 30_000_000)
        XCTAssertEqual(repo.calls.filter { $0 == "renewCode" }.count, before, "hors connexion rien n'est envoyé")
    }

    // MARK: - L'invitation après un départ (N3)

    func testInvitationAMemberWhoLeavesInvitesTheCreatorOnceAndLaterClosesIt() async {
        let viewModel = await readyWithThree()
        XCTAssertFalse(viewModel.state.inviteToRenew, "rien à proposer à la première image")

        await emit(others: ["uid-3"], viewModel: viewModel)
        XCTAssertTrue(viewModel.state.inviteToRenew)
        viewModel.onInviteLater()
        XCTAssertFalse(viewModel.state.inviteToRenew)
        // La même liste de nouveau ne la ramène pas.
        await emit(others: ["uid-3"], code: "BCDFGHK23456", viewModel: viewModel)
        XCTAssertFalse(viewModel.state.inviteToRenew)
        // Un autre départ est une autre invitation.
        await emit(others: [], viewModel: viewModel)
        XCTAssertTrue(viewModel.state.inviteToRenew)
    }

    func testInvitationNeverForARemovalTheCreatorJustMade() async {
        let viewModel = await readyWithThree()
        viewModel.onRemoveRequested(uid: "uid-2")
        viewModel.onActionConfirmed()

        // L'écoute entend la disparition AVANT que la réponse revienne.
        await emit(others: ["uid-3"], viewModel: viewModel)

        XCTAssertFalse(viewModel.state.inviteToRenew)
        await waitUntil { !viewModel.state.actionBusy }
    }

    func testInvitationIsNotForAMember() async {
        let member = await readyWithThree(uid: "uid-2")

        await emit(others: ["uid-2"], viewModel: member)

        XCTAssertFalse(member.state.inviteToRenew)
    }

    func testInvitationRenewingClosesIt() async {
        let viewModel = await readyWithThree()
        await emit(others: [], viewModel: viewModel)
        XCTAssertTrue(viewModel.state.inviteToRenew)

        viewModel.onRenewRequested()
        viewModel.onActionConfirmed()
        await waitUntil { viewModel.state.codeRenewed }

        XCTAssertFalse(viewModel.state.inviteToRenew)
    }

    // MARK: - Quitter

    func testLeaveTheCreatorHasNoneAMemberAsksFirst() async {
        let creator = await readyWithThree()
        creator.onLeaveRequested()
        XCTAssertNil(creator.state.action, "le créateur ne peut que supprimer la Miff (décision QM1)")

        let member = await readyWithThree(uid: "uid-2")
        member.onLeaveRequested()
        XCTAssertEqual(member.state.action, .leave)
        XCTAssertFalse(repo.calls.contains("leave"))
    }

    func testLeaveConfirmWritesOnceCleansUpAndClosesTheScreenWithoutALostAccessMessage() async {
        let viewModel = await readyWithThree(uid: "uid-2")
        viewModel.onLeaveRequested()

        viewModel.onActionConfirmed()
        viewModel.onActionConfirmed()
        // L'écoute entend le refus aussitôt, quand le lot arrive : c'est le départ lui-même, pas une perte.
        repo.observer.emit(.accessLost)
        await waitUntil { viewModel.state.departed }

        XCTAssertEqual(repo.calls.filter { $0 == "leave" }.count, 1)
        XCTAssertEqual(recorder.left, [testMiffId])
        XCTAssertTrue(viewModel.state.departed)
        XCTAssertTrue(recorder.lost.isEmpty, "aucun message d'accès perdu pour ce que la personne a choisi")
        XCTAssertNil(viewModel.state.action)
    }

    func testLeaveOfflineOrRefusedKeepsTheScreenAndStaysAMember() async {
        repo.leaveResult = .failed(.offline)
        let viewModel = await readyWithThree(uid: "uid-2")
        viewModel.onLeaveRequested()

        viewModel.onActionConfirmed()
        await waitUntil { viewModel.state.message != nil }

        XCTAssertEqual(viewModel.state.message, .offline)
        XCTAssertFalse(viewModel.state.departed)
        XCTAssertTrue(recorder.left.isEmpty)
        XCTAssertEqual(viewModel.state.action, .leave)
        XCTAssertGreaterThan(viewModel.state.restart, 0, "les écoutes sont rattachées pour voir où on en est")
        // Un vrai retrait qui arrive ensuite est bien vu.
        repo.observer.emit(.accessLost)
        await waitUntil { !self.recorder.lost.isEmpty }
        XCTAssertEqual(recorder.lost, [testMiffId])
    }

    // MARK: - Supprimer

    func testDeleteTheQuestionKnowsTheNumberOfOthersAndOnlyTheCreatorAsks() async {
        let creator = await readyWithThree()
        creator.onDeleteRequested()
        XCTAssertEqual(creator.state.action, .delete(othersCount: 2))

        let member = await readyWithThree(uid: "uid-2")
        member.onDeleteRequested()
        XCTAssertNil(member.state.action)

        let alone = await readyScreen(members: [testMember("uid-owner", role: .owner)])
        alone.onDeleteRequested()
        // Le compte est 0 : l'écran dit alors une autre phrase (jamais « 0 proche »).
        XCTAssertEqual(alone.state.action, .delete(othersCount: 0))
    }

    func testDeleteConfirmStartsTheDeletionOnceAndTheScreenFollowsItsState() async {
        let viewModel = await readyWithThree()
        viewModel.onDeleteRequested()

        viewModel.onActionConfirmed()

        XCTAssertEqual(deletions.started, [testMiffId])
        XCTAssertNil(viewModel.state.action)
        XCTAssertEqual(viewModel.state.deletion, .running(resumed: false))
        XCTAssertFalse(viewModel.state.canWrite, "rien d'autre n'est écrit sur une Miff en cours de suppression")
    }

    func testDeleteTheMembersDisappearOneByOneWithoutInvitationNorLostAccessUntilTheEnd() async {
        let viewModel = await readyWithThree()
        viewModel.onDeleteRequested()
        viewModel.onActionConfirmed()
        await waitUntil { viewModel.state.deletion != nil }

        await emit(others: ["uid-3"], viewModel: viewModel)
        XCTAssertFalse(viewModel.state.inviteToRenew)
        // Le dernier lot : la Miff n'existe plus. C'est le fait du créateur lui-même, pas une perte.
        repo.observer.emit(.accessLost)
        try? await Task.sleep(nanoseconds: 30_000_000)
        XCTAssertTrue(recorder.lost.isEmpty)
        deletions.set(testMiffId, .done)
        await waitUntil { viewModel.state.deletion == .done }

        XCTAssertEqual(viewModel.state.deletion, .done)
        XCTAssertEqual(viewModel.state.status, .ready)
    }

    func testDeleteStuckCanBeTriedAgainOnlyWhenStuck() async {
        let viewModel = await readyWithThree()
        viewModel.onDeletionRetry()
        XCTAssertTrue(deletions.retried.isEmpty)

        deletions.set(testMiffId, .stuck)
        await waitUntil { viewModel.state.deletion == .stuck }
        viewModel.onDeletionRetry()

        XCTAssertEqual(deletions.retried, [testMiffId])
    }

    func testDeleteOfflineIsRefusedCalmlyAndStartsNothing() async {
        let viewModel = await readyWithThree()
        viewModel.onDeleteRequested()
        network.online = false
        await waitUntil { viewModel.state.offline }

        viewModel.onActionConfirmed()

        XCTAssertEqual(viewModel.state.message, .offline)
        XCTAssertTrue(deletions.started.isEmpty)
    }

    func testADeletionAlreadyUnderWayBlocksEveryOtherAction() async {
        deletions.set(testMiffId, .running(resumed: true))
        let viewModel = screen()
        repo.observer.emit(data(members: three))
        await waitUntil { viewModel.state.status == .ready && viewModel.state.deletion != nil }

        viewModel.onRemoveRequested(uid: "uid-2")
        viewModel.onRenewRequested()
        viewModel.onDeleteRequested()
        viewModel.onSharingRequested()

        XCTAssertNil(viewModel.state.action)
        XCTAssertNil(viewModel.state.sharingDialog)
        XCTAssertFalse(viewModel.state.canSaveName)
    }
}
