import Foundation

// N6 : « Tu es dans une zone de danger de ta Miff » (ticket Notifications §20.7, décisions Q34 : toutes mes Miffs, texte variante A,
// toucher = le Feed, interrupteur présent). Une notification LOCALE et IMMÉDIATE, déclenchée par mon propre téléphone quand le suivi des
// lieux détecte que j'entre dans une zone de danger : aucune lecture ni écriture sur le serveur, 0 €. Du Swift PUR.

/// Ce que la décision lit.
struct DangerZoneInput {
    var nowMillis: Int
    /// Le compte connecté (nil : plus de session).
    var sessionUid: String?
    var miffId: String
    var placeId: String?
    /// L'heure d'entrée dans la zone (celle du statut en attente).
    var enteredAtMillis: Int
    var preferences = NotificationPreferences()
    var authorization = NotificationAuthorization.authorized
    /// Les clés déjà notifiées (conservées 48 h).
    var notifiedKeys: Set<String> = []
    /// La dernière N6 de ce couple (Miff, lieu), pour le retard de réarmement.
    var lastForThisPlaceMillis: Int?
    /// Les N6 déjà AFFICHÉES aujourd'hui (jour local).
    var shownToday = 0
    /// L'app est au premier plan : le système n'affiche rien, le centre garde une entrée silencieuse.
    var isForeground = false
    var calendar = Calendar.current
}

/// Ce que la décision rend.
enum DangerZoneDecision: Equatable {
    /// Aucune notification, aucune entrée de centre.
    case skip
    /// Une entrée de centre SILENCIEUSE, sans notification du système (premier plan, ou plafond du jour atteint).
    case silent
    /// Une notification immédiate ; `quiet` : nuit calme, sans son ni vibration.
    case show(quiet: Bool)
}

enum NotificationDangerZone {
    /// Une sortie et une rentrée rapprochées (une géofence qui oscille) ne donnent qu'UNE notification.
    static let rearmDelayMillis = 60 * 60 * 1000
    /// 3 par jour au plus, HORS du plafond de 6 de N1 à N4.
    static let maxPerDay = 3

    /// La clé de déduplication de l'événement : `n6|{Miff}|{lieu}|{heure d'entrée}`.
    static func key(miffId: String, placeId: String?, enteredAtMillis: Int) -> String {
        "n6|\(miffId)|\(placeId ?? "-")|\(enteredAtMillis)"
    }

    /// Le couple (Miff, lieu) pour le retard de réarmement.
    static func placeKey(miffId: String, placeId: String?) -> String {
        "\(miffId)|\(placeId ?? "-")"
    }

    static func decide(_ input: DangerZoneInput) -> DangerZoneDecision {
        guard input.sessionUid != nil,
              input.preferences.isEnabled(.n6),
              input.authorization.isAllowed else {
            return .skip
        }
        let eventKey = key(miffId: input.miffId, placeId: input.placeId, enteredAtMillis: input.enteredAtMillis)
        if input.notifiedKeys.contains(eventKey) {
            return .skip
        }
        if let last = input.lastForThisPlaceMillis, input.nowMillis - last < rearmDelayMillis {
            return .skip
        }
        if input.shownToday >= maxPerDay || input.isForeground {
            return .silent
        }
        let night = input.preferences.nightCalm
            && NotificationType.n6.silentAtNight
            && NotificationPlanner.isNight(atMillis: input.nowMillis, calendar: input.calendar)
        return .show(quiet: night)
    }
}
