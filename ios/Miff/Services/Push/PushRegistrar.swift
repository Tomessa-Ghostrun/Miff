import Foundation

// SRV-2I : tient le document de CE téléphone dans le registre des appareils (`users/{uid}/devices/{deviceId}`) d'accord avec ce que la personne a
// choisi (ticket `docs/tickets/notification-update-membres.md`, §6 et §9 ; contrat du backend : `docs/dev/jetons-appareil-clients.md`). Miroir
// de `PushRegistrar` d'Android.
//
// LES RÈGLES, au même endroit :
// - RIEN n'est enregistré sans un compte connecté sur ce téléphone AVEC une session valide (SESS : un compte, un appareil ; l'identifiant du
//   document est l'identifiant de la session), et sans l'ACCORD de la personne (`consent` : l'autorisation du système est accordée ET la personne
//   a répondu à l'écran qui dit que Miff enregistre un identifiant technique de son téléphone). Tant que ce n'est pas le cas, le service de
//   notification du téléphone reste ÉTEINT : aucun jeton n'existe.
// - Une NOUVELLE session sur ce téléphone supprime les autres documents du compte, avec ou sans accord (contrat §4.2), une fois par session.
// - L'interrupteur « Quand un proche donne des nouvelles » s'écrit `notify.updates` ; l'autorisation retirée SUPPRIME le document et invalide le
//   jeton.
// - Un document n'est réécrit que si le jeton, l'interrupteur ou (une fois par jour au plus) le fuseau ont changé, ou après 30 jours
//   (`RegistrationRules`) ; une écriture ratée attend 10 minutes.
// - SORTIE (`forget` : déconnexion, perte de session, suppression du compte) : l'invalidation du jeton chez Firebase passe EN PREMIER,
//   indépendamment de Firestore et sans attendre aucun verrou ni transaction (hors connexion, tout est borné dans le temps) ; puis le document
//   est supprimé si le compte est encore joignable ; puis la mémoire du téléphone est effacée.
//
// Rien ici ne journalise, n'envoie d'événement ni ne garde le jeton. Un échec n'est jamais montré : l'enregistrement est un confort, le Feed
// reste la source de vérité.

/// Tout ce dont le registraire a besoin du monde extérieur (des doubles dans les tests).
struct PushRegistrarDependencies {
    let registry: DeviceTokenRegistry
    let local: PushLocalState
    let tokens: PushTokenSource
    /// Le compte connecté sur ce téléphone, ou nil.
    let currentUid: () -> String?
    /// L'identifiant de la session de CE téléphone, CONFIRMÉ par le serveur (`DeviceSession.currentDeviceId`), ou nil.
    let sessionDeviceId: () -> String?
    /// La personne a consenti : l'autorisation du système est accordée ET elle a répondu à l'écran qui parle de l'identifiant du téléphone.
    let consent: (String) async -> Bool
    /// L'interrupteur « Quand un proche donne des nouvelles » de ce compte (valeur écrite dans `notify.updates`).
    let notifyUpdates: (String) -> Bool
    let isOnline: () -> Bool
    let timeZoneId: () -> String
    let appVersion: () -> String
    let nowMillis: () -> Int
    /// Retire du tiroir les notifications déjà affichées (à la sortie : la personne suivante ne voit jamais celles de la précédente).
    let clearDelivered: () -> Void
    var writeTimeoutSeconds: TimeInterval = 8
    var deleteTimeoutSeconds: TimeInterval = 4
}

/// Ce qu'une synchronisation sait d'elle-même pendant qu'elle écrit.
private struct SyncContext {
    let uid: String
    let deviceId: String
    let record: RegistrationRecord?
    let startedIn: Int
}

@MainActor
final class PushRegistrar {
    private let deps: PushRegistrarDependencies
    /// Une seule synchronisation à la fois. La sortie (`forget`) ne passe PAS par cette porte : elle ne doit jamais attendre une écriture.
    private let gate = AsyncGate()
    /// Monte à chaque sortie : une synchronisation commencée avant ne termine rien après (pas de jeton rallumé, pas d'écriture tardive).
    private var epoch = 0
    private var failedAtMillis: Int?
    private var cleaningFailedAtMillis: Int?
    /// Le souhait du dernier essai, pour distinguer une nouvelle tentative d'un changement (un changement s'essaie tout de suite).
    private var lastTriedWish: RegistrationWish?

    init(_ dependencies: PushRegistrarDependencies) {
        deps = dependencies
    }

    /// Regarde ce qu'il faut écrire ou supprimer et le fait. Sans danger à appeler souvent (retour au premier plan, réponse à l'écran
    /// d'accord, interrupteur, nouveau jeton) : ne coûte rien quand rien n'a changé. `freshToken` : un jeton que le système vient de fournir.
    func sync(freshToken: String? = nil) async {
        let startedIn = epoch
        await gate.run { await self.syncUnlocked(freshToken: freshToken, startedIn: startedIn) }
    }

    private func syncUnlocked(freshToken: String?, startedIn: Int) async {
        guard startedIn == epoch else {
            return
        }
        guard let uid = deps.currentUid() else {
            await cleanUpWithoutAccount()
            return
        }
        // Pas de session confirmée (hors ligne au lancement, remplacée, fermée) : rien n'est écrit, et rien n'est retiré non plus.
        guard let deviceId = deps.sessionDeviceId(), DeviceId.isValid(deviceId) else {
            return
        }
        await clearOtherDevices(uid: uid, deviceId: deviceId)
        let record = deps.local.record()
        guard await deps.consent(uid) else {
            await withdraw(uid: uid, deviceId: deviceId, record: record)
            return
        }
        guard startedIn == epoch else {
            return
        }
        let token = await currentToken(freshToken: freshToken)
        // Une sortie pendant l'allumage : le service qu'on vient d'allumer est éteint de nouveau (jamais de jeton après la sortie).
        guard startedIn == epoch else {
            await deps.tokens.disable()
            return
        }
        guard let token else {
            return
        }
        await write(token: token, for: SyncContext(uid: uid, deviceId: deviceId, record: record, startedIn: startedIn))
    }

    /// Le jeton à enregistrer : celui que le système vient de donner, sinon celui qu'il connaît déjà. Allumer le service est noté AVANT, pour
    /// qu'une app tuée au milieu l'éteigne au lancement suivant. nil : le jeton arrivera tout seul (`sync(freshToken:)`).
    private func currentToken(freshToken: String?) async -> String? {
        deps.local.setServiceOn(true)
        if let freshToken {
            return freshToken
        }
        return await deps.tokens.enableAndFetch()
    }

    private func write(token: String, for context: SyncContext) async {
        let uid = context.uid
        let deviceId = context.deviceId
        let startedIn = context.startedIn
        let wish = RegistrationWish(
            uid: uid,
            deviceId: deviceId,
            tokenFingerprint: TokenFingerprint.of(token),
            timeZoneId: deps.timeZoneId(),
            notifyUpdates: deps.notifyUpdates(uid)
        )
        let now = deps.nowMillis()
        guard case .write = RegistrationRules.decide(wish: wish, record: context.record, nowMillis: now) else {
            return
        }
        if wish == lastTriedWish && !RegistrationRules.mayRetry(failedAtMillis: failedAtMillis, nowMillis: now) {
            return
        }
        guard deps.isOnline(),
              let entry = DeviceEntry.make(
                token: token,
                timeZoneId: wish.timeZoneId,
                notifyUpdates: wish.notifyUpdates,
                appVersion: deps.appVersion()
              ) else {
            return
        }
        lastTriedWish = wish
        let registry = deps.registry
        do {
            try await timedOperation(seconds: deps.writeTimeoutSeconds) {
                try await registry.upsert(uid: uid, deviceId: deviceId, entry: entry)
            }
            // Une sortie pendant l'écriture : rien n'est retenu (le jeton est déjà invalidé, le serveur nettoiera le document).
            guard startedIn == epoch else {
                return
            }
            deps.local.saveRecord(
                RegistrationRecord(
                    uid: uid,
                    deviceId: deviceId,
                    tokenFingerprint: wish.tokenFingerprint,
                    timeZoneId: wish.timeZoneId,
                    notifyUpdates: wish.notifyUpdates,
                    writtenAtMillis: now
                )
            )
            failedAtMillis = nil
        } catch {
            // Hors ligne, règles pas encore déployées, session remplacée : réessayé plus tard. Jamais montré, jamais journalisé.
            failedAtMillis = now
        }
    }

    /// Une nouvelle session : les autres documents du compte sont supprimés (contrat §4.2), avec ou sans accord, UNE fois par session. Un échec
    /// est réessayé plus tard, pas à chaque retour au premier plan.
    private func clearOtherDevices(uid: String, deviceId: String) async {
        let now = deps.nowMillis()
        guard deps.local.othersClearedFor() != deviceId,
              deps.isOnline(),
              RegistrationRules.mayRetry(failedAtMillis: cleaningFailedAtMillis, nowMillis: now) else {
            return
        }
        let registry = deps.registry
        do {
            try await timedOperation(seconds: deps.writeTimeoutSeconds) {
                try await registry.deleteOthers(uid: uid, keepDeviceId: deviceId)
            }
            deps.local.setOthersClearedFor(deviceId)
            cleaningFailedAtMillis = nil
        } catch {
            cleaningFailedAtMillis = now
        }
    }

    /// L'accord n'est plus là pour le compte en cours (autorisation retirée dans les Réglages du téléphone) : le jeton est invalidé D'ABORD,
    /// puis le document supprimé, le service éteint.
    private func withdraw(uid: String, deviceId: String, record: RegistrationRecord?) async {
        guard record != nil || deps.local.serviceOn() else {
            return
        }
        await deps.tokens.disable()
        if let record, record.uid == uid {
            await deleteDocument(uid: record.uid, deviceId: record.deviceId)
        }
        deps.local.clear()
        // Les autres documents de cette session ont déjà été vus plus haut : pas redemandé.
        deps.local.setOthersClearedFor(deviceId)
        lastTriedWish = nil
    }

    /// Personne n'est connecté mais le service est resté allumé (app tuée pendant une déconnexion) : il est éteint. Le document, lui, ne peut plus
    /// être supprimé sans compte ; il est inerte (jeton invalidé, le serveur le supprime à la première réponse « jeton inconnu »).
    private func cleanUpWithoutAccount() async {
        guard deps.local.record() != nil || deps.local.serviceOn() else {
            return
        }
        await deps.tokens.disable()
        deps.local.clear()
        lastTriedWish = nil
    }

    /// LA SORTIE : déconnexion, perte de session, suppression du compte. Jamais bloquée par une écriture en cours ni par le réseau.
    /// 1. le jeton est invalidé chez Firebase et le service éteint ; 2. les notifications affichées sont retirées ; 3. le document est supprimé
    /// si le compte est encore joignable (borné) ; 4. la mémoire du téléphone est effacée. Ne lève jamais.
    func forget() async {
        epoch += 1
        let record = deps.local.record()
        if record != nil || deps.local.serviceOn() {
            await deps.tokens.disable()
        }
        deps.clearDelivered()
        if let record, deps.currentUid() == record.uid, deps.isOnline() {
            await deleteDocument(uid: record.uid, deviceId: record.deviceId)
        }
        deps.local.clear()
        lastTriedWish = nil
        failedAtMillis = nil
        cleaningFailedAtMillis = nil
    }

    private func deleteDocument(uid: String, deviceId: String) async {
        guard deps.isOnline() else {
            return
        }
        let registry = deps.registry
        do {
            try await timedOperation(seconds: deps.deleteTimeoutSeconds) {
                try await registry.delete(uid: uid, deviceId: deviceId)
            }
        } catch {
            // Hors ligne ou déjà parti : le serveur supprime le document à la première réponse « jeton inconnu » de FCM.
        }
    }
}
