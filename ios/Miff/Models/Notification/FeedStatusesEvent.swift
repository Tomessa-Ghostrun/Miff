import Foundation

/// Ce que le Feed dit aux notifications chaque fois que le SERVEUR lui livre les statuts de la Miff affichée (jamais pour une copie du
/// cache : elle ne prouve rien). Aucune position, aucun nom de lieu : seulement ce dont la couleur et les cartes ont besoin.
struct FeedStatusesEvent: Equatable, CustomStringConvertible {
    /// Un membre de la Miff : son identifiant et s'il partage (un membre qui ne partage pas est neutre).
    struct Member: Equatable {
        let uid: String
        let sharing: Bool
    }

    let miffId: String
    /// Les statuts de la Miff, par identifiant de membre.
    let statuses: [String: MemberStatus]
    /// Mon identifiant.
    let myUid: String?
    /// Les membres de la Miff (vide quand la Miff n'est pas encore connue).
    var members: [Member] = []

    /// MON statut, s'il est connu.
    var myStatus: MemberStatus? {
        myUid.flatMap { statuses[$0] }
    }

    var description: String {
        "FeedStatusesEvent(redacted)"
    }
}
