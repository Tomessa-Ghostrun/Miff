#if DEBUG || INTERNAL
import Foundation

/// Options de lancement réservées à la CI et au simulateur (`xcrun simctl launch ... -MiffForceRTL YES`).
/// Elles n'existent que dans la configuration **Debug** : dans le build Internal (TestFlight, appareil réel) et dans
/// le build Release, toutes sont inertes (valeurs fixes), pour qu'aucun argument de lancement ne puisse changer le
/// comportement d'un build distribué.
/// Elles ne contournent aucun contrôle de sécurité : elles changent seulement l'affichage ou lancent le test 1
/// (une lecture non authentifiée, sans aucune écriture).
enum DiagnosticLaunchOptions {
    #if DEBUG
    /// Force la mise en page de droite à gauche (le catalogue n'a pas de langue droite-à-gauche à tester).
    static var forceRightToLeft: Bool {
        UserDefaults.standard.bool(forKey: "MiffForceRTL")
    }

    /// Ouvre directement l'écran d'accueil au lieu du diagnostic.
    static var showHome: Bool {
        UserDefaults.standard.bool(forKey: "MiffShowHome")
    }

    /// Ouvre directement le VRAI parcours de l'app (carrousel, inscription...) au lieu du diagnostic.
    static var showApp: Bool {
        UserDefaults.standard.bool(forKey: "MiffShowApp")
    }

    /// Saute l'animation de lancement (captures de la CI : elle durerait plus longtemps que l'attente avant la capture).
    static var skipSplash: Bool {
        UserDefaults.standard.bool(forKey: "MiffSkipSplash")
    }

    /// Montre un seul écran avec un état fixe (voir `ScreenPreview`), pour les captures de la CI.
    static var preview: String? {
        UserDefaults.standard.string(forKey: "MiffPreview")
    }

    /// Lance le test 1 au démarrage (capture « Règles actives : OK »).
    static var autoRunTest1: Bool {
        UserDefaults.standard.bool(forKey: "MiffAutoTest1")
    }

    /// Section vers laquelle faire défiler l'écran au démarrage (voir `DiagnosticSection`).
    static var scrollTarget: DiagnosticSection? {
        UserDefaults.standard.string(forKey: "MiffScrollTo").flatMap(DiagnosticSection.init(rawValue:))
    }
    #else
    static let forceRightToLeft = false
    static let showHome = false
    static let showApp = false
    static let skipSplash = false
    static let preview: String? = nil
    static let autoRunTest1 = false
    static let scrollTarget: DiagnosticSection? = nil
    #endif
}

/// Sections de l'écran de diagnostic (ancres de défilement).
enum DiagnosticSection: String {
    case build
    case test1
    case test2
    case appCheck
    case privacy
    case gallery
    case language
}
#endif
