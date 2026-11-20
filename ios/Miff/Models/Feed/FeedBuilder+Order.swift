import Foundation

// Le tri des cartes du Feed par date de la dernière photo (ticket FEED-ORDRE-PHOTO) : séparé de `FeedBuilder.swift` pour garder chaque type sous la limite de longueur de SwiftLint.
extension FeedBuilder {
    /// Les cartes du Feed, de la dernière photo publiée la plus récente à la plus ancienne (ticket FEED-ORDRE-PHOTO, décisions du §3). La date est
    /// `status.photoAt`, lue DANS les statuts déjà écoutés (aucune lecture de plus) ; aucun autre signal ne déplace une carte. Ma carte est triée
    /// comme les autres. Sans date exploitable (voir `orderDate`) : ces membres suivent tous les autres, dans l'ordre d'arrivée (la liste des membres).
    static func sortedByPhoto(
        _ members: [MiffMember],
        cards: [FeedMemberCard],
        statuses: [String: MemberStatus]
    ) -> [FeedMemberCard] {
        var byUid: [String: FeedMemberCard] = [:]
        for card in cards {
            byUid[card.member.uid] = card
        }
        let entries = members.map { member in
            FeedOrderEntry(
                uid: member.uid,
                photoMillis: byUid[member.uid].flatMap { orderDate($0, statuses: statuses) }
            )
        }
        return FeedOrder.sorted(entries).compactMap { byUid[$0] }
    }

    /// La date de tri d'une carte (ticket FEED-ORDRE-PHOTO, §3 #1, #3 et #10) : `photoAt` du statut, SAUF pour les cartes sans date (nil, après
    /// toutes les autres) : « Partage désactivé », « Informations masquées », mon accord attendu (leur statut n'est pas lu, même s'il en reste un
    /// avec une `photoAt`). Toutes les autres étiquettes (« Pas encore de nouvelles », voyage, voyage terminé, position, vert, orange, rouge)
    /// sont classées par `photoAt` seule : ni `lastUpdateAt`, ni `presenceAt`, ni `hasPhoto` n'y changent rien, et jamais une date déduite
    /// d'un autre champ quand `photoAt` manque.
    private static func orderDate(_ card: FeedMemberCard, statuses: [String: MemberStatus]) -> Int? {
        guard let status = statuses[card.member.uid] else {
            return nil
        }
        switch card.label {
        case .sharingStopped, .newsHidden, .waitingAgreement:
            return nil
        default:
            return status.photoAtMillis
        }
    }
}
