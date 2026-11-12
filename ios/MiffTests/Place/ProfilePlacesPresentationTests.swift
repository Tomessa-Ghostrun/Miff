import XCTest
@testable import Miff

/// Ce que l'onglet Profil dessine sous le carrousel, selon le rôle de la personne dans la Miff regardée (décision du PM, 2026-11-11) :
/// un membre LIT seulement ; le créateur garde ses boutons. Ce qui est permis reste décidé par les règles de Firestore.
final class ProfilePlacesPresentationTests: XCTestCase {
    private let me = "uid-me"

    private func miffs() -> [MiffSummary] {
        [
            MiffSummary(id: "A", name: "Famille", ownerUid: "uid-me"),
            MiffSummary(id: "B", name: "Les copains", ownerUid: "uid-other"),
            MiffSummary(id: "C", name: "Collègues", ownerUid: nil)
        ]
    }

    private func places(_ ids: [String], type: PlaceType = .trusted) -> [Place] {
        ids.map { testPlace(id: $0, name: "Lieu \($0)", type: type) }
    }

    private func make(
        _ selected: String?,
        _ view: MiffPlacesView,
        uid: String? = "uid-me"
    ) -> ProfilePlacesPresentation? {
        let state = ProfilePlacesState(selectedMiffId: selected, view: view)
        return ProfilePlacesPresentation.make(state: state, miffs: miffs(), myUid: uid)
    }

    // MARK: - Quand rien n'est dessiné

    func testNothingIsDrawnWhileThePlacesAreNotRead() {
        XCTAssertNil(make("A", .loading))
    }

    func testNothingIsDrawnWithoutASelectedMiffOrForAMiffTheCarouselDoesNotHave() {
        XCTAssertNil(make(nil, .ready(places(["p1"]))))
        XCTAssertNil(make("Z", .ready(places(["p1"]))))
    }

    // MARK: - Le rôle

    func testTheCreatorOfTheShownMiffIsACreator() {
        XCTAssertEqual(make("A", .ready([]))?.isCreator, true)
    }

    func testAnotherMemberAndAnUnknownCreatorAreMembers() {
        XCTAssertEqual(make("B", .ready([]))?.isCreator, false)
        XCTAssertEqual(make("C", .ready([]))?.isCreator, false, "créateur inconnu : jamais de bouton du créateur")
        XCTAssertEqual(make("A", .ready([]), uid: nil)?.isCreator, false, "personne inconnue")
    }

    func testTheTitleNamesTheShownMiffEvenWithASingleMiff() {
        let presentation = make("B", .ready(places(["p1"])))

        XCTAssertEqual(presentation?.miffName, "Les copains")
        XCTAssertEqual(presentation?.miffId, "B")
    }

    // MARK: - Les états

    func testAnUnreadableListIsUnavailableForEveryone() {
        XCTAssertEqual(make("A", .unavailable)?.body, .unavailable)
        XCTAssertEqual(make("B", .unavailable)?.body, .unavailable)
    }

    func testAnEmptyListIsATrueEmptyListOnceRead() {
        XCTAssertEqual(make("B", .ready([]))?.body, .list([]))
    }

    // MARK: - Un membre ne voit jamais l'édition

    func testAMemberNeverGetsAnAddButtonTheCounterTheMiffPlusOfferOrTheSavedMessage() {
        let presentation = make("B", .ready(places(["p1", "p2", "p3"])))
        let three = places(["p1", "p2", "p3"])
        let ui = presentation?.listUi(places: three, premium: false, notice: .saved(name: "Maison"))

        XCTAssertEqual(ui?.isCreator, false)
        XCTAssertEqual(ui?.showsFreeCounter, false, "pas de compteur « n lieux sur 3 »")
        XCTAssertEqual(ui?.atFreeLimit, false, "pas de pastille Miff+")
        XCTAssertNil(ui?.premium, "la formule ne concerne que le créateur")
        XCTAssertNil(ui?.notice, "seul le créateur enregistre un lieu")
    }

    func testAMemberStillHearsThatAPlaceNoLongerExists() {
        let presentation = make("B", .ready(places(["p1"])))

        XCTAssertEqual(presentation?.listUi(places: [], premium: nil, notice: .gone).notice, .gone)
    }

    // MARK: - Le créateur est inchangé

    func testTheCreatorKeepsTheCounterTheLimitAndTheSavedMessage() {
        let presentation = make("A", .ready(places(["p1", "p2", "p3"])))
        let three = places(["p1", "p2", "p3"])
        let ui = presentation?.listUi(places: three, premium: false, notice: .saved(name: "Maison"))

        XCTAssertEqual(ui?.isCreator, true)
        XCTAssertEqual(ui?.showsFreeCounter, true)
        XCTAssertEqual(ui?.atFreeLimit, true)
        XCTAssertEqual(ui?.notice, .saved(name: "Maison"))
    }

    func testTheCreatorOfAnUnknownPlanIsToldNothingAboutTheFreePlan() {
        let presentation = make("A", .ready(places(["p1", "p2", "p3"])))
        let ui = presentation?.listUi(places: places(["p1", "p2", "p3"]), premium: nil, notice: nil)

        XCTAssertEqual(ui?.showsFreeCounter, false)
        XCTAssertEqual(ui?.atFreeLimit, false)
    }

    // MARK: - VoiceOver : le type et l'adresse sont lus

    func testARowIsReadAsOneBlockWithNameCategoryAndAddress() {
        let place = testPlace(id: "p1", name: "Maison de Papa", category: .home, address: "26 rue du Volnay")

        let spoken = PlaceRowSpeech.spoken(for: place)

        XCTAssertTrue(spoken.contains("Maison de Papa"))
        XCTAssertTrue(spoken.contains("26 rue du Volnay"))
        XCTAssertTrue(spoken.contains(PlaceCategory.home.label))
        XCTAssertFalse(spoken.contains(PlaceType.risk.label), "une zone de confiance ne porte aucune étiquette")
    }

    func testADangerZoneIsReadWithItsTypeAtTheEnd() {
        let place = testPlace(id: "p1", name: "Chantier", type: .risk, category: .other, address: "12 rue Haute")

        let spoken = PlaceRowSpeech.spoken(for: place)

        XCTAssertTrue(spoken.hasSuffix(PlaceType.risk.label), spoken)
        XCTAssertTrue(spoken.contains("12 rue Haute"))
    }

    func testARowWithoutCategoryOrAddressIsStillReadWithItsName() {
        let place = testPlace(id: "p1", name: "Chez Léa", category: nil, address: nil)

        let expected = L10n.format("place_list_row_nocategory_noaddress_a11y", "Chez Léa")
        XCTAssertEqual(PlaceRowSpeech.spoken(for: place), expected)
    }

    // MARK: - « Ajouter un lieu » coche la Miff de la liste

    func testTheFormOpenedFromAListTicksThatMiffFirstWhenThePersonCreatedIt() {
        let owned = [OwnedMiff(id: "A", name: "A", joinedAtMillis: 1), OwnedMiff(id: "B", name: "B", joinedAtMillis: 2)]

        XCTAssertEqual(DefaultMiffChoice.of(owned: owned, lastJoinedId: "B", openedFromId: "A"), "A")
    }

    func testAMiffThePersonDidNotCreateOrNoMiffFallsBackOnTheUsualChoice() {
        let owned = [OwnedMiff(id: "A", name: "A", joinedAtMillis: 1), OwnedMiff(id: "B", name: "B", joinedAtMillis: 2)]

        XCTAssertEqual(DefaultMiffChoice.of(owned: owned, lastJoinedId: "B", openedFromId: "someone-elses"), "B")
        XCTAssertEqual(DefaultMiffChoice.of(owned: owned, lastJoinedId: "B", openedFromId: nil), "B")
        XCTAssertEqual(DefaultMiffChoice.of(owned: owned, lastJoinedId: nil, openedFromId: "zzz"), "A")
    }
}
