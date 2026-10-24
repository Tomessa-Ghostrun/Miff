#if DEBUG
import FirebaseAppCheck
import FirebaseCore
import Foundation

/// Debug seulement : fournisseur App Check « de débogage » (le simulateur ne peut pas passer App Attest).
/// Au premier lancement, le SDK écrit dans la console Xcode un jeton de débogage (ligne « App Check debug token ») ;
/// il peut être enregistré dans la console Firebase > App Check > Gérer les jetons de débogage.
/// Ce jeton reste PRIVÉ : ne jamais le commiter ni le coller dans un ticket ou une conversation. App Check n'étant
/// pas appliqué, ne pas l'enregistrer fait seulement répondre « pas de jeton » à la vérification du diagnostic.
final class MiffDebugAppCheckProviderFactory: NSObject, AppCheckProviderFactory {
    private let wrapped = AppCheckDebugProviderFactory()

    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        wrapped.createProvider(with: app)
    }
}
#endif
