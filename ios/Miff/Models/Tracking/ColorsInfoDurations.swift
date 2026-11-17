import Foundation

// Les durées dont parle l'écran « Les couleurs de ta Miff » (ticket COULEURS-INFO, D7) : LUES dans `StatusThresholds`, jamais écrites dans un
// texte (critère STA9). Si le PM change un seuil, l'écran suit. Les textes ne contiennent que `%1$@` ; les mots (« 2 heures », « 2 hours »)
// viennent du formateur du système, dans la langue du téléphone. Miroir d'Android (`ColorsInfoDurations.kt`).

struct ColorsInfoDurations: Equatable {
    /// Orange après cette durée sans nouvelles (et vert jusque-là) : 2 h aujourd'hui.
    var orangeAfterMillis = StatusThresholds.orangeAfterMillis
    /// Rouge après cette durée sans nouvelles : 4 h aujourd'hui.
    var redAfterMillis = StatusThresholds.redAfterMillis
    /// En zone de danger, vert pendant cette durée après une mise à jour : 1 h aujourd'hui.
    var dangerGreenMillis = StatusThresholds.dangerZoneRedAfterMillis

    /// Les seuils du Feed, tels qu'ils sont maintenant.
    static let fromThresholds = ColorsInfoDurations()
}

/// Une durée en nombre ENTIER d'une unité que le formateur du système sait écrire : des heures quand c'est un nombre entier d'heures, sinon des
/// minutes (aucun seuil n'est en minutes aujourd'hui ; l'écran doit rester juste si l'un le devient).
struct WholeDuration: Equatable {
    enum Unit: Equatable {
        case hour
        case minute
    }

    let amount: Int
    let unit: Unit

    private static let minuteMillis = 60_000
    private static let hourMillis = 60 * minuteMillis

    /// 7 200 000 -> 2 heures ; 5 400 000 -> 90 minutes.
    static func of(millis: Int) -> WholeDuration {
        if millis % hourMillis == 0 {
            return WholeDuration(amount: millis / hourMillis, unit: .hour)
        }
        return WholeDuration(amount: max(1, millis / minuteMillis), unit: .minute)
    }

    /// « 2 heures » / « 2 hours » : écrit par le formateur du système, dans la langue donnée.
    func text(locale: Locale) -> String {
        let formatter = MeasurementFormatter()
        formatter.locale = locale
        formatter.unitOptions = .providedUnit
        formatter.unitStyle = .long
        formatter.numberFormatter.maximumFractionDigits = 0
        let unit: UnitDuration = self.unit == .hour ? .hours : .minutes
        return formatter.string(from: Measurement(value: Double(amount), unit: unit))
    }
}
