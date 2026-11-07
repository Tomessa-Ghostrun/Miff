import XCTest
@testable import Miff

/// L'écran d'information et la ré-authentification de la suppression du compte (AUTH-5c, miroir de `DeleteAccountViewModelsTest`
/// d'Android) : liste lue auprès du serveur et jamais incomplète, rien n'est écrit avant la preuve, un autre compte Google refusé, la
/// feuille Apple qui sert aussi à retirer l'autorisation, et le mot de passe qui ne reste nulle part.
@MainActor
final class DeleteAccountInfoViewModelTests: XCTestCase {
    private let procedure = ScriptedProcedure()
    private let network = FakeNetwork()

    private func makeModel() -> DeleteAccountInfoViewModel {
        DeleteAccountInfoViewModel(procedure: procedure, currentUid: { testAccountUid }, network: network)
    }

    func testTheListIsReadOnTheServerAndTheScreenKeepsContinueGreyUntilItIsComplete() async {
        procedure.inventoryAnswer = .loaded(AccountMiffsSummary(joined: 1, owned: [testOwned()]))
        let model = makeModel()
        XCTAssertFalse(model.state.canContinue, "pendant la lecture")

        await waitUntil { model.state.inventory == .loaded }

        XCTAssertTrue(model.state.canContinue)
        XCTAssertTrue(model.state.ownsMiffs)
        XCTAssertEqual(procedure.inventoryCalls, 1)
    }

    func testOfflineTheScreenOpensWithContinueGreyAndNothingIsRead() async {
        network.online = false
        let model = makeModel()

        await waitUntil { model.state.inventory == .offline }

        XCTAssertEqual(model.state.inventory, .offline)
        XCTAssertFalse(model.state.canContinue)
        XCTAssertEqual(procedure.inventoryCalls, 0)
        model.onContinue()
        XCTAssertFalse(model.state.confirming)
    }

    func testTheListShowsByItselfWhenTheNetworkIsBack() async {
        network.online = false
        let model = makeModel()
        await waitUntil { model.state.inventory == .offline }

        network.online = true
        await waitUntil { model.state.inventory == .loaded }

        XCTAssertEqual(model.state.inventory, .loaded)
    }

    func testAFailedReadKeepsContinueGreyAndRetryReadsAgain() async {
        procedure.inventoryAnswer = .failed
        let model = makeModel()
        await waitUntil { model.state.inventory == .readFailed }
        XCTAssertFalse(model.state.canContinue)

        procedure.inventoryAnswer = .loaded(.none)
        model.load()
        await waitUntil { model.state.inventory == .loaded }

        XCTAssertTrue(model.state.canContinue)
    }

    func testKeepingTheAccountClosesTheConfirmationAndChangesNothing() async {
        let model = makeModel()
        await waitUntil { model.state.inventory == .loaded }
        model.onContinue()
        XCTAssertTrue(model.state.confirming)

        model.onKeep()

        XCTAssertFalse(model.state.confirming)
        XCTAssertEqual(procedure.runCalls, 0, "rien n'a été touché")
    }

    func testConfirmingGoesOnToTheReauthenticationAndWritesNothing() async {
        let model = makeModel()
        await waitUntil { model.state.inventory == .loaded }
        model.onContinue()

        let goesOn = model.onConfirm()

        XCTAssertTrue(goesOn)
        XCTAssertFalse(model.state.confirming)
        XCTAssertEqual(procedure.runCalls, 0, "aucune écriture avant la ré-authentification")
    }

    func testConfirmingWithoutNetworkStaysOnTheScreenWithACalmNote() async {
        let model = makeModel()
        await waitUntil { model.state.inventory == .loaded }
        model.onContinue()
        network.online = false

        let goesOn = model.onConfirm()
        model.dismissConfirmation()

        XCTAssertFalse(goesOn)
        XCTAssertTrue(model.state.confirmOffline)
        XCTAssertTrue(model.state.canContinue, "« Continuer » reste actif")
    }

    func testTheWarnPlanNamesAtMostThreeMiffsAndOnlyThoseWithOthers() {
        let miffs = AccountMiffsSummary(
            joined: 0,
            owned: [
                testOwned(id: "a", name: "A", others: 2),
                testOwned(id: "b", name: "B", others: 0),
                testOwned(id: "c", name: "", others: 1),
                testOwned(id: "d", name: "D", others: 3)
            ]
        )

        let plan = miffs.warnPlan

        XCTAssertEqual(plan.named, ["A"])
        XCTAssertTrue(plan.generic, "une Miff sans nom et une quatrième sont couvertes par UN message sans nom")
        XCTAssertFalse(plan.single)
    }

    func testOneMiffWithOthersIsTheSimpleButtonAndNobodyElseMeansNoButton() {
        let one = AccountMiffsSummary(joined: 0, owned: [testOwned(name: "Chez nous", others: 1)]).warnPlan
        let alone = AccountMiffsSummary(joined: 0, owned: [testOwned(others: 0)]).warnPlan

        XCTAssertTrue(one.single)
        XCTAssertTrue(alone.isEmpty, "rien à dire à personne (SC7)")
    }
}

@MainActor
final class DeleteReauthViewModelTests: XCTestCase {
    private let timeline = Timeline()
    private lazy var gateway = FakeAuthGateway(timeline: timeline)
    private let network = FakeNetwork()
    private let social = FakeSocialSource()
    private let revoker = FakeAppleRevoker()
    private let verified = TestBox<[AppleRevocation]>([])

    private func makeModel(last: Bool = false) -> DeleteReauthViewModel {
        let verified = self.verified
        let dependencies = DeleteReauthDependencies(
            gateway: gateway,
            email: { "alex@exemple.fr" },
            network: network,
            social: social,
            revoker: revoker
        )
        return DeleteReauthViewModel(dependencies: dependencies, last: last, onVerified: { verified.value.append($0) })
    }

    // MARK: - Mot de passe

    func testAWrongPasswordKeepsThePersonOnTheScreenWithACalmSentence() async {
        gateway.passwordResults = [.invalidCredentials]
        let model = makeModel()

        model.submitPassword("mauvais-mot-de-passe")
        await waitUntil { !model.state.busy }

        XCTAssertEqual(model.state.message, .reauthPasswordWrong)
        XCTAssertTrue(verified.value.isEmpty)
        XCTAssertEqual(model.state.message?.text, L10n.string("settings_delete_reauth_password_wrong"))
    }

    func testTheRightPasswordStartsTheDeletionAndStaysBusyOnOneOperation() async {
        let model = makeModel()

        model.submitPassword("bon-mot-de-passe")
        model.submitPassword("bon-mot-de-passe")
        await waitUntil { !self.verified.value.isEmpty }

        XCTAssertEqual(verified.value, [.notNeeded])
        XCTAssertEqual(gateway.passwordCalls, 1, "un geste = une opération")
        XCTAssertTrue(model.state.busy)
    }

    func testNoNetworkSaysSoWithoutSendingTheAttempt() {
        network.online = false
        let model = makeModel()

        model.submitPassword("bon-mot-de-passe")

        XCTAssertEqual(model.state.message, .network)
        XCTAssertEqual(gateway.passwordCalls, 0)
    }

    func testAnEmptyPasswordSendsNothing() {
        let model = makeModel()

        model.submitPassword("")

        XCTAssertEqual(gateway.passwordCalls, 0)
        XCTAssertFalse(model.state.busy)
    }

    func testThePasswordIsNeverPartOfTheState() {
        let model = makeModel()

        model.submitPassword("secret-tres-particulier")

        XCTAssertFalse(String(describing: model.state).contains("secret-tres-particulier"))
    }

    func testTooManyAttemptsUsesTheSameTextAsTheSignIn() async {
        gateway.passwordResults = [.tooManyAttempts]
        let model = makeModel()

        model.submitPassword("x-mot-de-passe")
        await waitUntil { !model.state.busy }

        XCTAssertEqual(model.state.message, .tooManyAttempts)
    }

    // MARK: - Google

    func testAnotherGoogleAccountIsRefusedWithoutDeletingAnything() async {
        gateway.providers = [.google]
        gateway.deletionReauthResults = [.wrongAccount]
        let model = makeModel()

        model.submitGoogle()
        await waitUntil { !model.state.busy }

        XCTAssertEqual(model.state.message, .reauthWrongAccount)
        XCTAssertTrue(verified.value.isEmpty)
    }

    func testClosingTheGoogleWindowSaysNothing() async {
        gateway.providers = [.google]
        social.google = .cancelled
        let model = makeModel()

        model.submitGoogle()
        await waitUntil { !model.state.busy }

        XCTAssertNil(model.state.message)
        XCTAssertTrue(verified.value.isEmpty)
        XCTAssertEqual(gateway.deletionReauthCalls, 0)
    }

    func testTheGoogleAccountOfTheSessionStartsTheDeletion() async {
        gateway.providers = [.google]
        let model = makeModel()

        model.submitGoogle()
        await waitUntil { !self.verified.value.isEmpty }

        XCTAssertEqual(verified.value, [.notNeeded])
    }

    // MARK: - Apple : ré-authentification ET révocation

    func testAnAppleAccountIsAskedThroughTheAppleSheetOnlyEvenWithAPasswordToo() {
        gateway.providers = [.apple, .password, .google]
        let model = makeModel()

        XCTAssertTrue(model.state.offersApple)
        // La révocation Apple est obligatoire : un seul geste, la feuille Apple.
        XCTAssertFalse(model.state.offersPassword)
        XCTAssertFalse(model.state.offersGoogle)
    }

    func testTheAppleSheetReauthenticatesThenRevokesWithItsAuthorizationCode() async {
        gateway.providers = [.apple]
        social.apple = .credential(.apple(idToken: "id", rawNonce: "nonce", name: nil, authorizationCode: "code-1"))
        let model = makeModel()

        model.submitApple()
        await waitUntil { !self.verified.value.isEmpty }

        XCTAssertEqual(revoker.codes, ["code-1"])
        XCTAssertEqual(verified.value, [.revoked])
        XCTAssertEqual(
            timeline.entries.filter { $0 == "reauthSocial" }.count, 1,
            "la ré-authentification passe AVANT la révocation"
        )
    }

    func testAFailedRevocationDoesNotStopTheDeletionButIsRemembered() async {
        gateway.providers = [.apple]
        social.apple = .credential(.apple(idToken: "id", rawNonce: "nonce", name: nil, authorizationCode: "code-1"))
        revoker.result = false
        let model = makeModel()

        model.submitApple()
        await waitUntil { !self.verified.value.isEmpty }

        XCTAssertEqual(verified.value, [.failed])
    }

    func testAnAppleSheetWithoutAnAuthorizationCodeCountsAsAFailedRevocation() async {
        gateway.providers = [.apple]
        social.apple = .credential(.apple(idToken: "id", rawNonce: "nonce", name: nil, authorizationCode: nil))
        let model = makeModel()

        model.submitApple()
        await waitUntil { !self.verified.value.isEmpty }

        XCTAssertTrue(revoker.codes.isEmpty)
        XCTAssertEqual(verified.value, [.failed])
    }

    func testNothingIsRevokedWhenTheReauthenticationFails() async {
        gateway.providers = [.apple]
        social.apple = .credential(.apple(idToken: "id", rawNonce: "nonce", name: nil, authorizationCode: "code-1"))
        gateway.deletionReauthResults = [.wrongAccount]
        let model = makeModel()

        model.submitApple()
        await waitUntil { !model.state.busy }

        XCTAssertTrue(revoker.codes.isEmpty)
        XCTAssertTrue(verified.value.isEmpty)
        XCTAssertEqual(model.state.message, .reauthWrongAccount)
    }

    func testClosingTheAppleSheetSaysNothing() async {
        gateway.providers = [.apple]
        social.apple = .cancelled
        let model = makeModel()

        model.submitApple()
        await waitUntil { !model.state.busy }

        XCTAssertNil(model.state.message)
        XCTAssertEqual(gateway.deletionReauthCalls, 0)
    }

    // MARK: - Méthodes proposées

    func testOnlyTheMethodsLinkedToTheAccountAreOffered() {
        gateway.providers = [.google]
        let google = makeModel()
        gateway.providers = [.password]
        let password = makeModel()
        gateway.providers = []
        let none = makeModel()

        XCTAssertTrue(google.state.offersGoogle)
        XCTAssertFalse(google.state.offersPassword)
        XCTAssertTrue(password.state.offersPassword)
        XCTAssertTrue(none.state.noMethod)
    }

    func testTheLastCheckIsTheSameScreenAskedAtTheVeryEnd() {
        let model = makeModel(last: true)

        XCTAssertTrue(model.state.last)
    }

    func testRefreshReadsTheMethodsAgainAndForgetsAnOldMessage() async {
        gateway.providers = [.password]
        network.online = false
        let model = makeModel()
        model.submitPassword("x")
        XCTAssertEqual(model.state.message, .network)
        gateway.providers = [.google]

        model.refresh()

        XCTAssertTrue(model.state.offersGoogle)
        XCTAssertNil(model.state.message)
        XCTAssertFalse(model.state.busy)
    }
}
