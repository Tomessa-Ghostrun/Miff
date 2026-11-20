import Foundation

// CNC-I : les lignes du centre qui parlent d'UN PROCHE (N9, N10, N11 : des messages du serveur). Du Swift pur.

extension NotificationHistory {
    /// Les lignes qui parlent d'un proche (le plus récent d'abord).
    var lovedOneItems: [NotificationItem] {
        items.filter { $0.type.aboutALovedOne }
    }

    /// Ajoute plusieurs lignes d'un coup (la lecture du journal du serveur). Une clé déjà présente n'est jamais ajoutée une seconde fois.
    func adding(contentsOf lines: [NotificationItem], nowMillis: Int) -> NotificationHistory {
        var seen = Set(items.map { $0.id })
        var fresh: [NotificationItem] = []
        for line in lines where !seen.contains(line.id) {
            seen.insert(line.id)
            fresh.append(line)
        }
        guard !fresh.isEmpty else {
            return self
        }
        return NotificationHistory(items: items + fresh, nowMillis: nowMillis)
    }

    /// Retire les lignes de proches dont `remove` dit oui (une Miff qui n'est plus celle du compte : quittée, supprimée, accès perdu).
    func removingLovedOnes(where remove: (NotificationItem) -> Bool, nowMillis: Int) -> NotificationHistory {
        NotificationHistory(items: items.filter { !$0.type.aboutALovedOne || !remove($0) }, nowMillis: nowMillis)
    }
}
