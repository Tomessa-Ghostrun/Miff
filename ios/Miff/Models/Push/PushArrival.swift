import Foundation

// CNC-I (ticket docs/tickets/centre-notifications-complet.md §8.4 et §11, critère CNC-9 quater) : ce qu'un message du SERVEUR apporte au centre de
// notifications quand l'app le voit passer (au premier plan, ou au toucher sur la notification). Les deux familles de messages que le serveur envoie
// aujourd'hui : « {Prénom} a donné des nouvelles » (`circle_update`) et l'alerte (`circle_alert`, début ou fin). Forme exacte :
// `backend/functions/src/notifRegles.ts` et `alerteRegles.ts`, branche iOS (`construireMessage`, `construireMessageAlerte`).
//
// Ce qui voyage : un type, (un événement,) l'identifiant de la Miff, celui de l'auteur et l'identifiant de la ligne du journal du serveur (`entryId`).
// JAMAIS un prénom, une photo, un lieu ou une position : le prénom est lu à l'affichage, depuis la fiche du membre. Cette donnée vient du réseau, donc
// d'un inconnu : elle est VALIDÉE champ par champ, et un message qui ne ressemble pas EXACTEMENT à ce qu'on attend ne laisse aucune ligne.

struct PushArrival: Equatable, CustomStringConvertible {
    enum Kind: Equatable {
        /// « {Prénom} a donné des nouvelles ».
        case update
        /// « {Prénom} a déclaré une alerte ».
        case alertStart
        /// « {Prénom} a mis fin à son alerte ».
        case alertEnd
    }

    let kind: Kind
    let miffId: String
    let authorUid: String
    /// L'identifiant de la ligne du journal du serveur pour CE message : la ligne du centre porte la même clé que celle que le journal apporte plus
    /// tard (jamais de doublon). nil pour un serveur sans journal (clé absente).
    let entryId: String?
    /// L'identifiant que le service de messages a donné à ce message, nettoyé : sert de clé quand le serveur n'a pas d'`entryId`.
    let messageId: String?

    /// Des identifiants ne se lisent pas dans un journal.
    var description: String {
        "PushArrival(redacted)"
    }
}

enum PushArrivalParser {
    static let typeUpdate = "circle_update"
    static let typeAlert = "circle_alert"
    static let keyType = "type"
    static let keyEvent = "event"
    static let keyMiffId = "miffId"
    static let keyAuthorUid = "authorUid"
    static let keyEntryId = "entryId"
    /// La clé que le SDK de messages ajoute pour l'identifiant du message.
    static let keyMessageId = "gcm.message_id"

    /// L'arrivée décrite par ce message, ou nil quand il faut l'ignorer : autre type, événement inconnu, identifiant absent ou mal formé. Un `entryId`
    /// absent ou vide est un serveur sans journal ; un `entryId` présent mais mal formé fait ignorer tout le message (STRICT, comme les autres champs).
    static func parse(_ userInfo: [AnyHashable: Any]) -> PushArrival? {
        guard let kind = arrivalKind(of: userInfo),
              let miffId = userInfo[keyMiffId] as? String,
              PushIdentifier.isValid(miffId, maxLength: PushIdentifier.miffMaxLength),
              let author = userInfo[keyAuthorUid] as? String,
              PushIdentifier.isValid(author, maxLength: PushIdentifier.authorMaxLength) else {
            return nil
        }
        var entryId: String?
        if let rawValue = userInfo[keyEntryId] {
            // Une valeur qui n'est pas un texte est un message qui ne ressemble pas à ce qu'on attend.
            guard let raw = rawValue as? String else {
                return nil
            }
            if !raw.isEmpty {
                guard NotificationJournal.isIdentifier(raw) else {
                    return nil
                }
                entryId = raw
            }
        }
        return PushArrival(
            kind: kind,
            miffId: miffId,
            authorUid: author,
            entryId: entryId,
            messageId: cleanMessageId(userInfo[keyMessageId] as? String)
        )
    }

    private static func arrivalKind(of userInfo: [AnyHashable: Any]) -> PushArrival.Kind? {
        guard let type = userInfo[keyType] as? String else {
            return nil
        }
        if type == typeUpdate {
            return .update
        }
        guard type == typeAlert, let event = userInfo[keyEvent] as? String else {
            return nil
        }
        switch event {
        case "start":
            return .alertStart
        case "end":
            return .alertEnd
        default:
            return nil
        }
    }

    /// Lettres, chiffres et `_ . : % -` en 80 caractères au plus ; sinon aucun identifiant de message (la ligne est alors datée de l'arrivée).
    private static func cleanMessageId(_ raw: String?) -> String? {
        guard let raw, (1...80).contains(raw.unicodeScalars.count) else {
            return nil
        }
        let allowed = raw.unicodeScalars.allSatisfy { scalar in
            switch scalar {
            case "A"..."Z", "a"..."z", "0"..."9", "-", "_", ".", ":", "%":
                return true
            default:
                return false
            }
        }
        return allowed ? raw : nil
    }
}

/// Quand garder une ligne pour un message : seulement pour une personne connectée, jamais pour son propre événement, et jamais pour une Miff que
/// l'annuaire connaît et qui n'est pas la sienne. Si l'annuaire n'est pas encore lu (démarrage à froid, toucher sur la notification), la ligne est
/// gardée : l'écran du centre ne montre pas les lignes d'une Miff qui n'est pas celle du compte, et elles sont retirées dès que l'annuaire est lu.
enum PushArrivalRules {
    static func accepts(_ arrival: PushArrival, currentUid: String?, miffIds: [String]?) -> Bool {
        guard let currentUid, !currentUid.isEmpty, arrival.authorUid != currentUid else {
            return false
        }
        guard let miffIds else {
            return true
        }
        return miffIds.contains(arrival.miffId)
    }
}
