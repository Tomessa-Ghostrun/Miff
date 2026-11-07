import Foundation

// SÉCURITÉ ET CONFIDENTIALITÉ, pour ce fichier : les écritures de l'Update sont le contrat avec backend/firestore.rules (`validPhoto`,
// `validPhotoLocation`, `validStatus`). Miroir d'`UpdateWrites.kt` d'Android.
// - Le document de la PHOTO a exactement ces clés : `jpeg`, `createdAt`, `expiresAt`, et TOUJOURS `lat`, `lng`, `measuredAt` ensemble
//   (décision du PM du 2026-11-06 : pas de photo sans position ; les règles acceptent les trois ou aucune, le client n'en envoie jamais
//   une sans les autres, ni `deviceId`). Il est écrit avec `set` (jamais une mise à jour partielle) : la nouvelle photo REMPLACE
//   l'ancienne, donc aucune coordonnée d'une photo précédente ne survit.
// - `createdAt` est l'heure du SERVEUR (les règles l'exigent), `expiresAt` celle du téléphone + 23 h (les règles tolèrent 25 h : deux
//   heures de marge pour un téléphone en avance).
// - Le STATUT ne porte AUCUNE coordonnée et jamais `presence`, `placeId`, `presenceAt` (R-LIEU-1 : le document fusionné les garde) ; il
//   porte `deviceId` (SESS-3) comme toute écriture de statut.
// - Aucune coordonnée, aucune photo et aucun nom n'est jamais dans un journal, un événement ou un rapport de plantage : `DocWrite`
//   ne montre que le genre d'écriture.

enum UpdateWrites {
    /// `expiresAt` = maintenant + 23 h (backend/data-model.md §8 (iv) : les règles refusent plus de 25 h).
    static let photoLifetimeMillis = 23 * 60 * 60 * 1000

    private static func isWritable(miffId: String, uid: String) -> Bool {
        MiffId.isValid(miffId) && !uid.isEmpty && !uid.contains("/")
    }

    /// La photo de l'auteur dans UNE Miff : un `set` (remplace), avec son lieu. nil si la photo est vide ou dépasse 150 000 octets, ou si un
    /// identifiant est mal formé : rien n'est alors construit, donc rien n'est envoyé.
    static func photo(miffId: String, uid: String, photo: UpdatePhoto, place: PhotoPlace, nowMillis: Int) -> DocWrite? {
        guard isWritable(miffId: miffId, uid: uid),
              !photo.jpeg.isEmpty, photo.jpeg.count <= UpdatePhotoFormat.maxBytes else {
            return nil
        }
        let fields: [String: FieldValueSpec] = [
            "jpeg": .bytes(photo.jpeg),
            "createdAt": .serverTimestamp,
            "expiresAt": .timestamp(millis: nowMillis + photoLifetimeMillis),
            "lat": .double(place.lat),
            "lng": .double(place.lng),
            "measuredAt": .timestamp(millis: place.measuredAtMillis)
        ]
        return DocWrite(path: "miffs/\(miffId)/photos/\(uid)", kind: .set, data: fields)
    }

    /// Le statut de la photo : un UPDATE des seuls champs de la photo. `presence`, `placeId`, `presenceAt` et `locationAccess` ne sont PAS
    /// envoyés ; `lastUpdateAt` est l'heure du SERVEUR (c'est le moment de l'update, et ce d'où la couleur repart). `photoPublished` vrai :
    /// une photo est publiée (`hasPhoto = true`, `updateText = null` : pas de texte en v1). Faux : la photo de l'auteur a été retirée parce
    /// qu'elle a expiré (`hasPhoto = false`), `lastUpdateAt` est alors laissé tel quel. nil si l'identifiant de session est mal formé.
    static func photoStatus(
        miffId: String,
        uid: String,
        photoPublished: Bool,
        batteryPct: Int?,
        deviceId: String
    ) -> DocWrite? {
        guard isWritable(miffId: miffId, uid: uid), DeviceId.isValid(deviceId) else {
            return nil
        }
        var fields: [String: FieldValueSpec] = [
            "hasPhoto": .bool(photoPublished),
            "batteryPct": battery(batteryPct),
            "updatedAt": .serverTimestamp
        ]
        if photoPublished {
            fields["lastUpdateAt"] = .serverTimestamp
            fields["updateText"] = .null
        }
        let stamped = SessionWriteGuard.stamped(fields, deviceId: deviceId)
        return DocWrite(path: "miffs/\(miffId)/status/\(uid)", kind: .update, data: stamped)
    }

    private static func battery(_ percent: Int?) -> FieldValueSpec {
        guard let percent, (0...100).contains(percent) else {
            return .null
        }
        return .integer(percent)
    }

    /// Ce que publier écrit dans UNE Miff : la photo ET le statut, dans le même lot (un statut qui dit `hasPhoto` sans photo n'existe
    /// jamais). Vide quand quelque chose ne peut pas être construit : un lot vide n'est jamais envoyé.
    static func publish(
        miffId: String,
        uid: String,
        photo: UpdatePhoto,
        place: PhotoPlace,
        context: PublishContext
    ) -> [DocWrite] {
        guard let photoWrite = Self.photo(
            miffId: miffId, uid: uid, photo: photo, place: place, nowMillis: context.nowMillis
        ), let status = photoStatus(
            miffId: miffId, uid: uid, photoPublished: true, batteryPct: context.batteryPct, deviceId: context.deviceId
        ) else {
            return []
        }
        return [photoWrite, status]
    }

    /// Le nettoyage par l'auteur d'une photo expirée : la photo est supprimée et le statut dit `hasPhoto = false`.
    static func removeExpired(miffId: String, uid: String, batteryPct: Int?, deviceId: String) -> [DocWrite] {
        guard isWritable(miffId: miffId, uid: uid),
              let status = photoStatus(
                  miffId: miffId, uid: uid, photoPublished: false, batteryPct: batteryPct, deviceId: deviceId
              ) else {
            return []
        }
        return [DocWrite(path: "miffs/\(miffId)/photos/\(uid)", kind: .delete), status]
    }
}

/// Ce qui est commun à toutes les Miffs d'un lot de publication : l'heure du téléphone, la batterie et l'identifiant de la session.
struct PublishContext: Equatable {
    let nowMillis: Int
    let batteryPct: Int?
    let deviceId: String
}
