import Foundation

// CONFIDENTIALITÉ, pour ce fichier : garde les IDENTIFIANTS des Miffs où la personne partage sous un texte de consentement plus ancien
// et n'a pas encore donné son accord (« En attente de ton accord »). En mémoire seulement, pour la session ; ni nom, ni position.
// Rien n'est lu, rien n'est écrit.

/// Quelles Miffs attendent l'accord de la personne (miroir d'`AwaitingAgreement` d'Android, LOC-2b, maquette 23). Écrit par la porte des
/// autorisations (`PermissionGateViewModel`, qui regarde UNE fois par lancement si le consentement doit être renouvelé) ; lu par le
/// Feed, pour dire la vérité pendant que « Pas maintenant » a été choisi : rien n'est partagé, rien n'est détecté pour ces Miffs.
/// `requestReview()` ramène l'écran de renouvellement (« Revoir et accepter ») : la porte l'écoute.
@MainActor
final class AwaitingAgreement: ObservableObject {
    @Published private(set) var waiting: Set<String> = []
    /// Monte chaque fois que la personne demande à revoir l'écran de renouvellement.
    @Published private(set) var reviewRequests = 0

    func replace(_ miffIds: Set<String>) {
        waiting = miffIds
    }

    func remove(_ miffId: String) {
        waiting.remove(miffId)
    }

    func requestReview() {
        reviewRequests += 1
    }

    /// Fin de session : plus personne n'attend rien.
    func stop() {
        waiting = []
    }
}
