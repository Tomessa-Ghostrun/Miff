import XCTest
@testable import Miff

/// L'écran d'offre : la croix différée (ticket `paywall-v2.md` §6 ; CR1 à CR5, CR8, CR11 à CR13) et l'état des variantes simulées.
@MainActor
final class PaywallViewModelTests: XCTestCase {
    private let clock = ManualPaywallClock()
    private let announcer = RecordingPaywallAnnouncer()

    private func makeModel(
        reason: PaywallReason = .limit,
        state: PaywallState = .comingSoon,
        delay: TimeInterval = PaywallTiming.closeDelay
    ) -> PaywallViewModel {
        PaywallViewModel(reason: reason, state: state, closeDelay: delay, clock: clock, announcer: announcer)
    }

    // MARK: - La croix : absente, puis là

    func testTheCrossIsAbsentAtTheOpening() {
        let model = makeModel()
        model.onDisplayed()

        XCTAssertFalse(model.isCloseAvailable, "CR1 : à t = 0 la croix n'existe pas")
        XCTAssertTrue(announcer.spoken.isEmpty)
    }

    func testTheCrossAppearsAtThreeSecondsNotBefore() {
        let model = makeModel()
        model.onDisplayed()

        clock.set(2.9)
        model.tick()
        XCTAssertFalse(model.isCloseAvailable, "CR2 : à 2,9 s elle est absente")

        clock.set(3.0)
        model.tick()
        XCTAssertTrue(model.isCloseAvailable, "CR2 : à 3,0 s elle est là")
    }

    func testTheCountdownStartsAtTheDisplayNotAtTheCreation() {
        let model = makeModel()
        clock.set(40) // l'écran est créé, puis la navigation prend son temps
        XCTAssertFalse(model.isCloseAvailable)
        clock.set(100)
        model.tick()
        XCTAssertFalse(model.isCloseAvailable, "CR3 : sans affichage, le temps qui passe ne révèle rien")

        model.onDisplayed()
        clock.set(102.9)
        model.tick()
        XCTAssertFalse(model.isCloseAvailable)
        clock.set(103)
        model.tick()
        XCTAssertTrue(model.isCloseAvailable)
    }

    func testItStartsEvenIfThePricesAreStillLoadingOrTheStoreIsUnreachable() {
        for state in [PaywallState.loading, .unavailable, .comingSoon] {
            let model = makeModel(state: state)
            model.onDisplayed()
            clock.set(clock.now() + 3)
            model.tick()

            XCTAssertTrue(model.isCloseAvailable, "CR3 et CR13 : \(state) ne retarde jamais la croix")
        }
    }

    func testTheTimerWaitsTheDelayThenRevealsTheCross() async {
        let model = makeModel()

        await model.runCloseTimer()

        XCTAssertTrue(model.isCloseAvailable)
        XCTAssertEqual(clock.sleeps, [3], "une seule attente, de la durée du délai")
    }

    func testACancelledTimerRevealsNothing() async {
        clock.failsWithCancellation = true
        let model = makeModel()

        await model.runCloseTimer()

        XCTAssertFalse(model.isCloseAvailable, "l'écran a disparu avant l'échéance : rien n'est révélé")
        XCTAssertTrue(announcer.spoken.isEmpty)
    }

    // MARK: - Nouvel affichage, retour d'arrière-plan, persistance

    func testANewDisplayIsANewStartWithNothingShared() {
        let first = makeModel(reason: .extraMiff)
        first.onDisplayed()
        clock.set(5)
        first.tick()
        XCTAssertTrue(first.isCloseAvailable)

        // Fermer puis rouvrir, même tout de suite, depuis n'importe quel point d'entrée.
        for reason in [PaywallReason.limit, .danger, .extraMiff] {
            let next = makeModel(reason: reason)
            next.onDisplayed()
            XCTAssertFalse(next.isCloseAvailable, "CR4 : \(reason) cache de nouveau la croix pendant 3 s")
        }
    }

    func testOnceThereTheCrossDoesNotComeBackToTheDelay() {
        let model = makeModel(state: .offers(PaywallFixtures.withTrial))
        model.onDisplayed()
        clock.set(3)
        model.tick()

        // Rotation, texte plus grand, autre thème ou langue = l'écran se redessine ; le retour au premier plan relit l'horloge.
        model.onDisplayed()
        model.showAllOffers()
        model.send(.purchaseStarted(.annual))
        model.send(.purchaseCancelled)
        model.tick()
        XCTAssertTrue(model.isCloseAvailable, "CR5 : la croix reste, le délai n'est pas rejoué")
    }

    func testTimeSpentInTheBackgroundBeforeTheDelayCounts() {
        let model = makeModel()
        model.onDisplayed()
        clock.set(1) // passe en arrière-plan
        model.tick()
        XCTAssertFalse(model.isCloseAvailable)

        clock.set(45) // revient au premier plan
        model.tick()
        XCTAssertTrue(model.isCloseAvailable, "CR5 : 3 s se sont écoulées en temps réel")
    }

    func testAnOpenStoreSheetDoesNotRestartTheDelay() {
        let model = makeModel(state: .offers(PaywallFixtures.withTrial))
        model.onDisplayed()
        clock.set(3)
        model.tick()
        model.send(.purchaseStarted(.annual))

        model.onDisplayed() // la feuille du store se ferme : l'écran réapparaît
        XCTAssertTrue(model.isCloseAvailable, "CR11 : le délai n'est pas relancé au retour de la feuille")
    }

    // MARK: - VoiceOver

    func testTheAppearanceIsAnnouncedOnceWithoutAnythingElse() {
        let model = makeModel()
        model.onDisplayed()
        clock.set(2.9)
        model.tick()
        XCTAssertTrue(announcer.spoken.isEmpty, "CR8 : rien n'est annoncé avant l'apparition")

        clock.set(3)
        model.tick()
        model.tick()
        clock.set(30)
        model.tick()

        XCTAssertEqual(announcer.spoken, [L10n.string("paywall_close_available_a11y")], "annoncée une seule fois")
        XCTAssertNotEqual(announcer.spoken.first, "paywall_close_available_a11y", "le texte existe dans le catalogue")
    }

    func testWithoutDelayNothingIsAnnounced() {
        let model = makeModel(delay: 0)
        model.onDisplayed()
        model.tick()

        XCTAssertTrue(model.isCloseAvailable)
        XCTAssertTrue(announcer.spoken.isEmpty, "aucune croix n'« apparaît » : rien à dire")
    }

    // MARK: - La valeur unique du délai

    func testAZeroDelayRemovesTheRuleEverywhere() {
        for state in [PaywallState.comingSoon, .loading, .offers(PaywallFixtures.withTrial)] {
            let model = makeModel(state: state, delay: 0)

            XCTAssertTrue(model.isCloseAvailable, "CR12 : délai 0 = croix d'emblée, état \(state)")
        }
    }

    func testTheDefaultModelUsesTheSingleDelayOfTheApp() {
        let model = PaywallViewModel(reason: .limit, clock: clock, announcer: announcer)
        model.onDisplayed()

        clock.set(PaywallTiming.closeDelay - 0.1)
        model.tick()
        XCTAssertFalse(model.isCloseAvailable)
        clock.set(PaywallTiming.closeDelay)
        model.tick()
        XCTAssertTrue(model.isCloseAvailable)
    }

    // MARK: - Le contenu

    func testTheProductionVariantIsComingSoonWithoutAnyOffer() {
        let model = PaywallViewModel(reason: .extraMiff, clock: clock, announcer: announcer)

        XCTAssertEqual(model.state, .comingSoon, "avant SRV-8 : « Miff+ arrive bientôt », aucun prix")
        XCTAssertNil(model.state.offers)
        XCTAssertNil(model.layout)
        XCTAssertFalse(model.state.showsInformation, "ni restauration, ni abonnement à décrire")
        XCTAssertFalse(model.state.isBusy)
    }

    func testComingSoonIgnoresEveryEvent() {
        let model = makeModel()
        model.send(.offersLoaded(PaywallFixtures.withTrial))
        model.send(.purchaseStarted(.annual))
        model.send(.restoreStarted)
        model.showAllOffers()

        XCTAssertEqual(model.state, .comingSoon)
        XCTAssertFalse(model.showsAllOffers)
    }

    func testAnEligibleAccountSeesTheTrialLayoutThenCanSeeAllOffers() {
        let model = makeModel(state: .offers(PaywallFixtures.withTrial))
        XCTAssertEqual(model.layout, .trial)

        model.showAllOffers()
        XCTAssertEqual(model.layout, .plans, "« Voir toutes les offres » ouvre la disposition à deux boutons")
    }

    func testANonEligibleAccountAlwaysSeesTheTwoPlans() {
        let model = makeModel(state: .offers(PaywallFixtures.withoutTrial))

        XCTAssertEqual(model.layout, .plans)
    }

    func testASubscribedAccountCanLeaveAtOnceWithoutTheDelay() {
        let model = makeModel(state: .offers(PaywallFixtures.withTrial))
        model.onDisplayed()
        XCTAssertFalse(model.isCloseAvailable)

        model.send(.purchaseStarted(.annual))
        model.send(.purchaseSucceeded)

        XCTAssertEqual(model.state, .subscribed)
        XCTAssertTrue(model.isCloseAvailable, "Q-PAY1 : plus d'offre à lire, « Continuer » est actif tout de suite")
        XCTAssertNil(model.layout)
    }

    func testLeavingTheOffersResetsTheSeeAllChoice() {
        let model = makeModel(state: .offers(PaywallFixtures.withTrial))
        model.showAllOffers()
        model.send(.purchaseStarted(.monthly))
        model.send(.purchaseSucceeded)

        XCTAssertFalse(model.showsAllOffers)
    }
}
