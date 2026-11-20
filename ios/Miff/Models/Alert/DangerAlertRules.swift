import Foundation

// ALERTE « Je suis en danger » (ticket docs/tickets/alerte-danger.md, AD-I1 et AD-I2 ; miroir d'`AlertRules.kt` d'Android). Les règles pures et le
// contrat avec backend/firestore.rules (`match /alerts/{uid}` et `users/{uid}/quota/alerts`, backend/data-model.md §2.18 et §2.20, branche
// backend/alerte-danger, PR #274, NON DÉPLOYÉ).
//
// CONFIDENTIALITÉ ET SÉCURITÉ, pour ce fichier : une alerte dit qu'une personne se déclare en danger. Le document ne contient AUCUNE position,
// AUCUN texte libre, AUCUNE photo, AUCUN nom : sept clés exactement (les règles refusent une clé manquante et une clé inconnue). Rien d'une
// alerte n'est journalisé, envoyé comme événement ni montré dans un message qui la cite : chaque type qui en porte un cache son contenu.
// Ce fichier est compilé dans TOUS les builds (c'est du calcul pur, sans effet) ; ce qui LIT et ÉCRIT une alerte est derrière la condition de
// compilation `ALERT_DANGER` (project.yml : Debug et Internal oui, Release non).

/// Les nombres du geste et du contrat (un seul endroit : les tests et les écrans les lisent ici).
enum DangerAlertRules {
    /// Le doigt doit rester posé ce temps avant que l'écran devienne l'alerte (un simple appui long ne doit pas clignoter en rouge) : ticket 7.2.
    static let armMillis = 400
    /// Puis la personne maintient ce temps (ticket : « maintien 5 s », à régler en recette).
    static let holdMillis = 5_000
    // Retour du PM (recette) : AUCUNE distance n'annule le geste. Il faut poser le doigt SUR le bouton pour l'engager, mais une fois engagé, le doigt peut
    // sortir du bouton : seuls le relâchement, l'arrière-plan, un appel entrant ou une annulation du système l'arrêtent.

    private static let hour = 3_600_000
    private static let day = 24 * hour

    /// Une alerte dure 7 jours au plus : les règles acceptent un `expiresAt` dans la fenêtre (6 jours, 7 jours] après l'heure du SERVEUR.
    static let durationMillis = 7 * day
    /// Contrat du client : `expiresAt` = heure du TÉLÉPHONE + 7 jours - 2 heures. Un téléphone en avance de 2 h au plus, ou en retard de
    /// 22 h au plus, écrit une alerte valide ; au-delà le début est refusé (`DangerAlertFailure.refused`).
    static let clockMarginMillis = 2 * hour
    /// `purgeAt` = `expiresAt` + 2 jours au début (règle TTL à poser par le PM).
    static let purgeAfterExpiryMillis = 2 * day
    /// `purgeAt` = heure du téléphone + 2 jours à la fin (les règles acceptent de 1 à 3 jours).
    static let purgeAfterEndMillis = 2 * day
    /// Les règles refusent un ré-armement à moins de 60 s du début précédent (D20, silencieux) : l'app ATTEND les secondes restantes, elle
    /// n'affiche jamais de refus.
    static let rearmMinGapMillis = 60_000
    /// Quelques secondes de sûreté ajoutées à cette attente (l'horloge du téléphone n'est pas celle du serveur).
    static let rearmSafetyMillis = 3_000
    /// Une alerte qui n'a pas pu partir pendant ce temps ne part qu'après une nouvelle confirmation (ticket 12.3).
    static let staleSendMillis = 15 * 60_000
    /// Combien de temps l'app attend le serveur avant de considérer que rien n'est parti (l'écriture est une transaction : pas de réponse =
    /// rien d'écrit).
    static let sendTimeoutSeconds = 20.0
    /// Le message calme de la fin d'une alerte reste à l'écran ce temps.
    static let endedNoticeSeconds = 4.0
    /// Décision du PM (2026-11-18) : AU PLUS 3 débuts d'alerte par personne et par 24 h (fenêtre glissante, toutes les Miffs ensemble), imposés
    /// par les RÈGLES au moyen du compteur `users/{uid}/quota/alerts` (§2.20).
    static let maxStartsPerDay = 3
    static let quotaWindowMillis = 24 * hour
    /// Le compteur vit environ 2 jours après le dernier début : `expireAt` = heure du téléphone + 2 jours (les règles acceptent de 1 à 3).
    static let quotaExpireAfterMillis = 2 * day
    /// Le téléphone ne juge une case périmée que passé 24 h PLUS cette marge (son horloge n'est pas celle du serveur) ; une case que le
    /// serveur juge périmée est toujours honorée.
    static let quotaSafetyMillis = 2 * 60_000
    /// La bulle bleue de l'onboarding ne prend jamais plus de cette part de l'écran en hauteur : au-delà (grandes tailles de texte), son contenu défile dedans.
    static let hintMaxScreenShare = 0.45
    /// Entre deux vérifications de l'horloge d'une alerte (une alerte qui a dépassé sa date disparaît d'elle-même).
    static let tickSeconds = 60.0

    static func expiresAtMillis(deviceNowMillis: Int) -> Int {
        deviceNowMillis + durationMillis - clockMarginMillis
    }

    static func purgeAtOnStartMillis(expiresAtMillis: Int) -> Int {
        expiresAtMillis + purgeAfterExpiryMillis
    }

    static func purgeAtOnEndMillis(deviceNowMillis: Int) -> Int {
        deviceNowMillis + purgeAfterEndMillis
    }
}

/// Une alerte comme `miffs/{miffId}/alerts/{uid}` la dit (la clé est le chemin : ni `miffId` ni `uid` dans le document). Seul ce dont les écrans
/// ont besoin est gardé ; tout vient du réseau, donc chaque champ est vérifié par `parse` et un document qui n'a pas exactement la bonne forme
/// est ignoré (jamais une erreur).
struct DangerAlertDoc: Equatable, CustomStringConvertible {
    /// L'auteur (l'identifiant du document).
    let uid: String
    let active: Bool
    /// L'heure SERVEUR du début ; nil tant que le serveur ne l'a pas remplie (écriture encore en attente) : l'appelant prend alors son horloge.
    let startedAtMillis: Int?
    let expiresAtMillis: Int
    let seq: Int

    /// Une alerte EN COURS : `active` et pas arrivée à sa date. La date est la seule chose qui termine une alerte que personne n'a terminée
    /// (aucune écriture, aucune notification, D19).
    func isLive(nowMillis: Int) -> Bool {
        active && expiresAtMillis > nowMillis
    }

    var description: String {
        "DangerAlertDoc(redacted)"
    }

    /// Le document, ou nil quand ce n'est pas un des nôtres : statut inconnu, pas de date d'échéance exploitable, identifiant étrange.
    static func parse(
        uid: String,
        status: Any?,
        startedAtMillis: Int?,
        expiresAtMillis: Int?,
        seq: Any?
    ) -> DangerAlertDoc? {
        guard PushIdentifier.isValid(uid, maxLength: PushIdentifier.authorMaxLength) else {
            return nil
        }
        let active: Bool
        switch status as? String {
        case "active"?:
            active = true
        case "ended"?:
            active = false
        default:
            return nil
        }
        guard let expiresAtMillis, expiresAtMillis > 0 else {
            return nil
        }
        let number = (seq as? NSNumber)?.intValue ?? 1
        return DangerAlertDoc(
            uid: uid,
            active: active,
            startedAtMillis: startedAtMillis.flatMap { $0 > 0 ? $0 : nil },
            expiresAtMillis: expiresAtMillis,
            seq: number >= 1 ? number : 1
        )
    }
}

extension Array where Element == DangerAlertDoc {
    /// Les alertes d'UNE Miff qui sont en cours à `nowMillis`, dans l'ordre du début (la plus ancienne d'abord, stable).
    func live(at nowMillis: Int) -> [DangerAlertDoc] {
        filter { $0.isLive(nowMillis: nowMillis) }.sorted { left, right in
            let leftStart = left.startedAtMillis ?? Int.max
            let rightStart = right.startedAtMillis ?? Int.max
            if leftStart != rightStart {
                return leftStart < rightStart
            }
            return left.uid < right.uid
        }
    }
}

/// Pourquoi une alerte n'est pas partie ou n'a pas pris fin, réduit à ce que l'écran sait dire calmement.
enum DangerAlertFailure: Equatable {
    /// Pas de réseau, ou pas de réponse à temps : rien n'est écrit (une transaction n'existe qu'en ligne). L'app garde l'alerte EN ATTENTE.
    case offline
    /// Un autre téléphone tient la session du compte : ce téléphone est déconnecté, rien n'est écrit.
    case replaced
    /// Les trois débuts des dernières 24 h sont pris : les règles refuseraient un 4e (décision du PM). Lu AVANT d'écrire : rien n'est tenté.
    case quotaReached
    /// Je ne partage pas (ou plus) avec cette Miff : les règles veulent un partage actif.
    case sharingStopped
    /// La Miff n'est plus lisible pour cette personne (retirée, supprimée).
    case notMember
    /// Le serveur a dit non et rien ci-dessus ne l'explique (horloge très décalée, règles pas à jour...) : une phrase calme.
    case refused
}

/// Le compteur de la limite du jour, tel que le serveur le dit (`users/{uid}/quota/alerts`) : les instants (heure serveur) des derniers
/// débuts, nil pour une case libre. Des instants seulement.
struct DangerAlertQuota: Equatable, CustomStringConvertible {
    let slots: [Int?]

    /// Pas encore de compteur : la création du compteur est le crédit du premier début.
    static let empty = DangerAlertQuota(slots: [nil, nil, nil])

    init(slots: [Int?]) {
        precondition(slots.count == 3, "trois cases")
        self.slots = slots
    }

    /// La case qu'un nouveau début peut remplacer (libre, ou plus vieille que 24 h plus une marge), ou nil quand les trois débuts des
    /// dernières 24 h sont tous là.
    func slotToReplace(nowMillis: Int) -> Int? {
        slots.indices.first { index in
            guard let at = slots[index] else {
                return true
            }
            return nowMillis - at >= DangerAlertRules.quotaWindowMillis + DangerAlertRules.quotaSafetyMillis
        }
    }

    var description: String {
        "DangerAlertQuota(redacted)"
    }
}

/// Les écritures de l'alerte, en données (testées sans Firestore). Elles sont le contrat avec les règles : les sept clés, les dates du serveur pour
/// `startedAt` et `endedAt` (les règles refusent la date du téléphone ici), la fenêtre de `expiresAt`, l'identifiant de session.
enum DangerAlertWrites {
    static let startKeys: Set<String> = ["status", "startedAt", "expiresAt", "endedAt", "seq", "deviceId", "purgeAt"]
    static let endKeys: Set<String> = ["status", "endedAt", "purgeAt", "deviceId"]
    static let quotaKeys: Set<String> = ["s0", "s1", "s2", "lastMiffId", "updatedAt", "expireAt"]

    static func path(miffId: String, uid: String) -> String {
        "miffs/\(miffId)/alerts/\(uid)"
    }

    static func quotaPath(uid: String) -> String {
        "users/\(uid)/quota/alerts"
    }

    /// Un début (création avec `seq` = 1, ou ré-armement avec le `seq` précédent + 1) : sept clés exactement, `endedAt` vaut nul tant que
    /// l'alerte est active. nil quand l'identifiant de session n'a pas la forme exigée ou que `seq` est inférieur à 1 (rien n'est envoyé).
    static func start(deviceId: String, deviceNowMillis: Int, seq: Int) -> [String: FieldValueSpec]? {
        guard DeviceId.isValid(deviceId), seq >= 1 else {
            return nil
        }
        let expires = DangerAlertRules.expiresAtMillis(deviceNowMillis: deviceNowMillis)
        return [
            "status": .string("active"),
            "startedAt": .serverTimestamp,
            "expiresAt": .timestamp(millis: expires),
            "endedAt": .null,
            "seq": .integer(seq),
            "deviceId": .string(deviceId),
            "purgeAt": .timestamp(millis: DangerAlertRules.purgeAtOnStartMillis(expiresAtMillis: expires))
        ]
    }

    /// La fin manuelle : `status`, `endedAt` (heure du serveur), `purgeAt` et `deviceId` changent, rien d'autre (les règles refusent toute
    /// autre clé).
    static func end(deviceId: String, deviceNowMillis: Int) -> [String: FieldValueSpec]? {
        guard DeviceId.isValid(deviceId) else {
            return nil
        }
        return [
            "status": .string("ended"),
            "endedAt": .serverTimestamp,
            "purgeAt": .timestamp(millis: DangerAlertRules.purgeAtOnEndMillis(deviceNowMillis: deviceNowMillis)),
            "deviceId": .string(deviceId)
        ]
    }

    /// Le premier début de la personne : le compteur est CRÉÉ avec l'heure du serveur dans la première case (la création est le crédit).
    static func quotaCreate(miffId: String, deviceNowMillis: Int) -> [String: FieldValueSpec] {
        [
            "s0": .serverTimestamp,
            "s1": .null,
            "s2": .null,
            "lastMiffId": .string(miffId),
            "updatedAt": .serverTimestamp,
            "expireAt": .timestamp(millis: deviceNowMillis + DangerAlertRules.quotaExpireAfterMillis)
        ]
    }

    /// Un début suivant : UNE case (0, 1 ou 2) prend l'heure du serveur ; la Miff de l'alerte, l'heure d'écriture et l'échéance suivent. Les
    /// règles refusent tout autre changement. nil quand la case n'existe pas.
    static func quotaUpdate(slot: Int, miffId: String, deviceNowMillis: Int) -> [String: FieldValueSpec]? {
        guard (0...2).contains(slot) else {
            return nil
        }
        return [
            "s\(slot)": .serverTimestamp,
            "lastMiffId": .string(miffId),
            "updatedAt": .serverTimestamp,
            "expireAt": .timestamp(millis: deviceNowMillis + DangerAlertRules.quotaExpireAfterMillis)
        ]
    }

    /// La suppression de l'alerte d'une Miff, dans les lots qui arrêtent le partage, quittent, retirent un membre ou suppriment la Miff
    /// (les règles l'EXIGENT dans le même lot, data-model.md §2.18). Une suppression de document absent est permise et sans effet.
    /// `enabled` est faux tant que la fonction est éteinte (les règles en production ne connaissent pas encore ce chemin).
    static func deletion(miffId: String, uid: String, enabled: Bool) -> [DocWrite] {
        guard enabled else {
            return []
        }
        return [DocWrite(path: path(miffId: miffId, uid: uid), kind: .delete)]
    }
}
