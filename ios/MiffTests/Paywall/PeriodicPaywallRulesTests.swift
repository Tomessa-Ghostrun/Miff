import XCTest
@testable import Miff

/// L'offre périodique, règle par règle : les constantes du PM, ce qu'est une ouverture (PP1, PP2, PP3, PP5) et le moment (PP8, PP9, PP10, PP11). Voir `PeriodicRulesTestCase`.
final class PeriodicPaywallRulesTests: PeriodicRulesTestCase {
    // MARK: - Les constantes

    func testTheConstantsAreTheOnesOfThePm() {
        XCTAssertEqual(PeriodicPaywallDefaults.openingsBetween, 10, "décision du PM du 2026-11-26 : 10, et non 15")
        XCTAssertEqual(PeriodicPaywallDefaults.backgroundMillis, 30 * 60 * 1000)
        XCTAssertEqual(PeriodicPaywallDefaults.quietAfterTouchMillis, 1_500)
        XCTAssertEqual(PaywallTiming.closeDelay, 3, "la croix : 3 secondes, une seule valeur pour toutes les entrées")
    }

    func testTheMonotonicClockContinuesWhileThePhoneSleepsAndDoesNotDependOnTheWallClock() {
        let first = PeriodicPaywallClock.monotonicMillis()
        let second = PeriodicPaywallClock.monotonicMillis()

        XCTAssertGreaterThan(first, 0)
        XCTAssertGreaterThanOrEqual(second, first, "monotone")
    }

    // MARK: - PP1, PP2, PP5 : ce qu'est une ouverture

    func testAColdStartWithTheAppOnScreenCountsOne() {
        let rules = makeRules(counter: 4)

        coldStart(rules)

        XCTAssertEqual(store.value, 5)
    }

    func testTheCountIsDoneOnceWhateverTheOrderOfTheTwoEvents() {
        let rules = makeRules()

        rules.onShellShown()
        rules.onForeground()
        XCTAssertEqual(store.value, 1)
        rules.onShellShown()

        XCTAssertEqual(store.value, 1)
    }

    func testAWakeUpWithoutAScreenChangesNothing() {
        // La surveillance des lieux relance l'app en arrière-plan : elle ne devient jamais active, la coquille n'est jamais à l'écran.
        _ = makeRules(counter: 4)

        XCTAssertEqual(store.value, 4)
    }

    func testSignInAndOnboardingAreNeverCountedThenTheFirstOpeningOfTheSignedInAppCountsOne() {
        let rules = makeRules()

        rules.onForeground()
        XCTAssertEqual(store.value, 0, "l'écran de connexion n'est pas une ouverture")
        rules.onShellShown()

        XCTAssertEqual(store.value, 1)
    }

    func testAReturnAfterThirtyMinutesExactlyCountsAndAShorterOneDoesNot() {
        let rules = makeRules()
        coldStart(rules)

        away(rules, millis: thirty - 1)
        XCTAssertEqual(store.value, 1)
        away(rules, millis: thirty)
        XCTAssertEqual(store.value, 2)
        away(rules, millis: 60 * 60_000)

        XCTAssertEqual(store.value, 3)
    }

    func testSeveralShortReturnsAreOneSessionOneCount() {
        let rules = makeRules()
        coldStart(rules)

        for _ in 0..<6 {
            away(rules, millis: 5 * 60_000)
        }

        XCTAssertEqual(store.value, 1)
    }

    func testASecondForegroundCallWithoutABackgroundChangesNothing() {
        // Le centre de contrôle, une alerte du système ou un retour des Réglages : « inactive » puis « active », sans arrière-plan.
        let rules = makeRules()
        coldStart(rules)

        rules.onForeground()
        rules.onForeground()

        XCTAssertEqual(store.value, 1)
    }

    func testTheCounterIsCappedAtTheThreshold() {
        let rules = makeRules(counter: 9)

        coldStart(rules)
        XCTAssertEqual(store.value, 10)
        away(rules, millis: thirty)
        away(rules, millis: thirty)

        XCTAssertEqual(store.value, 10)
    }

    func testTheOpeningIsCountedWhenTheSignedInAppIsAlreadyThereAtTheReturn() {
        let rules = makeRules()
        coldStart(rules)

        rules.onBackground()
        clock.advance(thirty)
        rules.onForeground()

        XCTAssertEqual(store.value, 2)
    }

    func testASignOutSessionNeverCountsWhileTheShellIsGone() {
        let rules = makeRules()
        coldStart(rules)

        rules.onShellGone()
        away(rules, millis: thirty)
        XCTAssertEqual(store.value, 1, "l'écran de connexion d'après une déconnexion n'est pas compté")
        rules.onShellShown()

        XCTAssertEqual(store.value, 2)
    }

    func testARotationKeepsTheFirstTapAndCountsNothing() {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        rules.onTouch(pressed: true)
        rules.onActivation()
        rules.onTouch(pressed: false)

        // L'écran est refait : la coquille part et revient, sans retour au premier plan entre les deux.
        rules.onShellGone()
        rules.onShellShown()
        XCTAssertEqual(store.value, 10)
        clock.advance(PeriodicPaywallDefaults.quietAfterTouchMillis)

        XCTAssertEqual(rules.evaluate(calmSituation()), .show)
    }

    // MARK: - PP8, PP9, PP10 : le moment

    func testWithoutAnyTapTheOfferNeverComesWhateverTheDuration() {
        let rules = makeRules(counter: 9)
        coldStart(rules)

        clock.advance(3 * 60 * 60_000)

        XCTAssertEqual(rules.evaluate(calmSituation()), .no)
    }

    func testTouchesAloneNeverMakeTheOfferPossible() {
        // Un défilement, un balayage : la vue appelle `onTouch`, jamais `onActivation`.
        let rules = makeRules(counter: 9)
        coldStart(rules)

        rules.onTouch(pressed: true)
        rules.onTouch(pressed: false)
        clock.advance(10_000)

        XCTAssertEqual(rules.evaluate(calmSituation()), .no)
    }

    func testAfterTheFirstTapTheOfferComesOnlyAfterOneAndAHalfSecondsWithoutTouching() {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        rules.onTouch(pressed: true)
        rules.onActivation()
        rules.onTouch(pressed: false)

        XCTAssertEqual(rules.evaluate(calmSituation()), .wait(1_500))
        clock.advance(1_000)
        XCTAssertEqual(rules.evaluate(calmSituation()), .wait(500))
        clock.advance(500)

        XCTAssertEqual(rules.evaluate(calmSituation()), .show)
    }

    func testAShortReturnInTheSameSessionWaitsForOneAndAHalfSecondsOfCalmAgain() {
        // La personne revient (moins de 30 minutes : même session, pas d'ouverture de plus) : l'offre ne tombe pas à l'instant du retour.
        let rules = makeRules(counter: 9)
        coldStart(rules)
        tapAndSettle(rules)
        XCTAssertEqual(rules.evaluate(calmSituation()), .show)

        away(rules, millis: 5 * 60_000)
        XCTAssertEqual(store.value, 10, "aucune ouverture de plus")
        XCTAssertEqual(rules.evaluate(calmSituation()), .wait(1_500))
        clock.advance(1_500)

        XCTAssertEqual(rules.evaluate(calmSituation()), .show)
    }

    func testTheControlCenterOrASystemAlertAlsoRestartsTheCalm() {
        // « inactive » puis « active » sans arrière-plan : un second appel d'activation, sans nouvelle session.
        let rules = makeRules(counter: 9)
        coldStart(rules)
        tapAndSettle(rules)

        rules.onForeground()

        XCTAssertEqual(rules.evaluate(calmSituation()), .wait(1_500))
    }

    func testAFingerStillOnTheScreenIsNeverACalmMoment() {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        rules.onTouch(pressed: true)
        rules.onActivation()

        clock.advance(5_000)

        XCTAssertEqual(rules.evaluate(calmSituation()), .wait(1_500))
    }

    func testAFingerThatMovesIsAlwaysAlive() {
        // Un défilement qui dure plus longtemps que le délai d'un toucher perdu reste un doigt posé.
        let rules = makeRules(counter: 9)
        coldStart(rules)
        rules.onTouch(pressed: true)
        rules.onActivation()

        for _ in 0..<6 {
            clock.advance(5_000)
            rules.onTouchMoved()
        }

        XCTAssertEqual(rules.evaluate(calmSituation()), .wait(1_500))
    }

    func testALostTouchNeverBlocksTheOfferForever() {
        // Le système n'a plus rien dit depuis longtemps : le doigt est tenu pour relevé.
        let rules = makeRules(counter: 9)
        coldStart(rules)
        rules.onTouch(pressed: true)
        rules.onActivation()

        clock.advance(PeriodicPaywallDefaults.fingerStaleMillis)

        XCTAssertEqual(rules.evaluate(calmSituation()), .show)
    }

    func testANewTouchStartsTheWaitAgain() {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        tapAndSettle(rules)
        XCTAssertEqual(rules.evaluate(calmSituation()), .show)

        rules.onTouch(pressed: false)

        XCTAssertEqual(rules.evaluate(calmSituation()), .wait(1_500))
    }

    func testATapThatIsNotOnTheSignedInAppDoesNotCount() {
        let rules = makeRules(counter: 9)
        rules.onForeground()

        rules.onActivation()
        rules.onShellShown()
        clock.advance(10_000)

        XCTAssertEqual(rules.evaluate(calmSituation()), .no)
    }

    func testTheCounterBelowTheThresholdNeverShows() {
        let rules = makeRules(counter: 7)
        coldStart(rules)
        XCTAssertEqual(store.value, 8)

        tapAndSettle(rules)

        XCTAssertEqual(rules.evaluate(calmSituation()), .no)
    }

    func testTheTenthOpeningIsTheFirstThatCanShow() {
        let rules = makeRules()
        coldStart(rules)
        for _ in 0..<8 {
            away(rules, millis: thirty)
        }
        XCTAssertEqual(store.value, 9)
        tapAndSettle(rules)
        XCTAssertEqual(rules.evaluate(calmSituation()), .no)

        away(rules, millis: thirty)
        XCTAssertEqual(store.value, 10)
        tapAndSettle(rules)

        XCTAssertEqual(rules.evaluate(calmSituation()), .show)
    }

    func testOneAppearancePerSessionThenAnotherSessionOnlyWhenTheCounterIsFullAgain() {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        tapAndSettle(rules)
        XCTAssertEqual(rules.evaluate(calmSituation()), .show)
        rules.onLaunched()
        XCTAssertEqual(rules.evaluate(calmSituation()), .no, "aucune seconde ouverture pendant la première")
        rules.onPaywallDisplayed()
        XCTAssertEqual(store.value, 0)

        // La même session, quoi qu'il arrive ensuite : rien de plus.
        tapAndSettle(rules)
        XCTAssertEqual(rules.evaluate(calmSituation()), .no)

        // Une nouvelle session dont le compteur n'est pas plein : rien.
        away(rules, millis: thirty)
        tapAndSettle(rules)
        XCTAssertEqual(store.value, 1)
        XCTAssertEqual(rules.evaluate(calmSituation()), .no)

        // Une nouvelle session dont le compteur est plein : elle peut revenir.
        store.value = 9
        away(rules, millis: thirty)
        tapAndSettle(rules)
        XCTAssertEqual(rules.evaluate(calmSituation()), .show)
    }

    func testAnOfferDueButNeverShownStaysDueForTheNextSessionAndTheCounterIsNotReset() {
        let rules = makeRules(counter: 9)
        coldStart(rules)

        // Aucun appui dans cette session.
        away(rules, millis: thirty)
        XCTAssertEqual(store.value, 10)
        tapAndSettle(rules)

        XCTAssertEqual(rules.evaluate(calmSituation()), .show)
        XCTAssertEqual(store.value, 10)
    }

    // MARK: - PP11 : la remise à zéro à l'affichage

    func testTheCounterGoesToZeroAtTheDisplayNotBefore() {
        let rules = makeRules(counter: 9)
        coldStart(rules)
        tapAndSettle(rules)
        XCTAssertEqual(rules.evaluate(calmSituation()), .show)

        rules.onLaunched()
        XCTAssertEqual(store.value, 10, "pas encore : l'offre n'est pas à l'écran")
        rules.onPaywallDisplayed()

        XCTAssertEqual(store.value, 0)
    }

    func testAnOfferOpenedByThePersonResetsTheCounterAndCountsAsShownInTheSession() {
        // Un 4e lieu, une 2e Miff, une zone de danger ou l'écran Abonnement : le même appel, à l'affichage.
        let rules = makeRules(counter: 4)
        coldStart(rules)
        rules.onPaywallDisplayed()
        XCTAssertEqual(store.value, 0)

        store.value = 10
        tapAndSettle(rules)

        XCTAssertEqual(rules.evaluate(calmSituation()), .no, "elle vient de voir l'offre : pas deux fois")
    }
}
