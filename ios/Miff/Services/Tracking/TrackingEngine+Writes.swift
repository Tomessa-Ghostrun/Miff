import Foundation

// SÉCURITÉ ET CONFIDENTIALITÉ, pour ce fichier : c'est ici que partent les écritures de la détection. Voir l'en-tête de
// `TrackingEngine.swift`. Rien n'est écrit sans l'identifiant de la session confirmée de ce téléphone ; chaque écriture est UN
// document seul (jamais mêlé à un lieu ni à une autre écriture) ; la dernière position n'est écrite que dans les Miffs de la copie
// locale (partage actif ET consentement de version 3), avec le garde-fou de fréquence et le disjoncteur. Aucune coordonnée dans un
// statut. Une écriture refusée n'est pas rejouée : la Miff est regardée de nouveau plus tard.

/// Une position à offrir à chaque Miff : la position, l'heure de la MESURE déjà passée par `EventTime.forWrite`, et l'heure de l'offre.
struct PositionOffer {
    let fix: Fix
    let measuredAt: Int
    let now: Int
}

extension LocalState {
    /// Après un refus du serveur, la Miff est regardée de nouveau bientôt (pas à chaque réveil : l'horloge d'un téléphone en avance
    /// bouclerait dessus).
    mutating func recheckSoon(now: Int) {
        checkedAtMillis = now - TrackingLimits.placesRereadMillis + TrackingLimits.recheckAfterRefusalMillis
    }
}

extension TrackingEngine {
    /// Les Miffs dans l'ordre de CE passage : il commence APRÈS celle qui a été servie en dernier (revue I5), pour qu'un disjoncteur ne
    /// ménage pas toujours les mêmes.
    func rotated(_ miffIds: [String], after cursor: String?) -> [String] {
        let sorted = miffIds.sorted()
        guard let cursor else {
            return sorted
        }
        return sorted.filter { $0 > cursor } + sorted.filter { $0 <= cursor }
    }

    /// Une écriture, SEULE, dans UNE transaction : « pas de réponse » veut dire « rien d'écrit ».
    private func commit(_ write: DocWrite) async -> Guarded<Void> {
        let store = deps.store
        return await guardedCall(timeoutSeconds: deps.timeoutSeconds) {
            try await store.commit([write])
        }
    }

    // MARK: - Les statuts en attente

    /// Écrit ce qui attend, une écriture indépendante par Miff (un refus dans une Miff n'arrête jamais les autres), les Miffs servies
    /// à tour de rôle.
    func flushPending(_ start: LocalState, pass: PassContext) async -> LocalState {
        var state = start
        var served: String?
        for miffId in rotated(Array(start.miffs.keys), after: start.statusCursor) {
            if pass.permission.ended() {
                break
            }
            if await flushOne(miffId, state: &state, pass: pass) {
                served = miffId
            }
        }
        state.statusCursor = served ?? state.statusCursor
        return state
    }

    /// Écrit le statut en attente d'UNE Miff. Vrai quand une écriture a été tentée (la Miff est « servie »).
    private func flushOne(_ miffId: String, state: inout LocalState, pass: PassContext) async -> Bool {
        guard let pending = state.miffs[miffId]?.pending else {
            return false
        }
        let now = env.nowMillis()
        guard let fact = pending.fact, let at = EventTime.forWrite(eventMillis: pending.atMillis, nowMillis: now) else {
            giveUp(miffId, pending, state: &state)
            return false
        }
        let day = WriteBreaker.dayKey(millis: now)
        guard WriteBreaker.canWriteStatus(state.breaker, dayKey: day) else {
            // Le disjoncteur garde le DERNIER état en attente ; il part après minuit s'il est encore récent. D'ici là les proches voient
            // l'état d'avant, sans signe qu'il est ancien : une limite à décider par le PM (README, PR).
            journal.record(.breakerStatus, placeId: pending.placeId)
            return false
        }
        // SESS-3 : pas d'identifiant de session = rien n'est écrit (remplacé, ou le serveur n'a pas pu être interrogé) : le statut attend.
        guard env.isOnline(), let deviceId = await pass.permission.deviceId(), !pass.permission.ended() else {
            journal.record(.statusWaiting, placeId: pending.placeId)
            return false
        }
        // R-LIEU-1 (déployée) : la mise à jour laisse `presenceAt` / `lastUpdateAt` tranquilles sauf si l'événement les change ; un
        // refus n'est donc jamais « réglé » en écrivant l'heure de l'événement dans `lastUpdateAt`.
        let payload = StatusPayload(fact: fact, presenceAtMillis: at, batteryPct: pass.batteryPct, access: pass.access)
        let built = TrackingWrites.statusUpdate(miffId: miffId, uid: pass.uid, payload: payload, deviceId: deviceId)
        guard let write = built else {
            giveUp(miffId, pending, state: &state)
            return false
        }
        let outcome = await commit(write)
        await settleStatus(outcome, miffId: miffId, pending: pending, state: &state, pass: pass)
        return true
    }

    private func giveUp(_ miffId: String, _ pending: PendingStatus, state: inout LocalState) {
        journal.record(.statusGivenUp, placeId: pending.placeId)
        state.update(miffId) { $0.pending = nil }
    }

    private func settleStatus(
        _ outcome: Guarded<Void>,
        miffId: String,
        pending: PendingStatus,
        state: inout LocalState,
        pass: PassContext
    ) async {
        let now = env.nowMillis()
        switch outcome {
        case .value:
            journal.record(.statusWritten, placeId: pending.placeId, batteryPct: pass.batteryPct)
            state.breaker = WriteBreaker.recordStatus(state.breaker, dayKey: WriteBreaker.dayKey(millis: now))
            state.update(miffId) {
                $0.pending = nil
                $0.access = pass.access.rawValue
            }
        case .failed(let failure, _):
            switch failure {
            case .offline:
                journal.record(.statusWaiting, placeId: pending.placeId)
            case .refused:
                // Plus de partage, ou pas de consentement de la bonne version, ou une horloge trop en avance, d'après le serveur : la
                // Miff est regardée de nouveau bientôt. Et si c'était un remplacement de la session, plus rien n'est écrit.
                await pass.permission.refused()
                journal.record(.statusRefused, placeId: pending.placeId)
                state.recheckSoon(now: now)
                state.update(miffId) { $0.pending = nil }
            case .noProfile, .unknown:
                giveUp(miffId, pending, state: &state)
            }
        }
    }

    // MARK: - L'accès à la localisation

    /// L'accès à la localisation a changé (l'autorisation retirée, le service coupé) : écrit UNE fois par changement, avec la batterie ;
    /// jamais la présence (ticket §6.7). La mise à jour n'envoie ni `presenceAt` ni `lastUpdateAt` : elle passe (règle R-LIEU-1) même
    /// pour quelqu'un resté plus de 24 h au même endroit. Quelle que soit la réponse, elle n'est pas rejouée avant que l'accès change de
    /// nouveau (pas de boucle).
    func syncAccess(_ start: LocalState, pass: PassContext, now: Int) async -> LocalState {
        var state = start
        let access = pass.access
        for miffId in rotated(Array(start.miffs.keys), after: start.statusCursor) {
            guard let known = state.miffs[miffId]?.access else {
                state.update(miffId) { $0.access = access.rawValue }
                continue
            }
            if known == access.rawValue {
                continue
            }
            if pass.permission.ended() {
                break
            }
            await writeAccess(miffId, access: access, state: &state, pass: pass, now: now)
        }
        return state
    }

    private func writeAccess(
        _ miffId: String,
        access: LocationAccess,
        state: inout LocalState,
        pass: PassContext,
        now: Int
    ) async {
        let day = WriteBreaker.dayKey(millis: now)
        guard WriteBreaker.canWriteStatus(state.breaker, dayKey: day), env.isOnline() else {
            return
        }
        // SESS-3 : sans l'identifiant de la session rien n'est écrit, et le changement reste à dire (l'accès n'est pas marqué fait).
        guard let deviceId = await pass.permission.deviceId(), !pass.permission.ended(),
              let write = TrackingWrites.accessUpdate(
                  miffId: miffId,
                  uid: pass.uid,
                  batteryPct: pass.batteryPct,
                  access: access,
                  deviceId: deviceId
              ) else {
            return
        }
        let outcome = await commit(write)
        if case .failed(let failure, _) = outcome {
            if failure == .refused {
                await pass.permission.refused()
            }
        } else {
            state.breaker = WriteBreaker.recordStatus(state.breaker, dayKey: day)
        }
        state.update(miffId) { $0.access = access.rawValue }
    }

    // MARK: - La dernière position connue

    /// La DERNIÈRE position connue, à chaque Miff qui la mérite (partage + consentement de version 3 : toutes les Miffs de la copie
    /// locale), une écriture par Miff, SEULE (jamais avec un lieu ni un autre document), avec le garde-fou de fréquence et le
    /// disjoncteur (POS8). Aucun historique : elle remplace la précédente. Les Miffs sont servies à tour de rôle (revue I5).
    func publishPosition(_ start: LocalState, fix: Fix?, pass: PassContext, now: Int) async -> LocalState {
        guard let fix, let measuredAt = EventTime.forWrite(eventMillis: fix.measuredAtMillis, nowMillis: now) else {
            return start
        }
        let offer = PositionOffer(fix: fix, measuredAt: measuredAt, now: now)
        var state = start
        var served: String?
        for miffId in rotated(Array(start.miffs.keys), after: start.positionCursor) {
            if pass.permission.ended() {
                break
            }
            if await publishOne(miffId, offer: offer, state: &state, pass: pass) {
                served = miffId
            }
        }
        state.positionCursor = served ?? state.positionCursor
        return state
    }

    /// Vrai quand une écriture a été tentée pour cette Miff.
    private func publishOne(
        _ miffId: String,
        offer: PositionOffer,
        state: inout LocalState,
        pass: PassContext
    ) async -> Bool {
        guard let miff = state.miffs[miffId] else {
            return false
        }
        let last = miff.written
        if let last, offer.measuredAt <= last.atMillis {
            return false
        }
        let throttled = !PositionThrottle.shouldWrite(
            last: last,
            lat: offer.fix.lat,
            lng: offer.fix.lng,
            nowMillis: offer.measuredAt
        )
        if throttled {
            journal.record(.positionSkippedGuard)
            return false
        }
        let day = WriteBreaker.dayKey(millis: offer.now)
        guard WriteBreaker.canWritePosition(state.breaker, dayKey: day) else {
            journal.record(.breakerPosition)
            return false
        }
        // SESS-3 : une position ne part jamais d'un téléphone qui ne tient pas la session (elle dirait aux proches où est l'ANCIEN).
        guard env.isOnline(), let deviceId = await pass.permission.deviceId(), !pass.permission.ended() else {
            return false
        }
        let point = WrittenPosition(lat: offer.fix.lat, lng: offer.fix.lng, atMillis: offer.measuredAt)
        let built = TrackingWrites.position(miffId: miffId, uid: pass.uid, point: point, deviceId: deviceId)
        guard let write = built else {
            return false
        }
        let outcome = await commit(write)
        switch outcome {
        case .value:
            journal.record(.positionWritten, batteryPct: pass.batteryPct)
            state.breaker = WriteBreaker.recordPosition(state.breaker, dayKey: day)
            state.update(miffId) { $0.written = point }
        case .failed(let failure, _):
            // Refusée : le consentement ou le partage n'est pas ce que le téléphone croyait (ou l'horloge est trop en avance) : la Miff
            // est regardée de nouveau bientôt.
            if failure == .refused {
                await pass.permission.refused()
                state.recheckSoon(now: offer.now)
            }
        }
        return true
    }
}
