import Foundation

// Les lignes du centre de notifications au sujet des DEMANDES d'ajout d'un lieu (ticket LIEU-REQ, §7.1). Du Swift PUR : rien du système.
// CONFIDENTIALITÉ : une ligne porte les MÊMES choses que toutes les autres du centre (un type, l'identifiant de la Miff, une heure, une clé).
// AUCUN nom de personne, AUCUN nom ni adresse de lieu. La clé porte l'identifiant de la demande (`{uid}_{emplacement}` : un identifiant,
// pas un nom) ; les mots (« Léa propose un lieu pour « Famille » ») se font quand la ligne est DESSINÉE, avec ce que l'app sait alors,
// et ne sont jamais écrits dans le fichier.

enum PlaceRequestLines {
    private static let separator: Character = "|"

    /// La clé de déduplication : une même demande n'est jamais inscrite deux fois dans le centre.
    static func key(miffId: String, requestId: String) -> String {
        "\(NotificationType.n8.rawValue)\(separator)\(miffId)\(separator)\(requestId)"
    }

    /// La demande dont parle une ligne ; nil si ce n'est pas une ligne de demande.
    static func requestId(of item: NotificationItem) -> String? {
        guard item.type == .n8 else {
            return nil
        }
        let pieces = item.id.split(separator: separator, maxSplits: 2, omittingEmptySubsequences: false)
        guard pieces.count == 3, pieces[0] == Substring(NotificationType.n8.rawValue), !pieces[2].isEmpty else {
            return nil
        }
        return String(pieces[2])
    }

    /// La personne qui a demandé, lue dans l'identifiant de la demande.
    static func requester(of item: NotificationItem) -> String? {
        requestId(of: item).flatMap { PlaceRequest.parseId($0)?.requester }
    }

    /// UNE ligne par demande que le créateur connaît maintenant. Écrite SANS notification du système (l'app est devant : la ligne EST
    /// l'information), datée du moment de la demande (jamais dans le futur), pour qu'une demande vieille de deux semaines ne soit pas
    /// « aujourd'hui ».
    static func lines(miffId: String, requests: [PlaceRequest], nowMillis: Int) -> [NotificationItem] {
        requests.map {
            NotificationItem(
                id: key(miffId: miffId, requestId: $0.id),
                type: .n8,
                miffId: miffId,
                createdAtMillis: min($0.createdAtMillis ?? nowMillis, nowMillis),
                readAtMillis: nil,
                state: .silent
            )
        }
    }

    /// Une chose que le centre dessine pour ces demandes : `ids` sont les clés des lignes qu'elle représente (une ligne, ou plusieurs
    /// de la même personne dans la même Miff, toutes encore non lues : « Léa a proposé 3 lieux »).
    struct Entry: Equatable {
        let ids: [String]
        let miffId: String
        let requesterUid: String?
        let createdAtMillis: Int
        let unread: Bool

        var count: Int {
            ids.count
        }

        /// La ligne que la liste dessine pour cette entrée (la plus récente, sous la clé de la première).
        var item: NotificationItem {
            NotificationItem(
                id: ids[0],
                type: .n8,
                miffId: miffId,
                createdAtMillis: createdAtMillis,
                readAtMillis: unread ? nil : createdAtMillis,
                state: .silent
            )
        }
    }

    /// Les lignes de demandes comme le centre les dessine : les NON LUES d'une même personne dans une même Miff ne font QU'UNE entrée
    /// (l'heure la plus récente) ; chaque ligne lue est son entrée. Une ligne qui n'est pas une demande n'est jamais touchée ici.
    static func entries(of items: [NotificationItem]) -> [Entry] {
        let lines = items.filter { $0.type == .n8 }
        var order: [String] = []
        var groups: [String: [NotificationItem]] = [:]
        var single: [Entry] = []
        for line in lines {
            guard line.isUnread else {
                single.append(
                    Entry(
                        ids: [line.id], miffId: line.miffId, requesterUid: requester(of: line),
                        createdAtMillis: line.createdAtMillis, unread: false
                    )
                )
                continue
            }
            let groupKey = "\(line.miffId)\(separator)\(requester(of: line) ?? "")"
            if groups[groupKey] == nil {
                order.append(groupKey)
            }
            groups[groupKey, default: []].append(line)
        }
        let grouped = order.compactMap { groupKey -> Entry? in
            guard let members = groups[groupKey], let first = members.first else {
                return nil
            }
            return Entry(
                ids: members.map { $0.id },
                miffId: first.miffId,
                requesterUid: requester(of: first),
                createdAtMillis: members.map { $0.createdAtMillis }.max() ?? first.createdAtMillis,
                unread: true
            )
        }
        return grouped + single
    }

    /// Ce que dessine la liste : les lignes qui ne sont pas des demandes, et UNE ligne par entrée de demande, la plus récente d'abord.
    static func displayed(_ items: [NotificationItem]) -> [NotificationItem] {
        let others = items.filter { $0.type != .n8 }
        let merged = others + entries(of: items).map { $0.item }
        return merged.sorted {
            $0.createdAtMillis != $1.createdAtMillis ? $0.createdAtMillis > $1.createdAtMillis : $0.id < $1.id
        }
    }

    /// Les clés des lignes de demandes de cette Miff (la liste de la Miff a été vue : elles sont lues).
    static func ids(of items: [NotificationItem], miffId: String) -> [String] {
        items.filter { $0.type == .n8 && $0.miffId == miffId }.map { $0.id }
    }
}
