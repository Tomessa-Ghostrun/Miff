import UIKit

/// Le petit délégué de l'app, SEULEMENT pour recevoir le résultat de `registerForRemoteNotifications()` (SwiftUI n'a pas d'autre porte). Firebase
/// ne remplace plus ces méthodes en cachette (`FirebaseAppDelegateProxyEnabled = false`) : le jeton d'APNs est transmis à Firebase ici, explicitement,
/// par la fermeture que `PushServices` pose au démarrage.
///
/// SÉCURITÉ : le jeton d'APNs n'est ni journalisé ni gardé. Un échec d'enregistrement n'est pas montré : l'enregistrement est retenté à la
/// prochaine ouverture, et le Feed reste la source de vérité.
final class MiffAppDelegate: NSObject, UIApplicationDelegate {
    /// Posée par `PushServices` au démarrage ; nil tant que rien n'est branché (tests, démarrage sans Firebase).
    static var onAPNsToken: ((Data) -> Void)?

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Self.onAPNsToken?(deviceToken)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // Rien à faire : ni journal (l'erreur peut décrire le jeton), ni message à l'écran.
    }
}
