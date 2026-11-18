import Foundation

// Le tri des cartes du Feed par date de dernière update (ticket FEED-ORDRE) : séparé de `FeedBuilder.swift` pour garder chaque type sous la limite de longueur de SwiftLint.
extension FeedBuilder {
    /// Les cartes du Feed, de la dernière update la plus récente à la plus ancienne (ticket FEED-ORDRE, décisions du §3). La date est
    /// `status.lastUpdateAt`, lue DANS les statuts déjà écoutés (aucune lecture de plus). Ma carte est triée comme les autres. Sans date
    /// exploitable (voir `orderDate`) : ces membres suivent tous les autres, dans l'ordre d'arrivée (la liste des membres).
    static func sortedByLastUpdate(
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
                lastUpdateMillis: byUid[member.uid].flatMap { orderDate($0, statuses: statuses) }
            )
        }
        return FeedOrder.sorted(entries).compactMap { byUid[$0] }
    }

    /// La date de tri d'une carte (ticket FEED-ORDRE, §3 #1 et #3) : `lastUpdateAt` du statut, SAUF pour les cartes sans date exploitable
    /// (nil, après toutes les autres) : « Partage désactivé », « Informations masquées », mon accord attendu. Une carte « Pas encore de
    /// nouvelles » ou « Voyage terminé » n'a de date QUE si une update a été publiée APRÈS le début de son état : `lastUpdateAt` strictement
    /// plus récent que `presenceAt`. C'est le cas d'une update manuelle avant tout départ d'un lieu (`presence = unknown`, l'étiquette ne
    /// change pas). Pas de date quand les deux sont égaux (premier statut, à l'activation du partage : les deux valent l'heure du serveur),
    /// quand `lastUpdateAt` est plus ancien (fin d'un voyage : `lastUpdateAt` conservé, `presenceAt` récent) ou qu'une des deux manque :
    /// jamais une date qu'on ne peut pas justifier.
    private static func orderDate(_ card: FeedMemberCard, statuses: [String: MemberStatus]) -> Int? {
        guard let status = statuses[card.member.uid] else {
            return nil
        }
        switch card.label {
        case .sharingStopped, .newsHidden, .waitingAgreement:
            return nil
        case .noNews, .travelEnded:
            guard let update = status.lastUpdateAtMillis, let start = status.presenceAtMillis, update > start else {
                return nil
            }
            return update
        default:
            return status.lastUpdateAtMillis
        }
    }
}
