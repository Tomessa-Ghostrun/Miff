import Foundation

/// Un dépôt de lieux qui prévient le suivi des lieux quand un lieu a VRAIMENT changé (écriture confirmée par le serveur) : le
/// téléphone du créateur relit alors ses lieux tout de suite, au lieu d'attendre la prochaine relecture horaire. Il ne fait que relayer
/// les appels ; il ne lit ni n'écrit rien lui-même, ne garde aucun lieu, ne journalise rien.
struct ChangeNotifyingPlaceRepository: PlaceRepository {
    private let base: PlaceRepository
    private let onChanged: () -> Void

    init(_ base: PlaceRepository, onChanged: @escaping () -> Void) {
        self.base = base
        self.onChanged = onChanged
    }

    func prepare(uid: String) async -> PlaceContextLoad {
        await base.prepare(uid: uid)
    }

    func create(uid: String, draft: PlaceDraft, context: PlaceContext) async -> PlaceWriteResult {
        let result = await base.create(uid: uid, draft: draft, context: context)
        notify(result)
        return result
    }

    func update(uid: String, original: PlaceGroup, draft: PlaceDraft, context: PlaceContext) async -> PlaceWriteResult {
        let result = await base.update(uid: uid, original: original, draft: draft, context: context)
        notify(result)
        return result
    }

    func delete(placeId: String, miffIds: [String]) async -> PlaceWriteResult {
        let result = await base.delete(placeId: placeId, miffIds: miffIds)
        notify(result)
        return result
    }

    private func notify(_ result: PlaceWriteResult) {
        if case .done = result {
            onChanged()
        }
    }
}
