import Foundation

// SÉCURITÉ et CONFIDENTIALITÉ, pour ce fichier : le Feed ne confie que TROIS choses aux applications du SYSTÈME, et rien d'autre.
// - « Écrire à » : l'application Messages, le numéro de la carte préparé (`sms:`) ; Miff n'envoie rien.
// - « Appeler » : le composeur du système, le numéro préparé (`tel:`) ; iOS demande confirmation avant l'appel, Miff ne lance aucun
//   appel. Aucune clé « contacts », aucune permission d'appel ou de SMS n'existe, et aucune n'est nécessaire.
// - « Ouvrir dans les cartes » : Plans, sur les COORDONNÉES SEULES (aucun nom, aucune étiquette, aucune personne).
// Un numéro est VÉRIFIÉ ENCORE ici (E.164 : un « + » et des chiffres) : rien d'autre n'atteint jamais l'adresse d'un lien. Une position
// n'est écrite ni dans un journal, ni dans le presse-papiers, ni dans un message de partage.

enum FeedLinks {
    static func message(phone: String) -> URL? {
        guard ProfileDocument.isE164(phone) else {
            return nil
        }
        return URL(string: "sms:\(phone)")
    }

    static func call(phone: String) -> URL? {
        guard ProfileDocument.isE164(phone) else {
            return nil
        }
        return URL(string: "tel:\(phone)")
    }

    static func map(_ fix: PositionFix) -> URL? {
        URL(string: Locate.mapsUrl(fix))
    }
}
