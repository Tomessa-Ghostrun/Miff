import Foundation

// Les heures du Feed, en DONNÉES (aucun texte) : les écrans en font les phrases des clés `feed_*` (français « 16 h 02 », « hier à
// 16 h 02 », « le 3 nov. à 16 h 02 » ; mêmes cas qu'Android, les tests sont le contrat). Des fonctions pures d'un instant, de
// l'instant présent et d'un fuseau : rien n'est lu, rien n'est écrit, rien n'est journalisé.

enum FeedMillis {
    static let minute = 60 * 1000
    static let hour = 60 * minute
    static let day = 24 * hour
}

/// Depuis combien de temps, dit comme le Feed le dit (« 12 min », « 3 h », « hier à 08 h 12 », « le 3 nov. à 08 h 12 »).
enum Age: Equatable {
    /// Moins d'une heure : une minute au moins (le Feed ne dit jamais « 0 min »).
    case minutes(Int)
    /// D'une heure à moins d'un jour.
    case hours(Int)
    /// Un jour ou plus, et la date locale de l'instant est celle d'hier.
    case yesterday(atMillis: Int)
    /// Un jour ou plus, et avant hier.
    case date(atMillis: Int)

    /// Un instant dans le futur (téléphone à l'heure fausse) compte comme « maintenant » : une minute.
    static func of(thenMillis: Int, nowMillis: Int, zone: TimeZone) -> Age {
        let elapsed = nowMillis - thenMillis
        if elapsed < FeedMillis.hour {
            return .minutes(max(elapsed / FeedMillis.minute, 1))
        }
        if elapsed < FeedMillis.day {
            return .hours(elapsed / FeedMillis.hour)
        }
        let day = Moment.dayOf(atMillis: thenMillis, nowMillis: nowMillis, zone: zone)
        return day == .yesterday ? .yesterday(atMillis: thenMillis) : .date(atMillis: thenMillis)
    }
}

/// Le jour local d'une heure : la phrase dit « 16 h 02 », « hier à 16 h 02 » ou « le 3 nov. à 16 h 02 ».
enum Day: Equatable {
    case today
    case yesterday
    case other
}

/// Une heure absolue, avec son jour. Orange et rouge se disent à l'heure ABSOLUE (« à 16 h 40 »), jamais « il y a 62 min » qui
/// s'aggrave à chaque minute.
struct Moment: Equatable {
    let atMillis: Int
    let day: Day

    static func of(atMillis: Int, nowMillis: Int, zone: TimeZone) -> Moment {
        Moment(atMillis: atMillis, day: dayOf(atMillis: atMillis, nowMillis: nowMillis, zone: zone))
    }

    static func dayOf(atMillis: Int, nowMillis: Int, zone: TimeZone) -> Day {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        let then = Date(timeIntervalSince1970: Double(atMillis) / 1000)
        let now = Date(timeIntervalSince1970: Double(nowMillis) / 1000)
        if calendar.isDate(then, inSameDayAs: now) {
            return .today
        }
        if let before = calendar.date(byAdding: .day, value: -1, to: now), calendar.isDate(then, inSameDayAs: before) {
            return .yesterday
        }
        return .other
    }
}
