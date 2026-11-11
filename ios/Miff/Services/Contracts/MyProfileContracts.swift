import Foundation

// SÉCURITÉ, pour ce fichier : prénom, nom, numéro et photo sont des données personnelles. Rien ici ne les porte dans un résultat
// d'erreur : un `ProfileSaveResult` ne dit que « comment ça s'est terminé ».

/// Comment s'est terminé l'enregistrement de « Mes informations ».
enum ProfileSaveResult: Equatable {
    /// Le profil ET la fiche de chaque Miff sont écrits.
    case saved
    /// Le profil est écrit, mais la fiche d'au moins une Miff n'a pas pu être atteinte (réseau) : « Enregistrer » de nouveau finit le travail.
    case savedPartial
    /// Pas de réseau : rien n'a été écrit (les écritures sont des transactions, jamais mises en file).
    case offline
    /// Le serveur a répondu « non » (règles, App Check, ancien compte sans `termsVersion`).
    case refused
    case failed
}

/// Changer le profil de la personne connectée (Réglages > Mes informations, ticket `docs/tickets/modifier-infos-perso.md`).
///
/// Le profil vit deux fois (`backend/data-model.md` §2.1 et §2.6) : `users/{uid}` (privé) et une COPIE dans `miffs/{id}/members/{uid}` de
/// chaque Miff, qui est tout ce que voient les autres membres. Un changement est écrit dans les deux, comme sur Android
/// (`MyProfileRepository.kt`).
protocol MyProfileRepository {
    /// Écrit prénom, nom, numéro et photo (les quatre clés, toujours) sur le profil et sur la fiche de chaque Miff. `profile.phone`
    /// est obligatoire (E.164) : sans lui, rien n'est écrit (`.failed`).
    func save(uid: String, profile: ProfileSummary) async -> ProfileSaveResult
}
