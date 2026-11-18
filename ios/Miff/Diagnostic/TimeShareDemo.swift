#if DEBUG || INTERNAL
import SwiftUI

// DONNÉES DE TEST de la carte « Répartition de ton temps » (ticket §11.13, critère TS-23) : UNIQUEMENT dans les builds Debug et Internal
// (TestFlight de la recette du PM), comme l'écran de diagnostic ; ce fichier n'est PAS compilé dans le build de publication (la CI cherche le
// marqueur TIMESHAREDEMO dans l'exécutable de publication). Elles permettent de voir la carte tout de suite, sans attendre 7 jours de port du
// téléphone. Elles ne sont JAMAIS écrites : gardées en mémoire, elles remplacent à l'écran ce que dit le journal, et disparaissent à la
// relance de l'app ; le vrai journal n'est pas touché. Le libellé dit toujours « données de test ».

/// Ce que montrent les données de test : rien (le vrai journal), un graphique (30 jours d'habitudes) ou « pas assez de données » à 2 jours (« dans
/// 5 jours ») ou à 6 jours (« dans 1 jour »).
enum TimeShareDemoMode {
    case off
    case chart
    case twoDays
    case sixDays

    var next: TimeShareDemoMode {
        switch self {
        case .off:
            return .chart
        case .chart:
            return .twoDays
        case .twoDays:
            return .sixDays
        case .sixDays:
            return .off
        }
    }

    /// Combien de jours d'historique inventer ; nil : le vrai journal.
    var historyDays: Int? {
        switch self {
        case .off:
            return nil
        case .chart:
            return TimeShareDemoData.chartDays
        case .twoDays:
            return 2
        case .sixDays:
            return 6
        }
    }
}

enum TimeShareDemoData {
    /// Jours d'historique de la démonstration du graphique (plus de 7, pour que les trois fenêtres aient de la matière).
    static let chartDays = 31
    /// Le marqueur que la CI cherche dans l'exécutable de publication : présent en Debug et Internal, ABSENT en publication.
    static let marker = "TIMESHAREDEMO-V1"

    /// Un moment de la journée de démonstration : l'heure (depuis minuit), l'état, le lieu.
    private struct Step {
        let hour: Double
        let state: PresenceState
        let placeId: String?
    }

    /// Une trentaine de jours d'habitudes inventées pour la Miff, avec SES lieux : la nuit dans le premier lieu de confiance, la journée dans
    /// le deuxième (en semaine), le mercredi soir dans le troisième, le reste « dehors » ; un passage de deux heures dans la zone de danger
    /// tous les cinq jours. Sans lieu d'un type, la journée est « dehors ». Les faits s'arrêtent à `nowMillis`.
    static func facts(
        miffId: String,
        places: [TimeSharePlace],
        nowMillis: Int,
        historyDays: Int = TimeShareDemoData.chartDays,
        calendar: Calendar = .current
    ) -> [PresenceFact] {
        let trusted = places.filter { $0.type == .trusted }
        let danger = places.first { $0.type == .risk }
        let home = trusted.first
        let work = trusted.dropFirst().first
        let leisure = trusted.dropFirst(2).first
        var facts: [PresenceFact] = []
        let today = calendar.startOfDay(for: Date(timeIntervalSince1970: Double(nowMillis) / 1000))
        for back in stride(from: historyDays, through: 0, by: -1) {
            guard let day = calendar.date(byAdding: .day, value: -back, to: today) else {
                continue
            }
            let weekday = calendar.component(.weekday, from: day)
            let isWeekend = weekday == 1 || weekday == 7
            let riskDay = back % 5 == 2
            func at(_ hour: Double) -> Int {
                Int(day.timeIntervalSince1970 * 1000) + Int(hour * Double(TimeShareLimits.hourMillis))
            }
            var steps: [Step] = []
            steps.append(Step(hour: 0, state: home == nil ? .out : .trusted, placeId: home?.id))
            steps.append(Step(hour: 8, state: .out, placeId: nil))
            if !isWeekend {
                steps.append(Step(hour: 8.75, state: work == nil ? .out : .trusted, placeId: work?.id))
                if riskDay, let danger {
                    steps.append(Step(hour: 14, state: .danger, placeId: danger.id))
                    steps.append(Step(hour: 16, state: work == nil ? .out : .trusted, placeId: work?.id))
                }
                steps.append(Step(hour: 17.5, state: .out, placeId: nil))
            }
            if weekday == 4, let leisure {
                steps.append(Step(hour: 18, state: .trusted, placeId: leisure.id))
                steps.append(Step(hour: 20, state: .out, placeId: nil))
            }
            steps.append(Step(hour: 20.5, state: home == nil ? .out : .trusted, placeId: home?.id))
            for step in steps where at(step.hour) <= nowMillis {
                let moment = at(step.hour)
                facts.append(PresenceFact(miffId: miffId, state: step.state, placeId: step.placeId, atMillis: moment))
            }
        }
        return facts
    }
}

/// La ligne de la recette sous la carte (builds de test seulement) : afficher ou retirer les données de test. Dit clairement ce qui est montré.
struct TimeShareDemoRow: View {
    @ObservedObject var model: TimeShareViewModel

    private var label: String {
        switch model.demoMode {
        case .off:
            return "ios_diag_timeshare_demo_chart"
        case .chart:
            return "ios_diag_timeshare_demo_two_days"
        case .twoDays:
            return "ios_diag_timeshare_demo_six_days"
        case .sixDays:
            return "ios_diag_timeshare_demo_off"
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Button {
                model.cycleDemo()
            } label: {
                Text(LocalizedStringKey(label))
            }
            .buttonStyle(.miffSecondaryFullWidth)
            Text("ios_diag_timeshare_demo_caption")
                .accessibilityIdentifier(TimeShareDemoData.marker)
                .miffTextStyle(textBodyCaption)
                .foregroundColor(colorTextDescription.color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}
#endif
