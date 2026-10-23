import FirebaseAppCheck
import FirebaseCore
import Foundation

/// Démarrage de Firebase. Ordre IMPORTANT : le fournisseur App Check doit être installé AVANT
/// `FirebaseApp.configure()`.
enum FirebaseBootstrap {
    /// Vrai quand l'app tourne comme hôte des tests unitaires : on ne configure alors pas Firebase (les tests
    /// n'ont besoin d'aucun réseau et n'envoient rien).
    static var isRunningUnitTests: Bool {
        ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
            || NSClassFromString("XCTestCase") != nil
    }

    static var isConfigured: Bool {
        FirebaseApp.app() != nil
    }

    /// Fichier de configuration Firebase présent dans l'app ? (Il n'est jamais dans le dépôt : la CI le recrée.)
    static var hasConfigurationFile: Bool {
        Bundle.main.url(forResource: "GoogleService-Info", withExtension: "plist") != nil
    }

    /// Identifiant du projet Firebase lu dans la configuration embarquée (nil si Firebase n'est pas configuré).
    static var projectId: String? {
        FirebaseApp.app()?.options.projectID
    }

    static func configureIfPossible() {
        guard !isRunningUnitTests, hasConfigurationFile, !isConfigured else {
            return
        }
        AppCheck.setAppCheckProviderFactory(AppCheckFactory.make())
        FirebaseApp.configure()
    }
}
