// Quelques assertions portent un message en clair : elles dépassent la limite de ligne.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// Le choix des offres d'après ce que dit le store (ticket `docs/tickets/premium-achat-reel.md` §4, §7 et critères AN1, AN3, AN4) : rien n'est inventé, rien n'est écrit à la main.
final class MiffPlusOfferChoiceTests: XCTestCase {
    private func offers(_ products: [StoreProductInfo]) -> PaywallOffers? {
        MiffPlusOfferChoice.offers(from: products)
    }

    func testTheOffersAreTheTwoPlansOfTheStoreWithTheTrialWhenTheAccountIsEligible() throws {
        let result = try XCTUnwrap(offers([testProduct(), testMonthlyProduct()]))

        XCTAssertTrue(result.trialEligible)
        XCTAssertEqual(PaywallPriceFormat.string(result.annual), "59,88 €", "le texte du store, tel quel")
        XCTAssertEqual(PaywallPriceFormat.string(result.monthly), "6,99 €")
        XCTAssertEqual(
            PaywallPriceFormat.string(PaywallPriceFormat.monthlyEquivalent(ofAnnual: result.annual)),
            "4,99 €",
            "le douzième est écrit par le store dans la même devise"
        )
    }

    func testAnAccountThatAlreadyUsedTheTrialSeesNoTrial() throws {
        let result = try XCTUnwrap(offers([testProduct(eligible: false), testMonthlyProduct()]))

        XCTAssertFalse(result.trialEligible, "le store décide : Miff n'ajoute aucun blocage")
    }

    func testOnlyAFreeTrialOfSevenDaysIsShown() throws {
        let sevenDays = [StorePeriod(unit: .day, value: 7), StorePeriod(unit: .week, value: 1)]
        for period in sevenDays {
            let result = try XCTUnwrap(offers([testProduct(trial: period), testMonthlyProduct()]))
            XCTAssertTrue(result.trialEligible, "\(period)")
        }
        let others: [StorePeriod?] = [nil, StorePeriod(unit: .day, value: 3), StorePeriod(unit: .week, value: 2), StorePeriod(unit: .month, value: 1)]
        for period in others {
            let result = try XCTUnwrap(offers([testProduct(trial: period), testMonthlyProduct()]))
            XCTAssertFalse(result.trialEligible, "\(String(describing: period))")
        }
    }

    func testAPlanThatIsNotThePeriodPromisedIsNotOffered() {
        XCTAssertNil(offers([testProduct(period: .oneMonth), testMonthlyProduct()]), "l'annuel n'est pas d'un an")
        XCTAssertNil(offers([testProduct(), testProduct(id: MiffPlusProducts.monthlyId, period: .oneYear)]))
    }

    func testAMissingOrIncompleteProductMeansNotAvailable() {
        XCTAssertNil(offers([]))
        XCTAssertNil(offers([testProduct()]), "il manque le mensuel")
        XCTAssertNil(offers([testMonthlyProduct()]), "il manque l'annuel")
        XCTAssertNil(offers([testProduct(amount: 0), testMonthlyProduct()]), "un montant nul n'est pas un prix")
        XCTAssertNil(offers([testProduct(display: ""), testMonthlyProduct()]), "pas de texte de prix, pas d'offre")
    }

    func testAnUnknownProductIsIgnored() throws {
        let stranger = testProduct(id: "com.example.other")

        let result = try XCTUnwrap(offers([stranger, testProduct(), testMonthlyProduct()]))

        XCTAssertTrue(result.trialEligible)
    }

    func testTheProductIdentifiersAreTheOnesOfTheTicket() {
        XCTAssertEqual(MiffPlusProducts.annualId, "com.tomessa.miff.plus.annual")
        XCTAssertEqual(MiffPlusProducts.monthlyId, "com.tomessa.miff.plus.monthly")
        XCTAssertEqual(MiffPlusProducts.productId(for: .annual), MiffPlusProducts.annualId)
        XCTAssertEqual(MiffPlusProducts.productId(for: .monthly), MiffPlusProducts.monthlyId)
        XCTAssertEqual(MiffPlusProducts.all.count, 2)
    }

    func testTheManagementPageDependsOnTheStoreWhereItWasBought() {
        XCTAssertEqual(MiffPlusProducts.manageURL(for: .appStore), MiffPlusProducts.appStoreManageURL)
        XCTAssertEqual(MiffPlusProducts.manageURL(for: .googlePlay), MiffPlusProducts.googlePlayManageURL)
        XCTAssertNil(MiffPlusProducts.manageURL(for: .manual), "rien à gérer chez un store pour un accès à la main")
        XCTAssertNil(MiffPlusProducts.manageURL(for: .other))
        XCTAssertEqual(MiffPlusProducts.appStoreManageURL?.host, "apps.apple.com")
    }

    func testTheStoreTextIsPreferredAndTheSystemFormatterIsOnlyTheFallback() {
        let fallback = PaywallPrice(amount: Decimal(5988) / 100, currencyCode: "EUR")
        let fromStore = PaywallPrice(amount: Decimal(5988) / 100, currencyCode: "EUR", displayText: "US$59.88")

        XCTAssertEqual(PaywallPriceFormat.string(fromStore, locale: Locale(identifier: "fr_FR")), "US$59.88")
        XCTAssertNotEqual(PaywallPriceFormat.string(fallback, locale: Locale(identifier: "fr_FR")), "US$59.88")
    }
}
// swiftlint:enable line_length
