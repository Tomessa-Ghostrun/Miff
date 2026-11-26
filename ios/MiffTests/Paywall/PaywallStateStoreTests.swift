// Quelques assertions portent un message en clair : elles dépassent la limite de ligne.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// Les états que l'achat réel ajoute à l'écran d'offre (SRV-8I) : un abonnement déjà utilisé par un autre compte, un abonnement suspendu, un produit qui disparaît pendant l'achat, et les
/// issues d'une restauration. Chaque transition est calme : jamais de rouge ni de code, et jamais un droit accordé par l'écran.
@MainActor
final class PaywallStateStoreTests: XCTestCase {
    private let trial = PaywallFixtures.withTrial
    private let plans = PaywallFixtures.withoutTrial

    func testAnotherMiffAccountAndASuspendedSubscriptionAreCalmStatesThatKeepTheOffer() {
        let purchasing = PaywallState.purchasing(trial, .annual)

        XCTAssertEqual(purchasing.reduced(by: .purchaseOtherAccount), .otherAccount(trial))
        XCTAssertEqual(purchasing.reduced(by: .purchaseOnHold), .onHold(trial))
        XCTAssertEqual(PaywallState.otherAccount(trial).offers, trial)
        XCTAssertEqual(PaywallState.onHold(trial).offers, trial)
        XCTAssertFalse(PaywallState.otherAccount(trial).isBusy)
        XCTAssertFalse(PaywallState.onHold(trial).isBusy)
        XCTAssertTrue(PaywallState.otherAccount(trial).showsInformation)
    }

    func testAProductThatDisappearsDuringThePurchaseMakesTheOfferUnavailableWithoutPriceOrButton() {
        let state = PaywallState.purchasing(trial, .monthly).reduced(by: .purchaseUnavailable)

        XCTAssertEqual(state, .unavailable)
        XCTAssertNil(state.offers)
    }

    func testAfterAnotherAccountTheRefusalDoesNotBlockAnotherTryOrARestoration() {
        let state = PaywallState.otherAccount(plans)

        XCTAssertEqual(state.reduced(by: .purchaseStarted(.annual)), .purchasing(plans, .annual))
        XCTAssertEqual(state.reduced(by: .restoreStarted), .restoring(plans))
        XCTAssertEqual(state.reduced(by: .offersRequested), .loading)
    }

    func testASuspendedSubscriptionIsNeverBoughtASecondTime() {
        let state = PaywallState.onHold(plans)

        XCTAssertEqual(state.reduced(by: .purchaseStarted(.annual)), state, "il se règle sur la page du store")
        XCTAssertEqual(state.reduced(by: .offersRequested), .loading)
    }

    func testEveryOutcomeOfARestorationHasItsCalmState() {
        let restoring = PaywallState.restoring(plans)

        XCTAssertEqual(restoring.reduced(by: .restoreOtherAccount), .otherAccount(plans))
        XCTAssertEqual(restoring.reduced(by: .restorePending), .pending(plans))
        XCTAssertEqual(restoring.reduced(by: .restoreOnHold), .onHold(plans))
        XCTAssertEqual(restoring.reduced(by: .restoreCancelled), .offers(plans), "annulation sans message")
        XCTAssertEqual(restoring.reduced(by: .restoreFound), .subscribed)
    }

    func testTheNewEventsMeanNothingOutsideTheirState() {
        let events: [PaywallEvent] = [
            .purchaseOtherAccount, .purchaseOnHold, .purchaseUnavailable, .restoreOtherAccount, .restorePending,
            .restoreOnHold, .restoreCancelled
        ]
        for event in events {
            XCTAssertEqual(PaywallState.offers(trial).reduced(by: event), .offers(trial), "\(event)")
            XCTAssertEqual(PaywallState.comingSoon.reduced(by: event), .comingSoon, "\(event)")
            XCTAssertEqual(PaywallState.subscribed.reduced(by: event), .subscribed, "\(event)")
        }
    }

    // MARK: - Le modèle

    private func makeModel(state: PaywallState = .comingSoon) -> PaywallViewModel {
        PaywallViewModel(reason: .subscription, state: state, clock: ManualPaywallClock(), announcer: RecordingPaywallAnnouncer())
    }

    func testStartingTheStoreMovesComingSoonToLoadingAndNothingElse() {
        let model = makeModel()
        model.beginStore()
        XCTAssertEqual(model.state, .loading)

        let offers = makeModel(state: .offers(trial))
        offers.beginStore()
        XCTAssertEqual(offers.state, .offers(trial))
    }

    func testTheFreeTrialLinkGoesBackToTheMainViewOfAnEligibleAccount() {
        let model = makeModel(state: .offers(trial))
        model.showAllOffers()
        XCTAssertEqual(model.layout, .plans)

        model.showTrial()

        XCTAssertEqual(model.layout, .trial, "« Essai gratuit » revient à la vue principale, puis la feuille s'ouvre")
    }
}
// swiftlint:enable line_length
