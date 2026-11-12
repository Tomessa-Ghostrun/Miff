import Foundation

// MODE VOYAGE, N7 (VOY-4I, miroir d'Android VOY-4A ; tickets mode-voyage §9.1 et notifications §20.12bis) : « Ton mode voyage est terminé ».
// Du Swift PUR : l'heure et la clé de la notification de fin d'un voyage. La fin d'un voyage est un instant absolu (00:00 local du jour
// qui SUIT le dernier jour choisi) ; N7 part à 08:00 local ce matin-là, jamais en pleine nuit. Rien ici ne tient une position, un lieu ni un
// nom : un type, une clé et une heure.

enum TravelEndNotice {
    /// L'heure (locale) à laquelle N7 part.
    static let hour = 8
    /// Un rattrapage de N7 dont l'heure est passée reste une entrée silencieuse du centre pendant ce temps ; au-delà, le voyage est oublié.
    static let catchUpWindowMillis = 24 * 60 * 60 * 1000

    /// 08:00 du jour local de la fin, et jamais avant la fin elle-même (un fuseau changé pendant le voyage ne doit pas ramener N7 avant la
    /// fin : on prend alors le 08:00 suivant). Avec la fin habituelle (00:00 local) c'est `fin + 8 h`, jours de changement d'heure compris.
    static func dueAt(untilMillis: Int, calendar: Calendar) -> Int {
        let end = Date(timeIntervalSince1970: Double(untilMillis) / 1000)
        let day = calendar.startOfDay(for: end)
        let first = eight(of: day, calendar: calendar)
        // « >= » : quand 08:00 local EST la fin elle-même, N7 part tout de suite, pas un jour trop tard.
        if first >= untilMillis {
            return first
        }
        let next = calendar.date(byAdding: .day, value: 1, to: day) ?? day.addingTimeInterval(24 * 60 * 60)
        return eight(of: next, calendar: calendar)
    }

    /// La clé de déduplication (ticket §9.1) : un voyage qui finit à `untilMillis` est notifié une fois ; une fin déplacée est une autre clé.
    static func key(uid: String, untilMillis: Int) -> String {
        "n7|\(uid)|\(untilMillis)"
    }

    /// Je suis EN voyage tant que la fin n'est pas arrivée (la fin elle-même est déjà le retour).
    static func isTravelling(untilMillis: Int?, nowMillis: Int) -> Bool {
        guard let untilMillis else {
            return false
        }
        return nowMillis < untilMillis
    }

    private static func eight(of day: Date, calendar: Calendar) -> Int {
        let date = calendar.date(bySettingHour: hour, minute: 0, second: 0, of: day) ?? day
        return Int((date.timeIntervalSince1970 * 1000).rounded())
    }
}
