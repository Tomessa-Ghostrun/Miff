import XCTest
@testable import Miff

/// Les situations où l'offre périodique ne vient jamais (PP12), les surcouches, Miff+ (PP13 à PP15), l'effacement (PP6), l'extinction (PP17) et les menus et présentations du
/// système propres à iOS. Voir `PeriodicRulesTestCase`.
final class PeriodicPaywallSituationRulesTests: PeriodicRulesTestCase {
    // MARK: - PP12 : jamais dans ces situations, et le compteur est laissé tranquille

    private func assertNeverIn(
        _ name: String, _ situation: PeriodicPaywallSituation, file: StaticString = #filePath, line: UInt = #line
    ) {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        tapAndSettle(rules)

        XCTAssertEqual(rules.evaluate(situation), .no, "\(name) : aucune offre", file: file, line: line)
        XCTAssertEqual(store.value, 10, "\(name) : le compteur est laissé tranquille", file: file, line: line)
        // La situation prend fin : l'offre est restée due, elle vient.
        XCTAssertEqual(rules.evaluate(calmSituation()), .show, "\(name) : elle vient ensuite", file: file, line: line)
    }

    func testNeverOverAScreenPushedOnTheTabsOrAnyScreenThatIsNotTheTabs() {
        assertNeverIn(
            "onglets pas devant (création, arrivée, formulaire, suppression, Réglages)",
            calmSituation { $0.tabsInFront = false }
        )
    }

    func testNeverOnTheUpdateTheViewfinderOrThePreview() {
        assertNeverIn("update", calmSituation { $0.updateScreenOpen = true })
    }

    func testNeverWithTheKeyboardOpen() {
        assertNeverIn("clavier", calmSituation { $0.keyboardOpen = true })
    }

    func testNeverOverAMessageOfTheApp() {
        assertNeverIn("message de l'app", calmSituation { $0.messageOpen = true })
    }

    func testNeverDuringMyOwnAlert() {
        assertNeverIn("ma propre alerte", calmSituation { $0.ownAlertInProgress = true })
    }

    func testNeverWhileALovedOnesAlertIsOnTheFeedOrWaitsUnread() {
        assertNeverIn("alerte d'un proche", calmSituation { $0.lovedOneAlertUnseen = true })
    }

    func testNeverWhenThePricesWouldNotBeThere() {
        assertNeverIn("offre sans prix (achat allumé, hors connexion)", calmSituation { $0.offerReachable = false })
    }

    func testASessionOpenedByAnAlertNotificationNeverShowsEvenWhenCalm() {
        let rules = makeRules(counter: 9)
        rules.excludeSession()
        coldStart(rules)
        tapAndSettle(rules)

        XCTAssertEqual(rules.evaluate(calmSituation()), .no)
        XCTAssertEqual(store.value, 10)

        // La session suivante est une session normale.
        away(rules, millis: thirty)
        tapAndSettle(rules)
        XCTAssertEqual(rules.evaluate(calmSituation()), .show)
    }

    func testAnAlertNotificationTappedAfterTheSessionStartedExcludesItAndTheNextSessionIsFree() {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        tapAndSettle(rules)

        rules.excludeSession()
        XCTAssertEqual(rules.evaluate(calmSituation()), .no)
        clock.advance(20_000)
        away(rules, millis: thirty)
        tapAndSettle(rules)

        XCTAssertEqual(rules.evaluate(calmSituation()), .show)
    }

    func testAnAlertNotificationTappedWhileTheAppWasAwayExcludesTheSessionItOpens() {
        // La réponse du système arrive AVANT le retour au premier plan.
        let rules = makeRules(counter: 9)
        coldStart(rules)
        rules.onBackground()
        clock.advance(thirty)

        rules.excludeSession()
        rules.onForeground()
        tapAndSettle(rules)

        XCTAssertEqual(rules.evaluate(calmSituation()), .no)
    }

    // MARK: - Les surcouches (feuille, boîte de dialogue, menu, photo plein écran, fenêtre d'avis)

    func testAnOverlayOpenOverTheTabsBlocksTheOfferAndTheCounterIsLeftAlone() {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        tapAndSettle(rules)

        rules.onOverlayOpened()
        clock.advance(60_000)

        XCTAssertEqual(rules.evaluate(calmSituation()), .no)
        XCTAssertEqual(store.value, 10)
    }

    func testAfterTheOverlayClosesTheOfferWaitsForACalmMomentCountedFromTheClosing() {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        tapAndSettle(rules)
        rules.onOverlayOpened()
        clock.advance(10_000)

        rules.onOverlayClosed()

        XCTAssertEqual(rules.evaluate(calmSituation()), .wait(1_500))
        clock.advance(1_500)
        XCTAssertEqual(rules.evaluate(calmSituation()), .show)
    }

    func testTwoOverlaysAtOnceBlockUntilTheLastOneClosesAndACloseWithoutAnOpenNeverGoesBelowZero() {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        tapAndSettle(rules)
        rules.onOverlayOpened()
        rules.onOverlayOpened()
        rules.onOverlayClosed()
        clock.advance(5_000)
        XCTAssertEqual(rules.evaluate(calmSituation()), .no)

        rules.onOverlayClosed()
        rules.onOverlayClosed()
        clock.advance(1_500)
        XCTAssertEqual(rules.evaluate(calmSituation()), .show)

        rules.onOverlayOpened()
        XCTAssertEqual(rules.evaluate(calmSituation()), .no, "une surcouche ouverte reste une surcouche")
    }

    func testAFirstTapThatOpensAnOverlayDoesNotBringTheOfferOverIt() {
        // Le premier appui sur le sélecteur de Miffs ou sur une photo : l'appui compte, la surcouche qu'il ouvre retient l'offre.
        let rules = makeRules(counter: 9)
        coldStart(rules)
        rules.onTouch(pressed: true)
        rules.onActivation()
        rules.onTouch(pressed: false)

        rules.onOverlayOpened()
        clock.advance(30_000)

        XCTAssertEqual(rules.evaluate(calmSituation()), .no)
    }

    func testWithTheRuleOffTheOverlaysChangeNothing() {
        let rules = makeRules(openings: 0)
        var changes = 0
        rules.onChange = { changes += 1 }

        rules.onOverlayOpened()
        rules.onOverlayClosed()

        XCTAssertEqual(changes, 0)
    }

    // MARK: - Propre à iOS : une présentation du système, un menu du système

    func testASystemPresentationOverTheTabsBlocksTheOfferAndWaitsAfterItClosesAtLeastOneAndAHalfSeconds() {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        tapAndSettle(rules)

        let open = calmSituation { $0.modalPresented = true }
        XCTAssertEqual(rules.evaluate(open), .wait(PeriodicPaywallDefaults.modalPollMillis))
        clock.advance(PeriodicPaywallDefaults.modalPollMillis)
        XCTAssertEqual(rules.evaluate(open), .wait(PeriodicPaywallDefaults.modalPollMillis), "toujours ouverte")
        XCTAssertEqual(store.value, 10, "le compteur est laissé tranquille")

        // Elle se ferme juste après le dernier regard : l'offre attend encore 1,5 s de plus que cet intervalle, donc au moins 1,5 s après la fermeture.
        clock.advance(PeriodicPaywallDefaults.modalPollMillis)
        XCTAssertEqual(rules.evaluate(calmSituation()), .wait(1_500))
        clock.advance(1_500)
        XCTAssertEqual(rules.evaluate(calmSituation()), .show)
    }

    func testATapOnAMenuHoldsTheOfferBackUntilTheNextTouch() {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        tapAndSettle(rules)

        rules.onMenuAreaTapped()
        clock.advance(3_000)
        let held = rules.evaluate(calmSituation())
        XCTAssertEqual(held, .wait(PeriodicPaywallDefaults.modalPollMillis), "menu tenu pour ouvert")

        // La personne choisit dans le menu : un nouveau toucher ; le moment calme se compte depuis lui.
        rules.onTouch(pressed: true)
        rules.onTouch(pressed: false)
        XCTAssertEqual(rules.evaluate(calmSituation()), .wait(1_500))
        clock.advance(1_500)
        XCTAssertEqual(rules.evaluate(calmSituation()), .show)
    }

    func testAMenuThatNeverGetsAnotherTouchIsNotHeldForeverTheOfferComesAfterTheHold() {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        tapAndSettle(rules)

        rules.onMenuAreaTapped()
        clock.advance(PeriodicPaywallDefaults.menuHoldMillis)

        XCTAssertEqual(rules.evaluate(calmSituation()), .show)
    }

    // MARK: - PP13, PP14, PP15 : Miff+

    func testWithMiffPlusNeverShownAndTheCounterGoesBackToZero() {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        tapAndSettle(rules)
        XCTAssertEqual(store.value, 10)

        XCTAssertEqual(rules.evaluate(calmSituation { $0.miffPlus = true }), .no)

        XCTAssertEqual(store.value, 0)
    }

    func testAfterTheEndOfASubscriptionThePersonStartsFromZeroNotFromAnOfferThatWasDue() {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        tapAndSettle(rules)
        _ = rules.evaluate(calmSituation { $0.miffPlus = true })

        XCTAssertEqual(rules.evaluate(calmSituation { $0.miffPlus = false }), .no)
        XCTAssertEqual(store.value, 0)
    }

    func testAnUnknownRightNeverShowsAndTheCounterIsLeftAlone() {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        tapAndSettle(rules)

        XCTAssertEqual(rules.evaluate(calmSituation { $0.miffPlus = nil }), .no)
        XCTAssertEqual(store.value, 10)
        XCTAssertEqual(rules.evaluate(calmSituation { $0.miffPlus = false }), .show, "connu plus tard : elle vient")
    }

    func testMiffPlusAppearingDuringTheWaitCancelsTheOffer() {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        rules.onTouch(pressed: true)
        rules.onActivation()
        rules.onTouch(pressed: false)
        XCTAssertEqual(rules.evaluate(calmSituation()), .wait(1_500))

        clock.advance(1_500)

        XCTAssertEqual(rules.evaluate(calmSituation { $0.miffPlus = true }), .no)
    }

    // MARK: - PP6 : l'effacement

    func testSignOutAndDeletionOfTheAccountEraseTheCounterAndTheNextPersonStartsFromZero() {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        tapAndSettle(rules)

        rules.clear()

        XCTAssertEqual(store.value, 0)
        XCTAssertEqual(store.clears, 1)
        XCTAssertEqual(rules.evaluate(calmSituation()), .no)
        // Le compte suivant dans la même session : la session est déjà comptée, le compteur reste à zéro jusqu'à la prochaine ouverture.
        rules.onShellShown()
        XCTAssertEqual(store.value, 0)
    }

    // MARK: - PP17 : la règle s'éteint par une constante

    func testAThresholdOfZeroSwitchesTheWholeRuleOffCountingIncluded() {
        let rules = makeRules(openings: 0, counter: 0)
        XCTAssertFalse(rules.isOn)

        coldStart(rules)
        tapAndSettle(rules)
        rules.excludeSession()
        rules.onTouch(pressed: true)
        rules.onPaywallDisplayed()
        XCTAssertEqual(store.value, 0, "rien n'est compté")

        store.value = 99
        XCTAssertEqual(rules.evaluate(calmSituation()), .no)
        XCTAssertEqual(store.value, 99, "rien n'est touché")
    }

    func testALoweredThresholdIsUsedForTheCapAndTheDecision() {
        let rules = makeRules(openings: 3, counter: 2)

        coldStart(rules)
        XCTAssertEqual(store.value, 3)
        tapAndSettle(rules)

        XCTAssertEqual(rules.evaluate(calmSituation()), .show)
    }

    // MARK: - Les outils de la recette (Debug et Internal)

    func testTheDiagnosticCounterIsClampedBetweenZeroAndTheThreshold() {
        let rules = makeRules()

        rules.setCounterForDiagnostic(40)
        XCTAssertEqual(rules.counterForDiagnostic(), 10)
        rules.setCounterForDiagnostic(-3)

        XCTAssertEqual(rules.counterForDiagnostic(), 0)
        XCTAssertEqual(rules.thresholdForDiagnostic, 10)
    }

    func testTheDiagnosticCounterMakesTheNextTapEnoughWhenTheOfferIsDue() {
        let rules = makeRules()
        coldStart(rules)

        rules.setCounterForDiagnostic(rules.thresholdForDiagnostic)
        tapAndSettle(rules)

        XCTAssertEqual(rules.evaluate(calmSituation()), .show)
    }

    func testTheChangeHandlerMovesWhenSomethingThatCanChangeTheDecisionHappensButNotAtEveryFingerDown() {
        let rules = makeRules()
        var changes = 0
        rules.onChange = { changes += 1 }

        rules.onTouch(pressed: true)
        XCTAssertEqual(changes, 0, "un défilement ferait des dizaines d'événements : rien à la pose")
        rules.onTouch(pressed: false)

        XCTAssertEqual(changes, 1)
    }

    func testTheSessionSnapshotTellsTheStateOfTheSessionWithoutAnythingPersonal() {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        rules.onActivation()
        rules.onOverlayOpened()

        let snapshot = rules.sessionForDiagnostic()

        XCTAssertEqual(
            snapshot,
            PeriodicPaywallRules.SessionSnapshot(
                appOpen: true, firstTapDone: true, shownThisSession: false, excluded: false, overlaysOpen: 1
            )
        )
    }
}
