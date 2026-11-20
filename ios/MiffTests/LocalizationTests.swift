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
            pattern: "\"((?:diag|home|auth|place|common|profile|feed|miff|permission|ios|countdown|alert)_[a-z0-9_]+)\""
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

    /// Toute langue du catalogue est une langue du registre ; une langue `required` a TOUS les textes traduisibles, sauf les clés dont le registre
    /// la dispense (écran de diagnostic, `missing-key` : repli anglais). Une langue `planned` n'a pas à être complète : le job « Parité des clés de
    /// traduction » la suit en avertissement.
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
            let isExempt = try I18nRegistry.missingKeyExemption(language: language.code)
            for (key, entry) in catalog where entry["shouldTranslate"] as? Bool != false && !isExempt(key) {
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

    /// Les formes de pluriel écrites dans les SUBSTITUTIONS (« %#@arg1@ ») : le pluriel de `place_list_counter_free` et des textes à plusieurs
    /// valeurs. Même nom de substitution que le français, formes de la langue seulement, `one` et `other` toujours là.
    private func substitutionForms(_ entry: [String: Any], language: String) -> [String: Set<String>] {
        let localization = (entry["localizations"] as? [String: Any])?[language] as? [String: Any]
        let substitutions = localization?["substitutions"] as? [String: Any] ?? [:]
        var forms: [String: Set<String>] = [:]
        for (name, substitution) in substitutions {
            let variations = (substitution as? [String: Any])?["variations"] as? [String: Any]
            let plural = variations?["plural"] as? [String: Any] ?? [:]
            forms[name] = Set(plural.keys)
        }
        return forms
    }

    /// Les langues déclarées dans `CFBundleLocalizations` sont exactement les langues `required` du registre ; chacune a TOUS les textes
    /// traduisibles (hors écran de diagnostic, dispensé par le registre : repli anglais), les mêmes valeurs insérées que le français (type et
    /// position, dans le texte simple, dans chaque forme de pluriel et dans les substitutions) et les formes de pluriel de SA langue
    /// (`one`, `many`, `other` en es, it, pt ; `one`, `other` en de). Lot 4 iOS du ticket I18N-4L : es, it, pt, de.
    func testEveryDeclaredLanguageHasAllTheTextsTheFrenchValuesAndItsOwnPluralForms() throws {
        let registry = try I18nRegistry.languages()
        let declared = Bundle.main.infoDictionary?["CFBundleLocalizations"] as? [String] ?? []
        XCTAssertEqual(Set(declared), Set(registry.filter { $0.status == "required" }.map { $0.code }))
        let six: Set<String> = ["fr", "en", "es", "it", "pt", "de"]
        XCTAssertTrue(six.isSubset(of: Set(declared)), "langues déclarées : \(declared)")
        let catalog = try loadCatalog()
        for code in declared where code != "fr" {
            let language = try XCTUnwrap(registry.first { $0.code == code }, "\(code) absente du registre")
            let isExempt = try I18nRegistry.missingKeyExemption(language: code)
            for (key, entry) in catalog where entry["shouldTranslate"] as? Bool != false && !isExempt(key) {
                let label = "\(key) (\(code))"
                let texts = values(entry, language: code).filter { !$0.isEmpty }
                XCTAssertFalse(texts.isEmpty, "\(label) : texte manquant")
                let expected = Set(try values(entry, language: "fr").flatMap(placeholders))
                let found = Set(try texts.flatMap(placeholders))
                XCTAssertEqual(found, expected, "\(label) : valeurs insérées différentes du français")

                let frenchForms = substitutionForms(entry, language: "fr")
                let forms = substitutionForms(entry, language: code)
                XCTAssertEqual(Set(forms.keys), Set(frenchForms.keys), "\(label) : substitutions différentes")
                for (name, names) in forms {
                    XCTAssertTrue(names.isSubset(of: language.pluralsAllowed), "\(label) \(name) : forme inconnue")
                    XCTAssertTrue(language.pluralsRequired.isSubset(of: names), "\(label) \(name) : forme absente")
                }
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

    /// Chaque langue de l'app a ses textes compilés (`xx.lproj`). L'écran de diagnostic (`diag_*`) n'existe qu'en français et en anglais : dans
    /// les autres langues le registre le dispense (repli anglais, test suivant).
    func testCompiledStringsAreTranslatedInEveryLanguageOfTheApp() throws {
        for language in try I18nRegistry.shipped() {
            let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"), language)
            let bundle = try XCTUnwrap(Bundle(path: path))
            let tagline = L10n.string("home_tagline", bundle: bundle)
            XCTAssertNotEqual(tagline, "home_tagline", "home_tagline non traduit en \(language)")
            guard ["fr", "en"].contains(language) else {
                continue
            }
            for key in ["diag_title", "diag_test1_ok", "diag_switch_analytics"] {
                XCTAssertNotEqual(L10n.string(key, bundle: bundle), key, "\(key) non traduit en \(language)")
            }
        }
        let french = try XCTUnwrap(Bundle(path: try XCTUnwrap(Bundle.main.path(forResource: "fr", ofType: "lproj"))))
        let english = try XCTUnwrap(Bundle(path: try XCTUnwrap(Bundle.main.path(forResource: "en", ofType: "lproj"))))
        XCTAssertNotEqual(L10n.string("home_tagline", bundle: french), L10n.string("home_tagline", bundle: english))
    }

    /// Une clé absente du paquet d'une langue (écran de diagnostic, noms de langue, nom de l'offre : `shouldTranslate: false`) s'affiche en
    /// ANGLAIS, jamais sous la forme du nom de la clé ; une clé inconnue partout reste visible telle quelle (un oubli se voit).
    func testAKeyMissingFromALanguageFallsBackToEnglishNeverToTheRawKey() throws {
        let english = try XCTUnwrap(Bundle(path: try XCTUnwrap(Bundle.main.path(forResource: "en", ofType: "lproj"))))
        for language in try I18nRegistry.shipped() where !["fr", "en"].contains(language) {
            let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"), language)
            let bundle = try XCTUnwrap(Bundle(path: path))
            for key in ["diag_title", "ios_settings_language_german", "settings_subscription_plan_name"] {
                let shown = L10n.string(key, bundle: bundle)
                XCTAssertEqual(shown, L10n.string(key, bundle: english), "\(key) (\(language))")
                XCTAssertNotEqual(shown, key, "\(key) (\(language)) : clé brute")
            }
            XCTAssertEqual(L10n.string("ios_settings_language_german", bundle: bundle), "Deutsch")
            XCTAssertEqual(L10n.string("cle_inconnue_partout", bundle: bundle), "cle_inconnue_partout")
        }
    }

    func testPluralsFollowTheLanguageRules() throws {
        let path = try XCTUnwrap(Bundle.main.path(forResource: "en", ofType: "lproj"))
        let english = try XCTUnwrap(Bundle(path: path))
        let format = L10n.string("diag_lang_members", bundle: english)

        XCTAssertEqual(String.localizedStringWithFormat(format, 1), "1 member")
        XCTAssertEqual(String.localizedStringWithFormat(format, 3), "3 members")
    }
}
