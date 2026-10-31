import Foundation

/// Un point d'un chemin vectoriel.
struct PathPoint: Equatable {
    let px: Double
    let py: Double
}

/// Une instruction d'un chemin vectoriel, en coordonnées absolues.
enum PathCommand: Equatable {
    case move(PathPoint)
    case line(PathPoint)
    case curve(control1: PathPoint, control2: PathPoint, end: PathPoint)
    case close
}

/// Lit les données d'un chemin SVG (l'attribut `d`) : M, L, H, V, C, S, Z en majuscules et en minuscules, avec les
/// répétitions implicites (« L1 2 3 4 » veut dire deux segments). C'est tout ce que contiennent les logos de Miff
/// (design/assets/logo) et le « G » de Google ; une autre instruction (arcs, courbes quadratiques) arrête la lecture :
/// le résultat est alors ce qui a pu être lu jusque-là, jamais un plantage.
enum SVGPathParser {
    private enum Token {
        case command(Character)
        case number(Double)
    }

    static func parse(_ data: String) -> [PathCommand] {
        var reader = Reader(tokens: tokenize(data))
        return reader.readAll()
    }

    // MARK: - Découpage en instructions et nombres

    private static func tokenize(_ data: String) -> [Token] {
        let chars = Array(data)
        var tokens: [Token] = []
        var index = 0
        while index < chars.count {
            let char = chars[index]
            if char.isLetter {
                tokens.append(.command(char))
                index += 1
            } else if char == "-" || char == "+" || char == "." || char.isASCII && char.isNumber {
                let (value, next) = readNumber(chars, from: index)
                if let value {
                    tokens.append(.number(value))
                }
                index = max(next, index + 1)
            } else {
                index += 1
            }
        }
        return tokens
    }

    /// Lit un nombre à partir de `start` : signe, chiffres, un seul point, exposant. Rend aussi la position suivante.
    private static func readNumber(_ chars: [Character], from start: Int) -> (Double?, Int) {
        var index = start
        var text = ""
        if chars[index] == "-" || chars[index] == "+" {
            text.append(chars[index])
            index += 1
        }
        var seenDot = false
        while index < chars.count {
            let char = chars[index]
            if char.isASCII && char.isNumber {
                text.append(char)
            } else if char == "." && !seenDot {
                seenDot = true
                text.append(char)
            } else {
                break
            }
            index += 1
        }
        if index < chars.count, chars[index] == "e" || chars[index] == "E" {
            var exponent = "e"
            var probe = index + 1
            if probe < chars.count, chars[probe] == "-" || chars[probe] == "+" {
                exponent.append(chars[probe])
                probe += 1
            }
            var digits = ""
            while probe < chars.count, chars[probe].isASCII, chars[probe].isNumber {
                digits.append(chars[probe])
                probe += 1
            }
            if !digits.isEmpty {
                text += exponent + digits
                index = probe
            }
        }
        return (Double(text), index)
    }

    // MARK: - Interprétation

    private struct Reader {
        let tokens: [Token]
        var position = 0
        var commands: [PathCommand] = []
        var current = PathPoint(px: 0, py: 0)
        var subpathStart = PathPoint(px: 0, py: 0)
        /// Deuxième point de contrôle de la dernière courbe (pour l'instruction S).
        var lastControl: PathPoint?

        init(tokens: [Token]) {
            self.tokens = tokens
        }

        mutating func readAll() -> [PathCommand] {
            var command: Character = "M"
            while position < tokens.count {
                if case .command(let letter) = tokens[position] {
                    command = letter
                    position += 1
                    if letter == "Z" || letter == "z" {
                        commands.append(.close)
                        current = subpathStart
                        lastControl = nil
                        continue
                    }
                }
                guard let next = apply(command) else {
                    break
                }
                command = next
            }
            return commands
        }

        /// Lit les nombres d'UNE instruction ; rend l'instruction qui s'applique à la répétition implicite suivante, ou nil
        /// si la lecture doit s'arrêter (instruction inconnue, nombres manquants).
        private mutating func apply(_ command: Character) -> Character? {
            let relative = command.isLowercase
            switch command.uppercased() {
            case "M":
                guard let values = take(2) else { return nil }
                move(to: point(values[0], values[1], relative))
                return relative ? "l" : "L"
            case "L":
                guard let values = take(2) else { return nil }
                line(to: point(values[0], values[1], relative))
                return command
            case "H":
                guard let values = take(1) else { return nil }
                line(to: PathPoint(px: relative ? current.px + values[0] : values[0], py: current.py))
                return command
            case "V":
                guard let values = take(1) else { return nil }
                line(to: PathPoint(px: current.px, py: relative ? current.py + values[0] : values[0]))
                return command
            case "C":
                guard let values = take(6) else { return nil }
                curve(
                    point(values[0], values[1], relative),
                    point(values[2], values[3], relative),
                    point(values[4], values[5], relative)
                )
                return command
            case "S":
                guard let values = take(4) else { return nil }
                let reflected = lastControl.map { PathPoint(px: 2 * current.px - $0.px, py: 2 * current.py - $0.py) }
                curve(
                    reflected ?? current,
                    point(values[0], values[1], relative),
                    point(values[2], values[3], relative)
                )
                return command
            default:
                return nil
            }
        }

        private mutating func take(_ count: Int) -> [Double]? {
            var values: [Double] = []
            while values.count < count, position < tokens.count, case .number(let value) = tokens[position] {
                values.append(value)
                position += 1
            }
            return values.count == count ? values : nil
        }

        private func point(_ valueX: Double, _ valueY: Double, _ relative: Bool) -> PathPoint {
            relative ? PathPoint(px: current.px + valueX, py: current.py + valueY) : PathPoint(px: valueX, py: valueY)
        }

        private mutating func move(to target: PathPoint) {
            commands.append(.move(target))
            current = target
            subpathStart = target
            lastControl = nil
        }

        private mutating func line(to target: PathPoint) {
            commands.append(.line(target))
            current = target
            lastControl = nil
        }

        /// Pour la forme relative, `control2` et `end` sont relatifs au point courant AU DÉBUT de la courbe : `point`
        /// l'utilise tant que `current` n'a pas bougé.
        private mutating func curve(_ control1: PathPoint, _ control2: PathPoint, _ end: PathPoint) {
            commands.append(.curve(control1: control1, control2: control2, end: end))
            current = end
            lastControl = control2
        }
    }
}
