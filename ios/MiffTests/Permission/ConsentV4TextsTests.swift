import XCTest
@testable import Miff

/// CONS-4-I (docs/tickets/consentement-localisation-historique.md, section 10) : les textes du consentement version 4 SUR LES SOURCES, en
/// français et en anglais, et ce qui doit rester vrai à leur sujet (critères CA-1 à CA-5). Aucun texte ne dit « pas d'historique » sans
/// « sur nos serveurs », puisque le téléphone tient un journal local ; aucun ne dit que le journal est effacé quand le partage s'arrête
/// (il ne l'est pas : décision D16). Miroir de `ConsentV4TextsTest` d'Android, plus ce qui est propre à iOS (textes d'usage du système).
final class ConsentV4TextsTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Permission
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    private func catalog(_ name: String) throws -> [String: [String: Any]] {
        let url = iosRoot.appendingPathComponent("Miff/Resources/\(name).xcstrings")
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        return try XCTUnwrap(root["strings"] as? [String: [String: Any]])
    }

    private func texts(_ language: String, in name: String = "Localizable") throws -> [String: String] {
        var result: [String: String] = [:]
        for (key, entry) in try catalog(name) {
            let localizations = entry["localizations"] as? [String: Any]
            let localization = localizations?[language] as? [String: Any]
            let unit = localization?["stringUnit"] as? [String: Any]
            if let value = (unit?["value"] as? String)?.replacingOccurrences(of: "\u{2019}", with: "'") {
                result[key] = value
            }
        }
        return result
    }

    private func androidKeys(_ relativePath: String) throws -> Set<String> {
        let url = iosRoot.deletingLastPathComponent().appendingPathComponent(relativePath)
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Textes d'Android introuvables (\(url.path)).")
        }
        let xml = try String(contentsOf: url, encoding: .utf8)
        let regex = try NSRegularExpression(pattern: "<string name=\"([^\"]+)\"")
        let whole = NSRange(xml.startIndex..., in: xml)
        return Set(
            regex.matches(in: xml, range: whole).compactMap { match in
                Range(match.range(at: 1), in: xml).map { String(xml[$0]) }
            }
        )
    }

    private static let newKeys = [
        "miff_consent3_photo_title", "miff_consent3_photo_body", "miff_consent3_local_title",
        "miff_consent3_local_body", "permission_location_local_title", "permission_location_local_body",
        "settings_privacy_time_erase_note", "settings_privacy_clarify_body", "miff_clarify_title",
        "miff_clarify_body_one", "miff_clarify_body_unnamed", "miff_clarify_body_many", "miff_clarify_continue",
        "miff_clarify_accept", "miff_clarify_later", "miff_clarify_saving", "miff_clarify_error_extra"
    ]

    private func consentKeys(_ language: [String: String]) -> [String] {
        language.keys.filter {
            $0.hasPrefix("permission_location_") || $0.hasPrefix("miff_consent3_") || $0.hasPrefix("miff_renewal_")
                || $0.hasPrefix("miff_clarify_") || $0 == "settings_privacy_location_body"
                || $0.hasPrefix("settings_privacy_time_")
        }
    }

    // MARK: - CA-1 : les mêmes clés, les mêmes valeurs insérées, sur les deux plateformes

    func testEveryNewKeyExistsInFrenchAndEnglishWithTheSameInsertedValuesAndOnAndroidToo() throws {
        let french = try texts("fr")
        let english = try texts("en")
        let androidFrench = try androidKeys("android/app/src/main/res/values-fr/strings.xml")
        let androidEnglish = try androidKeys("android/app/src/main/res/values/strings.xml")
        for key in Self.newKeys {
            let fr = try XCTUnwrap(french[key], "\(key) manque en français")
            let en = try XCTUnwrap(english[key], "\(key) manque en anglais")
            XCTAssertEqual(fr.components(separatedBy: "%1$@").count, en.components(separatedBy: "%1$@").count, key)
            XCTAssertTrue(androidFrench.contains(key), "\(key) manque dans le français d'Android")
            XCTAssertTrue(androidEnglish.contains(key), "\(key) manque dans l'anglais d'Android")
        }
        // Le nom de la Miff est la seule valeur insérée, et seulement là où un nom est dit.
        for key in ["miff_clarify_body_one", "miff_renewal_intro_one"] {
            XCTAssertTrue(try XCTUnwrap(french[key]).contains("%1$@"), key)
            XCTAssertTrue(try XCTUnwrap(english[key]).contains("%1$@"), key)
        }
        for key in ["miff_clarify_body_unnamed", "miff_clarify_body_many"] {
            XCTAssertFalse(try XCTUnwrap(french[key]).contains("%1$@"), key)
            XCTAssertFalse(try XCTUnwrap(english[key]).contains("%1$@"), key)
        }
    }

    func testTheNewLinesAreTheOnesOfTheTicketWordForWord() throws {
        let french = try texts("fr")
        let english = try texts("en")

        XCTAssertEqual(french["miff_consent3_photo_title"], "L'endroit de ta photo")
        XCTAssertEqual(french["miff_consent3_local_title"], "Sur ton téléphone seulement")
        XCTAssertEqual(
            french["miff_consent3_local_body"],
            "Pour te montrer comment ton temps se répartit, ton téléphone note aussi tes arrivées et tes départs de "
                + "tes lieux, pendant 35 jours, sans position ni adresse. Rien n'est envoyé et personne d'autre ne le "
                + "voit. Tu peux l'effacer dans Réglages > Confidentialité."
        )
        XCTAssertEqual(french["permission_location_local_title"], "Pour toi seul(e)")
        XCTAssertEqual(english["permission_location_local_title"], "For you only")
        XCTAssertEqual(english["miff_consent3_local_title"], "On your phone only")
        XCTAssertEqual(english["miff_consent3_photo_title"], "The place of your photo")
        XCTAssertEqual(french["miff_clarify_title"], "Une précision sur ce que tu partages")
        XCTAssertEqual(english["miff_clarify_title"], "A clarification about what you share")
        XCTAssertEqual(french["miff_clarify_accept"], "J'ai compris")
        XCTAssertEqual(french["miff_clarify_later"], "Plus tard")
        XCTAssertEqual(english["miff_clarify_accept"], "Got it")
        XCTAssertEqual(english["miff_clarify_later"], "Later")
    }

    // MARK: - CA-2 : jamais « pas d'historique » sans « sur nos serveurs »

    private static let timeHistoryLabels: Set<String> = [
        "settings_privacy_time_title", "settings_privacy_time_erase", "settings_privacy_time_confirm_title"
    ]

    func testNoTextSaysNoHistoryWithoutSayingOnOurServersAndNoOldSentenceIsLeft() throws {
        for (key, text) in try texts("fr") where !Self.timeHistoryLabels.contains(key) {
            if text.range(of: "historique", options: .caseInsensitive) != nil
                || text.range(of: "trajets", options: .caseInsensitive) != nil {
                XCTAssertTrue(text.contains("nos serveurs"), "\(key) dit « historique » sans « nos serveurs »")
            }
            XCTAssertFalse(text.contains("Pas d'historique"), key)
            XCTAssertFalse(text.contains("Aucun historique de trajets n'est gardé."), key)
        }
        for (key, text) in try texts("en") where !Self.timeHistoryLabels.contains(key) {
            if text.range(of: "history", options: .caseInsensitive) != nil
                || text.range(of: "trips", options: .caseInsensitive) != nil {
                XCTAssertTrue(text.contains("our servers"), "\(key) says history without « our servers » : \(text)")
            }
            XCTAssertFalse(text.contains("No history of trips is kept."), key)
        }
    }

    // MARK: - CA-3 : les quatre précisions restent

    func testTheFourPrecisionsStayInTheLocationAndConsentTexts() throws {
        let french = try texts("fr")
        let frenchLocation = french.filter { $0.key.hasPrefix("permission_location_") }.values.joined(separator: " ")
        for word in ["dernière position connue", "heure", "historique", "arrière-plan"] {
            XCTAssertTrue(frenchLocation.contains(word), "permission_location_* n'a plus « \(word) »")
        }
        let english = try texts("en")
        let englishLocation = english.filter { $0.key.hasPrefix("permission_location_") }.values.joined(separator: " ")
        for word in ["last known location", "time", "history", "background"] {
            XCTAssertTrue(englishLocation.contains(word), "permission_location_* has no more « \(word) »")
        }
        let frenchConsent = french.filter { $0.key.hasPrefix("miff_consent3_") }.values.joined(separator: " ")
        for word in ["dernière position connue", "heure", "historique", "app est fermée"] {
            XCTAssertTrue(frenchConsent.contains(word), "miff_consent3_* n'a plus « \(word) »")
        }
    }

    func testTheDisclosureSentenceOfThePhoneHasNotMovedAndStillSaysLocationBackgroundAndAppClosed() throws {
        // La phrase obligatoire de la divulgation Google n'a pas d'écart : elle n'est PAS modifiée par CONS-4 (iOS la partage).
        XCTAssertEqual(
            try texts("fr")["permission_location_disclosure"],
            "Miff collecte la localisation de ton téléphone, y compris en arrière-plan, même quand l'app est fermée "
                + "ou que tu ne l'utilises pas, pour repérer quand tu arrives dans un lieu de confiance ou que tu le "
                + "quittes et pour que ta Miff voie ton statut et ta dernière position connue."
        )
        let english = try XCTUnwrap(try texts("en")["permission_location_disclosure"])
        for word in ["location", "background", "app is closed", "last known location"] {
            XCTAssertTrue(english.contains(word), word)
        }
    }

    // MARK: - CA-4 : les mots qu'on n'emploie jamais

    func testNoWordOfSurveillanceNoLegalReviewClaimInTheseTexts() throws {
        let french = try texts("fr")
        let keys = consentKeys(french)
        XCTAssertGreaterThanOrEqual(keys.count, 40, "motif de recherche cassé")
        for key in keys {
            // « suivi » seulement dans la négation « Ce n'est pas un suivi en direct ».
            let text = try XCTUnwrap(french[key]).replacingOccurrences(of: "Ce n'est pas un suivi en direct", with: "")
            for word in ["suivi", "surveillance", "habitude", "juriste", "conforme", "relu"] {
                XCTAssertNil(text.range(of: word, options: .caseInsensitive), "\(key) contient « \(word) »")
            }
        }
        let english = try texts("en")
        for key in consentKeys(english) {
            let text = try XCTUnwrap(english[key]).replacingOccurrences(of: "This is not live tracking", with: "")
            for word in ["tracking", "surveillance", "habit", "lawyer", "legal review", "compliant", "GDPR"] {
                XCTAssertNil(text.range(of: word, options: .caseInsensitive), "\(key) contains « \(word) » (EN)")
            }
        }
    }

    // MARK: - CA-5 : rien de faux sur le journal local

    func testTheLocalJournalIsNeverSaidErasedWhenTheSharingStopsAndIsSaidNeverSent() throws {
        let french = try texts("fr")
        let english = try texts("en")
        for key in [
            "miff_consent3_local_body", "permission_location_local_body", "miff_clarify_body_one",
            "settings_privacy_clarify_body"
        ] {
            XCTAssertFalse(try XCTUnwrap(french[key]).contains("arrêtes de partager"), "\(key) : le journal reste")
            XCTAssertFalse(try XCTUnwrap(english[key]).contains("stop sharing"), key)
        }
        let frenchTime = try XCTUnwrap(french["settings_privacy_time_body"])
        let englishTime = try XCTUnwrap(english["settings_privacy_time_body"])
        XCTAssertTrue(frenchTime.contains("Arrêter de partager ne l'efface pas"))
        XCTAssertTrue(englishTime.contains("Stopping sharing does not erase it"))
        for key in ["miff_consent3_local_body", "permission_location_local_body", "settings_privacy_time_body"] {
            XCTAssertTrue(try XCTUnwrap(french[key]).contains("Rien n'est envoyé"), key)
            XCTAssertTrue(try XCTUnwrap(english[key]).contains("Nothing is sent"), key)
        }
        // 35 jours, sans position ni adresse : les mêmes nombres partout où le journal est décrit.
        for key in ["miff_consent3_local_body", "miff_clarify_body_one", "settings_privacy_time_body"] {
            XCTAssertTrue(try XCTUnwrap(french[key]).contains("35 jours"), key)
            XCTAssertTrue(try XCTUnwrap(english[key]).contains("35 days"), key)
        }
    }

    func testTheErasureSentenceSaysThePhotoAndItsPlaceAreErasedToo() throws {
        let french = try XCTUnwrap(try texts("fr")["miff_consent3_erase_body"])
        let english = try XCTUnwrap(try texts("en")["miff_consent3_erase_body"])
        XCTAssertTrue(french.contains("ta position et ta photo avec son endroit sont effacées"))
        XCTAssertTrue(english.contains("your location and your photo with its place are erased"))
    }

    func testTheCalmCardSaysEverythingContinuesOnlyBecauseNothingIsPaused() throws {
        let french = try texts("fr")
        XCTAssertEqual(french["miff_clarify_continue"], "Tout continue comme avant.")
        XCTAssertEqual(try texts("en")["miff_clarify_continue"], "Everything goes on as before.")
        // Calme : ni pause, ni attente, ni obligation, ni urgence.
        for key in [
            "miff_clarify_title", "miff_clarify_body_one", "miff_clarify_continue", "miff_clarify_accept",
            "miff_clarify_later", "settings_privacy_clarify_body"
        ] {
            let text = try XCTUnwrap(french[key]).lowercased()
            for word in ["pause", "en attente", "obligatoire", "urgent", "bloqu"] {
                XCTAssertFalse(text.contains(word), "\(key) contient « \(word) »")
            }
        }
    }

    // MARK: - Les textes d'usage du système (iOS)

    func testTheAlwaysPurposeStringIsTheTextOfTheTicketAndNoLongerSaysNoHistoryWithoutTheServers() throws {
        let strings = try texts("fr", in: "InfoPlist")
        let english = try texts("en", in: "InfoPlist")
        let always = "NSLocationAlwaysAndWhenInUseUsageDescription"
        let french = try XCTUnwrap(strings[always])
        let en = try XCTUnwrap(english[always])

        XCTAssertTrue(french.contains("sans historique de trajets sur nos serveurs"), french)
        XCTAssertTrue(french.contains("Sur ton téléphone seulement, Miff note aussi tes arrivées et départs"), french)
        XCTAssertTrue(french.contains("Tu peux arrêter à tout moment."), french)
        XCTAssertTrue(en.contains("with no history of trips on our servers"), en)
        XCTAssertTrue(en.contains("On your phone only, Miff also notes your arrivals and departures"), en)
        XCTAssertTrue(en.contains("You can stop at any time."), en)
        // Les quatre précisions de la charte : dernière, heure, historique, arrière-plan.
        for word in ["dernière position connue", "heure", "historique", "arrière-plan"] {
            XCTAssertTrue(french.contains(word), word)
        }
        for word in ["last known location", "time", "history", "background"] {
            XCTAssertTrue(en.contains(word), word)
        }
        XCTAssertNil(french.range(of: "suivi", options: .caseInsensitive))
        XCTAssertNil(en.range(of: "tracking", options: .caseInsensitive))
    }

    func testThePurposeStringWhileUsingTheAppDoesNotSayHistoryAndIsUnchanged() throws {
        let whenInUse = "NSLocationWhenInUseUsageDescription"
        for language in ["fr", "en"] {
            let text = try XCTUnwrap(try texts(language, in: "InfoPlist")[whenInUse])
            XCTAssertNil(text.range(of: "histor", options: .caseInsensitive), "pas d'historique ici (\(language))")
        }
        let english = try XCTUnwrap(try texts("en", in: "InfoPlist")[whenInUse])
        XCTAssertTrue(english.hasPrefix("Miff uses your location to know when"))
    }
}
