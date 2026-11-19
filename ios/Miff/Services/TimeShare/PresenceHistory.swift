import Foundation

// Le journal LOCAL des changements de présence (ticket « Profil, répartition du temps », TIME-3). CONFIDENTIALITÉ : un fait ne porte que
// l'identifiant de la Miff, l'état, l'identifiant du lieu et l'heure (`PresenceFact`) ; AUCUNE coordonnée, AUCUN nom ni adresse de lieu,
// AUCUN identifiant de personne. Rien ne quitte le téléphone : ni Firestore, ni Analytics, ni sauvegarde (fichier exclu des sauvegardes).
// Ce journal est DISTINCT de `DetectionJournal` (journal de recette des builds Debug et Internal, absent du build de publication) : il est
// actif en publication et ne partage aucun code avec lui (critère TS-25).

/// Ce que la lecture du journal rend pour UNE Miff.
enum PresenceHistoryRead: Equatable, CustomStringConvertible {
    /// Les faits de la Miff dans l'ordre où ils ont été enregistrés (aucun : une liste vide).
    case facts([PresenceFact])
    /// Le stockage est illisible pour l'instant (fichier verrouillé, erreur d'entrée-sortie) : la carte le dit calmement et propose de réessayer.
    case unreadable

    var description: String {
        "PresenceHistoryRead(redacted)"
    }
}

/// Le journal de présence (un double en mémoire dans les tests). Utilisable depuis n'importe quel fil : le moteur de suivi écrit depuis un
/// réveil en arrière-plan, l'écran lit depuis le fil principal.
protocol PresenceHistory: AnyObject {
    /// « La présence de cette Miff est devenue (état, lieu) à cet instant » : l'UNIQUE point d'entrée du moteur. Un fait identique au
    /// dernier de la Miff n'est pas gardé (pas de changement) ; une première présence « inconnue » non plus (rien n'a été mesuré).
    func record(_ fact: PresenceFact)

    func read(miffId: String) -> PresenceHistoryRead

    /// Le choix de fenêtre de la personne (7, 14 ou 30 jours), gardé avec le journal et effacé avec lui.
    func windowDays() -> Int
    func setWindowDays(_ days: Int)

    /// Les faits des lieux de cette Miff qui ne sont pas dans cette liste (lieux supprimés : T11) cessent de nommer un lieu : ils deviennent
    /// « inconnue » à la même heure, donc leur temps est NON MESURÉ (jamais compté en « Ailleurs » : l'état d'avant ne se prolonge pas). À
    /// n'appeler qu'avec la liste COMPLÈTE des lieux, lue auprès du serveur.
    func forgetPlaces(miffId: String, keeping placeIds: Set<String>)

    /// Départ, retrait ou suppression d'une Miff : ses faits sont effacés.
    func purge(miffId: String)

    /// La liste des Miffs de la personne est confirmée par le serveur : les faits de toute autre Miff sont effacés.
    func retainOnly(miffIds: Set<String>)

    /// « Effacer mon historique de temps » : tous les faits partent ; le choix de fenêtre reste.
    func eraseFacts()

    /// Déconnexion, suppression du compte, session perdue : tout part, fichier compris.
    func clearAll()
}

/// Le journal qui ne garde rien (doubles de test qui ne parlent pas du journal).
final class NoPresenceHistory: PresenceHistory {
    func record(_ fact: PresenceFact) {}

    func read(miffId: String) -> PresenceHistoryRead {
        .facts([])
    }

    func windowDays() -> Int {
        TimeShareLimits.defaultWindowDays
    }

    func setWindowDays(_ days: Int) {}

    func forgetPlaces(miffId: String, keeping placeIds: Set<String>) {}

    func purge(miffId: String) {}

    func retainOnly(miffIds: Set<String>) {}

    func eraseFacts() {}

    func clearAll() {}
}
