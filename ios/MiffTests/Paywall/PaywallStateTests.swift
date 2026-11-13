import XCTest
@testable import Miff

/// Les états de l'écran d'offre et leurs transitions (ticket `paywall-v2.md` §5.2 ; critères PW9 à PW13). Livrés avant l'achat réel
/// (SRV-8I) et testés par des états simulés.
final class PaywallStateTests: XCTestCase {
    private let trial = PaywallFixtures.withTrial
    private let plans = PaywallFixtures.withoutTrial

    // MARK: - Chargement (PW9)

    func testThePricesLoadThenTheOffersAreShown() {
        var state = PaywallState.loading
        state = state.reduced(by: .offersLoaded(trial))

        XCTAssertEqual(state, .offers(trial))
    }

    func testPricesThatFailOrTakeTooLongBecomeUnavailableWithoutAnyPriceOrBuyButton() {
        XCTAssertEqual(PaywallState.loading.reduced(by: .offersFailed), .unavailable)
        XCTAssertEqual(PaywallState.loading.reduced(by: .loadingTimedOut), .unavailable)
        XCTAssertNil(PaywallState.unavailable.offers)
        XCTAssertEqual(PaywallTiming.offersLoadingTimeout, 10, "au-delà de 10 s : prix indisponibles")
    }

    func testRetryingLoadsAgainAndALateAnswerStillCounts() {
        XCTAssertEqual(PaywallState.unavailable.reduced(by: .offersRequested), .loading)
        XCTAssertEqual(PaywallState.unavailable.reduced(by: .offersLoaded(plans)), .offers(plans))
        XCTAssertEqual(PaywallState.failed(trial).reduced(by: .offersRequested), .loading)
    }

    func testATimeoutOnlyMeansSomethingWhileLoading() {
        XCTAssertEqual(PaywallState.offers(trial).reduced(by: .loadingTimedOut), .offers(trial))
        XCTAssertEqual(PaywallState.subscribed.reduced(by: .loadingTimedOut), .subscribed)
    }

    // MARK: - Achat (PW10, PW11)

    func testAPurchaseDisablesEverythingElseWhileItRuns() {
        let state = PaywallState.offers(trial).reduced(by: .purchaseStarted(.annual))

        XCTAssertEqual(state, .purchasing(trial, .annual))
        XCTAssertTrue(state.isBusy)
        XCTAssertEqual(state.reduced(by: .purchaseStarted(.monthly)), state, "pas de double achat")
        XCTAssertEqual(state.reduced(by: .restoreStarted), state)
    }

    func testCancellingTheStoreSheetGoesBackToTheOfferWithoutAMessage() {
        let state = PaywallState.purchasing(plans, .monthly).reduced(by: .purchaseCancelled)

        XCTAssertEqual(state, .offers(plans))
        XCTAssertFalse(state.isBusy)
    }

    func testAPendingPaymentGrantsNothingAndAllowsNoSecondPurchase() {
        let state = PaywallState.purchasing(plans, .annual).reduced(by: .purchasePending)

        XCTAssertEqual(state, .pending(plans))
        XCTAssertTrue(state.isBusy)
        XCTAssertNotEqual(state, .subscribed)
        XCTAssertEqual(state.reduced(by: .purchaseStarted(.annual)), state)
    }

    func testAFailureIsCalmAndAllowsAnotherTry() {
        let state = PaywallState.purchasing(trial, .annual).reduced(by: .purchaseFailed)

        XCTAssertEqual(state, .failed(trial))
        XCTAssertEqual(state.reduced(by: .purchaseStarted(.annual)), .purchasing(trial, .annual))
        XCTAssertEqual(state.reduced(by: .restoreStarted), .restoring(trial), "« Restaurer » reste disponible")
    }

    func testASuccessMakesTheAccountSubscribedFromAnyStateWithOffers() {
        let states: [PaywallState] = [
            .loading, .unavailable, .offers(trial), .purchasing(trial, .annual), .pending(trial), .failed(trial),
            .restoring(trial), .restoreNone(trial)
        ]
        for state in states {
            XCTAssertEqual(state.reduced(by: .purchaseSucceeded), .subscribed, "\(state)")
        }
    }

    // MARK: - Restauration (PW12)

    func testRestoringFindsASubscription() {
        let state = PaywallState.offers(trial).reduced(by: .restoreStarted)

        XCTAssertEqual(state, .restoring(trial))
        XCTAssertTrue(state.isBusy)
        XCTAssertEqual(state.reduced(by: .restoreFound), .subscribed)
    }

    func testRestoringFindsNothingOrFailsCalmly() {
        let restoring = PaywallState.restoring(plans)

        XCTAssertEqual(restoring.reduced(by: .restoreNothing), .restoreNone(plans))
        XCTAssertEqual(restoring.reduced(by: .restoreFailed), .failed(plans))
        XCTAssertEqual(PaywallState.restoreNone(plans).reduced(by: .restoreStarted), .restoring(plans))
    }

    func testRestoringNeverStartsByItself() {
        // Rien d'autre qu'un toucher (`restoreStarted`) ne lance la restauration : aucune autre transition n'y mène.
        let others: [PaywallEvent] = [
            .offersRequested, .offersLoaded(trial), .offersFailed, .loadingTimedOut, .purchaseCancelled,
            .purchasePending, .purchaseFailed, .restoreFound, .restoreNothing, .restoreFailed
        ]
        for state in [PaywallState.loading, .unavailable, .offers(trial), .failed(trial), .restoreNone(trial)] {
            let startedByItself = others.filter { event in
                if case .restoring = state.reduced(by: event) {
                    return true
                }
                return false
            }

            XCTAssertTrue(startedByItself.isEmpty, "\(state) : la restauration ne part pas toute seule")
        }
    }

    // MARK: - « Sans achat » et « abonné » (PW8, PW13)

    func testComingSoonAndSubscribedNeverMove() {
        let events: [PaywallEvent] = [
            .offersRequested, .offersLoaded(trial), .offersFailed, .purchaseStarted(.annual), .purchaseSucceeded,
            .restoreStarted, .restoreFound
        ]
        for event in events {
            XCTAssertEqual(PaywallState.comingSoon.reduced(by: event), .comingSoon, "\(event)")
            XCTAssertEqual(PaywallState.subscribed.reduced(by: event), .subscribed, "\(event)")
        }
    }

    func testOnlyTheStatesWithAnOfferShowTheInformationBlockAndOffers() {
        XCTAssertTrue(PaywallState.offers(trial).showsInformation)
        XCTAssertTrue(PaywallState.loading.showsInformation)
        XCTAssertTrue(PaywallState.unavailable.showsInformation)
        XCTAssertFalse(PaywallState.comingSoon.showsInformation)
        XCTAssertFalse(PaywallState.subscribed.showsInformation)
        XCTAssertNil(PaywallState.loading.offers)
        XCTAssertNil(PaywallState.comingSoon.offers)
        XCTAssertNil(PaywallState.subscribed.offers)
    }
}
