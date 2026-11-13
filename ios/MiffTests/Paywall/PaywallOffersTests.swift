import XCTest
@testable import Miff

/// Les prix (toujours ceux du store, jamais écrits à la main) et le bloc d'information obligatoire (ticket `paywall-v2.md` §5.4 ;
/// critères PW4 à PW6, PW2).
final class PaywallOffersTests: XCTestCase {
    private let french = Locale(identifier: "fr_FR")
    private let american = Locale(identifier: "en_US")
    private let trial24h = L10n.string("ios_paywall_info_cancel_trial")

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

    // MARK: - Bloc d'information (PW6)

    private func paragraphs(_ offers: PaywallOffers, _ layout: PaywallLayout) -> [String] {
        PaywallInformation.paragraphs(offers: offers, layout: layout, storeName: "App Store", locale: french)
    }

    func testTheTrialParagraphsNameTheBilledPriceTheRenewalAndTheCancellation() {
        let text = paragraphs(PaywallFixtures.withTrial, .trial).map(normalized)

        XCTAssertEqual(text.count, 2, "l'essai, puis l'annulation")
        XCTAssertTrue(text[0].contains("59,88 €"), "le prix facturé après l'essai")
        XCTAssertFalse(text[0].contains("4,99"), "l'équivalent mensuel n'est pas le prix annoncé ici")
        XCTAssertFalse(text.joined().contains("6,99"), "le mensuel n'est pas décrit dans la disposition de l'essai")
        XCTAssertTrue(text[1].contains("App Store"), "le nom du store")
        XCTAssertTrue(text[1].contains(trial24h), "iOS : 24 heures avant la fin de l'essai")
    }

    func testThePlansParagraphsDescribeBothPlansWithoutTheTrialWhenThereIsNone() {
        let text = paragraphs(PaywallFixtures.withoutTrial, .plans).map(normalized)

        XCTAssertEqual(text.count, 3, "annuel, mensuel, annulation")
        XCTAssertTrue(text[0].contains("59,88 €"))
        XCTAssertTrue(text[1].contains("6,99 €"))
        XCTAssertTrue(text[2].contains("App Store"))
        XCTAssertFalse(text.joined().contains(trial24h), "pas d'essai : pas de « 24 heures »")
    }

    func testAnEligibleAccountThatOpenedAllOffersStillReadsAboutItsTrial() {
        let text = paragraphs(PaywallFixtures.withTrial, .plans).map(normalized)

        XCTAssertEqual(text.count, 3)
        let trialOnly = paragraphs(PaywallFixtures.withTrial, .trial).map(normalized)
        XCTAssertEqual(text[0], trialOnly[0], "le même paragraphe d'essai")
        XCTAssertTrue(text[1].contains("6,99 €"))
        XCTAssertTrue(text[2].contains(trial24h))
    }

    func testTheFreePlanSentenceUsesTheLimitReallyApplied() {
        let text = PaywallInformation.freePlanSentence(freePlaces: 7)

        XCTAssertTrue(text.contains("7"), "jamais un nombre figé dans le texte : il suit la limite de l'app")
        XCTAssertNotEqual(text, "paywall_free_plan_stays")
        XCTAssertTrue(PaywallInformation.freePlanSentence().contains(String(PlaceLimits.freePlaces)))
    }

    func testTheStoreNameIsTheAppStoreOnIos() {
        XCTAssertEqual(L10n.string("ios_paywall_store_name"), "App Store")
    }
}
