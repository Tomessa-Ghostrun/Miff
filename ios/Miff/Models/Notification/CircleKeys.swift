import Foundation

// CNC-I (ticket docs/tickets/centre-notifications-complet.md §8.1 et §8.4) : les CLÉS des lignes du centre pour « {Prénom} a donné des nouvelles »
// (N9) et pour l'alerte (N10 début, N11 fin). Miroir exact de `CircleNewsKeys` et `CircleAlertKeys` d'Android : la clé d'une ligne est la même
// que celle que le journal du serveur apporte pour le même événement, de sorte qu'un message vu par le téléphone ET relu depuis le journal ne
// fasse qu'UNE ligne. Une clé ne porte que des identifiants (la Miff n'y est pas, l'auteur et l'événement y sont) : jamais un prénom.

/// Les quatre morceaux d'une clé `famille|u|{uid}|…`, ou nil pour une clé d'une autre forme.
private enum CircleKeyPieces {
    static func actor(of centerId: String, family: String) -> String? {
        let pieces = centerId.split(separator: "|", maxSplits: 3, omittingEmptySubsequences: false)
        guard pieces.count == 4, pieces[0] == Substring(family), pieces[1] == "u" else {
            return nil
        }
        let uid = String(pieces[2])
        return NotificationJournal.isIdentifier(uid) ? uid : nil
    }
}

enum CircleNewsKeys {
    /// La clé de la ligne que le journal apporte pour l'entrée `entryId` de `actorUid`.
    static func journalId(actorUid: String, entryId: String) -> String {
        "n9|u|\(actorUid)|\(entryId)"
    }

    /// La clé de la ligne qu'un message du serveur écrit : l'identifiant de l'entrée du journal quand le serveur en donne un (la même ligne que
    /// celle du journal), sinon l'identifiant du message, sinon l'heure de l'arrivée.
    static func pushId(_ arrival: PushArrival, receivedAtMillis: Int) -> String {
        let last = arrival.entryId ?? arrival.messageId ?? String(receivedAtMillis)
        return journalId(actorUid: arrival.authorUid, entryId: last)
    }

    /// Le proche dont parle une ligne, lu dans sa clé (`n9|u|{uid}|…`) ; nil pour une clé d'une autre forme.
    static func actorOf(_ centerId: String) -> String? {
        CircleKeyPieces.actor(of: centerId, family: "n9")
    }
}

enum CircleAlertKeys {
    /// Le mot de l'événement dans la clé ; nil pour ce qui n'est pas une alerte.
    static func word(_ kind: NotificationJournalKind) -> String? {
        switch kind {
        case .alertStart:
            return "start"
        case .alertEnd:
            return "end"
        case .update:
            return nil
        }
    }

    /// La clé de la ligne que le journal apporte pour l'entrée `entryId` de `actorUid` (début ou fin).
    static func journalId(actorUid: String, word: String, entryId: String) -> String {
        "al|u|\(actorUid)|\(word)|\(entryId)"
    }

    /// La clé de la ligne qu'un message d'alerte écrit (voir `CircleNewsKeys.pushId`).
    static func pushId(_ arrival: PushArrival, receivedAtMillis: Int) -> String {
        let word = arrival.kind == .alertEnd ? "end" : "start"
        let last = arrival.entryId ?? arrival.messageId ?? String(receivedAtMillis)
        return journalId(actorUid: arrival.authorUid, word: word, entryId: last)
    }

    /// Le proche dont parle une ligne d'alerte, lu dans sa clé (`al|u|{uid}|…`) ; nil pour une clé d'une autre forme.
    static func actorOf(_ centerId: String) -> String? {
        CircleKeyPieces.actor(of: centerId, family: "al")
    }
}
