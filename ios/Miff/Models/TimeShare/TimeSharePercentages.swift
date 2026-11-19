import Foundation

// Les pourcentages entiers d'une répartition (ticket §5.3), en calcul ENTIER : aucune erreur d'arrondi flottant, donc des égalités
// exactes et le même résultat qu'Android. Du Swift pur.

enum TimeSharePercentages {
    struct Share: Equatable {
        let percent: Int
        /// Part réelle de plus de 0 et de moins de 1 % : affichée « < 1 % », comptée 0 dans la répartition.
        let isUnderOne: Bool
    }

    /// Les pourcentages entiers de ces durées (dans l'ordre de la légende) : somme EXACTEMENT 100, méthode du plus grand reste ; égalité :
    /// la plus grande durée réelle, puis l'ordre de la légende. Une part réelle de moins de 1 % compte 0 (« < 1 % ») : les autres se
    /// répartissent les 100 points, d'après leur propre temps.
    static func allocate(_ millis: [Int]) -> [Share] {
        let total = millis.reduce(0, +)
        guard total > 0 else {
            return millis.map { _ in Share(percent: 0, isUnderOne: false) }
        }
        let under = millis.map { $0 > 0 && $0 * 100 < total }
        let base = millis.indices.filter { millis[$0] > 0 && !under[$0] }
        var percents = Array(repeating: 0, count: millis.count)
        for (index, value) in largestRemainder(millis, among: base, target: 100) {
            percents[index] = value
        }
        return millis.indices.map { Share(percent: percents[$0], isUnderOne: under[$0]) }
    }

    /// Répartit `target` points entre `indices` en proportion de leurs durées (plus grand reste). Le détail d'une ligne (la zone de danger)
    /// s'arrondit ainsi À L'INTÉRIEUR de sa ligne : il totalise toujours le pourcentage de la ligne.
    static func largestRemainder(_ millis: [Int], among indices: [Int], target: Int) -> [Int: Int] {
        let baseTotal = indices.reduce(0) { $0 + millis[$1] }
        guard baseTotal > 0, target > 0 else {
            return Dictionary(uniqueKeysWithValues: indices.map { ($0, 0) })
        }
        var result: [Int: Int] = [:]
        var remainders: [(index: Int, remainder: Int)] = []
        var given = 0
        for index in indices {
            let scaled = millis[index] * target
            result[index] = scaled / baseTotal
            remainders.append((index: index, remainder: scaled % baseTotal))
            given += scaled / baseTotal
        }
        let ranked = remainders.sorted { left, right in
            if left.remainder != right.remainder {
                return left.remainder > right.remainder
            }
            if millis[left.index] != millis[right.index] {
                return millis[left.index] > millis[right.index]
            }
            return left.index < right.index
        }
        for item in ranked.prefix(max(0, target - given)) {
            result[item.index, default: 0] += 1
        }
        return result
    }
}
