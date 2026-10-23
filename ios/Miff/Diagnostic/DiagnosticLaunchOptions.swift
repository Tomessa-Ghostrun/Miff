#if DEBUG || INTERNAL
import Foundation

/// Options de lancement réservées aux builds Debug et Internal, utilisées par la CI pour produire des captures
/// d'écran (`xcrun simctl launch ... -MiffForceRTL YES`). Absentes du build Release (fichier non compilé).
/// Elles ne contournent aucun contrôle de sécurité : elles changent seulement l'affichage ou lancent le test 1
/// (une lecture non authentifiée, sans aucune écriture).
enum DiagnosticLaunchOptions {
    /// Force la mise en page de droite à gauche (le catalogue n'a pas de langue droite-à-gauche à tester).
    static var forceRightToLeft: Bool {
        UserDefaults.standard.bool(forKey: "MiffForceRTL")
    }

    /// Ouvre directement l'écran d'accueil au lieu du diagnostic.
    static var showHome: Bool {
        UserDefaults.standard.bool(forKey: "MiffShowHome")
    }

    /// Lance le test 1 au démarrage (capture « Règles actives : OK »).
    static var autoRunTest1: Bool {
        UserDefaults.standard.bool(forKey: "MiffAutoTest1")
    }

    /// Section vers laquelle faire défiler l'écran au démarrage (voir `DiagnosticSection`).
    static var scrollTarget: DiagnosticSection? {
        UserDefaults.standard.string(forKey: "MiffScrollTo").flatMap(DiagnosticSection.init(rawValue:))
    }
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
