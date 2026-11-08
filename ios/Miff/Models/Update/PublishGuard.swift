import Foundation

// Le garde-fou des publications (ticket Update, V5, critère U6) : 20 par jour et 30 secondes entre deux, par téléphone. Il aide la
// personne (un pouce qui touche deux fois) et le quota du plan Spark (une update, c'est 2 écritures par Miff et jusqu'à 150 Ko de
// photo) ; la vraie protection contre l'abus reste App Check et la surveillance des quotas. Du Swift pur : le jour et les comptes sont
// donnés par l'appelant (un fichier privé du téléphone), pour que les cas soient testés. Même logique que `PublishGuard.kt`.

/// Le compte du jour : la clé du jour du téléphone et le nombre de publications comptées ce jour-là.
struct PublishCount: Equatable {
    let day: String
    let count: Int
}

enum PublishGuard {
    static let maxPerDay = 20
    static let minGapMillis = 30_000

    /// Ce que dit le garde-fou : on y va, on attend un peu (la dernière date de moins de 30 s), ou on revient plus tard (20 aujourd'hui).
    enum Verdict: Equatable {
        case go
        case tooSoon
        case tooMany
    }

    /// Le jour du téléphone, comme une clé : elle change à minuit du téléphone (le compte repart de zéro).
    static func dayKey(nowMillis: Int, timeZone: TimeZone) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let date = Date(timeIntervalSince1970: Double(nowMillis) / 1000)
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }

    /// `countToday` est le nombre de publications comptées pour `countDay` ; un compte d'un autre jour est ignoré. `lastMillis` est le
    /// moment de la dernière (un moment dans le futur, d'une horloge reculée, est ignoré : personne n'attend une horloge).
    static func check(
        nowMillis: Int,
        timeZone: TimeZone,
        countDay: String?,
        countToday: Int,
        lastMillis: Int?
    ) -> Verdict {
        if countDay == dayKey(nowMillis: nowMillis, timeZone: timeZone), countToday >= maxPerDay {
            return .tooMany
        }
        if let lastMillis, lastMillis <= nowMillis, nowMillis - lastMillis < minGapMillis {
            return .tooSoon
        }
        return .go
    }

    /// Le compte après une publication de plus à `nowMillis`.
    static func next(nowMillis: Int, timeZone: TimeZone, countDay: String?, countToday: Int) -> PublishCount {
        let today = dayKey(nowMillis: nowMillis, timeZone: timeZone)
        return PublishCount(day: today, count: countDay == today ? countToday + 1 : 1)
    }
}
