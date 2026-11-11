import Foundation

/// Un jour de voyage écrit dans la langue du lecteur par le formateur du SYSTÈME, jamais à la main : « 15 nov. » / « Nov 15 » (l'année
/// s'y ajoute seulement quand ce n'est pas l'année en cours : « 15 nov. 2027 »). Partagé par le Profil et le Feed. Le jour est celui du
/// calendrier (sans fuseau) : il est posé à midi UTC et formaté en UTC, pour qu'aucun fuseau ne le décale. Rien n'est journalisé.
enum TravelDayFormat {
    /// « 15 nov. » (avec l'année quand `TravelDay.showYear`).
    static func short(_ day: TravelDay, locale: Locale = .current) -> String {
        format(day.date, template: day.showYear ? "yMMMd" : "MMMd", locale: locale)
    }

    /// « 15 nov. 2026 » : la date de la ligne « Date de fin », toujours avec son année.
    static func withYear(_ date: TravelDate, locale: Locale = .current) -> String {
        format(date, template: "yMMMd", locale: locale)
    }

    /// Le même jour en toutes lettres (« 15 novembre 2026 »), pour le lecteur d'écran.
    static func spoken(_ date: TravelDate, locale: Locale = .current) -> String {
        format(date, template: "yMMMMd", locale: locale)
    }

    private static func format(_ date: TravelDate, template: String, locale: Locale) -> String {
        guard let noon = date.noonUTC else {
            return date.iso
        }
        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.timeZone = TimeZone(secondsFromGMT: 0) ?? .current
        formatter.setLocalizedDateFormatFromTemplate(template)
        return formatter.string(from: noon)
    }
}
