import Foundation

// Les contrats entre le Feed et le monde extérieur (Firestore). Du Swift pur : les vraies implémentations sont dans
// `Services/Firebase/`, les tests utilisent des doubles en mémoire. Le Feed LIT et n'écrit JAMAIS (0 écriture, 0 suppression,
// ticket FEED critère Q1).

// SÉCURITÉ, pour ce fichier : un statut dit où est quelqu'un, une position est la donnée la plus sensible de l'app. Rien de ce qui
// passe ici n'est journalisé, envoyé à Analytics ni mis dans un message d'erreur.

/// Ce que l'écoute des statuts d'une Miff a dit.
enum StatusFeed: Equatable, CustomStringConvertible {
    /// Tous les statuts de la Miff (les clés sont les identifiants des membres). `fromCache` : seul le cache du téléphone en répond
    /// (pas encore de réponse du serveur, ou pas de réseau) : le Feed dit alors « informations de 16 h 02 », jamais « tout va bien »
    /// comme si c'était maintenant.
    case data(byUid: [String: MemberStatus], fromCache: Bool)
    /// L'écoute s'est terminée : les règles ont refusé la lecture (mon propre statut n'est pas encore publié, mon partage est arrêté,
    /// j'ai été retiré) ou autre chose l'a arrêtée. Firestore ne relance pas une écoute terminée sur une erreur : le Feed en rattache
    /// une nouvelle plus tard, jamais en boucle.
    case ended

    var description: String {
        "StatusFeed(redacted)"
    }
}

/// La vue en direct des statuts d'UNE Miff. Froide : l'écoute existe tant que le flux est lu ; l'abandonner la retire.
protocol StatusObserver {
    func observe(miffId: String) -> AsyncStream<StatusFeed>
}

/// La lecture UNIQUE derrière le bouton « Localiser » : un `get` de `positions/{uid}` par toucher, auprès du SERVEUR (jamais du
/// cache : une vieille copie ne doit pas passer pour la position actuelle), sans écoute, jamais une liste.
protocol PositionReader {
    func read(miffId: String, uid: String) async -> PositionLookup
}

/// Ce que la lecture forcée des statuts a dit.
enum ServerRead: Equatable, CustomStringConvertible {
    /// Le serveur a répondu : `statuses` sont ses statuts actuels (nil quand les règles refusent la lecture : mon propre statut n'est
    /// pas encore publié).
    case fresh(statuses: [String: MemberStatus]?)
    /// Le serveur n'a pas pu être joint (ou a répondu autre chose que prévu) : le Feed garde ce qu'il a, daté, et le dit calmement.
    case unreachable

    var description: String {
        "ServerRead(redacted)"
    }
}

/// « Actualiser » (tirer pour actualiser, décision du PM du 2026-11-06) : UNE relecture forcée, auprès du SERVEUR, de ce qui alimente
/// les cartes (la Miff, ses membres, ses lieux, ses statuts). Des lectures seulement ; ce qui est lu entre dans le cache du SDK,
/// donc les écoutes de la Miff livrent les changements d'elles-mêmes.
protocol FeedRefresher {
    /// `readStatuses` est faux tant que les statuts ne sont pas lus du tout (je ne partage pas, ou mon accord est attendu) : les
    /// règles refuseraient et rien n'est voulu, donc ils ne sont pas demandés.
    func refresh(miffId: String, readStatuses: Bool) async -> ServerRead
}
