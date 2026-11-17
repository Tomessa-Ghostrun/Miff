import FirebaseMessaging
import Foundation
import UIKit

// SÉCURITÉ, pour ce fichier (le SEUL de l'app qui importe Firebase Messaging) :
// - le service de notification est ÉTEINT par défaut (Info.plist : `FirebaseMessagingAutoInitEnabled = false`) : aucun jeton n'est demandé à
//   Firebase ni à Apple avant que la personne ait répondu à l'écran qui dit que Miff enregistre un identifiant technique de son téléphone ET
//   accordé l'autorisation du système. Il n'est allumé ici (`enableAndFetch`) que par le registraire, une fois ces deux conditions réunies ;
// - la substitution automatique des méthodes de l'app par Firebase est COUPÉE (Info.plist : `FirebaseAppDelegateProxyEnabled = false`) : le
//   jeton d'APNs est transmis ici, explicitement, par `MiffAppDelegate` ; rien n'est branché en cachette sur les notifications ;
// - le jeton ne passe par aucun journal, événement Analytics ni message d'erreur ; une erreur de Firebase est avalée sans être lue.

/// Reçoit les jetons que Firebase génère (au premier allumage, puis à chaque rotation) et les confie au registraire.
final class PushMessagingBridge: NSObject, MessagingDelegate {
    var onToken: ((String) -> Void)?

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken, !fcmToken.isEmpty else {
            return
        }
        onToken?(fcmToken)
    }
}

@MainActor
final class FirebasePushTokenSource: PushTokenSource {
    /// Le plus longtemps que la sortie attend Firebase pour invalider le jeton (hors connexion, le désenregistrement local coupe la livraison).
    private let invalidationTimeoutSeconds: TimeInterval = 3
    private let bridge = PushMessagingBridge()

    /// Branche le récepteur de jetons. N'allume rien : le jeton n'existe pas tant que `enableAndFetch` n'a pas été appelé.
    func attach(onToken: @escaping (String) -> Void) {
        bridge.onToken = onToken
        guard FirebaseBootstrap.isConfigured else {
            return
        }
        Messaging.messaging().delegate = bridge
    }

    /// Le jeton d'APNs que le système vient de donner à l'app : confié à Firebase, qui génère alors le jeton de notification.
    func setAPNsToken(_ deviceToken: Data) {
        guard FirebaseBootstrap.isConfigured else {
            return
        }
        Messaging.messaging().apnsToken = deviceToken
    }

    func enableAndFetch() async -> String? {
        guard FirebaseBootstrap.isConfigured else {
            return nil
        }
        let messaging = Messaging.messaging()
        messaging.isAutoInitEnabled = true
        // À chaque ouverture : le système peut avoir changé le jeton d'APNs. Le résultat arrive à `MiffAppDelegate`.
        UIApplication.shared.registerForRemoteNotifications()
        return messaging.fcmToken
    }

    func disable() async {
        if FirebaseBootstrap.isConfigured {
            // D'abord le démarrage automatique (sans lui, la suppression du jeton le ferait renaître), puis l'invalidation chez Firebase.
            Messaging.messaging().isAutoInitEnabled = false
            do {
                try await timedOperation(seconds: invalidationTimeoutSeconds) {
                    try await Messaging.messaging().deleteToken()
                }
            } catch {
                // Hors connexion ou lenteur : le désenregistrement auprès d'APNs ci-dessous coupe quand même la livraison sur ce téléphone.
            }
        }
        UIApplication.shared.unregisterForRemoteNotifications()
    }
}
