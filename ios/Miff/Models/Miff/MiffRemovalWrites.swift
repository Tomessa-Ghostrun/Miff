import Foundation

// SÉCURITÉ, pour ce fichier : comme `MiffWrites`, les lots ci-dessous sont le contrat avec backend/firestore.rules (miroir de
// `MiffWrites` d'Android, MIFF-2). Un lot vide veut dire « refusé avant envoi » : l'appelant n'écrit rien. Aucun code ni aucun
// nom n'est jamais montré par une `description`.

/// Les lots de MIFF-4 : renouveler le code, retirer un proche, supprimer une Miff (ticket Miff §5.6 et §9).
extension MiffWrites {
    /// Lieux supprimés par lot quand une Miff est supprimée : chaque suppression coûte 2 lectures aux règles, donc 5 restent
    /// loin des 20 que Firestore accorde.
    static let placesPerLot = 5

    /// Un identifiant de document lu sur le réseau (un membre) ne devient un morceau de chemin que s'il ne peut pas en sortir.
    private static func isPathPart(_ value: String) -> Bool {
        !value.isEmpty && !value.contains("/")
    }

    /// Les trois écritures qui changent le code d'une Miff (critère N1, data-model.md §8) : la Miff pointe vers `newCode`, le
    /// document du nouveau code existe, l'ancien a disparu. Les règles refusent le lot s'il manque l'une des trois (un ancien
    /// code qui marcherait encore laisserait revenir une personne retirée). `name` est le nom que la Miff a MAINTENANT (le
    /// document du code doit porter le même). Vide si un code n'a pas la forme d'un code ou si le code ne change pas.
    private static func codeChange(
        miffId: String,
        name: String,
        oldCode: String,
        newCode: String,
        oldCodeExists: Bool
    ) -> [DocWrite] {
        guard JoinCode.isValid(oldCode), JoinCode.isValid(newCode), oldCode != newCode else {
            return []
        }
        var writes = [
            DocWrite(path: "miffs/\(miffId)", kind: .update, data: ["joinCode": .string(newCode)]),
            DocWrite(
                path: "joinCodes/\(newCode)",
                kind: .set,
                data: ["miffId": .string(miffId), "miffName": .string(name), "createdAt": .serverTimestamp]
            )
        ]
        // Les règles veulent l'ancien code disparu après le lot ; un document de code déjà absent ne peut pas être supprimé
        // de nouveau (la suppression serait refusée), et il a disparu de toute façon.
        if oldCodeExists {
            writes.append(DocWrite(path: "joinCodes/\(oldCode)", kind: .delete))
        }
        return writes
    }

    /// « Créer un nouveau code » (critère N1) : 3 écritures en UN lot.
    static func renewCode(
        miffId: String,
        name: String,
        oldCode: String,
        newCode: String,
        oldCodeExists: Bool = true
    ) -> [DocWrite] {
        codeChange(miffId: miffId, name: name, oldCode: oldCode, newCode: newCode, oldCodeExists: oldCodeExists)
    }

    /// Retirer un proche (critère R2) : 6 écritures en UN lot, dans cet ordre : la fiche, le statut et la photo de la
    /// personne, puis les trois écritures du nouveau code. RIEN d'autre dans ce lot : avec ces six écritures les règles lisent
    /// jusqu'à 18 documents sur les 20 que Firestore autorise (backend/rules-test-report.md, MIFF-2), et une septième écriture
    /// pourrait franchir la ligne. L'index de la personne n'est pas touché (le créateur ne peut pas le supprimer : la personne
    /// le nettoie à son prochain accès).
    static func removeMember(
        miffId: String,
        targetUid: String,
        name: String,
        oldCode: String,
        newCode: String,
        oldCodeExists: Bool = true
    ) -> [DocWrite] {
        let change = codeChange(
            miffId: miffId,
            name: name,
            oldCode: oldCode,
            newCode: newCode,
            oldCodeExists: oldCodeExists
        )
        guard isPathPart(targetUid), !change.isEmpty else {
            return []
        }
        let person = [
            DocWrite(path: "miffs/\(miffId)/members/\(targetUid)", kind: .delete),
            DocWrite(path: "miffs/\(miffId)/status/\(targetUid)", kind: .delete),
            DocWrite(path: "miffs/\(miffId)/photos/\(targetUid)", kind: .delete)
        ]
        return person + change
    }

    /// Supprimer des lieux (étape 2 de la suppression d'une Miff) : de 1 à `placesPerLot` par lot.
    static func deletePlaces(miffId: String, placeIds: [String]) -> [DocWrite] {
        guard (1...placesPerLot).contains(placeIds.count), placeIds.allSatisfy(isPathPart) else {
            return []
        }
        return placeIds.map { DocWrite(path: "miffs/\(miffId)/places/\($0)", kind: .delete) }
    }

    /// Le dernier lot de la suppression d'une Miff (critère S2), 7 écritures au plus en UN lot : la Miff, sa pierre tombale
    /// `miffsDeleted/{id}` (l'identifiant ne pourra plus servir), le code EN VIGUEUR, puis le statut, la photo, la fiche et
    /// l'index du créateur. Les règles le refusent sans la pierre tombale ou sans la suppression du code. `membershipKey` est
    /// nil quand l'index n'a pas été trouvé ; `codeExists` est faux quand le document du code a déjà disparu (il est alors
    /// laissé de côté : les règles refusent la suppression d'un code absent).
    static func finalDelete(
        miffId: String,
        uid: String,
        code: String,
        codeExists: Bool,
        membershipKey: String?
    ) -> [DocWrite] {
        guard JoinCode.isValid(code), isPathPart(uid) else {
            return []
        }
        var writes = [
            DocWrite(path: "miffs/\(miffId)", kind: .delete),
            DocWrite(path: "miffsDeleted/\(miffId)", kind: .set, data: ["deletedAt": .serverTimestamp])
        ]
        if codeExists {
            writes.append(DocWrite(path: "joinCodes/\(code)", kind: .delete))
        }
        writes.append(DocWrite(path: "miffs/\(miffId)/status/\(uid)", kind: .delete))
        writes.append(DocWrite(path: "miffs/\(miffId)/photos/\(uid)", kind: .delete))
        writes.append(DocWrite(path: "miffs/\(miffId)/members/\(uid)", kind: .delete))
        if let membershipKey {
            writes.append(DocWrite(path: "users/\(uid)/memberships/\(membershipKey)", kind: .delete))
        }
        return writes
    }
}
