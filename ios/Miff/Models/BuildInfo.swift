import Foundation

/// Identité du build affichée à l'écran : version marketing, numéro de build et configuration Xcode.
///
/// Les valeurs viennent de l'`Info.plist` (générée par XcodeGen à partir de `MARKETING_VERSION`,
/// `CURRENT_PROJECT_VERSION` et `CONFIGURATION`). Le numéro de build est injecté par la CI (numéro
/// d'exécution du workflow), ce qui permet de reconnaître sur l'iPhone quel build TestFlight on regarde.
struct BuildInfo: Equatable {
    /// Valeur affichée quand la clé est absente ou vide : l'écran ne plante jamais.
    static let placeholder = "?"

    let version: String
    let build: String
    let configuration: String

    init(version: String, build: String, configuration: String = BuildInfo.placeholder) {
        self.version = BuildInfo.clean(version)
        self.build = BuildInfo.clean(build)
        self.configuration = BuildInfo.clean(configuration)
    }

    init(infoDictionary: [String: Any]?) {
        self.init(
            version: infoDictionary?["CFBundleShortVersionString"] as? String ?? "",
            build: infoDictionary?["CFBundleVersion"] as? String ?? "",
            configuration: infoDictionary?["MiffBuildConfiguration"] as? String ?? ""
        )
    }

    /// Build en cours d'exécution (celui de l'app, pas celui du bundle de tests).
    static var current: BuildInfo {
        BuildInfo(infoDictionary: Bundle.main.infoDictionary)
    }

    private static func clean(_ value: String) -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? placeholder : trimmed
    }
}

/// Ce que contient CE build : le diagnostic interne n'existe que dans les configurations Debug et Internal
/// (compilation conditionnelle : en Release, son code n'est même pas compilé).
enum BuildFlavor {
    static var includesDiagnostic: Bool {
        #if DEBUG || INTERNAL
        return true
        #else
        return false
        #endif
    }

    /// La fenêtre d'avis (docs/tickets/pop-ins-avis.md) : présente seulement quand la condition de compilation `REVIEW_PROMPT` existe
    /// (project.yml : Debug et Internal oui, Release non). Même drapeau que `BuildConfig.REVIEW_PROMPT` d'Android.
    /// ALERTE « Je suis en danger » (docs/tickets/alerte-danger.md) : présente seulement quand la condition de compilation `ALERT_DANGER` existe
    /// (project.yml : Debug et Internal oui, Release non). Même drapeau que `BuildConfig.ALERT_DANGER` d'Android. Éteint, aucun type de la fonction
    /// n'est compilé et les lots d'arrêt du partage, de départ, de retrait et de suppression ne portent rien de plus (contrôle : ci/check-binaries.sh).
    static var includesAlertDanger: Bool {
        #if ALERT_DANGER
        return true
        #else
        return false
        #endif
    }

    /// CNC-I (docs/tickets/centre-notifications-complet.md) : la LECTURE du journal des notifications du serveur (`users/{uid}/notifications`) : présente seulement
    /// quand la condition de compilation `NOTIF_JOURNAL` existe (project.yml : Debug et Internal oui, Release non). Même drapeau que `BuildConfig.NOTIF_JOURNAL`
    /// d'Android. Éteinte, la passerelle Firestore n'est même pas compilée : rien n'est lu ni supprimé sur le serveur (le reste du centre fonctionne : avatars,
    /// logo, lignes locales, messages vus au premier plan ou touchés). À allumer en Release SEULEMENT après le déploiement des règles, de la règle d'expiration et des
    /// fonctions par le PM (ordre du ticket §8.9), avec `MIFF_ALLOW_NOTIF_JOURNAL_IN_RELEASE=1` pour le contrôle des binaires (ios/ci/check-binaries.sh).
    static var includesNotificationJournal: Bool {
        #if NOTIF_JOURNAL
        return true
        #else
        return false
        #endif
    }

    /// L'ACHAT réel de Miff+ (docs/tickets/premium-achat-reel.md, SRV-8I) : présent seulement quand la condition de compilation `PREMIUM_PURCHASE` existe (project.yml : Debug oui ;
    /// Internal et Release NON par défaut, seulement par le réglage explicite `MIFF_PREMIUM_CONDITIONS`). Même drapeau que `BuildConfig.PREMIUM_PURCHASE` d'Android. Éteint :
    /// « Miff+ arrive bientôt », aucune connexion à l'App Store, aucune ligne « Abonnement » dans les Réglages, aucun appel au serveur d'achat.
    static var includesPremiumPurchase: Bool {
        #if PREMIUM_PURCHASE
        return true
        #else
        return false
        #endif
    }

    /// Le FAUX magasin (`BILLING_FAKE`, Debug et, sur demande, Internal) : jamais en Release. Implique l'achat allumé.
    static var includesBillingFake: Bool {
        #if BILLING_FAKE
        return true
        #else
        return false
        #endif
    }

    static var includesReviewPrompt: Bool {
        #if REVIEW_PROMPT
        return true
        #else
        return false
        #endif
    }
}
