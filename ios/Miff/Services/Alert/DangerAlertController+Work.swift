#if ALERT_DANGER
import Foundation

// Le TRAVAIL de MON alerte : la boucle d'envoi et la boucle de fin (voir `DangerAlertController.swift` pour ce que la classe garantit).
// Chaque pas qui attend (réseau, session, serveur) vérifie ensuite que la tâche n'a pas été annulée : une alerte abandonnée, ou une session
// terminée, n'est JAMAIS « activée » par la réponse tardive d'une écriture.

extension DangerAlertController {
    /// Ce que dit la session de cet appareil avant d'écrire : l'identifiant à écrire, ou la raison de ne rien écrire.
    private enum Clearance {
        case cleared(String)
        case replaced
        case unverified
    }

    private func clearance(uid: String) async -> Clearance {
        switch await device.clearance(uid: uid) {
        case .cleared(let deviceId):
            return .cleared(deviceId)
        case .replaced:
            return .replaced
        case .unverified:
            return .unverified
        }
    }

    /// Attend que le réseau soit là (le flux dit l'état tout de suite, puis chaque changement).
    private func waitForNetwork() async {
        for await online in network.onlineChanges() where online {
            return
        }
    }

    /// Une écriture bornée : pas de réponse à temps veut dire « rien n'est parti » (l'écriture est une transaction, qui n'existe qu'en ligne).
    private func startBounded(uid: String, miffId: String, deviceId: String, now: Int) async -> DangerStartResult {
        let store = self.store
        do {
            return try await timedOperation(seconds: sendTimeoutSeconds) {
                await store.start(uid: uid, miffId: miffId, deviceId: deviceId, nowMillis: now)
            }
        } catch {
            return .failed(.offline)
        }
    }

    private func endBounded(uid: String, miffId: String, deviceId: String, now: Int) async -> DangerEndResult {
        let store = self.store
        do {
            return try await timedOperation(seconds: sendTimeoutSeconds) {
                await store.end(uid: uid, miffId: miffId, deviceId: deviceId, nowMillis: now)
            }
        } catch {
            return .failed(.offline)
        }
    }

    // MARK: - L'envoi

    func sendLoop(miffId: String, armedAt: Int, confirmed: Bool) async {
        var attempt = 0
        while !Task.isCancelled {
            guard let uid = currentUid() else {
                return
            }
            if !confirmed && nowMillis() - armedAt > DangerAlertRules.staleSendMillis {
                setStep(miffId: miffId, armedAt: armedAt, step: .needsConfirmation)
                return
            }
            if !network.isOnline() {
                setStep(miffId: miffId, armedAt: armedAt, step: .waitingNetwork)
                await waitForNetwork()
                if !network.isOnline() {
                    // Le flux s'est arrêté sans que le réseau soit revenu : une pause, jamais une boucle serrée.
                    await sleep(pause(for: attempt))
                    attempt += 1
                }
                continue
            }
            setStep(miffId: miffId, armedAt: armedAt, step: .sending)
            let deviceId: String
            switch await clearance(uid: uid) {
            case .cleared(let identifier):
                deviceId = identifier
            case .replaced:
                // Ce téléphone est en train d'être déconnecté : rien n'est écrit, le pointeur part avec le compte.
                guard !Task.isCancelled else {
                    return
                }
                drop()
                return
            case .unverified:
                setStep(miffId: miffId, armedAt: armedAt, step: .waitingNetwork)
                await sleep(pause(for: attempt))
                attempt += 1
                continue
            }
            let now = nowMillis()
            let result = await startBounded(uid: uid, miffId: miffId, deviceId: deviceId, now: now)
            guard !Task.isCancelled else {
                return
            }
            if await handle(result, miffId: miffId, armedAt: armedAt, attempt: &attempt, now: now) {
                return
            }
        }
    }

    /// Ce que fait la boucle d'une réponse du serveur. Vrai : la boucle s'arrête (alerte partie, abandonnée, ou refusée avec un message) ;
    /// faux : un nouvel essai.
    private func handle(
        _ result: DangerStartResult,
        miffId: String,
        armedAt: Int,
        attempt: inout Int,
        now: Int
    ) async -> Bool {
        switch result {
        case .started(let startedAt, let expiresAt):
            activate(miffId: miffId, startedAt: startedAt, expiresAt: expiresAt, armedAt: armedAt)
            return true
        case .adopted(let doc):
            activate(
                miffId: miffId, startedAt: doc.startedAtMillis ?? now, expiresAt: doc.expiresAtMillis, armedAt: armedAt
            )
            return true
        case .tooSoon(let waitMillis):
            // L'alerte précédente a pris fin il y a moins d'une minute : les règles refuseraient. L'app attend les quelques secondes qui
            // restent, sans bruit, et la personne voit « Envoi… ».
            await sleep(waitMillis)
            return false
        case .failed(let failure):
            return await handle(failure, miffId: miffId, armedAt: armedAt, attempt: &attempt)
        }
    }

    private func handle(
        _ failure: DangerAlertFailure,
        miffId: String,
        armedAt: Int,
        attempt: inout Int
    ) async -> Bool {
        switch failure {
        case .offline, .replaced:
            setStep(miffId: miffId, armedAt: armedAt, step: .waitingNetwork)
            await sleep(pause(for: attempt))
            attempt += 1
            return false
        case .sharingStopped, .notMember:
            // `.notMember` n'est pas produit par la vraie lecture d'aujourd'hui (une Miff perdue est refusée par les règles : `.refused`, ou retirée par la perte confirmée
            // de la Miff) ; s'il arrive, il se dit comme « partage arrêté » : un message calme, jamais une alerte restée en route.
            drop()
            onEvent?(.notSent(miffId: miffId, failure: failure))
            return true
        case .quotaReached:
            // Lu AVANT d'écrire : les trois débuts des dernières 24 h sont pris. Dit calmement, sans reproche ; la personne n'est jamais
            // laissée sans issue (les numéros d'urgence passent d'abord).
            setStep(miffId: miffId, armedAt: armedAt, step: .quota)
            return true
        case .refused:
            await settleRefusal(miffId: miffId, armedAt: armedAt)
            return true
        }
    }

    /// Un refus seul ne prouve pas que le téléphone a été remplacé (horloge, règles) : la session est relue, et seulement alors la personne
    /// est informée, calmement.
    private func settleRefusal(miffId: String, armedAt: Int) async {
        guard let uid = currentUid() else {
            return
        }
        let verdict = await device.checkAfterRefusal(uid: uid)
        guard !Task.isCancelled else {
            return
        }
        if verdict == .replaced {
            drop()
        } else {
            setStep(miffId: miffId, armedAt: armedAt, step: .refused)
        }
    }

    // MARK: - La fin

    func endLoop(miffId: String) async {
        var attempt = 0
        while !Task.isCancelled {
            guard let uid = currentUid() else {
                return
            }
            if !network.isOnline() {
                setEnd(miffId: miffId, step: .waitingNetwork)
                await waitForNetwork()
                if !network.isOnline() {
                    await sleep(pause(for: attempt))
                    attempt += 1
                }
                continue
            }
            setEnd(miffId: miffId, step: .ending)
            let deviceId: String
            switch await clearance(uid: uid) {
            case .cleared(let identifier):
                deviceId = identifier
            case .replaced:
                guard !Task.isCancelled else {
                    return
                }
                drop()
                return
            case .unverified:
                setEnd(miffId: miffId, step: .waitingNetwork)
                await sleep(pause(for: attempt))
                attempt += 1
                continue
            }
            let result = await endBounded(uid: uid, miffId: miffId, deviceId: deviceId, now: nowMillis())
            guard !Task.isCancelled else {
                return
            }
            switch result {
            case .ended:
                drop()
                onEvent?(.ended(miffId: miffId))
                return
            case .failed(let failure):
                if failure == .offline {
                    setEnd(miffId: miffId, step: .waitingNetwork)
                    await sleep(pause(for: attempt))
                    attempt += 1
                } else {
                    await settleEndRefusal(miffId: miffId, uid: uid)
                    return
                }
            }
        }
    }

    private func settleEndRefusal(miffId: String, uid: String) async {
        let verdict = await device.checkAfterRefusal(uid: uid)
        guard !Task.isCancelled else {
            return
        }
        if verdict == .replaced {
            drop()
        } else {
            setEnd(miffId: miffId, step: .idle)
            onEvent?(.endNotSent)
            // Un refus de la fin peut venir d'un accès perdu à la Miff (retirée d'une Miff autre que celle affichée, app ouverte) : l'annuaire est relu, et une perte
            // CONFIRMÉE retire l'alerte (`onAlertRemoved`), au lieu de laisser « la fin n'est pas partie » boucler.
            onAccessDoubt?()
        }
    }
}
#endif
