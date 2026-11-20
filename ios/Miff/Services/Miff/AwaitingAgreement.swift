import Foundation

// CONFIDENTIALITÉ, pour ce fichier : garde les IDENTIFIANTS des Miffs où la personne partage sous un texte de consentement plus ancien
// et n'a pas encore donné son accord (« En attente de ton accord »), et ceux où elle partage sous la version 3 (rien n'est en pause).
// En mémoire seulement, pour la session ; ni nom, ni position. Rien n'est lu, rien n'est écrit.

/// Quelles Miffs attendent l'accord de la personne (miroir d'`AwaitingAgreement` d'Android, LOC-2b, maquette 23). Écrit par la porte des
/// autorisations (`PermissionGateViewModel`, qui regarde UNE fois par lancement si le consentement doit être renouvelé) ; lu par le
/// Feed, pour dire la vérité pendant que « Pas maintenant » a été choisi : rien n'est partagé, rien n'est détecté pour ces Miffs.
/// `requestReview()` ramène l'écran de renouvellement (« Revoir et accepter ») : la porte l'écoute.
@MainActor
final class AwaitingAgreement: ObservableObject {
    /// Les Miffs dont le partage est RÉELLEMENT en pause (texte qui ne couvre pas la position : versions 1 et 2). Seules celles-ci
    /// donnent une pastille, une carte ou une bulle « En attente » (CONS-4-I, critère CA-10).
    @Published private(set) var waiting: Set<String> = []
    /// CONS-4-I : les Miffs où la personne partage sous le texte de la version 3 (toujours valable pour la position et la photo) et
    /// n'a pas encore lu la carte calme « Une précision ». RIEN n'est en pause pour elles : elles ne sont PAS dans `waiting`.
    /// Réglages > Confidentialité propose « Revoir et accepter ».
    @Published private(set) var clarifying: Set<String> = []
    /// Monte chaque fois que la personne demande à revoir l'écran de renouvellement (ou la carte « Une précision »).
    @Published private(set) var reviewRequests = 0

    func replace(_ miffIds: Set<String>) {
        waiting = miffIds
    }

    func replaceClarifying(_ miffIds: Set<String>) {
        clarifying = miffIds
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
        clarifying = []
    }
}
