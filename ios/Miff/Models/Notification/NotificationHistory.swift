import Foundation

// Le centre de notifications LOCAL (ticket §7 et §20.4) : l'historique de ce qui s'est passé sur CET appareil. Rien n'en quitte le
// téléphone. Il ne contient ni position, ni adresse, ni nom de lieu, ni photo : un type, une heure, l'identifiant de la Miff.

enum NotificationItemState: String, Codable, Equatable {
    /// Une notification du système a été affichée (ou programmée et arrivée à l'heure).
    case shown
    /// Entrée sans notification du système : l'app était au premier plan, ou rattrapage tardif.
    case silent
}

struct NotificationItem: Codable, Equatable, Identifiable {
    /// La clé de déduplication de l'événement (`n1|miff|moi|repère`…) : une même clé n'est jamais inscrite deux fois.
    let id: String
    let type: NotificationType
    let miffId: String
    let createdAtMillis: Int
    var readAtMillis: Int?
    let state: NotificationItemState

    var isUnread: Bool {
        readAtMillis == nil
    }
}

/// La liste des entrées et les règles qui la tiennent, en fonctions pures : conservation de 30 jours ou 100 entrées (le premier atteint),
/// plus récent d'abord, lu / non lu, suppression.
struct NotificationHistory: Equatable {
    static let maxAgeMillis = 30 * 24 * 60 * 60 * 1000
    static let maxItems = 100

    private(set) var items: [NotificationItem]

    /// Trie (plus récent d'abord, l'identifiant départage) et applique la conservation.
    init(items: [NotificationItem] = [], nowMillis: Int) {
        let sorted = items.sorted {
            $0.createdAtMillis != $1.createdAtMillis ? $0.createdAtMillis > $1.createdAtMillis : $0.id < $1.id
        }
        let recent = sorted.filter { nowMillis - $0.createdAtMillis <= Self.maxAgeMillis }
        self.items = Array(recent.prefix(Self.maxItems))
    }

    var unreadCount: Int {
        items.filter { $0.isUnread }.count
    }

    var isEmpty: Bool {
        items.isEmpty
    }

    func contains(id: String) -> Bool {
        items.contains { $0.id == id }
    }

    /// Ajoute une entrée. Une clé déjà présente n'est jamais ajoutée une seconde fois.
    func adding(_ item: NotificationItem, nowMillis: Int) -> NotificationHistory {
        guard !contains(id: item.id) else {
            return self
        }
        return NotificationHistory(items: items + [item], nowMillis: nowMillis)
    }

    func markingRead(id: String, nowMillis: Int) -> NotificationHistory {
        var changed = items
        for index in changed.indices where changed[index].id == id && changed[index].isUnread {
            changed[index].readAtMillis = nowMillis
        }
        return NotificationHistory(items: changed, nowMillis: nowMillis)
    }

    func markingAllRead(nowMillis: Int) -> NotificationHistory {
        var changed = items
        for index in changed.indices where changed[index].isUnread {
            changed[index].readAtMillis = nowMillis
        }
        return NotificationHistory(items: changed, nowMillis: nowMillis)
    }

    func deleting(id: String, nowMillis: Int) -> NotificationHistory {
        NotificationHistory(items: items.filter { $0.id != id }, nowMillis: nowMillis)
    }

    func pruned(nowMillis: Int) -> NotificationHistory {
        NotificationHistory(items: items, nowMillis: nowMillis)
    }
}

/// Les groupes de la liste : « Aujourd'hui », « Hier », « Plus ancien » (jour local de l'appareil).
enum NotificationSection: Equatable, CaseIterable {
    case today
    case yesterday
    case older

    var titleKey: String {
        switch self {
        case .today:
            return "notif_section_today"
        case .yesterday:
            return "notif_section_yesterday"
        case .older:
            return "notif_section_older"
        }
    }
}

struct NotificationGroup: Equatable {
    let section: NotificationSection
    let items: [NotificationItem]
}

enum NotificationGrouping {
    /// Une entrée datée du futur (horloge fausse) compte comme « aujourd'hui », comme `StatusColor` le fait pour un repère futur.
    static func section(
        createdAtMillis: Int,
        nowMillis: Int,
        calendar: Calendar
    ) -> NotificationSection {
        let created = Date(timeIntervalSince1970: Double(createdAtMillis) / 1000)
        let now = Date(timeIntervalSince1970: Double(nowMillis) / 1000)
        if createdAtMillis >= nowMillis || calendar.isDate(created, inSameDayAs: now) {
            return .today
        }
        if calendar.isDateInYesterday(created, relativeTo: now) {
            return .yesterday
        }
        return .older
    }

    /// Les groupes non vides, dans l'ordre Aujourd'hui, Hier, Plus ancien ; l'ordre des entrées (plus récent d'abord) est conservé.
    static func groups(
        of items: [NotificationItem],
        nowMillis: Int,
        calendar: Calendar
    ) -> [NotificationGroup] {
        var result: [NotificationGroup] = []
        for section in NotificationSection.allCases {
            let inSection = items.filter {
                Self.section(createdAtMillis: $0.createdAtMillis, nowMillis: nowMillis, calendar: calendar) == section
            }
            if !inSection.isEmpty {
                result.append(NotificationGroup(section: section, items: inSection))
            }
        }
        return result
    }
}

private extension Calendar {
    /// `isDateInYesterday` regarde « maintenant » du système : ici « hier » se compte depuis le `now` donné (testable).
    func isDateInYesterday(_ date: Date, relativeTo now: Date) -> Bool {
        guard let yesterday = self.date(byAdding: .day, value: -1, to: now) else {
            return false
        }
        return isDate(date, inSameDayAs: yesterday)
    }
}
