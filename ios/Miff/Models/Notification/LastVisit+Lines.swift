import Foundation

// Les lignes du centre pour « Depuis ta dernière visite », et les jours (NOTIF-P1I). Du Swift pur. Miroir de la seconde moitié de
// `LastVisit.kt` d'Android. CONFIDENTIALITÉ : une ligne du centre est une CLÉ d'identifiants, d'une lettre, d'un mot de catégorie et d'une
// heure, jamais un prénom ni un nom de lieu (critère C1-7).

/// Ce que dit une ligne du centre, relu de sa clé.
struct VisitParsed: Equatable, CustomStringConvertible {
    let uid: String
    let kind: VisitKind
    let place: PlaceKind?
    let shownAtMillis: Int

    var description: String {
        "VisitParsed(redacted)"
    }
}

extension LastVisit {
    private static let separator: Character = "|"
    private static let keyParts = 6
    private static let recentDays = 6

    /// La clé d'une ligne (`c1|miff|uid|sorte|lieu|heure`) : des identifiants, une lettre, un mot de catégorie et une heure, jamais un nom.
    static func key(miffId: String, line: VisitLine) -> String {
        [
            NotificationType.c1.rawValue,
            miffId,
            line.uid,
            line.kind.rawValue,
            line.place?.visitCode ?? "",
            String(line.shownAtMillis),
        ].joined(separator: String(separator))
    }

    /// Les lignes du centre pour ces lignes : écrites SANS notification du système (l'app est devant : la ligne EST l'information), non
    /// lues (décision Q2).
    static func items(miffId: String, lines: [VisitLine]) -> [NotificationItem] {
        lines.map {
            NotificationItem(
                id: key(miffId: miffId, line: $0),
                type: .c1,
                miffId: miffId,
                createdAtMillis: $0.atMillis,
                readAtMillis: nil,
                state: .silent
            )
        }
    }

    /// Ce que dit une ligne du centre, relu de sa clé ; nil quand l'entrée n'est pas une ligne bien formée de cette sorte.
    static func parse(_ item: NotificationItem) -> VisitParsed? {
        guard item.type == .c1 else {
            return nil
        }
        let pieces = item.id.split(separator: separator, omittingEmptySubsequences: false).map { String($0) }
        guard pieces.count == keyParts, pieces[0] == NotificationType.c1.rawValue,
              let kind = VisitKind(rawValue: pieces[3]), let shownAt = Int(pieces[5]) else {
            return nil
        }
        var place: PlaceKind?
        if !pieces[4].isEmpty {
            guard let found = PlaceKind(visitCode: pieces[4]) else {
                return nil
            }
            place = found
        }
        return VisitParsed(uid: pieces[2], kind: kind, place: place, shownAtMillis: shownAt)
    }

    /// Le membre dont parle une ligne du centre, ou nil.
    static func uid(of item: NotificationItem) -> String? {
        parse(item)?.uid
    }

    // MARK: - Les jours

    /// Comment une heure se dit : aujourd'hui, hier, le jour de la semaine (2 à 6 jours en arrière) ou la date courte. Une heure après
    /// aujourd'hui compte pour aujourd'hui.
    static func dayOf(atMillis: Int, nowMillis: Int, zone: TimeZone) -> VisitDay {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        let today = calendar.startOfDay(for: Date(timeIntervalSince1970: Double(nowMillis) / 1000))
        let day = calendar.startOfDay(for: Date(timeIntervalSince1970: Double(atMillis) / 1000))
        let back = calendar.dateComponents([.day], from: day, to: today).day ?? 0
        switch back {
        case ...0:
            return .today
        case 1:
            return .yesterday
        case 2...recentDays:
            return .weekday
        default:
            return .date
        }
    }
}

extension NotificationHistory {
    /// Les lignes « Depuis ta dernière visite » (le plus récent d'abord).
    var visitItems: [NotificationItem] {
        items.filter { $0.type == .c1 }
    }

    /// Les lignes des JOURS (Aujourd'hui, Hier, Plus ancien) : tout sauf le groupe « Depuis ta dernière visite ».
    var dayItems: [NotificationItem] {
        items.filter { $0.type != .c1 }
    }

    /// Le groupe est REMPLACÉ d'un bloc (ou retiré s'il n'y a rien de neuf) : jamais plus de `LastVisit.maxLines` lignes, jamais une ligne
    /// vieille de plusieurs jours. Une ligne dont la clé est encore là garde ce que la personne en a fait (lue) : une comparaison identique ne
    /// change rien.
    func replacingVisit(with lines: [NotificationItem], nowMillis: Int) -> NotificationHistory {
        let before = Dictionary(visitItems.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let next = lines.prefix(LastVisit.maxLines).map { before[$0.id] ?? $0 }
        return NotificationHistory(items: dayItems + next, nowMillis: nowMillis)
    }

    /// Retire les lignes du groupe dont `remove` dit oui (un membre qui a quitté la Miff, une Miff qui n'est plus là).
    func removingVisit(where remove: (NotificationItem) -> Bool, nowMillis: Int) -> NotificationHistory {
        NotificationHistory(items: items.filter { $0.type != .c1 || !remove($0) }, nowMillis: nowMillis)
    }
}
