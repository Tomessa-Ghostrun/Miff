import Foundation

// Les textes de la carte « Répartition de ton temps » (clés `profile_timeshare_*`, MÊMES clés et mêmes textes qu'Android). CONFIDENTIALITÉ :
// le nom d'un lieu passe ici pour être DIT à l'écran ou au lecteur d'écran ; jamais pour être journalisé. Aucun pourcentage ni aucune durée
// ne sort de ce fichier vers un journal, un événement ou un rapport de plantage (critère TS-4).

enum TimeShareTexts {
    /// « 72 % » en français (espace insécable fine), « 72% » en anglais : le format de pourcentage du SYSTÈME, jamais un texte construit.
    static func percent(_ value: Int, locale: Locale = .current) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .percent
        formatter.maximumFractionDigits = 0
        formatter.locale = locale
        return formatter.string(from: NSNumber(value: Double(value) / 100)) ?? "\(value) %"
    }

    /// Le pourcentage d'une ligne : « < 1 % » pour une part réelle de moins de 1 %.
    static func percent(of row: TimeShareRow, locale: Locale = .current) -> String {
        row.isUnderOnePercent ? L10n.string("profile_timeshare_percent_tiny") : percent(row.percent, locale: locale)
    }

    static func percent(of detail: TimeShareDangerDetail, locale: Locale = .current) -> String {
        if detail.isUnderOnePercent {
            return L10n.string("profile_timeshare_percent_tiny")
        }
        return percent(detail.percent, locale: locale)
    }

    /// « Sur les 7 derniers jours ».
    static func subtitle(window: Int) -> String {
        L10n.string("profile_timeshare_subtitle_\(window)")
    }

    /// « 7 jours » : le choix du sélecteur.
    static func windowLabel(_ window: Int) -> String {
        L10n.string("profile_timeshare_window_\(window)")
    }

    /// Le nom d'une ligne de la légende : le nom ACTUEL du lieu, « {n} autres lieux », « Zone de danger » (« Zones de danger » s'il y en a
    /// plusieurs), « Ailleurs ».
    static func name(of row: TimeShareRow) -> String {
        switch row.kind {
        case .place:
            return row.name ?? ""
        case .otherPlaces(let count):
            return L10n.plural("profile_timeshare_other_places", count: count)
        case .danger(let count):
            return dangerZones(count)
        case .elsewhere:
            return L10n.string("profile_timeshare_elsewhere")
        }
    }

    /// « Zone de danger » (un lieu) ou « Zones de danger » (plusieurs). Deux clés : Xcode refuse un pluriel qui ne cite pas le nombre ; la
    /// clé du singulier est celle d'Android (parité), celle du pluriel est propre à iOS.
    static func dangerZones(_ count: Int) -> String {
        L10n.string(count > 1 ? "ios_timeshare_danger_zones_many" : "profile_timeshare_danger_zones")
    }

    /// « Ton graphique arrive dans 3 jours, nous analysons tes habitudes » : wording FIGÉ par le PM, pluriel du catalogue (« dans 1 jour »).
    static func notEnough(daysLeft: Int) -> String {
        L10n.plural("profile_timeshare_not_enough", count: daysLeft)
    }

    /// L'encart de synthèse (maquette du PM) : « Tu as passé 72 % de ton temps dans tes zones de confiance ». Ne nomme jamais la zone de danger.
    static func summary(trustedPercent: Int, locale: Locale = .current) -> String {
        L10n.format("profile_timeshare_summary", percent(trustedPercent, locale: locale))
    }

    // MARK: - Pour le lecteur d'écran

    /// La description unique de l'anneau (qui est caché : décoratif) : « Répartition de ton temps. Sur les 7 derniers jours : Maison 48 %,
    /// Sport 24 %, Zone de danger 8 %, Ailleurs 20 %. dans tes lieux de confiance, 72 %. » Le NOM de la catégorie est dit, jamais l'emoji.
    static func spokenSummary(_ result: TimeShareResult, locale: Locale = .current) -> String {
        let parts = result.rows.map { "\(name(of: $0)) \(percent(of: $0, locale: locale))" }
        let card = L10n.format(
            "profile_timeshare_a11y_card", subtitle(window: result.windowDays), parts.joined(separator: ", ")
        )
        let center = L10n.format(
            "profile_timeshare_a11y_row",
            L10n.string("profile_timeshare_center_caption"),
            percent(result.trustedPercent, locale: locale)
        )
        return "\(card) \(center)."
    }

    /// La ligne de la zone de danger, dite seule : « Zone de danger, 12 %, afficher le détail ».
    static func spokenDangerRow(_ row: TimeShareRow, locale: Locale = .current) -> String {
        L10n.format("profile_timeshare_a11y_danger_row", name(of: row), percent(of: row, locale: locale))
    }

    /// Une ligne du détail : « Chantier rue Haute, 7 % ».
    static func spokenDetail(_ detail: TimeShareDangerDetail, locale: Locale = .current) -> String {
        L10n.format("profile_timeshare_a11y_row", detail.name, percent(of: detail, locale: locale))
    }
}
