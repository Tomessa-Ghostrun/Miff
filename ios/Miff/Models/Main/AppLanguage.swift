import Foundation

// CONFIDENTIALITÉ, pour ce fichier : le choix de langue est une préférence d'affichage gardée SUR L'APPAREIL (UserDefaults, une seule valeur :
// le code de la langue (« fr », « en », « es », « it », « pt », « de ») ou « system »). Ce n'est pas une donnée personnelle ; elle n'est ni
// envoyée au serveur, ni liée au compte, ni journalisée, ni jamais un paramètre d'événement Analytics.

/// Ce que la personne choisit dans Réglages > Langue (miroir de `AppLanguage` d'Android : une langue de l'app, ou « la langue du téléphone »).
/// Les langues sont celles du registre `docs/contracts/i18n-languages.json` (source unique ; le job « Parité des clés de traduction » vérifie que
/// ce fichier, `CFBundleLocalizations` et le catalogue disent la même chose). Elles sont écrites dans l'ORDRE ALPHABÉTIQUE DE LEUR NOM, écrit dans
/// leur langue (Deutsch, English, Español, Français, Italiano, Português : ticket I18N-4L §4.6), puis « langue du téléphone » en dernier.
///
/// `pt` est le portugais du PORTUGAL (décision du ticket §5) : un téléphone en pt-BR ou en pt-PT affiche `pt`. Une autre variante régionale
/// (`pt-BR`) serait un jeu de textes distinct, ajouté plus tard.
///
/// POUR AJOUTER UNE LANGUE : l'entrée du registre, un cas ci-dessous dont la valeur est le code (`case spanish = "es"`, AVANT `system`, à sa place
/// alphabétique), son libellé dans `LanguageLabels` (le compilateur refuse un `switch` incomplet ; nom écrit dans sa propre langue, clé
/// `ios_settings_language_*`), le code dans `CFBundleLocalizations` (`project.yml`) et les textes dans le catalogue. Rien d'autre : la liste de
/// l'écran, la lecture du choix gardé et la langue effective se déduisent de `allCases`.
///
/// Une langue n'est PROPOSÉE dans l'écran qu'une fois ses textes dans l'app (`AppLanguageController.offeredChoices`) : tant que le catalogue n'a
/// aucun texte pour elle, personne ne peut la choisir et un téléphone dans cette langue reste en anglais.
enum AppLanguageChoice: String, CaseIterable, Equatable {
    case german = "de"
    case english = "en"
    case spanish = "es"
    case french = "fr"
    case italian = "it"
    case portuguese = "pt"
    case system

    /// Lit la valeur gardée sur l'appareil : le code d'une langue de l'app, exactement (« fr » ; la casse et les espaces autour sont tolérés). Une
    /// valeur vide, inconnue ou écrite par une version future (« pt-BR », qui serait un jeu de textes à part) donne « langue du téléphone » : elle
    /// ne casse jamais l'écran. L'app n'écrit jamais autre chose que ces codes.
    static func from(stored: String?) -> AppLanguageChoice {
        guard let stored else {
            return .system
        }
        let cleaned = stored.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return allCases.first { $0 != .system && $0.rawValue == cleaned } ?? .system
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
    /// Les langues dont l'app a les textes (`Bundle.main.localizations`, réduites à celles qui ont leur `xx.lproj`).
    var available: () -> [String]
    /// Le paquet de textes d'une langue (`fr.lproj`), nil s'il n'existe pas.
    var languageBundle: (String) -> Bundle?

    static let live = AppLanguageEnvironment(
        preferred: { Locale.preferredLanguages },
        // Une langue déclarée dans `CFBundleLocalizations` mais sans textes encore (`planned` au registre) n'est PAS parlée par l'app : un
        // téléphone dans cette langue reste en anglais (sinon la langue effective dirait « es » avec des textes anglais).
        available: { Bundle.main.localizations.filter { Bundle.main.path(forResource: $0, ofType: "lproj") != nil } },
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
    /// Le code effectif (« fr », « en », « es »...), jamais nil : une langue de l'app, l'anglais quand le téléphone parle une autre langue.
    let code: String
    let locale: Locale
    let bundle: Bundle

    static func make(choice: AppLanguageChoice, environment: AppLanguageEnvironment = .live) -> AppLanguageState {
        guard let forced = choice.forcedCode else {
            let code = AppLanguageName.code(preferred: environment.preferred(), available: environment.available())
            // Le portugais de l'app est celui du PORTUGAL, même sur un téléphone en pt-BR : ses pluriels (« 0 dias », le zéro n'est pas un singulier
            // au Portugal, contrairement au Brésil) et ses dates suivent `pt_PT`. Les autres langues gardent les formats du téléphone.
            let locale: Locale = code == "pt" ? formattingLocale(code: code) : .autoupdatingCurrent
            return AppLanguageState(choice: choice, code: code, locale: locale, bundle: .main)
        }
        // Paquet absent (ne doit pas arriver) : le paquet principal, donc la langue du téléphone, jamais des clés brutes.
        return AppLanguageState(
            choice: choice,
            code: forced,
            locale: formattingLocale(code: forced),
            bundle: environment.languageBundle(forced) ?? .main
        )
    }

    /// La locale des dates, des nombres et des pluriels d'une langue de l'app : son code, sauf `pt` (portugais du Portugal, `pt_PT` : règle de
    /// pluriel et formats du Portugal, pas ceux du Brésil que `pt` seul désigne pour le système).
    static func formattingLocale(code: String) -> Locale {
        Locale(identifier: code == "pt" ? "pt_PT" : code)
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

    /// Les choix montrés dans Réglages > Langue, dans l'ordre de `allCases` : « langue du téléphone », et chaque langue dont les textes sont dans
    /// l'app (son `xx.lproj` existe). Le choix en cours y est toujours, même si ses textes ont disparu (version plus ancienne).
    var offeredChoices: [AppLanguageChoice] {
        AppLanguageChoice.allCases.filter {
            $0 == .system || $0 == state.choice || environment.languageBundle($0.rawValue) != nil
        }
    }

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
