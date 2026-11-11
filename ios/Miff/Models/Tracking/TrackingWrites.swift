import Foundation

// SÉCURITÉ ET CONFIDENTIALITÉ, pour ce fichier : les écritures ci-dessous sont le contrat avec backend/firestore.rules (`validStatus`,
// `validPosition`).
// - Le STATUT ne porte aucune coordonnée : des faits seulement (présence, identifiant du lieu, heures, batterie, accès à la localisation).
// - La DERNIÈRE POSITION est le seul endroit où une coordonnée quitte le téléphone : `positions/{moi}` d'UNE Miff, écrite SEULE (jamais
//   dans un lot de lieux ni avec un autre document), 5 décimales, l'heure de la MESURE, aucun historique (le document est remplacé).
// - Les heures sont celles de l'événement RÉEL, plafonnées à l'horloge du téléphone (les règles acceptent 24 h dans le passé et 5
//   minutes dans le futur).
// - « Un compte, un seul appareil » (SESS-3) : CHAQUE écriture de statut et de position porte `deviceId`, l'identifiant de la session de
//   ce téléphone, mises à jour partielles comprises (les règles jugent le document qui résulte : une mise à jour partielle sans la clé
//   ne passe que si la base porte déjà la valeur de la session, et l'étape suivante du déploiement rend la clé obligatoire). Un
//   identifiant mal formé fait rendre `nil` : l'écriture n'est pas construite (`SessionWriteGuard` la refuserait de toute façon).
// - R-LIEU-1 (déployée) : une mise à jour n'envoie jamais `presenceAt` ni `lastUpdateAt` quand elle ne les change pas (le document
//   fusionné garde la valeur en base, que les règles acceptent quel que soit son âge). `lastUpdateAt` n'est jamais réglé sur l'heure
//   d'un événement pour passer les règles.
// Miroir de `TrackingWrites.kt` d'Android (LOC-2b).

/// Ce qu'une mise à jour automatique de `status/{moi}` d'une Miff écrit.
struct StatusPayload {
    let fact: StatusFact
    /// L'heure de l'événement, déjà passée par `EventTime.forWrite`.
    let presenceAtMillis: Int
    let batteryPct: Int?
    let access: LocationAccess
    /// MODE VOYAGE : ce statut met fin à un voyage, donc les deux clés du voyage sont effacées dans la MÊME écriture (les règles les refusent à
    /// côté d'une autre présence).
    var clearTravel = false
}

enum TrackingWrites {
    private static func isWritable(miffId: String, uid: String, deviceId: String) -> Bool {
        MiffId.isValid(miffId) && !uid.isEmpty && !uid.contains("/") && DeviceId.isValid(deviceId)
    }

    private static func battery(_ percent: Int?) -> FieldValueSpec {
        guard let percent, (0...100).contains(percent) else {
            return .null
        }
        return .integer(percent)
    }

    /// Une mise à jour automatique de `status/{moi}` d'UNE Miff : un UPDATE des seuls champs qui changent, pour que `updateText` et
    /// `hasPhoto` restent comme la personne les a laissés (ticket §6.6 « conservés ») et que `lastUpdateAt` reste aussi, sauf si le
    /// fait dit une arrivée en zone de confiance. `escalationDueAt` est nul (inutile sur le plan Spark).
    static func statusUpdate(miffId: String, uid: String, payload: StatusPayload, deviceId: String) -> DocWrite? {
        guard isWritable(miffId: miffId, uid: uid, deviceId: deviceId) else {
            return nil
        }
        var fields: [String: FieldValueSpec] = [
            "presence": .string(payload.fact.presence.rawValue),
            "placeId": payload.fact.placeId.map { FieldValueSpec.string($0) } ?? .null,
            "presenceAt": .timestamp(millis: payload.presenceAtMillis),
            "batteryPct": battery(payload.batteryPct),
            "locationAccess": .string(payload.access.rawValue),
            "escalationDueAt": .null,
            "updatedAt": .serverTimestamp
        ]
        if payload.fact.setLastUpdate {
            fields["lastUpdateAt"] = .timestamp(millis: payload.presenceAtMillis)
        }
        if payload.clearTravel {
            fields["travelUntil"] = .null
            fields["travelUntilDay"] = .null
        }
        let stamped = SessionWriteGuard.stamped(fields, deviceId: deviceId)
        return DocWrite(path: "miffs/\(miffId)/status/\(uid)", kind: .update, data: stamped)
    }

    /// MODE VOYAGE, le DÉBUT d'un voyage dans UNE Miff (backend/firestore.rules `validTravel`) : `presence = travel`, aucun lieu, aucune batterie,
    /// aucune échéance, la fin du voyage (un instant) et le jour choisi (un texte, affichage seul). `presenceAt` est le moment où le voyage a
    /// commencé (« En voyage depuis… »), déjà passé par `EventTime.forWrite`. Un UPDATE (il ne crée jamais un statut) : `lastUpdateAt`,
    /// `updateText`, `hasPhoto` et `locationAccess` ne sont pas envoyés, ils restent ce qu'ils sont.
    static func travelStart(
        miffId: String,
        uid: String,
        period: TravelPeriod,
        presenceAtMillis: Int,
        deviceId: String
    ) -> DocWrite? {
        guard isWritable(miffId: miffId, uid: uid, deviceId: deviceId), TravelDate.parse(period.untilDay) != nil else {
            return nil
        }
        let fields: [String: FieldValueSpec] = [
            "presence": .string(PresenceValue.travel.rawValue),
            "placeId": .null,
            "presenceAt": .timestamp(millis: presenceAtMillis),
            "batteryPct": .null,
            "escalationDueAt": .null,
            "travelUntil": .timestamp(millis: period.untilMillis),
            "travelUntilDay": .string(period.untilDay),
            "updatedAt": .serverTimestamp
        ]
        let stamped = SessionWriteGuard.stamped(fields, deviceId: deviceId)
        return DocWrite(path: "miffs/\(miffId)/status/\(uid)", kind: .update, data: stamped)
    }

    /// MODE VOYAGE, un CHANGEMENT de la fin d'un voyage déjà écrit dans cette Miff : seules les deux clés du voyage bougent (le début reste).
    static func travelDate(miffId: String, uid: String, period: TravelPeriod, deviceId: String) -> DocWrite? {
        guard isWritable(miffId: miffId, uid: uid, deviceId: deviceId), TravelDate.parse(period.untilDay) != nil else {
            return nil
        }
        let fields: [String: FieldValueSpec] = [
            "travelUntil": .timestamp(millis: period.untilMillis),
            "travelUntilDay": .string(period.untilDay),
            "updatedAt": .serverTimestamp
        ]
        let stamped = SessionWriteGuard.stamped(fields, deviceId: deviceId)
        return DocWrite(path: "miffs/\(miffId)/status/\(uid)", kind: .update, data: stamped)
    }

    /// Seulement la batterie et l'accès à la localisation (quand l'autorisation a changé) : la présence n'est pas touchée.
    static func accessUpdate(
        miffId: String,
        uid: String,
        batteryPct: Int?,
        access: LocationAccess,
        deviceId: String
    ) -> DocWrite? {
        guard isWritable(miffId: miffId, uid: uid, deviceId: deviceId) else {
            return nil
        }
        let fields: [String: FieldValueSpec] = [
            "batteryPct": battery(batteryPct),
            "locationAccess": .string(access.rawValue),
            "updatedAt": .serverTimestamp
        ]
        let stamped = SessionWriteGuard.stamped(fields, deviceId: deviceId)
        return DocWrite(path: "miffs/\(miffId)/status/\(uid)", kind: .update, data: stamped)
    }

    /// Rafraîchissement de la batterie (à l'ouverture et à la sortie de l'app) : un UPDATE de `batteryPct` et `updatedAt` SEULS (plus
    /// `deviceId`). Le document qui résulte garde, tels que la base les a, la présence, le lieu, `presenceAt`, `lastUpdateAt`,
    /// `updateText`, `hasPhoto`, `locationAccess` et `escalationDueAt` : les règles l'acceptent (heures « conservées » R-LIEU-1,
    /// `updatedAt` = heure du serveur, clé de session). Si le statut n'existe pas, un update échoue : jamais de création. Le niveau
    /// doit être connu (0 à 100) : on n'efface jamais une batterie connue par un `null`.
    static func batteryUpdate(miffId: String, uid: String, batteryPct: Int, deviceId: String) -> DocWrite? {
        guard isWritable(miffId: miffId, uid: uid, deviceId: deviceId), (0...100).contains(batteryPct) else {
            return nil
        }
        let fields: [String: FieldValueSpec] = [
            "batteryPct": .integer(batteryPct),
            "updatedAt": .serverTimestamp
        ]
        let stamped = SessionWriteGuard.stamped(fields, deviceId: deviceId)
        return DocWrite(path: "miffs/\(miffId)/status/\(uid)", kind: .update, data: stamped)
    }

    /// La dernière position connue dans UNE Miff (`backend/data-model.md` §2.12) : latitude et longitude à 5 décimales, heure de la
    /// mesure. Rien d'autre (ni précision, ni vitesse, ni altitude, ni adresse, ni lieu).
    static func position(miffId: String, uid: String, point: WrittenPosition, deviceId: String) -> DocWrite? {
        guard isWritable(miffId: miffId, uid: uid, deviceId: deviceId),
              point.lat.isFinite, point.lng.isFinite,
              abs(point.lat) <= 90, abs(point.lng) <= 180 else {
            return nil
        }
        let fields: [String: FieldValueSpec] = [
            "lat": .double(TrackingGeo.round5(point.lat)),
            "lng": .double(TrackingGeo.round5(point.lng)),
            "measuredAt": .timestamp(millis: point.atMillis)
        ]
        let stamped = SessionWriteGuard.stamped(fields, deviceId: deviceId)
        return DocWrite(path: "miffs/\(miffId)/positions/\(uid)", kind: .set, data: stamped)
    }
}
