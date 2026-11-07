import Foundation

/// Règles des champs prénom et nom (ticket Onboarding §6.3, décision Q6) : 1 à 50 caractères une fois les espaces
/// retirés, aucun alphabet interdit (accents, traits d'union, apostrophes, écritures non latines, émojis acceptés),
/// aucune unicité. La limite de 50 est celle des règles Firestore ; elle est comptée en unités UTF-16 comme sur
/// Android (le décompte le plus strict : une limite respectée ici l'est quelle que soit la façon de compter du serveur).
///
/// Un nom est montré aux autres membres de chaque Miff : les caractères qui pourraient le faire passer pour autre chose
/// sont retirés (caractères de contrôle : un saut de ligne casserait une carte ; caractères invisibles ou qui inversent
/// le sens du texte : une marque de droite à gauche pourrait retourner le texte autour). Rien d'autre n'est touché.
enum ProfileForm {
    static let maxNameLength = ProfileDocument.maxNameLength
    /// Le petit compteur (« 45/50 ») n'apparaît que lorsque la limite approche.
    static let counterFrom = 45

    /// Un champ après application de la limite : `text` est ce que le champ garde, `wasCut` dit que quelque chose a été
    /// retiré.
    struct Limited: Equatable {
        let text: String
        let wasCut: Bool
    }

    /// Ce que garde un champ pendant la frappe : sans caractère de contrôle ni d'usurpation, 50 au plus. Les espaces
    /// sont gardés tels que tapés.
    static func limitInput(_ raw: String) -> Limited {
        let cleaned = removeUnsafe(raw)
        if cleaned.utf16.count > maxNameLength {
            return Limited(text: cutWithoutBreakingAPair(cleaned, max: maxNameLength), wasCut: true)
        }
        return Limited(text: cleaned, wasCut: false)
    }

    /// Ce qui est enregistré : caractères douteux retirés, espaces autour retirés, espaces répétés réduits à un seul.
    static func normalizeName(_ raw: String) -> String {
        removeUnsafe(raw)
            .replacingOccurrences(of: "[\\p{Z}\\s]+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// La flèche de l'étape profil est active quand les deux noms sont complets.
    static func isNameComplete(_ raw: String) -> Bool {
        let length = normalizeName(raw).utf16.count
        return length >= 1 && length <= maxNameLength
    }

    static func showCounter(length: Int) -> Bool {
        length >= counterFrom
    }

    /// Pré-remplissage à partir du nom donné par le fournisseur de connexion (Google, Apple). Une simple suggestion,
    /// toujours modifiable : le premier mot est le prénom, le reste le nom. Chaque partie est coupée à 50 caractères.
    /// Nil ou vide donne deux champs vides. Aucune photo n'est jamais reprise du fournisseur.
    static func splitDisplayName(_ displayName: String?) -> (first: String, last: String) {
        let name = normalizeName(displayName ?? "")
        guard !name.isEmpty else {
            return ("", "")
        }
        guard let space = name.firstIndex(of: " ") else {
            return (limitInput(name).text, "")
        }
        let first = String(name[name.startIndex..<space])
        let last = String(name[name.index(after: space)...])
        return (limitInput(first).text, limitInput(last).text)
    }

    private static func removeUnsafe(_ raw: String) -> String {
        var output = String.UnicodeScalarView()
        for scalar in raw.unicodeScalars {
            let code = scalar.value
            if isLineOrTabBreak(code) {
                output.append(" ")
            } else if isControl(code) || isInvisibleOrDirectional(code) {
                continue
            } else {
                output.append(scalar)
            }
        }
        return String(output)
    }

    /// Tabulation, sauts de ligne, onglet vertical, saut de page, « next line » : remplacés par une espace.
    private static func isLineOrTabBreak(_ code: UInt32) -> Bool {
        code == 0x09 || code == 0x0A || code == 0x0D || code == 0x0B || code == 0x0C || code == 0x85
    }

    private static func isControl(_ code: UInt32) -> Bool {
        code < 0x20 || (code >= 0x7F && code <= 0x9F)
    }

    /// Espace de largeur nulle, marques de direction, imbrications, forçages et isolats, joint de mots, marque d'ordre
    /// des octets, séparateurs de ligne et de paragraphe.
    private static func isInvisibleOrDirectional(_ code: UInt32) -> Bool {
        code == 0x200B || code == 0x200E || code == 0x200F || (code >= 0x202A && code <= 0x202E)
            || code == 0x2060 || (code >= 0x2066 && code <= 0x2069) || code == 0xFEFF
            || code == 0x2028 || code == 0x2029
    }

    private static func cutWithoutBreakingAPair(_ text: String, max: Int) -> String {
        var units = Array(text.utf16.prefix(max))
        if let last = units.last, last >= 0xD800 && last <= 0xDBFF {
            units.removeLast()
        }
        return String(decoding: units, as: UTF16.self)
    }
}
