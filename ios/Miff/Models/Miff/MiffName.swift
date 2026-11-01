import Foundation

/// Le nom d'une Miff (ticket Miff §5.2, décision QM6) : 2 à 40 caractères une fois les espaces des deux bouts retirés. Les
/// règles de Firestore acceptent 1 à 40 ; le minimum de 2 est une règle d'écran.
///
/// Les 40 sont comptés en unités UTF-16 (ce que comptent les règles : un émoji en prend 2). Le texte est coupé entre deux
/// caractères entiers tels que le lecteur les voit (une lettre avec son accent, un émoji avec ses modificateurs), jamais au
/// milieu de l'un d'eux.
///
/// Les caractères invisibles qui changent la façon dont un texte est dessiné ou lu sont retirés, dans ce qui est tapé ET
/// dans ce qui vient du serveur (un nom est montré à des personnes qui ne détiennent que le code : un autre téléphone a pu
/// écrire n'importe quoi) : caractères de contrôle, contrôles bidirectionnels (ils peuvent faire lire un nom à l'envers ou
/// cacher la fin d'une phrase), espaces de largeur nulle, joint de mots, marque d'ordre des octets, trait d'union
/// conditionnel. Les deux liants U+200C et U+200D restent : les séquences d'émojis et plusieurs écritures en ont besoin.
enum MiffName {
    static let minLength = 2
    static let maxLength = 40

    /// Contrôles bidirectionnels et caractères de format invisibles qui ne font jamais partie d'un nom.
    private static let invisible: Set<UInt32> = {
        var codes: Set<UInt32> = [0x061C, 0x00AD, 0x200B, 0x200E, 0x200F, 0x2060, 0xFEFF]
        for code in 0x202A...0x202E {
            codes.insert(UInt32(code))
        }
        for code in 0x2066...0x2069 {
            codes.insert(UInt32(code))
        }
        return codes
    }()

    /// `text` sans caractère de contrôle ni caractère de format invisible. Appliqué à ce qui est tapé ET à ce qui est lu.
    static func clean(_ text: String) -> String {
        var output = String.UnicodeScalarView()
        for scalar in text.unicodeScalars where !isControl(scalar.value) && !invisible.contains(scalar.value) {
            output.append(scalar)
        }
        return String(output)
    }

    private static func isControl(_ value: UInt32) -> Bool {
        value < 0x20 || (value >= 0x7F && value <= 0x9F)
    }

    /// Un nom lu sur le serveur, prêt à être montré : nettoyé, sans les espaces des bords, ou nil quand plus rien de visible
    /// ne reste (l'appelant le traite alors comme un nom absent). Ne jamais montrer un nom venu du serveur sans passer par
    /// ici (le nom d'un code est écrit par son détenteur AVANT tout consentement du lecteur). Un nom du serveur n'est PAS coupé
    /// à 40 unités : seules les règles de Firestore le bornent.
    static func fromServer(_ text: String) -> String? {
        let visible = clean(text).trimmingCharacters(in: .whitespacesAndNewlines)
        return visible.isEmpty ? nil : visible
    }

    /// Ce que le champ garde de `typed` : nettoyé, 40 unités au plus, coupé entre deux caractères entiers. `limitReached`
    /// vaut vrai quand quelque chose a été coupé ou que le champ est plein (« Limite de 40 caractères atteinte. »).
    static func limit(_ typed: String) -> NameInput {
        let cleaned = clean(typed)
        let total = cleaned.utf16.count
        if total <= maxLength {
            return NameInput(text: cleaned, limitReached: total == maxLength)
        }
        return NameInput(text: cut(cleaned), limitReached: true)
    }

    /// Le plus long début de `text` fait de caractères entiers qui tient dans `maxLength` unités.
    private static func cut(_ text: String) -> String {
        var units = 0
        var result = ""
        for character in text {
            let size = String(character).utf16.count
            if units + size > maxLength {
                break
            }
            units += size
            result.append(character)
        }
        if result.isEmpty {
            // Un seul « caractère » plus long que la limite : repli sur le dernier point de code entier.
            return cutScalars(text)
        }
        return result
    }

    private static func cutScalars(_ text: String) -> String {
        var units = 0
        var output = String.UnicodeScalarView()
        for scalar in text.unicodeScalars {
            let size = scalar.value > 0xFFFF ? 2 : 1
            if units + size > maxLength {
                break
            }
            units += size
            output.append(scalar)
        }
        return String(output)
    }

    /// Le nom tel qu'il est enregistré : le texte tapé sans les espaces des deux bouts.
    static func stored(_ typed: String) -> String {
        limit(typed).text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func isValid(_ typed: String) -> Bool {
        let length = stored(typed).utf16.count
        return length >= minLength && length <= maxLength
    }
}

/// Ce que garde le champ du nom. Rien de ce qu'il contient n'est montré par `description`.
struct NameInput: Equatable, CustomStringConvertible {
    let text: String
    let limitReached: Bool

    var description: String {
        "NameInput(redacted)"
    }
}

/// Les initiales d'une personne, pour sa pastille quand elle n'a pas de photo.
enum Initials {
    static func of(first: String, last: String) -> String {
        let letters = [first, last].compactMap { name -> String? in
            guard let letter = name.trimmingCharacters(in: .whitespacesAndNewlines).first else {
                return nil
            }
            return String(letter).uppercased()
        }
        return letters.isEmpty ? "?" : letters.joined()
    }
}

/// Un identifiant de document Firestore généré par le SDK (20 lettres et chiffres) : même forme que les règles. Une valeur
/// lue sur le réseau est vérifiée avant de devenir un morceau de chemin.
enum MiffId {
    static func isValid(_ value: String) -> Bool {
        let scalars = Array(value.unicodeScalars)
        return scalars.count == 20 && scalars.allSatisfy { scalar in
            (scalar.value >= 0x30 && scalar.value <= 0x39)
                || (scalar.value >= 0x41 && scalar.value <= 0x5A)
                || (scalar.value >= 0x61 && scalar.value <= 0x7A)
        }
    }
}
