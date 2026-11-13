// Scénarios de test longs par nature (un cas par méthode) : les limites de longueur ne s'appliquent pas ici.
// swiftlint:disable file_length type_body_length
import XCTest
@testable import Miff

/// Le formulaire d'un lieu (ticket LOC-3, miroir de `PlaceFormViewModelTest` ; §6.1 à §6.4, critères PL1 à PL9, MIF1, ADR1, HOR2).
/// Ni Firestore, ni réseau, ni MapKit : des doubles. La pause avant une recherche est courte ici.
@MainActor
final class PlaceFormViewModelTests: XCTestCase {
    private let repo = FakePlaceRepository()
    private let search = FakeAddressSearch()
    private let network = FakeNetwork()
    private let notices = PlaceNotices()

    private let famille = OwnedMiff(id: testMiffId, name: "Famille", joinedAtMillis: 100)
    private let tuches = OwnedMiff(id: testOtherMiffId, name: "Les tuches", joinedAtMillis: 200)

    private func context(
        premium: Bool = false,
        owned: [OwnedMiff]? = nil,
        lastJoined: String? = nil,
        places: [Place] = []
    ) -> PlaceContext {
        PlaceContext(premium: premium, ownedMiffs: owned ?? [famille], lastJoinedMiffId: lastJoined, places: places)
    }

    private func viewModel(
        placeId: String? = nil,
        context: PlaceContext? = nil,
        fromMiff: String? = nil,
        searchTimeout: TimeInterval = 8
    ) async -> PlaceFormViewModel {
        repo.context = context ?? self.context()
        let dependencies = PlaceFormDependencies(
            repository: repo,
            search: search,
            network: network,
            currentUid: { "uid-1" },
            notices: notices,
            debounceMillis: 20,
            searchTimeoutSeconds: searchTimeout
        )
        let model = PlaceFormViewModel(placeId: placeId, miffId: fromMiff, dependencies: dependencies)
        await waitUntil { !model.state.loading }
        return model
    }

    /// Un formulaire rempli jusqu'au point où « Enregistrer » est actif.
    private func fill(_ model: PlaceFormViewModel, name: String = "Maison") async {
        model.onNameChanged(name)
        model.onSuggestionChosen(testSuggestion())
        await waitUntil { model.state.chosen != nil }
    }

    private func pause(_ millis: UInt64) async {
        try? await Task.sleep(nanoseconds: millis * 1_000_000)
    }

    // MARK: - L'ouverture

    func testOpeningDefaultsAreTheTicketOnes() async {
        let model = await viewModel()
        let state = model.state

        XCTAssertFalse(state.loading)
        XCTAssertEqual(state.radiusM, 100)
        XCTAssertEqual(state.type, .trusted)
        XCTAssertEqual(state.category, .home)
        XCTAssertEqual(state.selectedMiffIds, [testMiffId])
        XCTAssertFalse(state.canSave)
    }

    func testOpeningTheMiffTickedIsTheLastJoinedOneIfTheyCreatedIt() async {
        let two = context(owned: [famille, tuches], lastJoined: testOtherMiffId)
        let model = await viewModel(context: two)
        let other = await viewModel(context: context(owned: [famille, tuches], lastJoined: "someone-elses"))

        XCTAssertEqual(model.state.selectedMiffIds, [testOtherMiffId])
        XCTAssertEqual(other.state.selectedMiffIds, [testMiffId])
    }

    func testOpeningWithASingleMiffHasNoPicker() async {
        let model = await viewModel()

        XCTAssertTrue(model.state.singleMiff)
        model.onMiffSheetOpened()
        XCTAssertFalse(model.state.miffSheetOpen)
    }

    func testOpeningOfflineSaysSoAndRetryReadsAgain() async {
        network.online = false
        let model = await viewModel()

        XCTAssertEqual(model.state.loadFailed, .offline)
        XCTAssertEqual(repo.prepareCalls, 0)
        network.online = true
        model.retry()
        await waitUntil { !model.state.loading }
        XCTAssertNil(model.state.loadFailed)
        XCTAssertEqual(repo.prepareCalls, 1)
    }

    func testOpeningAReadThatFailsIsACalmMessage() async {
        repo.prepareFailure = .refused

        let model = await viewModel()

        XCTAssertEqual(model.state.loadFailed, .generic)
    }

    func testOpeningWithNoMiffTheCreatorMadeIsACalmMessageNotAnEmptyForm() async {
        let model = await viewModel(context: context(owned: []))

        XCTAssertEqual(model.state.loadFailed, .generic)
    }

    func testOpeningWithThreePlacesOnTheFreePlanOpensTheOfferInsteadOfTheForm() async {
        let places = [testPlace(id: "p1", name: "A"), testPlace(id: "p2", name: "B"), testPlace(id: "p3", name: "C")]

        let model = await viewModel(context: context(places: places))

        XCTAssertEqual(model.state.paywall, .limit)
        XCTAssertTrue(model.state.owned.isEmpty)
    }

    // MARK: - Le bouton (PL2)

    func testButtonNeedsOneCharacterOfNameAndAnAddressChosen() async {
        let model = await viewModel()
        model.onNameChanged("M")
        XCTAssertFalse(model.state.canSave)

        model.onSuggestionChosen(testSuggestion())
        await waitUntil { model.state.chosen != nil }
        XCTAssertTrue(model.state.canSave)

        model.onNameChanged("")
        XCTAssertFalse(model.state.canSave)
    }

    func testButtonOneCharacterSaysTwoCharactersMinimumAndWritesNothing() async {
        let model = await viewModel()
        await fill(model, name: "M")

        model.onSave()

        XCTAssertTrue(model.state.nameTooShort)
        XCTAssertTrue(repo.created.isEmpty)
        // Retaper efface la phrase.
        model.onNameChanged("Ma")
        XCTAssertFalse(model.state.nameTooShort)
    }

    func testNameIsCutAtFifty() async {
        let model = await viewModel()

        model.onNameChanged(String(repeating: "a", count: 70))

        XCTAssertEqual(model.state.name.utf16.count, 50)
        XCTAssertTrue(model.state.nameLimitReached)
    }

    // MARK: - L'adresse (ADR1)

    func testAddressNothingBeforeThreeCharactersThenOneSearchAfterThePause() async {
        let model = await viewModel()

        model.onAddressChanged("17")
        await pause(120)
        XCTAssertTrue(search.queries.isEmpty)
        XCTAssertEqual(model.state.suggestions, .idle)

        model.onAddressChanged("17 a")
        XCTAssertEqual(model.state.suggestions, .loading)
        XCTAssertTrue(search.queries.isEmpty, "la pause de 300 ms (ici 20) passe avant toute requête")
        await waitUntil { search.queries == ["17 a"] }
        await waitUntil { model.state.suggestions != .loading }
        XCTAssertEqual(search.queries, ["17 a"])
        XCTAssertEqual(model.state.suggestions, .found([testSuggestion()]))
    }

    func testAddressANewLetterCancelsThePreviousRequest() async {
        let model = await viewModel()
        search.delayNanos = 200_000_000
        search.answer = .noResults

        model.onAddressChanged("17 av")
        await waitUntil { search.queries.count == 1 }
        search.answer = .found([testSuggestion()])
        model.onAddressChanged("17 ave")
        await waitUntil { search.queries.count == 2 }
        await pause(500)

        // Seule la dernière réponse est gardée : celle de la première n'est jamais arrivée à l'écran.
        XCTAssertEqual(search.queries, ["17 av", "17 ave"])
        XCTAssertEqual(model.state.suggestions, .found([testSuggestion()]))
    }

    func testAddressChoosingFixesTheAddressAndStopsTheSearch() async throws {
        let model = await viewModel()
        model.onAddressChanged("17 avenue")
        await waitUntil { model.state.suggestions != .loading }
        guard case .found(let items) = model.state.suggestions else {
            return XCTFail("des suggestions devraient être là")
        }
        let chosen = try XCTUnwrap(items.first)

        model.onSuggestionChosen(chosen)
        XCTAssertTrue(model.state.resolving, "les coordonnées sont demandées au service, pour ce choix seulement")
        await waitUntil { model.state.chosen != nil }

        XCTAssertEqual(model.state.addressText, testResolved.label)
        XCTAssertEqual(model.state.chosen, testResolved)
        XCTAssertFalse(model.state.resolving)
        XCTAssertEqual(model.state.suggestions, .idle)
        XCTAssertEqual(search.resolveCalls.count, 1)
        // Le texte déjà choisi n'est jamais cherché de nouveau.
        model.onAddressChanged(testResolved.label)
        await pause(120)
        XCTAssertEqual(search.queries.count, 1)
    }

    func testAddressTypingAfterTheChoiceErasesTheChoiceAndTheButtonSleepsAgain() async {
        let model = await viewModel()
        await fill(model)
        XCTAssertTrue(model.state.canSave)

        model.onAddressChanged(model.state.addressText + "x")

        XCTAssertNil(model.state.chosen)
        XCTAssertFalse(model.state.canSave)
    }

    func testAddressNoResultAndUnavailableAreCalmStatesThatCanBeRetried() async {
        let model = await viewModel()
        search.answer = .noResults
        model.onAddressChanged("zzzzzz")
        await waitUntil { model.state.suggestions != .loading }
        XCTAssertEqual(model.state.suggestions, .noResults)

        search.answer = .unavailable
        model.onAddressChanged("zzzzzzz")
        await waitUntil { model.state.suggestions != .loading }
        XCTAssertEqual(model.state.suggestions, .unavailable)

        search.answer = .found([testSuggestion()])
        model.onRetrySearch()
        await waitUntil { model.state.suggestions != .loading }
        XCTAssertEqual(model.state.suggestions, .found([testSuggestion()]))
    }

    func testAddressOfflineIsUnavailableWithoutAskingTheService() async {
        let model = await viewModel()
        network.online = false

        model.onAddressChanged("17 avenue")
        await waitUntil { model.state.suggestions != .loading }

        XCTAssertTrue(search.queries.isEmpty)
        XCTAssertEqual(model.state.suggestions, .unavailable)
    }

    func testAddressTheCrossStartsAgain() async {
        let model = await viewModel()
        await fill(model)

        model.onAddressCleared()

        XCTAssertEqual(model.state.addressText, "")
        XCTAssertNil(model.state.chosen)
    }

    func testAddressWhoseCoordinatesCannotBeReadIsACalmStateThatCanBeRetried() async {
        let model = await viewModel()
        search.resolved = nil

        model.onSuggestionChosen(testSuggestion())
        await waitUntil { model.state.suggestions == .unavailable }

        XCTAssertNil(model.state.chosen)
        XCTAssertFalse(model.state.resolving)
        XCTAssertFalse(model.state.canSave, "sans coordonnées, aucun lieu n'est enregistré")
    }

    func testAddressTypingWhileTheCoordinatesAreAskedDropsThatAnswer() async {
        let model = await viewModel()
        search.delayNanos = 150_000_000

        model.onSuggestionChosen(testSuggestion())
        model.onAddressChanged(testResolved.label + " bis")
        await pause(500)

        XCTAssertNil(model.state.chosen, "la réponse du service n'est plus celle de ce qui est tapé")
        XCTAssertFalse(model.state.resolving)
    }

    func testAddressASearchThatNeverAnswersEndsAsUnavailableAfterTheLimitOfTime() async {
        let model = await viewModel(searchTimeout: 0.2)
        search.delayNanos = 5_000_000_000

        model.onAddressChanged("17 avenue")
        await waitUntil { model.state.suggestions == .unavailable }

        XCTAssertEqual(model.state.suggestions, .unavailable)
    }

    // MARK: - Rayon, type, catégorie

    func testRadiusSnapsToTheStepsAndWaitsForTheAddress() async {
        let model = await viewModel()

        model.onRadiusChanged(300)
        XCTAssertEqual(model.state.radiusM, 100, "pas d'adresse choisie : le curseur dort")
        await fill(model)
        model.onRadiusChanged(337)
        XCTAssertEqual(model.state.radiusM, 350)
        model.onRadiusChanged(10)
        XCTAssertEqual(model.state.radiusM, 50)
    }

    func testTypeFreeDangerZoneOpensTheOfferAndTheChoiceStaysTrusted() async {
        let model = await viewModel()

        model.onTypeSelected(.risk)

        XCTAssertEqual(model.state.paywall, .danger)
        XCTAssertEqual(model.state.type, .trusted)
        model.onPaywallHandled()
        XCTAssertNil(model.state.paywall)
    }

    func testTypePremiumCanChooseDangerAndComeBack() async {
        let model = await viewModel(context: context(premium: true))

        model.onTypeSelected(.risk)
        XCTAssertEqual(model.state.type, .risk)
        XCTAssertNil(model.state.paywall)
        model.onTypeSelected(.trusted)
        XCTAssertEqual(model.state.type, .trusted)
    }

    func testCategoryIsAlwaysOneOfTheSevenHomePreselected() async {
        let model = await viewModel()

        XCTAssertEqual(model.state.category, .home)
        model.onCategorySelected(.work)
        XCTAssertEqual(model.state.category, .work)
    }

    // MARK: - Les Miffs (MIF1)

    private func fiveAndOne() async -> PlaceFormViewModel {
        let owned = (1...6).map { index in
            OwnedMiff(id: String(format: "MIFFID%014ld", index), name: "Miff \(index)", joinedAtMillis: index)
        }
        return await viewModel(context: context(premium: true, owned: owned))
    }

    func testMiffsSeveralCanBeTickedFiveAtMostAndNotUntickedByAMistake() async {
        let model = await fiveAndOne()
        let ids = model.state.owned.map { $0.id }

        for id in ids.dropFirst().prefix(4) {
            model.onMiffToggled(id)
        }
        XCTAssertEqual(model.state.selectedMiffIds.count, 5)
        model.onMiffToggled(ids[5]) // une sixième : rien ne se passe
        XCTAssertEqual(model.state.selectedMiffIds.count, 5)
        XCTAssertFalse(model.state.selectedMiffIds.contains(ids[5]))
        model.onMiffToggled(ids[1])
        XCTAssertEqual(model.state.selectedMiffIds.count, 4)
    }

    func testMiffsNoneTickedMeansNoSaving() async {
        let model = await viewModel(context: context(owned: [famille, tuches]))
        await fill(model)
        XCTAssertTrue(model.state.canSave)

        model.onMiffToggled(testMiffId)

        XCTAssertTrue(model.state.selectedMiffIds.isEmpty)
        XCTAssertFalse(model.state.canSave)
    }

    func testMiffsAreKeptInTheOrderOfTheList() async {
        let model = await viewModel(context: context(owned: [famille, tuches], lastJoined: testOtherMiffId))

        model.onMiffToggled(testMiffId)

        XCTAssertEqual(model.state.selectedMiffIds, [testMiffId, testOtherMiffId])
    }

    func testMiffsAnUnknownMiffIsNeverTicked() async {
        let model = await viewModel(context: context(owned: [famille, tuches]))

        model.onMiffToggled(testThirdMiffId)

        XCTAssertFalse(model.state.selectedMiffIds.contains(testThirdMiffId))
    }

    // MARK: - Enregistrer (PL9, HOR2)

    func testSaveWritesWhatWasFilledInAndOnlyThenSaysSo() async throws {
        let model = await viewModel(context: context(owned: [famille, tuches]))
        await fill(model, name: "  Maison de Papa ")
        model.onCategorySelected(.relative)
        model.onRadiusChanged(250)
        model.onMiffToggled(testOtherMiffId)
        let gate = TestGate()
        repo.gate = gate

        model.onSave()
        await waitUntil { repo.created.count == 1 }

        // En route : rien n'est montré comme enregistré, rien ne peut être touché.
        XCTAssertTrue(model.state.saving)
        XCTAssertNil(model.state.end)
        XCTAssertNil(notices.notice)
        gate.open()
        await waitUntil { model.state.end != nil }
        let draft = try XCTUnwrap(repo.created.first)
        XCTAssertEqual(draft.name.trimmingCharacters(in: .whitespaces), "Maison de Papa")
        XCTAssertEqual(draft.miffIds, [testMiffId, testOtherMiffId])
        XCTAssertEqual(draft.radiusM, 250)
        XCTAssertEqual(draft.category, .relative)
        XCTAssertEqual(draft.type, .trusted)
        XCTAssertEqual(draft.address, testResolved.label)
        XCTAssertEqual(model.state.end, .saved)
        XCTAssertEqual(notices.notice, .saved(name: "Maison de Papa"))
    }

    func testSaveTwoTapsOneWrite() async {
        let model = await viewModel()
        await fill(model)
        let gate = TestGate()
        repo.gate = gate

        model.onSave()
        model.onSave()
        await waitUntil { repo.created.count == 1 }
        model.onSave()
        await pause(60)

        XCTAssertEqual(repo.created.count, 1)
        gate.open()
        await waitUntil { model.state.end != nil }
    }

    func testSaveOfflineSaysSoWritesNothingAndQueuesNothing() async {
        let model = await viewModel()
        await fill(model)
        network.online = false

        model.onSave()

        XCTAssertEqual(model.state.message, .offline)
        XCTAssertTrue(repo.created.isEmpty)
        XCTAssertFalse(model.state.saving)
    }

    func testSaveTheServerSaysMiffPlusIsNeededOpensTheOffer() async {
        let model = await viewModel()
        await fill(model)
        repo.createResult = .needsPremium(.limit)

        model.onSave()
        await waitUntil { model.state.paywall != nil }

        XCTAssertEqual(model.state.paywall, .limit)
        XCTAssertNil(model.state.end)
        XCTAssertFalse(model.state.saving)
    }

    func testSaveAMiffIsGoneAndAFailureAreCalmMessagesAndTheButtonStaysUsable() async {
        let model = await viewModel()
        await fill(model)

        repo.createResult = .miffUnavailable
        model.onSave()
        await waitUntil { model.state.message != nil }
        XCTAssertEqual(model.state.message, .miffGone)
        XCTAssertTrue(model.state.canSave)

        repo.createResult = .failed(.offline)
        model.onSave()
        await waitUntil { model.state.message == .offline }
        XCTAssertEqual(model.state.message, .offline)

        repo.createResult = .failed(.refused)
        model.onSave()
        await waitUntil { model.state.message == .generic }
        XCTAssertEqual(model.state.message, .generic)
        XCTAssertNil(model.state.end)
    }

    func testSaveAPlaceGoneEndsTheForm() async {
        let model = await editing()
        model.onNameChanged("Autre nom")
        repo.updateResult = .placeGone

        model.onSave()
        await waitUntil { model.state.end != nil }

        XCTAssertEqual(model.state.end, .placeGone)
    }

    // MARK: - Modifier (PL7)

    private var existing: Place {
        testPlace(
            id: "p1",
            name: "Maison de Papa",
            miffId: testMiffId,
            category: .home,
            address: "26 rue du Volnay, 21000 Dijon"
        )
    }

    private func editing(premium: Bool = false, places: [Place]? = nil) async -> PlaceFormViewModel {
        let held = places ?? [existing]
        return await viewModel(
            placeId: "p1",
            context: context(premium: premium, owned: [famille, tuches], places: held)
        )
    }

    func testEditOpensFilledWithTheButtonAsleepUntilSomethingChanges() async {
        let model = await editing()
        let state = model.state

        XCTAssertEqual(state.mode, .edit)
        XCTAssertEqual(state.name, "Maison de Papa")
        XCTAssertEqual(state.addressText, "26 rue du Volnay, 21000 Dijon")
        XCTAssertNotNil(state.chosen)
        XCTAssertEqual(state.selectedMiffIds, [testMiffId])
        XCTAssertFalse(state.canSave)
        model.onNameChanged("Chez Papa")
        XCTAssertTrue(model.state.canSave)
        model.onNameChanged("Maison de Papa")
        XCTAssertFalse(model.state.canSave)
    }

    func testEditEverythingCanChangeTheMiffsToo() async {
        let model = await editing(premium: true)
        model.onMiffToggled(testOtherMiffId)
        XCTAssertTrue(model.state.canSave)
        model.onTypeSelected(.risk)
        model.onCategorySelected(.school)

        model.onSave()
        await waitUntil { repo.updated.count == 1 }

        let draft = repo.updated.first
        XCTAssertEqual(draft?.type, .risk)
        XCTAssertEqual(draft?.category, .school)
        XCTAssertEqual(draft?.miffIds, [testMiffId, testOtherMiffId])
        XCTAssertTrue(repo.created.isEmpty)
    }

    func testEditALegacyRadiusIsBroughtBackToTheNearestAllowedStep() async {
        let legacy = testPlace(id: "p1", name: "Ancien", radiusM: 120)
        let wide = testPlace(id: "p1", name: "Ancien", radiusM: 2_000)

        let model = await editing(places: [legacy])
        let other = await editing(places: [wide])

        XCTAssertEqual(model.state.radiusM, 100)
        XCTAssertFalse(model.state.canSave, "rien n'a changé pour la personne : le bouton dort")
        XCTAssertEqual(other.state.radiusM, 800)
    }

    func testSaveASlotTakenMeanwhileSaysThePlaceChangedAndTheButtonStaysUsable() async {
        let model = await viewModel()
        await fill(model)
        repo.createResult = .idTaken

        model.onSave()
        await waitUntil { model.state.message != nil }

        XCTAssertEqual(model.state.message, .idTaken)
        XCTAssertTrue(model.state.canSave)
        XCTAssertNil(model.state.end)
    }

    func testEditAPlaceWithoutCategoryMustGetOneBeforeSaving() async {
        let model = await editing(places: [testPlace(id: "p1", name: "Vieille", category: nil)])

        XCTAssertNil(model.state.category)
        model.onNameChanged("Vieille maison")
        XCTAssertFalse(model.state.canSave)
        model.onCategorySelected(.home)
        XCTAssertTrue(model.state.canSave)
    }

    func testEditAPlaceThatDoesNotExistAnyMoreEndsTheForm() async {
        let model = await viewModel(placeId: "gone", context: context(places: [existing]))

        XCTAssertEqual(model.state.end, .placeGone)
    }

    func testEditAFreeAccountCannotMakeItADangerZone() async {
        let model = await editing()

        model.onTypeSelected(.risk)

        XCTAssertEqual(model.state.paywall, .danger)
        XCTAssertEqual(model.state.type, .trusted)
    }

    func testEditTwoDifferentPlacesSharingAnIdentifierTheOneOpenedIsTheOneEdited() async {
        let stadium = Place(
            id: "p1",
            miffId: testOtherMiffId,
            name: "Le stade",
            type: .trusted,
            category: .sport,
            address: "Stade",
            lat: 10,
            lng: 20,
            radiusM: 150,
            createdBy: "u1"
        )
        let both = context(owned: [famille, tuches], places: [existing, stadium])

        let fromTuches = await viewModel(placeId: "p1", context: both, fromMiff: testOtherMiffId)
        let fromFamille = await viewModel(placeId: "p1", context: both, fromMiff: testMiffId)

        XCTAssertEqual(fromTuches.state.name, "Le stade")
        XCTAssertEqual(fromTuches.state.selectedMiffIds, [testOtherMiffId])
        XCTAssertEqual(fromFamille.state.name, "Maison de Papa")
        XCTAssertEqual(fromFamille.state.selectedMiffIds, [testMiffId])
    }

    func testEditAPlaceWithoutAddressAsksForOneAndTheButtonWaitsForIt() async {
        let model = await editing(places: [testPlace(id: "p1", name: "Vieille maison", address: nil)])

        XCTAssertTrue(model.state.addressMissing)
        XCTAssertNil(model.state.chosen)
        model.onNameChanged("Vieille maison de Papa")
        XCTAssertFalse(model.state.canSave)
        model.onSuggestionChosen(testSuggestion())
        await waitUntil { model.state.chosen != nil }
        XCTAssertTrue(model.state.canSave)
    }

    func testEditAPlaceWithoutCategorySaysSoAndShowsNoCategoryByDefault() async {
        let model = await editing(places: [testPlace(id: "p1", name: "Vieille", category: nil)])

        XCTAssertTrue(model.state.categoryMissing)
        XCTAssertNil(model.state.category)
        model.onCategorySelected(.leisure)
        XCTAssertFalse(model.state.categoryMissing)
    }

    // MARK: - Supprimer (PL8)

    func testDeleteAsksFirstThenDeletesEverywhere() async {
        let two = [existing, testPlace(id: "p1", name: "Maison de Papa", miffId: testOtherMiffId)]
        let model = await viewModel(placeId: "p1", context: context(owned: [famille, tuches], places: two))

        model.onDeleteRequested()
        XCTAssertTrue(model.state.deleteDialogOpen)
        XCTAssertTrue(repo.deleted.isEmpty)
        model.onDeleteConfirmed()
        await waitUntil { model.state.end != nil }

        XCTAssertEqual(repo.deleted.count, 1)
        XCTAssertEqual(repo.deleted.first?.placeId, "p1")
        XCTAssertEqual(repo.deleted.first?.miffIds, [testMiffId, testOtherMiffId])
        XCTAssertEqual(model.state.end, .deleted)
    }

    func testDeleteCancelKeepsEverything() async {
        let model = await editing()

        model.onDeleteRequested()
        model.onDeleteDismissed()

        XCTAssertFalse(model.state.deleteDialogOpen)
        XCTAssertTrue(repo.deleted.isEmpty)
    }

    func testDeleteOfflineSaysSoAndKeepsTheDialog() async {
        let model = await editing()
        model.onDeleteRequested()
        network.online = false

        model.onDeleteConfirmed()

        XCTAssertEqual(model.state.deleteMessage, .offline)
        XCTAssertTrue(model.state.deleteDialogOpen)
        XCTAssertTrue(repo.deleted.isEmpty)
    }

    func testDeleteAFailureIsACalmSentenceAndRetryable() async {
        let model = await editing()
        model.onDeleteRequested()
        repo.deleteResult = .failed(.refused)

        model.onDeleteConfirmed()
        await waitUntil { model.state.deleteMessage != nil }

        XCTAssertEqual(model.state.deleteMessage, .generic)
        XCTAssertNil(model.state.end)
        XCTAssertFalse(model.state.deleting)
    }

    func testDeleteTwoTapsOneDeletion() async {
        let model = await editing()
        model.onDeleteRequested()
        let gate = TestGate()
        repo.gate = gate

        model.onDeleteConfirmed()
        model.onDeleteConfirmed()
        await waitUntil { repo.deleted.count == 1 }
        await pause(60)

        XCTAssertEqual(repo.deleted.count, 1)
        gate.open()
        await waitUntil { model.state.end != nil }
    }

    func testDeleteIsNotOfferedWhenCreating() async {
        let model = await viewModel()

        model.onDeleteRequested()

        XCTAssertFalse(model.state.deleteDialogOpen)
    }

    func testDeleteTheDialogAndTheDeletionFollowTheMiffsThePlaceIsInNowNotWhatIsTickedInTheForm() async {
        let two = [existing, testPlace(id: "p1", name: "Maison de Papa", miffId: testOtherMiffId)]
        let model = await viewModel(
            placeId: "p1",
            context: context(owned: [famille, tuches], places: two),
            fromMiff: testMiffId
        )
        XCTAssertEqual(model.state.attachedMiffs.map { $0.id }, [testMiffId, testOtherMiffId])

        // La personne décoche une Miff dans le formulaire, puis demande la suppression : le lieu est encore dans les DEUX Miffs.
        model.onMiffToggled(testOtherMiffId)
        XCTAssertEqual(model.state.selectedMiffIds, [testMiffId])
        XCTAssertEqual(model.state.attachedMiffs.map { $0.id }, [testMiffId, testOtherMiffId])
        model.onDeleteRequested()
        model.onDeleteConfirmed()
        await waitUntil { repo.deleted.count == 1 }

        XCTAssertEqual(repo.deleted.first?.miffIds, [testMiffId, testOtherMiffId])
    }

    // MARK: - Vie privée

    func testStateShowsNothingInItsDescription() async {
        let model = await viewModel()
        await fill(model, name: "Chez Nathan")

        let text = "\(model.state)"

        XCTAssertFalse(text.contains("Nathan"))
        XCTAssertFalse(text.contains("Italie"))
        XCTAssertFalse("\(model.state.snapshot)".contains("Nathan"))
    }
}
// swiftlint:enable type_body_length
