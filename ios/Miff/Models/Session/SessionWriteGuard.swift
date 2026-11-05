import Foundation

/// Le garde du contrat « `deviceId` dans CHAQUE écriture de statut et de position » (SESS-1 : `backend/data-model.md`, section
/// « Un compte, un seul appareil », contrat client I-1).
///
/// Les règles Firestore ne peuvent pas l'exiger d'une mise à jour partielle (SF9 : une mise à jour qui n'envoie pas la clé passe si
/// la valeur déjà en base est celle de la session) : seul le client protège. Ce garde est donc appliqué à CHAQUE lot juste avant
/// son envoi (`FirestoreMiffStore`), et testé sur chaque lot que l'app sait construire. Une future écriture de statut ou de position
/// (LOC-4b) qui oublierait la clé est refusée AVANT d'être envoyée, et fait échouer les tests.
///
/// POUR LOC-4b : construire les champs avec `SessionWriteGuard.stamped(_:deviceId:)` à partir de `DeviceSession.currentDeviceId`
/// (nil = pas de session confirmée = ne rien écrire), pour `miffs/{id}/status/{uid}` et `miffs/{id}/positions/{uid}`, en `set` comme
/// en `update` partiel.
enum SessionWriteGuard {
    static let field = "deviceId"

    /// Ajoute l'identifiant de session aux champs d'un statut ou d'une position.
    static func stamped(_ fields: [String: FieldValueSpec], deviceId: String) -> [String: FieldValueSpec] {
        var stamped = fields
        stamped[field] = .string(deviceId)
        return stamped
    }

    /// Cette écriture est-elle un statut ou une position (`miffs/{id}/status/{uid}`, `miffs/{id}/positions/{uid}`) créé ou mis à
    /// jour ? Une suppression n'a aucune charge utile : rien à estampiller (les règles ne la contrôlent pas non plus).
    static func requiresDeviceId(_ write: DocWrite) -> Bool {
        guard write.kind != .delete else {
            return false
        }
        let parts = write.path.split(separator: "/")
        guard parts.count == 4, parts[0] == "miffs" else {
            return false
        }
        return parts[2] == "status" || parts[2] == "positions"
    }

    /// L'écriture porte-t-elle un identifiant de session bien formé ?
    static func carriesDeviceId(_ write: DocWrite) -> Bool {
        if case .string(let value)? = write.data[field] {
            return DeviceId.isValid(value)
        }
        return false
    }

    /// Les écritures d'un lot qui devraient porter l'identifiant et ne le portent pas.
    static func violations(in writes: [DocWrite]) -> [DocWrite] {
        writes.filter { requiresDeviceId($0) && !carriesDeviceId($0) }
    }

    static func isCompliant(_ writes: [DocWrite]) -> Bool {
        violations(in: writes).isEmpty
    }
}
