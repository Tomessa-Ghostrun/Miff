import XCTest
@testable import Miff

/// Les textes de l'écran d'offre (ticket `paywall-v2.md` §5.5 ; critères PW2, PW3, PW8, PW14) : présents en français et en anglais,
/// au tutoiement, sans prix, sans devise, sans « Premium », sans ton alarmiste.
final class PaywallTextsTests: XCTestCase {
    /// Les clés de l'écran d'offre, avec le nombre de valeurs (`%1$@` ou `%1$lld`) que leur texte reçoit.
    private let keys: [String: Int] = [
        "paywall_cta_trial": 0, "paywall_trial_then": 1, "paywall_monthly_equivalent": 1, "paywall_annual_savings": 0,
        "paywall_cancel_anytime": 0, "paywall_see_all_offers": 0, "paywall_free_plan_stays": 1,
        "paywall_close_available_a11y": 0, "paywall_loading": 0, "paywall_unavailable": 0, "paywall_retry": 0,
        "paywall_pending": 0, "paywall_error_calm": 0, "paywall_restore": 0, "paywall_restore_none": 1,
        "paywall_subscribed": 0, "paywall_subscribed_detail": 0, "paywall_continue": 0, "paywall_working": 0,
        "paywall_plan_annual_title": 1, "paywall_plan_monthly_title": 1, "paywall_no_commitment": 0,
        "paywall_info_trial": 1, "paywall_info_annual": 1, "paywall_info_monthly": 1, "paywall_info_cancel": 1,
        "paywall_terms_link": 0, "paywall_privacy_link": 0, "ios_paywall_store_name": 0,
        "ios_paywall_info_cancel_trial": 0
    ]

    private func value(_ key: String, _ language: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Catalogue source introuvable (\(url.path)).")
        }
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])
        let entry = try XCTUnwrap(strings[key], "\(key) absent du catalogue")
        let localization = (entry["localizations"] as? [String: Any])?[language] as? [String: Any]
        let unit = localization?["stringUnit"] as? [String: Any]
        return try XCTUnwrap(unit?["value"] as? String, "\(key) : pas de texte \(language)")
    }

    func testEveryTextExistsInFrenchAndEnglishWithTheSameNumberOfValues() throws {
        for (key, expected) in keys {
            for language in ["fr", "en"] {
                let text = try value(key, language)
                XCTAssertFalse(text.isEmpty, "\(key) \(language)")
                let count = text.components(separatedBy: "%1$").count - 1
                XCTAssertEqual(count, expected, "\(key) \(language) : nombre de valeurs")
            }
        }
    }

    func testNoTextWritesAPriceACurrencyOrTheWordPremium() throws {
        let forbidden = try NSRegularExpression(pattern: "€|\\$|\\beur\\b|\\busd\\b|premium", options: .caseInsensitive)
        for key in keys.keys {
            for language in ["fr", "en"] {
                // Les valeurs insérées (`%1$@`, `%1$lld`) ne sont pas un signe de dollar : on les retire avant de chercher.
                let text = try value(key, language).replacingOccurrences(of: "%1$", with: "")
                let range = NSRange(text.startIndex..., in: text)
                XCTAssertNil(forbidden.firstMatch(in: text, range: range), "\(key) \(language) : prix ou Premium")
            }
        }
    }

    func testTheOnlyPercentageWrittenInATextIsTheDecidedAlmostThirty() throws {
        for key in keys.keys where key != "paywall_annual_savings" {
            for language in ["fr", "en"] {
                XCTAssertFalse(try value(key, language).contains("%\u{0020}"), "\(key) : un pourcentage en dur ?")
            }
        }
        XCTAssertEqual(try value("paywall_annual_savings", "fr"), "presque 30 % d'économie")
        XCTAssertEqual(try value("paywall_annual_savings", "en"), "almost 30% off")
    }

    func testTheFrenchTextsAddressThePersonWithTu() throws {
        for key in ["paywall_unavailable", "paywall_pending", "paywall_error_calm", "paywall_info_cancel"] {
            let text = try value(key, "fr").lowercased()
            let formal = text.contains("vous") || text.contains("votre") || text.contains("vos ")
            XCTAssertFalse(formal, "\(key) : tutoiement")
        }
    }

    func testTheCalmMessagesAreNeverAlarming() throws {
        for key in ["paywall_unavailable", "paywall_pending", "paywall_error_calm", "paywall_restore_none"] {
            for language in ["fr", "en"] {
                let text = try value(key, language)
                XCTAssertFalse(text.contains("!"), "\(key) \(language) : pas de point d'exclamation")
            }
        }
    }

    func testTheComingSoonTextNeverMentionsAPriceOrPremium() {
        for key in ["paywall_soon_body", "place_paywall_soon"] {
            let text = L10n.string(key).lowercased()
            XCTAssertNotEqual(text, key, "\(key) n'a pas de texte")
            XCTAssertFalse(text.contains("premium") || text.contains("€"), "\(key) : PW3")
        }
    }
}
