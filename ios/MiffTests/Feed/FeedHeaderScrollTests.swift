import XCTest
@testable import Miff

// L'en-tête du Feed qui se masque au défilement et revient dès qu'on remonte (D41, retour du PM sur le build 27) : les règles PURES, sans
// interface. La distance défilée vient directement de la vue de défilement (`ScrollDistanceReader`) : 0 au repos, positive en descendant
// dans la liste, négative quand on tire vers le bas.
final class FeedHeaderScrollTests: XCTestCase {
    private func header(height: CGFloat = 100) -> CollapsingHeaderState {
        var state = CollapsingHeaderState()
        state.measured(height)
        return state
    }

    func testAtRestTheHeaderIsFullyShownAndTheListStartsBelowIt() {
        var state = header()

        state.scrolled(from: 0, to: 0)

        XCTAssertEqual(state.offset, 0)
        // La première carte commence sous l'en-tête : marge haute = sa hauteur + une marge, jamais moins.
        let listTop = state.height + Spacing.md
        XCTAssertGreaterThanOrEqual(listTop, state.height + state.offset)
    }

    func testScrollingDownHidesTheHeaderPointForPointThenFully() {
        var state = header()
        var previous: CGFloat = 0

        for scrolled in stride(from: CGFloat(10), through: 300, by: 10) {
            state.scrolled(from: previous, to: scrolled)
            previous = scrolled
            XCTAssertEqual(state.offset, -min(scrolled, 100), "à \(scrolled) pt")
        }
        XCTAssertEqual(state.offset, -100, "entièrement caché")
    }

    func testScrollingBackUpShowsItAtOnceWithoutWaitingForTheTop() {
        var state = header()
        state.scrolled(from: 0, to: 400)
        XCTAssertEqual(state.offset, -100)

        state.scrolled(from: 400, to: 370)
        XCTAssertEqual(state.offset, -70, "30 pt vers le haut : 30 pt d'en-tête qui redescend")
        state.scrolled(from: 370, to: 250)
        XCTAssertEqual(state.offset, 0, "loin du haut de la liste, il est déjà entièrement revenu")
    }

    func testNearTheTopItIsNeverMoreHiddenThanTheListScrolled() {
        var state = header()
        state.scrolled(from: 0, to: 60)
        XCTAssertEqual(state.offset, -60)

        // Un retour vers le haut puis une nouvelle descente près du haut : jamais plus caché que le défilement (pas de bande vide).
        state.scrolled(from: 60, to: 20)
        XCTAssertEqual(state.offset, -20)
        XCTAssertEqual(CollapsingHeaderState.scrolledFromTopIfNear(20, headerHeight: 100), 20)
        XCTAssertNil(CollapsingHeaderState.scrolledFromTopIfNear(101, headerHeight: 100), "loin du haut")
        XCTAssertEqual(CollapsingHeaderState.scrolledFromTopIfNear(100, headerHeight: 100), 100, "seuil inclus")
    }

    func testPullingDownAtTheTopKeepsTheHeaderShown() {
        var state = header()

        state.scrolled(from: 0, to: -40)

        XCTAssertEqual(state.offset, 0)
    }

    func testATinyMovementIsIgnoredSoTheHeaderDoesNotShakeButARealOneIsFollowed() {
        var state = header()
        state.scrolled(from: 0, to: 200)

        state.scrolled(from: 200, to: 200.3)
        XCTAssertEqual(state.offset, -100, "moins de \(CollapsingHeaderState.minimumStep) pt : rien ne bouge")
        state.scrolled(from: 200.3, to: 199.9)
        XCTAssertEqual(state.offset, -100)
        state.scrolled(from: 199.9, to: 190)
        XCTAssertEqual(state.offset, -90.1, accuracy: 0.001, "un vrai mouvement est suivi")
    }

    func testALockedHeaderNeverHidesWhateverTheScroll() {
        var state = header()
        state.lock(true)

        state.scrolled(from: 0, to: 500)

        XCTAssertEqual(state.offset, 0)
    }

    func testNothingHidesBeforeTheHeaderIsMeasured() {
        var state = CollapsingHeaderState()

        state.scrolled(from: 0, to: 500)

        XCTAssertEqual(state.offset, 0)
    }

    @MainActor
    func testTheModelReadsTheDirectScrollWithoutAnyRestPositionToGuess() {
        let model = FeedHeaderModel()
        model.measured(100)

        model.scrolled(to: 0)
        XCTAssertEqual(model.state.offset, 0)
        model.scrolled(to: 40)
        XCTAssertEqual(model.state.offset, -40)
        model.scrolled(to: 250)
        XCTAssertEqual(model.state.offset, -100)
        model.scrolled(to: 220)
        XCTAssertEqual(model.state.offset, -70)
        model.scrolled(to: -30)
        XCTAssertEqual(model.state.offset, 0, "tirer vers le bas en haut de la liste : l'en-tête est là")
    }

    @MainActor
    func testOnceTheDirectScrollIsReadTheFallbackPositionIsIgnored() {
        let model = FeedHeaderModel()
        model.measured(100)
        model.scrolled(to: 0)

        model.contentMoved(to: 150)
        model.contentMoved(to: 10)

        XCTAssertEqual(model.state.offset, 0, "la lecture de repli ne contredit pas la lecture directe")
    }

    func testTheShowAnimationIsShortAndSystemAnimationsAreRespectedByTheScreen() throws {
        XCTAssertLessThanOrEqual(FeedHeaderMotion.showSeconds, 0.3, "animation courte")
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Feed
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Views/Feed/FeedScreen.swift")
        let source = try String(contentsOf: url, encoding: .utf8)

        XCTAssertTrue(source.contains("accessibilityReduceMotion"), "« Réduire les animations » est lu")
        let rule = "if reduceMotion {\n            change()"
        XCTAssertTrue(source.contains(rule), "aucune animation quand il est demandé")
    }
}
