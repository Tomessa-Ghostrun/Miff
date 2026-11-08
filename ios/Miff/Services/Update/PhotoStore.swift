import Foundation

// SÉCURITÉ ET COÛT, pour ce fichier :
// - Une écriture de l'Update est une TRANSACTION, jamais un lot simple : un lot fait sans réseau est mis en attente et part PLUS TARD,
//   tout seul, ce qui serait une « publication fantôme » (une update qui dit « tout va bien » des heures après coup, ticket R9). Une
//   transaction n'existe qu'en ligne : « pas de confirmation » veut toujours dire « rien d'écrit » (critère U3).
// - MA fiche est LUE dans la transaction, dans chaque Miff du lot : rien n'est écrit là où je ne partage plus (un partage arrêté entre-temps
//   sur un autre téléphone n'est jamais défait), ni là où le texte de consentement que j'ai accepté ne couvre pas la dernière position
//   (version 03 ou plus : les règles refuseraient la photo, donc tout le lot). Coût : UNE lecture par Miff et par publication (quelques-unes
//   par jour), en plus des 3 lectures de règles par Miff.
// - Rien n'est journalisé ; ce qui sort d'ici est un `PhotoCommit` (identifiants de Miffs seulement) ou une erreur que l'appelant réduit à
//   une phrase calme.

/// Ce qu'une transaction de l'Update a fait, Miff par Miff du lot.
struct PhotoCommit: Equatable, CustomStringConvertible {
    /// Écrit.
    let written: [String]
    /// Ma fiche dit que je ne partage plus (ou n'existe pas) : rien n'est écrit là.
    let notSharing: [String]
    /// Je partage, mais sous un texte de consentement qui ne couvre pas la position : rien n'est écrit là.
    let needsAgreement: [String]

    var description: String {
        "PhotoCommit(redacted)"
    }
}

protocol PhotoStore {
    /// UNE transaction pour `miffIds` (5 au plus : 3 lectures de règles par Miff, 20 au plus par lot). `build` reçoit les Miffs où les
    /// écritures sont permises et fait le lot ; Firestore peut le rappeler s'il réessaie. `requireConsent` : exige aussi que le consentement
    /// couvre la position (publier une photo avec son lieu) ; le nettoyage d'une photo expirée ne l'exige pas.
    func commit(
        uid: String,
        miffIds: [String],
        requireConsent: Bool,
        build: @escaping ([String]) -> [DocWrite]
    ) async throws -> PhotoCommit
}

/// Ce que la lecture de MA PROPRE photo a dit (décision D35, 2026-11-06 : quand la date locale est perdue, UN `get` de sa propre photo
/// décide du nettoyage sur `expiresAt`). Jamais de suppression sur un refus des règles.
enum OwnPhotoRead: Equatable {
    case found(createdAtMillis: Int, expiresAtMillis: Int)
    /// Refusée (photo expirée, partage arrêté...), absente ou illisible : on ne conclut RIEN.
    case unreadable
}

protocol OwnPhotoReader {
    func read(miffId: String, uid: String) async -> OwnPhotoRead
}
