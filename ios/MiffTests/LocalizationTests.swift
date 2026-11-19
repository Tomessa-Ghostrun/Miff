import XCTest
@testable import Miff

/// String Catalog : source française, anglais présent, anglais en langue de repli, aucune clé sans traduction
/// (équivalent des contrôles Lint MissingTranslation / ExtraTranslation d'Android).
final class LocalizationTests: XCTestCase {
    private typealias Catalog = [String: [String: Any]]

    /// Catalogue source du dépôt (lu depuis les sources : `#filePath` est le chemin de ce fichier à la compilation).
    private func loadCatalog() throws -> Catalog {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Catalogue source introuvable (\(url.path)).")
        }
        let data = try Data(contentsOf: url)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(root["sourceLanguage"] as? String, "fr", "Le français doit être la langue source.")
        return try XCTUnwrap(root["strings"] as? Catalog)
    }

    /// Tous les textes d'une langue pour une clé (un seul texte, ou une variante par forme de pluriel).
    private func values(_ entry: [String: Any], language: String) -> [String] {
        let localization = (entry["localizations"] as? [String: Any])?[language] as? [String: Any]
        if let unit = localization?["stringUnit"] as? [String: Any], let value = unit["value"] as? String {
            return [value]
        }
        let plural = (localization?["variations"] as? [String: Any])?["plural"] as? [String: Any] ?? [:]
        return plural.values.compactMap { variant in
            let unit = (variant as? [String: Any])?["stringUnit"] as? [String: Any]
            return unit?["value"] as? String
        }
    }

    private func placeholders(_ text: String) throws -> [String] {
        let regex = try NSRegularExpression(pattern: "%(?:\\d+\\$)?(?:lld|@|d)")
        let range = NSRange(text.startIndex..., in: text)
        return regex.matches(in: text, range: range).compactMap { Range($0.range, in: text).map { String(text[$0]) } }
    }

    func testEveryKeyHasAFrenchAndAnEnglishText() throws {
        let catalog = try loadCatalog()
        XCTAssertGreaterThan(catalog.count, 80)
        for (key, entry) in catalog {
            XCTAssertFalse(values(entry, language: "fr").filter { !$0.isEmpty }.isEmpty, "\(key) : français manquant")
            XCTAssertFalse(values(entry, language: "en").filter { !$0.isEmpty }.isEmpty, "\(key) : anglais manquant")
        }
    }

    func testFrenchAndEnglishUseTheSameValuesInTheSamePlaces() throws {
        let catalog = try loadCatalog()
        for (key, entry) in catalog {
            let french = try values(entry, language: "fr").flatMap(placeholders)
            let english = try values(entry, language: "en").flatMap(placeholders)
            XCTAssertEqual(Set(french), Set(english), "\(key) : valeurs insérées différentes entre fr et en")
        }
    }

    func testKeysFollowTheSharedNamingContract() throws {
        let catalog = try loadCatalog()
        let pattern = "^[a-z][a-z0-9]*(_[a-z0-9]+)+$"
        for key in catalog.keys {
            XCTAssertNotNil(key.range(of: pattern, options: .regularExpression), "\(key) : format de clé invalide")
        }
    }

    /// Toute clé de texte écrite dans le code existe dans le catalogue (sinon l'écran afficherait « diag_xxx »).
    /// Seuls les préfixes de texte du contrat sont cherchés : `miff_` est volontairement absent, car les noms
    /// d'événements Analytics (`miff_created`...) ont la même forme. À étendre quand un écran `miff_` apparaît.
    func testEveryTextKeyUsedInTheCodeExistsInTheCatalog() throws {
        let catalog = try loadCatalog()
        let sources = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff")
        guard let files = FileManager.default.enumerator(at: sources, includingPropertiesForKeys: nil) else {
            throw XCTSkip("Sources introuvables (\(sources.path)).")
        }
        let regex = try NSRegularExpression(
            pattern: "\"((?:diag|home|auth|place|common|profile|feed|miff|permission|ios|countdown)_[a-z0-9_]+)\""
        )
        // Les noms d'événements Analytics (`miff_created`, `miff_joined`) ont la même forme qu'une clé de texte `miff_` : ce ne
        // sont pas des clés.
        let analyticsNames: Set<String> = ["miff_created", "miff_joined"]
        var used = Set<String>()
        for case let file as URL in files where file.pathExtension == "swift" {
            let text = try String(contentsOf: file, encoding: .utf8)
            let range = NSRange(text.startIndex..., in: text)
            for match in regex.matches(in: text, range: range) {
                if let keyRange = Range(match.range(at: 1), in: text) {
                    let key = String(text[keyRange])
                    if !analyticsNames.contains(key) {
                        used.insert(key)
                    }
                }
            }
        }

        XCTAssertGreaterThan(used.count, 60, "Presque aucune clé trouvée dans le code : motif de recherche cassé ?")
        for key in used.sorted() {
            XCTAssertNotNil(catalog[key], "Clé « \(key) » utilisée dans le code mais absente du catalogue")
        }
    }

    // MARK: - Les langues du registre (docs/contracts/i18n-languages.json)

    /// Toute langue du catalogue est une langue du registre ; une langue `required` a TOUS les textes traduisibles. Une langue `planned` (es, it,
    /// pt, de avant le lot 4 du ticket I18N-4L) n'a pas à être complète : le job « Parité des clés de traduction » la suit en avertissement.
    func testEveryLanguageOfTheCatalogIsInTheRegistryAndTheRequiredOnesAreComplete() throws {
        let registry = try I18nRegistry.languages()
        let catalog = try loadCatalog()
        var present = Set<String>()
        for entry in catalog.values {
            present.formUnion((entry["localizations"] as? [String: Any] ?? [:]).keys)
        }

        let unknown = present.subtracting(registry.map { $0.code })
        XCTAssertTrue(unknown.isEmpty, "Langue du catalogue absente du registre : \(unknown.sorted())")
        for language in registry where language.status == "required" {
            for (key, entry) in catalog where entry["shouldTranslate"] as? Bool != false {
                let texts = values(entry, language: language.code).filter { !$0.isEmpty }
                XCTAssertFalse(texts.isEmpty, "\(key) : texte \(language.code) manquant (langue required)")
            }
        }
    }

    /// Les formes de pluriel de chaque langue suivent le registre : jamais une forme que la langue ne connaît pas (« many » en anglais ou en
    /// allemand, « few »...), toujours `one` et `other`. Le zéro se range avec `other` en es, it, pt, de (« 0 días ») : relire les textes
    /// français pensés pour « 0 ou 1 » (ticket I18N-4L §4.2).
    func testThePluralFormsOfEveryLanguageFollowTheRegistry() throws {
        let catalog = try loadCatalog()
        for language in try I18nRegistry.languages() {
            for (key, entry) in catalog {
                let localization = (entry["localizations"] as? [String: Any])?[language.code] as? [String: Any]
                let plural = (localization?["variations"] as? [String: Any])?["plural"] as? [String: Any]
                guard let forms = plural.map({ Set($0.keys) }) else {
                    continue
                }
                let label = "\(key) (\(language.code))"
                let unknown = forms.subtracting(language.pluralsAllowed)
                XCTAssertTrue(unknown.isEmpty, "\(label) : forme inconnue de la langue \(unknown.sorted())")
                XCTAssertTrue(language.pluralsRequired.isSubset(of: forms), "\(label) : forme obligatoire absente")
            }
        }
    }

    func testTheAppBundleContainsFrenchAndEnglish() {
        XCTAssertTrue(Bundle.main.localizations.contains("fr"))
        XCTAssertTrue(Bundle.main.localizations.contains("en"))
    }

    func testEnglishIsTheFallbackLanguage() {
        XCTAssertEqual(Bundle.main.developmentLocalization, "en")
    }

    func testCompiledStringsAreTranslatedInEveryLanguageOfTheApp() throws {
        for language in try I18nRegistry.shipped() {
            let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"), language)
            let bundle = try XCTUnwrap(Bundle(path: path))
            for key in ["home_tagline", "diag_title", "diag_test1_ok", "diag_switch_analytics"] {
                XCTAssertNotEqual(L10n.string(key, bundle: bundle), key, "\(key) non traduit en \(language)")
            }
        }
        let french = try XCTUnwrap(Bundle(path: try XCTUnwrap(Bundle.main.path(forResource: "fr", ofType: "lproj"))))
        let english = try XCTUnwrap(Bundle(path: try XCTUnwrap(Bundle.main.path(forResource: "en", ofType: "lproj"))))
        XCTAssertNotEqual(L10n.string("home_tagline", bundle: french), L10n.string("home_tagline", bundle: english))
    }

    func testPluralsFollowTheLanguageRules() throws {
        let path = try XCTUnwrap(Bundle.main.path(forResource: "en", ofType: "lproj"))
        let english = try XCTUnwrap(Bundle(path: path))
        let format = L10n.string("diag_lang_members", bundle: english)

        XCTAssertEqual(String.localizedStringWithFormat(format, 1), "1 member")
        XCTAssertEqual(String.localizedStringWithFormat(format, 3), "3 members")
    }
}
