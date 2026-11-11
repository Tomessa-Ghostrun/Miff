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
        // MODE VOYAGE : personne de connu parmi les autres, et quelqu'un en voyage : la bulle le dit (jamais « Pas encore de nouvelles » pour un voyageur).
        if let travelers = travelersBubble(others, hidden: context.hidden) {
            return travelers
        }
        let knownOthers = others.filter { $0.level != .neutral }
        if context.hidden || knownOthers.isEmpty {
            return .noNews
        }
        return allGood(context, cards: cards, knownOthers: knownOthers)
    }

    /// Pas d'alerte : tout membre connu est vert. La « dernière nouvelle » est la plus récente des AUTRES (ma propre update ne rassure jamais).
    private static func allGood(_ context: Context, cards: [FeedMemberCard], knownOthers: [FeedMemberCard]) -> Bubble {
        let latest = knownOthers
            .compactMap { context.statuses[$0.member.uid]?.referenceMillis }
            .max()
            .map { context.age($0) }
        // MODE VOYAGE : un voyageur n'est pas « silencieux » : il est hors du décompte « n sur m » (ni dans n, ni dans m).
        let counted = cards.filter { !$0.traveling }
        let known = counted.filter { $0.level != .neutral }.count
        let someSilent = known < counted.count
        return .allGood(
            latestNews: latest,
            answered: someSilent ? known : nil,
            total: someSilent ? counted.count : nil
        )
    }

    // MARK: - Le voyage dans la bulle

    /// Tous les AUTRES membres sont neutres et au moins un est en voyage : une bulle neutre qui le dit.
    private static func travelersBubble(_ others: [FeedMemberCard], hidden: Bool) -> Bubble? {
        let travellers = others.filter { $0.traveling }
        guard !hidden, !travellers.isEmpty, others.allSatisfy({ $0.level == .neutral }) else {
            return nil
        }
        return .travelers(travelNote(others) ?? .many(count: travellers.count, all: travellers.count == others.count))
    }

    /// Les membres des `others` dont le voyage est en cours : un par son prénom, plusieurs par leur nombre ; nil quand aucun.
    static func travelNote(_ others: [FeedMemberCard]) -> TravelNote? {
        let travellers = others.filter { $0.traveling }
        guard let first = travellers.first else {
            return nil
        }
        // « Tes proches sont en voyage » seulement quand TOUS les autres membres le sont : avec un autre sans nouvelles, c'est « {n} proches ».
        if travellers.count == 1, case .traveling(let until, _) = first.label {
            return .one(name: first.member.callName, until: until)
        }
        return .many(count: travellers.count, all: travellers.count == others.count)
    }

    /// La ligne ajoutée à la bulle habituelle (la bulle des voyageurs le dit déjà ; rien quand je ne partage pas ou que mon accord attend).
    static func travelNote(under bubble: Bubble, cards: [FeedMemberCard], hidden: Bool) -> TravelNote? {
        if hidden {
            return nil
        }
        if case .travelers = bubble {
            return nil
        }
        return travelNote(cards.filter { !$0.isMe })
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
        case .travelers:
            return "travelers"
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
