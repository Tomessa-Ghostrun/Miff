import Foundation

// SÉCURITÉ, pour ce fichier : le nom, l'adresse et les coordonnées d'un lieu sont sensibles. Ils ne sont jamais journalisés,
// jamais dans un événement Analytics, jamais dans un message d'erreur. Chaque échec sort d'ici sous la forme d'une
// `MiffFailure` (sans message, sans chemin).

/// Tout ce que font les écrans avec les lieux auprès de Firestore (ticket LOC-3, miroir de LOC-1) : ce que la personne détient,
/// les limites de la formule gratuite décidées AVANT d'écrire, les lots (un document par Miff, même identifiant), et la
/// vérification auprès du serveur d'une écriture dont la réponse s'est perdue. Firestore est derrière `PlaceStore`.
final class FirestorePlaceRepository: PlaceRepository {
    private let store: PlaceStore
    private let newId: () -> String
    private let nowMillis: () -> Int
    private let timeoutSeconds: TimeInterval

    init(
        store: PlaceStore,
        newId: @escaping () -> String,
        nowMillis: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) },
        timeoutSeconds: TimeInterval = 20
    ) {
        self.store = store
        self.newId = newId
        self.nowMillis = nowMillis
        self.timeoutSeconds = timeoutSeconds
    }

    // MARK: - Lire

    func prepare(uid: String) async -> PlaceContextLoad {
        let store = self.store
        // Un abonnement inconnu n'est PAS « gratuit » : une lecture qui échoue n'ouvre jamais l'offre (la personne est invitée à
        // réessayer).
        let premium: Bool
        switch await guarded({ try await store.readEntitlement(uid: uid) }) {
        case .value(let read):
            premium = isActive(read)
        case .failed(let failure, _):
            return .failed(failure)
        }
        let memberships: [PlaceMembership]
        switch await guarded({ try await store.readMemberships(uid: uid) }) {
        case .value(let found):
            memberships = found
        case .failed(let failure, _):
            return .failed(failure)
        }
        let owned: [OwnedMiff]
        switch await ownedMiffs(uid: uid, memberships: memberships) {
        case .value(let found):
            owned = found
        case .failed(let failure, _):
            return .failed(failure)
        }
        var places: [Place] = []
        for miff in owned {
            switch await guarded({ try await store.readPlaces(miffId: miff.id) }) {
            case .value(let found):
                places += found
            case .failed(let failure, _):
                return .failed(failure)
            }
        }
        let lastJoined = memberships.max { $0.joinedAtMillis < $1.joinedAtMillis }?.miffId
        let sorted = owned.sorted { $0.joinedAtMillis < $1.joinedAtMillis }
        let context = PlaceContext(premium: premium, ownedMiffs: sorted, lastJoinedMiffId: lastJoined, places: places)
        return .loaded(context)
    }

    /// Les Miffs que la personne a créées parmi ses appartenances. Une Miff illisible n'est pas une Miff où elle peut écrire ;
    /// toute autre défaillance arrête la lecture.
    private func ownedMiffs(uid: String, memberships: [PlaceMembership]) async -> Guarded<[OwnedMiff]> {
        let store = self.store
        var owned: [OwnedMiff] = []
        for membership in memberships {
            switch await guarded({ try await store.readMiff(miffId: membership.miffId) }) {
            case .value(.exists(let details)):
                if details.ownerUid == uid {
                    owned.append(
                        OwnedMiff(id: membership.miffId, name: details.name, joinedAtMillis: membership.joinedAtMillis)
                    )
                }
            case .value(.missing):
                continue
            case .failed(let failure, let lostAccess):
                if !lostAccess {
                    return .failed(failure, lostAccess: false)
                }
            }
        }
        return .value(owned)
    }

    /// Miff+ = un abonnement actif qui n'a pas expiré (les règles décident pour de vrai). Aucun document = pas Miff+.
    private func isActive(_ read: EntitlementRead?) -> Bool {
        guard let read, let expires = read.expiresAtMillis else {
            return false
        }
        return read.active && expires > nowMillis()
    }

    // MARK: - Créer

    func create(uid: String, draft: PlaceDraft, context: PlaceContext) async -> PlaceWriteResult {
        guard draft.problem == nil, isOwned(draft.miffIds, by: context) else {
            return .failed(.unknown)
        }
        let slot = PlaceQuota.forCreate(
            premium: context.premium,
            type: draft.type,
            existingIdsByMiff: context.idsByMiff,
            distinctPlaces: context.distinctPlaces,
            newId: newId
        )
        let placeId: String
        switch slot {
        case .needsPremium(let reason):
            return .needsPremium(reason)
        case .use(let id):
            placeId = id
        }
        guard let failure = await commitAll([PlaceWrites.create(uid: uid, placeId: placeId, draft: draft)]) else {
            return .done(placeId: placeId)
        }
        let done = PlaceWriteResult.done(placeId: placeId)
        return await afterFailedWrite(uid: uid, failure: failure, miffIds: draft.miffIds, done: done) { places in
            self.matchesEverywhere(places, placeId: placeId, draft: draft)
        }
    }

    // MARK: - Modifier

    func update(
        uid: String,
        original: PlaceGroup,
        draft: PlaceDraft,
        context: PlaceContext
    ) async -> PlaceWriteResult {
        guard draft.problem == nil, isOwned(draft.miffIds, by: context), Place.isPlaceId(original.id) else {
            return .failed(.unknown)
        }
        // Les copies sont RELUES avant d'écrire : un second essai après un premier qui a échoué (ou un changement fait sur un
        // autre téléphone) part de ce que le serveur détient maintenant, pas de ce avec quoi le formulaire s'est ouvert. Copies
        // de ce lieu : celles de ses propres Miffs, et celles qu'un essai précédent a pu écrire dans une Miff ajoutée depuis.
        let everywhere = unique(original.miffIds + draft.miffIds)
        let fresh: [Place]
        switch await placesRead(everywhere) {
        case .value(let found):
            fresh = found
        case .failed(let failure, _):
            return .failed(failure)
        }
        let copies = currentCopies(of: original, wanted: draft, among: fresh)
        guard let from = copies.first else {
            return .placeGone
        }
        // Faire d'un lieu une zone de danger est réservé à Miff+ (R-LIEU-6) ; revenir à « confiance » est toujours permis.
        if let reason = PlaceQuota.forTypeChange(premium: context.premium, from: from.type, to: draft.type) {
            return .needsPremium(reason)
        }
        let current = copies.map { $0.miffId }
        let limitHit = needsPremiumForAddedMiffs(
            original: original,
            draft: draft,
            current: current,
            fresh: fresh,
            premium: context.premium
        )
        if limitHit {
            return .needsPremium(.limit)
        }
        let lots = PlaceWrites.change(
            uid: uid,
            placeId: original.id,
            currentMiffIds: current,
            draft: draft,
            premiumChecks: (
                typeChangedToRisk: draft.type == .risk && from.type != .risk,
                addedNeedPremium: addedMiffsNeedPremium(original: original, draft: draft)
            )
        )
        guard let failure = await commitAll(lots) else {
            return .done(placeId: original.id)
        }
        return await verdictAfterFailedUpdate(
            uid: uid,
            failure: failure,
            original: original,
            draft: draft,
            current: current
        )
    }

    /// Les copies de ce lieu que le serveur détient MAINTENANT : celles de ses propres Miffs, et celles qu'un essai précédent a pu
    /// écrire dans une Miff ajoutée depuis (mêmes champs que le brouillon).
    private func currentCopies(of original: PlaceGroup, wanted draft: PlaceDraft, among fresh: [Place]) -> [Place] {
        fresh.filter { place in
            guard place.id == original.id else {
                return false
            }
            return original.miffIds.contains(place.miffId) || isCopy(place, of: draft)
        }
    }

    /// Une Miff ajoutée reçoit un document NEUF : gratuit seulement dans les emplacements fixes, et comme lieu de confiance.
    private func addedMiffsNeedPremium(original: PlaceGroup, draft: PlaceDraft) -> Bool {
        !(PlaceLimits.freeIds.contains(original.id) && draft.type == .trusted)
    }

    /// Ajouter une Miff à un lieu qui n'a pas d'emplacement fixe (ou dont l'emplacement est pris par un autre lieu) demande Miff+.
    private func needsPremiumForAddedMiffs(
        original: PlaceGroup,
        draft: PlaceDraft,
        current: [String],
        fresh: [Place],
        premium: Bool
    ) -> Bool {
        let added = draft.miffIds.filter { !current.contains($0) }
        if premium || added.isEmpty {
            return false
        }
        let needsPremium = addedMiffsNeedPremium(original: original, draft: draft)
        let slotTaken = added.contains { miffId in fresh.contains { $0.miffId == miffId && $0.id == original.id } }
        return needsPremium || slotTaken
    }

    private func verdictAfterFailedUpdate(
        uid: String,
        failure: MiffFailure,
        original: PlaceGroup,
        draft: PlaceDraft,
        current: [String]
    ) async -> PlaceWriteResult {
        let everywhere = unique(original.miffIds + draft.miffIds)
        let removed = current.filter { !draft.miffIds.contains($0) }
        let verdict = await afterFailedWrite(
            uid: uid,
            failure: failure,
            miffIds: everywhere,
            done: .done(placeId: original.id)
        ) { places in
            // Le changement est passé quand chaque Miff voulue détient les nouveaux champs et qu'aucune Miff retirée ne détient
            // plus de copie.
            self.matchesEverywhere(places, placeId: original.id, draft: draft)
                && !removed.contains { miffId in places.contains { $0.miffId == miffId && $0.id == original.id } }
        }
        if case .failed = verdict, failure != .offline {
            // Supprimé depuis un autre téléphone pendant que le formulaire était ouvert : plus aucune Miff ne détient le lieu.
            if let left = await placesOf(original.miffIds), !left.contains(where: { $0.id == original.id }) {
                return .placeGone
            }
        }
        return verdict
    }

    // MARK: - Supprimer

    func delete(placeId: String, miffIds: [String]) async -> PlaceWriteResult {
        guard Place.isPlaceId(placeId), !miffIds.isEmpty, miffIds.count <= PlaceLimits.maxMiffs,
              miffIds.allSatisfy({ MiffId.isValid($0) }) else {
            return .failed(.unknown)
        }
        guard let failure = await commitAll([PlaceWrites.delete(placeId: placeId, miffIds: miffIds)]) else {
            return .done(placeId: placeId)
        }
        // Un lot dont la réponse s'est perdue, ou un lieu déjà supprimé ailleurs : il a disparu de toutes les Miffs, ce qui est
        // ce qui était demandé.
        if failure == .refused || failure == .offline || failure == .unknown,
           let left = await placesOf(miffIds), !left.contains(where: { $0.id == placeId }) {
            return .done(placeId: placeId)
        }
        return .failed(failure)
    }
}

// MARK: - Intérieur

extension FirestorePlaceRepository {
    private func isOwned(_ miffIds: [String], by context: PlaceContext) -> Bool {
        let owned = Set(context.ownedMiffs.map { $0.id })
        return miffIds.allSatisfy { owned.contains($0) }
    }

    private func unique(_ values: [String]) -> [String] {
        var seen = Set<String>()
        return values.filter { seen.insert($0).inserted }
    }

    /// Joue les lots l'un après l'autre ; nil quand tous ont été confirmés, sinon l'échec du premier qui ne l'a pas été.
    private func commitAll(_ lots: [[DocWrite]]) async -> MiffFailure? {
        let store = self.store
        for lot in lots {
            if case .failed(let failure, _) = await guarded({ try await store.commit(lot) }) {
                return failure
            }
        }
        return nil
    }

    /// Une écriture non confirmée est regardée sur le serveur avant de dire à la personne qu'elle a échoué : (1) est-elle passée
    /// quand même (réponse perdue) ? alors `done` ; (2) a-t-elle été refusée parce qu'une Miff choisie a disparu ? Sinon elle a
    /// simplement échoué, avec la raison que la personne peut lire.
    private func afterFailedWrite(
        uid: String,
        failure: MiffFailure,
        miffIds: [String],
        done: PlaceWriteResult,
        landed: ([Place]) -> Bool
    ) async -> PlaceWriteResult {
        if failure == .offline || failure == .unknown,
           let places = await placesOf(miffIds), landed(places) {
            return done
        }
        if failure == .refused || failure == .unknown {
            for miffId in miffIds {
                let gone = await isGone(miffId, for: uid)
                if gone {
                    return .miffUnavailable
                }
            }
        }
        return .failed(failure)
    }

    /// Cette Miff n'existe plus, ou n'est plus celle de la personne (lu auprès du SERVEUR).
    private func isGone(_ miffId: String, for uid: String) async -> Bool {
        let store = self.store
        switch await guarded({ try await store.readMiff(miffId: miffId) }) {
        case .value(.exists(let details)):
            return details.ownerUid != uid
        case .value(.missing):
            return true
        case .failed(_, let lostAccess):
            return lostAccess
        }
    }

    /// Les lieux de ces Miffs tels que le serveur les a. Une Miff illisible (accès perdu) compte pour vide.
    private func placesRead(_ miffIds: [String]) async -> Guarded<[Place]> {
        let store = self.store
        var all: [Place] = []
        for miffId in miffIds {
            switch await guarded({ try await store.readPlaces(miffId: miffId) }) {
            case .value(let found):
                all += found
            case .failed(let failure, let lostAccess):
                if !lostAccess {
                    return .failed(failure, lostAccess: false)
                }
            }
        }
        return .value(all)
    }

    /// Pareil, ou nil quand ils ne peuvent pas être lus.
    private func placesOf(_ miffIds: [String]) async -> [Place]? {
        if case .value(let places) = await placesRead(miffIds) {
            return places
        }
        return nil
    }

    /// Cette copie détient ce que dit le brouillon (nom, position, rayon, type, catégorie) : un essai précédent l'a écrite.
    private func isCopy(_ place: Place, of draft: PlaceDraft) -> Bool {
        place.name == PlaceName.stored(draft.name) && place.type == draft.type && place.category == draft.category
            && place.radiusM == draft.radiusM && place.lat == PlaceCoordinates.round5(draft.lat)
            && place.lng == PlaceCoordinates.round5(draft.lng)
    }

    private func matchesEverywhere(_ places: [Place], placeId: String, draft: PlaceDraft) -> Bool {
        draft.miffIds.allSatisfy { miffId in
            guard let copy = places.first(where: { $0.miffId == miffId && $0.id == placeId }) else {
                return false
            }
            return isCopy(copy, of: draft)
        }
    }

    private func guarded<Value>(_ call: @escaping () async throws -> Value) async -> Guarded<Value> {
        await guardedCall(timeoutSeconds: timeoutSeconds, call)
    }
}
