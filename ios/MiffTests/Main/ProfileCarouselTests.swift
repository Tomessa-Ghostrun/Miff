import XCTest
@testable import Miff

/// Le carrousel de Miffs de l'onglet Profil (retour du PM, 2026-11-10) : les règles de la rangée de miniatures, de la pagination, du
/// bouton « Créer une Miff » (l'offre pour un compte gratuit qui a déjà une Miff) et ce qui est gardé des membres.
final class ProfileCarouselRulesTests: XCTestCase {
    // MARK: - La rangée de miniatures

    func testFewMembersAreAllShownWithoutAPlusChip() {
        XCTAssertEqual(MemberStack.layout(total: 0, maxVisible: 5), MemberStack.Layout(visible: 0, extra: 0))
        XCTAssertEqual(MemberStack.layout(total: 1, maxVisible: 5), MemberStack.Layout(visible: 1, extra: 0))
        XCTAssertEqual(MemberStack.layout(total: 4, maxVisible: 5), MemberStack.Layout(visible: 4, extra: 0))
        XCTAssertEqual(MemberStack.layout(total: 5, maxVisible: 5), MemberStack.Layout(visible: 5, extra: 0))
    }

    func testMoreMembersThanPlacesShowOneLessAndAPlusChipThatCountsTheRest() {
        // La pastille « +N » prend la dernière place : 4 photos + « +4 » = 8 membres.
        XCTAssertEqual(MemberStack.layout(total: 8, maxVisible: 5), MemberStack.Layout(visible: 4, extra: 4))
        XCTAssertEqual(MemberStack.layout(total: 6, maxVisible: 5), MemberStack.Layout(visible: 4, extra: 2))
        XCTAssertEqual(MemberStack.layout(total: 15, maxVisible: 3), MemberStack.Layout(visible: 2, extra: 13))
    }

    func testTheNumberOfPlacesIsNeverBelowOneAndTheTotalNeverNegative() {
        XCTAssertEqual(MemberStack.layout(total: -3, maxVisible: 5), MemberStack.Layout(visible: 0, extra: 0))
        XCTAssertEqual(MemberStack.layout(total: 3, maxVisible: 0), MemberStack.Layout(visible: 0, extra: 3))
    }

    func testAtLeastThreeAndAtMostFivePlacesAreShown() {
        XCTAssertEqual(MemberStack.maxVisible, 5)
        XCTAssertEqual(MemberStack.maxVisibleLargeText, 3)
    }

    // MARK: - La pagination

    /// Où s'arrête un geste (3 pages) : la page de départ, le glissement, l'élan prévu, la largeur d'une page.
    private func settle(_ current: Int, _ translation: CGFloat, _ predictedEnd: CGFloat, width: CGFloat) -> Int {
        CarouselPaging.settle(
            current: current, translation: translation, predictedEnd: predictedEnd, pageWidth: width, count: 3
        )
    }

    func testThereIsOnePagePerMiffPlusTheCreateCard() {
        XCTAssertEqual(CarouselPaging.pageCount(miffs: 0), 1)
        XCTAssertEqual(CarouselPaging.pageCount(miffs: 1), 2)
        XCTAssertEqual(CarouselPaging.pageCount(miffs: 3), 4)
    }

    func testTheCarouselOpensOnTheMiffTheFeedShowsElseOnTheFirst() {
        XCTAssertEqual(CarouselPaging.startPage(miffIds: ["a", "b", "c"], activeId: "b"), 1)
        XCTAssertEqual(CarouselPaging.startPage(miffIds: ["a", "b", "c"], activeId: "c"), 2)
        XCTAssertEqual(CarouselPaging.startPage(miffIds: ["a", "b", "c"], activeId: nil), 0)
        XCTAssertEqual(CarouselPaging.startPage(miffIds: ["a", "b", "c"], activeId: "zzz"), 0)
        XCTAssertEqual(CarouselPaging.startPage(miffIds: [], activeId: "a"), 0)
    }

    func testTheContentStartsOnTheActiveMiffUnlessAPageIsImposed() {
        let miffs = [MiffSummary(id: "a", name: "A"), MiffSummary(id: "b", name: "B")]

        XCTAssertEqual(ProfileCarouselContent(miffs: miffs, activeId: "b").startPage, 1)
        XCTAssertEqual(ProfileCarouselContent(miffs: miffs, activeId: "b", initialPage: 2).startPage, 2)
    }

    func testThePageIsKeptInsideTheCarousel() {
        XCTAssertEqual(CarouselPaging.clamp(-1, count: 3), 0)
        XCTAssertEqual(CarouselPaging.clamp(1, count: 3), 1)
        XCTAssertEqual(CarouselPaging.clamp(7, count: 3), 2)
        XCTAssertEqual(CarouselPaging.clamp(0, count: 0), 0)
    }

    func testALongSwipeToTheLeftShowsTheNextPageAndToTheRightThePreviousOne() {
        XCTAssertEqual(settle(1, -150, -160, width: 300), 2)
        XCTAssertEqual(settle(1, 150, 160, width: 300), 0)
    }

    func testAShortSlowSwipeBringsTheCardBack() {
        XCTAssertEqual(settle(1, -40, -60, width: 300), 1)
    }

    func testAShortButFastFlickTurnsThePage() {
        XCTAssertEqual(settle(0, -40, -200, width: 300), 1)
    }

    func testTheCarouselNeverGoesPastItsEnds() {
        XCTAssertEqual(settle(0, 200, 260, width: 300), 0)
        XCTAssertEqual(settle(2, -200, -260, width: 300), 2)
    }

    func testWithoutAWidthNothingMoves() {
        XCTAssertEqual(settle(1, -500, -900, width: 0), 1)
    }

    func testTheNeighbourIsThePageThatIsAboutToAppear() {
        XCTAssertEqual(CarouselPaging.neighbour(current: 1, translation: -20, count: 3), 2)
        XCTAssertEqual(CarouselPaging.neighbour(current: 1, translation: 20, count: 3), 0)
        XCTAssertNil(CarouselPaging.neighbour(current: 2, translation: -20, count: 3))
        XCTAssertNil(CarouselPaging.neighbour(current: 0, translation: 20, count: 3))
    }

    // MARK: - « Créer une Miff »

    private func ready(
        _ count: Int,
        premium: Bool,
        premiumKnown: Bool = true
    ) -> DirectoryState {
        let miffs = (0..<count).map { MiffSummary(id: "m\($0)", name: "Miff \($0)") }
        var value = DirectoryReady(miffs: miffs, activeId: miffs.first?.id, premium: premium, fromCache: false)
        value.premiumKnown = premiumKnown
        return .ready(value)
    }

    func testAFreeAccountThatAlreadyHasAMiffGetsTheOffer() {
        XCTAssertEqual(ProfileCarouselRules.createOutcome(for: ready(1, premium: false)), .openPaywall)
    }

    func testPremiumOrNoMiffOrAnUnknownFormulaGetsTheNormalFlow() {
        XCTAssertEqual(ProfileCarouselRules.createOutcome(for: ready(1, premium: true)), .openFlow)
        XCTAssertEqual(ProfileCarouselRules.createOutcome(for: ready(3, premium: true)), .openFlow)
        XCTAssertEqual(ProfileCarouselRules.createOutcome(for: ready(0, premium: false)), .openFlow)
        // Un doute n'est jamais « gratuit » : c'est le serveur qui tranche.
        let unknown = ready(1, premium: false, premiumKnown: false)
        XCTAssertEqual(ProfileCarouselRules.createOutcome(for: unknown), .openFlow)
        XCTAssertEqual(ProfileCarouselRules.createOutcome(for: .loading), .openFlow)
        XCTAssertEqual(ProfileCarouselRules.createOutcome(for: .unavailable), .openFlow)
    }

    func testTheProfileOfferIsTheSameOneAsTheFeedSelectors() {
        for directory in [ready(1, premium: false), ready(2, premium: true), ready(0, premium: false)] {
            guard case .ready(let value) = directory else {
                return XCTFail("annuaire prêt attendu")
            }
            XCTAssertEqual(ProfileCarouselRules.createOutcome(for: directory), FeedSelectorRules.outcome(for: value))
        }
    }

    // MARK: - Ce qui est gardé des membres

    private func member(_ uid: String, avatar: Data? = nil) -> MiffMember {
        MiffMember(
            uid: uid, firstName: "Alex", lastName: "Martin", avatarJpeg: avatar,
            role: .member, sharingEnabled: true, phone: "+33612345678"
        )
    }

    func testOnlyInitialsAndPhotoAreKeptOfAMemberNeverTheNameOrThePhoneNumber() {
        let thumb = MemberThumb(member: member("u1", avatar: Data([1, 2])))

        XCTAssertEqual(thumb.uid, "u1")
        XCTAssertEqual(thumb.initials, "AM")
        XCTAssertEqual(thumb.avatarJpeg, Data([1, 2]))
        XCTAssertEqual(String(describing: thumb), "MemberThumb(redacted)")
    }

    func testAPreviewKeepsTheTotalButOnlyFivePhotos() {
        let members = (0..<9).map { member("u\($0)") }

        let preview = MemberPreview(members: members)

        XCTAssertEqual(preview.count, 9)
        XCTAssertEqual(preview.thumbs.map { $0.uid }, ["u0", "u1", "u2", "u3", "u4"])
        XCTAssertEqual(String(describing: preview), "MemberPreview(redacted)")
    }
}
