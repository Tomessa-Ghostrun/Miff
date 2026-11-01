import Foundation

// SÉCURITÉ, pour ce fichier : noms et identifiants de Miffs sont des données personnelles. Ils ne sont jamais journalisés ;
// chaque échec sort d'ici sous la forme d'une `MiffFailure` (sans message, sans code).

/// Ce que la personne tient, d'après le serveur.
struct HeldState {
    let valid: [MembershipRef]
    let summaries: [MiffSummary]
    let lost: [LostMiff]
    let premium: Bool
    let fromCache: Bool
}

enum StateRead {
    case value(HeldState)
    case failed(MiffFailure)
}

/// Lit ce que la personne tient : ses index d'appartenance, puis chaque Miff qu'ils désignent, et dit lesquelles sont perdues
/// (ticket Miff §5.4 et §5.7). Une Miff que le serveur dit « absente » ou refuse est PERDUE, mais seulement quand le serveur
/// est par ailleurs joignable (un refus d'App Check refuserait tout, et personne n'apprend qu'on l'a retiré à cause de cela).
/// Une erreur réseau n'est JAMAIS une perte.
final class MiffStateReader {
    private enum RefsRead {
        case refs([MembershipRef], fromCache: Bool)
        case failed(MiffFailure)
    }

    private enum Classification {
        case valid(name: String?)
        case lost(name: String?)
        case failed(MiffFailure)
    }

    private let store: MiffStore
    private let rules: MiffRepositoryRules

    init(store: MiffStore, rules: MiffRepositoryRules) {
        self.store = store
        self.rules = rules
    }

    /// `allowCache` : hors ligne, le cache du téléphone peut dire quoi MONTRER, mais rien n'est créé, rejoint ni nettoyé à
    /// partir de lui.
    func read(uid: String, allowCache: Bool) async -> StateRead {
        let premium = await isPremium(uid: uid)
        let refs: [MembershipRef]
        let fromCache: Bool
        switch await readRefs(uid: uid, allowCache: allowCache) {
        case .failed(let failure):
            return .failed(failure)
        case .refs(let found, let cached):
            refs = found
            fromCache = cached
        }
        var valid: [MembershipRef] = []
        var summaries: [MiffSummary] = []
        var lost: [LostMiff] = []
        for ref in refs {
            switch await classify(ref, uid: uid, fromCache: fromCache) {
            case .valid(let name):
                valid.append(ref)
                if let name {
                    summaries.append(MiffSummary(id: ref.miffId, name: name))
                }
            case .lost(let name):
                lost.append(LostMiff(ref: ref, name: name))
            case .failed(let failure):
                return .failed(failure)
            }
        }
        let held = HeldState(valid: valid, summaries: summaries, lost: lost, premium: premium, fromCache: fromCache)
        return .value(held)
    }

    /// La Miff existe-t-elle, lue auprès du SERVEUR ? Sert à vérifier un lot dont la réponse s'est perdue.
    func landed(_ miffId: String) async -> Bool {
        let store = self.store
        let read = await guarded { try await store.readMiff(miffId: miffId, fromServer: true) }
        if case .value(.exists) = read {
            return true
        }
        return false
    }

    private func readRefs(uid: String, allowCache: Bool) async -> RefsRead {
        let store = self.store
        let server = await guarded { try await store.readMemberships(uid: uid, fromServer: true) }
        switch server {
        case .value(let refs):
            return .refs(refs, fromCache: false)
        case .failed(let failure, _):
            // Hors ligne : le cache peut dire quoi montrer, mais rien n'est créé, rejoint ni nettoyé à partir de lui.
            guard allowCache else {
                return .failed(failure)
            }
            let cached = await guarded { try await store.readMemberships(uid: uid, fromServer: false) }
            guard case .value(let refs) = cached, !refs.isEmpty else {
                // Un cache VIDE n'est pas « aucune Miff » : hors ligne sur une installation neuve, on ne sait rien, donc
                // rien n'est affirmé (l'accueil inviterait sinon à en créer une une personne qui en a déjà une).
                return .failed(failure)
            }
            return .refs(refs, fromCache: true)
        }
    }

    private func classify(_ ref: MembershipRef, uid: String, fromCache: Bool) async -> Classification {
        let store = self.store
        let read = await guarded { try await store.readMiff(miffId: ref.miffId, fromServer: !fromCache) }
        switch read {
        case .value(.exists(let details)):
            return .valid(name: details.name)
        case .value(.missing):
            let name = await cachedName(ref.miffId)
            return .lost(name: name)
        case .failed(let failure, let lostAccess):
            if lostAccess, !fromCache, await serverReachable(uid) {
                let name = await cachedName(ref.miffId)
                return .lost(name: name)
            }
            if failure == .offline || lostAccess {
                // Aucune conclusion : elle reste, sans nom tant que le serveur n'a pas répondu (jamais « retiré » sur un doute).
                return .valid(name: nil)
            }
            return .failed(failure)
        }
    }

    private func cachedName(_ miffId: String) async -> String? {
        let store = self.store
        let read = await guarded { try await store.readMiff(miffId: miffId, fromServer: false) }
        if case .value(.exists(let details)) = read {
            return details.name
        }
        return nil
    }

    private func serverReachable(_ uid: String) async -> Bool {
        let store = self.store
        let read = await guarded { await store.serverReachable(uid: uid) }
        if case .value(let reachable) = read {
            return reachable
        }
        return false
    }

    /// Premium = un abonnement actif qui n'a pas expiré (les règles décident pour de vrai : ceci choisit seulement la clé de
    /// l'index). Si la lecture échoue, le compte est traité comme non Premium (limite connue F1, documentée dans le README).
    private func isPremium(uid: String) async -> Bool {
        let store = self.store
        let read = await guarded { try await store.readEntitlement(uid: uid) }
        guard case .value(let maybeEntitlement) = read,
              let entitlement = maybeEntitlement,
              let expires = entitlement.expiresAtMillis else {
            return false
        }
        return entitlement.active && expires > rules.epochMillis()
    }

    private func guarded<Value>(_ call: @escaping () async throws -> Value) async -> Guarded<Value> {
        await guardedCall(timeoutSeconds: rules.timeoutSeconds, call)
    }
}
