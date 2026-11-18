#if DEBUG
import SwiftUI

/// La carte « Répartition de ton temps » montrée SEULE avec un état fixe, pour les captures d'écran de la CI
/// (`xcrun simctl launch ... -MiffPreview timeshare-nominal`). Aucun réseau, aucune session, aucun fichier : les faits sont écrits ici
/// (lieux et durées de démonstration) et passent par le VRAI calcul. Compilé en configuration Debug SEULEMENT, comme `ScreenPreview`.
/// États : `timeshare-nominal` (anneau, légende, zone de danger, synthèse), `timeshare-many` (plus de 5 lieux : « {n} autres lieux »),
/// `timeshare-empty` (pas assez de données), `timeshare-location` (localisation insuffisante), `timeshare-travel`, `timeshare-sharing`,
/// `timeshare-error`, `timeshare-danger` (le détail de la zone de danger).
struct TimeShareScreenPreview: View {
    let name: String

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                Text(verbatim: "Mes Miffs")
                    .miffTextStyle(textDisplayTitle)
                    .foregroundColor(colorTextForm.color)
                if name == "timeshare-danger" {
                    TimeShareDangerSheet(placesCount: details.count, details: details, onClose: {})
                        .frame(height: 360)
                } else {
                    TimeShareCard(display: display, actions: TimeShareActions())
                }
            }
            .padding(Spacing.lg)
        }
        .background(gradientProfileHeader.ignoresSafeArea())
    }

    // MARK: - Les lieux et les faits de démonstration

    private static let now = Int(Date().timeIntervalSince1970 * 1000)
    private static let hour = TimeShareLimits.hourMillis

    private static let places: [TimeSharePlace] = [
        TimeSharePlace(id: "p1", name: "Maison", type: .trusted, category: .home),
        TimeSharePlace(id: "p2", name: "Sport", type: .trusted, category: .sport),
        TimeSharePlace(id: "p3", name: "Bureau", type: .trusted, category: .work),
        TimeSharePlace(id: "r1", name: "Chantier rue Haute", type: .risk, category: .other),
        TimeSharePlace(id: "r2", name: "Entrepôt", type: .risk, category: nil)
    ]

    private static func fact(_ state: PresenceState, _ placeId: String?, hoursAgo: Int) -> PresenceFact {
        PresenceFact(miffId: "demo", state: state, placeId: placeId, atMillis: now - hoursAgo * hour)
    }

    /// 7 jours : Maison 84 h, Sport 21 h, Chantier 9 h, Entrepôt 6 h, dehors 48 h.
    private static let nominalFacts: [PresenceFact] = [
        fact(.trusted, "p1", hoursAgo: 168), fact(.out, nil, hoursAgo: 120), fact(.trusted, "p2", hoursAgo: 96),
        fact(.out, nil, hoursAgo: 75), fact(.trusted, "p1", hoursAgo: 72), fact(.danger, "r1", hoursAgo: 30),
        fact(.out, nil, hoursAgo: 21), fact(.danger, "r2", hoursAgo: 20), fact(.out, nil, hoursAgo: 14),
        fact(.trusted, "p1", hoursAgo: 12)
    ]

    private var details: [TimeShareDangerDetail] {
        if case .nominal(let result, _) = display.state {
            return result.dangerDetails
        }
        return []
    }

    private var display: TimeShareDisplay {
        TimeShareDisplay(state: state, windowDays: 7, overWatchedLimit: name == "timeshare-many")
    }

    private var state: TimeShareCardState {
        switch name {
        case "timeshare-empty":
            return .notEnough(daysLeft: 5)
        case "timeshare-location":
            return .locationInsufficient
        case "timeshare-travel":
            return .travel
        case "timeshare-sharing":
            return .sharingStopped
        case "timeshare-error":
            return .unavailable
        case "timeshare-many":
            return many()
        default:
            return compute(Self.places, Self.nominalFacts)
        }
    }

    private func compute(_ places: [TimeSharePlace], _ facts: [PresenceFact]) -> TimeShareCardState {
        let outcome = TimeShareCalculator.compute(facts: facts, places: places, windowDays: 7, nowMillis: Self.now)
        return TimeShareCardState.make(
            places: places, readFailed: false, outcome: outcome, conditions: TimeShareConditions()
        )
    }

    /// Sept lieux de confiance et une zone de danger : cinq nommés, « 2 autres lieux », la zone de danger, « Ailleurs ».
    private func many() -> TimeShareCardState {
        let types: [PlaceCategory] = [.home, .work, .school, .sport, .relative, .leisure, .other]
        var places: [TimeSharePlace] = types.enumerated().map { index, category in
            TimeSharePlace(id: "p\(index + 1)", name: "Lieu \(index + 1)", type: .trusted, category: category)
        }
        places.append(TimeSharePlace(id: "r1", name: "Chantier", type: .risk, category: nil))
        var facts: [PresenceFact] = []
        var ago = 168
        for index in 0..<7 {
            facts.append(Self.fact(.trusted, "p\(index + 1)", hoursAgo: ago))
            ago -= 20 - index * 2
            facts.append(Self.fact(.out, nil, hoursAgo: ago))
            ago -= 3
        }
        facts.append(Self.fact(.danger, "r1", hoursAgo: 6))
        facts.append(Self.fact(.out, nil, hoursAgo: 3))
        return compute(places, facts)
    }
}

/// Le contexte des aperçus : le suivi est actif, rien n'est lu.
@MainActor
final class DemoTimeShareContext: TimeShareContextSource {
    func conditions(miffId: String) async -> TimeShareConditions {
        TimeShareConditions()
    }
}
#endif
