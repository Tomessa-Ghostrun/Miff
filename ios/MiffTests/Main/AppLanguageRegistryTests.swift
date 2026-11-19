import XCTest
@testable import Miff

/// Réglages > Langue pour les SIX langues du registre (`docs/contracts/i18n-languages.json`, ticket I18N-4L lot 3) : l'énumération, les libellés,
/// la langue effective selon le téléphone (la région ne compte jamais), le portugais du Portugal, et les langues proposées seulement une fois
/// leurs textes dans l'app. Aucune de ces vérifications ne dépend de traductions : les valeurs es / it / pt / de arrivent au lot 4.
private final class MemoryLanguageStore: AppLanguageStoring {
    var value: String?

    init(value: String? = nil) {
        self.value = value
    }

    func load() -> String? {
        value
    }

    func save(_ value: String) {
        self.value = value
    }
}

@MainActor
final class AppLanguageRegistryTests: XCTestCase {
    /// Un téléphone dont l'app n'a encore les textes que du français et de l'anglais.
    private func phone(_ preferred: [String]) -> AppLanguageEnvironment {
        AppLanguageEnvironment(
            preferred: { preferred },
            available: { ["en", "fr", "Base"] },
            languageBundle: { _ in nil }
        )
    }

    private func systemCode(_ environment: AppLanguageEnvironment) -> String {
        AppLanguageState.make(choice: .system, environment: environment).code
    }

    /// Les langues sont celles du registre (source unique), par ordre alphabétique de leur NOM écrit dans leur langue, puis « langue du
    /// téléphone ». Le nom du libellé est celui du registre.
    func testTheChoicesAreTheLanguagesOfTheRegistryInTheOrderOfTheirNativeName() throws {
        XCTAssertEqual(
            AppLanguageChoice.allCases,
            [.german, .english, .spanish, .french, .italian, .portuguese, .system]
        )
        let registry = try I18nRegistry.languages()
        let offered = AppLanguageChoice.allCases.filter { $0 != .system }
        XCTAssertEqual(Set(offered.map { $0.rawValue }), Set(registry.map { $0.code }))
        XCTAssertEqual(offered.count, registry.count)
        let names = offered.map { LanguageLabels.label(for: $0) }
        XCTAssertEqual(names, names.sorted { $0.compare($1, options: .diacriticInsensitive) == .orderedAscending })
        for language in registry {
            let choice = try XCTUnwrap(AppLanguageChoice(rawValue: language.code), language.code)
            XCTAssertEqual(LanguageLabels.label(for: choice), language.nativeName, language.code)
        }
    }

    func testPortugueseIsThePortugueseOfPortugalAndHasNoRegionalCode() {
        XCTAssertEqual(AppLanguageChoice.portuguese.rawValue, "pt")
        XCTAssertEqual(AppLanguageChoice.portuguese.forcedCode, "pt")
        XCTAssertFalse(AppLanguageChoice.allCases.contains { $0.rawValue.contains("-") })
    }

    /// Un téléphone dont l'app a TOUS les textes (les six langues du registre).
    private func phoneWithAllLanguages(_ preferred: [String]) -> AppLanguageEnvironment {
        AppLanguageEnvironment(
            preferred: { preferred },
            available: { ["en", "fr", "es", "it", "pt", "de", "Base"] },
            languageBundle: { _ in Bundle.main }
        )
    }

    /// Critère 12 du ticket I18N-4L : la langue du téléphone donne la langue de l'app, la région ne compte pas.
    func testThePhoneLanguageIsReadByItsLanguageNeverByItsRegion() {
        let cases: [(String, String)] = [
            ("pt-BR", "pt"), ("pt-PT", "pt"), ("pt", "pt"), ("es-MX", "es"), ("es-419", "es"), ("es-ES", "es"),
            ("de-AT", "de"), ("de-CH", "de"), ("it-IT", "it"), ("fr-CA", "fr"), ("en-GB", "en"), ("de_DE", "de")
        ]
        for (device, expected) in cases {
            XCTAssertEqual(systemCode(phoneWithAllLanguages([device])), expected, device)
        }
    }

    func testALanguageTheAppDoesNotSpeakIsEnglishAndTheNextSupportedOneWins() {
        for device in ["ja-JP", "nl-NL", "zh-Hans-CN", "ar-SA", "ko-KR"] {
            XCTAssertEqual(systemCode(phoneWithAllLanguages([device])), "en", device)
        }
        XCTAssertEqual(systemCode(phoneWithAllLanguages(["ja-JP", "pt-BR", "en-US"])), "pt")
        XCTAssertEqual(systemCode(phoneWithAllLanguages([])), "en")
    }

    /// Une langue déclarée (`planned` au registre) dont le catalogue n'a encore aucun texte n'a pas de `xx.lproj` : l'app ne la parle pas, un
    /// téléphone dans cette langue reste en anglais au lieu de dire « es » avec des textes anglais.
    func testALanguageWithoutTextsYetIsNotSpokenByTheApp() {
        XCTAssertEqual(systemCode(phone(["es-ES", "de-DE"])), "en")
        XCTAssertEqual(systemCode(phone(["pt-BR", "fr-FR"])), "fr")
    }

    func testEveryLanguageOfTheRegistryCanBeChosenAndAppliesAtOnce() throws {
        for language in try I18nRegistry.languages() {
            let store = MemoryLanguageStore()
            let controller = AppLanguageController(store: store, environment: phoneWithAllLanguages(["ja-JP"]))
            let choice = try XCTUnwrap(AppLanguageChoice(rawValue: language.code))

            controller.select(choice)

            XCTAssertEqual(controller.state.code, language.code)
            XCTAssertEqual(controller.state.locale.language.languageCode?.identifier, language.code)
            XCTAssertEqual(store.value, language.code)
            XCTAssertEqual(AppLanguageController(store: store, environment: phoneWithAllLanguages([])).choice, choice)
        }
    }

    /// Le portugais de l'app est celui du Portugal : ses formats et ses pluriels (« 0 dias » : au Portugal le zéro n'est pas un singulier) sont
    /// ceux de `pt_PT`, que le portugais soit choisi dans l'app ou vienne d'un téléphone en pt-BR. Les autres langues gardent leur propre code.
    func testPortugueseFormatsAndPluralsAreThoseOfPortugalWhateverThePhoneRegion() {
        let chosen = AppLanguageState.make(choice: .portuguese, environment: phoneWithAllLanguages(["fr-FR"]))
        let fromBrazil = AppLanguageState.make(choice: .system, environment: phoneWithAllLanguages(["pt-BR"]))
        let spanish = AppLanguageState.make(choice: .spanish, environment: phoneWithAllLanguages(["pt-BR"]))

        XCTAssertEqual(chosen.code, "pt")
        XCTAssertEqual(chosen.locale.identifier, "pt_PT")
        XCTAssertEqual(fromBrazil.code, "pt")
        XCTAssertEqual(fromBrazil.locale.identifier, "pt_PT")
        XCTAssertEqual(spanish.locale.identifier, "es")
    }

    /// Une langue n'est proposée qu'une fois ses textes dans l'app (son `xx.lproj`) : tant que le catalogue n'a aucun texte pour es, it, pt, de,
    /// personne ne peut les choisir. « Langue du téléphone » est toujours là, et le choix en cours aussi.
    func testOnlyTheLanguagesWhoseTextsAreInTheAppAreOffered() {
        func controller(
            withTextsFor codes: Set<String>,
            store: MemoryLanguageStore = MemoryLanguageStore()
        ) -> AppLanguageController {
            AppLanguageController(
                store: store,
                environment: AppLanguageEnvironment(
                    preferred: { ["en-US"] },
                    available: { Array(codes) },
                    languageBundle: { codes.contains($0) ? Bundle.main : nil }
                )
            )
        }

        XCTAssertEqual(controller(withTextsFor: ["fr", "en"]).offeredChoices, [.english, .french, .system])
        XCTAssertEqual(controller(withTextsFor: []).offeredChoices, [.system])
        XCTAssertEqual(
            controller(withTextsFor: ["fr", "en", "es", "it", "pt", "de"]).offeredChoices,
            AppLanguageChoice.allCases
        )
        // Le choix en cours reste montré même si ses textes ont disparu (version plus ancienne) : une ligne cochée, jamais une liste sans choix.
        let stored = controller(withTextsFor: ["fr", "en"], store: MemoryLanguageStore(value: "es"))
        XCTAssertEqual(stored.offeredChoices, [.english, .spanish, .french, .system])
    }

    /// L'écran montre les lignes des langues proposées, une seule sélectionnée (« Español, sélectionné »).
    func testTheScreenShowsTheOfferedLanguagesThenThePhoneLanguage() {
        let offered: [AppLanguageChoice] = [.german, .english, .spanish, .french, .italian, .portuguese, .system]

        let rows = LanguageRows.rows(selected: .spanish, offered: offered)

        XCTAssertEqual(rows.count, 7)
        XCTAssertEqual(rows.last?.choice, .system)
        XCTAssertEqual(rows.filter { $0.isSelected }.map { $0.choice }, [.spanish])
        let short = LanguageRows.rows(selected: .english, offered: [.english, .system])
        XCTAssertEqual(short.map { $0.choice }, [.english, .system])
    }
}
