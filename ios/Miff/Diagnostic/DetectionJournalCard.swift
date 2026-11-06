#if DEBUG || INTERNAL
import SwiftUI

// CONFIDENTIALITÉ, pour ce fichier : il montre le journal local de la détection (critère VIE2), qui ne peut contenir que le TYPE d'un
// événement, l'identifiant d'un lieu, l'heure et la batterie : jamais une coordonnée, une adresse, un nom. Compilé dans les builds Debug
// et Internal seulement (le PM le lit sur l'iPhone, via TestFlight, sans Mac). Aucune écriture, aucun envoi, rien dans le presse-papiers.

/// La carte « Journal de détection » du diagnostic : les derniers événements, du plus récent au plus ancien.
struct DetectionJournalCard: View {
    let journal: DetectionJournal

    @State private var lines: [JournalLine] = []

    private static let shown = 80

    var body: some View {
        SectionCard("ios_diag_detection_title", section: .detection) {
            DiagLine("ios_diag_detection_description", .description)
            HStack(spacing: Spacing.sm) {
                Button {
                    lines = journal.lines()
                } label: {
                    Text("ios_diag_detection_refresh")
                }
                .buttonStyle(.miffPrimary)
                Button {
                    journal.clear()
                    lines = []
                } label: {
                    Text("ios_diag_detection_clear")
                }
                .buttonStyle(.miffPrimary)
            }
            LiveResult {
                if lines.isEmpty {
                    DiagLine("ios_diag_detection_empty", .description)
                } else {
                    ForEach(Array(lines.suffix(Self.shown).reversed().enumerated()), id: \.offset) { _, line in
                        DiagLine(verbatim: Self.text(of: line))
                    }
                }
            }
        }
        .onAppear {
            lines = journal.lines()
        }
    }

    /// `heure  TYPE  lieu  batterie`, rien d'autre.
    static func text(of line: JournalLine) -> String {
        let date = Date(timeIntervalSince1970: Double(line.atMillis) / 1000)
        let time = date.formatted(date: .abbreviated, time: .standard)
        let place = line.placeId ?? "-"
        let battery = line.batteryPct.map { "\($0) %" } ?? "-"
        return "\(time)  \(line.kind.rawValue)  \(place)  \(battery)"
    }
}
#endif
