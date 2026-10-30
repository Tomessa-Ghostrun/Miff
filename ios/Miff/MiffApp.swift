import GoogleSignIn
import SwiftUI
import UIKit

@main
@MainActor
struct MiffApp: App {
    private let environment: AppEnvironment
    /// Lu une fois par lancement : le choix « réduire les animations » donne un logo fixe à la place de l'animation.
    private let reduceMotion: Bool

    init() {
        FirebaseBootstrap.configureIfPossible()
        let environment = AppEnvironment.live()
        // Analytics et Crashlytics démarrent éteints (Info.plist) et le choix gardé sur l'appareil n'est PAS réappliqué :
        // la collecte est mise en pause, et ne s'allume qu'une fois le choix du compte (users/{uid}) lu, ou après le geste
        // « Rejoindre » / « Continuer » (voir `SessionManager`). Une purge du cache Firestore restée en attente est aussi
        // faite ici, avant tout autre accès à Firestore.
        environment.privacySettings.pauseCollection()
        environment.session.start()
        self.environment = environment
        reduceMotion = UIAccessibility.isReduceMotionEnabled
    }

    /// L'animation de lancement joue toujours, sauf pour les captures de la CI (option réservée au build Debug).
    private static var splashEnabled: Bool {
        #if DEBUG || INTERNAL
        return !DiagnosticLaunchOptions.skipSplash
        #else
        return true
        #endif
    }

    var body: some Scene {
        WindowGroup {
            SplashHost(reduceMotion: reduceMotion, isEnabled: Self.splashEnabled) {
                RootView(environment: environment)
            }
            // Apparence claire imposée (les maquettes n'ont pas de mode sombre), en plus de UIUserInterfaceStyle.
            .preferredColorScheme(.light)
            // Retour de la fenêtre de connexion Google.
            .onOpenURL { url in
                _ = GIDSignIn.sharedInstance.handle(url)
            }
        }
    }
}
