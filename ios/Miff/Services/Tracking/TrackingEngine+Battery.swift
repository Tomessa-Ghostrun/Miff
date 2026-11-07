import Foundation

// SÉCURITÉ ET CONFIDENTIALITÉ, pour ce fichier : le rafraîchissement de la batterie à l'ouverture et à la sortie de l'app (décision du PM,
// 2026-11-07). Voir l'en-tête de `TrackingEngine.swift`.
// - N'a lieu que dans `watch` : connecté, appareil actif, « Toujours » + position exacte, au moins une Miff où la personne partage sous le
//   consentement de version 3 (copie locale relue au plus toutes les heures ; le serveur refuse de toute façon une écriture sans partage
//   actif). Une écriture sans l'identifiant de la session confirmée de ce téléphone ne part jamais ; une session arrêtée ou remplacée
//   n'écrit rien.
// - UN update seul par Miff (`TrackingWrites.batteryUpdate` : `batteryPct`, `updatedAt`, `deviceId`). Il ne CRÉE jamais un statut (un update
//   sur un document absent échoue) et laisse tout le reste du statut tel que la base l'a. Aucune position n'est lue ni écrite ici.
// - Limites : 15 minutes entre deux essais, 5 points d'écart (sauf dernière écriture de plus de 3 h), disjoncteur dédié de 40 par jour
//   ET disjoncteur de statuts non épuisé. Un refus n'est pas rejoué avant 15 minutes.
// - Le journal de test ne reçoit que le TYPE (`batteryRefreshed` / `batteryRefreshSkipped`) et le niveau de batterie.

/// Ce qui est arrivé à une Miff pendant le rafraîchissement.
private enum BatteryOutcome {
    case written
    case skipped
    /// Une écriture a été tentée (réussie ou non) : la Miff est « servie » pour la rotation.
    case attempted
}

extension TrackingEngine {
    /// Réécrit la batterie dans chaque Miff de la copie locale qui en a besoin. Les Miffs sont servies à tour de rôle (le disjoncteur ne
    /// ménage pas toujours les mêmes).
    func refreshBattery(_ start: LocalState, pass: PassContext) async -> LocalState {
        var state = start
        var served: String?
        var written = false
        var skipped = false
        for miffId in rotated(Array(start.miffs.keys), after: start.batteryCursor) {
            if pass.permission.ended() {
                break
            }
            switch await refreshBatteryOne(miffId, state: &state, pass: pass) {
            case .written:
                written = true
                served = miffId
            case .attempted:
                served = miffId
            case .skipped:
                skipped = true
            }
        }
        if skipped && !written {
            journal.record(.batteryRefreshSkipped)
        }
        state.batteryCursor = served ?? state.batteryCursor
        return state
    }

    private func refreshBatteryOne(
        _ miffId: String,
        state: inout LocalState,
        pass: PassContext
    ) async -> BatteryOutcome {
        // Un statut qui attend d'être écrit passe d'abord : ses proches ne doivent pas voir une heure récente sur un état périmé.
        guard let miff = state.miffs[miffId], miff.pending == nil, let level = pass.batteryPct else {
            return .skipped
        }
        let now = env.nowMillis()
        guard BatteryRefreshPolicy.decide(history: miff.batteryHistory, current: level, nowMillis: now) == .write else {
            return .skipped
        }
        let day = WriteBreaker.dayKey(millis: now)
        let canWrite = WriteBreaker.canWriteBattery(state.breaker, dayKey: day)
            && WriteBreaker.canWriteStatus(state.breaker, dayKey: day)
        guard canWrite else {
            journal.record(.breakerStatus)
            return .skipped
        }
        // SESS-3 : sans l'identifiant de la session, rien n'est écrit (remplacé, ou le serveur n'a pas pu être interrogé).
        guard env.isOnline(), let deviceId = await pass.permission.deviceId(), !pass.permission.ended() else {
            return .skipped
        }
        let built = TrackingWrites.batteryUpdate(miffId: miffId, uid: pass.uid, batteryPct: level, deviceId: deviceId)
        guard let write = built else {
            return .skipped
        }
        switch await commit(write) {
        case .value:
            journal.record(.batteryRefreshed, batteryPct: level)
            state.breaker = WriteBreaker.recordBattery(state.breaker, dayKey: day)
            state.update(miffId) { $0.noteBatteryWritten(level, at: now) }
            state.batteryCursor = miffId
            // Sauvé TOUT DE SUITE : si le temps d'arrière-plan s'arrête avant la fin du passage (sortie de l'app), l'historique, le
            // compteur et le curseur de cette Miff sont déjà gardés et la sortie suivante ne réécrit pas. Pas après un arrêt de session
            // (la copie locale a été effacée : rien n'est reconstruit).
            if !pass.permission.ended() {
                deps.local.save(state)
            }
            return .written
        case .failed(let failure, _):
            return await settleBatteryFailure(failure, miffId: miffId, state: &state, pass: pass, now: now)
        }
    }

    private func settleBatteryFailure(
        _ failure: MiffFailure,
        miffId: String,
        state: inout LocalState,
        pass: PassContext,
        now: Int
    ) async -> BatteryOutcome {
        if failure == .offline {
            // Rien n'est parti : la prochaine occasion réessaiera.
            return .skipped
        }
        // Refusé (partage arrêté, consentement, horloge), ou statut absent : pas de boucle, la Miff est regardée de nouveau bientôt ; et si
        // c'était un remplacement de la session, plus rien n'est écrit pendant ce passage.
        journal.record(.batteryRefreshSkipped)
        state.update(miffId) { $0.batteryTriedAtMillis = now }
        if failure == .refused {
            await pass.permission.refused()
            state.recheckSoon(now: now)
        }
        return .attempted
    }
}
