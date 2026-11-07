import FirebaseFirestore
import Foundation

// SÉCURITÉ, pour ce fichier : ce qui est lu ici a été écrit par QUELQU'UN D'AUTRE (statut d'un proche, position). Tout est
// vérifié avant d'être utilisé : une présence inconnue est neutre, un identifiant de lieu de la mauvaise forme est écarté, une
// batterie hors de 0 à 100 est écartée, une coordonnée hors bornes ou une position sans heure de mesure n'existe pas. Aucun
// chemin n'est construit avec un identifiant dont la forme n'a pas été vérifiée. Rien n'est journalisé.

enum FeedFirestoreMapping {
    /// Un identifiant de compte Firebase : lettres et chiffres, 1 à 128 caractères (jamais un morceau de chemin).
    static func isUid(_ value: String) -> Bool {
        let scalars = Array(value.unicodeScalars)
        return !scalars.isEmpty && scalars.count <= 128 && scalars.allSatisfy { scalar in
            (scalar.value >= 0x30 && scalar.value <= 0x39)
                || (scalar.value >= 0x41 && scalar.value <= 0x5A)
                || (scalar.value >= 0x61 && scalar.value <= 0x7A)
        }
    }

    private static func millis(_ value: Any?) -> Int? {
        (value as? Timestamp).map { Int($0.dateValue().timeIntervalSince1970 * 1000) }
    }

    /// Les champs d'un document `status/{uid}`. Les clés sont toujours présentes dans les règles ; ce qui manque est nil.
    static func status(fields: [String: Any]) -> MemberStatus {
        MemberStatus.fromServer(
            presence: fields["presence"] as? String,
            placeId: fields["placeId"] as? String,
            presenceAtMillis: millis(fields["presenceAt"]),
            lastUpdateAtMillis: millis(fields["lastUpdateAt"]),
            hasPhoto: fields["hasPhoto"] as? Bool,
            batteryPct: (fields["batteryPct"] as? NSNumber)?.doubleValue,
            locationAccess: fields["locationAccess"] as? String,
            // Une écriture encore en attente du serveur n'a pas son heure : on lit l'estimation (« maintenant »).
            updatedAtMillis: millis(fields["updatedAt"])
        )
    }

    /// Tous les statuts d'un instantané, par identifiant de membre. Un document illisible n'est simplement pas montré : il ne doit
    /// jamais faire tomber le rappel d'une écoute, donc l'app.
    static func statuses(of snapshot: QuerySnapshot) -> [String: MemberStatus] {
        var byUid: [String: MemberStatus] = [:]
        for document in snapshot.documents where isUid(document.documentID) {
            byUid[document.documentID] = status(fields: document.data(with: .estimate))
        }
        return byUid
    }

    /// Un document qui n'est pas une position de cette app (coordonnée manquante, pas un nombre, hors bornes, ou pas d'heure de
    /// mesure) est « introuvable » : la feuille ne dessine ni n'ouvre jamais un point dont elle ne peut pas répondre.
    static func position(fields: [String: Any]?) -> PositionLookup {
        guard let fields,
              let lat = (fields["lat"] as? NSNumber)?.doubleValue,
              let lng = (fields["lng"] as? NSNumber)?.doubleValue,
              let measuredAt = millis(fields["measuredAt"]),
              lat.isFinite, lng.isFinite, lat >= -90, lat <= 90, lng >= -180, lng <= 180 else {
            return .notFound
        }
        return .found(lat: lat, lng: lng, measuredAtMillis: measuredAt)
    }

    /// Ce que veut dire une lecture ratée, en réponses calmes : un refus des règles est `denied` (UN message pour tous les refus) ;
    /// « indisponible » est la façon du SDK de dire que le client est hors ligne ; tout le reste (délai dépassé, quota du plan
    /// atteint, connexion expirée, erreur du serveur) est `failed`. Le message de l'erreur n'est jamais lu : il peut citer un chemin.
    static func lookup(forError error: Error) -> PositionLookup {
        let nsError = error as NSError
        guard nsError.domain == FirestoreErrorNumber.domain else {
            return .failed
        }
        switch nsError.code {
        case FirestoreErrorNumber.permissionDenied:
            return .denied
        case FirestoreErrorNumber.unavailable:
            return .offline
        default:
            return .failed
        }
    }
}
