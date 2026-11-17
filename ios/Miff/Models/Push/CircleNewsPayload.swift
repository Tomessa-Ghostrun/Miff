import Foundation

// SRV-2I / SRV-4I : le message que le SERVEUR envoie quand un proche donne des nouvelles (ticket `docs/tickets/notification-update-membres.md`,
// §4 et §12 ; forme exacte : `backend/functions/src/notifRegles.ts`, `construireMessage`, branche iOS).
//
// Ce qui voyage dans la partie « données » du message : un type, l'identifiant de la Miff et l'identifiant de l'auteur. JAMAIS une photo, une
// position, un nom de lieu, un nom de Miff, un nom de famille : le prénom lui-même n'est PAS dans les données (le système le lit dans l'alerte,
// que le serveur a déjà composée par clés de texte). Cette donnée vient du réseau, donc d'un inconnu : elle est VALIDÉE champ par champ avant
// d'être utilisée, et un message qui ne ressemble pas EXACTEMENT à ce qu'on attend n'ouvre rien.

/// Ce qu'un toucher sur la notification demande d'ouvrir. Rien d'autre du message n'est gardé.
struct CircleNewsTarget: Equatable, CustomStringConvertible {
    let miffId: String
    /// L'auteur de l'update : sert à l'auto-filtre (jamais de réaction à ma propre update) ; le système s'en sert déjà pour regrouper.
    let authorUid: String

    /// Des identifiants ne se lisent pas dans un journal.
    var description: String {
        "CircleNewsTarget(redacted)"
    }
}

/// La forme d'un identifiant venu de l'extérieur (une Miff, un compte) : lettres, chiffres, « - » et « _ », en nombre borné. Jamais un
/// chemin, une URL, un espace ni un caractère de contrôle.
enum PushIdentifier {
    static let miffMaxLength = 64
    static let authorMaxLength = 128

    static func isValid(_ value: String, maxLength: Int) -> Bool {
        guard (1...maxLength).contains(value.unicodeScalars.count) else {
            return false
        }
        return value.unicodeScalars.allSatisfy { scalar in
            switch scalar {
            case "A"..."Z", "a"..."z", "0"..."9", "-", "_":
                return true
            default:
                return false
            }
        }
    }
}

enum CircleNewsParser {
    /// Le seul type compris par cette version ; tout autre est ignoré sans trace (une version plus récente du serveur peut en envoyer d'autres).
    static let type = "circle_update"

    static let keyType = "type"
    static let keyMiffId = "miffId"
    static let keyAuthorUid = "authorUid"

    /// La cible du message, ou nil s'il faut l'ignorer : type inconnu, identifiant de Miff absent ou mal formé, identifiant d'auteur ABSENT, vide
    /// ou mal formé (sans auteur, l'auto-filtre ne peut pas jouer : on ne le contourne pas). Les clés ajoutées par le système (`aps`, `gcm.*`)
    /// sont ignorées.
    static func parse(_ userInfo: [AnyHashable: Any]) -> CircleNewsTarget? {
        guard userInfo[keyType] as? String == type,
              let miffId = userInfo[keyMiffId] as? String,
              PushIdentifier.isValid(miffId, maxLength: PushIdentifier.miffMaxLength),
              let author = userInfo[keyAuthorUid] as? String,
              PushIdentifier.isValid(author, maxLength: PushIdentifier.authorMaxLength) else {
            return nil
        }
        return CircleNewsTarget(miffId: miffId, authorUid: author)
    }
}

/// Quand réagir à un message (ouvrir le Feed, garder une ligne de centre) : seulement pour une Miff dont la personne CONNECTÉE est membre, et
/// jamais pour sa propre update. L'appartenance vient de l'annuaire des Miffs de CE compte ; si elle n'est pas connue (annuaire pas lu, pas de
/// réponse), le message est ignoré. Aucun compte connecté : ignoré.
enum PushOpenRules {
    /// `miffIds` : les Miffs du compte, ou nil quand elles ne sont pas connues.
    static func accepts(_ target: CircleNewsTarget, currentUid: String?, miffIds: [String]?) -> Bool {
        guard let currentUid, !currentUid.isEmpty, let miffIds else {
            return false
        }
        return target.authorUid != currentUid && miffIds.contains(target.miffId)
    }
}
