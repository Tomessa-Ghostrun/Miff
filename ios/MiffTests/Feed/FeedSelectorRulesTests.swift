import XCTest
@testable import Miff

/// Le sélecteur de Miff de l'en-tête du Feed (décision du PM, 2026-11-08, qui remplace D12 / FPR Q2) : qui le voit, ce que fait un
/// toucher sur « Rejoindre » ou « Créer » selon la formule (gratuite, Miff+, inconnue), et l'offre d'une 2e Miff.
final class FeedSelectorRulesTests: XCTestCase {
    private let family = MiffSummary(id: "miffA", name: "Famille")
    private let friends = MiffSummary(id: "miffB", name: "Amis")

    private func ready(
        _ miffs: [MiffSummary],
        premium: Bool,
        premiumKnown: Bool = true,
        fromCache: Bool = false
    ) -> DirectoryReady {
        var value = DirectoryReady(miffs: miffs, activeId: miffs.first?.id, premium: premium, fromCache: fromCache)
        value.premiumKnown = premiumKnown
        return value
    }

    // MARK: - Qui voit quoi

    func testAFreeAccountWithOneMiffGetsAnOpenableSelector() {
        let state = FeedPillState.make(from: ready([family], premium: false))

        XCTAssertEqual(state, .selectable(name: "Famille", miffs: [family], activeId: "miffA"))
        XCTAssertTrue(FeedSelectorRules.isOpenable(state))
    }

    func testEveryFormulaGetsAnOpenableSelectorWithItsMiffs() {
        let formulas: [DirectoryReady] = [
            ready([family, friends], premium: false), // gratuit
            ready([family, friends], premium: true), // Miff+
            ready([family, friends], premium: false, premiumKnown: false), // abonnement non lu
            ready([family, friends], premium: false, premiumKnown: false, fromCache: true) // cache (hors connexion)
        ]
        for directory in formulas {
            let state = FeedPillState.make(from: directory)
            XCTAssertEqual(state, .selectable(name: "Famille", miffs: [family, friends], activeId: "miffA"))
            XCTAssertTrue(FeedSelectorRules.isOpenable(state))
        }
    }

    func testWithoutAMiffThePillIsFlatAndNotOpenable() {
        let state = FeedPillState.make(from: ready([], premium: false))

        XCTAssertEqual(state, .noMiff)
        XCTAssertFalse(FeedSelectorRules.isOpenable(state))
    }

    func testNothingIsDrawnBeforeTheDirectoryIsRead() {
        XCTAssertNil(FeedPillState.make(from: nil))
        XCTAssertFalse(FeedSelectorRules.isOpenable(nil))
    }

    func testTheActiveMiffIsMarkedWithATickAndTheOthersAreNot() {
        XCTAssertEqual(FeedSelectorRules.rowTitle(name: "Famille", isActive: true), "\u{2713} Famille")
        XCTAssertEqual(FeedSelectorRules.rowTitle(name: "Amis", isActive: false), "Amis")
    }

    // MARK: - Le toucher sur « Rejoindre » ou « Créer »

    func testAFreeAccountWithAMiffGetsThePaywall() {
        let outcome = FeedSelectorRules.outcome(for: ready([family], premium: false))

        XCTAssertEqual(outcome, .openPaywall)
    }

    func testAMiffPlusAccountGetsTheNormalFlow() {
        XCTAssertEqual(FeedSelectorRules.outcome(for: ready([family], premium: true)), .openFlow)
        XCTAssertEqual(FeedSelectorRules.outcome(for: ready([family, friends], premium: true)), .openFlow)
    }

    func testAnUnknownFormulaNeverCountsAsFreeTheServerDecides() {
        // Abonnement non lu, ou réponse du cache : `premium` vaut faux par défaut, ce qui ne veut pas dire « gratuit ».
        XCTAssertEqual(FeedSelectorRules.outcome(for: ready([family], premium: false, premiumKnown: false)), .openFlow)
        XCTAssertEqual(
            FeedSelectorRules.outcome(for: ready([family], premium: false, premiumKnown: false, fromCache: true)),
            .openFlow
        )
    }

    func testTheFormulaIsReadFromTheDirectory() {
        XCTAssertEqual(FeedSelectorFormula(ready([family], premium: false)), .free)
        XCTAssertEqual(FeedSelectorFormula(ready([family], premium: true)), .premium)
        XCTAssertEqual(FeedSelectorFormula(ready([family], premium: false, premiumKnown: false)), .unknown)
        // Même si `premium` vaut vrai, une réponse non connue reste inconnue.
        XCTAssertEqual(FeedSelectorFormula(ready([family], premium: true, premiumKnown: false)), .unknown)
    }

    func testTheOutcomeTable() {
        XCTAssertEqual(FeedSelectorRules.outcome(formula: .free, miffCount: 1), .openPaywall)
        XCTAssertEqual(FeedSelectorRules.outcome(formula: .free, miffCount: 2), .openPaywall)
        // Gratuit sans Miff (jamais atteint : le sélecteur n'existe pas alors) : rien à bloquer.
        XCTAssertEqual(FeedSelectorRules.outcome(formula: .free, miffCount: 0), .openFlow)
        XCTAssertEqual(FeedSelectorRules.outcome(formula: .premium, miffCount: 1), .openFlow)
        XCTAssertEqual(FeedSelectorRules.outcome(formula: .unknown, miffCount: 1), .openFlow)
    }

    func testNoDirectoryOpensTheNormalFlow() {
        XCTAssertEqual(FeedSelectorRules.outcome(for: nil), .openFlow)
    }

    // MARK: - L'offre d'une 2e Miff

    func testThePaywallOfASecondMiffHasItsOwnTextsInBothLanguages() {
        let reason = PaywallReason.extraMiff

        for key in [reason.titleKey, reason.bodyKey, reason.soonBodyKey] {
            XCTAssertNotEqual(L10n.string(key), key, "\(key) n'a pas de texte")
        }
        XCTAssertEqual(reason.titleKey, "feed_selector_paywall_title")
        XCTAssertEqual(reason.bodyKey, "feed_selector_paywall_body")
        XCTAssertEqual(reason.soonBodyKey, "feed_selector_paywall_soon_body")
    }

    func testTheOtherReasonsKeepTheirTexts() {
        XCTAssertEqual(PaywallReason.limit.titleKey, "place_paywall_limit_title")
        XCTAssertEqual(PaywallReason.limit.bodyKey, "place_paywall_limit_body")
        XCTAssertEqual(PaywallReason.danger.titleKey, "place_paywall_danger_title")
        XCTAssertEqual(PaywallReason.danger.bodyKey, "place_paywall_danger_body")
        XCTAssertEqual(PaywallReason.limit.soonBodyKey, "place_paywall_soon_body")
        XCTAssertEqual(PaywallReason.danger.soonBodyKey, "place_paywall_soon_body")
    }

    func testNoTextOfTheOfferSaysPremiumNorShowsAPrice() {
        for key in [
            "feed_selector_paywall_title", "feed_selector_paywall_body", "feed_selector_paywall_soon_body",
            "feed_selector_join", "feed_selector_create", "feed_selector_a11y"
        ] {
            let text = L10n.string(key).lowercased()
            XCTAssertFalse(text.contains("premium"), "\(key) : jamais « Premium » dans un texte affiché")
            XCTAssertFalse(text.contains("€"), "\(key) : aucun prix")
        }
    }

    func testTheSecondMiffOfferIsCountedWithItsOwnTriggerOncePerAppearance() {
        XCTAssertEqual(PlacePaywall.shown(for: .extraMiff), .paywallShown(trigger: .extraMiff))
        XCTAssertEqual(PlacePaywall.dismissed(for: .extraMiff), .paywallDismissed(trigger: .extraMiff))

        let tracking = PlacePaywallTracking(reason: .extraMiff)
        XCTAssertNil(tracking.disappeared(), "jamais « fermée » sans avoir été « ouverte »")
        XCTAssertEqual(tracking.appeared(), .paywallShown(trigger: .extraMiff))
        XCTAssertNil(tracking.appeared())
        XCTAssertEqual(tracking.disappeared(), .paywallDismissed(trigger: .extraMiff))
        XCTAssertNil(tracking.disappeared())
    }

    func testTheSecondMiffOfferEventPassesTheAnalyticsAllowList() throws {
        let event = try XCTUnwrap(PlacePaywall.shown(for: .extraMiff))

        XCTAssertEqual(event.params.keys.sorted(), ["trigger"])
        XCTAssertEqual(AnalyticsContract.validate(event.name, params: event.params), .allowed)
    }

    func testTheOfferRouteDependsOnNoMiff() {
        let route = MiffRoute.placePaywall(.extraMiff)

        XCTAssertFalse(route.belongs(to: "miffA"), "l'offre ne part pas avec une perte d'accès")
        XCTAssertFalse(route.isSettingsScreen)
    }
}
