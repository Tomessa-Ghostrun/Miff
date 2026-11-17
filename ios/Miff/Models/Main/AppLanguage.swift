import Foundation

// CONFIDENTIALITÉ, pour ce fichier : le choix de langue est une préférence d'affichage gardée SUR L'APPAREIL (UserDefaults, une seule valeur :
// « fr », « en » ou « system »). Ce n'est pas une donnée personnelle ; elle n'est ni envoyée au serveur, ni liée au compte, ni journalisée,
// ni jamais un paramètre d'événement Analytics.

/// Ce que la personne choisit dans Réglages > Langue (miroir de `AppLanguage` d'Android : français, anglais, ou « la langue du téléphone »).
/// Rien d'autre n'est proposé : ajouter une langue est une décision produit (relecture du ton, des textes légaux, des captures).
enum AppLanguageChoice: String, CaseIterable, Equatable {
    case french = "fr"
    case english = "en"
    case system

    /// Lit la valeur gardée sur l'appareil. Seule la langue compte (« fr-FR » est le français) ; une valeur vide, inconnue ou écrite par
    /// une version future donne « langue du téléphone » : elle ne casse jamais l'écran.
    static func from(stored: String?) -> AppLanguageChoice {
        guard let stored else {
            return .system
        }
        let cleaned = stored.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let language = cleaned.split(whereSeparator: { $0 == "-" || $0 == "_" }).first.map(String.init) ?? ""
        return [AppLanguageChoice.french, .english].first { $0.rawValue == language } ?? .system
    }

    /// Le code de langue imposé, ou nil quand c'est le téléphone qui décide.
    var forcedCode: String? {
        self == .system ? nil : rawValue
    }
}

/// Où le choix est gardé. Un seul réglage, par appareil.
protocol AppLanguageStoring {
    func load() -> String?
    func save(_ value: String)
}

struct UserDefaultsLanguageStore: AppLanguageStoring {
    static let key = "miff.appLanguage"
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func load() -> String? {
        #if DEBUG
        // Captures de la CI seulement (build Debug : `-MiffAppLanguage en`) : la langue est imposée pour ce lancement, sans rien écrire.
        if let forced = defaults.string(forKey: "MiffAppLanguage") {
            return forced
        }
        #endif
        return defaults.string(forKey: Self.key)
    }

    func save(_ value: String) {
        defaults.set(value, forKey: Self.key)
    }
}

/// Ce dont la résolution de la langue a besoin du système (des doubles dans les tests).
struct AppLanguageEnvironment {
    /// Les langues de la personne dans l'ordre (`Locale.preferredLanguages`).
    var preferred: () -> [String]
    /// Les langues déclarées par l'app (`Bundle.main.localizations`).
    var available: () -> [String]
    /// Le paquet de textes d'une langue (`fr.lproj`), nil s'il n'existe pas.
    var languageBundle: (String) -> Bundle?

    static let live = AppLanguageEnvironment(
        preferred: { Locale.preferredLanguages },
        available: { Bundle.main.localizations },
        languageBundle: { code in
            Bundle.main.path(forResource: code, ofType: "lproj").flatMap { Bundle(path: $0) }
        }
    )
}

/// La langue effective de l'app à un instant : le choix, le code résolu, la locale (dates, nombres) et le paquet de textes.
/// Pour « langue du téléphone » : exactement ce que faisait l'app avant ce choix (paquet principal, locale du téléphone), donc la
/// pseudo-langue allongée, le repli anglais et les captures de la CI ne changent pas.
struct AppLanguageState {
    let choice: AppLanguageChoice
    /// Le code effectif (« fr » ou « en »), jamais nil : le français ou l'anglais, même quand le téléphone parle une autre langue.
    let code: String
    let locale: Locale
    let bundle: Bundle

    static func make(choice: AppLanguageChoice, environment: AppLanguageEnvironment = .live) -> AppLanguageState {
        guard let forced = choice.forcedCode else {
            let code = AppLanguageName.code(preferred: environment.preferred(), available: environment.available())
            return AppLanguageState(choice: choice, code: code, locale: .autoupdatingCurrent, bundle: .main)
        }
        // Paquet absent (ne doit pas arriver) : le paquet principal, donc la langue du téléphone, jamais des clés brutes.
        return AppLanguageState(
            choice: choice,
            code: forced,
            locale: Locale(identifier: forced),
            bundle: environment.languageBundle(forced) ?? .main
        )
    }
}

/// La langue en cours, lisible de partout (y compris hors de toute vue : textes des notifications locales, formats de dates, e-mail de
/// réinitialisation). Un instantané protégé par un verrou : `L10n` peut être appelé de n'importe quel fil.
enum AppLanguage {
    /// Publiée à chaque changement : les notifications locales sont reprogrammées dans la nouvelle langue.
    static let changed = Notification.Name("com.tomessa.miff.appLanguageChanged")

    private static let lock = NSLock()
    /// Lu au premier accès, depuis l'appareil : même un réveil en arrière-plan (sans écran) parle la langue choisie.
    private static var storage = AppLanguageState.make(
        choice: AppLanguageChoice.from(stored: UserDefaultsLanguageStore().load())
    )

    static var state: AppLanguageState {
        lock.lock()
        defer { lock.unlock() }
        return storage
    }

    static var code: String { state.code }
    static var locale: Locale { state.locale }
    static var bundle: Bundle { state.bundle }

    static func apply(_ state: AppLanguageState) {
        lock.lock()
        storage = state
        lock.unlock()
    }
}

/// Le choix de langue : lu au lancement, changé depuis Réglages > Langue, appliqué À CHAUD. Les écrans qui calculent des textes
/// (`L10n`) lisent `AppLanguage` ; ceux qui en dépendent dans leur mise en page observent ce contrôleur (`followsAppLanguage`).
/// Le choix se fait toujours depuis l'écran (fil principal) : le contrôleur n'est pas lié à un acteur pour pouvoir servir d'argument par défaut.
final class AppLanguageController: ObservableObject {
    /// Le contrôleur de l'app. Les tests construisent le leur, avec un stockage en mémoire.
    static let shared = AppLanguageController(publishesGlobally: true)

    @Published private(set) var state: AppLanguageState
    private let store: AppLanguageStoring
    private let environment: AppLanguageEnvironment
    private let publishesGlobally: Bool

    /// `publishesGlobally` : le contrôleur de l'app seul met à jour `AppLanguage` (lu par `L10n`) et prévient les notifications.
    init(
        store: AppLanguageStoring = UserDefaultsLanguageStore(),
        environment: AppLanguageEnvironment = .live,
        publishesGlobally: Bool = false
    ) {
        self.store = store
        self.environment = environment
        self.publishesGlobally = publishesGlobally
        let stored = AppLanguageChoice.from(stored: store.load())
        let initial = AppLanguageState.make(choice: stored, environment: environment)
        state = initial
        if publishesGlobally {
            AppLanguage.apply(initial)
        }
    }

    var choice: AppLanguageChoice { state.choice }

    /// Garde le choix sur l'appareil et l'applique tout de suite. Choisir ce qui est déjà choisi ne fait rien.
    func select(_ choice: AppLanguageChoice) {
        guard choice != state.choice else {
            return
        }
        store.save(choice.rawValue)
        let next = AppLanguageState.make(choice: choice, environment: environment)
        state = next
        if publishesGlobally {
            AppLanguage.apply(next)
            NotificationCenter.default.post(name: AppLanguage.changed, object: nil)
        }
    }
}
