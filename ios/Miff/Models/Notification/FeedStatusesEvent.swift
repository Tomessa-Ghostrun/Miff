import Foundation

/// Ce que le Feed dit aux notifications chaque fois que le SERVEUR lui livre les statuts de la Miff affichée (jamais pour une copie du
/// cache : elle ne prouve rien). Aucune position, aucun nom de lieu : seulement ce dont la couleur et les cartes ont besoin.
struct FeedStatusesEvent: Equatable, CustomStringConvertible {
    let miffId: String
    /// Les statuts de la Miff, par identifiant de membre.
    let statuses: [String: MemberStatus]
    /// Mon identifiant.
    let myUid: String?

    /// MON statut, s'il est connu.
    var myStatus: MemberStatus? {
        myUid.flatMap { statuses[$0] }
    }

    var description: String {
        "FeedStatusesEvent(redacted)"
    }
}
