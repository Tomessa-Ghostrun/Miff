import XCTest
@testable import Miff

/// Réglages > Langue : le choix (français, English, langue du téléphone), sa conservation sur l'appareil, la langue effective qui en
/// résulte (texte, locale des dates et des nombres) et l'absence totale de renvoi vers les réglages de l'iPhone pour cette ligne.
private final class MemoryLanguageStore: AppLanguageStoring {
    var value: String?
    private(set) var saves: [String] = []

    init(value: String? = nil) {
        self.value = value
    }

    func load() -> String? {
        value
    }

    func save(_ value: String) {
        self.value = value
        saves.append(value)
    }
}

@MainActor
final class AppLanguageTests: XCTestCase {
    /// Un téléphone qui parle allemand, puis français, puis anglais : l'app ne parle que le français et l'anglais.
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

    override func tearDown() {
        // Les tests qui touchent à la langue GLOBALE la remettent sur celle du téléphone : les autres tests lisent le vrai catalogue.
        AppLanguage.apply(AppLanguageState.make(choice: .system))
        super.tearDown()
    }

    // MARK: - Le choix

    func testTheChoiceIsThePhoneLanguageByDefault() {
        let controller = AppLanguageController(store: MemoryLanguageStore(), environment: phone(["fr-FR"]))

        XCTAssertEqual(controller.choice, .system)
        XCTAssertNil(AppLanguageChoice.system.forcedCode)
    }

    func testAStoredValueIsReadByItsLanguageAndAnythingElseIsThePhoneLanguage() {
        XCTAssertEqual(AppLanguageChoice.from(stored: "fr"), .french)
        XCTAssertEqual(AppLanguageChoice.from(stored: "en"), .english)
        XCTAssertEqual(AppLanguageChoice.from(stored: "fr-FR"), .french)
        XCTAssertEqual(AppLanguageChoice.from(stored: " EN_us "), .english)
        XCTAssertEqual(AppLanguageChoice.from(stored: "system"), .system)
        for unknown in [nil, "", "   ", "de", "es-ES", "klingon", "🙂"] {
            XCTAssertEqual(AppLanguageChoice.from(stored: unknown), .system, "\(String(describing: unknown))")
        }
    }

    func testTheThreeChoicesAreOfferedInTheOrderOfAndroid() {
        XCTAssertEqual(AppLanguageChoice.allCases, [.french, .english, .system])
    }

    // MARK: - La langue effective

    func testAChosenLanguageWinsOverThePhone() {
        let german = phone(["de-DE", "en-US"])

        let french = AppLanguageState.make(choice: .french, environment: german)
        let english = AppLanguageState.make(choice: .english, environment: phone(["fr-FR"]))

        XCTAssertEqual(french.code, "fr")
        XCTAssertEqual(french.locale.identifier, "fr")
        XCTAssertEqual(english.code, "en")
        XCTAssertEqual(english.locale.identifier, "en")
    }

    func testThePhoneLanguageIsTheFirstOneTheAppSpeaksWithEnglishAsFallback() {
        XCTAssertEqual(systemCode(phone(["fr-FR"])), "fr")
        XCTAssertEqual(systemCode(phone(["de-DE", "fr-CA"])), "fr")
        XCTAssertEqual(systemCode(phone(["ja-JP"])), "en")
    }

    func testThePhoneLanguageChangesNothingToTheWayTheAppWasAlreadyReadingItsTexts() {
        let state = AppLanguageState.make(choice: .system)

        // Le paquet principal : pseudo-langue, repli anglais et captures de la CI inchangés.
        XCTAssertEqual(state.bundle, Bundle.main)
    }

    func testAChosenLanguageReadsItsOwnTextsAndFallsBackToTheMainBundleWhenTheyAreMissing() throws {
        let path = try XCTUnwrap(Bundle.main.path(forResource: "en", ofType: "lproj"))
        let english = try XCTUnwrap(Bundle(path: path))
        let found = AppLanguageEnvironment(
            preferred: { [] },
            available: { ["en", "fr"] },
            languageBundle: { _ in english }
        )

        XCTAssertEqual(AppLanguageState.make(choice: .english, environment: found).bundle, english)
        XCTAssertEqual(AppLanguageState.make(choice: .french, environment: phone([])).bundle, Bundle.main)
    }

    // MARK: - Garde sur l'appareil, à chaud

    func testAChoiceIsKeptOnTheDeviceAndRestoredAtTheNextLaunch() {
        let store = MemoryLanguageStore()
        let first = AppLanguageController(store: store, environment: phone(["fr-FR"]))

        first.select(.english)

        XCTAssertEqual(store.value, "en")
        XCTAssertEqual(first.choice, .english)
        XCTAssertEqual(first.state.code, "en")
        let nextLaunch = AppLanguageController(store: store, environment: phone(["fr-FR"]))
        XCTAssertEqual(nextLaunch.choice, .english)
        XCTAssertEqual(nextLaunch.state.code, "en")
    }

    func testChoosingThePhoneLanguageAgainForgetsTheChosenOne() {
        let store = MemoryLanguageStore(value: "en")
        let controller = AppLanguageController(store: store, environment: phone(["fr-FR"]))
        XCTAssertEqual(controller.state.code, "en")

        controller.select(.system)

        XCTAssertEqual(store.value, "system")
        XCTAssertEqual(controller.state.code, "fr")
    }

    func testChoosingWhatIsAlreadyChosenWritesNothing() {
        let store = MemoryLanguageStore(value: "fr")
        let controller = AppLanguageController(store: store, environment: phone(["en-US"]))

        controller.select(.french)

        XCTAssertTrue(store.saves.isEmpty)
    }

    func testOnlyOneShortValueIsWrittenAndNeverAnythingPersonal() {
        let defaults = UserDefaults(suiteName: "miff.tests.language.\(UUID().uuidString)")
        let store = UserDefaultsLanguageStore(defaults: defaults ?? .standard)
        XCTAssertNil(store.load())

        store.save("fr")

        XCTAssertEqual(store.load(), "fr")
        XCTAssertEqual(UserDefaultsLanguageStore.key, "miff.appLanguage")
        defaults?.removeObject(forKey: UserDefaultsLanguageStore.key)
    }

    // MARK: - Les textes calculés hors vue suivent le choix

    func testTextsComputedOutsideAnyViewFollowTheChoiceImmediately() {
        let controller = AppLanguageController(store: MemoryLanguageStore(), publishesGlobally: true)

        controller.select(.french)
        XCTAssertEqual(L10n.string("settings_language_title"), "Langue")
        XCTAssertEqual(AppLanguage.code, "fr")
        XCTAssertEqual(AppLanguage.locale.identifier, "fr")

        controller.select(.english)
        XCTAssertEqual(L10n.string("settings_language_title"), "Language")
        XCTAssertEqual(L10n.format("ios_settings_language_current", "English"), "Current language: English")
        XCTAssertEqual(AppLanguage.code, "en")
    }

    func testFormatsOfDatesAndTheFeedTextsFollowTheChosenLocale() {
        let controller = AppLanguageController(store: MemoryLanguageStore(), publishesGlobally: true)

        controller.select(.french)
        XCTAssertEqual(FeedTexts().locale.identifier, "fr")
        controller.select(.english)
        XCTAssertEqual(FeedTexts().locale.identifier, "en")
    }

    func testTheSystemChoiceGivesBackTheTextsOfThePhone() {
        let controller = AppLanguageController(store: MemoryLanguageStore(), publishesGlobally: true)
        controller.select(.english)

        controller.select(.system)

        let phone = Bundle.main.localizedString(forKey: "settings_language_title", value: nil, table: nil)
        XCTAssertEqual(L10n.string("settings_language_title"), phone)
    }

    func testPlannedNotificationsAreToldWhenTheLanguageChanges() {
        let controller = AppLanguageController(store: MemoryLanguageStore(), publishesGlobally: true)
        let told = expectation(forNotification: AppLanguage.changed, object: nil)

        controller.select(.english)

        wait(for: [told], timeout: 1)
    }

    func testAControllerOfATestNeverTouchesTheLanguageOfTheApp() {
        let before = AppLanguage.code
        let other = before == "fr" ? AppLanguageChoice.english : .french

        AppLanguageController(store: MemoryLanguageStore(), environment: phone([])).select(other)

        XCTAssertEqual(AppLanguage.code, before)
    }

    // MARK: - L'écran

    func testTheNamesOfTheTwoLanguagesAreNeverTranslated() {
        let controller = AppLanguageController(store: MemoryLanguageStore(), publishesGlobally: true)
        for choice in [AppLanguageChoice.french, .english] {
            controller.select(choice)

            XCTAssertEqual(LanguageLabels.label(for: .french), "Français")
            XCTAssertEqual(LanguageLabels.label(for: .english), "English")
        }
    }

    func testThePhoneLanguageLabelFollowsTheLanguageOfTheApp() {
        let controller = AppLanguageController(store: MemoryLanguageStore(), publishesGlobally: true)

        controller.select(.french)
        XCTAssertEqual(LanguageLabels.label(for: .system), "Langue du téléphone")
        controller.select(.english)
        XCTAssertEqual(LanguageLabels.label(for: .system), "Phone language")
    }

    func testTheSettingsRowSaysTheCurrentLanguageInItsOwnLanguage() {
        let controller = AppLanguageController(store: MemoryLanguageStore(), publishesGlobally: true)

        controller.select(.french)
        XCTAssertEqual(AppLanguageName.current, "Français")
        controller.select(.english)
        XCTAssertEqual(AppLanguageName.current, "English")
    }

    func testTheLanguageScreenIsReachedFromTheSettingsAndIsClosedToAnAccountWithoutMiff() {
        XCTAssertTrue(SettingsAccess.children(of: .settings).contains(.language))
        XCTAssertTrue(SettingsAccess.isAllowed(.language, restricted: false))
        XCTAssertFalse(SettingsAccess.isAllowed(.language, restricted: true))
        XCTAssertFalse(SettingsAccess.reachable(restricted: true).contains(.language))
        var path: [MiffRoute] = [.settings(.settings), .settings(.language), .miff("abc")]

        path.removeAll { $0.isMyInfoScreen || $0.isLanguageScreen || $0.isNotificationScreen }

        XCTAssertEqual(path, [.settings(.settings), .miff("abc")])
    }

    // MARK: - Le bouton radio

    func testExactlyOneRowIsSelectedWhateverTheChoice() {
        for choice in AppLanguageChoice.allCases {
            let rows = LanguageRows.rows(selected: choice)

            XCTAssertEqual(rows.map { $0.choice }, [.french, .english, .system])
            XCTAssertEqual(rows.filter { $0.isSelected }.map { $0.choice }, [choice])
        }
    }

    func testTheSelectedRowFollowsTheController() {
        let controller = AppLanguageController(store: MemoryLanguageStore(), environment: phone(["fr-FR"]))
        let before = LanguageRows.rows(selected: controller.choice).filter { $0.isSelected }
        XCTAssertEqual(before.map { $0.choice }, [.system])

        controller.select(.english)

        let after = LanguageRows.rows(selected: controller.choice).filter { $0.isSelected }
        XCTAssertEqual(after.map { $0.choice }, [.english])
    }

    func testARowIsOneButtonThatSaysWhenItIsSelected() {
        XCTAssertEqual(LanguageRows.traits(isSelected: true), [.isButton, .isSelected])
        XCTAssertEqual(LanguageRows.traits(isSelected: false), .isButton)
        XCTAssertFalse(LanguageRows.traits(isSelected: false).contains(.isSelected))
    }

    // MARK: - Les sources

    private var app: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Main
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff")
    }

    private func source(_ relative: String) throws -> String {
        let url = app.appendingPathComponent(relative)
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Fichier introuvable (\(url.path)).")
        }
        return try String(contentsOf: url, encoding: .utf8)
    }

    /// La ligne « Langue » ne renvoie plus aux réglages de l'iPhone : elle ouvre l'écran de l'app.
    func testTheLanguageRowNeverOpensThePhoneSettings() throws {
        let list = try source("Views/Settings/SettingsScreens.swift")
        let start = try XCTUnwrap(list.range(of: "case .language:"))
        let end = try XCTUnwrap(list.range(of: "case .privacy:"))
        let languageCase = String(list[start.lowerBound..<end.lowerBound])

        XCTAssertTrue(languageCase.contains("actions.onOpenLanguage"))
        XCTAssertFalse(languageCase.contains("openSystemSettings"))
        XCTAssertFalse(languageCase.contains("openSettingsURLString"))
        let screen = try source("Views/Settings/LanguageView.swift")
        XCTAssertFalse(screen.contains("openSystemSettings"))
        XCTAssertFalse(screen.contains("openSettingsURLString"))
        // Un cercle radio (miroir d'Android), plus de coche ; le cercle est caché de VoiceOver, la ligne entière est le bouton.
        XCTAssertTrue(screen.contains("LanguageRadioCircle"))
        XCTAssertFalse(screen.contains("checkmark"))
        XCTAssertTrue(screen.contains(".accessibilityHidden(true)"))
        XCTAssertTrue(screen.contains("Spacing.minTouchTarget"))
    }

    /// Les notifications locales, l'e-mail de réinitialisation et les formats lisent la langue CHOISIE, jamais celle du téléphone seule.
    func testTheTextsComputedOutsideTheViewsReadTheChosenLanguage() throws {
        let reconciler = try source("Services/Notification/NotificationReconciler.swift")
        XCTAssertTrue(reconciler.contains("var language: () -> String = { AppLanguage.code }"))
        XCTAssertFalse(reconciler.contains("Locale.preferredLanguages"))
        let reset = try source("Services/Firebase/FirebasePasswordResetGateway.swift")
        XCTAssertTrue(reset.contains("auth.languageCode = AppLanguage.code"))
        XCTAssertFalse(reset.contains("useAppLanguage()"))
        let lifecycle = try source("Views/Notification/NotificationLifecycle.swift")
        XCTAssertTrue(lifecycle.contains("AppLanguage.changed"))
        let l10n = try source("Localization/L10n.swift")
        XCTAssertFalse(l10n.contains("Locale.current"))
    }

    /// Le choix est posé une fois à la racine, et la coque comme chaque écran empilé se refont avec lui, jamais la pile elle-même.
    func testTheLanguageIsAppliedAtTheRootAndTheStackItselfIsNeverRebuilt() throws {
        XCTAssertTrue(try source("MiffApp.swift").contains("AppLanguageHost"))
        XCTAssertTrue(try source("Views/Main/MainShellView.swift").contains(".followsAppLanguage()"))
        let hosts = try source("Views/Miff/MiffHosts.swift")
        XCTAssertEqual(hosts.components(separatedBy: ".followsAppLanguage()").count - 1, 1)
    }
}
