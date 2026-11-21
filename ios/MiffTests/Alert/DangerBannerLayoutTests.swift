// Les assertions citent des lignes de code et des textes : elles dépassent la limite.
// swiftlint:disable line_length
#if ALERT_DANGER
import SwiftUI
import UIKit
import XCTest
@testable import Miff

/// Le bandeau de l'alerte en cours et la bulle bleue aux GRANDES tailles de texte, avec un nom de Miff long (calcul avec les vraies polices et les vrais textes,
/// comme `IncentiveBubbleLayoutTests`), et la règle du lien « Voir la Miff {nom} ». Un iPhone de 375 x 667 pt (le plus petit courant).
final class DangerBannerLayoutTests: XCTestCase {
    private static let screen = CGSize(width: 375, height: 667)
    /// 40 unités UTF-16 sans espace : le nom de Miff le plus long (data-model §2.5).
    private static let longName = String(repeating: "Miff", count: 10)

    private func text(_ key: String, _ language: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Alert
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])
        let localizations = try XCTUnwrap(strings[key]?["localizations"] as? [String: [String: Any]], key)
        let unit = try XCTUnwrap(localizations[language]?["stringUnit"] as? [String: Any], "\(key) \(language)")
        return try XCTUnwrap(unit["value"] as? String)
    }

    // MARK: - Mesures

    private struct Scaled {
        let font: UIFont
        let lineHeight: CGFloat
    }

    /// Ce que fait `miffTextStyle` : la taille ET l'interligne suivent la courbe du style système.
    private func scaled(_ token: TextStyleToken, _ style: UIFont.TextStyle, _ category: UIContentSizeCategory) -> Scaled {
        let traits = UITraitCollection(preferredContentSizeCategory: category)
        let metrics = UIFontMetrics(forTextStyle: style)
        return Scaled(
            font: token.face.uiFont(size: metrics.scaledValue(for: token.size, compatibleWith: traits)),
            lineHeight: metrics.scaledValue(for: token.lineHeight, compatibleWith: traits)
        )
    }

    private func lines(_ text: String, _ style: Scaled, width: CGFloat) -> CGFloat {
        let rect = (text as NSString).boundingRect(
            with: CGSize(width: width, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin],
            attributes: [.font: style.font],
            context: nil
        )
        return max((rect.height / style.font.lineHeight).rounded(.up), 1)
    }

    private func oneLineWidth(_ text: String, _ style: Scaled) -> CGFloat {
        (text as NSString).size(withAttributes: [.font: style.font]).width
    }

    /// La hauteur du bandeau d'une alerte en cours, avec le lien « Voir la Miff {nom} », à la taille de texte `category` (celle que la vue plafonne).
    private func bannerHeight(_ category: UIContentSizeCategory, language: String) throws -> CGFloat {
        let inner = Self.screen.width - 4 * Spacing.md // marge extérieure + remplissage, de chaque côté
        let title = scaled(textAlertBannerTitle, .title1, category)
        let body = scaled(textBodyRegular14, .callout, category)
        let action = scaled(textBodyBold14, .callout, category)
        let titleHeight = lines(try text("alert_banner_title", language), title, width: inner) * title.lineHeight
        let bodyHeight = lines(try text("alert_banner_body", language), body, width: inner) * body.lineHeight
        let callText = try text("alert_banner_call", language)
        let linkText = String(format: try text("alert_banner_see_miff", language), Self.longName)
        let callHeight = max(Spacing.minTouchTarget, lines(callText, action, width: inner) * action.lineHeight)
        let linkHeight = max(Spacing.minTouchTarget, lines(linkText, action, width: inner) * action.lineHeight)
        let sideBySide = oneLineWidth(callText, action) + Spacing.md + oneLineWidth(linkText, action) <= inner
        let actionsHeight = sideBySide ? max(callHeight, linkHeight) : callHeight + linkHeight
        return 2 * (Spacing.sm + Spacing.xs) + titleHeight + Spacing.xs + bodyHeight + Spacing.xs + actionsHeight
    }

    func testTheBannerWithALongMiffNameStaysCompactAtTheDefaultSize() throws {
        for language in ["fr", "en"] {
            let height = try bannerHeight(.large, language: language)
            XCTAssertLessThanOrEqual(height, 0.45 * Self.screen.height, "\(language) : hauteur calculée \(height)")
        }
    }

    /// La vue plafonne TOUT le bandeau à `DangerBannerView.maxDynamicType` (accessibility1) : au-delà, la hauteur ne grandit plus.
    func testTheBannerWithALongMiffNameStaysUsableAtTheLargestSizeItFollows() throws {
        XCTAssertEqual(DangerBannerView.maxDynamicType, .accessibility1)
        for language in ["fr", "en"] {
            let height = try bannerHeight(.accessibilityMedium, language: language)
            XCTAssertLessThanOrEqual(height, 0.8 * Self.screen.height, "\(language) : hauteur calculée \(height)")
        }
    }

    // MARK: - Le lien « Voir la Miff {nom} »

    func testTheLinkIsShownAsSoonAsThePersonHasSeveralMiffsEvenWhenTheFeedShowsTheOriginOne() {
        XCTAssertTrue(DangerBannerRules.showsSeeMiff(originMiffId: "A", activeMiffId: "A", miffCount: 2, nameKnown: true))
        XCTAssertTrue(DangerBannerRules.showsSeeMiff(originMiffId: "A", activeMiffId: "B", miffCount: 2, nameKnown: true))
        XCTAssertTrue(DangerBannerRules.showsSeeMiff(originMiffId: "A", activeMiffId: "B", miffCount: 1, nameKnown: true), "le Feed en montre une autre")
    }

    func testTheLinkIsNotShownWithASingleMiffNorWithoutAName() {
        XCTAssertFalse(DangerBannerRules.showsSeeMiff(originMiffId: "A", activeMiffId: "A", miffCount: 1, nameKnown: true))
        XCTAssertFalse(DangerBannerRules.showsSeeMiff(originMiffId: "A", activeMiffId: "A", miffCount: 3, nameKnown: false), "jamais un trou à la place du nom")
        XCTAssertFalse(DangerBannerRules.showsSeeMiff(originMiffId: "A", activeMiffId: nil, miffCount: 1, nameKnown: false))
    }

    func testTheBannerSlotUsesTheRule() throws {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Miff/Views/Alert/DangerAlertHost.swift")
        let code = try String(contentsOf: url, encoding: .utf8)

        XCTAssertTrue(code.contains("DangerBannerRules.showsSeeMiff("))
        XCTAssertTrue(code.contains("miffCount: model.miffNames.count"))
        XCTAssertFalse(code.contains("showSeeMiff: origin != activeMiffId"), "l'ancienne règle (seulement quand le Feed montre une autre Miff)")
    }

    // MARK: - La bulle bleue aux grandes tailles

    private func hintContentHeight(_ category: UIContentSizeCategory) throws -> CGFloat {
        let inner = Self.screen.width - 2 * Spacing.lg - 2 * Spacing.md // marge du viseur + remplissage de la bulle
        let line = scaled(textBodyRegular14, .callout, category)
        let more = scaled(textBodyBold14, .callout, category)
        let tap = "• " + (try text("alert_hint_tap", "fr"))
        let hold = "• " + (try text("alert_hint_hold", "fr"))
        let linesHeight = (lines(tap, line, width: inner) + lines(hold, line, width: inner)) * line.lineHeight
        let moreHeight = max(Spacing.minTouchTarget, lines(try text("alert_hint_more", "fr"), more, width: inner) * more.lineHeight)
        return 2 * (Spacing.sm + Spacing.xs) + linesHeight + Spacing.xs + moreHeight
    }

    func testTheHintBubbleIsCompactByDefaultAndScrollsInsideAtTheLargestSizes() throws {
        let cap = Self.screen.height * CGFloat(DangerAlertRules.hintMaxScreenShare)

        let normal = try hintContentHeight(.large)
        XCTAssertFalse(IncentiveBubbleLayout.scrolls(content: normal, maxBodyHeight: cap), "compacte à la taille par défaut : \(normal)")

        let huge = try hintContentHeight(.accessibilityExtraExtraExtraLarge)
        XCTAssertTrue(IncentiveBubbleLayout.scrolls(content: huge, maxBodyHeight: cap), "défile dedans aux plus grandes tailles : \(huge)")
        XCTAssertEqual(IncentiveBubbleLayout.bodyHeight(content: huge, maxBodyHeight: cap), cap, "jamais plus que la limite")
    }

    func testTheHintLimitIsAboutFortyFivePercentOfTheScreenAndIsPassedByTheViewfinder() throws {
        XCTAssertEqual(DangerAlertRules.hintMaxScreenShare, 0.45, accuracy: 0.0001)
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let support = try String(contentsOf: root.appendingPathComponent("Miff/Views/Alert/DangerViewfinderSupport.swift"), encoding: .utf8)
        let viewfinder = try String(contentsOf: root.appendingPathComponent("Miff/Views/Update/ViewfinderView.swift"), encoding: .utf8)

        XCTAssertTrue(support.contains("maxHeight: screenHeight * CGFloat(DangerAlertRules.hintMaxScreenShare)"))
        XCTAssertTrue(viewfinder.contains("DangerHintSlot(state: dangerState, hooks: hooks, screenHeight: screenHeight)"))
    }
}
#endif
// swiftlint:enable line_length
