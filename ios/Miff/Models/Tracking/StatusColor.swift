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

    /// ZONE DE DANGER (décision du PM, confirmée le 2026-11-08) : une personne dans une zone de danger passe ROUGE quand sa dernière
    /// update (le dernier signal « tout va bien » : update manuelle ou entrée en zone de confiance, `lastUpdateAt`) date de PLUS d'1 h, ou n'existe pas. Strictement « plus de » : à 1 h 00 pile elle n'est
    /// pas encore rouge. Moins d'1 h depuis sa dernière update : pas de rouge forcé, la couleur suit la règle d'ancienneté habituelle.
    /// Une zone de confiance l'emporte toujours (`StatusColor.level`). Mêmes nom et valeur sur Android (`DANGER_ZONE_RED_AFTER`).
    static let dangerZoneRedAfterMillis = 60 * 60 * 1000
}

/// Ce dont la couleur a besoin dans le document d'un membre, rien d'autre. Heures en millisecondes depuis 1970 ; nil quand le champ
/// manque.
struct MemberStatusFacts: Equatable, CustomStringConvertible {
    let sharingEnabled: Bool
    let presence: PresenceValue?
    let presenceAtMillis: Int?
    let lastUpdateAtMillis: Int?

    var description: String {
        "MemberStatusFacts(redacted)"
    }
}

enum StatusColor {
    /// L'ordre du ticket : partage arrêté ou statut absent = neutre ; zone de confiance = vert ; sinon zone de danger (voir
    /// `dangerZoneLevel` : rouge si sa dernière update a plus d'1 h) ; `unknown` = neutre (une personne jamais passée en zone de confiance,
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

    /// La couleur d'une personne dans une zone de danger : rouge quand sa dernière update a plus d'1 h (`dangerZoneRedAfterMillis`) ou n'existe
    /// pas (une ancienneté inconnue ne rassure pas à tort) ; sinon la règle d'ancienneté habituelle, qui donne alors vert (le repère,
    /// la dernière update, a moins d'1 h). Un repère dans le futur (téléphone à l'heure fausse) compte comme « maintenant ».
    static func dangerZoneLevel(_ facts: MemberStatusFacts, nowMillis: Int) -> StatusLevel {
        guard let lastUpdate = facts.lastUpdateAtMillis,
              nowMillis - lastUpdate <= StatusThresholds.dangerZoneRedAfterMillis else {
            return .red
        }
        return levelByAge(facts, nowMillis: nowMillis)
    }
}
