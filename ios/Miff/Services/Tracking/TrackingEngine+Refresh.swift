import Foundation

// SÉCURITÉ ET CONFIDENTIALITÉ, pour ce fichier : le grand recalcul (ouverture, passage en arrière-plan, déplacement significatif, entrée
// dans une zone) et la lecture des Miffs et des lieux. Voir l'en-tête de `TrackingEngine.swift`. Une position lue ici est gardée dans la
// copie locale privée et utilisée pour choisir les zones ; elle n'est ni journalisée ni envoyée ailleurs que comme dernière position
// (voir `TrackingEngine+Writes.swift`). Une clé de zone contient des coordonnées : elle n'est jamais journalisée (seul `placeId` l'est).

/// Le résultat d'une relecture du serveur : la nouvelle copie locale, et si la relecture a été COMPLÈTE (chaque réponse a été lue ; une
/// coupure réseau la rend incomplète et rien n'en est conclu).
struct SyncResult {
    var state: LocalState
    var verified: Bool
}

private enum MiffSync {
    case watched(LocalMiff, complete: Bool)
    /// Pas (ou plus) éligible : pas de partage, pas de consentement de version 3, ou accès perdu.
    case dropped
    /// Le serveur n'a pas répondu : ce que le téléphone savait est gardé (une coupure réseau n'est jamais « retiré »).
    case unreachable(LocalMiff?)
}

/// Quand les lieux sont relus : l'heure de la relecture, et si elle est forcée (le partage ou un lieu vient de changer).
private struct PlacesReading {
    let now: Int
    let force: Bool
}

private struct PlacesRead {
    let miff: LocalMiff
    let complete: Bool
}

extension TrackingEngine {
    func doRefresh(_ reason: Reason, supplied: Fix?) async {
        guard let uid = env.uid(), !deps.device.isRevoked else {
            // Personne n'est connecté, ou la session a été remplacée : ce qui est encore armé est orphelin (processus tué, déconnexion
            // interrompue) et est retiré.
            await doStop()
            return
        }
        let now = env.nowMillis()
        var state = stateFor(uid) ?? LocalState(uid: uid)
        if state.travel != nil {
            // MODE VOYAGE : le statut reste un voyage, la position continue d'être publiée (`TrackingEngine+Travel.swift`).
            await doTravelPass(uid, state, reason, supplied: supplied)
            return
        }
        // Un NOUVEL appareil pour ce compte (aucune relecture vérifiée du serveur jusque-là) lit d'abord s'il y a un voyage, avant d'armer.
        let newPhone = state.checkedAtMillis == 0
        if reason == .rearm {
            journal.record(.wakeRearm)
            state.armed = []
            state.updatesArmed = false
        }
        let pass = await makePass(uid: uid)
        await noteSystemConditions(reason)
        let synced = await syncMiffs(uid, state, reason, now)
        state = synced.state
        if newPhone, synced.verified, !state.miffs.isEmpty,
           await settleNewPhone(uid, state, reason, supplied: supplied) {
            return
        }
        guard !state.miffs.isEmpty else {
            await standDown(uid, state)
            return
        }
        guard pass.snapshot.locationComplete else {
            // « Toujours » + la position exacte ne sont pas là : ni détection ni position (AUT7). Les Miffs sont prévenues, une fois,
            // que l'accès a changé.
            state = await disarm(state)
            state = await syncAccess(state, pass: pass, now: now)
            deps.local.save(state)
            return
        }
        await watch(state, pass: pass, reason: reason, supplied: supplied, now: now)
    }

    /// Personne à prévenir : rien n'est surveillé, rien n'est gardé (la personne ne partage nulle part avec le consentement v3).
    private func standDown(_ uid: String, _ state: LocalState) async {
        journal.record(.notEligible)
        await deps.geofences.disarmAll()
        await stopMovement()
        deps.local.save(LocalState(uid: uid, checkedAtMillis: state.checkedAtMillis))
    }

    private func noteSystemConditions(_ reason: Reason) async {
        guard reason == .opened else {
            return
        }
        let conditions = await env.systemConditions()
        if conditions.backgroundRefreshOff {
            journal.record(.backgroundRefreshOff)
        }
        if conditions.lowPowerMode {
            journal.record(.lowPowerMode)
        }
    }

    /// Ce qui se fait quand tout est en règle : armer, lire la position, écrire ce qui attend.
    private func watch(_ start: LocalState, pass: PassContext, reason: Reason, supplied: Fix?, now: Int) async {
        var state = start
        // Tout réenregistrer (et redemander l'état de chaque zone) est réservé aux cas où le système a pu perdre ou changer ses
        // régions : échec de la surveillance, autorisation changée. Aux autres occasions, seul ce qui manque est enregistré.
        let full: [Reason] = [.rearm, .accessChanged]
        // 1. Les zones sont armées D'ABORD, sans attendre une position : un démarrage lent ne doit pas coûter l'armement (une fenêtre de
        //    relance a quelques secondes). Le système fait foi de ce qu'il surveille : ce qui manque est ajouté, le reste retiré.
        state = await armZones(state, fix: state.fix, reregister: full.contains(reason))
        deps.local.save(state)
        // 2. La position (attente bornée), seulement si elle est récente.
        let fix = await chooseFix(state, supplied: supplied, reason: reason, now: now)
        if let fix {
            state.fix = fix
            journal.record(.positionRead, batteryPct: pass.batteryPct)
        }
        state = reconcilePlaces(state, now: now)
        if reason == .opened {
            await recheckStates(state, fix: fix)
        }
        // Au-delà de 20 zones, le choix est refait avec la position fraîche.
        if let fix, allZones(state).count > ZoneSelection.maxWatched {
            state = await armZones(state, fix: fix, reregister: false)
        }
        deps.local.save(state)
        state = await flushPending(state, pass: pass)
        state = await publishPosition(state, fix: fix, pass: pass, now: now)
        state = await syncAccess(state, pass: pass, now: now)
        // Batterie rafraîchie à l'ouverture et à la sortie, EN DERNIER : ce qui précède (arrivée, départ, position, accès) passe avant et
        // porte déjà la batterie fraîche. Rien d'autre que la batterie ; aucune position.
        if reason == .opened || reason == .backgrounded {
            state = await refreshBattery(state, pass: pass)
        }
        deps.local.save(state)
    }

    /// À l'ouverture : demande au système de redire « dedans / dehors » pour les zones où l'on croit être alors que la position dit
    /// le contraire (sortie manquée) et pour celles qui contiennent la position sans que l'on s'y croie (arrivée manquée). En
    /// général aucune : on ne redemande pas les 20 zones à chaque ouverture.
    private func recheckStates(_ state: LocalState, fix: Fix?) async {
        var keys = Set<String>()
        for zone in allZones(state) where state.armed.contains(zone.key) {
            let believed = state.inside[zone.key] != nil
            guard let fix else {
                if believed {
                    keys.insert(zone.key)
                }
                continue
            }
            if ZoneSelection.contains(zone, lat: fix.lat, lng: fix.lng) != believed {
                keys.insert(zone.key)
            }
        }
        if !keys.isEmpty {
            await deps.geofences.requestStates(keys: keys.sorted())
        }
    }

    // MARK: - La position

    private func usable(_ fix: Fix, _ now: Int) -> Bool {
        now - fix.measuredAtMillis <= TrackingLimits.fixMaxAgeMillis
            && fix.measuredAtMillis - now <= TrackingLimits.clockSkewMillis
    }

    /// BATT-3 : seules l'ouverture de l'app et sa sortie peuvent réutiliser la dernière position du système (moins de deux minutes).
    /// JAMAIS l'entrée dans une zone ni le réarmement, le déplacement, le retour du réseau, un changement de partage ou d'accès : la
    /// personne vient de bouger, ou c'est le moment de vérifier, et une position de deux minutes serait fausse.
    static func mayReuseSystemFix(for reason: Reason) -> Bool {
        switch reason {
        case .opened, .backgrounded:
            return true
        case .moved, .zoneEntered, .rearm, .sharingChanged, .networkBack, .accessChanged:
            return false
        }
    }

    /// Une position pour cette occasion : celle que le système vient de signaler, sinon une position fraîche (quelques secondes au
    /// plus), sinon la dernière gardée. AUCUNE de plus de dix minutes n'est utilisée, pour aucune occasion : une vieille position
    /// écrirait une fausse arrivée ou une fausse position.
    func chooseFix(_ state: LocalState, supplied: Fix?, reason: Reason, now: Int) async -> Fix? {
        if let supplied, usable(supplied, now) {
            return supplied
        }
        let position = deps.position
        let mayReuse = Self.mayReuseSystemFix(for: reason)
        let wait = TrackingLimits.positionWaitSeconds
        let reading: PositionReading? = try? await timedOperation(seconds: wait) { () async -> PositionReading in
            if mayReuse {
                return await position.currentReusingRecent()
            }
            return PositionReading(fix: await position.current(), reused: false)
        }
        if let fresh = reading?.fix, usable(fresh, now) {
            if reading?.reused == true {
                // Une demande au système évitée (BATT-3) : le journal de test le compte, pour la mesure du PM.
                journal.record(.positionReused)
            }
            return fresh
        }
        journal.record(.positionUnavailable)
        if let kept = state.fix, usable(kept, now) {
            return kept
        }
        return nil
    }

    // MARK: - Quelles Miffs, quels lieux

    private func isReadDue(_ state: LocalState, _ reason: Reason, _ now: Int) -> Bool {
        switch reason {
        case .sharingChanged, .rearm, .networkBack, .accessChanged:
            return true
        case .opened, .backgrounded, .moved, .zoneEntered:
            break
        }
        if state.checkedAtMillis == 0 || now - state.checkedAtMillis > TrackingLimits.placesRereadMillis {
            return true
        }
        return reason == .opened && state.miffs.isEmpty
            && now - state.checkedAtMillis > TrackingLimits.retryWhenEmptyMillis
    }

    /// Le serveur est relu (les Miffs de la personne, mon consentement dans chacune, les lieux de chaque Miff éligible) quand c'est
    /// l'heure : un changement de partage, le retour du réseau, ou la copie locale de plus d'une heure. C'est le choix fait pour le cas
    /// « partage arrêté ailleurs » (revue I3) : entre deux relectures, une écriture que le serveur refuse (partage arrêté, texte qui
    /// n'est pas de version 3) est le signal, et la Miff est regardée de nouveau plus tôt.
    func syncMiffs(_ uid: String, _ start: LocalState, _ reason: Reason, _ now: Int) async -> SyncResult {
        guard isReadDue(start, reason, now), env.isOnline() else {
            return SyncResult(state: start, verified: false)
        }
        let store = deps.store
        let found = await guardedCall(timeoutSeconds: deps.timeoutSeconds) {
            try await store.memberships(uid: uid)
        }
        guard case .value(let refs) = found else {
            // Pas de réponse : rien n'est conclu, ce que le téléphone savait reste.
            return SyncResult(state: start, verified: false)
        }
        var seen = Set<String>()
        let miffIds = refs.map { $0.miffId }.filter { seen.insert($0).inserted }
        // Un partage ou un lieu qui change (le créateur vient d'enregistrer, de déplacer ou de supprimer un lieu) : les lieux sont
        // relus tout de suite, pas dans l'heure. Aucune boucle : seul un geste de la personne (ou un échec du système) donne ce motif,
        // jamais le moteur lui-même. Coût : un document par lieu et par Miff, plus 2 lectures de règles chacun.
        let forcePlaces = reason == .sharingChanged || reason == .rearm
        var miffs: [String: LocalMiff] = [:]
        var complete = true
        for miffId in miffIds {
            let reading = PlacesReading(now: now, force: forcePlaces)
            switch await syncOne(miffId, uid: uid, previous: start.miffs[miffId], read: reading) {
            case .watched(let miff, let isComplete):
                miffs[miffId] = miff
                complete = complete && isComplete
            case .dropped:
                break
            case .unreachable(let kept):
                complete = false
                miffs[miffId] = kept
            }
        }
        if !Set(start.miffs.keys).subtracting(miffs.keys).isEmpty {
            journal.record(.notEligible)
        }
        var next = start
        next.miffs = miffs
        if complete {
            next.checkedAtMillis = now
        }
        return SyncResult(state: next, verified: complete)
    }

    private func syncOne(
        _ miffId: String,
        uid: String,
        previous: LocalMiff?,
        read reading: PlacesReading
    ) async -> MiffSync {
        let store = deps.store
        let consent = await guardedCall(timeoutSeconds: deps.timeoutSeconds) {
            try await store.consent(miffId: miffId, uid: uid)
        }
        switch consent {
        case .value(let card):
            guard let card, card.enabled, MiffSharingConsent.coversPosition(card.version) else {
                return .dropped
            }
            let read = await readPlaces(miffId: miffId, previous: previous, reading: reading)
            return .watched(read.miff, complete: read.complete)
        case .failed(_, let lostAccess):
            // Le serveur a refusé : la personne n'est plus membre (retirée, Miff supprimée) ; sinon c'est une coupure.
            return lostAccess ? .dropped : .unreachable(previous)
        }
    }

    /// Les lieux d'une Miff, relus quand la copie a plus d'une heure.
    private func readPlaces(miffId: String, previous: LocalMiff?, reading: PlacesReading) async -> PlacesRead {
        let now = reading.now
        let base = previous ?? LocalMiff()
        let age = now - base.readAtMillis
        let stale = previous == nil || base.readAtMillis == 0 || age > TrackingLimits.placesRereadMillis
        let needed = reading.force || stale
        if !needed {
            return PlacesRead(miff: base, complete: true)
        }
        let store = deps.store
        let read = await guardedCall(timeoutSeconds: deps.timeoutSeconds) {
            try await store.places(miffId: miffId)
        }
        guard case .value(let facts) = read else {
            return PlacesRead(miff: base, complete: false)
        }
        return PlacesRead(miff: refreshed(base, with: facts, now: now), complete: true)
    }

    /// Le lieu où la personne était nommée, déplacé ou redimensionné (ou dont le type a changé) par son créateur : la présence ne
    /// peut plus être confirmée. Un lieu déplacé est une AUTRE zone (sa clé porte sa géométrie) : l'ancienne disparaît, la nouvelle
    /// est armée, et arrive comme une nouvelle arrivée si la personne est dedans.
    private func refreshed(_ base: LocalMiff, with facts: [PlaceFact], now: Int) -> LocalMiff {
        var oldById: [String: PlaceFact] = [:]
        for place in base.places {
            if let fact = place.fact {
                oldById[fact.id] = fact
            }
        }
        let changed = facts.filter { fresh in
            guard let old = oldById[fresh.id] else {
                return false
            }
            return !old.sameGeometry(fresh)
        }
        let moved = Set(changed.map { $0.id })
        var updated = base
        updated.places = facts.map { LocalPlace(fact: $0) }
        updated.readAtMillis = now
        if let named = base.derived?.placeId, moved.contains(named) {
            journal.record(.presenceUnknown, placeId: named)
            updated.derived = DerivedPresence(presence: .unknown, placeId: nil)
            updated.pending = PendingStatus(fact: PresenceMachine.unknown(atMillis: now))
        }
        return updated
    }

    // MARK: - Les zones

    func zonesOf(_ state: LocalState, _ miffId: String) -> [ZoneRef] {
        let facts = (state.miffs[miffId]?.places ?? []).compactMap { $0.fact }
        return facts.map { ZoneRef(key: $0.key, placeId: $0.id, type: $0.type) }
    }

    func allZones(_ state: LocalState) -> [TrackedZone] {
        var items: [ZoneSelection.MiffPlace] = []
        for (miffId, miff) in state.miffs {
            for place in miff.places {
                if let fact = place.fact {
                    items.append(ZoneSelection.MiffPlace(miffId: miffId, place: fact))
                }
            }
        }
        return ZoneSelection.merge(items)
    }

    /// Un lieu supprimé ou modifié pendant que la personne y était : la présence qui a été écrite n'est peut-être plus vraie, et rien
    /// ne dit ce qu'elle est. Elle devient `unknown` (jamais une supposition, ticket §6.6). Aucun `out` n'est inventé ici.
    func reconcilePlaces(_ state: LocalState, now: Int) -> LocalState {
        var next = state
        let known = Set(allZones(state).map { $0.key })
        next.inside = state.inside.filter { known.contains($0.key) }
        for (miffId, miff) in state.miffs {
            guard let last = miff.derived, last.presence == .inTrusted || last.presence == .inRisk,
                  let named = last.placeId else {
                continue
            }
            if zonesOf(state, miffId).contains(where: { $0.placeId == named }) {
                continue
            }
            journal.record(.presenceUnknown, placeId: named)
            next.update(miffId) {
                $0.derived = DerivedPresence(presence: .unknown, placeId: nil)
                $0.pending = PendingStatus(fact: PresenceMachine.unknown(atMillis: now))
            }
        }
        return next
    }

    /// Enregistre les zones auprès du système (20 au plus). Avec plus de 20 zones, la position choisit : celle qu'on a, même ancienne
    /// (elle ne fait qu'ORDONNER les zones ; une position fraîche corrigera le choix ensuite), vaut mieux que rien.
    func armZones(_ state: LocalState, fix: Fix?, reregister: Bool) async -> LocalState {
        var next = state
        let zones = allZones(state)
        let desired: [TrackedZone]
        if zones.count <= ZoneSelection.maxWatched {
            desired = zones
        } else if let fix {
            desired = ZoneSelection.select(
                lat: fix.lat,
                lng: fix.lng,
                zones: zones,
                insideKeys: Set(state.inside.keys)
            )
        } else {
            desired = Array(zones.prefix(ZoneSelection.maxWatched))
        }
        let desiredKeys = Set(desired.map { $0.key })
        // Appelé à chaque fois : le système fait foi de ce qu'il surveille (ce qui manque est ajouté, le reste retiré), sans rien
        // redemander pour les zones déjà surveillées. Le journal ne note que les vrais changements.
        if await deps.geofences.arm(desired: desired, reregister: reregister) {
            if reregister || desiredKeys != state.armed {
                journal.record(.armed)
            }
            next.armed = desiredKeys
        }
        // Une fois par PROCESSUS au moins : un processus relancé par le système doit redemander les changements significatifs.
        if reregister || !next.updatesArmed || !isMovementStarted() {
            let started = await deps.movement.start()
            next.updatesArmed = started
            setMovementStarted(started)
        }
        return next
    }
}
