import Foundation

// COÛT, CONFIDENTIALITÉ et SÉCURITÉ, pour ce fichier (FEED-8) :
// - UNE lecture d'un document (jamais une liste) par photo et par statut, quand la carte vient à l'écran et SEULEMENT si le statut dit qu'il y a
//   une photo récente (la carte décide : `PhotoSlot`). Une photo déjà lue, une absence déjà connue ne sont PAS relues (la mémoire). Deux cartes qui
//   demandent la même photo en même temps partagent UNE lecture. Hors connexion : aucune lecture tentée.
// - Un refus des règles est AMBIGU (photo expirée, partage arrêté, ou ma position à publier : réciprocité) et n'est JAMAIS pris pour « expirée » :
//   rien n'est supprimé sur un refus (backend/data-model.md, §8, Feed) ; ce code n'écrit ni ne supprime rien.
// - La phrase « Partage ta position pour voir cette photo. » n'est dite que si le SERVEUR confirme que MA position n'est pas publiée
//   (`OwnPositionResolver` : note locale « publiée » d'abord, sinon UNE lecture de mon propre document, mémorisée 30 s par Miff). Doute (hors
//   connexion, lecture refusée, déconnecté, chargement) : rien n'est dit, jamais la phrase. Photo absente ou invalide : jamais la phrase.
// - Les photos ne sont gardées qu'en mémoire (`PhotoCache`). Rien n'est journalisé ni envoyé : ni la photo, ni son lieu, ni ses heures.
// Miroir de `CachedPhotoReader` d'Android (la partie sans Firestore ; la lecture elle-même est `FirestorePhotoSource`).

struct PhotoLoaderDependencies {
    let source: PhotoSource
    let cache: PhotoCache
    let isOnline: () -> Bool
    /// Ai-je publié MA position dans cette Miff ? Dit ce qu'un refus signifie (la note locale, puis le serveur, voir `OwnPositionResolver`).
    let ownPosition: OwnPositionResolving
    var nowMillis: () -> Int = { Int(Date().timeIntervalSince1970 * 1000) }
}

@MainActor
final class PhotoLoader: PhotoReading {
    private let dependencies: PhotoLoaderDependencies
    private var inFlight: [String: Task<PhotoLookup, Never>] = [:]

    init(_ dependencies: PhotoLoaderDependencies) {
        self.dependencies = dependencies
    }

    /// Ce que la mémoire sait sans rien lire : une copie, ou une absence connue. nil : il faut lire (ou rien n'est connu).
    func peek(_ request: PhotoRequest) -> PhotoLookup? {
        let cache = dependencies.cache
        if let photo = cache.photo(
            miffId: request.miffId,
            uid: request.uid,
            lastUpdateAtMillis: request.lastUpdateAtMillis,
            nowMillis: dependencies.nowMillis()
        ) {
            return .found(photo)
        }
        if cache.isMissing(miffId: request.miffId, uid: request.uid, lastUpdateAtMillis: request.lastUpdateAtMillis) {
            return .missing
        }
        // Un refus connu ne dit la phrase que tant que le serveur confirme (il y a moins de 30 s) que MA position n'est pas publiée. Sinon : il
        // faut relire (position publiée depuis, photo lisible) ou redemander (réponse trop ancienne).
        let refusedBefore = cache.isRefused(
            miffId: request.miffId, uid: request.uid, lastUpdateAtMillis: request.lastUpdateAtMillis
        )
        if refusedBefore && dependencies.ownPosition.known(miffId: request.miffId) == .notPublished {
            return .needsOwnPosition
        }
        return nil
    }

    func read(_ request: PhotoRequest) async -> PhotoLookup {
        if let known = peek(request) {
            return known
        }
        guard dependencies.isOnline() else {
            return .offline
        }
        let key = "\(request.miffId)/\(request.uid)/\(request.lastUpdateAtMillis)"
        if let running = inFlight[key] {
            return await running.value
        }
        let task = Task { () -> PhotoLookup in
            let result = await self.load(request)
            self.inFlight[key] = nil
            return result
        }
        inFlight[key] = task
        return await task.value
    }

    private func load(_ request: PhotoRequest) async -> PhotoLookup {
        let cache = dependencies.cache
        let startedIn = cache.generation
        // Un refus déjà connu pour ce statut : la photo n'est pas relue (3 lectures de règles) tant que ma position n'est pas publiée.
        let refusedBefore = !request.isMe && cache.isRefused(
            miffId: request.miffId, uid: request.uid, lastUpdateAtMillis: request.lastUpdateAtMillis
        )
        if refusedBefore {
            let position = await dependencies.ownPosition.state(miffId: request.miffId)
            guard cache.generation == startedIn else {
                return .missing
            }
            switch position {
            case .notPublished:
                return .needsOwnPosition
            case .unknown:
                return .offline
            case .published:
                break
            }
        }
        let outcome = await dependencies.source.fetch(
            miffId: request.miffId, uid: request.uid, nowMillis: dependencies.nowMillis()
        )
        // La session a pris fin pendant la lecture (déconnexion) : la réponse n'est ni montrée ni gardée, une autre personne ne peut rien en tirer.
        guard cache.generation == startedIn else {
            return .missing
        }
        switch outcome {
        case .photo(let photo):
            cache.putRead(
                miffId: request.miffId, uid: request.uid, photo: photo, lastUpdateAtMillis: request.lastUpdateAtMillis
            )
            return .found(photo)
        case .absent:
            cache.putMissing(miffId: request.miffId, uid: request.uid, lastUpdateAtMillis: request.lastUpdateAtMillis)
            return .missing
        case .refused:
            return await refusal(for: request, startedIn: startedIn)
        case .unreachable:
            return .offline
        }
    }

    /// Ce que veut dire un refus (backend/data-model.md : « un refus de lecture est ambigu »). Sur MA carte, ou si MA position est publiée, ce
    /// n'est PAS la réciprocité (la photo a expiré, ou le partage s'est arrêté) : rien n'est montré. La phrase calme « Partage ta position pour
    /// voir cette photo. » n'est dite que si le serveur confirme que ma position n'est PAS publiée ; au moindre doute, rien.
    private func refusal(for request: PhotoRequest, startedIn: Int) async -> PhotoLookup {
        let cache = dependencies.cache
        if request.isMe {
            cache.putMissing(miffId: request.miffId, uid: request.uid, lastUpdateAtMillis: request.lastUpdateAtMillis)
            return .missing
        }
        let position = await dependencies.ownPosition.state(miffId: request.miffId)
        guard cache.generation == startedIn else {
            return .missing
        }
        switch position {
        case .published:
            cache.putMissing(miffId: request.miffId, uid: request.uid, lastUpdateAtMillis: request.lastUpdateAtMillis)
            return .missing
        case .notPublished:
            cache.putRefused(miffId: request.miffId, uid: request.uid, lastUpdateAtMillis: request.lastUpdateAtMillis)
            return .needsOwnPosition
        case .unknown:
            // Pas su, et pas mémorisé : la carte réessaiera (retour du réseau, retour à l'écran). Rien n'est dessiné.
            return .offline
        }
    }
}

/// Ma position est-elle publiée dans cette Miff ? Lu sur la copie locale du suivi (là où la détection note ses écritures). Du Swift pur : l'état
/// est donné. Seul un « oui » est fiable : la copie est vide après une reprise du partage, une réinstallation, ou sans copie locale.
enum OwnPositionProbe {
    static func isPublished(state: LocalState?, uid: String?, miffId: String) -> Bool {
        guard let uid, let state, state.uid == uid else {
            return false
        }
        return state.miffs[miffId]?.positionAtMillis != nil
    }
}
