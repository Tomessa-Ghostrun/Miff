// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// L'indicateur de « tirer pour actualiser » (retour du PM, TestFlight 39 : aucun symbole visible) : il apparaît PENDANT le tirage, se remplit avec
/// la distance, tourne dès que l'un des signaux d'actualisation est vrai ou qu'un tirage assez long est relâché, et reste au moins 0,8 s.
final class FeedPullIndicatorLogicTests: XCTestCase {
    private var logic = FeedPullIndicatorLogic()

    private func state(
        _ distance: CGFloat,
        dragging: Bool = true,
        refreshing: Bool = false,
        at now: TimeInterval = 0
    ) -> FeedPullIndicatorState {
        logic.update(distance: distance, dragging: dragging, refreshing: refreshing, now: now)
    }

    func testNothingIsShownAtRestOrForTheNoiseOfAScroll() {
        XCTAssertEqual(state(0), .hidden)
        XCTAssertEqual(state(FeedPullIndicatorLogic.minVisible - 0.1), .hidden)
    }

    func testTheIndicatorAppearsDuringThePullAndFillsWithTheDistance() {
        XCTAssertEqual(state(FeedPullIndicatorLogic.minVisible), .pulling(progress: 0.10))
        XCTAssertEqual(state(FeedPullIndicatorLogic.threshold / 2), .pulling(progress: 0.5))
        XCTAssertEqual(state(FeedPullIndicatorLogic.threshold), .pulling(progress: 1))
        XCTAssertEqual(state(FeedPullIndicatorLogic.threshold * 3), .pulling(progress: 1), "jamais plus que plein")
    }

    func testTheProgressIsRoundedSoTheScreenIsNotRedrawnForNothing() {
        XCTAssertEqual(state(30.0), state(30.4))
    }

    func testEverySignalOfRefreshShowsTheSpinnerEvenWithoutAnyPull() {
        XCTAssertEqual(state(0, dragging: false, refreshing: true), .loading)
    }

    func testTheSpinnerStaysAtLeastEightTenthsOfASecondEvenWhenTheSignalDropsAtOnce() {
        XCTAssertEqual(state(0, dragging: false, refreshing: true, at: 10), .loading)
        XCTAssertEqual(state(0, dragging: false, refreshing: false, at: 10.2), .loading)
        XCTAssertEqual(logic.secondsUntilChange(now: 10.2) ?? 0, 0.6, accuracy: 0.0001)
        XCTAssertEqual(state(0, dragging: false, refreshing: false, at: 10.79), .loading)
        XCTAssertEqual(state(0, dragging: false, refreshing: false, at: 10 + FeedPullIndicatorLogic.minLoadingSeconds), .hidden)
        XCTAssertNil(logic.secondsUntilChange(now: 10 + FeedPullIndicatorLogic.minLoadingSeconds))
    }

    func testTheSpinnerIsNotCutWhileTheSignalIsStillTrue() {
        _ = state(0, dragging: false, refreshing: true, at: 0)

        XCTAssertEqual(state(0, dragging: false, refreshing: true, at: 5), .loading)
        XCTAssertNil(logic.secondsUntilChange(now: 5), "rien n'est programmé tant que le signal dure")
    }

    func testALongPullReleasedStartsTheSpinnerEvenWhenNoSignalArrives() {
        _ = state(80, dragging: true, at: 0)
        _ = state(100, dragging: true, at: 0.1)

        XCTAssertEqual(state(60, dragging: false, at: 0.2), .loading, "relâché : la roue se lance")
        XCTAssertEqual(state(0, dragging: false, at: 0.5), .loading)
        XCTAssertEqual(state(0, dragging: false, at: 1.1), .hidden)
    }

    func testAShortPullReleasedOrAPullStillHeldNeverStartsTheSpinner() {
        _ = state(40, dragging: true, at: 0)
        XCTAssertEqual(state(10, dragging: false, at: 0.1), .pulling(progress: 0.15), "trop court : rien ne se lance")
        XCTAssertEqual(state(0, dragging: false, at: 0.2), .hidden)

        _ = state(90, dragging: true, at: 1)
        XCTAssertEqual(state(60, dragging: true, at: 1.1), .pulling(progress: 0.85), "le doigt est encore posé : rien ne se lance")
    }

    func testAReleaseIsCountedOnceAndAFreshPullStartsFromScratch() {
        _ = state(100, dragging: true, at: 0)
        _ = state(50, dragging: false, at: 0.1)
        XCTAssertEqual(state(0, dragging: false, at: 2), .hidden)

        XCTAssertEqual(state(20, dragging: true, at: 3), .pulling(progress: 0.3))
        XCTAssertEqual(state(5, dragging: false, at: 3.1), .hidden, "un nouveau tirage court ne relance rien")
    }

    func testTheConstantsAreTheDecidedOnes() {
        XCTAssertGreaterThanOrEqual(FeedPullIndicatorLogic.minLoadingSeconds, 0.8)
        XCTAssertGreaterThan(FeedPullIndicatorLogic.threshold, FeedPullIndicatorLogic.minVisible)
    }
}

/// Le modèle qui relie la vue de défilement, l'état du Feed et la pastille.
final class FeedPullIndicatorModelTests: XCTestCase {
    private final class Clock {
        var now: TimeInterval = 100
    }

    private func make() -> (FeedPullIndicatorModel, Clock) {
        let model = FeedPullIndicatorModel()
        let clock = Clock()
        model.uptime = { clock.now }
        return (model, clock)
    }

    func testThePullDrivesTheStateAndTheFeedRefreshingShowsTheSpinner() {
        let (model, _) = make()

        model.pulled(distance: 35, dragging: true, uikitRefreshing: false)
        XCTAssertEqual(model.state, .pulling(progress: 0.5))

        model.feedRefreshingChanged(true)
        XCTAssertEqual(model.state, .loading)
    }

    func testTheUIKitWheelAloneShowsTheSpinner() {
        let (model, _) = make()

        model.pulled(distance: 0, dragging: false, uikitRefreshing: true)

        XCTAssertEqual(model.state, .loading)
    }

    func testTheSpinnerRetiresByItselfAfterTheMinimumTimeWithoutAnyMoveOfTheList() {
        let (model, clock) = make()
        model.feedRefreshingChanged(true)
        model.feedRefreshingChanged(false)
        XCTAssertEqual(model.state, .loading, "0,8 s au minimum")

        let hidden = expectation(description: "la pastille se retire seule")
        clock.now += FeedPullIndicatorLogic.minLoadingSeconds + 0.1
        let observation = model.$state.dropFirst().sink { state in
            if state == .hidden {
                hidden.fulfill()
            }
        }
        wait(for: [hidden], timeout: 3)
        observation.cancel()
    }
}
// swiftlint:enable line_length
