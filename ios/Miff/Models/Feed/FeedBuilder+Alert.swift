import Foundation

// ALERTE « Je suis en danger » côté PROCHES (docs/tickets/alerte-danger.md 7.6 et 12 ; miroir de `FeedBuilder.withAlert`, `alertsFirst` et `declaredPages` d'Android).
// Du calcul pur, compilé dans tous les builds : sans la fonction aucune alerte n'est jamais donnée au Feed (`FeedInput.alerts` reste vide), donc rien de ceci ne sert.
//
// Ce que voient les proches de la Miff d'ORIGINE seulement (dans une autre Miff de la même personne : rien) :
// - sa carte est PREMIÈRE, rouge, « Alerte en cours » et « Depuis 15 h 02 » (l'heure absolue, comme le reste de l'app) ;
// - la bulle de la Miff est rouge, sa PREMIÈRE page est l'alerte (« {Prénom} a déclaré une alerte à 15 h 02 », avec « Appeler » et « Contacter »), et elle ne dit
//   JAMAIS « tout va bien » pendant une alerte (la maquette 04 le faisait : écart E3) ;
// - l'alerte l'emporte sur le mode voyage (la couleur reste rouge) ; à la fin, retour au calcul normal ;
// - une alerte arrivée à sa date, ou terminée, ne laisse AUCUNE trace (aucune ligne, aucun message : D19).

extension FeedBuilder {
    /// La carte d'une personne dont l'alerte est en cours : rouge, « Alerte en cours », « Depuis 15 h 02 », hors voyage. Le reste de la carte ne change pas.
    static func withAlert(_ card: FeedMemberCard, _ alert: DangerAlertDoc, _ context: Context) -> FeedMemberCard {
        var next = card
        next.level = .red
        next.label = .alertActive
        next.time = .since(context.moment(alert.startedAtMillis ?? context.now))
        next.traveling = false
        next.alertActive = true
        return next
    }

    /// Les cartes des alertes en cours passent AVANT les autres, la mienne comprise, la plus ancienne d'abord (stable). C'est l'UNIQUE exception à l'ordre des
    /// cartes (FEED-ORDRE, décision D43, jamais réordonné selon l'urgence ; ticket 7.6) : rien d'autre ne réordonne le Feed. Les autres cartes gardent leur ordre.
    static func alertsFirst(_ ordered: [FeedMemberCard], live: [DangerAlertDoc]) -> [FeedMemberCard] {
        guard !live.isEmpty else {
            return ordered
        }
        var rank: [String: Int] = [:]
        for (index, doc) in live.enumerated() {
            rank[doc.uid] = index
        }
        let alerted = ordered.filter { $0.alertActive }.sorted { left, right in
            (rank[left.member.uid] ?? Int.max) < (rank[right.member.uid] ?? Int.max)
        }
        return alerted + ordered.filter { !$0.alertActive }
    }

    /// Une page par alerte en cours, dans l'ordre des alertes (la plus ancienne d'abord) : « {Prénom} a déclaré une alerte à 15 h 02 », avec « Appeler » et « Contacter ».
    static func declaredPages(_ context: Context, cards: [FeedMemberCard], live: [DangerAlertDoc]) -> [AlertPage] {
        let alerted = cards.filter { $0.alertActive }
        let byUid = Dictionary(alerted.map { ($0.member.uid, $0) }, uniquingKeysWith: { first, _ in first })
        return live.compactMap { alert -> AlertPage? in
            guard let card = byUid[alert.uid] else {
                return nil
            }
            let reachable = !card.isMe && card.phone != nil
            var page = AlertPage(
                member: card.member,
                isMe: card.isMe,
                level: .red,
                kind: .declared(at: context.moment(alert.startedAtMillis ?? context.now)),
                canMessage: reachable,
                offlineSince: nil
            )
            page.canCall = reachable
            return page
        }
    }
}
