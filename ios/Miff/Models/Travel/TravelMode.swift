import Foundation

// MODE VOYAGE (docs/tickets/mode-voyage.md, VOY-1I) : les règles pures, partagées par le moteur, le Profil et le Feed. Rien n'est lu,
// écrit ni journalisé ici. La fin d'un voyage est un instant ABSOLU (00:00 heure locale du lendemain du jour choisi : le dernier jour est
// inclus en entier) ; le jour choisi est gardé à part, en texte `AAAA-MM-JJ`, pour l'AFFICHAGE seulement (un proche d'un autre fuseau
// doit lire le jour que le voyageur a choisi). Une date de voyage dit une absence prévue : elle n'est jamais journalisée (`description`
// ne montre rien). Miroir de `TravelMode.kt` d'Android (les cas des tests sont le contrat : TravelModeTests).

enum TravelLimits {
    /// Un voyage dure 90 jours au plus : le dernier jour choisissable est aujourd'hui + 89, pour que sa fin (00:00 du lendemain) ne soit
    /// jamais à plus de 90 jours, heure d'été comprise. Les règles du serveur acceptent une fin à 91 jours au plus.
    static let maxDays = 90

    /// La fin écrite ne doit pas être plus loin que cela de maintenant (90 jours et 23 heures) : sous les 91 jours des règles, avec une
    /// marge pour l'horloge.
    static let maxEndAheadMillis = 90 * 24 * 3_600_000 + 23 * 3_600_000

    /// Le jour proposé à l'ouverture du sélecteur : aujourd'hui + 7 jours.
    static let defaultDays = 7
}

/// Un jour du calendrier, sans heure ni fuseau (« 2026-11-15 »).
struct TravelDate: Equatable, Comparable, Hashable, CustomStringConvertible {
    let year: Int
    let month: Int
    let day: Int

    static func < (left: TravelDate, right: TravelDate) -> Bool {
        (left.year, left.month, left.day) < (right.year, right.month, right.day)
    }

    /// Le jour du calendrier de CE fuseau, pour cet instant.
    static func today(nowMillis: Int, zone: TimeZone) -> TravelDate {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        let date = Date(timeIntervalSince1970: Double(nowMillis) / 1000)
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return TravelDate(year: parts.year ?? 1970, month: parts.month ?? 1, day: parts.day ?? 1)
    }

    /// Le texte écrit dans le statut (`AAAA-MM-JJ`, zéros de remplissage).
    var iso: String {
        String(format: "%04d-%02d-%02d", year, month, day)
    }

    var description: String {
        "TravelDate(redacted)"
    }

    private static var utcCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0) ?? .current
        return calendar
    }

    /// Midi, heure UTC, de ce jour : un point de repère sans piège d'heure d'été.
    var noonUTC: Date? {
        Self.utcCalendar.date(from: DateComponents(year: year, month: month, day: day, hour: 12))
    }

    func adding(days: Int) -> TravelDate {
        let calendar = Self.utcCalendar
        guard let noon = noonUTC, let moved = calendar.date(byAdding: .day, value: days, to: noon) else {
            return self
        }
        let parts = calendar.dateComponents([.year, .month, .day], from: moved)
        return TravelDate(year: parts.year ?? year, month: parts.month ?? month, day: parts.day ?? day)
    }

    /// Le premier instant de ce jour dans CE fuseau (millisecondes depuis 1970).
    func startMillis(in zone: TimeZone) -> Int {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        let noon = calendar.date(from: DateComponents(year: year, month: month, day: day, hour: 12)) ?? Date()
        return Int(calendar.startOfDay(for: noon).timeIntervalSince1970 * 1000)
    }

    /// La forme seule : dix caractères, des chiffres (ASCII) et deux tirets (jamais un espace, un retour à la ligne ni un chiffre d'une autre écriture).
    private static func hasDayShape(_ value: String) -> Bool {
        let characters = Array(value)
        guard characters.count == 10 else {
            return false
        }
        return characters.enumerated().allSatisfy { index, character in
            if index == 4 || index == 7 {
                return character == "-"
            }
            return character.isASCII && character.isNumber
        }
    }

    /// Un jour reçu du serveur, ou nil quand ce n'est pas une date `AAAA-MM-JJ` qui existe (quelqu'un d'autre l'a écrit : jamais cru).
    static func parse(_ value: String?) -> TravelDate? {
        guard let value, hasDayShape(value) else {
            return nil
        }
        let numbers = value.split(separator: "-").compactMap { Int($0) }
        guard numbers.count == 3 else {
            return nil
        }
        let candidate = TravelDate(year: numbers[0], month: numbers[1], day: numbers[2])
        // « 2026-02-30 » n'existe pas : le calendrier le ramènerait au 2 mars, donc le jour relu ne serait pas le même.
        guard let noon = candidate.noonUTC else {
            return nil
        }
        let back = utcCalendar.dateComponents([.year, .month, .day], from: noon)
        let same = back.year == candidate.year && back.month == candidate.month && back.day == candidate.day
        return same ? candidate : nil
    }
}

/// Un voyage tel que le téléphone le garde et l'écrit : l'instant où il finit, et le jour choisi (affichage seul).
struct TravelPeriod: Codable, Equatable, CustomStringConvertible {
    let untilMillis: Int
    /// `AAAA-MM-JJ` : le dernier jour de voyage choisi.
    let untilDay: String

    /// Le voyage est fini à partir de l'instant `untilMillis` INCLUS.
    func isOver(nowMillis: Int) -> Bool {
        nowMillis >= untilMillis
    }

    var description: String {
        "TravelPeriod(redacted)"
    }
}

/// La réponse à « ce jour peut-il être choisi ? ».
enum TravelChoice: Equatable {
    case valid(TravelPeriod)
    /// Avant aujourd'hui.
    case tooEarly
    /// Après aujourd'hui + 89 jours (un voyage dure 90 jours au plus).
    case tooLate
}

enum TravelPlan {
    static func minDay(today: TravelDate) -> TravelDate {
        today
    }

    static func maxDay(today: TravelDate) -> TravelDate {
        today.adding(days: TravelLimits.maxDays - 1)
    }

    static func defaultDay(today: TravelDate) -> TravelDate {
        today.adding(days: TravelLimits.defaultDays)
    }

    /// L'instant où finit un voyage dont le dernier jour est `day` : 00:00 local du jour d'après.
    static func untilMillis(of day: TravelDate, zone: TimeZone) -> Int {
        day.adding(days: 1).startMillis(in: zone)
    }

    /// Valide un jour choisi `today` et construit le voyage ; les bornes sont celles du sélecteur.
    static func choose(day: TravelDate, today: TravelDate, zone: TimeZone) -> TravelChoice {
        if day < minDay(today: today) {
            return .tooEarly
        }
        if day > maxDay(today: today) {
            return .tooLate
        }
        return .valid(TravelPeriod(untilMillis: untilMillis(of: day, zone: zone), untilDay: day.iso))
    }
}

/// Le jour à montrer pour un voyage, et si l'année s'y ajoute (seulement quand ce n'est pas l'année en cours).
struct TravelDay: Equatable, CustomStringConvertible {
    let date: TravelDate
    let showYear: Bool

    var description: String {
        "TravelDay(redacted)"
    }

    /// Le jour choisi par le voyageur : `untilDay` quand il est bien formé, sinon (une autre version de l'app l'a écrit) la veille de
    /// `untilMillis` dans le fuseau du lecteur. nil quand ni l'un ni l'autre n'est utilisable.
    static func of(untilDay: String?, untilMillis: Int?, today: TravelDate, zone: TimeZone) -> TravelDay? {
        let date: TravelDate
        if let parsed = TravelDate.parse(untilDay) {
            date = parsed
        } else if let untilMillis {
            date = TravelDate.today(nowMillis: untilMillis, zone: zone).adding(days: -1)
        } else {
            return nil
        }
        return TravelDay(date: date, showYear: date.year != today.year)
    }
}

/// Ce qu'un proche voit d'un voyage.
enum TravelView: Equatable {
    /// « En voyage jusqu'au 15 nov. »
    case active(TravelDay)
    /// La fin est passée et le voyageur n'a pas donné de nouvelles depuis : « Voyage terminé » (jamais un faux « en voyage »).
    case ended

    /// Le voyage d'un statut, ou nil quand ce n'est pas un voyage (une autre présence, ou un voyage sans fin utilisable : un client
    /// modifié, lu comme neutre et sans étiquette). Calculé au moment de la lecture, sans nouvelle lecture du serveur.
    static func of(
        presence: PresenceValue?,
        untilMillis: Int?,
        untilDay: String?,
        nowMillis: Int,
        zone: TimeZone
    ) -> TravelView? {
        guard presence == .travel, let untilMillis else {
            return nil
        }
        if nowMillis >= untilMillis {
            return .ended
        }
        let today = TravelDate.today(nowMillis: nowMillis, zone: zone)
        guard let day = TravelDay.of(untilDay: untilDay, untilMillis: untilMillis, today: today, zone: zone) else {
            return nil
        }
        return .active(day)
    }
}

/// Ce que le téléphone lit de MON statut dans une Miff pour décider s'il adopte un voyage (nouvel appareil).
struct TravelRead: Equatable, CustomStringConvertible {
    let period: TravelPeriod
    let sinceMillis: Int?

    var description: String {
        "TravelRead(redacted)"
    }
}

/// Le voyage et le niveau de batterie d'un statut : jamais de niveau à côté d'un voyage (les règles refusent `travel` + batterie).
enum TravelStatusRules {
    /// Le niveau de batterie à écrire dans un statut : nil pendant un voyage, et tant qu'un statut garde encore les clés d'un voyage
    /// terminé (une mise à jour partielle avec un niveau serait refusée).
    static func battery(_ level: Int?, local: LocalState?, uid: String?) -> Int? {
        guard let uid, let local, local.uid == uid else {
            return level
        }
        if local.travel != nil || local.miffs.values.contains(where: { $0.clearTravel }) {
            return nil
        }
        return level
    }
}
