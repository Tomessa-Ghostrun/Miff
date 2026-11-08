import Foundation

// Batterie rafraîchie à l'ouverture et à la sortie de l'app (décision du PM, 2026-11-07) : la batterie est déjà écrite avec chaque statut
// d'arrivée ou de départ ; ce chantier la réécrit aussi quand la personne ouvre ou quitte l'app, pour que les proches voient un niveau
// récent (le Feed masque la batterie au-delà de 6 h après `updatedAt`). Du Swift pur : rien n'est lu sur le téléphone, aucune position.
//
// CONFIDENTIALITÉ : un niveau de batterie et deux heures ; aucune coordonnée, aucun lieu. Miroir de la règle d'Android (mêmes constantes,
// `TrackingLimits`).

/// Ce que le téléphone sait de la dernière batterie écrite dans UNE Miff.
struct BatteryHistory: Equatable {
    /// Le niveau écrit en dernier (nil : le téléphone ne le donnait pas à ce moment-là).
    var pct: Int?
    /// L'heure de la dernière écriture d'un statut portant la batterie, de quelque origine qu'elle soit (arrivée, départ, accès, rafraîchissement).
    var writtenAtMillis: Int?
    /// L'heure de la dernière tentative de rafraîchissement refusée par le serveur (pas de boucle sur un refus).
    var triedAtMillis: Int?
}

enum BatterySkip: Equatable {
    /// Le téléphone ne donne pas son niveau (ou une valeur hors de 0 à 100) : rien à écrire.
    case unknown
    /// Moins de 15 minutes depuis la dernière écriture ou tentative.
    case tooSoon
    /// Moins de 5 points d'écart avec la dernière valeur écrite, et cette écriture a moins de 3 h.
    case unchanged
}

enum BatteryDecision: Equatable {
    case write
    case skip(BatterySkip)
}

enum BatteryRefreshPolicy {
    /// Faut-il réécrire la batterie dans une Miff ? Dans l'ordre : niveau inconnu = non ; moins de 15 minutes depuis la dernière
    /// écriture ou tentative = non ; jamais écrit = oui ; dernière écriture de plus de 3 h = oui (même niveau) ; sinon oui seulement
    /// si le niveau a bougé d'au moins 5 points.
    static func decide(history: BatteryHistory, current: Int?, nowMillis: Int) -> BatteryDecision {
        guard let current, (0...100).contains(current) else {
            return .skip(.unknown)
        }
        if isTooSoon(history, nowMillis: nowMillis) {
            return .skip(.tooSoon)
        }
        return hasMoved(history, to: current, nowMillis: nowMillis) ? .write : .skip(.unchanged)
    }

    /// Moins de 15 minutes depuis la dernière écriture ou tentative. Une horloge qui a reculé : la comparaison est impossible, on
    /// n'attend pas (les disjoncteurs s'appliquent quand même).
    private static func isTooSoon(_ history: BatteryHistory, nowMillis: Int) -> Bool {
        let events = [history.writtenAtMillis, history.triedAtMillis].compactMap { $0 }
        guard let last = events.max() else {
            return false
        }
        let since = nowMillis - last
        return since >= 0 && since < TrackingLimits.batteryMinIntervalMillis
    }

    /// Jamais écrit, ou écriture de plus de 3 h, ou niveau qui a bougé d'au moins 5 points depuis la dernière valeur écrite.
    private static func hasMoved(_ history: BatteryHistory, to current: Int, nowMillis: Int) -> Bool {
        guard let writtenAt = history.writtenAtMillis, let known = history.pct else {
            return true
        }
        let since = nowMillis - writtenAt
        if since < 0 || since > TrackingLimits.batteryMaxAgeMillis {
            return true
        }
        return abs(current - known) >= TrackingLimits.batteryMinDeltaPoints
    }
}
