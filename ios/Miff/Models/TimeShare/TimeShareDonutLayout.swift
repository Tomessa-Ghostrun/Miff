import Foundation

// La géométrie de l'anneau, du Swift pur : quelle tranche, de quel angle à quel angle, de quelle couleur. Le dessin (`TimeShareDonut`) ne fait
// que l'afficher. Liste ORDONNÉE de catégories typées, jamais de « lieux » en dur (ticket §10.4) : ajouter une catégorie plus tard est une
// ligne et un token, sans toucher au calcul, à la mise en page ni aux textes d'accessibilité.

/// Le style d'une tranche : la couleur est celle du jeton, jamais seule (légende, hachures de la zone de danger).
enum TimeShareSliceStyle: Equatable {
    /// Un lieu de confiance nommé : la couleur suit son rang dans la légende (`ChartTokens.places`).
    case place(rank: Int)
    case otherPlaces
    case danger
    case elsewhere

    var color: ColorToken {
        switch self {
        case .place(let rank):
            return ChartTokens.places[min(max(rank, 0), ChartTokens.places.count - 1)]
        case .otherPlaces:
            return colorChartOthers
        case .danger:
            return colorChartDanger
        case .elsewhere:
            return colorChartElsewhere
        }
    }

    /// Seule la zone de danger est hachurée : le motif double la couleur.
    var isHatched: Bool {
        self == .danger
    }
}

/// Une tranche de l'anneau : de `start` à `end`, en fraction de tour, à partir de midi dans le sens horaire.
struct TimeShareSlice: Equatable, CustomStringConvertible {
    let index: Int
    let start: Double
    let end: Double
    let style: TimeShareSliceStyle

    var sweep: Double {
        end - start
    }

    var description: String {
        "TimeShareSlice(redacted)"
    }
}

enum TimeShareDonutLayout {
    /// Une tranche par ligne de la légende, dans le même ordre, d'après les durées RÉELLES (l'anneau est exact ; seuls les pourcentages sont
    /// arrondis). La première tranche commence à midi.
    static func slices(for result: TimeShareResult) -> [TimeShareSlice] {
        guard result.measuredMillis > 0 else {
            return []
        }
        var cursor = 0.0
        var rank = 0
        var slices: [TimeShareSlice] = []
        for (index, row) in result.rows.enumerated() {
            let sliceStyle = Self.style(of: row.kind, rank: &rank)
            let share = Double(row.millis) / Double(result.measuredMillis)
            let end = index == result.rows.count - 1 ? 1.0 : min(1.0, cursor + share)
            slices.append(TimeShareSlice(index: index, start: cursor, end: end, style: sliceStyle))
            cursor = end
        }
        return slices
    }

    /// Le style d'une ligne ; `rank` compte les lieux de confiance nommés rencontrés.
    static func style(of kind: TimeShareRowKind, rank: inout Int) -> TimeShareSliceStyle {
        switch kind {
        case .place:
            let current = rank
            rank += 1
            return .place(rank: current)
        case .otherPlaces:
            return .otherPlaces
        case .danger:
            return .danger
        case .elsewhere:
            return .elsewhere
        }
    }

    /// Les styles de toutes les lignes, dans l'ordre (la légende s'en sert pour ses pastilles).
    static func styles(for rows: [TimeShareRow]) -> [TimeShareSliceStyle] {
        var rank = 0
        return rows.map { style(of: $0.kind, rank: &rank) }
    }
}
