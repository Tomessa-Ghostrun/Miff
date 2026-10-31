import Foundation
import Security

// SÉCURITÉ, pour ce fichier : le code d'une Miff est un secret (qui le détient entre dans la Miff). Il est tiré par
// l'appareil avec un générateur cryptographique, jamais journalisé, jamais mis dans un événement Analytics, un rapport de
// plantage ou un message d'erreur.

/// Le code secret d'une Miff (ticket Miff §5.3, backend/data-model.md §2.4) : 12 caractères parmi 31 symboles (ni I, L, O,
/// 0, 1, qu'on confond entre eux), environ 59 bits. Même alphabet et même longueur que les règles Firestore.
enum JoinCode {
    static let alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    static let length = 12
    static let groupSize = 4

    private static let alphabetScalars = Set(alphabet.unicodeScalars)
    private static let symbols = Array(alphabet)

    /// Même forme que la règle `isJoinCode` : 12 caractères de l'alphabet, rien d'autre (pas même une fin de ligne).
    static func isValid(_ code: String) -> Bool {
        let scalars = Array(code.unicodeScalars)
        return scalars.count == length && scalars.allSatisfy { alphabetScalars.contains($0) }
    }

    /// Un nouveau code. Échantillonnage par rejet : un octet n'est utilisé que s'il tombe dans le plus grand multiple de 31
    /// contenu dans 256 (248), pour qu'aucun symbole ne soit plus probable qu'un autre (aucun biais de modulo). Vide
    /// (donc invalide, refusé par tous les appelants) si le générateur ne donne jamais rien d'utilisable : jamais un code
    /// faible.
    static func generate(randomBytes: (Int) -> [UInt8] = JoinCode.secureRandomBytes) -> String {
        let limit = 256 - 256 % symbols.count
        var output: [Character] = []
        var rounds = 0
        while output.count < length && rounds < 64 {
            rounds += 1
            for byte in randomBytes(length * 2) where output.count < length && Int(byte) < limit {
                output.append(symbols[Int(byte) % symbols.count])
            }
        }
        return output.count == length ? String(output) : ""
    }

    /// Octets aléatoires du système (`SecRandomCopyBytes`) ; si l'appel échoue, le générateur du système de Swift, qui
    /// est lui aussi cryptographique sur les plateformes d'Apple.
    static func secureRandomBytes(_ count: Int) -> [UInt8] {
        var bytes = [UInt8](repeating: 0, count: count)
        let status = SecRandomCopyBytes(kSecRandomDefault, count, &bytes)
        if status == errSecSuccess {
            return bytes
        }
        var generator = SystemRandomNumberGenerator()
        return bytes.map { _ in UInt8.random(in: 0...255, using: &generator) }
    }

    /// Ce qui a été tapé ou collé, nettoyé : majuscules, sans espaces, tirets, tabulations ni espaces insécables (un code
    /// se lit « XTZR SP36 7988 » ou se copie « XTZR-SP36-7988 »). Rien d'autre n'est changé : une lettre hors de l'alphabet
    /// reste, pour qu'on le dise à la personne (`analyze`).
    static func normalize(_ raw: String) -> String {
        var output = String.UnicodeScalarView()
        for scalar in raw.unicodeScalars where !isSeparator(scalar) {
            output.append(contentsOf: String(scalar).uppercased().unicodeScalars)
        }
        return String(output)
    }

    /// Tiret, tiret insécable, tirets de chiffres, demi-cadratin, cadratin, signe moins, souligné : ce avec quoi un code est
    /// copié.
    private static func isSeparator(_ scalar: Unicode.Scalar) -> Bool {
        if scalar.properties.isWhitespace {
            return true
        }
        switch scalar.value {
        case 0x2D, 0x5F, 0x2010, 0x2011, 0x2012, 0x2013, 0x2014, 0x2212:
            return true
        default:
            return false
        }
    }

    /// Ce que l'écran doit savoir du texte du champ (aucun réseau, ticket critère J2).
    static func analyze(_ raw: String) -> CodeInput {
        let cleaned = normalize(raw)
        // Le champ garde les 12 premiers caractères : ce qui dépasse n'est pas gardé (un texte collé plus long est coupé, et
        // la personne voit les 12 gardés). Seul ce qui est gardé est jugé.
        var kept = String.UnicodeScalarView()
        kept.append(contentsOf: cleaned.unicodeScalars.prefix(length))
        let hasForeign = kept.contains { !alphabetScalars.contains($0) }
        return CodeInput(
            code: String(kept),
            hasForeignCharacter: hasForeign,
            isComplete: kept.count == length && !hasForeign
        )
    }

    /// « XTZRSP367988 » devient « XTZR SP36 7988 » (affichage et partage).
    static func grouped(_ code: String) -> String {
        groups(of: code).joined(separator: " ")
    }

    /// Lu à voix haute par groupes de 4, lettre par lettre : « X T Z R, S P 3 6, 7 9 8 8 ».
    static func spoken(_ code: String) -> String {
        groups(of: code)
            .map { group in group.map { String($0) }.joined(separator: " ") }
            .joined(separator: ", ")
    }

    private static func groups(of code: String) -> [String] {
        let characters = Array(code)
        var result: [String] = []
        var start = 0
        while start < characters.count {
            let end = min(start + groupSize, characters.count)
            result.append(String(characters[start..<end]))
            start = end
        }
        return result
    }
}

/// L'état du champ du code. `code` fait 12 caractères au plus, normalisé. Rien de ce qu'il contient n'est montré par
/// `description`.
struct CodeInput: Equatable, CustomStringConvertible {
    let code: String
    let hasForeignCharacter: Bool
    let isComplete: Bool

    var description: String {
        "CodeInput(redacted)"
    }
}
