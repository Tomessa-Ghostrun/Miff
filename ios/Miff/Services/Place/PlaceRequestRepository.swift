import Foundation

// SÉCURITÉ, pour ce fichier : les demandes (nom, adresse, coordonnées) sont sensibles. Elles ne sont jamais journalisées, jamais dans
// un événement Analytics, jamais dans un message d'erreur. Chaque échec sort d'ici sous la forme d'une `MiffFailure` (sans message,
// sans chemin).

/// Les emplacements confiés à la file du téléphone pendant que la personne est hors ligne (clé `miffId|requestId`, heure de la remise) :
/// une SECONDE demande faite hors ligne juste après ne doit pas prendre le même emplacement (la première serait écrasée par une écriture
/// refusée et perdue sans un mot). Une marque vit 10 minutes, ou jusqu'à ce que le serveur montre la demande.
final class QueuedRequestMarks {
    static let lifetimeMillis = 10 * 60 * 1000

    private let lock = NSLock()
    private var marks: [String: Int] = [:]

    func set(_ key: String, atMillis: Int) {
        lock.lock()
        marks[key] = atMillis
        lock.unlock()
    }

    func remove(_ key: String) {
        lock.lock()
        marks[key] = nil
        lock.unlock()
    }

    /// Les identifiants de demande marqués pour `miffId` et `uid`, après avoir retiré les marques trop vieilles.
    func ids(miffId: String, uid: String, nowMillis: Int) -> Set<String> {
        lock.lock()
        defer { lock.unlock() }
        marks = marks.filter { nowMillis - $0.value <= Self.lifetimeMillis }
        let prefix = "\(miffId)|\(uid)_"
        return Set(marks.keys.filter { $0.hasPrefix(prefix) }.map { String($0.dropFirst(miffId.count + 1)) })
    }
}

/// Tout ce que font les écrans avec les demandes d'ajout d'un lieu auprès de Firestore (ticket LIEU-REQ ; miroir d'Android
/// `FirestorePlaceRequestRepository`) : les limites du membre décidées AVANT d'écrire, les lots (une demande + le compteur), et la
/// vérification auprès du serveur d'une écriture dont la réponse s'est perdue. Firestore est derrière `PlaceRequestStore` et `PlaceStore`.
final class FirestorePlaceRequestRepository: PlaceRequestRepository {
    /// Au plus une minute en tout pour reprendre ses demandes avant « Quitter », quoi que fasse le réseau : jamais une longue attente.
    static let leaveBudgetSeconds: TimeInterval = 60

    let store: PlaceRequestStore
    let places: PlaceStore
    /// Ce que l'acceptation lit pour appliquer les limites de la formule gratuite (la formule, les Miffs et les lieux de la personne).
    let placeRepository: PlaceRepository
    /// L'identifiant d'un lieu d'un créateur Miff+ (tiré sur le téléphone, comme pour une création).
    let newPlaceId: () -> String
    let nowMillis: () -> Int
    let timeoutSeconds: TimeInterval
    /// Un lieu vient d'être créé par l'acceptation d'une demande : le suivi des lieux relit les lieux (comme pour un lieu créé par le
    /// créateur lui-même, `ChangeNotifyingPlaceRepository`), pour que les zones se programment sans attendre l'heure.
    let onPlaceAdded: () -> Void
    /// Y a-t-il un réseau ? Quitter une Miff saute la reprise des demandes quand il n'y en a pas (le départ lui-même a besoin du réseau).
    private let online: () -> Bool
    private let queued = QueuedRequestMarks()

    init(
        store: PlaceRequestStore,
        places: PlaceStore,
        placeRepository: PlaceRepository,
        newPlaceId: @escaping () -> String,
        nowMillis: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) },
        timeoutSeconds: TimeInterval = 20,
        online: @escaping () -> Bool = { true },
        onPlaceAdded: @escaping () -> Void = {}
    ) {
        self.store = store
        self.places = places
        self.placeRepository = placeRepository
        self.newPlaceId = newPlaceId
        self.nowMillis = nowMillis
        self.timeoutSeconds = timeoutSeconds
        self.online = online
        self.onPlaceAdded = onPlaceAdded
    }

    // MARK: - Lire

    func prepare(uid: String, miffId: String) async -> RequestContextLoad {
        let places = self.places
        let store = self.store
        // Un abonnement inconnu n'est PAS « gratuit » pour la zone de danger : une lecture qui échoue dit « réessaie » au lieu de
        // fermer ou d'ouvrir sur une supposition.
        let premium: Bool
        switch await guarded({ try await places.readEntitlement(uid: uid) }) {
        case .value(let read):
            premium = isActive(read)
        case .failed(let failure, _):
            return .failed(failure)
        }
        let details: MiffDetails
        switch await guarded({ try await places.readMiff(miffId: miffId) }) {
        case .value(.exists(let found)):
            // Seul un membre qui N'EST PAS le créateur demande (le créateur ajoute lui-même ses lieux).
            if found.ownerUid == uid {
                return .unavailable
            }
            details = found
        case .value(.missing):
            return .unavailable
        case .failed(let failure, let lostAccess):
            return lostAccess ? .unavailable : .failed(failure)
        }
        let mine: [PlaceRequest]
        switch await guarded({ try await store.readMine(miffId: miffId, uid: uid, fromServer: true) }) {
        case .value(let found):
            mine = found
        case .failed(let failure, _):
            // Les règles refusent la lecture de `placeRequests` (pas déployées, ou la Miff a changé) : le formulaire s'OUVRE quand même.
            // Ses emplacements ne servent qu'à ENVOYER ; l'envoi dit calmement que les demandes ne sont pas disponibles.
            if failure != .refused {
                return .failed(failure)
            }
            mine = []
        }
        for request in mine {
            queued.remove("\(miffId)|\(request.id)")
        }
        // Une demande expirée occupe son emplacement tant qu'elle n'est pas supprimée : son auteur la supprime ici, pour que l'emplacement
        // soit de nouveau libre (ticket 5.3).
        let now = nowMillis()
        var kept: [PlaceRequest] = mine.filter { !$0.isExpired(nowMillis: now) }
        for request in mine where request.isExpired(nowMillis: now) {
            if await withdraw(request) != .done {
                kept.append(request)
            }
        }
        return .loaded(RequestContext(miffName: details.name, premium: premium, mine: kept))
    }

    func readMine(uid: String, miffId: String) async -> MineRead {
        let store = self.store
        switch await guarded({ try await store.readMine(miffId: miffId, uid: uid, fromServer: false) }) {
        case .value(let found):
            let now = nowMillis()
            return .loaded(found.filter { !$0.isExpired(nowMillis: now) })
        case .failed(let failure, _):
            return .failed(failure)
        }
    }

    /// Miff+ = un abonnement actif qui n'a pas expiré. Aucun document = pas Miff+.
    func isActive(_ read: EntitlementRead?) -> Bool {
        guard let read, let expires = read.expiresAtMillis else {
            return false
        }
        return read.active && expires > nowMillis()
    }

    // MARK: - Envoyer

    func submit(
        uid: String,
        miffId: String,
        draft: PlaceRequestDraft,
        context: RequestContext,
        online: Bool
    ) async -> RequestSubmitResult {
        let store = self.store
        if draft.problem != nil {
            return .failed(.unknown)
        }
        // La zone de danger est Miff+ pour la personne qui demande (D5) : décidé AVANT d'envoyer, les règles ne font que le répéter.
        if draft.type == .risk && !context.premium {
            return .needsPremium(.danger)
        }
        // 3 demandes en attente au plus (M5) : aucune écriture n'est tentée à la 4e. Les emplacements dans la file du téléphone comptent.
        let waiting = queued.ids(miffId: miffId, uid: uid, nowMillis: nowMillis())
        let taken = Set(context.mine.map { $0.id }).union(waiting)
        let free = PlaceRequestLimits.slots.filter { !taken.contains(PlaceRequestLimits.idOf(uid: uid, slot: $0)) }
        guard taken.count < PlaceRequestLimits.perMember, let slot = free.first else {
            return .memberLimit
        }
        let lot = PlaceRequestWrites.create(uid: uid, miffId: miffId, slot: slot, draft: draft)
        let sent = sentRequest(uid: uid, miffId: miffId, slot: slot, draft: draft)
        if !online {
            store.enqueue(lot)
            queued.set("\(miffId)|\(sent.id)", atMillis: nowMillis())
            return .queued
        }
        switch await guarded({ try await store.commit(lot) }) {
        case .value:
            return .done(sent)
        case .failed(let failure, _):
            return await afterFailedSubmit(uid: uid, sent: sent, draft: draft, failure: failure)
        }
    }

    /// La demande telle que l'écran la montre dès que le serveur a accepté le lot (pas encore de date : elle est toute neuve).
    private func sentRequest(uid: String, miffId: String, slot: Int, draft: PlaceRequestDraft) -> PlaceRequest {
        PlaceRequest(
            id: PlaceRequestLimits.idOf(uid: uid, slot: slot),
            miffId: miffId,
            requestedBy: uid,
            name: draft.name.trimmingCharacters(in: .whitespacesAndNewlines),
            type: draft.type,
            address: draft.address,
            lat: draft.lat,
            lng: draft.lng,
            radiusM: draft.radiusM,
            category: draft.category,
            createdAtMillis: nil
        )
    }

    /// Une écriture non confirmée est regardée avant de dire à la personne qu'elle a échoué : (1) est-elle passée quand même (la réponse
    /// s'est perdue) ? alors c'est fait ; (2) la Miff a-t-elle disparu, ou n'est-elle plus à demander ? (3) un refus du serveur sur une
    /// Miff qui est toujours là : la Miff porte toutes les demandes qu'elle prend.
    private func afterFailedSubmit(
        uid: String,
        sent: PlaceRequest,
        draft: PlaceRequestDraft,
        failure: MiffFailure
    ) async -> RequestSubmitResult {
        let places = self.places
        let miffId = sent.miffId
        if failure == .offline || failure == .unknown {
            if let mine = await serverMine(miffId: miffId, uid: uid), mine.contains(where: { $0.id == sent.id }) {
                return .done(sent)
            }
        }
        if failure == .refused || failure == .unknown {
            let gone: Bool
            switch await guarded({ try await places.readMiff(miffId: miffId) }) {
            case .value(.exists(let details)):
                gone = details.ownerUid == uid
            case .value(.missing):
                gone = true
            case .failed(_, let lostAccess):
                gone = lostAccess
            }
            if gone {
                return .miffUnavailable
            }
        }
        if failure != .refused {
            return .failed(failure)
        }
        return await afterRefusedSubmit(uid: uid, sent: sent, draft: draft)
    }

    /// Un refus sur une Miff qui est toujours là (contrat du backend, section 4) : trois causes se distinguent, la dernière est la limite
    /// de la Miff.
    private func afterRefusedSubmit(
        uid: String,
        sent: PlaceRequest,
        draft: PlaceRequestDraft
    ) async -> RequestSubmitResult {
        let store = self.store
        let places = self.places
        let miffId = sent.miffId
        // (a) une zone de danger alors que la formule a pris fin depuis l'ouverture du formulaire : l'offre, rien d'autre à faire.
        if draft.type == .risk,
           case .value(let read) = await guarded({ try await places.readEntitlement(uid: uid) }),
           !isActive(read) {
            return .needsPremium(.danger)
        }
        // (b) une course sur l'emplacement (un autre téléphone l'a pris) : les demandes sont relues et, s'il en reste un de libre, le
        // lot repart une fois.
        let fresh: [PlaceRequest]
        switch await guarded({ try await store.readMine(miffId: miffId, uid: uid, fromServer: true) }) {
        case .value(let found):
            fresh = found
        case .failed(let failure, _):
            // Même la lecture des demandes de la personne est refusée : ce n'est pas la limite de la Miff, les demandes ne sont
            // simplement pas encore disponibles.
            return failure == .refused ? .requestsUnavailable : .miffLimit
        }
        if fresh.contains(where: { $0.id == sent.id }) {
            guard let slot = PlaceRequestSlots.firstFree(uid: uid, existing: fresh) else {
                return .memberLimit
            }
            let again = sentRequest(uid: uid, miffId: miffId, slot: slot, draft: draft)
            let lot = PlaceRequestWrites.create(uid: uid, miffId: miffId, slot: slot, draft: draft)
            if case .value = await guarded({ try await store.commit(lot) }) {
                return .done(again)
            }
            return .miffLimit
        }
        // (c) tous les emplacements étaient libres : la Miff porte toutes les demandes qu'elle prend (10), ou le compteur a dérivé trop haut.
        return .miffLimit
    }

    // MARK: - Reprendre

    func withdraw(_ request: PlaceRequest) async -> RequestWithdrawResult {
        let store = self.store
        let first = PlaceRequestWrites.withdraw(miffId: request.miffId, requestId: request.id)
        let failure: MiffFailure
        switch await guarded({ try await store.commit(first) }) {
        case .value:
            return .done
        case .failed(let found, _):
            failure = found
        }
        // Le compteur a été remis à 0 par un créateur alors que des demandes existent : -1 est hors bornes et les règles refusent. Encore
        // une fois avec la valeur absolue 0 (contrat 2.2).
        if failure == .refused {
            let fallback = PlaceRequestWrites.withdrawWithCounterAtZero(miffId: request.miffId, requestId: request.id)
            if case .value = await guarded({ try await store.commit(fallback) }) {
                return .done
            }
        }
        // Un lot dont la réponse s'est perdue, ou une demande déjà supprimée (par le créateur, sur un autre téléphone) : elle est partie,
        // ce qui est ce qui était demandé.
        if failure != .noProfile,
           let left = await serverMine(miffId: request.miffId, uid: request.requestedBy),
           !left.contains(where: { $0.id == request.id }) {
            return .done
        }
        return .failed(failure)
    }

    func withdrawAllMine(uid: String, miffId: String) async {
        // Pas de réseau : le départ échouera de lui-même ; on n'attend rien ici (le créateur masque et supprime ce qui reste).
        guard online() else {
            return
        }
        // Au plus une minute en tout, quoi que fasse le réseau : jamais une longue attente avant « Quitter ».
        _ = try? await timedOperation(seconds: Self.leaveBudgetSeconds) { [self] in
            await withdrawEvery(uid: uid, miffId: miffId)
        }
    }

    private func withdrawEvery(uid: String, miffId: String) async {
        guard let mine = await serverMine(miffId: miffId, uid: uid) else {
            return
        }
        for request in mine {
            _ = await withdraw(request)
        }
    }

    /// Les demandes de la personne telles que le SERVEUR les a, ou nil quand elles ne peuvent pas être lues.
    private func serverMine(miffId: String, uid: String) async -> [PlaceRequest]? {
        let store = self.store
        let read = await guarded({ try await store.readMine(miffId: miffId, uid: uid, fromServer: true) })
        if case .value(let found) = read {
            return found
        }
        return nil
    }

    func guarded<Value>(_ call: @escaping () async throws -> Value) async -> Guarded<Value> {
        await guardedCall(timeoutSeconds: timeoutSeconds, call)
    }
}
