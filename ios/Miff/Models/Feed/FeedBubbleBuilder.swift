import Foundation

// La bulle de la Miff et le carrousel d'alertes (ticket FEED, critères B1 à B6) : la suite de `FeedBuilder`, dans son propre fichier
// pour rester sous la limite de longueur d'un type. Même logique, mêmes cas de test.

extension FeedBuilder {
    // MARK: - La bulle

    static func bubble(
        _ context: Context,
        cards: [FeedMemberCard],
        latestUpdate: Int?,
        miffLevel: StatusLevel
    ) -> Bubble {
        if context.input.waitingAgreement {
            return .awaitingAgreement
        }
        if !context.mySharing {
            return .sharingStopped
        }
        let pages = alertPages(context, cards: cards, latestUpdate: latestUpdate)
        if !pages.isEmpty {
            return .alerts(pages: pages, level: miffLevel)
        }
        let others = cards.filter { !$0.isMe }
        if others.isEmpty {
            return .alone
        }
        let knownOthers = others.filter { $0.level != .neutral }
        if context.hidden || knownOthers.isEmpty {
            return .noNews
        }
        // Pas d'alerte : tout membre connu est vert. La « dernière nouvelle » est la plus récente des AUTRES (ma propre update ne rassure jamais).
        let latest = knownOthers
            .compactMap { context.statuses[$0.member.uid]?.referenceMillis }
            .max()
            .map { context.age($0) }
        let known = cards.filter { $0.level != .neutral }.count
        let someSilent = known < cards.count
        return .allGood(
            latestNews: latest,
            answered: someSilent ? known : nil,
            total: someSilent ? cards.count : nil
        )
    }

    private struct SortableAlert {
        let page: AlertPage
        let reference: Int
        let index: Int
    }

    /// Les rouges d'abord, puis les oranges ; à niveau égal, le repère le plus ancien d'abord (la personne silencieuse depuis le plus
    /// longtemps) ; à égalité, l'ordre des cartes.
    private static func alertPages(_ context: Context, cards: [FeedMemberCard], latestUpdate: Int?) -> [AlertPage] {
        let offlineSince = context.input.effectivelyOffline ? latestUpdate.map { context.moment($0) } : nil
        var alerts: [SortableAlert] = []
        for (index, card) in cards.enumerated() where card.level == .red || card.level == .orange {
            guard let status = context.statuses[card.member.uid], let reference = status.referenceMillis else {
                continue
            }
            let page = AlertPage(
                member: card.member,
                isMe: card.isMe,
                level: card.level,
                kind: alertKind(card: card, status: status, reference: reference, context),
                canMessage: !card.isMe && card.phone != nil,
                offlineSince: offlineSince
            )
            let sortReference = status.presence == .inRisk ? (status.presenceAtMillis ?? reference) : reference
            alerts.append(SortableAlert(page: page, reference: sortReference, index: index))
        }
        let sorted = alerts.sorted { left, right in
            let leftRank = left.page.level == .red ? 0 : 1
            let rightRank = right.page.level == .red ? 0 : 1
            if leftRank != rightRank {
                return leftRank < rightRank
            }
            if left.reference != right.reference {
                return left.reference < right.reference
            }
            return left.index < right.index
        }
        return sorted.map { $0.page }
    }

    private static func alertKind(
        card: FeedMemberCard,
        status: MemberStatus,
        reference: Int,
        _ context: Context
    ) -> AlertKind {
        let since = context.moment(status.presenceAtMillis ?? reference)
        if status.presence == .inRisk {
            return .danger(place: placeRef(status.placeId, context.places, trusted: false), since: since)
        }
        if card.level == .red {
            return .noNewsSince(context.moment(reference))
        }
        return .left(place: placeRef(status.placeId, context.places, trusted: true), at: since)
    }

    /// Ce que le lecteur d'écran annonce quand la bulle change (jamais à chaque minute) ne change que si cette clé change.
    static func announceKey(_ bubble: Bubble) -> String {
        switch bubble {
        case .alerts(let pages, _):
            return "alerts:" + pages.map { "\($0.member.uid)/\($0.level)/\(kindName($0.kind))" }.joined(separator: ",")
        case .allGood:
            return "allGood"
        case .noNews:
            return "noNews"
        case .alone:
            return "alone"
        case .sharingStopped:
            return "sharingStopped"
        case .awaitingAgreement:
            return "awaitingAgreement"
        }
    }

    private static func kindName(_ kind: AlertKind) -> String {
        switch kind {
        case .left:
            return "left"
        case .noNewsSince:
            return "noNewsSince"
        case .danger:
            return "danger"
        }
    }
}
