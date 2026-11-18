import Foundation

// CONFIDENTIALITÉ, pour ce fichier (ticket « Profil, répartition du temps », TIME-3, critère TS-2) : un fait de présence ne porte QUE
// l'identifiant de la Miff, l'état, l'identifiant du lieu (1 à 20 lettres et chiffres) et l'heure. Aucune coordonnée, aucun nom ni
// adresse de lieu, aucun identifiant de personne. Le nom, la catégorie et le type affichés viennent TOUJOURS de la liste actuelle des
// lieux. Du Swift pur. Miroir du contrat commun d'Android (`docs/tickets/profil-repartition-temps.md` §6).

/// L'état d'un fait de présence : ce que le moteur de suivi déduit d'une Miff (`PresenceValue`), sans le mode voyage (un voyage est écrit
/// « inconnue » dans le journal : sa durée est non mesurée).
enum PresenceState: String, Equatable, CaseIterable {
    case trusted
    case danger
    case out
    case unknown

    /// L'état d'un statut du moteur. `travel` n'est jamais un fait du journal : le moteur ne l'écrit pas dans le suivi (il écrit « inconnue »).
    init(_ value: PresenceValue) {
        switch value {
        case .inTrusted:
            self = .trusted
        case .inRisk:
            self = .danger
        case .out:
            self = .out
        case .unknown, .travel:
            self = .unknown
        }
    }
}

/// « La présence de la Miff `miffId` est devenue (`state`, `placeId`) à l'instant `atMillis` » : un changement de présence que le moteur
/// calcule déjà, à l'instant même qu'il écrit dans le statut (`presenceAt`), pour que le Feed et le graphique ne se contredisent jamais.
struct PresenceFact: Equatable, CustomStringConvertible {
    let miffId: String
    let state: PresenceState
    /// Le lieu de la Miff (arrivée, séjour) ; pour « dehors », le lieu qu'on vient de quitter, quand le moteur le dit (ignoré par le calcul).
    let placeId: String?
    let atMillis: Int

    init(miffId: String, state: PresenceState, placeId: String?, atMillis: Int) {
        self.miffId = miffId
        self.state = state
        self.placeId = placeId
        self.atMillis = atMillis
    }

    /// Un fait du moteur : la Miff et le fait écrit dans son statut.
    init(miffId: String, fact: StatusFact) {
        self.init(
            miffId: miffId,
            state: PresenceState(fact.presence),
            placeId: fact.placeId,
            atMillis: fact.presenceAtMillis
        )
    }

    var description: String {
        "PresenceFact(redacted)"
    }
}

/// Les constantes du calcul : MÊMES valeurs sur iOS et sur Android (ticket §6), une définition par plateforme.
enum TimeShareLimits {
    /// Les fenêtres proposées, en jours. La première est celle de départ.
    static let windowsDays = [7, 14, 30]
    static let defaultWindowDays = 7
    static let hourMillis = 3_600_000
    static let dayMillis = 24 * hourMillis
    /// Tant que le PREMIER enregistrement du journal de la Miff a moins de 7 jours, « pas assez de données » : aucun graphique, aucun
    /// pourcentage (retour du PM, 2026-11-17 : le seuil de 24 h du ticket d'origine est remplacé). Il faut en plus du temps MESURÉ dans la fenêtre.
    static let minimumHistoryMillis = 7 * dayMillis

    /// « Ton graphique arrive dans X jours » : les jours qui restent avant les 7 jours d'historique, d'après le temps écoulé depuis le PREMIER
    /// enregistrement du journal de la Miff (aucun enregistrement : 0 écoulé, donc 7). Jamais moins de 1 tant que le graphique n'est pas
    /// affichable (6 jours et 2 h écoulés : « 1 jour »), jamais plus de 7. Les jours écoulés sont comptés ENTIERS (6 j 23 h : 6 jours écoulés).
    static func daysLeft(elapsedMillis: Int) -> Int {
        let elapsedDays = max(0, elapsedMillis) / dayMillis
        return min(7, max(1, 7 - elapsedDays))
    }
    /// Un état n'est compté que 48 h au plus sans nouveau fait : au-delà, non mesuré (sortie manquée, téléphone éteint, app tuée).
    static let openStateCapMillis = 48 * hourMillis
    /// Les faits plus vieux sont effacés (ils ne peuvent plus compter dans aucune fenêtre : 30 jours + 48 h de plafond < 35 jours).
    static let retentionMillis = 35 * dayMillis
    /// Les lieux de confiance nommés dans la légende ; les suivants sont regroupés en « {n} autres lieux ».
    static let maxNamedPlaces = 5
    /// Au-delà de ce nombre de zones surveillées en même temps (toutes Miffs confondues), « Ailleurs » peut être gonflé (note sous l'anneau).
    static let maxWatchedZones = 20

    /// Une fenêtre inconnue (valeur d'un ancien fichier, abîmée) retombe sur la fenêtre de départ.
    static func validWindow(_ days: Int?) -> Int {
        guard let days, windowsDays.contains(days) else {
            return defaultWindowDays
        }
        return days
    }
}
