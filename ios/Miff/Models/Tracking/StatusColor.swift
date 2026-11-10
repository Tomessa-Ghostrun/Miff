import Foundation

// Fonction pure de la couleur d'un membre (ticket Lieux §6.9, critères STA6, STA9). Rien n'est lu, rien n'est écrit : la couleur est
// calculée par le téléphone de celui qui REGARDE, avec l'heure du moment, et n'est JAMAIS stockée dans Firestore. Le Feed (ticket
// suivant) l'appelle ; la même fonction et les mêmes constantes nommées sont écrites à l'identique sur Android (les cas des tests
// sont le contrat).

enum StatusLevel: Equatable {
    case neutral
    case green
    case orange
    case red
}

/// Les seuils de couleur, en UN seul endroit (critère STA9) : le Feed ne doit jamais réécrire 2 h ni 4 h. Décision du PM (2026-11-08,
/// elle remplace 1 h / 2 h) : une personne qui a QUITTÉ une zone de confiance passe orange quand son repère (le plus récent de son
/// départ et de sa dernière update) a PLUS de 2 h, rouge quand il a PLUS de 4 h. Strictement « plus de » : à 2 h 00 pile elle est
/// encore verte, à 4 h 00 pile encore orange. Mêmes noms et mêmes valeurs sur Android.
enum StatusThresholds {
    static let orangeAfterMillis = 2 * 60 * 60 * 1000
    static let redAfterMillis = 4 * 60 * 60 * 1000

    /// ZONE DE DANGER (décision du PM du 2026-11-08, version finale) : dès qu'une personne ENTRE dans une zone de danger elle est ROUGE,
    /// même si elle avait publié une update juste avant (une update antérieure à l'entrée ne compte pas). Si elle publie une update
    /// POSTÉRIEURE à l'entrée, elle est VERTE pendant 1 h ; passé 1 h (strict : à 1 h 00 pile elle est encore verte) après CETTE update,
    /// si elle est toujours dans la zone, elle repasse ROUGE ; une nouvelle update = vert 1 h de plus. Jamais orange en zone de danger.
    /// Une zone de confiance l'emporte toujours (`StatusColor.level`). Mêmes nom et valeur sur Android (`DANGER_ZONE_RED_AFTER_MILLIS`).
    static let dangerZoneRedAfterMillis = 60 * 60 * 1000
}

/// Ce dont la couleur a besoin dans le document d'un membre, rien d'autre. Heures en millisecondes depuis 1970 ; nil quand le champ
/// manque.
struct MemberStatusFacts: Equatable, CustomStringConvertible {
    let sharingEnabled: Bool
    let presence: PresenceValue?
    let presenceAtMillis: Int?
    let lastUpdateAtMillis: Int?
    /// MODE VOYAGE : la fin du voyage quand `presence` est `travel` (la couleur n'en a pas besoin : un voyage est toujours neutre ; le Feed si).
    var travelUntilMillis: Int?

    var description: String {
        "MemberStatusFacts(redacted)"
    }
}

enum StatusColor {
    /// L'ordre du ticket : partage arrêté ou statut absent = neutre ; zone de confiance = vert ; sinon zone de danger (voir
    /// `dangerZoneLevel` : rouge dès l'entrée, vert 1 h après une update postérieure à l'entrée) ; `unknown` = neutre (une personne jamais passée en zone de confiance,
    /// décision N4) ; sinon l'ancienneté du repère = le plus récent du départ et de la dernière update (décision N2) : jusqu'à 2 h INCLUSE vert, au-delà et jusqu'à 4 h
    /// INCLUSE orange, plus de 4 h rouge. Un repère dans le futur (téléphone à l'heure fausse) compte comme « maintenant » : vert.
    static func level(_ facts: MemberStatusFacts?, nowMillis: Int) -> StatusLevel {
        guard let facts, facts.sharingEnabled, let presence = facts.presence else {
            return .neutral
        }
        switch presence {
        case .inTrusted:
            return .green
        case .inRisk:
            return dangerZoneLevel(facts, nowMillis: nowMillis)
        case .unknown:
            return .neutral
        case .travel:
            // MODE VOYAGE : un voyageur est ignoré dans la couleur de la Miff, avant ET après la fin du voyage (après, la carte dit « Voyage
            // terminé » jusqu'à la première mesure : jamais une couleur tirée d'un état qui n'est plus vrai).
            return .neutral
        case .out:
            return levelByAge(facts, nowMillis: nowMillis)
        }
    }

    /// L'ancienneté du repère (le plus récent du départ et de la dernière update) : jusqu'à 2 h INCLUSE vert, jusqu'à 4 h INCLUSE orange,
    /// au-delà rouge. Neutre quand aucun repère n'existe.
    private static func levelByAge(_ facts: MemberStatusFacts, nowMillis: Int) -> StatusLevel {
        let references = [facts.presenceAtMillis, facts.lastUpdateAtMillis].compactMap { $0 }
        guard let reference = references.max() else {
            return .neutral
        }
        let elapsed = nowMillis - reference
        if elapsed <= StatusThresholds.orangeAfterMillis {
            return .green
        }
        return elapsed <= StatusThresholds.redAfterMillis ? .orange : .red
    }

    /// La couleur d'une personne dans une zone de danger : VERT si elle a une update (`lastUpdateAt`) STRICTEMENT postérieure à son entrée
    /// (`presenceAt`) et de 1 h au plus (`dangerZoneRedAfterMillis`) ; ROUGE sinon : entrée sans update, update antérieure à l'entrée ou
    /// au même instant, update de plus d'1 h, ou heure d'entrée inconnue (on ne peut pas dire que l'update est postérieure : prudence,
    /// jamais un vert qu'on ne peut pas justifier). Plusieurs entrées / sorties : `presenceAt` est toujours celle de l'entrée COURANTE.
    /// Une update dans le futur (téléphone à l'heure fausse) compte comme « maintenant ». Jamais orange.
    static func dangerZoneLevel(_ facts: MemberStatusFacts, nowMillis: Int) -> StatusLevel {
        guard let entry = facts.presenceAtMillis, let update = facts.lastUpdateAtMillis, update > entry,
              nowMillis - update <= StatusThresholds.dangerZoneRedAfterMillis else {
            return .red
        }
        return .green
    }
}
