// Scénarios de test longs par nature (un cas par méthode) : les limites de longueur ne s'appliquent pas ici.
// swiftlint:disable file_length type_body_length
import XCTest
@testable import Miff

/// Le formulaire d'une DEMANDE d'ajout d'un lieu (ticket LIEU-REQ-B, miroir d'Android `PlaceRequestFormViewModelTest`, critères M3 à M5, M7,
/// M8, M11) : le même écran que la création d'un lieu, une seule Miff, « Soumettre la demande ». Ni Firestore ni réseau : des doubles.
@MainActor
final class PlaceRequestFormViewModelTests: XCTestCase {
    private let requests = FakePlaceRequestRepository()
    private let repo = FakePlaceRepository()
    private let search = FakeAddressSearch()
    private let network = FakeNetwork()
    private let notices = PlaceNotices()

    private func requestContext(premium: Bool = false, mine: [PlaceRequest] = []) -> RequestContext {
        RequestContext(miffName: "Famille", premium: premium, mine: mine)
    }

    private func viewModel(
        premium: Bool = false,
        mine: [PlaceRequest] = [],
        load: RequestContextLoad? = nil,
        withRepository: Bool = true
    ) async -> PlaceFormViewModel {
        requests.prepareResult = load ?? .loaded(requestContext(premium: premium, mine: mine))
        let dependencies = PlaceFormDependencies(
            repository: repo,
            search: search,
            network: network,
            currentUid: { testMemberUid },
            notices: notices,
            requests: withRepository ? requests : nil,
            debounceMillis: 20
        )
        let model = PlaceFormViewModel(placeId: nil, miffId: testMiffId, dependencies: dependencies, isRequest: true)
        await waitUntil { !model.state.loading }
        return model
    }

    private func fill(_ model: PlaceFormViewModel, name: String = "Club de judo") async {
        model.onNameChanged(name)
        model.onSuggestionChosen(testSuggestion())
        await waitUntil { model.state.chosen != nil }
    }

    // MARK: - L'ouverture

    func testItOpensInRequestModeOnTheOneMiffTheListBelongsTo() async {
        let model = await viewModel()
        let state = model.state

        XCTAssertEqual(state.mode, .request)
        XCTAssertFalse(state.loading)
        XCTAssertNil(state.loadFailed)
        XCTAssertEqual(state.selectedMiffIds, [testMiffId])
        XCTAssertEqual(state.owned.map { $0.name }, ["Famille"])
        XCTAssertTrue(state.singleMiff, "pas de sélecteur : la Miff est fixe")
        XCTAssertEqual(state.type, .trusted)
        XCTAssertEqual(state.category, .home, "la catégorie est demandée comme pour un lieu (neuvième clé du contrat)")
        XCTAssertFalse(state.canSave)
        XCTAssertFalse(state.requestBlocked)
    }

    func testItDoesNotOpenForTheCreatorOrWithoutTheRequestRepository() async {
        let creator = await viewModel(load: .unavailable)
        let without = await viewModel(withRepository: false)

        XCTAssertEqual(creator.state.loadFailed, .generic)
        XCTAssertEqual(without.state.loadFailed, .generic)
    }

    func testAFailedReadingIsTheCalmMessageWithRetry() async {
        let model = await viewModel(load: .failed(.offline))

        XCTAssertEqual(model.state.loadFailed, .offline)
    }

    func testWithoutANetworkTheFormDoesNotOpenBecauseItsSlotsNeedTheServer() async {
        network.online = false

        let model = await viewModel()

        XCTAssertEqual(model.state.loadFailed, .offline)
    }

    // MARK: - Soumettre

    func testSubmittingSendsTheRequestTellsTheListAndEndsTheForm() async {
        let model = await viewModel()
        await fill(model)

        model.onSave()
        await waitUntil { model.state.end != nil }

        XCTAssertEqual(model.state.end, .saved)
        XCTAssertEqual(requests.submitted.count, 1)
        let draft = requests.submitted[0]
        XCTAssertEqual(draft.name, "Club de judo")
        XCTAssertEqual(draft.type, .trusted)
        XCTAssertEqual(draft.category, .home)
        XCTAssertEqual(draft.radiusM, 100)
        XCTAssertEqual(requests.submitOnline, [true])
        XCTAssertEqual(notices.requestNotice, RequestNotice(queued: false))
        XCTAssertTrue(repo.created.isEmpty, "un membre n'écrit jamais un lieu : seulement une demande")
    }

    func testTheCategoryChosenIsTheOneSent() async {
        let model = await viewModel()
        await fill(model)
        model.onCategorySelected(.school)

        model.onSave()
        await waitUntil { model.state.end != nil }

        XCTAssertEqual(requests.submitted.first?.category, .school)
    }

    func testAQueuedRequestSaysItWillLeaveWhenTheNetworkIsBack() async {
        requests.submitResult = .queued
        let model = await viewModel()
        await fill(model)
        network.online = false

        model.onSave()
        await waitUntil { model.state.end != nil }

        XCTAssertEqual(model.state.end, .saved)
        XCTAssertEqual(requests.submitOnline, [false], "le dépôt le sait : il confie le lot à la file du téléphone")
        XCTAssertEqual(notices.requestNotice, RequestNotice(queued: true))
    }

    func testTheButtonStaysDisabledUntilNameAndAddressAreFilled() async {
        let model = await viewModel()

        model.onSave()
        XCTAssertTrue(requests.submitted.isEmpty)

        model.onNameChanged("Club")
        XCTAssertFalse(model.state.canSave)
        model.onSuggestionChosen(testSuggestion())
        await waitUntil { model.state.chosen != nil }
        XCTAssertTrue(model.state.canSave)
    }

    func testOneTapIsOneSubmission() async {
        let gate = TestGate()
        requests.gate = gate
        let model = await viewModel()
        await fill(model)

        model.onSave()
        model.onSave()
        await waitUntil { gate.arrivals == 1 }
        gate.open()
        await waitUntil { model.state.end != nil }

        XCTAssertEqual(requests.submitted.count, 1, "critère HOR2 : un appui = une demande")
    }

    // MARK: - Les limites

    func testWithThreeRequestsTheFormSaysSoCalmlyAndSendsNothing() async {
        let model = await viewModel(mine: [1, 2, 3].map { testRequest(slot: $0) })
        await fill(model)

        XCTAssertTrue(model.state.requestBlocked)
        XCTAssertEqual(model.state.displayedMessage, .requestLimitMember)
        XCTAssertTrue(model.state.messageIsCalm, "jamais la couleur d'une erreur")
        XCTAssertFalse(model.state.canSave)
        model.onSave()
        XCTAssertTrue(requests.submitted.isEmpty, "critère M5 : aucune écriture tentée")
        model.onNameChanged("Autre nom")
        XCTAssertEqual(model.state.displayedMessage, .requestLimitMember, "taper dans un champ n'efface pas la limite")
    }

    func testAMemberLimitFoundAtSendingBlocksTheFormToo() async {
        requests.submitResult = .memberLimit
        let model = await viewModel()
        await fill(model)

        model.onSave()
        await waitUntil { model.state.requestBlocked }

        XCTAssertEqual(model.state.message, .requestLimitMember)
        XCTAssertNil(model.state.end)
        XCTAssertFalse(model.state.saving)
    }

    func testTheMiffLimitAndTheUnavailableRequestsAreCalmSentences() async {
        requests.submitResult = .miffLimit
        let model = await viewModel()
        await fill(model)

        model.onSave()
        await waitUntil { model.state.message != nil }
        XCTAssertEqual(model.state.message, .requestLimitMiff)
        XCTAssertTrue(model.state.messageIsCalm)

        requests.submitResult = .requestsUnavailable
        model.onSave()
        await waitUntil { model.state.message == .requestsUnavailable }
        XCTAssertTrue(model.state.messageIsCalm)
        XCTAssertNil(model.state.end)
    }

    func testAGoneMiffAndAFailureAreTheUsualMessages() async {
        requests.submitResult = .miffUnavailable
        let model = await viewModel()
        await fill(model)
        model.onSave()
        await waitUntil { model.state.message != nil }
        XCTAssertEqual(model.state.message, .miffGone)

        requests.submitResult = .failed(.offline)
        model.onSave()
        await waitUntil { model.state.message == .offline }
        XCTAssertEqual(model.state.message, .offline)
        XCTAssertTrue(model.state.messageIsCalm)
    }

    // MARK: - La zone de danger (critères M7 et M8)

    func testAMemberWhoIsNotPremiumGetsTheOfferInsteadOfADangerZone() async {
        let model = await viewModel(premium: false)

        model.onTypeSelected(.risk)

        XCTAssertEqual(model.state.type, .trusted, "le choix reste « zone de confiance »")
        XCTAssertEqual(model.state.paywall, .danger)
    }

    func testAPremiumMemberCanChooseADangerZoneAndAskForIt() async {
        let model = await viewModel(premium: true)
        await fill(model)

        model.onTypeSelected(.risk)
        model.onSave()
        await waitUntil { model.state.end != nil }

        XCTAssertNil(model.state.paywall)
        XCTAssertEqual(requests.submitted.first?.type, .risk)
    }

    func testTheOfferIsOpenedWhenTheSendingFindsThePlanEnded() async {
        requests.submitResult = .needsPremium(.danger)
        let model = await viewModel(premium: true)
        await fill(model)

        model.onSave()
        await waitUntil { model.state.paywall != nil }

        XCTAssertEqual(model.state.paywall, .danger)
        XCTAssertNil(model.state.end)
        XCTAssertFalse(model.state.saving)
    }

    // MARK: - Ce qui reste d'un lieu

    func testARequestFormHasNoDeleteAndNeverWritesAPlace() async {
        let model = await viewModel()
        await fill(model)

        model.onDeleteRequested()

        XCTAssertFalse(model.state.deleteDialogOpen)
        XCTAssertTrue(repo.deleted.isEmpty)
    }
}
