// Quelques assertions portent un message en clair : elles dépassent la limite de ligne.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// Les limites de la formule gratuite dites dans les cartes de l'offre (« Limite de 3 lieux sinon », « Limite d'une seule Miff sinon », décision du PM du 2026-11-25), la pastille
/// d'économie qui ne dit que le vrai, et l'écran « Bienvenue sur Miff+ ! » avec ses confettis (retour du PM, reproduit sur iPhone).
final class PaywallLimitsAndWelcomeTests: XCTestCase {
    private let languages = ["fr", "en", "es", "it", "pt", "de"]

    private func bundle(_ language: String) throws -> Bundle {
        let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"), language)
        return try XCTUnwrap(Bundle(path: path), language)
    }

    // MARK: - Les limites dans les cartes

    func testTheNumbersOfTheFreePlanAreThoseTheTextsSpeakOf() {
        XCTAssertEqual(PlaceLimits.freePlaces, 3, "les phrases disent « 3 lieux » par leur argument ; les relire si cela change")
        XCTAssertEqual(PlaceLimits.freeMiffs, 1, "la phrase dit « une seule Miff » dans six langues ; la relire si cela change")
    }

    func testThePlacesLimitTakesItsNumberAsAnArgumentInEveryLanguageNeverWrittenInTheText() throws {
        for language in languages {
            let target = try bundle(language)
            for count in [1, PlaceLimits.freePlaces, 7] {
                let text = L10n.plural("paywall_feature_places_limit", count: count, bundle: target)

                XCTAssertTrue(text.contains(String(count)), "\(language) \(count) : \(text)")
                XCTAssertNotEqual(text, "paywall_feature_places_limit", "\(language) : la clé existe")
            }
        }
    }

    func testTheSingleMiffSentenceAndTheTrialLinkExistInEverySixLanguagesWithoutADigit() throws {
        for language in languages {
            let target = try bundle(language)
            for key in ["paywall_feature_miffs_limit", "paywall_free_trial_link"] {
                let text = L10n.string(key, bundle: target)

                XCTAssertNotEqual(text, key, "\(language) \(key)")
                XCTAssertNil(text.rangeOfCharacter(from: .decimalDigits), "\(language) \(key) : le nombre de Miffs est en lettres")
            }
        }
    }

    func testTheFreeMiffsLimitIsTheOneOfTheGateOfTheSelector() {
        XCTAssertEqual(
            FeedSelectorRules.outcome(formula: .free, miffCount: PlaceLimits.freeMiffs), .openPaywall,
            "une personne gratuite qui a déjà sa Miff voit l'offre"
        )
        XCTAssertEqual(FeedSelectorRules.outcome(formula: .free, miffCount: PlaceLimits.freeMiffs - 1), .openFlow)
        XCTAssertEqual(FeedSelectorRules.outcome(formula: .premium, miffCount: 5), .openFlow)
    }

    // MARK: - La pastille « presque 30 % d'économie »

    private func price(_ cents: Int, _ currency: String = "EUR") -> PaywallPrice {
        PaywallPrice(amount: Decimal(cents) / 100, currencyCode: currency)
    }

    func testTheSavingIsComputedFromTheAmountsOfTheStore() throws {
        let saving = try XCTUnwrap(PaywallSavings.saving(annual: price(5988), monthly: price(699)))

        XCTAssertEqual(saving, 1 - 59.88 / (12 * 6.99), accuracy: 0.0001)
        XCTAssertTrue(PaywallSavings.isWorthShowing(annual: price(5988), monthly: price(699)))
    }

    func testTheLabelIsHiddenWhenTheSavingWouldNotBeTrue() {
        XCTAssertFalse(PaywallSavings.isWorthShowing(annual: price(7000), monthly: price(699)), "moins de 25 %")
        XCTAssertFalse(PaywallSavings.isWorthShowing(annual: price(5988), monthly: price(699, "USD")), "devises différentes")
        XCTAssertFalse(PaywallSavings.isWorthShowing(annual: price(0), monthly: price(699)))
        XCTAssertNil(PaywallSavings.saving(annual: price(5988), monthly: price(0)))
    }

    // MARK: - Les confettis

    func testTheBurstIsFiftySixPiecesAndFixedForAGivenSeed() {
        XCTAssertEqual(Confetti.count, 56)
        XCTAssertEqual(Confetti.duration, 3.2, accuracy: 0.0001)
        XCTAssertEqual(Confetti.pieces().count, 56)
        XCTAssertEqual(Confetti.pieces(seed: 7), Confetti.pieces(seed: 7))
        XCTAssertNotEqual(Confetti.pieces(seed: 7), Confetti.pieces(seed: 8))
    }

    func testEveryPieceStartsInsideItsRanges() {
        for piece in Confetti.pieces() {
            XCTAssertTrue((0..<1).contains(piece.startX))
            XCTAssertTrue((0...0.3).contains(piece.delay))
            XCTAssertTrue((1...1.35).contains(piece.speed))
            XCTAssertTrue((6...12).contains(piece.size))
            XCTAssertTrue((0..<5).contains(piece.colorIndex))
        }
    }

    func testAPieceWaitsForItsDelayFallsFromAboveAndFadesAtTheEnd() throws {
        let piece = try XCTUnwrap(Confetti.pieces().first)

        XCTAssertNil(Confetti.y(of: piece, at: piece.delay), "pas encore commencé")
        XCTAssertEqual(Confetti.alpha(of: piece, at: 0), 0)
        let early = try XCTUnwrap(Confetti.y(of: piece, at: piece.delay + 0.01))
        XCTAssertLessThan(early, 0.1, "elle commence au-dessus de l'écran")
        XCTAssertEqual(Confetti.alpha(of: piece, at: piece.delay + 0.2), 1, "pleinement visible")
        let end = try XCTUnwrap(Confetti.y(of: piece, at: 1))
        XCTAssertGreaterThan(end, 1, "en dessous de l'écran à la fin")
        XCTAssertEqual(Confetti.alpha(of: piece, at: 1), 0, accuracy: 0.0001, "effacée à la fin")
    }

    func testTheRandomGeneratorStaysInsideZeroAndOneAndIsDeterministic() {
        var first = ConfettiRandom(seed: 42)
        var second = ConfettiRandom(seed: 42)
        for _ in 0..<1000 {
            let value = first.next()
            XCTAssertGreaterThanOrEqual(value, 0)
            XCTAssertLessThan(value, 1)
            XCTAssertEqual(value, second.next())
        }
    }

    // MARK: - L'écran de bienvenue

    func testTheWelcomeUsesTheTextsOfThePmAndTheThreeUnlockedThings() {
        XCTAssertEqual(L10n.string("paywall_subscribed", bundle: (try? bundle("fr")) ?? .main), "Bienvenue sur Miff+ !")
        XCTAssertEqual(L10n.string("paywall_subscribed_detail", bundle: (try? bundle("fr")) ?? .main), "Profite de Miff en version illimitée")
        XCTAssertEqual(L10n.string("paywall_unlocked_danger", bundle: (try? bundle("fr")) ?? .main), "Déclarer des zones de danger")
        XCTAssertEqual(L10n.string("paywall_subscribed", bundle: (try? bundle("en")) ?? .main), "Welcome to Miff+!")
    }
}
// swiftlint:enable line_length
