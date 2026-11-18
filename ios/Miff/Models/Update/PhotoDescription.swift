import Foundation

// La DESCRIPTION facultative d'une update (ticket docs/tickets/description-update.md, DESC-I ; miroir de `PhotoDescription.kt` d'Android).
// Un texte libre écrit par la personne, montré aux membres de la Miff AU-DESSUS de « Photo de … » dans la photo en plein écran.
// CONFIDENTIALITÉ, pour ce fichier : des règles pures, rien n'est lu ni écrit ici, et rien n'est JAMAIS journalisé. Une description peut contenir
// des données personnelles : elle ne va nulle part ailleurs que dans Firestore, et n'apparaît jamais dans une notification, un journal, un
// événement ou un rapport de plantage.
//
// Le contrat avec backend/firestore.rules (`validPhoto`) : la clé `description` est ABSENTE quand il n'y a pas de description (jamais `null`,
// jamais un texte vide), sinon un texte de 1 à 140 unités UTF-16 (`String.utf16.count` ; un emoji simple = 2). Mêmes nombres et même nettoyage
// que les prénoms (`ProfileForm`), pour qu'iOS et Android s'accordent : un texte vu par tous ne doit pas pouvoir se faire passer pour autre
// chose (sens inversé, texte invisible). UN écart voulu avec `ProfileForm`, aligné sur Android : U+2028 et U+2029 deviennent une espace (et ne
// sont pas retirés), pour que deux mots ne se collent jamais.

enum PhotoDescription {
    /// La limite des règles, en unités UTF-16.
    static let maxLength = 140

    /// Le petit compteur (« 125/140 ») n'apparaît qu'à partir d'ici, jamais avant.
    static let counterFrom = 120

    /// Un champ après application de la limite : `text` est ce que le champ garde, `wasCut` dit que quelque chose a été retiré à cause
    /// de la limite.
    struct Limited: Equatable {
        let text: String
        let wasCut: Bool
    }

    // MARK: - Pendant la frappe et le collage

    /// Ce que garde le champ pendant la frappe ou le collage : caractères de contrôle, invisibles et de direction retirés, tabulations et
    /// sauts de ligne remplacés par UNE espace, 140 unités au plus sans jamais couper un emoji. Les espaces sont gardés tels que tapés (le
    /// curseur ne saute pas) : elles sont rangées à la publication.
    static func limitInput(_ raw: String) -> Limited {
        let cleaned = removeUnsafe(raw)
        if cleaned.utf16.count <= maxLength {
            return Limited(text: cleaned, wasCut: false)
        }
        return Limited(text: cutToLimit(cleaned), wasCut: true)
    }

    /// Vrai quand la dernière frappe est la touche « Retour » : le nouveau texte est l'ancien avec UN saut de ligne en plus. Le champ
    /// SwiftUI à plusieurs lignes l'insère au lieu de « valider » : l'écran ferme alors le clavier (« Terminé », ne publie rien).
    static func isReturnKey(old: String, new: String) -> Bool {
        let before = Array(old.unicodeScalars)
        let after = Array(new.unicodeScalars)
        guard after.count == before.count + 1 else {
            return false
        }
        var index = 0
        while index < before.count, before[index] == after[index] {
            index += 1
        }
        // `index` est la place du caractère inséré (au plus `before.count`, donc toujours dans `after`).
        guard after[index] == "\n" || after[index] == "\r" else {
            return false
        }
        var rest = after
        rest.remove(at: index)
        return rest == before
    }

    // MARK: - À la publication

    /// Ce qui est publié : texte nettoyé, espaces répétées réduites à une, espaces autour retirées, 1 à 140 unités. nil quand il ne reste
    /// rien (vide, espaces seuls, caractères invisibles seuls) : la clé n'est alors PAS écrite (critères P1 et P2).
    static func normalize(_ raw: String) -> String? {
        let text = tidy(raw)
        guard !text.isEmpty else {
            return nil
        }
        if text.utf16.count <= maxLength {
            return text
        }
        let cut = cutToLimit(text).trimmingCharacters(in: .whitespacesAndNewlines)
        return cut.isEmpty ? nil : cut
    }

    // MARK: - Ce qui vient du réseau

    /// Une valeur lue sur le réseau (QUELQU'UN D'AUTRE l'a écrite, critère C5) : montrée seulement si c'est un texte ; nettoyée comme une saisie ;
    /// 140 unités au plus ; nil quand il ne reste rien. Jamais un lien, jamais un style : le texte est dessiné tel quel.
    static func fromServer(_ value: Any?) -> String? {
        guard let text = value as? String else {
            return nil
        }
        let cleaned = tidy(text)
        // Les règles ne laissent jamais passer plus de 140 unités : un texte plus long n'a pas été écrit par nous, et un texte coupé serait
        // trompeur. Ignoré (comme sur Android).
        return cleaned.isEmpty || cleaned.utf16.count > maxLength ? nil : cleaned
    }

    // MARK: - Le compteur

    static func showCounter(length: Int) -> Bool {
        length >= counterFrom
    }

    static func isAtLimit(length: Int) -> Bool {
        length >= maxLength
    }

    // MARK: - Nettoyage

    private static func tidy(_ raw: String) -> String {
        removeUnsafe(raw)
            .replacingOccurrences(of: "[\\p{Z}\\s]+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func removeUnsafe(_ raw: String) -> String {
        var output = String.UnicodeScalarView()
        for scalar in raw.unicodeScalars {
            let code = scalar.value
            if isLineBreak(code) {
                output.append(" ")
            } else if isControl(code) || isInvisibleOrDirectional(code) {
                continue
            } else {
                output.append(scalar)
            }
        }
        return String(output)
    }

    /// Tabulation, sauts de ligne, onglet vertical, saut de page, « next line », séparateurs de ligne et de paragraphe : UNE espace.
    private static func isLineBreak(_ code: UInt32) -> Bool {
        code == 0x09 || code == 0x0A || code == 0x0D || code == 0x0B || code == 0x0C || code == 0x85
            || code == 0x2028 || code == 0x2029
    }

    private static func isControl(_ code: UInt32) -> Bool {
        code < 0x20 || (code >= 0x7F && code <= 0x9F)
    }

    /// Espace de largeur nulle, marques de direction, imbrications, forçages et isolats, joint de mots, marque d'ordre des octets. U+200D
    /// (joint de largeur nulle) n'y est PAS : il tient ensemble les emojis composés (une famille, une personne et son métier).
    private static func isInvisibleOrDirectional(_ code: UInt32) -> Bool {
        code == 0x200B || code == 0x200E || code == 0x200F || (code >= 0x202A && code <= 0x202E)
            || code == 0x2060 || (code >= 0x2066 && code <= 0x2069) || code == 0xFEFF
    }

    /// Le plus long début de `text` de 140 unités UTF-16 au plus qui ne coupe aucun symbole : on avance par CARACTÈRE PERÇU (un emoji, une
    /// famille avec U+200D, une teinte de peau, un sélecteur de variation, une touche encadrée, un drapeau, une lettre et son accent
    /// combinant forment chacun un seul caractère perçu) : celui qui ne tient pas est retiré en entier, jamais coupé en deux.
    static func cutToLimit(_ text: String) -> String {
        var used = 0
        var kept = String()
        for character in text {
            let units = String(character).utf16.count
            if used + units > maxLength {
                break
            }
            kept.append(character)
            used += units
        }
        return kept
    }
}
