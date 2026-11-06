import Foundation

// SÉCURITÉ, pour ce fichier : les écritures ci-dessous sont le contrat avec backend/firestore.rules (règles R-LIEU-5,
// R-LIEU-2 et R-LIEU-6, déployées). Une création écrit CHAQUE clé (les règles refusent une clé manquante et une clé inconnue) ;
// les dates sont celles du serveur ; `createdBy` est l'identifiant de la personne ; les coordonnées ont 5 décimales. Le nom,
// l'adresse et les coordonnées d'un lieu ne quittent ce fichier que dans les documents de Firestore, jamais dans un journal ni
// un événement.

/// Les lots du ticket LOC-3 (miroir de LOC-1), en données : testés sans Firestore, appliqués chacun par UNE transaction.
enum PlaceWrites {
    /// Documents lus par les règles pour la vérification « créateur de la Miff » (`exists` et `get` de la Miff).
    static let ownerReads = 2

    /// Documents lus par les règles pour savoir si le compte est Miff+ (`isPremiumOneRead`).
    static let premiumReads = 1

    static func path(miffId: String, placeId: String) -> String {
        "miffs/\(miffId)/places/\(placeId)"
    }

    /// Les champs que la personne peut changer : ceux d'une mise à jour, et (avec les champs d'identité) d'une création.
    private static func editable(_ draft: PlaceDraft) -> [String: FieldValueSpec] {
        [
            "name": .string(PlaceName.stored(draft.name)),
            "type": .string(draft.type.rawValue),
            "category": .string(draft.category.rawValue),
            "address": .string(PlaceAddressText.stored(draft.address)),
            "lat": .double(PlaceCoordinates.round5(draft.lat)),
            "lng": .double(PlaceCoordinates.round5(draft.lng)),
            "radiusM": .integer(draft.radiusM)
        ]
    }

    /// Un document entier : chaque clé, comme `validPlaceShape` et `validPlaceCreate` le veulent. `priority` reste nul (pas
    /// d'usage pour l'instant).
    static func createDocument(uid: String, draft: PlaceDraft) -> [String: FieldValueSpec] {
        var fields = editable(draft)
        fields["priority"] = .null
        fields["createdBy"] = .string(uid)
        fields["createdAt"] = .serverTimestamp
        fields["updatedAt"] = .serverTimestamp
        return fields
    }

    /// Les champs changés d'un document existant : `createdBy`, `createdAt` et `priority` ne sont jamais touchés (immuables ou
    /// sans usage).
    static func updateFields(_ draft: PlaceDraft) -> [String: FieldValueSpec] {
        var fields = editable(draft)
        fields["updatedAt"] = .serverTimestamp
        return fields
    }

    /// UN lot : le même document dans chaque Miff choisie, avec le même identifiant (décision Q4).
    static func create(uid: String, placeId: String, draft: PlaceDraft) -> [DocWrite] {
        draft.miffIds.map { miffId in
            DocWrite(
                path: path(miffId: miffId, placeId: placeId),
                kind: .set,
                data: createDocument(uid: uid, draft: draft)
            )
        }
    }

    static func delete(placeId: String, miffIds: [String]) -> [DocWrite] {
        miffIds.map { DocWrite(path: path(miffId: $0, placeId: placeId), kind: .delete) }
    }

    /// Ce qui change un lieu existant, attaché à `currentMiffIds`, pour qu'il soit attaché à `draft.miffIds` : les Miffs gardées
    /// sont mises à jour, les Miffs ajoutées reçoivent un document neuf de même identifiant, les Miffs retirées perdent le leur
    /// (décision Q4). Le document d'une Miff ajoutée s'écrit comme une création.
    ///
    /// Les règles lisent des documents pour chaque écriture (backend/data-model.md §12 : 2 pour « créateur de la Miff », +1 quand
    /// Miff+ est vérifié) : si le lot dépassait `PlaceLimits.ruleReadBudget`, il est coupé en deux, les ajouts et changements
    /// d'abord, les retraits ensuite. Chaque lot est atomique ; entre les deux, le lieu est simplement dans les anciennes ET les
    /// nouvelles Miffs (état valide). Un second essai n'est PAS un rejeu des mêmes écritures (une Miff ajoutée par le premier lot
    /// porte déjà son document : l'écrire de nouveau comme une création serait refusé) : `currentMiffIds` doit être ce que le
    /// serveur détient MAINTENANT. Le dépôt relit les copies avant chaque essai et les donne ici.
    static func change(
        uid: String,
        placeId: String,
        currentMiffIds: [String],
        draft: PlaceDraft,
        premiumChecks: (typeChangedToRisk: Bool, addedNeedPremium: Bool)
    ) -> [[DocWrite]] {
        let kept = draft.miffIds.filter { currentMiffIds.contains($0) }
        let added = draft.miffIds.filter { !currentMiffIds.contains($0) }
        let removed = currentMiffIds.filter { !draft.miffIds.contains($0) }
        let updates = kept.map { miffId in
            DocWrite(path: path(miffId: miffId, placeId: placeId), kind: .update, data: updateFields(draft))
        }
        let creations = added.map { miffId in
            DocWrite(
                path: path(miffId: miffId, placeId: placeId),
                kind: .set,
                data: createDocument(uid: uid, draft: draft)
            )
        }
        let changes = updates + creations
        let deletions = delete(placeId: placeId, miffIds: removed)
        let keptCost = kept.count * (ownerReads + (premiumChecks.typeChangedToRisk ? premiumReads : 0))
        let addedCost = added.count * (ownerReads + (premiumChecks.addedNeedPremium ? premiumReads : 0))
        let cost = keptCost + addedCost + removed.count * ownerReads
        let lots = cost <= PlaceLimits.ruleReadBudget ? [changes + deletions] : [changes, deletions]
        return lots.filter { !$0.isEmpty }
    }

    /// Le pire nombre de documents lus par les règles pour un lot de créations (le budget est 20 : 5 Miffs à 3 = 15).
    static func createReads(miffCount: Int, needsPremium: Bool) -> Int {
        miffCount * (ownerReads + (needsPremium ? premiumReads : 0))
    }
}
