// Les assertions citent des lignes de code et des textes : elles dépassent la limite.
// swiftlint:disable line_length
#if ALERT_DANGER
import SwiftUI
import UIKit
import XCTest
@testable import Miff

/// Retour du PM (recette) : le bandeau rouge de l'alerte en cours hiérarchise l'information. Le TITRE est DEUX FOIS plus gros qu'avant (16 pt -> 32 pt),
/// « Mettre fin à l'alerte ? » ; la consigne qui suit est donnée telle quelle par le PM. Le bouton « Appeler » ne change pas.
final class DangerBannerTitleTests: XCTestCase {
    private func catalog() throws -> [String: [String: Any]] {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Alert
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        return try XCTUnwrap(root["strings"] as? [String: [String: Any]])
    }

    private func value(_ key: String, _ language: String) throws -> String {
        let localizations = try XCTUnwrap(catalog()[key]?["localizations"] as? [String: [String: Any]], key)
        let unit = try XCTUnwrap(localizations[language]?["stringUnit"] as? [String: Any], "\(key) \(language)")
        return try XCTUnwrap(unit["value"] as? String)
    }

    // MARK: - Les textes (clés existantes, texte modifié)

    func testTheFrenchTextsAreTheOnesGivenByThePM() throws {
        XCTAssertEqual(try value("alert_banner_title", "fr"), "Mettre fin à l'alerte ?")
        XCTAssertEqual(
            try value("alert_banner_body", "fr"),
            "Maintiens 5 secondes pour mettre fin à l'alerte, tes proches sauront que tu es désormais en sécurité"
        )
    }

    func testTheEnglishTextsSayTheSameThing() throws {
        XCTAssertEqual(try value("alert_banner_title", "en"), "End the alert?")
        XCTAssertEqual(
            try value("alert_banner_body", "en"),
            "Hold for 5 seconds to end the alert. Your loved ones will know you're now safe"
        )
    }

    func testTheTitleCarriesNoMiffNameAnymoreSoNoValueIsInserted() throws {
        for language in ["fr", "en"] {
            XCTAssertFalse(try value("alert_banner_title", language).contains("%"), language)
            XCTAssertFalse(try value("alert_banner_title_no_name", language).contains("%"), language)
        }
    }

    func testTheCallButtonIsUnchanged() throws {
        XCTAssertEqual(try value("alert_banner_call", "fr"), "Appeler")
        XCTAssertEqual(try value("alert_banner_call", "en"), "Call")
    }

    // MARK: - La taille

    func testTheTitleIsTwiceAsBigAsBefore() {
        XCTAssertEqual(textBodyBold16.size, 16, "le titre d'avant")
        XCTAssertEqual(textAlertBannerTitle.size, 2 * textBodyBold16.size)
        XCTAssertEqual(textAlertBannerTitle.size, 32)
        XCTAssertEqual(textAlertBannerTitle.face, textBodyBold16.face, "même police, même graisse")
    }

    func testTheTitleCapsItsDynamicTypeBelowTheLargestSizes() {
        XCTAssertLessThan(DangerBannerView.titleMaxDynamicType, .accessibility3)
        XCTAssertGreaterThan(DangerBannerView.titleMaxDynamicType, .xxxLarge, "il grandit encore aux premières tailles d'accessibilité")
    }

    /// Les VRAIS textes à la taille de texte par défaut, sur le plus petit iPhone courant (320 pt) : le titre tient sur peu de lignes et le bandeau reste
    /// une part raisonnable de l'écran (le bouton rond de l'Update se pose au-dessus de lui).
    func testTheRealTextsMakeAReasonableBannerOnTheSmallestPhone() throws {
        let width: CGFloat = 320 - 2 * Spacing.md // la bannière est posée avec une marge de chaque côté
        let inner = width - 2 * Spacing.md // son remplissage
        let titleFont = textAlertBannerTitle.face.uiFont(size: textAlertBannerTitle.size)
        let bodyFont = textBodyRegular14.face.uiFont(size: textBodyRegular14.size)

        for language in ["fr", "en"] {
            let title = try value("alert_banner_title", language) as NSString
            let body = try value("alert_banner_body", language) as NSString
            let titleLines = Self.lines(of: title, font: titleFont, width: inner)
            let bodyLines = Self.lines(of: body, font: bodyFont, width: inner)
            let height = 2 * (Spacing.sm + Spacing.xs)
                + titleLines * textAlertBannerTitle.lineHeight
                + Spacing.xs
                + bodyLines * textBodyRegular14.lineHeight
                + Spacing.xs
                + Spacing.minTouchTarget

            XCTAssertLessThanOrEqual(titleLines, 3, "\(language) : le titre passe à la ligne sans déborder")
            XCTAssertLessThanOrEqual(bodyLines, 6, language)
            XCTAssertLessThanOrEqual(height, 0.5 * 568, "\(language) : au plus la moitié de l'écran, hauteur calculée \(height)")
        }
    }

    private static func lines(of text: NSString, font: UIFont, width: CGFloat) -> CGFloat {
        let rect = text.boundingRect(
            with: CGSize(width: width, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin],
            attributes: [.font: font],
            context: nil
        )
        return max((rect.height / font.lineHeight).rounded(.up), 1)
    }
}
#endif
// swiftlint:enable line_length
