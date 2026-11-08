import Foundation

// Le registre LOCAL des rappels (ticket §20.3) : quelles clés ont déjà été notifiées (conservées 48 h, jamais renotifiées), quelles
// demandes sont en attente chez le système, et un compteur de fausses alertes pour la recette. Rien n'en sort de l'appareil : ni
// Analytics, ni serveur. Aucune position, aucun nom de lieu.

/// Une demande programmée chez le système, telle que ce téléphone se la rappelle. iOS ne dit pas à quelle heure une demande est prévue
/// (seulement qu'elle l'est) : c'est ce registre qui sert de vérité pour la remplacer ou la retirer.
struct ScheduledRecord: Codable, Equatable {
    let type: NotificationType
    let miffId: String
    let key: String
    let fireAtMillis: Int
    let quiet: Bool
    /// La langue du texte figé dans la demande : un changement de langue la fait reprogrammer.
    let language: String
}

struct NotificationLedger: Codable, Equatable {
    /// Clé notifiée -> heure de l'inscription.
    var notified: [String: Int] = [:]
    /// Identifiant de la demande -> ce qui a été programmé.
    var scheduled: [String: ScheduledRecord] = [:]
    /// Couple (Miff, lieu) -> heure de la dernière N6 : le retard de réarmement de 60 minutes (une géofence qui oscille ne donne qu'UNE N6).
    var lastDanger: [String: Int] = [:]
    /// N3 / N4 affichées alors que la couleur de la Miff était déjà revenue (compteur local pour la recette, jamais envoyé).
    var falseAlerts = 0
    /// N3 / N4 dont on a pu juger (donnée plus récente que l'affichage) : le dénominateur du compteur ci-dessus.
    var judgedFollowUps = 0

    var notifiedKeys: Set<String> {
        Set(notified.keys)
    }

    func marking(key: String, atMillis: Int) -> NotificationLedger {
        var copy = self
        copy.notified[key] = atMillis
        return copy
    }

    /// Oublie les clés notifiées depuis plus de 48 h.
    func pruned(nowMillis: Int) -> NotificationLedger {
        var copy = self
        copy.notified = notified.filter { nowMillis - $0.value <= NotificationPlanner.keysRetentionMillis }
        copy.lastDanger = lastDanger.filter { nowMillis - $0.value <= NotificationPlanner.keysRetentionMillis }
        return copy
    }

    /// Réunit les clés notifiées et les retards de réarmement de `other` (le plus récent gagne) : ils ne font que s'ajouter, donc une
    /// réconciliation qui a attendu le système ne perd jamais ce qu'une autre (une N6, un toucher) a inscrit pendant ce temps.
    func merging(marksOf other: NotificationLedger) -> NotificationLedger {
        var copy = self
        copy.notified.merge(other.notified) { max($0, $1) }
        copy.lastDanger.merge(other.lastDanger) { max($0, $1) }
        return copy
    }

    func markingDanger(placeKey: String, atMillis: Int) -> NotificationLedger {
        var copy = self
        copy.lastDanger[placeKey] = atMillis
        return copy
    }

    func scheduling(_ record: ScheduledRecord, identifier: String) -> NotificationLedger {
        var copy = self
        copy.scheduled[identifier] = record
        return copy
    }

    func unscheduling(_ identifiers: [String]) -> NotificationLedger {
        var copy = self
        for identifier in identifiers {
            copy.scheduled[identifier] = nil
        }
        return copy
    }

    func countingFollowUp(falseAlert: Bool) -> NotificationLedger {
        var copy = self
        copy.judgedFollowUps += 1
        if falseAlert {
            copy.falseAlerts += 1
        }
        return copy
    }
}
