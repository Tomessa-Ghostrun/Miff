import Foundation

// CNC-I (ticket docs/tickets/centre-notifications-complet.md §8.1 et §8.4, contrat `backend/data-model.md` §2.21) : le JOURNAL du serveur,
// `users/{uid}/notifications/{entryId}`. Le serveur y écrit une petite entrée par destinataire de chaque notification qu'il envoie (une update d'un
// proche, le début ou la fin d'une alerte), gardée 30 jours ; l'app ne fait que le LIRE (une requête depuis un curseur, jamais un écouteur) et en
// fait des lignes du centre, pour que rien ne se perde quand le téléphone était éteint, l'app arrêtée de force, l'autorisation retirée ou le
// message trop vieux. Règles pures, sans Firestore (la lecture est dans `Services/Firebase`), testées sans réseau. Miroir de
// `NotificationJournal.kt` d'Android.
//
// CONFIDENTIALITÉ : une entrée porte une version, une sorte, l'identifiant de la Miff, celui du proche et deux dates. JAMAIS un prénom, une photo,
// un lieu, une position ni le nom d'une Miff : les mots et l'image d'une ligne se font quand elle est DESSINÉE. Tout ce qui vient du réseau est
// validé ici avant d'être utilisé ; une entrée d'une version ou d'une sorte inconnue est ignorée sans erreur (compatibilité).

/// Ce que le serveur a notifié : les trois sortes de message qu'il envoie.
enum NotificationJournalKind: Equatable {
    case update
    case alertStart
    case alertEnd

    /// Le mot « alert » des valeurs du contrat (`alert_start`, `alert_end`). Il est écrit à part, et la valeur construite : ce sont des valeurs de DONNÉES du
    /// serveur, pas des clés de texte, et le garde-fou des clés (`LocalizationTests`) prend pour une clé de texte toute chaîne qui commence par « alert_ ».
    private static let alertWord = "alert"

    /// La valeur du champ `kind` du document (`backend/data-model.md` §2.21).
    var code: String {
        switch self {
        case .update:
            return "update"
        case .alertStart:
            return Self.alertWord + "_start"
        case .alertEnd:
            return Self.alertWord + "_end"
        }
    }

    /// La sorte que dit cette valeur, ou nil quand elle est inconnue (un serveur plus récent peut en écrire d'autres : l'entrée est ignorée).
    init?(code: String) {
        let all: [NotificationJournalKind] = [.update, .alertStart, .alertEnd]
        guard let found = all.first(where: { $0.code == code }) else {
            return nil
        }
        self = found
    }
}

/// Une entrée du journal, validée. Ne dit rien dans un journal d'exécution : des identifiants ne s'y écrivent pas.
struct JournalEntry: Equatable, CustomStringConvertible {
    let entryId: String
    let kind: NotificationJournalKind
    let miffId: String
    let actorUid: String
    let createdAtMillis: Int
    /// La fin des 30 jours de l'entrée : une entrée dont la date est passée est ignorée (la suppression par la règle d'expiration n'a lieu que
    /// « typiquement dans les 24 h » après).
    let expireAtMillis: Int

    var description: String {
        "JournalEntry(redacted)"
    }
}

enum NotificationJournal {
    /// La version de la forme que cette app comprend ; toute autre est ignorée (un serveur plus récent peut écrire autre chose).
    static let version = 1
    static let collection = "notifications"
    /// Au plus 100 entrées par requête (les règles refusent une `limit` plus grande) et 2 requêtes par synchronisation.
    static let pageSize = 100
    static let maxPages = 2
    /// Une requête commence 2 minutes AVANT le curseur : une entrée écrite un instant avant une autre, mais validée après, n'est jamais sautée.
    static let overlapMillis = 2 * 60 * 1000
    /// À la première synchronisation d'un téléphone, les entrées de plus de 24 h arrivent déjà lues : aucun point sur la cloche pour le passé.
    static let freshMillis = 24 * 60 * 60 * 1000

    /// Les préfixes des identifiants que le serveur écrit : `u-…`, `as-…`, `ae-…`.
    private static let entryPrefixes = ["u-", "as-", "ae-"]

    /// La forme d'un identifiant venu du réseau (`^[A-Za-z0-9_-]{1,128}$`).
    static func isIdentifier(_ value: String) -> Bool {
        PushIdentifier.isValid(value, maxLength: 128)
    }

    /// L'entrée du document `docId` qui contient `data`, ou nil quand elle doit être ignorée : version ou sorte inconnue, champ absent ou mal
    /// formé, date qui n'est pas une heure. STRICT sur ce qui est utilisé ; toute autre clé qu'une version plus récente ajouterait est ignorée.
    static func parse(
        docId: String,
        data: [String: Any],
        createdAtMillis: Int?,
        expireAtMillis: Int? = Int.max
    ) -> JournalEntry? {
        guard isIdentifier(docId), integer(data["v"]) == version,
              let kindCode = data["kind"] as? String, let kind = NotificationJournalKind(code: kindCode),
              let miffId = data["miffId"] as? String, isIdentifier(miffId),
              let actorUid = data["actorUid"] as? String, isIdentifier(actorUid),
              let createdAtMillis, createdAtMillis > 0,
              let expireAtMillis, expireAtMillis > 0 else {
            return nil
        }
        return JournalEntry(
            entryId: docId, kind: kind, miffId: miffId, actorUid: actorUid,
            createdAtMillis: createdAtMillis, expireAtMillis: expireAtMillis
        )
    }

    /// Un entier lu sur le réseau (Firestore rend un nombre de façons différentes selon la taille).
    private static func integer(_ value: Any?) -> Int? {
        if let number = value as? Int {
            return number
        }
        if let number = value as? Int64 {
            return Int(number)
        }
        return (value as? NSNumber)?.intValue
    }

    /// La ligne du centre qu'une entrée représente : la MÊME clé que la ligne que le message de cette entrée écrit (un message et le journal pour
    /// une entrée ne font qu'une ligne).
    static func key(of entry: JournalEntry) -> String {
        switch entry.kind {
        case .update:
            return CircleNewsKeys.journalId(actorUid: entry.actorUid, entryId: entry.entryId)
        case .alertStart, .alertEnd:
            let word = CircleAlertKeys.word(entry.kind) ?? "start"
            return CircleAlertKeys.journalId(actorUid: entry.actorUid, word: word, entryId: entry.entryId)
        }
    }

    static func lineType(of kind: NotificationJournalKind) -> NotificationType {
        switch kind {
        case .update:
            return .n9
        case .alertStart:
            return .n10
        case .alertEnd:
            return .n11
        }
    }

    /// La ligne pour `entry`, ou nil quand elle n'est pas pour cette personne (jamais une ligne de son propre événement) ou quand elle a expiré.
    /// Elle est écrite SANS notification du système (silencieuse : le message a déjà eu sa chance). Elle arrive non lue, sauf à la
    /// `firstSync` d'un téléphone pour une entrée de plus de 24 h : elle est alors déjà lue. La ligne ne porte aucun nom.
    static func item(for entry: JournalEntry, myUid: String, nowMillis: Int, firstSync: Bool) -> NotificationItem? {
        guard entry.actorUid != myUid, nowMillis < entry.expireAtMillis else {
            return nil
        }
        let old = nowMillis - entry.createdAtMillis > freshMillis
        return NotificationItem(
            id: key(of: entry),
            type: lineType(of: entry.kind),
            miffId: entry.miffId,
            createdAtMillis: entry.createdAtMillis,
            readAtMillis: firstSync && old ? entry.createdAtMillis : nil,
            state: .silent
        )
    }

    /// La ligne qu'un message du serveur écrit quand l'app le voit passer. Datée de l'arrivée (le serveur n'envoie aucune heure dans le message).
    /// `opened` : la personne a touché la notification (le système l'avait affichée) ; sinon l'app était devant, rien n'a été affiché.
    static func item(for arrival: PushArrival, nowMillis: Int, opened: Bool) -> NotificationItem {
        let id: String
        let type: NotificationType
        switch arrival.kind {
        case .update:
            id = CircleNewsKeys.pushId(arrival, receivedAtMillis: nowMillis)
            type = .n9
        case .alertStart:
            id = CircleAlertKeys.pushId(arrival, receivedAtMillis: nowMillis)
            type = .n10
        case .alertEnd:
            id = CircleAlertKeys.pushId(arrival, receivedAtMillis: nowMillis)
            type = .n11
        }
        return NotificationItem(
            id: id, type: type, miffId: arrival.miffId, createdAtMillis: nowMillis,
            readAtMillis: nil, state: opened ? .shown : .silent
        )
    }

    /// Où la requête suivante commence : 2 minutes avant le curseur, ou nil (rien de lu encore : tout le journal, 30 jours).
    static func since(cursorMillis: Int?) -> Int? {
        cursorMillis.map { max($0 - overlapMillis, 1) }
    }

    /// Le nouveau curseur : la date la plus récente vue, qui ne recule jamais.
    static func nextCursor(previous: Int?, entries: [JournalEntry]) -> Int? {
        ([previous].compactMap { $0 } + entries.map { $0.createdAtMillis }).max()
    }

    /// L'identifiant de l'entrée du journal d'où vient une ligne du centre, lu dans sa clé, pour que supprimer la ligne supprime aussi l'entrée sur
    /// le serveur. nil pour une ligne qui n'en a jamais eu (un rappel, une demande de lieu, « Depuis ta dernière visite », une ancienne ligne dont la
    /// clé finit par un identifiant de message).
    static func entryId(ofCenterId centerId: String) -> String? {
        let pieces = centerId.split(separator: "|", omittingEmptySubsequences: false).map { String($0) }
        let entryId: String
        if pieces.count == 4, pieces[0] == "n9", pieces[1] == "u" {
            entryId = pieces[3]
        } else if pieces.count == 5, pieces[0] == "al", pieces[1] == "u" {
            entryId = pieces[4]
        } else {
            return nil
        }
        guard isIdentifier(entryId), entryPrefixes.contains(where: { entryId.hasPrefix($0) }) else {
            return nil
        }
        return entryId
    }
}
