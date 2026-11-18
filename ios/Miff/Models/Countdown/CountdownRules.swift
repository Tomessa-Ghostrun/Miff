import Foundation

// COMPTE À REBOURS AVANT L'ORANGE (ticket `docs/tickets/compte-a-rebours-orange.md`, COUNTDOWN-I). Une fonction PURE : MON statut (celui du
// téléphone, jamais celui d'un proche), l'état « partage arrêté » et l'heure donnent « masqué » ou « visible » avec le temps restant. Rien
// n'est lu, rien n'est écrit, aucun réseau : tout vient de ce que l'appareil sait déjà (`MyStatusStore`) et de l'horloge. Aucune coordonnée.
//
// CONFIDENTIALITÉ : seule la personne concernée voit ce décompte, sur son appareil. `MyStatusFacts` est MON statut ; aucun type de ce
// fichier ne sait représenter un autre membre.

/// La durée affichée, déjà arrondie à la minute SUPÉRIEURE (§2.3 du ticket) : jamais de secondes, jamais « 0 min » avant l'échéance.
enum CountdownDuration: Equatable {
    /// 1 h 12 min (les minutes s'écrivent sur 2 chiffres : 1 h 05 min).
    case hoursMinutes(hours: Int, minutes: Int)
    /// 1 h, 2 h.
    case hours(Int)
    /// 47 min (de 2 à 59).
    case minutes(Int)
    /// Il reste 1 minute ou moins.
    case lessThanMinute

    /// La durée de `minutes` minutes entières (au moins 1).
    static func make(minutes: Int) -> CountdownDuration {
        if minutes <= 1 {
            return .lessThanMinute
        }
        if minutes < 60 {
            return .minutes(minutes)
        }
        let hours = minutes / 60
        let rest = minutes % 60
        return rest == 0 ? .hours(hours) : .hoursMinutes(hours: hours, minutes: rest)
    }
}

/// Ce que l'encart montre quand il est visible.
struct CountdownDisplay: Equatable, CustomStringConvertible {
    /// Le temps restant réel, en millisecondes (plafonné à la fenêtre, toujours > 0).
    let remainingMillis: Int
    /// Les minutes affichées : le temps restant arrondi à la minute supérieure (1 à 120).
    let minutes: Int
    /// L'instant (millisecondes depuis 1970) où ce que l'encart montre change : la prochaine frontière de minute alignée sur l'échéance, ou
    /// l'échéance elle-même (disparition) quand il reste une minute ou moins.
    let nextChangeMillis: Int

    var duration: CountdownDuration {
        CountdownDuration.make(minutes: minutes)
    }

    /// La part du contour encore pleine : minutes affichées × 60 s / fenêtre (1 = plein, ~0 = presque vide).
    var fraction: Double {
        Double(minutes * CountdownRules.minuteMillis) / Double(CountdownRules.windowMillis)
    }

    var description: String {
        "CountdownDisplay(redacted)"
    }
}

enum CountdownState: Equatable {
    case hidden
    case visible(CountdownDisplay)

    var isVisible: Bool {
        if case .visible = self {
            return true
        }
        return false
    }
}

enum CountdownRules {
    static let minuteMillis = 60 * 1000
    /// La fenêtre totale du vert = la constante UNIQUE de l'app (`StatusThresholds`), jamais recopiée ici.
    static var windowMillis: Int {
        StatusThresholds.orangeAfterMillis
    }

    /// L'état de l'encart. Il est visible si et seulement si : MON statut est connu, je partage, je suis DEHORS d'un lieu de confiance
    /// (`out` : ni dedans, ni en zone de danger, ni en voyage, ni inconnu) ET le niveau calculé par `StatusColor` (le même calcul que
    /// l'anneau de ma photo et le bouton central) est vert ET il reste du temps. Le repère est le plus récent du départ et de la dernière
    /// update, donc une update qui remet en vert repart d'une fenêtre pleine.
    static func state(_ status: MyStatusFacts?, sharingStopped: Bool, nowMillis: Int) -> CountdownState {
        guard let status, !sharingStopped, status.presence == .out else {
            return .hidden
        }
        guard UpdatePrompt.level(status, sharing: true, nowMillis: nowMillis) == .green else {
            return .hidden
        }
        let references = [status.presenceAtMillis, status.lastUpdateAtMillis].compactMap { $0 }
        guard let reference = references.max() else {
            return .hidden
        }
        // Un repère dans le futur (horloge reculée) ne donne jamais plus que la fenêtre ; jamais de valeur négative.
        let remaining = min(reference + windowMillis - nowMillis, windowMillis)
        guard remaining > 0 else {
            return .hidden
        }
        return .visible(display(remainingMillis: remaining, nowMillis: nowMillis))
    }

    /// Le temps restant arrondi à la minute SUPÉRIEURE : ⌈r / 60 s⌉, en entiers (les cas de test du ticket sont le contrat).
    static func displayedMinutes(remainingMillis: Int) -> Int {
        (remainingMillis + minuteMillis - 1) / minuteMillis
    }

    private static func display(remainingMillis: Int, nowMillis: Int) -> CountdownDisplay {
        let minutes = displayedMinutes(remainingMillis: remainingMillis)
        // r = (M - 1) minutes pile : la minute affichée passe à M - 1 (et à 0 = disparition quand M = 1).
        let next = nowMillis + remainingMillis - (minutes - 1) * minuteMillis
        return CountdownDisplay(remainingMillis: remainingMillis, minutes: minutes, nextChangeMillis: next)
    }
}
