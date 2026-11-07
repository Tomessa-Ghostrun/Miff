import FirebaseAuth
import Foundation

/// L'adresse e-mail du compte connecté, pour l'afficher en lecture seule dans Réglages > Mon compte. Lue au moment de l'affichage et
/// jamais gardée : ni sur le disque, ni dans Firestore, ni dans un événement ou un journal (ticket Onboarding, critère RG8). Pour un
/// compte « Se connecter avec Apple » c'est l'adresse relais d'Apple, montrée telle quelle.
protocol AccountInfoSource {
    func currentEmail() -> String?
}

struct FirebaseAccountInfo: AccountInfoSource {
    func currentEmail() -> String? {
        guard FirebaseBootstrap.isConfigured else {
            return nil
        }
        let email = Auth.auth().currentUser?.email?.trimmingCharacters(in: .whitespacesAndNewlines)
        return email?.isEmpty == false ? email : nil
    }
}
