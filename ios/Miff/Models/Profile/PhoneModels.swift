import Foundation

/// Une entrée du sélecteur de pays : drapeau, nom dans la langue du téléphone, indicatif (le « +33 » de la France).
struct PhoneCountry: Equatable, Identifiable {
    /// Code région ISO 3166-1 alpha-2 (« FR »).
    let region: String
    let callingCode: Int
    let name: String

    var id: String {
        region
    }

    /// Drapeau construit avec les deux lettres du code région (symboles d'indicateur régional). Décoratif seulement.
    var flag: String {
        var result = String.UnicodeScalarView()
        for scalar in region.uppercased().unicodeScalars where scalar.value >= 65 && scalar.value <= 90 {
            if let flagScalar = Unicode.Scalar(0x1F1E6 + scalar.value - 65) {
                result.append(flagScalar)
            }
        }
        return String(result)
    }

    var dialCode: String {
        "+\(callingCode)"
    }
}

/// Résultat de la lecture de ce que la personne a saisi dans le champ du numéro.
enum PhoneResult: Equatable {
    /// `e164` est la SEULE chose enregistrée (« +33612345678 ») ; `region` est le pays auquel appartient le numéro.
    case valid(e164: String, region: String)
    case invalid
    /// Un numéro valide d'un genre sur lequel personne ne peut être appelé ou écrit de la façon habituelle : surtaxé,
    /// à coût partagé, gratuit, messagerie, pager... (les « 0899 » et « 0800 » de la France). Refusé exprès.
    case unsupportedType
}

/// Un numéro « +... » saisi ou collé : le pays auquel il appartient et la partie nationale à montrer dans le champ.
struct DetectedNumber: Equatable {
    let region: String
    let national: String
}

/// Ce que l'app demande à la bibliothèque de numéros (PhoneNumberKit dans l'app, un faux dans les tests de logique).
/// Règles du ticket Onboarding §6.4 (décision Q1 du PM) : la personne choisit un pays et saisit le numéro NATIONAL ;
/// seule la forme E.164 est enregistrée. La validité est une validité de FORME selon le plan de numérotation du pays
/// (mobile ou fixe) : rien ne prouve que la personne possède le numéro (pas de SMS sur le plan gratuit), et rien n'est
/// unique. Le numéro n'est jamais journalisé ni mis dans un message d'erreur.
protocol PhoneNumberTools {
    /// Tous les pays qui ont un plan de numérotation, triés par nom dans `locale`. Environ 240 entrées.
    func countries(locale: Locale) -> [PhoneCountry]
    /// Un seul pays, pour le sélecteur fermé (« France +33 ») : inutile de construire toute la liste.
    func country(region: String, locale: Locale) -> PhoneCountry
    func isSupportedRegion(_ region: String?) -> Bool
    /// Exemple de mobile du pays, sans l'indicatif ni le premier zéro (« 6 12 34 56 78 » pour la France).
    func exampleHint(region: String) -> String?
    /// Lit ce que la personne a saisi pour le pays `region`. Un « + » en tête veut dire numéro international : le pays
    /// est alors celui du numéro lui-même.
    func normalize(region: String, input: String) -> PhoneResult
    /// « +33 6 12 34 56 78 » tapé ou collé choisit la France et garde « 612345678 » (critère TEL2). Seulement si le
    /// texte est un numéro international plausible d'un pays connu ; sinon nil et le champ reste tel que saisi.
    func detectInternational(_ input: String) -> DetectedNumber?
}

/// Parties pures de la saisie du numéro (sans bibliothèque), testées sans téléphone.
enum PhoneInput {
    /// Pays utilisé quand le téléphone n'en donne pas, ou donne un pays sans plan de numérotation.
    static let fallbackRegion = "FR"
    static let maxInputLength = 40

    /// Pays présélectionné : la région de l'appareil, sinon la France (ticket §6.4).
    static func defaultRegion(deviceRegion: String?, tools: PhoneNumberTools) -> String {
        guard let upper = deviceRegion?.uppercased(), tools.isSupportedRegion(upper) else {
            return fallbackRegion
        }
        return upper
    }

    /// La flèche de l'étape téléphone est active dès qu'UN chiffre a été saisi (ticket §6.4).
    static func hasDigit(_ input: String) -> Bool {
        input.unicodeScalars.contains { digitValue($0) != nil }
    }

    /// Recherche du sélecteur de pays : accents et casse ignorés ; par nom, code région ou indicatif.
    static func matchesSearch(country: PhoneCountry, query: String) -> Bool {
        var folded = fold(query).trimmingCharacters(in: .whitespacesAndNewlines)
        if folded.hasPrefix("+") {
            folded.removeFirst()
        }
        if folded.isEmpty {
            return true
        }
        if folded.allSatisfy({ $0.isNumber }) {
            return String(country.callingCode).hasPrefix(folded)
        }
        return fold(country.name).contains(folded) || country.region.caseInsensitiveCompare(folded) == .orderedSame
    }

    /// Garde les chiffres (toute écriture, ramenés à 0-9) et un seul « + » en tête ; tolère espaces, tirets, points,
    /// parenthèses et barres obliques. Nil si autre chose se trouve dans le texte (lettres, autres symboles), s'il est
    /// bien trop long pour être un numéro, ou s'il ne contient aucun chiffre.
    static func clean(_ input: String) -> String? {
        guard input.count <= maxInputLength else {
            return nil
        }
        var output = ""
        var seenContent = false
        for scalar in input.unicodeScalars {
            if let digit = digitValue(scalar) {
                output.append(String(digit))
                seenContent = true
            } else if scalar == "+" && !seenContent {
                output.append("+")
                seenContent = true
            } else if !isIgnorable(scalar) {
                return nil
            }
        }
        return hasDigit(output) ? output : nil
    }

    private static let separators = Set("-.()/‐‑‒–—".unicodeScalars)

    /// Espaces de toute sorte, séparateurs usuels, marques de direction gauche-droite et droite-gauche.
    private static func isIgnorable(_ scalar: Unicode.Scalar) -> Bool {
        scalar.properties.isWhitespace
            || scalar.properties.generalCategory == .spaceSeparator
            || separators.contains(scalar)
            || scalar.value == 0x200E
            || scalar.value == 0x200F
    }

    /// Valeur d'un chiffre décimal de n'importe quelle écriture (arabe, devanagari...), nil pour tout autre caractère.
    private static func digitValue(_ scalar: Unicode.Scalar) -> Int? {
        guard scalar.properties.generalCategory == .decimalNumber,
              let value = scalar.properties.numericValue else {
            return nil
        }
        return Int(value)
    }

    private static func fold(_ text: String) -> String {
        text.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: nil)
    }
}
