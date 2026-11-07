import FirebaseAppCheck
import FirebaseCore
import Foundation

/// Fournisseur App Check de production : App Attest (iPhone réel, build signé avec la capacité « App Attest »).
/// Mode « non appliqué » côté Firebase pour l'instant : un échec d'obtention du jeton n'empêche pas les lectures.
final class MiffAppAttestProviderFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        AppAttestProvider(app: app)
    }
}

enum AppCheckFactory {
    /// Debug : fournisseur de débogage (simulateur, qui ne peut pas passer App Attest).
    /// Internal et Release : App Attest.
    static func make() -> AppCheckProviderFactory {
        #if DEBUG
        return MiffDebugAppCheckProviderFactory()
        #else
        return MiffAppAttestProviderFactory()
        #endif
    }
}
