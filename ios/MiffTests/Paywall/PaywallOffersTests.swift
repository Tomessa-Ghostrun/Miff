import XCTest
@testable import Miff

/// Les prix (toujours ceux du store, jamais écrits à la main) et l'information sous l'offre (ticket `paywall-v2.md` §5.4 ; critères PW4 à PW6, PW2 ; retours du PM du
/// 2026-11-25 reproduits sur iPhone : UNE phrase sous l'essai, plus de paragraphe « comment annuler » ni de « la formule gratuite reste disponible »).
final class PaywallOffersTests: XCTestCase {
    private let french = Locale(identifier: "fr_FR")
    private let american = Locale(identifier: "en_US")

    private func normalized(_ text: String) -> String {
        text.replacingOccurrences(of: "\u{00A0}", with: " ").replacingOccurrences(of: "\u{202F}", with: " ")
    }

    // MARK: - Prix

    func testThePricesAreWrittenByTheSystemFormatterInTheLanguageOfThePhone() {
        XCTAssertEqual(normalized(PaywallPriceFormat.string(PaywallFixtures.annual, locale: french)), "59,88 €")
        XCTAssertEqual(normalized(PaywallPriceFormat.string(PaywallFixtures.monthly, locale: french)), "6,99 €")
        let dollars = PaywallPrice(amount: Decimal(5988) / 100, currencyCode: "USD")
        XCTAssertEqual(PaywallPriceFormat.string(dollars, locale: american), "$59.88")
    }

    func testTheMonthlyEquivalentIsComputedFromTheAnnualPriceOfTheStore() {
        let equivalent = PaywallPriceFormat.monthlyEquivalent(ofAnnual: PaywallFixtures.annual)

        XCTAssertEqual(equivalent.amount, Decimal(499) / 100)
        XCTAssertEqual(equivalent.currencyCode, "EUR")
        XCTAssertEqual(normalized(PaywallPriceFormat.string(equivalent, locale: french)), "4,99 €")
    }

    func testAnotherStoreTierGivesAnotherEquivalentRoundedToTheCent() {
        let tier = PaywallPrice(amount: Decimal(4999) / 100, currencyCode: "EUR")

        XCTAssertEqual(PaywallPriceFormat.monthlyEquivalent(ofAnnual: tier).amount, Decimal(417) / 100)
    }

    // MARK: - Dispositions (PW4, PW5)

    func testAnEligibleAccountStartsOnTheTrialLayoutAndSeeAllOffersOpensThePlans() {
        XCTAssertEqual(PaywallLayout.of(PaywallFixtures.withTrial, showsAllOffers: false), .trial)
        XCTAssertEqual(PaywallLayout.of(PaywallFixtures.withTrial, showsAllOffers: true), .plans)
    }

    func testAnAccountWithoutTheTrialNeverSeesTheTrialLayout() {
        XCTAssertEqual(PaywallLayout.of(PaywallFixtures.withoutTrial, showsAllOffers: false), .plans)
        XCTAssertEqual(PaywallLayout.of(PaywallFixtures.withoutTrial, showsAllOffers: true), .plans)
    }

    // MARK: - Information sous l'offre (PW6)

    private func paragraphs(_ offers: PaywallOffers, _ layout: PaywallLayout) -> [String] {
        PaywallInformation.paragraphs(offers: offers, layout: layout, locale: french).map(normalized)
    }

    func testTheTrialViewHasOneSingleSentenceWithTheBilledPriceAndTheRenewal() {
        let text = paragraphs(PaywallFixtures.withTrial, .trial)

        XCTAssertEqual(text.count, 1, "une seule phrase sous les cartes (retour du PM)")
        XCTAssertTrue(text[0].contains("59,88 €"), "le prix facturé après l'essai, celui du store")
        XCTAssertFalse(text[0].contains("4,99"), "l'équivalent mensuel n'est pas le prix annoncé ici")
        XCTAssertFalse(text[0].contains("6,99"), "le mensuel n'est pas décrit dans la vue de l'essai")
    }

    func testTheViewsWithoutTheTrialHaveOneSentencePerPlan() {
        for offers in [PaywallFixtures.withoutTrial, PaywallFixtures.withTrial] {
            let text = paragraphs(offers, .plans)

            XCTAssertEqual(text.count, 2, "annuel, mensuel : plus rien d'autre")
            XCTAssertTrue(text[0].contains("59,88 €"))
            XCTAssertTrue(text[1].contains("6,99 €"))
        }
    }

    func testTheSentencesAreTheOnesOfTheCatalogWithThePriceOfTheStoreAsTheirOnlyValue() {
        let price = PaywallPriceFormat.string(PaywallFixtures.annual, locale: french)

        XCTAssertEqual(
            PaywallInformation.paragraphs(offers: PaywallFixtures.withTrial, layout: .trial, locale: french),
            [L10n.format("paywall_info_trial_short", price)]
        )
    }

    func testThereIsNoCancellationParagraphAnyMore() {
        for offers in [PaywallFixtures.withTrial, PaywallFixtures.withoutTrial] {
            for layout in [PaywallLayout.trial, .plans] {
                for sentence in paragraphs(offers, layout) {
                    // L'ancienne clé `ios_paywall_info_cancel_trial` (« Pour un essai, au moins 24 heures avant sa fin. ») n'existe plus.
                    let mentionsTheDelay = sentence.contains("24 heures") || sentence.contains("24 hours")
                    XCTAssertFalse(mentionsTheDelay, "plus de « 24 heures avant la fin »")
                    XCTAssertFalse(sentence.contains("App Store"), "plus de « annule dans les réglages de ton compte »")
                }
            }
        }
    }

    func testTheStoreNameIsTheAppStoreOnIos() {
        XCTAssertEqual(L10n.string("ios_paywall_store_name"), "App Store")
    }
}
