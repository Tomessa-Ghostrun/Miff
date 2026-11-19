#if ALERT_DANGER
import Foundation

// ALERTE « Je suis en danger » (ticket docs/tickets/alerte-danger.md §11, backend/data-model.md §2.19, fonction `notifierAlerte`, NON DÉPLOYÉE) :
// la partie « données » du message que le SERVEUR envoie quand un membre d'une Miff déclare une alerte (événement `start`) ou y met fin à la
// main (événement `end`). Forme exacte : `backend/functions/src/alerteRegles.ts`, `construireMessageAlerte`, branche iOS.
//
// Ce qui voyage : un type, un événement, l'identifiant de la Miff et celui de l'auteur. JAMAIS une position, un lieu, le nom de la Miff, un nom
// de famille ni une photo (le nom de la Miff pourrait révéler une relation à quelqu'un qui lit l'écran verrouillé) ; le PRÉNOM n'est pas dans
// les données : le système le lit dans l'alerte, que le serveur a déjà composée par clés de texte (`notif_alert_*`), résolues dans la langue du
// téléphone. Cette donnée vient du réseau, donc d'un inconnu : elle est VALIDÉE champ par champ, et un message qui ne ressemble pas EXACTEMENT
// à ce qu'on attend n'ouvre rien. Miroir de `CircleAlertParser` d'Android.

enum CircleAlertEvent: String, Equatable {
    case start
    case end
}

/// Ce qu'un message d'alerte demande : l'événement, la Miff à ouvrir et l'auteur dont la carte est la bonne. Rien d'autre n'est gardé.
struct CircleAlertTarget: Equatable, CustomStringConvertible {
    let event: CircleAlertEvent
    let miffId: String
    let authorUid: String

    /// La cible d'ouverture du Feed (la même que celle d'« un proche a donné des nouvelles ») : la Miff, et l'auteur pour l'auto-filtre.
    var openTarget: CircleNewsTarget {
        CircleNewsTarget(miffId: miffId, authorUid: authorUid)
    }

    /// Des identifiants ne se lisent pas dans un journal.
    var description: String {
        "CircleAlertTarget(redacted)"
    }
}

enum CircleAlertParser {
    /// Le type de cette famille de messages ; tout autre est laissé aux autres analyseurs.
    static let type = "circle_alert"

    static let keyType = "type"
    static let keyEvent = "event"
    static let keyMiffId = "miffId"
    static let keyAuthorUid = "authorUid"

    /// La cible du message, ou nil quand il faut l'ignorer : autre type, événement inconnu, identifiant de Miff ou d'auteur absent ou mal formé.
    /// STRICT exprès. Les clés ajoutées par le système (`aps`, `gcm.*`) sont ignorées.
    static func parse(_ userInfo: [AnyHashable: Any]) -> CircleAlertTarget? {
        guard userInfo[keyType] as? String == type,
              let rawEvent = userInfo[keyEvent] as? String,
              let event = CircleAlertEvent(rawValue: rawEvent),
              let miffId = userInfo[keyMiffId] as? String,
              PushIdentifier.isValid(miffId, maxLength: PushIdentifier.miffMaxLength),
              let author = userInfo[keyAuthorUid] as? String,
              PushIdentifier.isValid(author, maxLength: PushIdentifier.authorMaxLength) else {
            return nil
        }
        return CircleAlertTarget(event: event, miffId: miffId, authorUid: author)
    }
}
#endif
