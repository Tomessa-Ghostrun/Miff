import Foundation

// COÛT, CONFIDENTIALITÉ et SÉCURITÉ, pour ce fichier (FEED-8) :
// - UNE lecture d'un document (jamais une liste) par photo et par statut, quand la carte vient à l'écran et SEULEMENT si le statut dit qu'il y a
//   une photo récente (la carte décide : `PhotoSlot`). Une photo déjà lue, une absence ou un refus déjà connus pour le même statut ne sont PAS
//   relus (la mémoire). Deux cartes qui demandent la même photo en même temps partagent UNE lecture. Hors connexion : aucune lecture tentée.
// - Un refus des règles n'est JAMAIS pris pour « expirée » : il veut aussi dire « cette photo a un lieu et tu n'as pas encore publié le tien »
//   (réciprocité). Rien n'est donc jamais supprimé sur un refus (backend/data-model.md, §8, Feed) ; ce code n'écrit ni ne supprime rien.
// - Les photos ne sont gardées qu'en mémoire (`PhotoCache`). Rien n'est journalisé ni envoyé : ni la photo, ni son lieu, ni ses heures.
// Miroir de `FirestorePhotoReader` d'Android (la partie sans Firestore ; la lecture elle-même est `FirestorePhotoSource`).

struct PhotoLoaderDependencies {
    let source: PhotoSource
    let cache: PhotoCache
    let isOnline: () -> Bool
    /// Ai-je publié MA position dans cette Miff ? (connu localement : c'est la détection qui l'écrit.) Dit ce qu'un refus signifie.
    var ownPositionPublished: (String) -> Bool = { _ in false }
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
        let refusedBefore = cache.isRefused(
            miffId: request.miffId, uid: request.uid, lastUpdateAtMillis: request.lastUpdateAtMillis
        )
        // Un refus connu reste le même tant que je n'ai pas publié MA position : ensuite, la photo est lisible, il faut relire.
        if refusedBefore && !dependencies.ownPositionPublished(request.miffId) {
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
            let result = await self.fetch(request)
            self.inFlight[key] = nil
            return result
        }
        inFlight[key] = task
        return await task.value
    }

    private func fetch(_ request: PhotoRequest) async -> PhotoLookup {
        let cache = dependencies.cache
        let startedIn = cache.generation
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
            return refusal(for: request)
        case .unreachable:
            return .offline
        }
    }

    /// Ce que veut dire un refus (backend/data-model.md : « un refus de lecture est ambigu »). Sur MA carte, ou si j'ai publié ma propre
    /// position, ce n'est PAS la réciprocité (la photo a expiré, ou le partage s'est arrêté) : rien n'est montré. Sinon, c'est la
    /// réciprocité : la phrase calme « Partage ta position pour voir cette photo. ».
    private func refusal(for request: PhotoRequest) -> PhotoLookup {
        let cache = dependencies.cache
        if request.isMe || dependencies.ownPositionPublished(request.miffId) {
            cache.putMissing(miffId: request.miffId, uid: request.uid, lastUpdateAtMillis: request.lastUpdateAtMillis)
            return .missing
        }
        cache.putRefused(miffId: request.miffId, uid: request.uid, lastUpdateAtMillis: request.lastUpdateAtMillis)
        return .needsOwnPosition
    }
}

/// Ma position est-elle publiée dans cette Miff ? Lu sur la copie locale du suivi (là où la détection note ses écritures). Du Swift pur : l'état
/// est donné.
enum OwnPositionProbe {
    static func isPublished(state: LocalState?, uid: String?, miffId: String) -> Bool {
        guard let uid, let state, state.uid == uid else {
            return false
        }
        return state.miffs[miffId]?.positionAtMillis != nil
    }
}
