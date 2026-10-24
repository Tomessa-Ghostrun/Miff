import SwiftUI

@main
struct MiffApp: App {
    private let environment: AppEnvironment

    init() {
        FirebaseBootstrap.configureIfPossible()
        let environment = AppEnvironment.live()
        // Analytics et Crashlytics démarrent éteints (Info.plist) ; on réapplique ici le choix enregistré de
        // l'utilisateur (éteint par défaut).
        environment.privacySettings.applyStoredChoices()
        self.environment = environment
    }

    var body: some Scene {
        WindowGroup {
            RootView(environment: environment)
                // Apparence claire imposée (les maquettes n'ont pas de mode sombre), en plus de UIUserInterfaceStyle.
                .preferredColorScheme(.light)
        }
    }
}
