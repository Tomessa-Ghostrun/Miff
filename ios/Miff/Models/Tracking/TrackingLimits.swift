import Foundation

// Les limites des écritures automatiques de LOC-4b, en UN seul endroit, chacune avec la raison de sa valeur (le PM a demandé qu'elles
// soient écrites : ticket §6.6 « disjoncteur », critères POS8 et STA4). Mêmes valeurs qu'Android (LOC-2b), pour que les deux
// plateformes pèsent pareil sur le quota. Plan Spark : 20 000 écritures par jour pour tout le projet (`docs/dev/surveillance-quotas.md`
// §3). Du Swift pur : rien n'est lu sur le téléphone.

enum TrackingLimits {
    /// Disjoncteur, statuts : écritures automatiques de `status/{moi}` par jour et par téléphone, comptées par document écrit (un par
    /// Miff). Une journée normale compte 6 à 10 événements (ticket §9.3) et quelques Miffs ; 60 laisse de la marge pour une journée
    /// chargée et arrête une zone qui clignote sur son bord (aucune temporisation : décision du PM) avant qu'elle brûle le quota.
    /// Au-delà, le DERNIER état reste en attente et part après minuit s'il est encore récent. Signal pour le changer : un testeur
    /// qui l'atteint un jour normal (le journal le dit), ou plus de 12 000 écritures par jour dans la console.
    static let maxStatusWritesPerDay = 60

    /// Disjoncteur, dernière position : écritures de `positions/{moi}` par jour et par téléphone, comptées par document (un par
    /// Miff). 150, c'est environ une toutes les dix minutes sur une longue journée avec une Miff ; le garde-fou ci-dessous garde
    /// une journée normale très en dessous.
    static let maxPositionWritesPerDay = 150

    /// Garde-fou de fréquence de la position (décision L4, à mesurer à la recette du PM) : au plus une écriture toutes les 15 min...
    static let positionMinIntervalMillis = 15 * 60 * 1000

    /// ... sauf si la personne a bougé d'au moins ce nombre de mètres depuis la dernière position écrite ...
    static let positionMinDistanceMeters = 250.0

    /// ... mais jamais deux fois en moins de ceci (un trajet en voiture écrirait sinon à chaque mise à jour du système).
    static let positionFloorIntervalMillis = 2 * 60 * 1000

    /// Les règles refusent une heure de plus de 24 h ; 5 minutes de marge pour le temps que met l'écriture à arriver (critère STA3).
    static let maxEventAgeMillis = 24 * 60 * 60 * 1000 - 5 * 60 * 1000

    /// Les lieux et le consentement d'une Miff sont relus auprès du serveur quand la copie locale a plus que ceci (ticket §6.4 (3)).
    static let placesRereadMillis = 60 * 60 * 1000

    /// Une position de plus de ceci n'est jamais utilisée, pour aucune occasion : la personne a bougé depuis, et une vieille
    /// position écrirait une fausse arrivée ou une fausse position. On en demande une autre, ou on ne fait rien.
    static let fixMaxAgeMillis = 10 * 60 * 1000

    /// Une région qu'on vient de créer est gardée en mémoire ce temps au plus, le temps que le système la montre dans sa liste.
    static let createdRegionSeconds = 60.0

    /// Le repli sur la dernière position connue du système n'est accepté que si elle a moins de ceci.
    static let systemFixMaxAgeMillis = 2 * 60 * 1000

    /// Le temps que le moteur attend une position fraîche (une fenêtre de relance a une dizaine de secondes en tout : les zones sont
    /// armées AVANT cette attente).
    static let positionWaitSeconds = 3.0

    /// Les règles acceptent une heure jusqu'à 5 minutes en avance sur celle du serveur : la même marge pour une position qui semble
    /// être dans le futur.
    static let clockSkewMillis = 5 * 60 * 1000

    /// Après un refus du serveur, la Miff est regardée de nouveau dans ce temps, pas à chaque réveil (un téléphone dont l'horloge a plus
    /// de 5 minutes d'avance serait refusé à chaque fois et relirait le serveur à chaque réveil).
    static let recheckAfterRefusalMillis = 5 * 60 * 1000

    /// Deux passages au premier plan à moins de ceci l'un de l'autre n'en font qu'un (démarrage à froid).
    static let duplicateOpenMillis = 3 * 1000

    /// Au plus un réenregistrement complet des régions par échec de la surveillance toutes les cinq minutes (pas de boucle).
    static let rearmMinIntervalMillis = 5 * 60 * 1000

    /// Une personne qui ne partage nulle part est regardée de nouveau au plus à cette fréquence à l'ouverture (2 lectures par Miff).
    static let retryWhenEmptyMillis = 2 * 60 * 1000

    /// Le temps total d'une fenêtre de relance qu'on s'accorde (le système en donne une dizaine, plus ce que `beginBackgroundTask`
    /// ajoute) : passé ce délai, ce qui n'est pas fini est déjà gardé (le statut en attente) et part au réveil suivant.
    static let wakeBudgetSeconds = 25.0
}

/// L'heure d'un événement telle qu'elle est écrite (critère STA3).
enum EventTime {
    /// L'heure RÉELLE de l'événement, jamais plus tard que l'horloge du téléphone, ou nil quand elle a plus de 24 h environ : les
    /// règles la refuseraient, elle est donc abandonnée (pas de boucle). LIMITE : les règles comparent avec l'horloge du SERVEUR (24 h
    /// avant, 5 minutes après). Un téléphone dont l'horloge a plus de 5 minutes d'avance est refusé même avec une heure plafonnée à la
    /// sienne ; un tel refus n'est pas rejoué à chaque réveil (`recheckAfterRefusalMillis`).
    static func forWrite(eventMillis: Int, nowMillis: Int) -> Int? {
        let capped = min(eventMillis, nowMillis)
        return nowMillis - capped > TrackingLimits.maxEventAgeMillis ? nil : capped
    }
}

/// Combien d'écritures automatiques ont été faites aujourd'hui (le jour du téléphone).
struct BreakerState: Codable, Equatable {
    var dayKey: String
    var statusWrites: Int
    var positionWrites: Int
}

enum WriteBreaker {
    /// Le jour du téléphone, sous une forme qui change à minuit (une chaîne comparée telle quelle).
    static func dayKey(millis: Int, calendar: Calendar = .current) -> String {
        let date = Date(timeIntervalSince1970: Double(millis) / 1000)
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return "\(parts.year ?? 0)-\(parts.month ?? 0)-\(parts.day ?? 0)"
    }

    private static func today(_ state: BreakerState?, dayKey: String) -> BreakerState {
        if let state, state.dayKey == dayKey {
            return state
        }
        return BreakerState(dayKey: dayKey, statusWrites: 0, positionWrites: 0)
    }

    static func canWriteStatus(_ state: BreakerState?, dayKey: String) -> Bool {
        today(state, dayKey: dayKey).statusWrites < TrackingLimits.maxStatusWritesPerDay
    }

    static func canWritePosition(_ state: BreakerState?, dayKey: String) -> Bool {
        today(state, dayKey: dayKey).positionWrites < TrackingLimits.maxPositionWritesPerDay
    }

    static func recordStatus(_ state: BreakerState?, dayKey: String) -> BreakerState {
        var next = today(state, dayKey: dayKey)
        next.statusWrites += 1
        return next
    }

    static func recordPosition(_ state: BreakerState?, dayKey: String) -> BreakerState {
        var next = today(state, dayKey: dayKey)
        next.positionWrites += 1
        return next
    }
}

/// La dernière position écrite dans UNE Miff (gardée sur le téléphone, pour appliquer le garde-fou de fréquence).
struct WrittenPosition: Equatable, CustomStringConvertible {
    let lat: Double
    let lng: Double
    let atMillis: Int

    var description: String {
        "WrittenPosition(redacted)"
    }
}

enum PositionThrottle {
    /// Cette position doit-elle être écrite dans une Miff ? Oui quand rien n'a encore été écrit ; jamais quand la dernière écriture
    /// est sous le plancher ; oui après 15 minutes ; oui avant si la personne a bougé de 250 m ou plus depuis la dernière position
    /// écrite.
    static func shouldWrite(last: WrittenPosition?, lat: Double, lng: Double, nowMillis: Int) -> Bool {
        guard let last else {
            return true
        }
        let elapsed = nowMillis - last.atMillis
        // Une horloge qui a reculé : on écrit (la comparaison est impossible), le disjoncteur s'applique quand même.
        if elapsed < 0 {
            return true
        }
        if elapsed < TrackingLimits.positionFloorIntervalMillis {
            return false
        }
        if elapsed >= TrackingLimits.positionMinIntervalMillis {
            return true
        }
        let moved = TrackingGeo.distanceMeters(lat1: last.lat, lng1: last.lng, lat2: lat, lng2: lng)
        return moved >= TrackingLimits.positionMinDistanceMeters
    }
}
